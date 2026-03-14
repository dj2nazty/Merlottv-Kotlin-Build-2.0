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
    val subtitlesEnabled: Boolean = false
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

    /**
     * Optimized ExoPlayer configuration for live TV:
     *
     * v2.25.0 improvements:
     * - BandwidthMeter: Tracks network speed so ExoPlayer picks the best quality automatically
     * - Adjustable buffer duration: user-configurable 0.3s–3.0s via Settings slider
     * - Near-instant playback start based on user's chosen buffer
     * - HTTP timeouts: 6s connect, 6s read — fail fast on dead streams
     * - Cross-protocol redirects enabled for CDN flexibility
     *
     * v2.25.1: Buffer duration now reads from user Settings (default 800ms)
     * v2.25.2: Frame rate tracking, recent channels history, stream source display
     */
    val player: ExoPlayer = run {
        // Read user's buffer preference (synchronous — only runs once at ViewModel creation)
        val userBufferMs = runBlocking { settingsDataStore.bufferDurationMs.first() }

        // BandwidthMeter tracks download speed and helps ExoPlayer pick optimal quality
        val bandwidthMeter = DefaultBandwidthMeter.Builder(application)
            .setResetOnNetworkTypeChange(true) // Re-estimate when WiFi ↔ cellular
            .build()

        // Buffer config derived from user setting
        val minBuffer = userBufferMs
        val maxBuffer = (userBufferMs * 8).coerceAtMost(24_000)
        val playbackBuffer = (userBufferMs * 3 / 8).coerceAtLeast(200)
        val rebufferBuffer = userBufferMs

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                /* minBufferMs */                  minBuffer,
                /* maxBufferMs */                  maxBuffer,
                /* bufferForPlaybackMs */           playbackBuffer,
                /* bufferForPlaybackAfterRebufferMs */ rebufferBuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()

        // HTTP data source with tight timeouts for fast failover on dead streams
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(6_000)
            .setReadTimeoutMs(6_000)
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
                        if (playbackState == Player.STATE_READY) {
                            updateFrameRate()
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
        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e("LiveTvVM", "Player error in safePlayChannel", e)
        }
        _uiState.value = _uiState.value.copy(
            videoResolution = "",
            videoFrameRate = "",
            isFailingOver = false,
            failoverMessage = ""
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
                        player.setMediaItem(MediaItem.fromUri(backupChannel.streamUrl))
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
