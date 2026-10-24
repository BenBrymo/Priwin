package com.predictengines.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.LabeledDropdown
import com.predictengines.app.ui.components.PrimaryButton
import com.predictengines.app.ui.theme.BrymoAccent
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.PoissonAccent
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.viewmodel.AppUiState

@Composable
fun MatchupScreen(
    state: AppUiState,
    onHome: (String) -> Unit,
    onAway: (String) -> Unit,
    onSwap: () -> Unit,
    onRun: () -> Unit,
    onLink: (name: String, target: String) -> Unit
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IndigoPrimary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppCard(
            title = "Pick any two teams",
            subtitle = "${state.teams.size} teams · ${state.matchCount} matches in ${state.selected?.name.orEmpty()}",
            accentColor = IndigoPrimary
        ) {
            LabeledDropdown(
                label = "Home team",
                options = state.teams,
                selected = state.homeTeam,
                onSelected = onHome,
                accentColor = BrymoAccent
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = onSwap,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardSurfaceAlt)
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Swap teams", tint = TextPrimary)
                }
            }
            LabeledDropdown(
                label = "Away team",
                options = state.teams,
                selected = state.awayTeam,
                onSelected = onAway,
                accentColor = PoissonAccent
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "Run all models",
                onClick = onRun,
                icon = Icons.Filled.Insights,
                enabled = state.teams.size >= 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.manualError?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LossRed.copy(alpha = 0.15f))
                    .padding(14.dp)
            ) {
                Text(message, color = LossRed, style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.manualAnalysis?.let {
            AnalysisContent(it, statsTeams = state.statsTeams, onLinkStatsName = onLink)
        }

        if (state.teams.size < 2) {
            Text(
                "This league has no match results yet. Load a matches file from the Data tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = LossRed
            )
        }
    }
}
