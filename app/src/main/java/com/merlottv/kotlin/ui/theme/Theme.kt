package com.merlottv.kotlin.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

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
        pressedAlpha = 0.10f,
        focusedAlpha = 0f,   // No teal focus overlay
        draggedAlpha = 0.08f,
        hoveredAlpha = 0.04f
    )
}

/**
 * No-op indication that removes all default focus rings, ripples, and
 * teal highlights from Material3 components. The app handles its own
 * focus visuals (grey background, border, scale) per component.
 */
@Suppress("DEPRECATION")
private object NoIndication : Indication {
    private object NoIndicationInstance : IndicationInstance {
        override fun ContentDrawScope.drawIndication() {
            drawContent()
        }
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        return NoIndicationInstance
    }
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
            LocalIndication provides NoIndication,
            content = content
        )
    }
}
