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
    STATS("Stats", Icons.Filled.Edit),
    DATA("Data", Icons.Filled.Storage)
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsState()
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

    Scaffold(
        containerColor = DeepSpace,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MidnightSurface) {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
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
                            if (n > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-6).dp)
                                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                        .clip(CircleShape)
                                        .background(WinGreen)
                                        .padding(horizontal = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (n > 99) "99+" else n.toString(),
                                        style = MaterialTheme.typography.labelSmall, color = Color.Black, maxLines = 1
                                    )
                                }
                            }
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
