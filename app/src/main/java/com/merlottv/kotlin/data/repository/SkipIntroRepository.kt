package com.merlottv.kotlin.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.merlottv.kotlin.ui.screens.player.SkipInterval
import com.merlottv.kotlin.ui.screens.player.SkipType
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.skipIntroDataStore: DataStore<Preferences> by preferencesDataStore(name = "skip_intro")

/**
 * Repository for skip intro/outro/recap intervals.
 *
 * Supports:
 * 1. User-defined skip points (user presses "Mark Intro Start/End" during playback)
 * 2. Stored per series (seriesId + season), reusable across episodes
 * 3. Default heuristic: first 90 seconds as potential intro range for series content
 */
class SkipIntroRepository(private val context: Context) {

    companion object {
        private const val TAG = "SkipIntroRepo"

        // Key format: "skip_{seriesId}_S{season}"
        private fun skipKey(seriesId: String, season: Int): Preferences.Key<String> =
            stringPreferencesKey("skip_${seriesId}_S${season}")

        // Default intro heuristic for series: assume intro is in first 2 minutes
        const val DEFAULT_INTRO_START_MS = 0L
        const val DEFAULT_INTRO_END_MS = 90_000L // 90 seconds

        // Outro heuristic: last 2 minutes
        const val OUTRO_BEFORE_END_MS = 120_000L // 2 minutes before end
    }

    /**
     * Get skip intervals for a specific episode.
     * Returns user-defined intervals if available, otherwise returns heuristic defaults.
     *
     * @param seriesId The series/show ID (e.g., IMDB ID or content ID)
     * @param season Season number
     * @param episode Episode number
     * @param totalDurationMs Total duration of the episode in milliseconds (for outro calculation)
     * @param useHeuristic Whether to fall back to heuristic intervals if no user-defined ones exist
     */
    suspend fun getSkipIntervals(
        seriesId: String,
        season: Int,
        episode: Int,
        totalDurationMs: Long,
        useHeuristic: Boolean = true
    ): List<SkipInterval> {
        // Try user-defined intervals first
        val userIntervals = getUserDefinedIntervals(seriesId, season)
        if (userIntervals.isNotEmpty()) {
            Log.d(TAG, "Found ${userIntervals.size} user-defined intervals for $seriesId S${season}")
            return userIntervals
        }

        if (!useHeuristic) return emptyList()

        // Heuristic: only for episodes > 10 minutes (skip for short content)
        if (totalDurationMs < 600_000) return emptyList()

        val intervals = mutableListOf<SkipInterval>()

        // Intro heuristic: if episode > 20 min, assume intro in first 90s
        // (only suggest for episodes after E01 — first episode usually has no recap/standard intro)
        if (episode > 1 && totalDurationMs > 1_200_000) {
            intervals.add(
                SkipInterval(
                    type = SkipType.INTRO,
                    startMs = DEFAULT_INTRO_START_MS,
                    endMs = DEFAULT_INTRO_END_MS
                )
            )
        }

        // No outro heuristic — the Next Episode auto-play overlay handles end-of-episode

        return intervals
    }

    /**
     * Save a user-defined skip interval for a series+season.
     * This interval will be reused for all episodes in that season.
     */
    suspend fun saveSkipInterval(
        seriesId: String,
        season: Int,
        interval: SkipInterval
    ) {
        val key = skipKey(seriesId, season)
        val existing = getUserDefinedIntervals(seriesId, season).toMutableList()

        // Replace existing interval of same type, or add new
        existing.removeAll { it.type == interval.type }
        existing.add(interval)

        val jsonArray = JSONArray()
        existing.forEach { entry ->
            val obj = JSONObject()
            obj.put("type", entry.type.name)
            obj.put("startMs", entry.startMs)
            obj.put("endMs", entry.endMs)
            jsonArray.put(obj)
        }

        context.skipIntroDataStore.edit { prefs ->
            prefs[key] = jsonArray.toString()
        }

        Log.d(TAG, "Saved ${interval.type} interval for $seriesId S${season}: ${interval.startMs}ms - ${interval.endMs}ms")
    }

    /**
     * Clear all user-defined skip intervals for a series+season.
     */
    suspend fun clearSkipIntervals(seriesId: String, season: Int) {
        val key = skipKey(seriesId, season)
        context.skipIntroDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    private suspend fun getUserDefinedIntervals(
        seriesId: String,
        season: Int
    ): List<SkipInterval> {
        return try {
            val key = skipKey(seriesId, season)
            val prefs = context.skipIntroDataStore.data.first()
            val json = prefs[key] ?: return emptyList()
            parseIntervalsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read skip intervals", e)
            emptyList()
        }
    }

    private fun parseIntervalsJson(json: String): List<SkipInterval> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val typeName = obj.optString("type", "INTRO")
                val type = try { SkipType.valueOf(typeName) } catch (_: Exception) { SkipType.INTRO }
                SkipInterval(
                    type = type,
                    startMs = obj.optLong("startMs", 0),
                    endMs = obj.optLong("endMs", 0)
                )
            }.filter { it.endMs > it.startMs }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
