package com.predictengines.app.engine

import com.predictengines.app.data.Match
import com.predictengines.app.data.StatDelta
import com.predictengines.app.data.StatRow
import com.predictengines.app.data.TeamMatcher
import com.predictengines.app.engine.StatsEngine.Kind
import com.predictengines.app.engine.StatsEngine.Metric
import com.predictengines.app.engine.StatsEngine.Scope
import kotlin.math.roundToInt

/**
 * Keeps the stats file current from played games.
 *
 *  - [scoreDeltas]: once a fixture's score is known, every market the score settles (wins, draws, losses,
 *    win to nil, win or draw, BTTS, goal lines) gets +1 game for each team, and +1 value when it happened.
 *  - [tickDeltas]: corners, cards and first-to-score can't be read from a score, so the user's tick
 *    on a Stats pick is the evidence. Correct = the picked event happened, wrong = its opposite did.
 *
 * Only the latest-season row of a team's market is changed, and teams missing from a market are skipped
 * (a stats list is a "top N" table: absent means unknown, not zero).
 */
object StatsUpdater {

    fun rowKey(r: StatRow) = "${r.market.lowercase().trim()}|${r.season.trim()}|${r.team.lowercase().trim()}"

    /** Pick keys whose result the score alone decides. */
    fun isScoreDecided(key: String): Boolean = when {
        key.startsWith("C_") || key.startsWith("K_") || key.startsWith("FTS") -> false
        else -> true
    }

    // ---- choosing rows ---------------------------------------------------------------------

    private fun better(a: StatRow, b: StatRow) = a.season > b.season || (a.season == b.season && a.games > b.games)

    /** The row for (scope, metric) among one team's rows: latest season, then the larger sample. */
    private fun best(rows: List<StatRow>, scope: Scope, metric: Metric): StatRow? {
        var out: StatRow? = null
        for (r in rows) {
            val (s, m) = StatsEngine.classify(r.market) ?: continue
            if (s != scope || m != metric) continue
            if (out == null || better(r, out)) out = r
        }
        return out
    }

    /** Same fallback order the model uses: the preferred venue, then Total, then the other venue. */
    private fun forMetric(rows: List<StatRow>, metric: Metric, preferred: Scope): StatRow? {
        best(rows, preferred, metric)?.let { return it }
        best(rows, Scope.TOTAL, metric)?.let { return it }
        val other = when (preferred) { Scope.HOME -> Scope.AWAY; Scope.AWAY -> Scope.HOME; Scope.TOTAL -> return null }
        return best(rows, other, metric)
    }

    private fun teamRows(team: String, all: List<StatRow>, names: List<String>): List<StatRow> {
        val resolved = TeamMatcher.resolve(team, names) ?: return emptyList()
        return all.filter { it.team == resolved }
    }

    // ---- from a score ----------------------------------------------------------------------

    fun scoreDeltas(home: String, away: String, hg: Int, ag: Int, rows: List<StatRow>): List<StatDelta> {
        if (rows.isEmpty() || home == away) return emptyList()
        val names = rows.map { it.team }.distinct()
        val out = ArrayList<StatDelta>()
        val seen = HashSet<String>()
        for (isHome in listOf(true, false)) {
            val mine = teamRows(if (isHome) home else away, rows, names)
            val resolved = mine.firstOrNull()?.team ?: continue
            // every (scope, metric) the team has, grouped, each using its best row
            val groups = HashMap<Pair<Scope, Metric>, StatRow>()
            for (r in mine) {
                val key = StatsEngine.classify(r.market) ?: continue
                val cur = groups[key]
                if (cur == null || better(r, cur)) groups[key] = r
            }
            for ((key, row) in groups) {
                val (scope, metric) = key
                if (scope == Scope.HOME && !isHome) continue
                if (scope == Scope.AWAY && isHome) continue
                val happened = scoreEvent(metric, isHome, hg, ag) ?: continue
                val rk = rowKey(row)
                if (!seen.add(rk)) continue
                out.add(StatDelta(rk, if (happened) 1 else 0, 1, "score"))
            }
            if (resolved.isEmpty()) continue
        }
        return out
    }

    /** True / false if the score settles [metric] for that team, null if it can't. */
    internal fun scoreEvent(metric: Metric, isHome: Boolean, hg: Int, ag: Int): Boolean? {
        val gf = if (isHome) hg else ag
        val ga = if (isHome) ag else hg
        return when (metric.kind) {
            Kind.WIN -> gf > ga
            Kind.DRAW -> gf == ga
            Kind.LOSS -> gf < ga
            Kind.WIN_TO_NIL -> gf > ga && ga == 0
            Kind.WIN_OR_DRAW -> gf >= ga
            Kind.BTTS -> hg > 0 && ag > 0
            Kind.GOALS -> if (metric.over) hg + ag > metric.line else hg + ag < metric.line
            Kind.FIRST_TO_SCORE, Kind.CORNERS, Kind.CARDS -> null
        }
    }

    // ---- from a tick -----------------------------------------------------------------------

    /**
     * Stats changes for a ticked Stats pick that a score can't settle. [score] is optional and only used to
     * tell whether anyone scored for first-to-score picks. Returns empty for picks the score decides.
     */
    fun tickDeltas(
        key: String, correct: Boolean, home: String, away: String, score: Match?, rows: List<StatRow>
    ): List<StatDelta> {
        if (isScoreDecided(key) || rows.isEmpty() || home == away) return emptyList()
        val names = rows.map { it.team }.distinct()
        val h = teamRows(home, rows, names)
        val a = teamRows(away, rows, names)
        val tag = "tick:$key"
        val out = ArrayList<StatDelta>()
        val seen = HashSet<String>()
        fun add(row: StatRow?, happened: Boolean) {
            if (row == null) return
            val rk = rowKey(row)
            if (seen.add(rk)) out.add(StatDelta(rk, if (happened) 1 else 0, 1, tag))
        }

        when {
            key.startsWith("C_") || key.startsWith("K_") -> {
                val kind = if (key.startsWith("C_")) Kind.CORNERS else Kind.CARDS
                val over = key.drop(2).firstOrNull() == 'O'
                val line = key.drop(3).toDoubleOrNull() ?: return emptyList()
                // the picked event happened if the pick was correct; otherwise its opposite did
                val pickedHappened = correct
                for ((rowsOf, venue) in listOf(h to Scope.HOME, a to Scope.AWAY)) {
                    add(forMetric(rowsOf, Metric(kind, line, over), venue), pickedHappened)
                    add(forMetric(rowsOf, Metric(kind, line, !over), venue), !pickedHappened)
                }
            }
            key == "FTS_H" || key == "FTS_A" -> {
                val homePicked = key == "FTS_H"
                val fts = Metric(Kind.FIRST_TO_SCORE)
                val someoneScored = score == null || score.homeGoals + score.awayGoals > 0
                val homeFirst = if (homePicked) correct else !correct
                add(forMetric(h, fts, Scope.HOME), homeFirst && someoneScored)
                add(forMetric(a, fts, Scope.AWAY), !homeFirst && someoneScored)
            }
        }
        return out
    }

    // ---- applying --------------------------------------------------------------------------

    fun percentOf(value: Double, games: Int): Double? =
        if (games > 0) (value / games * 1000).roundToInt() / 10.0 else null

    /**
     * Returns the rows that change when [deltas] are added (or, with [sign] = -1, taken back out).
     * Value and games never go below zero, and value never exceeds games.
     */
    fun apply(rows: List<StatRow>, deltas: List<StatDelta>, sign: Int = 1): List<StatRow> {
        if (deltas.isEmpty()) return emptyList()
        val byKey = HashMap<String, StatRow>()
        for (r in rows) byKey[rowKey(r)] = r
        val changed = LinkedHashMap<String, StatRow>()
        for (d in deltas) {
            val cur = changed[d.rowKey] ?: byKey[d.rowKey] ?: continue
            val games = (cur.games + sign * d.dg).coerceAtLeast(0)
            val value = (cur.value + sign * d.dv).coerceIn(0.0, games.toDouble())
            changed[d.rowKey] = cur.copy(value = value, games = games, percent = percentOf(value, games) ?: cur.percent)
        }
        return changed.values.toList()
    }
}
