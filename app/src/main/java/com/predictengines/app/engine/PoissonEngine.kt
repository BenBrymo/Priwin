package com.predictengines.app.engine

import com.predictengines.app.data.Match
import com.predictengines.app.data.PoissonResult
import com.predictengines.app.data.ScoreCell
import kotlin.math.exp

/**
 * Poisson goal model.
 *
 * 1. League averages: mean home goals (muHome) and mean away goals (muAway) from all matches.
 * 2. For each team, attack and defence strength = average of (goals scored / conceded) relative
 *    to what an average team would score / concede in that same venue role. Using every match
 *    (venue-adjusted) rather than only home or only away games keeps the sample usable.
 * 3. Strengths are shrunk toward 1.0 (league average) with [PRIOR_GAMES] pseudo-games, so a
 *    team with only a handful of results doesn't produce wild numbers.
 * 4. Expected goals:  home = muHome * homeAttack * awayDefence
 *                     away = muAway * awayAttack * homeDefence
 * 5. Each side's goals are Poisson distributed and independent, giving a full scoreline grid
 *    from which 1X2, double chance, Over/Under, BTTS and correct-score odds are summed.
 */
object PoissonEngine {

    const val MIN_LEAGUE_MATCHES = 10
    private const val MAX_GOALS = 10
    private const val PRIOR_GAMES = 6.0
    private const val HEATMAP_SIZE = 5
    private const val MIN_XG = 0.15
    private const val MAX_XG = 4.5

    private class Strength(val attack: Double, val defence: Double, val games: Int)

    private fun strengthOf(team: String, matches: List<Match>, muHome: Double, muAway: Double): Strength? {
        var attackSum = 0.0
        var defenceSum = 0.0
        var games = 0
        for (m in matches) {
            if (m.homeTeam == team) {
                attackSum += m.homeGoals / muHome
                defenceSum += m.awayGoals / muAway
                games++
            } else if (m.awayTeam == team) {
                attackSum += m.awayGoals / muAway
                defenceSum += m.homeGoals / muHome
                games++
            }
        }
        if (games == 0) return null
        return Strength(
            attack = (attackSum + PRIOR_GAMES) / (games + PRIOR_GAMES),
            defence = (defenceSum + PRIOR_GAMES) / (games + PRIOR_GAMES),
            games = games
        )
    }

    /** Poisson probabilities for 0..MAX_GOALS goals, built iteratively (no factorial overflow). */
    private fun pmf(lambda: Double): DoubleArray {
        val p = DoubleArray(MAX_GOALS + 1)
        p[0] = exp(-lambda)
        for (k in 1..MAX_GOALS) p[k] = p[k - 1] * lambda / k
        return p
    }

    fun predict(homeTeam: String, awayTeam: String, matches: List<Match>): PoissonResult? {
        if (matches.size < MIN_LEAGUE_MATCHES) return null

        val muHome = matches.map { it.homeGoals }.average()
        val muAway = matches.map { it.awayGoals }.average()
        if (muHome <= 0.0 || muAway <= 0.0) return null

        val home = strengthOf(homeTeam, matches, muHome, muAway) ?: return null
        val away = strengthOf(awayTeam, matches, muHome, muAway) ?: return null

        val xgHome = (muHome * home.attack * away.defence).coerceIn(MIN_XG, MAX_XG)
        val xgAway = (muAway * away.attack * home.defence).coerceIn(MIN_XG, MAX_XG)

        val ph = pmf(xgHome)
        val pa = pmf(xgAway)

        // Normalise so the truncated grid sums to exactly 1.
        var total = 0.0
        for (h in 0..MAX_GOALS) for (a in 0..MAX_GOALS) total += ph[h] * pa[a]

        var homeWin = 0.0
        var draw = 0.0
        var awayWin = 0.0
        var over15 = 0.0
        var over25 = 0.0
        var over35 = 0.0
        var bttsYes = 0.0
        val cells = ArrayList<ScoreCell>()
        val grid = List(HEATMAP_SIZE) { MutableList(HEATMAP_SIZE) { 0.0 } }

        for (h in 0..MAX_GOALS) {
            for (a in 0..MAX_GOALS) {
                val p = ph[h] * pa[a] / total
                when {
                    h > a -> homeWin += p
                    h == a -> draw += p
                    else -> awayWin += p
                }
                val goals = h + a
                if (goals > 1) over15 += p
                if (goals > 2) over25 += p
                if (goals > 3) over35 += p
                if (h > 0 && a > 0) bttsYes += p
                cells.add(ScoreCell(h, a, p * 100))
                if (h < HEATMAP_SIZE && a < HEATMAP_SIZE) grid[h][a] = p * 100
            }
        }

        return PoissonResult(
            expectedHomeGoals = xgHome,
            expectedAwayGoals = xgAway,
            homeWinPercent = homeWin * 100,
            drawPercent = draw * 100,
            awayWinPercent = awayWin * 100,
            homeOrDrawPercent = (homeWin + draw) * 100,
            awayOrDrawPercent = (awayWin + draw) * 100,
            homeOrAwayPercent = (homeWin + awayWin) * 100,
            over15Percent = over15 * 100,
            over25Percent = over25 * 100,
            over35Percent = over35 * 100,
            under25Percent = (1 - over25) * 100,
            bttsYesPercent = bttsYes * 100,
            bttsNoPercent = (1 - bttsYes) * 100,
            topScores = cells.sortedByDescending { it.probabilityPercent }.take(5),
            scoreGrid = grid,
            homeGamesUsed = home.games,
            awayGamesUsed = away.games
        )
    }
}
