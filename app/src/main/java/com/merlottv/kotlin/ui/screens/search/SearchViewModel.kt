package com.merlottv.kotlin.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val addonRepository: AddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private val searchDispatcher = Dispatchers.IO.limitedParallelism(6)

    // Cache fetched manifests so we don't re-fetch every search
    private var resolvedAddons: List<Addon>? = null

    init {
        // Pre-fetch manifests in background so first search is fast
        viewModelScope.launch(Dispatchers.IO) {
            resolveAddons()
        }
    }

    /**
     * Fetch manifests for all addons to discover their actual catalogs.
     * Without this, only Cinemeta (which has hardcoded catalogs) supports search.
     * With manifests, Torbox, Torrentio, IMDB Catalogs, Netflix etc. catalogs are discovered.
     */
    private suspend fun resolveAddons(): List<Addon> {
        resolvedAddons?.let { return it }
        val base = addonRepository.getAllAddons().first()
        val resolved = supervisorScope {
            base.map { addon ->
                async(Dispatchers.IO) {
                    try {
                        addonRepository.fetchManifest(addon.url) ?: addon
                    } catch (_: Exception) {
                        addon
                    }
                }
            }.awaitAll()
        }
        resolvedAddons = resolved
        return resolved
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Use resolved addons (with fetched manifests) for proper catalog discovery
                val addons = resolveAddons()

                // Search ALL addons in parallel (including Torbox, Torrentio)
                val allResults = supervisorScope {
                    addons.flatMap { addon ->
                        listOf("movie", "series").map { type ->
                            async(searchDispatcher) {
                                try {
                                    addonRepository.searchCatalog(addon, type, query)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }
                    }.awaitAll().flatten()
                }
                val unique = allResults.distinctBy { it.id }
                _uiState.value = _uiState.value.copy(results = unique, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
