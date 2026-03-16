package com.merlottv.kotlin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.merlottv.kotlin.domain.model.WeatherAlert
import kotlinx.coroutines.delay

/**
 * Global scrolling weather alert ticker — shows at the top of screen
 * during Live TV and VOD playback when NWS alerts are active.
 */
@Composable
fun WeatherAlertTicker(
    alerts: List<WeatherAlert>,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && alerts.isNotEmpty(),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val worstSeverity = alerts.firstOrNull()?.severity ?: "Unknown"
        val severityColor = when (worstSeverity) {
            "Extreme" -> Color(0xFFCC0000)
            "Severe" -> Color(0xFFDD2200)
            "Moderate" -> Color(0xFFCC6600)
            "Minor" -> Color(0xFFCCAA00)
            else -> Color(0xFF888888)
        }

        // Cycle through alerts if multiple
        var currentAlertIndex by remember { mutableIntStateOf(0) }

        LaunchedEffect(alerts.size) {
            if (alerts.size > 1) {
                while (true) {
                    delay(6000) // Show each alert for 6 seconds
                    currentAlertIndex = (currentAlertIndex + 1) % alerts.size
                }
            }
        }

        val currentAlert = alerts.getOrNull(currentAlertIndex) ?: alerts.firstOrNull()

        // Scrolling offset for marquee effect
        var scrollOffset by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(currentAlertIndex) {
            scrollOffset = 0f
            while (true) {
                delay(32) // ~30fps
                scrollOffset -= 1.5f // pixels per frame
                if (scrollOffset < -3000f) scrollOffset = 0f // reset after scroll
            }
        }

        if (currentAlert != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .drawWithContent {
                        drawContent()
                        // Colored severity stripe on left edge
                        drawRect(
                            color = severityColor,
                            topLeft = Offset.Zero,
                            size = Size(4.dp.toPx(), size.height)
                        )
                    }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xE6111118),
                                Color(0xCC111118)
                            )
                        )
                    )
                    .padding(start = 12.dp, end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Alert badge
                    Text(
                        text = "\u26A0",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    // Scrolling text
                    Box(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WEATHER ALERT: ${currentAlert.event} \u2014 ${currentAlert.headline}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.graphicsLayer {
                                translationX = scrollOffset
                            }
                        )
                    }

                    // Alert count (if multiple)
                    if (alerts.size > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${currentAlertIndex + 1}/${alerts.size}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
