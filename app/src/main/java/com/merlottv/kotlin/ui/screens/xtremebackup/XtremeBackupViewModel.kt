package com.merlottv.kotlin.ui.screens.xtremebackup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.local.XtremeServerEntry
import com.merlottv.kotlin.data.parser.M3uParser
import com.merlottv.kotlin.domain.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class XtremeBackupUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val servers: List<XtremeServerEntry> = emptyList(),
    val selectedServerIndex: Int = 0,
    val channels: List<Channel> = emptyList(),
    val filteredChannels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String = "All",
    val searchQuery: String = "",
    val error: String? = null,
    val channelCount: Int = 0
)

@HiltViewModel
class XtremeBackupViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val m3uParser: M3uParser,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(XtremeBackupUiState())
    val uiState: StateFlow<XtremeBackupUiState> = _uiState.asStateFlow()

    // Dedicated client with tighter timeouts
    private val playlistClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // In-memory cache per server
    private val channelCache = mutableMapOf<String, Pair<List<Channel>, Long>>()
    private val cacheTtl = 15 * 60 * 1000L // 15 minutes

    fun onScreenVisible() {
        if (_uiState.value.hasLoadedOnce) return
        loadServers()
    }

    private fun loadServers() {
        viewModelScope.launch {
            val servers = settingsDataStore.xtremeServers.first().filter { it.enabled }
            _uiState.value = _uiState.value.copy(servers = servers, hasLoadedOnce = true)
            if (servers.isNotEmpty()) {
                loadChannelsForServer(0)
            }
        }
    }

    fun selectServer(index: Int) {
        val servers = _uiState.value.servers
        if (index !in servers.indices) return
        _uiState.value = _uiState.value.copy(
            selectedServerIndex = index,
            selectedGroup = "All",
            searchQuery = ""
        )
        loadChannelsForServer(index)
    }

    fun selectGroup(group: String) {
        val all = _uiState.value.channels
        val query = _uiState.value.searchQuery
        val filtered = filterChannels(all, group, query)
        _uiState.value = _uiState.value.copy(
            selectedGroup = group,
            filteredChannels = filtered
        )
    }

    fun search(query: String) {
        val all = _uiState.value.channels
        val group = _uiState.value.selectedGroup
        val filtered = filterChannels(all, group, query)
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredChannels = filtered
        )
    }

    fun refresh() {
        viewModelScope.launch {
            val index = _uiState.value.selectedServerIndex
            val servers = _uiState.value.servers
            val fmt = try { settingsDataStore.xtreamOutputFormat.first() } catch (_: Exception) { "m3u8" }
            if (index in servers.indices) {
                channelCache.remove(servers[index].buildM3uUrl(fmt))
                loadChannelsForServer(index)
            }
        }
    }

    fun reloadServers() {
        viewModelScope.launch {
            val servers = settingsDataStore.xtremeServers.first().filter { it.enabled }
            _uiState.value = _uiState.value.copy(servers = servers)
            if (servers.isNotEmpty()) {
                val idx = _uiState.value.selectedServerIndex.coerceIn(servers.indices)
                loadChannelsForServer(idx)
            } else {
                _uiState.value = _uiState.value.copy(
                    channels = emptyList(),
                    filteredChannels = emptyList(),
                    groups = emptyList(),
                    channelCount = 0
                )
            }
        }
    }

    private fun loadChannelsForServer(index: Int) {
        val servers = _uiState.value.servers
        if (index !in servers.indices) return
        val server = servers[index]
        val fmt = kotlinx.coroutines.runBlocking { try { settingsDataStore.xtreamOutputFormat.first() } catch (_: Exception) { "m3u8" } }
        val url = server.buildM3uUrl(fmt)

        // Check cache
        val cached = channelCache[url]
        if (cached != null && (System.currentTimeMillis() - cached.second) < cacheTtl) {
            applyChannels(cached.first, index)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                selectedServerIndex = index
            )
            try {
                val channels = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    val response = playlistClient.newCall(request).execute()
                    response.use { resp ->
                        val body = resp.body
                        if (body != null) {
                            m3uParser.parseStream(body.byteStream())
                        } else {
                            emptyList()
                        }
                    }
                }
                if (channels.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No channels found on ${server.name}",
                        channels = emptyList(),
                        filteredChannels = emptyList(),
                        groups = emptyList(),
                        channelCount = 0
                    )
                    return@launch
                }
                channelCache[url] = channels to System.currentTimeMillis()
                applyChannels(channels, index)
            } catch (e: Exception) {
                Log.e("XtremeBackupVM", "Failed to load from ${server.name}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to connect to ${server.name}: ${e.message}"
                )
            }
        }
    }

    private fun applyChannels(channels: List<Channel>, serverIndex: Int) {
        val groupSet = LinkedHashSet<String>(channels.size / 10)
        for (ch in channels) { groupSet.add(ch.group) }
        val sortedGroups = groupSet.sortedWith(
            compareByDescending<String> { group ->
                val lower = group.lowercase()
                lower.contains("usa") || lower.contains("us ") ||
                lower.startsWith("us:") || lower.startsWith("us|") ||
                lower.contains("united states")
            }.thenBy { it.lowercase() }
        )
        val groups = listOf("All") + sortedGroups

        val selectedGroup = _uiState.value.selectedGroup
        val query = _uiState.value.searchQuery
        val filtered = filterChannels(channels, selectedGroup, query)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            channels = channels,
            filteredChannels = filtered,
            groups = groups,
            channelCount = channels.size,
            selectedServerIndex = serverIndex,
            error = null
        )
    }

    private fun filterChannels(all: List<Channel>, group: String, query: String): List<Channel> {
        var result = if (group == "All") all else all.filter { it.group == group }
        if (query.isNotBlank()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
            }
        }
        return result
    }
}
