package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.data.parser.XmltvParser
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.domain.repository.EpgRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepositoryImpl @Inject constructor(
    private val xmltvParser: XmltvParser,
    private val okHttpClient: OkHttpClient
) : EpgRepository {

    private val _channels = MutableStateFlow<List<EpgChannel>>(emptyList())
    private val _programs = MutableStateFlow<List<EpgEntry>>(emptyList())

    override suspend fun loadEpg(urls: List<String>) {
        withContext(Dispatchers.IO) {
            val allChannels = mutableListOf<EpgChannel>()
            val allPrograms = mutableListOf<EpgEntry>()

            for (url in urls) {
                try {
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val (channels, programs) = xmltvParser.parse(body)
                    allChannels.addAll(channels)
                    allPrograms.addAll(programs)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _channels.value = allChannels
            _programs.value = allPrograms
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
            val programsByChannel = _programs.value.groupBy { it.channelId }
            channels.map { ch ->
                ch.copy(programs = programsByChannel[ch.id] ?: emptyList())
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
