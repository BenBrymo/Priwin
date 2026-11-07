package com.predictengines.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
            "No league and market has ${state.trendMin}+ settled picks with a score of ${state.trendScore}+ yet. Lower the score on the Data tab to see more."
        else "${strong.size} spots · settled picks from your weekly lists, each game's selection counted once · score allows for how few picks there are",
        accentColor = WinGreen
    ) {
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

/** Data tab: the two numbers that decide what counts as a strong spot. */
@Composable
fun TrendSettings(state: AppUiState, onSet: (Int, Int) -> Unit) {
    @Composable
    fun Stepper(label: String, value: Int, step: Int, min: Int, max: Int, change: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = { change((value - step).coerceAtLeast(min)) }, enabled = value > min) {
                Text("−", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
            }
            Text(value.toString(), style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(horizontal = 6.dp))
            TextButton(onClick = { change((value + step).coerceAtMost(max)) }, enabled = value < max) {
                Text("+", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Stepper("Fewest settled picks", state.trendMin, 1, 5, 100) { onSet(it, state.trendScore) }
        Stepper("Lowest score", state.trendScore, 5, 40, 95) { onSet(state.trendMin, it) }
        Text(
            "A league and market (for example Over 1.5 in the Bundesliga) counts as strong when it has at least this many settled picks and its score is at least this high. " +
                "The score is the hit rate corrected for how few picks there are, so 75 is demanding: lower it to see more spots.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted
        )
    }
}
