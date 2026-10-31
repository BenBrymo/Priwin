package com.predictengines.app.data

/** What a Kryptonite does to a flagged fixture or pick: just warn, sink to the bottom of its week, or disappear. */
enum class KryptoMode(val label: String) {
    WARN("Warn only"),
    BOTTOM("Push to bottom"),
    HIDE("Hide")
}

/** A warning raised by one Kryptonite for one team. */
data class KryptoFlag(val ruleId: String, val team: String, val text: String)

/** The description of one Kryptonite, shown on the Data screen. */
data class KryptoRule(val id: String, val name: String, val description: String)

/**
 * Kryptonites: rules that watch a team's results and flag the team (and every fixture and pick it is part of)
 * until the rule's clearing condition is met. Add a rule by giving it an id in [RULES] and a branch in [evaluate].
 */
object Kryptonites {

    const val STREAK_BREAK = "streak_break"

    /** Wins in a row needed before a defeat counts, and wins in a row needed to clear the flag again. */
    const val TRIGGER_STREAK = 4
    const val CLEAR_STREAK = 3

    val RULES = listOf(
        KryptoRule(
            STREAK_BREAK,
            "Winning streak broken",
            "A team that won $TRIGGER_STREAK or more games in a row and then loses is flagged. A draw does not flag it. " +
                "The flag stays until the team wins $CLEAR_STREAK in a row again."
        )
    )

    /** Flags for [team] from the matches played before the game being looked at (any order). */
    fun evaluate(team: String, matches: List<Match>): List<KryptoFlag> {
        val out = ArrayList<KryptoFlag>()
        streakBreak(team, matches)?.let { out.add(it) }
        return out
    }

    /** The flags of a fixture: those of its home and away team. */
    fun forFixture(home: String, away: String, matches: List<Match>): List<KryptoFlag> =
        evaluate(home, matches) + evaluate(away, matches)

    private fun streakBreak(team: String, matches: List<Match>): KryptoFlag? {
        val games = matches.filter { it.homeTeam == team || it.awayTeam == team }.sortedBy { it.date }
        var wins = 0          // wins in a row while not flagged
        var flagged = false
        var broken = 0        // the length of the streak that was broken
        var since = 0         // wins in a row since the flag was raised
        for (m in games) {
            val mine = if (m.homeTeam == team) m.homeGoals else m.awayGoals
            val theirs = if (m.homeTeam == team) m.awayGoals else m.homeGoals
            val won = mine > theirs
            val lost = mine < theirs
            if (!flagged) {
                when {
                    won -> wins++
                    lost -> {
                        if (wins >= TRIGGER_STREAK) { flagged = true; broken = wins; since = 0 }
                        wins = 0
                    }
                    else -> wins = 0 // a draw ends the streak but raises no flag
                }
            } else {
                if (won) {
                    since++
                    if (since >= CLEAR_STREAK) { flagged = false; wins = since }
                } else {
                    since = 0
                }
            }
        }
        if (!flagged) return null
        val need = CLEAR_STREAK - since
        return KryptoFlag(
            STREAK_BREAK, team,
            "$team lost after a $broken-game winning streak · needs $need more ${if (need == 1) "win" else "wins"} in a row"
        )
    }

    /** The strongest effect among a fixture's flags, given the mode chosen for each rule (default: warn only). */
    fun effect(flags: List<KryptoFlag>, modes: Map<String, KryptoMode>): KryptoMode =
        flags.map { modes[it.ruleId] ?: KryptoMode.WARN }.maxByOrNull { it.ordinal } ?: KryptoMode.WARN
}
