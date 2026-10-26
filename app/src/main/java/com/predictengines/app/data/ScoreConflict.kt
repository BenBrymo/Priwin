package com.predictengines.app.data

/**
 * A game whose score you entered by hand and whose score in the matches file is different.
 * Until you decide, your own entry is the one the app uses.
 */
data class ScoreConflict(
    val date: String,
    val home: String,
    val away: String,
    val mineHome: Int,
    val mineAway: Int,
    val csvHome: Int,
    val csvAway: Int
) {
    val key: String get() = "$date|$home|$away"
}
