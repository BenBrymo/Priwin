package com.predictengines.app.ui.lite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.predictengines.app.BuildConfig
import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.LiteLabels
import com.predictengines.app.data.PerformanceReport
import com.predictengines.app.data.ReportBuilder
import com.predictengines.app.data.TopPick
import com.predictengines.app.data.Trends
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.PillChip
import com.predictengines.app.ui.components.PrimaryButton
import com.predictengines.app.ui.screens.slipItems
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private const val STALE_DAYS = 7

private fun gradeColor(g: LiteLabels.Grade) = when (g) {
    LiteLabels.Grade.STRONG -> WinGreen
    LiteLabels.Grade.GOOD -> IndigoPrimary
    LiteLabels.Grade.RISKY -> DrawAmber
}

private fun packDate(ms: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

/** A warning when the installed data pack is old, so nobody bets on stale picks without knowing. */
@Composable
private fun StaleBanner(state: AppUiState) {
    if (state.packAt <= 0L) return
    val days = (System.currentTimeMillis() - state.packAt) / (24L * 60 * 60 * 1000)
    if (days < STALE_DAYS) return
    AppCard(
        title = "These picks may be out of date",
        subtitle = "The data is $days days old (made ${packDate(state.packAt)}). Open the About tab and tap Check for updates.",
        accentColor = LossRed
    ) {}
}

/** This week's ready-made slips of 8, by list. */
@Composable
fun LiteSlipsScreen(state: AppUiState, onOpenPick: (TopPick) -> Unit) {
    val lists = listOf(
        "Best mix" to Trends.SOURCE, "Top picks" to "Top 20", "Over 1.5" to "Over 1.5", "Handicap" to "Handicap", "Corners" to "Corners"
    )
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val source = lists[sel].second
    val picks = when (source) {
        Trends.SOURCE -> state.trendPicks
        "Top 20" -> state.topPicks
        "Over 1.5" -> state.over15Picks
        "Handicap" -> state.handicapPicks
        else -> state.cornerPicks
    }
    val ranked = remember(picks, source) {
        val coming = picks.filter { !DateUtils.isPlayed(it.date, it.time) }
        val week = coming.mapNotNull { ReportBuilder.weekStartOf(it.date) }.minOrNull()
        val inWeek = coming.filter { ReportBuilder.weekStartOf(it.date) == week }
        (if (source == Trends.SOURCE) inWeek.sortedByDescending { it.pick.strength } else inWeek).mapIndexed { i, p -> (i + 1) to p }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { StaleBanner(state) }
        item {
            Text("This week's slips", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                "Ready-made slips of 8 games, strongest first. Each slip shows the chance that every pick lands. Predictions are not guarantees: only bet what you can afford to lose.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(lists) { i, l -> PillChip(text = l.first, selected = i == sel, onClick = { sel = i }) }
            }
        }
        if (ranked.isEmpty()) {
            item {
                AppCard(
                    title = if (state.topPicksLoading) "Getting the picks ready..." else "No slip is ready yet",
                    subtitle = if (state.topPicksLoading) "This takes a moment the first time."
                    else "There are no picks for the coming games in this list yet. Check for updates on the About tab, or try another list.",
                    accentColor = IndigoPrimary
                ) {}
            }
        } else {
            slipItems("lite_$source", ranked, source, source, state, onOpenPick)
        }
    }
}

/** The strongest single picks for today and tomorrow. */
@Composable
fun LiteTodayScreen(state: AppUiState, onOpenPick: (TopPick) -> Unit) {
    val today = LocalDate.now()
    val picks = remember(state.trendPicks, state.topPicks, today) {
        val days = setOf(today, today.plusDays(1))
        fun comingSoon(list: List<TopPick>) = list.filter { !DateUtils.isPlayed(it.date, it.time) && DateUtils.parse(it.date)?.let { d -> d in days } == true }
        val first = comingSoon(state.trendPicks).sortedByDescending { it.pick.strength }
        val seen = HashSet(first.map { "${it.leagueId}|${it.date}|${it.home}|${it.away}" })
        // top up with the Top 20 list when few Trend picks are on
        val more = if (first.size >= 6) emptyList() else comingSoon(state.topPicks).filter { seen.add("${it.leagueId}|${it.date}|${it.home}|${it.away}") }
        (first + more).take(12)
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { StaleBanner(state) }
        item {
            Text("Best picks for today and tomorrow", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                "Tap a pick to see the game's analysis. Strong means this kind of pick has landed 65% or more in our record; Risky means less than 55%.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
        }
        if (picks.isEmpty()) {
            item {
                AppCard(
                    title = if (state.topPicksLoading) "Getting the picks ready..." else "No picks for today or tomorrow",
                    subtitle = if (state.topPicksLoading) "This takes a moment the first time." else "Look at this week's slips, or check for updates on the About tab.",
                    accentColor = IndigoPrimary
                ) {}
            }
        }
        itemsIndexed(picks, key = { _, p -> "${p.leagueId}|${p.date}|${p.home}|${p.away}|${p.pick.key}" }) { _, p ->
            val g = LiteLabels.grade(p)
            AppCard(onClick = { onOpenPick(p) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${p.homeDisplay} v ${p.awayDisplay}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(LiteLabels.pickName(p), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = WinGreen)
                        Text(p.leagueName + " · " + DateUtils.kickoffLabelWithYear(p.date, p.time), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Text(g.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = gradeColor(g))
                }
                val why = LiteLabels.why(p)
                if (why.isNotBlank()) Text(why, style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Plain proof: how each list's settled picks have done, wins and losses. */
@Composable
fun LiteRecordScreen(state: AppUiState) {
    val visible = remember(state.leagues) { state.leagues.map { it.id }.toSet() }
    val lists = PerformanceReport.LISTS.filter { it != Trends.SOURCE }
    val reports = remember(state.marks, visible) {
        val records = state.marks.values.filter { !it.source.endsWith(PerformanceReport.HIDDEN_SUFFIX) }
        fun at(w: PerformanceReport.Window) =
            PerformanceReport.build(records, emptyMap(), visible, w, LocalDate.now(), lists).associate { it.source to it.total }
        at(PerformanceReport.Window.WEEKS4) to at(PerformanceReport.Window.ALL)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Our record", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(
            "Every pick that has been settled is counted here, wins and losses, so you can judge the picks for yourself.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
        )
        for ((title, data) in listOf("Last 4 weeks" to reports.first, "All time" to reports.second)) {
            AppCard(title = title, accentColor = IndigoPrimary) {
                var right = 0; var all = 0
                lists.forEach { name ->
                    val t = data[name] ?: PerformanceReport.Tally()
                    right += t.correct; all += t.total
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        if (t.total == 0) Text("no settled picks", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        else Text(
                            "${t.correct} of ${t.total} right · ${Math.round(t.rate)}%", style = MaterialTheme.typography.titleSmall,
                            color = if (t.rate >= 65) WinGreen else if (t.rate < 50) LossRed else TextPrimary
                        )
                    }
                }
                if (all > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Together: $right of $all right (${Math.round(100.0 * right / all)}%)",
                        style = MaterialTheme.typography.titleSmall, color = TextPrimary
                    )
                }
            }
        }
        Text(
            "A pick that is right most of the time still loses sometimes, and a slip of 8 needs every pick to land. Past results do not promise future ones.",
            style = MaterialTheme.typography.labelMedium, color = TextMuted
        )
    }
}

/** Data freshness, updates, and the responsible-gambling notes. */
@Composable
fun LiteAboutScreen(state: AppUiState, onCheck: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("About Priwin Picks", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        AppCard(
            title = "Picks data",
            subtitle = if (state.packAt > 0L) "Updated ${packDate(state.packAt)}" else "Using the data that came with the app",
            accentColor = IndigoPrimary
        ) {
            Text(
                if (BuildConfig.PACK_URL.isBlank()) "Online updates are not set up in this build." else "The app checks for new picks when it opens. You can also check now.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
            if (BuildConfig.PACK_URL.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(text = if (state.packChecking) "Checking..." else "Check for updates", onClick = onCheck, enabled = !state.packChecking)
            }
        }
        AppCard(title = "How to read the picks", accentColor = WinGreen) {
            Text(
                "Strong: this kind of pick has landed 65% or more in our record.\nGood: 55% or more.\nRisky: less than 55%, or too few results to tell.\n\n" +
                    "A slip is 8 games, one pick per game. The chance shown is every pick landing, worked out from how often each kind of pick has landed. It assumes the games do not affect each other, so treat it as a guide, not a promise.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
        }
        AppCard(title = "Bet responsibly", accentColor = DrawAmber) {
            Text(
                "Priwin Picks gives predictions only. It does not take bets, and no pick is guaranteed to win. You must be 18 or older. Only bet what you can afford to lose, and stop if it stops being fun.\n\n" +
                    "If gambling is a problem for you or someone you know, free help is available. In the UK: BeGambleAware (begambleaware.org) and GamCare (gamcare.org.uk). In the US: ncpgambling.org or 1-800-GAMBLER. Elsewhere, search for your country's gambling helpline.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
        }
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}
