package com.merlottv.kotlin.data.parser

import com.merlottv.kotlin.domain.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uParser @Inject constructor() {

    /**
     * Stream-based parsing — reads line-by-line from InputStream.
     * Uses manual indexOf parsing instead of Regex to eliminate MatchResult allocations.
     * With ~5000 channels × 4 attributes, that's ~20K fewer objects created.
     */
    fun parseStream(inputStream: InputStream): List<Channel> {
        val channels = ArrayList<Channel>(2000)
        var channelNumber = 0
        var pendingExtInf: String? = null

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 64 * 1024).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                val trimmed = line.trimStart()

                if (trimmed.startsWith("#EXTINF:")) {
                    pendingExtInf = line.trim()
                } else if (pendingExtInf != null && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    channelNumber++
                    val extInf = pendingExtInf!!
                    val url = trimmed

                    val name = extractAfterComma(extInf)
                    val group = extractAttribute(extInf, "group-title=\"")
                    val logo = extractAttribute(extInf, "tvg-logo=\"")
                    val epgId = extractAttribute(extInf, "tvg-id=\"")
                    val tvgName = extractAttribute(extInf, "tvg-name=\"")

                    // Skip VOD content (movies/series) — only keep live TV channels
                    if (!isVodEntry(group, name, url)) {
                        channels.add(
                            Channel(
                                id = epgId.ifEmpty { "${group}_${name}".hashCode().toString() },
                                name = name,
                                group = group,
                                logoUrl = logo,
                                streamUrl = url,
                                epgId = epgId.ifEmpty { tvgName },
                                number = channelNumber
                            )
                        )
                    }
                    pendingExtInf = null
                } else if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                    // Comment or blank line — keep pendingExtInf
                } else {
                    pendingExtInf = null
                }

                line = reader.readLine()
            }
        }
        return channels
    }

    /**
     * Legacy String-based parsing — kept for backward compatibility.
     * Prefer parseStream() for large files to avoid OOM.
     */
    fun parse(content: String): List<Channel> {
        if (content.isBlank()) return emptyList()

        val channels = ArrayList<Channel>(2000)
        val lines = content.lines()
        val lineCount = lines.size
        var i = 0
        var channelNumber = 0

        while (i < lineCount) {
            val line = lines[i]
            val trimmed = line.trimStart()

            if (trimmed.startsWith("#EXTINF:")) {
                channelNumber++
                val fullTrimmed = line.trim()
                val name = extractAfterComma(fullTrimmed)
                val group = extractAttribute(fullTrimmed, "group-title=\"")
                val logo = extractAttribute(fullTrimmed, "tvg-logo=\"")
                val epgId = extractAttribute(fullTrimmed, "tvg-id=\"")
                val tvgName = extractAttribute(fullTrimmed, "tvg-name=\"")

                var url = ""
                var j = i + 1
                while (j < lineCount) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        url = nextLine
                        break
                    }
                    j++
                }

                if (url.isNotEmpty() && !isVodEntry(group, name, url)) {
                    channels.add(
                        Channel(
                            id = epgId.ifEmpty { "${group}_${name}".hashCode().toString() },
                            name = name,
                            group = group,
                            logoUrl = logo,
                            streamUrl = url,
                            epgId = epgId.ifEmpty { tvgName },
                            number = channelNumber
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
     * Detects VOD entries (movies/TV series) that should be excluded from Live TV.
     * Checks group title, channel name, and URL for common VOD patterns.
     */
    private fun isVodEntry(group: String, name: String, url: String): Boolean {
        val groupLower = group.lowercase()
        val nameLower = name.lowercase()
        val urlLower = url.lowercase()

        // Group-based VOD detection
        val vodGroupKeywords = arrayOf(
            "vod", "movie", "film", "series", "tv show", "tvshow",
            "on demand", "catch up", "catchup", "ppv", "pay per view",
            "cinema", "boxset", "box set", "episode"
        )
        for (keyword in vodGroupKeywords) {
            if (groupLower.contains(keyword)) return true
        }

        // URL-based VOD detection — file extensions typical of VOD content
        val vodExtensions = arrayOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm")
        for (ext in vodExtensions) {
            if (urlLower.endsWith(ext) || urlLower.contains("$ext?") || urlLower.contains("$ext&")) return true
        }

        // URL path patterns common in IPTV providers for VOD
        if (urlLower.contains("/movie/") || urlLower.contains("/series/") ||
            urlLower.contains("/vod/") || urlLower.contains("/films/")) return true

        // Name patterns — "S01 E01", "(2024)", year patterns typical of VOD titles
        if (nameLower.matches(Regex(".*s\\d{1,2}\\s*e\\d{1,2}.*"))) return true
        if (nameLower.matches(Regex(".*\\(\\d{4}\\).*")) && !groupLower.contains("live") && !groupLower.contains("channel")) return true

        return false
    }

    /**
     * Zero-allocation attribute extraction using indexOf instead of Regex.
     * Finds: key="value" and returns value.
     * Eliminates MatchResult + groupValues allocations per attribute per channel.
     */
    private fun extractAttribute(line: String, prefix: String): String {
        val start = line.indexOf(prefix)
        if (start < 0) return ""
        val valueStart = start + prefix.length
        val end = line.indexOf('"', valueStart)
        if (end < 0) return ""
        return line.substring(valueStart, end)
    }

    private fun extractAfterComma(line: String): String {
        val commaIndex = line.lastIndexOf(',')
        return if (commaIndex >= 0 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else ""
    }
}
