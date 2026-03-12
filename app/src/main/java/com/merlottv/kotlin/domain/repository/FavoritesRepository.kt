package com.merlottv.kotlin.domain.repository

import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun getFavoriteChannelIds(): Flow<Set<String>>
    fun getFavoriteVodIds(): Flow<Set<String>>
    suspend fun toggleFavoriteChannel(channelId: String)
    suspend fun toggleFavoriteVod(vodId: String)
    suspend fun isFavoriteChannel(channelId: String): Boolean
    suspend fun isFavoriteVod(vodId: String): Boolean
}
