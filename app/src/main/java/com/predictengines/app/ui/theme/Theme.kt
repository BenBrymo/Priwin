package com.predictengines.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PredictEnginesColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = TextPrimary,
    secondary = VioletAccent,
    onSecondary = TextPrimary,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle,
    error = LossRed
)

/** The app is dark-only: the card/gradient design is built around it. */
@Composable
fun PredictEnginesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PredictEnginesColorScheme,
        typography = AppTypography,
        content = content
    )
}
