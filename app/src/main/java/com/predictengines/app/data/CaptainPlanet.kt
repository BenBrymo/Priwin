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
            else "$name: ${tally.correct} of ${tally.total} settled picks right (${Math.round(tally.rate)}%, ${where.ifBlank { "all lists" }}). Drop its picks?"
    }

    /** True when ban entry [entry] covers a pick with this selection [key] and market type [group]. */
    fun covers(entry: String, key: String, group: String) = entry == key || (entry.startsWith(GROUP) && entry.substring(GROUP.length) == group)

    fun isBanned(key: String, group: String, banned: Set<String>) = banned.isNotEmpty() && banned.any { covers(it, key, group) }

    /** A readable name for a ban entry: "BTTS (all picks)" or "BTTS Yes". */
    fun nameOf(entry: String) =
        if (entry.startsWith(GROUP)) entry.substring(GROUP.length) + " (every pick of this type)" else PerformanceReport.keyName(entry)

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
        val base = records.filter { it.source in Trends.BASE_SOURCES && it.leagueId in visible && inWindow(it) && !isBanned(it.key, it.group, banned) }
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
        out += groupProps.filter { (e, _) -> e.substring(GROUP.length) !in badKeyGroups }.values
        val open = out.filter { p -> p.count >= (declined[p.id]?.plus(RETRY_AFTER) ?: 0) }.toMutableList()

        // recovery of dropped markets, judged on the picks that were kept off the lists
        val hidden = records.filter { it.source == HIDDEN_SOURCE && it.leagueId in visible }
        for (entry in banned) {
            val x = hidden.filter { covers(entry, it.key, it.group) }
            val recent = x.sortedByDescending { it.date }.take(RECENT)
            val t = tally(recent)
            val p = Proposal(entry, nameOf(entry).replace(" (every pick of this type)", ""), t, true, x.size)
            if (t.total >= RECENT_MIN && t.rate >= RECOVER_RATE && p.count >= (declined[p.id]?.plus(RETRY_AFTER) ?: 0)) open.add(p)
        }
        return open.sortedBy { it.tally.rate }
    }
}
