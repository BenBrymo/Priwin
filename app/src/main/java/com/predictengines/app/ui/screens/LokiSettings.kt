package com.predictengines.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.Lokis
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.viewmodel.AppUiState

/** The Lokis, each with a switch and (for two of them) a number to change. */
@Composable
fun LokiSettings(state: AppUiState, onSetOn: (String, Boolean) -> Unit, onSetParam: (String, Int) -> Unit) {
    val cfg = state.lokiConfig
    Lokis.RULES.forEach { rule ->
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(checked = cfg.on(rule.id), onCheckedChange = { onSetOn(rule.id, it) })
            }
            Text(rule.describe(), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text("Starting strength ${rule.strength.toInt()}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            rule.param?.let { q ->
                val v = cfg.param(rule.id)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(q.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        if (v != q.default) Text("default ${q.default}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    TextButton(onClick = { onSetParam(rule.id, (v - 1).coerceAtLeast(q.min)) }, enabled = v > q.min) {
                        Text("\u2212", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(v.toString(), style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.width(32.dp))
                    TextButton(onClick = { onSetParam(rule.id, (v + 1).coerceAtMost(q.max)) }, enabled = v < q.max) {
                        Text("+", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
