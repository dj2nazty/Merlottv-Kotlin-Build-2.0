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
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
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

            // Channel avatars — circular icons with name underneath
            val channelNames = viewModel.getChannelNames()
            val avatarMap = remember(uiState.channelsWithAvatars) {
                uiState.channelsWithAvatars.associate { it.channelName to it.avatarUrl }
            }
            val channelChipFocusRequesters = remember { List(channelNames.size) { FocusRequester() } }
            val channelListState = rememberLazyListState()
            val channelScope = rememberCoroutineScope()
            LazyRow(
                state = channelListState,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                itemsIndexed(channelNames) { index, name ->
                    val isSelected = uiState.selectedChannel == name
                    var isFocused by remember { mutableStateOf(false) }
                    val avatarUrl = avatarMap[name]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .focusRequester(channelChipFocusRequesters[index])
                            .onFocusChanged { state ->
                                isFocused = state.isFocused
                                if (state.isFocused) {
                                    channelScope.launch { channelListState.animateScrollToItem(index) }
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (index > 0) {
                                                try { channelChipFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                            }
                                            true // Always consume Left — prevent sidebar from opening
                                        }
                                        Key.DirectionRight -> {
                                            if (index < channelNames.size - 1) {
                                                try { channelChipFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                            }
                                            true // Always consume Right
                                        }
                                        Key.DirectionCenter, Key.Enter -> {
                                            viewModel.onChannelSelected(name)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                    ) {
                        // Circle avatar
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MerlotColors.Accent.copy(alpha = 0.15f)
                                    else Color(0xFF1E1E1E)
                                )
                                .border(
                                    width = when {
                                        isSelected -> 2.5.dp
                                        isFocused -> 2.5.dp
                                        else -> 0.dp
                                    },
                                    color = when {
                                        isSelected -> MerlotColors.Accent
                                        isFocused -> Color(0xFF00BFA5)
                                        else -> Color.Transparent
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (name == "All") {
                                // "All" icon — YouTube play button style
                                Text(
                                    "▶",
                                    color = if (isSelected) MerlotColors.Accent else MerlotColors.TextPrimary,
                                    fontSize = 20.sp
                                )
                            } else if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = name,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // Fallback: first letter of channel name
                                Text(
                                    name.first().uppercase(),
                                    color = MerlotColors.TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Channel name under the circle
                        Text(
                            text = name,
                            color = when {
                                isSelected -> MerlotColors.Accent
                                isFocused -> Color(0xFF00BFA5)
                                else -> MerlotColors.TextMuted
                            },
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val gridColumns = ((maxWidth - 32.dp) / (180.dp + 10.dp)).toInt().coerceAtLeast(1)
                        val cardFocusRequesters = remember(uiState.filteredVideos.size) {
                            List(uiState.filteredVideos.size) { FocusRequester() }
                        }
                        LaunchedEffect(uiState.filteredVideos.isNotEmpty()) {
                            if (uiState.filteredVideos.isNotEmpty()) {
                                delay(300)
                                try { cardFocusRequesters.firstOrNull()?.requestFocus() } catch (_: Exception) {}
                            }
                        }
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
                                val isLeftEdge = index % gridColumns == 0
                                val isRightEdge = index % gridColumns == gridColumns - 1 || index == uiState.filteredVideos.size - 1
                                YouTubeVideoCard(
                                    video = video,
                                    focusRequester = cardFocusRequesters.getOrNull(index) ?: remember { FocusRequester() },
                                    isFirstCard = index == 0,
                                    onClick = { playingYtVideoId = video.videoId },
                                    onLeft = {
                                        if (!isLeftEdge && index > 0) {
                                            try { cardFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                        }
                                    },
                                    onRight = {
                                        if (!isRightEdge && index < uiState.filteredVideos.size - 1) {
                                            try { cardFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                        }
                                    }
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
    isFirstCard: Boolean = false,
    onClick: () -> Unit,
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {}
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
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onLeft()
                            true // Always consume Left — prevent sidebar from opening
                        }
                        Key.DirectionRight -> {
                            onRight()
                            true // Always consume Right
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
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

        // Channel name + view count + time ago
        Text(
            text = video.channelName,
            color = MerlotColors.TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )

        // "38K views • 3 days ago"
        val metaText = buildString {
            if (video.viewCount.isNotBlank()) append(video.viewCount)
            if (video.viewCount.isNotBlank() && video.publishedTimeText.isNotBlank()) append(" • ")
            if (video.publishedTimeText.isNotBlank()) append(video.publishedTimeText)
        }
        if (metaText.isNotBlank()) {
            Text(
                text = metaText,
                color = MerlotColors.TextMuted.copy(alpha = 0.7f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
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
