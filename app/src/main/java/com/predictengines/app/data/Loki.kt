package com.predictengines.app.data

/** A number a Loki uses, which the user can change on the Data screen. */
class LokiParam(val label: String, val default: Int, val min: Int, val max: Int)

/** One Loki: a rule that turns a Kryptonite flag (or a run of results) into a pick. */
class LokiRule(
    val id: String,
    val name: String,
    /** The starting strength of its picks (0-100): fixed, so the Loki report shows what it is really worth. */
    val strength: Double,
    val param: LokiParam? = null,
    val describe: () -> String
)

/** The user's choices: Lokis switched off, and changed numbers (Loki id to value). */
class LokiConfig(val off: Set<String> = emptySet(), val params: Map<String, Int> = emptyMap()) {
    fun on(id: String) = id !in off
    fun param(id: String): Int =
        params[id] ?: Lokis.RULES.firstOrNull { it.id == id }?.param?.default ?: 0
}

/** One pick made by one or more Lokis for one game. */
data class LokiPick(val key: String, val label: String, val group: String, val lokis: List<String>, val strength: Double)

/**
 * Lokis take advantage of what the Kryptonites notice: a team that just lost its winning streak may be shaken, so its
 * next opponent is picked to win or draw, and so on. Each Loki is one rule in [RULES]; a game's picks come from
 * [forFixture], which reads the game's Kryptonite flags and the teams' earlier results.
 */
object Lokis {

    const val WIN_STREAK = "loki_win_streak"
    const val DROUGHT = "loki_drought"
    const val CS_BROKEN = "loki_cs_broken"
    const val COLLAPSE = "loki_collapse"
    const val CONGESTION = "loki_congestion"
    const val STACKED = "loki_stacked"
    const val BOUNCE = "loki_bounce"
    const val FADE = "loki_fade"

    @Volatile var config: LokiConfig = LokiConfig()

    private fun n(id: String) = config.param(id)

    val RULES = listOf(
        LokiRule(WIN_STREAK, "Winning streak broken", 62.0) {
            "A team lost after a winning streak (the Winning streak broken Kryptonite). Picks its opponent to win or draw: the loss may have shaken the team."
        },
        LokiRule(DROUGHT, "Goal drought", 58.0) {
            "A team has not scored for several games (the Goal drought Kryptonite). Picks its opponent to win to nil."
        },
        LokiRule(CS_BROKEN, "Clean-sheet run broken", 56.0) {
            "A team's clean-sheet run has just ended (the Clean-sheet run broken Kryptonite). Picks both teams to score."
        },
        LokiRule(COLLAPSE, "Defensive collapse", 60.0) {
            "A team has been conceding 2+ goals a game (the Defensive collapse Kryptonite). Picks Over 2.5 goals and both teams to score."
        },
        LokiRule(CONGESTION, "Fixture congestion", 56.0) {
            "A team is playing its 3rd game in 8 days (the Fixture congestion Kryptonite). Picks its opponent to win or draw."
        },
        LokiRule(STACKED, "Stacked flags", 64.0) {
            "A team carries two or more flags, and more than its opponent. Picks the opponent to win."
        },
        LokiRule(BOUNCE, "Bounce back", 62.0, LokiParam("Wins in a row after the losing streak", 2, 1, 4)) {
            "A team on a losing streak (the Losing streak broken length) won ${n(BOUNCE)} in a row. Picks the team to win or draw in its next game: the recovery looks real."
        },
        LokiRule(FADE, "Fade the recovery", 58.0, LokiParam("Wins in a row after the losing streak", 5, 3, 10)) {
            "A team on a losing streak then won ${n(FADE)} in a row. Picks its opponent to win or draw in the next game: the run may be over."
        }
    )

    // ---- helpers ---------------------------------------------------------------------------

    private fun results(team: String, matches: List<Match>, date: String): List<Char> =
        matches.filter { (it.homeTeam == team || it.awayTeam == team) && (date.isBlank() || (it.date.isNotBlank() && it.date < date)) }
            .sortedBy { it.date }
            .map {
                val mine = if (it.homeTeam == team) it.homeGoals else it.awayGoals
                val theirs = if (it.homeTeam == team) it.awayGoals else it.homeGoals
                if (mine > theirs) 'W' else if (mine < theirs) 'L' else 'D'
            }

    /** True when the team's last [wins] games are wins, straight after a run of losing-streak length or more losses. */
    private fun recoveryAfter(team: String, matches: List<Match>, date: String, wins: Int): Boolean {
        val r = results(team, matches, date)
        val need = Kryptonites.config.param(Kryptonites.LOSING_BROKEN, "trigger")
        if (r.size < wins + need) return false
        if (r.takeLast(wins).any { it != 'W' }) return false
        var losses = 0
        var i = r.size - wins - 1
        while (i >= 0 && r[i] == 'L') { losses++; i-- }
        return losses >= need
    }

    private fun flagCount(flags: List<KryptoFlag>, team: String) = flags.filter { it.team == team }.map { it.ruleId }.distinct().size

    private class Raw(val key: String, val label: String, val group: String, val loki: LokiRule)

    /** The Loki picks of one game. [matches] are the results known so far, [flags] the game's Kryptonite flags. */
    fun forFixture(home: String, away: String, date: String, matches: List<Match>, flags: List<KryptoFlag>): List<LokiPick> {
        val raw = ArrayList<Raw>()
        fun rule(id: String) = RULES.first { it.id == id }
        fun flagged(ruleId: String, team: String) = flags.any { it.ruleId == ruleId && it.team == team }
        fun doubleChance(of: String, lk: LokiRule) {
            if (of == home) raw.add(Raw("1X", "$home win or draw", "Double chance", lk))
            else raw.add(Raw("X2", "$away win or draw", "Double chance", lk))
        }
        val sides = listOf(home to away, away to home) // (team, its opponent)

        if (config.on(WIN_STREAK)) for ((t, o) in sides) if (flagged(Kryptonites.STREAK_BREAK, t)) doubleChance(o, rule(WIN_STREAK))
        if (config.on(DROUGHT)) for ((t, o) in sides) if (flagged(Kryptonites.GOAL_DROUGHT, t)) {
            raw.add(Raw(if (o == home) "WTN_H" else "WTN_A", "$o win to nil", "Win to nil", rule(DROUGHT)))
        }
        if (config.on(CS_BROKEN) && sides.any { flagged(Kryptonites.CLEAN_SHEET_BROKEN, it.first) }) {
            raw.add(Raw("BTTS_Y", "BTTS Yes", "BTTS", rule(CS_BROKEN)))
        }
        if (config.on(COLLAPSE) && sides.any { flagged(Kryptonites.DEFENSIVE_COLLAPSE, it.first) }) {
            raw.add(Raw("O2.5", "Over 2.5 goals", "Goals", rule(COLLAPSE)))
            raw.add(Raw("BTTS_Y", "BTTS Yes", "BTTS", rule(COLLAPSE)))
        }
        if (config.on(CONGESTION)) for ((t, o) in sides) if (flagged(Kryptonites.CONGESTION, t)) doubleChance(o, rule(CONGESTION))
        if (config.on(STACKED)) {
            val hc = flagCount(flags, home); val ac = flagCount(flags, away)
            if (hc >= 2 && hc > ac) raw.add(Raw("2", "$away win", "1X2", rule(STACKED)))
            if (ac >= 2 && ac > hc) raw.add(Raw("1", "$home win", "1X2", rule(STACKED)))
        }
        if (config.on(BOUNCE)) for ((t, _) in sides) if (recoveryAfter(t, matches, date, n(BOUNCE))) {
            if (t == home) raw.add(Raw("1X", "$home win or draw", "Double chance", rule(BOUNCE)))
            else raw.add(Raw("X2", "$away win or draw", "Double chance", rule(BOUNCE)))
        }
        if (config.on(FADE)) for ((t, o) in sides) if (recoveryAfter(t, matches, date, n(FADE))) doubleChance(o, rule(FADE))

        // The same pick from several Lokis is one pick, a little stronger for each extra Loki.
        return raw.groupBy { it.key }.map { (key, rs) ->
            val lokis = rs.map { it.loki }.distinctBy { it.id }
            LokiPick(
                key, rs.first().label, rs.first().group, lokis.map { it.name },
                (lokis.maxOf { it.strength } + 3.0 * (lokis.size - 1)).coerceAtMost(95.0)
            )
        }.sortedByDescending { it.strength }
    }
}
