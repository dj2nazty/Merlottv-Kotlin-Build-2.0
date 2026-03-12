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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val m3uParser: M3uParser,
    private val okHttpClient: OkHttpClient
) : ChannelRepository {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())

    override suspend fun loadChannels(playlistUrl: String): List<Channel> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(playlistUrl).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val channels = m3uParser.parse(body)
                _channels.value = channels
                channels
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private val boundedIo = Dispatchers.IO.limitedParallelism(4)

    override suspend fun loadMultipleChannels(playlistUrls: List<String>): List<Channel> {
        return withContext(boundedIo) {
            val allChannels = supervisorScope {
                playlistUrls.map { url ->
                    async {
                        try {
                            val request = Request.Builder().url(url).build()
                            val response = okHttpClient.newCall(request).execute()
                            val body = response.body?.string() ?: ""
                            m3uParser.parse(body)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
            // De-duplicate by stream URL
            val deduplicated = allChannels.distinctBy { it.streamUrl }
            _channels.value = deduplicated
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
