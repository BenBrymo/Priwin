package com.predictengines.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.predictengines.app.ui.theme.WinGreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.predictengines.app.ui.components.LocalKeyPlayers
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.components.PillChip
import com.predictengines.app.ui.screens.DataScreen
import com.predictengines.app.ui.screens.FixturesScreen
import com.predictengines.app.ui.screens.StatsEditorScreen
import com.predictengines.app.ui.screens.MatchupScreen
import com.predictengines.app.ui.components.inputColors
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import com.predictengines.app.ui.screens.PlayersScreen
import androidx.compose.material.icons.filled.Groups
import com.predictengines.app.ui.screens.ReportScreen
import com.predictengines.app.ui.theme.CardSurface
import com.predictengines.app.ui.theme.DeepSpace
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.MidnightSurface
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.viewmodel.AppViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    FIXTURES("Fixtures", Icons.Filled.SportsSoccer),
    MATCHUP("Matchup", Icons.Filled.Insights),
    REPORTS("Reports", Icons.Filled.Assessment),
    PLAYERS("Players", Icons.Filled.Groups),
    STATS("Stats", Icons.Filled.Edit),
    DATA("Data", Icons.Filled.Storage)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val navScope = rememberCoroutineScope()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    val detailOpen = tab == Tab.FIXTURES && state.openFixtureId != null

    // Played games with no score, per league. The open league is counted live; the others come from the background load.
    val missing: Map<String, Int> = remember(state.missingByLeague, state.fixtures, state.scores, state.selected, state.isLoading) {
        val m = HashMap(state.missingByLeague)
        val sel = state.selected
        if (sel != null && !state.isLoading) m[sel.id] = com.predictengines.app.ui.screens.missingResults(state).size
        m.filterValues { it > 0 }
    }
    var missingDismissed by rememberSaveable { mutableStateOf(false) }
    var showMissingList by remember { mutableStateOf(false) }
    var jumpToResults by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = detailOpen) { vm.closeFixture() }

    // A hand-entered score that differs from the matches file: ask which one is right.
    var snoozed by rememberSaveable { mutableStateOf("") }
    val conflictSignature = state.conflicts.joinToString(";") { it.key }
    val conflict = state.conflicts.firstOrNull()
    if (conflict != null && snoozed != conflictSignature) {
        AlertDialog(
            onDismissRequest = { snoozed = conflictSignature },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Which score is right?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${conflict.home} v ${conflict.away} (${conflict.date})",
                        style = MaterialTheme.typography.titleMedium, color = TextPrimary
                    )
                    Text("You entered ${conflict.mineHome}-${conflict.mineAway}", color = TextPrimary)
                    Text("The matches file says ${conflict.csvHome}-${conflict.csvAway}", color = TextPrimary)
                    Text(
                        "Until you decide, your own score is used. Corners, cards and first scorer you entered are kept either way." +
                            if (state.conflicts.size > 1) " ${state.conflicts.size - 1} more to review after this one." else "",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.resolveConflict(conflict, useFile = false) }) {
                    Text("Keep mine (${conflict.mineHome}-${conflict.mineAway})", color = IndigoPrimary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.resolveConflict(conflict, useFile = true) }) {
                        Text("Use file (${conflict.csvHome}-${conflict.csvAway})", color = IndigoPrimary)
                    }
                    TextButton(onClick = { snoozed = conflictSignature }) { Text("Later", color = TextSecondary) }
                }
            }
        )
    }

    // A red card in a team's last game: ask who was sent off, so a key player's ban is counted.
    // "Later" is remembered for each red card separately (until the app is closed), so one answered or snoozed prompt never brings another back
    var redSnoozed by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val red = state.pendingReds.firstOrNull { it.key !in redSnoozed }
    if (red != null) {
        val squad = state.players.filter { it.leagueId == red.leagueId && it.team == red.team }
        var banGames by remember(red.key) { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { redSnoozed = redSnoozed + red.key },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Who was sent off?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${red.team} had a red card against ${red.opponent} (${red.date}).",
                        style = MaterialTheme.typography.bodyMedium, color = TextPrimary
                    )
                    OutlinedTextField(
                        value = banGames, onValueChange = { banGames = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Ban length in games") }, singleLine = true, colors = inputColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (squad.isEmpty()) {
                        Text(
                            "${red.team} has no key players yet. Add them on the Players tab to have a ban counted.",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                        )
                    } else {
                        squad.forEach { p ->
                            TextButton(
                                onClick = { vm.answerRed(red, p.id, banGames.toIntOrNull() ?: 1) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("${p.name} (${p.position.short})", color = IndigoPrimary, modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.answerRed(red, null, 1) }) { Text("Someone else", color = IndigoPrimary) }
            },
            dismissButton = { TextButton(onClick = { redSnoozed = redSnoozed + red.key }) { Text("Later", color = TextSecondary) } }
        )
    }

    if (showMissingList) {
        AlertDialog(
            onDismissRequest = { showMissingList = false },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Games waiting for a result") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.leagues.filter { (missing[it.id] ?: 0) > 0 }.forEach { l ->
                        TextButton(
                            onClick = {
                                showMissingList = false
                                tabIndex = Tab.FIXTURES.ordinal
                                if (l.id != state.selected?.id) vm.selectLeague(l.id)
                                jumpToResults += 1
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(l.name, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text("${missing[l.id]}", color = WinGreen)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMissingList = false }) { Text("Close", color = TextSecondary) } }
        )
    }

    com.predictengines.app.ui.components.ProvideClock {
    CompositionLocalProvider(
        LocalKeyPlayers provides state.players,
        com.predictengines.app.ui.components.LocalKryptoDismiss provides { f: com.predictengines.app.data.KryptoFlag -> vm.dismissKryptoFlag(f) }
    ) {
    Scaffold(
        containerColor = DeepSpace,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MidnightSurface) {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        // icons only; a long press names the tab
                        icon = {
                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = { tabIndex = index },
                                    onLongClick = { navScope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(t.label) } }
                                )
                            ) { Icon(t.icon, contentDescription = t.label) }
                        },
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TextPrimary,
                            selectedTextColor = TextPrimary,
                            indicatorColor = IndigoPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { inner: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(MidnightSurface, DeepSpace)))
                .padding(inner)
        ) {
            if (!detailOpen) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    Text(
                        "Priwin",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        "Brymo + Poisson + Stats, with a consensus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.leagues, key = { it.id }) { league ->
                        val n = missing[league.id] ?: 0
                        Box(modifier = Modifier.padding(top = 6.dp, end = 6.dp)) {
                            PillChip(
                                text = league.name,
                                selected = league.id == state.selected?.id,
                                onClick = { vm.selectLeague(league.id) }
                            )
                            com.predictengines.app.ui.components.ResultBadge(
                                count = n, checking = league.id in state.missingChecking,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)
                            )
                        }
                    }
                }
                if (tab == Tab.FIXTURES && missing.isNotEmpty() && !missingDismissed) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardSurface)
                            .padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${missing.values.sum()} played ${if (missing.values.sum() == 1) "game needs" else "games need"} a result" +
                                " in ${missing.size} ${if (missing.size == 1) "league" else "leagues"}",
                            style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showMissingList = true }) { Text("Show", color = IndigoPrimary) }
                        TextButton(onClick = { missingDismissed = true }) { Text("Hide", color = TextSecondary) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.FIXTURES -> FixturesScreen(
                        state = state,
                        onOpen = vm::openFixture,
                        onClose = vm::closeFixture,
                        onGoToData = { tabIndex = Tab.DATA.ordinal },
                        onMark = vm::setMark,
                        onLink = vm::linkNames,
                        onOpenTopPick = vm::openTopPick,
                        onSaveScore = { id, h, a, x -> vm.saveScore(id, h, a, x) },
                        onRemoveScore = vm::removeScore,
                        onSaveScores = vm::saveScores,
                        scoreHints = vm::scoreHints,
                        jumpToResults = jumpToResults
                    )
                    Tab.MATCHUP -> MatchupScreen(
                        state = state,
                        onHome = vm::selectHome,
                        onAway = vm::selectAway,
                        onSwap = vm::swapTeams,
                        onRun = vm::runManual,
                        onLink = vm::linkNames
                    )
                    Tab.REPORTS -> ReportScreen(state)
                    Tab.PLAYERS -> PlayersScreen(state, onSave = vm::savePlayer, onDelete = vm::deletePlayer, onSaveNews = vm::saveNews, onDeleteNews = vm::deleteNews)
                    Tab.STATS -> StatsEditorScreen(
                        state = state,
                        onRepair = vm::repairStatsChanges,
                        onEdit = vm::editStat,
                        onAdd = vm::addStat,
                        onDelete = vm::deleteStats,
                        onExport = vm::exportStats
                    )
                    Tab.DATA -> DataScreen(
                        state = state,
                        onImport = vm::importFile,
                        onClear = vm::clearImport,
                        onAddLeague = vm::addLeague,
                        onDeleteLeague = vm::deleteLeague,
                        onHideLeague = vm::hideLeague,
                        onResetLeague = vm::resetLeague,
                        onSetKryptoMode = vm::setKryptoMode,
                        onSetKryptoParam = vm::setKryptoParam,
                        onSetKryptoMarkets = vm::setKryptoMarkets,
                        onResetKryptoRule = vm::resetKryptoRule,
                        onRestoreDismissed = vm::restoreDismissedFlags,
                        onSetLokiOn = vm::setLokiOn,
                        onSetLokiParam = vm::setLokiParam,
                        onCheckKrypto = vm::checkKryptonites,
                        onShowLeague = vm::showLeague,
                        onUnlink = vm::unlinkNames,
                        onExportMatches = vm::exportMatches,
                        onBackup = vm::backupTo,
                        onRestore = vm::restoreFrom,
                        onSetAutoSettleFrom = vm::setAutoSettleFrom
                    )
                }
            }
        }
    }
    }
    }
}
