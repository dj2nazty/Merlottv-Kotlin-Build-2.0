package com.merlottv.kotlin.ui.screens.livetv

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    // Current index in filtered list for channel up/down
    val currentChannelIndex: Int = -1,
    // Backup stream failover
    val isFailingOver: Boolean = false,
    val failoverMessage: String = "",
    // Category sidebar visibility
    val showCategories: Boolean = true,
    // Quick menu (OK button popup)
    val showQuickMenu: Boolean = false,
    val lastWatchedChannel: Channel? = null,
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

    // Failover tracking
    private var failoverAttempts = 0
    private val maxFailoverAttempts = 3
    private var isPlayerReleased = false
    private var failoverMessageJob: Job? = null

    /**
     * Zero-buffer ExoPlayer configuration for live TV:
     * - minBufferMs = 1500ms: Absolute minimum data to hold before starting playback
     * - maxBufferMs = 8000ms: Small ceiling so player never stockpiles and wastes memory
     * - bufferForPlaybackMs = 500ms: Start playback after only 500ms of data (near-instant)
     * - bufferForPlaybackAfterRebufferMs = 1000ms: After a rebuffer, resume after 1s (fast recovery)
     * - HTTP connect/read timeouts = 8s: Fail fast on dead streams
     * - prioritizeTimeOverSizeThresholds = true: Prefer time-based buffering, not byte-based
     */
    val player: ExoPlayer = run {
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                /* minBufferMs */ 1_500,
                /* maxBufferMs */ 8_000,
                /* bufferForPlaybackMs */ 500,
                /* bufferForPlaybackAfterRebufferMs */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()

        // HTTP data source with tight timeouts for fast failover
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(application, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(application)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
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
                })
            }
    }

    init {
        loadChannels()
        observeFavorites()
        loadEpgInBackground()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val playlists = settingsDataStore.playlists.first()
                val enabledUrls = playlists.filter { it.enabled }.map { it.url }
                val channels = withContext(Dispatchers.IO) {
                    if (enabledUrls.size == 1) {
                        channelRepository.loadChannels(enabledUrls.first())
                    } else if (enabledUrls.isNotEmpty()) {
                        channelRepository.loadMultipleChannels(enabledUrls)
                    } else {
                        emptyList()
                    }
                }

                // Extract groups efficiently — use LinkedHashSet to preserve insertion order
                // and avoid creating intermediate List from map().distinct()
                val groupSet = LinkedHashSet<String>(channels.size / 10)
                for (ch in channels) { groupSet.add(ch.group) }

                // Sort groups: Favorites first, USA-related second, then alphabetically
                val sortedGroups = groupSet.sortedWith(
                    compareByDescending<String> { group ->
                        val lower = group.lowercase()
                        lower.contains("usa") || lower.contains("us ") ||
                        lower.startsWith("us:") || lower.startsWith("us|") ||
                        lower.contains("united states") || lower.contains("american")
                    }.thenBy { it.lowercase() }
                )
                // Add "★ Favorites" as the first category
                val groups = listOf("★ Favorites") + sortedGroups

                // Don't set filteredChannels — null means "use channels directly"
                // This avoids duplicating the full list (thousands of Channel objects) in state
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    channels = channels,
                    groups = groups,
                    totalChannels = channels.size
                ).withFilteredChannels(null)

                // Auto-play last watched channel
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
                // Find channel and its index in a single pass instead of find() + indexOf()
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
                        currentChannelIndex = foundIndex
                    )
                    safePlayChannel(foundChannel)
                    loadEpgForChannel(foundChannel)
                }
            }
        } catch (e: Exception) {
            Log.e("LiveTvVM", "Auto-play last watched failed", e)
        }
    }

    private fun loadEpgInBackground() {
        viewModelScope.launch {
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

    fun onChannelSelected(channel: Channel) {
        val previousChannel = _uiState.value.selectedChannel
        val index = _uiState.value.filteredChannels.indexOf(channel)
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            isFullscreen = true,
            showOverlay = false,
            currentChannelIndex = index,
            lastWatchedChannel = previousChannel
        )
        safePlayChannel(channel)
        loadEpgForChannel(channel)
        // Persist last watched channel
        viewModelScope.launch {
            try { settingsDataStore.setLastWatchedChannelId(channel.id) } catch (_: Exception) {}
        }
    }

    /** Safe wrapper that catches all player exceptions */
    private fun safePlayChannel(channel: Channel) {
        if (isPlayerReleased) return
        failoverAttempts = 0
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
            isFailingOver = false,
            failoverMessage = ""
        )
    }

    private fun handlePlaybackError() {
        if (isPlayerReleased) return
        val currentChannel = _uiState.value.selectedChannel ?: return
        if (failoverAttempts >= maxFailoverAttempts) {
            _uiState.value = _uiState.value.copy(
                isFailingOver = false,
                failoverMessage = "No working backup found"
            )
            clearFailoverMessageAfterDelay()
            return
        }

        failoverAttempts++
        _uiState.value = _uiState.value.copy(
            isFailingOver = true,
            failoverMessage = "Switching to backup... ($failoverAttempts/$maxFailoverAttempts)"
        )

        viewModelScope.launch {
            try {
                val backupChannel = withContext(Dispatchers.IO) {
                    backupChannelRepository.findBackupStream(currentChannel.name)
                }
                if (isPlayerReleased) return@launch
                if (backupChannel != null && backupChannel.streamUrl != currentChannel.streamUrl) {
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
                        failoverMessage = "Playing from backup source"
                    )
                    clearFailoverMessageAfterDelay()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isFailingOver = false,
                        failoverMessage = "No backup available"
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

        val newIndex = if (state.currentChannelIndex <= 0) {
            channels.size - 1
        } else {
            state.currentChannelIndex - 1
        }

        val channel = channels[newIndex]
        _uiState.value = state.copy(
            selectedChannel = channel,
            currentChannelIndex = newIndex,
            showOverlay = true
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

        val newIndex = if (state.currentChannelIndex >= channels.size - 1) {
            0
        } else {
            state.currentChannelIndex + 1
        }

        val channel = channels[newIndex]
        _uiState.value = state.copy(
            selectedChannel = channel,
            currentChannelIndex = newIndex,
            showOverlay = true
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
        _uiState.value = _uiState.value.copy(showQuickMenu = true, showOverlay = true)
    }

    fun hideQuickMenu() {
        _uiState.value = _uiState.value.copy(showQuickMenu = false)
    }

    fun goToLastWatchedChannel() {
        val lastCh = _uiState.value.lastWatchedChannel ?: return
        hideQuickMenu()
        onChannelSelected(lastCh)
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
            // No filter active — reuse full channel list (null = no separate allocation)
            _uiState.value = state.withFilteredChannels(null)
            return
        }

        var filtered = state.channels

        if (hasGroup) {
            val group = state.selectedGroup!!
            if (group == "★ Favorites") {
                // Filter to only favorite channels
                val favIds = state.favoriteIds
                filtered = filtered.filter { favIds.contains(it.id) }
            } else {
                filtered = filtered.filter { it.group == group }
            }
        }

        if (hasSearch) {
            val query = state.searchQuery
            filtered = filtered.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
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
