package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.model.AddonCatalog
import com.merlottv.kotlin.domain.model.CatalogExtra
import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.Meta
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.model.Stream
import com.merlottv.kotlin.domain.model.Video
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : AddonRepository {

    private val _addons = MutableStateFlow(DefaultData.DEFAULT_ADDONS)

    override fun getAllAddons(): Flow<List<Addon>> = _addons

    override suspend fun fetchManifest(url: String): Addon? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                parseManifest(body, url)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    override suspend fun getCatalog(
        addon: Addon,
        type: String,
        catalogId: String,
        skip: Int
    ): List<MetaPreview> {
        return withContext(Dispatchers.IO) {
            try {
                val base = addon.url.removeSuffix("/manifest.json")
                val url = "$base/catalog/$type/$catalogId/skip=$skip.json"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseCatalogResponse(body)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    override suspend fun getMeta(type: String, id: String): Meta? {
        return withContext(Dispatchers.IO) {
            for (addon in _addons.value) {
                try {
                    val base = addon.url.removeSuffix("/manifest.json")
                    val url = "$base/meta/$type/$id.json"
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: continue
                        val meta = parseMetaResponse(body)
                        if (meta != null) return@withContext meta
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            null
        }
    }

    override suspend fun getStreams(type: String, id: String): List<Stream> {
        return withContext(Dispatchers.IO) {
            val allStreams = mutableListOf<Stream>()
            for (addon in _addons.value) {
                try {
                    val base = addon.url.removeSuffix("/manifest.json")
                    val url = "$base/stream/$type/$id.json"
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: continue
                        val streams = parseStreamResponse(body, addon.name, addon.logo)
                        allStreams.addAll(streams)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            allStreams
        }
    }

    override suspend fun searchCatalog(addon: Addon, type: String, query: String): List<MetaPreview> {
        return withContext(Dispatchers.IO) {
            try {
                val base = addon.url.removeSuffix("/manifest.json")
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$base/catalog/$type/top/search=$encodedQuery.json"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseCatalogResponse(body)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    override suspend fun addAddon(url: String): Addon? {
        val addon = fetchManifest(url) ?: return null
        val current = _addons.value.toMutableList()
        if (current.none { it.url == url }) {
            current.add(addon)
            _addons.value = current
        }
        return addon
    }

    override suspend fun removeAddon(url: String) {
        val current = _addons.value.toMutableList()
        current.removeAll { it.url == url && !it.isDefault }
        _addons.value = current
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseManifest(json: String, url: String): Addon? {
        try {
            val adapter = moshi.adapter(Map::class.java)
            val map = adapter.fromJson(json) as? Map<String, Any?> ?: return null
            val catalogs = (map["catalogs"] as? List<Map<String, Any?>>)?.map { cat ->
                AddonCatalog(
                    id = cat["id"] as? String ?: "",
                    name = cat["name"] as? String ?: "",
                    type = cat["type"] as? String ?: "",
                    extra = (cat["extra"] as? List<Map<String, Any?>>)?.map { ex ->
                        CatalogExtra(
                            name = ex["name"] as? String ?: "",
                            isRequired = ex["isRequired"] as? Boolean ?: false
                        )
                    } ?: emptyList()
                )
            } ?: emptyList()

            return Addon(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "Unknown",
                url = url,
                description = map["description"] as? String ?: "",
                logo = map["logo"] as? String ?: "",
                version = map["version"] as? String ?: "",
                catalogs = catalogs,
                types = (map["types"] as? List<String>) ?: emptyList(),
                resources = (map["resources"] as? List<Any>)?.mapNotNull {
                    when (it) {
                        is String -> it
                        is Map<*, *> -> it["name"] as? String
                        else -> null
                    }
                } ?: emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCatalogResponse(json: String): List<MetaPreview> {
        try {
            val adapter = moshi.adapter(Map::class.java)
            val map = adapter.fromJson(json) as? Map<String, Any?> ?: return emptyList()
            val metas = map["metas"] as? List<Map<String, Any?>> ?: return emptyList()
            return metas.map { m ->
                MetaPreview(
                    id = m["id"] as? String ?: "",
                    type = m["type"] as? String ?: "",
                    name = m["name"] as? String ?: "",
                    poster = m["poster"] as? String ?: "",
                    posterShape = m["posterShape"] as? String ?: "poster",
                    description = m["description"] as? String ?: "",
                    imdbRating = m["imdbRating"] as? String ?: ""
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMetaResponse(json: String): Meta? {
        try {
            val adapter = moshi.adapter(Map::class.java)
            val map = adapter.fromJson(json) as? Map<String, Any?> ?: return null
            val meta = map["meta"] as? Map<String, Any?> ?: return null
            return Meta(
                id = meta["id"] as? String ?: "",
                type = meta["type"] as? String ?: "",
                name = meta["name"] as? String ?: "",
                poster = meta["poster"] as? String ?: "",
                posterShape = meta["posterShape"] as? String ?: "poster",
                background = meta["background"] as? String ?: "",
                logo = meta["logo"] as? String ?: "",
                description = meta["description"] as? String ?: "",
                releaseInfo = meta["releaseInfo"] as? String ?: "",
                year = meta["year"]?.toString() ?: "",
                runtime = meta["runtime"] as? String ?: "",
                genres = (meta["genres"] as? List<String>) ?: emptyList(),
                cast = (meta["cast"] as? List<String>) ?: emptyList(),
                director = (meta["director"] as? List<String>) ?: emptyList(),
                writer = (meta["writer"] as? List<String>) ?: emptyList(),
                imdbRating = meta["imdbRating"] as? String ?: "",
                videos = (meta["videos"] as? List<Map<String, Any?>>)?.map { v ->
                    Video(
                        id = v["id"] as? String ?: "",
                        title = v["title"] as? String ?: v["name"] as? String ?: "",
                        season = (v["season"] as? Number)?.toInt() ?: 0,
                        episode = (v["episode"] as? Number)?.toInt() ?: 0,
                        released = v["released"] as? String ?: "",
                        overview = v["overview"] as? String ?: "",
                        thumbnail = v["thumbnail"] as? String ?: ""
                    )
                } ?: emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStreamResponse(json: String, addonName: String, addonLogo: String): List<Stream> {
        try {
            val adapter = moshi.adapter(Map::class.java)
            val map = adapter.fromJson(json) as? Map<String, Any?> ?: return emptyList()
            val streams = map["streams"] as? List<Map<String, Any?>> ?: return emptyList()
            return streams.map { s ->
                Stream(
                    name = s["name"] as? String ?: "",
                    title = s["title"] as? String ?: "",
                    url = s["url"] as? String ?: "",
                    ytId = s["ytId"] as? String ?: "",
                    infoHash = s["infoHash"] as? String ?: "",
                    fileIdx = (s["fileIdx"] as? Number)?.toInt(),
                    externalUrl = s["externalUrl"] as? String ?: "",
                    addonName = addonName,
                    addonLogo = addonLogo
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}
