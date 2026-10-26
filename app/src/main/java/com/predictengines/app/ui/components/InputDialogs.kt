package com.predictengines.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.predictengines.app.data.FirstScorer
import com.predictengines.app.data.MatchExtras
import com.predictengines.app.data.ScoreCell
import com.predictengines.app.ui.theme.CardSurface
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.OutlineSubtle
import com.predictengines.app.ui.theme.PoissonAccent
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextPrimary
import com.predictengines.app.ui.theme.TextSecondary
import com.predictengines.app.ui.theme.WinGreen

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

/**
 * Builds the extras from what you picked. The match totals are the sums of the two teams' numbers (a total is only
 * known when both teams are given). A first scorer only matters when both teams scored.
 */
private fun buildExtras(
    initial: MatchExtras?,
    hc: Int?, ac: Int?, hk: Int?, ak: Int?,
    first: FirstScorer?, goalsHome: Int, goalsAway: Int
): MatchExtras? {
    val corners = when {
        hc != null && ac != null -> hc + ac
        hc == null && ac == null -> initial?.corners   // an older saved total is kept if you pick nothing
        else -> null
    }
    val cards = when {
        hk != null && ak != null -> hk + ak
        hk == null && ak == null -> initial?.cards
        else -> null
    }
    val f = if (goalsHome > 0 && goalsAway > 0) first else null
    val x = MatchExtras(corners, cards, f, hc, ac, hk, ak)
    return if (x.isEmpty) null else x
}

/**
 * Pick the final score, corners and cards for each team, and who scored first, by tapping numbers: no typing.
 * [suggested] are quick-pick scorelines (the model's likely ones first).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreDialog(
    home: String,
    away: String,
    initial: Pair<Int, Int>?,
    initialExtras: MatchExtras?,
    canRemove: Boolean,
    onSave: (Int, Int, MatchExtras?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    suggested: List<Pair<Int, Int>> = emptyList()
) {
    var hg by remember { mutableStateOf(initial?.first) }
    var ag by remember { mutableStateOf(initial?.second) }
    var hc by remember { mutableStateOf(initialExtras?.homeCorners) }
    var ac by remember { mutableStateOf(initialExtras?.awayCorners) }
    var hk by remember { mutableStateOf(initialExtras?.homeCards) }
    var ak by remember { mutableStateOf(initialExtras?.awayCards) }
    var first by remember { mutableStateOf(initialExtras?.first) }

    val h = hg
    val a = ag
    val canSave = h != null && a != null
    val bothScored = (h ?: 0) > 0 && (a ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Result") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Tap the numbers. The stats and every pick the result decides are settled for you.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                )

                if (suggested.isNotEmpty()) {
                    Text("Quick scores", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggested.forEach { (sh, sa) ->
                            PillChip(
                                text = "$sh-$sa",
                                selected = hg == sh && ag == sa,
                                onClick = { hg = sh; ag = sa },
                                accent = WinGreen
                            )
                        }
                    }
                }

                Text(
                    if (canSave) "$home  $h - $a  $away" else "Pick both scores",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (canSave) TextPrimary else TextMuted
                )
                NumberPickerRow("$home goals", hg, 10, { hg = it }, accent = WinGreen)
                NumberPickerRow("$away goals", ag, 10, { ag = it }, accent = WinGreen)

                Text("Corners (optional)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                NumberPickerRow("$home corners", hc, 20, { hc = it }, accent = PoissonAccent, clearable = true)
                NumberPickerRow("$away corners", ac, 20, { ac = it }, accent = PoissonAccent, clearable = true)

                Text("Cards (optional)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                NumberPickerRow("$home cards", hk, 10, { hk = it }, accent = IndigoPrimary, clearable = true)
                NumberPickerRow("$away cards", ak, 10, { ak = it }, accent = IndigoPrimary, clearable = true)

                if (bothScored) {
                    Text("Scored first", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip(
                            home, first == FirstScorer.HOME,
                            { first = if (first == FirstScorer.HOME) null else FirstScorer.HOME }
                        )
                        PillChip(
                            away, first == FirstScorer.AWAY,
                            { first = if (first == FirstScorer.AWAY) null else FirstScorer.AWAY }
                        )
                    }
                } else if (canSave) {
                    Text(
                        "Who scored first is decided by the score here, so there is nothing to pick.",
                        style = MaterialTheme.typography.labelSmall, color = TextMuted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (h != null && a != null) onSave(h, a, buildExtras(initialExtras, hc, ac, hk, ak, first, h, a))
                }
            ) {
                Text("Save", color = if (canSave) IndigoPrimary else TextMuted)
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

/** Quick-pick scorelines: the model's likely ones first, then the most common results. */
fun suggestedScores(likely: List<ScoreCell>): List<Pair<Int, Int>> {
    val common = listOf(
        1 to 0, 2 to 1, 1 to 1, 2 to 0, 0 to 0, 0 to 1, 1 to 2, 0 to 2,
        3 to 1, 2 to 2, 3 to 0, 1 to 3, 3 to 2, 4 to 1, 5 to 1
    )
    return (likely.map { it.homeGoals to it.awayGoals } + common).distinct().take(16)
}
