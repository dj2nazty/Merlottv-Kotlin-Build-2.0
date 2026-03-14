package com.merlottv.kotlin.ui.screens.livetv

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.merlottv.kotlin.data.local.SettingsDataStore
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
    val videoBitrateKbps: Int = 0
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

    /**
     * Optimized ExoPlayer configuration for live TV:
     *
     * v2.25.0: BandwidthMeter, adjustable buffer, fast failover
     * v2.25.1: User-configurable buffer slider (0.3s–3.0s)
     * v2.25.2: Frame rate, recent channels, stream source display
     * v2.25.3: FIXED post-load rebuffering:
     *   - Decoupled startup buffer from steady-state buffer
     *   - Slider controls STARTUP speed only (bufferForPlaybackMs)
     *   - Steady-state buffer is always 30s min / 60s max — prevents mid-stream rebuffering
     *   - After rebuffer, requires 3s buffer before resuming (prevents rapid re-stalls)
     *   - Back-buffer retains 30s of played data to handle brief seek-backs
     *   - Live offset set to 10s behind edge for network headroom
     *   - Rebuffer tracking: count, duration, bitrate displayed in quick menu
     */
    val player: ExoPlayer = run {
        // Read user's buffer preference (synchronous — only runs once at ViewModel creation)
        val userBufferMs = try {
            runBlocking { settingsDataStore.bufferDurationMs.first() }
        } catch (e: Exception) {
            Log.e("LiveTvVM", "Failed to read buffer setting, using default", e)
            800 // safe default
        }

        // BandwidthMeter tracks download speed and helps ExoPlayer pick optimal quality
        val bandwidthMeter = DefaultBandwidthMeter.Builder(application)
            .setResetOnNetworkTypeChange(true) // Re-estimate when WiFi ↔ cellular
            .build()

        // === KEY FIX: Decouple startup speed from steady-state buffer ===
        //
        // The user's slider (userBufferMs) controls how fast channels START playing.
        // But once playing, we need a much larger buffer to prevent rebuffering.
        //
        // minBufferMs = 30s: ExoPlayer always tries to keep 30s of video ahead.
        //   This is the #1 fix — previously it was only 800ms, so any tiny
        //   network hiccup caused rebuffering.
        //
        // maxBufferMs = 60s: ExoPlayer will buffer up to 60s ahead when possible.
        //   Gives plenty of headroom for local TV streams.
        //
        // bufferForPlaybackMs = userBufferMs * 3/8 (min 200ms): How much buffer
        //   before INITIAL playback starts. This is what the slider controls.
        //
        // bufferForPlaybackAfterRebufferMs = 3000ms: After a rebuffer event,
        //   require 3 full seconds of buffer before resuming. Previously this was
        //   only 800ms, causing rapid re-stalls. 3s gives the network time to
        //   stabilize.

        val minBuffer = 30_000          // 30 seconds steady-state minimum
        val maxBuffer = 60_000          // 60 seconds maximum lookahead
        val playbackBuffer = (userBufferMs * 3 / 8).coerceAtLeast(200) // Startup speed (slider)
        val rebufferBuffer = 3_000      // 3 seconds required after rebuffer

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                /* minBufferMs */                      minBuffer,
                /* maxBufferMs */                      maxBuffer,
                /* bufferForPlaybackMs */               playbackBuffer,
                /* bufferForPlaybackAfterRebufferMs */  rebufferBuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            // Back-buffer: retain 30s of already-played data
            .setBackBuffer(30_000, /* retainBackBufferFromKeyframe */ true)
            .build()

        // HTTP data source with tight timeouts for fast failover on dead streams
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(6_000)
            .setReadTimeoutMs(8_000)    // Slightly more read time for large segments
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Connection" to "keep-alive"
            ))
            .setTransferListener(bandwidthMeter)

        val dataSourceFactory = DefaultDataSource.Factory(application, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(application)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .build()
            .apply {
                playWhenReady = true

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
        viewModelScope.launch {
            try { settingsDataStore.setLastWatchedChannelId(channel.id) } catch (_: Exception) {}
        }
    }

    private fun safePlayChannel(channel: Channel) {
        if (isPlayerReleased) return
        failoverAttempts = 0
        triedStreamUrls.clear()

        // Reset rebuffer tracking for new channel
        sessionRebufferCount = 0
        sessionTotalRebufferMs = 0L
        rebufferStartTime = 0L
        wasPlayingBeforeBuffer = false

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
                        .setTargetOffsetMs(10_000)      // Target 10s behind live edge
                        .setMinOffsetMs(5_000)          // Never closer than 5s to edge
                        .setMaxOffsetMs(30_000)         // Never more than 30s behind
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

    private fun handlePlaybackError() {
        if (isPlayerReleased) return
        val currentChannel = _uiState.value.selectedChannel ?: return

        triedStreamUrls.add(currentChannel.streamUrl)

        failoverAttempts++
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

    override fun onCleared() {
        super.onCleared()
        isPlayerReleased = true
        failoverMessageJob?.cancel()
        try { player.release() } catch (_: Exception) {}
    }
}
