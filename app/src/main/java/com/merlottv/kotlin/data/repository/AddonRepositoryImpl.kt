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
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : AddonRepository {

    private val _addons = MutableStateFlow(DefaultData.DEFAULT_ADDONS)

    // Manifest cache: URL -> (Addon, timestamp)
    private val manifestCache = ConcurrentHashMap<String, Pair<Addon, Long>>()
    private val CACHE_TTL = TimeUnit.MINUTES.toMillis(30)

    // Catalog cache: URL -> (List<MetaPreview>, timestamp) — 10 min TTL
    private val catalogCache = ConcurrentHashMap<String, Pair<List<MetaPreview>, Long>>()
    private val CATALOG_CACHE_TTL = TimeUnit.MINUTES.toMillis(10)

    // Meta cache: "type:id" -> (Meta, timestamp) — 15 min TTL
    private val metaCache = ConcurrentHashMap<String, Pair<Meta, Long>>()
    private val META_CACHE_TTL = TimeUnit.MINUTES.toMillis(15)

    // Per-request client with shorter timeout for catalog/meta fetches
    private val fastClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    override fun getAllAddons(): Flow<List<Addon>> = _addons

    override suspend fun fetchManifest(url: String): Addon? {
        // Check cache first
        manifestCache[url]?.let { (addon, timestamp) ->
            if (System.currentTimeMillis() - timestamp < CACHE_TTL) {
                return addon
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = fastClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val addon = parseManifest(body, url)
                if (addon != null) {
                    manifestCache[url] = Pair(addon, System.currentTimeMillis())
                }
                addon
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
        skip: Int,
        genre: String?
    ): List<MetaPreview> {
        return withContext(Dispatchers.IO) {
            try {
                val base = addon.url.removeSuffix("/manifest.json")
                // Build URL with optional genre and skip extra params
                val extras = mutableListOf<String>()
                if (genre != null) extras.add("genre=$genre")
                if (skip > 0) extras.add("skip=$skip")
                val url = if (extras.isNotEmpty()) {
                    "$base/catalog/$type/$catalogId/${extras.joinToString("&")}.json"
                } else {
                    "$base/catalog/$type/$catalogId.json"
                }

                // Check catalog cache first
                catalogCache[url]?.let { (items, timestamp) ->
                    if (System.currentTimeMillis() - timestamp < CATALOG_CACHE_TTL) {
                        Log.d("AddonRepo", "getCatalog CACHE HIT: $url (${items.size} items)")
                        return@withContext items
                    }
                }

                Log.d("AddonRepo", "getCatalog URL: $url")
                val request = Request.Builder().url(url).build()
                val response = fastClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                val items = parseCatalogResponse(body)

                // Cache the result
                if (items.isNotEmpty()) {
                    catalogCache[url] = Pair(items, System.currentTimeMillis())
                }
                items
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    override suspend fun getMeta(type: String, id: String): Meta? {
        // Check meta cache first
        val cacheKey = "$type:$id"
        metaCache[cacheKey]?.let { (meta, timestamp) ->
            if (System.currentTimeMillis() - timestamp < META_CACHE_TTL) {
                Log.d("AddonRepo", "getMeta CACHE HIT: $cacheKey")
                return meta
            }
        }

        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            // Query ALL addons in parallel for massive speed improvement
            val results = supervisorScope {
                _addons.value.map { addon ->
                    async(Dispatchers.IO) {
                        withTimeoutOrNull(5000L) { // Reduced from 7s to 5s
                            try {
                                val base = addon.url.removeSuffix("/manifest.json")
                                val url = "$base/meta/$type/$id.json"
                                val request = Request.Builder().url(url).build()
                                val response = fastClient.newCall(request).execute()
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (body != null) parseMetaResponse(body) else null
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll()
            }.filterNotNull()

            val elapsed = System.currentTimeMillis() - startTime
            Log.d("AddonRepo", "getMeta parallel: ${results.size} results in ${elapsed}ms")

            if (results.isEmpty()) return@withContext null

            val best = if (type != "series") {
                // For movies, return the richest result (most fields populated)
                results.maxByOrNull {
                    (if (it.description.isNotEmpty()) 1 else 0) +
                    (if (it.poster.isNotEmpty()) 1 else 0) +
                    (if (it.background.isNotEmpty()) 1 else 0) +
                    (if (it.imdbRating.isNotEmpty()) 1 else 0) +
                    it.genres.size + it.cast.size +
                    it.trailerStreams.size
                }
            } else {
                // For series, prefer one with episodes/videos
                val withVideos = results.filter { it.videos.isNotEmpty() }
                if (withVideos.isNotEmpty()) {
                    withVideos.maxByOrNull { it.videos.size }
                } else {
                    // Fallback: return richest metadata even without episodes
                    results.maxByOrNull {
                        (if (it.description.isNotEmpty()) 1 else 0) +
                        (if (it.poster.isNotEmpty()) 1 else 0) +
                        it.genres.size + it.cast.size
                    }
                }
            }

            // Cache the result
            if (best != null) {
                metaCache[cacheKey] = Pair(best, System.currentTimeMillis())
            }
            best
        }
    }

    override suspend fun getStreams(type: String, id: String): List<Stream> {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            // Query ALL addons in parallel for faster stream discovery
            val results = supervisorScope {
                _addons.value.map { addon ->
                    async(Dispatchers.IO) {
                        withTimeoutOrNull(7000L) {
                            try {
                                val base = addon.url.removeSuffix("/manifest.json")
                                val url = "$base/stream/$type/$id.json"
                                val request = Request.Builder().url(url).build()
                                val response = fastClient.newCall(request).execute()
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (body != null) parseStreamResponse(body, addon.name, addon.logo)
                                    else emptyList()
                                } else emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        } ?: emptyList()
                    }
                }.awaitAll()
            }.flatten()

            val elapsed = System.currentTimeMillis() - startTime
            Log.d("AddonRepo", "getStreams parallel: ${results.size} streams in ${elapsed}ms")
            results
        }
    }

    override suspend fun searchCatalog(addon: Addon, type: String, query: String): List<MetaPreview> {
        return withContext(Dispatchers.IO) {
            try {
                val base = addon.url.removeSuffix("/manifest.json")
                // Use %20 for spaces (URLEncoder uses + which some Stremio addons reject)
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")

                // Find catalogs for this type that support search
                val searchCatalogs = addon.catalogs
                    .filter { cat ->
                        cat.type == type &&
                        cat.extra.any { it.name == "search" }
                    }
                    .map { it.id }
                    .distinct()
                    .ifEmpty {
                        // Fallback: if no catalogs are defined (manifest not fetched),
                        // try "top" which is the Cinemeta default
                        if (addon.catalogs.isEmpty()) listOf("top") else return@withContext emptyList()
                    }

                // Parallelize multi-catalog search within each addon
                val results = supervisorScope {
                    searchCatalogs.map { catalogId ->
                        async(Dispatchers.IO) {
                            try {
                                val url = "$base/catalog/$type/$catalogId/search=$encodedQuery.json"
                                val request = Request.Builder().url(url).build()
                                val response = fastClient.newCall(request).execute()
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (body != null) parseCatalogResponse(body) else emptyList()
                                } else emptyList()
                            } catch (_: Exception) { emptyList() }
                        }
                    }.awaitAll().flatten()
                }
                results
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
        manifestCache.remove(url)
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
                            isRequired = ex["isRequired"] as? Boolean ?: false,
                            options = (ex["options"] as? List<String>) ?: emptyList()
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
                    imdbRating = m["imdbRating"] as? String ?: "",
                    background = m["background"] as? String ?: "",
                    logo = m["logo"] as? String ?: ""
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
                } ?: emptyList(),
                trailerStreams = (meta["trailerStreams"] as? List<Map<String, Any?>>)?.map { t ->
                    com.merlottv.kotlin.domain.model.TrailerStream(
                        title = t["title"] as? String ?: "",
                        ytId = t["ytId"] as? String ?: "",
                        url = t["url"] as? String ?: "",
                        source = t["source"] as? String ?: ""
                    )
                } ?: run {
                    // Fallback: check for single "trailer" field (YouTube URL or ID)
                    val trailer = meta["trailer"] as? String ?: ""
                    if (trailer.isNotEmpty()) {
                        val ytId = if (trailer.contains("youtube.com") || trailer.contains("youtu.be")) {
                            trailer.substringAfter("v=", "").substringAfter("youtu.be/", "").substringBefore("&").substringBefore("?")
                        } else trailer
                        listOf(com.merlottv.kotlin.domain.model.TrailerStream(title = "Trailer", ytId = ytId))
                    } else emptyList()
                }
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
