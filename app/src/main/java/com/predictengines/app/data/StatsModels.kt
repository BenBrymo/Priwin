package com.predictengines.app.data

/** 1X2 outcome, shared by all three models and the consensus card. */
enum class Outcome { HOME, DRAW, AWAY }

/** One row of a stats file (statschecker-style): a team's record for one market. */
data class StatRow(
    val market: String,
    val season: String,
    val rank: Int?,
    val team: String,
    val value: Double,
    val games: Int,
    val percent: Double?
)

/** One stat that the Stats model actually used for a matchup, shown for transparency. */
data class StatSignal(
    val team: String,
    val market: String,
    val season: String,
    val value: Double,
    val games: Int,
    val ratePercent: Double,
    /** What the stat argues for, e.g. "Arsenal win" or "Over 2.5 goals". */
    val supports: String
)

/** One market call from the Stats model. [key] is stable (e.g. "O2.5", "BTTS_Y", "1X") for the consensus. */
data class StatPick(
    val key: String,
    val group: String,
    val label: String,
    val confidencePercent: Double
)

data class StatsResult(
    /** Team names as written in the stats file; null when the team isn't in it. */
    val homeMatched: String?,
    val awayMatched: String?,
    /** Evidence strength per outcome, 0..1 (not a probability). */
    val homeEvidence: Double,
    val drawEvidence: Double,
    val awayEvidence: Double,
    val outcome: Outcome?,
    /** The 1X2 call, labelled like Brymo's ("Arsenal Win", "Draw Likely"), if there is a clear one. */
    val pick: Prediction?,
    /** Market calls (goals, double chance, corners, cards, first to score, win to nil), strongest first. */
    val markets: List<StatPick>,
    val signals: List<StatSignal>
) {
    val hasData: Boolean get() = signals.isNotEmpty()

    /** True when the model has something to say: a 1X2 pick or at least one market pick. */
    val hasPrediction: Boolean get() = pick != null || markets.isNotEmpty()
}
