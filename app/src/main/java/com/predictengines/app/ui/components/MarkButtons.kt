package com.predictengines.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.DeepSpace
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen

/**
 * Tick / cross pair. [state] is true (correct), false (wrong) or null (not marked yet).
 * Tapping the active button clears the mark.
 */
@Composable
fun MarkButtons(state: Boolean?, onChange: (Boolean?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarkButton(Icons.Filled.Check, "Mark correct", state == true, WinGreen) {
            onChange(if (state == true) null else true)
        }
        MarkButton(Icons.Filled.Close, "Mark wrong", state == false, LossRed) {
            onChange(if (state == false) null else false)
        }
    }
}

@Composable
private fun MarkButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) color else CardSurfaceAlt)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) DeepSpace else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
