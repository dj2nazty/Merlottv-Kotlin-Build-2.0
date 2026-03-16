package com.merlottv.kotlin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.merlottv.kotlin.R
import kotlinx.coroutines.delay

/**
 * Branded loading screen with the MerlotTV logo.
 * Subtler pulse animation inspired by NuvioTV:
 * - 400ms delay before logo fades in (700ms)
 * - Gentle 2000ms scale pulse from 1.0x to 1.04x
 * - Smooth overlay fade in/out
 */
@Composable
fun MerlotLoadingScreen(
    visible: Boolean = true
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        // Delayed logo appearance
        var logoVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(400) // 400ms delay before logo appears
            logoVisible = true
        }

        val logoAlpha by animateFloatAsState(
            targetValue = if (logoVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 700),
            label = "loadingLogoAlpha"
        )

        // Subtle infinite pulse — 1.0x to 1.04x over 2000ms (like NuvioTV)
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loadingLogoScale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.merlot_splash_logo),
                contentDescription = "MerlotTV",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(280.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = logoAlpha
                    }
            )
        }
    }
}
