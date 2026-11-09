package com.predictengines.app.data

import com.predictengines.app.data.PerformanceReport.Tally
import java.time.LocalDate
import kotlin.math.sqrt

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
    /** A bad market must be this many standard errors under 50% (about 84% sure it is not just bad luck). */
    private const val BAD_Z = 1.0
    /** Recovery: this share of the latest [RECENT] dropped picks, with at least [RECENT_MIN] settled. */
    private const val RECOVER_RATE = 55.0
    private const val RECENT = 15
    private const val RECENT_MIN = 10

    data class Config(
        /** Fewest settled picks of a market before it can be judged bad. */
        val minPicks: Int = 10,
        val window: PerformanceReport.Window = PerformanceReport.Window.ALL,
        /** Also send a phone notification when a proposal is waiting. */
        val notify: Boolean = true
    )

    /** [lift] false: drop this market. true: it has recovered, lift the ban. */
    data class Proposal(val key: String, val name: String, val tally: Tally, val lift: Boolean, val count: Int = tally.total) {
        val id: String get() = (if (lift) "lift:" else "ban:") + key
        val text: String
            get() = if (lift) "$name has recovered: ${tally.correct} of the last ${tally.total} dropped picks were right (${Math.round(tally.rate)}%). Bring it back?"
            else "$name: ${tally.correct} of ${tally.total} settled picks right (${Math.round(tally.rate)}%). Drop its picks?"
    }

    private fun tally(rs: Collection<PredictionRecord>) = Tally(rs.count { it.correct }, rs.count { !it.correct })

    /** One pick can sit on several lists: count each game's selection once. */
    private fun dedupe(rs: List<PredictionRecord>): List<PredictionRecord> {
        val seen = HashSet<String>()
        return rs.filter { seen.add("${it.leagueId}|${it.date}|${it.home}|${it.away}|${it.key}") }
    }

    /** True when a market's picks are clearly under 50%: the Report's Avoid verdict, and not just a few unlucky picks. */
    fun isBad(t: Tally, minPicks: Int): Boolean {
        if (t.total < minPicks || t.rate >= PerformanceReport.AVOID_RATE) return false
        val z = (0.5 - t.correct.toDouble() / t.total) / sqrt(0.25 / t.total)
        return z >= BAD_Z
    }

    /**
     * [records] are the settled picks. [banned] the markets already dropped. [declined] maps a proposal id to how many picks the
     * market had when you said "Not now".
     */
    fun proposals(
        records: Collection<PredictionRecord>, visible: Set<String>, cfg: Config,
        banned: Set<String>, declined: Map<String, Int>, today: LocalDate = LocalDate.now()
    ): List<Proposal> {
        val from = PerformanceReport.cutoff(cfg.window, today)
        fun inWindow(r: PredictionRecord) = from == null || DateUtils.parse(r.date)?.let { !it.isBefore(from) } == true
        val live = dedupe(records.filter { it.source in Trends.BASE_SOURCES && it.leagueId in visible && inWindow(it) })
        val out = ArrayList<Proposal>()
        for ((key, x) in live.groupBy { it.key }) {
            if (key in banned) continue
            val t = tally(x)
            val p = Proposal(key, PerformanceReport.keyName(key), t, false)
            if (isBad(t, cfg.minPicks) && p.count >= (declined[p.id]?.plus(RETRY_AFTER) ?: 0)) out.add(p)
        }
        // recovery of dropped markets, judged on the picks that were kept off the lists
        val hidden = records.filter { it.source == HIDDEN_SOURCE && it.leagueId in visible }
        for ((key, x) in hidden.groupBy { it.key }) {
            if (key !in banned) continue
            val recent = dedupe(x.sortedByDescending { it.date }).take(RECENT)
            val t = tally(recent)
            val p = Proposal(key, PerformanceReport.keyName(key), t, true, x.size)
            if (t.total >= RECENT_MIN && t.rate >= RECOVER_RATE && p.count >= (declined[p.id]?.plus(RETRY_AFTER) ?: 0)) out.add(p)
        }
        return out.sortedBy { it.tally.rate }
    }
}
