package com.predictengines.app.ui.screens

import android.content.Intent
import com.predictengines.app.data.ListPicksStore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
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

    val lists = remember(state.marks, state.listEntries) {
        val marked = state.marks.keys
        ListPicksStore.SOURCES.map { src ->
            src to ReportBuilder.listWeeks(src, state.marks.values, state.listEntries, marked)
        }
    }

    if (weeks.isEmpty() && lists.all { it.second.isEmpty() }) {
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
        items(lists, key = { "list_${it.first}" }) { (src, listWeeks) -> ListReportCard(src, listWeeks) }
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
                    putExtra(Intent.EXTRA_SUBJECT, "Priwin weekly report: $title")
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

/** One weekly list's report: foldable card, with a foldable block for each week inside. */
@Composable
private fun ListReportCard(source: String, weeks: List<ReportBuilder.ListWeek>) {
    val context = LocalContext.current
    var open by rememberSaveable(source) { mutableStateOf(false) }
    var flipped by rememberSaveable(source) { mutableStateOf(emptySet<String>()) }
    val correct = weeks.sumOf { it.correct }
    val wrong = weeks.sumOf { it.wrong }
    val waiting = weeks.sumOf { it.waiting }
    val total = correct + wrong
    val rate = if (total == 0) 0 else Math.round(correct * 100.0 / total).toInt()
    val title = when (source) {
        "Top 20" -> "Top 20 picks"
        "Handicap" -> "Handicap picks"
        else -> "Over 1.5 goals"
    }

    AppCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    if (total == 0 && waiting == 0) "No results yet"
                    else "$correct correct · $wrong wrong" + (if (total > 0) " · $rate%" else "") +
                        (if (waiting > 0) " · $waiting waiting" else ""),
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary
                )
            }
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Fold" else "Unfold", tint = TextSecondary
            )
        }
        if (open) {
            if (weeks.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Picks on this list are settled automatically once their games have results.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted
                )
            }
            weeks.forEachIndexed { index, w ->
                val key = w.weekStart?.toString() ?: "no_date"
                val weekOpen = (index == 0) != (key in flipped)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { flipped = if (key in flipped) flipped - key else flipped + key },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ReportBuilder.weekLabel(w.weekStart), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            "${w.correct} correct · ${w.wrong} wrong" + (if (w.total > 0) " · ${w.hitRatePercent}%" else "") +
                                (if (w.waiting > 0) " · ${w.waiting} waiting" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (w.total == 0) TextMuted else if (w.hitRatePercent >= 50) WinGreen else LossRed
                        )
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Priwin $title report: ${ReportBuilder.weekLabel(w.weekStart)}")
                            putExtra(Intent.EXTRA_TEXT, ReportBuilder.listText(title, w))
                        }
                        context.startActivity(Intent.createChooser(send, "Share report"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Share report", tint = TextSecondary) }
                    Icon(
                        if (weekOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (weekOpen) "Fold" else "Unfold", tint = TextSecondary
                    )
                }
                if (weekOpen) {
                    w.records.forEach { r ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                if (r.correct) "✓" else "✗",
                                color = if (r.correct) WinGreen else LossRed,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp)
                            )
                            Column {
                                Text(
                                    "${r.home} v ${r.away} · ${r.label}",
                                    style = MaterialTheme.typography.bodyMedium, color = TextPrimary
                                )
                                Text(
                                    "${r.leagueName} · ${r.date}",
                                    style = MaterialTheme.typography.labelSmall, color = TextMuted
                                )
                            }
                        }
                    }
                    if (w.waiting > 0) {
                        Text(
                            "${w.waiting} more waiting for their games to be played or scored",
                            style = MaterialTheme.typography.labelSmall, color = DrawAmber,
                            modifier = Modifier.padding(top = 4.dp)
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
