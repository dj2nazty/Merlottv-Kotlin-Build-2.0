package com.merlottv.kotlin.ui.screens.channelbackup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.repository.BackupTvAddonRepository
import com.merlottv.kotlin.domain.model.BackupTvChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelBackupUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val selectedGenre: String = "All",
    val channels: List<BackupTvChannel> = emptyList(),
    val filteredChannels: List<BackupTvChannel> = emptyList(),
    val genres: List<String> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ChannelBackupViewModel @Inject constructor(
    private val repository: BackupTvAddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelBackupUiState())
    val uiState: StateFlow<ChannelBackupUiState> = _uiState.asStateFlow()

    /** Call when screen becomes visible — lazy-loads data on first visit only */
    fun onScreenVisible() {
        if (_uiState.value.hasLoadedOnce) return
        loadChannels(forceRefresh = false)
    }

    fun selectGenre(genre: String) {
        val all = _uiState.value.channels
        val filtered = if (genre == "All") all else all.filter { it.genre == genre }
        _uiState.value = _uiState.value.copy(
            selectedGenre = genre,
            filteredChannels = filtered
        )
    }

    fun refresh() {
        loadChannels(forceRefresh = true)
    }

    private fun loadChannels(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val channels = repository.getChannels(forceRefresh)
                if (channels.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        error = "No channels available"
                    )
                    return@launch
                }

                // Extract unique genres from data, prepend "All"
                val genres = listOf("All") + channels.map { it.genre }.distinct().sorted()
                val selected = _uiState.value.selectedGenre
                val filtered = if (selected == "All") channels else channels.filter { it.genre == selected }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    channels = channels,
                    filteredChannels = filtered,
                    genres = genres,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    error = e.message ?: "Failed to load channels"
                )
            }
        }
    }
}
