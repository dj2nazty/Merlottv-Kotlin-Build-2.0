package com.merlottv.kotlin.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val addons = addonRepository.getAllAddons().first()
                val rows = mutableListOf<CatalogRow>()

                for (addon in addons) {
                    // Fetch manifest to get fresh catalogs
                    val manifest = addonRepository.fetchManifest(addon.url)
                    val catalogs = manifest?.catalogs ?: addon.catalogs

                    for (catalog in catalogs.take(3)) {
                        try {
                            val items = addonRepository.getCatalog(
                                addon = manifest ?: addon,
                                type = catalog.type,
                                catalogId = catalog.id
                            )
                            if (items.isNotEmpty()) {
                                rows.add(CatalogRow(
                                    title = "${catalog.name} — ${addon.name}",
                                    items = items
                                ))
                            }
                        } catch (e: Exception) {
                            // Skip failed catalogs
                        }
                    }
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
