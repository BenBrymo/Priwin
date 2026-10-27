package com.predictengines.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.StatRow
import com.predictengines.app.engine.StatsEngine
import com.predictengines.app.engine.StatsUpdater
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.LabeledDropdown
import com.predictengines.app.ui.components.PrimaryButton
import com.predictengines.app.ui.components.SecondaryButton
import com.predictengines.app.ui.components.inputColors
import com.predictengines.app.ui.theme.CardSurface
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.StatsAccent
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.util.Locale

private const val ALL_MARKETS = "All markets"
private const val LOW_PERCENT = 50.0

private fun percentOf(r: StatRow): Double = r.percent ?: StatsUpdater.percentOf(r.value, r.games) ?: 0.0
private fun num(d: Double): String = if (d == Math.rint(d)) d.toLong().toString() else d.toString()

private val COL_SEASON = 60.dp
private val COL_VALUE = 48.dp
private val COL_PCT = 42.dp

/** One market as a table: its records one under another (highest percentage first), scrolling inside the card. */
@Composable
private fun MarketTable(
    market: String,
    rows: List<StatRow>,
    onEdit: (StatRow) -> Unit,
    onDelete: (StatRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val lows = rows.count { percentOf(it) < LOW_PERCENT }
    AppCard(modifier = modifier) {
        Text(market, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${rows.size} ${if (rows.size == 1) "record" else "records"}" + if (lows > 0) " · $lows below 50%" else "",
            style = MaterialTheme.typography.labelSmall, color = TextMuted
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Team", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1f))
            Text("Season", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.width(COL_SEASON))
            Text("Rec.", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.width(COL_VALUE))
            Text("%", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.width(COL_PCT))
            Spacer(Modifier.width(80.dp))
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
            items(rows, key = { StatsUpdater.rowKey(it) }) { r ->
                val pct = percentOf(r)
                val low = pct < LOW_PERCENT
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.team, style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text(
                        r.season, style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(COL_SEASON)
                    )
                    Text(
                        "${num(r.value)}/${r.games}", style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary, maxLines = 1, modifier = Modifier.width(COL_VALUE)
                    )
                    Text(
                        String.format(Locale.US, "%.0f%%", pct), style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = if (low) LossRed else WinGreen, modifier = Modifier.width(COL_PCT)
                    )
                    IconButton(onClick = { onEdit(r) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = IndigoPrimary)
                    }
                    IconButton(onClick = { onDelete(r) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = if (low) LossRed else TextMuted)
                    }
                }
            }
        }
    }
}

/**
 * Edit the stats data in the app: search, change a record's numbers, add a record, export the file,
 * and delete any record (the delete button is on every row).
 */
@Composable
fun StatsEditorScreen(
    state: AppUiState,
    onEdit: (StatRow, Double, Int) -> Unit,
    onAdd: (StatRow) -> Unit,
    onDelete: (List<StatRow>) -> Unit,
    onExport: (Uri) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var market by rememberSaveable { mutableStateOf(ALL_MARKETS) }
    var lowOnly by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StatRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<StatRow>?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) onExport(uri)
    }

    val rows = state.statsRows
    val markets = remember(rows) { rows.map { it.market }.distinct().sorted() }
    val shown = remember(rows, query, market, lowOnly) {
        val q = query.trim().lowercase(Locale.ROOT)
        rows.filter { r ->
            (market == ALL_MARKETS || r.market == market) &&
                (q.isEmpty() || r.team.lowercase(Locale.ROOT).contains(q) || r.market.lowercase(Locale.ROOT).contains(q)) &&
                (!lowOnly || percentOf(r) < LOW_PERCENT)
        }.sortedWith(compareBy({ it.team.lowercase(Locale.ROOT) }, { it.market }))
    }
    val lowCount = remember(rows) { rows.count { percentOf(it) < LOW_PERCENT } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard(
                title = "Stats editor",
                subtitle = "${rows.size} records · $lowCount below 50% · changes are saved on the phone and used straight away",
                accentColor = StatsAccent
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Add record", onClick = { adding = true }, icon = Icons.Filled.Add,
                        color = StatsAccent, modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(text = "Export", onClick = { exporter.launch("statschecker_updated.csv") }, icon = Icons.Filled.Download)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search team or market") }, colors = inputColors(), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                LabeledDropdown(
                    label = "Market", options = listOf(ALL_MARKETS) + markets, selected = market,
                    onSelected = { market = it }, accentColor = StatsAccent, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Only records below 50%", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("These can be deleted", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Switch(checked = lowOnly, onCheckedChange = { lowOnly = it })
                }
                if (lowOnly && shown.isNotEmpty()) {
                    TextButton(onClick = { confirmDelete = shown }) {
                        Text("Delete all ${shown.size} shown", color = LossRed)
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    "No stats loaded yet. Load a stats CSV on the Data tab, or add records here.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted
                )
            }
        } else if (shown.isEmpty()) {
            item { Text("No records match.", style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        } else {
            item {
                Text(
                    "${shown.size} records" + if (shown.size > 300) " · refine the search to narrow down" else "",
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary
                )
            }
            val grouped = shown.groupBy { it.market }.toSortedMap().entries.toList()
            item(key = "market_tables") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(grouped, key = { it.key }) { (mkt, list) ->
                        val ordered = list.sortedWith(
                            compareByDescending<StatRow> { percentOf(it) }.thenBy { it.team.lowercase(Locale.ROOT) }
                        )
                        MarketTable(
                            market = mkt, rows = ordered,
                            onEdit = { editing = it },
                            onDelete = { confirmDelete = listOf(it) },
                            modifier = Modifier.width(390.dp).height(460.dp)
                        )
                    }
                }
            }
        }
    }

    editing?.let { r ->
        var v by remember(r) { mutableStateOf(num(r.value)) }
        var g by remember(r) { mutableStateOf(r.games.toString()) }
        val value = v.toDoubleOrNull()
        val games = g.toIntOrNull()
        val valid = value != null && games != null && value >= 0 && games >= 0 && value <= games
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = CardSurface, titleContentColor = TextPrimary, textContentColor = TextSecondary,
            title = { Text("${r.team} · ${r.market}") },
            text = {
                Column {
                    Text("Season ${r.season}. The percentage is worked out for you.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = v, onValueChange = { v = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, singleLine = true,
                            label = { Text("Value (times it happened)") }, colors = inputColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = g, onValueChange = { g = it.filter(Char::isDigit).take(4) }, singleLine = true,
                            label = { Text("Games") }, colors = inputColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                        )
                    }
                    if (!valid) Text("Value can't be more than games.", color = LossRed, style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { onEdit(r, value!!, games!!); editing = null }) {
                    Text("Save", color = if (valid) IndigoPrimary else TextMuted)
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    if (adding) {
        val season = remember(rows) { rows.maxOfOrNull { it.season } ?: "2026/2027" }
        var team by remember { mutableStateOf("") }
        var mk by remember { mutableStateOf(markets.firstOrNull().orEmpty()) }
        var seasonText by remember { mutableStateOf(season) }
        var v by remember { mutableStateOf("") }
        var g by remember { mutableStateOf("") }
        val value = v.toDoubleOrNull()
        val games = g.toIntOrNull()
        val marketName = mk.trim()
        val understood = marketName.isNotEmpty() && StatsEngine.classify(marketName) != null
        val valid = team.isNotBlank() && marketName.isNotEmpty() && seasonText.isNotBlank() && value != null && games != null && games > 0 && value <= games
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = CardSurface, titleContentColor = TextPrimary, textContentColor = TextSecondary,
            title = { Text("Add record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = team, onValueChange = { team = it }, singleLine = true,
                        label = { Text("Team") }, colors = inputColors(), modifier = Modifier.fillMaxWidth())
                    if (markets.isNotEmpty()) {
                        LabeledDropdown("Existing markets", markets, null, { mk = it }, Modifier.fillMaxWidth(), StatsAccent)
                    }
                    OutlinedTextField(value = mk, onValueChange = { mk = it }, singleLine = true,
                        label = { Text("Market (pick above or type a new one)") }, colors = inputColors(), modifier = Modifier.fillMaxWidth())
                    if (marketName.isNotEmpty()) {
                        Text(
                            if (understood) "New markets are fine. This one is understood by the Stats model."
                            else "Not a market the Stats model understands (e.g. Home Wins, Over 2.5 Away, BTTS, Corners Over 9.5). It will be saved but ignored when predicting.",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (understood) WinGreen else TextMuted
                        )
                    }
                    OutlinedTextField(value = seasonText, onValueChange = { seasonText = it }, singleLine = true,
                        label = { Text("Season") }, colors = inputColors(), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = v, onValueChange = { v = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, singleLine = true,
                            label = { Text("Value") }, colors = inputColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = g, onValueChange = { g = it.filter(Char::isDigit).take(4) }, singleLine = true,
                            label = { Text("Games") }, colors = inputColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    onAdd(StatRow(marketName, seasonText.trim(), null, team.trim(), value!!, games!!, StatsUpdater.percentOf(value!!, games!!)))
                    adding = false
                }) { Text("Add", color = if (valid) IndigoPrimary else TextMuted) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    confirmDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = CardSurface, titleContentColor = TextPrimary, textContentColor = TextSecondary,
            title = { Text(if (list.size == 1) "Delete this record?" else "Delete ${list.size} records?") },
            text = {
                Text(
                    if (list.size == 1) "${list[0].team} · ${list[0].market} (${num(list[0].value)}/${list[0].games}) will be removed from the stats and stay removed."
                    else "These records will be removed from the stats and stay removed. You can add them back with Add record."
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(list); confirmDelete = null }) { Text("Delete", color = LossRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}
