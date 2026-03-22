package com.merlottv.kotlin.ui.screens.tvguide

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.domain.repository.ChannelRepository
import com.merlottv.kotlin.domain.repository.EpgRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TvGuideUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val loadingMessage: String = "Loading TV Guide...",
    val isSyncing: Boolean = false,
    // User's actual channels (filtered view)
    val guideChannels: List<Channel> = emptyList(),
    // All channels (unfiltered master list)
    val allGuideChannels: List<Channel> = emptyList(),
    // Matched EPG data for guideChannels (same order/indices)
    val epgChannels: List<EpgChannel> = emptyList(),
    // Category groups from channels
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    // Favorites
    val favoriteIds: Set<String> = emptySet(),
    // ViewModel-driven D-pad navigation
    val selectedIndex: Int = 0,
    val scrollRequest: Int = 0,
    val timelineAtStart: Boolean = true,
    // Channel panel (step 1: slide-in channel list)
    val showChannelPanel: Boolean = false,
    // Category picker (step 2: slide-in category sidebar)
    val showCategoryPicker: Boolean = false,
    // Program detail
    val selectedProgram: EpgEntry? = null
)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val favoritesRepository: FavoritesRepository,
    private val epgRepository: EpgRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvGuideUiState())
    val uiState: StateFlow<TvGuideUiState> = _uiState.asStateFlow()

    init {
        loadGuideData()
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            try {
                favoritesRepository.getFavoriteChannelIds().collect { favIds ->
                    _uiState.value = _uiState.value.copy(favoriteIds = favIds)
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadGuideData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                loadingMessage = "Loading TV Guide..."
            )
            try {
                // Step 1: Load user's actual channels from playlists
                val playlists = settingsDataStore.playlists.first()
                val enabledPlaylists = playlists.filter { it.enabled }
                val enabledUrls = enabledPlaylists.map { it.url }

                val channels = withContext(Dispatchers.IO) {
                    if (enabledUrls.size == 1) {
                        channelRepository.loadChannels(enabledUrls.first())
                    } else if (enabledUrls.isNotEmpty()) {
                        channelRepository.loadMultipleChannels(enabledUrls)
                    } else {
                        emptyList()
                    }
                }
                if (channels.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No channels available. Add a playlist in Settings."
                    )
                    return@launch
                }

                val groupSet = LinkedHashSet<String>(channels.size / 10)
                for (ch in channels) { groupSet.add(ch.group) }
                val groups = groupSet.sortedBy { it.lowercase() }

                // Step 2: Load EPG data from Room DB
                val epgData = try {
                    epgRepository.getAllEpgChannels().first()
                } catch (_: Exception) { emptyList() }

                // Build lookup map from EPG channel name/id to EpgChannel
                val epgByName = mutableMapOf<String, EpgChannel>()
                epgData.forEach { epgCh ->
                    epgByName[epgCh.name.lowercase()] = epgCh
                    epgByName[epgCh.id.lowercase()] = epgCh
                }

                // Match each Live TV channel to its EPG data
                val matchedEpg = channels.map { channel ->
                    val epgId = channel.epgId.ifEmpty { channel.id }
                    val epgMatch = epgByName[epgId.lowercase()]
                        ?: epgByName[channel.name.lowercase()]
                    EpgChannel(
                        id = channel.id,
                        name = channel.name,
                        icon = epgMatch?.icon ?: channel.logoUrl,
                        programs = epgMatch?.programs ?: emptyList()
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allGuideChannels = channels,
                    guideChannels = channels,
                    epgChannels = matchedEpg,
                    groups = groups
                )

                Log.d("TvGuideVM", "Loaded ${channels.size} channels with EPG matching")

                // Step 3: Background refresh if EPG data is stale
                if (epgRepository.isEpgStale()) {
                    _uiState.value = _uiState.value.copy(isSyncing = true)
                    withContext(Dispatchers.IO) {
                        val allUrls = getEpgUrls()
                        epgRepository.loadEpg(allUrls)
                    }
                    // Reload EPG after refresh
                    refreshEpgMatching()
                    _uiState.value = _uiState.value.copy(isSyncing = false)
                }
            } catch (e: Exception) {
                Log.e("TvGuideVM", "Failed to load guide data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load TV Guide: ${e.message}"
                )
            }
        }
    }

    /** Re-match EPG data after a background refresh */
    private suspend fun refreshEpgMatching() {
        try {
            val epgData = epgRepository.getAllEpgChannels().first()
            val epgByName = mutableMapOf<String, EpgChannel>()
            epgData.forEach { epgCh ->
                epgByName[epgCh.name.lowercase()] = epgCh
                epgByName[epgCh.id.lowercase()] = epgCh
            }
            val channels = _uiState.value.guideChannels
            val matchedEpg = channels.map { channel ->
                val epgId = channel.epgId.ifEmpty { channel.id }
                val epgMatch = epgByName[epgId.lowercase()]
                    ?: epgByName[channel.name.lowercase()]
                EpgChannel(
                    id = channel.id,
                    name = channel.name,
                    icon = epgMatch?.icon ?: channel.logoUrl,
                    programs = epgMatch?.programs ?: emptyList()
                )
            }
            _uiState.value = _uiState.value.copy(epgChannels = matchedEpg)
        } catch (_: Exception) {}
    }

    private suspend fun getEpgUrls(): List<String> {
        val defaultUrls = DefaultData.EPG_SOURCES.map { it.url }
        val customSources = settingsDataStore.customEpgSources.first()
        val customUrls = customSources.filter { it.enabled }.map { it.url }
        return (defaultUrls + customUrls).distinct()
    }

    // ═══════════════════════════════════════════════════════════════════
    // D-Pad Navigation (ViewModel-driven, same pattern as Live TV EPG)
    // ═══════════════════════════════════════════════════════════════════

    /** Move highlight up/down */
    fun navigate(delta: Int) {
        val maxIndex = _uiState.value.guideChannels.size - 1
        if (maxIndex < 0) return
        val newIndex = (_uiState.value.selectedIndex + delta).coerceIn(0, maxIndex)
        _uiState.value = _uiState.value.copy(selectedIndex = newIndex)
    }

    /** Scroll timeline left/right — increment counter to trigger LaunchedEffect */
    fun scrollTimeline(direction: Int) {
        _uiState.value = _uiState.value.copy(
            scrollRequest = _uiState.value.scrollRequest + direction
        )
    }

    /** Called by composable to report current timeline scroll position */
    fun updateTimelineAtStart(atStart: Boolean) {
        if (_uiState.value.timelineAtStart != atStart) {
            _uiState.value = _uiState.value.copy(timelineAtStart = atStart)
        }
    }

    /** Show channel panel (step 1 — D-pad left from grid) */
    fun showChannelPanel() {
        // Auto-select the current channel's group so the channel panel
        // and category sidebar reflect where the user actually is
        val currentChannel = _uiState.value.guideChannels.getOrNull(_uiState.value.selectedIndex)
        val currentGroup = currentChannel?.group?.takeIf { it.isNotBlank() }
        val needsGroupChange = currentGroup != null && _uiState.value.selectedGroup != currentGroup

        _uiState.value = _uiState.value.copy(
            showChannelPanel = true,
            selectedGroup = if (needsGroupChange) currentGroup else _uiState.value.selectedGroup
        )

        if (needsGroupChange) {
            applyFilter()
        }
    }

    /** Hide channel panel */
    fun hideChannelPanel() {
        _uiState.value = _uiState.value.copy(showChannelPanel = false)
    }

    /** Show category picker (step 2 — D-pad left from channel panel) */
    fun showCategoryPicker() {
        _uiState.value = _uiState.value.copy(showCategoryPicker = true)
    }

    /** Hide category picker */
    fun hideCategoryPicker() {
        _uiState.value = _uiState.value.copy(showCategoryPicker = false)
    }

    /** Toggle category picker visibility */
    fun toggleCategoryPicker() {
        _uiState.value = _uiState.value.copy(
            showCategoryPicker = !_uiState.value.showCategoryPicker
        )
    }

    /** Get the currently highlighted channel */
    fun getSelectedChannel(): Channel? {
        return _uiState.value.guideChannels.getOrNull(_uiState.value.selectedIndex)
    }

    /** Save selected channel to SettingsDataStore for Live TV pickup */
    fun saveChannelForPlayback(channel: Channel) {
        viewModelScope.launch {
            try {
                settingsDataStore.setLastWatchedChannelId(channel.id)
            } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Category Filtering
    // ═══════════════════════════════════════════════════════════════════

    /** Filter by category group */
    fun setGroup(group: String?) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
        applyFilter()
    }

    private fun applyFilter() {
        val allChannels = _uiState.value.allGuideChannels
        val group = _uiState.value.selectedGroup
        val filtered = if (group == null) {
            allChannels
        } else if (group == "★ Favorites") {
            allChannels.filter { _uiState.value.favoriteIds.contains(it.id) }
        } else {
            allChannels.filter { it.group.equals(group, ignoreCase = true) }
        }

        // Re-match EPG for filtered channels
        viewModelScope.launch {
            try {
                val epgData = epgRepository.getAllEpgChannels().first()
                val epgByName = mutableMapOf<String, EpgChannel>()
                epgData.forEach { epgCh ->
                    epgByName[epgCh.name.lowercase()] = epgCh
                    epgByName[epgCh.id.lowercase()] = epgCh
                }
                val matchedEpg = filtered.map { channel ->
                    val epgId = channel.epgId.ifEmpty { channel.id }
                    val epgMatch = epgByName[epgId.lowercase()]
                        ?: epgByName[channel.name.lowercase()]
                    EpgChannel(
                        id = channel.id,
                        name = channel.name,
                        icon = epgMatch?.icon ?: channel.logoUrl,
                        programs = epgMatch?.programs ?: emptyList()
                    )
                }
                _uiState.value = _uiState.value.copy(
                    guideChannels = filtered,
                    epgChannels = matchedEpg,
                    selectedIndex = 0
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    guideChannels = filtered,
                    selectedIndex = 0
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Program Detail
    // ═══════════════════════════════════════════════════════════════════

    fun selectProgram(program: EpgEntry?) {
        _uiState.value = _uiState.value.copy(selectedProgram = program)
    }

    fun retry() {
        loadGuideData()
    }
}
