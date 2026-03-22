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
import androidx.media3.session.MediaSession
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
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.ChannelRepository
import com.merlottv.kotlin.domain.repository.EpgRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val showCategories: Boolean = false,
    // Channel list visibility (shown by default in non-fullscreen mode)
    val showChannelList: Boolean = true,
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
    val bitrateCheckerEnabled: Boolean = false,
    val videoBitrateKbps: Int = 0,
    val audioBitrateKbps: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val measuredBitrateKbps: Int = 0,  // actual network throughput from BandwidthMeter
    // Player engine: "exo" or "vlc"
    val activePlayerEngine: String = "exo",
    val isUsingVlc: Boolean = false,
    // Buffer config info for Quick Menu display
    val bufferConfigLabel: String = "Apollo",
    val bufferSizeSec: Int = 600,
    val bufferMemoryCapMb: Int = 0,
    val liveOffsetMs: Long = 5_000,
    // EPG Guide overlay (TiviMate-style — press RIGHT in fullscreen)
    val showEpgGuide: Boolean = false,
    val epgChannels: List<EpgChannel> = emptyList(),
    val epgLoading: Boolean = false,
    val epgSelectedProgram: EpgEntry? = null,
    // EPG Guide uses the user's actual channel list with category filter
    val epgGuideChannels: List<Channel> = emptyList(),
    val epgGuideGroups: List<String> = emptyList(),
    val epgGuideSelectedGroup: String? = null,
    val epgSelectedIndex: Int = 0,
    val epgScrollRequest: Int = 0,  // incremented/decremented to trigger scroll
    val showEpgCategoryPicker: Boolean = false,
    val epgTimelineAtStart: Boolean = true  // true when timeline scroll is at or near position 0
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
    private val backupChannelRepository: BackupChannelRepository,
    private val addonRepository: AddonRepository
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

    // Single shared BandwidthMeter — both players use this for consistent bitrate estimation
    private val sharedBandwidthMeter: DefaultBandwidthMeter = DefaultBandwidthMeter.Builder(application)
        .setResetOnNetworkTypeChange(true)
        .build()

    // Periodic bitrate refresh job (active while Quick Menu is open)
    private var bitrateRefreshJob: Job? = null

    // Rebuffer tracking state
    private var rebufferStartTime = 0L
    private var sessionRebufferCount = 0
    private var sessionTotalRebufferMs = 0L
    private var wasPlayingBeforeBuffer = false
    private var rebufferWindowStartTime = 0L // When the first rebuffer in current window started

    // Buffer Automatic Backup Scan setting (read once at init, updated via Flow)
    private var bufferAutoBackupScanEnabled = false

    // Stall watchdog — auto-reconnects when buffering exceeds threshold
    private var stallWatchdogJob: Job? = null

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
        private const val STALL_WATCHDOG_MS = 30_000L // Auto-reconnect after 30s of buffering
        private const val VLC_STALL_WATCHDOG_MS = 20_000L // VLC stall timeout (shorter, VLC buffers less)
        private const val REBUFFER_THRESHOLD = 2 // Trigger failover after this many rebuffers...
        private const val REBUFFER_WINDOW_MS = 10 * 60 * 1000L // ...within this time window (10 min)

        // Local affiliate channels that can't handle Apollo's aggressive 10-min buffer.
        // These get a gentle TiviMate-style config instead.
        private val LOCAL_AFFILIATE_PATTERNS = listOf(
            "wtvg", "wtol", "wnwo", "wupw",  // Call signs
            "abc", "cbs", "nbc", "fox"         // Network names (matched with channel name)
        )
    }

    /**
     * Detect if a channel is a local affiliate that needs gentle buffer treatment.
     * Matches against call signs (WTVG, WTOL, WNWO, WUPW) and network names.
     */
    private fun isLocalAffiliate(channel: Channel): Boolean {
        val nameLower = channel.name.lowercase()
        return LOCAL_AFFILIATE_PATTERNS.any { pattern ->
            nameLower.contains(pattern)
        }
    }

    /**
     * Gentle ExoPlayer for local affiliate channels.
     * Uses TiviMate-proven 50s buffer instead of Apollo's aggressive 10-min.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val gentlePlayer: ExoPlayer = run {
        val userBufferMs = try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { settingsDataStore.bufferDurationMs.first() }
        } catch (e: Exception) { 1000 }

        val gentleLoadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 65_536))
            .setBufferDurationsMs(
                50_000,  // 50s min buffer — TiviMate proven
                50_000,  // 50s max buffer
                userBufferMs.coerceAtLeast(500), // Slider controls startup
                2_000    // 2s rebuffer resume
            )
            .setPrioritizeTimeOverSizeThresholds(true) // Time first — gentle on servers
            .setTargetBufferBytes(60 * 1024 * 1024)    // 60MB cap — plenty for 50s
            .setBackBuffer(20_000, true)                // 20s back-buffer
            .build()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(mapOf("Connection" to "keep-alive", "Accept" to "*/*"))
            .setTransferListener(sharedBandwidthMeter)

        val dataSourceFactory = DefaultDataSource.Factory(application, httpFactory)
        val gentleMediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLiveTargetOffsetMs(10_000) // 10s behind live edge — extra headroom

        val trackSelector = DefaultTrackSelector(application).apply {
            parameters = buildUponParameters()
                .setForceLowestBitrate(false)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .build()
        }

        val renderersFactory = DefaultRenderersFactory(application)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        ExoPlayer.Builder(application)
            .setLoadControl(gentleLoadControl)
            .setMediaSourceFactory(gentleMediaSourceFactory)
            .setBandwidthMeter(sharedBandwidthMeter)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                playWhenReady = true
                Log.d("LiveTvVM", "Gentle player created for local affiliates (50s buffer, 60MB cap)")

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
                        Log.w("LiveTvVM", "Gentle player error: ${error.errorCodeName}", error)
                        handlePlaybackError()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (isPlayerReleased) return
                        when (playbackState) {
                            Player.STATE_READY -> {
                                stallWatchdogJob?.cancel()
                                stallWatchdogJob = null
                                if (wasPlayingBeforeBuffer && rebufferStartTime > 0) {
                                    val rebufferDuration = System.currentTimeMillis() - rebufferStartTime
                                    sessionTotalRebufferMs += rebufferDuration
                                    rebufferStartTime = 0L
                                    _uiState.value = _uiState.value.copy(
                                        isBuffering = false,
                                        lastRebufferDurationMs = rebufferDuration,
                                        totalRebufferMs = sessionTotalRebufferMs
                                    )
                                }
                                wasPlayingBeforeBuffer = false
                            }
                            Player.STATE_BUFFERING -> {
                                if (_uiState.value.selectedChannel != null && !_uiState.value.isBuffering) {
                                    val isRebuffer = _uiState.value.videoResolution.isNotEmpty()
                                    if (isRebuffer) {
                                        sessionRebufferCount++
                                        val now = System.currentTimeMillis()
                                        rebufferStartTime = now
                                        wasPlayingBeforeBuffer = true

                                        // Track rebuffer window — reset if outside 10-min window
                                        if (rebufferWindowStartTime == 0L || now - rebufferWindowStartTime > REBUFFER_WINDOW_MS) {
                                            rebufferWindowStartTime = now
                                            sessionRebufferCount = 1 // Reset count for new window
                                        }

                                        _uiState.value = _uiState.value.copy(
                                            isBuffering = true,
                                            rebufferCount = sessionRebufferCount
                                        )

                                        // Hit rebuffer threshold — immediately try backup streams (no waiting)
                                        if (bufferAutoBackupScanEnabled && sessionRebufferCount >= REBUFFER_THRESHOLD) {
                                            Log.w("LiveTvVM", "Rebuffer threshold hit: $sessionRebufferCount rebuffers in ${(now - rebufferWindowStartTime) / 1000}s — switching to backup stream NOW")
                                            handlePlaybackError()
                                            return
                                        }
                                    }
                                }
                            }
                            Player.STATE_ENDED -> {
                                Log.w("LiveTvVM", "Gentle stream ended unexpectedly, forcing reconnect")
                                handlePlaybackError()
                            }
                        }
                    }
                })
            }
    }

    /** Get the active ExoPlayer for the current channel */
    private var usingGentlePlayer = false
    fun getActivePlayer(): ExoPlayer = if (usingGentlePlayer) gentlePlayer else player

    // MediaSession — exposes playback state to system media controls (remote, Google Assistant)
    private var mediaSession: MediaSession? = null

    /** Create or update MediaSession for the active player */
    private fun ensureMediaSession() {
        val activePlayer = getActivePlayer()
        // Release old session if player changed
        if (mediaSession?.player != activePlayer) {
            mediaSession?.release()
            mediaSession = MediaSession.Builder(getApplication(), activePlayer)
                .setId("merlot_livetv")
                .build()
        }
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

        // Stop whichever ExoPlayer is active
        try {
            getActivePlayer().stop()
            getActivePlayer().clearMediaItems()
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
                            // Start VLC stall watchdog — auto-failover if stuck buffering
                            if (vlcBufferTimeout?.isActive != true) {
                                vlcBufferTimeout = viewModelScope.launch {
                                    delay(VLC_STALL_WATCHDOG_MS)
                                    if (isPlayerReleased) return@launch
                                    if (_uiState.value.isBuffering && _uiState.value.isUsingVlc) {
                                        Log.w("LiveTvVM", "VLC stall watchdog triggered — buffering for ${VLC_STALL_WATCHDOG_MS / 1000}s, failing over")
                                        stopVlc()
                                        switchToExoPlayer()
                                        handlePlaybackError()
                                    }
                                }
                            }
                        } else {
                            vlcBufferTimeout?.cancel()
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
    fun stopVlc() {
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
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { settingsDataStore.bufferDurationMs.first() }
        } catch (e: Exception) {
            Log.e("LiveTvVM", "Failed to read buffer setting, using default", e)
            1000
        }

        // Use shared BandwidthMeter — consistent bitrate estimation across both players

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

        // === RESTORED: Full Apollo 10-minute buffer for all normal channels ===
        val steadyBuffer = 600_000      // 10 MINUTES — Apollo's 0x927C0
        val playbackBuffer = userBufferMs.coerceAtLeast(500)
        val rebufferBuffer = 2_500      // 2.5s — Apollo's 0x9C4

        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val targetBufferBytes = (maxHeapBytes / 2).toInt() // HALF of heap — Apollo exact
        Log.d("LiveTvVM", "Apollo buffer: heap=${maxHeapBytes/1024/1024}MB, buffer cap=${targetBufferBytes/1024/1024}MB, steady=${steadyBuffer/1000}s")

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 65_536))
            .setBufferDurationsMs(steadyBuffer, steadyBuffer, playbackBuffer, rebufferBuffer)
            .setPrioritizeTimeOverSizeThresholds(false) // Size first — Apollo exact
            .setTargetBufferBytes(targetBufferBytes)
            .setBackBuffer(60_000, true) // 60s back-buffer — Apollo exact
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
            .setTransferListener(sharedBandwidthMeter)

        // Apply SSL bypass
        if (sslContext != null) {
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames)
        }

        val dataSourceFactory = DefaultDataSource.Factory(application, httpDataSourceFactory)

        // === Apollo-style 5s live offset for normal channels ===
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
            .setBandwidthMeter(sharedBandwidthMeter)
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
                                // Cancel stall watchdog — stream is alive
                                stallWatchdogJob?.cancel()
                                stallWatchdogJob = null
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
                                        val now = System.currentTimeMillis()
                                        rebufferStartTime = now
                                        wasPlayingBeforeBuffer = true

                                        // Track rebuffer window — reset if outside 10-min window
                                        if (rebufferWindowStartTime == 0L || now - rebufferWindowStartTime > REBUFFER_WINDOW_MS) {
                                            rebufferWindowStartTime = now
                                            sessionRebufferCount = 1 // Reset count for new window
                                        }

                                        _uiState.value = _uiState.value.copy(
                                            isBuffering = true,
                                            rebufferCount = sessionRebufferCount
                                        )
                                        Log.d("LiveTvVM", "Rebuffer #$sessionRebufferCount started (window: ${(now - rebufferWindowStartTime) / 1000}s)")

                                        // Hit rebuffer threshold — immediately try backup streams (no waiting)
                                        if (bufferAutoBackupScanEnabled && sessionRebufferCount >= REBUFFER_THRESHOLD) {
                                            Log.w("LiveTvVM", "Rebuffer threshold hit: $sessionRebufferCount rebuffers in ${(now - rebufferWindowStartTime) / 1000}s — switching to backup stream NOW")
                                            handlePlaybackError()
                                            return
                                        }
                                    }
                                }
                            }
                            Player.STATE_ENDED -> {
                                // Stream ended unexpectedly — force reconnect
                                Log.w("LiveTvVM", "Stream ended unexpectedly, forcing reconnect")
                                handlePlaybackError()
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

    /** Extract video/audio bitrate, codecs, and measured throughput from the currently playing track */
    private fun updateBitrate() {
        try {
            val vFormat = player.videoFormat
            val aFormat = player.audioFormat
            val measuredBps = sharedBandwidthMeter.bitrateEstimate
            _uiState.value = _uiState.value.copy(
                videoBitrateKbps = if (vFormat != null && vFormat.bitrate > 0) vFormat.bitrate / 1000 else _uiState.value.videoBitrateKbps,
                audioBitrateKbps = if (aFormat != null && aFormat.bitrate > 0) aFormat.bitrate / 1000 else _uiState.value.audioBitrateKbps,
                videoCodec = vFormat?.sampleMimeType?.toCodecLabel() ?: _uiState.value.videoCodec,
                audioCodec = aFormat?.sampleMimeType?.toCodecLabel() ?: _uiState.value.audioCodec,
                measuredBitrateKbps = if (measuredBps > 0) (measuredBps / 1000).toInt() else _uiState.value.measuredBitrateKbps
            )
        } catch (_: Exception) {}
    }

    /** Convert MIME type to human-readable codec label */
    private fun String.toCodecLabel(): String = when {
        contains("avc") -> "H.264"
        contains("hevc") || contains("hev1") -> "H.265"
        contains("vp9") -> "VP9"
        contains("av01") -> "AV1"
        contains("mp4a") -> "AAC"
        contains("ac3") -> "AC3"
        contains("eac3") -> "EAC3"
        contains("opus") -> "Opus"
        contains("vorbis") -> "Vorbis"
        contains("mp3") || contains("mpeg-L3") -> "MP3"
        else -> substringAfterLast("/").uppercase()
    }

    /** Called when Quick Menu visibility changes — starts/stops periodic bitrate refresh */
    fun onQuickMenuVisibilityChanged(visible: Boolean) {
        if (visible) {
            updateBitrate() // immediate refresh
            bitrateRefreshJob = viewModelScope.launch {
                while (isActive) {
                    delay(2000)
                    updateBitrate()
                }
            }
        } else {
            bitrateRefreshJob?.cancel()
            bitrateRefreshJob = null
        }
    }

    init {
        // Observe bitrate checker setting reactively
        viewModelScope.launch {
            settingsDataStore.bitrateCheckerEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(bitrateCheckerEnabled = enabled)
            }
        }

        // Observe Buffer Automatic Backup Scan setting
        viewModelScope.launch {
            settingsDataStore.bufferAutoBackupScan.collect { enabled ->
                bufferAutoBackupScanEnabled = enabled
                Log.d("LiveTvVM", "Buffer Auto Backup Scan: ${if (enabled) "ON" else "OFF"}")
            }
        }

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
        _uiState.value = _uiState.value.copy(
            selectedGroup = group,
            showCategories = false,
            showChannelList = true
        )
        applyFilters()
    }

    fun showCategories() {
        _uiState.value = _uiState.value.copy(showCategories = true, showChannelList = false)
    }

    fun hideCategories() {
        _uiState.value = _uiState.value.copy(showCategories = false, showChannelList = true)
    }

    fun showChannelList() {
        _uiState.value = _uiState.value.copy(showChannelList = true, showCategories = false)
    }

    fun hideChannelList() {
        _uiState.value = _uiState.value.copy(showChannelList = false)
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

        // Pause addon network traffic — give Live TV full bandwidth
        addonRepository.setNetworkPaused(true)

        failoverAttempts = 0
        sameUrlRetryCount = 0
        triedStreamUrls.clear()

        // Reset rebuffer tracking for new channel
        sessionRebufferCount = 0
        sessionTotalRebufferMs = 0L
        rebufferStartTime = 0L
        rebufferWindowStartTime = 0L
        wasPlayingBeforeBuffer = false

        // Cancel any active stall watchdog
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null

        // If VLC is active, stop it and switch back to ExoPlayer for new channel
        if (_uiState.value.isUsingVlc) {
            switchToExoPlayer()
        }

        // Detect if this is a local affiliate → use gentle player
        val isGentle = isLocalAffiliate(channel)
        val wasGentle = usingGentlePlayer

        // Stop the previously active player
        val prevPlayer = if (wasGentle) gentlePlayer else player
        try {
            prevPlayer.stop()
            prevPlayer.clearMediaItems()
        } catch (_: Exception) {}

        // Also stop the other player if it was left running
        if (isGentle != wasGentle) {
            val otherPlayer = if (wasGentle) player else gentlePlayer
            try {
                otherPlayer.stop()
                otherPlayer.clearMediaItems()
            } catch (_: Exception) {}
        }

        usingGentlePlayer = isGentle
        val activePlayer = if (isGentle) gentlePlayer else player

        if (isGentle) {
            Log.d("LiveTvVM", "Local affiliate detected: '${channel.name}' → using gentle player (50s buffer)")
        } else {
            Log.d("LiveTvVM", "Normal channel: '${channel.name}' → using Apollo player (10-min buffer)")
        }

        try {
            // Local affiliates get more conservative live config
            val targetOffset = if (isGentle) 10_000L else 8_000L
            val minOffset = if (isGentle) 5_000L else 3_000L
            val maxOffset = if (isGentle) 60_000L else 45_000L

            val mediaItem = MediaItem.Builder()
                .setUri(channel.streamUrl)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.04f)
                        .setMinPlaybackSpeed(0.96f)
                        .setTargetOffsetMs(targetOffset)
                        .setMinOffsetMs(minOffset)
                        .setMaxOffsetMs(maxOffset)
                        .build()
                )
                .build()
            activePlayer.setMediaItem(mediaItem)
            activePlayer.prepare()
            activePlayer.play()
            ensureMediaSession()
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
            totalRebufferMs = 0L,
            // Buffer config info for Quick Menu
            bufferConfigLabel = if (usingGentlePlayer) "Gentle (Local)" else "Apollo",
            bufferSizeSec = if (usingGentlePlayer) 50 else 600,
            bufferMemoryCapMb = if (usingGentlePlayer) 60
                else (Runtime.getRuntime().maxMemory() / 2 / 1024 / 1024).toInt(),
            liveOffsetMs = if (usingGentlePlayer) 10_000 else 5_000
        )
    }

    // TiviMate-style retry: try same stream up to 3 times with linear backoff before seeking backup
    private var sameUrlRetryCount = 0
    private val MAX_SAME_URL_RETRIES = 3

    private fun handlePlaybackError() {
        if (isPlayerReleased) return
        val currentChannel = _uiState.value.selectedChannel ?: return

        failoverAttempts++

        // ── OLD BEHAVIOR (setting OFF): same-URL retries → VLC → backup search ──
        if (!bufferAutoBackupScanEnabled) {
            // TiviMate-style: retry same URL with linear backoff (0s, 1s, 2s)
            if (sameUrlRetryCount < MAX_SAME_URL_RETRIES) {
                sameUrlRetryCount++
                val retryDelayMs = (sameUrlRetryCount - 1) * 1000L
                Log.d("LiveTvVM", "Retrying same stream (attempt $sameUrlRetryCount/$MAX_SAME_URL_RETRIES, delay ${retryDelayMs}ms)")
                _uiState.value = _uiState.value.copy(
                    isFailingOver = true,
                    failoverMessage = "Reconnecting... (retry $sameUrlRetryCount/$MAX_SAME_URL_RETRIES)"
                )
                viewModelScope.launch {
                    if (retryDelayMs > 0) delay(retryDelayMs)
                    if (isPlayerReleased) return@launch
                    try {
                        val ap = getActivePlayer()
                        ap.stop()
                        ap.clearMediaItems()
                        val isGentle = isLocalAffiliate(currentChannel)
                        val mediaItem = MediaItem.Builder()
                            .setUri(currentChannel.streamUrl)
                            .setLiveConfiguration(
                                MediaItem.LiveConfiguration.Builder()
                                    .setMaxPlaybackSpeed(1.04f)
                                    .setMinPlaybackSpeed(0.96f)
                                    .setTargetOffsetMs(if (isGentle) 10_000 else 8_000)
                                    .setMinOffsetMs(if (isGentle) 5_000 else 3_000)
                                    .setMaxOffsetMs(if (isGentle) 60_000 else 45_000)
                                    .build()
                            )
                            .build()
                        ap.setMediaItem(mediaItem)
                        ap.prepare()
                        ap.play()
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
            switchToExoPlayer()
            triedStreamUrls.add(currentChannel.streamUrl)
            // Fall through to backup search below...
        } else {
            // ── NEW BEHAVIOR (setting ON): skip retries + VLC, go straight to backup ──
            if (_uiState.value.isUsingVlc) {
                switchToExoPlayer()
            }
            triedStreamUrls.add(currentChannel.streamUrl)
            Log.d("LiveTvVM", "Failover attempt $failoverAttempts — searching backup streams immediately")
        }
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
                        val ap = getActivePlayer()
                        ap.stop()
                        ap.clearMediaItems()
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
                        ap.setMediaItem(mediaItem)
                        ap.prepare()
                        ap.play()
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
            showCategories = false,
            showChannelList = true,
            showEpgGuide = false
        )
    }

    fun enterFullscreen() {
        _uiState.value = _uiState.value.copy(
            isFullscreen = true,
            showChannelList = false,
            showCategories = false
        )
    }

    // ── EPG Guide overlay (TiviMate-style) ──────────────────────────

    fun showEpgGuide() {
        _uiState.value = _uiState.value.copy(
            showEpgGuide = true,
            showQuickMenu = false,
            epgSelectedIndex = getCurrentChannelEpgIndex()
        )
        loadEpgGuideData()
    }

    /** Move EPG highlight up/down */
    fun epgNavigate(delta: Int) {
        val maxIndex = _uiState.value.epgGuideChannels.size - 1
        if (maxIndex < 0) return
        val newIndex = (_uiState.value.epgSelectedIndex + delta).coerceIn(0, maxIndex)
        _uiState.value = _uiState.value.copy(epgSelectedIndex = newIndex)
    }

    /** Select the currently highlighted EPG channel */
    fun epgSelectCurrent() {
        val channel = _uiState.value.epgGuideChannels.getOrNull(_uiState.value.epgSelectedIndex) ?: return
        switchToChannelFromGuide(channel)
    }

    /** Scroll EPG timeline left/right */
    fun epgScrollTimeline(direction: Int) {
        // Increment scroll request counter to trigger LaunchedEffect
        _uiState.value = _uiState.value.copy(
            epgScrollRequest = _uiState.value.epgScrollRequest + direction
        )
    }

    fun hideEpgGuide() {
        _uiState.value = _uiState.value.copy(
            showEpgGuide = false,
            epgSelectedProgram = null,
            epgGuideSelectedGroup = null,
            showEpgCategoryPicker = false
        )
    }

    fun toggleEpgCategoryPicker() {
        _uiState.value = _uiState.value.copy(
            showEpgCategoryPicker = !_uiState.value.showEpgCategoryPicker
        )
    }

    fun selectEpgProgram(program: EpgEntry?) {
        _uiState.value = _uiState.value.copy(epgSelectedProgram = program)
    }

    /** Switch to a channel from the EPG guide — uses direct Channel object */
    fun switchToChannelFromGuide(channel: Channel) {
        hideEpgGuide()
        onChannelSelected(channel)
    }

    /** Filter EPG guide by category group */
    fun setEpgGuideGroup(group: String?) {
        _uiState.value = _uiState.value.copy(epgGuideSelectedGroup = group)
        applyEpgGuideFilter()
    }

    /** Get the index of the currently playing channel in the EPG guide list */
    fun getCurrentChannelEpgIndex(): Int {
        val currentName = _uiState.value.selectedChannel?.name ?: return 0
        return _uiState.value.epgGuideChannels.indexOfFirst {
            it.name.equals(currentName, ignoreCase = true)
        }.coerceAtLeast(0)
    }

    private fun applyEpgGuideFilter() {
        val allChannels = _uiState.value.channels
        val group = _uiState.value.epgGuideSelectedGroup
        val filtered = if (group == null) {
            allChannels
        } else if (group == "★ Favorites") {
            allChannels.filter { _uiState.value.favoriteIds.contains(it.id) }
        } else {
            allChannels.filter { it.group.equals(group, ignoreCase = true) }
        }
        _uiState.value = _uiState.value.copy(epgGuideChannels = filtered)
    }

    /** Called by EPG overlay to report current timeline scroll position */
    fun updateEpgTimelineAtStart(atStart: Boolean) {
        if (_uiState.value.epgTimelineAtStart != atStart) {
            _uiState.value = _uiState.value.copy(epgTimelineAtStart = atStart)
        }
    }

    private fun loadEpgGuideData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(epgLoading = true)

            // Build channel list from the user's actual Live TV channels
            val allChannels = _uiState.value.channels
            val groups = allChannels.map { it.group }.distinct().sorted()

            // Apply current group filter (or show all)
            val group = _uiState.value.epgGuideSelectedGroup
            val filteredChannels = if (group == null) {
                allChannels
            } else if (group == "★ Favorites") {
                allChannels.filter { _uiState.value.favoriteIds.contains(it.id) }
            } else {
                allChannels.filter { it.group.equals(group, ignoreCase = true) }
            }

            // Load EPG data from Room DB
            try {
                val epgData = try {
                    epgRepository.getAllEpgChannels().first()
                } catch (_: Exception) { emptyList() }

                // Build a lookup map from EPG channel name/id to programs
                val epgByName = mutableMapOf<String, EpgChannel>()
                epgData.forEach { epgCh ->
                    epgByName[epgCh.name.lowercase()] = epgCh
                    epgByName[epgCh.id.lowercase()] = epgCh
                }

                // Match each Live TV channel to its EPG data
                val matchedEpgChannels = filteredChannels.map { channel ->
                    val epgId = channel.epgId.ifEmpty { channel.id }
                    val epgMatch = epgByName[epgId.lowercase()]
                        ?: epgByName[channel.name.lowercase()]
                    EpgChannel(
                        id = channel.id,
                        name = channel.name,
                        icon = epgMatch?.icon ?: channel.logoUrl,
                        programs = epgMatch?.programs ?: emptyList()
                    )
                }

                _uiState.value = _uiState.value.copy(
                    epgChannels = matchedEpgChannels,
                    epgGuideChannels = filteredChannels,
                    epgGuideGroups = groups,
                    epgLoading = false
                )

                // Background refresh if stale
                if (epgRepository.isEpgStale()) {
                    loadEpgInternal()
                }
            } catch (e: Exception) {
                Log.e("LiveTvVM", "EPG guide load failed", e)
                _uiState.value = _uiState.value.copy(
                    epgGuideChannels = filteredChannels,
                    epgGuideGroups = groups,
                    epgLoading = false
                )
            }
        }
    }

    fun stopPlayback() {
        if (isPlayerReleased) return
        try { player.stop() } catch (_: Exception) {}
        try { gentlePlayer.stop() } catch (_: Exception) {}
        // Resume addon network traffic so VOD detail screens can load
        addonRepository.setNetworkPaused(false)
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
            val group = state.selectedGroup ?: ""
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
                val connection = (url.openConnection() as? java.net.HttpURLConnection) ?: continue
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
        // Resume addon network traffic — Live TV no longer needs bandwidth
        addonRepository.setNetworkPaused(false)
        isPlayerReleased = true
        failoverMessageJob?.cancel()
        stallWatchdogJob?.cancel()
        // Cancel all pre-warm jobs
        for ((id, job) in prewarmJobs) {
            job.cancel()
            Log.d("LiveTvVM", "Pre-warm: cancelled $id")
        }
        prewarmJobs.clear()
        prewarmChannelUrls.clear()
        prewarmedChannelIds.clear()
        // Release MediaSession
        try { mediaSession?.release() } catch (_: Exception) {}
        mediaSession = null
        // Release VLC
        stopVlc()
        try { libVLC?.release() } catch (_: Exception) {}
        libVLC = null
        try { player.release() } catch (_: Exception) {}
        try { gentlePlayer.release() } catch (_: Exception) {}
    }
}
