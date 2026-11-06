package com.predictengines.app.data

/**
 * A check that Kryptonite flags are right. It does two things for every upcoming fixture:
 *  1. recounts the simple form rules (Goal drought, Scoring spree, Clean-sheet run, Defensive collapse) with separate,
 *     plain code straight from the scores, and says so when the engine and the recount disagree;
 *  2. calculates every flag again from the matches file as it is now, and says so when the flags the app is showing
 *     are different (a stale saved flag).
 * It also lists the games behind every flag, so a wrong flag can be traced to a missing or wrong game in the file.
 */
object KryptoAudit {

    class FixtureRef(
        val date: String, val shownHome: String, val shownAway: String,
        /** The teams' names as the matches file spells them; null when a name could not be matched. */
        val home: String?, val away: String?,
        /** The flags the app is showing on this fixture; null when not compared. */
        val stored: List<KryptoFlag>?
    )

    data class Evidence(val team: String, val rule: String, val fixture: String, val games: List<String>)
    data class Issue(val fixture: String, val team: String, val rule: String, val problem: String, val games: List<String>)
    data class Report(
        val leagueId: String, val leagueName: String, val fixtures: Int, val flags: Int, val newestGame: String,
        val issues: List<Issue>, val evidence: List<Evidence>, val unmatched: List<String>
    )

    private fun before(all: List<Match>, date: String): List<Match> =
        if (date.isBlank()) all else all.filter { it.date.isBlank() || it.date < date }

    private fun ruleName(id: String) = Kryptonites.RULES.firstOrNull { it.id == id }?.name ?: id

    /** The team's last [n] games before [date], newest first, as "2026-10-18 · Lorient 1:0 Monaco". */
    fun gameLines(team: String, matches: List<Match>, date: String, n: Int = 6): List<String> =
        before(matches, date).filter { it.homeTeam == team || it.awayTeam == team }
            .sortedBy { it.date }.takeLast(n).reversed()
            .map { "${it.date.ifBlank { "no date" }} · ${it.homeTeam} ${it.homeGoals}:${it.awayGoals} ${it.awayTeam}" }

    /** What each simple rule should say about [team], counted separately from the engine: rule id -> flagged? */
    fun recount(team: String, matches: List<Match>): Map<String, Boolean> {
        val mine = matches.filter { it.homeTeam == team || it.awayTeam == team }.sortedBy { it.date }
        fun scored(m: Match) = if (m.homeTeam == team) m.homeGoals else m.awayGoals
        fun conceded(m: Match) = if (m.homeTeam == team) m.awayGoals else m.homeGoals
        fun last(n: Int): List<Match>? = mine.takeLast(n).takeIf { it.size == n && n > 0 }
        val cfg = Kryptonites.config
        val out = LinkedHashMap<String, Boolean>()
        out[Kryptonites.GOAL_DROUGHT] = last(cfg.param(Kryptonites.GOAL_DROUGHT, "games"))?.all { scored(it) == 0 } ?: false
        cfg.param(Kryptonites.SCORING_SPREE, "games").let { n ->
            val g = cfg.param(Kryptonites.SCORING_SPREE, "goals")
            out[Kryptonites.SCORING_SPREE] = last(n)?.all { scored(it) >= g } ?: false
        }
        out[Kryptonites.CLEAN_SHEET_RUN] = last(cfg.param(Kryptonites.CLEAN_SHEET_RUN, "games"))?.all { conceded(it) == 0 } ?: false
        cfg.param(Kryptonites.DEFENSIVE_COLLAPSE, "games").let { n ->
            val g = cfg.param(Kryptonites.DEFENSIVE_COLLAPSE, "goals")
            out[Kryptonites.DEFENSIVE_COLLAPSE] = last(n)?.all { conceded(it) >= g } ?: false
        }
        return out
    }

    fun check(
        leagueId: String, leagueName: String, fixtures: List<FixtureRef>, matches: List<Match>,
        players: List<KeyPlayer> = emptyList(), news: List<TeamNews> = emptyList()
    ): Report {
        val issues = ArrayList<Issue>()
        val evidence = LinkedHashMap<String, Evidence>()
        val unmatched = LinkedHashSet<String>()
        var flagCount = 0
        var checked = 0
        for (fx in fixtures) {
            val home = fx.home; val away = fx.away
            if (home == null || away == null) {
                if (home == null) unmatched.add(fx.shownHome)
                if (away == null) unmatched.add(fx.shownAway)
                continue
            }
            checked++
            val label = "${fx.shownHome} v ${fx.shownAway} (${fx.date})"
            val b = before(matches, fx.date)
            for ((team, atHome) in listOf(home to true, away to false)) {
                val fresh = Kryptonites.evaluate(team, b, fx.date, atHome, players, news)
                flagCount += fresh.size
                val games = gameLines(team, matches, fx.date)
                fresh.forEach { f -> evidence.getOrPut("$team|${f.ruleId}") { Evidence(team, ruleName(f.ruleId), label, games) } }
                for ((id, expected) in recount(team, b)) {
                    val has = fresh.any { it.ruleId == id }
                    if (has != expected) issues.add(
                        Issue(
                            label, team, ruleName(id),
                            if (has) "Flagged, but a plain recount of the scores says it should not be."
                            else "Not flagged, but a plain recount of the scores says it should be.",
                            games
                        )
                    )
                }
                fx.stored?.let { st ->
                    val shown = st.filter { it.team == team }.map { it.ruleId }.toSet()
                    val now = fresh.map { it.ruleId }.toSet()
                    (shown - now).forEach { id ->
                        issues.add(Issue(label, team, ruleName(id), "Shown on the fixture, but calculating again from the matches file does not flag it. The saved flag is stale.", games))
                    }
                    (now - shown).forEach { id ->
                        issues.add(Issue(label, team, ruleName(id), "Calculating again from the matches file flags it, but the fixture does not show it. The saved flags are stale.", games))
                    }
                }
            }
        }
        val newest = matches.map { it.date }.filter { it.isNotBlank() }.maxOrNull().orEmpty()
        return Report(leagueId, leagueName, checked, flagCount, newest, issues, evidence.values.toList(), unmatched.toList())
    }
}
