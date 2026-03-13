package com.merlottv.kotlin.ui.screens.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.*
import com.merlottv.kotlin.domain.repository.EspnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GameDetailTab(val title: String) {
    BoxScore("Box Score"),
    PlayByPlay("Play-by-Play"),
    TeamStats("Team Stats")
}

data class GameDetailUiState(
    val isLoading: Boolean = true,
    val selectedTab: GameDetailTab = GameDetailTab.BoxScore,
    val summary: SportGameSummary? = null,
    val error: String? = null
)

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val espnRepository: EspnRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    fun load(leagueName: String, eventId: String) {
        if (_uiState.value.summary != null) return // Already loaded
        val league = try {
            SportsLeague.valueOf(leagueName.uppercase())
        } catch (_: Exception) {
            SportsLeague.NFL
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val summary = espnRepository.getGameSummary(league, eventId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    summary = summary,
                    error = if (summary == null) "Game data not available" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load: ${e.message}"
                )
            }
        }
    }

    fun selectTab(tab: GameDetailTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }
}
