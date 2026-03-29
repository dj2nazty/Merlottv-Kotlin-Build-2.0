package com.merlottv.kotlin.ui.screens.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.BuildConfig
import com.merlottv.kotlin.data.local.BackupSourceEntry
import com.merlottv.kotlin.data.local.CustomYouTubeChannelEntry
import com.merlottv.kotlin.data.local.EpgSourceEntry
import com.merlottv.kotlin.data.local.PlaylistEntry
import com.merlottv.kotlin.data.local.XtremeServerEntry
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.local.UserProfile
import com.merlottv.kotlin.data.repository.YouTubeRepository
import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.YouTubeChannel
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
    // In-app APK download
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Int = 0, // 0-100
    val downloadError: String? = null,
    // Backup stream sources
    val backupSources: List<BackupSourceEntry> = emptyList(),
    // Xtreme Backup servers
    val xtremeServers: List<XtremeServerEntry> = emptyList(),
    // Profiles
    val profiles: List<UserProfile> = emptyList(),
    val activeProfileId: String = "default",
    // Live TV category order
    val categoryOrder: List<String> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    // Live TV buffer duration (ms) — adjustable 300–3000 in 100ms steps
    val bufferDurationMs: Int = 800,
    // Buffer Automatic Backup Scan — auto-failover to backup M3U on rebuffer
    val bufferAutoBackupScan: Boolean = false,
    // Weather alerts on Live TV / VOD
    val weatherAlertsEnabled: Boolean = true,
    // Auto frame rate matching
    val frameRateMatching: String = "off", // "off", "start", "start_stop"
    // Next episode auto-play
    val nextEpisodeAutoPlay: Boolean = true,
    val nextEpisodeThresholdPercent: Int = 95,
    // Xtream stream format
    val xtreamOutputFormat: String = "m3u8", // "m3u8" (HLS) or "ts" (MPEG-TS)
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
    val reorderMode: Boolean = false,
    // Custom YouTube Channels
    val customYouTubeChannels: List<CustomYouTubeChannelEntry> = emptyList(),
    val youtubeHandleInput: String = "",
    val youtubeSearching: Boolean = false,
    val youtubeSearchResult: YouTubeChannel? = null,
    val youtubeSearchError: String? = null
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
    private val cloudSyncManager: CloudSyncManager,
    private val youtubeRepository: YouTubeRepository
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
            val xtremeServers = settingsDataStore.xtremeServers.first()
            val bufferMs = settingsDataStore.bufferDurationMs.first()
            val bufferAutoBackup = settingsDataStore.bufferAutoBackupScan.first()
            val weatherAlertsOn = settingsDataStore.weatherAlertsEnabled.first()
            val frameRateMode = settingsDataStore.frameRateMatching.first()
            val nextEpAutoPlay = settingsDataStore.nextEpisodeAutoPlay.first()
            val nextEpThreshold = settingsDataStore.nextEpisodeThresholdPercent.first()
            val bitrateCheckerOn = settingsDataStore.bitrateCheckerEnabled.first()
            val xtreamFormat = settingsDataStore.xtreamOutputFormat.first()
            val disabledAddonUrls = settingsDataStore.disabledAddons.first()
            val customYtChannels = settingsDataStore.customYouTubeChannels.first()
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
                xtremeServers = xtremeServers,
                bufferDurationMs = bufferMs,
                bufferAutoBackupScan = bufferAutoBackup,
                weatherAlertsEnabled = weatherAlertsOn,
                frameRateMatching = frameRateMode,
                nextEpisodeAutoPlay = nextEpAutoPlay,
                nextEpisodeThresholdPercent = nextEpThreshold,
                bitrateCheckerEnabled = bitrateCheckerOn,
                xtreamOutputFormat = xtreamFormat,
                disabledAddons = disabledAddonUrls,
                customYouTubeChannels = customYtChannels
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

    // ─── In-App APK Download & Install ───
    fun downloadUpdate() {
        val url = _uiState.value.updateUrl
        if (url.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingUpdate = true,
                downloadProgress = 0,
                downloadError = null
            )
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

                    val body = response.body ?: throw Exception("Empty response")
                    val contentLength = body.contentLength()
                    val cacheDir = getApplication<Application>().cacheDir
                    val apkFile = java.io.File(cacheDir, "merlottv_update.apk")

                    apkFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Long = 0
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                if (contentLength > 0) {
                                    val progress = (bytesRead * 100 / contentLength).toInt()
                                    _uiState.value = _uiState.value.copy(downloadProgress = progress)
                                }
                            }
                        }
                    }
                    apkFile
                }

                _uiState.value = _uiState.value.copy(
                    isDownloadingUpdate = false,
                    downloadProgress = 100
                )

                // Trigger install via FileProvider
                val context = getApplication<Application>()
                val authority = "${context.packageName}.fileprovider"
                val apkUri = androidx.core.content.FileProvider.getUriForFile(context, authority, apkFile)
                val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloadingUpdate = false,
                    downloadError = e.message ?: "Download failed"
                )
            }
        }
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

    // ─── Xtreme Backup Servers ───
    fun addXtremeServer(name: String, serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) return
        viewModelScope.launch {
            val current = _uiState.value.xtremeServers.toMutableList()
            current.add(XtremeServerEntry(name.ifBlank { "Server ${current.size + 1}" }, serverUrl, username, password))
            settingsDataStore.setXtremeServers(current)
            _uiState.value = _uiState.value.copy(xtremeServers = current)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    fun removeXtremeServer(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.xtremeServers.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                settingsDataStore.setXtremeServers(current)
                _uiState.value = _uiState.value.copy(xtremeServers = current)
                cloudSyncManager.notifySettingsChanged()
            }
        }
    }

    fun toggleXtremeServer(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.xtremeServers.toMutableList()
            if (index in current.indices) {
                current[index] = current[index].copy(enabled = !current[index].enabled)
                settingsDataStore.setXtremeServers(current)
                _uiState.value = _uiState.value.copy(xtremeServers = current)
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

    // ─── Buffer Automatic Backup Scan ───
    fun toggleBufferAutoBackupScan(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(bufferAutoBackupScan = enabled)
        viewModelScope.launch {
            settingsDataStore.setBufferAutoBackupScan(enabled)
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

    // ─── Xtream Output Format ───
    fun setXtreamOutputFormat(format: String) {
        _uiState.value = _uiState.value.copy(xtreamOutputFormat = format)
        viewModelScope.launch {
            settingsDataStore.setXtreamOutputFormat(format)
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
                // Use getEnabledAddons() to match what Home/VOD screens actually show
                val addons = addonRepository.getEnabledAddons().first()
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
                 .sortedBy { defaultSortOrder(it.title) } // Match Home screen default order

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
        // Check if we're using the default hide marker (hide all non-MerlotTV+ catalogs)
        val useDefaultHide = settingsDataStore.HOME_DEFAULT_HIDE_MARKER in hiddenKeys

        fun isEnabled(key: String): Boolean {
            return if (useDefaultHide) {
                // Default: only MerlotTV+ catalogs enabled, excluding network catalogs
                key.startsWith("com.merlottv.tmdb:") && !key.contains(":net.")
            } else {
                key !in hiddenKeys
            }
        }

        if (savedOrder.isEmpty()) {
            return allItems.map { it.copy(enabled = isEnabled(it.key)) }
        }
        val itemMap = allItems.associateBy { it.key }
        // Saved items first (in order), then new items appended
        val ordered = mutableListOf<CategoryItem>()
        for (key in savedOrder) {
            val item = itemMap[key] ?: continue
            ordered.add(item.copy(enabled = isEnabled(key)))
        }
        // Append any new items not in saved order
        for (item in allItems) {
            if (ordered.none { it.key == item.key }) {
                ordered.add(item.copy(enabled = isEnabled(item.key)))
            }
        }
        return ordered
    }

    fun toggleHomeCategoryVisible(key: String) {
        val items = _uiState.value.homeCategoryItems.map {
            if (it.key == key) it.copy(enabled = !it.enabled) else it
        }
        val toggled = items.find { it.key == key }
        Log.d("SettingsVM", "toggleHome: $key -> enabled=${toggled?.enabled}")
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
            val homeHidden = homeItems.filter { !it.enabled }.map { it.key }.toSet()
            val vodHidden = vodItems.filter { !it.enabled }.map { it.key }.toSet()
            Log.d("SettingsVM", "SAVE: ${homeItems.size} home items, ${homeHidden.size} hidden home, ${vodItems.size} vod items, ${vodHidden.size} hidden vod")
            Log.d("SettingsVM", "SAVE hidden home keys: $homeHidden")
            settingsDataStore.setHomeCategoryOrder(homeItems.map { it.key })
            settingsDataStore.setHomeHiddenCategories(homeHidden)
            settingsDataStore.setVodCategoryOrder(vodItems.map { it.key })
            settingsDataStore.setVodHiddenCategories(vodHidden)
            Log.d("SettingsVM", "SAVE complete — closing overlay")
            _uiState.value = _uiState.value.copy(showVodCategorySystem = false, reorderMode = false)
            cloudSyncManager.notifySettingsChanged()
        }
    }

    /** Must match HomeViewModel.defaultSortOrder exactly so Settings and Home agree */
    /** Must match HomeViewModel.defaultSortOrder exactly so Settings and Home agree */
    private fun defaultSortOrder(title: String): Int {
        val t = title.lowercase()
        return when {
            // MerlotTV+ catalogs first — user's preferred order
            "popular new tv" in t -> 0
            "popular" in t && "movie" in t -> 1
            "popular" in t && "series" in t -> 2
            "new" in t && "movie" in t -> 3
            "new" in t && "series" in t -> 4
            "featured" in t && "movie" in t -> 5
            "featured" in t && "series" in t -> 6
            "airing today" in t -> 7
            "on the air" in t -> 8
            "upcoming" in t -> 9
            "in theaters" in t || "now_playing" in t -> 10
            "top rated" in t -> 11
            // Streaming service catalogs
            "trending" in t -> 12
            "netflix" in t -> 13
            "disney" in t -> 14
            "prime" in t || "amazon" in t -> 15
            "hbo" in t || "max" in t -> 16
            "apple" in t -> 17
            "paramount" in t -> 18
            "peacock" in t -> 19
            "discovery" in t -> 20
            // Network catalogs
            "nbc" in t -> 21
            "abc" in t -> 22
            "cbs" in t -> 23
            "fox" in t -> 24
            "cw" in t -> 25
            "showtime" in t -> 26
            "imdb" in t -> 27
            "latest" in t -> 28
            else -> 30
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

    // ─── Custom YouTube Channels ───
    fun onYouTubeHandleInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(youtubeHandleInput = input)
    }

    fun searchYouTubeChannel() {
        val handle = _uiState.value.youtubeHandleInput.trim()
        if (handle.isBlank()) return
        _uiState.value = _uiState.value.copy(
            youtubeSearching = true,
            youtubeSearchResult = null,
            youtubeSearchError = null
        )
        viewModelScope.launch {
            try {
                val channel = youtubeRepository.resolveChannelByHandle(handle)
                if (channel != null) {
                    _uiState.value = _uiState.value.copy(
                        youtubeSearching = false,
                        youtubeSearchResult = channel
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        youtubeSearching = false,
                        youtubeSearchError = "Channel not found for \"$handle\""
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    youtubeSearching = false,
                    youtubeSearchError = "Search failed: ${e.message}"
                )
            }
        }
    }

    fun confirmAddYouTubeChannel() {
        val result = _uiState.value.youtubeSearchResult ?: return
        viewModelScope.launch {
            val current = _uiState.value.customYouTubeChannels.toMutableList()
            // Avoid duplicates
            if (current.any { it.channelId == result.channelId }) {
                _uiState.value = _uiState.value.copy(
                    youtubeSearchResult = null,
                    youtubeHandleInput = "",
                    youtubeSearchError = "${result.channelName} is already added"
                )
                return@launch
            }
            current.add(
                CustomYouTubeChannelEntry(
                    channelId = result.channelId,
                    channelName = result.channelName,
                    handle = result.handle,
                    avatarUrl = result.avatarUrl
                )
            )
            settingsDataStore.setCustomYouTubeChannels(current)
            youtubeRepository.invalidateCache()
            _uiState.value = _uiState.value.copy(
                customYouTubeChannels = current,
                youtubeSearchResult = null,
                youtubeHandleInput = "",
                youtubeSearchError = null
            )
        }
    }

    fun dismissYouTubeSearch() {
        _uiState.value = _uiState.value.copy(
            youtubeSearchResult = null,
            youtubeSearchError = null
        )
    }

    fun removeCustomYouTubeChannel(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.customYouTubeChannels.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                settingsDataStore.setCustomYouTubeChannels(current)
                youtubeRepository.invalidateCache()
                _uiState.value = _uiState.value.copy(customYouTubeChannels = current)
            }
        }
    }

    fun toggleCustomYouTubeChannel(index: Int) {
        viewModelScope.launch {
            val current = _uiState.value.customYouTubeChannels.toMutableList()
            if (index in current.indices) {
                current[index] = current[index].copy(enabled = !current[index].enabled)
                settingsDataStore.setCustomYouTubeChannels(current)
                youtubeRepository.invalidateCache()
                _uiState.value = _uiState.value.copy(customYouTubeChannels = current)
            }
        }
    }
}
