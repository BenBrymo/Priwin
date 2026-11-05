package com.predictengines.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.predictengines.app.data.Countdown
import com.predictengines.app.data.Urgency
import com.predictengines.app.ui.theme.DrawAmber
import com.predictengines.app.ui.theme.LossRed
import com.predictengines.app.ui.theme.TextMuted
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private val OrangeUrgent = Color(0xFFFB923C)

fun urgencyColor(u: Urgency): Color = when (u) {
    Urgency.RED -> LossRed
    Urgency.ORANGE -> OrangeUrgent
    Urgency.AMBER -> DrawAmber
    Urgency.GREY -> TextMuted
}

/** The one shared clock: the current time, ticking on the minute (see [ProvideClock]). */
val LocalNow = androidx.compose.runtime.compositionLocalOf { LocalDateTime.now() }

/**
 * Runs the single clock of the app. Everything below reads it with [rememberNow], so there is one timer instead of one
 * per card, and only the pieces that show the time redraw when the minute changes.
 */
@Composable
fun ProvideClock(content: @Composable () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = LocalDateTime.now()
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalNow provides now, content = content)
}

/** The current time, refreshed every minute so countdowns never go stale while the screen is open. */
@Composable
fun rememberNow(): LocalDateTime = LocalNow.current

/** "Tomorrow 19:00" / "In 2h 15m" / "In 5 days" for a game still to be played; nothing for played games. */
@Composable
fun CountdownChip(date: String, time: String, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val c = Countdown.of(date, time, now) ?: return
    // Within three hours of kickoff the chip pulses gently.
    val alpha = if (c.withinHours) {
        val t = rememberInfiniteTransition(label = "pulse")
        val a by t.animateFloat(
            initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha"
        )
        a
    } else 1f
    TagChip(c.text, urgencyColor(c.urgency), modifier.alpha(alpha))
}

/** A small red dot, for a tab that has a game kicking off today. */
@Composable
fun TodayDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(LossRed))
}
