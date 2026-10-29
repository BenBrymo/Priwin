package com.predictengines.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Switch
import com.predictengines.app.ui.components.PillChip
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.reminders.ReminderPrefs
import com.predictengines.app.reminders.ReminderScheduler
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.DataKind
import com.predictengines.app.data.ImportInfo
import com.predictengines.app.data.League
import com.predictengines.app.data.NameLink
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.CodeLine
import com.predictengines.app.ui.components.PrimaryButton
import com.predictengines.app.ui.components.SecondaryButton
import com.predictengines.app.ui.components.TagChip
import com.predictengines.app.ui.theme.BrymoAccent
import com.predictengines.app.ui.theme.CardSurface
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.OutlineSubtle
import com.predictengines.app.ui.theme.PoissonAccent
import com.predictengines.app.ui.theme.StatsAccent
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.VioletAccent
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataScreen(
    state: AppUiState,
    onImport: (DataKind, Uri) -> Unit,
    onClear: (DataKind) -> Unit,
    onAddLeague: (String) -> Unit,
    onDeleteLeague: (League) -> Unit,
    onHideLeague: (League) -> Unit,
    onResetLeague: (League) -> Unit,
    onShowLeague: (League) -> Unit,
    onUnlink: (NameLink) -> Unit,
    onExportMatches: (Uri) -> Unit,
    onBackup: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
    onSetAutoSettleFrom: (String) -> Unit
) {
    val league = state.selected
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) onExportMatches(uri)
    }

    // One system file picker, reused for both file types. Storage Access Framework:
    // no storage permission is needed, the user just picks the file.
    var pendingKind by remember { mutableStateOf<DataKind?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val kind = pendingKind
        if (uri != null && kind != null) onImport(kind, uri)
        pendingKind = null
    }
    fun pick(kind: DataKind) {
        pendingKind = kind
        picker.launch(arrayOf("*/*"))
    }

    val backupMaker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) onBackup(uri)
    }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) restoreUri = uri
    }

    var showAdd by remember { mutableStateOf(false) }
    var editingSettleDate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<League?>(null) }
    var toReset by remember { mutableStateOf<League?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (league != null) {
            DataSourceCard(
                title = "Match results",
                subtitle = "Played games the models learn from",
                accent = BrymoAccent,
                info = state.matchesImport,
                fallback = if (league.assetFile != null) "Built-in dataset · ${state.matchCount} matches" else "Nothing loaded yet",
                canReset = state.matchesImport != null,
                resetLabel = if (league.assetFile != null) "Restore built-in" else "Remove",
                onLoad = { pick(DataKind.MATCHES) },
                onReset = { onClear(DataKind.MATCHES) }
            )
            if (state.addedScoreKeys.isNotEmpty() || state.matchCount > 0) {
                SecondaryButton(
                    text = "Export ${league.name} matches" + if (state.addedScoreKeys.isNotEmpty()) " (incl. ${state.addedScoreKeys.size} added scores)" else "",
                    onClick = { exporter.launch("${league.id}_matches.csv") },
                    icon = Icons.Filled.Download
                )
            }

            DataSourceCard(
                title = "League fixtures",
                subtitle = "Upcoming games to predict",
                accent = PoissonAccent,
                info = state.fixturesImport,
                fallback = if (state.builtInFixtures) {
                    "Built-in fixtures · ${state.fixtures.size} games"
                } else "No fixtures loaded yet",
                canReset = state.fixturesImport != null,
                resetLabel = if (state.builtInFixtures) "Restore built-in" else "Remove",
                onLoad = { pick(DataKind.FIXTURES) },
                onReset = { onClear(DataKind.FIXTURES) }
            )
        }

        DataSourceCard(
            title = "Stats data",
            subtitle = "Stats model · shared by all leagues · a loaded file is added on top of the built-in stats",
            accent = StatsAccent,
            info = state.statsImport,
            fallback = if (state.statsRowCount > 0) "Built-in stats · ${state.statsRowCount} rows" else "No stats loaded",
            canReset = state.statsImport != null,
            resetLabel = "Restore built-in",
            onLoad = { pick(DataKind.STATS) },
            onReset = { onClear(DataKind.STATS) }
        )
        if (state.statsRowCount > 0) {
            Text(
                "Used markets: ${state.statsUsedMarkets.joinToString().ifEmpty { "none" }}" +
                    if (state.statsIgnoredMarkets.isNotEmpty()) {
                        "\nIgnored (not understood): ${state.statsIgnoredMarkets.joinToString()}"
                    } else "",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        AppCard(title = "File format", subtitle = "CSV, TSV or TXT · comma, semicolon or tab", accentColor = IndigoPrimary) {
            Text("Match results", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            CodeLine("Date,Time,HomeTeam,AwayTeam,FTHG,FTAG\n21/08/2026,20:00,Arsenal,Coventry,3,0")
            Spacer(Modifier.height(14.dp))
            Text("Fixtures", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            CodeLine("Date,HomeTeam,AwayTeam\n19/10/2026 19:00,Tottenham,Coventry")
            Spacer(Modifier.height(14.dp))
            Text("Stats", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            CodeLine("market,season,rank,team,stat_value,games,percentage\nAway Wins,2026/2027,2,Manchester City,3,3,100%")
            Spacer(Modifier.height(14.dp))
            Text(
                "Column order doesn't matter and extra columns are ignored. The kick-off time can be a separate " +
                    "Time column or sit in the Date cell (19/10/2026 19:00). Dates in both matches and fixtures " +
                    "files are Day/Month/Year; yyyy-MM-dd also works. Team names in the " +
                    "fixtures file are matched to the matches file automatically, ignoring case, accents and " +
                    "suffixes like FC.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        AppCard(
            title = "Name links",
            subtitle = "Names you told the app are the same club",
            accentColor = StatsAccent
        ) {
            Text(
                "Common spellings (Man City / Manchester City, AS Roma / Roma, Ath Madrid / Atletico Madrid) are " +
                    "matched automatically across your matches, fixtures and stats. If a team still isn't " +
                    "recognised, tap Link on it in Fixtures or in the Stats card and pick the right team. " +
                    "No file editing needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            if (state.nameLinks.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("No links yet.", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            } else {
                Spacer(Modifier.height(8.dp))
                state.nameLinks.forEach { link ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${link.a}  =  ${link.b}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        IconButton(onClick = { onUnlink(link) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove link", tint = LossRed)
                        }
                    }
                }
            }
        }

        AppCard(
            title = "Automatic settling",
            subtitle = "When a newer matches file contains the score of a played fixture, its stats and picks are settled for you, once.",
            accentColor = PoissonAccent
        ) {
            Text(
                "Counting games played on or after ${state.autoSettleFrom.ifBlank { "today" }}",
                style = MaterialTheme.typography.bodyLarge, color = TextPrimary
            )
            Text(
                "Earlier games are left alone so results already counted in your stats file aren't counted twice.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
            TextButton(onClick = { editingSettleDate = true }) { Text("Change start date", color = IndigoPrimary) }
        }

        ReminderCard()

        AppCard(
            title = "Backup & restore",
            subtitle = "Ticks and reports, name links, added scores, stats edits and loaded files in one file. Make one before uninstalling or changing phone.",
            accentColor = WinGreen
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Back up",
                    onClick = {
                        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        backupMaker.launch("priwin_backup_$day.zip")
                    },
                    icon = Icons.Filled.Download,
                    color = WinGreen,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Restore",
                    onClick = { restorePicker.launch(arrayOf("*/*")) },
                    icon = Icons.Filled.UploadFile,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AppCard(
            title = "Leagues",
            subtitle = "Add any league, then load its files. Hide a built-in league to take it out of the app (nothing is deleted).",
            accentColor = VioletAccent,
            trailing = {
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add league", tint = VioletAccent)
                }
            }
        ) {
            state.leagues.forEach { l ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        l.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (l.id == league?.id) TextPrimary else TextSecondary
                    )
                    IconButton(onClick = { toReset = l }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset ${l.name}", tint = TextSecondary)
                    }
                    if (l.isCustom) {
                        TagChip("Custom", VioletAccent)
                        IconButton(onClick = { toDelete = l }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${l.name}", tint = LossRed)
                        }
                    } else {
                        Text("Built-in", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        IconButton(onClick = { onHideLeague(l) }) {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = "Hide ${l.name}", tint = TextSecondary)
                        }
                    }
                }
            }
            if (state.duplicateLeagues.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Left out: same name as another league", style = MaterialTheme.typography.labelLarge, color = DrawAmber)
                Text(
                    "The copy with the right files is kept. These are not used anywhere; delete a custom one to tidy up.",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted
                )
                state.duplicateLeagues.forEach { l ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            l.name + if (l.isCustom) " (custom)" else " (built-in)",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = TextMuted
                        )
                        if (l.isCustom) {
                            IconButton(onClick = { toDelete = l }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${l.name}", tint = LossRed)
                            }
                        }
                    }
                }
            }
            if (state.hiddenLeagues.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Hidden leagues", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                state.hiddenLeagues.forEach { l ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(l.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = TextMuted)
                        TextButton(onClick = { onShowLeague(l) }) { Text("Show again", color = IndigoPrimary) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Add a league",
                onClick = { showAdd = true },
                icon = Icons.Filled.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Add league") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("League name") },
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
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onAddLeague(name.trim())
                        showAdd = false
                    }
                ) { Text("Add", color = if (name.isNotBlank()) IndigoPrimary else TextMuted) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (editingSettleDate) {
        var text by remember { mutableStateOf(state.autoSettleFrom) }
        val valid = Regex("\\d{4}-\\d{2}-\\d{2}").matches(text) && runCatching { java.time.LocalDate.parse(text) }.isSuccess
        AlertDialog(
            onDismissRequest = { editingSettleDate = false },
            containerColor = CardSurface, titleContentColor = TextPrimary, textContentColor = TextSecondary,
            title = { Text("Settle games played from") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(10) }, singleLine = true,
                    label = { Text("Date (yyyy-MM-dd)") }, colors = com.predictengines.app.ui.components.inputColors()
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { onSetAutoSettleFrom(text); editingSettleDate = false }) {
                    Text("Save", color = if (valid) IndigoPrimary else TextMuted)
                }
            },
            dismissButton = { TextButton(onClick = { editingSettleDate = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Restore this backup?") },
            text = { Text("This replaces your current ticks, name links, added scores, stats edits and loaded files with the ones in the backup.") },
            confirmButton = {
                TextButton(onClick = { onRestore(uri); restoreUri = null }) { Text("Restore", color = IndigoPrimary) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    toReset?.let { target ->
        AlertDialog(
            onDismissRequest = { toReset = null },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Reset ${target.name}?") },
            text = {
                Text(
                    "This removes the matches and fixtures files you imported for this league and every score you entered, " +
                        "and takes back what those scores added to your stats. The league goes back to its built-in data. " +
                        "Your own ticks, other leagues and the stats file's other changes are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = { onResetLeague(target); toReset = null }) { Text("Reset", color = LossRed) }
            },
            dismissButton = { TextButton(onClick = { toReset = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Delete ${target.name}?") },
            text = { Text("This removes the league and any files you loaded into it.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteLeague(target)
                    toDelete = null
                }) { Text("Delete", color = LossRed) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun DataSourceCard(
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    info: ImportInfo?,
    fallback: String,
    canReset: Boolean,
    resetLabel: String,
    onLoad: () -> Unit,
    onReset: () -> Unit
) {
    AppCard(title = title, subtitle = subtitle, accentColor = accent) {
        if (info != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip("Imported", WinGreen)
                Spacer(Modifier.width(10.dp))
                Text(
                    info.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            val skipped = if (info.skipped > 0) " · ${info.skipped} rows skipped" else ""
            Text(
                "${info.rows} rows$skipped · ${formatTime(info.importedAtMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        } else {
            Text(fallback, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = if (info != null) "Replace file" else "Load from phone",
                onClick = onLoad,
                icon = Icons.Filled.UploadFile,
                color = accent,
                modifier = Modifier.weight(1f)
            )
            if (canReset) {
                SecondaryButton(
                    text = resetLabel,
                    onClick = onReset,
                    icon = Icons.Filled.RestartAlt
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))


/** Daily phone reminder for the picks that kick off today and tomorrow (Top 20, Handicap and Over 1.5 lists). */
@Composable
private fun ReminderCard() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var on by remember { mutableStateOf(ReminderPrefs.enabled(context)) }
    var hour by remember { mutableStateOf(ReminderPrefs.hour(context)) }
    var note by remember { mutableStateOf<String?>(null) }

    fun apply(enabled: Boolean) {
        on = enabled
        ReminderPrefs.setEnabled(context, enabled)
        ReminderScheduler.sync(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            apply(true)
            note = null
        } else {
            apply(false)
            note = "Notifications are blocked for Priwin. Allow them in the phone's app settings to get reminders."
        }
    }

    AppCard(
        title = "Pick reminders",
        subtitle = "A notification at the time you choose, even when the app is closed, listing the Top 20, Handicap and Over 1.5 picks that kick off today and tomorrow.",
        accentColor = DrawAmber
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Remind me every day", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    if (!want) apply(false)
                    else if (ReminderScheduler.canNotify(context)) { apply(true); note = null }
                    else if (android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else apply(true)
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Time of day", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (h in listOf(6, 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 21)) {
                PillChip(
                    text = String.format(Locale.US, "%02d:00", h),
                    selected = hour == h,
                    onClick = {
                        hour = h
                        ReminderPrefs.setHour(context, h)
                        ReminderScheduler.sync(context)
                    }
                )
            }
        }
        Text(
            "The phone may deliver it a few minutes late to save battery.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted
        )
        TextButton(onClick = {
            note = when {
                !ReminderScheduler.canNotify(context) -> "Turn the reminders on first, and allow notifications when asked."
                ReminderScheduler.notify(context, force = true) -> "Sent. Check your notifications."
                else -> "Couldn't show a notification."
            }
        }) { Text("Send a test now", color = IndigoPrimary) }
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
    }
}
