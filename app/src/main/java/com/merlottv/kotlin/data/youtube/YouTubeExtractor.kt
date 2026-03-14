package com.merlottv.kotlin.data.youtube

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of YouTube stream extraction.
 * - videoUrl + audioUrl = adaptive streams (need MergingMediaSource)
 * - progressiveUrl = muxed video+audio in one stream
 * - hlsManifestUrl = HLS manifest fallback
 */
data class YouTubeStreamResult(
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val progressiveUrl: String? = null,
    val hlsManifestUrl: String? = null,
    val durationMs: Long = 0L,
    val userAgent: String = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip"
)

/**
 * Extracts actual playable video/audio stream URLs from YouTube
 * by calling YouTube's internal Innertube /youtubei/v1/player API.
 *
 * Based on the approach used by NuvioTV, NewPipe, and yt-dlp.
 */
@Singleton
class YouTubeExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "YouTubeExtractor"
        private const val INNERTUBE_API_URL =
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

        // Client configs — tried in order until one returns playable streams.
        // TV_EMBEDDED is tried first because it returns HLS manifests that don't
        // require PO tokens or nsig decryption (unlike adaptive/progressive URLs).
        private val CLIENTS = listOf(
            ClientConfig(
                name = "TV_EMBEDDED",
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                androidSdkVersion = null,
                userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.5) AppleWebKit/537.36 (KHTML, like Gecko) 85.0.4183.93/6.5 TV Safari/537.36",
                deviceMake = null,
                deviceModel = null,
                osVersion = null,
                isEmbedded = true
            ),
            ClientConfig(
                name = "IOS",
                clientName = "IOS",
                clientVersion = "20.10.4",
                androidSdkVersion = null,
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osVersion = "18.3.2"
            ),
            ClientConfig(
                name = "ANDROID",
                clientName = "ANDROID",
                clientVersion = "20.10.35",
                androidSdkVersion = 34,
                userAgent = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip",
                deviceMake = null,
                deviceModel = null,
                osVersion = "14"
            )
        )
    }

    private data class ClientConfig(
        val name: String,
        val clientName: String,
        val clientVersion: String,
        val androidSdkVersion: Int?,
        val userAgent: String,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osVersion: String? = null,
        val isEmbedded: Boolean = false
    )

    private data class CacheEntry(
        val result: YouTubeStreamResult,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Extract playable stream URLs for a YouTube video ID.
     * Returns null if all client attempts fail.
     */
    suspend fun extract(videoId: String): YouTubeStreamResult? = withContext(Dispatchers.IO) {
        // Check cache
        val cached = cache[videoId]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return@withContext cached.result
        }

        // First pass: try to get HLS (most reliable — no PO tokens needed)
        for (clientConfig in CLIENTS) {
            try {
                val result = tryClient(videoId, clientConfig)
                if (result != null && result.hlsManifestUrl != null) {
                    cache[videoId] = CacheEntry(result, System.currentTimeMillis())
                    Log.d(TAG, "Got HLS for $videoId via ${clientConfig.name}")
                    return@withContext result
                }
                // If we got adaptive/progressive but no HLS, save as fallback
                if (result != null && (result.videoUrl != null || result.progressiveUrl != null)) {
                    Log.d(TAG, "Got adaptive/progressive for $videoId via ${clientConfig.name} (no HLS, saving as fallback)")
                    cache[videoId] = CacheEntry(result, System.currentTimeMillis())
                    // Don't return yet — keep looking for HLS from other clients
                }
            } catch (e: Exception) {
                Log.w(TAG, "Client ${clientConfig.name} failed for $videoId: ${e.message}")
            }
        }

        // Second: return cached adaptive/progressive if no HLS was found
        val fallback = cache[videoId]
        if (fallback != null) {
            Log.d(TAG, "Using adaptive/progressive fallback for $videoId")
            return@withContext fallback.result
        }

        Log.w(TAG, "All clients failed for $videoId")
        null
    }

    @Suppress("UNCHECKED_CAST")
    private fun tryClient(videoId: String, config: ClientConfig): YouTubeStreamResult? {
        val requestBody = buildRequestBody(videoId, config)
        val request = Request.Builder()
            .url(INNERTUBE_API_URL)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", config.userAgent)
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", getClientId(config.clientName))
            .header("X-YouTube-Client-Version", config.clientVersion)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }

        val body = response.body?.string() ?: return null
        val adapter = moshi.adapter(Map::class.java)
        val json = adapter.fromJson(body) as? Map<String, Any?> ?: return null

        // Check playability
        val playabilityStatus = json["playabilityStatus"] as? Map<String, Any?>
        val status = playabilityStatus?.get("status") as? String
        if (status != "OK") {
            Log.w(TAG, "Playability status: $status for $videoId via ${config.name}")
            return null
        }

        val streamingData = json["streamingData"] as? Map<String, Any?> ?: return null

        // Parse duration
        val videoDetails = json["videoDetails"] as? Map<String, Any?>
        val durationSec = (videoDetails?.get("lengthSeconds") as? String)?.toLongOrNull() ?: 0L
        val durationMs = durationSec * 1000L

        // Parse adaptive formats (separate video + audio)
        val adaptiveFormats = streamingData["adaptiveFormats"] as? List<Map<String, Any?>> ?: emptyList()

        // Parse progressive formats (muxed video+audio)
        val formats = streamingData["formats"] as? List<Map<String, Any?>> ?: emptyList()

        // HLS manifest
        val hlsManifestUrl = streamingData["hlsManifestUrl"] as? String

        // Select best video stream (H.264/AVC, 720p preferred for TV compatibility)
        val bestVideo = selectBestVideo(adaptiveFormats)

        // Select best audio stream (AAC preferred)
        val bestAudio = selectBestAudio(adaptiveFormats)

        // Select best progressive stream (muxed fallback)
        val bestProgressive = selectBestProgressive(formats)

        Log.d(TAG, "Client ${config.name}: HLS=${hlsManifestUrl != null}, video=${bestVideo != null}, audio=${bestAudio != null}, progressive=${bestProgressive != null}")
        if (hlsManifestUrl != null) {
            Log.d(TAG, "HLS URL: ${hlsManifestUrl.take(120)}...")
        }

        return YouTubeStreamResult(
            videoUrl = bestVideo,
            audioUrl = bestAudio,
            progressiveUrl = bestProgressive,
            hlsManifestUrl = hlsManifestUrl,
            durationMs = durationMs,
            userAgent = config.userAgent
        )
    }

    private fun buildRequestBody(videoId: String, config: ClientConfig): String {
        val parts = mutableListOf<String>()
        parts.add(""""clientName":"${config.clientName}"""")
        parts.add(""""clientVersion":"${config.clientVersion}"""")
        if (config.androidSdkVersion != null) {
            parts.add(""""androidSdkVersion":${config.androidSdkVersion}""")
        }
        if (config.deviceMake != null) {
            parts.add(""""deviceMake":"${config.deviceMake}"""")
        }
        if (config.deviceModel != null) {
            parts.add(""""deviceModel":"${config.deviceModel}"""")
        }
        if (config.osVersion != null) {
            parts.add(""""osVersion":"${config.osVersion}"""")
        }
        parts.add(""""hl":"en"""")
        parts.add(""""gl":"US"""")

        val clientJson = parts.joinToString(",")

        // For embedded clients, include thirdParty context with embed URL
        val thirdParty = if (config.isEmbedded) {
            ""","thirdParty":{"embedUrl":"https://www.youtube.com"}"""
        } else ""

        return """{"context":{"client":{$clientJson}$thirdParty},"videoId":"$videoId","playbackContext":{"contentPlaybackContext":{"html5Preference":"HTML5_PREF_WANTS"}},"contentCheckOk":true,"racyCheckOk":true}"""
    }

    private fun getClientId(clientName: String): String = when (clientName) {
        "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> "85"
        "ANDROID_VR" -> "28"
        "ANDROID" -> "3"
        "IOS" -> "5"
        else -> "3"
    }

    @Suppress("UNCHECKED_CAST")
    private fun selectBestVideo(adaptiveFormats: List<Map<String, Any?>>): String? {
        // Filter to video-only streams
        val videoStreams = adaptiveFormats.filter { format ->
            val mimeType = format["mimeType"] as? String ?: ""
            mimeType.startsWith("video/")
        }

        if (videoStreams.isEmpty()) return null

        // Prefer H.264/AVC in MP4 container, 720p for TV compatibility
        // Score: higher is better
        data class ScoredStream(val url: String, val score: Int)

        val scored = videoStreams.mapNotNull { format ->
            val url = format["url"] as? String ?: return@mapNotNull null
            val mimeType = format["mimeType"] as? String ?: ""
            val width = (format["width"] as? Number)?.toInt() ?: 0
            val height = (format["height"] as? Number)?.toInt() ?: 0
            val bitrate = (format["bitrate"] as? Number)?.toInt() ?: 0

            var score = 0

            // Codec preference: H.264 > VP9 > AV1
            when {
                mimeType.contains("avc1") -> score += 1000  // H.264 — best TV compatibility
                mimeType.contains("vp9") || mimeType.contains("vp09") -> score += 500
                mimeType.contains("av01") -> score += 100   // AV1 — poor on cheap TVs
            }

            // Container preference: MP4 > WebM
            when {
                mimeType.contains("video/mp4") -> score += 200
                mimeType.contains("video/webm") -> score += 50
            }

            // Resolution preference: 720p ideal, 1080p OK, avoid 4K (wastes bandwidth)
            when {
                height == 720 -> score += 300   // Ideal for TV
                height == 480 -> score += 250
                height == 1080 -> score += 200  // OK but heavier
                height == 360 -> score += 150
                height >= 1440 -> score += 50   // Too heavy for most TV boxes
                else -> score += 100
            }

            // Tiebreaker: higher bitrate
            score += (bitrate / 100_000).coerceAtMost(50)

            ScoredStream(url, score)
        }

        return scored.maxByOrNull { it.score }?.url
    }

    @Suppress("UNCHECKED_CAST")
    private fun selectBestAudio(adaptiveFormats: List<Map<String, Any?>>): String? {
        val audioStreams = adaptiveFormats.filter { format ->
            val mimeType = format["mimeType"] as? String ?: ""
            mimeType.startsWith("audio/")
        }

        if (audioStreams.isEmpty()) return null

        data class ScoredStream(val url: String, val score: Int)

        val scored = audioStreams.mapNotNull { format ->
            val url = format["url"] as? String ?: return@mapNotNull null
            val mimeType = format["mimeType"] as? String ?: ""
            val bitrate = (format["bitrate"] as? Number)?.toInt() ?: 0

            var score = 0

            // Prefer AAC (mp4a) — universal hardware decode on Android TV
            when {
                mimeType.contains("mp4a") -> score += 500
                mimeType.contains("opus") -> score += 200
                mimeType.contains("vorbis") -> score += 100
            }

            // Container preference
            when {
                mimeType.contains("audio/mp4") -> score += 100
                mimeType.contains("audio/webm") -> score += 50
            }

            // Prefer medium bitrate (128kbps sweet spot for trailers)
            score += when {
                bitrate in 100_000..200_000 -> 100
                bitrate in 50_000..100_000 -> 80
                bitrate > 200_000 -> 60
                else -> 40
            }

            ScoredStream(url, score)
        }

        return scored.maxByOrNull { it.score }?.url
    }

    @Suppress("UNCHECKED_CAST")
    private fun selectBestProgressive(formats: List<Map<String, Any?>>): String? {
        // Progressive = muxed video+audio, usually lower quality but simpler
        val scored = formats.mapNotNull { format ->
            val url = format["url"] as? String ?: return@mapNotNull null
            val height = (format["height"] as? Number)?.toInt() ?: 0
            val mimeType = format["mimeType"] as? String ?: ""

            var score = 0

            // Prefer MP4
            if (mimeType.contains("video/mp4")) score += 200

            // Prefer 720p
            when {
                height == 720 -> score += 300
                height == 360 -> score += 200
                height == 480 -> score += 250
                else -> score += 100
            }

            Pair(url, score)
        }

        return scored.maxByOrNull { it.second }?.first
    }
}
