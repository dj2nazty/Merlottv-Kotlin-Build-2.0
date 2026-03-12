package com.merlottv.kotlin.ui.screens.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class VodUiState(
    val isLoading: Boolean = true,
    val selectedTab: String = "Home",
    val items: List<MetaPreview> = emptyList()
)

@HiltViewModel
class VodViewModel @Inject constructor(
    private val addonRepository: AddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VodUiState())
    val uiState: StateFlow<VodUiState> = _uiState.asStateFlow()

    // Cache loaded content per tab
    private val cache = mutableMapOf<String, List<MetaPreview>>()

    init {
        loadContent("Home")
    }

    fun onTabSelected(tab: String) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        // Use cache if available
        cache[tab]?.let { cached ->
            _uiState.value = _uiState.value.copy(items = cached, isLoading = false)
            return
        }
        loadContent(tab)
    }

    private fun loadContent(tab: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val addons = addonRepository.getAllAddons().first()

                val type = when (tab) {
                    "Movies" -> "movie"
                    "Series" -> "series"
                    else -> null
                }

                // Fetch manifests + catalogs in parallel
                val allItems = supervisorScope {
                    addons.map { addon ->
                        async {
                            try {
                                val manifest = addonRepository.fetchManifest(addon.url) ?: addon
                                val catalogs = manifest.catalogs
                                val filteredCatalogs = if (type != null) {
                                    catalogs.filter { it.type == type }
                                } else catalogs

                                val results = mutableListOf<MetaPreview>()
                                for (catalog in filteredCatalogs.take(2)) {
                                    try {
                                        results.addAll(
                                            addonRepository.getCatalog(manifest, catalog.type, catalog.id)
                                        )
                                    } catch (_: Exception) {}
                                }
                                results
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }

                val unique = allItems.distinctBy { it.id }
                cache[tab] = unique

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = unique
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
