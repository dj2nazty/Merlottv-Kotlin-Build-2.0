package com.merlottv.kotlin.ui.screens.vod

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.data.local.WatchProgressDataStore
import com.merlottv.kotlin.domain.model.Meta
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.model.Stream
import com.merlottv.kotlin.domain.model.Video
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
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

data class VodDetailUiState(
    val isLoading: Boolean = true,
    val meta: Meta? = null,
    val streams: List<Stream> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val isFavorite: Boolean = false,
    val selectedStreamUrl: String? = null,
    val selectedStreamTitle: String? = null,
    val autoPlayTriggered: Boolean = false,
    // Season/Episode browser
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val episodesForSeason: List<Video> = emptyList(),
    val selectedEpisode: Video? = null,
    // Watch progress
    val watchPosition: Long = 0L,
    val watchDuration: Long = 0L,
    val watchProgressPercent: Float = 0f,
    // Similar content ("Like This")
    val similarItems: List<MetaPreview> = emptyList(),
    val isLoadingSimilar: Boolean = false,
    // Trailer
    val trailerYtId: String? = null
)

@HiltViewModel
class VodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addonRepository: AddonRepository,
    private val favoritesRepository: FavoritesRepository,
    application: Application
) : ViewModel() {

    private val type: String = savedStateHandle.get<String>("type") ?: "movie"
    private val id: String = savedStateHandle.get<String>("id") ?: ""

    private val watchProgressStore = try {
        WatchProgressDataStore(application.applicationContext)
    } catch (_: Exception) { null }

    private val _uiState = MutableStateFlow(VodDetailUiState())
    val uiState: StateFlow<VodDetailUiState> = _uiState.asStateFlow()

    init {
        loadMeta()
        checkFavorite()
        loadWatchProgress()
    }

    private fun loadWatchProgress() {
        viewModelScope.launch {
            val pos = watchProgressStore?.getPosition(id) ?: 0L
            if (pos > 0) {
                val items = try {
                    watchProgressStore?.getContinueWatchingItems()?.first() ?: emptyList()
                } catch (_: Exception) { emptyList() }
                val item = items.firstOrNull { it.id == id }
                val dur = item?.duration ?: 0L
                val percent = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                _uiState.value = _uiState.value.copy(
                    watchPosition = pos,
                    watchDuration = dur,
                    watchProgressPercent = percent
                )
            }
        }
    }

    fun loadSimilarContent() {
        val meta = _uiState.value.meta ?: return
        if (_uiState.value.similarItems.isNotEmpty() || _uiState.value.isLoadingSimilar) return
        val genres = meta.genres
        if (genres.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSimilar = true)
            try {
                val addons = addonRepository.getAllAddons().first()
                val genre = genres.first()
                val results = supervisorScope {
                    addons.map { addon ->
                        async(Dispatchers.IO) {
                            try {
                                addonRepository.searchCatalog(addon, type, genre)
                            } catch (_: Exception) { emptyList() }
                        }
                    }.awaitAll().flatten()
                }
                val similar = results
                    .filter { it.id != id }
                    .distinctBy { it.id }
                    .take(20)
                _uiState.value = _uiState.value.copy(
                    similarItems = similar,
                    isLoadingSimilar = false
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingSimilar = false)
            }
        }
    }

    /** Play from beginning (restart) — clears saved position */
    fun playFromStart() {
        viewModelScope.launch {
            watchProgressStore?.removeProgress(id)
            _uiState.value = _uiState.value.copy(watchPosition = 0L, watchProgressPercent = 0f)
        }
        playBestStream()
    }

    private fun loadMeta() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val meta = addonRepository.getMeta(type, id)
            // Extract trailer YouTube ID from trailerStreams
            val trailerYtId = meta?.trailerStreams
                ?.firstOrNull { it.ytId.isNotEmpty() }?.ytId
            if (meta != null && meta.videos.isNotEmpty()) {
                val seasons = meta.videos
                    .map { it.season }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                val firstSeason = seasons.firstOrNull() ?: 1
                val episodes = meta.videos
                    .filter { it.season == firstSeason }
                    .sortedBy { it.episode }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    meta = meta,
                    seasons = seasons,
                    selectedSeason = firstSeason,
                    episodesForSeason = episodes,
                    trailerYtId = trailerYtId
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    meta = meta,
                    trailerYtId = trailerYtId
                )
            }
        }
    }

    fun selectSeason(season: Int) {
        val meta = _uiState.value.meta ?: return
        val episodes = meta.videos
            .filter { it.season == season }
            .sortedBy { it.episode }
        _uiState.value = _uiState.value.copy(
            selectedSeason = season,
            episodesForSeason = episodes,
            selectedEpisode = null,
            streams = emptyList()
        )
    }

    fun selectEpisode(episode: Video) {
        _uiState.value = _uiState.value.copy(selectedEpisode = episode)
    }

    fun playEpisode(episode: Video) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingStreams = true,
                selectedEpisode = episode
            )
            try {
                val streams = addonRepository.getStreams(type, episode.id)
                _uiState.value = _uiState.value.copy(
                    streams = streams,
                    isLoadingStreams = false
                )
                val bestStream = selectBestStream(streams)
                if (bestStream != null) {
                    val url = bestStream.url.ifEmpty { bestStream.externalUrl }
                    if (url.isNotEmpty()) {
                        val episodeTitle = "${_uiState.value.meta?.name ?: ""} S${episode.season}E${episode.episode} - ${episode.title}"
                        _uiState.value = _uiState.value.copy(
                            selectedStreamUrl = url,
                            selectedStreamTitle = episodeTitle,
                            autoPlayTriggered = true
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingStreams = false)
            }
        }
    }

    private fun checkFavorite() {
        viewModelScope.launch {
            val isFav = favoritesRepository.isFavoriteVod(id)
            _uiState.value = _uiState.value.copy(isFavorite = isFav)
        }
    }

    fun playBestStream() {
        val episode = _uiState.value.selectedEpisode
        if (episode != null) {
            playEpisode(episode)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStreams = true)
            try {
                val streams = addonRepository.getStreams(type, id)
                _uiState.value = _uiState.value.copy(streams = streams, isLoadingStreams = false)

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
            val episode = _uiState.value.selectedEpisode
            val title = if (episode != null) {
                "${_uiState.value.meta?.name ?: ""} S${episode.season}E${episode.episode} - ${episode.title}"
            } else {
                _uiState.value.meta?.name ?: ""
            }
            _uiState.value = _uiState.value.copy(
                selectedStreamUrl = url,
                selectedStreamTitle = title,
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
            val meta = _uiState.value.meta
            if (meta != null) {
                favoritesRepository.toggleFavoriteVodWithMeta(
                    id,
                    FavoriteVodMeta(
                        id = id,
                        name = meta.name,
                        poster = meta.poster,
                        type = meta.type,
                        imdbRating = meta.imdbRating,
                        description = meta.description
                    )
                )
            } else {
                favoritesRepository.toggleFavoriteVod(id)
            }
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }

    private fun selectBestStream(streams: List<Stream>): Stream? {
        if (streams.isEmpty()) return null

        val directStreams = streams.filter { stream ->
            val url = stream.url.ifEmpty { stream.externalUrl }
            url.isNotEmpty() &&
            (url.startsWith("http://") || url.startsWith("https://")) &&
            !url.contains("magnet:") &&
            !url.endsWith(".torrent")
        }

        if (directStreams.isEmpty()) {
            return streams.firstOrNull { it.url.isNotEmpty() || it.externalUrl.isNotEmpty() }
        }

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

        val torbox = directStreams.firstOrNull { stream ->
            stream.addonName.contains("torbox", ignoreCase = true) ||
            stream.url.contains("torbox", ignoreCase = true)
        }
        if (torbox != null) return torbox

        return directStreams.first()
    }
}
