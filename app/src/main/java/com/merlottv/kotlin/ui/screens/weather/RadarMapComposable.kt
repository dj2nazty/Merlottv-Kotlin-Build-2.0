package com.merlottv.kotlin.ui.screens.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.RadarFrame
import com.merlottv.kotlin.ui.theme.MerlotColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Animated radar map composable using RainViewer tiles.
 * Displays a 512px tile at zoom 6 centered on the user's lat/lon.
 * Cycles through past radar frames to show precipitation movement.
 */
@Composable
fun RadarMapComposable(
    frames: List<RadarFrame>,
    currentIndex: Int,
    lat: Double,
    lon: Double,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
) {
    if (frames.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(if (isFullscreen) 600.dp else 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MerlotColors.Surface2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No radar data available",
                color = MerlotColors.TextMuted,
                fontSize = 14.sp
            )
        }
        return
    }

    val currentFrame = frames.getOrNull(currentIndex) ?: frames.first()

    // Calculate tile x/y from lat/lon at zoom level 6
    val zoom = 6
    val tileSize = 512
    val n = 1 shl zoom // 2^zoom
    val tileX = ((lon + 180.0) / 360.0 * n).toInt()
    val tileY = ((1.0 - Math.log(Math.tan(Math.toRadians(lat)) + 1.0 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0 * n).toInt()

    // RainViewer tile URL: {host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png
    // color=2 = universal color scheme, smooth=1 snow=1
    val tileUrl = "${currentFrame.host}${currentFrame.path}/$tileSize/$zoom/$tileX/$tileY/2/1_1.png"

    // Dark base map tile (OpenStreetMap dark tile)
    val baseMapUrl = "https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/$zoom/$tileX/$tileY@2x.png"

    // Format timestamp
    val timeText = remember(currentFrame.time) {
        try {
            val sdf = SimpleDateFormat("h:mm a", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(Date(currentFrame.time * 1000L))
        } catch (_: Exception) { "" }
    }

    val shape = RoundedCornerShape(12.dp)
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.height(300.dp))
            .clip(shape)
            .background(Color(0xFF0A0A1A), shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MerlotColors.Accent else MerlotColors.Border,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onToggleFullscreen()
                    true
                } else false
            }
    ) {
        // Base map layer
        AsyncImage(
            model = baseMapUrl,
            contentDescription = "Base map",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Radar overlay
        AsyncImage(
            model = tileUrl,
            contentDescription = "Radar overlay",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Timestamp overlay (bottom-left)
        if (timeText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = timeText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Frame indicator (bottom-right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${currentIndex + 1}/${frames.size}",
                color = MerlotColors.TextMuted,
                fontSize = 11.sp
            )
        }

        // Fullscreen hint (top-right) — only show when NOT fullscreen
        if (!isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isFocused) "Press ENTER for fullscreen" else "RADAR",
                    color = if (isFocused) MerlotColors.Accent else MerlotColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Back hint in fullscreen mode
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Press BACK to exit fullscreen",
                    color = MerlotColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
