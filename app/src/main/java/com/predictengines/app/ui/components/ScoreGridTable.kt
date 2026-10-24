package com.predictengines.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.ScoreCell
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.DeepSpace
import com.predictengines.app.ui.theme.PoissonAccent
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import java.util.Locale

/** Table listing the most likely exact scorelines from the Poisson grid. */
@Composable
fun ScoreGridTable(scores: List<ScoreCell>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Scoreline", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("Likelihood", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        scores.forEachIndexed { index, cell ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (index == 0) CardSurfaceAlt else Color.Transparent)
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${cell.homeGoals} - ${cell.awayGoals}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = String.format(Locale.US, "%.1f%%", cell.probabilityPercent),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (index == 0) PoissonAccent else TextSecondary
                )
            }
        }
    }
}

/** Heat-map of scoreline probabilities. Rows = home goals, columns = away goals. */
@Composable
fun ScoreHeatmap(grid: List<List<Double>>, homeTeam: String, awayTeam: String) {
    val maxP = (grid.maxOfOrNull { row -> row.maxOrNull() ?: 0.0 } ?: 0.0).coerceAtLeast(0.0001)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Columns: $awayTeam goals   |   Rows: $homeTeam goals",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            for (a in grid.indices) {
                Text(
                    a.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
        }
        grid.forEachIndexed { h, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    h.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                row.forEach { p ->
                    val intensity = (p / maxP).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1.25f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PoissonAccent.copy(alpha = 0.08f + 0.85f * intensity)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", p),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (intensity > 0.55f) DeepSpace else TextPrimary
                        )
                    }
                }
            }
        }
    }
}
