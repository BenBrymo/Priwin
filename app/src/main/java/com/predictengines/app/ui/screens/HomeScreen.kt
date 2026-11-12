package com.predictengines.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.PerformanceReport
import com.predictengines.app.data.ReportBuilder
import com.predictengines.app.data.TopPick
import com.predictengines.app.data.Trends
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.time.LocalDate

/** The page the app opens on: this week's best picks, how each list did lately, what waits for approval, and the Loki count. */
@Composable
fun HomeScreen(
    state: AppUiState,
    onOpenPick: (TopPick) -> Unit,
    onGoToTrends: () -> Unit,
    onGoToData: () -> Unit
) {
    val visible = remember(state.leagues) { state.leagues.map { it.id }.toSet() }

    // the strongest Trend picks of the first week that still has games coming
    val best = remember(state.trendPicks) {
        val coming = state.trendPicks.filter { !DateUtils.isPlayed(it.date, it.time) }
        val week = coming.mapNotNull { ReportBuilder.weekStartOf(it.date) }.minOrNull()
        coming.filter { ReportBuilder.weekStartOf(it.date) == week }.sortedByDescending { it.pick.strength }.take(5)
    }
    val slip = remember(state.trendByList) { Trends.slip(state.trendByList) }

    // each list's hit rate over the last 4 weeks, judged the way the Report does
    val lastFour = remember(state.marks, visible) {
        val hide = PerformanceReport.HIDDEN_SUFFIX
        val records = state.marks.values.filter { !it.source.endsWith(hide) }
        val lists = PerformanceReport.LISTS.filter { it != Trends.SOURCE }
        PerformanceReport.build(records, emptyMap(), visible, PerformanceReport.Window.WEEKS4, LocalDate.now(), lists)
            .associate { it.source to it.total }
    }
    val lokiComing = remember(state.lokiPicks) { state.lokiPicks.count { !DateUtils.isPlayed(it.date, it.time) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppCard(
            title = "This week's best picks",
            subtitle = if (best.isEmpty()) "No strong Trend picks are coming yet. Settle more picks, or open the Trends tab to see why."
            else "The strongest Trend picks still to play" + (slip?.let { " · weekly slip: ${it.legs.size} picks, all land about ${Math.round(it.chance)}%" } ?: ""),
            accentColor = WinGreen,
            onClick = onGoToTrends
        ) {
            best.forEach { p ->
                TextButton(onClick = { onOpenPick(p) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${p.homeDisplay} v ${p.awayDisplay}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            p.pick.label.substringBefore(" · ") + " · ${p.leagueName} · ${p.date}",
                            style = MaterialTheme.typography.labelMedium, color = WinGreen
                        )
                    }
                }
            }
            if (best.isNotEmpty()) Text("Tap a pick for its analysis, or this card to go to the Fixtures tab (Trends list).", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }

        AppCard(
            title = "Last 4 weeks, by list",
            subtitle = "How often each list's settled picks were right",
            accentColor = IndigoPrimary
        ) {
            PerformanceReport.LISTS.filter { it != Trends.SOURCE }.forEach { name ->
                val t = lastFour[name] ?: PerformanceReport.Tally()
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    if (t.total == 0) Text("no settled picks", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    else Text(
                        "${t.correct}/${t.total} · ${Math.round(t.rate)}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (t.rate >= 65) WinGreen else if (t.rate < 50) LossRed else TextPrimary
                    )
                }
            }
        }

        AppCard(
            title = "Captain Planet",
            subtitle = if (state.cpProposals.isEmpty()) "Nothing waiting for your approval"
            else "${state.cpProposals.size} waiting for your approval",
            accentColor = DrawAmber,
            onClick = onGoToData
        ) {
            state.cpProposals.take(3).forEach { Text(it.text, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp)) }
            if (state.cpProposals.size > 3) Text("and ${state.cpProposals.size - 3} more", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            if (state.cpProposals.isNotEmpty()) Text("Tap to review on the Data tab.", style = MaterialTheme.typography.labelSmall, color = IndigoPrimary)
        }

        AppCard(
            title = "Loki",
            subtitle = if (lokiComing == 0) "No Loki picks for games still to play" else "$lokiComing Loki ${if (lokiComing == 1) "pick" else "picks"} for games still to play",
            accentColor = LossRed
        ) {}
    }
}
