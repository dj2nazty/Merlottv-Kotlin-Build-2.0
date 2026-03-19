package com.merlottv.kotlin.ui.screens.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject

data class CatalogSection(
    val key: String = "",
    val title: String,
    val addonName: String,
    val addonLogo: String = "",
    val brandLogo: String = "",
    val type: String,
    val catalogId: String,
    val items: List<MetaPreview>
)

/**
 * Hardcoded platform sub-tabs that appear under Movies/Series.
 * Each pulls content from an MDBList or filters existing catalogs.
 */
data class PlatformTab(
    val id: String,
    val name: String,
    val iconRes: Int,           // R.drawable resource ID (real brand icon)
    val bgColor: Long,          // Brand background color (ARGB)
    val mdbListUrl: String      // MDBList JSON endpoint
)

val PLATFORM_TABS = listOf(
    PlatformTab(
        id = "discovery",
        name = "Discovery+",
        iconRes = com.merlottv.kotlin.R.drawable.ic_discovery_plus,
        bgColor = 0xFF00237A,   // Discovery+ dark blue
        mdbListUrl = "https://mdblist.com/lists/shaunatkins11/discovery-tv/json"
    ),
    PlatformTab(
        id = "hallmark",
        name = "Hallmark",
        iconRes = com.merlottv.kotlin.R.drawable.ic_hallmark,
        bgColor = 0xFF1A1A2E,   // Dark navy
        mdbListUrl = "https://mdblist.com/lists/hakomed/hallmark-movies/json"
    ),
    PlatformTab(
        id = "netflix",
        name = "Netflix",
        iconRes = com.merlottv.kotlin.R.drawable.ic_netflix,
        bgColor = 0xFF000000,   // Netflix black
        mdbListUrl = "https://mdblist.com/lists/notamongo5/netflix-series-2/json"
    ),
    PlatformTab(
        id = "hgtv",
        name = "HGTV",
        iconRes = com.merlottv.kotlin.R.drawable.ic_hgtv,
        bgColor = 0xFFFFFFFF,   // HGTV white
        mdbListUrl = "https://mdblist.com/lists/bigred777/hgtv-magnolia-diy-shows/json"
    ),
    PlatformTab(
        id = "disney",
        name = "Disney+",
        iconRes = com.merlottv.kotlin.R.drawable.ic_disney_plus,
        bgColor = 0xFF0B1D3A,   // Disney+ dark blue
        mdbListUrl = "https://mdblist.com/lists/notamongo5/disney-series/json"
    ),
    PlatformTab(
        id = "prime",
        name = "Prime",
        iconRes = com.merlottv.kotlin.R.drawable.ic_prime,
        bgColor = 0xFF00A8E1,   // Prime Video blue
        mdbListUrl = "https://mdblist.com/lists/notamongo5/amazon-prime-video-series/json"
    ),
    PlatformTab(
        id = "hbomax",
        name = "Max",
        iconRes = com.merlottv.kotlin.R.drawable.ic_hbo_max,
        bgColor = 0xFF6B32C8,   // HBO Max purple
        mdbListUrl = "https://mdblist.com/lists/notamongo5/hbo-max-series/json"
    ),
    PlatformTab(
        id = "paramount",
        name = "Paramount+",
        iconRes = com.merlottv.kotlin.R.drawable.ic_paramount_plus,
        bgColor = 0xFF0064FF,   // Paramount+ blue
        mdbListUrl = "https://mdblist.com/lists/notamongo5/paramount-plus-series/json"
    ),
    PlatformTab(
        id = "peacock",
        name = "Peacock",
        iconRes = com.merlottv.kotlin.R.drawable.ic_peacock,
        bgColor = 0xFF000000,   // Peacock black
        mdbListUrl = "https://mdblist.com/lists/notamongo5/peacock-premium-series/json"
    ),
    PlatformTab(
        id = "crunchyroll",
        name = "Crunchyroll",
        iconRes = com.merlottv.kotlin.R.drawable.ic_crunchyroll,
        bgColor = 0xFFF47521,   // Crunchyroll orange
        mdbListUrl = "https://mdblist.com/lists/dj2nazty/crunchyroll/json"
    ),
    PlatformTab(
        id = "appletv",
        name = "Apple TV+",
        iconRes = com.merlottv.kotlin.R.drawable.ic_apple_tv,
        bgColor = 0xFF000000,   // Apple TV+ black
        mdbListUrl = "https://mdblist.com/lists/notamongo5/apple-tv-series/json"
    ),
    PlatformTab(
        id = "starz",
        name = "Starz",
        iconRes = com.merlottv.kotlin.R.drawable.ic_starz,
        bgColor = 0xFF000000,   // Starz black
        mdbListUrl = "https://mdblist.com/lists/ryankeast/tv-shows-providers-starz/json"
    ),
    PlatformTab(
        id = "pbs",
        name = "PBS",
        iconRes = com.merlottv.kotlin.R.drawable.ic_pbs,
        bgColor = 0xFF000000,   // PBS black
        mdbListUrl = ""         // TODO: User will provide MDBList URL later
    ),
    PlatformTab(
        id = "kids",
        name = "Kids",
        iconRes = com.merlottv.kotlin.R.drawable.ic_kids,
        bgColor = 0xFFFFFFFF,   // Kids white
        mdbListUrl = "https://mdblist.com/lists/dj2nazty/animated-kids-movies/json"
    ),
    PlatformTab(
        id = "foodnetwork",
        name = "Food Network",
        iconRes = com.merlottv.kotlin.R.drawable.ic_food_network,
        bgColor = 0xFFCC0000,   // Food Network red
        mdbListUrl = "https://mdblist.com/lists/dj2nazty/food-network/json"
    ),
    PlatformTab(
        id = "imdbtop250",
        name = "IMDB Top 250",
        iconRes = com.merlottv.kotlin.R.drawable.ic_imdb_top250,
        bgColor = 0xFFE6B91E,   // IMDB gold
        mdbListUrl = "https://mdblist.com/lists/snoak/imdb-top-250-movies/json"
    )
)

data class VodUiState(
    val isLoading: Boolean = true,
    val isFilterLoading: Boolean = false,
    val selectedTab: String = "All",
    val selectedPlatformTab: PlatformTab? = null,
    val platformSections: List<CatalogSection> = emptyList(),
    val isPlatformLoading: Boolean = false,
    val sections: List<CatalogSection> = emptyList(),
    val filteredSections: List<CatalogSection> = emptyList(),
    val error: String? = null,
    val selectedGenre: String? = null,
    val selectedYear: String? = null,
    val availableGenres: List<String> = emptyList(),
    val availableYears: List<String> = emptyList()
)

@HiltViewModel
class VodViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(VodUiState())
    val uiState: StateFlow<VodUiState> = _uiState.asStateFlow()

    // Shared OkHttpClient — reuses connections, much faster than creating new ones
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Limit Cinemeta parallelism to avoid overwhelming the device
    private val cinemetaDispatcher = Dispatchers.IO.limitedParallelism(6)

    val favoriteVodIds: StateFlow<Set<String>> = favoritesRepository.getFavoriteVodIds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // TMDB filter data extracted from manifest
    private var movieGenres: List<String> = emptyList()
    private var seriesGenres: List<String> = emptyList()
    private var yearOptions: List<String> = emptyList()
    private var tmdbAddon: Addon? = null

    init {
        loadAllCatalogs()
    }

    fun onTabSelected(tab: String) {
        val genres = when (tab) {
            "Movies" -> movieGenres
            "Series" -> seriesGenres
            else -> emptyList()
        }
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            selectedPlatformTab = null,
            platformSections = emptyList(),
            selectedGenre = null,
            selectedYear = null,
            availableGenres = genres,
            availableYears = if (tab != "All") yearOptions else emptyList()
        )
        applyFilter(tab)
    }

    fun onPlatformTabSelected(tab: PlatformTab?) {
        if (tab == null || tab == _uiState.value.selectedPlatformTab) {
            // Deselect — go back to All view
            _uiState.value = _uiState.value.copy(
                selectedTab = "All",
                selectedPlatformTab = null,
                platformSections = emptyList(),
                availableGenres = emptyList(),
                availableYears = emptyList()
            )
            applyFilter("All")
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedTab = "All",  // Clear main tab highlight
            selectedPlatformTab = tab,
            isPlatformLoading = true,
            platformSections = emptyList(),
            selectedGenre = null,
            selectedYear = null,
            availableGenres = emptyList(),
            availableYears = emptyList()
        )
        loadMdbListContent(tab)
    }

    private val mdbListCache = mutableMapOf<String, List<MetaPreview>>()

    private fun loadMdbListContent(tab: PlatformTab) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (tab.mdbListUrl.isBlank()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isPlatformLoading = false,
                            platformSections = listOf(
                                CatalogSection(
                                    key = "mdblist:${tab.id}",
                                    title = "${tab.name} — Coming Soon",
                                    addonName = "MDBList",
                                    type = "movie",
                                    catalogId = tab.id,
                                    items = emptyList()
                                )
                            )
                        )
                    }
                    return@launch
                }
                val cacheKey = tab.id

                // Check cache first
                mdbListCache[cacheKey]?.let { cached ->
                    val section = CatalogSection(
                        key = "mdblist:${tab.id}",
                        title = "${tab.name} — ${cached.size} titles",
                        addonName = "MDBList",
                        brandLogo = "",
                        type = "movie",
                        catalogId = tab.id,
                        items = cached
                    )
                    _uiState.value = _uiState.value.copy(
                        isPlatformLoading = false,
                        platformSections = listOf(section)
                    )
                    return@launch
                }

                // Fetch MDBList JSON
                val request = Request.Builder().url(tab.mdbListUrl).build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")
                val jsonArray = JSONArray(body)

                // Extract ALL IMDB IDs with their MDBList mediatype
                data class MdbItem(val imdbId: String, val mediaType: String)
                val mdbItems = mutableListOf<MdbItem>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val imdbId = item.optString("imdb_id", "")
                    val mediaType = item.optString("mediatype", "")
                    if (imdbId.isNotEmpty()) {
                        mdbItems.add(MdbItem(imdbId, mediaType))
                    }
                }

                if (mdbItems.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isPlatformLoading = false,
                        platformSections = emptyList()
                    )
                    return@launch
                }

                // Resolve IMDB IDs via Cinemeta PROGRESSIVELY in chunks of 20
                // Show results as they come in so the user sees content fast
                val cinemetaBase = "https://v3-cinemeta.strem.io"
                val allResolved = mutableListOf<MetaPreview>()
                val chunks = mdbItems.chunked(20)

                for ((chunkIdx, chunk) in chunks.withIndex()) {
                    // Check if user switched away from this platform tab
                    if (_uiState.value.selectedPlatformTab?.id != tab.id) return@launch

                    val batchResults = supervisorScope {
                        chunk.map { mdbItem ->
                            async(cinemetaDispatcher) {
                                withTimeoutOrNull(4000L) {
                                    // Map MDBList mediatype → Cinemeta type
                                    val cinemetaType = when (mdbItem.mediaType) {
                                        "show" -> "series"
                                        "movie" -> "movie"
                                        else -> "movie" // default fallback
                                    }
                                    try {
                                        val url = "$cinemetaBase/meta/$cinemetaType/${mdbItem.imdbId}.json"
                                        val req = Request.Builder().url(url).build()
                                        val resp = httpClient.newCall(req).execute()
                                        if (resp.isSuccessful) {
                                            val metaBody = resp.body?.string() ?: return@withTimeoutOrNull null
                                            val metaJson = org.json.JSONObject(metaBody).optJSONObject("meta")
                                                ?: return@withTimeoutOrNull null
                                            MetaPreview(
                                                id = metaJson.optString("imdb_id", mdbItem.imdbId),
                                                type = cinemetaType,
                                                name = metaJson.optString("name", ""),
                                                poster = metaJson.optString("poster", ""),
                                                description = metaJson.optString("description", ""),
                                                imdbRating = metaJson.optString("imdbRating", ""),
                                                background = metaJson.optString("background", ""),
                                                logo = metaJson.optString("logo", "")
                                            )
                                        } else null
                                    } catch (_: Exception) { null }
                                }
                            }
                        }.awaitAll()
                    }.filterNotNull().filter { it.name.isNotEmpty() && it.poster.isNotEmpty() }

                    allResolved.addAll(batchResults)

                    // Update UI progressively after each chunk
                    val section = CatalogSection(
                        key = "mdblist:${tab.id}",
                        title = "${tab.name} — ${allResolved.size} of ${mdbItems.size}",
                        addonName = "MDBList",
                        brandLogo = "",
                        type = "movie",
                        catalogId = tab.id,
                        items = allResolved.toList()
                    )
                    _uiState.value = _uiState.value.copy(
                        isPlatformLoading = chunkIdx == 0 && allResolved.isEmpty(),
                        platformSections = listOf(section)
                    )
                }

                // Final update with clean title
                val finalSection = CatalogSection(
                    key = "mdblist:${tab.id}",
                    title = "${tab.name} — ${allResolved.size} titles",
                    addonName = "MDBList",
                    brandLogo = "",
                    type = "movie",
                    catalogId = tab.id,
                    items = allResolved.toList()
                )

                // Cache results
                mdbListCache[cacheKey] = allResolved.toList()

                _uiState.value = _uiState.value.copy(
                    isPlatformLoading = false,
                    platformSections = if (allResolved.isNotEmpty()) listOf(finalSection) else emptyList()
                )
            } catch (e: Exception) {
                android.util.Log.w("VodViewModel", "MDBList load failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isPlatformLoading = false,
                    platformSections = emptyList()
                )
            }
        }
    }

    fun onGenreSelected(genre: String?) {
        val current = _uiState.value
        val newGenre = if (genre == current.selectedGenre) null else genre
        _uiState.value = current.copy(selectedGenre = newGenre)
        if (newGenre != null || current.selectedYear != null) {
            loadFilteredCatalog(genre = newGenre, year = current.selectedYear)
        } else {
            applyFilter(current.selectedTab)
        }
    }

    fun onYearSelected(year: String?) {
        val current = _uiState.value
        val newYear = if (year == current.selectedYear) null else year
        _uiState.value = current.copy(selectedYear = newYear)
        if (newYear != null || current.selectedGenre != null) {
            loadFilteredCatalog(genre = current.selectedGenre, year = newYear)
        } else {
            applyFilter(current.selectedTab)
        }
    }

    private fun loadFilteredCatalog(genre: String? = null, year: String? = null) {
        val addon = tmdbAddon ?: return
        val tab = _uiState.value.selectedTab
        val type = if (tab == "Movies") "movie" else "series"
        val typeLabel = if (type == "movie") "Movies" else "Series"

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isFilterLoading = true)
            try {
                if (genre != null && year != null) {
                    // Both genre + year: fetch both in parallel, intersect results
                    val (genreItems, yearItems) = supervisorScope {
                        val g = async {
                            addonRepository.getCatalog(addon, type, "tmdb.top", genre = genre)
                        }
                        val y = async {
                            addonRepository.getCatalog(addon, type, "tmdb.year", genre = year)
                        }
                        Pair(g.await(), y.await())
                    }
                    val yearIds = yearItems.map { it.id }.toSet()
                    val items = genreItems.filter { it.id in yearIds }
                    val title = "$genre $typeLabel — $year"

                    val section = CatalogSection(
                        title = title, addonName = "TMDB", addonLogo = addon.logo,
                        brandLogo = "", type = type, catalogId = "tmdb.top+year",
                        items = items
                    )
                    _uiState.value = _uiState.value.copy(
                        isFilterLoading = false,
                        filteredSections = if (items.isNotEmpty()) listOf(section) else emptyList()
                    )
                } else {
                    // Single filter: genre OR year
                    val catalogId: String
                    val genreParam: String
                    val title: String

                    if (year != null) {
                        catalogId = "tmdb.year"
                        genreParam = year
                        title = "$year $typeLabel"
                    } else {
                        catalogId = "tmdb.top"
                        genreParam = genre!!
                        title = "$genre $typeLabel"
                    }

                    val items = addonRepository.getCatalog(
                        addon = addon, type = type, catalogId = catalogId, genre = genreParam
                    )
                    val section = CatalogSection(
                        title = title, addonName = "TMDB", addonLogo = addon.logo,
                        brandLogo = "", type = type, catalogId = catalogId,
                        items = items
                    )
                    _uiState.value = _uiState.value.copy(
                        isFilterLoading = false,
                        filteredSections = if (items.isNotEmpty()) listOf(section) else emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isFilterLoading = false,
                    error = "Failed to load filtered content"
                )
            }
        }
    }

    private fun applyFilter(tab: String) {
        val all = _uiState.value.sections
        val filtered = when (tab) {
            "Movies" -> all.filter { it.type == "movie" }
            "Series" -> all.filter { it.type == "series" }
            else -> all
        }
        _uiState.value = _uiState.value.copy(filteredSections = filtered)
    }

    private fun loadAllCatalogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val addons = addonRepository.getEnabledAddons().first()

                // Step 1: Fetch all manifests in parallel
                val manifests = supervisorScope {
                    addons.map { addon ->
                        async {
                            try {
                                addonRepository.fetchManifest(addon.url) ?: addon
                            } catch (_: Exception) {
                                addon
                            }
                        }
                    }.awaitAll()
                }

                // Extract TMDB filter options from manifest
                val tmdbManifest = manifests.find { it.id == "tmdb-addon" || it.name.contains("TMDB", true) }
                if (tmdbManifest != null) {
                    tmdbAddon = tmdbManifest
                    movieGenres = tmdbManifest.catalogs
                        .find { it.id == "tmdb.top" && it.type == "movie" }
                        ?.extra?.find { it.name == "genre" }?.options ?: emptyList()
                    seriesGenres = tmdbManifest.catalogs
                        .find { it.id == "tmdb.top" && it.type == "series" }
                        ?.extra?.find { it.name == "genre" }?.options ?: emptyList()
                    yearOptions = tmdbManifest.catalogs
                        .find { it.id == "tmdb.year" && it.type == "movie" }
                        ?.extra?.find { it.name == "genre" }?.options ?: emptyList()
                }

                // Step 2: Build catalog jobs
                data class CatalogJob(
                    val addon: Addon,
                    val catalogId: String,
                    val catalogName: String,
                    val type: String
                )

                val jobs = mutableListOf<CatalogJob>()
                for (manifest in manifests) {
                    if (manifest.catalogs.isEmpty()) continue
                    for (catalog in manifest.catalogs) {
                        val requiresSpecial = catalog.extra.any {
                            it.isRequired && it.name != "genre" && it.name != "search"
                        }
                        if (requiresSpecial) continue
                        jobs.add(CatalogJob(manifest, catalog.id, catalog.name, catalog.type))
                    }
                }
                // Hide Torbox "Your Media" catalogs from display
                jobs.removeAll { it.addon.name.contains("torbox", true) && it.catalogName.contains("your media", true) }

                // Step 3: Fetch all catalogs in parallel
                val sections = supervisorScope {
                    jobs.map { job ->
                        async {
                            try {
                                val items = addonRepository.getCatalog(
                                    addon = job.addon,
                                    type = job.type,
                                    catalogId = job.catalogId
                                )
                                if (items.isNotEmpty()) {
                                    val title = buildSectionTitle(job.catalogName, job.addon.name, job.type)
                                    val brandLogo = getBrandLogoUrl(job.catalogName)
                                    CatalogSection(
                                        key = "${job.addon.id}:${job.catalogId}:${job.type}",
                                        title = title,
                                        addonName = job.addon.name,
                                        addonLogo = job.addon.logo,
                                        brandLogo = brandLogo,
                                        type = job.type,
                                        catalogId = job.catalogId,
                                        items = items
                                    )
                                } else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                // Apply saved category order and hidden from VOD Category System
                val savedOrder = settingsDataStore.vodCategoryOrder.first()
                val hiddenKeys = settingsDataStore.vodHiddenCategories.first()

                val visible = sections.filter { it.key !in hiddenKeys }

                val sorted = if (savedOrder.isNotEmpty()) {
                    // Use saved order exactly — no secondary sort that would reshuffle
                    val orderMap = savedOrder.withIndex().associate { (i, key) -> key to i }
                    visible.sortedBy { orderMap[it.key] ?: (1000 + getCategoryOrder(it.title, it.catalogId)) }
                } else {
                    visible.sortedWith(compareBy(
                        { getCategoryOrder(it.title, it.catalogId) },
                        { when (it.type) { "movie" -> 0; "series" -> 1; else -> 2 } },
                        { it.title }
                    ))
                }

                val current = _uiState.value
                _uiState.value = current.copy(
                    isLoading = false,
                    selectedTab = if (current.selectedPlatformTab != null) current.selectedTab else "All",
                    sections = sorted,
                    filteredSections = sorted
                )
            } catch (e: Exception) {
                val current = _uiState.value
                _uiState.value = current.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun buildSectionTitle(catalogName: String, addonName: String, type: String): String {
        val typeLabel = when (type) {
            "movie" -> "Movies"
            "series" -> "Series"
            else -> type.replaceFirstChar { it.uppercase() }
        }

        val platformCatalogNames = listOf(
            "Netflix", "Disney+", "Prime Video", "Apple TV+",
            "HBO Max", "Paramount+", "Peacock", "Discovery+"
        )

        return if (catalogName in platformCatalogNames) {
            "$catalogName $typeLabel"
        } else {
            "$catalogName $typeLabel — $addonName"
        }
    }

    /**
     * Ordering: Popular/New/Featured first, then streaming platforms alphabetically
     */
    private fun getCategoryOrder(title: String, catalogId: String): Int {
        val t = title.lowercase()
        val cid = catalogId.lowercase()
        return when {
            "trending" in t -> 0
            "popular" in t || cid == "top" -> 1
            "upcoming" in t -> 2
            "in theaters" in t || "now_playing" in cid -> 3
            "airing today" in t -> 4
            "on the air" in t -> 5
            "top rated" in t -> 6
            "latest" in t || "new" in t || cid == "year" -> 7
            "featured" in t || "imdbrating" in cid -> 8
            "netflix" in t -> 9
            "disney" in t -> 10
            "prime" in t || "amazon" in t -> 11
            "hbo" in t -> 12
            "apple tv" in t -> 13
            "paramount" in t -> 14
            "peacock" in t -> 15
            "discovery" in t -> 16
            // Network catalogs
            "nbc" in t -> 17
            "abc" in t -> 18
            "cbs" in t -> 19
            "fox" in t -> 20
            "cw" in t -> 21
            "showtime" in t -> 22
            "imdb" in t -> 23
            else -> 30
        }
    }

    /**
     * Map streaming platform catalog names to their official brand logo URLs
     */
    private fun getBrandLogoUrl(catalogName: String): String {
        return when (catalogName) {
            "Netflix" -> "https://images.justwatch.com/icon/207360008/s100/netflix.webp"
            "Disney+" -> "https://images.justwatch.com/icon/147638351/s100/disneyplus.webp"
            "Prime Video" -> "https://images.justwatch.com/icon/52449861/s100/amazonprimevideo.webp"
            "Apple TV+" -> "https://images.justwatch.com/icon/190848813/s100/appletvplus.webp"
            "HBO Max" -> "https://images.justwatch.com/icon/305458112/s100/max.webp"
            "Paramount+" -> "https://images.justwatch.com/icon/232112229/s100/paramountplus.webp"
            "Peacock" -> "https://images.justwatch.com/icon/194375027/s100/peacocktv.webp"
            "Discovery+" -> "https://images.justwatch.com/icon/187278474/s100/discoveryplus.webp"
            "Popular" -> "https://images.justwatch.com/icon/cinemeta/s100/popular.webp"
            else -> ""
        }
    }

    fun retry() {
        loadAllCatalogs()
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
                android.util.Log.w("VodViewModel", "toggleFavorite failed: ${e.message}")
            }
        }
    }
}
