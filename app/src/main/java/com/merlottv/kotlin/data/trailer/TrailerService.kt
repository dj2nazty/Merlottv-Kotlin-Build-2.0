package com.merlottv.kotlin.data.trailer

import android.util.Log
import com.merlottv.kotlin.data.youtube.TmdbTrailerRepository
import com.merlottv.kotlin.data.youtube.YouTubeExtractor
import com.merlottv.kotlin.data.youtube.YouTubeStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that resolves content → playable trailer stream URL.
 *
 * Flow: Content IMDB ID → TmdbTrailerRepository (IMDB → TMDB → YouTube video ID)
 *       → YouTubeExtractor (YouTube video ID → stream URL)
 *
 * Caches resolved stream URLs to avoid repeated extraction.
 * Negative-caches misses (content with no trailer) for 30 minutes.
 */
@Singleton
class TrailerService @Inject constructor(
    private val tmdbTrailerRepo: TmdbTrailerRepository,
    private val youTubeExtractor: YouTubeExtractor
) {
    companion object {
        private const val TAG = "TrailerService"
        private const val STREAM_CACHE_TTL_MS = 30 * 60 * 1000L // 30 min (YouTube URLs expire)
        private const val NEGATIVE_CACHE_TTL_MS = 30 * 60 * 1000L // 30 min for misses
    }

    data class TrailerStreamResult(
        val ytVideoId: String,
        val streamUrl: String,   // Playable video URL
        val audioUrl: String?,   // Separate audio track (for DASH)
        val hlsUrl: String?,     // HLS manifest if available
        val isProgressive: Boolean // true = single URL with audio+video
    )

    private data class CacheEntry(
        val result: TrailerStreamResult?,
        val timestamp: Long
    )

    private val streamCache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Resolve a playable trailer stream URL for the given content.
     *
     * @param imdbId IMDB ID (e.g. "tt1234567")
     * @param title Content title (fallback for TMDB search)
     * @param year Release year
     * @param type "movie" or "series"
     * @return Playable stream result, or null if no trailer found
     */
    suspend fun getTrailerStream(
        imdbId: String?,
        title: String,
        year: String,
        type: String
    ): TrailerStreamResult? = withContext(Dispatchers.IO) {
        val cacheKey = "${imdbId ?: title}_${year}_$type"

        // Check cache
        val cached = streamCache[cacheKey]
        if (cached != null) {
            val ttl = if (cached.result != null) STREAM_CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
            if (System.currentTimeMillis() - cached.timestamp < ttl) {
                return@withContext cached.result
            }
        }

        try {
            // Step 1: Get YouTube video ID via TMDB
            val ytVideoId = tmdbTrailerRepo.findTrailerId(imdbId, title, year, type)
            if (ytVideoId == null) {
                Log.d(TAG, "No trailer found for $title ($year)")
                streamCache[cacheKey] = CacheEntry(null, System.currentTimeMillis())
                return@withContext null
            }

            // Step 2: Extract playable stream URL from YouTube
            val ytResult = try {
                youTubeExtractor.extract(ytVideoId)
            } catch (e: Exception) {
                Log.w(TAG, "YouTube extraction failed for $ytVideoId: ${e.message}")
                null
            }

            if (ytResult == null) {
                streamCache[cacheKey] = CacheEntry(null, System.currentTimeMillis())
                return@withContext null
            }

            // Step 3: Build result — prefer progressive (single URL), then HLS, then DASH
            val result = buildStreamResult(ytVideoId, ytResult)
            streamCache[cacheKey] = CacheEntry(result, System.currentTimeMillis())

            if (result != null) {
                Log.d(TAG, "Resolved trailer for $title: ${result.streamUrl.take(60)}...")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Trailer resolution failed for $title: ${e.message}")
            null
        }
    }

    /**
     * Quick check if a trailer is likely available (cached result or TMDB lookup only).
     * Does NOT extract YouTube streams — faster for preflight checks.
     */
    suspend fun hasTrailer(
        imdbId: String?,
        title: String,
        year: String,
        type: String
    ): Boolean {
        // Check stream cache first
        val cacheKey = "${imdbId ?: title}_${year}_$type"
        val cached = streamCache[cacheKey]
        if (cached != null) {
            val ttl = if (cached.result != null) STREAM_CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
            if (System.currentTimeMillis() - cached.timestamp < ttl) {
                return cached.result != null
            }
        }

        // Just check TMDB — don't extract YouTube streams
        return tmdbTrailerRepo.findTrailerId(imdbId, title, year, type) != null
    }

    /**
     * Clear all cached trailer data.
     */
    fun clearCache() {
        streamCache.clear()
    }

    private fun buildStreamResult(ytVideoId: String, ytResult: YouTubeStreamResult): TrailerStreamResult? {
        // Prefer progressive (single file with audio+video) for inline previews
        if (ytResult.progressiveUrl != null) {
            return TrailerStreamResult(
                ytVideoId = ytVideoId,
                streamUrl = ytResult.progressiveUrl,
                audioUrl = null,
                hlsUrl = ytResult.hlsManifestUrl,
                isProgressive = true
            )
        }

        // HLS is good for adaptive streaming
        if (ytResult.hlsManifestUrl != null) {
            return TrailerStreamResult(
                ytVideoId = ytVideoId,
                streamUrl = ytResult.hlsManifestUrl,
                audioUrl = null,
                hlsUrl = ytResult.hlsManifestUrl,
                isProgressive = false
            )
        }

        // DASH: separate video + audio
        if (ytResult.videoUrl != null) {
            return TrailerStreamResult(
                ytVideoId = ytVideoId,
                streamUrl = ytResult.videoUrl,
                audioUrl = ytResult.audioUrl,
                hlsUrl = null,
                isProgressive = false
            )
        }

        return null
    }
}
