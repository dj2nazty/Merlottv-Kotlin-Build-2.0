package com.merlottv.kotlin.ui.screens.livetv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.repository.BackupChannelRepository
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.domain.repository.ChannelRepository
import com.merlottv.kotlin.domain.repository.EpgRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val filteredChannels: List<Channel> = emptyList(),
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
    val showCategories: Boolean = true
)

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

    val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val resolution = if (videoSize.width > 0 && videoSize.height > 0) {
                    "${videoSize.width}x${videoSize.height}"
                } else ""
                _uiState.value = _uiState.value.copy(videoResolution = resolution)
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError()
            }
        })
    }

    init {
        loadChannels()
        observeFavorites()
        loadEpgInBackground()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val playlists = settingsDataStore.playlists.first()
            val enabledUrls = playlists.filter { it.enabled }.map { it.url }
            val channels = if (enabledUrls.size == 1) {
                channelRepository.loadChannels(enabledUrls.first())
            } else if (enabledUrls.isNotEmpty()) {
                channelRepository.loadMultipleChannels(enabledUrls)
            } else {
                emptyList()
            }

            // Sort groups: USA-related first, then alphabetically
            val groups = channels.map { it.group }.distinct().sortedWith(
                compareByDescending<String> { group ->
                    val lower = group.lowercase()
                    lower.contains("usa") || lower.contains("us ") ||
                    lower.startsWith("us:") || lower.startsWith("us|") ||
                    lower.contains("united states") || lower.contains("american")
                }.thenBy { it.lowercase() }
            )

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                channels = channels,
                filteredChannels = channels,
                groups = groups,
                totalChannels = channels.size
            )

            // Auto-play last watched channel
            autoPlayLastWatched(channels)
        }
    }

    private suspend fun autoPlayLastWatched(channels: List<Channel>) {
        val lastId = settingsDataStore.lastWatchedChannelId.first()
        if (lastId.isNotEmpty()) {
            val channel = channels.find { it.id == lastId }
            if (channel != null) {
                val index = channels.indexOf(channel)
                _uiState.value = _uiState.value.copy(
                    selectedChannel = channel,
                    isFullscreen = true,
                    showOverlay = false,
                    currentChannelIndex = index
                )
                playChannel(channel)
                loadEpgForChannel(channel)
            }
        }
    }

    private fun loadEpgInBackground() {
        viewModelScope.launch {
            try {
                // Merge default EPG sources + custom user EPG sources
                val defaultUrls = DefaultData.EPG_SOURCES.map { it.url }
                val customSources = settingsDataStore.customEpgSources.first()
                val customUrls = customSources.filter { it.enabled }.map { it.url }
                val allUrls = (defaultUrls + customUrls).distinct()
                epgRepository.loadEpg(allUrls)
            } catch (_: Exception) {}
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.getFavoriteChannelIds().collect { ids ->
                _uiState.value = _uiState.value.copy(favoriteIds = ids)
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
        val index = _uiState.value.filteredChannels.indexOf(channel)
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            isFullscreen = true,
            showOverlay = false,
            currentChannelIndex = index
        )
        playChannel(channel)
        loadEpgForChannel(channel)
        // Persist last watched channel
        viewModelScope.launch {
            settingsDataStore.setLastWatchedChannelId(channel.id)
        }
    }

    private fun playChannel(channel: Channel) {
        failoverAttempts = 0
        player.stop()
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        player.play()
        _uiState.value = _uiState.value.copy(
            videoResolution = "",
            isFailingOver = false,
            failoverMessage = ""
        )
    }

    private fun handlePlaybackError() {
        val currentChannel = _uiState.value.selectedChannel ?: return
        if (failoverAttempts >= maxFailoverAttempts) {
            _uiState.value = _uiState.value.copy(
                isFailingOver = false,
                failoverMessage = "No working backup found"
            )
            return
        }

        failoverAttempts++
        _uiState.value = _uiState.value.copy(
            isFailingOver = true,
            failoverMessage = "Switching to backup... (attempt $failoverAttempts/$maxFailoverAttempts)"
        )

        viewModelScope.launch {
            try {
                val backupChannel = backupChannelRepository.findBackupStream(currentChannel.name)
                if (backupChannel != null && backupChannel.streamUrl != currentChannel.streamUrl) {
                    // Swap to backup stream without resetting failoverAttempts
                    player.stop()
                    player.setMediaItem(MediaItem.fromUri(backupChannel.streamUrl))
                    player.prepare()
                    player.play()
                    _uiState.value = _uiState.value.copy(
                        isFailingOver = false,
                        failoverMessage = "Playing from backup source"
                    )
                    // Clear the message after 3 seconds
                    kotlinx.coroutines.delay(3000)
                    _uiState.value = _uiState.value.copy(failoverMessage = "")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isFailingOver = false,
                        failoverMessage = "No backup available for this channel"
                    )
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isFailingOver = false,
                    failoverMessage = "Backup search failed"
                )
            }
        }
    }

    private fun loadEpgForChannel(channel: Channel) {
        viewModelScope.launch {
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
        playChannel(channel)
        loadEpgForChannel(channel)
        viewModelScope.launch { settingsDataStore.setLastWatchedChannelId(channel.id) }
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
        playChannel(channel)
        loadEpgForChannel(channel)
        viewModelScope.launch { settingsDataStore.setLastWatchedChannelId(channel.id) }
    }

    fun toggleOverlay() {
        _uiState.value = _uiState.value.copy(showOverlay = !_uiState.value.showOverlay)
    }

    fun hideOverlay() {
        _uiState.value = _uiState.value.copy(showOverlay = false)
    }

    fun exitFullscreen() {
        _uiState.value = _uiState.value.copy(isFullscreen = false, showOverlay = false)
    }

    fun stopPlayback() {
        player.stop()
    }

    fun resumePlayback() {
        if (_uiState.value.selectedChannel != null && !player.isPlaying) {
            player.play()
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            favoritesRepository.toggleFavoriteChannel(channelId)
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = state.channels

        state.selectedGroup?.let { group ->
            filtered = filtered.filter { it.group == group }
        }

        if (state.searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(state.searchQuery, ignoreCase = true) ||
                it.group.contains(state.searchQuery, ignoreCase = true)
            }
        }

        _uiState.value = state.copy(filteredChannels = filtered)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
