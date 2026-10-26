package com.predictengines.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary

/**
 * A row of tappable numbers 0..[max] (scrolls sideways). When [clearable], tapping the chosen number again
 * clears it, so optional values can be left unknown.
 */
@Composable
fun NumberPickerRow(
    label: String,
    value: Int?,
    max: Int,
    onPick: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = IndigoPrimary,
    clearable: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (value != null) "$label: $value" else label,
            style = MaterialTheme.typography.labelMedium,
            color = if (value != null) TextPrimary else TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (n in 0..max) {
                val selected = value == n
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else CardSurfaceAlt)
                        .clickable { onPick(if (selected && clearable) null else n) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = n.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) TextPrimary else TextMuted
                    )
                }
            }
        }
    }
}
