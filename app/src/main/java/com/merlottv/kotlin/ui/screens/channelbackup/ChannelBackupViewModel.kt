package com.merlottv.kotlin.ui.screens.channelbackup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.repository.BackupTvAddonRepository
import com.merlottv.kotlin.data.repository.TvPassRepository
import com.merlottv.kotlin.domain.model.BackupTvChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BackupTab { USA_TV, TV_PASS }

data class ChannelBackupUiState(
    val activeTab: BackupTab = BackupTab.USA_TV,

    // USA TV state
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val selectedGenre: String = "All",
    val channels: List<BackupTvChannel> = emptyList(),
    val filteredChannels: List<BackupTvChannel> = emptyList(),
    val genres: List<String> = emptyList(),
    val error: String? = null,

    // TV Pass state
    val tvPassLoading: Boolean = false,
    val tvPassHasLoadedOnce: Boolean = false,
    val tvPassSelectedGenre: String = "All",
    val tvPassChannels: List<BackupTvChannel> = emptyList(),
    val tvPassFilteredChannels: List<BackupTvChannel> = emptyList(),
    val tvPassGenres: List<String> = emptyList(),
    val tvPassError: String? = null
)

@HiltViewModel
class ChannelBackupViewModel @Inject constructor(
    private val repository: BackupTvAddonRepository,
    private val tvPassRepository: TvPassRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelBackupUiState())
    val uiState: StateFlow<ChannelBackupUiState> = _uiState.asStateFlow()

    /** Call when screen becomes visible — lazy-loads data on first visit only */
    fun onScreenVisible() {
        if (!_uiState.value.hasLoadedOnce) {
            loadChannels(forceRefresh = false)
        }
    }

    // ─── Tab switching ───

    fun switchTab(tab: BackupTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
        // Lazy-load TV Pass on first switch
        if (tab == BackupTab.TV_PASS && !_uiState.value.tvPassHasLoadedOnce) {
            loadTvPassChannels(forceRefresh = false)
        }
    }

    // ─── USA TV ───

    fun selectGenre(genre: String) {
        val all = _uiState.value.channels
        val filtered = if (genre == "All") all else all.filter { it.genre == genre }
        _uiState.value = _uiState.value.copy(
            selectedGenre = genre,
            filteredChannels = filtered
        )
    }

    fun refresh() {
        if (_uiState.value.activeTab == BackupTab.USA_TV) {
            loadChannels(forceRefresh = true)
        } else {
            loadTvPassChannels(forceRefresh = true)
        }
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

    // ─── TV Pass ───

    fun selectTvPassGenre(genre: String) {
        val all = _uiState.value.tvPassChannels
        val filtered = if (genre == "All") all else all.filter { it.genre == genre }
        _uiState.value = _uiState.value.copy(
            tvPassSelectedGenre = genre,
            tvPassFilteredChannels = filtered
        )
    }

    private fun loadTvPassChannels(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tvPassLoading = true, tvPassError = null)
            try {
                val channels = tvPassRepository.getChannels(forceRefresh)
                if (channels.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        tvPassLoading = false,
                        tvPassHasLoadedOnce = true,
                        tvPassError = "No channels available"
                    )
                    return@launch
                }

                val genres = listOf("All") + channels.map { it.genre }.distinct().sorted()
                val selected = _uiState.value.tvPassSelectedGenre
                val filtered = if (selected == "All") channels else channels.filter { it.genre == selected }

                _uiState.value = _uiState.value.copy(
                    tvPassLoading = false,
                    tvPassHasLoadedOnce = true,
                    tvPassChannels = channels,
                    tvPassFilteredChannels = filtered,
                    tvPassGenres = genres,
                    tvPassError = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    tvPassLoading = false,
                    tvPassHasLoadedOnce = true,
                    tvPassError = e.message ?: "Failed to load TV Pass channels"
                )
            }
        }
    }
}
