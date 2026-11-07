package com.predictengines.app.data

import com.predictengines.app.data.PerformanceReport.Tally
import java.time.LocalDate

/**
 * Trend picks. Trends never makes a prediction and has no rules of its own: it reads the same results the report shows
 * (the verdicts of [PerformanceReport]) and keeps the models' upcoming picks that sit in a place the data calls Strong.
 * Two kinds of strong place count:
 *  - a strong league on a list: the league's verdict on that list is Strong (Over 1.5 in the Bundesliga), so that list's
 *    picks in that league are kept;
 *  - a strong market in a league: one market (Over 1.5, Home win...) in that league is Strong across all the lists.
 * A cold place is an Avoid verdict; it is only used to tag cards on the other lists.
 */
object Trends {
    const val SOURCE = "Trends"

    /** The weekly lists whose settled picks feed the trends (Trend picks themselves never feed back in). */
    val BASE_SOURCES = setOf("Top 20", "Handicap", "Over 1.5", "Loki", "Corners")

    /** How far (in standard errors) strong picks must sit above the rest before the backtest calls the filter helpful. */
    private const val BEAT_Z = 1.28

    data class Config(
        /** Which results count: all time, this season, the last 12 or 4 weeks. */
        val window: PerformanceReport.Window = PerformanceReport.Window.ALL,
        /** A Trend pick needs at least this many models behind it, and this average confidence (%). */
        val minBackers: Int = 1,
        val minConfidence: Int = 60,
        /** Also keep a strong league's picks on a list, not only strong single markets. */
        val leagueSpots: Boolean = true
    )

    /** One place the data is strong (or cold): a market in a league ([list] null), or a league on one list. */
    data class Spot(
        val leagueId: String, val leagueName: String,
        /** The market key for a market spot; blank for a league-on-a-list spot. */
        val key: String, val name: String, val tally: Tally, val list: String? = null
    ) {
        val isList: Boolean get() = list != null
        val score: Double get() = tally.adjusted
        val rate: Double get() = tally.rate
        val shortText: String get() = "${tally.correct}/${tally.total}"
        val line: String
            get() = "${tally.correct}/${tally.total} right (${Math.round(tally.rate)}%) · score ${Math.round(tally.adjusted)}"
    }

    data class Found(val strong: List<Spot>, val cold: List<Spot>)

    /** How the filter would have done: each past pick judged by what was known before its game, never by its own result. */
    data class Backtest(val strong: Tally, val cold: Tally, val other: Tally)

    private fun tally(rs: Collection<PredictionRecord>) = Tally(rs.count { it.correct }, rs.count { !it.correct })

    private fun inWindow(r: PredictionRecord, from: LocalDate?) =
        from == null || DateUtils.parse(r.date)?.let { !it.isBefore(from) } == true

    private fun base(records: Collection<PredictionRecord>, visible: Set<String>) =
        records.filter { it.source in BASE_SOURCES && it.leagueId in visible }

    /** One pick can sit on several lists (Top 20 and Over 1.5): count each game's selection once for market spots. */
    private fun dedupe(rs: List<PredictionRecord>): List<PredictionRecord> {
        val seen = HashSet<String>()
        return rs.filter { seen.add("${it.leagueId}|${it.date}|${it.home}|${it.away}|${it.key}") }
    }

    fun spots(records: Collection<PredictionRecord>, visible: Set<String>, cfg: Config, today: LocalDate = LocalDate.now()): Found {
        val from = PerformanceReport.cutoff(cfg.window, today)
        val rs = base(records, visible).filter { inWindow(it, from) }
        val strong = ArrayList<Spot>(); val cold = ArrayList<Spot>()
        for ((id, x) in dedupe(rs).groupBy { it.leagueId to it.key }) {
            val t = tally(x)
            val spot = Spot(id.first, x.first().leagueName, id.second, PerformanceReport.keyName(id.second), t)
            when (PerformanceReport.verdict(t, PerformanceReport.MIN_MARKET)) {
                PerformanceReport.Verdict.STRONG -> strong.add(spot)
                PerformanceReport.Verdict.AVOID -> cold.add(spot)
                else -> {}
            }
        }
        if (cfg.leagueSpots) for ((id, x) in rs.groupBy { it.source to it.leagueId }) {
            val t = tally(x)
            // a list that only ever picks one market (Over 1.5) would repeat that market's spot: show it once
            val repeats = strong.any { !it.isList && it.leagueId == id.second && it.tally == t }
            if (!repeats && PerformanceReport.verdict(t, PerformanceReport.MIN_LEAGUE) == PerformanceReport.Verdict.STRONG) {
                strong.add(Spot(id.second, x.first().leagueName, "", "${id.first} list", t, list = id.first))
            }
        }
        return Found(
            strong.sortedWith(compareBy<Spot> { it.leagueName }.thenBy { !it.isList }.thenByDescending { it.score }),
            cold.sortedBy { it.rate }
        )
    }

    /**
     * Keeps the upcoming picks that sit in a strong spot and have enough model support. [lists] are the candidates of each
     * weekly list (name first; null for the pool of every consensus pick). A pick in several spots keeps its best one.
     */
    fun pick(lists: List<Pair<String?, List<TopPick>>>, found: Found, cfg: Config): List<TopPick> {
        val market = found.strong.filter { !it.isList }.associateBy { it.leagueId to it.key }
        val leagueList = found.strong.filter { it.isList }.associateBy { it.list.orEmpty() to it.leagueId }
        val out = LinkedHashMap<String, TopPick>()
        for ((src, cands) in lists) for (tp in cands) {
            if (tp.pick.backers.size < cfg.minBackers || tp.pick.averageConfidence < cfg.minConfidence) continue
            val s = market[tp.leagueId to tp.pick.key] ?: src?.let { leagueList[it to tp.leagueId] } ?: continue
            val why = if (s.isList) "${s.leagueName} is strong on ${s.list} ${s.shortText}" else "${s.leagueName} ${s.name} ${s.shortText}"
            val made = tp.copy(pick = tp.pick.copy(label = "${tp.pick.label} · $why", strength = s.score + 0.1 * tp.pick.strength))
            val id = "${tp.leagueId}|${tp.date}|${tp.home}|${tp.away}|${tp.pick.key}"
            val old = out[id]
            if (old == null || made.pick.strength > old.pick.strength) out[id] = made
        }
        return out.values.toList()
    }

    fun backtest(records: Collection<PredictionRecord>, visible: Set<String>, cfg: Config): Backtest {
        val all = base(records, visible)
        val dated = dedupe(all).mapNotNull { r -> DateUtils.parse(r.date)?.let { r to it } }
        val byMarket = dated.groupBy { it.first.leagueId to it.first.key }.mapValues { (_, l) -> l.sortedBy { it.second } }
        val byList = all.mapNotNull { r -> DateUtils.parse(r.date)?.let { r to it } }
            .groupBy { it.first.source to it.first.leagueId }.mapValues { (_, l) -> l.sortedBy { it.second } }
        var s = Tally(); var c = Tally(); var o = Tally()
        for ((r, d) in dated) {
            val from = PerformanceReport.cutoff(cfg.window, d)
            fun prior(l: List<Pair<PredictionRecord, LocalDate>>?) =
                tally(l.orEmpty().takeWhile { it.second.isBefore(d) }.filter { from == null || !it.second.isBefore(from) }.map { it.first })
            val mv = PerformanceReport.verdict(prior(byMarket[r.leagueId to r.key]), PerformanceReport.MIN_MARKET)
            val lv = if (cfg.leagueSpots) PerformanceReport.verdict(prior(byList[r.source to r.leagueId]), PerformanceReport.MIN_LEAGUE)
            else PerformanceReport.Verdict.OK
            val t = if (r.correct) Tally(1, 0) else Tally(0, 1)
            when {
                mv == PerformanceReport.Verdict.STRONG || lv == PerformanceReport.Verdict.STRONG -> s += t
                mv == PerformanceReport.Verdict.AVOID -> c += t
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
