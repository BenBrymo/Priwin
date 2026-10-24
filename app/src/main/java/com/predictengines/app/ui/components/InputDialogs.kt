package com.predictengines.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.theme.CardSurface
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.OutlineSubtle
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary

@Composable
fun inputColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CardSurfaceAlt,
    unfocusedContainerColor = CardSurfaceAlt,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = IndigoPrimary,
    unfocusedLabelColor = TextSecondary,
    focusedBorderColor = IndigoPrimary,
    unfocusedBorderColor = OutlineSubtle,
    cursorColor = IndigoPrimary
)

/** Type in the final score of a played fixture. [initial] is the current score when editing. */
@Composable
fun ScoreDialog(
    home: String,
    away: String,
    initial: Pair<Int, Int>?,
    canRemove: Boolean,
    onSave: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var hg by remember { mutableStateOf(initial?.first?.toString().orEmpty()) }
    var ag by remember { mutableStateOf(initial?.second?.toString().orEmpty()) }
    val h = hg.toIntOrNull()
    val a = ag.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Final score") },
        text = {
            Column {
                Text(
                    "Saved into this league's matches and used to update the stats.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hg, onValueChange = { hg = it.filter(Char::isDigit).take(2) },
                        singleLine = true, label = { Text(home, maxLines = 1) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = inputColors(), modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ag, onValueChange = { ag = it.filter(Char::isDigit).take(2) },
                        singleLine = true, label = { Text(away, maxLines = 1) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = inputColors(), modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = h != null && a != null, onClick = { onSave(h!!, a!!) }) {
                Text("Save", color = if (h != null && a != null) IndigoPrimary else TextMuted)
            }
        },
        dismissButton = {
            Row {
                if (canRemove) TextButton(onClick = onRemove) { Text("Remove", color = LossRed) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}
