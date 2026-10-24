package com.predictengines.app.engine

import com.predictengines.app.data.Outcome
import com.predictengines.app.data.Prediction
import com.predictengines.app.data.StatPick
import com.predictengines.app.data.StatRow
import com.predictengines.app.data.StatSignal
import com.predictengines.app.data.StatsResult
import com.predictengines.app.data.TeamMatcher
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The "Stats" model. It reads the stats file the user loaded (statschecker-style rows such as
 * "Away Wins · Lazio · 4/4 · 100%") and checks the fixture against it.
 *
 * Every market is one team's record: `stat_value / games` is how often it happened in that team's
 * games. No suffix / "Total" = all games, "Home" / "Away" = only that team's home / away games
 * (as prefix or suffix: "Home Wins", "Over 2.5 Home"). Over/Under, BTTS, corners and cards are about
 * the whole match, not just the team's own numbers.
 *
 * How it works
 *  1. Market names are understood by [classify]. Anything unknown is ignored.
 *  2. A team's rate is shrunk toward a typical baseline, so "1 of 1 = 100%" counts for far less than
 *     "9 of 9 = 100%". The result is converted to an edge over the baseline (0 = ordinary, 1 = perfect).
 *  3. 1X2 evidence: home = home team's Home Wins + away team's Away Lost (+ half-weight Wins to 0 and
 *     Win or Draw). Draw gets the Draws stats (+ half of Win or Draw). Away mirrors home.
 *  4. Every other market averages the edges of whichever teams have that stat.
 *
 * Lists like statschecker's are usually "top N" tables, so a team missing from a market means
 * "no information", never "bad". Evidence/confidence is a strength score, not a probability.
 */
object StatsEngine {

    enum class Scope { HOME, AWAY, TOTAL }

    enum class Kind { WIN, DRAW, LOSS, WIN_TO_NIL, WIN_OR_DRAW, BTTS, FIRST_TO_SCORE, GOALS, CORNERS, CARDS }

    /** [line] and [over] only matter for GOALS / CORNERS / CARDS. */
    data class Metric(val kind: Kind, val line: Double = 0.0, val over: Boolean = true)

    private val GOAL_LINES = listOf(1.5, 2.5, 3.5)
    private val CORNER_LINES = listOf(8.5, 9.5, 10.5, 11.5)
    private val CARD_LINES = listOf(3.5, 4.5, 5.5, 6.5)

    private const val SHRINK_GAMES = 4.0
    private const val ASSUMED_GAMES_WHEN_MISSING = 3
    // Deliberately low: if a team has ANY usable stat the model should still say something.
    private const val MIN_TOP_EVIDENCE = 0.05
    private const val MIN_MARGIN = 0.02
    private const val MIN_MARKET_EDGE = 0.03
    private const val ONE_TEAM_FACTOR = 0.6
    private const val HALF = 0.5
    /** A stat from the other venue (a Home stat for an away game) still counts, but much less. */
    private const val OFF_VENUE_FACTOR = 0.4

    // ---- market names ----------------------------------------------------------------------

    private val LINE_MARKET = Regex("^(corners|cards)?(over|under)(\\d+\\.\\d+)$")
    private val WIN_OR_DRAW = Regex("^(win|wins|w)or(draw|draws|d)$")
    // "Win to 0", "Win to Nil", "Win to Nill" (the statschecker spelling), "Wins to Zero"
    private val WIN_TO_NIL = Regex("^(win|wins)(to)?(0|nil|nill|zero)$")

    /** Understands every market in the statschecker list; null if it isn't one of them. */
    fun classify(market: String): Pair<Scope, Metric>? {
        val words = market.lowercase().replace(Regex("[^a-z0-9. ]"), " ").split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val rest = words.toMutableList()
        var scope = Scope.TOTAL
        when {
            rest.first() == "home" -> { scope = Scope.HOME; rest.removeAt(0) }
            rest.first() == "away" -> { scope = Scope.AWAY; rest.removeAt(0) }
            rest.size > 1 && rest.last() == "home" -> { scope = Scope.HOME; rest.removeAt(rest.lastIndex) }
            rest.size > 1 && rest.last() == "away" -> { scope = Scope.AWAY; rest.removeAt(rest.lastIndex) }
        }
        rest.removeAll { it == "total" }
        val c = rest.joinToString("")
        if (c.isEmpty()) return null

        val metric: Metric = run {
            LINE_MARKET.matchEntire(c)?.let { m ->
                val line = m.groupValues[3].toDouble()
                val over = m.groupValues[2] == "over"
                return@run when (m.groupValues[1]) {
                    "corners" -> if (line in CORNER_LINES) Metric(Kind.CORNERS, line, over) else null
                    "cards" -> if (line in CARD_LINES) Metric(Kind.CARDS, line, over) else null
                    else -> if (line in GOAL_LINES) Metric(Kind.GOALS, line, over) else null
                }
            }
            when {
                c == "firsttoscore" || c == "fts" -> Metric(Kind.FIRST_TO_SCORE)
                WIN_OR_DRAW.matches(c) -> Metric(Kind.WIN_OR_DRAW)
                WIN_TO_NIL.matches(c) -> Metric(Kind.WIN_TO_NIL)
                c == "btts" || c == "bothteamstoscore" -> Metric(Kind.BTTS)
                c == "win" || c == "wins" -> Metric(Kind.WIN)
                c == "draw" || c == "draws" -> Metric(Kind.DRAW)
                c == "lost" || c == "loss" || c == "losses" -> Metric(Kind.LOSS)
                else -> null
            }
        } ?: return null
        return scope to metric
    }

    /** Typical long-run frequency of each metric: the "nothing special" baseline. */
    private fun baseline(m: Metric, scope: Scope): Double = when (m.kind) {
        Kind.WIN -> when (scope) { Scope.HOME -> 0.45; Scope.AWAY -> 0.28; Scope.TOTAL -> 0.37 }
        Kind.LOSS -> when (scope) { Scope.HOME -> 0.28; Scope.AWAY -> 0.45; Scope.TOTAL -> 0.37 }
        Kind.DRAW -> 0.26
        Kind.WIN_TO_NIL -> when (scope) { Scope.HOME -> 0.27; Scope.AWAY -> 0.13; Scope.TOTAL -> 0.20 }
        Kind.WIN_OR_DRAW -> when (scope) { Scope.HOME -> 0.71; Scope.AWAY -> 0.54; Scope.TOTAL -> 0.63 }
        Kind.BTTS -> 0.52
        Kind.FIRST_TO_SCORE -> when (scope) { Scope.HOME -> 0.56; Scope.AWAY -> 0.42; Scope.TOTAL -> 0.49 }
        Kind.GOALS -> overBaseline(m, mapOf(1.5 to 0.76, 2.5 to 0.52, 3.5 to 0.30))
        Kind.CORNERS -> overBaseline(m, mapOf(8.5 to 0.73, 9.5 to 0.61, 10.5 to 0.49, 11.5 to 0.37))
        Kind.CARDS -> overBaseline(m, mapOf(3.5 to 0.64, 4.5 to 0.46, 5.5 to 0.30, 6.5 to 0.17))
    }

    private fun overBaseline(m: Metric, overRates: Map<Double, Double>): Double {
        val over = overRates[m.line] ?: 0.5
        return if (m.over) over else 1.0 - over
    }

    /** Which markets in a file the model can use, and which it ignores. */
    fun marketSupport(rows: List<StatRow>): Pair<List<String>, List<String>> {
        val markets = rows.map { it.market }.distinct().sorted()
        val (used, ignored) = markets.partition { classify(it) != null }
        return used to ignored
    }

    // ---- per-team stats --------------------------------------------------------------------

    private class Stat(val row: StatRow, val scope: Scope, val metric: Metric, val offVenue: Boolean = false) {
        val base = baseline(metric, scope)
        val rate: Double = run {
            val r = if (row.games > 0) row.value / row.games else (row.percent ?: 0.0) / 100.0
            r.coerceIn(0.0, 1.0)
        }
        val shrunk: Double = run {
            val g = (if (row.games > 0) row.games else ASSUMED_GAMES_WHEN_MISSING).toDouble()
            val s = (rate * g + base * SHRINK_GAMES) / (g + SHRINK_GAMES)
            if (offVenue) base + (s - base) * OFF_VENUE_FACTOR else s
        }
        /** 0 = at baseline (or worse), 1 = perfect. */
        val edge: Double = max(0.0, (shrunk - base) / (1.0 - base))
    }

    /** All of one team's usable stats: latest season wins, then the larger sample. */
    private class TeamStats(rows: List<StatRow>) {
        private val byKey = HashMap<Pair<Scope, Metric>, Stat>()

        init {
            for (row in rows) {
                val (scope, metric) = classify(row.market) ?: continue
                val key = scope to metric
                val old = byKey[key]?.row
                if (old == null || row.season > old.season || (row.season == old.season && row.games > old.games)) {
                    byKey[key] = Stat(row, scope, metric)
                }
            }
        }

        /**
         * Preferred scope first (e.g. HOME for the home team), then the Total market, then, as a weak
         * last resort, the other venue's stat, so a team with any data still contributes something.
         */
        fun get(metric: Metric, preferred: Scope): Stat? {
            byKey[preferred to metric]?.let { return it }
            byKey[Scope.TOTAL to metric]?.let { return it }
            val other = when (preferred) { Scope.HOME -> Scope.AWAY; Scope.AWAY -> Scope.HOME; Scope.TOTAL -> return null }
            return byKey[other to metric]?.let { Stat(it.row, it.scope, it.metric, offVenue = true) }
        }
    }

    // ---- selections beyond 1X2 -------------------------------------------------------------

    private class Selection(
        val key: String,
        val group: String,
        val label: String,
        val homeMetric: Metric?,
        val awayMetric: Metric?,
        /** Selections with the same non-null tag contradict each other; only the stronger one is kept. */
        val conflictTag: String? = null
    )

    private fun fmt(line: Double) = String.format(java.util.Locale.US, "%.1f", line)

    private fun selections(home: String, away: String): List<Selection> {
        val out = ArrayList<Selection>()
        for (line in GOAL_LINES) {
            out.add(Selection("O${fmt(line)}", "Goals", "Over ${fmt(line)} goals",
                Metric(Kind.GOALS, line, true), Metric(Kind.GOALS, line, true), "goals${fmt(line)}"))
            out.add(Selection("U${fmt(line)}", "Goals", "Under ${fmt(line)} goals",
                Metric(Kind.GOALS, line, false), Metric(Kind.GOALS, line, false), "goals${fmt(line)}"))
        }
        out.add(Selection("BTTS_Y", "Goals", "BTTS Yes", Metric(Kind.BTTS), Metric(Kind.BTTS)))
        out.add(Selection("1X", "Double chance", "Double chance 1X", Metric(Kind.WIN_OR_DRAW), null))
        out.add(Selection("X2", "Double chance", "Double chance X2", null, Metric(Kind.WIN_OR_DRAW)))
        for ((prefix, group, lines, kind) in listOf(
            Quad("C", "Corners", CORNER_LINES, Kind.CORNERS),
            Quad("K", "Cards", CARD_LINES, Kind.CARDS)
        )) {
            for (line in lines) {
                out.add(Selection("${prefix}_O${fmt(line)}", group, "$group Over ${fmt(line)}",
                    Metric(kind, line, true), Metric(kind, line, true), "$prefix${fmt(line)}"))
                out.add(Selection("${prefix}_U${fmt(line)}", group, "$group Under ${fmt(line)}",
                    Metric(kind, line, false), Metric(kind, line, false), "$prefix${fmt(line)}"))
            }
        }
        out.add(Selection("FTS_H", "First to score", "$home score first", Metric(Kind.FIRST_TO_SCORE), null, "fts"))
        out.add(Selection("FTS_A", "First to score", "$away score first", null, Metric(Kind.FIRST_TO_SCORE), "fts"))
        out.add(Selection("WTN_H", "Win to nil", "$home win to nil", Metric(Kind.WIN_TO_NIL), null))
        out.add(Selection("WTN_A", "Win to nil", "$away win to nil", null, Metric(Kind.WIN_TO_NIL)))
        return out
    }

    private data class Quad(val prefix: String, val group: String, val lines: List<Double>, val kind: Kind)

    // ---- prediction ------------------------------------------------------------------------

    fun predict(home: String, away: String, rows: List<StatRow>): StatsResult? {
        if (rows.isEmpty()) return null
        val names = rows.map { it.team }.distinct()
        val homeName = TeamMatcher.resolve(home, names)
        val awayName = TeamMatcher.resolve(away, names)
        val h = homeName?.let { n -> TeamStats(rows.filter { it.team == n }) }
        val a = awayName?.let { n -> TeamStats(rows.filter { it.team == n }) }

        val signals = ArrayList<StatSignal>()
        val seen = HashSet<String>()
        fun record(team: String, s: Stat, supports: String) {
            if (!seen.add("$team|${s.row.market}|$supports")) return
            val note = if (s.offVenue) " (other-venue stat, reduced weight)" else ""
            signals.add(StatSignal(team, s.row.market, s.row.season, s.row.value, s.row.games, s.rate * 100, supports + note))
        }

        // ---- 1X2 ----
        var homeEv = 0.0
        var drawEv = 0.0
        var awayEv = 0.0
        fun use(team: String, s: Stat?, supports: String, weight: Double, apply: (Double) -> Unit) {
            if (s == null || s.edge <= 0) return
            apply(s.edge * weight)
            record(team, s, supports)
        }
        val m = { k: Kind -> Metric(k) }
        use(home, h?.get(m(Kind.WIN), Scope.HOME), "$home win", 1.0) { homeEv += it }
        use(away, a?.get(m(Kind.LOSS), Scope.AWAY), "$home win", 1.0) { homeEv += it }
        use(home, h?.get(m(Kind.WIN_TO_NIL), Scope.HOME), "$home win", HALF) { homeEv += it }
        use(away, a?.get(m(Kind.WIN), Scope.AWAY), "$away win", 1.0) { awayEv += it }
        use(home, h?.get(m(Kind.LOSS), Scope.HOME), "$away win", 1.0) { awayEv += it }
        use(away, a?.get(m(Kind.WIN_TO_NIL), Scope.AWAY), "$away win", HALF) { awayEv += it }
        use(home, h?.get(m(Kind.DRAW), Scope.HOME), "a draw", 1.0) { drawEv += it }
        use(away, a?.get(m(Kind.DRAW), Scope.AWAY), "a draw", 1.0) { drawEv += it }
        // Win-or-Draw: "doesn't lose", so it backs the team's win and the draw, at half weight each.
        use(home, h?.get(m(Kind.WIN_OR_DRAW), Scope.HOME), "$home win or draw", HALF) { homeEv += it; drawEv += it }
        use(away, a?.get(m(Kind.WIN_OR_DRAW), Scope.AWAY), "$away win or draw", HALF) { awayEv += it; drawEv += it }
        homeEv = min(1.0, homeEv)
        drawEv = min(1.0, drawEv)
        awayEv = min(1.0, awayEv)

        val ranked = listOf(Outcome.HOME to homeEv, Outcome.DRAW to drawEv, Outcome.AWAY to awayEv)
            .sortedByDescending { it.second }
        val top = ranked[0]
        val margin = top.second - ranked[1].second
        var outcome: Outcome? = null
        var pick: Prediction? = null
        if (top.second >= MIN_TOP_EVIDENCE && margin >= MIN_MARGIN) {
            outcome = top.first
            val label = when (top.first) {
                Outcome.HOME -> "$home Win"
                Outcome.AWAY -> "$away Win"
                Outcome.DRAW -> "Draw Likely"
            }
            pick = Prediction(label, (100.0 * (0.5 * top.second + 0.5 * margin)).roundToInt().toDouble())
        }

        // ---- everything else ----
        class Candidate(val sel: Selection, val confidence: Double, val edge: Double, val used: List<Pair<String, Stat>>)
        val candidates = ArrayList<Candidate>()
        for (sel in selections(home, away)) {
            val hs = sel.homeMetric?.let { h?.get(it, Scope.HOME) }
            val a2 = sel.awayMetric?.let { a?.get(it, Scope.AWAY) }
            val found = listOfNotNull(hs?.let { home to it }, a2?.let { away to it })
            if (found.isEmpty()) continue
            val base = found.map { it.second.base }.average()
            val avg = found.map { it.second.shrunk }.average()
            // One team's stat is weaker evidence than two agreeing teams.
            val sides = (if (sel.homeMetric != null) 1 else 0) + (if (sel.awayMetric != null) 1 else 0)
            val factor = if (found.size == 2 || sides == 1) 1.0 else ONE_TEAM_FACTOR
            val adjusted = base + (avg - base) * factor
            candidates.add(Candidate(sel, adjusted * 100, adjusted - base, found))
        }
        // Normally only markets with a real edge are kept. If none qualify but some stat exists,
        // fall back to the single best one so a team with any data always gets a prediction.
        var usable = candidates.filter { it.edge >= MIN_MARKET_EDGE }
        if (usable.isEmpty()) usable = candidates.filter { it.edge > 0 }.sortedByDescending { it.edge }.take(1)
        // Contradicting pairs (Over vs Under on the same line, home vs away scoring first): keep the stronger.
        val dropped = HashSet<String>()
        usable.filter { it.sel.conflictTag != null }.groupBy { it.sel.conflictTag }.values.forEach { grp ->
            val best = grp.maxByOrNull { it.edge }!!
            grp.filter { it !== best }.forEach { dropped.add(it.sel.key) }
        }
        val kept = usable.filter { it.sel.key !in dropped }.sortedByDescending { it.confidence }
        val markets = kept.map { StatPick(it.sel.key, it.sel.group, it.sel.label, it.confidence.roundToInt().toDouble()) }
        for (c in kept) for ((team, stat) in c.used) record(team, stat, c.sel.label)

        return StatsResult(
            homeMatched = homeName,
            awayMatched = awayName,
            homeEvidence = homeEv,
            drawEvidence = drawEv,
            awayEvidence = awayEv,
            outcome = outcome,
            pick = pick,
            markets = markets,
            signals = signals
        )
    }
}
