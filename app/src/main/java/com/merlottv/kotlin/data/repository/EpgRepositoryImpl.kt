package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.data.parser.XmltvParser
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.domain.repository.EpgRepository
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
class EpgRepositoryImpl @Inject constructor(
    private val xmltvParser: XmltvParser,
    private val okHttpClient: OkHttpClient
) : EpgRepository {

    private val _channels = MutableStateFlow<List<EpgChannel>>(emptyList())
    private val _programs = MutableStateFlow<List<EpgEntry>>(emptyList())

    // Dedicated client with longer timeouts for large EPG files
    private val epgClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun loadEpg(urls: List<String>) {
        withContext(Dispatchers.IO) {
            val allChannels = mutableListOf<EpgChannel>()
            val allPrograms = mutableListOf<EpgEntry>()

            // Load EPG sources in parallel
            supervisorScope {
                val results = urls.map { url ->
                    async {
                        try {
                            val request = Request.Builder()
                                .url(url)
                                .header("Accept-Encoding", "gzip")
                                .build()
                            val response = epgClient.newCall(request).execute()
                            if (response.isSuccessful) {
                                val body = response.body
                                if (body != null) {
                                    // Use stream-based parsing to avoid OOM on large files
                                    val result = xmltvParser.parseStream(body.byteStream())
                                    body.close()
                                    result
                                } else {
                                    Pair(emptyList(), emptyList())
                                }
                            } else {
                                response.close()
                                Pair(emptyList<EpgChannel>(), emptyList<EpgEntry>())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Pair(emptyList<EpgChannel>(), emptyList<EpgEntry>())
                        }
                    }
                }.awaitAll()

                for ((channels, programs) in results) {
                    allChannels.addAll(channels)
                    allPrograms.addAll(programs)
                }
            }

            // De-duplicate channels by ID (keep first occurrence)
            val uniqueChannels = allChannels.distinctBy { it.id }

            _channels.value = uniqueChannels
            _programs.value = allPrograms.sortedBy { it.startTime }
        }
    }

    override fun getEpgForChannel(channelId: String): Flow<List<EpgEntry>> {
        return _programs.map { programs ->
            programs.filter { it.channelId.equals(channelId, ignoreCase = true) }
                .sortedBy { it.startTime }
        }
    }

    override fun getAllEpgChannels(): Flow<List<EpgChannel>> {
        return _channels.map { channels ->
            val programsByChannel = _programs.value.groupBy { it.channelId.lowercase() }
            channels.map { ch ->
                ch.copy(programs = programsByChannel[ch.id.lowercase()] ?: emptyList())
            }
        }
    }

    override fun getCurrentProgram(channelId: String): EpgEntry? {
        val now = System.currentTimeMillis()
        return _programs.value.find { entry ->
            entry.channelId.equals(channelId, ignoreCase = true) &&
            entry.startTime <= now && entry.endTime >= now
        }
    }
}
