package com.merlottv.kotlin.data.parser

import com.merlottv.kotlin.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uParser @Inject constructor() {

    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0
        var channelNumber = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXTINF:")) {
                channelNumber++
                val name = extractAfterComma(line)
                val group = extractAttribute(line, "group-title")
                val logo = extractAttribute(line, "tvg-logo")
                val epgId = extractAttribute(line, "tvg-id")
                val tvgName = extractAttribute(line, "tvg-name")

                // Next non-empty, non-comment line is the URL
                var url = ""
                var j = i + 1
                while (j < lines.size) {
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

    private fun extractAttribute(line: String, attr: String): String {
        val regex = Regex("""$attr="([^"]*?)"""")
        return regex.find(line)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun extractAfterComma(line: String): String {
        val commaIndex = line.lastIndexOf(',')
        return if (commaIndex >= 0 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else ""
    }
}
