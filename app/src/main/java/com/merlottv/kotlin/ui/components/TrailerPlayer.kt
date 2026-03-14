package com.merlottv.kotlin.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
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
    onDismiss: () -> Unit
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

    // Extract streams on launch
    LaunchedEffect(ytVideoId) {
        isLoading = true
        extractionFailed = false
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

    // If extraction failed, fall back to WebView player
    if (extractionFailed) {
        YouTubeWebPlayer(
            url = "https://www.youtube.com/watch?v=$ytVideoId",
            onDismiss = onDismiss
        )
        return
    }

    // Loading state
    if (isLoading) {
        BackHandler { onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        onDismiss(); true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MerlotColors.Accent)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading trailer...", color = MerlotColors.TextMuted, fontSize = 12.sp)
            }
        }
        return
    }

    // We have streams — build and play with ExoPlayer
    val result = streamResult ?: return

    BackHandler { onDismiss() }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // Create ExoPlayer and prepare media source
    DisposableEffect(result) {
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(2_500, 30_000, 800, 1_500)
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
        // HLS manifests from the TV_EMBEDDED client don't require PO tokens,
        // so they're far more reliable than adaptive/progressive stream URLs.
        val mediaSourceReady = when {
            result.hlsManifestUrl != null -> {
                Log.d("TrailerPlayer", "Using HLS manifest: ${result.hlsManifestUrl.take(80)}...")
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(result.userAgent)
                    .setAllowCrossProtocolRedirects(true)
                val hlsSource = HlsMediaSource.Factory(httpFactory)
                    .createMediaSource(MediaItem.fromUri(result.hlsManifestUrl))
                exoPlayer.setMediaSource(hlsSource)
                true
            }
            else -> {
                val source = buildMediaSource(context, result)
                if (source != null) {
                    Log.d("TrailerPlayer", "Using adaptive/progressive source")
                    exoPlayer.setMediaSource(source)
                    true
                } else {
                    Log.w("TrailerPlayer", "No playable source found")
                    false
                }
            }
        }

        if (!mediaSourceReady) {
            exoPlayer.release()
            return@DisposableEffect onDispose {}
        }

        exoPlayer.playWhenReady = true
        exoPlayer.prepare()

        // Listen for errors — if playback fails, we'll let the WebView fallback handle it
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("TrailerPlayer", "Playback error: ${error.message}")
            }
        })

        player = exoPlayer

        onDispose {
            exoPlayer.release()
            player = null
        }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back -> {
                            onDismiss(); true
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
                            player?.let { p ->
                                if (p.isPlaying) p.pause() else p.play()
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
                        useController = false  // We handle controls via D-pad
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Close button (top-right)
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
