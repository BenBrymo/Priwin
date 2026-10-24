package com.predictengines.app.engine

import com.predictengines.app.data.Match
import com.predictengines.app.data.TeamStats

/** Ported from the original desktop tool's printTeamStats(). */
object TeamStatsEngine {

    fun stats(team: String, matches: List<Match>): TeamStats {
        val relevant = matches.filter { it.homeTeam == team || it.awayTeam == team }
        val total = relevant.size.coerceAtLeast(1)

        var wins = 0
        var draws = 0
        var losses = 0
        var goalsFor = 0
        var goalsAgainst = 0
        var bttsYes = 0

        for (m in relevant) {
            val (scored, conceded) =
                if (m.homeTeam == team) m.homeGoals to m.awayGoals else m.awayGoals to m.homeGoals
            goalsFor += scored
            goalsAgainst += conceded
            when {
                scored > conceded -> wins++
                scored == conceded -> draws++
                else -> losses++
            }
            if (m.homeGoals > 0 && m.awayGoals > 0) bttsYes++
        }

        return TeamStats(
            teamName = team,
            played = relevant.size,
            wins = wins,
            draws = draws,
            losses = losses,
            avgGoalsFor = goalsFor.toDouble() / total,
            avgGoalsAgainst = goalsAgainst.toDouble() / total,
            bttsRatePercent = (bttsYes.toDouble() / total) * 100
        )
    }
}
