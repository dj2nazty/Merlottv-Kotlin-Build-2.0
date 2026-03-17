package com.merlottv.kotlin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

/**
 * Metadata for a favorited VOD item (stored as JSON).
 */
data class FavoriteVodMeta(
    val id: String,
    val name: String,
    val poster: String,
    val type: String,        // "movie" or "series"
    val imdbRating: String = "",
    val description: String = ""
)

class FavoritesDataStore(private val context: Context) {

    companion object {
        // Legacy global keys (backward compat)
        val FAVORITE_CHANNELS = stringSetPreferencesKey("favorite_channels")
        val FAVORITE_VOD = stringSetPreferencesKey("favorite_vod")

        // Profile-aware keys
        fun channelsKey(profileId: String) = stringSetPreferencesKey("fav_channels_$profileId")
        fun vodKey(profileId: String) = stringSetPreferencesKey("fav_vod_$profileId")

        // VOD metadata stored as JSON map: id -> {name, poster, type, ...}
        private fun vodMetaKey(profileId: String) = stringPreferencesKey("fav_vod_meta_$profileId")
        private val LEGACY_VOD_META = stringPreferencesKey("fav_vod_meta")

        // Custom named favorites lists: JSON object { "listName": ["vodId1", "vodId2", ...], ... }
        val CUSTOM_LISTS = stringPreferencesKey("custom_favorites_lists")

        // Watched VOD tracking (profile-aware)
        fun watchedKey(profileId: String) = stringSetPreferencesKey("watched_vod_$profileId")
        val LEGACY_WATCHED = stringSetPreferencesKey("watched_vod")
    }

    // =============== Profile-aware favorites ===============

    fun favoriteChannels(profileId: String): Flow<Set<String>> = context.favoritesDataStore.data.map { prefs ->
        prefs[channelsKey(profileId)] ?: prefs[FAVORITE_CHANNELS] ?: emptySet()
    }

    fun favoriteVod(profileId: String): Flow<Set<String>> = context.favoritesDataStore.data.map { prefs ->
        prefs[vodKey(profileId)] ?: prefs[FAVORITE_VOD] ?: emptySet()
    }

    suspend fun toggleFavoriteChannel(channelId: String, profileId: String) {
        context.favoritesDataStore.edit { prefs ->
            val key = channelsKey(profileId)
            val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(channelId)) current.remove(channelId) else current.add(channelId)
            prefs[key] = current
        }
    }

    suspend fun toggleFavoriteVod(vodId: String, profileId: String) {
        context.favoritesDataStore.edit { prefs ->
            val key = vodKey(profileId)
            val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(vodId)) current.remove(vodId) else current.add(vodId)
            prefs[key] = current
        }
    }

    // =============== VOD Metadata (name, poster, type for display) ===============

    suspend fun saveVodMeta(meta: FavoriteVodMeta, profileId: String = "default") {
        context.favoritesDataStore.edit { prefs ->
            val key = vodMetaKey(profileId)
            val existing = prefs[key] ?: prefs[LEGACY_VOD_META] ?: "{}"
            val json = try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
            val item = JSONObject().apply {
                put("name", meta.name)
                put("poster", meta.poster)
                put("type", meta.type)
                put("imdbRating", meta.imdbRating)
                put("description", meta.description)
            }
            json.put(meta.id, item)
            prefs[key] = json.toString()
        }
    }

    suspend fun removeVodMeta(vodId: String, profileId: String = "default") {
        context.favoritesDataStore.edit { prefs ->
            val key = vodMetaKey(profileId)
            val existing = prefs[key] ?: prefs[LEGACY_VOD_META] ?: "{}"
            val json = try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
            json.remove(vodId)
            prefs[key] = json.toString()
        }
    }

    fun getVodMetaMap(profileId: String = "default"): Flow<Map<String, FavoriteVodMeta>> {
        return context.favoritesDataStore.data.map { prefs ->
            val raw = prefs[vodMetaKey(profileId)] ?: prefs[LEGACY_VOD_META] ?: "{}"
            val result = mutableMapOf<String, FavoriteVodMeta>()
            try {
                val json = JSONObject(raw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val obj = json.getJSONObject(id)
                    result[id] = FavoriteVodMeta(
                        id = id,
                        name = obj.optString("name", ""),
                        poster = obj.optString("poster", ""),
                        type = obj.optString("type", "movie"),
                        imdbRating = obj.optString("imdbRating", ""),
                        description = obj.optString("description", "")
                    )
                }
            } catch (_: Exception) {}
            result
        }
    }

    // =============== Custom Named Favorites Lists ===============

    fun getCustomLists(): Flow<Map<String, List<String>>> = context.favoritesDataStore.data.map { prefs ->
        val raw = prefs[CUSTOM_LISTS] ?: "{}"
        val result = mutableMapOf<String, List<String>>()
        try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val listName = keys.next()
                val arr = json.getJSONArray(listName)
                val ids = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    ids.add(arr.getString(i))
                }
                result[listName] = ids
            }
        } catch (_: Exception) {}
        result
    }

    suspend fun createCustomList(name: String) {
        context.favoritesDataStore.edit { prefs ->
            val raw = prefs[CUSTOM_LISTS] ?: "{}"
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (!json.has(name)) {
                json.put(name, JSONArray())
            }
            prefs[CUSTOM_LISTS] = json.toString()
        }
    }

    suspend fun deleteCustomList(name: String) {
        context.favoritesDataStore.edit { prefs ->
            val raw = prefs[CUSTOM_LISTS] ?: "{}"
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            json.remove(name)
            prefs[CUSTOM_LISTS] = json.toString()
        }
    }

    suspend fun addToCustomList(listName: String, vodId: String) {
        context.favoritesDataStore.edit { prefs ->
            val raw = prefs[CUSTOM_LISTS] ?: "{}"
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            val arr = if (json.has(listName)) json.getJSONArray(listName) else JSONArray()
            // Avoid duplicates
            var found = false
            for (i in 0 until arr.length()) {
                if (arr.getString(i) == vodId) { found = true; break }
            }
            if (!found) {
                arr.put(vodId)
            }
            json.put(listName, arr)
            prefs[CUSTOM_LISTS] = json.toString()
        }
    }

    suspend fun removeFromCustomList(listName: String, vodId: String) {
        context.favoritesDataStore.edit { prefs ->
            val raw = prefs[CUSTOM_LISTS] ?: "{}"
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (json.has(listName)) {
                val arr = json.getJSONArray(listName)
                val newArr = JSONArray()
                for (i in 0 until arr.length()) {
                    if (arr.getString(i) != vodId) {
                        newArr.put(arr.getString(i))
                    }
                }
                json.put(listName, newArr)
                prefs[CUSTOM_LISTS] = json.toString()
            }
        }
    }

    // =============== Rename Custom List ===============

    suspend fun renameCustomList(oldName: String, newName: String) {
        if (oldName == newName || newName.isBlank()) return
        context.favoritesDataStore.edit { prefs ->
            val raw = prefs[CUSTOM_LISTS] ?: "{}"
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (json.has(oldName)) {
                val arr = json.getJSONArray(oldName)
                json.remove(oldName)
                json.put(newName.trim(), arr)
                prefs[CUSTOM_LISTS] = json.toString()
            }
        }
    }

    // =============== Watched VOD Tracking ===============

    fun watchedVodIds(profileId: String): Flow<Set<String>> = context.favoritesDataStore.data.map { prefs ->
        prefs[watchedKey(profileId)] ?: prefs[LEGACY_WATCHED] ?: emptySet()
    }

    suspend fun toggleWatched(vodId: String, profileId: String) {
        context.favoritesDataStore.edit { prefs ->
            val key = watchedKey(profileId)
            val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(vodId)) current.remove(vodId) else current.add(vodId)
            prefs[key] = current
        }
    }

    // =============== Cloud Sync Restore ===============

    suspend fun restoreFavoriteChannels(profileId: String, ids: Set<String>) {
        context.favoritesDataStore.edit { prefs ->
            prefs[channelsKey(profileId)] = ids
        }
    }

    suspend fun restoreFavoriteVod(profileId: String, ids: Set<String>) {
        context.favoritesDataStore.edit { prefs ->
            prefs[vodKey(profileId)] = ids
        }
    }

    suspend fun restoreVodMeta(profileId: String, metaMap: Map<String, FavoriteVodMeta>) {
        val json = JSONObject()
        metaMap.forEach { (id, meta) ->
            val item = JSONObject().apply {
                put("name", meta.name)
                put("poster", meta.poster)
                put("type", meta.type)
                put("imdbRating", meta.imdbRating)
                put("description", meta.description)
            }
            json.put(id, item)
        }
        context.favoritesDataStore.edit { prefs ->
            prefs[vodMetaKey(profileId)] = json.toString()
        }
    }

    suspend fun restoreWatched(profileId: String, ids: Set<String>) {
        context.favoritesDataStore.edit { prefs ->
            prefs[watchedKey(profileId)] = ids
        }
    }

    suspend fun restoreCustomLists(lists: Map<String, List<String>>) {
        val json = JSONObject()
        lists.forEach { (name, ids) ->
            json.put(name, JSONArray(ids))
        }
        context.favoritesDataStore.edit { prefs ->
            prefs[CUSTOM_LISTS] = json.toString()
        }
    }

    // =============== Legacy methods (no profile) ===============

    val favoriteChannels: Flow<Set<String>> = context.favoritesDataStore.data.map { prefs ->
        prefs[FAVORITE_CHANNELS] ?: emptySet()
    }

    val favoriteVod: Flow<Set<String>> = context.favoritesDataStore.data.map { prefs ->
        prefs[FAVORITE_VOD] ?: emptySet()
    }

    suspend fun toggleFavoriteChannel(channelId: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITE_CHANNELS]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(channelId)) current.remove(channelId) else current.add(channelId)
            prefs[FAVORITE_CHANNELS] = current
        }
    }

    suspend fun toggleFavoriteVod(vodId: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITE_VOD]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(vodId)) current.remove(vodId) else current.add(vodId)
            prefs[FAVORITE_VOD] = current
        }
    }
}
