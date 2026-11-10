package com.predictengines.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.PerformanceReport
import com.predictengines.app.data.Trends
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState

/** The leagues and markets the Report calls Strong (and the cold ones to avoid), with this week's trend picks per league. */
@Composable
fun TrendSpots(state: AppUiState) {
    var showCold by rememberSaveable { mutableStateOf(false) }
    val strong = state.trendStrong
    val cold = state.trendCold
    val upcoming = state.trendPicks.groupingBy { it.leagueId }.eachCount()
    AppCard(
        title = "Where your data is strong",
        subtitle = if (strong.isEmpty())
            "No league or market is Strong in the Report yet (65%+ right, enough settled picks). Settle more picks, or change the Trend settings on the Data tab."
        else "${strong.size} strong places, as the Report judges them \u00b7 the picks below come from these",
        accentColor = WinGreen
    ) {
        val bandsOn = strong.filter { it.scope == "band" }.map { it.name }
        Text(
            when {
                !state.trendConfig.bandGate -> "Confidence gate is off: picks at any confidence are kept."
                bandsOn.isEmpty() -> "No confidence band is Strong over ${state.trendConfig.window.label.lowercase()}, so picks at any confidence are kept."
                else -> "Only picks at ${bandsOn.joinToString(" and ")} confidence are kept: the bands the Report marks Strong over ${state.trendConfig.window.label.lowercase()}."
            },
            style = MaterialTheme.typography.labelMedium, color = TextMuted
        )
        state.trendBacktest?.let { Text(Trends.verdict(it), style = MaterialTheme.typography.labelMedium, color = TextMuted) }
        strong.groupBy { it.leagueId }.forEach { (id, spots) ->
            Spacer(Modifier.height(6.dp))
            val n = upcoming[id] ?: 0
            Text(
                spots.first().leagueName + if (n > 0) " \u00b7 $n pick${if (n == 1) "" else "s"} coming" else "",
                style = MaterialTheme.typography.titleSmall, color = TextPrimary
            )
            spots.forEach { s ->
                Text(
                    (when {
                        s.scope == "market" -> "Strong market in every league: ${s.name}"
                        s.scope == "band" -> "Strong confidence band: ${s.name}"
                        s.isList -> "Strong on the ${s.list} list"
                        else -> "Strong market: ${s.name}"
                    }) + " \u00b7 ${s.line}",
                    style = MaterialTheme.typography.labelMedium, color = WinGreen
                )
            }
        }
        if (cold.isNotEmpty()) {
            TextButton(onClick = { showCold = !showCold }) {
                Text(if (showCold) "Hide cold spots" else "Show cold spots to avoid (${cold.size})", color = IndigoPrimary)
            }
            if (showCold) cold.groupBy { it.leagueName }.forEach { (league, spots) ->
                Text(league, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(top = 6.dp))
                spots.forEach { s ->
                    Text("${s.name} \u00b7 ${s.line}", style = MaterialTheme.typography.labelMedium, color = LossRed)
                }
            }
        }
    }
}

/** Data tab: what counts as a strong spot, which picks count, and how the filter has done so far. */
@Composable
fun TrendSettings(state: AppUiState, onSet: (Trends.Config) -> Unit) {
    val c = state.trendConfig
    @Composable
    fun Stepper(label: String, value: Int, step: Int, min: Int, max: Int, change: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = { change((value - step).coerceAtLeast(min)) }, enabled = value > min) {
                Text("\u2212", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
            }
            Text(value.toString(), style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(horizontal = 6.dp))
            TextButton(onClick = { change((value + step).coerceAtMost(max)) }, enabled = value < max) {
                Text("+", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Strong leagues count too", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    "Keep every pick a list makes in a league the Report calls Strong on that list (Over 1.5 in the Bundesliga), not only single strong markets.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted
                )
            }
            Switch(checked = c.leagueSpots, onCheckedChange = { onSet(c.copy(leagueSpots = it)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Only strong confidence bands", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    "When the Report marks a confidence band Strong (for example 60-69%), keep only picks inside it. Picks at other confidence levels are left out. With no strong band, this does nothing.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted
                )
            }
            Switch(checked = c.bandGate, onCheckedChange = { onSet(c.copy(bandGate = it)) })
        }
        val windows = PerformanceReport.Window.entries
        TextButton(onClick = { onSet(c.copy(window = windows[(windows.indexOf(c.window) + 1) % windows.size])) }) {
            Text("Results counted: ${c.window.label} (tap to change)", color = IndigoPrimary)
        }
        Stepper("Fewest models behind a pick", c.minBackers, 1, 1, 3) { onSet(c.copy(minBackers = it)) }
        Stepper("Lowest average confidence %", c.minConfidence, 5, 50, 90) { onSet(c.copy(minConfidence = it)) }
        Text(
            "Strong is the Report's own verdict: 65%+ right, a score of 50+ after allowing for few picks, with 8+ settled picks for a league or 5+ for a market. " +
                "Trend picks also need the model support set here.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted
        )
        state.trendBacktest?.let { b ->
            Spacer(Modifier.height(10.dp))
            Text("How the filter has done", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(Trends.verdict(b), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(
                "In a strong spot: ${b.strong.correct}/${b.strong.total} \u00b7 in a cold spot: ${b.cold.correct}/${b.cold.total} \u00b7 everything else: ${b.other.correct}/${b.other.total}. " +
                    "Each past pick is judged only by the picks settled before its game.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted
            )
        }
    }
}
