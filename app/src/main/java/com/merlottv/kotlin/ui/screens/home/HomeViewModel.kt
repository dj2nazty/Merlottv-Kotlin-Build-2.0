package com.merlottv.kotlin.ui.screens.home

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

data class CatalogRow(
    val title: String,
    val items: List<MetaPreview>
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val featuredItem: MetaPreview? = null,
    val catalogRows: List<CatalogRow> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val addonRepository: AddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadCatalogs()
    }

    private fun loadCatalogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val addons = addonRepository.getAllAddons().first()

                // Fetch all manifests in parallel
                val manifests = supervisorScope {
                    addons.map { addon ->
                        async {
                            try {
                                addonRepository.fetchManifest(addon.url) ?: addon
                            } catch (e: Exception) {
                                addon
                            }
                        }
                    }.awaitAll()
                }

                // Build catalog fetch jobs in parallel
                data class CatalogJob(val addonName: String, val catalogName: String, val type: String, val catalogId: String, val addonUrl: String)

                val jobs = mutableListOf<CatalogJob>()
                for (manifest in manifests) {
                    for (catalog in manifest.catalogs.take(3)) {
                        jobs.add(CatalogJob(manifest.name, catalog.name, catalog.type, catalog.id, manifest.url))
                    }
                }

                // Fetch catalogs in parallel (max ~6 at a time via supervisorScope)
                val rows = supervisorScope {
                    jobs.map { job ->
                        async {
                            try {
                                val addon = manifests.find { it.url == job.addonUrl } ?: return@async null
                                val items = addonRepository.getCatalog(addon, job.type, job.catalogId)
                                if (items.isNotEmpty()) {
                                    CatalogRow(
                                        title = "${job.catalogName} — ${job.addonName}",
                                        items = items
                                    )
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                val featured = rows.firstOrNull()?.items?.firstOrNull()

                _uiState.value = HomeUiState(
                    isLoading = false,
                    featuredItem = featured,
                    catalogRows = rows
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
}
