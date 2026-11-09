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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.CaptainPlanet
import com.predictengines.app.data.PerformanceReport
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.viewmodel.AppUiState

/** Markets Captain Planet dropped (approved by you); fixture cards hide their consensus picks. */
val LocalCaptainBans = compositionLocalOf<Set<String>> { emptySet() }

/** Data tab: proposals waiting for approval, the markets already dropped, and the settings. */
@Composable
fun CaptainPlanetCard(
    state: AppUiState,
    onDecide: (CaptainPlanet.Proposal, Boolean) -> Unit,
    onLift: (String) -> Unit,
    onSet: (CaptainPlanet.Config) -> Unit
) {
    val c = state.cpConfig
    Column(Modifier.fillMaxWidth()) {
        if (state.cpProposals.isEmpty()) {
            Text("Nothing waiting for your approval.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        state.cpProposals.forEach { p ->
            Spacer(Modifier.height(6.dp))
            Text(
                if (p.lift) "Bring back ${p.name}?" else "Drop ${p.name}?",
                style = MaterialTheme.typography.titleSmall, color = DrawAmber
            )
            Text(p.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onDecide(p, true) }) { Text(if (p.lift) "Approve: bring it back" else "Approve: drop it", color = IndigoPrimary) }
                TextButton(onClick = { onDecide(p, false) }) { Text("Not now", color = TextMuted) }
            }
        }
        if (state.cpBans.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Dropped markets", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            state.cpBans.sorted().forEach { k ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(PerformanceReport.keyName(k), style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onLift(k) }) { Text("Lift", color = IndigoPrimary) }
                }
            }
            Text(
                "Their picks are still written down and judged, so you will be told when a market recovers.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Fewest settled picks before judging a market", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSet(c.copy(minPicks = (c.minPicks - 1).coerceAtLeast(5))) }, enabled = c.minPicks > 5) {
                Text("−", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
            }
            Text(c.minPicks.toString(), style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(horizontal = 6.dp))
            TextButton(onClick = { onSet(c.copy(minPicks = (c.minPicks + 1).coerceAtMost(60))) }, enabled = c.minPicks < 60) {
                Text("+", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
            }
        }
        val windows = PerformanceReport.Window.entries
        TextButton(onClick = { onSet(c.copy(window = windows[(windows.indexOf(c.window) + 1) % windows.size])) }) {
            Text("Results counted: ${c.window.label} (tap to change)", color = IndigoPrimary)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Phone notification for a new proposal", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            Switch(checked = c.notify, onCheckedChange = { onSet(c.copy(notify = it)) })
        }
        Text(
            "A market is bad when it is under 50% right over enough settled picks and clearly not just bad luck. Nothing is dropped until you approve. " +
                "An approved market is dropped from every list and from the consensus picks on fixture cards, in all leagues. Each model's own card is unchanged.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted
        )
    }
}
