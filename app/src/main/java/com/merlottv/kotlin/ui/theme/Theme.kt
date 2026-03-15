package com.merlottv.kotlin.ui.theme

import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

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

/**
 * Custom ripple theme that uses white/grey instead of Material3's default
 * teal/cyan primary color. Prevents teal bleed on focus/press/hover for
 * ALL Material3 components (IconButton, Button, Switch, etc.).
 */
private object MerlotRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor(): Color = Color.White

    @Composable
    override fun rippleAlpha(): RippleAlpha = RippleAlpha(
        pressedAlpha = 0.12f,
        focusedAlpha = 0.12f,
        draggedAlpha = 0.08f,
        hoveredAlpha = 0.08f
    )
}

@Composable
fun MerlotTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MerlotDarkColorScheme,
        typography = MerlotTypography
    ) {
        @Suppress("DEPRECATION")
        CompositionLocalProvider(
            LocalRippleTheme provides MerlotRippleTheme,
            content = content
        )
    }
}
