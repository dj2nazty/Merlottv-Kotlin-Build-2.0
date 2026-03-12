package com.merlottv.kotlin.ui.screens.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.model.Meta
import com.merlottv.kotlin.domain.model.Stream
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodDetailUiState(
    val isLoading: Boolean = true,
    val meta: Meta? = null,
    val streams: List<Stream> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val isFavorite: Boolean = false,
    val selectedStreamUrl: String? = null,
    val selectedStreamTitle: String? = null,
    val autoPlayTriggered: Boolean = false
)

@HiltViewModel
class VodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addonRepository: AddonRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val type: String = savedStateHandle.get<String>("type") ?: "movie"
    private val id: String = savedStateHandle.get<String>("id") ?: ""

    private val _uiState = MutableStateFlow(VodDetailUiState())
    val uiState: StateFlow<VodDetailUiState> = _uiState.asStateFlow()

    init {
        loadMeta()
        checkFavorite()
    }

    private fun loadMeta() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val meta = addonRepository.getMeta(type, id)
            _uiState.value = _uiState.value.copy(isLoading = false, meta = meta)
        }
    }

    private fun checkFavorite() {
        viewModelScope.launch {
            val isFav = favoritesRepository.isFavoriteVod(id)
            _uiState.value = _uiState.value.copy(isFavorite = isFav)
        }
    }

    fun playBestStream() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStreams = true)
            try {
                val streams = addonRepository.getStreams(type, id)
                _uiState.value = _uiState.value.copy(streams = streams, isLoadingStreams = false)

                // Auto-select best stream
                val bestStream = selectBestStream(streams)
                if (bestStream != null) {
                    val url = bestStream.url.ifEmpty { bestStream.externalUrl }
                    if (url.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            selectedStreamUrl = url,
                            selectedStreamTitle = _uiState.value.meta?.name ?: "",
                            autoPlayTriggered = true
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingStreams = false)
            }
        }
    }

    fun playStream(stream: Stream) {
        val url = stream.url.ifEmpty { stream.externalUrl }
        if (url.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                selectedStreamUrl = url,
                selectedStreamTitle = _uiState.value.meta?.name ?: "",
                autoPlayTriggered = true
            )
        }
    }

    fun clearPlayback() {
        _uiState.value = _uiState.value.copy(
            selectedStreamUrl = null,
            autoPlayTriggered = false
        )
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            favoritesRepository.toggleFavoriteVod(id)
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }

    /**
     * Select the best stream based on priority:
     * 1. Direct HTTP/HTTPS URLs (not torrents/magnets)
     * 2. Prefer streams with "1080" or "720" in the title
     * 3. Prefer streams from known good addons (Torbox, Torrentio)
     * 4. Fall back to first available direct URL
     */
    private fun selectBestStream(streams: List<Stream>): Stream? {
        if (streams.isEmpty()) return null

        // Filter to only direct playable URLs (http/https, not magnet/torrent)
        val directStreams = streams.filter { stream ->
            val url = stream.url.ifEmpty { stream.externalUrl }
            url.isNotEmpty() &&
            (url.startsWith("http://") || url.startsWith("https://")) &&
            !url.contains("magnet:") &&
            !url.endsWith(".torrent")
        }

        if (directStreams.isEmpty()) {
            // Fallback: any stream with a URL
            return streams.firstOrNull { it.url.isNotEmpty() || it.externalUrl.isNotEmpty() }
        }

        // Prefer high quality
        val hd1080 = directStreams.firstOrNull { stream ->
            val text = "${stream.name} ${stream.title}".lowercase()
            text.contains("1080") || text.contains("bluray") || text.contains("remux")
        }
        if (hd1080 != null) return hd1080

        val hd720 = directStreams.firstOrNull { stream ->
            val text = "${stream.name} ${stream.title}".lowercase()
            text.contains("720") || text.contains("hd")
        }
        if (hd720 != null) return hd720

        // Prefer Torbox streams (they're debrid/fast)
        val torbox = directStreams.firstOrNull { stream ->
            stream.addonName.contains("torbox", ignoreCase = true) ||
            stream.url.contains("torbox", ignoreCase = true)
        }
        if (torbox != null) return torbox

        // Just pick the first direct stream
        return directStreams.first()
    }
}
