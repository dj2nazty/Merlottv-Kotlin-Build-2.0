package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.ChannelGroup
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    suspend fun loadChannels(playlistUrl: String): List<Channel>
    fun getChannelGroups(): Flow<List<ChannelGroup>>
    fun searchChannels(query: String): Flow<List<Channel>>
}
