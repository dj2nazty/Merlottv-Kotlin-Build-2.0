package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.data.local.FavoriteVodMeta
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun getFavoriteChannelIds(): Flow<Set<String>>
    fun getFavoriteVodIds(): Flow<Set<String>>
    fun getFavoriteVodMetas(): Flow<Map<String, FavoriteVodMeta>>
    suspend fun toggleFavoriteChannel(channelId: String)
    suspend fun toggleFavoriteVod(vodId: String)
    suspend fun toggleFavoriteVodWithMeta(vodId: String, meta: FavoriteVodMeta)
    suspend fun isFavoriteChannel(channelId: String): Boolean
    suspend fun isFavoriteVod(vodId: String): Boolean
}
