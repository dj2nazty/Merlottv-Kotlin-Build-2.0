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

enum class TeamDetailTab(val title: String) {
    Roster("Roster"),
    Schedule("Schedule")
}

data class TeamDetailUiState(
    val isLoading: Boolean = true,
    val selectedTab: TeamDetailTab = TeamDetailTab.Roster,
    val teamInfo: SportTeamInfo? = null,
    val roster: List<SportPlayer> = emptyList(),
    val schedule: List<SportScheduleEvent> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class TeamDetailViewModel @Inject constructor(
    private val espnRepository: EspnRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamDetailUiState())
    val uiState: StateFlow<TeamDetailUiState> = _uiState.asStateFlow()

    fun load(leagueName: String, teamId: String) {
        if (_uiState.value.teamInfo != null) return // Already loaded
        val league = try {
            SportsLeague.valueOf(leagueName.uppercase())
        } catch (_: Exception) {
            SportsLeague.NFL
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Load team info, roster, and schedule concurrently
                val teams = espnRepository.getTeams(league)
                val teamInfo = teams.find { it.id == teamId }
                val roster = espnRepository.getTeamRoster(league, teamId)
                val schedule = espnRepository.getTeamSchedule(league, teamId)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    teamInfo = teamInfo,
                    roster = roster,
                    schedule = schedule,
                    error = if (teamInfo == null) "Team not found" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load: ${e.message}"
                )
            }
        }
    }

    fun selectTab(tab: TeamDetailTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }
}
