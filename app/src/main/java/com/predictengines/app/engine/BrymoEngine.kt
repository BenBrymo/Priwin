package com.predictengines.app.engine

import com.predictengines.app.data.BrymoResult
import com.predictengines.app.data.Match
import com.predictengines.app.data.Prediction
import kotlin.math.max

/**
 * Tag-based heuristic engine, ported from the original "Brymo's Model" desktop tool
 * (analyzeTeam + predictMatch). Each team is tagged from its historical record
 * (Wins/Draws/Losses/Balanced, scoring tendencies, BTTS, +2 handicap), and matchups
 * between tag combinations are translated into labeled, confidence-scored predictions.
 */
object BrymoEngine {

    private data class TeamTags(
        val teamName: String,
        val tags: MutableList<String> = mutableListOf(),
        val confidence: MutableMap<String, Double> = mutableMapOf()
    )

    private fun analyzeTeam(team: String, matches: List<Match>): TeamTags {
        val relevant = matches.filter { it.homeTeam == team || it.awayTeam == team }
        val tags = TeamTags(team)
        val totalMatches = relevant.size.toDouble().coerceAtLeast(1.0)
        val goals = relevant.map {
            if (it.homeTeam == team) it.homeGoals + it.awayGoals else it.awayGoals + it.homeGoals
        }

        var wins = 0
        var draws = 0
        var losses = 0

        for (m in relevant) {
            val (scored, conceded) =
                if (m.homeTeam == team) m.homeGoals to m.awayGoals else m.awayGoals to m.homeGoals
            when {
                scored > conceded -> wins++
                scored == conceded -> draws++
                else -> losses++
            }
        }

        val winRate = wins / totalMatches
        val drawRate = draws / totalMatches
        val lossRate = losses / totalMatches

        when {
            winRate > 0.5 -> {
                tags.tags.add("Wins"); tags.confidence["Wins"] = winRate * 100
            }
            drawRate > 0.5 -> {
                tags.tags.add("Draws"); tags.confidence["Draws"] = drawRate * 100
            }
            lossRate > 0.5 -> {
                tags.tags.add("Losses"); tags.confidence["Losses"] = lossRate * 100
            }
            else -> {
                tags.tags.add("Balanced")
                tags.confidence["Balanced"] = maxOf(winRate, drawRate, lossRate) * 100
            }
        }

        val over25 = goals.count { it >= 3 }
        val under35 = goals.count { it < 3 }
        val over15 = goals.count { it > 1 }

        if (over25 > 0) tags.tags.add("Over 2.5").also { tags.confidence["Over 2.5"] = (over25 / totalMatches) * 100 }
        if (under35 > 0) tags.tags.add("Under 3.5").also { tags.confidence["Under 3.5"] = (under35 / totalMatches) * 100 }
        if (over15 > 0) tags.tags.add("Over 1.5").also { tags.confidence["Over 1.5"] = (over15 / totalMatches) * 100 }

        val avgGoals = if (goals.isNotEmpty()) goals.average() else 0.0
        if (avgGoals >= 3.0) {
            tags.tags.add("High Scoring")
            tags.confidence["High Scoring"] = (over25 / totalMatches) * 100
        } else {
            tags.tags.add("Low Scoring")
            tags.confidence["Low Scoring"] = (under35 / totalMatches) * 100
        }

        val handicapWin = relevant.count {
            val scored = if (it.homeTeam == team) it.homeGoals else it.awayGoals
            val conceded = if (it.homeTeam == team) it.awayGoals else it.homeGoals
            scored + 2 > conceded
        }
        if (handicapWin > 0) {
            tags.tags.add("+2 Handicap Wins")
            tags.confidence["+2 Handicap Wins"] = (handicapWin / totalMatches) * 100
        }

        val bttsYes = relevant.count { it.homeGoals > 0 && it.awayGoals > 0 }
        val bttsNo = relevant.count { it.homeGoals == 0 || it.awayGoals == 0 }
        if (bttsYes > 0) {
            tags.tags.add("BTTS Yes")
            tags.confidence["BTTS Yes"] = (bttsYes / totalMatches) * 100
        }
        if (bttsNo > 0) {
            tags.tags.add("BTTS No")
            tags.confidence["BTTS No"] = (bttsNo / totalMatches) * 100
        }

        return tags
    }

    private fun predictMatch(home: TeamTags, away: TeamTags, homeTeam: String, awayTeam: String): BrymoResult {
        val predictions = mutableMapOf<String, Double>()

        when {
            "Wins" in home.tags && ("Losses" in away.tags || "Balanced" in away.tags) ->
                predictions["$homeTeam Win"] = home.confidence["Wins"] ?: 0.0

            "Wins" in away.tags && ("Losses" in home.tags || "Balanced" in home.tags) ->
                predictions["$awayTeam Win"] = away.confidence["Wins"] ?: 0.0

            "Draws" in home.tags && "Draws" in away.tags ->
                predictions["Draw Likely"] = ((home.confidence["Draws"] ?: 0.0) + (away.confidence["Draws"] ?: 0.0)) / 2

            "Balanced" in home.tags && "Balanced" in away.tags ->
                predictions["Too Close to Call"] = maxOf(
                    home.confidence["Balanced"] ?: 0.0,
                    away.confidence["Balanced"] ?: 0.0
                )

            "Balanced" in home.tags && ("Losses" in away.tags || "Draws" in away.tags) ->
                predictions["$homeTeam Likely"] = home.confidence["Balanced"] ?: 0.0

            "Balanced" in away.tags && ("Losses" in home.tags || "Draws" in home.tags) ->
                predictions["$awayTeam Likely"] = away.confidence["Balanced"] ?: 0.0
        }

        if ("High Scoring" in home.tags && "High Scoring" in away.tags)
            predictions["Goals Market Likely (Over 2.5)"] = max(
                home.confidence["High Scoring"] ?: 0.0,
                away.confidence["High Scoring"] ?: 0.0
            )

        if ("Low Scoring" in home.tags && "Low Scoring" in away.tags)
            predictions["Defensive Market Likely (Under 3.5)"] = max(
                home.confidence["Low Scoring"] ?: 0.0,
                away.confidence["Low Scoring"] ?: 0.0
            )

        if ("+2 Handicap Wins" in home.tags)
            predictions["$homeTeam +2 Handicap Likely"] = home.confidence["+2 Handicap Wins"] ?: 0.0

        if ("+2 Handicap Wins" in away.tags)
            predictions["$awayTeam +2 Handicap Likely"] = away.confidence["+2 Handicap Wins"] ?: 0.0

        if ("BTTS Yes" in home.tags && "BTTS Yes" in away.tags)
            predictions["BTTS Yes Likely"] = max(
                home.confidence["BTTS Yes"] ?: 0.0,
                away.confidence["BTTS Yes"] ?: 0.0
            )

        if ("BTTS No" in home.tags && "BTTS No" in away.tags)
            predictions["BTTS No Likely"] = max(
                home.confidence["BTTS No"] ?: 0.0,
                away.confidence["BTTS No"] ?: 0.0
            )

        val all = predictions.entries
            .sortedByDescending { it.value }
            .map { Prediction(it.key, it.value) }

        val maxConf = predictions.values.maxOrNull() ?: 0.0
        val best = predictions.filterValues { it == maxConf }
            .map { Prediction(it.key, it.value) }

        return BrymoResult(home.tags.toList(), away.tags.toList(), all, best)
    }

    fun predict(homeTeam: String, awayTeam: String, matches: List<Match>): BrymoResult {
        val homeTags = analyzeTeam(homeTeam, matches)
        val awayTags = analyzeTeam(awayTeam, matches)
        return predictMatch(homeTags, awayTags, homeTeam, awayTeam)
    }
}
