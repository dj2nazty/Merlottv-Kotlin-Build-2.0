package com.merlottv.kotlin.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class FavoritesUiState(
    val favoriteChannelIds: Set<String> = emptySet(),
    val favoriteVodIds: Set<String> = emptySet(),
    val vodMetas: Map<String, FavoriteVodMeta> = emptyMap(),
    val selectedTab: String = "All", // "All", "Movies", "Series", "Channels", or custom list name
    val customLists: Map<String, List<String>> = emptyMap(),
    val showCreateListDialog: Boolean = false,
    val newListName: String = "",
    // Watched tracking
    val watchedVodIds: Set<String> = emptySet(),
    // Rename dialog
    val showRenameDialog: Boolean = false,
    val renameListTarget: String = "",
    val renameListName: String = "",
    // Item context menu (triggered by Menu button on poster card)
    val showItemMenu: Boolean = false,
    val itemMenuTarget: FavoriteVodMeta? = null,
    // "Add to list" submenu inside item menu
    val showAddToListMenu: Boolean = false
) {
    val filteredVodMetas: List<FavoriteVodMeta>
        get() {
            // Check if selectedTab is a custom list name
            if (customLists.containsKey(selectedTab)) {
                val listVodIds = customLists[selectedTab] ?: emptyList()
                return listVodIds.map { id ->
                    vodMetas[id] ?: FavoriteVodMeta(
                        id = id,
                        name = id,
                        poster = "",
                        type = "movie"
                    )
                }
            }

            val allMetas = favoriteVodIds.map { id ->
                vodMetas[id] ?: FavoriteVodMeta(
                    id = id,
                    name = id, // Fallback to ID if no metadata stored
                    poster = "",
                    type = "movie"
                )
            }
            return when (selectedTab) {
                "Movies" -> allMetas.filter { it.type == "movie" }
                "Series" -> allMetas.filter { it.type == "series" }
                else -> allMetas
            }
        }
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val addonRepository: AddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    // Track which IDs we've already tried to repair, so we don't loop.
    // Cleared on profile switch (when the VOD set changes significantly).
    private val repairedIds = mutableSetOf<String>()
    private var lastVodIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            favoritesRepository.getFavoriteChannelIds().collect { ids ->
                _uiState.value = _uiState.value.copy(favoriteChannelIds = ids)
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoriteVodIds().collect { ids ->
                // Detect profile switch: if more than 1 item changed at once,
                // the underlying flatMapLatest re-emitted for a new profile.
                // Clear repairedIds so metadata repair runs for the new profile's items.
                if (lastVodIds.isNotEmpty() && ids != lastVodIds) {
                    val changed = (ids - lastVodIds).size + (lastVodIds - ids).size
                    if (changed > 1) {
                        repairedIds.clear()
                    }
                }
                lastVodIds = ids
                _uiState.value = _uiState.value.copy(favoriteVodIds = ids)
                // Auto-repair missing metadata
                repairMissingMetadata(ids)
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoriteVodMetas().collect { metas ->
                _uiState.value = _uiState.value.copy(vodMetas = metas)
            }
        }
        viewModelScope.launch {
            favoritesRepository.getCustomLists().collect { lists ->
                _uiState.value = _uiState.value.copy(customLists = lists)
            }
        }
        viewModelScope.launch {
            favoritesRepository.getWatchedVodIds().collect { ids ->
                _uiState.value = _uiState.value.copy(watchedVodIds = ids)
            }
        }
    }

    /**
     * For any favorite VOD IDs that don't have stored metadata,
     * fetch meta from the addon API and save it so posters/titles display properly.
     */
    private fun repairMissingMetadata(vodIds: Set<String>) {
        val currentMetas = _uiState.value.vodMetas
        val missingIds = vodIds.filter { id ->
            !currentMetas.containsKey(id) && !repairedIds.contains(id)
        }
        if (missingIds.isEmpty()) return

        viewModelScope.launch {
            for (id in missingIds) {
                repairedIds.add(id)
                try {
                    // Guess type from ID format: tt1234567 is usually a movie,
                    // but try both — series first since they're more commonly favorited
                    val meta = withContext(Dispatchers.IO) {
                        addonRepository.getMeta("series", id)
                            ?: addonRepository.getMeta("movie", id)
                    }
                    if (meta != null && meta.name.isNotEmpty()) {
                        val favMeta = FavoriteVodMeta(
                            id = id,
                            name = meta.name,
                            poster = meta.poster,
                            type = meta.type,
                            imdbRating = meta.imdbRating,
                            description = meta.description
                        )
                        // Save metadata directly without toggling the favorite status
                        favoritesRepository.saveVodMeta(favMeta)
                    }
                } catch (_: Exception) {
                    // Ignore — best effort repair
                }
            }
        }
    }

    fun selectTab(tab: String) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    // Custom list management

    fun createList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            favoritesRepository.createCustomList(name.trim())
            _uiState.value = _uiState.value.copy(showCreateListDialog = false, newListName = "")
        }
    }

    fun deleteList(name: String) {
        viewModelScope.launch {
            favoritesRepository.deleteCustomList(name)
            // If the deleted list was selected, switch back to "All"
            if (_uiState.value.selectedTab == name) {
                _uiState.value = _uiState.value.copy(selectedTab = "All")
            }
        }
    }

    fun addToList(listName: String, vodId: String) {
        viewModelScope.launch {
            favoritesRepository.addToCustomList(listName, vodId)
        }
    }

    fun removeFromList(listName: String, vodId: String) {
        viewModelScope.launch {
            favoritesRepository.removeFromCustomList(listName, vodId)
        }
    }

    fun showCreateListDialog() {
        _uiState.value = _uiState.value.copy(showCreateListDialog = true, newListName = "")
    }

    fun hideCreateListDialog() {
        _uiState.value = _uiState.value.copy(showCreateListDialog = false, newListName = "")
    }

    fun updateNewListName(name: String) {
        _uiState.value = _uiState.value.copy(newListName = name)
    }

    // ─── Rename list ───

    fun showRenameDialog(listName: String) {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = true,
            renameListTarget = listName,
            renameListName = listName
        )
    }

    fun hideRenameDialog() {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = false,
            renameListTarget = "",
            renameListName = ""
        )
    }

    fun updateRenameListName(name: String) {
        _uiState.value = _uiState.value.copy(renameListName = name)
    }

    fun confirmRename() {
        val oldName = _uiState.value.renameListTarget
        val newName = _uiState.value.renameListName.trim()
        if (newName.isBlank() || newName == oldName) {
            hideRenameDialog()
            return
        }
        viewModelScope.launch {
            favoritesRepository.renameCustomList(oldName, newName)
            // If the renamed list was the active tab, update selectedTab
            if (_uiState.value.selectedTab == oldName) {
                _uiState.value = _uiState.value.copy(selectedTab = newName)
            }
            hideRenameDialog()
        }
    }

    // ─── Item context menu ───

    fun showItemMenu(meta: FavoriteVodMeta) {
        _uiState.value = _uiState.value.copy(
            showItemMenu = true,
            itemMenuTarget = meta,
            showAddToListMenu = false
        )
    }

    fun hideItemMenu() {
        _uiState.value = _uiState.value.copy(
            showItemMenu = false,
            itemMenuTarget = null,
            showAddToListMenu = false
        )
    }

    fun showAddToListSubmenu() {
        _uiState.value = _uiState.value.copy(showAddToListMenu = true)
    }

    fun hideAddToListSubmenu() {
        _uiState.value = _uiState.value.copy(showAddToListMenu = false)
    }

    // ─── Watched tracking ───

    fun toggleWatched(vodId: String) {
        viewModelScope.launch {
            favoritesRepository.toggleWatched(vodId)
        }
    }

    // ─── Remove from favorites ───

    fun removeFavorite(vodId: String) {
        viewModelScope.launch {
            favoritesRepository.toggleFavoriteVod(vodId)
            // Also remove from all custom lists
            _uiState.value.customLists.forEach { (listName, ids) ->
                if (ids.contains(vodId)) {
                    favoritesRepository.removeFromCustomList(listName, vodId)
                }
            }
        }
    }
}
