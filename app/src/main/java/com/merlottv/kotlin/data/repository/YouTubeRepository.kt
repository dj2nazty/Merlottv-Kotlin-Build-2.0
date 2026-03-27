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
        YouTubeChannel("UCX6OQ3DkcsbYNE6H8uQQuVA", "MrBeast", "@MrBeast"),
        YouTubeChannel("UCnmGIkw-KdI0W5siakKPKog", "Ryan Trahan", "@ryan"),
        YouTubeChannel("UCY1kMZp36IQSyNx_9h4mpCg", "Mark Rober", "@MarkRober"),
        YouTubeChannel("UCfpCQ89W9wjkHc8J_6eTbBg", "Outdoor Boys", "@OutdoorBoys")
    )

    private var cachedVideos: List<YouTubeVideo> = emptyList()
    private var cacheTimestamp: Long = 0L
    private var isNewPipeInitialized = false

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
                        fetchChannelVideosFromRss(channel)
                    }
                }.awaitAll().flatten()
            }

            // Filter out short videos (under 2 minutes) — this removes Shorts and very brief content
            val filteredVideos = coroutineScope {
                allVideos.map { video ->
                    async {
                        val duration = getVideoDurationSeconds(video.videoId)
                        // Keep video if duration is unknown (-1) or >= 2 minutes
                        if (duration in 0 until MIN_DURATION_SECONDS) {
                            Log.d(TAG, "Filtered short video: ${video.title} (${duration}s) from ${video.channelName}")
                            null
                        } else {
                            video
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            Log.d(TAG, "Filtered ${allVideos.size - filteredVideos.size} short videos, kept ${filteredVideos.size} regular videos")

            // Sort by published date descending (newest first)
            val sorted = filteredVideos.sortedByDescending { it.publishedDate }
            cachedVideos = sorted
            cacheTimestamp = System.currentTimeMillis()
            sorted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch YouTube videos", e)
            cachedVideos // Return stale cache on error
        }
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
