package com.merlottv.kotlin.data.repository

import android.util.Log
import com.merlottv.kotlin.domain.model.YouTubeChannel
import com.merlottv.kotlin.domain.model.YouTubeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "YouTubeRepository"
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes
    }

    val channels = listOf(
        YouTubeChannel("UCX6OQ3DkcsbYNE6H8uQQuVA", "MrBeast", "@MrBeast",
            "https://yt3.googleusercontent.com/nxYrc_1_2f77DoBadyxMTmv7ZpRZapHR5jbuYe7PlPd5cIRJxtNNEYyOC0ZsxaDyJJzXrnJiuDE=s176"),
        YouTubeChannel("UCnmGIkw-KdI0W5siakKPKog", "Ryan Trahan", "@ryan",
            "https://yt3.googleusercontent.com/fiJrCXLTjjY531uelhbpUD21Cb0iMb6vF21M6-H7ZhjMZPe2cAkIeB9yWUHtENkFhq1F3oVbgg=s176"),
        YouTubeChannel("UCY1kMZp36IQSyNx_9h4mpCg", "Mark Rober", "@MarkRober",
            "https://yt3.googleusercontent.com/ytc/AIdro_ksXY2REjZ6gYKSgnWT5jC_zT9mX900vyFtVinR8KbHww=s176"),
        YouTubeChannel("UCfpCQ89W9wjkHc8J_6eTbBg", "Outdoor Boys", "@OutdoorBoys",
            "https://yt3.googleusercontent.com/ytc/AIdro_kMwdFZd-Nl1trq6PgKkkqH58o9Lj0phbNoTaFTtNeoNg4=s176"),
        YouTubeChannel("UCgOjLy9aHn5zyhcnY-pUY7w", "LouisAyy", "@itslouisayy",
            "https://yt3.googleusercontent.com/gLOq-5cIk2yoH3enxb2gRAcRCqMEpajA21rVV4tbmAailAuAMkzKA7wBRgh_UK5R2CLfFvMsbw=s176"),
        YouTubeChannel("UChQssdHvCDA4dyMd34LxCvQ", "corvscate", "@corvscateee",
            "https://yt3.googleusercontent.com/nZ-6ea0ODmEoKL_oEIMZ2QWgiMueKjjee6EjKZwF605bYM3faqmpWcKWkYho59BZlu7QlM6I=s176"),
        YouTubeChannel("UC56D-IHcUvLVFTX_8NpQMXg", "Brent Rivera", "@brentrivera",
            "https://yt3.googleusercontent.com/ytc/AIdro_nckg4JPK13gMS2o4E9ZVGXg6bHK0PgwVXTe3YCmvKHksU=s176"),
        YouTubeChannel("UCPpATKqmMV-CNRNWYaDUwiA", "Alexa Rivera", "@AlexaRivera",
            "https://yt3.googleusercontent.com/ytc/AIdro_kxLcU1jnY9TKkr2nYYLELoU49m2oN1XLtgbFLEDTFqwWgC=s176"),
        YouTubeChannel("UCVAbWl3d3XuHY28wU9DoDpA", "pierson", "@pierson",
            "https://yt3.googleusercontent.com/ytc/AIdro_m5jZXt6SUoifMivcJSL7mJ_uKZiXWZ9JiymX1z_mH-Kupe=s176"),
        YouTubeChannel("UCDP7DZOgj8VTyhyVNup83QQ", "Amp World", "@AmpWorld.",
            "https://yt3.googleusercontent.com/ytc/AIdro_kffCERRNGERegcpf4HCMZJ-ARS7v1bW6yfAV8pWgo0kG8=s176"),
        YouTubeChannel("UCZevH_tgMbrm6r-_OiU6Ubg", "Drew Dirksen", "@drewdirksen",
            "https://yt3.googleusercontent.com/tIMTAmRD-YTkApPA9jWLxc0av08NImyEVWn5wARFktDdjdX3AkNbw-ZXf3eNQ8LRYt4S63dRSQ=s176"),
        YouTubeChannel("UCfdmAsuxBFKc43Oe3PJG_ig", "Matthew Sieverin", "@MatthewSieverin",
            "https://yt3.googleusercontent.com/Q5BXNyq0Iq65EDKrbIqm2ad16Dzor7PMUUwi2GKAxteEdeUJVJO5qpWs_UNzcg9jhHmhGvNH=s176"),
        YouTubeChannel("UCKmmERguliWTynG9OIoDhDw", "Law By Mike", "@LawByMike",
            "https://yt3.googleusercontent.com/I01ufsSeJLlmEGw6ufYtYNj1FuOgBFXmBFLJjMhQb0uXiSPt-fiubzvYIxP8gBZK_zZunuZKYQ=s176"),
        YouTubeChannel("UC0QHWhjbe5fGJEPz3sVb6nw", "Doctor Mike", "@DoctorMike",
            "https://yt3.googleusercontent.com/YPiqL_eCFzCI8cVU_bZrQ0jTrGRNp6lc7a8hR5qmszAjHSL8qnqBgRunlPd8eZZAWHlKAtFRew=s176"),
        YouTubeChannel("UCKaCalz5N5ienIbfPzEbYuA", "Jordan Matter", "@jordanmatter",
            "https://yt3.googleusercontent.com/ytc/AIdro_lDjDRPwwtga_q3VbrMyDHbpETZ28gejqUoD1Codxft3TA=s176"),
        YouTubeChannel("UClQ3NafOy_42dJ0toK3QUKw", "Stay Wild", "@StayWild-",
            "https://yt3.googleusercontent.com/CjNRuV7sXgZYcKhcMdVp6_Oo8tMoOwmDjCRqWYdd-70WeKJYxfxHgFtqUIlRGsq48CWkL_OEyAY=s176"),
        YouTubeChannel("UCwVg9btOceLQuNCdoQk9CXg", "Ben Azelart", "@BenAzelart",
            "https://yt3.googleusercontent.com/Y_5UEmFz4ClK77lAjsApC6Hv1h0t4WaeJWpZCPfK1t_ghrnaVeDtiqQWKOJyu4aofx7o25jmaaA=s176"),
        YouTubeChannel("UCfw8x3VR-ElcaWW2Tg_jgSA", "Rebecca Zamolo", "@rebeccazamolo",
            "https://yt3.googleusercontent.com/Lo0hg1nOIahjHQTOa5ITXzgeOU3v6QPYQCPyOXeGI3zMerogVZ-PE7RtcCVH4Hb6IJw0KQsPjw=s176"),
        YouTubeChannel("UCPuEAY09CtdTzFNWuqVZgDw", "Topper Guild", "@topperguild",
            "https://yt3.googleusercontent.com/tIUjsEc4SPp4pLASoh1Zvt9Oto5X9qdkWUTeJPDTUcuhLLbh_QAdn7R4CXTcGT-EfG03-DDQ=s176"),
        YouTubeChannel("UCHJy1RypctP_ozyxaLTXlBg", "Tiny Cabin Life", "@TinyCabinLife",
            "https://yt3.googleusercontent.com/9kTcffRUr5WUUiHOTYJDkHFJgLiEdlqZiq62HQMNlT-8DuinBcjB-RbcB3TtiDQOALGgQJIS=s176"),
        YouTubeChannel("UCstLIadsuuLmDdIzMZxesfg", "Wild Homestead", "@wildhomestead",
            "https://yt3.googleusercontent.com/_SmV4Kx-3K20ES_O3CNrHaQltAtweNO1hLMWh_3nWobSSpc4HAPx5iIhhwcwbb0WQAyPfq6i=s176"),
        YouTubeChannel("UCYXlRJxXcBmE3uuKyC7JaCA", "Alaska Cabin Adventures", "@alaskacabinadventures",
            "https://yt3.googleusercontent.com/QbXH8mC4ahUfIRkh_RF7IbbVKG7O7R2Z46KzB8su3CoB0gqh6OzTQQXDeZqZofkLILaoqwe13w=s176")
    )

    private var cachedVideos: List<YouTubeVideo> = emptyList()
    private var cacheTimestamp: Long = 0L
    private var isNewPipeInitialized = false
    private var channelAvatars: Map<String, String> = emptyMap() // channelId → avatarUrl

    /**
     * Get channels with avatar URLs. Uses hardcoded avatars (always available).
     */
    fun getChannelsWithAvatars(): List<YouTubeChannel> {
        return channels // avatarUrl is already hardcoded in each channel
    }

    private suspend fun fetchChannelAvatars() = withContext(Dispatchers.IO) {
        val avatars = mutableMapOf<String, String>()
        coroutineScope {
            channels.map { channel ->
                async {
                    try {
                        val requestBody = """
                            {
                                "context": {
                                    "client": {
                                        "clientName": "WEB",
                                        "clientVersion": "2.20240101.00.00",
                                        "hl": "en",
                                        "gl": "US"
                                    }
                                },
                                "browseId": "${channel.channelId}"
                            }
                        """.trimIndent()

                        val request = Request.Builder()
                            .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                            .post(requestBody.toRequestBody("application/json".toMediaType()))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .header("Content-Type", "application/json")
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        val body = response.body?.string() ?: return@async

                        // Extract avatar URL from the response — look for thumbnail URLs in channel metadata
                        val avatarRegex = Regex(""""thumbnails"\s*:\s*\[\s*\{\s*"url"\s*:\s*"(https://yt3\.googleusercontent\.com/[^"]+)"""")
                        val match = avatarRegex.find(body)
                        if (match != null) {
                            // Get the URL and request a 176px version
                            var url = match.groupValues[1]
                            // Replace size param to get a good quality avatar
                            url = url.replace(Regex("=s\\d+"), "=s176")
                            if (!url.contains("=s")) url += "=s176"
                            synchronized(avatars) {
                                avatars[channel.channelId] = url
                            }
                            Log.d(TAG, "Got avatar for ${channel.channelName}: ${url.take(80)}...")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch avatar for ${channel.channelName}", e)
                    }
                }
            }.awaitAll()
        }
        channelAvatars = avatars
    }

    private fun ensureNewPipeInitialized() {
        if (!isNewPipeInitialized) {
            NewPipe.init(OkHttpDownloader(okHttpClient), Localization.DEFAULT)
            isNewPipeInitialized = true
        }
    }

    suspend fun fetchAllVideos(forceRefresh: Boolean = false): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedVideos.isNotEmpty() &&
            System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION_MS
        ) {
            return@withContext cachedVideos
        }

        try {
            val allVideos = coroutineScope {
                channels.map { channel ->
                    async {
                        try {
                            fetchChannelVideosInnertube(channel)
                        } catch (e: Exception) {
                            Log.e(TAG, "Innertube fetch failed for ${channel.channelName}, falling back to RSS", e)
                            fetchChannelVideosFromRss(channel)
                        }
                    }
                }.awaitAll().flatten()
            }

            Log.d(TAG, "Fetched ${allVideos.size} total videos across ${channels.size} channels")

            // Sort by published date descending (newest first)
            val sorted = allVideos.sortedByDescending { it.publishedDate }
            cachedVideos = sorted
            cacheTimestamp = System.currentTimeMillis()
            sorted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch YouTube videos", e)
            cachedVideos // Return stale cache on error
        }
    }

    /**
     * Fetch channel videos using Innertube browse API with pagination.
     * Goes back up to 4 years of content, filtering out Shorts (< 120s).
     * Much more comprehensive than RSS which only returns 15 videos.
     */
    private fun fetchChannelVideosInnertube(channel: YouTubeChannel): List<YouTubeVideo> {
        val cutoffMs = System.currentTimeMillis() - (4L * 365 * 24 * 60 * 60 * 1000) // 4 years ago
        val videos = mutableListOf<YouTubeVideo>()
        var continuation: String? = null
        var isFirstPage = true
        var pagesLoaded = 0
        val maxPages = 20 // Safety limit

        while (pagesLoaded < maxPages) {
            pagesLoaded++
            val requestBody = if (isFirstPage) {
                // First request: browse the channel's Videos tab
                """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB",
                            "clientVersion": "2.20240101.00.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "browseId": "${channel.channelId}",
                    "params": "EgZ2aWRlb3PyBgQKAjoA"
                }
                """.trimIndent()
            } else {
                // Continuation request
                """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB",
                            "clientVersion": "2.20240101.00.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "continuation": "$continuation"
                }
                """.trimIndent()
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/json")
                .build()

            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Browse request failed for ${channel.channelName} page $pagesLoaded", e)
                break
            }

            val body = response.body?.string() ?: break

            // Parse videos from response
            val pageVideos = parseInnertubeVideos(body, channel.channelName, cutoffMs)
            videos.addAll(pageVideos.first)

            // Check if we've gone past the cutoff date
            if (pageVideos.second) {
                Log.d(TAG, "${channel.channelName}: reached 4-year cutoff after $pagesLoaded pages, ${videos.size} videos")
                break
            }

            // Get continuation token for next page
            continuation = extractContinuationToken(body)
            if (continuation == null) {
                Log.d(TAG, "${channel.channelName}: no more pages after $pagesLoaded, ${videos.size} videos")
                break
            }

            isFirstPage = false
        }

        Log.d(TAG, "${channel.channelName}: fetched ${videos.size} videos across $pagesLoaded pages")
        return videos
    }

    /**
     * Parse Innertube browse response for video items.
     * Returns Pair(videos, reachedCutoff)
     */
    private fun parseInnertubeVideos(json: String, channelName: String, cutoffMs: Long): Pair<List<YouTubeVideo>, Boolean> {
        val videos = mutableListOf<YouTubeVideo>()
        var reachedCutoff = false

        // Extract richItemRenderer or gridVideoRenderer blocks containing videoId
        // Pattern: "videoId":"XXXXXXXXXXX" near "title":{"runs":[{"text":"..."}]}
        val videoBlockRegex = Regex(""""videoRenderer"\s*:\s*\{[^}]*?"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})".*?(?:"title"\s*:\s*\{"runs"\s*:\s*\[\s*\{"text"\s*:\s*"((?:[^"\\]|\\.)*)"\})|(?:"title"\s*:\s*\{"runs"\s*:\s*\[\s*\{"text"\s*:\s*"((?:[^"\\]|\\.)*)"\})""")

        // Simpler approach: find all videoIds and titles
        val videoIdRegex = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
        val allVideoIds = videoIdRegex.findAll(json).map { it.groupValues[1] }.toList()

        // Extract video data using a more targeted approach
        // Look for richItemRenderer containing videoRenderer blocks
        val richItemRegex = Regex(""""richItemRenderer".*?"videoRenderer"\s*:\s*\{""")

        // Use a sequential parsing approach for reliability
        val videoSegments = json.split("\"videoRenderer\"")

        for (i in 1 until videoSegments.size) {
            val segment = videoSegments[i]

            // Extract videoId
            val vidId = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""").find(segment)?.groupValues?.get(1) ?: continue

            // Extract title
            val title = Regex(""""title"\s*:\s*\{\s*"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(segment)?.groupValues?.get(1)
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?.replace("\\n", " ")
                ?: continue

            // Extract length/duration text to filter Shorts
            val lengthText = Regex(""""lengthText"\s*:\s*\{\s*"accessibility".*?"simpleText"\s*:\s*"([^"]+)"""").find(segment)?.groupValues?.get(1)
            val durationSeconds = parseDurationText(lengthText)

            // Filter Shorts (under 2 minutes)
            if (durationSeconds in 0 until MIN_DURATION_SECONDS) {
                continue
            }

            // Extract published time text (e.g., "2 years ago", "3 months ago")
            val publishedText = Regex(""""publishedTimeText"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"""").find(segment)?.groupValues?.get(1) ?: ""

            // Extract view count text (e.g., "38K views", "1.2M views")
            val viewCountText = Regex(""""viewCountText"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"""").find(segment)?.groupValues?.get(1)
                ?: Regex(""""shortViewCountText"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"""").find(segment)?.groupValues?.get(1)
                ?: ""

            // Convert relative time to approximate ISO date for sorting
            val publishedDate = relativeTimeToIso(publishedText)

            // Check if we've gone past 4 years
            if (publishedText.contains("year") || publishedText.contains("years")) {
                val yearsMatch = Regex("""(\d+)\s+year""").find(publishedText)
                val years = yearsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (years > 4) {
                    reachedCutoff = true
                    break
                }
            }

            videos.add(
                YouTubeVideo(
                    videoId = vidId,
                    title = title,
                    thumbnailUrl = "https://i.ytimg.com/vi/$vidId/hqdefault.jpg",
                    channelName = channelName,
                    publishedDate = publishedDate,
                    viewCount = viewCountText,
                    publishedTimeText = publishedText
                )
            )
        }

        return Pair(videos, reachedCutoff)
    }

    /**
     * Parse duration text like "12:34" or "1:23:45" to seconds.
     * Returns -1 if unparseable.
     */
    private fun parseDurationText(text: String?): Int {
        if (text == null) return -1
        val parts = text.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2] // H:MM:SS
            2 -> parts[0] * 60 + parts[1]                     // M:SS
            1 -> parts[0]                                       // SS
            else -> -1
        }
    }

    /**
     * Convert relative time text to approximate ISO date string for sorting.
     */
    private fun relativeTimeToIso(text: String): String {
        val now = System.currentTimeMillis()
        val ms = when {
            text.contains("hour") -> {
                val n = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 1
                n * 3600 * 1000
            }
            text.contains("day") -> {
                val n = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 1
                n * 86400 * 1000
            }
            text.contains("week") -> {
                val n = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 1
                n * 7 * 86400 * 1000
            }
            text.contains("month") -> {
                val n = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 1
                n * 30 * 86400 * 1000
            }
            text.contains("year") -> {
                val n = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 1
                n * 365 * 86400 * 1000
            }
            else -> 0L
        }
        val date = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return date.format(java.util.Date(now - ms))
    }

    /**
     * Extract continuation token from Innertube response for pagination.
     */
    private fun extractContinuationToken(json: String): String? {
        // Look for continuationCommand or continuationEndpoint tokens
        val regex = Regex(""""token"\s*:\s*"([a-zA-Z0-9%_=-]+)"""")
        val matches = regex.findAll(json).toList()
        // The last continuation token is usually the "load more" button
        return matches.lastOrNull()?.groupValues?.get(1)?.takeIf { it.length > 20 }
    }

    private fun fetchChannelVideosFromRss(channel: YouTubeChannel): List<YouTubeVideo> {
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=${channel.channelId}"
        val request = Request.Builder().url(url).build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            parseRssFeed(body, channel.channelName)
        } catch (e: Exception) {
            Log.e(TAG, "RSS fetch failed for ${channel.channelName}", e)
            emptyList()
        }
    }

    private fun parseRssFeed(xml: String, channelName: String): List<YouTubeVideo> {
        val videos = mutableListOf<YouTubeVideo>()

        // Simple XML parsing — extract <entry> blocks
        val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        val videoIdRegex = Regex("<yt:videoId>(.*?)</yt:videoId>")
        val titleRegex = Regex("<title>(.*?)</title>")
        val publishedRegex = Regex("<published>(.*?)</published>")

        for (match in entryRegex.findAll(xml)) {
            val entry = match.groupValues[1]
            val videoId = videoIdRegex.find(entry)?.groupValues?.get(1) ?: continue
            val title = titleRegex.find(entry)?.groupValues?.get(1)
                ?.replace("&amp;", "&")
                ?.replace("&lt;", "<")
                ?.replace("&gt;", ">")
                ?.replace("&quot;", "\"")
                ?.replace("&#39;", "'")
                ?: continue
            val published = publishedRegex.find(entry)?.groupValues?.get(1) ?: ""

            videos.add(
                YouTubeVideo(
                    videoId = videoId,
                    title = title,
                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    channelName = channelName,
                    publishedDate = published
                )
            )
        }
        return videos
    }

    private val MIN_DURATION_SECONDS = 120 // Filter out videos under 2 minutes

    /**
     * Gets the duration of a YouTube video in seconds using the Innertube API.
     * This is the same reliable API used by YouTubeExtractor for stream extraction.
     * Returns -1 if duration cannot be determined (video is kept as safe default).
     */
    private fun getVideoDurationSeconds(videoId: String): Int {
        return try {
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "IOS",
                            "clientVersion": "20.10.4",
                            "deviceMake": "Apple",
                            "deviceModel": "iPhone16,2",
                            "osVersion": "18.3.2",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "videoId": "$videoId",
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)")
                .header("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return -1

            // Extract lengthSeconds from videoDetails in Innertube response
            val lengthMatch = Regex("\"lengthSeconds\"\\s*:\\s*\"(\\d+)\"").find(body)
            val seconds = lengthMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
            Log.d(TAG, "Video $videoId duration: ${seconds}s (Innertube)")
            seconds
        } catch (e: Exception) {
            Log.e(TAG, "Innertube duration check failed for $videoId", e)
            -1
        }
    }

    suspend fun extractStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            ensureNewPipeInitialized()
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(url)

            // Prefer video streams — pick best quality MP4
            val videoStreams = info.videoOnlyStreams + info.videoStreams
            val bestStream = videoStreams
                .filter { it.format?.mimeType?.contains("video/mp4") == true || it.format?.suffix == "mp4" }
                .maxByOrNull { it.resolution?.replace("p", "")?.toIntOrNull() ?: 0 }
                ?: videoStreams.firstOrNull()

            bestStream?.content ?: info.videoStreams.firstOrNull()?.content
        } catch (e: Exception) {
            Log.e(TAG, "Stream extraction failed for $videoId", e)
            null
        }
    }

    /**
     * OkHttp-based Downloader for NewPipe Extractor
     */
    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            val requestBuilder = Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), null)

            request.headers().forEach { (key, values) ->
                values.forEach { value ->
                    requestBuilder.addHeader(key, value)
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.code == 429) {
                throw ReCaptchaException("reCAPTCHA challenge", request.url())
            }

            val responseHeaders = mutableMapOf<String, List<String>>()
            response.headers.toMultimap().forEach { (key, values) ->
                responseHeaders[key] = values
            }

            return Response(
                response.code,
                response.message,
                responseHeaders,
                responseBody,
                response.request.url.toString()
            )
        }
    }
}
