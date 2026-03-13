package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.parser.M3uParser
import com.merlottv.kotlin.domain.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and caches channels from backup M3U sources.
 * Used for auto-failover when a primary channel stream fails.
 *
 * v2.25.0: Increased parallelism (3→5), tighter timeouts for backup downloads,
 * dedicated OkHttpClient to not block main playlist loading.
 */
@Singleton
class BackupChannelRepository @Inject constructor(
    private val m3uParser: M3uParser,
    private val okHttpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore
) {
    @Volatile
    private var cachedBackupChannels: List<Channel> = emptyList()
    @Volatile
    private var cacheTimestamp: Long = 0L
    private val cacheDuration = 30 * 60 * 1000L // 30 minutes
    private val boundedIo = Dispatchers.IO.limitedParallelism(5) // Bumped from 3→5

    // Dedicated client with tighter timeouts for backup source downloads
    private val backupClient = okHttpClient.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // Pre-compiled regex for normalizeName — avoids recompilation per call
    private val qualityTagRegex = Regex("\\b(HD|FHD|UHD|4K|SD|LQ|H\\.?265|H\\.?264|HEVC)\\b", RegexOption.IGNORE_CASE)
    private val countryPrefixRegex = Regex("^(US|UK|CA|AU|IN|FR|DE|ES|IT|BR|MX|NL|SE|NO|DK|FI|PL|RO|HU|CZ|GR|TR|ZA|NZ|IE|PT|BE|AT|CH):\\s*", RegexOption.IGNORE_CASE)
    private val separatorRegex = Regex("[\\s|:_\\-]+")

    /**
     * Find a backup stream for a channel by name.
     * Excludes any URLs in [excludeUrls] so the caller can skip already-tried streams.
     * Returns null if no match found.
     */
    suspend fun findBackupStream(channelName: String, excludeUrls: Set<String> = emptySet()): Channel? {
        ensureCacheLoaded()
        val candidates = if (excludeUrls.isEmpty()) {
            cachedBackupChannels
        } else {
            cachedBackupChannels.filter { it.streamUrl !in excludeUrls }
        }
        return findBestMatch(channelName, candidates)
    }

    fun invalidateCache() {
        cachedBackupChannels = emptyList()
        cacheTimestamp = 0L
    }

    fun hasSources(): Boolean {
        return cachedBackupChannels.isNotEmpty() || cacheTimestamp == 0L
    }

    private suspend fun ensureCacheLoaded() {
        val now = System.currentTimeMillis()
        if (cachedBackupChannels.isNotEmpty() && (now - cacheTimestamp) < cacheDuration) {
            return // Cache is still valid
        }

        val backupSources = settingsDataStore.backupSources.first()
        val enabledUrls = backupSources.filter { it.enabled }.map { it.url }
        if (enabledUrls.isEmpty()) {
            cachedBackupChannels = emptyList()
            cacheTimestamp = now
            return
        }

        val allChannels = withContext(boundedIo) {
            supervisorScope {
                enabledUrls.map { url ->
                    async {
                        try {
                            val request = Request.Builder().url(url).build()
                            val response = backupClient.newCall(request).execute()
                            response.use { resp ->
                                val body = resp.body
                                if (body != null) {
                                    m3uParser.parseStream(body.byteStream())
                                } else {
                                    emptyList()
                                }
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        }

        cachedBackupChannels = allChannels.distinctBy { it.streamUrl }
        cacheTimestamp = now
    }

    private fun findBestMatch(name: String, candidates: List<Channel>): Channel? {
        if (candidates.isEmpty()) return null
        val normalized = normalizeName(name)

        // 1. Exact case-insensitive match
        candidates.find { it.name.equals(name, ignoreCase = true) }?.let { return it }

        // 2. Normalized exact match
        candidates.find { normalizeName(it.name) == normalized }?.let { return it }

        // 3. Contains match (query contains candidate or vice versa)
        candidates.find {
            val candidateNorm = normalizeName(it.name)
            candidateNorm.contains(normalized) || normalized.contains(candidateNorm)
        }?.let { return it }

        return null
    }

    /**
     * Strip common IPTV naming noise: country codes, quality tags, etc.
     */
    private fun normalizeName(name: String): String {
        return name
            .replace(qualityTagRegex, "")
            .replace(countryPrefixRegex, "")
            .replace(separatorRegex, " ")
            .trim()
            .lowercase()
    }
}
