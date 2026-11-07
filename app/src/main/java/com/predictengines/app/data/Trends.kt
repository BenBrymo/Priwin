package com.predictengines.app.data

import com.predictengines.app.data.PerformanceReport.Tally

/**
 * Data-trend picks. The Data tab already knows how every market has done in every league; this turns that into picks:
 * a "spot" is one market in one league (Over 1.5 in the Bundesliga). A spot is strong when it has enough settled picks
 * and a high enough score (the hit rate corrected for sample size), and cold when it has enough picks and hits under 50%.
 * Upcoming picks that fall in a strong spot are the Trend picks.
 */
object Trends {
    const val SOURCE = "Trends"
    const val DEFAULT_MIN = 15
    const val DEFAULT_SCORE = 75

    /** The weekly lists whose settled picks feed the trends (Trend picks themselves never feed back in). */
    val BASE_SOURCES = setOf("Top 20", "Handicap", "Over 1.5", "Loki", "Corners")

    data class Spot(val leagueId: String, val leagueName: String, val key: String, val name: String, val tally: Tally) {
        val score: Double get() = tally.adjusted
    }

    data class Found(val strong: List<Spot>, val cold: List<Spot>)

    fun spots(records: Collection<PredictionRecord>, visible: Set<String>, minPicks: Int, minScore: Int): Found {
        // one pick can sit on several lists (Top 20 and Over 1.5): count each game's selection once
        val seen = HashSet<String>()
        val rs = records.filter { r ->
            r.source in BASE_SOURCES && r.leagueId in visible &&
                seen.add("${r.leagueId}|${r.date}|${r.home}|${r.away}|${r.key}")
        }
        val all = rs.groupBy { it.leagueId to it.key }.mapNotNull { (id, x) ->
            if (x.size < minPicks) return@mapNotNull null
            Spot(id.first, x.first().leagueName, id.second, PerformanceReport.keyName(id.second),
                Tally(x.count { it.correct }, x.count { !it.correct }))
        }
        val strong = all.filter { it.score >= minScore }
            .sortedWith(compareBy<Spot> { it.leagueName }.thenByDescending { it.score })
        val cold = all.filter { it.tally.rate < PerformanceReport.AVOID_RATE }.sortedBy { it.tally.rate }
        return Found(strong, cold)
    }

    /** The candidates that fall in a strong spot, strongest data first. The label carries the league's record for the market. */
    fun pick(candidates: List<TopPick>, strong: List<Spot>): List<TopPick> {
        val byKey = strong.associateBy { it.leagueId to it.key }
        return candidates.mapNotNull { tp ->
            val s = byKey[tp.leagueId to tp.pick.key] ?: return@mapNotNull null
            tp.copy(
                pick = tp.pick.copy(
                    label = "${tp.pick.label} · ${s.leagueName} ${s.tally.correct}/${s.tally.total}",
                    strength = s.score + 0.1 * tp.pick.strength
                )
            )
        }
    }
}
