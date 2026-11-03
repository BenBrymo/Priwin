package com.predictengines.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PlayerPosition(val short: String, val label: String) {
    GK("GK", "Goalkeeper"),
    DEF("DEF", "Defender"),
    MID("MID", "Midfielder"),
    FWD("FWD", "Forward")
}

enum class PlayerStatus(val label: String) {
    AVAILABLE("Available"),
    DOUBTFUL("Doubtful"),
    INJURED("Injured"),
    SUSPENDED("Suspended")
}

/**
 * A key player of a team in one league. [returnDate] (yyyy-MM-dd, optional) is the day the player is expected back.
 * A suspension from a red card is counted in games: [banGames] games after the game on [banFrom].
 * [photoStamp] is 0 without a photo, otherwise the time the photo was saved (it also refreshes the picture on screen).
 */
data class KeyPlayer(
    val id: String,
    val leagueId: String,
    val team: String,
    val name: String,
    val position: PlayerPosition = PlayerPosition.MID,
    val status: PlayerStatus = PlayerStatus.AVAILABLE,
    val returnDate: String = "",
    val banGames: Int = 0,
    val banFrom: String = "",
    val photoStamp: Long = 0L,
    /** Which part of a tall photo shows in the round picture: 0 = the top (heads), 1 = the bottom. */
    val photoFocus: Float = 0f
)

/** The kinds of team news you can note. [defaultGames] is how many of the team's games the flag lasts by default. */
enum class NewsType(val label: String, val defaultGames: Int) {
    MANAGER("Manager change", 2),
    TURMOIL("Club turmoil", 2),
    TRAVEL("Travel or fatigue", 1),
    PITCH("Pitch or weather", 1),
    OTHER("Other", 2)
}

/**
 * A piece of news about a team that can affect results. It flags the team's fixtures from [date] on, for [games] of the
 * team's games, or until [endDate] (yyyy-MM-dd) when one is set.
 */
data class TeamNews(
    val id: String,
    val leagueId: String,
    val team: String,
    val type: NewsType,
    val date: String,
    val note: String = "",
    val games: Int = type.defaultGames,
    val endDate: String = ""
)

/** A team whose last game had a red card for it, waiting for the user to say who was sent off. */
data class RedEvent(val leagueId: String, val team: String, val date: String, val opponent: String) {
    val key: String get() = "$leagueId|$team|$date"
}

object KeyPlayers {

    /** Changes whenever the players of a league change (used to rebuild the saved predictions). */
    fun stamp(players: List<KeyPlayer>, leagueId: String): String =
        players.filter { it.leagueId == leagueId }.sortedBy { it.id }
            .joinToString("|") { "${it.id},${it.team},${it.name},${it.status},${it.returnDate},${it.banGames},${it.banFrom}" }
            .hashCode().toString()

    /** Changes whenever the news of a league changes (used to rebuild the saved predictions). */
    fun newsStamp(news: List<TeamNews>, leagueId: String): String =
        news.filter { it.leagueId == leagueId }.sortedBy { it.id }
            .joinToString("|") { "${it.id},${it.team},${it.type},${it.date},${it.note},${it.games},${it.endDate}" }
            .hashCode().toString()

    /** True when [n] still counts for a fixture of its team on [fixtureDate], given the games played before it. */
    fun newsActive(n: TeamNews, fixtureDate: String, matchesBefore: List<Match>): Boolean {
        val fx = DateUtils.parse(fixtureDate) ?: return false
        val start = DateUtils.parse(n.date)
        if (start != null && fx.isBefore(start)) return false
        val end = DateUtils.parse(n.endDate)
        if (end != null) return !fx.isAfter(end)
        return gamesLeftNews(n, matchesBefore) > 0
    }

    /** Games of a games-based news item still to run (0 when over). */
    fun gamesLeftNews(n: TeamNews, matchesBefore: List<Match>): Int =
        (n.games - gamesSince(n.team, matchesBefore, n.date)).coerceAtLeast(0)

    /** Games [team] has played after [from] (a yyyy-MM-dd date), among [matches]. */
    fun gamesSince(team: String, matches: List<Match>, from: String): Int {
        val start = DateUtils.parse(from) ?: return 0
        return matches.count {
            (it.homeTeam == team || it.awayTeam == team) && DateUtils.parse(it.date)?.isAfter(start) == true
        }
    }

    /** What a player's status means for a fixture on [fixtureDate], given the games played before it. */
    fun statusFor(p: KeyPlayer, fixtureDate: String, matchesBefore: List<Match>): PlayerStatus {
        if (p.status == PlayerStatus.AVAILABLE) return PlayerStatus.AVAILABLE
        val back = DateUtils.parse(p.returnDate)
        val fx = DateUtils.parse(fixtureDate)
        if (back != null && fx != null && !fx.isBefore(back)) return PlayerStatus.AVAILABLE
        if (p.status == PlayerStatus.SUSPENDED && p.banGames > 0 && p.banFrom.isNotBlank() &&
            gamesSince(p.team, matchesBefore, p.banFrom) >= p.banGames
        ) return PlayerStatus.AVAILABLE
        return p.status
    }

    /** Games of a suspension still to serve before the fixture (0 when it is not a counted ban). */
    fun gamesLeft(p: KeyPlayer, matchesBefore: List<Match>): Int =
        if (p.status == PlayerStatus.SUSPENDED && p.banGames > 0 && p.banFrom.isNotBlank())
            (p.banGames - gamesSince(p.team, matchesBefore, p.banFrom)).coerceAtLeast(0)
        else 0

    /** Red cards of a team's last game that have not been answered yet and are recent enough to matter. */
    fun pendingReds(
        leagueId: String, matches: List<Match>, answered: Set<String>, today: LocalDate, withinDays: Long = 21
    ): List<RedEvent> {
        val last = HashMap<String, Match>()
        for (m in matches.sortedBy { it.date }) { last[m.homeTeam] = m; last[m.awayTeam] = m }
        val out = ArrayList<RedEvent>()
        for ((team, m) in last) {
            val x = m.extras ?: continue
            val reds = (if (m.homeTeam == team) x.homeReds else x.awayReds) ?: continue
            if (reds <= 0) continue
            val d = DateUtils.parse(m.date) ?: continue
            if (ChronoUnit.DAYS.between(d, today) > withinDays) continue
            val e = RedEvent(leagueId, team, m.date, if (m.homeTeam == team) m.awayTeam else m.homeTeam)
            if (e.key !in answered) out.add(e)
        }
        return out.sortedWith(compareByDescending<RedEvent> { it.date }.thenBy { it.team })
    }
}
