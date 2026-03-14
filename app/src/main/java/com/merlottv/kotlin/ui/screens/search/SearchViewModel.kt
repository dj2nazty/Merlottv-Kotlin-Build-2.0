package com.merlottv.kotlin.ui.screens.search

import android.util.Log
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false,
    val resultCount: Int = 0,       // Total results found so far
    val searchTimeMs: Long = 0L     // How long the search took
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val addonRepository: AddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private val searchDispatcher = Dispatchers.IO.limitedParallelism(8)

    // Cache fetched manifests so we don't re-fetch every search
    private var resolvedAddons: List<Addon>? = null

    // Search result cache — instant repeat searches (NuvioTV-style)
    private val searchCache = ConcurrentHashMap<String, List<MetaPreview>>()
    private val CACHE_MAX_SIZE = 50

    init {
        // Pre-fetch manifests in background so first search is fast
        viewModelScope.launch(Dispatchers.IO) {
            resolveAddons()
        }
    }

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
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false, resultCount = 0)
            return
        }

        // Check cache first — instant results for repeat searches
        val cacheKey = query.trim().lowercase()
        searchCache[cacheKey]?.let { cached ->
            Log.d("SearchVM", "Cache hit for '$query': ${cached.size} results")
            _uiState.value = _uiState.value.copy(
                results = cached,
                isLoading = false,
                resultCount = cached.size,
                searchTimeMs = 0
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // debounce
            val startTime = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val addons = resolveAddons()

                // === Progressive results — show results as each addon responds ===
                // Track accumulated results with thread-safe collection
                val accumulatedResults = java.util.Collections.synchronizedList(mutableListOf<MetaPreview>())
                var hasRenderedFirst = false

                supervisorScope {
                    // Launch each addon+type search as independent coroutine
                    val jobs = addons.flatMap { addon ->
                        listOf("movie", "series").map { type ->
                            async(searchDispatcher) {
                                try {
                                    val results = addonRepository.searchCatalog(addon, type, query)
                                    if (results.isNotEmpty()) {
                                        accumulatedResults.addAll(results)
                                        // Deduplicate by type:id (NuvioTV-style)
                                        val unique = synchronized(accumulatedResults) {
                                            accumulatedResults.distinctBy { "${it.type}:${it.id}" }
                                        }

                                        // Progressive rendering — update UI as results arrive
                                        if (!hasRenderedFirst) {
                                            hasRenderedFirst = true
                                            // First result renders instantly
                                            _uiState.value = _uiState.value.copy(
                                                results = unique,
                                                isLoading = true, // Still loading more
                                                resultCount = unique.size
                                            )
                                        } else {
                                            // Subsequent results — adaptive debounce
                                            _uiState.value = _uiState.value.copy(
                                                results = unique,
                                                resultCount = unique.size
                                            )
                                        }
                                    }
                                    results
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                val elapsed = System.currentTimeMillis() - startTime
                // Final deduplicated results
                val finalResults = accumulatedResults.distinctBy { "${it.type}:${it.id}" }

                Log.d("SearchVM", "Search '$query': ${finalResults.size} results in ${elapsed}ms")

                // Cache results for instant repeat searches
                if (finalResults.isNotEmpty()) {
                    // Evict oldest entries if cache is full
                    if (searchCache.size >= CACHE_MAX_SIZE) {
                        searchCache.keys.take(searchCache.size - CACHE_MAX_SIZE + 1).forEach {
                            searchCache.remove(it)
                        }
                    }
                    searchCache[cacheKey] = finalResults
                }

                _uiState.value = _uiState.value.copy(
                    results = finalResults,
                    isLoading = false,
                    resultCount = finalResults.size,
                    searchTimeMs = elapsed
                )
            } catch (e: Exception) {
                Log.e("SearchVM", "Search failed: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
