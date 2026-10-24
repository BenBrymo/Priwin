package com.predictengines.app.engine

import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.FixtureItem
import com.predictengines.app.data.League
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
                home = home, away = away, pick = best
            )
        }

    /** Strongest first; more backers, then earlier kickoff, break ties. */
    fun rank(all: List<TopPick>, limit: Int = DEFAULT_LIMIT): List<TopPick> =
        all.sortedWith(
            compareByDescending<TopPick> { it.pick.strength }
                .thenByDescending { it.pick.backers.size }
                .thenBy { DateUtils.sortKey(it.date, it.time) }
        ).take(limit)
}
