package com.predictengines.app.data

/**
 * A prediction the user marked as correct or wrong. Stored with everything needed to report on it later,
 * so reports still work after the fixtures file is replaced.
 */
data class PredictionRecord(
    val id: String,
    val leagueId: String,
    val leagueName: String,
    /** Fixture date, yyyy-MM-dd (blank if the fixtures file had none). */
    val date: String,
    val home: String,
    val away: String,
    /** "Consensus", "Brymo", "Poisson" or "Stats". */
    val source: String,
    val key: String,
    val label: String,
    /** Market type: 1X2, Goals, Corners... */
    val group: String,
    val correct: Boolean,
    val markedAtMillis: Long
) {
    companion object {
        fun idOf(leagueId: String, date: String, home: String, away: String, source: String, key: String) =
            "$leagueId|$date|$home|$away|$source|$key"
    }
}
