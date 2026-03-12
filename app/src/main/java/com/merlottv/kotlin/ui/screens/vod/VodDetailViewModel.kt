package com.merlottv.kotlin.ui.screens.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.Meta
import com.merlottv.kotlin.domain.model.Stream
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodDetailUiState(
    val isLoading: Boolean = true,
    val meta: Meta? = null,
    val streams: List<Stream> = emptyList(),
    val isFavorite: Boolean = false
)

@HiltViewModel
class VodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addonRepository: AddonRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val type: String = savedStateHandle.get<String>("type") ?: "movie"
    private val id: String = savedStateHandle.get<String>("id") ?: ""

    private val _uiState = MutableStateFlow(VodDetailUiState())
    val uiState: StateFlow<VodDetailUiState> = _uiState.asStateFlow()

    init {
        loadMeta()
        checkFavorite()
    }

    private fun loadMeta() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val meta = addonRepository.getMeta(type, id)
            _uiState.value = _uiState.value.copy(isLoading = false, meta = meta)
        }
    }

    private fun checkFavorite() {
        viewModelScope.launch {
            val isFav = favoritesRepository.isFavoriteVod(id)
            _uiState.value = _uiState.value.copy(isFavorite = isFav)
        }
    }

    fun loadStreams() {
        viewModelScope.launch {
            val streams = addonRepository.getStreams(type, id)
            _uiState.value = _uiState.value.copy(streams = streams)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            favoritesRepository.toggleFavoriteVod(id)
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }
}
