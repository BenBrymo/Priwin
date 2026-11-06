package com.predictengines.app.data

import java.time.LocalDate
import kotlin.math.sqrt

/**
 * How the settled picks of the three weekly lists (Top 20, Handicap, Over 1.5) performed, per league, market, team,
 * confidence and agreement; how picks that carried a Kryptonite flag did against clean ones (and against the picks a
 * Kryptonite hid); and how each model (Brymo, Poisson, Stats) did on its own picks. The verdict weighs the hit rate
 * and the number of picks together (see [Tally.adjusted]).
 */
object PerformanceReport {

    const val ALL_LISTS = "All lists"
    val LISTS = listOf("Top 20", "Handicap", "Over 1.5", "Loki", "Corners")

    enum class Window(val label: String) {
        ALL("All time"), SEASON("This season"), WEEKS12("Last 12 weeks"), WEEKS4("Last 4 weeks")
    }

    /** First day of the window, or null for all time. A football season is counted from 1 August. */
    fun cutoff(w: Window, today: LocalDate): LocalDate? = when (w) {
        Window.ALL -> null
        Window.SEASON -> LocalDate.of(if (today.monthValue >= 8) today.year else today.year - 1, 8, 1)
        Window.WEEKS12 -> today.minusWeeks(12)
        Window.WEEKS4 -> today.minusWeeks(4)
    }

    data class Tally(val correct: Int = 0, val wrong: Int = 0) {
        val total: Int get() = correct + wrong
        /** Plain hit rate, 0-100. */
        val rate: Double get() = if (total == 0) 0.0 else correct * 100.0 / total
        /**
         * The hit rate corrected for how many picks there are (the lower end of the 95% Wilson interval, 0-100):
         * 3 right out of 3 scores far lower than 40 right out of 55.
         */
        val adjusted: Double
            get() {
                if (total == 0) return 0.0
                val n = total.toDouble(); val p = correct / n; val z = 1.96
                return 100.0 * (p + z * z / (2 * n) - z * sqrt(p * (1 - p) / n + z * z / (4 * n * n))) / (1 + z * z / n)
            }
        operator fun plus(o: Tally) = Tally(correct + o.correct, wrong + o.wrong)
    }

    /**
     * How far apart two hit rates are, in standard errors (two-proportion z): 0 means identical, and about 1.65 or more
     * means the gap is unlikely to be luck. A 95% against 82% gap on 20 against 66 picks is only about 1.4.
     */
    fun gapZ(a: Tally, b: Tally): Double {
        if (a.total == 0 || b.total == 0) return 0.0
        val pooled = (a.correct + b.correct).toDouble() / (a.total + b.total)
        val se = sqrt(pooled * (1 - pooled) * (1.0 / a.total + 1.0 / b.total))
        return if (se == 0.0) 0.0 else (a.correct.toDouble() / a.total - b.correct.toDouble() / b.total) / se
    }

    const val CLEAR_GAP_Z = 1.65
    const val MIN_GAP_POINTS = 8.0
    /** Fewest flagged picks of one kind to show in a Kryptonite's by-pick-type split. */
    const val MIN_TYPE = 3

    enum class Verdict(val label: String) { STRONG("Strong"), OK("OK"), AVOID("Avoid"), FEW("Not enough picks") }

    const val MIN_LEAGUE = 8
    const val MIN_MARKET = 5
    const val MIN_GROUP = 10
    const val MIN_TEAM = 4
    const val MIN_BAND = 8
    const val MIN_RULE = 8

    /** Source name of a pick a Kryptonite switch kept off its list ("Over 1.5 (hidden)"); it is judged but never shown. */
    const val HIDDEN_SUFFIX = " (hidden)"

    const val STRONG_RATE = 65.0
    const val AVOID_RATE = 50.0

    fun verdict(t: Tally, minPicks: Int): Verdict = when {
        t.total < minPicks -> Verdict.FEW
        t.rate < AVOID_RATE -> Verdict.AVOID
        t.rate >= STRONG_RATE && t.adjusted >= 50.0 -> Verdict.STRONG
        else -> Verdict.OK
    }

    data class Row(val name: String, val tally: Tally, val verdict: Verdict)

    data class LeagueReport(
        val leagueId: String, val leagueName: String, val tally: Tally, val verdict: Verdict,
        val markets: List<Row>, val teams: List<Row>
    )

    /** One market across every league: its keys (Over 2.5, Draw...) and how each league did on it. */
    data class MarketReport(val group: String, val tally: Tally, val verdict: Verdict, val keys: List<Row>, val leagues: List<Row>)

    /** What the report knows about a written-down pick (looked up by the pick's id). */
    data class PickMeta(
        /** Kryptonite rule ids that flagged it; null when not recorded (older picks). */
        val krypto: List<String>?,
        /** Average confidence of the models that backed it, 0-100, if known. */
        val confidence: Double?,
        val backers: Int,
        val modelCount: Int,
        /** True for a pick a Kryptonite kept off the list (it is judged, but not counted as a list pick). */
        val hidden: Boolean,
        /** For Loki picks: the names of the Lokis that made it. */
        val tags: List<String> = emptyList()
    )

    /** One Kryptonite rule: picks that carried its flag against picks that did not (among picks whose flags were recorded). */
    data class RuleRow(
        val ruleId: String, val name: String, val flagged: Tally, val clean: Tally, val hidden: Tally, val advice: String,
        /** The same flagged-against-clean comparison inside each kind of pick (Over goals, Under goals, BTTS Yes...). */
        val byType: List<TypeSplit> = emptyList()
    )

    data class TypeSplit(val type: String, val flagged: Tally, val clean: Tally)

    /** The kind of pick, fine enough to tell overs from unders: "Over goals", "Under goals", "BTTS Yes", "Corners over"... */
    fun pickType(r: PredictionRecord): String {
        val k = r.key
        return when {
            k.length > 1 && k[0] == 'O' && k[1].isDigit() -> "Over goals"
            k.length > 1 && k[0] == 'U' && k[1].isDigit() -> "Under goals"
            k == "BTTS_Y" -> "BTTS Yes"
            k == "BTTS_N" -> "BTTS No"
            k.startsWith("C_O") -> "Corners over"
            k.startsWith("C_U") -> "Corners under"
            k.startsWith("TC_") -> if (k.getOrNull(5) == 'O') "Team corners over" else "Team corners under"
            else -> r.group
        }
    }

    data class SourceReport(
        val source: String,
        val total: Tally,
        val leagues: List<LeagueReport>,
        val markets: List<MarketReport>,
        val confidence: List<Row>,
        val agreement: List<Row>,
        /** Picks with at least one flag, with none, with no flag information, and picks a Kryptonite hid. */
        val flagged: Tally, val clean: Tally, val unknown: Tally, val hidden: Tally,
        val rules: List<RuleRow>,
        /** For Loki picks: how each Loki did (a pick made by two Lokis counts for both). */
        val tags: List<Row> = emptyList()
    )

    private fun ordered(rows: List<Row>): List<Row> =
        rows.sortedWith(compareBy<Row> { it.verdict == Verdict.FEW }.thenByDescending { it.tally.adjusted }.thenBy { it.name })

    private fun tally(rs: List<PredictionRecord>) = Tally(rs.count { it.correct }, rs.count { !it.correct })

    private fun inWindow(r: PredictionRecord, from: LocalDate?) =
        from == null || DateUtils.parse(r.date)?.let { !it.isBefore(from) } == true

    private fun inSource(src: String, r: PredictionRecord) = if (src == ALL_LISTS) r.source in LISTS else r.source == src

    fun confidenceBand(c: Double?): String = when {
        c == null -> "Unknown"
        c < 60 -> "Under 60%"
        c < 70 -> "60-69%"
        c < 80 -> "70-79%"
        c < 90 -> "80-89%"
        else -> "90% and over"
    }
    private val BAND_ORDER = listOf("Under 60%", "60-69%", "70-79%", "80-89%", "90% and over", "Unknown")

    /** A readable name for a pick key such as "O2.5" or "BTTS_Y"; unknown keys are shown as they are. */
    fun keyName(key: String): String = when {
        key == "1" -> "Home win"
        key == "2" -> "Away win"
        key == "X" -> "Draw"
        key == "1X" -> "Home or draw"
        key == "X2" -> "Draw or away"
        key == "12" -> "Home or away (no draw)"
        key == "BTTS_Y" -> "Both teams score"
        key == "BTTS_N" -> "Not both teams score"
        key.startsWith("C_") -> "Corners " + key.drop(2).replace("_", " ")
        key.startsWith("TC_H_") -> "Home team corners " + (if (key.getOrNull(5) == 'O') "over " else "under ") + key.drop(6)
        key.startsWith("TC_A_") -> "Away team corners " + (if (key.getOrNull(5) == 'O') "over " else "under ") + key.drop(6)
        key.length > 1 && key[0] == 'O' && key[1].isDigit() -> "Over " + key.drop(1)
        key.length > 1 && key[0] == 'U' && key[1].isDigit() -> "Under " + key.drop(1)
        else -> key.replace("_", " ")
    }

    /**
     * [records] are the settled picks (any source; only the three lists are used), [meta] what is known of each pick by its id,
     * [visible] the leagues shown. [sources] are the lists to report ([ALL_LISTS] joins the three).
     */
    fun build(
        records: Collection<PredictionRecord>,
        meta: Map<String, PickMeta>,
        visible: Set<String>,
        window: Window,
        today: LocalDate,
        sources: List<String> = LISTS + ALL_LISTS
    ): List<SourceReport> {
        val from = cutoff(window, today)
        return sources.map { src ->
            val all = records.filter { r -> inSource(src, r) && r.leagueId in visible && inWindow(r, from) }
            val hiddenRecs = all.filter { meta[it.id]?.hidden == true }
            val recs = all.filter { meta[it.id]?.hidden != true }

            val leagues = recs.groupBy { it.leagueId }.map { (id, rs) ->
                val t = tally(rs)
                val markets = ordered(rs.groupBy { it.group }.map { (g, x) -> tally(x).let { Row(g, it, verdict(it, MIN_MARKET)) } })
                val teamRecs = HashMap<String, MutableList<PredictionRecord>>()
                for (r in rs) { teamRecs.getOrPut(r.home) { ArrayList() }.add(r); teamRecs.getOrPut(r.away) { ArrayList() }.add(r) }
                val teams = ordered(teamRecs.map { (team, x) -> tally(x).let { Row(team, it, verdict(it, MIN_TEAM)) } })
                LeagueReport(id, rs.first().leagueName, t, verdict(t, MIN_LEAGUE), markets, teams)
            }.sortedWith(compareBy<LeagueReport> { it.verdict == Verdict.FEW }.thenByDescending { it.tally.adjusted }.thenBy { it.leagueName })

            val markets = recs.groupBy { it.group }.map { (g, rs) ->
                val t = tally(rs)
                MarketReport(
                    g, t, verdict(t, MIN_GROUP),
                    ordered(rs.groupBy { it.key }.map { (k, x) -> tally(x).let { Row(keyName(k), it, verdict(it, MIN_MARKET)) } }),
                    ordered(rs.groupBy { it.leagueName }.map { (n, x) -> tally(x).let { Row(n, it, verdict(it, MIN_MARKET)) } })
                )
            }.sortedWith(compareBy<MarketReport> { it.verdict == Verdict.FEW }.thenByDescending { it.tally.adjusted }.thenBy { it.group })

            val confidence = recs.groupBy { confidenceBand(meta[it.id]?.confidence) }
                .map { (b, x) -> tally(x).let { Row(b, it, verdict(it, MIN_BAND)) } }
                .sortedBy { BAND_ORDER.indexOf(it.name) }
            val agreement = recs.groupBy { r ->
                val m = meta[r.id]
                if (m == null || m.backers == 0) "Unknown" else if (m.backers == 1) "1 model only" else "${m.backers} of ${m.modelCount} models"
            }.map { (n, x) -> tally(x).let { Row(n, it, verdict(it, MIN_BAND)) } }.sortedByDescending { it.name }

            val known = recs.filter { meta[it.id]?.krypto != null }
            fun flagsOf(r: PredictionRecord) = meta.getValue(r.id).krypto.orEmpty()
            val flaggedRecs = known.filter { flagsOf(it).isNotEmpty() }
            val rules = Kryptonites.RULES.mapNotNull { rule ->
                val with = known.filter { rule.id in flagsOf(it) }
                val hid = hiddenRecs.filter { rule.id in meta[it.id]?.krypto.orEmpty() }
                if (with.isEmpty() && hid.isEmpty()) return@mapNotNull null
                val cleanRecs = known.filter { rule.id !in flagsOf(it) }
                val f = tally(with); val c = tally(cleanRecs); val h = tally(hid)
                val byType = with.groupBy { pickType(it) }
                    .filter { (_, x) -> x.size >= MIN_TYPE }
                    .map { (t, x) -> TypeSplit(t, tally(x), tally(cleanRecs.filter { pickType(it) == t })) }
                    .sortedByDescending { it.flagged.total }
                RuleRow(rule.id, rule.name, f, c, h, advice(f, c, h), byType)
            }.sortedByDescending { it.flagged.total + it.hidden.total }
            SourceReport(
                src, tally(recs), leagues, markets, confidence, agreement,
                tally(flaggedRecs), tally(known.filter { flagsOf(it).isEmpty() }),
                tally(recs.filter { meta[it.id]?.krypto == null }), tally(hiddenRecs), rules,
                recs.flatMap { r -> meta[r.id]?.tags.orEmpty().map { it to r } }.groupBy({ it.first }, { it.second })
                    .map { (n, x) -> tally(x).let { Row(n, it, verdict(it, MIN_BAND)) } }
                    .sortedWith(compareBy<Row> { it.verdict == Verdict.FEW }.thenByDescending { it.tally.adjusted }.thenBy { it.name })
            )
        }
    }

    private fun advice(f: Tally, c: Tally, h: Tally): String {
        val hid = if (h.total >= MIN_RULE) {
            if (h.rate < c.rate - 5 || h.rate < AVOID_RATE) " Hidden picks did ${Math.round(h.rate)}%: hiding is right."
            else " Hidden picks did ${Math.round(h.rate)}%: hiding is costing you good picks."
        } else ""
        val main = when {
            f.total < MIN_RULE -> "Too few flagged picks to judge yet."
            c.total < MIN_RULE -> "Too few clean picks to compare."
            // a gap only counts when it is big enough AND unlikely to be luck for this many picks
            c.rate - f.rate >= MIN_GAP_POINTS && gapZ(c, f) >= CLEAR_GAP_Z -> "Flagged picks do worse. Push to bottom or Hide fits."
            f.rate - c.rate >= MIN_GAP_POINTS && gapZ(f, c) >= CLEAR_GAP_Z -> "Flagged picks do better. The warning is not hurting."
            Math.abs(f.rate - c.rate) >= MIN_GAP_POINTS ->
                "The gap (${Math.round(f.rate)}% flagged, ${Math.round(c.rate)}% clean) could still be luck with ${f.total} flagged picks. Warn only is enough until there are more."
            else -> "No clear difference. Warn only is enough."
        }
        return main + hid
    }

    // ---- models -----------------------------------------------------------------------------

    val MODELS = listOf("Brymo", "Poisson", "Stats")
    val MODEL_ROWS = MODELS + "Consensus"

    data class ModelCell(val model: String, val tally: Tally)
    /** One league or market: each model's tally there, and the best single model (enough picks, highest score). */
    data class ModelLine(val name: String, val cells: List<ModelCell>, val best: String?)
    data class ModelReport(val overall: List<ModelCell>, val best: String?, val leagues: List<ModelLine>, val markets: List<ModelLine>)

    private fun bestOf(cells: List<ModelCell>, min: Int): String? =
        cells.filter { it.model in MODELS && it.tally.total >= min }.maxByOrNull { it.tally.adjusted }?.model

    /**
     * How each model did on the picks it showed on its card (ticked or settled automatically), and the consensus for
     * comparison, per league and per market.
     */
    fun models(records: Collection<PredictionRecord>, visible: Set<String>, window: Window, today: LocalDate): ModelReport {
        val from = cutoff(window, today)
        val recs = records.filter { it.source in MODEL_ROWS && it.leagueId in visible && inWindow(it, from) }
        fun cells(rs: List<PredictionRecord>) = MODEL_ROWS.map { m -> ModelCell(m, tally(rs.filter { it.source == m })) }
        fun lines(by: (PredictionRecord) -> String, min: Int) =
            recs.groupBy(by).map { (n, rs) -> cells(rs).let { ModelLine(n, it, bestOf(it, min)) } }
                .filter { l -> l.cells.any { it.tally.total > 0 } }.sortedBy { it.name }
        val overall = cells(recs)
        return ModelReport(overall, bestOf(overall, MIN_GROUP), lines({ it.leagueName }, MIN_LEAGUE), lines({ it.group }, MIN_GROUP))
    }
}
