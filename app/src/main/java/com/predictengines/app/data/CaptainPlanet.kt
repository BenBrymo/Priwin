package com.predictengines.app.data

import com.predictengines.app.data.PerformanceReport.Tally
import java.time.LocalDate

/**
 * Captain Planet watches how each market (Both teams score, Over 2.5...) does in your settled picks and PROPOSES dropping
 * the ones that are clearly bad. Nothing changes until you approve. An approved market is dropped from every list and from
 * the consensus picks on fixture cards (one market across all leagues). The dropped picks are still written down, as
 * "Captain Planet (hidden)", and judged after their games, so the app can tell you when a market recovers and offer to lift the ban.
 */
object CaptainPlanet {
    /** Source name of a pick Captain Planet kept off the lists; recorded and judged as "Captain Planet (hidden)". */
    const val NAME = "Captain Planet"
    const val HIDDEN_SOURCE = NAME + PerformanceReport.HIDDEN_SUFFIX

    /** How many more settled picks a market needs after you said "Not now" before it is proposed again. */
    const val RETRY_AFTER = 10
    /** Recovery: this share of the latest [RECENT] dropped picks, with at least [RECENT_MIN] settled. */
    private const val RECOVER_RATE = 55.0
    private const val RECENT = 15
    private const val RECENT_MIN = 10
    /** A ban on a whole market type (all BTTS picks) is stored as "g:BTTS"; a ban on one selection as its key ("BTTS_Y"). */
    private const val GROUP = "g:"
    /** A ban on a team is "t:Team@@league" and on a whole league "l:leagueId": every pick of their games is dropped. */
    private const val TEAM = "t:"
    private const val LEAGUE = "l:"

    data class Config(
        /** Fewest settled picks of one selection before it can be judged bad (a whole market type needs twice as many). */
        val minPicks: Int = PerformanceReport.MIN_MARKET,
        val window: PerformanceReport.Window = PerformanceReport.Window.ALL,
        /** Also send a phone notification when a proposal is waiting. */
        val notify: Boolean = true
    )

    /** [key] is the ban entry. [lift] false: drop this market. true: it has recovered, lift the ban. [where] is the list or "all lists". */
    data class Proposal(
        val key: String, val name: String, val tally: Tally, val lift: Boolean, val count: Int = tally.total, val where: String = ""
    ) {
        val id: String get() = (if (lift) "lift:" else "ban:") + key
        val text: String
            get() = if (lift) "$name has recovered: ${tally.correct} of the last ${tally.total} dropped picks were right (${Math.round(tally.rate)}%). Bring it back?"
            else "$name: ${tally.correct} of ${tally.total} settled picks right (${Math.round(tally.rate)}%, ${where.ifBlank { "all lists" }}). " +
                if (key.startsWith(TEAM) || key.startsWith(LEAGUE)) "Drop all its games?" else "Drop its picks?"
    }

    /** A ban in one league only is written "<entry>@@<league id>"; without that tail it covers every league. */
    private const val LEAGUE_TAIL = "@@"

    fun leagueEntry(entry: String, leagueId: String) = entry + LEAGUE_TAIL + leagueId

    /** True when ban entry [entry] covers a pick of this selection [key], market type [group] and [leagueId]. */
    fun covers(entry: String, key: String, group: String, leagueId: String = ""): Boolean {
        val at = entry.indexOf(LEAGUE_TAIL)
        val what = if (at >= 0) entry.substring(0, at) else entry
        if (at >= 0 && entry.substring(at + LEAGUE_TAIL.length) != leagueId) return false
        return what == key || (what.startsWith(GROUP) && what.substring(GROUP.length) == group)
    }

    fun teamEntry(team: String, leagueId: String) = leagueEntry(TEAM + team, leagueId)

    fun leagueBanEntry(leagueId: String) = LEAGUE + leagueId

    /** True when a team or league ban drops every pick of this game. */
    fun coversGame(entry: String, leagueId: String, home: String, away: String): Boolean {
        val at = entry.indexOf(LEAGUE_TAIL)
        val what = if (at >= 0) entry.substring(0, at) else entry
        if (at >= 0 && entry.substring(at + LEAGUE_TAIL.length) != leagueId) return false
        return when {
            what.startsWith(LEAGUE) -> what.substring(LEAGUE.length) == leagueId
            what.startsWith(TEAM) -> what.substring(TEAM.length).let { it == home || it == away }
            else -> false
        }
    }

    fun gameBanned(banned: Set<String>, leagueId: String, home: String, away: String) =
        banned.isNotEmpty() && banned.any { coversGame(it, leagueId, home, away) }

    fun isBanned(key: String, group: String, banned: Set<String>, leagueId: String = "") =
        banned.isNotEmpty() && banned.any { covers(it, key, group, leagueId) }

    /** A readable name for a ban entry: "BTTS (every pick of this type)", "BTTS Yes" or "BTTS Yes in Ligue 1". */
    fun nameOf(entry: String, leagueName: (String) -> String = { it }): String {
        val at = entry.indexOf(LEAGUE_TAIL)
        val what = if (at >= 0) entry.substring(0, at) else entry
        if (what.startsWith(LEAGUE)) return leagueName(what.substring(LEAGUE.length)) + " (the whole league)"
        if (what.startsWith(TEAM)) {
            val team = what.substring(TEAM.length)
            return if (at >= 0) team + " (" + leagueName(entry.substring(at + LEAGUE_TAIL.length)) + ")" else team
        }
        val base = if (what.startsWith(GROUP)) what.substring(GROUP.length) + " (every pick of this type)" else PerformanceReport.keyName(what)
        return if (at >= 0) base + " in " + leagueName(entry.substring(at + LEAGUE_TAIL.length)) else base
    }

    private fun tally(rs: Collection<PredictionRecord>) = Tally(rs.count { it.correct }, rs.count { !it.correct })

    private fun inSource(src: String, r: PredictionRecord) =
        if (src == PerformanceReport.ALL_LISTS) r.source in Trends.BASE_SOURCES else r.source == src

    /**
     * Proposals judged exactly as the Report judges them: a market type (BTTS, Goals...) or one selection (BTTS Yes) in a list or
     * across all lists whose verdict is Avoid (under 50% right with enough settled picks). When a selection is bad it is proposed
     * on its own, so the good half of a market type (BTTS No) is kept; a whole type is proposed only when no single selection is.
     * [banned] are the entries already dropped. [declined] maps a proposal id to how many picks it had when you said "Not now".
     */
    fun proposals(
        records: Collection<PredictionRecord>, visible: Set<String>, cfg: Config,
        banned: Set<String>, declined: Map<String, Int>, today: LocalDate = LocalDate.now()
    ): List<Proposal> {
        val from = PerformanceReport.cutoff(cfg.window, today)
        fun inWindow(r: PredictionRecord) = from == null || DateUtils.parse(r.date)?.let { !it.isBefore(from) } == true
        // picks a ban already covers are not judged again
        val base = records.filter { it.source in Trends.BASE_SOURCES && it.leagueId in visible && inWindow(it) && !isBanned(it.key, it.group, banned, it.leagueId) && !gameBanned(banned, it.leagueId, it.home, it.away) }
        val keyProps = LinkedHashMap<String, Proposal>(); val keyGroup = HashMap<String, String>()
        val groupProps = LinkedHashMap<String, Proposal>()
        for (src in listOf(PerformanceReport.ALL_LISTS) + Trends.BASE_SOURCES.toList().sorted()) {
            val where = if (src == PerformanceReport.ALL_LISTS) "all lists" else "the $src list"
            val recs = base.filter { inSource(src, it) }
            for ((key, x) in recs.groupBy { it.key }) {
                val t = tally(x)
                if (t.total < cfg.minPicks || PerformanceReport.verdict(t, PerformanceReport.MIN_MARKET) != PerformanceReport.Verdict.AVOID) continue
                if (banned.any { covers(it, key, x.first().group) }) continue
                keyProps.putIfAbsent(key, Proposal(key, PerformanceReport.keyName(key), t, false, where = where)); keyGroup[key] = x.first().group
            }
            for ((group, x) in recs.groupBy { it.group }) {
                val t = tally(x)
                if (t.total < cfg.minPicks * 2 || PerformanceReport.verdict(t, PerformanceReport.MIN_GROUP) != PerformanceReport.Verdict.AVOID) continue
                if (GROUP + group in banned) continue
                groupProps.putIfAbsent(GROUP + group, Proposal(GROUP + group, nameOf(GROUP + group).replace(" (every pick of this type)", ""), t, false, where = where))
            }
        }
        val badKeyGroups = keyProps.keys.mapNotNull { keyGroup[it] }.toSet()
        val out = ArrayList<Proposal>()
        out += keyProps.values
        val wholeGroups = groupProps.filter { (e, _) -> e.substring(GROUP.length) !in badKeyGroups }
        out += wholeGroups.values
        // a market that is only bad in one league (and not bad overall) is proposed for that league alone, with twice the picks
        for ((id, x) in base.groupBy { it.leagueId to it.key }) {
            val (lg, key) = id
            if (key in keyProps || GROUP + x.first().group in wholeGroups || GROUP + x.first().group in groupProps) continue
            val t = tally(x)
            if (t.total < cfg.minPicks * 2 || PerformanceReport.verdict(t, PerformanceReport.MIN_MARKET) != PerformanceReport.Verdict.AVOID) continue
            out.add(Proposal(leagueEntry(key, lg), PerformanceReport.keyName(key) + " in " + x.first().leagueName, t, false, where = "that league"))
        }
        // a whole league that is Avoid overall, then single teams that are Avoid (not in a league already proposed), judged like the Report's rows
        val badLeagues = HashSet<String>()
        for ((lg, x) in base.groupBy { it.leagueId }) {
            val t = tally(x)
            if (t.total < maxOf(PerformanceReport.MIN_LEAGUE, cfg.minPicks * 3) || PerformanceReport.verdict(t, PerformanceReport.MIN_LEAGUE) != PerformanceReport.Verdict.AVOID) continue
            badLeagues += lg
            out.add(Proposal(leagueBanEntry(lg), x.first().leagueName, t, false, where = "all lists"))
        }
        val teamRecs = HashMap<Pair<String, String>, MutableList<PredictionRecord>>()
        for (r in base) {
            if (r.leagueId in badLeagues) continue
            teamRecs.getOrPut(r.leagueId to r.home) { ArrayList() }.add(r)
            teamRecs.getOrPut(r.leagueId to r.away) { ArrayList() }.add(r)
        }
        for ((id, x) in teamRecs) {
            val t = tally(x)
            if (t.total < cfg.minPicks || PerformanceReport.verdict(t, PerformanceReport.MIN_TEAM) != PerformanceReport.Verdict.AVOID) continue
            out.add(Proposal(teamEntry(id.second, id.first), id.second + " (" + x.first().leagueName + ")", t, false, where = "all lists"))
        }
        val open = out.filter { p -> p.count >= (declined[p.id]?.plus(RETRY_AFTER) ?: 0) }.toMutableList()

        // recovery of dropped markets, judged on the picks that were kept off the lists
        val hidden = records.filter { it.source == HIDDEN_SOURCE && it.leagueId in visible }
        for (entry in banned) {
            val x = hidden.filter { covers(entry, it.key, it.group, it.leagueId) || coversGame(entry, it.leagueId, it.home, it.away) }
            val recent = x.sortedByDescending { it.date }.take(RECENT)
            val t = tally(recent)
            val p = Proposal(entry, nameOf(entry) { id -> hidden.firstOrNull { it.leagueId == id }?.leagueName ?: id }.replace(" (every pick of this type)", ""), t, true, x.size)
            if (t.total >= RECENT_MIN && t.rate >= RECOVER_RATE && p.count >= (declined[p.id]?.plus(RETRY_AFTER) ?: 0)) open.add(p)
        }
        return open.sortedBy { it.tally.rate }
    }
}
