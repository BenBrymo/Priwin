package com.predictengines.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.Trends
import com.predictengines.app.ui.components.AppCard
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.WinGreen
import com.predictengines.app.viewmodel.AppUiState
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun pct(v: Double): String = if (v < 1.0) "under 1%" else "${Math.round(v)}%"

/** The week's combined slip: the strongest Trend picks, one per game, spread across lists, with the chance they all land. */
@Composable
fun SlipCard(state: AppUiState) {
    val slip = remember(state.trendByList) { Trends.slip(state.trendByList) }
    var open by rememberSaveable { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    if (slip == null) {
        AppCard(
            title = "Weekly slip",
            subtitle = "Not enough Trend picks coming yet for a slip (it needs at least 3 games). It appears as soon as more strong picks are in.",
            accentColor = DrawAmber
        ) {}
        return
    }
    AppCard(
        title = "Weekly slip",
        subtitle = "${slip.legs.size} picks, one per game, spread across lists · week of ${slip.week.format(fmt)} · all land: about ${pct(slip.chance)}",
        accentColor = DrawAmber,
        onClick = { open = !open }
    ) {
        if (!open) {
            Text("Tap to open", style = MaterialTheme.typography.labelMedium, color = IndigoPrimary)
            return@AppCard
        }
        slip.legs.forEach { leg ->
            val p = leg.pick
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${p.homeDisplay} v ${p.awayDisplay}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(p.pick.label.substringBefore(" · "), style = MaterialTheme.typography.bodyMedium, color = WinGreen)
                    Text(
                        "${leg.list} · ${p.leagueName} · ${p.date} ${p.time}".trim(),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted
                    )
                }
                Text("${Math.round(p.pick.hit)}%", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "All ${slip.legs.size} land: about ${pct(slip.chance)} (a cautious ${pct(slip.cautious)} after allowing for few picks). " +
                "Expect about ${"%.1f".format(Locale.US, slip.expected)} of ${slip.legs.size} to land.",
            style = MaterialTheme.typography.bodyMedium, color = TextPrimary
        )
        Text(
            "Each % is how often that pick's strongest place in your Report has hit. The slip multiplies them, assuming the picks do not affect each other. " +
                "It is a measure of your track record, not a promise.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted
        )
    }
}
