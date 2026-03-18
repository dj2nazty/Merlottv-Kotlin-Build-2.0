package com.merlottv.kotlin.ui.components

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.PlayerView
import com.merlottv.kotlin.data.trailer.TrailerService
import com.merlottv.kotlin.ui.theme.MerlotColors

/**
 * Lightweight inline trailer player for poster card previews.
 * - Muted, no controls
 * - Fade-in on first frame rendered
 * - Minimal buffer (low memory)
 * - Player lifecycle tied to trailerResult key — properly disposes when card changes
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun InlineTrailerPlayer(
    trailerResult: TrailerService.TrailerStreamResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFirstFrameRendered by remember(trailerResult.streamUrl) { mutableStateOf(false) }

    val fadeAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "trailerFadeIn"
    )

    // Key the player to the stream URL — ensures proper cleanup when content changes
    val player = remember(trailerResult.streamUrl) {
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(1_500, 8_000, 500, 1_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip")

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .build()
            .apply {
                volume = 1f
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    // Set up listener and prepare media — keyed to stream URL
    LaunchedEffect(trailerResult.streamUrl) {
        isFirstFrameRendered = false

        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }
        })

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip")

        if (trailerResult.isProgressive) {
            val source = ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(trailerResult.streamUrl))
            player.setMediaSource(source)
        } else if (trailerResult.hlsUrl != null) {
            val source = HlsMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(trailerResult.hlsUrl))
            player.setMediaSource(source)
        } else if (trailerResult.audioUrl != null) {
            val videoSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(trailerResult.streamUrl))
            val audioSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(trailerResult.audioUrl))
            player.setMediaSource(MergingMediaSource(videoSource, audioSource))
        } else {
            val source = ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(trailerResult.streamUrl))
            player.setMediaSource(source)
        }

        player.prepare()
        player.play()
    }

    // Release player when this composable leaves composition — keyed to stream URL
    DisposableEffect(trailerResult.streamUrl) {
        onDispose {
            try {
                player.stop()
                player.release()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MerlotColors.Black)
        )

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                // Ensure player view always points to current player
                view.player = player
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(fadeAlpha)
        )
    }
}
