package com.merlottv.kotlin.ui.screens.livetv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.repository.ChannelRepository
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
    val favoriteIds: Set<String> = emptySet()
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    application: Application,
    private val channelRepository: ChannelRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsDataStore: SettingsDataStore
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    init {
        loadChannels()
        observeFavorites()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val url = settingsDataStore.playlistUrl.first()
            val channels = channelRepository.loadChannels(url)
            val groups = channels.map { it.group }.distinct().sorted()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                channels = channels,
                filteredChannels = channels,
                groups = groups,
                totalChannels = channels.size
            )
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
        _uiState.value = _uiState.value.copy(selectedGroup = group)
        applyFilters()
    }

    fun onChannelSelected(channel: Channel) {
        _uiState.value = _uiState.value.copy(selectedChannel = channel)
        player.stop()
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        player.play()
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
