package com.merlottv.kotlin.ui.screens.tvguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.repository.EpgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TvGuideUiState(
    val isLoading: Boolean = true,
    val channels: List<EpgChannel> = emptyList(),
    val error: String? = null,
    val loadingMessage: String = "Loading TV Guide..."
)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    private val epgRepository: EpgRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvGuideUiState())
    val uiState: StateFlow<TvGuideUiState> = _uiState.asStateFlow()

    init {
        loadEpg()
    }

    private fun loadEpg() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = TvGuideUiState(
                isLoading = true,
                loadingMessage = "Downloading EPG data from ${DefaultData.EPG_SOURCES.size} sources..."
            )
            try {
                epgRepository.loadEpg(DefaultData.EPG_SOURCES.map { it.url })

                _uiState.value = _uiState.value.copy(loadingMessage = "Processing program data...")

                epgRepository.getAllEpgChannels().collect { channels ->
                    val filtered = channels.filter { it.programs.isNotEmpty() }
                        .sortedBy { it.name.lowercase() }
                    _uiState.value = TvGuideUiState(
                        isLoading = false,
                        channels = filtered
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = TvGuideUiState(
                    isLoading = false,
                    error = "Failed to load EPG: ${e.message}"
                )
            }
        }
    }

    fun retry() {
        loadEpg()
    }
}
