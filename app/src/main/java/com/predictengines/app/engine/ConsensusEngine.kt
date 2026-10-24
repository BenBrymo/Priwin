package com.predictengines.app.engine

import com.predictengines.app.data.Analysis
import com.predictengines.app.data.BrymoResult
import com.predictengines.app.data.ConsensusPick
import com.predictengines.app.data.ConsensusResult
import com.predictengines.app.data.ConsensusVote
import com.predictengines.app.data.Outcome
import com.predictengines.app.data.PoissonResult
import com.predictengines.app.data.StatsResult
import com.predictengines.app.data.TrackablePick

/**
 * Finds the best bets across Brymo, Poisson and Stats, in ANY market (not just 1X2).
 *
 * Every model is mapped onto shared selections (1, X, 2, 1X, X2, 12, Over/Under 1.5 / 2.5 / 3.5, BTTS).
 * A model BACKS a selection when it makes that call:
 *  - Brymo: it produced that label (for 1X2, its single strongest 1X2 label).
 *  - Poisson: probability >= 55% (double chance >= 72%); for 1X2 its top outcome.
 *  - Stats: it produced that pick (1X2 pick, goals, BTTS, double chance...).
 * A model that says nothing ABSTAINS; one that backs something ruling the selection out is AGAINST.
 *
 * Ranking: a selection needs at least two backers and more backers than opponents.
 *   strength = average confidence of backers * (backers - 0.75 * against) / models available
 *
 * If NO selection has two backers, the strongest single-model pick (any market, including corners,
 * cards, first to score and win to nil, which only the Stats model covers) is returned instead, so a
 * fixture is never left without a pick. Such a pick has one backer, see [ConsensusPick.isSingleModel].
 * Confidence scales differ between models, so the average is a rough guide.
 */
object ConsensusEngine {

    private const val BINARY_THRESHOLD = 55.0
    private const val DOUBLE_CHANCE_THRESHOLD = 72.0
    const val DEFAULT_MAX_PICKS = 3
    private const val MAX_TRACKED_PER_MODEL = 4

    private val ALL_KEYS = listOf(
        "1", "X", "2", "1X", "X2", "12",
        "O1.5", "O2.5", "O3.5", "U1.5", "U2.5", "U3.5", "BTTS_Y", "BTTS_N"
    )
    private val OUTCOME_KEYS = setOf("1", "X", "2")

    private class Opinion(val label: String, val confidence: Double)

    fun compute(a: Analysis, maxPicks: Int = DEFAULT_MAX_PICKS): ConsensusResult =
        compute(a.homeTeam, a.awayTeam, a.brymo, a.poisson, a.stats, maxPicks)

    fun compute(
        home: String,
        away: String,
        brymo: BrymoResult,
        poisson: PoissonResult?,
        stats: StatsResult?,
        maxPicks: Int = DEFAULT_MAX_PICKS
    ): ConsensusResult {
        val opinions = opinionsOf(home, away, brymo, poisson, stats)
        val models = opinions.keys.toList()

        val picks = ArrayList<ConsensusPick>()
        if (models.size >= 2) {
            for (key in ALL_KEYS) {
                val backers = models.filter { opinions.getValue(it).containsKey(key) }
                if (backers.size < 2) continue
                val against = againstOf(key, backers, models, opinions)
                if (backers.size <= against.size) continue
                val abstain = models.filter { it !in backers && it !in against }
                val votes = backers.map { ConsensusVote(it, opinions.getValue(it).getValue(key).confidence) }
                val strength = votes.map { it.confidence }.average() / 100.0 *
                    (backers.size - 0.75 * against.size) / models.size
                picks.add(
                    ConsensusPick(
                        key, label(key, home, away), category(key), votes, against, abstain, models.size, strength
                    )
                )
            }
            picks.sortByDescending { it.strength }
        }
        if (picks.isNotEmpty()) return ConsensusResult(models, picks.take(maxPicks))

        // Fallback: the strongest single-model pick, from any market.
        var best: Triple<String, String, Opinion>? = null
        for ((model, ops) in opinions) {
            for ((key, op) in ops) {
                if (best == null || op.confidence > best.third.confidence) best = Triple(model, key, op)
            }
        }
        val single = best?.let { (model, key, op) ->
            val against = againstOf(key, listOf(model), models, opinions)
            ConsensusPick(
                key, op.label, category(key),
                listOf(ConsensusVote(model, op.confidence)),
                against, models.filter { it != model && it !in against }, models.size,
                op.confidence / 100.0 / models.size
            )
        }
        return ConsensusResult(models, listOfNotNull(single))
    }

    private fun againstOf(
        key: String,
        backers: List<String>,
        models: List<String>,
        opinions: Map<String, Map<String, Opinion>>
    ) = models.filter { m -> m !in backers && opinions.getValue(m).keys.any { exclusive(key, it) } }

    /**
     * Everything the user can tick or cross for a fixture: the consensus picks, then each model's
     * own strongest picks (so reports can compare the models).
     */
    fun trackable(
        home: String,
        away: String,
        brymo: BrymoResult,
        poisson: PoissonResult?,
        stats: StatsResult?,
        consensus: ConsensusResult
    ): List<TrackablePick> {
        val out = ArrayList<TrackablePick>()
        consensus.picks.forEach { out.add(TrackablePick("Consensus", it.key, it.label, it.group, it.averageConfidence)) }
        val opinions = opinionsOf(home, away, brymo, poisson, stats)
        for ((model, ops) in opinions) {
            ops.entries.sortedByDescending { it.value.confidence }.take(MAX_TRACKED_PER_MODEL).forEach { (key, op) ->
                out.add(TrackablePick(model, key, op.label, category(key), op.confidence))
            }
        }
        return out
    }

    // ---- what each model backs -------------------------------------------------------------

    private fun opinionsOf(
        home: String,
        away: String,
        brymo: BrymoResult,
        poisson: PoissonResult?,
        stats: StatsResult?
    ): Map<String, Map<String, Opinion>> {
        val out = LinkedHashMap<String, Map<String, Opinion>>()
        out["Brymo"] = brymoOpinions(home, away, brymo)
        poisson?.let { out["Poisson"] = poissonOpinions(home, away, it) }
        stats?.takeIf { it.hasPrediction }?.let { out["Stats"] = statsOpinions(home, away, it) }
        return out
    }

    private fun brymoOpinions(home: String, away: String, b: BrymoResult): Map<String, Opinion> {
        val out = LinkedHashMap<String, Opinion>()
        for (p in b.all) { // sorted by confidence, highest first
            val key = when (p.label) {
                "$home Win", "$home Likely" -> "1"
                "$away Win", "$away Likely" -> "2"
                "Draw Likely" -> "X"
                "Goals Market Likely (Over 2.5)" -> "O2.5"
                "Defensive Market Likely (Under 3.5)" -> "U3.5"
                "BTTS Yes Likely" -> "BTTS_Y"
                "BTTS No Likely" -> "BTTS_N"
                "Too Close to Call" -> continue
                else -> "B:${p.label}" // e.g. a +2 handicap pick: Brymo-only
            }
            if (key in OUTCOME_KEYS && out.keys.any { it in OUTCOME_KEYS }) continue
            if (key !in out) {
                val label = if (key.startsWith("B:")) p.label else label(key, home, away)
                out[key] = Opinion(label, p.confidencePercent)
            }
        }
        return out
    }

    private fun poissonOpinions(home: String, away: String, p: PoissonResult): Map<String, Opinion> {
        val out = LinkedHashMap<String, Opinion>()
        val top = when {
            p.homeWinPercent >= p.drawPercent && p.homeWinPercent >= p.awayWinPercent -> "1" to p.homeWinPercent
            p.awayWinPercent >= p.drawPercent -> "2" to p.awayWinPercent
            else -> "X" to p.drawPercent
        }
        out[top.first] = Opinion(label(top.first, home, away), top.second)
        listOf(
            "O1.5" to p.over15Percent, "U1.5" to 100 - p.over15Percent,
            "O2.5" to p.over25Percent, "U2.5" to p.under25Percent,
            "O3.5" to p.over35Percent, "U3.5" to 100 - p.over35Percent,
            "BTTS_Y" to p.bttsYesPercent, "BTTS_N" to p.bttsNoPercent
        ).forEach { (k, v) -> if (v >= BINARY_THRESHOLD) out[k] = Opinion(label(k, home, away), v) }
        listOf("1X" to p.homeOrDrawPercent, "X2" to p.awayOrDrawPercent, "12" to p.homeOrAwayPercent)
            .forEach { (k, v) -> if (v >= DOUBLE_CHANCE_THRESHOLD) out[k] = Opinion(label(k, home, away), v) }
        return out
    }

    private fun statsOpinions(home: String, away: String, s: StatsResult): Map<String, Opinion> {
        val out = LinkedHashMap<String, Opinion>()
        val o = s.outcome
        val pick = s.pick
        if (o != null && pick != null) {
            val k = when (o) { Outcome.HOME -> "1"; Outcome.DRAW -> "X"; Outcome.AWAY -> "2" }
            out[k] = Opinion(label(k, home, away), pick.confidencePercent)
        }
        s.markets.forEach { out[it.key] = Opinion(it.label, it.confidencePercent) }
        return out
    }

    // ---- logic -----------------------------------------------------------------------------

    /** True if [a] and [b] cannot both come true. */
    internal fun exclusive(a: String, b: String): Boolean {
        if (a == b) return false
        if (a in OUTCOME_KEYS && b in OUTCOME_KEYS) return true
        val rulesOut = mapOf("1X" to "2", "X2" to "1", "12" to "X")
        if (rulesOut[a] == b || rulesOut[b] == a) return true
        if ((a == "BTTS_Y" && b == "BTTS_N") || (a == "BTTS_N" && b == "BTTS_Y")) return true
        if (a.length > 1 && b.length > 1 && a[0] != b[0] && a[0] in "OU" && b[0] in "OU") {
            val (over, under) = if (a[0] == 'O') a to b else b to a
            val overLine = over.drop(1).toDoubleOrNull() ?: return false
            val underLine = under.drop(1).toDoubleOrNull() ?: return false
            return underLine <= overLine // Over 2.5 rules out Under 2.5 and Under 1.5, not Under 3.5
        }
        return false
    }

    /** Market type for a selection key, used to group results in reports. */
    fun category(key: String): String = when {
        key in OUTCOME_KEYS -> "1X2"
        key == "1X" || key == "X2" || key == "12" -> "Double chance"
        key.startsWith("BTTS") -> "BTTS"
        key.startsWith("C_") -> "Corners"
        key.startsWith("K_") -> "Cards"
        key.startsWith("FTS") -> "First to score"
        key.startsWith("WTN") -> "Win to nil"
        key.startsWith("B:") -> "Handicap / other"
        key.length > 1 && key[0] in "OU" -> "Goals"
        else -> "Other"
    }

    fun label(key: String, home: String, away: String): String = when (key) {
        "1" -> "$home win"
        "X" -> "Draw"
        "2" -> "$away win"
        "1X" -> "$home or draw (1X)"
        "X2" -> "$away or draw (X2)"
        "12" -> "Either team wins (12)"
        "BTTS_Y" -> "BTTS Yes"
        "BTTS_N" -> "BTTS No"
        else -> if (key.length > 1 && key[0] == 'O') "Over ${key.drop(1)} goals"
        else if (key.length > 1 && key[0] == 'U') "Under ${key.drop(1)} goals" else key
    }
}
