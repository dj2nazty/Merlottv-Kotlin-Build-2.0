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

enum class SportsTab(val title: String) {
    Scores("Scores"),
    Standings("Standings"),
    Teams("Teams")
}

data class SportsUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val selectedTab: SportsTab = SportsTab.Scores,
    val selectedLeague: SportsLeague = SportsLeague.NFL,
    val scores: List<SportScore> = emptyList(),
    val standings: List<SportConference> = emptyList(),
    val teams: List<SportTeamInfo> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SportsViewModel @Inject constructor(
    private val espnRepository: EspnRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SportsUiState())
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()

    // NO init{} — lazy loading only when screen becomes visible

    fun onScreenVisible() {
        if (!_uiState.value.hasLoadedOnce) {
            loadData()
        }
    }

    fun selectTab(tab: SportsTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        loadData()
    }

    fun selectLeague(league: SportsLeague) {
        if (league == _uiState.value.selectedLeague) return
        _uiState.value = _uiState.value.copy(selectedLeague = league)
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val league = _uiState.value.selectedLeague
                when (_uiState.value.selectedTab) {
                    SportsTab.Scores -> {
                        val scores = espnRepository.getScoreboard(league)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            hasLoadedOnce = true,
                            scores = scores,
                            error = if (scores.isEmpty()) "No games found" else null
                        )
                    }
                    SportsTab.Standings -> {
                        val standings = espnRepository.getStandings(league)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            hasLoadedOnce = true,
                            standings = standings,
                            error = if (standings.isEmpty()) "No standings available" else null
                        )
                    }
                    SportsTab.Teams -> {
                        val teams = espnRepository.getTeams(league)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            hasLoadedOnce = true,
                            teams = teams,
                            error = if (teams.isEmpty()) "No teams found" else null
                        )
                    }
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
}
