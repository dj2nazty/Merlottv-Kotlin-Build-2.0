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

    // Indexed by lowercase channelId for O(1) lookup instead of O(n) full-list scan
    @Volatile
    private var programIndex: Map<String, List<EpgEntry>> = emptyMap()

    // Dedicated client with longer timeouts for large EPG files
    private val epgClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val boundedIo = Dispatchers.IO.limitedParallelism(3)

    override suspend fun loadEpg(urls: List<String>) {
        withContext(boundedIo) {
            val allChannels = mutableListOf<EpgChannel>()
            val allPrograms = mutableListOf<EpgEntry>()

            supervisorScope {
                val results = urls.map { url ->
                    async {
                        try {
                            val request = Request.Builder()
                                .url(url)
                                .header("Accept-Encoding", "gzip")
                                .build()
                            val response = epgClient.newCall(request).execute()
                            response.use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body
                                    if (body != null) {
                                        xmltvParser.parseStream(body.byteStream())
                                    } else {
                                        Pair(emptyList(), emptyList())
                                    }
                                } else {
                                    Pair(emptyList<EpgChannel>(), emptyList<EpgEntry>())
                                }
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

            // De-duplicate channels by ID using a set (avoids creating intermediate list)
            val seenChannelIds = HashSet<String>(allChannels.size)
            val uniqueChannels = ArrayList<EpgChannel>(allChannels.size / 2)
            for (ch in allChannels) {
                if (seenChannelIds.add(ch.id)) {
                    uniqueChannels.add(ch)
                }
            }
            _channels.value = uniqueChannels

            // Build indexed map with dedup in a SINGLE PASS:
            // Groups by channelId while deduplicating, then sorts each group.
            // Avoids: Triple allocation per entry, intermediate deduped list, double iteration.
            val tempIndex = HashMap<String, MutableList<EpgEntry>>(uniqueChannels.size)
            val seenPrograms = HashSet<Long>(allPrograms.size)
            for (entry in allPrograms) {
                val key = entry.channelId.lowercase()
                // Composite hash key avoids Triple allocation — hash channelId+startTime+title
                val dedupKey = (key.hashCode().toLong() * 31 + entry.startTime) * 31 + entry.title.hashCode()
                if (seenPrograms.add(dedupKey)) {
                    tempIndex.getOrPut(key) { ArrayList() }.add(entry)
                }
            }
            // Sort each channel's program list in-place (avoids creating new sorted copies)
            for ((_, programs) in tempIndex) {
                programs.sortBy { it.startTime }
            }
            programIndex = tempIndex
        }
    }

    override fun getEpgForChannel(channelId: String): Flow<List<EpgEntry>> {
        // O(1) map lookup instead of O(n) filter + sort over entire program list
        val programs = programIndex[channelId.lowercase()] ?: emptyList()
        return MutableStateFlow(programs)
    }

    override fun getAllEpgChannels(): Flow<List<EpgChannel>> {
        return _channels.map { channels ->
            channels.map { ch ->
                ch.copy(programs = programIndex[ch.id.lowercase()] ?: emptyList())
            }
        }
    }

    override fun getCurrentProgram(channelId: String): EpgEntry? {
        // O(1) map lookup + small per-channel list scan instead of O(n) full-list scan
        val now = System.currentTimeMillis()
        val channelPrograms = programIndex[channelId.lowercase()] ?: return null
        return channelPrograms.find { it.startTime <= now && it.endTime >= now }
    }
}
