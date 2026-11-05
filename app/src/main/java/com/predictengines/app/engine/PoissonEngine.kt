package com.predictengines.app.engine

import com.predictengines.app.data.Match
import com.predictengines.app.data.PoissonCorners
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
 * 6. Corners use the same idea on corner counts (see [predictCorners]): expected corners per side, spread with a
 *    negative binomial, giving match-total and per-team corner lines.
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

    // ---- corners ---------------------------------------------------------------------------

    const val MIN_CORNER_MATCHES = 20
    private const val MIN_TEAM_CORNER_GAMES = 3
    private const val MAX_CORNERS = 40
    private const val MIN_CORNER_XC = 1.5
    private const val MAX_CORNER_XC = 12.0
    val TOTAL_CORNER_LINES = listOf(8.5, 9.5, 10.5, 11.5)
    val TEAM_CORNER_LINES = listOf(5.5, 6.5)

    /**
     * Negative binomial with the given mean and variance = dispersion x mean (dispersion near 1 falls back to Poisson).
     * Built iteratively like [pmf], and normalised over 0..MAX_CORNERS.
     */
    internal fun cornerPmf(mean: Double, dispersion: Double): DoubleArray {
        val p = DoubleArray(MAX_CORNERS + 1)
        if (dispersion < 1.02) {
            p[0] = exp(-mean)
            for (k in 1..MAX_CORNERS) p[k] = p[k - 1] * mean / k
        } else {
            val prob = 1.0 / dispersion
            val r = mean / (dispersion - 1.0)
            p[0] = Math.pow(prob, r)
            for (k in 1..MAX_CORNERS) p[k] = p[k - 1] * (k + r - 1) / k * (1 - prob)
        }
        val sum = p.sum()
        for (k in p.indices) p[k] /= sum
        return p
    }

    /** P(X > line) in percent. */
    private fun overLine(pmf: DoubleArray, line: Double): Double {
        var s = 0.0
        for (k in pmf.indices) if (k > line) s += pmf[k]
        return s * 100
    }

    private fun predictCorners(homeTeam: String, awayTeam: String, matches: List<Match>): PoissonCorners? {
        val cm = matches.filter { it.extras?.homeCorners != null && it.extras?.awayCorners != null }
        if (cm.size < MIN_CORNER_MATCHES) return null
        val muH = cm.map { it.extras!!.homeCorners!! }.average()
        val muA = cm.map { it.extras!!.awayCorners!! }.average()
        if (muH <= 0.0 || muA <= 0.0) return null

        // one pass: every team's corners won (attack) and conceded (defence), relative to its venue role
        class Acc(var att: Double = 0.0, var def: Double = 0.0, var games: Int = 0)
        val acc = HashMap<String, Acc>()
        for (m in cm) {
            val hc = m.extras!!.homeCorners!!
            val ac = m.extras!!.awayCorners!!
            val h = acc.getOrPut(m.homeTeam) { Acc() }
            h.att += hc / muH; h.def += ac / muA; h.games++
            val a = acc.getOrPut(m.awayTeam) { Acc() }
            a.att += ac / muA; a.def += hc / muH; a.games++
        }
        fun att(t: String) = acc[t]?.let { (it.att + PRIOR_GAMES) / (it.games + PRIOR_GAMES) } ?: 1.0
        fun def(t: String) = acc[t]?.let { (it.def + PRIOR_GAMES) / (it.games + PRIOR_GAMES) } ?: 1.0
        val hAcc = acc[homeTeam] ?: return null
        val aAcc = acc[awayTeam] ?: return null
        if (hAcc.games < MIN_TEAM_CORNER_GAMES || aAcc.games < MIN_TEAM_CORNER_GAMES) return null

        // how much wider than Poisson corners really are, from how far games land from what the model expected
        var chi = 0.0
        var n = 0
        for (m in cm) {
            val eh = (muH * att(m.homeTeam) * def(m.awayTeam)).coerceIn(MIN_CORNER_XC, MAX_CORNER_XC)
            val ea = (muA * att(m.awayTeam) * def(m.homeTeam)).coerceIn(MIN_CORNER_XC, MAX_CORNER_XC)
            val dh = m.extras!!.homeCorners!! - eh
            val da = m.extras!!.awayCorners!! - ea
            chi += dh * dh / eh + da * da / ea
            n += 2
        }
        val dispersion = (chi / n).coerceIn(1.0, 2.0)

        val xcHome = (muH * att(homeTeam) * def(awayTeam)).coerceIn(MIN_CORNER_XC, MAX_CORNER_XC)
        val xcAway = (muA * att(awayTeam) * def(homeTeam)).coerceIn(MIN_CORNER_XC, MAX_CORNER_XC)
        val ph = cornerPmf(xcHome, dispersion)
        val pa = cornerPmf(xcAway, dispersion)

        // match total = home + away corners (treated as independent)
        val total = DoubleArray(2 * MAX_CORNERS + 1)
        for (h in ph.indices) for (a in pa.indices) total[h + a] += ph[h] * pa[a]

        return PoissonCorners(
            expectedHome = xcHome,
            expectedAway = xcAway,
            totalOver = TOTAL_CORNER_LINES.associateWith { overLine(total, it) },
            homeOver = TEAM_CORNER_LINES.associateWith { overLine(ph, it) },
            awayOver = TEAM_CORNER_LINES.associateWith { overLine(pa, it) },
            homeGamesUsed = hAcc.games,
            awayGamesUsed = aAcc.games,
            dispersion = dispersion
        )
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
            awayGamesUsed = away.games,
            corners = predictCorners(homeTeam, awayTeam, matches)
        )
    }
}
