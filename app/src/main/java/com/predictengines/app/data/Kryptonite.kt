package com.predictengines.app.data

/** What a Kryptonite does to a flagged fixture or pick: just warn, sink to the bottom of its week, or disappear. */
enum class KryptoMode(val label: String) {
    WARN("Warn only"),
    BOTTOM("Push to bottom"),
    HIDE("Hide")
}

/** A warning raised by one Kryptonite for one team. */
data class KryptoFlag(val ruleId: String, val team: String, val text: String, val playerId: String = "")

/** One number a rule uses (a streak length, a number of days...), which the user can change. */
class KryptoParam(
    val key: String, val label: String, val default: Int, val min: Int, val max: Int, val step: Int = 1,
    /** True when the number is a time of day in minutes after midnight (shown as hh:mm). */
    val isTime: Boolean = false
)

/** One Kryptonite, shown on the Data screen: its group, its numbers, the pick types it applies to, and a description. */
class KryptoRule(
    val id: String,
    val name: String,
    val group: String,
    val params: List<KryptoParam>,
    /** Pick types ("1X2", "Goals"...) its flag counts for, in the Top 20, Handicap and Over 1.5 lists. */
    val markets: Set<String>,
    val describe: () -> String
)

/** The user's choices: changed numbers ("rule.key" to value) and changed pick-type lists (rule id to types). */
class KryptoConfig(val params: Map<String, Int> = emptyMap(), val markets: Map<String, Set<String>> = emptyMap()) {
    fun param(ruleId: String, key: String): Int =
        params["$ruleId.$key"] ?: Kryptonites.RULES.firstOrNull { it.id == ruleId }?.params?.firstOrNull { it.key == key }?.default ?: 0
    fun marketsOf(ruleId: String): Set<String> =
        markets[ruleId] ?: Kryptonites.RULES.firstOrNull { it.id == ruleId }?.markets ?: emptySet()
    /** Changes when a number changes (flags depend on the numbers, not on the pick types). */
    fun paramStamp(): String = params.toSortedMap().toString().hashCode().toString()
}

/**
 * Kryptonites: rules that watch a team's results and flag the team (and every fixture and pick it is part of)
 * until the rule's clearing condition is met. Add a rule by giving it an id in [RULES] and a branch in [evaluate].
 * The numbers each rule uses come from [config] (the user's choices, or the defaults listed in [RULES]).
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
    const val CONGESTION = "congestion"
    const val TEAM_NEWS = "team_news"

    /** The user's choices, set when the app starts and whenever they change. */
    @Volatile var config: KryptoConfig = KryptoConfig()

    private fun p(rule: String, key: String): Int = config.param(rule, key)

    val GROUPS = listOf("Form", "Schedule", "Players", "Models")

    /** Pick types a Kryptonite can apply to. */
    val MARKETS = listOf(
        "1X2", "Double chance", "Goals", "Over/Under", "BTTS", "Corners", "Team corners", "Cards",
        "First to score", "Win to nil", "Handicap / other"
    )
    private val ALL_MARKETS = MARKETS.toSet()
    private val RESULT_MARKETS = setOf("1X2", "Double chance", "Handicap / other", "Win to nil", "First to score")
    private val GOAL_MARKETS = setOf("Goals", "Over/Under", "BTTS")

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    val RULES = listOf(
        KryptoRule(
            STREAK_BREAK, "Winning streak broken", "Form",
            listOf(KryptoParam("trigger", "Wins in a row before a loss counts", 4, 1, 15), KryptoParam("clear", "Wins in a row to clear it", 3, 1, 10)),
            RESULT_MARKETS
        ) {
            "A team that won ${p(STREAK_BREAK, "trigger")} or more games in a row and then loses is flagged. A draw does not flag it. " +
                "The flag stays until the team wins ${p(STREAK_BREAK, "clear")} in a row again."
        },
        KryptoRule(
            LOSING_BROKEN, "Losing streak broken", "Form",
            listOf(KryptoParam("trigger", "Losses in a row before a win counts", 4, 1, 15), KryptoParam("clear", "Wins in a row to clear it", 3, 1, 10)),
            RESULT_MARKETS
        ) {
            "A team that lost ${p(LOSING_BROKEN, "trigger")} or more games in a row and then wins is flagged. A draw does not flag it. " +
                "The flag stays until the team wins ${p(LOSING_BROKEN, "clear")} more in a row."
        },
        KryptoRule(
            GOAL_DROUGHT, "Goal drought", "Form",
            listOf(KryptoParam("games", "Games without a goal", 3, 1, 10)),
            GOAL_MARKETS + "First to score"
        ) { "A team that failed to score in each of its last ${p(GOAL_DROUGHT, "games")} games is flagged. It clears as soon as the team scores." },
        KryptoRule(
            DRAW_MAGNET, "Draw magnet", "Form",
            listOf(KryptoParam("draws", "Draws", 4, 1, 12), KryptoParam("window", "Out of the last games", 6, 2, 15)),
            setOf("1X2", "Double chance", "Handicap / other")
        ) { "A team with ${p(DRAW_MAGNET, "draws")} or more draws in its last ${p(DRAW_MAGNET, "window")} games is flagged." },
        KryptoRule(
            DEFENSIVE_COLLAPSE, "Defensive collapse", "Form",
            listOf(KryptoParam("games", "Games in a row", 3, 1, 10), KryptoParam("goals", "Goals conceded in each", 2, 1, 6)),
            GOAL_MARKETS + "Win to nil"
        ) {
            "A team that conceded ${p(DEFENSIVE_COLLAPSE, "goals")} or more goals in each of its last ${p(DEFENSIVE_COLLAPSE, "games")} games is flagged. " +
                "It clears as soon as the team concedes fewer."
        },
        KryptoRule(
            SCORING_SPREE, "Scoring spree", "Form",
            listOf(KryptoParam("games", "Games in a row", 3, 1, 10), KryptoParam("goals", "Goals scored in each", 3, 1, 8)),
            GOAL_MARKETS
        ) {
            "A team that scored ${p(SCORING_SPREE, "goals")} or more goals in each of its last ${p(SCORING_SPREE, "games")} games is flagged. " +
                "It clears the first time the team scores fewer."
        },
        KryptoRule(
            SPREE_COOLING, "Scoring spree cooling", "Form",
            listOf(
                KryptoParam("run", "Length of the spree that ended", 3, 1, 10), KryptoParam("goals", "Goals scored in each", 3, 1, 8),
                KryptoParam("back", "Games in a row to clear it", 2, 1, 10)
            ),
            GOAL_MARKETS
        ) {
            "After a scoring spree of ${p(SPREE_COOLING, "run")} or more games ends, the team is flagged because the spree may be over. " +
                "The flag stays until the team scores ${p(SPREE_COOLING, "goals")} or more in ${p(SPREE_COOLING, "back")} games in a row again."
        },
        KryptoRule(
            CLEAN_SHEET_RUN, "Clean-sheet run", "Form",
            listOf(KryptoParam("games", "Clean sheets in a row", 3, 1, 10)),
            GOAL_MARKETS + "Win to nil"
        ) {
            "A team that kept a clean sheet in each of its last ${p(CLEAN_SHEET_RUN, "games")} games is flagged. It clears the first time the team concedes."
        },
        KryptoRule(
            CLEAN_SHEET_BROKEN, "Clean-sheet run broken", "Form",
            listOf(KryptoParam("run", "Length of the run that ended", 3, 1, 10), KryptoParam("back", "Clean sheets in a row to clear it", 2, 1, 10)),
            GOAL_MARKETS + "Win to nil"
        ) {
            "After a run of ${p(CLEAN_SHEET_BROKEN, "run")} or more clean sheets ends, the team is flagged. " +
                "The flag stays until the team keeps ${p(CLEAN_SHEET_BROKEN, "back")} clean sheets in a row again."
        },
        KryptoRule(
            FORM_SPLIT, "Home/away form split", "Form",
            listOf(
                KryptoParam("games", "Games looked at per venue", 5, 3, 12), KryptoParam("min", "Least games needed at each venue", 3, 1, 10),
                KryptoParam("gap", "Gap in tenths of a point (15 = 1.5)", 15, 5, 30, step = 5)
            ),
            setOf("1X2", "Double chance", "Handicap / other", "Win to nil")
        ) {
            "A team whose points per game at home and away (last ${p(FORM_SPLIT, "games")} games at each, at least ${p(FORM_SPLIT, "min")}) differ by " +
                "${p(FORM_SPLIT, "gap") / 10.0} or more is flagged when it plays at its weaker venue."
        },
        KryptoRule(
            EARLY_KICKOFF, "Early kickoff", "Schedule",
            listOf(
                KryptoParam("from", "From", 11 * 60, 0, 23 * 60 + 30, step = 30, isTime = true),
                KryptoParam("to", "To (included)", 12 * 60 + 30, 0, 23 * 60 + 30, step = 30, isTime = true)
            ),
            ALL_MARKETS
        ) {
            "A game that kicks off from ${hhmm(p(EARLY_KICKOFF, "from"))} to ${hhmm(p(EARLY_KICKOFF, "to"))} (both included) is flagged. " +
                "Games with no kickoff time are never flagged."
        },
        KryptoRule(
            SHORT_REST, "Short rest", "Schedule",
            listOf(KryptoParam("days", "Fewer than this many days since the last game", 3, 1, 10)),
            ALL_MARKETS
        ) { "A team whose previous game was fewer than ${p(SHORT_REST, "days")} days before the fixture is flagged." },
        KryptoRule(
            CONGESTION, "Fixture congestion", "Schedule",
            listOf(KryptoParam("games", "Games (this one included)", 3, 2, 8), KryptoParam("days", "Within this many days", 8, 3, 21)),
            ALL_MARKETS
        ) {
            "A team playing ${p(CONGESTION, "games")} games within ${p(CONGESTION, "days")} days (this one included) is flagged. " +
                "It replaces Short rest for the same team, because it is the stronger warning."
        },
        KryptoRule(
            LONG_BREAK, "Long break", "Schedule",
            listOf(KryptoParam("days", "This many days or more since the last game", 14, 5, 60)),
            ALL_MARKETS
        ) { "A team whose previous game was ${p(LONG_BREAK, "days")} or more days before the fixture is flagged." },
        KryptoRule(
            FEW_GAMES, "Few games played", "Schedule",
            listOf(KryptoParam("results", "Fewer than this many results on file", 5, 1, 20)),
            ALL_MARKETS
        ) { "A team with fewer than ${p(FEW_GAMES, "results")} results in the matches file is flagged, because its predictions rest on little data." },
        KryptoRule(
            KEY_PLAYER, "Injured or unavailable key player", "Players", emptyList(), ALL_MARKETS
        ) {
            "A team with a key player who is injured, suspended or doubtful (set on the Players tab) is flagged. " +
                "A red-card ban counts the team's games and ends by itself; an injury ends on its return date or when you set the player to Available."
        },
        KryptoRule(
            TEAM_NEWS, "Team news", "Players", emptyList(), ALL_MARKETS
        ) {
            "News you note on the Players tab (manager change, club turmoil, travel or fatigue, pitch or weather, other) flags the team's fixtures " +
                "for the number of games or until the date you set. Defaults: manager change and club turmoil 2 games, the others 1 game."
        },
        KryptoRule(
            RED_CARD, "Red card last game", "Players", emptyList(), ALL_MARKETS
        ) { "A team that had a player sent off in its last game is flagged. Needs the red-card columns in the matches file." },
        KryptoRule(
            MODELS_DISAGREE, "Models disagree", "Models", emptyList(), ALL_MARKETS
        ) { "A fixture whose best consensus pick has at least one model against it is flagged, because the models do not agree." }
    )

    fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

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
        if (mins !in p(EARLY_KICKOFF, "from")..p(EARLY_KICKOFF, "to")) return null
        return KryptoFlag(EARLY_KICKOFF, "", "Early kickoff ${hhmm(mins)}")
    }

    /** Flags for [team] from the matches played before the game being looked at (any order). */
    fun evaluate(
        team: String, matches: List<Match>, fixtureDate: String = "", atHome: Boolean? = null,
        players: List<KeyPlayer> = emptyList(), news: List<TeamNews> = emptyList()
    ): List<KryptoFlag> {
        val out = ArrayList<KryptoFlag>()
        keyPlayers(team, matches, fixtureDate, players).forEach { out.add(it) }
        teamNews(team, matches, fixtureDate, news).forEach { out.add(it) }
        scoringSpree(team, matches)?.let { out.add(it) }
        cleanSheetRun(team, matches)?.let { out.add(it) }
        val sg = p(SPREE_COOLING, "goals")
        runBroken(
            team, matches, SPREE_COOLING, p(SPREE_COOLING, "run"), p(SPREE_COOLING, "back"),
            hit = { m -> (if (m.homeTeam == team) m.homeGoals else m.awayGoals) >= sg },
            text = { n, need -> "$team's $n-game scoring spree ended · needs $need more ${plural(need, "game", "games")} of $sg+ goals in a row" }
        )?.let { out.add(it) }
        runBroken(
            team, matches, CLEAN_SHEET_BROKEN, p(CLEAN_SHEET_BROKEN, "run"), p(CLEAN_SHEET_BROKEN, "back"),
            hit = { m -> (if (m.homeTeam == team) m.awayGoals else m.homeGoals) == 0 },
            text = { n, need -> "$team's $n-game clean-sheet run ended · needs $need more ${plural(need, "clean sheet", "clean sheets")} in a row" }
        )?.let { out.add(it) }
        if (atHome != null) formSplit(team, matches, atHome)?.let { out.add(it) }
        goalDrought(team, matches)?.let { out.add(it) }
        drawMagnet(team, matches)?.let { out.add(it) }
        defensiveCollapse(team, matches)?.let { out.add(it) }
        longBreak(team, matches, fixtureDate)?.let { out.add(it) }
        redCard(team, matches, players)?.let { out.add(it) }
        // Congestion is the stronger form of Short rest: when it fires, Short rest is not shown for the same team.
        (congestion(team, matches, fixtureDate) ?: shortRest(team, matches, fixtureDate))?.let { out.add(it) }
        fewGames(team, matches)?.let { out.add(it) }
        streakBreak(team, matches)?.let { out.add(it) }
        losingBroken(team, matches)?.let { out.add(it) }
        return out
    }

    /** The flags of a fixture: those of its home and away team. */
    fun forFixture(
        home: String, away: String, matches: List<Match>, time: String = "", date: String = "",
        consensus: ConsensusResult? = null, players: List<KeyPlayer> = emptyList(), news: List<TeamNews> = emptyList()
    ): List<KryptoFlag> =
        evaluate(home, matches, date, true, players, news) + evaluate(away, matches, date, false, players, news) +
            listOfNotNull(earlyKickoff(time), modelsDisagree(consensus))

    private fun modelsDisagree(c: ConsensusResult?): KryptoFlag? {
        val best = c?.picks?.firstOrNull() ?: return null
        if (best.against.isEmpty()) return null
        return KryptoFlag(MODELS_DISAGREE, "", "Models disagree · ${best.against.joinToString(", ")} against ${best.label}")
    }

    private fun gamesOf(team: String, matches: List<Match>) =
        matches.filter { it.homeTeam == team || it.awayTeam == team }.sortedBy { it.date }

    private fun goalDrought(team: String, matches: List<Match>): KryptoFlag? {
        val n = p(GOAL_DROUGHT, "games")
        val last = gamesOf(team, matches).takeLast(n)
        if (last.size < n) return null
        val dry = last.all { (if (it.homeTeam == team) it.homeGoals else it.awayGoals) == 0 }
        return if (dry) KryptoFlag(GOAL_DROUGHT, team, "$team has not scored in its last $n games") else null
    }

    private fun scoringSpree(team: String, matches: List<Match>): KryptoFlag? {
        val n = p(SCORING_SPREE, "games"); val g = p(SCORING_SPREE, "goals")
        val last = gamesOf(team, matches).takeLast(n)
        if (last.size < n) return null
        val hot = last.all { (if (it.homeTeam == team) it.homeGoals else it.awayGoals) >= g }
        return if (hot) KryptoFlag(SCORING_SPREE, team, "$team scored $g+ goals in each of its last $n games") else null
    }

    private fun cleanSheetRun(team: String, matches: List<Match>): KryptoFlag? {
        val n = p(CLEAN_SHEET_RUN, "games")
        val last = gamesOf(team, matches).takeLast(n)
        if (last.size < n) return null
        val tight = last.all { (if (it.homeTeam == team) it.awayGoals else it.homeGoals) == 0 }
        return if (tight) KryptoFlag(CLEAN_SHEET_RUN, team, "$team kept a clean sheet in each of its last $n games") else null
    }

    /**
     * Follow-up of a run: after a run of [runGames]+ games where [hit] was true ended, the team is flagged until
     * [hit] is true [back] games in a row again.
     */
    private fun runBroken(
        team: String, matches: List<Match>, ruleId: String, runGames: Int, back: Int,
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
                    if (run >= runGames) { flagged = true; broken = run; since = 0 }
                    run = 0
                }
            } else if (h) {
                since++
                if (since >= back) { flagged = false; run = since }
            } else since = 0
        }
        return if (flagged) KryptoFlag(ruleId, team, text(broken, back - since)) else null
    }

    private fun formSplit(team: String, matches: List<Match>, atHome: Boolean): KryptoFlag? {
        fun ppg(g: List<Match>, home: Boolean): Double =
            g.fold(0) { acc, m ->
                val mine = if (home) m.homeGoals else m.awayGoals
                val theirs = if (home) m.awayGoals else m.homeGoals
                acc + (if (mine > theirs) 3 else if (mine == theirs) 1 else 0)
            } / g.size.toDouble()
        val all = gamesOf(team, matches)
        val h = all.filter { it.homeTeam == team }.takeLast(p(FORM_SPLIT, "games"))
        val a = all.filter { it.awayTeam == team }.takeLast(p(FORM_SPLIT, "games"))
        if (h.size < p(FORM_SPLIT, "min") || a.size < p(FORM_SPLIT, "min")) return null
        val hp = ppg(h, true)
        val ap = ppg(a, false)
        val gap = hp - ap
        val weakerIsHome = gap < 0
        if (kotlin.math.abs(gap) < p(FORM_SPLIT, "gap") / 10.0 || weakerIsHome != atHome) return null
        val where = if (atHome) "at home" else "away"
        return KryptoFlag(
            FORM_SPLIT, team,
            "$team averages %.1f pts/game at home but %.1f away · playing $where".format(hp, ap)
        )
    }

    private fun drawMagnet(team: String, matches: List<Match>): KryptoFlag? {
        val w = p(DRAW_MAGNET, "window")
        val last = gamesOf(team, matches).takeLast(w)
        if (last.size < w) return null
        val draws = last.count { it.homeGoals == it.awayGoals }
        return if (draws >= p(DRAW_MAGNET, "draws")) KryptoFlag(DRAW_MAGNET, team, "$team drew $draws of its last $w games") else null
    }

    private fun defensiveCollapse(team: String, matches: List<Match>): KryptoFlag? {
        val n = p(DEFENSIVE_COLLAPSE, "games"); val g = p(DEFENSIVE_COLLAPSE, "goals")
        val last = gamesOf(team, matches).takeLast(n)
        if (last.size < n) return null
        val leaky = last.all { (if (it.homeTeam == team) it.awayGoals else it.homeGoals) >= g }
        return if (leaky) KryptoFlag(DEFENSIVE_COLLAPSE, team, "$team conceded $g+ goals in each of its last $n games") else null
    }

    private fun longBreak(team: String, matches: List<Match>, fixtureDate: String): KryptoFlag? {
        val fx = DateUtils.parse(fixtureDate) ?: return null
        val prev = gamesOf(team, matches).mapNotNull { DateUtils.parse(it.date) }.filter { it.isBefore(fx) }.maxOrNull()
            ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(prev, fx).toInt()
        return if (days >= p(LONG_BREAK, "days")) KryptoFlag(LONG_BREAK, team, "$team has not played for $days days") else null
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

    private fun teamNews(team: String, matches: List<Match>, fixtureDate: String, news: List<TeamNews>): List<KryptoFlag> =
        news.filter { it.team == team && KeyPlayers.newsActive(it, fixtureDate, matches) }.map { n ->
            val tail = when {
                n.endDate.isNotBlank() -> " (until ${n.endDate})"
                else -> KeyPlayers.gamesLeftNews(n, matches).let { " ($it more ${plural(it, "game", "games")})" }
            }
            val note = if (n.note.isBlank()) "" else ": ${n.note.trim()}"
            KryptoFlag(TEAM_NEWS, team, "$team · ${n.type.label}$note$tail")
        }

    /** The fixture is the team's [games]th within [days] days: games before it that fall in the window, plus this one. */
    private fun congestion(team: String, matches: List<Match>, fixtureDate: String): KryptoFlag? {
        val fx = DateUtils.parse(fixtureDate) ?: return null
        val need = p(CONGESTION, "games"); val window = p(CONGESTION, "days")
        val prior = gamesOf(team, matches).mapNotNull { DateUtils.parse(it.date) }.count {
            val d = java.time.temporal.ChronoUnit.DAYS.between(it, fx)
            d in 1..(window - 1).toLong()
        }
        if (prior + 1 < need) return null
        return KryptoFlag(CONGESTION, team, "$team: game ${prior + 1} in $window days")
    }

    private fun shortRest(team: String, matches: List<Match>, fixtureDate: String): KryptoFlag? {
        val fx = DateUtils.parse(fixtureDate) ?: return null
        val prev = gamesOf(team, matches).mapNotNull { DateUtils.parse(it.date) }.filter { it.isBefore(fx) }.maxOrNull()
            ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(prev, fx).toInt()
        if (days >= p(SHORT_REST, "days")) return null
        return KryptoFlag(SHORT_REST, team, "$team played $days ${if (days == 1) "day" else "days"} before this game")
    }

    private fun fewGames(team: String, matches: List<Match>): KryptoFlag? {
        val n = gamesOf(team, matches).size
        if (n >= p(FEW_GAMES, "results")) return null
        return KryptoFlag(FEW_GAMES, team, "$team has only $n ${if (n == 1) "result" else "results"} on file")
    }

    private fun streakBreak(team: String, matches: List<Match>): KryptoFlag? {
        val trigger = p(STREAK_BREAK, "trigger"); val clear = p(STREAK_BREAK, "clear")
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
                        if (wins >= trigger) { flagged = true; broken = wins; since = 0 }
                        wins = 0
                    }
                    else -> wins = 0 // a draw ends the streak but raises no flag
                }
            } else {
                if (won) {
                    since++
                    if (since >= clear) { flagged = false; wins = since }
                } else {
                    since = 0
                }
            }
        }
        if (!flagged) return null
        val need = clear - since
        return KryptoFlag(
            STREAK_BREAK, team,
            "$team lost after a $broken-game winning streak · needs $need more ${plural(need, "win", "wins")} in a row"
        )
    }

    private fun losingBroken(team: String, matches: List<Match>): KryptoFlag? {
        val trigger = p(LOSING_BROKEN, "trigger"); val clear = p(LOSING_BROKEN, "clear")
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
                        if (losses >= trigger) { flagged = true; broken = losses; since = 0 }
                        losses = 0
                    }
                    else -> losses = 0 // a draw ends the losing run but raises no flag
                }
            } else {
                if (won) {
                    since++
                    if (since >= clear) { flagged = false; losses = 0 }
                } else {
                    since = 0
                    if (lost) { flagged = false; losses = 1 } // lost again: the run of form is back to normal
                }
            }
        }
        if (!flagged) return null
        val need = clear - since
        return KryptoFlag(
            LOSING_BROKEN, team,
            "$team won after a $broken-game losing streak · needs $need more ${plural(need, "win", "wins")} in a row"
        )
    }

    /** The flags that count for a pick of type [group] ("1X2", "Goals"...); all flags when [group] is null (a whole fixture). */
    fun relevant(flags: List<KryptoFlag>, group: String?): List<KryptoFlag> =
        if (group == null) flags else flags.filter { group in config.marketsOf(it.ruleId) }

    /**
     * The strongest effect among the flags, given the mode chosen for each rule (default: warn only). With a pick type
     * given, only the flags that apply to that type count.
     */
    fun effect(flags: List<KryptoFlag>, modes: Map<String, KryptoMode>, group: String? = null): KryptoMode =
        relevant(flags, group).map { modes[it.ruleId] ?: KryptoMode.WARN }.maxByOrNull { it.ordinal } ?: KryptoMode.WARN
}
