package com.predictengines.app.data

import com.predictengines.app.data.PerformanceReport.Tally
import java.time.LocalDate

/**
 * Data-trend picks. The Data tab already knows how every market has done in every league; this turns that into a filter
 * over the models' own picks. A "spot" is one market in one league (Over 1.5 in the Bundesliga). A spot is strong when it
 * has enough settled picks, a high enough score (the hit rate corrected for sample size) and, unless switched off, it
 * beats the same market in the other leagues by a margin that is unlikely to be luck. A spot is cold when it has enough
 * picks and hits under 50% or clearly worse than the other leagues. Trends never makes a prediction: upcoming picks that
 * the models already made and that fall in a strong spot are the Trend picks.
 */
object Trends {
    const val SOURCE = "Trends"
    const val DEFAULT_MIN = 15
    const val DEFAULT_SCORE = 65

    /** The weekly lists whose settled picks feed the trends (Trend picks themselves never feed back in). */
    val BASE_SOURCES = setOf("Top 20", "Handicap", "Over 1.5", "Loki", "Corners")

    /** Fewest settled picks the other leagues need before a spot is compared with them. */
    private const val MIN_REST = 10
    /** How far (in standard errors) a spot must sit from the other leagues' rate: about a 90% one-sided confidence. */
    private const val BEAT_Z = 1.28

    data class Config(
        val minPicks: Int = DEFAULT_MIN,
        val minScore: Int = DEFAULT_SCORE,
        /** The spot must also beat the same market in the other leagues. */
        val beatAverage: Boolean = true,
        /** Which settled picks count: all time, this season, the last 12 or 4 weeks. */
        val window: PerformanceReport.Window = PerformanceReport.Window.ALL,
        /** A Trend pick needs at least this many models behind it, and this average confidence (%). */
        val minBackers: Int = 1,
        val minConfidence: Int = 60
    )

    data class Spot(
        val leagueId: String, val leagueName: String, val key: String, val name: String,
        val tally: Tally, val rest: Tally
    ) {
        val score: Double get() = tally.adjusted
    }

    data class Found(val strong: List<Spot>, val cold: List<Spot>)

    /** How the filter would have done: each past pick judged by what was known before its game, never by its own result. */
    data class Backtest(val strong: Tally, val cold: Tally, val other: Tally)

    private fun dedupe(records: Collection<PredictionRecord>, visible: Set<String>): List<PredictionRecord> {
        // one pick can sit on several lists (Top 20 and Over 1.5): count each game's selection once
        val seen = HashSet<String>()
        return records.filter { r ->
            r.source in BASE_SOURCES && r.leagueId in visible &&
                seen.add("${r.leagueId}|${r.date}|${r.home}|${r.away}|${r.key}")
        }
    }

    private fun tally(rs: Collection<PredictionRecord>) = Tally(rs.count { it.correct }, rs.count { !it.correct })

    private fun beats(own: Tally, rest: Tally, cfg: Config): Boolean =
        !cfg.beatAverage || rest.total < MIN_REST || (own.rate > rest.rate && PerformanceReport.gapZ(own, rest) >= BEAT_Z)

    fun isStrong(own: Tally, rest: Tally, cfg: Config): Boolean =
        own.total >= cfg.minPicks && own.adjusted >= cfg.minScore && beats(own, rest, cfg)

    fun isCold(own: Tally, rest: Tally, cfg: Config): Boolean =
        own.total >= cfg.minPicks && (
            own.rate < PerformanceReport.AVOID_RATE ||
                (cfg.beatAverage && rest.total >= MIN_REST && own.rate < rest.rate && PerformanceReport.gapZ(rest, own) >= BEAT_Z)
            )

    private fun inWindow(r: PredictionRecord, from: LocalDate?) =
        from == null || DateUtils.parse(r.date)?.let { !it.isBefore(from) } == true

    fun spots(
        records: Collection<PredictionRecord>, visible: Set<String>, cfg: Config, today: LocalDate = LocalDate.now()
    ): Found {
        val from = PerformanceReport.cutoff(cfg.window, today)
        val rs = dedupe(records, visible).filter { inWindow(it, from) }
        val strong = ArrayList<Spot>()
        val cold = ArrayList<Spot>()
        for ((_, group) in rs.groupBy { it.key }) {
            val all = tally(group)
            for ((_, x) in group.groupBy { it.leagueId }) {
                val own = tally(x)
                if (own.total < cfg.minPicks) continue
                val rest = Tally(all.correct - own.correct, all.wrong - own.wrong)
                val spot = Spot(x.first().leagueId, x.first().leagueName, x.first().key, PerformanceReport.keyName(x.first().key), own, rest)
                if (isStrong(own, rest, cfg)) strong.add(spot) else if (isCold(own, rest, cfg)) cold.add(spot)
            }
        }
        return Found(
            strong.sortedWith(compareBy<Spot> { it.leagueName }.thenByDescending { it.score }),
            cold.sortedBy { it.tally.rate }
        )
    }

    /** The candidates that fall in a strong spot and have enough model support, strongest data first. */
    fun pick(candidates: List<TopPick>, strong: List<Spot>, cfg: Config): List<TopPick> {
        val byKey = strong.associateBy { it.leagueId to it.key }
        return candidates.mapNotNull { tp ->
            val s = byKey[tp.leagueId to tp.pick.key] ?: return@mapNotNull null
            if (tp.pick.backers.size < cfg.minBackers || tp.pick.averageConfidence < cfg.minConfidence) return@mapNotNull null
            tp.copy(
                pick = tp.pick.copy(
                    label = "${tp.pick.label} · ${s.leagueName} ${s.tally.correct}/${s.tally.total}",
                    strength = s.score + 0.1 * tp.pick.strength
                )
            )
        }
    }

    fun backtest(records: Collection<PredictionRecord>, visible: Set<String>, cfg: Config): Backtest {
        val rs = dedupe(records, visible).mapNotNull { r -> DateUtils.parse(r.date)?.let { r to it } }
        val byKey = rs.groupBy { it.first.key }.mapValues { (_, l) -> l.sortedBy { it.second } }
        var s = Tally(); var c = Tally(); var o = Tally()
        for ((r, d) in rs) {
            val from = PerformanceReport.cutoff(cfg.window, d)
            val prior = byKey.getValue(r.key).takeWhile { it.second.isBefore(d) }
                .filter { from == null || !it.second.isBefore(from) }.map { it.first }
            val own = tally(prior.filter { it.leagueId == r.leagueId })
            val rest = tally(prior.filter { it.leagueId != r.leagueId })
            val t = if (r.correct) Tally(1, 0) else Tally(0, 1)
            when {
                isStrong(own, rest, cfg) -> s += t
                isCold(own, rest, cfg) -> c += t
                else -> o += t
            }
        }
        return Backtest(s, c, o)
    }

    /** One honest sentence on whether the filter has been helping. */
    fun verdict(b: Backtest): String {
        val pct = { t: Tally -> "${Math.round(t.rate)}%" }
        if (b.strong.total < 20) {
            return "Too few picks (${b.strong.total}) were in a strong spot at the time to judge the filter yet. Keep settling picks."
        }
        val diff = b.strong.rate - b.other.rate
        val z = PerformanceReport.gapZ(b.strong, b.other)
        return when {
            diff >= 3 && z >= BEAT_Z -> "The filter is helping: picks in a strong spot hit ${pct(b.strong)} against ${pct(b.other)} for the rest."
            diff <= -3 && z <= -BEAT_Z -> "The filter is not helping: picks in a strong spot hit ${pct(b.strong)} against ${pct(b.other)} for the rest. Tighten the settings or ignore the Trends tab."
            else -> "No clear benefit yet: picks in a strong spot hit ${pct(b.strong)} against ${pct(b.other)} for the rest."
        }
    }
}
