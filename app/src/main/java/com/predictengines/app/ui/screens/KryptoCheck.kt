package com.predictengines.app.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState

/** The "Check Kryptonite flags" card: a button, the verdict, and the games behind every flag. */
@Composable
fun KryptoCheck(state: AppUiState, onCheck: () -> Unit) {
    val league = state.selected
    var showEvidence by rememberSaveable { mutableStateOf(false) }
    val report = state.kryptoAudit?.takeIf { it.leagueId == league?.id }
    Column(Modifier.fillMaxWidth()) {
        if (league == null) {
            Text("Pick a league first.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@Column
        }
        TextButton(onClick = onCheck, enabled = !state.kryptoAuditBusy) {
            Text(if (state.kryptoAuditBusy) "Checking…" else "Check ${league.name}", color = IndigoPrimary)
        }
        if (report == null) {
            Text("Not checked yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@Column
        }
        Text(
            "Checked ${report.fixtures} upcoming fixtures and ${report.flags} flags. Newest game in the matches file: " +
                (report.newestGame.ifBlank { "unknown" }) + ".",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        if (report.issues.isEmpty()) {
            Text("Every flag matches a fresh recount of the games.", style = MaterialTheme.typography.titleSmall, color = WinGreen)
        } else {
            Text("${report.issues.size} problem(s) found", style = MaterialTheme.typography.titleSmall, color = LossRed)
            report.issues.take(12).forEach { i ->
                Spacer(Modifier.height(8.dp))
                Text("${i.team} · ${i.rule}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(i.fixture, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(i.problem, style = MaterialTheme.typography.bodyMedium, color = DrawAmber)
                i.games.forEach { g -> Text(g, style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
            }
            if (report.issues.size > 12) {
                Text("…and ${report.issues.size - 12} more.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
        if (report.unmatched.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Team names in the fixtures file that match no team in the matches file (their flags can't be worked out): " +
                    report.unmatched.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium, color = DrawAmber
            )
        }
        if (report.evidence.isNotEmpty()) {
            TextButton(onClick = { showEvidence = !showEvidence }) {
                Text(if (showEvidence) "Hide the games behind each flag" else "Show the games behind each flag (${report.evidence.size})", color = IndigoPrimary)
            }
            if (showEvidence) {
                report.evidence.take(60).forEach { e ->
                    Text("${e.team} · ${e.rule}", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(top = 8.dp))
                    Text("first seen on ${e.fixture} · last games, newest first", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    e.games.forEach { g -> Text(g, style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
                }
            }
        }
    }
}
