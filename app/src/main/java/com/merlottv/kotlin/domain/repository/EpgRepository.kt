package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import kotlinx.coroutines.flow.Flow

interface EpgRepository {
    suspend fun loadEpg(urls: List<String>)
    fun getEpgForChannel(channelId: String): Flow<List<EpgEntry>>
    fun getAllEpgChannels(): Flow<List<EpgChannel>>
    fun getCurrentProgram(channelId: String): EpgEntry?
    suspend fun isEpgStale(): Boolean
    suspend fun forceRefresh(urls: List<String>)
}
