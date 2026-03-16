package com.merlottv.kotlin.ui.screens.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.data.youtube.TmdbCastRepository
import com.merlottv.kotlin.domain.model.TmdbFilmCredit
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActorDetailUiState(
    val isLoading: Boolean = true,
    val personName: String = "",
    val filmography: List<TmdbFilmCredit> = emptyList()
)

@HiltViewModel
class ActorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tmdbCastRepository: TmdbCastRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val personId: Int = savedStateHandle.get<Int>("personId") ?: 0
    private val personName: String = savedStateHandle.get<String>("personName") ?: ""

    private val _uiState = MutableStateFlow(ActorDetailUiState(personName = personName))
    val uiState: StateFlow<ActorDetailUiState> = _uiState.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    init {
        loadFilmography()
        loadFavorites()
    }

    private fun loadFilmography() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val films = tmdbCastRepository.getFilmography(personId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    filmography = films
                )
                Log.d("ActorDetail", "Loaded ${films.size} credits for $personName")
            } catch (e: Exception) {
                Log.w("ActorDetail", "Failed to load filmography: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            val ids = favoritesRepository.getFavoriteVodIds().first()
            _favoriteIds.value = ids
        }
    }

    fun toggleFavorite(film: TmdbFilmCredit) {
        viewModelScope.launch {
            val imdbId = film.imdbId.ifEmpty { "tmdb:${film.id}" }
            favoritesRepository.toggleFavoriteVodWithMeta(
                imdbId,
                FavoriteVodMeta(
                    id = imdbId,
                    name = film.title,
                    poster = film.posterUrl,
                    type = film.type,
                    imdbRating = film.voteAverage,
                    description = "as ${film.character}"
                )
            )
            // Refresh favorite IDs
            _favoriteIds.value = favoritesRepository.getFavoriteVodIds().first()
        }
    }

    suspend fun resolveImdbId(film: TmdbFilmCredit): String? {
        if (film.imdbId.isNotEmpty()) return film.imdbId
        return tmdbCastRepository.getImdbId(film.id, film.type)
    }
}
