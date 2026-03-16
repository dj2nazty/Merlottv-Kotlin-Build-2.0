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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlinx.coroutines.delay

/**
 * Data class for next episode info.
 */
data class NextEpisodeInfo(
    val contentId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnail: String?,
    val streamUrl: String? = null
)

/**
 * Netflix-style "Next Episode" card overlay that appears near the end of an episode.
 * Shows episode info with a 15-second countdown, then auto-plays.
 */
@Composable
fun NextEpisodeCardOverlay(
    visible: Boolean,
    episodeInfo: NextEpisodeInfo?,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit,
    countdownSeconds: Int = 15
) {
    if (episodeInfo == null) return

    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var countdown by remember(visible) { mutableIntStateOf(countdownSeconds) }

    // Auto-play countdown
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        countdown = countdownSeconds
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        // Countdown finished — auto-play
        onPlayNow()
    }

    // Request focus when visible
    LaunchedEffect(visible) {
        if (visible) {
            delay(350)
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
                .width(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MerlotColors.Surface.copy(alpha = 0.95f))
                .then(
                    if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(12.dp))
                    else Modifier
                )
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> { onPlayNow(); true }
                            Key.Back, Key.Escape -> { onDismiss(); true }
                            else -> false
                        }
                    } else false
                }
                .focusable()
                .padding(12.dp)
        ) {
            Column {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = MerlotColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Up Next",
                        color = MerlotColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Playing in ${countdown}s",
                        color = MerlotColors.TextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Thumbnail + info
                Row {
                    // Thumbnail
                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MerlotColors.Surface2)
                    ) {
                        if (episodeInfo.thumbnail != null) {
                            AsyncImage(
                                model = episodeInfo.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(112.dp)
                                    .height(64.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .width(112.dp)
                                .height(64.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MerlotColors.Transparent,
                                            MerlotColors.Black.copy(alpha = 0.5f)
                                        )
                                    )
                                )
                        )
                        // Play icon
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MerlotColors.White,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Episode info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "S${episodeInfo.season.toString().padStart(2, '0')}E${episodeInfo.episode.toString().padStart(2, '0')}",
                            color = MerlotColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = episodeInfo.title,
                            color = MerlotColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Countdown progress bar
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MerlotColors.Surface2)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(countdown.toFloat() / countdownSeconds)
                            .height(3.dp)
                            .background(MerlotColors.Accent)
                    )
                }
            }
        }
    }
}
