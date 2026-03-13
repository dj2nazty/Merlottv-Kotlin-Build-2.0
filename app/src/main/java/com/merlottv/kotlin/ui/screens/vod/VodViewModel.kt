package com.merlottv.kotlin.ui.screens.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

data class CatalogSection(
    val title: String,
    val addonName: String,
    val addonLogo: String = "",
    val brandLogo: String = "",
    val type: String,
    val catalogId: String,
    val items: List<MetaPreview>
)

data class VodUiState(
    val isLoading: Boolean = true,
    val selectedTab: String = "All",
    val sections: List<CatalogSection> = emptyList(),
    val filteredSections: List<CatalogSection> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class VodViewModel @Inject constructor(
    private val addonRepository: AddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VodUiState())
    val uiState: StateFlow<VodUiState> = _uiState.asStateFlow()

    init {
        loadAllCatalogs()
    }

    fun onTabSelected(tab: String) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        applyFilter(tab)
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
                val addons = addonRepository.getAllAddons().first()

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

                // Sort: Popular/New/Featured first, then streaming platforms
                val sorted = sections.sortedWith(compareBy(
                    { getCategoryOrder(it.title, it.catalogId) },
                    { if (it.type == "movie") 0 else 1 },
                    { it.title }
                ))

                _uiState.value = VodUiState(
                    isLoading = false,
                    selectedTab = "All",
                    sections = sorted,
                    filteredSections = sorted
                )
            } catch (e: Exception) {
                _uiState.value = VodUiState(
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
            "popular" in t || cid == "top" -> 0         // Popular first
            "new" in t || cid == "year" -> 1             // New releases second
            "featured" in t || "imdbrating" in cid -> 2  // Featured/Top Rated third
            "netflix" in t -> 3
            "disney" in t -> 4
            "prime" in t || "amazon" in t -> 5
            "hbo" in t -> 6
            "apple tv" in t -> 7
            "paramount" in t -> 8
            "peacock" in t -> 9
            "discovery" in t -> 10
            "imdb" in t -> 11
            else -> 15
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
}
