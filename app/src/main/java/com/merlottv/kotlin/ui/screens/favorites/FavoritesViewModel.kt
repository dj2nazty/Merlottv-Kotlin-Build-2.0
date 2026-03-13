package com.merlottv.kotlin.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val favoriteChannelIds: Set<String> = emptySet(),
    val favoriteVodIds: Set<String> = emptySet(),
    val vodMetas: Map<String, FavoriteVodMeta> = emptyMap(),
    val selectedTab: String = "All" // "All", "Movies", "Series", "Channels"
) {
    val filteredVodMetas: List<FavoriteVodMeta>
        get() {
            val allMetas = favoriteVodIds.map { id ->
                vodMetas[id] ?: FavoriteVodMeta(
                    id = id,
                    name = id, // Fallback to ID if no metadata stored
                    poster = "",
                    type = "movie"
                )
            }
            return when (selectedTab) {
                "Movies" -> allMetas.filter { it.type == "movie" }
                "Series" -> allMetas.filter { it.type == "series" }
                else -> allMetas
            }
        }
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoritesRepository.getFavoriteChannelIds().collect { ids ->
                _uiState.value = _uiState.value.copy(favoriteChannelIds = ids)
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoriteVodIds().collect { ids ->
                _uiState.value = _uiState.value.copy(favoriteVodIds = ids)
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoriteVodMetas().collect { metas ->
                _uiState.value = _uiState.value.copy(vodMetas = metas)
            }
        }
    }

    fun selectTab(tab: String) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }
}
