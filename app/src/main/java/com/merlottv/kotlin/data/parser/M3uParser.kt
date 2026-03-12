package com.merlottv.kotlin.data.parser

import com.merlottv.kotlin.domain.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uParser @Inject constructor() {

    // Pre-compiled regex — created ONCE at singleton init, reused for every parse
    private val groupTitleRegex = Regex("""group-title="([^"]*?)"""")
    private val tvgLogoRegex = Regex("""tvg-logo="([^"]*?)"""")
    private val tvgIdRegex = Regex("""tvg-id="([^"]*?)"""")
    private val tvgNameRegex = Regex("""tvg-name="([^"]*?)"""")

    /**
     * Stream-based parsing — reads line-by-line from InputStream.
     * Avoids loading entire 10-50MB M3U file into memory as a String.
     * This is the PRIMARY method — use this instead of parse(String).
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
                    // Store the EXTINF line, wait for the URL on the next non-comment line
                    pendingExtInf = line.trim()
                } else if (pendingExtInf != null && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    // This is the URL line following an EXTINF
                    channelNumber++
                    val extInf = pendingExtInf!!
                    val url = trimmed

                    val name = extractAfterComma(extInf)
                    val group = groupTitleRegex.find(extInf)?.groupValues?.getOrNull(1) ?: ""
                    val logo = tvgLogoRegex.find(extInf)?.groupValues?.getOrNull(1) ?: ""
                    val epgId = tvgIdRegex.find(extInf)?.groupValues?.getOrNull(1) ?: ""
                    val tvgName = tvgNameRegex.find(extInf)?.groupValues?.getOrNull(1) ?: ""

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
                    pendingExtInf = null
                } else if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                    // Comment or blank line — skip but don't clear pendingExtInf
                    // (there may be #EXTVLCOPT or similar between EXTINF and URL)
                } else {
                    // Non-URL, non-comment line with no pending EXTINF — skip
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
                val group = groupTitleRegex.find(fullTrimmed)?.groupValues?.getOrNull(1) ?: ""
                val logo = tvgLogoRegex.find(fullTrimmed)?.groupValues?.getOrNull(1) ?: ""
                val epgId = tvgIdRegex.find(fullTrimmed)?.groupValues?.getOrNull(1) ?: ""
                val tvgName = tvgNameRegex.find(fullTrimmed)?.groupValues?.getOrNull(1) ?: ""

                // Next non-empty, non-comment line is the URL
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

                if (url.isNotEmpty()) {
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

    private fun extractAfterComma(line: String): String {
        val commaIndex = line.lastIndexOf(',')
        return if (commaIndex >= 0 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else ""
    }
}
