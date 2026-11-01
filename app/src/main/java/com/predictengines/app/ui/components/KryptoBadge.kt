package com.predictengines.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.KeyPlayer
import com.predictengines.app.data.KryptoFlag
import com.predictengines.app.ui.screens.PlayerAvatar
import com.predictengines.app.ui.theme.LossRed

/** The key players of every league, so a flag about a player can show that player's picture. */
val LocalKeyPlayers = staticCompositionLocalOf<List<KeyPlayer>> { emptyList() }

/** The Kryptonite warnings of a fixture or pick: one red tag per flag, with the player's picture when it is about a player. */
@Composable
fun KryptoBadges(flags: List<KryptoFlag>, modifier: Modifier = Modifier) {
    if (flags.isEmpty()) return
    val players = LocalKeyPlayers.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.height(2.dp))
        flags.forEach { flag ->
            val player = if (flag.playerId.isBlank()) null else players.firstOrNull { it.id == flag.playerId }
            if (player == null) {
                TagChip("⚠ Kryptonite · ${flag.text}", LossRed)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(player, 36.dp)
                    Spacer(Modifier.width(8.dp))
                    TagChip("⚠ Kryptonite · ${flag.text}", LossRed, Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}
