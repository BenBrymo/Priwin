package com.predictengines.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.PerformanceReport
import com.predictengines.app.data.PredictionRecord
import com.predictengines.app.data.Slips
import com.predictengines.app.data.TopPick
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.util.Locale

private fun pct(v: Double): String = if (v < 1.0) "under 1%" else "${Math.round(v)}%"

/** How often a pick of this kind has landed: the pick's own Trend record, else its list's Report row for the market, else all lists'. */
private fun legChance(state: AppUiState, list: String, tp: TopPick): Pair<Double, Double>? {
    if (tp.pick.hit > 0) return tp.pick.hit to tp.pick.hitSafe
    val name = PerformanceReport.keyName(tp.pick.key)
    for (l in listOf(list, PerformanceReport.ALL_LISTS)) {
        val t = state.listRates[l]?.get(name)
        if (t != null && t.total >= PerformanceReport.MIN_MARKET) return t.rate to t.adjusted
    }
    return null
}

/** One slip card per 8 picks of a week's list (one pick per game). */
internal fun LazyListScope.slipItems(
    keyBase: String, ranked: List<Pair<Int, TopPick>>, listName: String, source: String,
    state: AppUiState, onOpen: (TopPick) -> Unit
) {
    val slips = Slips.build(ranked) { legChance(state, listName, it) }
    slips.forEach { s ->
        item(key = "slip_${keyBase}_${s.number}") { BetSlipCard(s, source, state, onOpen) }
    }
}

@Composable
private fun BetSlipCard(slip: Slips.Slip, source: String, state: AppUiState, onOpen: (TopPick) -> Unit) {
    fun correct(tp: TopPick) = state.marks[PredictionRecord.idOf(tp.leagueId, tp.date, tp.home, tp.away, source, tp.pick.key)]?.correct
    val played = slip.legs.map { DateUtils.isPlayed(it.pick.date, it.pick.time) }
    val allPlayed = played.all { it }
    val right = slip.legs.count { correct(it.pick) == true }
    val wrong = slip.legs.count { correct(it.pick) == false }
    val subtitle = when {
        allPlayed && right + wrong > 0 -> "$right of ${right + wrong} settled picks right" + if (wrong == 0 && right == slip.legs.size) " · slip landed" else if (wrong > 0) " · slip lost" else ""
        slip.chance != null -> "All ${slip.legs.size} land: about ${pct(slip.chance!!)}"
        else -> "No record yet for these picks, so no chance can be worked out"
    }
    AppCard(
        title = "Slip ${slip.number} · ${slip.legs.size} ${if (slip.legs.size == 1) "pick" else "picks"}" + if (slip.isShort) " (short slip)" else "",
        subtitle = subtitle,
        accentColor = if (wrong > 0) LossRed else WinGreen
    ) {
        slip.legs.forEach { leg ->
            val tp = leg.pick
            val c = correct(tp)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(tp) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#${leg.rank}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text("${tp.homeDisplay} v ${tp.awayDisplay}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(tp.pick.label.substringBefore(" · "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = WinGreen)
                    Text(tp.leagueName + " · " + DateUtils.kickoffLabelWithYear(tp.date, tp.time), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                when {
                    c == true -> Text("✓", style = MaterialTheme.typography.titleLarge, color = WinGreen)
                    c == false -> Text("✗", style = MaterialTheme.typography.titleLarge, color = LossRed)
                    DateUtils.isPlayed(tp.date, tp.time) -> Text("awaiting", style = MaterialTheme.typography.labelSmall, color = DrawAmber)
                    leg.hit != null -> Text("${Math.round(leg.hit)}%", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }
            }
        }
        if (!allPlayed && slip.chance != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "All ${slip.legs.size} land: about ${pct(slip.chance!!)} (a cautious ${pct(slip.cautious ?: 0.0)}). Expect about ${"%.1f".format(Locale.US, slip.expected)} of ${slip.legs.size} to land." +
                    if (slip.withRecord < slip.legs.size) " ${slip.legs.size - slip.withRecord} picks have no record yet and are left out of that figure." else "",
                style = MaterialTheme.typography.labelMedium, color = TextPrimary
            )
            Text(
                "Each % is how often that kind of pick has landed in your Report; they are multiplied as if the games do not affect each other. One pick per game. Tap a pick for its analysis.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted
            )
        }
    }
}
