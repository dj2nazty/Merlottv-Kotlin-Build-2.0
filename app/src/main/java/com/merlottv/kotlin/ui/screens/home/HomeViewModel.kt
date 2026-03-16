package com.merlottv.kotlin.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.WatchProgressDataStore
import com.merlottv.kotlin.data.local.WatchProgressItem
import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

data class CatalogRow(
    val title: String,
    val items: List<MetaPreview>
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val featuredItems: List<MetaPreview> = emptyList(),
    val continueWatching: List<WatchProgressItem> = emptyList(),
    val catalogRows: List<CatalogRow> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val watchProgressDataStore: WatchProgressDataStore,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val favoriteVodIds: StateFlow<Set<String>> = favoritesRepository.getFavoriteVodIds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Limit concurrent catalog HTTP requests — max 6 instead of 30+ simultaneous
    private val catalogDispatcher = Dispatchers.IO.limitedParallelism(6)

    init {
        loadCatalogs()
        loadContinueWatching()
    }

    private fun loadContinueWatching() {
        viewModelScope.launch {
            watchProgressDataStore.getContinueWatchingItems().collect { items ->
                _uiState.value = _uiState.value.copy(continueWatching = items)
            }
        }
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            Log.d("HomeViewModel", "Starting loadCatalogs")
            try {
                val addons = addonRepository.getAllAddons().first()
                Log.d("HomeViewModel", "Got ${addons.size} addons: ${addons.map { it.name }}")

                val manifests = supervisorScope {
                    addons.map { addon ->
                        async(Dispatchers.IO) {
                            try {
                                val m = addonRepository.fetchManifest(addon.url)
                                Log.d("HomeViewModel", "Manifest for ${addon.name}: catalogs=${m?.catalogs?.size ?: 0}")
                                m ?: addon
                            } catch (e: Exception) {
                                Log.e("HomeViewModel", "Manifest fetch failed for ${addon.name}: ${e.message}")
                                addon
                            }
                        }
                    }.awaitAll()
                }

                data class CatalogJob(
                    val addonName: String,
                    val catalogName: String,
                    val catalogId: String,
                    val type: String,
                    val addonUrl: String
                )

                val jobs = mutableListOf<CatalogJob>()
                for (manifest in manifests) {
                    for (catalog in manifest.catalogs) {
                        val requiresSpecial = catalog.extra.any {
                            it.isRequired && it.name != "genre" && it.name != "search"
                        }
                        if (requiresSpecial) continue
                        jobs.add(CatalogJob(manifest.name, catalog.name, catalog.id, catalog.type, manifest.url))
                    }
                }
                // Hide Torbox "Your Media" catalogs from display
                jobs.removeAll { it.addonName.contains("torbox", true) && it.catalogName.contains("your media", true) }
                Log.d("HomeViewModel", "Total catalog jobs: ${jobs.size}")

                // Simple awaitAll with bounded dispatcher — single state emission
                // avoids the overhead of Channel-based progressive loading which
                // caused excessive recompositions on the emulator
                val rows = supervisorScope {
                    jobs.map { job ->
                        async(catalogDispatcher) {
                            try {
                                val addon = manifests.find { it.url == job.addonUrl } ?: return@async null
                                val items = addonRepository.getCatalog(addon, job.type, job.catalogId)
                                if (items.isNotEmpty()) {
                                    val typeLabel = when (job.type) {
                                        "movie" -> "Movies"
                                        "series" -> "Series"
                                        else -> job.type
                                    }
                                    CatalogRow(
                                        title = "${job.catalogName} $typeLabel — ${job.addonName}",
                                        items = items
                                    )
                                } else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                val sorted = rows.sortedWith(compareBy { row ->
                    val t = row.title.lowercase()
                    when {
                        "popular" in t -> 0
                        "new" in t -> 1
                        "featured" in t -> 2
                        "netflix" in t -> 3
                        "disney" in t -> 4
                        "prime" in t -> 5
                        "hbo" in t -> 6
                        "apple" in t -> 7
                        "paramount" in t -> 8
                        "peacock" in t -> 9
                        "imdb" in t -> 10
                        else -> 15
                    }
                })

                // Collect top movies for the hero carousel — prefer items with landscape background images
                val heroItems = sorted
                    .filter { row ->
                        val t = row.title.lowercase()
                        ("movie" in t) && ("popular" in t || "new" in t || "featured" in t || "trending" in t || "top" in t)
                    }
                    .flatMap { it.items }
                    .distinctBy { it.id }
                    .filter { it.background.isNotEmpty() || it.poster.isNotEmpty() }
                    // Sort: items with background image first (they look much better in the carousel)
                    .sortedByDescending { it.background.isNotEmpty() }
                    .take(8)
                    .ifEmpty {
                        // Fallback: just take first items from the first row
                        sorted.firstOrNull()?.items?.take(5) ?: emptyList()
                    }

                Log.d("HomeViewModel", "Loaded ${sorted.size} catalog rows, ${heroItems.size} hero items")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    featuredItems = heroItems,
                    catalogRows = sorted,
                    error = null
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "loadCatalogs failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun toggleFavorite(item: MetaPreview) {
        viewModelScope.launch {
            favoritesRepository.toggleFavoriteVodWithMeta(
                item.id,
                FavoriteVodMeta(
                    id = item.id,
                    name = item.name,
                    poster = item.poster,
                    type = item.type,
                    imdbRating = item.imdbRating,
                    description = item.description
                )
            )
        }
    }
}
