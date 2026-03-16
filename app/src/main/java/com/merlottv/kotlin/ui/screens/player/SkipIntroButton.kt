package com.merlottv.kotlin.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlinx.coroutines.delay

/**
 * Skip interval types (intro, outro, recap).
 */
enum class SkipType(val label: String) {
    INTRO("Skip Intro"),
    OUTRO("Skip Outro"),
    RECAP("Skip Recap")
}

/**
 * Data class representing a skip interval in a video.
 */
data class SkipInterval(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long
)

/**
 * Netflix-style "Skip Intro" button overlay.
 * Appears when playback enters a skip interval and auto-hides after 10 seconds.
 * D-pad: Enter = skip, Back = dismiss.
 */
@Composable
fun SkipIntroButton(
    visible: Boolean,
    skipInterval: SkipInterval?,
    onSkip: (endMs: Long) -> Unit,
    onDismiss: () -> Unit,
    autoHideSeconds: Int = 10
) {
    if (skipInterval == null) return

    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableStateOf(1f) }

    // Auto-hide countdown
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        progressFraction = 1f
        val steps = autoHideSeconds * 10 // Update every 100ms for smooth progress
        for (i in steps downTo 0) {
            progressFraction = i.toFloat() / steps
            delay(100)
        }
        onDismiss()
    }

    // Request focus when visible
    LaunchedEffect(visible) {
        if (visible) {
            delay(300)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MerlotColors.Surface.copy(alpha = 0.85f),
                            MerlotColors.Surface.copy(alpha = 0.95f)
                        )
                    )
                )
                .then(
                    if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                    else Modifier.border(1.dp, MerlotColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                )
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                onSkip(skipInterval.endMs)
                                true
                            }
                            Key.Back, Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .focusable()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Button content
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = null,
                    tint = if (isFocused) MerlotColors.Accent else MerlotColors.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = skipInterval.type.label,
                    color = if (isFocused) MerlotColors.Accent else MerlotColors.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Progress bar at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(top = 32.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MerlotColors.Surface2)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(2.dp)
                        .background(MerlotColors.Accent.copy(alpha = 0.7f))
                )
            }
        }
    }
}
