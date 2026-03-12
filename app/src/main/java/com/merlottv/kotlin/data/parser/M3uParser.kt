package com.merlottv.kotlin.data.parser

import com.merlottv.kotlin.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uParser @Inject constructor() {

    // Pre-compiled regex — created ONCE at singleton init, reused for every parse
    // Previously these were compiled per-attribute per-channel (4 × thousands = catastrophic)
    private val groupTitleRegex = Regex("""group-title="([^"]*?)"""")
    private val tvgLogoRegex = Regex("""tvg-logo="([^"]*?)"""")
    private val tvgIdRegex = Regex("""tvg-id="([^"]*?)"""")
    private val tvgNameRegex = Regex("""tvg-name="([^"]*?)"""")

    fun parse(content: String): List<Channel> {
        if (content.isBlank()) return emptyList()

        val channels = ArrayList<Channel>(2000) // Pre-size for typical IPTV lists
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
