package com.predictengines.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.predictengines.app.ui.components.inputColors
import com.predictengines.app.ui.components.PrimaryButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.text.KeyboardOptions
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
import com.predictengines.app.data.MatchExtras
import com.predictengines.app.data.ScoreCell
import com.predictengines.app.data.fixtureKey
import com.predictengines.app.viewmodel.AppViewModel
import com.predictengines.app.data.Match
import com.predictengines.app.data.scoreKey
import com.predictengines.app.ui.components.ScoreDialog
import com.predictengines.app.ui.components.suggestedScores
import com.predictengines.app.data.PredictionRecord
import com.predictengines.app.data.ReportBuilder
import com.predictengines.app.data.TopPick
import com.predictengines.app.data.TrackablePick
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.BestPickChip
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    onOpenTopPick: (TopPick) -> Unit,
    onSaveScore: (fixtureId: Int, home: Int, away: Int, extras: MatchExtras?) -> Unit,
    onRemoveScore: (fixtureId: Int) -> Unit,
    onSaveScores: (List<AppViewModel.ScoreEntry>) -> Unit,
    scoreHints: (home: String, away: String) -> List<ScoreCell> = { _, _ -> emptyList() }
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    if (state.openFixtureId != null) {
        FixtureDetail(state, onClose, onMark, onLink)
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = section, containerColor = Color.Transparent, contentColor = IndigoPrimary,
            edgePadding = 8.dp
        ) {
            Tab(selected = section == 0, onClick = { section = 0 }, text = { Text("This league") })
            Tab(selected = section == 1, onClick = { section = 1 }, text = {
                val waiting = missingResults(state).size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Results")
                    if (waiting > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                .clip(CircleShape)
                                .background(WinGreen)
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (waiting > 99) "99+" else waiting.toString(),
                                style = MaterialTheme.typography.labelSmall, color = Color.Black, maxLines = 1
                            )
                        }
                    }
                }
            })
            Tab(selected = section == 2, onClick = { section = 2 }, text = { Text("Top 20 picks") })
            Tab(selected = section == 3, onClick = { section = 3 }, text = { Text("Handicap") })
            Tab(selected = section == 4, onClick = { section = 4 }, text = { Text("Over 1.5") })
        }
        Box(Modifier.weight(1f)) {
            if (section == 0) LeagueFixtures(state, onOpen, onGoToData, onMark, onLink, onSaveScore, onRemoveScore, scoreHints)
            else if (section == 1) ResultsEntry(state, onSaveScore, scoreHints)
            else if (section == 2) TopPicksList(state, onOpenTopPick)
            else if (section == 3) TopPicksList(
                state, onOpenTopPick, picks = state.handicapPicks, prefix = "h", showBest = false,
                title = "Brymo handicap picks of each week",
                subtitle = "best 20 per week across all leagues · Brymo's strongest +2 handicap pick per game · tap a card to open the analysis",
                emptyText = "No upcoming games have a Brymo handicap pick yet."
            )
            else if (section == 4) TopPicksList(
                state, onOpenTopPick, picks = state.over15Picks, prefix = "o", showBest = false,
                title = "Over 1.5 goals of each week",
                subtitle = "best 20 per week across all leagues · backed by Poisson and/or Stats, both backing it first · tap a card to open the analysis",
                emptyText = "No upcoming games have Poisson or Stats backing Over 1.5 goals yet."
            )
        }
    }
}

@Composable
private fun TopPicksList(
    state: AppUiState,
    onOpenTopPick: (TopPick) -> Unit,
    picks: List<TopPick> = state.topPicks,
    prefix: String = "t",
    showBest: Boolean = true,
    title: String = "Top 20 picks of each week",
    subtitle: String? = null,
    emptyText: String = "No upcoming predictable fixtures in any league yet. Add fixtures files (built in or loaded on the Data tab) and the strongest picks will be ranked here."
) {
    var folded by rememberSaveable { mutableStateOf(emptySet<String>()) }
    if (state.topPicksLoading && picks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IndigoPrimary)
        }
        return
    }
    if (picks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                emptyText,
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
                title = title,
                subtitle = "${picks.size} picks · " + (subtitle ?: "best 20 per week across all leagues, ranked within the week · tap a card to open the analysis"),
                accentColor = WinGreen
            ) {}
        }
        // Same week groups as the league list: Monday-Sunday, foldable, games side by side.
        val thisWeek = ReportBuilder.weekStartOf(LocalDate.now().toString())
        // The list arrives week by week, strongest first inside each week: rank = position within the week.
        val groups = picks.groupBy { ReportBuilder.weekStartOf(it.date) }
            .map { (week, picks) -> week to picks.mapIndexed { i, tp -> (i + 1) to tp } }
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        for ((week, list) in groups) {
            val key = "$prefix|${week ?: "none"}"
            val open = key !in folded
            val title = when {
                week == null -> "Date TBC"
                week == thisWeek -> "This week"
                thisWeek != null && week == thisWeek.plusWeeks(1) -> "Next week"
                else -> "Week of ${week.format(fmt)}"
            }
            val range = if (week != null) "${week.format(fmt)} - ${week.plusDays(6).format(fmt)}" else ""
            item(key = "wk_$key") {
                AppCard(onClick = { folded = if (key in folded) folded - key else folded + key }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                listOf(range, "${list.size} ${if (list.size == 1) "pick" else "picks"}")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Icon(
                            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (open) "Fold" else "Unfold",
                            tint = TextSecondary
                        )
                    }
                }
            }
            if (open) {
                item(key = "row_$key") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(
                            list,
                            key = { (_, p) -> "${p.leagueId}|${p.date}|${p.home}|${p.away}" }
                        ) { (rank, tp) ->
                            TopPickCard(rank, tp, onClick = { onOpenTopPick(tp) }, modifier = Modifier.width(300.dp), showBest = showBest)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPickCard(rank: Int, tp: TopPick, onClick: () -> Unit, modifier: Modifier = Modifier, showBest: Boolean = true) {
    val pick = tp.pick
    AppCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#$rank",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3) WinGreen else TextSecondary,
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
                    tp.leagueName + " · " + DateUtils.kickoffLabelWithYear(tp.date, tp.time),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pick.label, modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = WinGreen
            )
            Spacer(Modifier.width(8.dp))
            if (showBest) BestPickChip(pick)
        }
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

/** Played games in the open league that have no score yet (not in the matches file, not entered by you). */
private fun missingResults(state: AppUiState): List<FixtureItem> {
    val now = LocalDateTime.now()
    return state.fixtures.filter { fx ->
        fx.home != null && fx.away != null && fx.fixture.date.isNotBlank() &&
            DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now) &&
            !state.scores.containsKey(scoreKey(fx.fixture.date, fx.home, fx.away))
    }.sortedByDescending { DateUtils.sortKey(it.fixture.date, it.fixture.time) }
}

/**
 * Played games that have no score yet (neither in the matches file nor entered by you). Tap Add result, pick the
 * score, corners, cards and who scored first, and the stats and picks are settled for you.
 */
@Composable
private fun ResultsEntry(
    state: AppUiState,
    onSaveScore: (Int, Int, Int, MatchExtras?) -> Unit,
    scoreHints: (home: String, away: String) -> List<ScoreCell>
) {
    val missing = missingResults(state)
    var flipped by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val thisWeek = ReportBuilder.weekStartOf(LocalDate.now().toString())

    var scoring by remember { mutableStateOf<FixtureItem?>(null) }
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = IndigoPrimary) }
        return
    }
    if (missing.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No played games are waiting for a result in ${state.selected?.name.orEmpty()}.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard(
                title = "${missing.size} games need a result",
                subtitle = "The matches file has no score for these. Tap Add result and pick the score, corners, cards and who scored first.",
                accentColor = WinGreen
            ) {}
        }
        weekGroups(
            prefix = "r", fixtures = missing, thisWeek = thisWeek, flipped = flipped,
            onFlip = { flipped = if (it in flipped) flipped - it else flipped + it },
            defaultOpen = { _, _ -> true },
            showMarks = false,
            card = { fx, _, modifier ->
                AppCard(modifier = modifier) {
                    Text(
                        DateUtils.kickoffLabelWithYear(fx.fixture.date, fx.fixture.time),
                        style = MaterialTheme.typography.labelMedium, color = TextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        fx.fixture.homeTeam,
                        style = MaterialTheme.typography.titleSmall, color = TextPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        fx.fixture.awayTeam,
                        style = MaterialTheme.typography.titleSmall, color = TextPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(text = "Add result", onClick = { scoring = fx }, color = WinGreen)
                }
            }
        )
    }

    scoring?.let { fx ->
        ScoreDialog(
            home = fx.home.orEmpty(), away = fx.away.orEmpty(),
            initial = null,
            initialExtras = state.extras[fixtureKey(state.selected?.id.orEmpty(), fx.fixture.date, fx.home.orEmpty(), fx.away.orEmpty())],
            canRemove = false,
            onSave = { h, a, x -> onSaveScore(fx.id, h, a, x); scoring = null },
            onRemove = { scoring = null },
            onDismiss = { scoring = null },
            suggested = suggestedScores(scoreHints(fx.home.orEmpty(), fx.away.orEmpty()))
        )
    }
}

@Composable
private fun LeagueFixtures(
    state: AppUiState,
    onOpen: (Int) -> Unit,
    onGoToData: () -> Unit,
    onMark: (date: String, home: String, away: String, pick: TrackablePick, correct: Boolean?) -> Unit,
    onLink: (name: String, target: String) -> Unit,
    onSaveScore: (fixtureId: Int, home: Int, away: Int, extras: MatchExtras?) -> Unit,
    onRemoveScore: (fixtureId: Int) -> Unit,
    scoreHints: (home: String, away: String) -> List<ScoreCell>
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
    // Week groups the user has folded or unfolded (flips the default for that group).
    var flipped by rememberSaveable { mutableStateOf(emptySet<String>()) }
    // The fixture name the user tapped "Link" on; the picker shows the matches file's teams.
    var linking by remember { mutableStateOf<String?>(null) }
    // The played fixture whose score is being typed in.
    var scoring by remember { mutableStateOf<FixtureItem?>(null) }

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

        val thisWeek = ReportBuilder.weekStartOf(LocalDate.now().toString())
        val card: @Composable (FixtureItem, Boolean, Modifier) -> Unit = { fx, marksOn, mod ->
            val sk = scoreKey(fx.fixture.date, fx.home.orEmpty(), fx.away.orEmpty())
            FixtureCard(
                fx, state.marks, leagueId, showMarks = marksOn, onMark = onMark,
                onLinkName = { linking = it }, onClick = { onOpen(fx.id) }, modifier = mod,
                score = state.scores[sk], scoreEditable = sk in state.addedScoreKeys, onScore = { scoring = fx }
            )
        }

        // Played weeks come first (most recent week on top), then upcoming weeks, then not-ready games.
        if (showPlayed) {
            weekGroups(
                prefix = "p", fixtures = played, thisWeek = thisWeek, flipped = flipped,
                onFlip = { flipped = if (it in flipped) flipped - it else flipped + it },
                defaultOpen = { index, _ -> index == 0 }, showMarks = true, card = card
            )
        }
        weekGroups(
            prefix = "u", fixtures = upcoming, thisWeek = thisWeek, flipped = flipped,
            onFlip = { flipped = if (it in flipped) flipped - it else flipped + it },
            defaultOpen = { _, week -> week == null || thisWeek == null || !week.isAfter(thisWeek.plusWeeks(1)) },
            showMarks = false, card = card
        )
        if (showNotReady && notReady.isNotEmpty()) {
            weekGroups(
                prefix = "n", fixtures = notReady, thisWeek = thisWeek, flipped = flipped,
                onFlip = { flipped = if (it in flipped) flipped - it else flipped + it },
                defaultOpen = { _, _ -> true }, showMarks = false, card = card, singleGroupTitle = "Not ready"
            )
        }
    }

    scoring?.let { fx ->
        val sk = scoreKey(fx.fixture.date, fx.home.orEmpty(), fx.away.orEmpty())
        val current = state.scores[sk]
        ScoreDialog(
            home = fx.home.orEmpty(), away = fx.away.orEmpty(),
            initial = current?.let { it.homeGoals to it.awayGoals },
            initialExtras = state.extras[fixtureKey(state.selected?.id.orEmpty(), fx.fixture.date, fx.home.orEmpty(), fx.away.orEmpty())],
            canRemove = sk in state.addedScoreKeys,
            onSave = { h, a, x -> onSaveScore(fx.id, h, a, x); scoring = null },
            onRemove = { onRemoveScore(fx.id); scoring = null },
            onDismiss = { scoring = null },
            suggested = suggestedScores(scoreHints(fx.home.orEmpty(), fx.away.orEmpty()))
        )
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

/**
 * Lays fixtures out as Monday-Sunday week groups. Each group has a foldable header and its games
 * sit side by side in a horizontally scrolling row. [defaultOpen] decides which groups start unfolded
 * (index within this list, week start); tapping a header flips that group.
 */
private fun LazyListScope.weekGroups(
    prefix: String,
    fixtures: List<FixtureItem>,
    thisWeek: LocalDate?,
    flipped: Set<String>,
    onFlip: (String) -> Unit,
    defaultOpen: (index: Int, week: LocalDate?) -> Boolean,
    showMarks: Boolean,
    card: @Composable (FixtureItem, Boolean, Modifier) -> Unit,
    singleGroupTitle: String? = null
) {
    val groups = LinkedHashMap<LocalDate?, MutableList<FixtureItem>>()
    for (fx in fixtures) groups.getOrPut(ReportBuilder.weekStartOf(fx.fixture.date)) { ArrayList() }.add(fx)
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    groups.entries.forEachIndexed { index, (week, list) ->
        val key = "$prefix|${week ?: "none"}"
        val open = defaultOpen(index, week) != (key in flipped)
        val ordered = list.sortedBy { DateUtils.sortKey(it.fixture.date, it.fixture.time) }
        val title = singleGroupTitle ?: when {
            week == null -> "Date TBC"
            week == thisWeek -> "This week"
            thisWeek != null && week == thisWeek.plusWeeks(1) -> "Next week"
            thisWeek != null && week == thisWeek.minusWeeks(1) -> "Last week"
            else -> "Week of ${week.format(fmt)}"
        }
        val range = if (week != null) "${week.format(fmt)} - ${week.plusDays(6).format(fmt)}" else ""
        item(key = "wk_$key") {
            AppCard(onClick = { onFlip(key) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            listOf(range, "${list.size} ${if (list.size == 1) "game" else "games"}")
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Icon(
                        if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (open) "Fold" else "Unfold",
                        tint = TextSecondary
                    )
                }
            }
        }
        if (open) {
            item(key = "row_$key") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(ordered, key = { "fx_${it.id}" }) { fx -> card(fx, showMarks, Modifier.width(300.dp)) }
                }
            }
        }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    score: Match? = null,
    scoreEditable: Boolean = false,
    onScore: () -> Unit = {}
) {
    val summary = item.summary
    AppCard(modifier = modifier, onClick = if (summary != null) onClick else null) {
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
            if (item.fixture.time.isNotBlank() || item.fixture.date.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                TagChip(DateUtils.kickoffLabel(item.fixture.date, item.fixture.time), IndigoPrimary)
            }
        }

        if (showMarks && item.home != null && item.away != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (score != null) {
                    TagChip("Final ${score.homeGoals} - ${score.awayGoals}", WinGreen)
                    TextButton(onClick = onScore) {
                        Text(if (scoreEditable) "Edit score" else "Fix score", color = IndigoPrimary)
                    }
                } else {
                    TextButton(onClick = onScore) { Text("Add score", color = IndigoPrimary) }
                }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pick.label,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (i == 0) WinGreen else TextPrimary
                        )
                        if (i == 0) {
                            Spacer(Modifier.width(8.dp))
                            BestPickChip(pick)
                        }
                    }
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
