package com.merlottv.kotlin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val EPG_URLS = stringPreferencesKey("epg_urls")
        val TORBOX_KEY = stringPreferencesKey("torbox_key")
        val CUSTOM_ADDONS = stringPreferencesKey("custom_addons")

        const val DEFAULT_PLAYLIST = "https://x-api.uk/get.php?username=MetrlotBackup&password=2813308004&type=m3u_plus"
        const val DEFAULT_TORBOX_KEY = "50c74a49-a6bc-40e9-931e-1cee1943e87b"
    }

    val playlistUrl: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[PLAYLIST_URL] ?: DEFAULT_PLAYLIST
    }

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
