package com.merlottv.kotlin.data.parser

import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XmltvParser @Inject constructor() {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parse(xmlContent: String): Pair<List<EpgChannel>, List<EpgEntry>> {
        val channels = mutableListOf<EpgChannel>()
        val programs = mutableListOf<EpgEntry>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var currentChannelId = ""
            var currentChannelName = ""
            var currentChannelIcon = ""
            var currentTitle = ""
            var currentDesc = ""
            var currentCategory = ""
            var currentStart = 0L
            var currentEnd = 0L
            var currentIcon = ""
            var inChannel = false
            var inProgramme = false
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (parser.name) {
                            "channel" -> {
                                inChannel = true
                                currentChannelId = parser.getAttributeValue(null, "id") ?: ""
                                currentChannelName = ""
                                currentChannelIcon = ""
                            }
                            "programme" -> {
                                inProgramme = true
                                currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                                val startStr = parser.getAttributeValue(null, "start") ?: ""
                                val endStr = parser.getAttributeValue(null, "stop") ?: ""
                                currentStart = parseDate(startStr)
                                currentEnd = parseDate(endStr)
                                currentTitle = ""
                                currentDesc = ""
                                currentCategory = ""
                                currentIcon = ""
                            }
                            "icon" -> {
                                val src = parser.getAttributeValue(null, "src") ?: ""
                                if (inChannel) currentChannelIcon = src
                                else if (inProgramme) currentIcon = src
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            when {
                                inChannel && currentTag == "display-name" -> currentChannelName = text
                                inProgramme && currentTag == "title" -> currentTitle = text
                                inProgramme && currentTag == "desc" -> currentDesc = text
                                inProgramme && currentTag == "category" -> currentCategory = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                inChannel = false
                                channels.add(EpgChannel(currentChannelId, currentChannelName, currentChannelIcon))
                            }
                            "programme" -> {
                                inProgramme = false
                                programs.add(
                                    EpgEntry(
                                        channelId = currentChannelId,
                                        title = currentTitle,
                                        description = currentDesc,
                                        startTime = currentStart,
                                        endTime = currentEnd,
                                        category = currentCategory,
                                        icon = currentIcon
                                    )
                                )
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(channels, programs)
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
