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
