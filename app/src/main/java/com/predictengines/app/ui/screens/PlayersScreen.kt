package com.predictengines.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.KeyPlayer
import com.predictengines.app.data.PlayerPosition
import com.predictengines.app.data.PlayerStatus
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.PillChip
import com.predictengines.app.ui.components.PrimaryButton
import com.predictengines.app.ui.components.TagChip
import com.predictengines.app.ui.components.inputColors
import com.predictengines.app.ui.theme.CardSurface
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.io.File

private fun statusColor(s: PlayerStatus): Color = when (s) {
    PlayerStatus.AVAILABLE -> WinGreen
    PlayerStatus.DOUBTFUL -> DrawAmber
    PlayerStatus.INJURED, PlayerStatus.SUSPENDED -> LossRed
}

/** Key players of every team in the open league, with photos, positions and who is out. */
@Composable
fun PlayersScreen(
    state: AppUiState,
    onSave: (KeyPlayer, Uri?) -> Unit,
    onDelete: (String) -> Unit
) {
    val league = state.selected
    var openTeam by rememberSaveable(league?.id) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<KeyPlayer?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<KeyPlayer?>(null) }

    if (league == null || state.teams.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            AppCard(title = "Key players", accentColor = IndigoPrimary) {
                Text(
                    "Load this league's matches file first (Data tab) so its teams are known, then add the key players of each team here.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                )
            }
        }
        return
    }

    val mine = state.players.filter { it.leagueId == league.id }
    val team = openTeam

    if (team == null) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text("Key players · ${league.name}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Pick a team and add the players that matter most. When one is injured, suspended or doubtful, that team's fixtures get a Kryptonite warning.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                )
            }
            items(state.teams, key = { it }) { t ->
                val list = mine.filter { it.team == t }
                val out = list.filter { it.status != PlayerStatus.AVAILABLE }
                AppCard(onClick = { openTeam = t }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                if (list.isEmpty()) "No key players yet"
                                else "${list.size} key ${if (list.size == 1) "player" else "players"}" +
                                    if (out.isNotEmpty()) " · ${out.size} out or doubtful" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (out.isNotEmpty()) LossRed else TextMuted
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            list.take(4).forEach { PlayerAvatar(it, 34.dp) }
                        }
                    }
                }
            }
        }
    } else {
        val list = mine.filter { it.team == team }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { openTeam = null }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(team, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(league.name, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Add a key player", onClick = { adding = true }, icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth())
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "No key players for this team yet. Add as many as you want, there is no limit.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                    )
                }
            }
            items(list.sortedBy { it.position.ordinal }, key = { it.id }) { p ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(p, 64.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TagChip(p.position.short, IndigoPrimary)
                                TagChip(statusLabel(p), statusColor(p.status))
                            }
                        }
                        IconButton(onClick = { editing = p }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextSecondary)
                        }
                        IconButton(onClick = { deleting = p }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = LossRed)
                        }
                    }
                }
            }
        }
    }

    if (adding && team != null) {
        PlayerDialog(
            initial = null, leagueId = league.id, team = team,
            onSave = { p, uri -> onSave(p, uri); adding = false },
            onDismiss = { adding = false }
        )
    }
    editing?.let { p ->
        PlayerDialog(
            initial = p, leagueId = p.leagueId, team = p.team,
            onSave = { np, uri -> onSave(np, uri); editing = null },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Remove ${p.name}?") },
            text = { Text("The player and the photo are removed from ${p.team}.") },
            confirmButton = { TextButton(onClick = { onDelete(p.id); deleting = null }) { Text("Remove", color = LossRed) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

private fun statusLabel(p: KeyPlayer): String = when (p.status) {
    PlayerStatus.SUSPENDED -> if (p.banGames > 0) "Suspended · ${p.banGames} ${if (p.banGames == 1) "game" else "games"}" else "Suspended"
    PlayerStatus.INJURED -> if (p.returnDate.isNotBlank()) "Injured · back ${p.returnDate}" else "Injured"
    PlayerStatus.DOUBTFUL -> if (p.returnDate.isNotBlank()) "Doubtful · until ${p.returnDate}" else "Doubtful"
    PlayerStatus.AVAILABLE -> "Available"
}

/** The player's photo in a circle, or their initials when there is none. */
@Composable
fun PlayerAvatar(p: KeyPlayer, size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(p.id, p.photoStamp) {
        if (p.photoStamp == 0L) null
        else runCatching { BitmapFactory.decodeFile(File(ctx.filesDir, "player_photos/${p.id}.jpg").path)?.asImageBitmap() }.getOrNull()
    }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(CardSurfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = p.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val initials = p.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
            Text(initials, style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun PlayerDialog(
    initial: KeyPlayer?,
    leagueId: String,
    team: String,
    onSave: (KeyPlayer, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var position by remember { mutableStateOf(initial?.position ?: PlayerPosition.MID) }
    var status by remember { mutableStateOf(initial?.status ?: PlayerStatus.AVAILABLE) }
    var returnDate by remember { mutableStateOf(initial?.returnDate.orEmpty()) }
    var games by remember { mutableStateOf(if ((initial?.banGames ?: 0) > 0) initial!!.banGames.toString() else "") }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) picked = uri }
    val id = remember { initial?.id ?: java.util.UUID.randomUUID().toString() }
    val preview = (initial ?: KeyPlayer(id, leagueId, team, name)).copy(name = name)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(if (initial == null) "New key player · $team" else "Edit ${initial.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (picked != null) {
                        PickedPhoto(picked!!)
                    } else {
                        PlayerAvatar(preview, 64.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { launcher.launch("image/*") }) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = IndigoPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text(if ((initial?.photoStamp ?: 0L) > 0L || picked != null) "Change photo" else "Add photo", color = IndigoPrimary)
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, colors = inputColors(), modifier = Modifier.fillMaxWidth()
                )
                Text("Position", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerPosition.entries.forEach { p ->
                        PillChip(p.short, selected = position == p, onClick = { position = p })
                    }
                }
                Text("Status", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerStatus.entries.forEach { s ->
                        PillChip(s.label, selected = status == s, onClick = { status = s }, accent = statusColor(s))
                    }
                }
                if (status != PlayerStatus.AVAILABLE) {
                    OutlinedTextField(
                        value = returnDate, onValueChange = { returnDate = it.take(10) },
                        label = { Text("Back on (yyyy-mm-dd, optional)") },
                        singleLine = true, colors = inputColors(), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (status == PlayerStatus.SUSPENDED) {
                    OutlinedTextField(
                        value = games, onValueChange = { games = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Ban length in games (optional)") },
                        singleLine = true, colors = inputColors(), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        "With a ban length, the suspension ends by itself after that many games of the team.",
                        style = MaterialTheme.typography.labelSmall, color = TextMuted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val ban = if (status == PlayerStatus.SUSPENDED) games.toIntOrNull() ?: 0 else 0
                    val base = initial ?: KeyPlayer(id, leagueId, team, name.trim())
                    onSave(
                        base.copy(
                            name = name.trim(), position = position, status = status,
                            returnDate = if (status == PlayerStatus.AVAILABLE) "" else returnDate.trim(),
                            banGames = ban,
                            // a new or changed ban counts from the day it is saved; an untouched one keeps its start
                            banFrom = if (status == PlayerStatus.SUSPENDED && ban > 0 && ban == (initial?.banGames ?: -1) && initial?.status == PlayerStatus.SUSPENDED) base.banFrom else ""
                        ),
                        picked
                    )
                }
            ) { Text("Save", color = IndigoPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

/** A just-picked photo, shown before it is saved. */
@Composable
private fun PickedPhoto(uri: Uri) {
    val ctx = LocalContext.current
    val bmp = remember(uri) {
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use {
                val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(it, null, o)?.asImageBitmap()
            }
        }.getOrNull()
    }
    Box(Modifier.size(64.dp).clip(CircleShape).background(CardSurfaceAlt), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
