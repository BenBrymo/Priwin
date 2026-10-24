package com.predictengines.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import java.util.Locale

data class MarketRow(val label: String, val percent: Double)

/** Market | Probability | Fair odds table (fair odds = 100 / probability, i.e. no bookmaker margin). */
@Composable
fun MarketsTable(rows: List<MarketRow>, accent: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 6.dp)) {
            Text("Market", Modifier.weight(1.8f), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                "Chance", Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall, color = TextSecondary
            )
            Text(
                "Fair odds", Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall, color = TextSecondary
            )
        }
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (index % 2 == 0) CardSurfaceAlt else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Text(
                    row.label, Modifier.weight(1.8f),
                    style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    String.format(Locale.US, "%.1f%%", row.percent), Modifier.weight(1f),
                    textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
                    color = accent, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (row.percent < 0.5) "—" else String.format(Locale.US, "%.2f", 100.0 / row.percent),
                    Modifier.weight(1f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                )
            }
        }
    }
}
