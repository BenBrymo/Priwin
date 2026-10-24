package com.predictengines.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.FixtureItem
import com.predictengines.app.data.PredictionRecord
import com.predictengines.app.data.TopPick
import com.predictengines.app.data.TrackablePick
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.EmptyState
import com.predictengines.app.ui.components.MarkButtons
import com.predictengines.app.ui.components.NamePickerDialog
import com.predictengines.app.ui.components.TagChip
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
fun FixturesScreen(
    state: AppUiState,
    onOpen: (Int) -> Unit,
    onClose: () -> Unit,
    onGoToData: () -> Unit,
    onMark: (date: String, home: String, away: String, pick: TrackablePick, correct: Boolean?) -> Unit,
    onLink: (name: String, target: String) -> Unit,
    onOpenTopPick: (TopPick) -> Unit
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    if (state.openFixtureId != null) {
        FixtureDetail(state, onClose, onMark, onLink)
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section, containerColor = Color.Transparent, contentColor = IndigoPrimary) {
            Tab(selected = section == 0, onClick = { section = 0 }, text = { Text("This league") })
            Tab(selected = section == 1, onClick = { section = 1 }, text = { Text("Top 20 picks") })
        }
        Box(Modifier.weight(1f)) {
            if (section == 0) LeagueFixtures(state, onOpen, onGoToData, onMark, onLink)
            else TopPicksList(state, onOpenTopPick)
        }
    }
}

@Composable
private fun TopPicksList(state: AppUiState, onOpenTopPick: (TopPick) -> Unit) {
    if (state.topPicksLoading && state.topPicks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IndigoPrimary)
        }
        return
    }
    if (state.topPicks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No upcoming predictable fixtures in any league yet. Add fixtures files (built in or loaded on the Data tab) and the strongest picks will be ranked here.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard(
                title = "Top ${state.topPicks.size} picks",
                subtitle = "Strongest consensus pick of each upcoming fixture, across all leagues · tap to open the analysis",
                accentColor = WinGreen
            ) {}
        }
        itemsIndexed(state.topPicks, key = { _, p -> "${p.leagueId}|${p.date}|${p.home}|${p.away}" }) { index, tp ->
            val pick = tp.pick
            AppCard(onClick = { onOpenTopPick(tp) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${index + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (index < 3) WinGreen else TextSecondary,
                        modifier = Modifier.width(48.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${tp.homeDisplay} v ${tp.awayDisplay}",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            tp.leagueName + " · " + DateUtils.kickoffLabel(tp.date, tp.time),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(pick.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = WinGreen)
                Text(
                    if (pick.isSingleModel) {
                        "${pick.backers[0].model} only · ${pick.backers[0].confidence.roundToInt()}%"
                    } else {
                        "${pick.backers.joinToString(", ") { it.model }} (${pick.backers.size} of ${pick.modelCount}) · avg ${pick.averageConfidence.roundToInt()}%"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun LeagueFixtures(
    state: AppUiState,
    onOpen: (Int) -> Unit,
    onGoToData: () -> Unit,
    onMark: (date: String, home: String, away: String, pick: TrackablePick, correct: Boolean?) -> Unit,
    onLink: (name: String, target: String) -> Unit
) {
    val leagueName = state.selected?.name.orEmpty()
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IndigoPrimary)
        }
        return
    }
    if (state.fixtures.isEmpty()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            EmptyState(
                icon = Icons.Filled.CalendarMonth,
                title = "No fixtures for $leagueName yet",
                message = "Load a fixtures file from your phone and the models will predict every game in it.",
                actionLabel = "Load fixtures file",
                onAction = onGoToData
            )
        }
        return
    }

    val leagueId = state.selected?.id.orEmpty()

    // Upcoming fixtures stay in view. Played games and games that can't be predicted yet are folded away.
    val upcoming = ArrayList<FixtureItem>()
    val played = ArrayList<FixtureItem>()
    val notReady = ArrayList<FixtureItem>()
    val now = LocalDateTime.now()
    for (fx in state.fixtures) {
        when {
            DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now) -> played.add(fx)
            fx.summary == null -> notReady.add(fx)
            else -> upcoming.add(fx)
        }
    }
    played.reverse() // most recent first
    var showPlayed by rememberSaveable { mutableStateOf(false) }
    var showNotReady by rememberSaveable { mutableStateOf(false) }
    // The fixture name the user tapped "Link" on; the picker shows the matches file's teams.
    var linking by remember { mutableStateOf<String?>(null) }

    fun markId(fx: FixtureItem, key: String) =
        PredictionRecord.idOf(leagueId, fx.fixture.date, fx.home.orEmpty(), fx.away.orEmpty(), "Consensus", key)
    val toMark = played.sumOf { fx -> fx.summary?.consensus?.picks.orEmpty().count { markId(fx, it.key) !in state.marks } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard(
                title = "${upcoming.size} upcoming fixtures",
                subtitle = (state.fixturesImport?.fileName ?: "Fixtures file") +
                    " · consensus picks · tap a game for each model's analysis",
                accentColor = IndigoPrimary
            ) {}
        }

        if (played.isNotEmpty() || notReady.isNotEmpty()) {
            item(key = "toggles") {
                AppCard {
                    if (played.isNotEmpty()) {
                        ToggleRow(
                            label = "Show played fixtures (${played.size})",
                            note = if (toMark > 0) "$toMark picks still to mark" else "all picks marked",
                            checked = showPlayed,
                            onChange = { showPlayed = it }
                        )
                    }
                    if (played.isNotEmpty() && notReady.isNotEmpty()) Spacer(Modifier.height(8.dp))
                    if (notReady.isNotEmpty()) {
                        ToggleRow(
                            label = "Show not-ready fixtures (${notReady.size})",
                            note = "a team wasn't found in the matches file · tap Link to fix",
                            checked = showNotReady,
                            onChange = { showNotReady = it }
                        )
                    }
                }
            }
        }

        var lastDay: String? = null
        for (fx in upcoming) {
            val day = DateUtils.dayLabel(fx.fixture.date)
            if (day != lastDay) {
                lastDay = day
                item(key = "day_$day") {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }
            item(key = "fx_${fx.id}") {
                FixtureCard(fx, state.marks, leagueId, showMarks = false, onMark = onMark, onLinkName = { linking = it }, onClick = { onOpen(fx.id) })
            }
        }

        if (played.isNotEmpty()) {
            if (showPlayed) {
                item(key = "hdr_played") {
                    Text("Played (most recent first)", style = MaterialTheme.typography.labelLarge, color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                items(played, key = { "fx_${it.id}" }) { fx ->
                    FixtureCard(fx, state.marks, leagueId, showMarks = true, onMark = onMark, onLinkName = { linking = it }, onClick = { onOpen(fx.id) })
                }
            }
        }

        if (notReady.isNotEmpty()) {
            if (showNotReady) {
                item(key = "hdr_notready") {
                    Text("Not ready", style = MaterialTheme.typography.labelLarge, color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                items(notReady, key = { "fx_${it.id}" }) { fx ->
                    FixtureCard(fx, state.marks, leagueId, showMarks = false, onMark = onMark, onLinkName = { linking = it }, onClick = { onOpen(fx.id) })
                }
            }
        }
    }

    linking?.let { name ->
        NamePickerDialog(
            name = name,
            candidates = state.teams,
            sourceDescription = "your matches file ($leagueName)",
            onPick = { target ->
                onLink(name, target)
                linking = null
            },
            onDismiss = { linking = null }
        )
    }
}

@Composable
private fun ToggleRow(label: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(note, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FixtureCard(
    item: FixtureItem,
    marks: Map<String, PredictionRecord>,
    leagueId: String,
    showMarks: Boolean,
    onMark: (date: String, home: String, away: String, pick: TrackablePick, correct: Boolean?) -> Unit,
    onLinkName: (String) -> Unit,
    onClick: () -> Unit
) {
    val summary = item.summary
    AppCard(onClick = if (summary != null) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.fixture.homeTeam,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.fixture.awayTeam,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.fixture.time.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                TagChip(item.fixture.time, IndigoPrimary)
            }
        }

        if (summary == null) {
            Spacer(Modifier.height(12.dp))
            val missing = listOfNotNull(
                item.fixture.homeTeam.takeIf { item.home == null },
                item.fixture.awayTeam.takeIf { item.away == null }
            )
            val message = if (missing.isNotEmpty()) {
                "Couldn't find ${missing.joinToString(" and ")} in the matches file."
            } else {
                "Home and away resolve to the same team."
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = LossRed)
            missing.forEach { name ->
                TextButton(onClick = { onLinkName(name) }) {
                    Text("Link \"$name\" to a team", color = IndigoPrimary)
                }
            }
            return@AppCard
        }

        val picks = summary.consensus.picks
        Spacer(Modifier.height(12.dp))
        if (picks.isEmpty()) {
            Text(
                "No pick available: the models have no usable data for this game.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            return@AppCard
        }
        picks.forEachIndexed { i, pick ->
            val id = PredictionRecord.idOf(
                leagueId, item.fixture.date, item.home.orEmpty(), item.away.orEmpty(), "Consensus", pick.key
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pick.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (i == 0) WinGreen else TextPrimary
                    )
                    Text(
                        if (pick.isSingleModel) {
                            "${pick.backers[0].model} only · ${pick.backers[0].confidence.roundToInt()}%"
                        } else {
                            "${pick.backers.size} of ${pick.modelCount} models · avg ${pick.averageConfidence.roundToInt()}%"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                if (showMarks) {
                    MarkButtons(
                        state = marks[id]?.correct,
                        onChange = { value ->
                            onMark(
                                item.fixture.date, item.home.orEmpty(), item.away.orEmpty(),
                                TrackablePick("Consensus", pick.key, pick.label, pick.group, pick.averageConfidence),
                                value
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FixtureDetail(
    state: AppUiState,
    onClose: () -> Unit,
    onMark: (date: String, home: String, away: String, pick: TrackablePick, correct: Boolean?) -> Unit,
    onLink: (name: String, target: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Fixture analysis", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        val analysis = state.fixtureAnalysis
        if (analysis == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IndigoPrimary, modifier = Modifier.size(36.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                AnalysisContent(
                    analysis,
                    marks = state.marks,
                    leagueId = state.selected?.id.orEmpty(),
                    onMark = { pick, value ->
                        onMark(analysis.date, analysis.homeTeam, analysis.awayTeam, pick, value)
                    },
                    statsTeams = state.statsTeams,
                    onLinkStatsName = onLink
                )
            }
        }
    }
}
