package com.predictengines.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.PerformanceReport
import com.predictengines.app.data.PerformanceReport.Tally
import com.predictengines.app.data.PerformanceReport.Verdict
import com.predictengines.app.data.PredictionRecord
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.components.PillChip
import com.predictengines.app.ui.components.TagChip
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.PoissonAccent
import com.predictengines.app.ui.theme.StatsAccent
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.time.LocalDate

private fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.STRONG -> WinGreen
    Verdict.OK -> IndigoPrimary
    Verdict.AVOID -> LossRed
    Verdict.FEW -> TextMuted
}

private fun tallyText(t: Tally): String =
    if (t.total == 0) "no picks" else "${t.correct}/${t.total} right · ${Math.round(t.rate)}% · score ${Math.round(t.adjusted)}"

private fun listTitle(s: String) = when (s) {
    "Top 20" -> "Top 20 picks"
    PerformanceReport.ALL_LISTS -> "All lists"
    else -> s
}

/** A card that folds: title and arrow stay, the body shows when open. */
@Composable
private fun FoldCard(
    id: String, title: String, subtitle: String, accent: Color, startOpen: Boolean = false,
    body: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable(id) { mutableStateOf(startOpen) }
    AppCard(
        title = title, subtitle = subtitle, accentColor = accent, onClick = { open = !open },
        trailing = {
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Fold" else "Unfold", tint = TextSecondary
            )
        }
    ) {
        if (open) {
            Spacer(Modifier.height(6.dp))
            body()
        }
    }
}

/**
 * The performance reports of the Reports screen: leagues, confidence and agreement, models compared, markets across
 * leagues, and the Kryptonite effect. All follow the same time window and list chosen on the first card.
 */
@Composable
fun PerformanceCards(state: AppUiState, shownMarks: Map<String, PredictionRecord>, visible: Set<String>) {
    var windowIdx by rememberSaveable { mutableIntStateOf(0) }
    var sourceIdx by rememberSaveable { mutableIntStateOf(0) }
    val windows = PerformanceReport.Window.entries
    val sources = PerformanceReport.LISTS + PerformanceReport.ALL_LISTS
    val window = windows[windowIdx]

    val reports = remember(shownMarks, state.listEntries, visible, windowIdx) {
        val hide = PerformanceReport.HIDDEN_SUFFIX
        // a pick a Kryptonite kept off its list is judged under the list's name, flagged as hidden
        val records = shownMarks.values.map { if (it.source.endsWith(hide)) it.copy(source = it.source.removeSuffix(hide)) else it }
        val meta = HashMap<String, PerformanceReport.PickMeta>()
        state.listEntries.forEach { e ->
            val conf = if (e.backers.isEmpty()) null else e.backers.map { b -> b.second }.average()
            meta[e.id] = PerformanceReport.PickMeta(
                e.krypto, conf, e.backers.size, e.modelCount, e.source.endsWith(hide),
                if (e.source == "Loki") e.backers.map { b -> b.first } else emptyList()
            )
        }
        val today = LocalDate.now()
        Triple(
            PerformanceReport.build(records, meta, visible, window, today, sources),
            PerformanceReport.models(shownMarks.values, visible, window, today),
            window
        )
    }
    val current = reports.first[sourceIdx]
    val models = reports.second
    val windowNote = window.label.lowercase()

    // ---- 1. leagues ----------------------------------------------------------------------
    var openLeagues by rememberSaveable { mutableStateOf(emptySet<String>()) }
    FoldCard(
        "perf_leagues", "Where the picks perform",
        "Settled picks per league, market and team ($windowNote). The score weighs the hit rate by how many picks there are.",
        IndigoPrimary, startOpen = true
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            windows.forEachIndexed { i, w -> PillChip(w.label, selected = i == windowIdx, onClick = { windowIdx = i }) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEachIndexed { i, s -> PillChip(listTitle(s), selected = i == sourceIdx, onClick = { sourceIdx = i }) }
        }
        Spacer(Modifier.height(10.dp))
        if (current.total.total == 0) {
            Text(
                "No settled picks in this window yet. Picks settle by themselves once their games have results.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted
            )
        } else {
            val avoid = current.leagues.filter { it.verdict == Verdict.AVOID }.map { it.leagueName }
            val strong = current.leagues.filter { it.verdict == Verdict.STRONG }.map { it.leagueName }
            Text("All leagues: ${tallyText(current.total)}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (avoid.isNotEmpty()) Text("Avoid: ${avoid.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = LossRed)
            if (strong.isNotEmpty()) Text("Strong: ${strong.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = WinGreen)
            Text(
                "A verdict needs ${PerformanceReport.MIN_LEAGUE} settled picks for a league, ${PerformanceReport.MIN_MARKET} for a market and " +
                    "${PerformanceReport.MIN_TEAM} for a team. Strong: ${PerformanceReport.STRONG_RATE.toInt()}%+ and a solid score. " +
                    "Avoid: under ${PerformanceReport.AVOID_RATE.toInt()}%.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted
            )
            current.leagues.forEach { lg ->
                val key = "${current.source}|${lg.leagueId}"
                val open = key in openLeagues
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { openLeagues = if (open) openLeagues - key else openLeagues + key },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(lg.leagueName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(tallyText(lg.tally), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    TagChip(lg.verdict.label, verdictColor(lg.verdict))
                    Icon(
                        if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (open) "Fold" else "Unfold", tint = TextSecondary
                    )
                }
                if (open) {
                    Spacer(Modifier.height(6.dp))
                    Text("Markets", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    lg.markets.forEach { PerfRow(it) }
                    Spacer(Modifier.height(6.dp))
                    Text("Teams (their fixtures, home or away)", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    lg.teams.forEach { PerfRow(it) }
                }
            }
        }
    }

    // ---- 2. confidence and agreement -----------------------------------------------------
    FoldCard(
        "perf_confidence", "Confidence and agreement · ${listTitle(current.source)}",
        "Do stronger picks win more? ($windowNote)", PoissonAccent
    ) {
        val bands = current.confidence.filter { it.name != "Unknown" }
        if (current.total.total == 0) {
            Text("No settled picks in this window yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            Text("By the backing models' average confidence", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            current.confidence.forEach { PerfRow(it) }
            Spacer(Modifier.height(6.dp))
            Text(trendOf(bands), style = MaterialTheme.typography.bodyMedium, color = DrawAmber)
            Spacer(Modifier.height(10.dp))
            Text("By how many models agreed", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            current.agreement.forEach { PerfRow(it) }
            if (current.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("By Loki (a pick made by two Lokis counts for both)", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                current.tags.forEach { PerfRow(it) }
            }
        }
    }

    // ---- 3. models compared --------------------------------------------------------------
    FoldCard(
        "perf_models", "Models compared",
        "Each model's own picks (the ones on its card) next to the consensus ($windowNote)", StatsAccent
    ) {
        if (models.overall.all { it.tally.total == 0 }) {
            Text("No settled model picks in this window yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            Text("All leagues", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            ModelCells(models.overall)
            models.best?.let { Text("Best model overall: $it", style = MaterialTheme.typography.bodyMedium, color = WinGreen) }
            Spacer(Modifier.height(10.dp))
            Text("By league", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            models.leagues.forEach { ModelLineRow(it) }
            Spacer(Modifier.height(10.dp))
            Text("By market", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            models.markets.forEach { ModelLineRow(it) }
            Text(
                "Best needs ${PerformanceReport.MIN_LEAGUE} settled picks in a league or ${PerformanceReport.MIN_GROUP} in a market.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted
            )
        }
    }

    // ---- 4. markets across leagues -------------------------------------------------------
    var openMarkets by rememberSaveable { mutableStateOf(emptySet<String>()) }
    FoldCard(
        "perf_markets", "Markets across all leagues · ${listTitle(current.source)}",
        "Which pick types win, and where ($windowNote)", WinGreen
    ) {
        if (current.markets.isEmpty()) {
            Text("No settled picks in this window yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            current.markets.forEach { m ->
                val key = "${current.source}|${m.group}"
                val open = key in openMarkets
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clickable { openMarkets = if (open) openMarkets - key else openMarkets + key },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.group, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(tallyText(m.tally), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    TagChip(m.verdict.label, verdictColor(m.verdict))
                    Icon(
                        if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (open) "Fold" else "Unfold", tint = TextSecondary
                    )
                }
                if (open) {
                    Text("Picks", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(top = 6.dp))
                    m.keys.forEach { PerfRow(it) }
                    Text("Leagues", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(top = 6.dp))
                    m.leagues.forEach { PerfRow(it) }
                }
            }
        }
    }

    // ---- 5. Kryptonite effect ------------------------------------------------------------
    FoldCard(
        "perf_krypto", "Kryptonite effect · ${listTitle(current.source)}",
        "Flagged picks against clean ones, and the picks your switches kept off the list ($windowNote)", LossRed
    ) {
        val known = current.flagged.total + current.clean.total
        if (known == 0 && current.hidden.total == 0) {
            Text(
                "No settled picks with recorded flags yet. Picks written down from now on keep the flags they carried, " +
                    "so this fills in as their games are settled." +
                    if (current.unknown.total > 0) " (${current.unknown.total} older settled picks have no flag record.)" else "",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted
            )
        } else {
            if (known > 0) {
                CompareRow("With any flag", current.flagged, LossRed)
                CompareRow("Clean (no flag)", current.clean, WinGreen)
            }
            if (current.hidden.total > 0) {
                CompareRow("Held back by your switches", current.hidden, DrawAmber)
                Text(
                    "Picks that were hidden or pushed off the list. They are judged anyway, so you can see if holding them back paid.",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted
                )
            }
            if (current.unknown.total > 0) {
                Text("${current.unknown.total} older settled picks have no flag record and are left out.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            if (current.rules.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("By Kryptonite", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                current.rules.forEach { r ->
                    Spacer(Modifier.height(8.dp))
                    Text(r.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("Flagged: ${tallyText(r.flagged)}", style = MaterialTheme.typography.labelSmall, color = LossRed)
                    Text("Without it: ${tallyText(r.clean)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    if (r.hidden.total > 0) Text("Held back: ${tallyText(r.hidden)}", style = MaterialTheme.typography.labelSmall, color = DrawAmber)
                    Text(r.advice, style = MaterialTheme.typography.labelSmall, color = DrawAmber)
                }
            }
        }
    }
}

/** Compares the lowest and highest confidence band that have enough picks. */
private fun trendOf(bands: List<PerformanceReport.Row>): String {
    val usable = bands.filter { it.tally.total >= PerformanceReport.MIN_MARKET }
    if (usable.size < 2) return "Not enough picks per band yet to see a trend."
    val lo = usable.first().tally.rate
    val hi = usable.last().tally.rate
    return when {
        hi - lo >= 8 -> "Higher confidence does better (${Math.round(lo)}% to ${Math.round(hi)}%). The ranking works."
        lo - hi >= 8 -> "Higher confidence does worse (${Math.round(lo)}% to ${Math.round(hi)}%). Treat the strongest picks with care."
        else -> "No clear link between confidence and results yet."
    }
}

@Composable
private fun PerfRow(r: PerformanceReport.Row) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(tallyText(r.tally), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Text(r.verdict.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = verdictColor(r.verdict))
    }
}

@Composable
private fun ModelCells(cells: List<PerformanceReport.ModelCell>) {
    Text(
        cells.joinToString(" · ") { c ->
            if (c.tally.total == 0) "${c.model} –" else "${c.model} ${Math.round(c.tally.rate)}% (${c.tally.total})"
        },
        style = MaterialTheme.typography.bodyMedium, color = TextSecondary
    )
}

@Composable
private fun ModelLineRow(l: PerformanceReport.ModelLine) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            l.best?.let { Text("Best: $it", style = MaterialTheme.typography.labelMedium, color = WinGreen) }
        }
        ModelCells(l.cells)
    }
}

@Composable
private fun CompareRow(label: String, t: Tally, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(tallyText(t), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Text(if (t.total > 0) "${Math.round(t.rate)}%" else "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
