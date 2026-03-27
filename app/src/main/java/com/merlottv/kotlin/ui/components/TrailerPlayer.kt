package com.merlottv.kotlin.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.PlayerView
import com.merlottv.kotlin.data.youtube.YouTubeExtractor
import com.merlottv.kotlin.data.youtube.YouTubeStreamResult
import com.merlottv.kotlin.ui.theme.MerlotColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native ExoPlayer-based YouTube trailer player.
 * Extracts actual stream URLs via Innertube API and plays natively — no WebView.
 *
 * Falls back to [YouTubeWebPlayer] if extraction fails.
 *
 * @param ytVideoId YouTube video ID (e.g. "dQw4w9WgXcQ")
 * @param onDismiss Called when user presses Back or Close
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TrailerPlayerEntryPoint {
    fun youTubeExtractor(): YouTubeExtractor
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TrailerPlayer(
    ytVideoId: String,
    onDismiss: () -> Unit,
    loadingContent: (@Composable () -> Unit)? = null,
    videoTitle: String? = null,
    channelName: String? = null,
    showControls: Boolean = false,
    nextVideoTitle: String? = null,
    onPlayNext: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val extractor = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TrailerPlayerEntryPoint::class.java
        ).youTubeExtractor()
    }

    var streamResult by remember { mutableStateOf<YouTubeStreamResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var extractionFailed by remember { mutableStateOf(false) }
    var showBrandedIntro by remember { mutableStateOf(true) }

    // Extract streams on launch
    LaunchedEffect(ytVideoId) {
        isLoading = true
        extractionFailed = false
        showBrandedIntro = true
        val result = withContext(Dispatchers.IO) {
            try {
                extractor.extract(ytVideoId)
            } catch (e: Exception) {
                Log.e("TrailerPlayer", "Extraction failed: ${e.message}")
                null
            }
        }
        if (result != null && (result.videoUrl != null || result.progressiveUrl != null || result.hlsManifestUrl != null)) {
            streamResult = result
            isLoading = false
        } else {
            // Extraction failed — fall back to WebView
            extractionFailed = true
            isLoading = false
        }
    }

    // Branded intro timer — show pulsing logo for 3 seconds then transition to video
    LaunchedEffect(showBrandedIntro, isLoading) {
        if (showBrandedIntro) {
            kotlinx.coroutines.delay(3000L)
            showBrandedIntro = false
        }
    }

    // If extraction failed, fall back to WebView player
    if (extractionFailed) {
        YouTubeWebPlayer(
            url = "https://www.youtube.com/watch?v=$ytVideoId",
            onDismiss = onDismiss
        )
        return
    }

    // Branded loading screen — shown during extraction AND as a 5-second intro
    if (isLoading || showBrandedIntro) {
        BackHandler { onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        onDismiss(); true
                    } else false
                }
        ) {
            if (loadingContent != null) {
                loadingContent()
            } else {
                MerlotLoadingScreen()
            }
        }
        return
    }

    // We have streams — build and play with ExoPlayer
    val result = streamResult ?: return

    BackHandler { onDismiss() }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // Player state tracked as composable state (accessible from both DisposableEffect and UI)
    var videoEnded by remember { mutableStateOf(false) }
    var isPlayerPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var countdownSeconds by remember { mutableIntStateOf(10) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Create ExoPlayer and prepare media source
    DisposableEffect(result) {
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(5_000, 60_000, 2_000, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()

        // Priority: HLS > Adaptive (video+audio) > Progressive
        // HLS from IOS client supports up to 1080p and doesn't require signature
        // decryption. Adaptive URLs often fail with "Source error" due to PO tokens.
        val mediaSourceReady = when {
            result.hlsManifestUrl != null -> {
                Log.d("TrailerPlayer", "Using HLS manifest (best reliability + quality)")
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(result.userAgent)
                    .setAllowCrossProtocolRedirects(true)
                val hlsSource = HlsMediaSource.Factory(httpFactory)
                    .createMediaSource(MediaItem.fromUri(result.hlsManifestUrl))
                exoPlayer.setMediaSource(hlsSource)
                true
            }
            result.videoUrl != null -> {
                val source = buildMediaSource(context, result)
                if (source != null) {
                    Log.d("TrailerPlayer", "Using adaptive streams")
                    exoPlayer.setMediaSource(source)
                    true
                } else {
                    Log.w("TrailerPlayer", "Adaptive source build failed")
                    false
                }
            }
            result.progressiveUrl != null -> {
                val source = buildMediaSource(context, result)
                if (source != null) {
                    Log.d("TrailerPlayer", "Using progressive stream")
                    exoPlayer.setMediaSource(source)
                    true
                } else {
                    Log.w("TrailerPlayer", "No playable source found")
                    false
                }
            }
            else -> {
                Log.w("TrailerPlayer", "No playable source found")
                false
            }
        }

        if (!mediaSourceReady) {
            exoPlayer.release()
            return@DisposableEffect onDispose {}
        }

        // Allow up to 1080p but let ExoPlayer adapt to bandwidth (no forced minimum)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(1920, 1080)
            .build()

        exoPlayer.playWhenReady = true
        exoPlayer.prepare()

        // Listen for errors and video completion
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("TrailerPlayer", "Playback error: ${error.message}")
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && onPlayNext != null) {
                    videoEnded = true
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlayerPlaying = playing
            }
        })

        player = exoPlayer

        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
            player = null
            System.gc()
        }
    }

    // Update position/duration periodically for progress bar
    LaunchedEffect(player) {
        while (true) {
            player?.let { p ->
                currentPositionMs = p.currentPosition.coerceAtLeast(0)
                durationMs = p.duration.coerceAtLeast(0)
            }
            kotlinx.coroutines.delay(500L)
        }
    }

    // Countdown timer for auto-play next
    LaunchedEffect(videoEnded) {
        if (videoEnded && onPlayNext != null) {
            countdownSeconds = 10
            while (countdownSeconds > 0) {
                kotlinx.coroutines.delay(1000L)
                countdownSeconds--
            }
            onPlayNext()
        }
    }

    // Show controls when video becomes visible (after loading/intro)
    LaunchedEffect(isLoading, showBrandedIntro) {
        if (!isLoading && !showBrandedIntro) {
            controlsVisible = true
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // Auto-hide controls after 5 seconds of no interaction while playing
    LaunchedEffect(lastInteractionTime, isPlayerPlaying, isLoading, showBrandedIntro) {
        if (!isLoading && !showBrandedIntro && isPlayerPlaying && !videoEnded) {
            kotlinx.coroutines.delay(5000L)
            controlsVisible = false
        }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    // Any key press shows controls
                    if (showControls) {
                        controlsVisible = true
                        lastInteractionTime = System.currentTimeMillis()
                    }
                    when (event.key) {
                        Key.Back -> {
                            if (videoEnded) {
                                videoEnded = false // Cancel auto-play
                                onDismiss()
                            } else {
                                onDismiss()
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            player?.let { p ->
                                val newPos = (p.currentPosition - 10_000).coerceAtLeast(0)
                                p.seekTo(newPos)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            player?.let { p ->
                                val newPos = (p.currentPosition + 10_000)
                                    .coerceAtMost(p.duration.coerceAtLeast(0))
                                p.seekTo(newPos)
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (videoEnded && onPlayNext != null) {
                                onPlayNext()
                            } else {
                                player?.let { p ->
                                    if (p.isPlaying) p.pause() else p.play()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        // ExoPlayer view
        player?.let { exo ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.player = exo
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Video ended — auto-play next overlay
        if (videoEnded && onPlayNext != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Up Next",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (nextVideoTitle != null) {
                        Text(
                            nextVideoTitle,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        "Playing in ${countdownSeconds}s",
                        color = MerlotColors.Accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Play Now button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MerlotColors.Accent)
                                .clickable { onPlayNext() }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("Play Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        // Cancel button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable {
                                    videoEnded = false
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Controls overlay — always visible when paused, auto-hides when playing
        if (showControls && !videoEnded && (controlsVisible || !isPlayerPlaying)) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient with title info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        if (videoTitle != null) {
                            Text(
                                videoTitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (channelName != null) {
                            Text(
                                channelName,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Center play/pause icon
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayerPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Bottom gradient with progress bar and time
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        // Progress bar
                        val progress = if (durationMs > 0) {
                            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFFF0000), // YouTube red
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Time and info row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                "◀ -10s   +10s ▶",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Close button (top-right) — shown when controls are hidden
        if (!showControls || (!controlsVisible && isPlayerPlaying && !videoEnded)) {
            var closeFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .onFocusChanged { closeFocused = it.isFocused }
                    .then(
                        if (closeFocused) Modifier.border(2.dp, MerlotColors.Accent, CircleShape)
                        else Modifier
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter)
                        ) {
                            onDismiss(); true
                        } else false
                    }
                    .focusable()
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Format milliseconds to mm:ss or h:mm:ss */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * Build the appropriate MediaSource based on extracted stream URLs.
 * - If both video and audio URLs: MergingMediaSource (adaptive)
 * - If only progressive URL: ProgressiveMediaSource
 *
 * Uses DefaultHttpDataSource with matching User-Agent as primary approach,
 * falls back to YouTubeChunkedDataSource if needed.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun buildMediaSource(
    context: android.content.Context,
    result: YouTubeStreamResult
): MediaSource? {
    // Try standard HTTP data source first with matching User-Agent
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(result.userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)

    // Adaptive: separate video + audio streams merged together
    if (result.videoUrl != null && result.audioUrl != null) {
        Log.d("TrailerPlayer", "Building MergingMediaSource (video+audio)")
        val videoSource = ProgressiveMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(result.videoUrl))
        val audioSource = ProgressiveMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(result.audioUrl))
        return MergingMediaSource(videoSource, audioSource)
    }

    // Progressive: muxed video+audio
    if (result.progressiveUrl != null) {
        Log.d("TrailerPlayer", "Building ProgressiveMediaSource (muxed)")
        return ProgressiveMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(result.progressiveUrl))
    }

    return null
}
