package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.ChannelGroup
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    suspend fun loadChannels(playlistUrl: String): List<Channel>
    suspend fun loadMultipleChannels(playlistUrls: List<String>): List<Channel>
    /** Load channels from multiple playlists, grouping each by playlist name prefix */
    suspend fun loadMultipleChannelsGrouped(playlists: List<Pair<String, String>>): List<Channel>
    fun getChannelGroups(): Flow<List<ChannelGroup>>
    fun searchChannels(query: String): Flow<List<Channel>>
}
