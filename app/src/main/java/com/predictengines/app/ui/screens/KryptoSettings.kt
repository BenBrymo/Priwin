package com.predictengines.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.predictengines.app.data.KryptoMode
import com.predictengines.app.data.KryptoParam
import com.predictengines.app.data.KryptoRule
import com.predictengines.app.data.Kryptonites
import com.predictengines.app.ui.components.PillChip
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.viewmodel.AppUiState

/**
 * The Kryptonites settings: the rules in foldable groups. Each rule shows what it does, its numbers (change them with
 * the - and + buttons), the pick types it counts for, and what it does to a flagged pick. "Reset" puts one rule back
 * to its defaults.
 */
@Composable
fun KryptoSettings(
    state: AppUiState,
    onSetMode: (String, KryptoMode) -> Unit,
    onSetParam: (String, String, Int) -> Unit,
    onSetMarkets: (String, Set<String>) -> Unit,
    onReset: (KryptoRule) -> Unit,
    onRestoreDismissed: () -> Unit = {}
) {
    val dismissedCount = state.kryptoConfig.dismissed.size
    if (dismissedCount > 0) {
        TextButton(onClick = onRestoreDismissed) {
            Text("Bring back $dismissedCount dismissed ${if (dismissedCount == 1) "flag" else "flags"}", color = IndigoPrimary)
        }
    }
    Kryptonites.GROUPS.forEach { group ->
        val rules = Kryptonites.RULES.filter { it.group == group }
        if (rules.isEmpty()) return@forEach
        var open by rememberSaveable(group) { mutableStateOf(false) }
        val active = rules.count { (state.kryptoModes[it.id] ?: KryptoMode.WARN) != KryptoMode.WARN }
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(group, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "${rules.size} rules" + if (active > 0) " · $active set to hide or push down" else "",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted
                )
            }
            Text(if (open) "▲" else "▼", color = IndigoPrimary)
        }
        if (open) rules.forEach { rule -> RuleSettings(rule, state, onSetMode, onSetParam, onSetMarkets, onReset) }
    }
}

@Composable
private fun RuleSettings(
    rule: KryptoRule,
    state: AppUiState,
    onSetMode: (String, KryptoMode) -> Unit,
    onSetParam: (String, String, Int) -> Unit,
    onSetMarkets: (String, Set<String>) -> Unit,
    onReset: (KryptoRule) -> Unit
) {
    val cfg = state.kryptoConfig
    val markets = cfg.marketsOf(rule.id)
    val changed = rule.params.any { cfg.param(rule.id, it.key) != it.default } ||
        markets != rule.markets || (state.kryptoModes[rule.id] ?: KryptoMode.WARN) != KryptoMode.WARN
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 14.dp)) {
        Text(rule.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(rule.describe(), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        rule.params.forEach { q -> ParamRow(rule, q, cfg.param(rule.id, q.key), onSetParam) }
        Spacer(Modifier.height(6.dp))
        Text("Counts for these pick types (others ignore this flag)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kryptonites.MARKETS.forEach { m ->
                PillChip(
                    text = m, selected = m in markets,
                    onClick = { onSetMarkets(rule.id, if (m in markets) markets - m else markets + m) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("What it does to a flagged pick", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KryptoMode.entries.forEach { m ->
                PillChip(
                    text = m.label, selected = (state.kryptoModes[rule.id] ?: KryptoMode.WARN) == m,
                    onClick = { onSetMode(rule.id, m) }
                )
            }
        }
        if (changed) TextButton(onClick = { onReset(rule) }) { Text("Reset to defaults", color = IndigoPrimary) }
    }
}

@Composable
private fun ParamRow(rule: KryptoRule, q: KryptoParam, value: Int, onSetParam: (String, String, Int) -> Unit) {
    fun show(v: Int) = if (q.isTime) Kryptonites.hhmm(v) else v.toString()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(q.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            if (value != q.default) Text("default ${show(q.default)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (q.isToggle) {
            androidx.compose.material3.Switch(checked = value == 1, onCheckedChange = { onSetParam(rule.id, q.key, if (it) 1 else 0) })
            return@Row
        }
        TextButton(onClick = { onSetParam(rule.id, q.key, (value - q.step).coerceAtLeast(q.min)) }, enabled = value > q.min) {
            Text("−", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
        }
        Text(show(value), style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.width(48.dp))
        TextButton(onClick = { onSetParam(rule.id, q.key, (value + q.step).coerceAtMost(q.max)) }, enabled = value < q.max) {
            Text("+", color = IndigoPrimary, style = MaterialTheme.typography.titleLarge)
        }
    }
}
