package com.merlottv.kotlin.data.repository

import android.util.Log
import com.merlottv.kotlin.data.local.db.EpgDao
import com.merlottv.kotlin.data.local.db.toDomain
import com.merlottv.kotlin.data.local.db.toEntity
import com.merlottv.kotlin.data.parser.XmltvParser
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.domain.repository.EpgRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val okHttpClient: OkHttpClient,
    private val epgDao: EpgDao
) : EpgRepository {

    companion object {
        private const val TAG = "EpgRepo"
        private const val STALE_THRESHOLD_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    // In-memory fast path for LiveTvViewModel.getCurrentProgram() (non-suspend)
    @Volatile
    private var programIndex: Map<String, List<EpgEntry>> = emptyMap()

    private val epgClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val boundedIo = Dispatchers.IO.limitedParallelism(4)

    override suspend fun loadEpg(urls: List<String>) {
        withContext(boundedIo) {
            // Check if DB data is fresh enough
            val lastUpdate = epgDao.getLastUpdateTime()
            if (lastUpdate != null && (System.currentTimeMillis() - lastUpdate) < STALE_THRESHOLD_MS) {
                Log.d(TAG, "EPG data is fresh (${(System.currentTimeMillis() - lastUpdate) / 60000}min old), loading from DB")
                loadFromDb()
                return@withContext
            }

            // Data is stale or missing — download from network
            Log.d(TAG, "EPG data stale or missing, downloading from ${urls.size} sources")
            downloadAndStore(urls)
        }
    }

    override suspend fun forceRefresh(urls: List<String>) {
        withContext(boundedIo) {
            Log.d(TAG, "Force refreshing EPG from ${urls.size} sources")
            downloadAndStore(urls)
        }
    }

    override suspend fun isEpgStale(): Boolean {
        val lastUpdate = epgDao.getLastUpdateTime() ?: return true
        return (System.currentTimeMillis() - lastUpdate) >= STALE_THRESHOLD_MS
    }

    private suspend fun downloadAndStore(urls: List<String>) {
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
                        Log.e(TAG, "Failed to download EPG from $url", e)
                        Pair(emptyList<EpgChannel>(), emptyList<EpgEntry>())
                    }
                }
            }.awaitAll()

            for ((channels, programs) in results) {
                allChannels.addAll(channels)
                allPrograms.addAll(programs)
            }
        }

        // De-duplicate channels by ID
        val seenChannelIds = HashSet<String>(allChannels.size)
        val uniqueChannels = ArrayList<EpgChannel>(allChannels.size / 2)
        for (ch in allChannels) {
            if (seenChannelIds.add(ch.id)) {
                uniqueChannels.add(ch)
            }
        }

        // Deduplicate programs
        val tempIndex = HashMap<String, MutableList<EpgEntry>>(uniqueChannels.size)
        val seenPrograms = HashSet<Long>(allPrograms.size)
        for (entry in allPrograms) {
            val key = entry.channelId.lowercase()
            val dedupKey = (key.hashCode().toLong() * 31 + entry.startTime) * 31 + entry.title.hashCode()
            if (seenPrograms.add(dedupKey)) {
                tempIndex.getOrPut(key) { ArrayList() }.add(entry)
            }
        }
        for ((_, programs) in tempIndex) {
            programs.sortBy { it.startTime }
        }

        // Update in-memory index
        programIndex = tempIndex

        // Store to Room DB
        val now = System.currentTimeMillis()
        val channelEntities = uniqueChannels.map { it.toEntity(lastUpdated = now) }
        val programEntities = tempIndex.values.flatten().map { it.toEntity() }

        try {
            epgDao.replaceAll(channelEntities, programEntities)
            // Clean up expired programs older than 6 hours
            epgDao.deleteExpiredPrograms(now - 6 * 3600_000L)
            Log.d(TAG, "Stored ${channelEntities.size} channels and ${programEntities.size} programs to DB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store EPG to DB", e)
        }
    }

    private suspend fun loadFromDb() {
        // Populate in-memory index from DB for fast getCurrentProgram() lookups
        val now = System.currentTimeMillis()
        val windowStart = now - 6 * 3600_000L
        val windowEnd = now + 24 * 3600_000L

        try {
            val channels = epgDao.getAllChannels().first()
            val programs = epgDao.getAllProgramsInWindow(windowStart, windowEnd).first()

            val tempIndex = HashMap<String, MutableList<EpgEntry>>()
            for (entity in programs) {
                val key = entity.channelId.lowercase()
                tempIndex.getOrPut(key) { ArrayList() }.add(entity.toDomain())
            }
            for ((_, progs) in tempIndex) {
                progs.sortBy { it.startTime }
            }
            programIndex = tempIndex
            Log.d(TAG, "Loaded ${channels.size} channels, ${programs.size} programs from DB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from DB", e)
        }
    }

    override fun getEpgForChannel(channelId: String): Flow<List<EpgEntry>> {
        // Fast in-memory path first
        val memPrograms = programIndex[channelId.lowercase()]
        if (memPrograms != null) {
            return MutableStateFlow(memPrograms)
        }
        // Fallback to Room
        val now = System.currentTimeMillis()
        return epgDao.getProgramsForChannel(
            channelId,
            now - 6 * 3600_000L,
            now + 24 * 3600_000L
        ).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getAllEpgChannels(): Flow<List<EpgChannel>> {
        val now = System.currentTimeMillis()
        val windowStart = now - 6 * 3600_000L
        val windowEnd = now + 24 * 3600_000L

        return epgDao.getAllChannels().combine(
            epgDao.getAllProgramsInWindow(windowStart, windowEnd)
        ) { channels, programs ->
            val programsByChannel = programs.groupBy { it.channelId.lowercase() }
            channels.map { ch ->
                ch.toDomain(
                    programs = programsByChannel[ch.id.lowercase()]?.map { it.toDomain() } ?: emptyList()
                )
            }
        }
    }

    override fun getCurrentProgram(channelId: String): EpgEntry? {
        // Fast in-memory path (non-suspend, needed by LiveTvViewModel)
        val now = System.currentTimeMillis()
        val channelPrograms = programIndex[channelId.lowercase()] ?: return null
        return channelPrograms.find { it.startTime <= now && it.endTime >= now }
    }
}
