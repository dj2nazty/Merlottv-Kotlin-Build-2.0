package com.merlottv.kotlin.ui.screens.tvguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.repository.EpgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TvGuideUiState(
    val isLoading: Boolean = true,
    val channels: List<EpgChannel> = emptyList(),
    val error: String? = null
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
        viewModelScope.launch {
            _uiState.value = TvGuideUiState(isLoading = true)
            try {
                epgRepository.loadEpg(DefaultData.EPG_SOURCES.map { it.url })
                epgRepository.getAllEpgChannels().collect { channels ->
                    _uiState.value = TvGuideUiState(
                        isLoading = false,
                        channels = channels.filter { it.programs.isNotEmpty() }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = TvGuideUiState(isLoading = false, error = e.message)
            }
        }
    }
}
