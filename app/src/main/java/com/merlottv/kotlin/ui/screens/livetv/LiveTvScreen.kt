package com.merlottv.kotlin.ui.screens.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit = {}
) {
    // Keep screen awake while Live TV is active; stop playback on navigation away
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Stop all audio/video playback immediately when leaving Live TV
            try { viewModel.stopPlayback() } catch (_: Exception) {}
            try { viewModel.stopVlc() } catch (_: Exception) {}
        }
    }

    // Pause Live TV when app goes to background — prevents audio leak
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    try { viewModel.stopPlayback() } catch (_: Exception) {}
                    try { viewModel.stopVlc() } catch (_: Exception) {}
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    // Resume channel when coming back
                    try { viewModel.resumePlayback() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState by viewModel.uiState.collectAsState()
    var lastBackPressTime by remember { mutableStateOf(0L) }

    if (uiState.isFullscreen && uiState.selectedChannel != null) {
        FullscreenPlayer(
            viewModel = viewModel,
            uiState = uiState,
            onNavigateToHome = onNavigateToHome,
            lastBackPressTime = lastBackPressTime,
            onBackPressed = { lastBackPressTime = it }
        )
    } else {
        ChannelListView(
            viewModel = viewModel,
            uiState = uiState,
            onNavigateToHome = onNavigateToHome,
            lastBackPressTime = lastBackPressTime,
            onBackPressed = { lastBackPressTime = it }
        )
    }
}

@Composable
private fun FullscreenPlayer(
    viewModel: LiveTvViewModel,
    uiState: LiveTvUiState,
    onNavigateToHome: () -> Unit = {},
    lastBackPressTime: Long = 0L,
    onBackPressed: (Long) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    // Auto-hide overlay after 5 seconds (but not if quick menu or EPG guide is open)
    LaunchedEffect(uiState.showOverlay, uiState.showQuickMenu, uiState.showEpgGuide) {
        if (uiState.showOverlay && !uiState.showQuickMenu && !uiState.showEpgGuide) {
            delay(5000)
            viewModel.hideOverlay()
        }
    }

    // Request focus for D-pad input — with safety delay
    // Also re-capture focus when quick menu or EPG guide closes
    LaunchedEffect(Unit) {
        delay(100)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
    LaunchedEffect(uiState.showQuickMenu) {
        if (!uiState.showQuickMenu) {
            delay(100)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
        // Start/stop periodic bitrate refresh when Quick Menu opens/closes
        viewModel.onQuickMenuVisibilityChanged(uiState.showQuickMenu)
    }
    LaunchedEffect(uiState.showEpgGuide) {
        if (!uiState.showEpgGuide) {
            delay(100)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    // Priority: EPG Guide > Quick Menu > Default
                    if (uiState.showEpgGuide && uiState.showEpgCategoryPicker) {
                        // Category picker is open — let it handle UP/DOWN/CENTER
                        when (event.key) {
                            Key.Back, Key.DirectionLeft -> {
                                viewModel.toggleEpgCategoryPicker()
                                true
                            }
                            // Let UP/DOWN/CENTER pass through to category picker focus
                            else -> false
                        }
                    } else if (uiState.showEpgGuide) {
                        // EPG Guide is open — handle all D-pad here (focus stays on parent)
                        when (event.key) {
                            Key.Back -> {
                                viewModel.hideEpgGuide()
                                true
                            }
                            Key.DirectionDown -> {
                                viewModel.epgNavigate(1)
                                true
                            }
                            Key.DirectionUp -> {
                                viewModel.epgNavigate(-1)
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                viewModel.epgSelectCurrent()
                                true
                            }
                            Key.DirectionRight -> {
                                viewModel.epgScrollTimeline(1)
                                true
                            }
                            Key.DirectionLeft -> {
                                // LEFT scrolls timeline back; opens category picker only at start
                                if (uiState.epgTimelineAtStart) {
                                    viewModel.toggleEpgCategoryPicker()
                                } else {
                                    viewModel.epgScrollTimeline(-1)
                                }
                                true
                            }
                            else -> false
                        }
                    } else if (uiState.showQuickMenu) {
                        // Quick menu is open — let Up/Down/OK/Enter pass through
                        when (event.key) {
                            Key.Back -> {
                                viewModel.hideQuickMenu()
                                true
                            }
                            else -> false
                        }
                    } else {
                        // Default fullscreen mode
                        when (event.key) {
                            Key.DirectionUp -> {
                                viewModel.channelUp()
                                true
                            }
                            Key.DirectionDown -> {
                                viewModel.channelDown()
                                true
                            }
                            Key.DirectionLeft -> {
                                viewModel.exitFullscreen()
                                viewModel.showChannelList()
                                true
                            }
                            Key.DirectionRight -> {
                                // Open EPG Guide overlay (TiviMate-style)
                                viewModel.showEpgGuide()
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                viewModel.showQuickMenu()
                                true
                            }
                            Key.Back -> {
                                val now = System.currentTimeMillis()
                                if (now - lastBackPressTime < 1500L) {
                                    onNavigateToHome()
                                } else {
                                    onBackPressed(now)
                                    if (uiState.showOverlay) {
                                        viewModel.hideOverlay()
                                    } else {
                                        viewModel.exitFullscreen()
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }
                } else false
            }
    ) {
        // Full-screen video player — ExoPlayer or VLC
        if (uiState.isUsingVlc) {
            // VLC SurfaceView
            AndroidView(
                factory = { context ->
                    android.view.SurfaceView(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        holder.addCallback(object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                viewModel.getVlcPlayer()?.vlcVout?.apply {
                                    setVideoSurface(holder.surface, holder)
                                    attachViews()
                                }
                            }
                            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                                viewModel.getVlcPlayer()?.vlcVout?.setWindowSize(width, height)
                            }
                            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                try { viewModel.getVlcPlayer()?.vlcVout?.detachViews() } catch (_: Exception) {}
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // ExoPlayer PlayerView — uses active player (Apollo or gentle)
            AndroidView(
                factory = { context ->
                    androidx.media3.ui.PlayerView(context).apply {
                        useController = false
                        player = viewModel.getActivePlayer()
                    }
                },
                update = { playerView ->
                    playerView.player = viewModel.getActivePlayer()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Transparent EPG Info Overlay (slides up from bottom)
        AnimatedVisibility(
            visible = uiState.showOverlay && !uiState.showQuickMenu && !uiState.showEpgGuide,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ChannelInfoOverlay(uiState = uiState)
        }

        // Failover indicator (top-center)
        AnimatedVisibility(
            visible = uiState.isFailingOver || uiState.failoverMessage.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.isFailingOver) {
                    CircularProgressIndicator(
                        color = MerlotColors.Accent,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    text = uiState.failoverMessage,
                    color = MerlotColors.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Buffering indicator (bottom-right) — shows during rebuffering
        AnimatedVisibility(
            visible = uiState.isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = MerlotColors.Accent,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Buffering...",
                    color = MerlotColors.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // EPG Guide overlay (TiviMate-style — press RIGHT in fullscreen)
        AnimatedVisibility(
            visible = uiState.showEpgGuide,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            EpgGuideOverlay(
                viewModel = viewModel,
                uiState = uiState
            )
        }

        // Quick Menu overlay (OK button popup) — centered on screen
        AnimatedVisibility(
            visible = uiState.showQuickMenu,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            QuickMenuOverlay(
                uiState = uiState,
                onRecentChannelClick = { index ->
                    viewModel.goToRecentChannel(index)
                },
                onToggleFavorite = {
                    viewModel.toggleCurrentChannelFavorite()
                },
                onToggleSubtitles = {
                    viewModel.toggleSubtitles()
                },
                onTogglePlayerEngine = {
                    viewModel.togglePlayerEngine()
                },
                onDismiss = { viewModel.hideQuickMenu() }
            )
        }
    }
}

@Composable
private fun QuickMenuOverlay(
    uiState: LiveTvUiState,
    onRecentChannelClick: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onTogglePlayerEngine: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val currentChannel = uiState.selectedChannel
    val isFavorite = currentChannel != null && uiState.favoriteIds.contains(currentChannel.id)
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        delay(100)
        try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.62f)
            .clip(RoundedCornerShape(16.dp))
            .background(MerlotColors.Black.copy(alpha = 0.94f))
            .border(1.dp, Color(0xFF888888).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        // ── LEFT PANEL: Menu Actions ──
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Title
            Text(
                text = "Quick Menu",
                color = MerlotColors.Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Stream source + player engine indicator
            val sourceText = buildString {
                if (uiState.streamSource.isNotEmpty()) append("Source: ${uiState.streamSource}")
                if (isNotEmpty()) append(" • ")
                append("Engine: ${if (uiState.isUsingVlc) "VLC" else "ExoPlayer"}")
            }
            Text(
                text = sourceText,
                color = if (uiState.isUsingVlc) Color(0xFFFF9800) else MerlotColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Buffer config info
            val bufferInfo = buildString {
                append("Profile: ${uiState.bufferConfigLabel}")
                append(" • Buffer: ${if (uiState.bufferSizeSec >= 60) "${uiState.bufferSizeSec / 60}min" else "${uiState.bufferSizeSec}s"}")
                append(" • RAM: ${uiState.bufferMemoryCapMb}MB")
                append(" • Offset: ${uiState.liveOffsetMs / 1000}s")
            }
            Text(
                text = bufferInfo,
                color = if (uiState.bufferConfigLabel == "Apollo") Color(0xFF4CAF50) else Color(0xFF2196F3),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Video info line: resolution + quality + codec + framerate
            val videoLine = buildString {
                if (uiState.videoResolution.isNotEmpty()) {
                    append(uiState.videoResolution)
                    val qualityLabel = getQualityLabel(uiState.videoResolution)
                    if (qualityLabel.isNotEmpty()) append(" • $qualityLabel")
                }
                if (uiState.bitrateCheckerEnabled && uiState.videoCodec.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(uiState.videoCodec)
                }
                if (uiState.videoFrameRate.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(uiState.videoFrameRate)
                }
            }
            if (videoLine.isNotEmpty()) {
                Text(
                    text = videoLine,
                    color = MerlotColors.Accent.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = if (uiState.bitrateCheckerEnabled) 2.dp else 4.dp)
                )
            }

            // Bitrate checker details (only when enabled in Settings > Playback)
            if (uiState.bitrateCheckerEnabled) {
                // Video bitrate line
                if (uiState.videoBitrateKbps > 0) {
                    val videoBitrateText = if (uiState.videoBitrateKbps >= 1000)
                        String.format("Video: %.1f Mbps", uiState.videoBitrateKbps / 1000f)
                    else "Video: ${uiState.videoBitrateKbps} Kbps"
                    Text(
                        text = videoBitrateText,
                        color = Color(0xFF4CAF50),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Audio bitrate + codec line
                val audioLine = buildString {
                    if (uiState.audioBitrateKbps > 0) {
                        append("Audio: ${uiState.audioBitrateKbps} Kbps")
                    }
                    if (uiState.audioCodec.isNotEmpty()) {
                        if (isNotEmpty()) append(" • ")
                        else append("Audio: ")
                        append(uiState.audioCodec)
                    }
                }
                if (audioLine.isNotEmpty()) {
                    Text(
                        text = audioLine,
                        color = Color(0xFF2196F3),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Measured network throughput
                if (uiState.measuredBitrateKbps > 0) {
                    val throughputText = if (uiState.measuredBitrateKbps >= 1000)
                        String.format("Throughput: %.1f Mbps", uiState.measuredBitrateKbps / 1000f)
                    else "Throughput: ${uiState.measuredBitrateKbps} Kbps"
                    Text(
                        text = throughputText,
                        color = Color(0xFFFF9800),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                // When bitrate checker is off, show simple bitrate if available (original behavior)
                if (uiState.videoBitrateKbps > 0) {
                    val simpleBitrate = if (uiState.videoBitrateKbps >= 1000)
                        String.format("%.1f Mbps", uiState.videoBitrateKbps / 1000f)
                    else "${uiState.videoBitrateKbps} Kbps"
                    Text(
                        text = simpleBitrate,
                        color = MerlotColors.Accent.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Rebuffer stats
            if (uiState.rebufferCount > 0 || uiState.totalRebufferMs > 0L) {
                val rebufferInfo = buildString {
                    append("Rebuffers: ${uiState.rebufferCount}")
                    if (uiState.totalRebufferMs > 0L) {
                        append(" • Total: ${String.format("%.1f", uiState.totalRebufferMs / 1000f)}s")
                    }
                }
                Text(
                    text = rebufferInfo,
                    color = if (uiState.rebufferCount >= 3) Color(0xFFFF6B6B) else MerlotColors.TextMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                Text(
                    text = "No rebuffers",
                    color = Color(0xFF4CAF50),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Recent channels (last 3)
            val recentChannels = uiState.recentChannels
            if (recentChannels.isNotEmpty()) {
                Text(
                    text = "RECENT CHANNELS",
                    color = MerlotColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                )
                recentChannels.forEachIndexed { index, channel ->
                    QuickMenuItem(
                        icon = Icons.Default.Tv,
                        label = channel.name,
                        enabled = true,
                        onClick = { onRecentChannelClick(index) },
                        focusRequester = if (index == 0) firstItemFocusRequester else null
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Add/Remove from favorites
            QuickMenuItem(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                iconTint = if (isFavorite) MerlotColors.Accent else MerlotColors.TextMuted,
                enabled = currentChannel != null,
                onClick = onToggleFavorite,
                focusRequester = if (recentChannels.isEmpty()) firstItemFocusRequester else null
            )

            // Toggle CC / Subtitles
            QuickMenuItem(
                icon = if (uiState.subtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                label = if (uiState.subtitlesEnabled) "Subtitles: ON" else "Subtitles: OFF",
                iconTint = if (uiState.subtitlesEnabled) MerlotColors.Accent else MerlotColors.TextMuted,
                enabled = true,
                onClick = onToggleSubtitles
            )

            // Toggle Player Engine (ExoPlayer <-> VLC)
            QuickMenuItem(
                icon = Icons.Default.Hd,
                label = if (uiState.isUsingVlc) "Player: VLC" else "Player: ExoPlayer",
                iconTint = if (uiState.isUsingVlc) Color(0xFFFF9800) else MerlotColors.Accent,
                enabled = currentChannel != null,
                onClick = onTogglePlayerEngine
            )

            // Close button
            QuickMenuItem(
                icon = Icons.Default.Close,
                label = "Close",
                enabled = true,
                onClick = onDismiss
            )
        }

        // ── Vertical Divider ──
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color(0xFF888888).copy(alpha = 0.4f))
        )

        // ── RIGHT PANEL: Channel EPG Info ──
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Channel header: logo + name + number/group
            if (currentChannel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    if (currentChannel.logoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = currentChannel.logoUrl,
                            contentDescription = currentChannel.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MerlotColors.Surface2)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = currentChannel.name,
                            color = MerlotColors.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        val channelMeta = buildString {
                            if (currentChannel.number > 0) append("CH ${currentChannel.number}")
                            if (currentChannel.group.isNotEmpty()) {
                                if (isNotEmpty()) append(" \u2022 ")
                                append(currentChannel.group)
                            }
                        }
                        if (channelMeta.isNotEmpty()) {
                            Text(
                                text = channelMeta,
                                color = MerlotColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Resolution badge
                if (uiState.videoResolution.isNotEmpty()) {
                    val qualityLabel = getQualityLabel(uiState.videoResolution)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MerlotColors.AccentAlpha10)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hd,
                            contentDescription = null,
                            tint = MerlotColors.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.videoResolution,
                            color = MerlotColors.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (qualityLabel.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = qualityLabel,
                                color = MerlotColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Thin separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF888888).copy(alpha = 0.25f))
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Current program
                uiState.currentProgram?.let { program ->
                    // NOW label
                    Text(
                        text = "NOW",
                        color = Color(0xFF4CAF50),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    // Program title
                    Text(
                        text = program.title,
                        color = MerlotColors.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    // Description
                    if (program.description.isNotEmpty()) {
                        Text(
                            text = program.description,
                            color = MerlotColors.TextMuted,
                            fontSize = 10.sp,
                            maxLines = 3,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    // Time range
                    val startStr = timeFormat.format(Date(program.startTime))
                    val endStr = timeFormat.format(Date(program.endTime))
                    Text(
                        text = "$startStr - $endStr",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    // Progress bar
                    val now = System.currentTimeMillis()
                    val duration = (program.endTime - program.startTime).coerceAtLeast(1L)
                    val elapsed = (now - program.startTime).coerceIn(0L, duration)
                    val progress = elapsed.toFloat() / duration.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MerlotColors.Surface2)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = progress)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MerlotColors.Accent)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } ?: run {
                    // No EPG data
                    Text(
                        text = "No program info available",
                        color = MerlotColors.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                // Next program
                uiState.nextProgram?.let { next ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "NEXT",
                            color = MerlotColors.Accent.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = next.title,
                            color = MerlotColors.TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeFormat.format(Date(next.startTime)),
                            color = MerlotColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                // No channel selected
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No channel selected",
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    iconTint: Color = MerlotColors.TextMuted,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) Color(0xFF666666).copy(alpha = 0.5f)
                else MerlotColors.Transparent
            )
            .then(
                if (isFocused) Modifier.border(1.dp, Color(0xFF888888), RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter) && enabled
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                if (isFocused) MerlotColors.White else iconTint
            } else MerlotColors.TextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (enabled) {
                if (isFocused) MerlotColors.White else MerlotColors.TextPrimary
            } else MerlotColors.TextMuted.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChannelInfoOverlay(uiState: LiveTvUiState) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val channel = uiState.selectedChannel ?: return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MerlotColors.Transparent,
                        MerlotColors.Black.copy(alpha = 0.85f),
                        MerlotColors.Black.copy(alpha = 0.95f)
                    )
                )
            )
            .padding(top = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Channel name + number row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = MerlotColors.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "CH ${channel.number} • ${channel.group}",
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Resolution/Quality badge
                if (uiState.videoResolution.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MerlotColors.Accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Hd,
                            contentDescription = null,
                            tint = MerlotColors.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.videoResolution,
                            color = MerlotColors.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val qualityLabel = getQualityLabel(uiState.videoResolution)
                        if (qualityLabel.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = qualityLabel,
                                color = MerlotColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current program
            uiState.currentProgram?.let { program ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MerlotColors.AccentAlpha10)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint = MerlotColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NOW",
                            color = MerlotColors.Accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = program.title,
                            color = MerlotColors.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (program.description.isNotEmpty()) {
                            Text(
                                text = program.description,
                                color = MerlotColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${timeFormat.format(Date(program.startTime))} - ${timeFormat.format(Date(program.endTime))}",
                            color = MerlotColors.TextMuted,
                            fontSize = 10.sp
                        )
                        val now = System.currentTimeMillis()
                        val progress = ((now - program.startTime).toFloat() / (program.endTime - program.startTime).toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(80.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MerlotColors.Surface2)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(MerlotColors.Accent)
                            )
                        }
                    }
                }
            }

            // Next program
            uiState.nextProgram?.let { program ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MerlotColors.TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEXT: ",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${program.title} (${timeFormat.format(Date(program.startTime))})",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // D-pad hint
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "\u25B2 \u25BC Channel  \u25CF Menu  \u25C0 Back",
                    color = MerlotColors.TextMuted.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

private fun getQualityLabel(resolution: String): String {
    val parts = resolution.split("x")
    if (parts.size != 2) return ""
    val height = parts[1].toIntOrNull() ?: return ""
    return when {
        height >= 2160 -> "4K UHD"
        height >= 1440 -> "2K QHD"
        height >= 1080 -> "Full HD"
        height >= 720 -> "HD"
        height >= 480 -> "SD"
        else -> "LQ"
    }
}

@Composable
private fun ChannelListView(
    viewModel: LiveTvViewModel,
    uiState: LiveTvUiState,
    onNavigateToHome: () -> Unit = {},
    lastBackPressTime: Long = 0L,
    onBackPressed: (Long) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val categoryFocusRequester = remember { FocusRequester() }
    val channelFocusRequester = remember { FocusRequester() }

    // Request focus on categories when they become visible — with safety delay
    LaunchedEffect(uiState.showCategories) {
        if (uiState.showCategories) {
            delay(150)
            try { categoryFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Request focus on channel list when it becomes visible — scroll to current channel
    LaunchedEffect(uiState.showChannelList, uiState.filteredChannels.isNotEmpty()) {
        if (uiState.showChannelList && !uiState.showCategories && uiState.filteredChannels.isNotEmpty()) {
            // Scroll to the currently playing channel
            val currentIndex = uiState.filteredChannels.indexOfFirst { it.id == uiState.selectedChannel?.id }
            if (currentIndex >= 0) {
                listState.scrollToItem((currentIndex - 3).coerceAtLeast(0))
            }
            delay(200)
            try { channelFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // When categories are showing, consume Left to prevent main sidebar
                // and handle Right/Back to dismiss categories
                if (uiState.showCategories) {
                    when (event.key) {
                        Key.DirectionRight, Key.Back -> {
                            viewModel.hideCategories()
                            true
                        }
                        Key.DirectionLeft -> true // consume — don't let main sidebar open
                        else -> false
                    }
                } else false
            }
    ) {
        // Full-screen player as background
        if (uiState.selectedChannel != null) {
            if (uiState.isUsingVlc) {
                AndroidView(
                    factory = { context ->
                        android.view.SurfaceView(context).apply {
                            holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                    viewModel.getVlcPlayer()?.vlcVout?.apply {
                                        setVideoSurface(holder.surface, holder)
                                        attachViews()
                                    }
                                }
                                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                                    viewModel.getVlcPlayer()?.vlcVout?.setWindowSize(width, height)
                                }
                                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                    try { viewModel.getVlcPlayer()?.vlcVout?.detachViews() } catch (_: Exception) {}
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AndroidView(
                    factory = { context ->
                        androidx.media3.ui.PlayerView(context).apply {
                            useController = false
                        }
                    },
                    update = { playerView ->
                        playerView.player = viewModel.getActivePlayer()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Idle state
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MerlotColors.TextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("MERLOT TV", color = MerlotColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Select a channel from the list to start watching",
                    color = MerlotColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Category sidebar — slides in from left
        AnimatedVisibility(
            visible = uiState.showCategories,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(MerlotColors.Black.copy(alpha = 0.88f))
                    .padding(vertical = 8.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionRight, Key.Back -> {
                                // Go back to channel list
                                viewModel.hideCategories()
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                // Header
                Text(
                    text = "Categories",
                    color = MerlotColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Search — readOnly until Enter/OK is pressed to prevent keyboard on D-pad hover
                var isSearchEditing by remember { mutableStateOf(false) }
                var focusResultsAfterKeyboard by remember { mutableStateOf(false) }
                val searchKeyboardController = LocalSoftwareKeyboardController.current
                val searchResultsFocusRequester = remember { FocusRequester() }
                val searchScope = rememberCoroutineScope()

                // Focus results after keyboard dismisses (with delay for keyboard animation)
                LaunchedEffect(focusResultsAfterKeyboard) {
                    if (focusResultsAfterKeyboard) {
                        delay(200)
                        try { searchResultsFocusRequester.requestFocus() } catch (_: Exception) {}
                        focusResultsAfterKeyboard = false
                    }
                }
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchChanged(it) },
                    readOnly = !isSearchEditing,
                    placeholder = { Text("Search... (press OK to type)", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MerlotColors.TextMuted, modifier = Modifier.size(14.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MerlotColors.TextPrimary,
                        unfocusedTextColor = MerlotColors.TextPrimary,
                        cursorColor = MerlotColors.Accent,
                        focusedBorderColor = MerlotColors.Accent,
                        unfocusedBorderColor = MerlotColors.Border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .onFocusChanged { if (!it.isFocused && isSearchEditing) { isSearchEditing = false; searchKeyboardController?.hide() } }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when {
                                    // Not editing: Enter/OK opens keyboard
                                    !isSearchEditing && (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                                        isSearchEditing = true; searchKeyboardController?.show(); true
                                    }
                                    // Editing: Back closes keyboard, focus moves to results if search has text
                                    isSearchEditing && event.key == Key.Back -> {
                                        isSearchEditing = false; searchKeyboardController?.hide()
                                        if (uiState.searchQuery.isNotBlank()) {
                                            focusResultsAfterKeyboard = true
                                        }
                                        true
                                    }
                                    // Not editing: Down arrow moves focus to results/categories
                                    !isSearchEditing && event.key == Key.DirectionDown -> {
                                        if (uiState.searchQuery.isNotBlank()) {
                                            try { searchResultsFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    // While editing: let ALL keys pass through to on-screen keyboard
                                    isSearchEditing -> false
                                    else -> false
                                }
                            } else false
                        },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Category list OR search results (vertical)
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MerlotColors.Accent, modifier = Modifier.size(24.dp))
                    }
                } else if (uiState.searchQuery.isNotBlank()) {
                    // Search results — show matching channels inline
                    val searchResults = uiState.filteredChannels
                    Text(
                        text = "${searchResults.size} results",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        itemsIndexed(searchResults, key = { index, ch -> "search_${index}_${ch.id}" }) { index, channel ->
                            ChannelItem(
                                channel = channel,
                                isSelected = channel.id == uiState.selectedChannel?.id,
                                isFavorite = uiState.favoriteIds.contains(channel.id),
                                onClick = {
                                    viewModel.onSearchChanged("") // clear search
                                    viewModel.onChannelSelected(channel)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                                focusRequester = if (index == 0) searchResultsFocusRequester else null
                            )
                        }
                    }
                } else {
                    val categoryListState = rememberLazyListState()

                    // Scroll to current category when sidebar opens
                    LaunchedEffect(uiState.showCategories) {
                        if (uiState.showCategories && uiState.selectedGroup != null) {
                            val idx = uiState.groups.indexOf(uiState.selectedGroup)
                            if (idx >= 0) {
                                // +1 for "All Channels" item at top
                                categoryListState.scrollToItem((idx + 1 - 3).coerceAtLeast(0))
                            }
                        }
                    }

                    LazyColumn(
                        state = categoryListState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        item(key = "all_channels") {
                            CategoryItem(
                                label = "All Channels (${uiState.totalChannels})",
                                isSelected = uiState.selectedGroup == null,
                                onClick = { viewModel.onGroupSelected(null) },
                                focusRequester = if (uiState.selectedGroup == null) categoryFocusRequester else null
                            )
                        }
                        items(uiState.groups, key = { it }) { group ->
                            CategoryItem(
                                label = group,
                                isSelected = uiState.selectedGroup == group,
                                onClick = { viewModel.onGroupSelected(group) },
                                focusRequester = if (uiState.selectedGroup == group) categoryFocusRequester else null
                            )
                        }
                    }
                }
            }
        }

        // Channel list — semi-transparent overlay, shows channels in current category
        AnimatedVisibility(
            visible = uiState.showChannelList && !uiState.isLoading,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(MerlotColors.Black.copy(alpha = 0.75f))
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                viewModel.onSearchChanged("")
                                viewModel.showCategories()
                                true
                            }
                            Key.DirectionRight, Key.Back -> {
                                if (event.key == Key.Back) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastBackPressTime < 1500L) {
                                        onNavigateToHome()
                                        return@onPreviewKeyEvent true
                                    }
                                    onBackPressed(now)
                                }
                                // Go back to fullscreen (only if a channel is playing)
                                if (uiState.selectedChannel != null) {
                                    viewModel.hideChannelList()
                                    viewModel.enterFullscreen()
                                }
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                // Group name header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Black.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) "Search: \"${uiState.searchQuery}\""
                               else uiState.selectedGroup ?: "All Channels",
                        color = MerlotColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${uiState.filteredChannels.size} ch",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp
                    )
                }

                // Channel list — use itemsIndexed for O(1) index access
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    val focusTargetIndex = run {
                        val idx = uiState.filteredChannels.indexOfFirst { it.id == uiState.selectedChannel?.id }
                        if (idx >= 0) idx else 0
                    }
                    itemsIndexed(uiState.filteredChannels, key = { index, ch -> "${index}_${ch.id}" }) { index, channel ->
                        ChannelItem(
                            channel = channel,
                            isSelected = channel.id == uiState.selectedChannel?.id,
                            isFavorite = uiState.favoriteIds.contains(channel.id),
                            onClick = { viewModel.onChannelSelected(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                            focusRequester = if (index == focusTargetIndex) channelFocusRequester else null
                        )
                    }
                }

                // Hint bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Black.copy(alpha = 0.9f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "\u25C0 Categories  \u25B6 Hide  \u25CF Select",
                        color = MerlotColors.TextMuted.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Bottom bar showing current channel info
        if (uiState.selectedChannel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val ch = uiState.selectedChannel ?: return
                if (ch.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ch.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(ch.name, color = MerlotColors.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("CH ${ch.number} • ${ch.group}", color = MerlotColors.TextMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFF666666).copy(alpha = 0.4f)
                    isSelected -> MerlotColors.Accent.copy(alpha = 0.15f)
                    else -> MerlotColors.Transparent
                }
            )
            .then(
                if (isFocused) Modifier.border(1.5.dp, Color(0xFF888888), RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = when {
                isFocused -> MerlotColors.White
                isSelected -> MerlotColors.Accent
                else -> MerlotColors.White
            },
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .background(
                when {
                    isFocused -> Color(0xFF666666).copy(alpha = 0.4f)
                    isSelected -> MerlotColors.Accent.copy(alpha = 0.12f)
                    else -> MerlotColors.Transparent
                }
            )
            .then(
                if (isFocused) Modifier.border(1.dp, Color(0xFF888888), RoundedCornerShape(4.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MerlotColors.Surface2)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MerlotColors.Surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(2).uppercase(),
                    color = MerlotColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (isSelected) MerlotColors.Accent else MerlotColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.group.isNotEmpty()) {
                Text(
                    text = channel.group,
                    color = MerlotColors.TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) MerlotColors.Accent else MerlotColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "${channel.number}",
            color = MerlotColors.TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.width(24.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// EPG Guide Overlay — TiviMate-style EPG grid over live video
// ═══════════════════════════════════════════════════════════════════════

private const val EPG_PIXELS_PER_MINUTE = 3f
private const val EPG_CHANNEL_COL_WIDTH = 160
private const val EPG_ROW_HEIGHT = 56
private const val EPG_TIMELINE_HEIGHT = 32

private fun epgRoundToHalfHour(timeMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMs
    val min = cal.get(Calendar.MINUTE)
    cal.set(Calendar.MINUTE, if (min < 30) 0 else 30)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
private fun EpgGuideOverlay(
    viewModel: LiveTvViewModel,
    uiState: LiveTvUiState
) {
    val scrollState = rememberScrollState()
    val channelListState = rememberLazyListState()
    val programListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showCategoryPicker = uiState.showEpgCategoryPicker

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Use ViewModel-driven selectedIndex (parent handles D-pad UP/DOWN/CENTER)
    val selectedIndex = uiState.epgSelectedIndex

    // Update time every 60 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val timelineStartMs = remember(currentTimeMs) {
        epgRoundToHalfHour(currentTimeMs - 60 * 60_000L)
    }
    val timelineEndMs = remember(timelineStartMs) {
        timelineStartMs + 6 * 60 * 60_000L
    }

    // Auto-scroll timeline to current time on first load
    LaunchedEffect(uiState.epgGuideChannels) {
        if (uiState.epgGuideChannels.isNotEmpty()) {
            val nowOffsetPx = ((currentTimeMs - timelineStartMs) / 60_000f * EPG_PIXELS_PER_MINUTE).toInt()
            val scrollTarget = (nowOffsetPx - 200).coerceAtLeast(0)
            scrollState.scrollTo(scrollTarget)
            val currentIndex = viewModel.getCurrentChannelEpgIndex()
            if (currentIndex > 0) {
                channelListState.scrollToItem(currentIndex)
                programListState.scrollToItem(currentIndex)
            }
        }
    }

    // Sync LazyColumn scroll when selectedIndex changes (driven by ViewModel)
    LaunchedEffect(selectedIndex) {
        if (uiState.epgGuideChannels.isNotEmpty()) {
            channelListState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0))
            programListState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0))
        }
    }

    // Scroll timeline when ViewModel requests (driven by LEFT/RIGHT D-pad)
    LaunchedEffect(uiState.epgScrollRequest) {
        if (uiState.epgScrollRequest != 0) {
            val delta = if (uiState.epgScrollRequest > 0) 200 else -200
            scrollState.animateScrollTo((scrollState.value + delta).coerceAtLeast(0))
        }
    }

    // Report timeline scroll position to ViewModel (for LEFT = scroll vs category picker logic)
    LaunchedEffect(scrollState.value) {
        viewModel.updateEpgTimelineAtStart(scrollState.value <= 10)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(16.dp))
            .background(MerlotColors.Black.copy(alpha = 0.92f))
            .border(1.dp, MerlotColors.Border, RoundedCornerShape(16.dp))
    ) {
        when {
            uiState.epgLoading && uiState.epgGuideChannels.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MerlotColors.Accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading TV Guide...", color = MerlotColors.TextMuted, fontSize = 13.sp)
                }
            }
            uiState.epgGuideChannels.isEmpty() -> {
                Text(
                    "No channels available",
                    color = MerlotColors.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header with category selector
                    EpgOverlayHeader(
                        uiState = uiState,
                        onCategoryClick = { viewModel.toggleEpgCategoryPicker() }
                    )

                    // EPG Grid
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left column — channel names (highlighted row tracks selectedIndex)
                        Column(
                            modifier = Modifier
                                .width(EPG_CHANNEL_COL_WIDTH.dp)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(EPG_TIMELINE_HEIGHT.dp)
                                    .fillMaxWidth()
                                    .background(MerlotColors.Surface.copy(alpha = 0.8f))
                            )
                            LazyColumn(
                                state = channelListState,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                itemsIndexed(
                                    uiState.epgGuideChannels,
                                    key = { index, ch -> "${index}_${ch.id}" }
                                ) { index, channel ->
                                    EpgOverlayChannelCell(
                                        channel = channel,
                                        epgChannel = uiState.epgChannels.getOrNull(index),
                                        isCurrentChannel = channel.name.equals(
                                            uiState.selectedChannel?.name, ignoreCase = true
                                        ),
                                        isHighlighted = index == selectedIndex,
                                        onClick = { viewModel.switchToChannelFromGuide(channel) }
                                    )
                                }
                            }
                        }

                        // Right column — timeline + programs (highlighted row)
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Column {
                                EpgOverlayTimelineHeader(scrollState, timelineStartMs, timelineEndMs)
                                Box(modifier = Modifier.weight(1f)) {
                                    LazyColumn(
                                        state = programListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(scrollState)
                                    ) {
                                        itemsIndexed(
                                            uiState.epgChannels,
                                            key = { index, ch -> "${index}_${ch.id}" }
                                        ) { index, channel ->
                                            EpgOverlayChannelRow(
                                                channel = channel,
                                                currentTimeMs = currentTimeMs,
                                                timelineStartMs = timelineStartMs,
                                                isHighlighted = index == selectedIndex,
                                                onProgramSelected = { viewModel.selectEpgProgram(it) }
                                            )
                                        }
                                    }
                                    EpgOverlayNowIndicator(currentTimeMs, timelineStartMs, scrollState)
                                }
                            }
                        }
                    }
                }

                // Category picker overlay
                if (showCategoryPicker) {
                    EpgCategoryPicker(
                        groups = uiState.epgGuideGroups,
                        selectedGroup = uiState.epgGuideSelectedGroup,
                        onGroupSelected = { group ->
                            viewModel.setEpgGuideGroup(group)
                            viewModel.showEpgGuide()
                            viewModel.toggleEpgCategoryPicker()
                        },
                        onDismiss = { viewModel.toggleEpgCategoryPicker() }
                    )
                }

                // Program detail dialog
                uiState.epgSelectedProgram?.let { program ->
                    EpgOverlayProgramDialog(program) { viewModel.selectEpgProgram(null) }
                }
            }
        }
    }
}

@Composable
private fun EpgOverlayHeader(
    uiState: LiveTvUiState,
    onCategoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("TV Guide", color = MerlotColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "${uiState.epgGuideChannels.size} channels",
            color = MerlotColors.TextMuted,
            fontSize = 12.sp
        )

        // Category selector button
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MerlotColors.Surface2)
                .border(1.dp, MerlotColors.Border, RoundedCornerShape(6.dp))
                .clickable { onCategoryClick() }
                .focusable()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "▾ ${uiState.epgGuideSelectedGroup ?: "All Channels"}",
                color = MerlotColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (uiState.epgLoading) {
            Spacer(modifier = Modifier.width(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = MerlotColors.Accent,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Updating...", color = MerlotColors.TextMuted, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        // Show currently playing channel
        uiState.selectedChannel?.let { ch ->
            Text(
                "▶ ${ch.name}",
                color = MerlotColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EpgOverlayTimelineHeader(scrollState: ScrollState, startMs: Long, endMs: Long) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .height(EPG_TIMELINE_HEIGHT.dp)
            .horizontalScroll(scrollState)
            .background(MerlotColors.Surface.copy(alpha = 0.8f))
    ) {
        var time = startMs
        while (time < endMs) {
            val blockWidthDp = (30 * EPG_PIXELS_PER_MINUTE).toInt()
            Box(
                modifier = Modifier
                    .width(blockWidthDp.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, MerlotColors.Border)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = timeFormat.format(Date(time)),
                    color = MerlotColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            time += 30 * 60_000L
        }
    }
}

@Composable
private fun EpgOverlayNowIndicator(currentTimeMs: Long, timelineStartMs: Long, scrollState: ScrollState) {
    val density = LocalDensity.current
    val nowOffsetPx = with(density) {
        ((currentTimeMs - timelineStartMs) / 60_000f * EPG_PIXELS_PER_MINUTE).dp.roundToPx()
    }
    val visibleOffset = nowOffsetPx - scrollState.value
    if (visibleOffset > 0) {
        Box(
            modifier = Modifier
                .offset { IntOffset(visibleOffset, 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(MerlotColors.Accent)
                .zIndex(10f)
        )
    }
}

@Composable
private fun EpgOverlayChannelCell(
    channel: Channel,
    epgChannel: EpgChannel?,
    isCurrentChannel: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val logoUrl = channel.logoUrl.ifEmpty { epgChannel?.icon ?: "" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EPG_ROW_HEIGHT.dp)
            .border(
                width = if (isHighlighted) 2.dp else 0.5.dp,
                color = if (isHighlighted) MerlotColors.Accent else MerlotColors.Border
            )
            .background(
                when {
                    isHighlighted -> MerlotColors.Accent.copy(alpha = 0.25f)
                    isCurrentChannel -> MerlotColors.AccentAlpha10
                    else -> MerlotColors.Surface.copy(alpha = 0.6f)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (logoUrl.isNotEmpty()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = when {
                    isHighlighted -> MerlotColors.White
                    isCurrentChannel -> MerlotColors.Accent
                    else -> MerlotColors.TextPrimary
                },
                fontSize = 11.sp,
                fontWeight = if (isCurrentChannel || isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isCurrentChannel) {
                Text(
                    text = "▶ Now Playing",
                    color = MerlotColors.Accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EpgOverlayChannelRow(
    channel: EpgChannel,
    currentTimeMs: Long,
    timelineStartMs: Long,
    isHighlighted: Boolean = false,
    onProgramSelected: (EpgEntry) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .height(EPG_ROW_HEIGHT.dp)
            .then(
                if (isHighlighted) Modifier.border(2.dp, MerlotColors.Accent)
                else Modifier
            )
    ) {
        val programs = channel.programs
            .filter { it.endTime > timelineStartMs }
            .sortedBy { it.startTime }

        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, MerlotColors.Border)
                    .background(MerlotColors.Surface2.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("No program info", color = MerlotColors.TextMuted, fontSize = 10.sp)
            }
        } else {
            programs.forEach { program ->
                val durationMin = ((program.endTime - program.startTime) / 60_000L).coerceAtLeast(1)
                val widthDp = (durationMin * EPG_PIXELS_PER_MINUTE).coerceIn(80f, 600f).toInt()
                val isAiring = program.startTime <= currentTimeMs && program.endTime >= currentTimeMs
                val isPast = program.endTime < currentTimeMs
                var isFocused by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .fillMaxHeight()
                        .alpha(if (isPast) 0.4f else 1f)
                        .border(
                            width = if (isFocused) 2.dp else 0.5.dp,
                            color = if (isFocused) MerlotColors.Accent else MerlotColors.Border
                        )
                        .background(
                            when {
                                isAiring -> MerlotColors.AccentAlpha10
                                else -> MerlotColors.Surface2.copy(alpha = 0.5f)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onProgramSelected(program) }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                onProgramSelected(program)
                                true
                            } else false
                        }
                ) {
                    Column {
                        Text(
                            text = program.title,
                            color = if (isAiring) MerlotColors.Accent else MerlotColors.TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = timeFormat.format(Date(program.startTime)) + " - " + timeFormat.format(Date(program.endTime)),
                            color = MerlotColors.TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgOverlayProgramDialog(program: EpgEntry, onDismiss: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MerlotColors.Surface)
                .border(1.dp, MerlotColors.Border, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = program.title,
                    color = MerlotColors.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = timeFormat.format(Date(program.startTime)) + " - " + timeFormat.format(Date(program.endTime)),
                    color = MerlotColors.TextPrimary,
                    fontSize = 13.sp
                )
                if (program.category.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = program.category,
                        color = MerlotColors.AccentDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (program.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = program.description,
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                var closeFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (closeFocused) MerlotColors.Accent else MerlotColors.Surface2,
                        contentColor = if (closeFocused) MerlotColors.Black else MerlotColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End).onFocusChanged { closeFocused = it.isFocused }.focusable()
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EpgCategoryPicker(
    groups: List<String>,
    selectedGroup: String?,
    onGroupSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    // Build the full list: "All Channels" + "★ Favorites" + groups
    val allItems = remember(groups) {
        listOf<String?>(null, "★ Favorites") + groups.map { it }
    }
    var highlightedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pickerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        try { pickerFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .background(MerlotColors.Surface)
                .border(1.dp, MerlotColors.Border, RoundedCornerShape(12.dp))
                .focusRequester(pickerFocusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (highlightedIndex < allItems.size - 1) {
                                    highlightedIndex++
                                    scope.launch {
                                        listState.animateScrollToItem(
                                            (highlightedIndex - 3).coerceAtLeast(0)
                                        )
                                    }
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (highlightedIndex > 0) {
                                    highlightedIndex--
                                    scope.launch {
                                        listState.animateScrollToItem(
                                            (highlightedIndex - 3).coerceAtLeast(0)
                                        )
                                    }
                                }
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                val item = allItems.getOrNull(highlightedIndex)
                                onGroupSelected(item)
                                true
                            }
                            Key.Back -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Text(
                    "Select Category",
                    color = MerlotColors.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                Spacer(modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MerlotColors.Border))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(allItems) { index, item ->
                        val label = item ?: "All Channels"
                        val isSelected = item == selectedGroup
                        val isHighlighted = index == highlightedIndex

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGroupSelected(item) }
                                .border(
                                    width = if (isHighlighted) 2.dp else 0.dp,
                                    color = if (isHighlighted) MerlotColors.Accent else Color.Transparent
                                )
                                .background(
                                    when {
                                        isHighlighted -> MerlotColors.Accent.copy(alpha = 0.25f)
                                        isSelected -> MerlotColors.Surface2
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                label,
                                color = when {
                                    isHighlighted -> MerlotColors.White
                                    isSelected -> MerlotColors.Accent
                                    else -> MerlotColors.TextPrimary
                                },
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isHighlighted) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
