package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.data.local.FavoriteVodMeta
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

    override fun getFavoriteVodMetas(): Flow<Map<String, FavoriteVodMeta>> =
        favoritesDataStore.getVodMetaMap()

    override suspend fun toggleFavoriteChannel(channelId: String) {
        favoritesDataStore.toggleFavoriteChannel(channelId)
    }

    override suspend fun toggleFavoriteVod(vodId: String) {
        favoritesDataStore.toggleFavoriteVod(vodId)
    }

    override suspend fun toggleFavoriteVodWithMeta(vodId: String, meta: FavoriteVodMeta) {
        val isFav = favoritesDataStore.favoriteVod.first().contains(vodId)
        favoritesDataStore.toggleFavoriteVod(vodId)
        if (!isFav) {
            // Adding — save metadata
            favoritesDataStore.saveVodMeta(meta)
        } else {
            // Removing — clean up metadata
            favoritesDataStore.removeVodMeta(vodId)
        }
    }

    override suspend fun saveVodMeta(meta: FavoriteVodMeta) {
        favoritesDataStore.saveVodMeta(meta)
    }

    override suspend fun isFavoriteChannel(channelId: String): Boolean {
        return favoritesDataStore.favoriteChannels.first().contains(channelId)
    }

    override suspend fun isFavoriteVod(vodId: String): Boolean {
        return favoritesDataStore.favoriteVod.first().contains(vodId)
    }

    // Custom named favorites lists

    override fun getCustomLists(): Flow<Map<String, List<String>>> =
        favoritesDataStore.getCustomLists()

    override suspend fun createCustomList(name: String) {
        favoritesDataStore.createCustomList(name)
    }

    override suspend fun deleteCustomList(name: String) {
        favoritesDataStore.deleteCustomList(name)
    }

    override suspend fun addToCustomList(listName: String, vodId: String) {
        favoritesDataStore.addToCustomList(listName, vodId)
    }

    override suspend fun removeFromCustomList(listName: String, vodId: String) {
        favoritesDataStore.removeFromCustomList(listName, vodId)
    }
}
