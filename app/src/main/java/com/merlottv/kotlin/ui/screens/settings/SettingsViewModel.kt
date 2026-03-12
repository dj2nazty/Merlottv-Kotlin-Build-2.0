package com.merlottv.kotlin.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.BuildConfig
import com.merlottv.kotlin.data.local.EpgSourceEntry
import com.merlottv.kotlin.data.local.PlaylistEntry
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.local.UserProfile
import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val playlistUrl: String = "",
    val torboxKey: String = "",
    val addons: List<Addon> = emptyList(),
    // Multiple playlists
    val playlists: List<PlaylistEntry> = emptyList(),
    // Custom EPG sources
    val customEpgSources: List<EpgSourceEntry> = emptyList(),
    val defaultEpgSources: List<EpgSourceEntry> = emptyList(),
    // Speed test
    val isRunningSpeedTest: Boolean = false,
    val downloadSpeed: String = "",
    val uploadSpeed: String = "",
    val speedTestError: String = "",
    // App version & update
    val appVersion: String = BuildConfig.VERSION_NAME,
    val updateAvailable: Boolean = false,
    val latestVersion: String = "",
    val updateUrl: String = "",
    val isCheckingUpdate: Boolean = false,
    // Profiles
    val profiles: List<UserProfile> = emptyList(),
    val activeProfileId: String = "default"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsDataStore: SettingsDataStore,
    private val addonRepository: AddonRepository,
    private val profileDataStore: ProfileDataStore
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadProfiles()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val playlist = settingsDataStore.playlistUrl.first()
            val torbox = settingsDataStore.torboxKey.first()
            val addons = addonRepository.getAllAddons().first()
            val playlists = settingsDataStore.playlists.first()
            val customEpg = settingsDataStore.customEpgSources.first()
            val defaultEpg = DefaultData.EPG_SOURCES.map {
                EpgSourceEntry(it.name, it.url, isDefault = true, enabled = true)
            }
            _uiState.value = _uiState.value.copy(
                playlistUrl = playlist,
                torboxKey = torbox,
                addons = addons,
                playlists = playlists,
                customEpgSources = customEpg,
                defaultEpgSources = defaultEpg
            )
        }
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            profileDataStore.profiles.collect { profiles ->
                val activeId = profileDataStore.getActiveProfileId()
                _uiState.value = _uiState.value.copy(
                    profiles = profiles,
                    activeProfileId = activeId
                )
            }
        }
    }

    // ─── Playlists ───
    fun addPlaylist(name: String, url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val current = _uiState.value.playlists.toMutableList()
            current.add(PlaylistEntry(name.ifBlank { "Playlist ${current.size + 1}" }, url))
            settingsDataStore.setPlaylists(current)
            _uiState.value = _uiState.value.copy(playlists = current)
        }
    }

    fun removePlaylist(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.playlists.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                settingsDataStore.setPlaylists(current)
                _uiState.value = _uiState.value.copy(playlists = current)
            }
        }
    }

    fun togglePlaylist(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.playlists.toMutableList()
            if (index in current.indices) {
                current[index] = current[index].copy(enabled = !current[index].enabled)
                settingsDataStore.setPlaylists(current)
                _uiState.value = _uiState.value.copy(playlists = current)
            }
        }
    }

    // ─── EPG Sources ───
    fun addEpgSource(name: String, url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val current = _uiState.value.customEpgSources.toMutableList()
            current.add(EpgSourceEntry(name.ifBlank { "EPG ${current.size + 1}" }, url))
            settingsDataStore.setCustomEpgSources(current)
            _uiState.value = _uiState.value.copy(customEpgSources = current)
        }
    }

    fun removeEpgSource(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.customEpgSources.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                settingsDataStore.setCustomEpgSources(current)
                _uiState.value = _uiState.value.copy(customEpgSources = current)
            }
        }
    }

    // ─── Speed Test ───
    fun runSpeedTest() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunningSpeedTest = true,
                downloadSpeed = "",
                uploadSpeed = "",
                speedTestError = ""
            )

            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                // Download test (10MB)
                val downloadMbps = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://speed.cloudflare.com/__down?bytes=10000000")
                        .build()
                    val startTime = System.nanoTime()
                    val response = client.newCall(request).execute()
                    val bytes = response.body?.bytes()?.size ?: 0
                    val elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0
                    if (elapsed > 0) (bytes * 8.0 / 1_000_000.0) / elapsed else 0.0
                }

                _uiState.value = _uiState.value.copy(
                    downloadSpeed = String.format("%.1f Mbps", downloadMbps)
                )

                // Upload test (5MB)
                val uploadMbps = withContext(Dispatchers.IO) {
                    val data = ByteArray(5_000_000)
                    val body = data.toRequestBody("application/octet-stream".toMediaType())
                    val request = Request.Builder()
                        .url("https://speed.cloudflare.com/__up")
                        .post(body)
                        .build()
                    val startTime = System.nanoTime()
                    val response = client.newCall(request).execute()
                    response.body?.close()
                    val elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0
                    if (elapsed > 0) (data.size * 8.0 / 1_000_000.0) / elapsed else 0.0
                }

                _uiState.value = _uiState.value.copy(
                    isRunningSpeedTest = false,
                    uploadSpeed = String.format("%.1f Mbps", uploadMbps)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningSpeedTest = false,
                    speedTestError = e.message ?: "Speed test failed"
                )
            }
        }
    }

    // ─── App Update Check ───
    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdate = true)
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/dj2nazty/Merlottv-Kotlin-Build-2.0/releases/latest")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: "{}"
                    JSONObject(body)
                }

                val tagName = result.optString("tag_name", "")
                val latestVersion = tagName.removePrefix("v")
                val assets = result.optJSONArray("assets")
                val downloadUrl = if (assets != null && assets.length() > 0) {
                    assets.getJSONObject(0).optString("browser_download_url", "")
                } else ""

                val isNewer = isVersionNewer(latestVersion, BuildConfig.VERSION_NAME)

                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    updateAvailable = isNewer,
                    latestVersion = latestVersion,
                    updateUrl = downloadUrl
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    updateAvailable = false
                )
            }
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {}
        return false
    }

    // ─── Profiles ───
    fun addProfile(name: String, colorIndex: Int) {
        viewModelScope.launch {
            try {
                profileDataStore.addProfile(name, colorIndex)
            } catch (_: Exception) {}
        }
    }

    fun removeProfile(profileId: String) {
        viewModelScope.launch {
            profileDataStore.removeProfile(profileId)
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            profileDataStore.setActiveProfile(profileId)
            _uiState.value = _uiState.value.copy(activeProfileId = profileId)
        }
    }

    // ─── Existing ───
    fun savePlaylistUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setPlaylistUrl(url)
            _uiState.value = _uiState.value.copy(playlistUrl = url)
        }
    }

    fun saveTorboxKey(key: String) {
        viewModelScope.launch {
            settingsDataStore.setTorboxKey(key)
            _uiState.value = _uiState.value.copy(torboxKey = key)
        }
    }

    fun addAddon(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            addonRepository.addAddon(url)
            val addons = addonRepository.getAllAddons().first()
            _uiState.value = _uiState.value.copy(addons = addons)
        }
    }
}
