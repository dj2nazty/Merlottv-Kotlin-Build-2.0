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
import androidx.media3.ui.PlayerView
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.local.WatchProgressDataStore
import com.merlottv.kotlin.data.repository.SubtitleRepository
import com.merlottv.kotlin.domain.model.Subtitle
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Subtitle state
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var subtitles by remember { mutableStateOf<List<Subtitle>>(emptyList()) }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var selectedSubtitleLang by remember { mutableStateOf("eng") }
    var subtitleSize by remember { mutableStateOf(1.0f) }
    var subtitleFont by remember { mutableStateOf("default") }
    var activeSubtitle by remember { mutableStateOf<Subtitle?>(null) }

    val watchProgressStore = remember {
        try { WatchProgressDataStore(context.applicationContext) } catch (_: Exception) { null }
    }
    val settingsStore = remember {
        try { SettingsDataStore(context.applicationContext) } catch (_: Exception) { null }
    }
    val subtitleRepo = remember { SubtitleRepository(
        okhttp3.OkHttpClient.Builder().build(),
        com.squareup.moshi.Moshi.Builder().build()
    ) }

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

        // Fetch subtitles in background
        if (contentId.isNotEmpty()) {
            val fetchedSubs = withContext(Dispatchers.IO) {
                subtitleRepo.getSubtitles(contentType, contentId)
            }
            subtitles = fetchedSubs

            // Auto-apply subtitle if enabled
            if (subtitlesEnabled && fetchedSubs.isNotEmpty()) {
                val preferred = fetchedSubs.firstOrNull { it.lang == selectedSubtitleLang }
                    ?: fetchedSubs.firstOrNull { it.lang.startsWith("eng") }
                    ?: fetchedSubs.first()
                applySubtitle(player, preferred, streamUrl)
                activeSubtitle = preferred
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
                    // Save progress every ~30 seconds (10 cycles × 3s)
                    saveCounter++
                    if (saveCounter >= 10) {
                        saveCounter = 0
                        val id = contentId.ifEmpty { streamUrl.hashCode().toString() }
                        watchProgressStore?.saveProgress(
                            id, currentPosition, totalDuration, title, poster, contentType
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Save progress on dispose using GlobalScope so it survives composable disposal
    DisposableEffect(Unit) {
        onDispose {
            val pos = player.currentPosition
            val dur = player.duration.coerceAtLeast(0)
            val id = contentId.ifEmpty { streamUrl.hashCode().toString() }
            // Use runBlocking to ensure save completes before player is released
            try {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    watchProgressStore?.saveProgress(id, pos, dur, title, poster, contentType)
                }
            } catch (_: Exception) {}
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
                            if (showSubtitleMenu) {
                                // Let subtitle menu handle it
                                false
                            } else {
                                if (player.isPlaying) player.pause() else player.play()
                                showControls = true
                                true
                            }
                        }
                        Key.Back -> {
                            if (showSubtitleMenu) {
                                showSubtitleMenu = false
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        Key.DirectionLeft -> {
                            if (!showSubtitleMenu) {
                                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                currentPosition = player.currentPosition
                                showControls = true
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (!showSubtitleMenu) {
                                val maxPos = player.duration.coerceAtLeast(0)
                                player.seekTo((player.currentPosition + 10_000).coerceAtMost(maxPos))
                                currentPosition = player.currentPosition
                                showControls = true
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (showControls && !showSubtitleMenu) {
                                // Open subtitle menu on D-pad Up when controls visible
                                showSubtitleMenu = true
                            }
                            showControls = true
                            true
                        }
                        Key.DirectionDown -> {
                            if (showSubtitleMenu) {
                                // Close subtitle menu on D-pad Down
                                showSubtitleMenu = false
                            }
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
            SubtitleMenuPanel(
                subtitles = subtitles,
                enabled = subtitlesEnabled,
                selectedLang = selectedSubtitleLang,
                currentSize = subtitleSize,
                currentFont = subtitleFont,
                activeSubtitle = activeSubtitle,
                isLoading = subtitles.isEmpty() && contentId.isNotEmpty(),
                onToggle = { enabled ->
                    subtitlesEnabled = enabled
                    scope.launch { settingsStore?.setSubtitlesEnabled(enabled) }
                    if (!enabled) {
                        // Disable subtitles
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        activeSubtitle = null
                    } else if (subtitles.isNotEmpty()) {
                        val preferred = subtitles.firstOrNull { it.lang == selectedSubtitleLang }
                            ?: subtitles.first()
                        applySubtitle(player, preferred, streamUrl)
                        activeSubtitle = preferred
                    }
                },
                onSelectLanguage = { lang ->
                    selectedSubtitleLang = lang
                    scope.launch { settingsStore?.setSubtitleLanguage(lang) }
                    val sub = subtitles.firstOrNull { it.lang == lang }
                    if (sub != null && subtitlesEnabled) {
                        applySubtitle(player, sub, streamUrl)
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

        // Subtitle size indicator at bottom when active
        if (subtitlesEnabled && activeSubtitle != null && showControls) {
            Text(
                text = "CC: ${activeSubtitle!!.label} (${(subtitleSize * 100).toInt()}%)",
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
private fun SubtitleMenuPanel(
    subtitles: List<Subtitle>,
    enabled: Boolean,
    selectedLang: String,
    currentSize: Float,
    currentFont: String,
    activeSubtitle: Subtitle?,
    isLoading: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSizeChange: (Float) -> Unit,
    onFontChange: (String) -> Unit,
    onClose: () -> Unit
) {
    // Get unique languages
    val languages = subtitles.map { it.lang }.distinct().sorted()

    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxSize()
            .background(MerlotColors.Black.copy(alpha = 0.85f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Subtitles", color = MerlotColors.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("D-pad \u2193 to close", color = MerlotColors.TextMuted, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (isLoading) {
            Text("Loading subtitles...", color = MerlotColors.TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        } else if (subtitles.isEmpty()) {
            Text("No subtitles available for this content", color = MerlotColors.TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    if (isSelected) Text("✓", color = MerlotColors.Accent, fontSize = 12.sp)
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
                    if (isSelected) Text("✓", color = MerlotColors.Accent, fontSize = 12.sp)
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
                        if (isSelected) Text("✓", color = MerlotColors.Accent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Apply a subtitle to the ExoPlayer by rebuilding the MediaItem with a subtitle track.
 */
private fun applySubtitle(player: ExoPlayer, subtitle: Subtitle, streamUrl: String) {
    val currentPos = player.currentPosition
    // Detect MIME type from URL or default to SRT
    val mimeType = when {
        subtitle.url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        subtitle.url.contains(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
        subtitle.url.contains(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
        subtitle.url.contains(".ttml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP  // Default: SRT (most common from OpenSubtitles)
    }
    val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
        .setMimeType(mimeType)
        .setLanguage(subtitle.lang)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()

    val newMediaItem = MediaItem.Builder()
        .setUri(Uri.parse(streamUrl))
        .setSubtitleConfigurations(listOf(subtitleConfig))
        .build()

    player.setMediaItem(newMediaItem)
    player.prepare()
    player.seekTo(currentPos)
    player.playWhenReady = true

    // Enable text track
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setPreferredTextLanguage(subtitle.lang)
        .build()
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
