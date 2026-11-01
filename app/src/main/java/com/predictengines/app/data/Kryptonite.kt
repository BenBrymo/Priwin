package com.predictengines.app.data

/** What a Kryptonite does to a flagged fixture or pick: just warn, sink to the bottom of its week, or disappear. */
enum class KryptoMode(val label: String) {
    WARN("Warn only"),
    BOTTOM("Push to bottom"),
    HIDE("Hide")
}

/** A warning raised by one Kryptonite for one team. */
data class KryptoFlag(val ruleId: String, val team: String, val text: String, val playerId: String = "")

/** The description of one Kryptonite, shown on the Data screen. */
data class KryptoRule(val id: String, val name: String, val description: String)

/**
 * Kryptonites: rules that watch a team's results and flag the team (and every fixture and pick it is part of)
 * until the rule's clearing condition is met. Add a rule by giving it an id in [RULES] and a branch in [evaluate].
 */
object Kryptonites {

    const val STREAK_BREAK = "streak_break"
    const val EARLY_KICKOFF = "early_kickoff"
    const val LOSING_BROKEN = "losing_broken"
    const val GOAL_DROUGHT = "goal_drought"
    const val SHORT_REST = "short_rest"
    const val FEW_GAMES = "few_games"
    const val DRAW_MAGNET = "draw_magnet"
    const val DEFENSIVE_COLLAPSE = "defensive_collapse"
    const val LONG_BREAK = "long_break"
    const val RED_CARD = "red_card"
    const val SCORING_SPREE = "scoring_spree"
    const val SPREE_COOLING = "spree_cooling"
    const val CLEAN_SHEET_RUN = "clean_sheet_run"
    const val CLEAN_SHEET_BROKEN = "clean_sheet_broken"
    const val FORM_SPLIT = "form_split"
    const val MODELS_DISAGREE = "models_disagree"
    const val KEY_PLAYER = "key_player"

    /** A run needs [RUN_GAMES] games to count; once it ended, the follow-up flag clears after [RUN_BACK] in a row again. */
    const val RUN_GAMES = 3
    const val RUN_BACK = 2
    const val SPREE_GOALS = 3
    /** Home/away split: games per venue looked at, the least needed at each venue, and the points-per-game gap. */
    const val SPLIT_GAMES = 5
    const val SPLIT_MIN = 3
    const val SPLIT_GAP = 1.5

    /** Draws in the last [DRAW_WINDOW] games; goals conceded in each of the last [COLLAPSE_GAMES]; days without a game. */
    const val DRAW_WINDOW = 6
    const val DRAW_MIN = 4
    const val COLLAPSE_GAMES = 3
    const val COLLAPSE_GOALS = 2
    const val LONG_BREAK_DAYS = 14

    /** Games in a row without a goal that make a drought; days of rest below which a team is flagged; results needed. */
    const val DROUGHT_GAMES = 3
    const val MIN_REST_DAYS = 3
    const val MIN_RESULTS = 5

    /** Losses in a row needed before a win counts as a surprise. The flag clears after [CLEAR_STREAK] wins in a row. */
    const val TRIGGER_LOSSES = 4

    /** Kickoffs from 11:00 to 12:30 (both included), in minutes after midnight. */
    const val EARLY_FROM = 11 * 60
    const val EARLY_TO = 12 * 60 + 30

    /** Wins in a row needed before a defeat counts, and wins in a row needed to clear the flag again. */
    const val TRIGGER_STREAK = 4
    const val CLEAR_STREAK = 3

    val RULES = listOf(
        KryptoRule(
            STREAK_BREAK,
            "Winning streak broken",
            "A team that won $TRIGGER_STREAK or more games in a row and then loses is flagged. A draw does not flag it. " +
                "The flag stays until the team wins $CLEAR_STREAK in a row again."
        ),
        KryptoRule(
            LOSING_BROKEN,
            "Losing streak broken",
            "A team that lost $TRIGGER_LOSSES or more games in a row and then wins is flagged. A draw does not flag it. " +
                "The flag stays until the team wins $CLEAR_STREAK more in a row."
        ),
        KryptoRule(
            GOAL_DROUGHT,
            "Goal drought",
            "A team that failed to score in each of its last $DROUGHT_GAMES games is flagged. It clears as soon as the team scores."
        ),
        KryptoRule(
            SHORT_REST,
            "Short rest",
            "A team whose previous game was fewer than $MIN_REST_DAYS days before the fixture is flagged."
        ),
        KryptoRule(
            FEW_GAMES,
            "Few games played",
            "A team with fewer than $MIN_RESULTS results in the matches file is flagged, because its predictions rest on little data."
        ),
        KryptoRule(
            DRAW_MAGNET,
            "Draw magnet",
            "A team with $DRAW_MIN or more draws in its last $DRAW_WINDOW games is flagged."
        ),
        KryptoRule(
            DEFENSIVE_COLLAPSE,
            "Defensive collapse",
            "A team that conceded $COLLAPSE_GOALS or more goals in each of its last $COLLAPSE_GAMES games is flagged. " +
                "It clears as soon as the team concedes fewer."
        ),
        KryptoRule(
            LONG_BREAK,
            "Long break",
            "A team whose previous game was $LONG_BREAK_DAYS or more days before the fixture is flagged."
        ),
        KryptoRule(
            RED_CARD,
            "Red card last game",
            "A team that had a player sent off in its last game is flagged. Needs the red-card columns in the matches file."
        ),
        KryptoRule(
            SCORING_SPREE,
            "Scoring spree",
            "A team that scored $SPREE_GOALS or more goals in each of its last $RUN_GAMES games is flagged. " +
                "It clears the first time the team scores fewer."
        ),
        KryptoRule(
            SPREE_COOLING,
            "Scoring spree cooling",
            "After a scoring spree of $RUN_GAMES or more games ends, the team is flagged because the spree may be over. " +
                "The flag stays until the team scores $SPREE_GOALS or more in $RUN_BACK games in a row again."
        ),
        KryptoRule(
            CLEAN_SHEET_RUN,
            "Clean-sheet run",
            "A team that kept a clean sheet in each of its last $RUN_GAMES games is flagged. " +
                "It clears the first time the team concedes."
        ),
        KryptoRule(
            CLEAN_SHEET_BROKEN,
            "Clean-sheet run broken",
            "After a run of $RUN_GAMES or more clean sheets ends, the team is flagged. " +
                "The flag stays until the team keeps $RUN_BACK clean sheets in a row again."
        ),
        KryptoRule(
            FORM_SPLIT,
            "Home/away form split",
            "A team whose points per game at home and away (last $SPLIT_GAMES games at each, at least $SPLIT_MIN) differ by " +
                "$SPLIT_GAP or more is flagged when it plays at its weaker venue."
        ),
        KryptoRule(
            MODELS_DISAGREE,
            "Models disagree",
            "A fixture whose best consensus pick has at least one model against it is flagged, because the models do not agree."
        ),
        KryptoRule(
            KEY_PLAYER,
            "Injured or unavailable key player",
            "A team with a key player who is injured, suspended or doubtful (set on the Players tab) is flagged. " +
                "A red-card ban counts the team's games and ends by itself; an injury ends on its return date or when you set the player to Available."
        ),
        KryptoRule(
            EARLY_KICKOFF,
            "Early kickoff",
            "A game that kicks off from 11:00 to 12:30 (both included) is flagged. Games with no kickoff time are never flagged."
        )
    )

    private val TIME = Regex("""(\d{1,2})[:.h](\d{2})""")

    /** The kickoff time as minutes after midnight, or null when the text holds no time. */
    fun kickoffMinutes(time: String): Int? {
        val m = TIME.find(time) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    private fun earlyKickoff(time: String): KryptoFlag? {
        val mins = kickoffMinutes(time) ?: return null
        if (mins !in EARLY_FROM..EARLY_TO) return null
        val label = "%02d:%02d".format(mins / 60, mins % 60)
        return KryptoFlag(EARLY_KICKOFF, "", "Early kickoff $label")
    }

    /** Flags for [team] from the matches played before the game being looked at (any order). */
    fun evaluate(
        team: String, matches: List<Match>, fixtureDate: String = "", atHome: Boolean? = null,
        players: List<KeyPlayer> = emptyList()
    ): List<KryptoFlag> {
        val out = ArrayList<KryptoFlag>()
        keyPlayers(team, matches, fixtureDate, players).forEach { out.add(it) }
        scoringSpree(team, matches)?.let { out.add(it) }
        cleanSheetRun(team, matches)?.let { out.add(it) }
        runBroken(
            team, matches, SPREE_COOLING,
            hit = { m -> (if (m.homeTeam == team) m.homeGoals else m.awayGoals) >= SPREE_GOALS },
            text = { n, need -> "$team's $n-game scoring spree ended · needs $need more ${if (need == 1) "game" else "games"} of $SPREE_GOALS+ goals in a row" }
        )?.let { out.add(it) }
        runBroken(
            team, matches, CLEAN_SHEET_BROKEN,
            hit = { m -> (if (m.homeTeam == team) m.awayGoals else m.homeGoals) == 0 },
            text = { n, need -> "$team's $n-game clean-sheet run ended · needs $need more ${if (need == 1) "clean sheet" else "clean sheets"} in a row" }
        )?.let { out.add(it) }
        if (atHome != null) formSplit(team, matches, atHome)?.let { out.add(it) }
        goalDrought(team, matches)?.let { out.add(it) }
        drawMagnet(team, matches)?.let { out.add(it) }
        defensiveCollapse(team, matches)?.let { out.add(it) }
        longBreak(team, matches, fixtureDate)?.let { out.add(it) }
        redCard(team, matches, players)?.let { out.add(it) }
        shortRest(team, matches, fixtureDate)?.let { out.add(it) }
        fewGames(team, matches)?.let { out.add(it) }
        streakBreak(team, matches)?.let { out.add(it) }
        losingBroken(team, matches)?.let { out.add(it) }
        return out
    }

    /** The flags of a fixture: those of its home and away team. */
    fun forFixture(
        home: String, away: String, matches: List<Match>, time: String = "", date: String = "",
        consensus: ConsensusResult? = null, players: List<KeyPlayer> = emptyList()
    ): List<KryptoFlag> =
        evaluate(home, matches, date, true, players) + evaluate(away, matches, date, false, players) +
            listOfNotNull(earlyKickoff(time), modelsDisagree(consensus))

    private fun modelsDisagree(c: ConsensusResult?): KryptoFlag? {
        val best = c?.picks?.firstOrNull() ?: return null
        if (best.against.isEmpty()) return null
        return KryptoFlag(MODELS_DISAGREE, "", "Models disagree · ${best.against.joinToString(", ")} against ${best.label}")
    }

    private fun gamesOf(team: String, matches: List<Match>) =
        matches.filter { it.homeTeam == team || it.awayTeam == team }.sortedBy { it.date }

    private fun goalDrought(team: String, matches: List<Match>): KryptoFlag? {
        val last = gamesOf(team, matches).takeLast(DROUGHT_GAMES)
        if (last.size < DROUGHT_GAMES) return null
        val dry = last.all { (if (it.homeTeam == team) it.homeGoals else it.awayGoals) == 0 }
        return if (dry) KryptoFlag(GOAL_DROUGHT, team, "$team has not scored in its last $DROUGHT_GAMES games") else null
    }

    private fun scoringSpree(team: String, matches: List<Match>): KryptoFlag? {
        val last = gamesOf(team, matches).takeLast(RUN_GAMES)
        if (last.size < RUN_GAMES) return null
        val hot = last.all { (if (it.homeTeam == team) it.homeGoals else it.awayGoals) >= SPREE_GOALS }
        return if (hot) KryptoFlag(SCORING_SPREE, team, "$team scored $SPREE_GOALS+ goals in each of its last $RUN_GAMES games") else null
    }

    private fun cleanSheetRun(team: String, matches: List<Match>): KryptoFlag? {
        val last = gamesOf(team, matches).takeLast(RUN_GAMES)
        if (last.size < RUN_GAMES) return null
        val tight = last.all { (if (it.homeTeam == team) it.awayGoals else it.homeGoals) == 0 }
        return if (tight) KryptoFlag(CLEAN_SHEET_RUN, team, "$team kept a clean sheet in each of its last $RUN_GAMES games") else null
    }

    /**
     * Follow-up of a run: after a run of [RUN_GAMES]+ games where [hit] was true ended, the team is flagged until
     * [hit] is true [RUN_BACK] games in a row again.
     */
    private fun runBroken(
        team: String, matches: List<Match>, ruleId: String,
        hit: (Match) -> Boolean, text: (Int, Int) -> String
    ): KryptoFlag? {
        var run = 0
        var flagged = false
        var broken = 0
        var since = 0
        for (m in gamesOf(team, matches)) {
            val h = hit(m)
            if (!flagged) {
                if (h) run++ else {
                    if (run >= RUN_GAMES) { flagged = true; broken = run; since = 0 }
                    run = 0
                }
            } else if (h) {
                since++
                if (since >= RUN_BACK) { flagged = false; run = since }
            } else since = 0
        }
        return if (flagged) KryptoFlag(ruleId, team, text(broken, RUN_BACK - since)) else null
    }

    private fun formSplit(team: String, matches: List<Match>, atHome: Boolean): KryptoFlag? {
        fun ppg(g: List<Match>, home: Boolean): Double =
            g.fold(0) { acc, m ->
                val mine = if (home) m.homeGoals else m.awayGoals
                val theirs = if (home) m.awayGoals else m.homeGoals
                acc + (if (mine > theirs) 3 else if (mine == theirs) 1 else 0)
            } / g.size.toDouble()
        val all = gamesOf(team, matches)
        val h = all.filter { it.homeTeam == team }.takeLast(SPLIT_GAMES)
        val a = all.filter { it.awayTeam == team }.takeLast(SPLIT_GAMES)
        if (h.size < SPLIT_MIN || a.size < SPLIT_MIN) return null
        val hp = ppg(h, true)
        val ap = ppg(a, false)
        val gap = hp - ap
        val weakerIsHome = gap < 0
        if (kotlin.math.abs(gap) < SPLIT_GAP || weakerIsHome != atHome) return null
        val where = if (atHome) "at home" else "away"
        return KryptoFlag(
            FORM_SPLIT, team,
            "$team averages %.1f pts/game at home but %.1f away · playing $where".format(hp, ap)
        )
    }

    private fun drawMagnet(team: String, matches: List<Match>): KryptoFlag? {
        val last = gamesOf(team, matches).takeLast(DRAW_WINDOW)
        if (last.size < DRAW_WINDOW) return null
        val draws = last.count { it.homeGoals == it.awayGoals }
        return if (draws >= DRAW_MIN) KryptoFlag(DRAW_MAGNET, team, "$team drew $draws of its last $DRAW_WINDOW games") else null
    }

    private fun defensiveCollapse(team: String, matches: List<Match>): KryptoFlag? {
        val last = gamesOf(team, matches).takeLast(COLLAPSE_GAMES)
        if (last.size < COLLAPSE_GAMES) return null
        val leaky = last.all { (if (it.homeTeam == team) it.awayGoals else it.homeGoals) >= COLLAPSE_GOALS }
        return if (leaky) KryptoFlag(DEFENSIVE_COLLAPSE, team, "$team conceded $COLLAPSE_GOALS+ goals in each of its last $COLLAPSE_GAMES games") else null
    }

    private fun longBreak(team: String, matches: List<Match>, fixtureDate: String): KryptoFlag? {
        val fx = DateUtils.parse(fixtureDate) ?: return null
        val prev = gamesOf(team, matches).mapNotNull { DateUtils.parse(it.date) }.filter { it.isBefore(fx) }.maxOrNull()
            ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(prev, fx).toInt()
        return if (days >= LONG_BREAK_DAYS) KryptoFlag(LONG_BREAK, team, "$team has not played for $days days") else null
    }

    private fun keyPlayers(team: String, matches: List<Match>, fixtureDate: String, players: List<KeyPlayer>): List<KryptoFlag> =
        players.filter { it.team == team }.mapNotNull { p ->
            val label = "${p.name} (${p.position.short})"
            when (KeyPlayers.statusFor(p, fixtureDate, matches)) {
                PlayerStatus.AVAILABLE -> null
                PlayerStatus.DOUBTFUL -> KryptoFlag(KEY_PLAYER, team, "$team: $label is doubtful, may be unavailable", p.id)
                PlayerStatus.INJURED -> KryptoFlag(KEY_PLAYER, team, "$team: $label is injured", p.id)
                PlayerStatus.SUSPENDED -> {
                    val left = KeyPlayers.gamesLeft(p, matches)
                    val tail = if (left > 0) " ($left more ${if (left == 1) "game" else "games"})" else ""
                    KryptoFlag(KEY_PLAYER, team, "$team: $label is suspended$tail", p.id)
                }
            }
        }

    private fun redCard(team: String, matches: List<Match>, players: List<KeyPlayer>): KryptoFlag? {
        val last = gamesOf(team, matches).lastOrNull() ?: return null
        // The sent-off player is a key player already suspended for it: the key-player flag says it, no second warning.
        if (players.any { it.team == team && it.status == PlayerStatus.SUSPENDED && it.banFrom == last.date }) return null
        val x = last.extras ?: return null
        val reds = (if (last.homeTeam == team) x.homeReds else x.awayReds) ?: return null
        return if (reds > 0) KryptoFlag(RED_CARD, team, "$team had a player sent off in its last game") else null
    }

    private fun shortRest(team: String, matches: List<Match>, fixtureDate: String): KryptoFlag? {
        val fx = DateUtils.parse(fixtureDate) ?: return null
        val prev = gamesOf(team, matches).mapNotNull { DateUtils.parse(it.date) }.filter { it.isBefore(fx) }.maxOrNull()
            ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(prev, fx).toInt()
        if (days >= MIN_REST_DAYS) return null
        return KryptoFlag(SHORT_REST, team, "$team played $days ${if (days == 1) "day" else "days"} before this game")
    }

    private fun fewGames(team: String, matches: List<Match>): KryptoFlag? {
        val n = gamesOf(team, matches).size
        if (n >= MIN_RESULTS) return null
        return KryptoFlag(FEW_GAMES, team, "$team has only $n ${if (n == 1) "result" else "results"} on file")
    }

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

    private fun losingBroken(team: String, matches: List<Match>): KryptoFlag? {
        val games = matches.filter { it.homeTeam == team || it.awayTeam == team }.sortedBy { it.date }
        var losses = 0
        var flagged = false
        var broken = 0
        var since = 0
        for (m in games) {
            val mine = if (m.homeTeam == team) m.homeGoals else m.awayGoals
            val theirs = if (m.homeTeam == team) m.awayGoals else m.homeGoals
            val won = mine > theirs
            val lost = mine < theirs
            if (!flagged) {
                when {
                    lost -> losses++
                    won -> {
                        if (losses >= TRIGGER_LOSSES) { flagged = true; broken = losses; since = 0 }
                        losses = 0
                    }
                    else -> losses = 0 // a draw ends the losing run but raises no flag
                }
            } else {
                if (won) {
                    since++
                    if (since >= CLEAR_STREAK) { flagged = false; losses = 0 }
                } else {
                    since = 0
                    if (lost) { flagged = false; losses = 1 } // lost again: the run of form is back to normal
                }
            }
        }
        if (!flagged) return null
        val need = CLEAR_STREAK - since
        return KryptoFlag(
            LOSING_BROKEN, team,
            "$team won after a $broken-game losing streak · needs $need more ${if (need == 1) "win" else "wins"} in a row"
        )
    }

    /** The strongest effect among a fixture's flags, given the mode chosen for each rule (default: warn only). */
    fun effect(flags: List<KryptoFlag>, modes: Map<String, KryptoMode>): KryptoMode =
        flags.map { modes[it.ruleId] ?: KryptoMode.WARN }.maxByOrNull { it.ordinal } ?: KryptoMode.WARN
}
