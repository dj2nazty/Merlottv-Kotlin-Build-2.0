package com.merlottv.kotlin.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.SettingsDataStore
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class CatalogRow(
    val key: String = "",
    val title: String,
    val items: List<MetaPreview>
)

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val featuredItems: List<MetaPreview> = emptyList(),
    val continueWatching: List<WatchProgressItem> = emptyList(),
    val catalogRows: List<CatalogRow> = emptyList(),
    val error: String? = null,
    val inTheaterIds: Set<String> = emptySet()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val watchProgressDataStore: WatchProgressDataStore,
    private val favoritesRepository: FavoritesRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val favoriteVodIds: StateFlow<Set<String>> = favoritesRepository.getFavoriteVodIds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Limit concurrent catalog HTTP requests — max 12 for faster startup
    private val catalogDispatcher = Dispatchers.IO.limitedParallelism(4)

    private var catalogLoadJob: kotlinx.coroutines.Job? = null
    private var reloadDebounceJob: kotlinx.coroutines.Job? = null
    private val initTime = System.currentTimeMillis()

    init {
        loadCatalogs()
        loadContinueWatching()
        // Watch for category order/hidden changes and reload (skip first 3s after init)
        viewModelScope.launch {
            settingsDataStore.homeCategoryOrder.collect {
                if (System.currentTimeMillis() - initTime < 3000) return@collect
                scheduleReload("order changed")
            }
        }
        viewModelScope.launch {
            settingsDataStore.homeHiddenCategories.collect {
                if (System.currentTimeMillis() - initTime < 3000) return@collect
                scheduleReload("hidden changed")
            }
        }
    }

    /** Debounce reload — both order and hidden may change at the same time */
    private fun scheduleReload(reason: String) {
        reloadDebounceJob?.cancel()
        reloadDebounceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500) // Wait 500ms for both saves to settle
            Log.d("HomeViewModel", "Reloading catalogs: $reason")
            catalogLoadJob?.cancel()
            loadCatalogs()
        }
    }

    /** Public: force a full catalog reload */
    fun reloadCatalogs() {
        catalogLoadJob?.cancel()
        loadCatalogs()
    }

    private fun loadContinueWatching() {
        viewModelScope.launch {
            watchProgressDataStore.getContinueWatchingItems().collect { items ->
                _uiState.value = _uiState.value.copy(continueWatching = items)
            }
        }
    }

    private fun loadCatalogs() {
        catalogLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            Log.d("HomeViewModel", "Starting loadCatalogs")
            try {
                val addons = addonRepository.getEnabledAddons().first()
                Log.d("HomeViewModel", "Got ${addons.size} enabled addons: ${addons.map { it.name }}")

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
                    val addonId: String,
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
                        jobs.add(CatalogJob(manifest.id, manifest.name, catalog.name, catalog.id, catalog.type, manifest.url))
                    }
                }
                // Hide Torbox "Your Media" catalogs from display
                jobs.removeAll { it.addonName.contains("torbox", true) && it.catalogName.contains("your media", true) }

                // Load saved order/hidden upfront so we can skip hidden catalogs entirely
                val savedOrder = settingsDataStore.homeCategoryOrder.first()
                val hiddenKeys = settingsDataStore.homeHiddenCategories.first()

                // Remove hidden catalogs BEFORE fetching — saves network requests
                val beforeCount = jobs.size
                val useDefaultHide = settingsDataStore.HOME_DEFAULT_HIDE_MARKER in hiddenKeys
                jobs.removeAll { job ->
                    val key = "${job.addonId}:${job.catalogId}:${job.type}"
                    if (useDefaultHide) {
                        // Default: only show MerlotTV+ catalogs (excluding network catalogs)
                        job.addonId != "com.merlottv.tmdb" || key.contains(":net.")
                    } else {
                        key in hiddenKeys
                    }
                }
                Log.d("HomeViewModel", "Total catalog jobs: ${jobs.size} (${beforeCount - jobs.size} hidden, skipped)")
                val orderMap = if (savedOrder.isNotEmpty()) {
                    savedOrder.withIndex().associate { (i, key) -> key to i }
                } else emptyMap()

                // Fetch "In Theaters" IDs even if the row is hidden — we tag movies app-wide
                val theaterIdSet = mutableSetOf<String>()
                launch(catalogDispatcher) {
                    try {
                        val merlotAddon = manifests.find { it.id == "com.merlottv.tmdb" }
                        if (merlotAddon != null) {
                            val nowPlayingItems = addonRepository.getCatalog(merlotAddon, "movie", "merlot.now_playing")
                            theaterIdSet.addAll(nowPlayingItems.map { it.id })
                            Log.d("HomeViewModel", "Fetched ${theaterIdSet.size} in-theater movie IDs")
                            _uiState.value = _uiState.value.copy(inTheaterIds = theaterIdSet.toSet())
                        }
                    } catch (e: Exception) {
                        Log.w("HomeViewModel", "Failed to fetch in-theater IDs: ${e.message}")
                    }
                }

                // Progressive loading: emit each catalog row as it arrives
                // so the UI shows content immediately instead of waiting for ALL catalogs
                val accumulatedRows = mutableListOf<CatalogRow>()
                val rowChannel = Channel<CatalogRow>(Channel.UNLIMITED)

                // Launch all catalog fetches
                val fetchJob = launch {
                    supervisorScope {
                        jobs.map { job ->
                            async(catalogDispatcher) {
                                try {
                                    val addon = manifests.find { it.url == job.addonUrl } ?: return@async
                                    val items = addonRepository.getCatalog(addon, job.type, job.catalogId)
                                    if (items.isNotEmpty()) {
                                        val typeLabel = when (job.type) {
                                            "movie" -> "Movies"
                                            "series" -> "Series"
                                            else -> job.type
                                        }
                                        // Skip type label if catalog name already contains it
                                        val nameLower = job.catalogName.lowercase()
                                        val alreadyHasType = "movies" in nameLower || "series" in nameLower ||
                                            "shows" in nameLower
                                        val displayTitle = if (alreadyHasType) {
                                            "${job.catalogName} — ${job.addonName}"
                                        } else {
                                            "${job.catalogName} $typeLabel — ${job.addonName}"
                                        }
                                        // Deduplicate within this row (same ID from addon)
                                        val dedupedItems = items.distinctBy { it.id }
                                        val row = CatalogRow(
                                            key = "${job.addonId}:${job.catalogId}:${job.type}",
                                            title = displayTitle,
                                            items = dedupedItems
                                        )
                                        rowChannel.send(row)
                                    }
                                } catch (_: Exception) {}
                            }
                        }.awaitAll()
                    }
                    rowChannel.close()
                }

                // Consume rows as they arrive and update UI progressively
                for (row in rowChannel) {
                    accumulatedRows.add(row)

                    // Sort with each emission
                    val sorted = if (orderMap.isNotEmpty()) {
                        accumulatedRows.sortedWith(compareBy { orderMap[it.key] ?: (1000 + defaultSortOrder(it.title)) })
                    } else {
                        accumulatedRows.sortedWith(compareBy { defaultSortOrder(it.title) })
                    }

                    // Build hero items from what we have so far
                    val heroItems = buildHeroItems(sorted)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        featuredItems = heroItems,
                        catalogRows = sorted.toList(),
                        error = null
                    )
                }

                // Final emission with complete data
                fetchJob.join()
                if (accumulatedRows.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (_uiState.value.catalogRows.isEmpty()) "No catalogs available" else null
                    )
                }

                Log.d("HomeViewModel", "Loaded ${accumulatedRows.size} catalog rows")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "loadCatalogs failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun buildHeroItems(rows: List<CatalogRow>): List<MetaPreview> {
        return rows
            .filter { row ->
                val t = row.title.lowercase()
                // Movies: popular, new, featured, trending, top
                // Series: airing today, popular new tv shows (MerlotTV+)
                ("movie" in t && ("popular" in t || "new" in t || "featured" in t || "trending" in t || "top" in t)) ||
                ("airing today" in t) ||
                ("popular new tv" in t)
            }
            .flatMap { it.items }
            .distinctBy { it.id }
            .filter { it.background.isNotEmpty() || it.poster.isNotEmpty() }
            .sortedByDescending { it.background.isNotEmpty() }
            .take(8)
            .ifEmpty {
                rows.firstOrNull()?.items?.take(5) ?: emptyList()
            }
    }

    fun toggleFavorite(item: MetaPreview) {
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                Log.w("HomeViewModel", "toggleFavorite failed: ${e.message}")
            }
        }
    }

    private fun defaultSortOrder(title: String): Int {
        val t = title.lowercase()
        return when {
            // MerlotTV+ catalogs first — user's preferred order
            "popular new tv" in t -> 0
            "popular" in t && "movie" in t -> 1
            "popular" in t && "series" in t -> 2
            "new" in t && "movie" in t -> 3
            "new" in t && "series" in t -> 4
            "featured" in t && "movie" in t -> 5
            "featured" in t && "series" in t -> 6
            "airing today" in t -> 7
            "on the air" in t -> 8
            "upcoming" in t -> 9
            "in theaters" in t || "now_playing" in t -> 10
            "top rated" in t -> 11
            // Streaming service catalogs
            "trending" in t -> 12
            "netflix" in t -> 13
            "disney" in t -> 14
            "prime" in t || "amazon" in t -> 15
            "hbo" in t || "max" in t -> 16
            "apple" in t -> 17
            "paramount" in t -> 18
            "peacock" in t -> 19
            "discovery" in t -> 20
            // Network catalogs
            "nbc" in t -> 21
            "abc" in t -> 22
            "cbs" in t -> 23
            "fox" in t -> 24
            "cw" in t -> 25
            "showtime" in t -> 26
            "imdb" in t -> 27
            "latest" in t -> 28
            else -> 30
        }
    }
}
