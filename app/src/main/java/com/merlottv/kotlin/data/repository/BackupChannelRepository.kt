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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and caches channels from backup M3U sources.
 * Used for auto-failover when a primary channel stream fails.
 */
@Singleton
class BackupChannelRepository @Inject constructor(
    private val m3uParser: M3uParser,
    private val okHttpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore
) {
    private var cachedBackupChannels: List<Channel> = emptyList()
    private var cacheTimestamp: Long = 0L
    private val cacheDuration = 30 * 60 * 1000L // 30 minutes
    private val boundedIo = Dispatchers.IO.limitedParallelism(3)

    /**
     * Find a backup stream for a channel by name.
     * Returns null if no match found.
     */
    suspend fun findBackupStream(channelName: String): Channel? {
        ensureCacheLoaded()
        return findBestMatch(channelName, cachedBackupChannels)
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
                            val response = okHttpClient.newCall(request).execute()
                            response.use { resp ->
                                val body = resp.body?.string() ?: ""
                                m3uParser.parse(body)
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
            .replace(Regex("\\b(HD|FHD|UHD|4K|SD|LQ|H\\.?265|H\\.?264|HEVC)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(US|UK|CA|AU|IN|FR|DE|ES|IT|BR|MX|NL|SE|NO|DK|FI|PL|RO|HU|CZ|GR|TR|ZA|NZ|IE|PT|BE|AT|CH):\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\s|:_\\-]+"), " ")
            .trim()
            .lowercase()
    }
}
