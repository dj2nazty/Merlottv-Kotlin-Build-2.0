package com.merlottv.kotlin.ui.screens.youtube

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.YouTubeVideo
import com.merlottv.kotlin.ui.components.MerlotChip
import com.merlottv.kotlin.ui.theme.MerlotColors
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import kotlinx.coroutines.delay

@Composable
fun YouTubeScreen(
    viewModel: YouTubeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var playingYtVideoId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    Box(modifier = Modifier.fillMaxSize().background(MerlotColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YouTube",
                    color = MerlotColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.videos.isNotEmpty()) {
                    Text(
                        text = "${uiState.filteredVideos.size} videos",
                        color = MerlotColors.TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Channel filter chips
            val channelNames = viewModel.getChannelNames()
            val channelChipFocusRequesters = remember { List(channelNames.size) { FocusRequester() } }
            LazyRow(
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(channelNames) { index, name ->
                    val isSelected = uiState.selectedChannel == name
                    Box(
                        modifier = Modifier
                            .focusRequester(channelChipFocusRequesters[index])
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (index > 0) {
                                                try { channelChipFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                            }
                                            true // Always consume Left
                                        }
                                        Key.DirectionRight -> {
                                            if (index < channelNames.size - 1) {
                                                try { channelChipFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                            }
                                            true // Always consume Right
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        MerlotChip(
                            selected = isSelected,
                            onClick = { viewModel.onChannelSelected(name) },
                            label = {
                                Text(
                                    name,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary
                                )
                            }
                        )
                    }
                }
            }

            // Content
            when {
                uiState.isLoading -> {
                    YouTubeLoadingAnimation()
                }
                uiState.filteredVideos.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No videos found", color = MerlotColors.TextMuted, fontSize = 14.sp)
                    }
                }
                else -> {
                    val firstCardFocus = remember { FocusRequester() }
                    LaunchedEffect(uiState.filteredVideos.isNotEmpty()) {
                        if (uiState.filteredVideos.isNotEmpty()) {
                            delay(300)
                            try { firstCardFocus.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val gridColumns = ((maxWidth - 32.dp) / (180.dp + 10.dp)).toInt().coerceAtLeast(1)
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(180.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                uiState.filteredVideos,
                                key = { _, video -> video.videoId },
                                contentType = { _, _ -> "ytcard" }
                            ) { index, video ->
                                YouTubeVideoCard(
                                    video = video,
                                    focusRequester = if (index == 0) firstCardFocus else null,
                                    isLeftEdge = index % gridColumns == 0,
                                    onClick = { playingYtVideoId = video.videoId }
                                )
                            }
                        }
                    }
                }
            }
        }

        // TrailerPlayer overlay with controls, info, and auto-play next
        playingYtVideoId?.let { ytId ->
            val currentVideo = uiState.filteredVideos.find { it.videoId == ytId }
            // Find next video by same creator, sorted by date (newest first, so next = older)
            val sameChannelVideos = uiState.filteredVideos.filter { it.channelName == currentVideo?.channelName }
            val currentIndex = sameChannelVideos.indexOfFirst { it.videoId == ytId }
            val nextVideo = if (currentIndex >= 0 && currentIndex < sameChannelVideos.size - 1) {
                sameChannelVideos[currentIndex + 1]
            } else null

            com.merlottv.kotlin.ui.components.TrailerPlayer(
                ytVideoId = ytId,
                onDismiss = { playingYtVideoId = null },
                loadingContent = { YouTubeLoadingAnimation() },
                videoTitle = currentVideo?.title,
                channelName = currentVideo?.channelName,
                showControls = true,
                nextVideoTitle = nextVideo?.title,
                onPlayNext = nextVideo?.let { next ->
                    { playingYtVideoId = next.videoId }
                }
            )
        }
    }
}

@Composable
private fun YouTubeVideoCard(
    video: YouTubeVideo,
    focusRequester: FocusRequester? = null,
    isLeftEdge: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "ytScale"
    )

    Column(
        modifier = Modifier
            .width(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .let { mod -> if (focusRequester != null) mod.focusRequester(focusRequester) else mod }
            .onPreviewKeyEvent { event ->
                if (isLeftEdge && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    true // Consume Left on left-edge cards
                } else false
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MerlotColors.Surface)
                .then(
                    if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                    else Modifier
                )
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Text(
            text = video.title,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = video.channelName,
            color = MerlotColors.TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Pulsing YouTube play icon loading animation.
 * Red rounded box with white PlayArrow that pulses in scale and alpha.
 */
@Composable
private fun YouTubeLoadingAnimation() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ytPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ytPulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ytPulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Loading YouTube...",
                color = MerlotColors.TextMuted.copy(alpha = pulseAlpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
