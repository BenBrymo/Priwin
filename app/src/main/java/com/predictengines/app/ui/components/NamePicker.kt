package com.predictengines.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.TeamMatcher
import com.predictengines.app.ui.theme.CardSurface
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.OutlineSubtle
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary

/**
 * "Which team is this?" picker. Shows [candidates] (the names from the file the app couldn't find
 * [name] in), likeliest first, with a search box. Picking one links the two names as the same club.
 */
@Composable
fun NamePickerDialog(
    name: String,
    candidates: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    sourceDescription: String = "the data"
) {
    var query by remember { mutableStateOf("") }
    val ranked = remember(name, candidates) { TeamMatcher.suggest(name, candidates) }
    val shown = remember(ranked, query) {
        if (query.isBlank()) ranked else ranked.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Link \"$name\" to…") },
        text = {
            Column {
                Text(
                    "Pick the team in $sourceDescription that is the same club. Likeliest first. " +
                        "The link is remembered, so you only do this once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
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
                )
                if (shown.isEmpty()) {
                    Text(
                        if (candidates.isEmpty()) "There are no names to choose from yet." else "No team matches that search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(top = 8.dp)) {
                        items(shown, key = { it }) { candidate ->
                            Text(
                                candidate,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(candidate) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
