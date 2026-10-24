package com.predictengines.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.VioletAccent
import com.predictengines.app.ui.theme.WinGreen
import kotlin.math.roundToInt

/** Stacked horizontal bar showing the Home / Draw / Away probability split. */
@Composable
fun ThreeWayBar(
    homeLabel: String,
    homePercent: Double,
    drawPercent: Double,
    awayLabel: String,
    awayPercent: Double,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50))
        ) {
            Box(Modifier.weight(homePercent.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(WinGreen))
            Box(Modifier.weight(drawPercent.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(DrawAmber))
            Box(Modifier.weight(awayPercent.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(VioletAccent))
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            LegendItem(homeLabel, homePercent, WinGreen, Alignment.Start, Modifier.weight(1f))
            LegendItem("Draw", drawPercent, DrawAmber, Alignment.CenterHorizontally, Modifier.weight(1f))
            LegendItem(awayLabel, awayPercent, VioletAccent, Alignment.End, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    percent: Double,
    color: Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = 2.dp), horizontalAlignment = alignment) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${percent.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
    }
}
