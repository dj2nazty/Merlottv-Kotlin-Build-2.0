package com.merlottv.kotlin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.watchProgressDataStore: DataStore<Preferences> by preferencesDataStore(name = "watch_progress")

/**
 * Stores watch progress for VOD content per profile.
 * Keys are prefixed with profile ID for multi-profile support.
 */
class WatchProgressDataStore(private val context: Context) {

    companion object {
        // Profile-aware keys
        private fun positionKey(profileId: String, id: String) = longPreferencesKey("pos_${profileId}_$id")
        private fun durationKey(profileId: String, id: String) = longPreferencesKey("dur_${profileId}_$id")
        private fun titleKey(profileId: String, id: String) = stringPreferencesKey("title_${profileId}_$id")
        private fun posterKey(profileId: String, id: String) = stringPreferencesKey("poster_${profileId}_$id")
        private fun typeKey(profileId: String, id: String) = stringPreferencesKey("type_${profileId}_$id")
        private fun timestampKey(profileId: String, id: String) = longPreferencesKey("ts_${profileId}_$id")
        private fun trackedIdsKey(profileId: String) = stringPreferencesKey("tracked_ids_$profileId")

        // Legacy keys (no profile prefix) for backward compat
        private fun legacyPositionKey(id: String) = longPreferencesKey("pos_$id")
        private fun legacyDurationKey(id: String) = longPreferencesKey("dur_$id")
        private fun legacyTitleKey(id: String) = stringPreferencesKey("title_$id")
        private fun legacyPosterKey(id: String) = stringPreferencesKey("poster_$id")
        private fun legacyTypeKey(id: String) = stringPreferencesKey("type_$id")
        private fun legacyTimestampKey(id: String) = longPreferencesKey("ts_$id")
        val LEGACY_TRACKED_IDS = stringPreferencesKey("tracked_ids")
    }

    suspend fun saveProgress(
        id: String,
        position: Long,
        duration: Long,
        title: String,
        poster: String,
        type: String,
        profileId: String = "default"
    ) {
        if (duration <= 0) return
        val percent = position.toFloat() / duration.toFloat()
        if (position < 30_000 || percent > 0.95f) {
            removeProgress(id, profileId)
            return
        }

        context.watchProgressDataStore.edit { prefs ->
            prefs[positionKey(profileId, id)] = position
            prefs[durationKey(profileId, id)] = duration
            prefs[titleKey(profileId, id)] = title
            prefs[posterKey(profileId, id)] = poster
            prefs[typeKey(profileId, id)] = type
            prefs[timestampKey(profileId, id)] = System.currentTimeMillis()

            val existing = prefs[trackedIdsKey(profileId)] ?: ""
            val ids = existing.split(",").filter { it.isNotEmpty() }.toMutableSet()
            ids.add(id)
            prefs[trackedIdsKey(profileId)] = ids.joinToString(",")
        }
    }

    suspend fun removeProgress(id: String, profileId: String = "default") {
        context.watchProgressDataStore.edit { prefs ->
            prefs.remove(positionKey(profileId, id))
            prefs.remove(durationKey(profileId, id))
            prefs.remove(titleKey(profileId, id))
            prefs.remove(posterKey(profileId, id))
            prefs.remove(typeKey(profileId, id))
            prefs.remove(timestampKey(profileId, id))

            val existing = prefs[trackedIdsKey(profileId)] ?: ""
            val ids = existing.split(",").filter { it.isNotEmpty() && it != id }
            prefs[trackedIdsKey(profileId)] = ids.joinToString(",")
        }
    }

    suspend fun getPosition(id: String, profileId: String = "default"): Long {
        val prefs = context.watchProgressDataStore.data.first()
        return prefs[positionKey(profileId, id)]
            ?: prefs[legacyPositionKey(id)] // Fallback to legacy
            ?: 0L
    }

    fun getContinueWatchingItems(profileId: String = "default"): Flow<List<WatchProgressItem>> {
        return context.watchProgressDataStore.data.map { prefs ->
            val idsStr = prefs[trackedIdsKey(profileId)]
                ?: prefs[LEGACY_TRACKED_IDS] // Fallback to legacy
                ?: ""
            val ids = idsStr.split(",").filter { it.isNotEmpty() }

            ids.mapNotNull { id ->
                val position = prefs[positionKey(profileId, id)]
                    ?: prefs[legacyPositionKey(id)]
                    ?: return@mapNotNull null
                val duration = prefs[durationKey(profileId, id)]
                    ?: prefs[legacyDurationKey(id)]
                    ?: return@mapNotNull null
                val title = prefs[titleKey(profileId, id)]
                    ?: prefs[legacyTitleKey(id)]
                    ?: return@mapNotNull null
                val poster = prefs[posterKey(profileId, id)]
                    ?: prefs[legacyPosterKey(id)]
                    ?: ""
                val type = prefs[typeKey(profileId, id)]
                    ?: prefs[legacyTypeKey(id)]
                    ?: "movie"
                val timestamp = prefs[timestampKey(profileId, id)]
                    ?: prefs[legacyTimestampKey(id)]
                    ?: 0L

                WatchProgressItem(
                    id = id,
                    title = title,
                    poster = poster,
                    type = type,
                    position = position,
                    duration = duration,
                    timestamp = timestamp,
                    progressPercent = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                )
            }.sortedByDescending { it.timestamp }
        }
    }
}

data class WatchProgressItem(
    val id: String,
    val title: String,
    val poster: String,
    val type: String,
    val position: Long,
    val duration: Long,
    val timestamp: Long,
    val progressPercent: Float
)
