package com.merlottv.kotlin.data.repository

import android.util.Log
import com.merlottv.kotlin.domain.model.BackupStream
import com.merlottv.kotlin.domain.model.BackupTvChannel
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches live TV channels from the USA TV Stremio addon.
 * Provides 190 US channels across 10 genres with multiple stream URLs each.
 *
 * Uses a 30-minute in-memory cache to avoid re-fetching.
 */
@Singleton
class BackupTvAddonRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "BackupTvAddon"
        private const val CATALOG_URL = "https://848b3516657c-usatv.baby-beamup.club/catalog/tv/all.json"
        private const val CACHE_TTL = 30 * 60 * 1000L // 30 minutes
    }

    @Volatile
    private var cachedChannels: List<BackupTvChannel> = emptyList()
    @Volatile
    private var cacheTimestamp: Long = 0L

    /**
     * Returns all channels from the USA TV addon, using cache if fresh.
     */
    suspend fun getChannels(forceRefresh: Boolean = false): List<BackupTvChannel> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedChannels.isNotEmpty() && (now - cacheTimestamp) < CACHE_TTL) {
            Log.d(TAG, "Cache hit: ${cachedChannels.size} channels")
            return cachedChannels
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(CATALOG_URL).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP ${response.code} from USA TV addon")
                    return@withContext cachedChannels // Return stale cache on error
                }

                val body = response.body?.string() ?: return@withContext cachedChannels
                val channels = parseCatalog(body)
                Log.d(TAG, "Fetched ${channels.size} channels from USA TV addon")

                cachedChannels = channels
                cacheTimestamp = System.currentTimeMillis()
                channels
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch USA TV addon: ${e.message}")
                cachedChannels // Return stale cache on error
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCatalog(json: String): List<BackupTvChannel> {
        return try {
            val adapter = moshi.adapter(Map::class.java)
            val data = adapter.fromJson(json) as? Map<String, Any?> ?: return emptyList()
            val metas = data["metas"] as? List<Map<String, Any?>> ?: return emptyList()

            metas.mapNotNull { meta ->
                val id = meta["id"] as? String ?: return@mapNotNull null
                val name = meta["name"] as? String ?: return@mapNotNull null
                val genre = meta["genre"] as? String ?: "Other"
                val logo = meta["logo"] as? String ?: ""
                val poster = meta["poster"] as? String ?: ""
                val rawStreams = meta["streams"] as? List<Map<String, Any?>> ?: emptyList()

                val streams = rawStreams.mapNotNull { s ->
                    val url = s["url"] as? String ?: return@mapNotNull null
                    val streamName = s["name"] as? String ?: ""
                    val desc = s["description"] as? String ?: ""
                    BackupStream(url = url, name = streamName, description = desc)
                }

                if (streams.isEmpty()) return@mapNotNull null

                BackupTvChannel(
                    id = id,
                    name = name,
                    genre = genre,
                    logoUrl = logo,
                    posterUrl = poster,
                    streams = streams
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            emptyList()
        }
    }
}
