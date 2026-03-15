package com.merlottv.kotlin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MerlotDarkColorScheme = darkColorScheme(
    primary = MerlotColors.Accent,
    onPrimary = MerlotColors.Black,
    primaryContainer = MerlotColors.Surface2,
    onPrimaryContainer = MerlotColors.TextPrimary,
    secondary = MerlotColors.AccentDark,
    onSecondary = MerlotColors.Black,
    secondaryContainer = MerlotColors.Surface2,
    onSecondaryContainer = MerlotColors.TextPrimary,
    tertiaryContainer = MerlotColors.Surface2,
    onTertiaryContainer = MerlotColors.TextPrimary,
    background = MerlotColors.Background,
    onBackground = MerlotColors.TextPrimary,
    surface = MerlotColors.Surface,
    onSurface = MerlotColors.TextPrimary,
    surfaceVariant = MerlotColors.Surface2,
    onSurfaceVariant = MerlotColors.TextMuted,
    outline = MerlotColors.Border,
    error = MerlotColors.Danger,
    onError = MerlotColors.White
)

@Composable
fun MerlotTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MerlotDarkColorScheme,
        typography = MerlotTypography,
        content = content
    )
}
