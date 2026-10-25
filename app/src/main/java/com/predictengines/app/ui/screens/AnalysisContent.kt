package com.predictengines.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.Analysis
import com.predictengines.app.ui.components.BestPickChip
import com.predictengines.app.data.BrymoResult
import com.predictengines.app.data.ConsensusPick
import com.predictengines.app.data.Outcome
import com.predictengines.app.data.PredictionRecord
import com.predictengines.app.data.TrackablePick
import com.predictengines.app.data.PoissonResult
import com.predictengines.app.data.Prediction
import com.predictengines.app.data.StatsResult
import com.predictengines.app.ui.components.MarkButtons
import com.predictengines.app.ui.components.NamePickerDialog
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.ConfidenceBar
import com.predictengines.app.ui.components.MarketRow
import com.predictengines.app.ui.components.MarketsTable
import com.predictengines.app.ui.components.ScoreGridTable
import com.predictengines.app.ui.components.ScoreHeatmap
import com.predictengines.app.ui.components.StatsComparisonTable
import com.predictengines.app.ui.components.TagChip
import com.predictengines.app.ui.components.ThreeWayBar
import com.predictengines.app.ui.theme.BrymoAccent
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimaryDim
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.PoissonAccent
import com.predictengines.app.ui.theme.StatsAccent
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.VioletAccent
import com.predictengines.app.ui.theme.WinGreen
import java.util.Locale
import kotlin.math.roundToInt

/** All three models' output for one matchup, then their consensus. Shared by the fixture detail and manual matchup screens. */
@Composable
fun AnalysisContent(
    analysis: Analysis,
    marks: Map<String, PredictionRecord> = emptyMap(),
    leagueId: String = "",
    /** When set (fixture detail), a "Mark results" card lets the user tick or cross each prediction. */
    onMark: ((TrackablePick, Boolean?) -> Unit)? = null,
    /** Every team name in the stats data; with [onLinkStatsName] it lets the user link an unrecognised team. */
    statsTeams: List<String> = emptyList(),
    onLinkStatsName: ((name: String, target: String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MatchBanner(analysis)
        BrymoCard(analysis.brymo, analysis.homeTeam, analysis.awayTeam, analysis.modelBest["Brymo"])
        val poisson = analysis.poisson
        if (poisson != null) {
            PoissonCard(poisson, analysis.homeTeam, analysis.awayTeam, analysis.modelBest["Poisson"])
        } else {
            AppCard(title = "Poisson Model", subtitle = "Not enough data", accentColor = PoissonAccent) {
                Text(
                    "The Poisson model needs at least 10 played matches in this league and results for both teams. " +
                        "Load a bigger matches file from the Data tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        StatsCard(analysis.stats, analysis.homeTeam, analysis.awayTeam, statsTeams, onLinkStatsName, analysis.modelBest["Stats"])
        ConsensusCard(analysis)
        if (onMark != null && analysis.trackable.isNotEmpty()) {
            ResultsCard(analysis, marks, leagueId, onMark)
        }
        AppCard(title = "Head-to-Head Form", accentColor = VioletAccent) {
            StatsComparisonTable(analysis.homeStats, analysis.awayStats)
        }
    }
}

@Composable
private fun MatchBanner(analysis: Analysis) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(IndigoPrimaryDim, VioletAccent)))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (analysis.kickoff != null) {
            Text(analysis.kickoff, style = MaterialTheme.typography.labelLarge, color = TextPrimary.copy(alpha = 0.8f))
            Spacer(Modifier.height(10.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                analysis.homeTeam,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                "VS",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary.copy(alpha = 0.7f)
            )
            Text(
                analysis.awayTeam,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }
    }
}

// ---- consensus ---------------------------------------------------------------------------

private fun brymoOutcome(brymo: BrymoResult, home: String, away: String): Outcome? {
    for (p in brymo.all) { // already sorted by confidence, highest first
        when (p.label) {
            "$home Win", "$home Likely" -> return Outcome.HOME
            "$away Win", "$away Likely" -> return Outcome.AWAY
            "Draw Likely" -> return Outcome.DRAW
        }
    }
    return null
}

private fun poissonOutcome(p: PoissonResult): Outcome = when {
    p.homeWinPercent >= p.drawPercent && p.homeWinPercent >= p.awayWinPercent -> Outcome.HOME
    p.awayWinPercent >= p.drawPercent -> Outcome.AWAY
    else -> Outcome.DRAW
}

private fun describe(o: Outcome, home: String, away: String): String = when (o) {
    Outcome.HOME -> "$home win"
    Outcome.DRAW -> "a draw"
    Outcome.AWAY -> "$away win"
}

/** Each model's 1X2 call, in display order. Models without a call are left out. */
private fun votes(a: Analysis): List<Pair<String, Outcome>> = listOfNotNull(
    brymoOutcome(a.brymo, a.homeTeam, a.awayTeam)?.let { "Brymo" to it },
    a.poisson?.let { "Poisson" to poissonOutcome(it) },
    a.stats?.outcome?.let { "Stats" to it }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsensusCard(analysis: Analysis) {
    val home = analysis.homeTeam
    val away = analysis.awayTeam
    val votes = votes(analysis)
    val consensus = analysis.consensus
    if (votes.isEmpty() && consensus.picks.isEmpty()) return

    val groups = votes.groupBy({ it.second }, { it.first })
    val leader = groups.entries.maxByOrNull { it.value.size }
    val total = votes.size

    val color: Color
    val headline: String
    val detail: String
    when {
        leader == null -> {
            color = TextSecondary
            headline = "No clear 1X2 call"
            detail = "None of the models has a 1X2 signal for this matchup."
        }
        total == 1 -> {
            color = TextSecondary
            headline = "${votes[0].first} leans ${describe(votes[0].second, home, away)}"
            detail = "Only one model has a 1X2 signal for this matchup."
        }
        groups.size == 1 -> {
            color = WinGreen
            headline = (if (total == 3) "All three models agree: " else "Both models agree: ") +
                describe(leader!!.key, home, away)
            detail = votes.joinToString(" & ") { it.first } + " point to the same 1X2 outcome."
        }
        leader!!.value.size >= 2 -> {
            color = DrawAmber
            headline = "Majority (${leader.value.size} of $total): ${describe(leader!!.key, home, away)}"
            detail = groups.entries.joinToString(". ") { (o, names) ->
                names.joinToString(" & ") + ": " + describe(o, home, away)
            } + "."
        }
        else -> {
            color = LossRed
            headline = if (total == 3) "No consensus: all three differ" else "Models disagree"
            detail = votes.joinToString(". ") { (name, o) -> "$name: ${describe(o, home, away)}" } + "."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(16.dp)
    ) {
        Text("Consensus", style = MaterialTheme.typography.labelLarge, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(headline, style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Brymo", "Poisson", "Stats").forEach { name ->
                val vote = votes.firstOrNull { it.first == name }
                TagChip(
                    text = if (vote != null) "$name: ${describe(vote.second, home, away)}" else "$name: no call",
                    color = if (vote != null) modelAccent(name) else TextMuted
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Best picks across models", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        if (consensus.picks.isEmpty()) {
            Text(
                if (consensus.models.size < 2) "Needs at least two models with data to compare."
                else "No pick is available for this matchup.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        } else {
            if (consensus.picks.all { it.isSingleModel }) {
                Text(
                    "No bet is backed by two models, so this is the strongest single-model pick.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DrawAmber
                )
                Spacer(Modifier.height(4.dp))
            }
            consensus.picks.forEachIndexed { i, pick -> ConsensusPickRow(pick, best = i == 0) }
            Spacer(Modifier.height(4.dp))
            Text(
                "Ranked by agreement and average confidence. A model that backs something that rules a pick out counts against it.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

private fun modelAccent(name: String): Color = when (name) {
    "Brymo" -> BrymoAccent
    "Poisson" -> PoissonAccent
    else -> StatsAccent
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsensusPickRow(pick: ConsensusPick, best: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pick.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (best) BestPickChip(pick)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            (if (pick.isSingleModel) {
                "${pick.backers[0].model} only · ${pick.backers[0].confidence.roundToInt()}%"
            } else {
                "${pick.backers.size} of ${pick.modelCount} models · avg ${pick.averageConfidence.roundToInt()}%"
            }) + if (pick.against.isNotEmpty()) " · against: ${pick.against.joinToString()}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pick.backers.forEach { TagChip("${it.model} ${it.confidence.roundToInt()}%", modelAccent(it.model)) }
            pick.abstain.forEach { TagChip("$it: no call", TextMuted) }
            pick.against.forEach { TagChip("$it: against", LossRed) }
        }
    }
}

// ---- mark results ------------------------------------------------------------------------

@Composable
private fun ResultsCard(
    analysis: Analysis,
    marks: Map<String, PredictionRecord>,
    leagueId: String,
    onMark: (TrackablePick, Boolean?) -> Unit
) {
    AppCard(
        title = "Mark results",
        subtitle = "Tick what came true, cross what didn't. Saved on your phone and used in the weekly report.",
        accentColor = WinGreen
    ) {
        analysis.trackable.groupBy { it.source }.forEach { (source, picks) ->
            val heading = when (source) {
                "Consensus" -> "Consensus"
                "Poisson" -> "Poisson · best ${picks.size}"
                else -> "$source · best ${picks.size}"
            }
            Text(heading, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            picks.forEach { pick ->
                val id = PredictionRecord.idOf(
                    leagueId, analysis.date, analysis.homeTeam, analysis.awayTeam, pick.source, pick.key
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pick.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    MarkButtons(state = marks[id]?.correct, onChange = { onMark(pick, it) })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---- Brymo -------------------------------------------------------------------------------

/** A model's three best calls, shown at the top of its card. */
@Composable
private fun BestThree(top: List<Prediction>?, color: androidx.compose.ui.graphics.Color, count: Int = 3) {
    val title = "Best $count picks (highest %)"
    Text(title, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    Spacer(Modifier.height(4.dp))
    if (top.isNullOrEmpty()) {
        Text(
            "No pick available for this game.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted
        )
    } else {
        top.forEach { ConfidenceBar(it.label, it.confidencePercent, color) }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun BrymoCard(brymo: BrymoResult, homeTeam: String, awayTeam: String, top: List<Prediction>?) {
    AppCard(
        title = "Brymo Model",
        subtitle = "Tag-based heuristic engine",
        accentColor = BrymoAccent
    ) {
        BestThree(top, color = BrymoAccent)
        Text("Team tags", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(homeTeam, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        FlowChips(brymo.homeTags)
        Spacer(Modifier.height(10.dp))
        Text(awayTeam, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        FlowChips(brymo.awayTags)

        if (brymo.all.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "No strong signal found for this matchup.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            return@AppCard
        }

        Spacer(Modifier.height(16.dp))
        Text(
            if (brymo.best.size > 1) "Best predictions" else "Best prediction",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        Spacer(Modifier.height(4.dp))
        brymo.best.forEach { ConfidenceBar(it.label, it.confidencePercent, BrymoAccent) }

        if (brymo.all.size > brymo.best.size) {
            Spacer(Modifier.height(10.dp))
            Text("Other signals", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            brymo.all.filter { p -> brymo.best.none { it.label == p.label } }.forEach {
                ConfidenceBar(it.label, it.confidencePercent, TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { TagChip(text = it, color = BrymoAccent) }
    }
}

// ---- Poisson -----------------------------------------------------------------------------

@Composable
private fun PoissonCard(p: PoissonResult, homeTeam: String, awayTeam: String, top: List<Prediction>?) {
    AppCard(
        title = "Poisson Model",
        subtitle = "Statistical goal-expectancy engine",
        accentColor = PoissonAccent
    ) {
        BestThree(top, color = PoissonAccent, count = 5)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ExpectedGoals(homeTeam, p.expectedHomeGoals, Modifier.weight(1f))
            Text("xG", color = TextMuted, style = MaterialTheme.typography.labelLarge)
            ExpectedGoals(awayTeam, p.expectedAwayGoals, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Based on ${p.homeGamesUsed} / ${p.awayGamesUsed} matches" +
                if (minOf(p.homeGamesUsed, p.awayGamesUsed) < 6) " · small sample, treat with caution" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (minOf(p.homeGamesUsed, p.awayGamesUsed) < 6) DrawAmber else TextMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))
        ThreeWayBar(
            homeLabel = homeTeam,
            homePercent = p.homeWinPercent,
            drawPercent = p.drawPercent,
            awayLabel = awayTeam,
            awayPercent = p.awayWinPercent
        )

        Spacer(Modifier.height(20.dp))
        Text("Markets", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        MarketsTable(
            rows = listOf(
                MarketRow("$homeTeam win", p.homeWinPercent),
                MarketRow("Draw", p.drawPercent),
                MarketRow("$awayTeam win", p.awayWinPercent),
                MarketRow("Double chance 1X", p.homeOrDrawPercent),
                MarketRow("Double chance X2", p.awayOrDrawPercent),
                MarketRow("Double chance 12", p.homeOrAwayPercent),
                MarketRow("Over 1.5 goals", p.over15Percent),
                MarketRow("Over 2.5 goals", p.over25Percent),
                MarketRow("Under 2.5 goals", p.under25Percent),
                MarketRow("Over 3.5 goals", p.over35Percent),
                MarketRow("BTTS Yes", p.bttsYesPercent),
                MarketRow("BTTS No", p.bttsNoPercent)
            ),
            accent = PoissonAccent
        )

        Spacer(Modifier.height(20.dp))
        Text("Scoreline map (%)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        ScoreHeatmap(p.scoreGrid, homeTeam, awayTeam)

        Spacer(Modifier.height(20.dp))
        Text("Most likely scorelines", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        ScoreGridTable(p.topScores)

        Spacer(Modifier.height(14.dp))
        Text(
            "Fair odds have no bookmaker margin built in. Compare them with real odds to look for value.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

// ---- Stats -------------------------------------------------------------------------------

@Composable
private fun StatsCard(
    stats: StatsResult?,
    homeTeam: String,
    awayTeam: String,
    statsTeams: List<String>,
    onLink: ((name: String, target: String) -> Unit)?,
    top: List<Prediction>?
) {
    var linking by remember { mutableStateOf<String?>(null) }
    AppCard(
        title = "Stats Model",
        subtitle = "Reads the built-in stats and any file you loaded",
        accentColor = StatsAccent
    ) {
        if (stats == null) {
            Text(
                "No stats data loaded. Open the Data tab and load a stats CSV (market, season, rank, team, " +
                    "stat_value, games, percentage) to get this model's prediction.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            return@AppCard
        }

        BestThree(top, color = StatsAccent)
        val missing = listOfNotNull(
            homeTeam.takeIf { stats.homeMatched == null },
            awayTeam.takeIf { stats.awayMatched == null }
        )
        if (missing.isNotEmpty()) {
            Text(
                "Not found in the stats data: ${missing.joinToString(" and ")}.",
                style = MaterialTheme.typography.labelMedium,
                color = if (missing.size == 2) LossRed else DrawAmber
            )
            if (onLink != null && statsTeams.isNotEmpty()) {
                missing.forEach { name ->
                    TextButton(onClick = { linking = name }) {
                        Text("Link \"$name\" to a team in the stats data", color = StatsAccent)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (!stats.hasData) {
            Text(
                "The stats file has nothing useful for this matchup. It may not include these teams, " +
                    "or only markets this model doesn't use.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            return@AppCard
        }

        Text("1X2 evidence", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        ConfidenceBar("$homeTeam win", stats.homeEvidence * 100, StatsAccent)
        ConfidenceBar("Draw", stats.drawEvidence * 100, StatsAccent)
        ConfidenceBar("$awayTeam win", stats.awayEvidence * 100, StatsAccent)
        Spacer(Modifier.height(8.dp))
        val pick = stats.pick
        if (pick != null) {
            Text("Stats pick", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            ConfidenceBar(pick.label, pick.confidencePercent, StatsAccent)
        } else {
            Text(
                "No clear 1X2 pick: the stats don't favour one outcome.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        if (stats.markets.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            stats.markets.groupBy { it.group }.forEach { (group, picks) ->
                Text(group, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                picks.forEach { ConfidenceBar(it.label, it.confidencePercent, StatsAccent) }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Stats used", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        stats.signals.forEach { sig ->
            Text(
                "${sig.team} · ${sig.market}: ${formatCount(sig.value)}/${sig.games} " +
                    "(${sig.ratePercent.roundToInt()}%) → ${sig.supports}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Evidence is a strength score, not a probability. Small samples count for less, and a team " +
                "missing from a market just means no information.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }

    linking?.let { name ->
        NamePickerDialog(
            name = name,
            candidates = statsTeams,
            sourceDescription = "the stats data",
            onPick = { target ->
                onLink?.invoke(name, target)
                linking = null
            },
            onDismiss = { linking = null }
        )
    }
}

private fun formatCount(v: Double): String =
    if (v == Math.rint(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

@Composable
private fun ExpectedGoals(team: String, value: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            String.format(Locale.US, "%.2f", value),
            style = MaterialTheme.typography.headlineMedium,
            color = PoissonAccent,
            fontWeight = FontWeight.Bold
        )
        Text(
            team,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
