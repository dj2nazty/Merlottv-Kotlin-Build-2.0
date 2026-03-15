package com.merlottv.kotlin.ui.screens.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.CurrentWeather
import com.merlottv.kotlin.domain.model.DayForecast
import com.merlottv.kotlin.domain.model.RadarFrame
import com.merlottv.kotlin.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeatherUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val error: String? = null,
    val currentWeather: CurrentWeather? = null,
    val forecast: List<DayForecast> = emptyList(),
    val radarFrames: List<RadarFrame> = emptyList(),
    val radarAnimIndex: Int = 0,
    val zipCode: String = "43616",
    val showZipDialog: Boolean = false,
    val showFullscreenRadar: Boolean = false
)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var radarAnimJob: Job? = null

    // NO init{} — lazy loading only when screen becomes visible (same as SpaceX)

    fun onScreenVisible() {
        if (!_uiState.value.hasLoadedOnce) {
            viewModelScope.launch {
                val zip = settingsDataStore.weatherZipCode.first()
                _uiState.value = _uiState.value.copy(zipCode = zip)
                loadAll()
            }
        }
    }

    fun refresh() {
        loadAll()
    }

    fun changeZipCode(zip: String) {
        val trimmed = zip.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            settingsDataStore.setWeatherZipCode(trimmed)
            _uiState.value = _uiState.value.copy(zipCode = trimmed, showZipDialog = false)
            loadAll()
        }
    }

    fun toggleZipDialog() {
        _uiState.value = _uiState.value.copy(showZipDialog = !_uiState.value.showZipDialog)
    }

    fun dismissZipDialog() {
        _uiState.value = _uiState.value.copy(showZipDialog = false)
    }

    fun toggleFullscreenRadar() {
        _uiState.value = _uiState.value.copy(showFullscreenRadar = !_uiState.value.showFullscreenRadar)
    }

    fun dismissFullscreenRadar() {
        _uiState.value = _uiState.value.copy(showFullscreenRadar = false)
    }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val zip = _uiState.value.zipCode
                val weatherResult = repository.getCurrentAndForecast(zip)
                val radarFrames = repository.getRadarFrames()

                if (weatherResult != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        currentWeather = weatherResult.first,
                        forecast = weatherResult.second,
                        radarFrames = radarFrames,
                        radarAnimIndex = 0,
                        error = null
                    )
                    // Start radar animation if we have frames
                    if (radarFrames.isNotEmpty()) {
                        startRadarAnimation(radarFrames.size)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        error = "Could not load weather for ZIP: $zip"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    error = "Failed to load weather: ${e.message}"
                )
            }
        }
    }

    private fun startRadarAnimation(frameCount: Int) {
        radarAnimJob?.cancel()
        radarAnimJob = viewModelScope.launch {
            while (true) {
                delay(500) // cycle frames every 500ms
                val nextIndex = (_uiState.value.radarAnimIndex + 1) % frameCount
                _uiState.value = _uiState.value.copy(radarAnimIndex = nextIndex)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        radarAnimJob?.cancel()
    }
}
