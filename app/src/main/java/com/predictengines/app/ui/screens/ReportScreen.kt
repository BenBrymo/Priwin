package com.predictengines.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.Breakdown
import com.predictengines.app.data.ReportBuilder
import com.predictengines.app.data.WeekReport
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.EmptyState
import com.predictengines.app.ui.components.TagChip
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState

/** Weekly (Monday to Sunday) summary of the predictions the user ticked or crossed. */
@Composable
fun ReportScreen(state: AppUiState) {
    val leagueId = state.selected?.id.orEmpty()
    val weeks = remember(state.marks, state.fixtures, leagueId) {
        ReportBuilder.weeks(state.marks.values, ReportBuilder.pending(state.fixtures, state.marks, leagueId))
    }

    if (weeks.isEmpty()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            EmptyState(
                icon = Icons.Filled.Assessment,
                title = "No results marked yet",
                message = "Open Fixtures, unfold Played, and tick the picks that came true and cross the ones " +
                    "that didn't. Your weekly report appears here."
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(weeks, key = { it.weekStart?.toString() ?: "no_date" }) { week -> WeekCard(week) }
    }
}

@Composable
private fun WeekCard(week: WeekReport) {
    val context = LocalContext.current
    var showList by remember { mutableStateOf(false) }
    val title = ReportBuilder.weekLabel(week.weekStart)

    AppCard(
        title = title,
        subtitle = if (week.weekStart == null) "Fixtures without a date" else "Monday to Sunday",
        accentColor = IndigoPrimary,
        trailing = {
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Predict Engines weekly report: $title")
                    putExtra(Intent.EXTRA_TEXT, ReportBuilder.toText(week))
                }
                context.startActivity(Intent.createChooser(send, "Share report"))
            }) {
                Icon(Icons.Filled.Share, contentDescription = "Share report", tint = TextSecondary)
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (week.total > 0) "${week.hitRatePercent}%" else "-",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    week.total == 0 -> TextMuted
                    week.hitRatePercent >= 50 -> WinGreen
                    else -> LossRed
                }
            )
            Spacer(Modifier.padding(horizontal = 8.dp))
            Text("hit rate", style = MaterialTheme.typography.labelLarge, color = TextMuted)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip("${week.correct} correct", WinGreen)
            TagChip("${week.wrong} wrong", LossRed)
            if (week.pending > 0) TagChip("${week.pending} to mark", DrawAmber)
        }

        if (week.bySource.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("By model", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            week.bySource.forEach { BreakdownRow(it) }
        }
        if (week.byMarket.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("By market", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            week.byMarket.forEach { BreakdownRow(it) }
        }
        if (week.pending > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "\"To mark\" counts played games in the selected league that still have unmarked consensus picks.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        if (week.records.isNotEmpty()) {
            TextButton(onClick = { showList = !showList }) {
                Text(
                    if (showList) "Hide predictions" else "Show ${week.records.size} predictions",
                    color = IndigoPrimary
                )
            }
            if (showList) {
                week.records.forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            if (r.correct) "✓" else "✗",
                            color = if (r.correct) WinGreen else LossRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            "${r.home} v ${r.away} · ${r.label} · ${r.source}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(b: Breakdown) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(b.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(
            "${b.correct}/${b.total} · ${b.hitRatePercent}%",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
