package com.predictengines.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.predictengines.app.ui.theme.IndigoPrimary
import com.predictengines.app.ui.theme.TextMuted
import com.predictengines.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private val VERBS = listOf(
    "Pondering", "Sleuthering", "Running command", "Cogitating", "Crunching numbers", "Counting corners",
    "Weighing form", "Sniffing out Kryptonites", "Consulting the models", "Reading the tea leaves"
)

/**
 * The app's loading screen: a turning sparkle, a status word that changes every moment (Pondering, Sleuthering...),
 * a dimmer line with what the app is really doing right now ([step]), and a running timer once it takes a while.
 */
@Composable
fun ThinkingLoader(modifier: Modifier = Modifier, step: String = "") {
    var verb by remember { mutableIntStateOf((System.currentTimeMillis() % VERBS.size).toInt()) }
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(1000)
            tick++
            seconds = tick
            if (tick % 2 == 0) verb = (verb + 1) % VERBS.size
        }
    }
    val transition = rememberInfiniteTransition(label = "thinking")
    val turn by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "turn"
    )
    val glow by transition.animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow"
    )
    val dots by transition.animateFloat(
        0f, 3.99f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "dots"
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✦", color = IndigoPrimary, fontSize = 44.sp, modifier = Modifier.rotate(turn).alpha(glow))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Crossfade(targetState = VERBS[verb], animationSpec = tween(500), label = "verb") { v ->
                Text(v, style = MaterialTheme.typography.titleMedium, color = IndigoPrimary)
            }
            Text(".".repeat(dots.toInt()), style = MaterialTheme.typography.titleMedium, color = IndigoPrimary)
        }
        if (step.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Crossfade(targetState = step, animationSpec = tween(300), label = "step") { s ->
                Text(s, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            }
        }
        if (seconds >= 3) {
            Spacer(Modifier.height(4.dp))
            Text("${seconds}s", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
