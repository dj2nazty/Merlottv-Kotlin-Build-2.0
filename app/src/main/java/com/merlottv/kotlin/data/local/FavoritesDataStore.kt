package com.merlottv.kotlin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

class FavoritesDataStore(private val context: Context) {

    companion object {
        val FAVORITE_CHANNELS = stringSetPreferencesKey("favorite_channels")
        val FAVORITE_VOD = stringSetPreferencesKey("favorite_vod")
    }

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
