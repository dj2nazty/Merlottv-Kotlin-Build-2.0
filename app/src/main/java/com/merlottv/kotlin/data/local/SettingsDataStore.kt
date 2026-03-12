package com.merlottv.kotlin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        if (json != null) parseBackupSourcesJson(json) else emptyList()
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
}
