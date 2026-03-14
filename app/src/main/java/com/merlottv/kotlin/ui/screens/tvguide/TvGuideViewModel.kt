package com.merlottv.kotlin.ui.screens.tvguide

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
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
    val loadingMessage: String = "Loading TV Guide...",
    val selectedProgram: EpgEntry? = null,
    val isSyncing: Boolean = false
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
                loadingMessage = "Loading TV Guide..."
            )
            try {
                val allUrls = getEpgUrls()

                // Step 1: Try loading from DB (instant if data exists)
                val cachedChannels = try {
                    epgRepository.getAllEpgChannels().first()
                } catch (_: Exception) {
                    emptyList()
                }

                val filtered = cachedChannels
                    .filter { it.programs.isNotEmpty() }
                    .sortedBy { it.name.lowercase() }

                if (filtered.isNotEmpty()) {
                    // Show cached data immediately
                    _uiState.value = TvGuideUiState(
                        isLoading = false,
                        channels = filtered
                    )
                    Log.d("TvGuideVM", "Loaded ${filtered.size} channels from cache")
                }

                // Step 2: If stale, refresh in background
                if (epgRepository.isEpgStale()) {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = true,
                        loadingMessage = if (filtered.isEmpty())
                            "Downloading EPG data from ${allUrls.size} sources..."
                        else "Updating EPG data..."
                    )
                    // If no cached data, show loading state
                    if (filtered.isEmpty()) {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }

                    withContext(Dispatchers.IO) {
                        epgRepository.loadEpg(allUrls)
                    }
                }

                // Step 3: Observe live updates from Room
                epgRepository.getAllEpgChannels().collect { channels ->
                    val updated = channels
                        .filter { it.programs.isNotEmpty() }
                        .sortedBy { it.name.lowercase() }
                    _uiState.value = TvGuideUiState(
                        isLoading = false,
                        channels = updated,
                        isSyncing = false
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

    private suspend fun getEpgUrls(): List<String> {
        val defaultUrls = DefaultData.EPG_SOURCES.map { it.url }
        val customSources = settingsDataStore.customEpgSources.first()
        val customUrls = customSources.filter { it.enabled }.map { it.url }
        return (defaultUrls + customUrls).distinct()
    }

    fun selectProgram(program: EpgEntry?) {
        _uiState.value = _uiState.value.copy(selectedProgram = program)
    }

    fun retry() {
        loadEpg()
    }
}
