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

/** The strong spots (league and market pairs the data backs) and the cold ones to avoid, shown above the Trend picks. */
@Composable
fun TrendSpots(state: AppUiState) {
    var showCold by rememberSaveable { mutableStateOf(false) }
    val strong = state.trendStrong
    val cold = state.trendCold
    AppCard(
        title = "Where your data is strong",
        subtitle = if (strong.isEmpty())
            "No league and market has ${state.trendConfig.minPicks}+ settled picks with a score of ${state.trendConfig.minScore}+ yet. Lower the score or switch off 'Must beat the other leagues' on the Data tab to see more."
        else "${strong.size} spots · settled picks from your weekly lists, each game's selection counted once · score allows for how few picks there are",
        accentColor = WinGreen
    ) {
        state.trendBacktest?.let { Text(Trends.verdict(it), style = MaterialTheme.typography.labelMedium, color = TextMuted) }
        strong.groupBy { it.leagueName }.forEach { (league, spots) ->
            Spacer(Modifier.height(6.dp))
            Text(league, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            spots.forEach { s ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text(
                        "${s.tally.correct}/${s.tally.total} · ${Math.round(s.tally.rate)}% · score ${Math.round(s.score)}",
                        style = MaterialTheme.typography.labelSmall, color = WinGreen
                    )
                }
            }
        }
        if (cold.isNotEmpty()) {
            TextButton(onClick = { showCold = !showCold }) {
                Text(if (showCold) "Hide cold spots" else "Show cold spots to avoid (${cold.size})", color = IndigoPrimary)
            }
            if (showCold) cold.groupBy { it.leagueName }.forEach { (league, spots) ->
                Text(league, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(top = 6.dp))
                spots.forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("${s.tally.correct}/${s.tally.total} · ${Math.round(s.tally.rate)}%", style = MaterialTheme.typography.labelSmall, color = LossRed)
                    }
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
        Stepper("Fewest settled picks", c.minPicks, 1, 5, 100) { onSet(c.copy(minPicks = it)) }
        Stepper("Lowest score", c.minScore, 5, 40, 95) { onSet(c.copy(minScore = it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Must beat the other leagues", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    "A spot must hit clearly better than the same market everywhere else, so easy markets don't pass just for being easy.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted
                )
            }
            Switch(checked = c.beatAverage, onCheckedChange = { onSet(c.copy(beatAverage = it)) })
        }
        val windows = PerformanceReport.Window.entries
        TextButton(onClick = { onSet(c.copy(window = windows[(windows.indexOf(c.window) + 1) % windows.size])) }) {
            Text("Picks counted: ${c.window.label} (tap to change)", color = IndigoPrimary)
        }
        Stepper("Fewest models behind a pick", c.minBackers, 1, 1, 3) { onSet(c.copy(minBackers = it)) }
        Stepper("Lowest average confidence %", c.minConfidence, 5, 50, 90) { onSet(c.copy(minConfidence = it)) }
        Text(
            "A league and market (for example Over 1.5 in the Bundesliga) is strong with this many settled picks, this score (the hit rate corrected for how few picks there are) " +
                "and, if on, a clear lead over the other leagues. Trend picks also need the model support set here.",
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
