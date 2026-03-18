package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.data.local.FavoritesDataStore
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.sync.CloudSyncManager
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDataStore: FavoritesDataStore,
    private val profileDataStore: ProfileDataStore,
    private val cloudSyncManager: CloudSyncManager
) : FavoritesRepository {

    // Mutex to serialize favorite toggles and prevent race conditions on rapid clicks
    private val favoriteMutex = Mutex()

    // Profile-aware: automatically re-emits when active profile changes
    override fun getFavoriteChannelIds(): Flow<Set<String>> =
        profileDataStore.activeProfileId.flatMapLatest { profileId ->
            favoritesDataStore.favoriteChannels(profileId)
        }

    override fun getFavoriteVodIds(): Flow<Set<String>> =
        profileDataStore.activeProfileId.flatMapLatest { profileId ->
            favoritesDataStore.favoriteVod(profileId)
        }

    override fun getFavoriteVodMetas(): Flow<Map<String, FavoriteVodMeta>> =
        profileDataStore.activeProfileId.flatMapLatest { profileId ->
            favoritesDataStore.getVodMetaMap(profileId)
        }

    override suspend fun toggleFavoriteChannel(channelId: String) {
        val profileId = profileDataStore.getActiveProfileId()
        favoritesDataStore.toggleFavoriteChannel(channelId, profileId)
        cloudSyncManager.notifyFavoritesChanged(profileId)
    }

    override suspend fun toggleFavoriteVod(vodId: String) {
        val profileId = profileDataStore.getActiveProfileId()
        favoritesDataStore.toggleFavoriteVod(vodId, profileId)
        cloudSyncManager.notifyFavoritesChanged(profileId)
    }

    override suspend fun toggleFavoriteVodWithMeta(vodId: String, meta: FavoriteVodMeta) {
        favoriteMutex.withLock {
            val profileId = profileDataStore.getActiveProfileId()
            val isFav = favoritesDataStore.favoriteVod(profileId).first().contains(vodId)
            favoritesDataStore.toggleFavoriteVod(vodId, profileId)
            if (!isFav) {
                favoritesDataStore.saveVodMeta(meta, profileId)
            } else {
                favoritesDataStore.removeVodMeta(vodId, profileId)
            }
            try { cloudSyncManager.notifyFavoritesChanged(profileId) } catch (_: Exception) {}
        }
    }

    override suspend fun saveVodMeta(meta: FavoriteVodMeta) {
        val profileId = profileDataStore.getActiveProfileId()
        favoritesDataStore.saveVodMeta(meta, profileId)
        cloudSyncManager.notifyFavoritesChanged(profileId)
    }

    override suspend fun isFavoriteChannel(channelId: String): Boolean {
        val profileId = profileDataStore.getActiveProfileId()
        return favoritesDataStore.favoriteChannels(profileId).first().contains(channelId)
    }

    override suspend fun isFavoriteVod(vodId: String): Boolean {
        val profileId = profileDataStore.getActiveProfileId()
        return favoritesDataStore.favoriteVod(profileId).first().contains(vodId)
    }

    // Custom named favorites lists

    override fun getCustomLists(): Flow<Map<String, List<String>>> =
        favoritesDataStore.getCustomLists()

    override suspend fun createCustomList(name: String) {
        favoritesDataStore.createCustomList(name)
        cloudSyncManager.notifyFavoritesChanged(profileDataStore.getActiveProfileId())
    }

    override suspend fun deleteCustomList(name: String) {
        favoritesDataStore.deleteCustomList(name)
        cloudSyncManager.notifyFavoritesChanged(profileDataStore.getActiveProfileId())
    }

    override suspend fun addToCustomList(listName: String, vodId: String) {
        favoritesDataStore.addToCustomList(listName, vodId)
        cloudSyncManager.notifyFavoritesChanged(profileDataStore.getActiveProfileId())
    }

    override suspend fun removeFromCustomList(listName: String, vodId: String) {
        favoritesDataStore.removeFromCustomList(listName, vodId)
        cloudSyncManager.notifyFavoritesChanged(profileDataStore.getActiveProfileId())
    }

    override suspend fun renameCustomList(oldName: String, newName: String) {
        favoritesDataStore.renameCustomList(oldName, newName)
        cloudSyncManager.notifyFavoritesChanged(profileDataStore.getActiveProfileId())
    }

    // Watched tracking

    override fun getWatchedVodIds(): Flow<Set<String>> =
        profileDataStore.activeProfileId.flatMapLatest { profileId ->
            favoritesDataStore.watchedVodIds(profileId)
        }

    override suspend fun toggleWatched(vodId: String) {
        val profileId = profileDataStore.getActiveProfileId()
        favoritesDataStore.toggleWatched(vodId, profileId)
        cloudSyncManager.notifyFavoritesChanged(profileId)
    }
}
