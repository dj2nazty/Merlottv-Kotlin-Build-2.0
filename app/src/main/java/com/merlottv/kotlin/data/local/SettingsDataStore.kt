package com.merlottv.kotlin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class PlaylistEntry(
    val name: String,
    val url: String,
    val enabled: Boolean = true
)

data class EpgSourceEntry(
    val name: String,
    val url: String,
    val isDefault: Boolean = false,
    val enabled: Boolean = true
)

data class BackupSourceEntry(
    val name: String,
    val url: String,
    val enabled: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    companion object {
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val PLAYLISTS = stringPreferencesKey("playlists_json")
        val EPG_URLS = stringPreferencesKey("epg_urls")
        val CUSTOM_EPG_SOURCES = stringPreferencesKey("custom_epg_sources")
        val BACKUP_SOURCES = stringPreferencesKey("backup_sources_json")
        val LAST_WATCHED_CHANNEL_ID = stringPreferencesKey("last_watched_channel_id")
        val TORBOX_KEY = stringPreferencesKey("torbox_key")
        val CUSTOM_ADDONS = stringPreferencesKey("custom_addons")

        // Live TV category order
        val CATEGORY_ORDER = stringPreferencesKey("live_tv_category_order")

        // Live TV buffer duration (milliseconds) — adjustable 300ms to 3000ms
        val BUFFER_DURATION_MS = intPreferencesKey("live_tv_buffer_duration_ms")
        const val DEFAULT_BUFFER_MS = 1000 // 1.0 second default (matches TiviMate)

        // Weather
        val WEATHER_ZIP = stringPreferencesKey("weather_zip_code")
        const val DEFAULT_WEATHER_ZIP = "43616"

        // Weather alerts on Live TV / VOD
        val WEATHER_ALERTS_ENABLED = booleanPreferencesKey("weather_alerts_enabled")

        // Subtitle settings
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")         // 1.0 = normal
        val SUBTITLE_FONT = stringPreferencesKey("subtitle_font")       // "default", "monospace", "serif"

        const val DEFAULT_PLAYLIST = "https://x-api.uk/get.php?username=MetrlotBackup&password=2813308004&type=m3u_plus"
        const val DEFAULT_TORBOX_KEY = "50c74a49-a6bc-40e9-931e-1cee1943e87b"
    }

    // ─── Legacy single playlist (backward compat) ───
    val playlistUrl: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[PLAYLIST_URL] ?: DEFAULT_PLAYLIST
    }

    // ─── Multiple Playlists ───
    val playlists: Flow<List<PlaylistEntry>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[PLAYLISTS]
        if (json != null) {
            parsePlaylistsJson(json)
        } else {
            // Migrate from single URL
            val singleUrl = prefs[PLAYLIST_URL] ?: DEFAULT_PLAYLIST
            listOf(PlaylistEntry("Merlot TV", singleUrl, true))
        }
    }

    suspend fun setPlaylists(entries: List<PlaylistEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("name", entry.name)
            obj.put("url", entry.url)
            obj.put("enabled", entry.enabled)
            jsonArray.put(obj)
        }
        context.settingsDataStore.edit { it[PLAYLISTS] = jsonArray.toString() }
    }

    private fun parsePlaylistsJson(json: String): List<PlaylistEntry> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PlaylistEntry(
                    name = obj.optString("name", "Playlist ${i + 1}"),
                    url = obj.optString("url", ""),
                    enabled = obj.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) {
            listOf(PlaylistEntry("Merlot TV", DEFAULT_PLAYLIST, true))
        }
    }

    // ─── Custom EPG Sources ───
    val customEpgSources: Flow<List<EpgSourceEntry>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[CUSTOM_EPG_SOURCES]
        if (json != null) parseEpgSourcesJson(json) else emptyList()
    }

    suspend fun setCustomEpgSources(entries: List<EpgSourceEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("name", entry.name)
            obj.put("url", entry.url)
            obj.put("enabled", entry.enabled)
            jsonArray.put(obj)
        }
        context.settingsDataStore.edit { it[CUSTOM_EPG_SOURCES] = jsonArray.toString() }
    }

    private fun parseEpgSourcesJson(json: String): List<EpgSourceEntry> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                EpgSourceEntry(
                    name = obj.optString("name", "EPG ${i + 1}"),
                    url = obj.optString("url", ""),
                    isDefault = false,
                    enabled = obj.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ─── Backup Stream Sources ───
    val backupSources: Flow<List<BackupSourceEntry>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[BACKUP_SOURCES]
        if (json != null) {
            parseBackupSourcesJson(json)
        } else {
            // Default: use hardcoded backup sources from DefaultData
            com.merlottv.kotlin.domain.model.DefaultData.DEFAULT_BACKUP_SOURCES.map { src ->
                BackupSourceEntry(name = src.name, url = src.url, enabled = true)
            }
        }
    }

    suspend fun setBackupSources(entries: List<BackupSourceEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("name", entry.name)
            obj.put("url", entry.url)
            obj.put("enabled", entry.enabled)
            jsonArray.put(obj)
        }
        context.settingsDataStore.edit { it[BACKUP_SOURCES] = jsonArray.toString() }
    }

    private fun parseBackupSourcesJson(json: String): List<BackupSourceEntry> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BackupSourceEntry(
                    name = obj.optString("name", "Backup ${i + 1}"),
                    url = obj.optString("url", ""),
                    enabled = obj.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ─── Last Watched Channel ───
    val lastWatchedChannelId: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[LAST_WATCHED_CHANNEL_ID] ?: ""
    }

    suspend fun setLastWatchedChannelId(channelId: String) {
        context.settingsDataStore.edit { it[LAST_WATCHED_CHANNEL_ID] = channelId }
    }

    // ─── Existing settings ───
    val torboxKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[TORBOX_KEY] ?: DEFAULT_TORBOX_KEY
    }

    val customAddons: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[CUSTOM_ADDONS] ?: "[]"
    }

    suspend fun setPlaylistUrl(url: String) {
        context.settingsDataStore.edit { it[PLAYLIST_URL] = url }
    }

    suspend fun setTorboxKey(key: String) {
        context.settingsDataStore.edit { it[TORBOX_KEY] = key }
    }

    suspend fun setCustomAddons(json: String) {
        context.settingsDataStore.edit { it[CUSTOM_ADDONS] = json }
    }

    // ─── Weather Alerts Toggle ───
    val weatherAlertsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[WEATHER_ALERTS_ENABLED] ?: true // Enabled by default
    }

    suspend fun setWeatherAlertsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[WEATHER_ALERTS_ENABLED] = enabled }
    }

    // ─── Subtitle Settings ───
    val subtitlesEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SUBTITLES_ENABLED] ?: false
    }

    val subtitleLanguage: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SUBTITLE_LANGUAGE] ?: "eng"
    }

    val subtitleSize: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[SUBTITLE_SIZE] ?: 1.0f
    }

    val subtitleFont: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SUBTITLE_FONT] ?: "default"
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[SUBTITLES_ENABLED] = enabled }
    }

    suspend fun setSubtitleLanguage(lang: String) {
        context.settingsDataStore.edit { it[SUBTITLE_LANGUAGE] = lang }
    }

    suspend fun setSubtitleSize(size: Float) {
        context.settingsDataStore.edit { it[SUBTITLE_SIZE] = size }
    }

    suspend fun setSubtitleFont(font: String) {
        context.settingsDataStore.edit { it[SUBTITLE_FONT] = font }
    }

    // ─── Live TV Buffer Duration ───
    val bufferDurationMs: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[BUFFER_DURATION_MS] ?: DEFAULT_BUFFER_MS
    }

    suspend fun setBufferDurationMs(ms: Int) {
        // Clamp to valid range: 300ms – 3000ms
        val clamped = ms.coerceIn(300, 3000)
        context.settingsDataStore.edit { it[BUFFER_DURATION_MS] = clamped }
    }

    // ─── Weather ZIP Code ───
    val weatherZipCode: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[WEATHER_ZIP] ?: DEFAULT_WEATHER_ZIP
    }

    suspend fun setWeatherZipCode(zip: String) {
        context.settingsDataStore.edit { it[WEATHER_ZIP] = zip }
    }

    // ─── Live TV Category Order ───
    val categoryOrder: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[CATEGORY_ORDER]
        if (json != null) {
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList() }
        } else emptyList()
    }

    suspend fun setCategoryOrder(order: List<String>) {
        val jsonArray = JSONArray()
        order.forEach { jsonArray.put(it) }
        context.settingsDataStore.edit { it[CATEGORY_ORDER] = jsonArray.toString() }
    }
}
