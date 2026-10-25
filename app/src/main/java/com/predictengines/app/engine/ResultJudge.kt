package com.predictengines.app.engine

import com.predictengines.app.data.FirstScorer
import com.predictengines.app.data.MatchExtras

/**
 * Decides whether a pick came true from the final score (plus optional extras). Returns null when it
 * can't be known, so that pick stays for the user to tick by hand.
 */
object ResultJudge {

    fun judge(key: String, home: String, away: String, hg: Int, ag: Int, x: MatchExtras?): Boolean? {
        val total = hg + ag
        return when {
            key == "1" -> hg > ag
            key == "X" -> hg == ag
            key == "2" -> ag > hg
            key == "1X" -> hg >= ag
            key == "X2" -> ag >= hg
            key == "12" -> hg != ag
            key == "BTTS_Y" -> hg > 0 && ag > 0
            key == "BTTS_N" -> !(hg > 0 && ag > 0)
            key == "WTN_H" -> hg > ag && ag == 0
            key == "WTN_A" -> ag > hg && hg == 0
            key == "FTS_H" -> firstToScore(true, hg, ag, x)
            key == "FTS_A" -> firstToScore(false, hg, ag, x)
            key.startsWith("C_") -> line(key.drop(2), x?.corners)
            key.startsWith("K_") -> line(key.drop(2), x?.cards)
            key.length > 1 && (key[0] == 'O' || key[0] == 'U') -> line(key, total)
            key.startsWith("B:") -> handicap(key.removePrefix("B:"), home, away, hg, ag)
            else -> null
        }
    }

    /** "O2.5" / "U9.5" against a total; null when the total isn't known. */
    private fun line(selection: String, total: Int?): Boolean? {
        if (total == null || selection.length < 2) return null
        val l = selection.drop(1).toDoubleOrNull() ?: return null
        return when (selection[0]) { 'O' -> total > l; 'U' -> total < l; else -> null }
    }

    private fun firstToScore(forHome: Boolean, hg: Int, ag: Int, x: MatchExtras?): Boolean? {
        val mine = if (forHome) hg else ag
        val theirs = if (forHome) ag else hg
        if (mine == 0) return false                 // never scored, so can't have scored first
        if (theirs == 0) return true                // only they scored
        val first = x?.first ?: return null
        return (first == FirstScorer.HOME) == forHome
    }

    /** Brymo's "<Team> +2 Handicap Likely": the team's goals plus 2 beat the opponent's. */
    private fun handicap(label: String, home: String, away: String, hg: Int, ag: Int): Boolean? {
        val suffix = " +2 Handicap Likely"
        if (!label.endsWith(suffix)) return null
        return when (label.removeSuffix(suffix)) {
            home -> hg + 2 > ag
            away -> ag + 2 > hg
            else -> null
        }
    }
}
