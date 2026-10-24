package com.predictengines.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    BackHandler(enabled = detailOpen) { vm.closeFixture() }

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
                        PillChip(
                            text = league.name,
                            selected = league.id == state.selected?.id,
                            onClick = { vm.selectLeague(league.id) }
                        )
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
                        onSaveScore = vm::saveScore,
                        onRemoveScore = vm::removeScore
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
                        onUnlink = vm::unlinkNames,
                        onExportMatches = vm::exportMatches
                    )
                }
            }
        }
    }
}
