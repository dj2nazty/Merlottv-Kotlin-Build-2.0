package com.merlottv.kotlin.ui.screens.tvguide

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.repository.EpgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TvGuideUiState(
    val isLoading: Boolean = true,
    val channels: List<EpgChannel> = emptyList(),
    val error: String? = null,
    val loadingMessage: String = "Loading TV Guide..."
)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    private val epgRepository: EpgRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvGuideUiState())
    val uiState: StateFlow<TvGuideUiState> = _uiState.asStateFlow()

    init {
        loadEpg()
    }

    private fun loadEpg() {
        viewModelScope.launch {
            _uiState.value = TvGuideUiState(
                isLoading = true,
                loadingMessage = "Downloading EPG data..."
            )
            try {
                // Merge default + custom EPG sources
                val defaultUrls = DefaultData.EPG_SOURCES.map { it.url }
                val customSources = settingsDataStore.customEpgSources.first()
                val customUrls = customSources.filter { it.enabled }.map { it.url }
                val allUrls = (defaultUrls + customUrls).distinct()

                _uiState.value = _uiState.value.copy(
                    loadingMessage = "Downloading EPG data from ${allUrls.size} sources..."
                )

                withContext(Dispatchers.IO) {
                    epgRepository.loadEpg(allUrls)
                }

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
                Log.e("TvGuideVM", "Failed to load EPG", e)
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
