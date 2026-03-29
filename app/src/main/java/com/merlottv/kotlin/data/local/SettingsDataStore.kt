package com.merlottv.kotlin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

data class XtremeServerEntry(
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val enabled: Boolean = true
) {
    /** Build full Xtream Codes M3U playlist URL */
    fun buildM3uUrl(outputFormat: String = "m3u8"): String {
        val base = serverUrl.trimEnd('/')
        return "$base/get.php?username=$username&password=$password&type=m3u_plus&output=$outputFormat"
    }
}

data class CustomYouTubeChannelEntry(
    val channelId: String,
    val channelName: String,
    val handle: String,
    val avatarUrl: String = "",
    val enabled: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    companion object {
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val PLAYLISTS = stringPreferencesKey("playlists_json")
        val EPG_URLS = stringPreferencesKey("epg_urls")
        val CUSTOM_EPG_SOURCES = stringPreferencesKey("custom_epg_sources")
        val BACKUP_SOURCES = stringPreferencesKey("backup_sources_json")
        val XTREME_SERVERS = stringPreferencesKey("xtreme_servers_json")
        val CUSTOM_YOUTUBE_CHANNELS = stringPreferencesKey("custom_youtube_channels_json")
        val LAST_WATCHED_CHANNEL_ID = stringPreferencesKey("last_watched_channel_id")
        val TORBOX_KEY = stringPreferencesKey("torbox_key")
        val CUSTOM_ADDONS = stringPreferencesKey("custom_addons")
        val DISABLED_ADDONS = stringSetPreferencesKey("disabled_addons")

        // Live TV category order
        val CATEGORY_ORDER = stringPreferencesKey("live_tv_category_order")

        // VOD Category System — order and visibility for Home and VOD screens
        val HOME_CATEGORY_ORDER = stringPreferencesKey("home_category_order")
        val HOME_HIDDEN_CATEGORIES = stringPreferencesKey("home_hidden_categories")
        val VOD_CATEGORY_ORDER = stringPreferencesKey("vod_category_order")
        val VOD_HIDDEN_CATEGORIES = stringPreferencesKey("vod_hidden_categories")

        // Live TV buffer duration (milliseconds) — adjustable 300ms to 3000ms
        val BUFFER_DURATION_MS = intPreferencesKey("live_tv_buffer_duration_ms")
        const val DEFAULT_BUFFER_MS = 1000 // 1.0 second default (matches TiviMate)

        // Buffer Automatic Backup Scan — auto-failover to backup M3U on rebuffer
        val BUFFER_AUTO_BACKUP_SCAN = booleanPreferencesKey("buffer_auto_backup_scan")

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

        // Frame rate matching — switches display refresh rate to match video (eliminates judder)
        val FRAME_RATE_MATCHING = stringPreferencesKey("frame_rate_matching") // "off", "start", "start_stop"

        // Next episode auto-play
        val NEXT_EPISODE_AUTOPLAY = booleanPreferencesKey("next_episode_autoplay")

        // Bitrate checker — show video/audio bitrate info in Live TV Quick Menu
        val BITRATE_CHECKER_ENABLED = booleanPreferencesKey("bitrate_checker_enabled")
        val NEXT_EPISODE_THRESHOLD_PERCENT = intPreferencesKey("next_episode_threshold_percent") // 90-99

        // Xtream Codes stream output format — "m3u8" (HLS) or "ts" (MPEG-TS)
        val XTREAM_OUTPUT_FORMAT = stringPreferencesKey("xtream_output_format")

        const val DEFAULT_PLAYLIST = "https://x-api.uk/get.php?username=MetrlotBackup&password=2813308004&type=m3u_plus"
        const val XTREME_BACKUP_PLAYLIST = "xtream://pianopride.com:8080/h7z2NejYf7/0859309752"
        val XTREME_MIGRATION_DONE = booleanPreferencesKey("xtreme_backup_migration_done")
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
            // First launch / migration — include Xtreme Backup by default
            val singleUrl = prefs[PLAYLIST_URL] ?: DEFAULT_PLAYLIST
            listOf(
                PlaylistEntry("Merlot TV", singleUrl, true),
                PlaylistEntry("Xtreme Backup", XTREME_BACKUP_PLAYLIST, true)
            )
        }
    }

    /** One-time migration: inject Xtreme Backup into existing saved playlists */
    suspend fun migrateXtremeBackup() {
        context.settingsDataStore.edit { prefs ->
            if (prefs[XTREME_MIGRATION_DONE] == true) return@edit
            val json = prefs[PLAYLISTS]
            if (json != null) {
                val list = parsePlaylistsJson(json)
                if (list.none { it.url == XTREME_BACKUP_PLAYLIST }) {
                    val merlot = list.firstOrNull()
                    val rest = list.drop(1)
                    val updated = listOfNotNull(merlot) + PlaylistEntry("Xtreme Backup", XTREME_BACKUP_PLAYLIST, true) + rest
                    val jsonArray = JSONArray()
                    updated.forEach { entry ->
                        val obj = JSONObject()
                        obj.put("name", entry.name)
                        obj.put("url", entry.url)
                        obj.put("enabled", entry.enabled)
                        jsonArray.put(obj)
                    }
                    prefs[PLAYLISTS] = jsonArray.toString()
                }
            }
            prefs[XTREME_MIGRATION_DONE] = true
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

    // ─── Xtreme Backup Servers ───
    val xtremeServers: Flow<List<XtremeServerEntry>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[XTREME_SERVERS]
        if (json != null) {
            parseXtremeServersJson(json)
        } else {
            DEFAULT_XTREME_SERVERS
        }
    }

    private val DEFAULT_XTREME_SERVERS = listOf(
        XtremeServerEntry(
            name = "PianoPride",
            serverUrl = "http://pianopride.com:8080",
            username = "h7z2NejYf7",
            password = "0859309752",
            enabled = true
        )
    )

    suspend fun setXtremeServers(entries: List<XtremeServerEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("name", entry.name)
            obj.put("serverUrl", entry.serverUrl)
            obj.put("username", entry.username)
            obj.put("password", entry.password)
            obj.put("enabled", entry.enabled)
            jsonArray.put(obj)
        }
        context.settingsDataStore.edit { it[XTREME_SERVERS] = jsonArray.toString() }
    }

    private fun parseXtremeServersJson(json: String): List<XtremeServerEntry> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                XtremeServerEntry(
                    name = obj.optString("name", "Server ${i + 1}"),
                    serverUrl = obj.optString("serverUrl", ""),
                    username = obj.optString("username", ""),
                    password = obj.optString("password", ""),
                    enabled = obj.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ─── Custom YouTube Channels ───
    val customYouTubeChannels: Flow<List<CustomYouTubeChannelEntry>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[CUSTOM_YOUTUBE_CHANNELS]
        if (json != null) parseCustomYouTubeChannelsJson(json) else emptyList()
    }

    suspend fun setCustomYouTubeChannels(entries: List<CustomYouTubeChannelEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("channelId", entry.channelId)
            obj.put("channelName", entry.channelName)
            obj.put("handle", entry.handle)
            obj.put("avatarUrl", entry.avatarUrl)
            obj.put("enabled", entry.enabled)
            jsonArray.put(obj)
        }
        context.settingsDataStore.edit { it[CUSTOM_YOUTUBE_CHANNELS] = jsonArray.toString() }
    }

    private fun parseCustomYouTubeChannelsJson(json: String): List<CustomYouTubeChannelEntry> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CustomYouTubeChannelEntry(
                    channelId = obj.optString("channelId", ""),
                    channelName = obj.optString("channelName", "Channel ${i + 1}"),
                    handle = obj.optString("handle", ""),
                    avatarUrl = obj.optString("avatarUrl", ""),
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

    // ─── Disabled Addons ───
    val disabledAddons: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[DISABLED_ADDONS] ?: emptySet()
    }

    suspend fun setAddonEnabled(url: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[DISABLED_ADDONS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                current.remove(url)
            } else {
                current.add(url)
            }
            prefs[DISABLED_ADDONS] = current
        }
    }

    // ─── Cloud Sync: Restore disabled addons (bulk write) ───
    suspend fun restoreDisabledAddons(addons: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[DISABLED_ADDONS] = addons
        }
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

    // ─── Frame Rate Matching ───
    val frameRateMatching: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[FRAME_RATE_MATCHING] ?: "off"
    }

    suspend fun setFrameRateMatching(mode: String) {
        context.settingsDataStore.edit { it[FRAME_RATE_MATCHING] = mode }
    }

    // ─── Next Episode Auto-Play ───
    val nextEpisodeAutoPlay: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[NEXT_EPISODE_AUTOPLAY] ?: true
    }

    suspend fun setNextEpisodeAutoPlay(enabled: Boolean) {
        context.settingsDataStore.edit { it[NEXT_EPISODE_AUTOPLAY] = enabled }
    }

    val nextEpisodeThresholdPercent: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[NEXT_EPISODE_THRESHOLD_PERCENT] ?: 95
    }

    suspend fun setNextEpisodeThresholdPercent(percent: Int) {
        context.settingsDataStore.edit { it[NEXT_EPISODE_THRESHOLD_PERCENT] = percent.coerceIn(85, 99) }
    }

    // ─── Xtream Output Format (HLS vs MPEG-TS) ───
    val xtreamOutputFormat: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[XTREAM_OUTPUT_FORMAT] ?: "m3u8" // Default to HLS
    }

    suspend fun setXtreamOutputFormat(format: String) {
        context.settingsDataStore.edit { it[XTREAM_OUTPUT_FORMAT] = format }
    }

    // ─── Bitrate Checker Toggle ───
    val bitrateCheckerEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[BITRATE_CHECKER_ENABLED] ?: false // Disabled by default
    }

    suspend fun setBitrateCheckerEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[BITRATE_CHECKER_ENABLED] = enabled }
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

    // ─── Buffer Automatic Backup Scan ───
    val bufferAutoBackupScan: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[BUFFER_AUTO_BACKUP_SCAN] ?: false // Off by default (old behavior)
    }

    suspend fun setBufferAutoBackupScan(enabled: Boolean) {
        context.settingsDataStore.edit { it[BUFFER_AUTO_BACKUP_SCAN] = enabled }
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

    // ─── VOD Category System ───
    private fun readJsonStringList(prefs: Preferences, key: Preferences.Key<String>): List<String> {
        val json = prefs[key] ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun writeJsonStringList(key: Preferences.Key<String>, list: List<String>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        context.settingsDataStore.edit { it[key] = jsonArray.toString() }
    }

    val homeCategoryOrder: Flow<List<String>> = context.settingsDataStore.data.map { readJsonStringList(it, HOME_CATEGORY_ORDER) }
    /**
     * Default: only MerlotTV+ catalogs are visible on the Home screen.
     * All other addon catalogs (Netflix, IMDB, Fusion, Torbox, network catalogs)
     * are hidden until the user explicitly enables them in Settings.
     *
     * We use a special marker "@@DEFAULT_HIDE_NON_MERLOT@@" so HomeViewModel
     * knows to apply the default filter instead of a static key list.
     */
    val HOME_DEFAULT_HIDE_MARKER = "@@DEFAULT_HIDE_NON_MERLOT@@"

    /** MerlotTV+ network catalogs always hidden by default even after user saves */
    private val ALWAYS_DEFAULT_HIDDEN = setOf(
        "com.merlottv.tmdb:net.nbc:series",
        "com.merlottv.tmdb:net.abc:series",
        "com.merlottv.tmdb:net.cbs:series",
        "com.merlottv.tmdb:net.fox:series",
        "com.merlottv.tmdb:net.cw:series",
        "com.merlottv.tmdb:net.hbo:series",
        "com.merlottv.tmdb:net.showtime:series",
        "com.merlottv.tmdb:net.fx:series",
        "com.merlottv.tmdb:net.amc:series",
        "com.merlottv.tmdb:net.usa:series",
        "com.merlottv.tmdb:net.bravo:series",
        "com.merlottv.tmdb:net.hgtv:series",
        "com.merlottv.tmdb:net.history:series",
        "com.merlottv.tmdb:net.pbs:series"
    )

    val homeHiddenCategories: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        val saved = readJsonStringList(prefs, HOME_HIDDEN_CATEGORIES)
        if (saved.isEmpty()) setOf(HOME_DEFAULT_HIDE_MARKER) else saved.toSet()
    }
    val vodCategoryOrder: Flow<List<String>> = context.settingsDataStore.data.map { readJsonStringList(it, VOD_CATEGORY_ORDER) }
    val vodHiddenCategories: Flow<Set<String>> = context.settingsDataStore.data.map { readJsonStringList(it, VOD_HIDDEN_CATEGORIES).toSet() }

    suspend fun setHomeCategoryOrder(order: List<String>) = writeJsonStringList(HOME_CATEGORY_ORDER, order)
    suspend fun setHomeHiddenCategories(hidden: Set<String>) = writeJsonStringList(HOME_HIDDEN_CATEGORIES, hidden.toList())
    suspend fun setVodCategoryOrder(order: List<String>) = writeJsonStringList(VOD_CATEGORY_ORDER, order)
    suspend fun setVodHiddenCategories(hidden: Set<String>) = writeJsonStringList(VOD_HIDDEN_CATEGORIES, hidden.toList())
}
