package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.data.parser.M3uParser
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.ChannelGroup
import com.merlottv.kotlin.domain.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val m3uParser: M3uParser,
    private val okHttpClient: OkHttpClient
) : ChannelRepository {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())

    // ── In-memory channel cache (15-minute TTL) ──────────────────────────
    // Avoids re-downloading + re-parsing M3U playlists every time the user
    // navigates to Live TV. Cache is keyed on the sorted list of playlist URLs.
    @Volatile private var cachedChannels: List<Channel> = emptyList()
    @Volatile private var cacheKey: String = ""
    @Volatile private var cacheTimestamp: Long = 0L
    private val cacheDuration = 15 * 60 * 1000L // 15 minutes

    // Dedicated client with tighter timeouts for playlist downloads
    // Main OkHttpClient has 10s connect / 15s read which is fine for APIs
    // but M3U files can be large — give slightly more read time but fail fast on connect
    private val playlistClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun isCacheValid(urls: List<String>): Boolean {
        val key = urls.sorted().joinToString("|")
        return cachedChannels.isNotEmpty() &&
                key == cacheKey &&
                (System.currentTimeMillis() - cacheTimestamp) < cacheDuration
    }

    private fun updateCache(urls: List<String>, channels: List<Channel>) {
        cachedChannels = channels
        cacheKey = urls.sorted().joinToString("|")
        cacheTimestamp = System.currentTimeMillis()
    }

    override suspend fun loadChannels(playlistUrl: String): List<Channel> {
        val urls = listOf(playlistUrl)
        if (isCacheValid(urls)) {
            _channels.value = cachedChannels
            return cachedChannels
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(playlistUrl).build()
                val response = playlistClient.newCall(request).execute()
                val channels = response.use { resp ->
                    val body = resp.body
                    if (body != null) {
                        m3uParser.parseStream(body.byteStream())
                    } else {
                        emptyList()
                    }
                }
                _channels.value = channels
                updateCache(urls, channels)
                channels
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private val boundedIo = Dispatchers.IO.limitedParallelism(6) // Bumped from 4→6 for faster parallel downloads

    override suspend fun loadMultipleChannels(playlistUrls: List<String>): List<Channel> {
        if (isCacheValid(playlistUrls)) {
            _channels.value = cachedChannels
            return cachedChannels
        }

        return withContext(boundedIo) {
            val allChannels = supervisorScope {
                playlistUrls.map { url ->
                    async {
                        try {
                            val request = Request.Builder().url(url).build()
                            val response = playlistClient.newCall(request).execute()
                            response.use { resp ->
                                val body = resp.body
                                if (body != null) {
                                    m3uParser.parseStream(body.byteStream())
                                } else {
                                    emptyList()
                                }
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
            val deduplicated = allChannels.distinctBy { it.streamUrl }
            _channels.value = deduplicated
            updateCache(playlistUrls, deduplicated)
            deduplicated
        }
    }

    override fun getChannelGroups(): Flow<List<ChannelGroup>> {
        return _channels.map { channels ->
            channels.groupBy { it.group }
                .map { (group, chList) ->
                    ChannelGroup(
                        name = group.ifEmpty { "Uncategorized" },
                        channels = chList.sortedBy { it.number }
                    )
                }
                .sortedBy { it.name }
        }
    }

    override fun searchChannels(query: String): Flow<List<Channel>> {
        return _channels.map { channels ->
            if (query.isBlank()) channels
            else channels.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
            }
        }
    }
}
