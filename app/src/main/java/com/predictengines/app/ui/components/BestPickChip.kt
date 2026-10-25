package com.predictengines.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.predictengines.app.data.ConsensusPick
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.WinGreen

/**
 * "Best pick" for the strongest consensus pick of a game. When only one model backs it the chip reads
 * "Best pick · 1 model" in amber, so it doesn't look as solid as a pick several models agree on.
 */
@Composable
fun BestPickChip(pick: ConsensusPick, modifier: Modifier = Modifier) {
    if (pick.backers.size <= 1) TagChip("Best pick · 1 model", DrawAmber, modifier)
    else TagChip("Best pick", WinGreen, modifier)
}
