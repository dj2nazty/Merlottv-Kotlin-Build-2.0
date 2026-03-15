package com.merlottv.kotlin.ui.screens.livetv

import android.app.Application
import android.os.Process
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.merlottv.kotlin.data.local.SettingsDataStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import com.merlottv.kotlin.data.repository.BackupChannelRepository
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.domain.repository.ChannelRepository
import com.merlottv.kotlin.domain.repository.EpgRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import javax.inject.Inject

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    // null = no filter applied, use channels directly (avoids duplicating the full list in memory)
    private val _filteredChannels: List<Channel>? = null,
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val selectedChannel: Channel? = null,
    val searchQuery: String = "",
    val totalChannels: Int = 0,
    val favoriteIds: Set<String> = emptySet(),
    // Fullscreen mode
    val isFullscreen: Boolean = false,
    val showOverlay: Boolean = false,
    // EPG info for current channel
    val currentProgram: EpgEntry? = null,
    val nextProgram: EpgEntry? = null,
    // Video quality info
    val videoResolution: String = "",
    val videoFrameRate: String = "",
    // Current index in filtered list for channel up/down
    val currentChannelIndex: Int = -1,
    // Backup stream failover
    val isFailingOver: Boolean = false,
    val failoverMessage: String = "",
    // Category sidebar visibility
    val showCategories: Boolean = true,
    // Quick menu (OK button popup)
    val showQuickMenu: Boolean = false,
    // Last 3 watched channels (most recent first)
    val recentChannels: List<Channel> = emptyList(),
    // Stream source: which playlist/source is currently playing
    val streamSource: String = "",
    // Subtitles (embedded CC)
    val subtitlesEnabled: Boolean = false,
    // Rebuffer tracking — shows how many times the stream has rebuffered
    val rebufferCount: Int = 0,
    val isBuffering: Boolean = false,
    val lastRebufferDurationMs: Long = 0L,
    val totalRebufferMs: Long = 0L,
    // Bitrate info
    val videoBitrateKbps: Int = 0,
    // Player engine: "exo" or "vlc"
    val activePlayerEngine: String = "exo",
    val isUsingVlc: Boolean = false
) {
    /** Returns filtered channels if a filter is active, otherwise the full channel list */
    val filteredChannels: List<Channel> get() = _filteredChannels ?: channels

    /** Helper to set filteredChannels — pass null to clear filter and reuse channels */
    fun withFilteredChannels(filtered: List<Channel>?) = copy(_filteredChannels = filtered)
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    application: Application,
    private val channelRepository: ChannelRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsDataStore: SettingsDataStore,
    private val epgRepository: EpgRepository,
    private val backupChannelRepository: BackupChannelRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    // Failover tracking — searches all backup sources, not just 3
    private var failoverAttempts = 0
    private val triedStreamUrls = mutableSetOf<String>()
    private var isPlayerReleased = false
    private var failoverMessageJob: Job? = null

    // Track playlist names for source display
    private var playlistNames: Map<String, String> = emptyMap() // url → name

    // Rebuffer tracking state
    private var rebufferStartTime = 0L
    private var sessionRebufferCount = 0
    private var sessionTotalRebufferMs = 0L
    private var wasPlayingBeforeBuffer = false

    // ═══════════════════════════════════════════════════════════════════
    // Priority Connection Pre-Warm — keeps favorite channel connections hot
    // No background decoders (saves hardware codecs for the active player).
    // Pre-resolves DNS, opens TCP, fetches first 256KB of stream data so
    // when user switches to a favorite, the connection is already established
    // and ExoPlayer can start playing instantly from warm cache.
    // ═══════════════════════════════════════════════════════════════════
    companion object {
        private const val MAX_PREWARM_CHANNELS = 4
        private const val PREWARM_BYTES = 256 * 1024 // 256KB prefetch per channel
        private const val PREWARM_REFRESH_MS = 30_000L // Re-warm every 30s to keep connections alive
    }

    // channelId → active pre-warm job
    private val prewarmJobs = mutableMapOf<String, Job>()
    // channelId → streamUrl (to detect URL changes)
    private val prewarmChannelUrls = mutableMapOf<String, String>()
    // Track which channels have been pre-warmed for faster switch detection
    private val prewarmedChannelIds = mutableSetOf<String>()

    // ═══════════════════════════════════════════════════════════════════
    // VLC Fallback Player — Apollo-style dual engine
    // LibVLC handles codecs/containers that ExoPlayer can't (TS muxed,
    // non-standard HLS, MPEG-TS variants common in IPTV).
    // Auto-switches after ExoPlayer retries fail, or manual toggle.
    // ═══════════════════════════════════════════════════════════════════
    private var libVLC: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private var vlcBufferTimeout: Job? = null

    /** Initialize LibVLC with optimized options for live TV */
    private fun getOrCreateLibVLC(): LibVLC {
        return libVLC ?: LibVLC(getApplication(), arrayListOf(
            "--network-caching=2000",       // 2s network cache (VLC handles its own buffering)
            "--live-caching=2000",           // 2s live stream cache
            "--file-caching=1500",
            "--clock-jitter=0",             // Reduce A/V sync jitter
            "--clock-synchro=0",
            "--drop-late-frames",           // Drop frames instead of rebuffering
            "--skip-frames",
            "--avcodec-skiploopfilter=4",    // Skip deblocking filter for speed
            "--avcodec-skip-idct=4",
            "--avcodec-hurry-up",
            "--no-audio-time-stretch",      // Don't stretch audio when catching up
            "--no-sub-autodetect-file",     // Don't waste time on subtitle detection
            "--http-reconnect",             // Auto-reconnect on network drops
            "--sout-keep"                   // Keep stream output alive
        )).also { libVLC = it }
    }

    /** Play a stream URL using VLC instead of ExoPlayer */
    fun playWithVlc(streamUrl: String) {
        if (isPlayerReleased) return

        // Stop ExoPlayer
        try {
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) {}

        // Release previous VLC player if exists
        stopVlc()

        try {
            val lib = getOrCreateLibVLC()
            val media = Media(lib, android.net.Uri.parse(streamUrl))
            media.setHWDecoderEnabled(true, false)  // Prefer hardware decoding
            media.addOption(":network-caching=2000")

            val vPlayer = VlcMediaPlayer(lib)
            vPlayer.media = media
            media.release() // VLC copies internally

            vPlayer.setEventListener { event ->
                when (event.type) {
                    VlcMediaPlayer.Event.Playing -> {
                        Log.d("LiveTvVM", "VLC: Playing")
                        vlcBufferTimeout?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isBuffering = false,
                            isFailingOver = false,
                            failoverMessage = ""
                        )
                    }
                    VlcMediaPlayer.Event.Buffering -> {
                        val pct = event.buffering
                        Log.d("LiveTvVM", "VLC: Buffering ${pct}%")
                        if (pct < 100f) {
                            _uiState.value = _uiState.value.copy(isBuffering = true)
                        }
                    }
                    VlcMediaPlayer.Event.EncounteredError -> {
                        Log.e("LiveTvVM", "VLC: Playback error")
                        // VLC failed too — fall back to backup search
                        stopVlc()
                        switchToExoPlayer()
                        handlePlaybackError()
                    }
                    VlcMediaPlayer.Event.Stopped, VlcMediaPlayer.Event.EndReached -> {
                        Log.d("LiveTvVM", "VLC: Stream ended")
                    }
                }
            }

            vPlayer.play()
            vlcPlayer = vPlayer

            _uiState.value = _uiState.value.copy(
                activePlayerEngine = "vlc",
                isUsingVlc = true,
                isBuffering = true
            )
            Log.d("LiveTvVM", "VLC: Started playback for $streamUrl")
        } catch (e: Exception) {
            Log.e("LiveTvVM", "VLC: Failed to start", e)
            // VLC init failed — switch back to ExoPlayer
            switchToExoPlayer()
        }
    }

    /** Stop VLC player and release resources */
    private fun stopVlc() {
        vlcBufferTimeout?.cancel()
        try {
            vlcPlayer?.stop()
            vlcPlayer?.release()
        } catch (_: Exception) {}
        vlcPlayer = null
    }

    /** Switch back to ExoPlayer mode */
    private fun switchToExoPlayer() {
        stopVlc()
        _uiState.value = _uiState.value.copy(
            activePlayerEngine = "exo",
            isUsingVlc = false
        )
    }

    /** Get the VLC MediaPlayer instance (for attaching to SurfaceView in UI) */
    fun getVlcPlayer(): VlcMediaPlayer? = vlcPlayer

    /** Toggle between ExoPlayer and VLC for the current channel */
    fun togglePlayerEngine() {
        val currentChannel = _uiState.value.selectedChannel ?: return
        if (_uiState.value.isUsingVlc) {
            // Switch to ExoPlayer
            switchToExoPlayer()
            safePlayChannel(currentChannel)
        } else {
            // Switch to VLC
            playWithVlc(currentChannel.streamUrl)
        }
    }

    /**
     * Apollo-grade ExoPlayer configuration for live TV:
     *
     * v2.30.0: Apollo App reverse-engineered optimizations:
     *   - 10-MINUTE forward buffer (600s) — matches Apollo's 0x927C0ms exactly
     *   - Dynamic memory cap: Runtime.maxMemory()/2 — uses half of available heap
     *   - Size-over-time priority: fills bytes first, not time thresholds
     *   - 60s back-buffer for rewind capability (Apollo uses 0xEA60ms)
     *   - 2.5s playback start + rebuffer resume (Apollo uses 0x9C4ms)
     *   - EXTENSION_RENDERER_MODE_PREFER: hardware decoders first (Apollo uses mode 2)
     *   - Audio focus handling with AudioAttributes (Apollo sets USAGE_MEDIA + CONTENT_TYPE_MOVIE)
     *   - handleAudioBecomingNoisy: auto-pause on headphone disconnect
     *   - Live target offset: 5s behind edge (Apollo uses 0x1388ms)
     *
     * Previous versions (preserved):
     *   v2.25.0: BandwidthMeter, adjustable buffer, fast failover
     *   v2.25.1: User-configurable buffer slider
     *   v2.28.0: TiviMate buffer config (50s buffer, SSL bypass)
     *   v2.29.2: 90s buffer, 120MB cap
     */
    val player: ExoPlayer = run {
        // Read user's buffer preference (synchronous — only runs once at ViewModel creation)
        val userBufferMs = try {
            runBlocking { settingsDataStore.bufferDurationMs.first() }
        } catch (e: Exception) {
            Log.e("LiveTvVM", "Failed to read buffer setting, using default", e)
            1000
        }

        // BandwidthMeter tracks download speed and helps ExoPlayer pick optimal quality
        val bandwidthMeter = DefaultBandwidthMeter.Builder(application)
            .setResetOnNetworkTypeChange(true) // Re-estimate when WiFi ↔ cellular
            .build()

        // === Apollo-grade buffer configuration ===
        //
        // Apollo's zero-buffer secret (decoded from smali):
        //   - minBuffer = maxBuffer = 600,000ms (10 MINUTES)
        //   - bufferForPlaybackMs = 2,500ms (start after 2.5s buffer)
        //   - bufferForPlaybackAfterRebufferMs = 2,500ms (resume after 2.5s)
        //   - targetBufferBytes = Runtime.maxMemory() / 2 (HALF of heap)
        //   - prioritizeTimeOverSizeThresholds = false (fill bytes first)
        //   - backBuffer = 60,000ms (60s rewind)
        //
        // This means Apollo downloads up to 10 MINUTES of video ahead.
        // Even if internet dies for 9 minutes, playback continues.
        // The user's slider still controls startup speed (bufferForPlaybackMs).

        val steadyBuffer = 600_000      // 10 MINUTES — Apollo uses 0x927C0 (600,000ms)
        val playbackBuffer = userBufferMs.coerceAtLeast(500) // Slider controls startup (default 1000ms, min 500ms)
        val rebufferBuffer = 2_500      // 2.5s after rebuffer — Apollo uses 0x9C4 (2,500ms)

        // Dynamic memory cap: use HALF of available heap (Apollo's exact approach)
        // On 3GB TV with largeHeap: ~288MB for buffer alone
        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val targetBufferBytes = (maxHeapBytes / 2).toInt()
        Log.d("LiveTvVM", "Apollo buffer: heap=${maxHeapBytes/1024/1024}MB, buffer cap=${targetBufferBytes/1024/1024}MB, steady=${steadyBuffer/1000}s")

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 65_536)) // 64KB chunks
            .setBufferDurationsMs(
                /* minBufferMs */                      steadyBuffer,
                /* maxBufferMs */                      steadyBuffer,  // min=max for predictable behavior
                /* bufferForPlaybackMs */               playbackBuffer,
                /* bufferForPlaybackAfterRebufferMs */  rebufferBuffer
            )
            // === KEY APOLLO DIFFERENCE: Size over time ===
            // false = fill bytes first, then worry about time thresholds
            // This makes the buffer fill as fast as the connection allows
            .setPrioritizeTimeOverSizeThresholds(false)
            // Dynamic cap: half of available heap (Apollo's Runtime.maxMemory()/2)
            .setTargetBufferBytes(targetBufferBytes)
            // Back-buffer: retain 60s of played content (Apollo uses 0xEA60 = 60,000ms)
            .setBackBuffer(60_000, /* retainBackBufferFromKeyframe */ true)
            .build()

        // === SSL bypass for IPTV streams (TiviMate-style) ===
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = try {
            SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }
        } catch (e: Exception) {
            Log.w("LiveTvVM", "SSL context init failed, using default", e)
            null
        }
        val trustAllHostnames = HostnameVerifier { _, _ -> true }

        // HTTP data source with optimized timeouts
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(5_000)     // 5s connect
            .setReadTimeoutMs(8_000)        // 8s read
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(mapOf(
                "Connection" to "keep-alive",
                "Accept" to "*/*"
            ))
            .setTransferListener(bandwidthMeter)

        // Apply SSL bypass
        if (sslContext != null) {
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames)
        }

        val dataSourceFactory = DefaultDataSource.Factory(application, httpDataSourceFactory)

        // === Apollo-style MediaSourceFactory with 5s live offset ===
        // Apollo uses setLiveTargetOffsetMs(0x1388) = 5,000ms
        // Closer to live edge = more responsive, but needs big buffer to compensate
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLiveTargetOffsetMs(5_000) // 5s behind live edge (Apollo: 0x1388)

        // === Apollo-style track selector ===
        val trackSelector = DefaultTrackSelector(application).apply {
            parameters = buildUponParameters()
                .setForceLowestBitrate(false)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                // Note: Apollo uses setMaxVideoSizeSd() to cap at SD for zero buffering.
                // We skip this to preserve HD/4K quality — our 10-min buffer handles it.
                .build()
        }

        // === Apollo-style renderers: PREFER hardware extensions ===
        // Apollo uses setExtensionRendererMode(2) = EXTENSION_RENDERER_MODE_PREFER
        // This prioritizes hardware decoders over software — faster decoding, less CPU
        val renderersFactory = DefaultRenderersFactory(application)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // === Apollo-style AudioAttributes ===
        // Apollo sets USAGE_MEDIA (1) + CONTENT_TYPE_MOVIE (3)
        // This tells Android's audio system this is media playback — gets audio focus priority
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(application)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderersFactory)
            // === Apollo: Audio focus + noisy handling ===
            .setAudioAttributes(audioAttributes, /* handleAudioFocus */ true)
            .setHandleAudioBecomingNoisy(true) // Auto-pause when headphones disconnect
            .build()
            .apply {
                playWhenReady = true

                // === High-priority playback thread ===
                try {
                    val field = this.javaClass.getDeclaredField("playbackThread")
                    field.isAccessible = true
                    val thread = field.get(this) as? Thread
                    thread?.let {
                        Process.setThreadPriority(it.id.toInt(), Process.THREAD_PRIORITY_URGENT_AUDIO)
                        Log.d("LiveTvVM", "Set playback thread priority to URGENT_AUDIO (-19)")
                    }
                } catch (e: Exception) {
                    Log.d("LiveTvVM", "Could not set thread priority: ${e.message}")
                }

                addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (isPlayerReleased) return
                        val resolution = if (videoSize.width > 0 && videoSize.height > 0) {
                            "${videoSize.width}x${videoSize.height}"
                        } else ""
                        _uiState.value = _uiState.value.copy(videoResolution = resolution)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (isPlayerReleased) return
                        Log.w("LiveTvVM", "Player error: ${error.errorCodeName}", error)
                        handlePlaybackError()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (isPlayerReleased) return
                        when (playbackState) {
                            Player.STATE_READY -> {
                                updateFrameRate()
                                updateBitrate()
                                // Track rebuffer end
                                if (wasPlayingBeforeBuffer && rebufferStartTime > 0) {
                                    val rebufferDuration = System.currentTimeMillis() - rebufferStartTime
                                    sessionTotalRebufferMs += rebufferDuration
                                    rebufferStartTime = 0L
                                    _uiState.value = _uiState.value.copy(
                                        isBuffering = false,
                                        lastRebufferDurationMs = rebufferDuration,
                                        totalRebufferMs = sessionTotalRebufferMs
                                    )
                                    Log.d("LiveTvVM", "Rebuffer ended: ${rebufferDuration}ms (total: ${sessionRebufferCount} rebuffers, ${sessionTotalRebufferMs}ms)")
                                }
                                wasPlayingBeforeBuffer = false
                            }
                            Player.STATE_BUFFERING -> {
                                // Only count as rebuffer if we were already playing
                                if (_uiState.value.selectedChannel != null && !_uiState.value.isBuffering) {
                                    val isRebuffer = _uiState.value.videoResolution.isNotEmpty() // Was playing if we had video
                                    if (isRebuffer) {
                                        sessionRebufferCount++
                                        rebufferStartTime = System.currentTimeMillis()
                                        wasPlayingBeforeBuffer = true
                                        _uiState.value = _uiState.value.copy(
                                            isBuffering = true,
                                            rebufferCount = sessionRebufferCount
                                        )
                                        Log.d("LiveTvVM", "Rebuffer #$sessionRebufferCount started")
                                    }
                                }
                            }
                        }
                    }
                })
            }
    }

    /** Extract frame rate from the currently playing video track */
    private fun updateFrameRate() {
        try {
            val format = player.videoFormat
            if (format != null && format.frameRate > 0) {
                val fps = format.frameRate
                val fpsStr = if (fps == fps.toInt().toFloat()) {
                    "${fps.toInt()} fps"
                } else {
                    String.format("%.2f fps", fps)
                }
                _uiState.value = _uiState.value.copy(videoFrameRate = fpsStr)
            } else {
                _uiState.value = _uiState.value.copy(videoFrameRate = "")
            }
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(videoFrameRate = "")
        }
    }

    /** Extract video bitrate from the currently playing track */
    private fun updateBitrate() {
        try {
            val format = player.videoFormat
            if (format != null && format.bitrate > 0) {
                _uiState.value = _uiState.value.copy(
                    videoBitrateKbps = format.bitrate / 1000
                )
            }
        } catch (_: Exception) {}
    }

    init {
        // Global exception handler — prevents app crash if ExoPlayer throws OOM or unexpected error
        viewModelScope.launch {
            val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
                Log.e("LiveTvVM", "Uncaught on ${thread.name}: ${throwable.message}", throwable)
                if (throwable is OutOfMemoryError) {
                    // Emergency: release player buffers to free memory
                    try {
                        player.stop()
                        player.clearMediaItems()
                    } catch (_: Exception) {}
                }
            }
            // Set on the playback thread if accessible
            try {
                val field = player.javaClass.getDeclaredField("playbackThread")
                field.isAccessible = true
                val thread = field.get(player) as? Thread
                thread?.uncaughtExceptionHandler = handler
            } catch (_: Exception) {}
        }

        // Launch channels + EPG + backup pre-warm ALL in parallel for fastest startup
        loadChannelsAndEpgParallel()
        observeFavorites()
        preWarmBackupCache()
    }

    /**
     * v2.25.0: Load channels and EPG data in parallel instead of sequentially.
     */
    private fun loadChannelsAndEpgParallel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Launch EPG loading concurrently
            val epgJob = launch { loadEpgInternal() }

            try {
                val playlists = settingsDataStore.playlists.first()
                val enabledPlaylists = playlists.filter { it.enabled }
                val enabledUrls = enabledPlaylists.map { it.url }

                // Build playlist name map for source tracking
                playlistNames = enabledPlaylists.associate { it.url to it.name }

                val channels = withContext(Dispatchers.IO) {
                    if (enabledUrls.size == 1) {
                        channelRepository.loadChannels(enabledUrls.first())
                    } else if (enabledUrls.isNotEmpty()) {
                        channelRepository.loadMultipleChannels(enabledUrls)
                    } else {
                        emptyList()
                    }
                }

                val groupSet = LinkedHashSet<String>(channels.size / 10)
                for (ch in channels) { groupSet.add(ch.group) }

                val customOrder = settingsDataStore.categoryOrder.first()

                val sortedGroups = if (customOrder.isNotEmpty()) {
                    val ordered = customOrder.filter { groupSet.contains(it) }.toMutableList()
                    val remaining = groupSet.filter { it !in ordered }.sortedBy { it.lowercase() }
                    ordered + remaining
                } else {
                    groupSet.sortedWith(
                        compareByDescending<String> { group ->
                            val lower = group.lowercase()
                            lower.contains("usa") || lower.contains("us ") ||
                            lower.startsWith("us:") || lower.startsWith("us|") ||
                            lower.contains("united states") || lower.contains("american")
                        }.thenBy { it.lowercase() }
                    )
                }
                val groups = listOf("★ Favorites") + sortedGroups

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    channels = channels,
                    groups = groups,
                    totalChannels = channels.size
                ).withFilteredChannels(null)

                autoPlayLastWatched(channels)
                // Start pre-warming favorite channel connections once list is loaded
                updatePrewarmPool()
            } catch (e: Exception) {
                Log.e("LiveTvVM", "Failed to load channels", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun autoPlayLastWatched(channels: List<Channel>) {
        if (isPlayerReleased || channels.isEmpty()) return
        try {
            val lastId = settingsDataStore.lastWatchedChannelId.first()
            if (lastId.isNotEmpty()) {
                var foundIndex = -1
                var foundChannel: Channel? = null
                for (i in channels.indices) {
                    if (channels[i].id == lastId) {
                        foundIndex = i
                        foundChannel = channels[i]
                        break
                    }
                }
                if (foundChannel != null) {
                    _uiState.value = _uiState.value.copy(
                        selectedChannel = foundChannel,
                        isFullscreen = true,
                        showOverlay = false,
                        currentChannelIndex = foundIndex,
                        streamSource = resolveSourceName(foundChannel)
                    )
                    safePlayChannel(foundChannel)
                    loadEpgForChannel(foundChannel)
                }
            }
        } catch (e: Exception) {
            Log.e("LiveTvVM", "Auto-play last watched failed", e)
        }
    }

    /** Determine which playlist a channel came from based on its stream URL domain */
    private fun resolveSourceName(channel: Channel): String {
        // If we have playlist names, try to match by checking if the channel URL
        // could belong to a playlist. Since M3U channels don't store their source,
        // we show the first playlist name, or "Primary" / "Backup" based on context.
        return when {
            playlistNames.size == 1 -> playlistNames.values.first()
            playlistNames.isNotEmpty() -> playlistNames.values.first()
            else -> "Primary"
        }
    }

    private suspend fun loadEpgInternal() {
        try {
            val defaultUrls = DefaultData.EPG_SOURCES.map { it.url }
            val customSources = settingsDataStore.customEpgSources.first()
            val customUrls = customSources.filter { it.enabled }.map { it.url }
            val allUrls = (defaultUrls + customUrls).distinct()
            withContext(Dispatchers.IO) {
                epgRepository.loadEpg(allUrls)
            }
        } catch (e: Exception) {
            Log.e("LiveTvVM", "EPG load failed", e)
        }
    }

    private fun preWarmBackupCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupChannelRepository.findBackupStream("__prewarm__")
            } catch (_: Exception) {}
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            try {
                favoritesRepository.getFavoriteChannelIds().collect { ids ->
                    _uiState.value = _uiState.value.copy(favoriteIds = ids)
                    // Update connection pre-warm pool when favorites change
                    updatePrewarmPool()
                }
            } catch (e: Exception) {
                Log.e("LiveTvVM", "Favorites observe failed", e)
            }
        }
    }

    fun onSearchChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    val isSearchActive: Boolean get() = _uiState.value.searchQuery.isNotBlank()

    fun onGroupSelected(group: String?) {
        _uiState.value = _uiState.value.copy(selectedGroup = group, showCategories = false)
        applyFilters()
    }

    fun showCategories() {
        _uiState.value = _uiState.value.copy(showCategories = true)
    }

    fun hideCategories() {
        _uiState.value = _uiState.value.copy(showCategories = false)
    }

    /**
     * Add the current channel to the recent channels history (max 3, no duplicates).
     */
    private fun addToRecentChannels(previousChannel: Channel?) {
        if (previousChannel == null) return
        val current = _uiState.value.recentChannels.toMutableList()
        // Remove if already in the list (avoid duplicates)
        current.removeAll { it.id == previousChannel.id }
        // Add to front (most recent first)
        current.add(0, previousChannel)
        // Keep only last 3
        while (current.size > 3) current.removeAt(current.size - 1)
        _uiState.value = _uiState.value.copy(recentChannels = current)
    }

    fun onChannelSelected(channel: Channel) {
        val previousChannel = _uiState.value.selectedChannel
        val index = _uiState.value.filteredChannels.indexOf(channel)

        // Add previous channel to recent history
        addToRecentChannels(previousChannel)

        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            isFullscreen = true,
            showOverlay = false,
            currentChannelIndex = index,
            streamSource = resolveSourceName(channel),
            videoFrameRate = "" // Reset until new stream reports frame rate
        )

        safePlayChannel(channel)
        loadEpgForChannel(channel)
        // Refresh pre-warm pool (current channel changed, may need to swap slots)
        updatePrewarmPool()
        viewModelScope.launch {
            try { settingsDataStore.setLastWatchedChannelId(channel.id) } catch (_: Exception) {}
        }
    }

    private fun safePlayChannel(channel: Channel) {
        if (isPlayerReleased) return
        failoverAttempts = 0
        sameUrlRetryCount = 0
        triedStreamUrls.clear()

        // Reset rebuffer tracking for new channel
        sessionRebufferCount = 0
        sessionTotalRebufferMs = 0L
        rebufferStartTime = 0L
        wasPlayingBeforeBuffer = false

        // If VLC is active, stop it and switch back to ExoPlayer for new channel
        if (_uiState.value.isUsingVlc) {
            switchToExoPlayer()
        }

        try {
            player.stop()
            player.clearMediaItems()
            // Build media item with live configuration for better live stream handling
            val mediaItem = MediaItem.Builder()
                .setUri(channel.streamUrl)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.04f)     // Catch up gently if behind
                        .setMinPlaybackSpeed(0.96f)     // Slow down gently if too far ahead
                        .setTargetOffsetMs(8_000)       // Target 8s behind live edge (was 10s)
                        .setMinOffsetMs(3_000)          // Never closer than 3s to edge (was 5s)
                        .setMaxOffsetMs(45_000)         // Allow up to 45s behind (was 30s) — prevents forced rebuffer
                        .build()
                )
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e("LiveTvVM", "Player error in safePlayChannel", e)
        }
        _uiState.value = _uiState.value.copy(
            videoResolution = "",
            videoFrameRate = "",
            videoBitrateKbps = 0,
            isFailingOver = false,
            failoverMessage = "",
            rebufferCount = 0,
            isBuffering = false,
            lastRebufferDurationMs = 0L,
            totalRebufferMs = 0L
        )
    }

    // TiviMate-style retry: try same stream up to 3 times with linear backoff before seeking backup
    private var sameUrlRetryCount = 0
    private val MAX_SAME_URL_RETRIES = 3

    private fun handlePlaybackError() {
        if (isPlayerReleased) return
        val currentChannel = _uiState.value.selectedChannel ?: return

        failoverAttempts++

        // TiviMate-style: retry same URL with linear backoff (0s, 1s, 2s) before trying backup
        if (sameUrlRetryCount < MAX_SAME_URL_RETRIES) {
            sameUrlRetryCount++
            val retryDelayMs = (sameUrlRetryCount - 1) * 1000L // 0ms, 1000ms, 2000ms
            Log.d("LiveTvVM", "Retrying same stream (attempt $sameUrlRetryCount/$MAX_SAME_URL_RETRIES, delay ${retryDelayMs}ms)")
            _uiState.value = _uiState.value.copy(
                isFailingOver = true,
                failoverMessage = "Reconnecting... (retry $sameUrlRetryCount/$MAX_SAME_URL_RETRIES)"
            )
            viewModelScope.launch {
                if (retryDelayMs > 0) delay(retryDelayMs)
                if (isPlayerReleased) return@launch
                try {
                    player.stop()
                    player.clearMediaItems()
                    val mediaItem = MediaItem.Builder()
                        .setUri(currentChannel.streamUrl)
                        .setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder()
                                .setMaxPlaybackSpeed(1.04f)
                                .setMinPlaybackSpeed(0.96f)
                                .setTargetOffsetMs(8_000)
                                .setMinOffsetMs(3_000)
                                .setMaxOffsetMs(45_000)
                                .build()
                        )
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    _uiState.value = _uiState.value.copy(isFailingOver = false, failoverMessage = "")
                } catch (e: Exception) {
                    Log.e("LiveTvVM", "Retry $sameUrlRetryCount failed", e)
                }
            }
            return
        }

        // Exhausted ExoPlayer retries — try VLC before searching backups
        if (!_uiState.value.isUsingVlc) {
            Log.d("LiveTvVM", "ExoPlayer retries exhausted — switching to VLC fallback")
            _uiState.value = _uiState.value.copy(
                isFailingOver = true,
                failoverMessage = "Switching to VLC player..."
            )
            playWithVlc(currentChannel.streamUrl)
            return
        }

        // Both ExoPlayer AND VLC failed — now search backups
        switchToExoPlayer() // Reset to ExoPlayer for backup attempt
        triedStreamUrls.add(currentChannel.streamUrl)
        _uiState.value = _uiState.value.copy(
            isFailingOver = true,
            failoverMessage = "Searching backup sources... (attempt $failoverAttempts)"
        )

        viewModelScope.launch {
            try {
                val backupChannel = withContext(Dispatchers.IO) {
                    backupChannelRepository.findBackupStream(currentChannel.name, triedStreamUrls)
                }
                if (isPlayerReleased) return@launch
                if (backupChannel != null) {
                    triedStreamUrls.add(backupChannel.streamUrl)
                    try {
                        player.stop()
                        player.clearMediaItems()
                        val mediaItem = MediaItem.Builder()
                            .setUri(backupChannel.streamUrl)
                            .setLiveConfiguration(
                                MediaItem.LiveConfiguration.Builder()
                                    .setMaxPlaybackSpeed(1.04f)
                                    .setMinPlaybackSpeed(0.96f)
                                    .setTargetOffsetMs(10_000)
                                    .setMinOffsetMs(5_000)
                                    .setMaxOffsetMs(30_000)
                                    .build()
                            )
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    } catch (e: Exception) {
                        Log.e("LiveTvVM", "Backup play failed", e)
                    }
                    _uiState.value = _uiState.value.copy(
                        isFailingOver = false,
                        failoverMessage = "Playing from backup source",
                        streamSource = "Backup" // Mark as playing from backup
                    )
                    clearFailoverMessageAfterDelay()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isFailingOver = false,
                        failoverMessage = "No working backup found (searched all sources)"
                    )
                    clearFailoverMessageAfterDelay()
                }
            } catch (e: Exception) {
                Log.e("LiveTvVM", "Backup search failed", e)
                _uiState.value = _uiState.value.copy(
                    isFailingOver = false,
                    failoverMessage = "Backup search failed"
                )
                clearFailoverMessageAfterDelay()
            }
        }
    }

    private fun clearFailoverMessageAfterDelay() {
        failoverMessageJob?.cancel()
        failoverMessageJob = viewModelScope.launch {
            delay(4000)
            _uiState.value = _uiState.value.copy(failoverMessage = "")
        }
    }

    private fun loadEpgForChannel(channel: Channel) {
        viewModelScope.launch {
            try {
                val epgId = channel.epgId.ifEmpty { channel.id }
                val currentProgram = epgRepository.getCurrentProgram(epgId)

                val now = System.currentTimeMillis()
                var nextProgram: EpgEntry? = null
                epgRepository.getEpgForChannel(epgId).first().let { programs ->
                    nextProgram = programs.firstOrNull { it.startTime > now }
                }

                _uiState.value = _uiState.value.copy(
                    currentProgram = currentProgram,
                    nextProgram = nextProgram
                )
            } catch (e: Exception) {
                Log.e("LiveTvVM", "EPG for channel failed", e)
            }
        }
    }

    fun channelUp() {
        val state = _uiState.value
        val channels = state.filteredChannels
        if (channels.isEmpty()) return

        val previousChannel = state.selectedChannel
        val newIndex = if (state.currentChannelIndex <= 0) {
            channels.size - 1
        } else {
            state.currentChannelIndex - 1
        }

        val channel = channels[newIndex]
        addToRecentChannels(previousChannel)
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            currentChannelIndex = newIndex,
            showOverlay = true,
            streamSource = resolveSourceName(channel),
            videoFrameRate = ""
        )
        safePlayChannel(channel)
        loadEpgForChannel(channel)
        viewModelScope.launch {
            try { settingsDataStore.setLastWatchedChannelId(channel.id) } catch (_: Exception) {}
        }
    }

    fun channelDown() {
        val state = _uiState.value
        val channels = state.filteredChannels
        if (channels.isEmpty()) return

        val previousChannel = state.selectedChannel
        val newIndex = if (state.currentChannelIndex >= channels.size - 1) {
            0
        } else {
            state.currentChannelIndex + 1
        }

        val channel = channels[newIndex]
        addToRecentChannels(previousChannel)
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            currentChannelIndex = newIndex,
            showOverlay = true,
            streamSource = resolveSourceName(channel),
            videoFrameRate = ""
        )
        safePlayChannel(channel)
        loadEpgForChannel(channel)
        viewModelScope.launch {
            try { settingsDataStore.setLastWatchedChannelId(channel.id) } catch (_: Exception) {}
        }
    }

    fun toggleOverlay() {
        _uiState.value = _uiState.value.copy(showOverlay = !_uiState.value.showOverlay)
    }

    fun hideOverlay() {
        _uiState.value = _uiState.value.copy(showOverlay = false)
    }

    fun showQuickMenu() {
        // Refresh frame rate when opening quick menu
        updateFrameRate()
        _uiState.value = _uiState.value.copy(showQuickMenu = true, showOverlay = true)
    }

    fun hideQuickMenu() {
        _uiState.value = _uiState.value.copy(showQuickMenu = false)
    }

    /** Switch to a recent channel by index (0 = most recent, 1, 2) */
    fun goToRecentChannel(index: Int) {
        val recent = _uiState.value.recentChannels
        if (index !in recent.indices) return
        val channel = recent[index]
        hideQuickMenu()
        onChannelSelected(channel)
    }

    fun toggleCurrentChannelFavorite() {
        val channelId = _uiState.value.selectedChannel?.id ?: return
        toggleFavorite(channelId)
        hideQuickMenu()
    }

    fun toggleSubtitles() {
        val enabled = !_uiState.value.subtitlesEnabled
        _uiState.value = _uiState.value.copy(subtitlesEnabled = enabled)
        try {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                .build()
        } catch (_: Exception) {}
    }

    fun exitFullscreen() {
        _uiState.value = _uiState.value.copy(
            isFullscreen = false,
            showOverlay = false,
            showCategories = true
        )
    }

    fun stopPlayback() {
        if (isPlayerReleased) return
        try { player.stop() } catch (_: Exception) {}
    }

    fun resumePlayback() {
        if (isPlayerReleased) return
        try {
            if (_uiState.value.selectedChannel != null && !player.isPlaying) {
                player.play()
            }
        } catch (_: Exception) {}
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            try { favoritesRepository.toggleFavoriteChannel(channelId) } catch (_: Exception) {}
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        val hasGroup = state.selectedGroup != null
        val hasSearch = state.searchQuery.isNotBlank()

        if (!hasGroup && !hasSearch) {
            _uiState.value = state.withFilteredChannels(null)
            return
        }

        var filtered = state.channels

        if (hasSearch) {
            val query = state.searchQuery
            filtered = filtered.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
            }
        } else if (hasGroup) {
            val group = state.selectedGroup!!
            if (group == "★ Favorites") {
                val favIds = state.favoriteIds
                filtered = filtered.filter { favIds.contains(it.id) }
            } else {
                filtered = filtered.filter { it.group == group }
            }
        }

        _uiState.value = state.withFilteredChannels(filtered)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Connection Pre-Warm: DNS resolve + TCP connect + byte prefetch
    // Zero hardware decoders used — just network-level warm-up
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when favorites change or channels load.
     * Starts/stops pre-warm jobs for up to MAX_PREWARM_CHANNELS favorite channels.
     */
    private fun updatePrewarmPool() {
        if (isPlayerReleased) return
        val channels = _uiState.value.channels
        val favoriteIds = _uiState.value.favoriteIds
        val currentChannelId = _uiState.value.selectedChannel?.id

        // Find favorite channels (up to MAX), excluding the currently playing one
        val targetChannels = channels
            .filter { it.id in favoriteIds && it.id != currentChannelId }
            .take(MAX_PREWARM_CHANNELS)

        val targetIds = targetChannels.map { it.id }.toSet()

        // Cancel pre-warm jobs for channels no longer in the priority list
        val toRemove = prewarmJobs.keys - targetIds
        for (id in toRemove) {
            prewarmJobs[id]?.cancel()
            prewarmJobs.remove(id)
            prewarmChannelUrls.remove(id)
            prewarmedChannelIds.remove(id)
        }

        // Start pre-warm for new priority channels
        for (channel in targetChannels) {
            val existingUrl = prewarmChannelUrls[channel.id]
            if (existingUrl == channel.streamUrl && prewarmJobs[channel.id]?.isActive == true) {
                continue // Already pre-warming this channel
            }

            // Cancel old job if URL changed
            prewarmJobs[channel.id]?.cancel()

            prewarmChannelUrls[channel.id] = channel.streamUrl
            prewarmJobs[channel.id] = viewModelScope.launch(Dispatchers.IO) {
                prewarmChannel(channel)
            }
        }

        Log.d("LiveTvVM", "Pre-warm: ${prewarmJobs.size} favorite channels warming")
    }

    /**
     * Pre-warm a single channel connection:
     * 1. DNS resolution (cached by system after first resolve)
     * 2. TCP connection + TLS handshake
     * 3. Fetch first 256KB of stream data (warms HTTP cache + keeps connection alive)
     * 4. Re-warm every 30s to prevent TCP idle timeout
     */
    private suspend fun prewarmChannel(channel: Channel) {
        while (true) {
            try {
                val url = java.net.URL(channel.streamUrl)
                // Step 1: DNS resolve
                val host = url.host
                java.net.InetAddress.getByName(host)
                Log.d("LiveTvVM", "Pre-warm DNS: ${channel.name} → $host resolved")

                // Step 2+3: Open connection and fetch first bytes
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    setRequestProperty("Connection", "keep-alive")
                    setRequestProperty("Accept", "*/*")
                    instanceFollowRedirects = true
                }

                // Handle HTTPS with SSL bypass (same as main player)
                if (connection is javax.net.ssl.HttpsURLConnection) {
                    try {
                        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                            override fun checkClientTrusted(c: Array<out X509Certificate>?, t: String?) {}
                            override fun checkServerTrusted(c: Array<out X509Certificate>?, t: String?) {}
                            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        })
                        val ctx = SSLContext.getInstance("TLS")
                        ctx.init(null, trustAll, SecureRandom())
                        connection.sslSocketFactory = ctx.socketFactory
                        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
                    } catch (_: Exception) {}
                }

                connection.connect()
                val responseCode = connection.responseCode

                if (responseCode in 200..399) {
                    // Fetch first PREWARM_BYTES to fill OS TCP buffer and HTTP cache
                    val input = connection.inputStream
                    val buffer = ByteArray(8192)
                    var totalRead = 0
                    while (totalRead < PREWARM_BYTES) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        totalRead += read
                    }
                    input.close()
                    prewarmedChannelIds.add(channel.id)
                    Log.d("LiveTvVM", "Pre-warm OK: ${channel.name} — ${totalRead / 1024}KB prefetched (HTTP $responseCode)")
                } else {
                    Log.w("LiveTvVM", "Pre-warm: ${channel.name} returned HTTP $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("LiveTvVM", "Pre-warm failed: ${channel.name} — ${e.message}")
            }

            // Re-warm every 30s to keep DNS cache + TCP connections alive
            delay(PREWARM_REFRESH_MS)
        }
    }

    override fun onCleared() {
        super.onCleared()
        isPlayerReleased = true
        failoverMessageJob?.cancel()
        // Cancel all pre-warm jobs
        for ((id, job) in prewarmJobs) {
            job.cancel()
            Log.d("LiveTvVM", "Pre-warm: cancelled $id")
        }
        prewarmJobs.clear()
        prewarmChannelUrls.clear()
        prewarmedChannelIds.clear()
        // Release VLC
        stopVlc()
        try { libVLC?.release() } catch (_: Exception) {}
        libVLC = null
        try { player.release() } catch (_: Exception) {}
    }
}
