package com.merlottv.kotlin.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.repository.YouTubeRepository
import com.merlottv.kotlin.domain.model.YouTubeVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouTubeUiState(
    val isLoading: Boolean = false,
    val videos: List<YouTubeVideo> = emptyList(),
    val filteredVideos: List<YouTubeVideo> = emptyList(),
    val selectedChannel: String = "All",
    val error: String? = null
)

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun loadIfNeeded() {
        if (hasLoaded) return
        hasLoaded = true
        loadVideos()
    }

    private fun loadVideos(forceRefresh: Boolean = false) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val videos = youtubeRepository.fetchAllVideos(forceRefresh)
            val channel = _uiState.value.selectedChannel
            val filtered = if (channel == "All") videos else videos.filter { it.channelName == channel }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                videos = videos,
                filteredVideos = filtered
            )
        }
    }

    fun onChannelSelected(channelName: String) {
        val videos = _uiState.value.videos
        val filtered = if (channelName == "All") videos else videos.filter { it.channelName == channelName }
        _uiState.value = _uiState.value.copy(
            selectedChannel = channelName,
            filteredVideos = filtered
        )
    }

    fun getChannelNames(): List<String> {
        return listOf("All") + youtubeRepository.channels.map { it.channelName }
    }
}
