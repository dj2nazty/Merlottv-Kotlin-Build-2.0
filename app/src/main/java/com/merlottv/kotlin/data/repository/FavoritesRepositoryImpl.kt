package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.data.local.FavoritesDataStore
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDataStore: FavoritesDataStore
) : FavoritesRepository {

    override fun getFavoriteChannelIds(): Flow<Set<String>> = favoritesDataStore.favoriteChannels

    override fun getFavoriteVodIds(): Flow<Set<String>> = favoritesDataStore.favoriteVod

    override suspend fun toggleFavoriteChannel(channelId: String) {
        favoritesDataStore.toggleFavoriteChannel(channelId)
    }

    override suspend fun toggleFavoriteVod(vodId: String) {
        favoritesDataStore.toggleFavoriteVod(vodId)
    }

    override suspend fun isFavoriteChannel(channelId: String): Boolean {
        return favoritesDataStore.favoriteChannels.first().contains(channelId)
    }

    override suspend fun isFavoriteVod(vodId: String): Boolean {
        return favoritesDataStore.favoriteVod.first().contains(vodId)
    }
}
