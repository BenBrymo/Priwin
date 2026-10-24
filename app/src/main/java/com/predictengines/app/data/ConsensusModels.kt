package com.predictengines.app.data

/** One model backing a consensus pick, with how confident it is (scales differ per model). */
data class ConsensusVote(val model: String, val confidence: Double)

/**
 * A bet the models agree on (or, when no bet has two backers, the strongest single-model pick).
 * [key] is stable ("O2.5", "1X", "C_O9.5"...), [group] is the market type used by reports.
 */
data class ConsensusPick(
    val key: String,
    val label: String,
    val group: String,
    val backers: List<ConsensusVote>,
    val against: List<String>,
    val abstain: List<String>,
    val modelCount: Int,
    val strength: Double
) {
    val averageConfidence: Double get() = backers.map { it.confidence }.average()
    val isSingleModel: Boolean get() = backers.size == 1
}

data class ConsensusResult(val models: List<String>, val picks: List<ConsensusPick>)

/** A prediction the user can tick or cross: the consensus or one model's own pick. */
data class TrackablePick(
    /** "Consensus", "Brymo", "Poisson" or "Stats". */
    val source: String,
    val key: String,
    val label: String,
    val group: String,
    val confidence: Double
)

/** One entry in the cross-league "Top picks" list: the best consensus pick of an upcoming fixture. */
data class TopPick(
    val leagueId: String,
    val leagueName: String,
    /** Fixture date (yyyy-MM-dd) and time, as shown in the fixtures list. */
    val date: String,
    val time: String,
    /** Names as written in the fixtures file (for display). */
    val homeDisplay: String,
    val awayDisplay: String,
    /** Names resolved to the matches file (used to find the fixture again). */
    val home: String,
    val away: String,
    val pick: ConsensusPick
)
