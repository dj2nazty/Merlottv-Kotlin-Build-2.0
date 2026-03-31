package com.merlottv.kotlin.data.repository

import android.util.Log
import com.merlottv.kotlin.domain.model.BackupStream
import com.merlottv.kotlin.domain.model.BackupTvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches free live TV channels from TV Pass (tvpass.org).
 * Parses M3U playlist for channels and XMLTV EPG for current program info.
 *
 * Uses a 30-minute in-memory cache to avoid re-fetching.
 */
@Singleton
class TvPassRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "TvPass"
        private const val PLAYLIST_URL = "https://tvpass.org/playlist.m3u"
        private const val EPG_URL = "https://tvpass.org/epg.xml"
        private const val LOGO_BASE = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states/"
        private const val CACHE_TTL = 30 * 60 * 1000L // 30 minutes

        /**
         * Maps common channel name patterns to their logo filenames in tv-logo/tv-logos repo.
         * Key: lowercase channel keyword/prefix. Value: logo filename (without -us.png suffix).
         */
        private val LOGO_MAP = mapOf(
            "a&e" to "a-and-e",
            "abc" to "abc",
            "acc network" to "acc-network",
            "amc" to "amc",
            "american heroes" to "american-heroes-channel",
            "animal planet" to "animal-planet",
            "bbc america" to "bbc-america",
            "bbc news" to "bbc-news-north-america-hd",
            "bet" to "bet",
            "big ten" to "big-ten-network",
            "bloomberg" to "bloomberg-tv",
            "boomerang" to "boomerang",
            "bravo" to "bravo",
            "c-span" to "c-span",
            "c-span 2" to "c-span-2",
            "cartoon network" to "cartoon-network",
            "cbs" to "cbs",
            "cbs sports" to "cbs-sports-network",
            "cmt" to "cmt",
            "cnbc" to "cnbc",
            "cnn" to "cnn",
            "comedy central" to "comedy-central",
            "cooking channel" to "cooking-channel",
            "court tv" to "court-tv",
            "cw" to "the-cw",
            "discovery" to "discovery-channel",
            "disney" to "disney-channel",
            "disney junior" to "disney-junior",
            "disney xd" to "disney-xd",
            "e!" to "e",
            "espn" to "espn",
            "espn2" to "espn-2",
            "espn news" to "espn-news",
            "espnu" to "espn-u",
            "food network" to "food-network",
            "fox" to "fox",
            "fox business" to "fox-business",
            "fox news" to "fox-news",
            "fox sports" to "fox-sports-1",
            "free speech" to "free-speech-tv",
            "freeform" to "freeform",
            "fs1" to "fox-sports-1",
            "fs2" to "fox-sports-2",
            "fx" to "fx",
            "fxx" to "fxx",
            "golf channel" to "golf-channel",
            "hallmark" to "hallmark-channel",
            "hgtv" to "hgtv",
            "history" to "history",
            "hln" to "hln",
            "investigation discovery" to "investigation-discovery",
            "ion" to "ion-television",
            "lifetime" to "lifetime",
            "logo" to "logo-tv",
            "mlb network" to "mlb-network",
            "motortrend" to "motortrend",
            "msnbc" to "msnbc",
            "mtv" to "mtv",
            "nba tv" to "nba-tv",
            "nbc" to "nbc",
            "nbc sports" to "nbc-sports",
            "newsmax" to "newsmax-tv",
            "newsnation" to "newsnation",
            "nfl network" to "nfl-network",
            "nhl network" to "nhl-network",
            "nickelodeon" to "nickelodeon",
            "nick jr" to "nick-jr",
            "oprah" to "own",
            "own" to "own",
            "oxygen" to "oxygen",
            "paramount" to "paramount-network",
            "pbs" to "pbs",
            "pop" to "pop",
            "reelz" to "reelz",
            "science channel" to "science-channel",
            "sec network" to "sec-network",
            "showtime" to "showtime",
            "starz" to "starz",
            "sundance" to "sundance-tv",
            "syfy" to "syfy",
            "tbs" to "tbs",
            "tcm" to "turner-classic-movies",
            "telemundo" to "telemundo",
            "tennis channel" to "tennis-channel",
            "tlc" to "tlc",
            "tnt" to "tnt",
            "travel channel" to "travel-channel",
            "trutv" to "tru-tv",
            "tv land" to "tv-land",
            "tvone" to "tv-one",
            "univision" to "univision",
            "usa network" to "usa-network",
            "vh1" to "vh1",
            "vice" to "vice-tv",
            "we tv" to "we-tv",
            "weather channel" to "the-weather-channel",
        )
    }

    @Volatile
    private var cachedChannels: List<BackupTvChannel> = emptyList()
    @Volatile
    private var cacheTimestamp: Long = 0L

    /**
     * Returns all channels from TV Pass, using cache if fresh.
     */
    suspend fun getChannels(forceRefresh: Boolean = false): List<BackupTvChannel> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedChannels.isNotEmpty() && (now - cacheTimestamp) < CACHE_TTL) {
            Log.d(TAG, "Cache hit: ${cachedChannels.size} channels")
            return cachedChannels
        }

        return withContext(Dispatchers.IO) {
            try {
                // Fetch M3U playlist
                val m3uBody = fetchUrl(PLAYLIST_URL) ?: return@withContext cachedChannels
                val channels = parseM3u(m3uBody)
                Log.d(TAG, "Parsed ${channels.size} channels from M3U")

                if (channels.isEmpty()) return@withContext cachedChannels

                // Fetch EPG and merge current program info
                val enriched = try {
                    val epgBody = fetchUrl(EPG_URL)
                    if (epgBody != null) {
                        val currentPrograms = parseEpgCurrentPrograms(epgBody)
                        Log.d(TAG, "Parsed ${currentPrograms.size} current programs from EPG")
                        mergeEpgIntoChannels(channels, currentPrograms)
                    } else {
                        Log.w(TAG, "EPG fetch failed, using channels without program info")
                        channels
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "EPG parse failed: ${e.message}, using channels without program info")
                    channels
                }

                cachedChannels = enriched
                cacheTimestamp = System.currentTimeMillis()
                enriched
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch TV Pass: ${e.message}")
                cachedChannels // Return stale cache on error
            }
        }
    }

    private fun fetchUrl(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code} from $url")
                return null
            }
            response.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "Fetch error for $url: ${e.message}")
            null
        }
    }

    /**
     * Parses M3U playlist into BackupTvChannel list.
     *
     * Format:
     * #EXTINF:-1 tvg-id="ABCNEWS" tvg-name="ABC News Live" tvg-logo="https://..." group-title="News",ABC News Live
     * https://tvpass.org/live/ABCNEWS/sd
     */
    private fun parseM3u(body: String): List<BackupTvChannel> {
        val channels = mutableListOf<BackupTvChannel>()
        val lines = body.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                // Parse EXTINF line
                val tvgId = extractAttribute(line, "tvg-id") ?: ""
                val tvgName = extractAttribute(line, "tvg-name") ?: ""
                val tvgLogo = extractAttribute(line, "tvg-logo") ?: ""
                val groupTitle = extractAttribute(line, "group-title") ?: "Other"

                // Display name is after the last comma in EXTINF line
                val displayName = line.substringAfterLast(",", "").trim().ifEmpty { tvgName }

                // Next non-empty line should be the stream URL
                var streamUrl = ""
                var j = i + 1
                while (j < lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        streamUrl = nextLine
                        break
                    }
                    j++
                }

                if (streamUrl.isNotEmpty() && displayName.isNotEmpty()) {
                    val id = tvgId.ifEmpty { displayName.replace(" ", "_").uppercase() }
                    val logoUrl = tvgLogo.ifEmpty { buildLogoUrl(displayName) }
                    channels.add(
                        BackupTvChannel(
                            id = id,
                            name = displayName,
                            genre = groupTitle,
                            logoUrl = logoUrl,
                            posterUrl = logoUrl,
                            streams = listOf(
                                BackupStream(
                                    url = streamUrl,
                                    name = "SD",
                                    description = "" // Will be filled with current program from EPG
                                )
                            )
                        )
                    )
                    i = j + 1
                    continue
                }
            }
            i++
        }

        return channels
    }

    /**
     * Builds a logo URL from the channel display name using the tv-logo/tv-logos GitHub repo.
     * Tries exact match first, then longest prefix match from LOGO_MAP.
     */
    private fun buildLogoUrl(displayName: String): String {
        val lower = displayName.lowercase().trim()

        // Try longest match first (more specific matches win)
        val match = LOGO_MAP.entries
            .filter { (key, _) -> lower.startsWith(key) || lower.contains(key) }
            .maxByOrNull { it.key.length }

        return if (match != null) {
            "${LOGO_BASE}${match.value}-us.png"
        } else {
            // Fallback: convert channel name to slug format
            val slug = lower
                .replace("&", "and")
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .trim()
                .replace(Regex("\\s+"), "-")
            "${LOGO_BASE}${slug}-us.png"
        }
    }

    /**
     * Extracts an attribute value from an EXTINF line.
     * e.g., extractAttribute(line, "tvg-id") returns the value of tvg-id="value"
     */
    private fun extractAttribute(line: String, attr: String): String? {
        val pattern = """$attr="([^"]*)"""".toRegex()
        return pattern.find(line)?.groupValues?.get(1)
    }

    /**
     * Parses XMLTV EPG and returns a map of channel ID → current program title.
     * Only keeps programs that are currently airing (start ≤ now < stop).
     */
    private fun parseEpgCurrentPrograms(xml: String): Map<String, String> {
        val programs = mutableMapOf<String, String>()
        val now = System.currentTimeMillis()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            var eventType = parser.eventType
            var currentChannel: String? = null
            var currentStart: Long = 0
            var currentStop: Long = 0
            var currentTitle: String? = null
            var inTitle = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                currentChannel = parser.getAttributeValue(null, "channel")
                                val startStr = parser.getAttributeValue(null, "start")
                                val stopStr = parser.getAttributeValue(null, "stop")
                                currentStart = parseEpgDate(dateFormat, startStr)
                                currentStop = parseEpgDate(dateFormat, stopStr)
                                currentTitle = null
                            }
                            "title" -> {
                                inTitle = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inTitle && currentTitle == null) {
                            currentTitle = parser.text?.trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "title" -> inTitle = false
                            "programme" -> {
                                // Check if this program is currently airing
                                if (currentChannel != null && currentTitle != null &&
                                    currentStart <= now && now < currentStop
                                ) {
                                    programs[currentChannel] = currentTitle
                                }
                                currentChannel = null
                                currentTitle = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "EPG parse error: ${e.message}")
        }

        return programs
    }

    private fun parseEpgDate(format: SimpleDateFormat, dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            format.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Merges current program titles from EPG into channel list.
     * Program title goes into BackupStream.description for display.
     */
    private fun mergeEpgIntoChannels(
        channels: List<BackupTvChannel>,
        currentPrograms: Map<String, String>
    ): List<BackupTvChannel> {
        return channels.map { channel ->
            val program = currentPrograms[channel.id]
            if (program != null) {
                channel.copy(
                    streams = channel.streams.map { stream ->
                        stream.copy(description = program)
                    }
                )
            } else {
                channel
            }
        }
    }
}
