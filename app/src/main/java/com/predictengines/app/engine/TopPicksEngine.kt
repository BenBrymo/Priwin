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
    fun candidates(league: League, items: List<FixtureItem>, now: LocalDateTime = LocalDateTime.now(), banned: Set<String> = emptySet()): List<TopPick> =
        items.mapNotNull { fx ->
            val home = fx.home ?: return@mapNotNull null
            val away = fx.away ?: return@mapNotNull null
            if (DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now)) return@mapNotNull null
            val best = fx.summary?.consensus?.picks?.filter { it.key !in banned }?.maxByOrNull { it.strength } ?: return@mapNotNull null
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

    /** The Loki picks (see [Lokis]) of each upcoming fixture of one league; a game can have several. */
    fun lokiCandidates(
        league: League, items: List<FixtureItem>, matches: List<com.predictengines.app.data.Match>,
        now: LocalDateTime = LocalDateTime.now()
    ): List<TopPick> =
        items.flatMap { fx ->
            val home = fx.home ?: return@flatMap emptyList()
            val away = fx.away ?: return@flatMap emptyList()
            if (DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now)) return@flatMap emptyList()
            val flags = fx.summary?.flags.orEmpty()
            com.predictengines.app.data.Lokis.forFixture(home, away, fx.fixture.date, matches, flags).map { lp ->
                TopPick(
                    leagueId = league.id, leagueName = league.name,
                    date = fx.fixture.date, time = fx.fixture.time,
                    homeDisplay = fx.fixture.homeTeam, awayDisplay = fx.fixture.awayTeam,
                    home = home, away = away,
                    pick = ConsensusPick(
                        key = lp.key, label = lp.label, group = lp.group,
                        backers = lp.lokis.map { ConsensusVote(it, lp.strength) },
                        against = emptyList(), abstain = emptyList(), modelCount = lp.lokis.size, strength = lp.strength
                    ),
                    flags = flags
                )
            }
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
     * Everything a game could be picked for, for the Trends list: its consensus picks, Over 1.5 where backed, and its best
     * corner line. The Trends list keeps the ones that fall in a league-and-market spot the data backs.
     */
    fun trendCandidates(league: League, items: List<FixtureItem>, now: LocalDateTime = LocalDateTime.now()): List<TopPick> {
        val out = ArrayList<TopPick>()
        val seen = HashSet<String>()
        fun add(tp: TopPick) { if (seen.add("${tp.date}|${tp.home}|${tp.away}|${tp.pick.key}")) out.add(tp) }
        items.forEach { fx ->
            val home = fx.home ?: return@forEach
            val away = fx.away ?: return@forEach
            if (DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now)) return@forEach
            fx.summary?.consensus?.picks?.forEach { p ->
                add(TopPick(league.id, league.name, fx.fixture.date, fx.fixture.time, fx.fixture.homeTeam, fx.fixture.awayTeam, home, away, p, fx.summary?.flags.orEmpty()))
            }
        }
        over15Candidates(league, items, now).forEach { add(it) }
        cornerCandidates(league, items, now).forEach { add(it) }
        return out
    }

    /** The best corner line (Poisson and/or Stats) of each upcoming game. Two backers rank above one; then average confidence. */
    fun cornerCandidates(league: League, items: List<FixtureItem>, now: LocalDateTime = LocalDateTime.now()): List<TopPick> =
        upcoming(league, items, now) { fx ->
            val cp = fx.summary?.corner ?: return@upcoming null
            val avg = cp.votes.map { it.confidence }.average()
            ConsensusPick(
                key = cp.key, label = cp.label, group = com.predictengines.app.engine.ConsensusEngine.category(cp.key),
                backers = cp.votes, against = emptyList(), abstain = emptyList(), modelCount = 3,
                strength = avg + 100.0 * (cp.votes.size - 1)
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
        val strongestFirst = compareBy<TopPick> { Kryptonites.effect(it.flags, modes, it.pick.group) == KryptoMode.BOTTOM }
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
     * Picks the Kryptonite switches kept off the list: those on the list as it would be with every switch on Warn only,
     * but not on the list as shown. They are written down (and judged later) so the reports can say if holding them back paid.
     */
    fun heldBack(all: List<TopPick>, shown: List<TopPick>, limit: Int = DEFAULT_LIMIT): List<TopPick> {
        fun id(t: TopPick) = "${t.leagueId}|${t.date}|${t.home}|${t.away}|${t.pick.key}"
        val shownIds = shown.mapTo(HashSet()) { id(it) }
        return rank(all, limit, emptyMap()).filter { id(it) !in shownIds && Kryptonites.relevant(it.flags, it.pick.group).isNotEmpty() }
    }

    /**
     * The [limit] strongest picks of EACH Monday-Sunday week, across all leagues. Weeks run earliest first;
     * inside a week the strongest comes first (more backers, then earlier kickoff, break ties).
     */
    fun rank(all: List<TopPick>, limit: Int = DEFAULT_LIMIT, modes: Map<String, KryptoMode> = emptyMap()): List<TopPick> {
        val strongestFirst = compareBy<TopPick> { Kryptonites.effect(it.flags, modes, it.pick.group) == KryptoMode.BOTTOM }
            .thenByDescending { it.pick.strength }
            .thenByDescending { it.pick.backers.size }
            .thenBy { DateUtils.sortKey(it.date, it.time) }
        return all.filter { Kryptonites.effect(it.flags, modes, it.pick.group) != KryptoMode.HIDE }.groupBy { ReportBuilder.weekStartOf(it.date) }
            .entries.sortedBy { it.key?.toEpochDay() ?: Long.MAX_VALUE }
            .flatMap { (_, picks) -> picks.sortedWith(strongestFirst).take(limit) }
    }
}
