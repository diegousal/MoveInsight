package com.moveinsight.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary            = CyanPrimary,
    onPrimary          = NavyDeep,
    primaryContainer   = NavyLight,
    onPrimaryContainer = CyanLight,
    secondary          = OrangePower,
    onSecondary        = NavyDeep,
    background         = NavyDeep,
    onBackground       = TextPrimary,
    surface            = NavyMid,
    onSurface          = TextPrimary,
    surfaceVariant     = NavyLight,
    onSurfaceVariant   = TextSecondary,
    error              = RedAlert,
    onError            = Color.White,
    outline            = DividerColor
)

@Composable
fun MoveInsightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = NeuroSquatTypography,
        content     = content
    )
}