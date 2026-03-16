package com.merlottv.kotlin.data.youtube

import android.util.Log
import com.merlottv.kotlin.BuildConfig
import com.merlottv.kotlin.domain.model.TmdbCastMember
import com.merlottv.kotlin.domain.model.TmdbFilmCredit
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

@Singleton
class TmdbCastRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "TmdbCast"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val IMG_BASE = "https://image.tmdb.org/t/p/w185"
        private const val POSTER_BASE = "https://image.tmdb.org/t/p/w342"
        private const val CACHE_TTL_MS = 60 * 60 * 1000L
    }

    private data class CastCacheEntry(val cast: List<TmdbCastMember>, val timestamp: Long)
    private data class FilmCacheEntry(val films: List<TmdbFilmCredit>, val timestamp: Long)

    private val castCache = ConcurrentHashMap<String, CastCacheEntry>()
    private val filmCache = ConcurrentHashMap<Int, FilmCacheEntry>()

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Get cast members for a movie or TV show.
     * Uses IMDB ID → TMDB ID → credits endpoint.
     */
    suspend fun getCast(
        imdbId: String?,
        title: String,
        year: String,
        type: String
    ): List<TmdbCastMember> = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.TMDB_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isEmpty()) return@withContext emptyList()

        val cacheKey = "${imdbId ?: title}_${year}_$type"
        val cached = castCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return@withContext cached.cast
        }

        try {
            val tmdbId = resolveTmdbId(imdbId, title, year, type, apiKey)
            if (tmdbId == null) {
                castCache[cacheKey] = CastCacheEntry(emptyList(), System.currentTimeMillis())
                return@withContext emptyList()
            }

            val mediaType = if (type == "movie") "movie" else "tv"
            val url = "$BASE_URL/$mediaType/$tmdbId/credits?api_key=$apiKey"
            val json = fetchJson(url)
            if (json == null) {
                castCache[cacheKey] = CastCacheEntry(emptyList(), System.currentTimeMillis())
                return@withContext emptyList()
            }

            @Suppress("UNCHECKED_CAST")
            val castList = json["cast"] as? List<Map<String, Any?>> ?: emptyList()

            val cast = castList.take(20).mapNotNull { entry ->
                val id = (entry["id"] as? Number)?.toInt() ?: return@mapNotNull null
                val name = entry["name"] as? String ?: return@mapNotNull null
                val character = entry["character"] as? String ?: ""
                val profilePath = entry["profile_path"] as? String
                val profileUrl = if (profilePath != null) "$IMG_BASE$profilePath" else ""

                TmdbCastMember(
                    id = id,
                    name = name,
                    character = character,
                    profileUrl = profileUrl
                )
            }

            castCache[cacheKey] = CastCacheEntry(cast, System.currentTimeMillis())
            Log.d(TAG, "Found ${cast.size} cast for $title")
            cast
        } catch (e: Exception) {
            Log.w(TAG, "Cast lookup failed for $title: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get filmography for a person by their TMDB person ID.
     */
    suspend fun getFilmography(personId: Int): List<TmdbFilmCredit> = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.TMDB_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isEmpty()) return@withContext emptyList()

        val cached = filmCache[personId]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return@withContext cached.films
        }

        try {
            val url = "$BASE_URL/person/$personId/combined_credits?api_key=$apiKey"
            val json = fetchJson(url) ?: return@withContext emptyList()

            @Suppress("UNCHECKED_CAST")
            val castCredits = json["cast"] as? List<Map<String, Any?>> ?: emptyList()

            val films = castCredits
                .filter { entry ->
                    val mediaType = entry["media_type"] as? String
                    mediaType == "movie" || mediaType == "tv"
                }
                .sortedByDescending { entry ->
                    (entry["vote_count"] as? Number)?.toDouble() ?: 0.0
                }
                .take(40)
                .mapNotNull { entry ->
                    val id = (entry["id"] as? Number)?.toInt() ?: return@mapNotNull null
                    val mediaType = entry["media_type"] as? String ?: "movie"
                    val title = (entry["title"] as? String)
                        ?: (entry["name"] as? String)
                        ?: return@mapNotNull null
                    val posterPath = entry["poster_path"] as? String
                    val posterUrl = if (posterPath != null) "$POSTER_BASE$posterPath" else ""
                    val releaseDate = (entry["release_date"] as? String)
                        ?: (entry["first_air_date"] as? String)
                        ?: ""
                    val year = releaseDate.take(4)
                    val character = entry["character"] as? String ?: ""
                    val voteAvg = (entry["vote_average"] as? Number)?.toDouble() ?: 0.0
                    val voteAvgStr = if (voteAvg > 0) String.format("%.1f", voteAvg) else ""
                    val type = if (mediaType == "movie") "movie" else "series"

                    TmdbFilmCredit(
                        id = id,
                        imdbId = "", // Will be resolved when navigating
                        title = title,
                        posterUrl = posterUrl,
                        type = type,
                        year = year,
                        character = character,
                        voteAverage = voteAvgStr
                    )
                }
                .filter { it.posterUrl.isNotEmpty() }

            filmCache[personId] = FilmCacheEntry(films, System.currentTimeMillis())
            Log.d(TAG, "Found ${films.size} credits for person $personId")
            films
        } catch (e: Exception) {
            Log.w(TAG, "Filmography lookup failed for person $personId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get the external IDs (including IMDB) for a TMDB movie/TV.
     */
    suspend fun getImdbId(tmdbId: Int, type: String): String? = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.TMDB_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isEmpty()) return@withContext null

        try {
            val mediaType = if (type == "movie") "movie" else "tv"
            val url = "$BASE_URL/$mediaType/$tmdbId/external_ids?api_key=$apiKey"
            val json = fetchJson(url) ?: return@withContext null
            json["imdb_id"] as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveTmdbId(imdbId: String?, title: String, year: String, type: String, apiKey: String): Int? {
        if (imdbId != null && imdbId.startsWith("tt")) {
            val url = "$BASE_URL/find/$imdbId?api_key=$apiKey&external_source=imdb_id"
            val json = fetchJson(url) ?: return null
            @Suppress("UNCHECKED_CAST")
            val resultKey = if (type == "movie") "movie_results" else "tv_results"
            val results = json[resultKey] as? List<Map<String, Any?>> ?: emptyList()
            return (results.firstOrNull()?.get("id") as? Number)?.toInt()
        }

        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val mediaType = if (type == "movie") "movie" else "tv"
        val yearParam = if (year.isNotEmpty()) {
            val paramName = if (type == "movie") "year" else "first_air_date_year"
            "&$paramName=$year"
        } else ""

        val url = "$BASE_URL/search/$mediaType?api_key=$apiKey&query=$encodedTitle$yearParam"
        val json = fetchJson(url) ?: return null
        @Suppress("UNCHECKED_CAST")
        val results = json["results"] as? List<Map<String, Any?>> ?: emptyList()
        return (results.firstOrNull()?.get("id") as? Number)?.toInt()
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
