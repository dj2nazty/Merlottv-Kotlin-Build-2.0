package com.merlottv.kotlin.ui.screens.player

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.PlayerView
import com.merlottv.kotlin.data.local.WatchProgressDataStore
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    streamUrl: String,
    title: String = "",
    contentId: String = "",
    poster: String = "",
    contentType: String = "movie",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val watchProgressStore = remember {
        try {
            WatchProgressDataStore(context.applicationContext)
        } catch (_: Exception) {
            null
        }
    }

    val player = remember {
        // Low-latency buffer config for fast playback start
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                /* minBufferMs */ 2_500,
                /* maxBufferMs */ 15_000,
                /* bufferForPlaybackMs */ 800,
                /* bufferForPlaybackAfterRebufferMs */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                setMediaItem(mediaItem)
                playWhenReady = true
                prepare()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            totalDuration = duration.coerceAtLeast(0)
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
            }
    }

    // Resume from saved position
    LaunchedEffect(contentId) {
        if (contentId.isNotEmpty() && watchProgressStore != null) {
            val savedPos = watchProgressStore.getPosition(contentId)
            if (savedPos > 0) {
                player.seekTo(savedPos)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // Track position periodically
    LaunchedEffect(player) {
        while (true) {
            delay(3000)
            try {
                if (player.isPlaying) {
                    currentPosition = player.currentPosition
                    totalDuration = player.duration.coerceAtLeast(0)
                }
            } catch (_: Exception) {}
        }
    }

    // Save progress and release on dispose
    DisposableEffect(Unit) {
        onDispose {
            val pos = player.currentPosition
            val dur = player.duration.coerceAtLeast(0)
            val id = contentId.ifEmpty { streamUrl.hashCode().toString() }
            scope.launch {
                try {
                    watchProgressStore?.saveProgress(
                        id = id,
                        position = pos,
                        duration = dur,
                        title = title,
                        poster = poster,
                        type = contentType
                    )
                } catch (_: Exception) {}
            }
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                            showControls = true
                            true
                        }
                        Key.Back -> {
                            onBack()
                            true
                        }
                        Key.DirectionLeft -> {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                            currentPosition = player.currentPosition
                            showControls = true
                            true
                        }
                        Key.DirectionRight -> {
                            val maxPos = player.duration.coerceAtLeast(0)
                            player.seekTo((player.currentPosition + 10_000).coerceAtMost(maxPos))
                            currentPosition = player.currentPosition
                            showControls = true
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            showControls = true
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // ExoPlayer view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Pause icon overlay (centered)
        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MerlotColors.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MerlotColors.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MerlotColors.Black.copy(alpha = 0.6f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MerlotColors.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (title.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = MerlotColors.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MerlotColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        // Bottom progress bar
        AnimatedVisibility(
            visible = showControls && totalDuration > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MerlotColors.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MerlotColors.Surface2)
                ) {
                    val progress = if (totalDuration > 0) {
                        (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(MerlotColors.Accent)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), color = MerlotColors.TextMuted, fontSize = 10.sp)
                    Text(
                        "\u25C0 10s    ENTER Pause    10s \u25B6",
                        color = MerlotColors.TextMuted.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                    Text(formatTime(totalDuration), color = MerlotColors.TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
