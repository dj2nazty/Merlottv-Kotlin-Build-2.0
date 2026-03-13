package com.merlottv.kotlin.ui.screens.spacex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.SpaceXLaunch
import com.merlottv.kotlin.domain.repository.SpaceXRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SpaceXTab(val title: String) {
    Upcoming("Upcoming"),
    Past("Past")
}

data class SpaceXUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val selectedTab: SpaceXTab = SpaceXTab.Upcoming,
    val upcomingLaunches: List<SpaceXLaunch> = emptyList(),
    val pastLaunches: List<SpaceXLaunch> = emptyList(),
    val nextLaunch: SpaceXLaunch? = null,
    val countdownText: String = "",
    val error: String? = null
)

@HiltViewModel
class SpaceXViewModel @Inject constructor(
    private val repository: SpaceXRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpaceXUiState())
    val uiState: StateFlow<SpaceXUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    // NO init{} — lazy loading only when screen becomes visible

    fun onScreenVisible() {
        if (!_uiState.value.hasLoadedOnce) {
            loadData()
        }
    }

    fun selectTab(tab: SpaceXTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        if (tab == SpaceXTab.Past && _uiState.value.pastLaunches.isEmpty()) {
            loadPastLaunches()
        }
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val upcoming = repository.getUpcomingLaunches()
                val nextLaunch = upcoming.firstOrNull { it.netEpochMs > System.currentTimeMillis() }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    upcomingLaunches = upcoming,
                    nextLaunch = nextLaunch,
                    error = if (upcoming.isEmpty()) "No upcoming launches found" else null
                )

                // Start countdown if we have a next launch
                if (nextLaunch != null) {
                    startCountdown(nextLaunch.netEpochMs)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    error = "Failed to load: ${e.message}"
                )
            }
        }
    }

    private fun loadPastLaunches() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val past = repository.getPastLaunches(20)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pastLaunches = past,
                    error = if (past.isEmpty()) "No past launches found" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load: ${e.message}"
                )
            }
        }
    }

    private fun startCountdown(targetMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val diff = targetMs - now

                if (diff <= 0) {
                    _uiState.value = _uiState.value.copy(countdownText = "LIFTOFF!")
                    break
                }

                val days = diff / (1000 * 60 * 60 * 24)
                val hours = (diff / (1000 * 60 * 60)) % 24
                val minutes = (diff / (1000 * 60)) % 60
                val seconds = (diff / 1000) % 60

                val text = buildString {
                    if (days > 0) append("${days}d ")
                    if (days > 0 || hours > 0) append("${hours}h ")
                    append("${minutes}m ${seconds}s")
                }

                _uiState.value = _uiState.value.copy(countdownText = text)
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
