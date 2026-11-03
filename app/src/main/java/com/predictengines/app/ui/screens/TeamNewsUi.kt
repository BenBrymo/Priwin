package com.predictengines.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.NewsType
import com.predictengines.app.data.TeamNews
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.PillChip
import com.predictengines.app.ui.components.TagChip
import com.predictengines.app.ui.components.inputColors
import com.predictengines.app.ui.theme.CardSurface
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary

/** One piece of team news on a team's page. */
@Composable
fun NewsRow(n: TeamNews, onEdit: () -> Unit, onDelete: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TagChip(n.type.label, DrawAmber)
                if (n.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(n.note, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
                Text(
                    "From ${n.date} · " + if (n.endDate.isNotBlank()) "until ${n.endDate}" else "${n.games} ${if (n.games == 1) "game" else "games"}",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextSecondary) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = LossRed) }
        }
    }
}

/** Adds or edits a piece of team news: its type, date, note, and how long it flags the team. */
@Composable
fun NewsDialog(initial: TeamNews?, leagueId: String, team: String, onSave: (TeamNews) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(initial?.type ?: NewsType.MANAGER) }
    var date by remember { mutableStateOf(initial?.date ?: java.time.LocalDate.now().toString()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var games by remember { mutableStateOf((initial?.games ?: type.defaultGames).toString()) }
    var endDate by remember { mutableStateOf(initial?.endDate.orEmpty()) }
    val id = remember { initial?.id ?: java.util.UUID.randomUUID().toString() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface, titleContentColor = TextPrimary, textContentColor = TextSecondary,
        title = { Text("Team news · $team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewsType.entries.forEach { t ->
                        PillChip(t.label, selected = type == t, onClick = {
                            // moving to another type offers that type's usual length, unless the user typed their own
                            if (games == type.defaultGames.toString()) games = t.defaultGames.toString()
                            type = t
                        })
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(80) }, label = { Text("Note (optional)") },
                    singleLine = true, colors = inputColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it.take(10) }, label = { Text("Happened on (yyyy-mm-dd)") },
                    singleLine = true, colors = inputColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = games, onValueChange = { games = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Flag the team's next games") }, singleLine = true, colors = inputColors(),
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = endDate, onValueChange = { endDate = it.take(10) }, label = { Text("Or until (yyyy-mm-dd, optional)") },
                    singleLine = true, colors = inputColors(), modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "An end date, when set, is used instead of the number of games.",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = date.length == 10,
                onClick = {
                    onSave(
                        TeamNews(id, leagueId, team, type, date.trim(), note.trim(), (games.toIntOrNull() ?: type.defaultGames).coerceIn(1, 30), endDate.trim())
                    )
                }
            ) { Text("Save", color = IndigoPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
