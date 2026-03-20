package com.merlottv.kotlin.ui.screens.player

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.local.WatchProgressDataStore
import com.merlottv.kotlin.data.repository.SkipIntroRepository
import com.merlottv.kotlin.data.repository.SubtitleRepository
import com.merlottv.kotlin.data.sync.CloudSyncManager
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.Stream
import com.merlottv.kotlin.domain.model.Subtitle
import com.merlottv.kotlin.ui.theme.MerlotColors
import com.merlottv.kotlin.core.player.FrameRateUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerSyncEntryPoint {
    fun cloudSyncManager(): CloudSyncManager
    fun profileDataStore(): ProfileDataStore
}

private val FocusedGrey = Color(0xFF666666)

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

    // Keep screen awake while video is playing
    val activity = context as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }

    // Intro video overlay — shown until the stream is actually ready to play
    var playerReady by remember { mutableStateOf(false) }
    var introVideoView by remember { mutableStateOf<android.widget.VideoView?>(null) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Subtitle state
    val panelFocusRequester = remember { FocusRequester() }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var subtitles by remember { mutableStateOf<List<Subtitle>>(emptyList()) }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var selectedSubtitleLang by remember { mutableStateOf("eng") }
    var subtitleSize by remember { mutableStateOf(1.0f) }
    var subtitleFont by remember { mutableStateOf("default") }
    var activeSubtitle by remember { mutableStateOf<Subtitle?>(null) }

    // Stream selection state
    var availableStreams by remember { mutableStateOf<List<Stream>>(emptyList()) }
    var currentStreamUrl by remember { mutableStateOf(streamUrl) }
    var isLoadingStreams by remember { mutableStateOf(false) }

    // Next episode auto-play state
    var showNextEpisode by remember { mutableStateOf(false) }
    var nextEpisodeInfo by remember { mutableStateOf<NextEpisodeInfo?>(null) }
    var nextEpisodeAutoPlayEnabled by remember { mutableStateOf(true) }
    var nextEpisodeThreshold by remember { mutableStateOf(95) }

    // Skip intro state
    var skipIntervals by remember { mutableStateOf<List<SkipInterval>>(emptyList()) }
    var activeSkipInterval by remember { mutableStateOf<SkipInterval?>(null) }
    var showSkipButton by remember { mutableStateOf(false) }
    var dismissedSkipTypes by remember { mutableStateOf<Set<SkipType>>(emptySet()) }

    val watchProgressStore = remember {
        try { WatchProgressDataStore(context.applicationContext) } catch (_: Exception) { null }
    }
    val settingsStore = remember {
        try { SettingsDataStore(context.applicationContext) } catch (_: Exception) { null }
    }
    val syncEntryPoint = remember {
        try { EntryPointAccessors.fromApplication(context.applicationContext, PlayerSyncEntryPoint::class.java) } catch (_: Exception) { null }
    }
    val subtitleRepo = remember { SubtitleRepository(
        okhttp3.OkHttpClient.Builder().build(),
        com.squareup.moshi.Moshi.Builder().build()
    ) }
    val skipIntroRepo = remember { SkipIntroRepository(context.applicationContext) }

    // Auto frame rate matching — detect video fps and switch display refresh rate
    val frameRateMode = remember { mutableStateOf("off") }
    LaunchedEffect(Unit) {
        val mode = try { settingsStore?.frameRateMatching?.first() } catch (_: Exception) { null }
        frameRateMode.value = mode ?: "off"
    }
    LaunchedEffect(streamUrl, frameRateMode.value) {
        if (frameRateMode.value == "off" || activity == null) return@LaunchedEffect
        try {
            val detection = FrameRateUtils.detectFrameRate(Uri.parse(streamUrl))
            if (detection != null) {
                FrameRateUtils.matchFrameRate(activity, detection.snapped)
            }
        } catch (_: Exception) {}
    }
    // Restore frame rate on exit if mode is START_STOP
    DisposableEffect(frameRateMode.value) {
        onDispose {
            if (frameRateMode.value == "start_stop" && activity != null) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                    try { FrameRateUtils.restoreOriginalMode(activity) } catch (_: Exception) {}
                }
            }
        }
    }

    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(2_500, 15_000, 800, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("MerlotTV/2.18.0 (Android)")

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
                            playerReady = true
                        }
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // Stream failed — dismiss intro so user sees the error state
                        playerReady = true
                    }
                })
            }
    }

    // MediaSession — exposes playback state to system media controls (remote, Google Assistant)
    val mediaSession = remember(player) {
        MediaSession.Builder(context, player)
            .setId("merlot_vod_${contentId.hashCode()}")
            .build()
    }

    // Load subtitle settings + fetch subtitles
    LaunchedEffect(contentId) {
        // Load settings
        if (settingsStore != null) {
            subtitlesEnabled = settingsStore.subtitlesEnabled.first()
            selectedSubtitleLang = settingsStore.subtitleLanguage.first()
            subtitleSize = settingsStore.subtitleSize.first()
            subtitleFont = settingsStore.subtitleFont.first()
        }

        // Resume from saved position
        if (contentId.isNotEmpty() && watchProgressStore != null) {
            val savedPos = watchProgressStore.getPosition(contentId)
            if (savedPos > 0) player.seekTo(savedPos)
        }

        // Fetch subtitles and available streams in background
        if (contentId.isNotEmpty()) {
            // Fetch subtitles
            val fetchedSubs = withContext(Dispatchers.IO) {
                subtitleRepo.getSubtitles(contentType, contentId)
            }
            subtitles = fetchedSubs

            // Auto-apply subtitle if enabled
            if (subtitlesEnabled && fetchedSubs.isNotEmpty()) {
                val preferred = fetchedSubs.firstOrNull { it.lang == selectedSubtitleLang }
                    ?: fetchedSubs.firstOrNull { it.lang.startsWith("eng") }
                    ?: fetchedSubs.first()
                applySubtitle(player, preferred, currentStreamUrl)
                activeSubtitle = preferred
            }

            // Fetch available streams for stream switching
            isLoadingStreams = true
            val fetchedStreams = withContext(Dispatchers.IO) {
                fetchStreamsForContent(contentType, contentId)
            }
            availableStreams = fetchedStreams
            isLoadingStreams = false
        }
    }

    // Load skip intro intervals when duration becomes available
    LaunchedEffect(totalDuration, contentId, contentType) {
        if (totalDuration <= 0 || contentId.isEmpty()) return@LaunchedEffect
        if (contentType == "series") {
            // Parse season/episode from contentId if possible (format: "tt1234567:1:3" = imdb:season:episode)
            val parts = contentId.split(":")
            val seriesId = parts.getOrNull(0) ?: contentId
            val season = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val episode = parts.getOrNull(2)?.toIntOrNull() ?: 1
            skipIntervals = skipIntroRepo.getSkipIntervals(
                seriesId = seriesId,
                season = season,
                episode = episode,
                totalDurationMs = totalDuration
            )
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Move focus into the options panel when it opens
    LaunchedEffect(showSubtitleMenu) {
        if (showSubtitleMenu) {
            delay(150) // Wait for panel to render
            try { panelFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls && !showSubtitleMenu) {
            delay(4000)
            showControls = false
        }
    }

    // Track position periodically + save progress every 30 seconds
    LaunchedEffect(player) {
        var saveCounter = 0
        while (true) {
            delay(3000)
            try {
                if (player.isPlaying) {
                    currentPosition = player.currentPosition
                    totalDuration = player.duration.coerceAtLeast(0)
                    // Check next episode threshold
                    if (nextEpisodeAutoPlayEnabled && !showNextEpisode &&
                        contentType == "series" && totalDuration > 0 && nextEpisodeInfo != null
                    ) {
                        val percent = (currentPosition.toFloat() / totalDuration * 100).toInt()
                        if (percent >= nextEpisodeThreshold) {
                            showNextEpisode = true
                        }
                    }
                    // Check skip intro intervals
                    if (skipIntervals.isNotEmpty()) {
                        val matchingInterval = skipIntervals.firstOrNull { interval ->
                            currentPosition in interval.startMs..interval.endMs &&
                                interval.type !in dismissedSkipTypes
                        }
                        if (matchingInterval != null && activeSkipInterval?.type != matchingInterval.type) {
                            activeSkipInterval = matchingInterval
                            showSkipButton = true
                        } else if (matchingInterval == null && showSkipButton) {
                            showSkipButton = false
                            activeSkipInterval = null
                        }
                    }
                    // Save progress every ~30 seconds (10 cycles × 3s)
                    saveCounter++
                    if (saveCounter >= 10) {
                        saveCounter = 0
                        val id = contentId.ifEmpty { streamUrl.hashCode().toString() }
                        watchProgressStore?.saveProgress(
                            id, currentPosition, totalDuration, title, poster, contentType
                        )
                        // Sync watch progress to cloud
                        try {
                            val pid = syncEntryPoint?.profileDataStore()?.getActiveProfileId() ?: "default"
                            syncEntryPoint?.cloudSyncManager()?.notifyWatchProgressChanged(pid)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Save progress on dispose — release player immediately, save async via GlobalScope
    DisposableEffect(Unit) {
        onDispose {
            val pos = player.currentPosition
            val dur = player.duration.coerceAtLeast(0)
            val id = contentId.ifEmpty { streamUrl.hashCode().toString() }
            // Release intro video — stopPlayback + suspend to free hardware codec
            try {
                introVideoView?.stopPlayback()
                introVideoView?.suspend()  // Releases underlying MediaPlayer + codec
                introVideoView = null
            } catch (_: Exception) {}
            mediaSession.release()
            player.release()
            // Save progress async — survives composable disposal via GlobalScope
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    watchProgressStore?.saveProgress(id, pos, dur, title, poster, contentType)
                    val pid = syncEntryPoint?.profileDataStore()?.getActiveProfileId() ?: "default"
                    syncEntryPoint?.cloudSyncManager()?.notifyWatchProgressChanged(pid)
                } catch (_: Exception) {}
            }
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
                    // When player options panel is open, only handle Back key here
                    // Let ALL other keys (Up/Down/Enter/Left/Right) pass through to the panel
                    if (showSubtitleMenu) {
                        when (event.key) {
                            Key.Back -> {
                                showSubtitleMenu = false
                                // Return focus to the main player box
                                scope.launch {
                                    kotlinx.coroutines.delay(100)
                                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                                }
                                true
                            }
                            else -> false // Let panel handle all navigation
                        }
                    } else {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                if (player.isPlaying) player.pause() else player.play()
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
                            Key.DirectionUp -> {
                                if (showControls) {
                                    showSubtitleMenu = true
                                }
                                showControls = true
                                true
                            }
                            Key.DirectionDown -> {
                                showControls = true
                                true
                            }
                            else -> false
                        }
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
                    // Hide buffering spinner while intro plays; show it for mid-playback rebuffer
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    subtitleView?.apply {
                        setUserDefaultTextSize()
                    }
                }
            },
            update = { playerView ->
                playerView.subtitleView?.apply {
                    val typeface = when (subtitleFont) {
                        "monospace" -> android.graphics.Typeface.MONOSPACE
                        "serif" -> android.graphics.Typeface.SERIF
                        "sans-serif" -> android.graphics.Typeface.SANS_SERIF
                        else -> android.graphics.Typeface.DEFAULT
                    }
                    val style = androidx.media3.ui.CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.argb(128, 0, 0, 0),
                        android.graphics.Color.TRANSPARENT,
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        android.graphics.Color.BLACK,
                        typeface
                    )
                    setStyle(style)
                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f * subtitleSize)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Branded intro video overlay ──────────────────────────────────
        // Plays merlot_player_intro.mp4 on loop until the stream is buffered
        // and ready. Covers the black screen / buffering state entirely.
        AnimatedVisibility(
            visible = !playerReady,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val uri = android.net.Uri.parse(
                                "android.resource://${ctx.packageName}/${com.merlottv.kotlin.R.raw.merlot_player_intro}"
                            )
                            setVideoURI(uri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                mp.start()
                            }
                            setOnErrorListener { _, _, _ -> true }
                            introVideoView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Release intro video once stream is ready — free hardware codec immediately
        LaunchedEffect(playerReady) {
            if (playerReady) {
                delay(500) // Let fade-out animation finish
                try {
                    introVideoView?.stopPlayback()
                    introVideoView?.suspend()
                    introVideoView = null
                } catch (_: Exception) {}
            }
        }

        // Timeout: if stream isn't ready in 30s, dismiss intro anyway
        LaunchedEffect(Unit) {
            delay(30_000)
            if (!playerReady) {
                playerReady = true
            }
        }

        // Pause icon overlay
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
                Icon(Icons.Default.PlayArrow, "Play", tint = MerlotColors.White, modifier = Modifier.size(48.dp))
            }
        }

        // Top bar with subtitle toggle
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MerlotColors.White, modifier = Modifier.size(28.dp))
                }
                if (title.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, color = MerlotColors.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Subtitle toggle button — always visible
                IconButton(onClick = { showSubtitleMenu = !showSubtitleMenu }) {
                    Icon(
                        imageVector = if (subtitlesEnabled && subtitles.isNotEmpty()) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                        contentDescription = "Subtitles (D-pad Up)",
                        tint = when {
                            subtitlesEnabled && subtitles.isNotEmpty() -> MerlotColors.Accent
                            subtitles.isEmpty() -> MerlotColors.TextMuted.copy(alpha = 0.5f)
                            else -> MerlotColors.White
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MerlotColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        // Subtitle menu overlay
        AnimatedVisibility(
            visible = showSubtitleMenu,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PlayerOptionsPanel(
                panelFocusRequester = panelFocusRequester,
                subtitles = subtitles,
                enabled = subtitlesEnabled,
                selectedLang = selectedSubtitleLang,
                currentSize = subtitleSize,
                currentFont = subtitleFont,
                activeSubtitle = activeSubtitle,
                isLoading = subtitles.isEmpty() && contentId.isNotEmpty(),
                availableStreams = availableStreams,
                currentStreamUrl = currentStreamUrl,
                isLoadingStreams = isLoadingStreams,
                onToggle = { enabled ->
                    subtitlesEnabled = enabled
                    scope.launch { settingsStore?.setSubtitlesEnabled(enabled) }
                    if (!enabled) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        activeSubtitle = null
                    } else if (subtitles.isNotEmpty()) {
                        val preferred = subtitles.firstOrNull { it.lang == selectedSubtitleLang }
                            ?: subtitles.first()
                        applySubtitle(player, preferred, currentStreamUrl)
                        activeSubtitle = preferred
                    }
                },
                onSelectLanguage = { lang ->
                    selectedSubtitleLang = lang
                    scope.launch { settingsStore?.setSubtitleLanguage(lang) }
                    val sub = subtitles.firstOrNull { it.lang == lang }
                    if (sub != null && subtitlesEnabled) {
                        applySubtitle(player, sub, currentStreamUrl)
                        activeSubtitle = sub
                    }
                },
                onSizeChange = { size ->
                    subtitleSize = size
                    scope.launch { settingsStore?.setSubtitleSize(size) }
                },
                onFontChange = { font ->
                    subtitleFont = font
                    scope.launch { settingsStore?.setSubtitleFont(font) }
                },
                onSelectStream = { stream ->
                    val newUrl = stream.url.ifEmpty { stream.externalUrl }
                    if (newUrl.isNotEmpty() && newUrl != currentStreamUrl) {
                        currentStreamUrl = newUrl
                        playerReady = false // Show intro video while new stream buffers
                        val savedPos = player.currentPosition
                        player.stop()
                        player.clearMediaItems()
                        player.setMediaItem(MediaItem.fromUri(Uri.parse(newUrl)))
                        player.prepare()
                        player.seekTo(savedPos)
                        player.playWhenReady = true
                        // Re-apply subtitle if active
                        val sub = activeSubtitle
                        if (subtitlesEnabled && sub != null) {
                            applySubtitle(player, sub, newUrl)
                        }
                    }
                    showSubtitleMenu = false
                },
                onClose = { showSubtitleMenu = false }
            )
        }

        // Bottom progress bar
        AnimatedVisibility(
            visible = showControls && totalDuration > 0 && !showSubtitleMenu,
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
                        "\u25C0 10s    OK Pause    10s \u25B6    \u25B2 Subtitles",
                        color = MerlotColors.TextMuted.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                    Text(formatTime(totalDuration), color = MerlotColors.TextMuted, fontSize = 10.sp)
                }
            }
        }

        // Skip Intro button overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 140.dp)
        ) {
            SkipIntroButton(
                visible = showSkipButton,
                skipInterval = activeSkipInterval,
                onSkip = { endMs ->
                    showSkipButton = false
                    dismissedSkipTypes = dismissedSkipTypes + (activeSkipInterval?.type ?: SkipType.INTRO)
                    activeSkipInterval = null
                    player.seekTo(endMs)
                },
                onDismiss = {
                    showSkipButton = false
                    dismissedSkipTypes = dismissedSkipTypes + (activeSkipInterval?.type ?: SkipType.INTRO)
                    activeSkipInterval = null
                }
            )
        }

        // Next episode auto-play overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
        ) {
            NextEpisodeCardOverlay(
                visible = showNextEpisode,
                episodeInfo = nextEpisodeInfo,
                onPlayNow = {
                    showNextEpisode = false
                    // Navigate to next episode — save current progress first
                    val id = contentId.ifEmpty { streamUrl.hashCode().toString() }
                    scope.launch(Dispatchers.IO) {
                        try {
                            watchProgressStore?.saveProgress(
                                id, player.currentPosition,
                                player.duration.coerceAtLeast(0),
                                title, poster, contentType
                            )
                        } catch (_: Exception) {}
                    }
                    // Play next episode URL if available
                    nextEpisodeInfo?.streamUrl?.let { nextUrl ->
                        player.stop()
                        val mediaItem = MediaItem.fromUri(Uri.parse(nextUrl))
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    }
                },
                onDismiss = { showNextEpisode = false }
            )
        }

        // Subtitle size indicator at bottom when active
        if (subtitlesEnabled && activeSubtitle != null && showControls) {
            Text(
                text = "CC: ${activeSubtitle?.label ?: "CC"} (${(subtitleSize * 100).toInt()}%)",
                color = MerlotColors.Accent.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp, bottom = 60.dp)
            )
        }
    }
}

@Composable
private fun PlayerOptionsPanel(
    panelFocusRequester: FocusRequester = remember { FocusRequester() },
    subtitles: List<Subtitle>,
    enabled: Boolean,
    selectedLang: String,
    currentSize: Float,
    currentFont: String,
    activeSubtitle: Subtitle?,
    isLoading: Boolean = false,
    availableStreams: List<Stream> = emptyList(),
    currentStreamUrl: String = "",
    isLoadingStreams: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSizeChange: (Float) -> Unit,
    onFontChange: (String) -> Unit,
    onSelectStream: (Stream) -> Unit = {},
    onClose: () -> Unit
) {
    // Get unique languages
    val languages = subtitles.map { it.lang }.distinct().sorted()

    // Tab state: 0 = Subtitles, 1 = Streams
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxSize()
            .background(MerlotColors.Black.copy(alpha = 0.90f))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Player Options", color = MerlotColors.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("D-pad \u2193 to close", color = MerlotColors.TextMuted, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Tab selector: Subtitles | Streams
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Subtitles tab
            var subTabFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedTab == 0) MerlotColors.Accent.copy(alpha = 0.25f) else Color.Transparent)
                    .then(
                        if (subTabFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                        else if (selectedTab == 0) Modifier.border(1.dp, MerlotColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .focusRequester(panelFocusRequester)
                    .onFocusChanged { subTabFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            selectedTab = 0; true
                        } else false
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Subtitles",
                    color = if (selectedTab == 0) MerlotColors.Accent else MerlotColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Streams tab
            var streamTabFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedTab == 1) MerlotColors.Accent.copy(alpha = 0.25f) else Color.Transparent)
                    .then(
                        if (streamTabFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                        else if (selectedTab == 1) Modifier.border(1.dp, MerlotColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .onFocusChanged { streamTabFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            selectedTab = 1; true
                        } else false
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                val streamCount = if (availableStreams.isNotEmpty()) " (${availableStreams.size})" else ""
                Text(
                    "Streams$streamCount",
                    color = if (selectedTab == 1) MerlotColors.Accent else MerlotColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            // ─── SUBTITLES TAB ───
            if (isLoading) {
                Text("Loading subtitles...", color = MerlotColors.TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            } else if (subtitles.isEmpty()) {
                Text("No subtitles available", color = MerlotColors.TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // On/Off toggle
            var toggleFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (toggleFocused) FocusedGrey.copy(alpha = 0.3f) else Color.Transparent)
                    .then(if (toggleFocused) Modifier.border(2.dp, FocusedGrey, RoundedCornerShape(8.dp)) else Modifier)
                    .onFocusChanged { toggleFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            onToggle(!enabled); true
                        } else false
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Subtitles", color = MerlotColors.White, fontSize = 14.sp)
                Text(
                    if (enabled) "ON" else "OFF",
                    color = if (enabled) MerlotColors.Accent else MerlotColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(8.dp))

                // Size controls
                Text("Size", color = MerlotColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                val sizes = listOf(0.75f to "Small", 1.0f to "Normal", 1.25f to "Large", 1.5f to "Extra Large")
                sizes.forEach { (size, label) ->
                    val isSelected = (currentSize - size).let { kotlin.math.abs(it) } < 0.05f
                    var isFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isFocused -> FocusedGrey.copy(alpha = 0.3f)
                                    isSelected -> MerlotColors.Accent.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                }
                            )
                            .then(if (isFocused) Modifier.border(1.dp, FocusedGrey, RoundedCornerShape(6.dp)) else Modifier)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                                    onSizeChange(size); true
                                } else false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, color = if (isSelected) MerlotColors.Accent else MerlotColors.White, fontSize = 12.sp)
                        if (isSelected) Text("\u2713", color = MerlotColors.Accent, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Font controls
                Text("Font", color = MerlotColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                val fonts = listOf("default" to "Default", "monospace" to "Monospace", "serif" to "Serif", "sans-serif" to "Sans Serif")
                fonts.forEach { (fontKey, fontLabel) ->
                    val isSelected = currentFont == fontKey
                    var isFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isFocused -> FocusedGrey.copy(alpha = 0.3f)
                                    isSelected -> MerlotColors.Accent.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                }
                            )
                            .then(if (isFocused) Modifier.border(1.dp, FocusedGrey, RoundedCornerShape(6.dp)) else Modifier)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                                    onFontChange(fontKey); true
                                } else false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(fontLabel, color = if (isSelected) MerlotColors.Accent else MerlotColors.White, fontSize = 12.sp)
                        if (isSelected) Text("\u2713", color = MerlotColors.Accent, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Language selection
                Text("Language", color = MerlotColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                LazyColumn {
                    items(languages) { lang ->
                        val isSelected = lang == selectedLang
                        var isFocused by remember { mutableStateOf(false) }
                        val label = SubtitleRepository.getLanguageLabel(lang)
                        val count = subtitles.count { it.lang == lang }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isFocused -> FocusedGrey.copy(alpha = 0.3f)
                                        isSelected -> MerlotColors.Accent.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(if (isFocused) Modifier.border(1.dp, FocusedGrey, RoundedCornerShape(6.dp)) else Modifier)
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                                        onSelectLanguage(lang); true
                                    } else false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "$label ($count)",
                                color = if (isSelected) MerlotColors.Accent else MerlotColors.White,
                                fontSize = 12.sp
                            )
                            if (isSelected) Text("\u2713", color = MerlotColors.Accent, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // ─── STREAMS TAB ───
            if (isLoadingStreams) {
                Text("Loading streams...", color = MerlotColors.TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            } else if (availableStreams.isEmpty()) {
                Text("No alternative streams found", color = MerlotColors.TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    "${availableStreams.size} streams available",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn {
                    items(availableStreams) { stream ->
                        val streamPlayUrl = stream.url.ifEmpty { stream.externalUrl }
                        val isCurrentStream = streamPlayUrl == currentStreamUrl
                        var isFocused by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isFocused -> FocusedGrey.copy(alpha = 0.3f)
                                        isCurrentStream -> MerlotColors.Accent.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                                    else if (isCurrentStream) Modifier.border(1.dp, MerlotColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                                        if (!isCurrentStream) onSelectStream(stream)
                                        true
                                    } else false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // Stream name (quality/source info)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stream.name.ifEmpty { stream.addonName },
                                    color = if (isCurrentStream) MerlotColors.Accent else MerlotColors.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrentStream) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrentStream) {
                                    Text(
                                        "PLAYING",
                                        color = MerlotColors.Accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Stream title (often contains quality, size, codec details)
                            if (stream.title.isNotEmpty()) {
                                Text(
                                    text = stream.title.take(120),
                                    color = MerlotColors.TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 2
                                )
                            }

                            // Addon source label
                            if (stream.addonName.isNotEmpty() && stream.name.isNotEmpty()) {
                                Text(
                                    text = stream.addonName,
                                    color = MerlotColors.Accent.copy(alpha = 0.6f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Apply a subtitle to the ExoPlayer by rebuilding the MediaItem with a subtitle track.
 * Key: set track selection parameters BEFORE prepare() so ExoPlayer selects text tracks
 * during preparation, not after.
 */
private fun applySubtitle(player: ExoPlayer, subtitle: Subtitle, streamUrl: String) {
    val currentPos = player.currentPosition
    val wasPlaying = player.isPlaying

    // Step 1: Enable text track rendering BEFORE setting the new media item
    // This ensures ExoPlayer's track selector knows to select text tracks during preparation
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setPreferredTextLanguage(subtitle.lang)
        .build()

    // Step 2: Detect MIME type from URL
    val mimeType = when {
        subtitle.url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        subtitle.url.contains(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
        subtitle.url.contains(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
        subtitle.url.contains(".ttml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP  // Default: SRT (most common from OpenSubtitles)
    }

    // Step 3: Build subtitle configuration with FORCED flag to ensure selection
    val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
        .setMimeType(mimeType)
        .setLanguage(subtitle.lang)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
        .setLabel(subtitle.label)
        .build()

    // Step 4: Build new MediaItem with subtitle configuration
    val newMediaItem = MediaItem.Builder()
        .setUri(Uri.parse(streamUrl))
        .setSubtitleConfigurations(listOf(subtitleConfig))
        .build()

    // Step 5: Stop, set media, prepare, seek (in correct order)
    player.stop()
    player.setMediaItem(newMediaItem)
    player.prepare()
    player.seekTo(currentPos)
    player.playWhenReady = wasPlaying || true
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

/**
 * Fetch streams from all installed Stremio addons for the given content.
 * Calls /stream/{type}/{id}.json on each addon that supports streams.
 */
@Suppress("UNCHECKED_CAST")
private fun fetchStreamsForContent(type: String, id: String): List<Stream> {
    val client = OkHttpClient.Builder()
        .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    val moshi = com.squareup.moshi.Moshi.Builder().build()
    val allStreams = mutableListOf<Stream>()

    for (addon in DefaultData.DEFAULT_ADDONS) {
        // Skip addons that only provide subtitles or catalogs (no streams)
        if (addon.resources.contains("subtitles") && addon.resources.size == 1) continue
        if (addon.id == "imdb-catalogs" || addon.id == "netflix-catalog") continue

        try {
            val baseUrl = addon.url.removeSuffix("/manifest.json")
            val url = "$baseUrl/stream/$type/$id.json"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) continue

            val body = response.body?.string() ?: continue
            val adapter = moshi.adapter(Map::class.java)
            val map = adapter.fromJson(body) as? Map<String, Any?> ?: continue
            val streams = map["streams"] as? List<Map<String, Any?>> ?: continue

            for (s in streams) {
                val streamUrl = s["url"] as? String ?: ""
                val streamName = s["name"] as? String ?: ""
                val streamTitle = s["title"] as? String ?: ""
                val ytId = s["ytId"] as? String ?: ""
                val infoHash = s["infoHash"] as? String ?: ""
                val fileIdx = (s["fileIdx"] as? Number)?.toInt()
                val externalUrl = s["externalUrl"] as? String ?: ""

                // Skip streams with no playable URL
                if (streamUrl.isEmpty() && externalUrl.isEmpty() && ytId.isEmpty() && infoHash.isEmpty()) continue

                allStreams.add(
                    Stream(
                        name = streamName,
                        title = streamTitle,
                        url = streamUrl,
                        ytId = ytId,
                        infoHash = infoHash,
                        fileIdx = fileIdx,
                        externalUrl = externalUrl,
                        addonName = addon.name,
                        addonLogo = ""
                    )
                )
            }
        } catch (_: Exception) {
            continue
        }
    }
    return allStreams
}
