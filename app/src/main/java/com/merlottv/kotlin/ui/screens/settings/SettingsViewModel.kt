package com.merlottv.kotlin.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.BuildConfig
import com.merlottv.kotlin.data.local.BackupSourceEntry
import com.merlottv.kotlin.data.local.EpgSourceEntry
import com.merlottv.kotlin.data.local.PlaylistEntry
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.local.UserProfile
import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.data.sync.CloudSyncManager
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.ChannelRepository
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
    // Backup stream sources
    val backupSources: List<BackupSourceEntry> = emptyList(),
    // Profiles
    val profiles: List<UserProfile> = emptyList(),
    val activeProfileId: String = "default",
    // Live TV category order
    val categoryOrder: List<String> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    // Live TV buffer duration (ms) — adjustable 300–3000 in 100ms steps
    val bufferDurationMs: Int = 800,
    // Weather alerts on Live TV / VOD
    val weatherAlertsEnabled: Boolean = true,
    // Auto frame rate matching
    val frameRateMatching: String = "off", // "off", "start", "start_stop"
    // Next episode auto-play
    val nextEpisodeAutoPlay: Boolean = true,
    val nextEpisodeThresholdPercent: Int = 95,
    // Bitrate checker in Live TV Quick Menu
    val bitrateCheckerEnabled: Boolean = false,
    // Disabled addons (URLs)
    val disabledAddons: Set<String> = emptySet(),
    // Release notes
    val releaseNotes: String = "",
    val isFetchingReleaseNotes: Boolean = false,
    val showReleaseNotes: Boolean = false,
    // VOD Category System
    val showVodCategorySystem: Boolean = false,
    val homeCategoryItems: List<CategoryItem> = emptyList(),
    val vodCategoryItems: List<CategoryItem> = emptyList(),
    val isLoadingVodCategories: Boolean = false,
    val activeCategoryTab: String = "Home",
    val reorderMode: Boolean = false
)

data class CategoryItem(
    val key: String,
    val title: String,
    val enabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsDataStore: SettingsDataStore,
    private val addonRepository: AddonRepository,
    private val profileDataStore: ProfileDataStore,
    private val channelRepository: ChannelRepository,
    private val cloudSyncManager: CloudSyncManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadProfiles()
        loadCategoryOrder()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val playlist = settingsDataStore.playlistUrl.first()
            val torbox = settingsDataStore.torboxKey.first()
            val addons = addonRepository.getAllAddons().first()
            val playlists = settingsDataStore.playlists.first()
            val customEpg = settingsDataStore.customEpgSources.first()
            val backupSources = settingsDataStore.backupSources.first()
            val bufferMs = settingsDataStore.bufferDurationMs.first()
            val weatherAlertsOn = settingsDataStore.weatherAlertsEnabled.first()
            val frameRateMode = settingsDataStore.frameRateMatching.first()
            val nextEpAutoPlay = settingsDataStore.nextEpisodeAutoPlay.first()
            val nextEpThreshold = settingsDataStore.nextEpisodeThresholdPercent.first()
            val bitrateCheckerOn = settingsDataStore.bitrateCheckerEnabled.first()
            val disabledAddonUrls = settingsDataStore.disabledAddons.first()
            val defaultEpg = DefaultData.EPG_SOURCES.map {
                EpgSourceEntry(it.name, it.url, isDefault = true, enabled = true)
            }
            _uiState.value = _uiState.value.copy(
                playlistUrl = playlist,
                torboxKey = torbox,
                addons = addons,
                playlists = playlists,
                customEpgSources = customEpg,
                defaultEpgSources = defaultEpg,
                backupSources = backupSources,
                bufferDurationMs = bufferMs,
                weatherAlertsEnabled = weatherAlertsOn,
                frameRateMatching = frameRateMode,
                nextEpisodeAutoPlay = nextEpAutoPlay,
                nextEpisodeThresholdPercent = nextEpThreshold,
                bitrateCheckerEnabled = bitrateCheckerOn,
                disabledAddons = disabledAddonUrls
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
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun removePlaylist(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.playlists.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                settingsDataStore.setPlaylists(current)
                _uiState.value = _uiState.value.copy(playlists = current)
                cloudSyncManager.notifySettingsChanged()
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
                cloudSyncManager.notifySettingsChanged()
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
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun removeEpgSource(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.customEpgSources.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                settingsDataStore.setCustomEpgSources(current)
                _uiState.value = _uiState.value.copy(customEpgSources = current)
                cloudSyncManager.notifySettingsChanged()
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
                val (latestVersion, downloadUrl) = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()

                    // Fetch recent releases (not just latest) so we can skip web-only releases
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/dj2nazty/Merlottv-Kotlin-Build-2.0/releases?per_page=10")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: "[]"
                    val releases = org.json.JSONArray(body)

                    // Find the newest release that has an APK asset (skip web-only releases)
                    var foundVersion = ""
                    var foundUrl = ""
                    for (i in 0 until releases.length()) {
                        val release = releases.getJSONObject(i)
                        val assets = release.optJSONArray("assets")
                        if (assets != null && assets.length() > 0) {
                            // Check if any asset is an APK
                            var apkUrl = ""
                            for (j in 0 until assets.length()) {
                                val asset = assets.getJSONObject(j)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk")) {
                                    apkUrl = asset.optString("browser_download_url", "")
                                    break
                                }
                            }
                            if (apkUrl.isNotEmpty()) {
                                foundVersion = release.optString("tag_name", "").removePrefix("v")
                                foundUrl = apkUrl
                                break // newest release with APK found
                            }
                        }
                    }

                    Pair(foundVersion, foundUrl)
                }

                val isNewer = latestVersion.isNotEmpty() && isVersionNewer(latestVersion, BuildConfig.VERSION_NAME)

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

    // ─── Release Notes ───
    fun toggleReleaseNotes() {
        if (_uiState.value.showReleaseNotes) {
            // Already showing — just hide
            _uiState.value = _uiState.value.copy(showReleaseNotes = false)
            return
        }
        // Show and fetch if not already fetched
        _uiState.value = _uiState.value.copy(showReleaseNotes = true)
        if (_uiState.value.releaseNotes.isNotEmpty()) return // Already fetched
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetchingReleaseNotes = true)
            try {
                val notes = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    // Try fetching release for the current version tag
                    val currentTag = "v${BuildConfig.VERSION_NAME}"
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/dj2nazty/Merlottv-Kotlin-Build-2.0/releases/tags/$currentTag")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        json.optString("body", "No release notes available.")
                    } else {
                        // Fall back to latest release
                        val latestReq = Request.Builder()
                            .url("https://api.github.com/repos/dj2nazty/Merlottv-Kotlin-Build-2.0/releases/latest")
                            .header("Accept", "application/vnd.github.v3+json")
                            .build()
                        val latestResp = client.newCall(latestReq).execute()
                        val json = JSONObject(latestResp.body?.string() ?: "{}")
                        val tag = json.optString("tag_name", "")
                        val body = json.optString("body", "No release notes available.")
                        "Release $tag:\n$body"
                    }
                }
                _uiState.value = _uiState.value.copy(
                    releaseNotes = notes,
                    isFetchingReleaseNotes = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    releaseNotes = "Unable to fetch release notes: ${e.message}",
                    isFetchingReleaseNotes = false
                )
            }
        }
    }

    // ─── Backup Stream Sources ───
    fun addBackupSource(name: String, url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val current = _uiState.value.backupSources.toMutableList()
            current.add(BackupSourceEntry(name.ifBlank { "Backup ${current.size + 1}" }, url))
            settingsDataStore.setBackupSources(current)
            _uiState.value = _uiState.value.copy(backupSources = current)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun removeBackupSource(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.backupSources.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                settingsDataStore.setBackupSources(current)
                _uiState.value = _uiState.value.copy(backupSources = current)
                cloudSyncManager.notifySettingsChanged()
            }
        }
    }

    fun toggleBackupSource(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.backupSources.toMutableList()
            if (index in current.indices) {
                current[index] = current[index].copy(enabled = !current[index].enabled)
                settingsDataStore.setBackupSources(current)
                _uiState.value = _uiState.value.copy(backupSources = current)
                cloudSyncManager.notifySettingsChanged()
            }
        }
    }

    fun importBackupFile(content: String) {
        viewModelScope.launch {
            val lines = content.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }

            if (lines.isEmpty()) return@launch

            val current = _uiState.value.backupSources.toMutableList()
            lines.forEach { url ->
                // Extract a display name from the username parameter if possible
                val name = try {
                    val usernameParam = Regex("[?&]username=([^&]+)").find(url)?.groupValues?.get(1)
                    val hostParam = Regex("://([^/]+)").find(url)?.groupValues?.get(1)
                    usernameParam ?: hostParam ?: "Backup"
                } catch (_: Exception) { "Backup" }

                // Avoid duplicate URLs
                if (current.none { it.url == url }) {
                    current.add(BackupSourceEntry(name, url))
                }
            }
            settingsDataStore.setBackupSources(current)
            _uiState.value = _uiState.value.copy(backupSources = current)
        }
    }

    // ─── Profiles ───
    fun addProfile(name: String, colorIndex: Int) {
        viewModelScope.launch {
            try {
                profileDataStore.addProfile(name, colorIndex)
                cloudSyncManager.notifyProfilesChanged()
            } catch (_: Exception) {}
        }
    }

    fun removeProfile(profileId: String) {
        viewModelScope.launch {
            profileDataStore.removeProfile(profileId)
            cloudSyncManager.notifyProfilesChanged()
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            profileDataStore.setActiveProfile(profileId)
            _uiState.value = _uiState.value.copy(activeProfileId = profileId)
            cloudSyncManager.notifyProfilesChanged()
        }
    }

    // ─── Existing ───
    fun savePlaylistUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setPlaylistUrl(url)
            _uiState.value = _uiState.value.copy(playlistUrl = url)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun saveTorboxKey(key: String) {
        viewModelScope.launch {
            settingsDataStore.setTorboxKey(key)
            _uiState.value = _uiState.value.copy(torboxKey = key)
            cloudSyncManager.notifySettingsChanged()
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

    fun removeAddon(url: String) {
        viewModelScope.launch {
            addonRepository.removeAddon(url)
            val addons = addonRepository.getAllAddons().first()
            _uiState.value = _uiState.value.copy(addons = addons)
        }
    }

    fun toggleAddonEnabled(url: String, currentlyEnabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAddonEnabled(url, !currentlyEnabled)
            val updated = settingsDataStore.disabledAddons.first()
            _uiState.value = _uiState.value.copy(disabledAddons = updated)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    // ─── Weather Alerts Toggle ───
    fun toggleWeatherAlerts(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(weatherAlertsEnabled = enabled)
        viewModelScope.launch {
            settingsDataStore.setWeatherAlertsEnabled(enabled)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    // ─── Live TV Buffer Duration ───
    fun setBufferDuration(ms: Int) {
        val clamped = ms.coerceIn(300, 3000)
        _uiState.value = _uiState.value.copy(bufferDurationMs = clamped)
        viewModelScope.launch {
            settingsDataStore.setBufferDurationMs(clamped)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    // ─── Frame Rate Matching ───
    fun setFrameRateMatching(mode: String) {
        _uiState.value = _uiState.value.copy(frameRateMatching = mode)
        viewModelScope.launch {
            settingsDataStore.setFrameRateMatching(mode)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    // ─── Bitrate Checker ───
    fun toggleBitrateChecker(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(bitrateCheckerEnabled = enabled)
        viewModelScope.launch {
            settingsDataStore.setBitrateCheckerEnabled(enabled)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    // ─── Next Episode Auto-Play ───
    fun toggleNextEpisodeAutoPlay(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(nextEpisodeAutoPlay = enabled)
        viewModelScope.launch {
            settingsDataStore.setNextEpisodeAutoPlay(enabled)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun setNextEpisodeThreshold(percent: Int) {
        val clamped = percent.coerceIn(85, 99)
        _uiState.value = _uiState.value.copy(nextEpisodeThresholdPercent = clamped)
        viewModelScope.launch {
            settingsDataStore.setNextEpisodeThresholdPercent(clamped)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    // ─── Live TV Category Order ───
    private fun loadCategoryOrder() {
        viewModelScope.launch {
            val savedOrder = settingsDataStore.categoryOrder.first()
            _uiState.value = _uiState.value.copy(categoryOrder = savedOrder)

            // Load available categories from playlists
            try {
                val playlists = settingsDataStore.playlists.first()
                val enabledUrls = playlists.filter { it.enabled }.map { it.url }
                if (enabledUrls.isNotEmpty()) {
                    val channels = withContext(Dispatchers.IO) {
                        if (enabledUrls.size == 1) channelRepository.loadChannels(enabledUrls.first())
                        else channelRepository.loadMultipleChannels(enabledUrls)
                    }
                    val groups = channels.map { it.group }.distinct().sorted()
                    // If we have a saved order, show it; otherwise show default
                    val orderedGroups = if (savedOrder.isNotEmpty()) {
                        val ordered = savedOrder.filter { it in groups }.toMutableList()
                        val remaining = groups.filter { it !in ordered }
                        ordered + remaining
                    } else {
                        groups
                    }
                    _uiState.value = _uiState.value.copy(
                        availableCategories = orderedGroups,
                        categoryOrder = if (savedOrder.isNotEmpty()) savedOrder else orderedGroups
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun moveCategoryUp(index: Int) {
        if (index <= 0) return
        val current = _uiState.value.categoryOrder.toMutableList()
        if (index in current.indices) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            _uiState.value = _uiState.value.copy(categoryOrder = current)
        }
    }

    fun moveCategoryDown(index: Int) {
        val current = _uiState.value.categoryOrder.toMutableList()
        if (index in current.indices && index < current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            _uiState.value = _uiState.value.copy(categoryOrder = current)
        }
    }

    fun saveCategoryOrder() {
        viewModelScope.launch {
            settingsDataStore.setCategoryOrder(_uiState.value.categoryOrder)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun resetCategoryOrder() {
        viewModelScope.launch {
            settingsDataStore.setCategoryOrder(emptyList())
            _uiState.value = _uiState.value.copy(categoryOrder = _uiState.value.availableCategories)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    // ─── VOD Category System ───
    fun openVodCategorySystem() {
        _uiState.value = _uiState.value.copy(showVodCategorySystem = true, activeCategoryTab = "Home", reorderMode = false)
        loadVodCategories()
    }

    fun closeVodCategorySystem() {
        _uiState.value = _uiState.value.copy(showVodCategorySystem = false, reorderMode = false)
    }

    fun setActiveCategoryTab(tab: String) {
        _uiState.value = _uiState.value.copy(activeCategoryTab = tab, reorderMode = false)
    }

    fun toggleReorderMode() {
        _uiState.value = _uiState.value.copy(reorderMode = !_uiState.value.reorderMode)
    }

    private fun loadVodCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVodCategories = true)
            try {
                val addons = addonRepository.getAllAddons().first()
                val manifests = supervisorScope {
                    addons.map { addon ->
                        async(Dispatchers.IO) {
                            try { addonRepository.fetchManifest(addon.url) ?: addon } catch (_: Exception) { addon }
                        }
                    }.awaitAll()
                }

                data class CatJob(val addonId: String, val addonName: String, val catalogName: String, val catalogId: String, val type: String)

                val jobs = mutableListOf<CatJob>()
                for (manifest in manifests) {
                    for (catalog in manifest.catalogs) {
                        val requiresSpecial = catalog.extra.any { it.isRequired && it.name != "genre" && it.name != "search" }
                        if (requiresSpecial) continue
                        jobs.add(CatJob(manifest.id, manifest.name, catalog.name, catalog.id, catalog.type))
                    }
                }
                jobs.removeAll { it.addonName.contains("torbox", true) && it.catalogName.contains("your media", true) }

                // Build items with title format matching HomeViewModel/VodViewModel
                val allItems = jobs.map { job ->
                    val typeLabel = when (job.type) { "movie" -> "Movies"; "series" -> "Series"; else -> job.type }
                    val key = "${job.addonId}:${job.catalogId}:${job.type}"
                    val title = "${job.catalogName} $typeLabel — ${job.addonName}"
                    CategoryItem(key = key, title = title, enabled = true)
                }.distinctBy { it.key }

                // Load saved order and hidden for Home
                val homeOrder = settingsDataStore.homeCategoryOrder.first()
                val homeHidden = settingsDataStore.homeHiddenCategories.first()
                val homeItems = mergeWithSavedOrder(allItems, homeOrder, homeHidden)

                // Load saved order and hidden for VOD
                val vodOrder = settingsDataStore.vodCategoryOrder.first()
                val vodHidden = settingsDataStore.vodHiddenCategories.first()
                val vodItems = mergeWithSavedOrder(allItems, vodOrder, vodHidden)

                _uiState.value = _uiState.value.copy(
                    isLoadingVodCategories = false,
                    homeCategoryItems = homeItems,
                    vodCategoryItems = vodItems
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingVodCategories = false)
            }
        }
    }

    private fun mergeWithSavedOrder(
        allItems: List<CategoryItem>,
        savedOrder: List<String>,
        hiddenKeys: Set<String>
    ): List<CategoryItem> {
        if (savedOrder.isEmpty()) {
            return allItems.map { it.copy(enabled = it.key !in hiddenKeys) }
        }
        val itemMap = allItems.associateBy { it.key }
        // Saved items first (in order), then new items appended
        val ordered = mutableListOf<CategoryItem>()
        for (key in savedOrder) {
            val item = itemMap[key] ?: continue
            ordered.add(item.copy(enabled = key !in hiddenKeys))
        }
        // Append any new items not in saved order
        for (item in allItems) {
            if (ordered.none { it.key == item.key }) {
                ordered.add(item.copy(enabled = item.key !in hiddenKeys))
            }
        }
        return ordered
    }

    fun toggleHomeCategoryVisible(key: String) {
        val items = _uiState.value.homeCategoryItems.map {
            if (it.key == key) it.copy(enabled = !it.enabled) else it
        }
        _uiState.value = _uiState.value.copy(homeCategoryItems = items)
    }

    fun toggleVodCategoryVisible(key: String) {
        val items = _uiState.value.vodCategoryItems.map {
            if (it.key == key) it.copy(enabled = !it.enabled) else it
        }
        _uiState.value = _uiState.value.copy(vodCategoryItems = items)
    }

    fun moveHomeCategoryUp(index: Int) {
        if (index <= 0) return
        val items = _uiState.value.homeCategoryItems.toMutableList()
        if (index in items.indices) {
            val item = items.removeAt(index)
            items.add(index - 1, item)
            _uiState.value = _uiState.value.copy(homeCategoryItems = items)
        }
    }

    fun moveHomeCategoryDown(index: Int) {
        val items = _uiState.value.homeCategoryItems.toMutableList()
        if (index in items.indices && index < items.size - 1) {
            val item = items.removeAt(index)
            items.add(index + 1, item)
            _uiState.value = _uiState.value.copy(homeCategoryItems = items)
        }
    }

    fun moveVodCategoryUp(index: Int) {
        if (index <= 0) return
        val items = _uiState.value.vodCategoryItems.toMutableList()
        if (index in items.indices) {
            val item = items.removeAt(index)
            items.add(index - 1, item)
            _uiState.value = _uiState.value.copy(vodCategoryItems = items)
        }
    }

    fun moveVodCategoryDown(index: Int) {
        val items = _uiState.value.vodCategoryItems.toMutableList()
        if (index in items.indices && index < items.size - 1) {
            val item = items.removeAt(index)
            items.add(index + 1, item)
            _uiState.value = _uiState.value.copy(vodCategoryItems = items)
        }
    }

    fun saveVodCategorySystem() {
        viewModelScope.launch {
            val homeItems = _uiState.value.homeCategoryItems
            val vodItems = _uiState.value.vodCategoryItems
            settingsDataStore.setHomeCategoryOrder(homeItems.map { it.key })
            settingsDataStore.setHomeHiddenCategories(homeItems.filter { !it.enabled }.map { it.key }.toSet())
            settingsDataStore.setVodCategoryOrder(vodItems.map { it.key })
            settingsDataStore.setVodHiddenCategories(vodItems.filter { !it.enabled }.map { it.key }.toSet())
            _uiState.value = _uiState.value.copy(showVodCategorySystem = false, reorderMode = false)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun resetHomeCategoryOrder() {
        viewModelScope.launch {
            settingsDataStore.setHomeCategoryOrder(emptyList())
            settingsDataStore.setHomeHiddenCategories(emptySet())
            loadVodCategories()
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun resetVodCategoryOrder() {
        viewModelScope.launch {
            settingsDataStore.setVodCategoryOrder(emptyList())
            settingsDataStore.setVodHiddenCategories(emptySet())
            loadVodCategories()
            cloudSyncManager.notifySettingsChanged()
        }
    }
}
