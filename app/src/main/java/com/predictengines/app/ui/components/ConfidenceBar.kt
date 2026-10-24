package com.predictengines.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.theme.OutlineSubtle
import com.predictengines.app.ui.theme.TextPrimary
import kotlin.math.roundToInt

/** A labeled row with a percentage-filled horizontal bar, used for prediction confidence. */
@Composable
fun ConfidenceBar(
    label: String,
    percent: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${percent.roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(OutlineSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percent / 100.0).coerceIn(0.0, 1.0).toFloat())
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

/** Small rounded chip used to display a tag (e.g. "Wins", "BTTS Yes") or short status. */
@Composable
fun TagChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
