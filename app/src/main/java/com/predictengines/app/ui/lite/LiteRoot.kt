package com.predictengines.app.ui.lite

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.LocalKeyPlayers
import com.predictengines.app.ui.components.PillChip
import com.predictengines.app.ui.components.PrimaryButton
import com.predictengines.app.ui.screens.FixturesScreen
import com.predictengines.app.ui.theme.DeepSpace
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.MidnightSurface
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.viewmodel.AppViewModel

private enum class LiteTab(val label: String, val icon: ImageVector) {
    SLIPS("Slips", Icons.Filled.ConfirmationNumber),
    TODAY("Today", Icons.Filled.Today),
    LEAGUES("Leagues", Icons.Filled.SportsSoccer),
    RECORD("Record", Icons.Filled.Assessment),
    ABOUT("About", Icons.Filled.Info)
}

private fun ageConfirmed(ctx: Context) = ctx.getSharedPreferences("lite", Context.MODE_PRIVATE).getBoolean("age_ok", false)

private fun confirmAge(ctx: Context) { ctx.getSharedPreferences("lite", Context.MODE_PRIVATE).edit().putBoolean("age_ok", true).apply() }

/** Priwin Picks: the simple app for people who want predictions and slips. */
@Composable
fun LiteRoot(vm: AppViewModel) {
    val ctx = LocalContext.current
    var adult by remember { mutableStateOf(ageConfirmed(ctx)) }
    if (!adult) {
        Column(
            Modifier.fillMaxSize().background(DeepSpace).padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Priwin Picks", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(
                "This app shows football predictions and ready-made slips for people who bet. It does not take bets, and no pick is guaranteed to win. " +
                    "You must be 18 or older to use it. Only bet what you can afford to lose.",
                style = MaterialTheme.typography.bodyLarge, color = TextSecondary
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "I am 18 or older", onClick = { confirmAge(ctx); adult = true }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { (ctx as? Activity)?.finish() }, modifier = Modifier.fillMaxWidth()) {
                Text("I am under 18 (close the app)", color = TextMuted)
            }
        }
        return
    }

    val state by vm.state.collectAsState()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = LiteTab.entries[tabIndex]
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.consumeMessage()
        }
    }
    // look for a newer data pack whenever the app opens
    LaunchedEffect(Unit) { vm.updatePack() }

    val detailOpen = tab == LiteTab.LEAGUES && state.openFixtureId != null
    BackHandler(enabled = detailOpen) { vm.closeFixture() }

    com.predictengines.app.ui.components.ProvideClock {
        CompositionLocalProvider(
            LocalKeyPlayers provides state.players,
            com.predictengines.app.ui.screens.LocalCaptainBans provides state.cpBans,
            com.predictengines.app.ui.screens.LocalCaptainLeague provides state.selected?.id.orEmpty()
        ) {
            Scaffold(
                containerColor = DeepSpace,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar(containerColor = MidnightSurface) {
                        LiteTab.entries.forEachIndexed { index, t ->
                            NavigationBarItem(
                                selected = index == tabIndex,
                                onClick = { tabIndex = index },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TextPrimary, selectedTextColor = TextPrimary, indicatorColor = IndigoPrimary,
                                    unselectedIconColor = TextMuted, unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }
            ) { inner: PaddingValues ->
                Column(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MidnightSurface, DeepSpace))).padding(inner)
                ) {
                    if (!detailOpen) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                            Text("Priwin Picks", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                            Text("Predictions and slips. 18+. Bet responsibly.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (tab == LiteTab.LEAGUES && !detailOpen) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.leagues, key = { it.id }) { league ->
                                PillChip(text = league.name, selected = league.id == state.selected?.id, onClick = { vm.selectLeague(league.id) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(Modifier.fillMaxSize()) {
                        when (tab) {
                            LiteTab.SLIPS -> LiteSlipsScreen(state, onOpenPick = { vm.openTopPick(it); tabIndex = LiteTab.LEAGUES.ordinal })
                            LiteTab.TODAY -> LiteTodayScreen(state, onOpenPick = { vm.openTopPick(it); tabIndex = LiteTab.LEAGUES.ordinal })
                            LiteTab.LEAGUES -> FixturesScreen(
                                state = state,
                                onOpen = vm::openFixture,
                                onClose = vm::closeFixture,
                                onGoToData = { tabIndex = LiteTab.ABOUT.ordinal },
                                onMark = vm::setMark,
                                onLink = vm::linkNames,
                                onOpenTopPick = vm::openTopPick,
                                onSaveScore = { id, h, a, x -> vm.saveScore(id, h, a, x) },
                                onRemoveScore = vm::removeScore,
                                onSaveScores = vm::saveScores,
                                scoreHints = vm::scoreHints
                            )
                            LiteTab.RECORD -> LiteRecordScreen(state)
                            LiteTab.ABOUT -> LiteAboutScreen(state, onCheck = { vm.updatePack(force = true) })
                        }
                    }
                }
            }
        }
    }
}
