package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.model.Meta
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.model.Stream
import kotlinx.coroutines.flow.Flow

interface AddonRepository {
    fun getAllAddons(): Flow<List<Addon>>
    fun getEnabledAddons(): Flow<List<Addon>>
    suspend fun fetchManifest(url: String): Addon?
    suspend fun getCatalog(addon: Addon, type: String, catalogId: String, skip: Int = 0, genre: String? = null): List<MetaPreview>
    suspend fun getMeta(type: String, id: String): Meta?
    suspend fun getStreams(type: String, id: String): List<Stream>
    suspend fun searchCatalog(addon: Addon, type: String, query: String): List<MetaPreview>
    suspend fun addAddon(url: String): Addon?
    suspend fun removeAddon(url: String)

    /** Pause/resume network requests — when paused, only cached results are returned.
     *  Use this when Live TV is active to prevent addon traffic from competing for bandwidth. */
    fun setNetworkPaused(paused: Boolean)
}
