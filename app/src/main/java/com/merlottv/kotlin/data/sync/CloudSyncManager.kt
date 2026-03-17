package com.merlottv.kotlin.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.data.local.FavoritesDataStore
import com.merlottv.kotlin.data.local.PlaylistEntry
import com.merlottv.kotlin.data.local.EpgSourceEntry
import com.merlottv.kotlin.data.local.BackupSourceEntry
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.local.UserProfile
import com.merlottv.kotlin.data.local.WatchProgressDataStore
import com.merlottv.kotlin.data.local.WatchProgressItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val profileDataStore: ProfileDataStore,
    private val favoritesDataStore: FavoritesDataStore,
    private val watchProgressDataStore: WatchProgressDataStore,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "CloudSync"
        private const val USERS = "users"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<ListenerRegistration>()

    // Flag to suppress upload-back while downloading cloud data
    @Volatile
    private var isSyncing = false

    private fun uid(): String? = auth.currentUser?.uid

    // ══════════════════════════════════════════════════════
    //  UPLOAD — Local → Firestore
    // ══════════════════════════════════════════════════════

    fun uploadAll() {
        val uid = uid() ?: return
        scope.launch {
            try {
                Log.d(TAG, "uploadAll for $uid")
                uploadProfiles()
                uploadSettings()
                // Upload favorites and progress for all profiles
                val profiles = profileDataStore.profiles.first()
                for (profile in profiles) {
                    uploadFavorites(profile.id)
                    uploadWatchProgress(profile.id)
                }
                Log.d(TAG, "uploadAll complete")
            } catch (e: Exception) {
                Log.e(TAG, "uploadAll failed: ${e.message}", e)
            }
        }
    }

    suspend fun uploadProfiles() {
        val uid = uid() ?: return
        try {
            val profiles = profileDataStore.profiles.first()
            val activeId = profileDataStore.getActiveProfileId()
            val doc = firestore.collection(USERS).document(uid)
                .collection("profiles").document("data")

            val profilesList = profiles.map { p ->
                mapOf(
                    "id" to p.id,
                    "name" to p.name,
                    "colorIndex" to p.colorIndex,
                    "avatarUrl" to p.avatarUrl,
                    "isDefault" to p.isDefault
                )
            }
            doc.set(mapOf("profiles" to profilesList, "activeProfileId" to activeId)).await()
            Log.d(TAG, "Uploaded ${profiles.size} profiles")
        } catch (e: Exception) {
            Log.e(TAG, "uploadProfiles failed: ${e.message}", e)
        }
    }

    suspend fun uploadFavorites(profileId: String) {
        val uid = uid() ?: return
        try {
            val channels = favoritesDataStore.favoriteChannels(profileId).first().toList()
            val vod = favoritesDataStore.favoriteVod(profileId).first().toList()
            val vodMeta = favoritesDataStore.getVodMetaMap(profileId).first()
            val watched = favoritesDataStore.watchedVodIds(profileId).first().toList()
            val customLists = favoritesDataStore.getCustomLists().first()

            val metaMap = vodMeta.mapValues { (_, meta) ->
                mapOf(
                    "name" to meta.name,
                    "poster" to meta.poster,
                    "type" to meta.type,
                    "imdbRating" to meta.imdbRating,
                    "description" to meta.description
                )
            }

            val listsMap = customLists.mapValues { (_, ids) -> ids }

            val doc = firestore.collection(USERS).document(uid)
                .collection("favorites").document(profileId)

            doc.set(
                mapOf(
                    "channels" to channels,
                    "vod" to vod,
                    "vodMeta" to metaMap,
                    "watched" to watched,
                    "customLists" to listsMap
                )
            ).await()
            Log.d(TAG, "Uploaded favorites for profile $profileId")
        } catch (e: Exception) {
            Log.e(TAG, "uploadFavorites failed for $profileId: ${e.message}", e)
        }
    }

    suspend fun uploadWatchProgress(profileId: String) {
        val uid = uid() ?: return
        try {
            val items = watchProgressDataStore.getContinueWatchingItems(profileId).first()
            val itemsMap = items.associate { item ->
                item.id to mapOf(
                    "title" to item.title,
                    "poster" to item.poster,
                    "type" to item.type,
                    "position" to item.position,
                    "duration" to item.duration,
                    "timestamp" to item.timestamp,
                    "progressPercent" to item.progressPercent.toDouble()
                )
            }

            val doc = firestore.collection(USERS).document(uid)
                .collection("watchProgress").document(profileId)

            doc.set(mapOf("items" to itemsMap)).await()
            Log.d(TAG, "Uploaded watch progress for profile $profileId (${items.size} items)")
        } catch (e: Exception) {
            Log.e(TAG, "uploadWatchProgress failed for $profileId: ${e.message}", e)
        }
    }

    suspend fun uploadSettings() {
        val uid = uid() ?: return
        try {
            val data = mutableMapOf<String, Any>()

            // Playlists
            val playlists = settingsDataStore.playlists.first()
            data["playlists"] = playlists.map { mapOf("name" to it.name, "url" to it.url, "enabled" to it.enabled) }

            // EPG sources
            val epgSources = settingsDataStore.customEpgSources.first()
            data["epgSources"] = epgSources.map { mapOf("name" to it.name, "url" to it.url, "enabled" to it.enabled) }

            // Backup sources
            val backupSources = settingsDataStore.backupSources.first()
            data["backupSources"] = backupSources.map { mapOf("name" to it.name, "url" to it.url, "enabled" to it.enabled) }

            // Simple settings
            data["torboxKey"] = settingsDataStore.torboxKey.first()
            data["customAddons"] = settingsDataStore.customAddons.first()
            data["disabledAddons"] = settingsDataStore.disabledAddons.first().toList()
            data["lastWatchedChannelId"] = settingsDataStore.lastWatchedChannelId.first()

            // Subtitle settings
            data["subtitlesEnabled"] = settingsDataStore.subtitlesEnabled.first()
            data["subtitleLanguage"] = settingsDataStore.subtitleLanguage.first()
            data["subtitleSize"] = settingsDataStore.subtitleSize.first().toDouble()
            data["subtitleFont"] = settingsDataStore.subtitleFont.first()

            // Playback settings
            data["frameRateMatching"] = settingsDataStore.frameRateMatching.first()
            data["nextEpisodeAutoPlay"] = settingsDataStore.nextEpisodeAutoPlay.first()
            data["nextEpisodeThresholdPercent"] = settingsDataStore.nextEpisodeThresholdPercent.first()
            data["bitrateCheckerEnabled"] = settingsDataStore.bitrateCheckerEnabled.first()
            data["bufferDurationMs"] = settingsDataStore.bufferDurationMs.first()

            // Weather
            data["weatherZipCode"] = settingsDataStore.weatherZipCode.first()
            data["weatherAlertsEnabled"] = settingsDataStore.weatherAlertsEnabled.first()

            // VOD category system
            data["homeCategoryOrder"] = settingsDataStore.homeCategoryOrder.first()
            data["homeHiddenCategories"] = settingsDataStore.homeHiddenCategories.first().toList()
            data["vodCategoryOrder"] = settingsDataStore.vodCategoryOrder.first()
            data["vodHiddenCategories"] = settingsDataStore.vodHiddenCategories.first().toList()

            // Live TV category order
            data["categoryOrder"] = settingsDataStore.categoryOrder.first()

            val doc = firestore.collection(USERS).document(uid)
                .collection("settings").document("app")

            doc.set(data).await()
            Log.d(TAG, "Uploaded settings")
        } catch (e: Exception) {
            Log.e(TAG, "uploadSettings failed: ${e.message}", e)
        }
    }

    // ══════════════════════════════════════════════════════
    //  DOWNLOAD — Firestore → Local (Cloud Wins)
    // ══════════════════════════════════════════════════════

    suspend fun downloadAll() {
        val uid = uid() ?: return
        isSyncing = true
        try {
            Log.d(TAG, "downloadAll for $uid")
            downloadProfiles(uid)
            downloadSettings(uid)
            // Download favorites and progress for all profiles
            val profiles = profileDataStore.profiles.first()
            for (profile in profiles) {
                downloadFavorites(uid, profile.id)
                downloadWatchProgress(uid, profile.id)
            }
            Log.d(TAG, "downloadAll complete")
        } catch (e: Exception) {
            Log.e(TAG, "downloadAll failed: ${e.message}", e)
        } finally {
            isSyncing = false
        }
    }

    private suspend fun downloadProfiles(uid: String) {
        try {
            val doc = firestore.collection(USERS).document(uid)
                .collection("profiles").document("data").get().await()

            if (!doc.exists()) {
                Log.d(TAG, "No cloud profiles found, uploading local")
                uploadProfiles()
                return
            }

            @Suppress("UNCHECKED_CAST")
            val profilesList = doc.get("profiles") as? List<Map<String, Any>> ?: return
            val activeId = doc.getString("activeProfileId") ?: "default"

            val profiles = profilesList.map { map ->
                UserProfile(
                    id = map["id"] as? String ?: "default",
                    name = map["name"] as? String ?: "Profile",
                    colorIndex = (map["colorIndex"] as? Number)?.toInt() ?: 0,
                    avatarUrl = map["avatarUrl"] as? String ?: "",
                    isDefault = map["isDefault"] as? Boolean ?: false
                )
            }

            profileDataStore.restoreProfiles(profiles)
            profileDataStore.restoreActiveProfileId(activeId)
            Log.d(TAG, "Downloaded ${profiles.size} profiles")
        } catch (e: Exception) {
            Log.e(TAG, "downloadProfiles failed: ${e.message}", e)
        }
    }

    private suspend fun downloadFavorites(uid: String, profileId: String) {
        try {
            val doc = firestore.collection(USERS).document(uid)
                .collection("favorites").document(profileId).get().await()

            if (!doc.exists()) {
                Log.d(TAG, "No cloud favorites for $profileId, uploading local")
                uploadFavorites(profileId)
                return
            }

            @Suppress("UNCHECKED_CAST")
            val channels = (doc.get("channels") as? List<String>)?.toSet() ?: emptySet()
            @Suppress("UNCHECKED_CAST")
            val vod = (doc.get("vod") as? List<String>)?.toSet() ?: emptySet()
            @Suppress("UNCHECKED_CAST")
            val watched = (doc.get("watched") as? List<String>)?.toSet() ?: emptySet()

            favoritesDataStore.restoreFavoriteChannels(profileId, channels)
            favoritesDataStore.restoreFavoriteVod(profileId, vod)
            favoritesDataStore.restoreWatched(profileId, watched)

            // Restore VOD metadata
            @Suppress("UNCHECKED_CAST")
            val vodMetaRaw = doc.get("vodMeta") as? Map<String, Map<String, Any>>
            if (vodMetaRaw != null) {
                val metaMap = vodMetaRaw.mapValues { (id, map) ->
                    FavoriteVodMeta(
                        id = id,
                        name = map["name"] as? String ?: "",
                        poster = map["poster"] as? String ?: "",
                        type = map["type"] as? String ?: "movie",
                        imdbRating = map["imdbRating"] as? String ?: "",
                        description = map["description"] as? String ?: ""
                    )
                }
                favoritesDataStore.restoreVodMeta(profileId, metaMap)
            }

            // Restore custom lists
            @Suppress("UNCHECKED_CAST")
            val customListsRaw = doc.get("customLists") as? Map<String, List<String>>
            if (customListsRaw != null) {
                favoritesDataStore.restoreCustomLists(customListsRaw)
            }

            Log.d(TAG, "Downloaded favorites for profile $profileId")
        } catch (e: Exception) {
            Log.e(TAG, "downloadFavorites failed for $profileId: ${e.message}", e)
        }
    }

    private suspend fun downloadWatchProgress(uid: String, profileId: String) {
        try {
            val doc = firestore.collection(USERS).document(uid)
                .collection("watchProgress").document(profileId).get().await()

            if (!doc.exists()) {
                Log.d(TAG, "No cloud watch progress for $profileId, uploading local")
                uploadWatchProgress(profileId)
                return
            }

            @Suppress("UNCHECKED_CAST")
            val itemsRaw = doc.get("items") as? Map<String, Map<String, Any>> ?: return

            val items = itemsRaw.mapValues { (id, map) ->
                WatchProgressItem(
                    id = id,
                    title = map["title"] as? String ?: "",
                    poster = map["poster"] as? String ?: "",
                    type = map["type"] as? String ?: "movie",
                    position = (map["position"] as? Number)?.toLong() ?: 0L,
                    duration = (map["duration"] as? Number)?.toLong() ?: 0L,
                    timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                    progressPercent = (map["progressPercent"] as? Number)?.toFloat() ?: 0f
                )
            }

            watchProgressDataStore.restoreProgress(profileId, items)
            Log.d(TAG, "Downloaded watch progress for profile $profileId (${items.size} items)")
        } catch (e: Exception) {
            Log.e(TAG, "downloadWatchProgress failed for $profileId: ${e.message}", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun downloadSettings(uid: String) {
        try {
            val doc = firestore.collection(USERS).document(uid)
                .collection("settings").document("app").get().await()

            if (!doc.exists()) {
                Log.d(TAG, "No cloud settings found, uploading local")
                uploadSettings()
                return
            }

            val data = doc.data ?: return

            // Playlists
            val playlistsRaw = data["playlists"] as? List<Map<String, Any>>
            if (playlistsRaw != null) {
                val playlists = playlistsRaw.map {
                    PlaylistEntry(
                        name = it["name"] as? String ?: "",
                        url = it["url"] as? String ?: "",
                        enabled = it["enabled"] as? Boolean ?: true
                    )
                }
                settingsDataStore.setPlaylists(playlists)
            }

            // EPG sources
            val epgRaw = data["epgSources"] as? List<Map<String, Any>>
            if (epgRaw != null) {
                val sources = epgRaw.map {
                    EpgSourceEntry(
                        name = it["name"] as? String ?: "",
                        url = it["url"] as? String ?: "",
                        enabled = it["enabled"] as? Boolean ?: true
                    )
                }
                settingsDataStore.setCustomEpgSources(sources)
            }

            // Backup sources
            val backupRaw = data["backupSources"] as? List<Map<String, Any>>
            if (backupRaw != null) {
                val sources = backupRaw.map {
                    BackupSourceEntry(
                        name = it["name"] as? String ?: "",
                        url = it["url"] as? String ?: "",
                        enabled = it["enabled"] as? Boolean ?: true
                    )
                }
                settingsDataStore.setBackupSources(sources)
            }

            // Simple settings
            (data["torboxKey"] as? String)?.let { settingsDataStore.setTorboxKey(it) }
            (data["customAddons"] as? String)?.let { settingsDataStore.setCustomAddons(it) }
            (data["lastWatchedChannelId"] as? String)?.let { settingsDataStore.setLastWatchedChannelId(it) }

            // Disabled addons
            val disabledRaw = data["disabledAddons"] as? List<String>
            if (disabledRaw != null) {
                settingsDataStore.restoreDisabledAddons(disabledRaw.toSet())
            }

            // Subtitle settings
            (data["subtitlesEnabled"] as? Boolean)?.let { settingsDataStore.setSubtitlesEnabled(it) }
            (data["subtitleLanguage"] as? String)?.let { settingsDataStore.setSubtitleLanguage(it) }
            (data["subtitleSize"] as? Number)?.toFloat()?.let { settingsDataStore.setSubtitleSize(it) }
            (data["subtitleFont"] as? String)?.let { settingsDataStore.setSubtitleFont(it) }

            // Playback settings
            (data["frameRateMatching"] as? String)?.let { settingsDataStore.setFrameRateMatching(it) }
            (data["nextEpisodeAutoPlay"] as? Boolean)?.let { settingsDataStore.setNextEpisodeAutoPlay(it) }
            (data["nextEpisodeThresholdPercent"] as? Number)?.toInt()?.let { settingsDataStore.setNextEpisodeThresholdPercent(it) }
            (data["bitrateCheckerEnabled"] as? Boolean)?.let { settingsDataStore.setBitrateCheckerEnabled(it) }
            (data["bufferDurationMs"] as? Number)?.toInt()?.let { settingsDataStore.setBufferDurationMs(it) }

            // Weather
            (data["weatherZipCode"] as? String)?.let { settingsDataStore.setWeatherZipCode(it) }
            (data["weatherAlertsEnabled"] as? Boolean)?.let { settingsDataStore.setWeatherAlertsEnabled(it) }

            // VOD category system
            val homeCatOrder = data["homeCategoryOrder"] as? List<String>
            if (homeCatOrder != null) settingsDataStore.setHomeCategoryOrder(homeCatOrder)
            val homeHidden = data["homeHiddenCategories"] as? List<String>
            if (homeHidden != null) settingsDataStore.setHomeHiddenCategories(homeHidden.toSet())
            val vodCatOrder = data["vodCategoryOrder"] as? List<String>
            if (vodCatOrder != null) settingsDataStore.setVodCategoryOrder(vodCatOrder)
            val vodHidden = data["vodHiddenCategories"] as? List<String>
            if (vodHidden != null) settingsDataStore.setVodHiddenCategories(vodHidden.toSet())

            // Live TV category order
            val catOrder = data["categoryOrder"] as? List<String>
            if (catOrder != null) settingsDataStore.setCategoryOrder(catOrder)

            Log.d(TAG, "Downloaded settings")
        } catch (e: Exception) {
            Log.e(TAG, "downloadSettings failed: ${e.message}", e)
        }
    }

    // ══════════════════════════════════════════════════════
    //  REAL-TIME LISTENERS
    // ══════════════════════════════════════════════════════

    fun startRealtimeSync() {
        stopRealtimeSync()
        val uid = uid() ?: return
        Log.d(TAG, "Starting real-time sync for $uid")

        // Listen to settings changes
        val settingsListener = firestore.collection(USERS).document(uid)
            .collection("settings").document("app")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                if (isSyncing) return@addSnapshotListener
                scope.launch {
                    isSyncing = true
                    try { downloadSettings(uid) } finally { isSyncing = false }
                }
            }
        listeners.add(settingsListener)

        // Listen to profiles changes
        val profilesListener = firestore.collection(USERS).document(uid)
            .collection("profiles").document("data")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                if (isSyncing) return@addSnapshotListener
                scope.launch {
                    isSyncing = true
                    try { downloadProfiles(uid) } finally { isSyncing = false }
                }
            }
        listeners.add(profilesListener)

        // Listen to favorites for all current profiles
        scope.launch {
            val profiles = profileDataStore.profiles.first()
            for (profile in profiles) {
                val favListener = firestore.collection(USERS).document(uid)
                    .collection("favorites").document(profile.id)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                        if (isSyncing) return@addSnapshotListener
                        scope.launch {
                            isSyncing = true
                            try { downloadFavorites(uid, profile.id) } finally { isSyncing = false }
                        }
                    }
                listeners.add(favListener)

                val progressListener = firestore.collection(USERS).document(uid)
                    .collection("watchProgress").document(profile.id)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                        if (isSyncing) return@addSnapshotListener
                        scope.launch {
                            isSyncing = true
                            try { downloadWatchProgress(uid, profile.id) } finally { isSyncing = false }
                        }
                    }
                listeners.add(progressListener)
            }
        }
    }

    fun stopRealtimeSync() {
        listeners.forEach { it.remove() }
        listeners.clear()
        Log.d(TAG, "Stopped real-time sync")
    }

    // ══════════════════════════════════════════════════════
    //  CHANGE NOTIFICATIONS — called after local writes
    // ══════════════════════════════════════════════════════

    fun notifyFavoritesChanged(profileId: String) {
        if (isSyncing || uid() == null) return
        scope.launch {
            try { uploadFavorites(profileId) } catch (e: Exception) {
                Log.e(TAG, "notifyFavoritesChanged upload failed: ${e.message}")
            }
        }
    }

    fun notifyWatchProgressChanged(profileId: String) {
        if (isSyncing || uid() == null) return
        scope.launch {
            try { uploadWatchProgress(profileId) } catch (e: Exception) {
                Log.e(TAG, "notifyWatchProgressChanged upload failed: ${e.message}")
            }
        }
    }

    fun notifySettingsChanged() {
        if (isSyncing || uid() == null) return
        scope.launch {
            try { uploadSettings() } catch (e: Exception) {
                Log.e(TAG, "notifySettingsChanged upload failed: ${e.message}")
            }
        }
    }

    fun notifyProfilesChanged() {
        if (isSyncing || uid() == null) return
        scope.launch {
            try { uploadProfiles() } catch (e: Exception) {
                Log.e(TAG, "notifyProfilesChanged upload failed: ${e.message}")
            }
        }
    }
}
