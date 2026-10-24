package com.predictengines.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.TeamStats
import com.predictengines.app.ui.theme.OutlineSubtle
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import java.util.Locale

/** Side-by-side form table for the two teams. */
@Composable
fun StatsComparisonTable(home: TeamStats, away: TeamStats) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text("", modifier = Modifier.weight(1.2f))
            Text(
                home.teamName,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                away.teamName,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(color = OutlineSubtle)

        StatsRow("Matches", home.played.toString(), away.played.toString())
        StatsRow("W-D-L", "${home.wins}-${home.draws}-${home.losses}", "${away.wins}-${away.draws}-${away.losses}")
        StatsRow("Goals for / game", f2(home.avgGoalsFor), f2(away.avgGoalsFor))
        StatsRow("Goals against / game", f2(home.avgGoalsAgainst), f2(away.avgGoalsAgainst))
        StatsRow("BTTS rate", f0(home.bttsRatePercent), f0(away.bttsRatePercent))
    }
}

private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
private fun f0(v: Double) = String.format(Locale.US, "%.0f%%", v)

@Composable
private fun StatsRow(label: String, homeValue: String, awayValue: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(label, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            homeValue, modifier = Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge, color = TextPrimary
        )
        Text(
            awayValue, modifier = Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge, color = TextPrimary
        )
    }
    HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.5f))
}
