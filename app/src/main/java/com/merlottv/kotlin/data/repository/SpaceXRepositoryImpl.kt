package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.domain.model.LaunchStatus
import com.merlottv.kotlin.domain.model.SpaceXLaunch
import com.merlottv.kotlin.domain.repository.SpaceXRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaceXRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : SpaceXRepository {

    private val boundedIo = Dispatchers.IO.limitedParallelism(2)

    private val launchClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // In-memory cache with 5-minute TTL
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val CACHE_TTL = 5 * 60 * 1000L

    private data class CacheEntry(val data: Any, val timestamp: Long)

    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String): T? {
        val entry = cache[key] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL) {
            entry.data as? T
        } else {
            cache.remove(key)
            null
        }
    }

    private fun putCache(key: String, data: Any) {
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    private companion object {
        const val BASE = "https://ll.thespacedevs.com/2.3.0"
    }

    private val mapAdapter by lazy {
        moshi.adapter(Map::class.java)
    }

    private val isoFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // ─── Upcoming Launches ──────────────────────────────────────────────

    override suspend fun getUpcomingLaunches(): List<SpaceXLaunch> {
        getCached<List<SpaceXLaunch>>("upcoming")?.let { return it }

        return withContext(boundedIo) {
            try {
                val url = "$BASE/launches/upcoming/?search=spacex&limit=20&format=json"
                val json = fetch(url) ?: return@withContext emptyList()
                val results = extractResults(json)
                val launches = results.mapNotNull { parseLaunch(it) }
                    .sortedBy { it.netEpochMs }
                putCache("upcoming", launches)
                launches
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ─── Past Launches ──────────────────────────────────────────────────

    override suspend fun getPastLaunches(limit: Int): List<SpaceXLaunch> {
        val cacheKey = "past_$limit"
        getCached<List<SpaceXLaunch>>(cacheKey)?.let { return it }

        return withContext(boundedIo) {
            try {
                val url = "$BASE/launches/previous/?search=spacex&limit=$limit&format=json"
                val json = fetch(url) ?: return@withContext emptyList()
                val results = extractResults(json)
                val launches = results.mapNotNull { parseLaunch(it) }
                    .sortedByDescending { it.netEpochMs }
                putCache(cacheKey, launches)
                launches
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ─── JSON Parsing ───────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun extractResults(json: Map<*, *>): List<Map<String, Any?>> {
        return (json["results"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseLaunch(data: Map<String, Any?>): SpaceXLaunch? {
        val id = data["id"]?.toString() ?: return null
        val name = data["name"]?.toString() ?: "Unknown Mission"
        val netUtc = data["net"]?.toString() ?: ""
        val windowStart = data["window_start"]?.toString()
        val windowEnd = data["window_end"]?.toString()

        // Parse date to epoch ms
        val netEpochMs = try {
            isoFormat.parse(netUtc)?.time ?: 0L
        } catch (e: Exception) {
            // Try alternate format with fractional seconds
            try {
                val altFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                altFormat.timeZone = TimeZone.getTimeZone("UTC")
                altFormat.parse(netUtc)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }

        // Status
        val statusMap = data["status"] as? Map<String, Any?>
        val statusId = (statusMap?.get("id") as? Number)?.toInt() ?: 2
        val status = LaunchStatus.fromId(statusId)

        // Mission
        val mission = data["mission"] as? Map<String, Any?>
        val missionName = mission?.get("name")?.toString()
        val missionType = mission?.get("type")?.toString()
        val missionDescription = mission?.get("description")?.toString()
        val orbitMap = mission?.get("orbit") as? Map<String, Any?>
        val orbit = orbitMap?.get("abbrev")?.toString()

        // Rocket
        val rocket = data["rocket"] as? Map<String, Any?>
        val rocketConfig = rocket?.get("configuration") as? Map<String, Any?>
        val rocketName = rocketConfig?.get("full_name")?.toString()
            ?: rocketConfig?.get("name")?.toString()
            ?: "Unknown Rocket"

        // Pad
        val pad = data["pad"] as? Map<String, Any?>
        val padName = pad?.get("name")?.toString()
        val padLocationMap = pad?.get("location") as? Map<String, Any?>
        val padLocation = padLocationMap?.get("name")?.toString()
            ?: pad?.get("location")?.toString()

        // Image
        val imageMap = data["image"] as? Map<String, Any?>
        val imageUrl = imageMap?.get("image_url")?.toString()
            ?: imageMap?.get("thumbnail_url")?.toString()

        // Video URLs
        val webcastLive = data["webcast_live"] as? Boolean ?: false
        val vidUrls = (data["vid_urls"] as? List<*>)?.mapNotNull { vidEntry ->
            when (vidEntry) {
                is Map<*, *> -> vidEntry["url"]?.toString()
                is String -> vidEntry
                else -> null
            }
        } ?: emptyList()

        return SpaceXLaunch(
            id = id,
            name = name,
            netUtc = netUtc,
            netEpochMs = netEpochMs,
            windowStart = windowStart,
            windowEnd = windowEnd,
            status = status,
            missionName = missionName,
            missionType = missionType,
            missionDescription = missionDescription,
            rocketName = rocketName,
            padName = padName,
            padLocation = padLocation,
            imageUrl = imageUrl,
            webcastLive = webcastLive,
            videoUrls = vidUrls,
            orbit = orbit
        )
    }

    // ─── Network ────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun fetch(url: String): Map<String, Any?>? {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .build()
        val response = launchClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        return mapAdapter.fromJson(body) as? Map<String, Any?>
    }
}
