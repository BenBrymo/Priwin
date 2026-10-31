package com.predictengines.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.KryptoFlag
import com.predictengines.app.ui.theme.LossRed

/** The Kryptonite warnings of a fixture or pick: one red tag per flagged team, saying why. */
@Composable
fun KryptoBadges(flags: List<KryptoFlag>, modifier: Modifier = Modifier) {
    if (flags.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.height(2.dp))
        flags.forEach { TagChip("⚠ Kryptonite · ${it.text}", LossRed) }
    }
}
