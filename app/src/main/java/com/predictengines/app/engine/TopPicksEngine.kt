package com.predictengines.app.engine

import com.predictengines.app.data.ConsensusPick
import com.predictengines.app.data.ConsensusVote
import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.KryptoMode
import com.predictengines.app.data.Kryptonites
import com.predictengines.app.data.FixtureItem
import com.predictengines.app.data.League
import com.predictengines.app.data.ReportBuilder
import com.predictengines.app.data.TopPick
import java.time.LocalDateTime

/**
 * Ranks the strongest consensus picks across every league's upcoming fixtures.
 * Each fixture contributes at most its single best pick, so the list is spread over many games
 * instead of five markets of the same match; played fixtures and fixtures that can't be predicted are skipped.
 */
object TopPicksEngine {

    const val DEFAULT_LIMIT = 20

    /** The best pick of each upcoming, predictable fixture of one league. */
    fun candidates(league: League, items: List<FixtureItem>, now: LocalDateTime = LocalDateTime.now()): List<TopPick> =
        items.mapNotNull { fx ->
            val home = fx.home ?: return@mapNotNull null
            val away = fx.away ?: return@mapNotNull null
            if (DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now)) return@mapNotNull null
            val best = fx.summary?.consensus?.picks?.maxByOrNull { it.strength } ?: return@mapNotNull null
            TopPick(
                leagueId = league.id, leagueName = league.name,
                date = fx.fixture.date, time = fx.fixture.time,
                homeDisplay = fx.fixture.homeTeam, awayDisplay = fx.fixture.awayTeam,
                home = home, away = away, pick = best,
                flags = fx.summary?.flags.orEmpty()
            )
        }

    private fun upcoming(league: League, items: List<FixtureItem>, now: LocalDateTime, make: (FixtureItem) -> ConsensusPick?): List<TopPick> =
        items.mapNotNull { fx ->
            val home = fx.home ?: return@mapNotNull null
            val away = fx.away ?: return@mapNotNull null
            if (DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now)) return@mapNotNull null
            val pick = make(fx) ?: return@mapNotNull null
            TopPick(
                leagueId = league.id, leagueName = league.name,
                date = fx.fixture.date, time = fx.fixture.time,
                homeDisplay = fx.fixture.homeTeam, awayDisplay = fx.fixture.awayTeam,
                home = home, away = away, pick = pick,
                flags = fx.summary?.flags.orEmpty()
            )
        }

    /** Brymo's "+2 Handicap Likely" pick (at most one per game), ranked by Brymo's confidence. */
    fun handicapCandidates(league: League, items: List<FixtureItem>, now: LocalDateTime = LocalDateTime.now()): List<TopPick> =
        upcoming(league, items, now) { fx ->
            val h = fx.summary?.handicap ?: return@upcoming null
            ConsensusPick(
                key = "B:${h.label}", label = h.label, group = "Handicap / other",
                backers = listOf(ConsensusVote("Brymo", h.confidencePercent)),
                against = emptyList(), abstain = emptyList(), modelCount = 3, strength = h.confidencePercent
            )
        }

    /** "Over 1.5 goals" where Poisson and/or Stats back it. Two backers rank above one; then average confidence. */
    fun over15Candidates(league: League, items: List<FixtureItem>, now: LocalDateTime = LocalDateTime.now()): List<TopPick> =
        upcoming(league, items, now) { fx ->
            val votes = fx.summary?.over15?.takeIf { it.isNotEmpty() } ?: return@upcoming null
            val avg = votes.map { it.confidence }.average()
            ConsensusPick(
                key = "O1.5", label = "Over 1.5 goals", group = "Over/Under",
                backers = votes, against = emptyList(), abstain = emptyList(), modelCount = 3,
                strength = avg + 100.0 * (votes.size - 1) // only used for ordering: more backers first
            )
        }

    /**
     * Adds the picks whose games are already played to a ranked list of upcoming picks. Played picks keep their
     * week; each week then shows its played picks plus as many upcoming ones as fit in [limit], strongest first.
     */
    fun withPlayed(
        upcoming: List<TopPick>, played: List<TopPick>, limit: Int = DEFAULT_LIMIT,
        modes: Map<String, KryptoMode> = emptyMap()
    ): List<TopPick> {
        val strongestFirst = compareBy<TopPick> { Kryptonites.effect(it.flags, modes) == KryptoMode.BOTTOM }
            .thenByDescending { it.pick.strength }
            .thenByDescending { it.pick.backers.size }
            .thenBy { DateUtils.sortKey(it.date, it.time) }
        val up = upcoming.groupBy { ReportBuilder.weekStartOf(it.date) }
        val pl = played.groupBy { ReportBuilder.weekStartOf(it.date) }
        return (up.keys + pl.keys).distinct()
            .sortedBy { it?.toEpochDay() ?: Long.MAX_VALUE }
            .flatMap { week ->
                val past = pl[week].orEmpty()
                val room = (limit - past.size).coerceAtLeast(0)
                val ahead = up[week].orEmpty().sortedWith(strongestFirst).take(room)
                (past + ahead).sortedWith(strongestFirst)
            }
    }

    /**
     * The [limit] strongest picks of EACH Monday-Sunday week, across all leagues. Weeks run earliest first;
     * inside a week the strongest comes first (more backers, then earlier kickoff, break ties).
     */
    fun rank(all: List<TopPick>, limit: Int = DEFAULT_LIMIT, modes: Map<String, KryptoMode> = emptyMap()): List<TopPick> {
        val strongestFirst = compareBy<TopPick> { Kryptonites.effect(it.flags, modes) == KryptoMode.BOTTOM }
            .thenByDescending { it.pick.strength }
            .thenByDescending { it.pick.backers.size }
            .thenBy { DateUtils.sortKey(it.date, it.time) }
        return all.filter { Kryptonites.effect(it.flags, modes) != KryptoMode.HIDE }.groupBy { ReportBuilder.weekStartOf(it.date) }
            .entries.sortedBy { it.key?.toEpochDay() ?: Long.MAX_VALUE }
            .flatMap { (_, picks) -> picks.sortedWith(strongestFirst).take(limit) }
    }
}
