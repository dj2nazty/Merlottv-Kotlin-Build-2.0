package com.merlottv.kotlin.data.youtube

import android.util.Log
import com.merlottv.kotlin.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds YouTube trailer IDs for movies/series via the TMDB API.
 * Used as a fallback when Stremio addon metadata doesn't include trailers.
 *
 * API: https://api.themoviedb.org/3/
 * Free tier: 40 requests per 10 seconds.
 */
@Singleton
class TmdbTrailerRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "TmdbTrailer"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
    }

    private data class CacheEntry(
        val ytId: String?,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Find a YouTube trailer ID for the given content.
     *
     * @param imdbId IMDB ID (e.g. "tt1234567") — preferred lookup method
     * @param title Content title — used for search fallback
     * @param year Release year — helps narrow search
     * @param type "movie" or "series"
     * @return YouTube video ID, or null if not found
     */
    suspend fun findTrailerId(
        imdbId: String?,
        title: String,
        year: String,
        type: String
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.TMDB_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isEmpty()) {
            Log.w(TAG, "No TMDB API key configured")
            return@withContext null
        }

        val cacheKey = "${imdbId ?: title}_${year}_$type"
        val cached = cache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return@withContext cached.ytId
        }

        try {
            // Step 1: Get TMDB ID
            val tmdbId = if (imdbId != null && imdbId.startsWith("tt")) {
                findTmdbIdByImdb(imdbId, type, apiKey)
            } else {
                searchTmdbId(title, year, type, apiKey)
            }

            if (tmdbId == null) {
                Log.d(TAG, "No TMDB ID found for $title ($year)")
                cache[cacheKey] = CacheEntry(null, System.currentTimeMillis())
                return@withContext null
            }

            // Step 2: Get videos for the TMDB ID
            val ytId = getTrailerYtId(tmdbId, type, apiKey)
            cache[cacheKey] = CacheEntry(ytId, System.currentTimeMillis())

            if (ytId != null) {
                Log.d(TAG, "Found trailer for $title: $ytId")
            }
            ytId
        } catch (e: Exception) {
            Log.w(TAG, "TMDB lookup failed for $title: ${e.message}")
            null
        }
    }

    /**
     * Find TMDB ID using IMDB ID via /find endpoint.
     */
    @Suppress("UNCHECKED_CAST")
    private fun findTmdbIdByImdb(imdbId: String, type: String, apiKey: String): Int? {
        val url = "$BASE_URL/find/$imdbId?api_key=$apiKey&external_source=imdb_id"
        val json = fetchJson(url) ?: return null

        val resultKey = if (type == "movie") "movie_results" else "tv_results"
        val results = json[resultKey] as? List<Map<String, Any?>> ?: emptyList()
        return (results.firstOrNull()?.get("id") as? Number)?.toInt()
    }

    /**
     * Search TMDB by title and year as fallback.
     */
    @Suppress("UNCHECKED_CAST")
    private fun searchTmdbId(title: String, year: String, type: String, apiKey: String): Int? {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val mediaType = if (type == "movie") "movie" else "tv"
        val yearParam = if (year.isNotEmpty()) {
            val paramName = if (type == "movie") "year" else "first_air_date_year"
            "&$paramName=$year"
        } else ""

        val url = "$BASE_URL/search/$mediaType?api_key=$apiKey&query=$encodedTitle$yearParam"
        val json = fetchJson(url) ?: return null

        val results = json["results"] as? List<Map<String, Any?>> ?: emptyList()
        return (results.firstOrNull()?.get("id") as? Number)?.toInt()
    }

    /**
     * Get YouTube trailer ID from TMDB videos endpoint.
     * Prefers official trailers, then teasers.
     */
    @Suppress("UNCHECKED_CAST")
    private fun getTrailerYtId(tmdbId: Int, type: String, apiKey: String): String? {
        val mediaType = if (type == "movie") "movie" else "tv"
        val url = "$BASE_URL/$mediaType/$tmdbId/videos?api_key=$apiKey"
        val json = fetchJson(url) ?: return null

        val results = json["results"] as? List<Map<String, Any?>> ?: emptyList()

        // Filter to YouTube videos only
        val youtubeVideos = results.filter { video ->
            (video["site"] as? String)?.equals("YouTube", ignoreCase = true) == true &&
            (video["key"] as? String)?.isNotEmpty() == true
        }

        if (youtubeVideos.isEmpty()) return null

        // Score and rank: official trailers > teasers > other
        data class ScoredVideo(val key: String, val score: Int)

        val scored = youtubeVideos.mapNotNull { video ->
            val key = video["key"] as? String ?: return@mapNotNull null
            val videoType = (video["type"] as? String)?.lowercase() ?: ""
            val isOfficial = video["official"] as? Boolean ?: false
            val size = (video["size"] as? Number)?.toInt() ?: 0

            var score = 0
            when {
                videoType == "trailer" -> score += 100
                videoType == "teaser" -> score += 50
                videoType == "clip" -> score += 20
                else -> score += 10
            }
            if (isOfficial) score += 50
            // Prefer higher resolution
            score += (size / 100).coerceAtMost(20)

            ScoredVideo(key, score)
        }

        return scored.maxByOrNull { it.score }?.key
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchJson(url: String): Map<String, Any?>? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }

        val body = response.body?.string() ?: return null
        return try {
            moshi.adapter(Map::class.java).fromJson(body) as? Map<String, Any?>
        } catch (_: Exception) {
            null
        }
    }
}
