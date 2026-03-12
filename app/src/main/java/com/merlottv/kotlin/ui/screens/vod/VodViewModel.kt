package com.merlottv.kotlin.ui.screens.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    init {
        loadContent("Home")
    }

    fun onTabSelected(tab: String) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        loadContent(tab)
    }

    private fun loadContent(tab: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val addons = addonRepository.getAllAddons().first()
                val allItems = mutableListOf<MetaPreview>()

                val type = when (tab) {
                    "Movies" -> "movie"
                    "Series" -> "series"
                    else -> null
                }

                for (addon in addons) {
                    val manifest = addonRepository.fetchManifest(addon.url)
                    val catalogs = manifest?.catalogs ?: addon.catalogs
                    val filteredCatalogs = if (type != null) {
                        catalogs.filter { it.type == type }
                    } else catalogs

                    for (catalog in filteredCatalogs.take(2)) {
                        try {
                            val items = addonRepository.getCatalog(
                                addon = manifest ?: addon,
                                type = catalog.type,
                                catalogId = catalog.id
                            )
                            allItems.addAll(items)
                        } catch (_: Exception) {}
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = allItems.distinctBy { it.id }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
