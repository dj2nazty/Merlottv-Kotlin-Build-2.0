package com.merlottv.kotlin.data.parser

import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XmltvParser @Inject constructor() {

    // Thread-local date formatters — SimpleDateFormat is NOT thread-safe
    // Using ThreadLocal avoids race conditions when parsing from multiple coroutines
    private val dateFormatLocal = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val dateFormatAltLocal = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    /**
     * Stream-based parsing - reads directly from InputStream to avoid loading
     * entire multi-MB XML files into memory as a String.
     */
    fun parseStream(inputStream: InputStream): Pair<List<EpgChannel>, List<EpgEntry>> {
        val channels = mutableListOf<EpgChannel>()
        val programs = mutableListOf<EpgEntry>()

        val now = System.currentTimeMillis()
        val windowStart = now - 6 * 3600_000L
        val windowEnd = now + 24 * 3600_000L

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

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
                        currentTag = parser.name ?: ""
                        when (currentTag) {
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
                                inProgramme && currentTag == "desc" -> currentDesc = text.take(300)
                                inProgramme && currentTag == "category" -> currentCategory = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                inChannel = false
                                if (currentChannelId.isNotEmpty()) {
                                    channels.add(EpgChannel(currentChannelId, currentChannelName, currentChannelIcon))
                                }
                            }
                            "programme" -> {
                                inProgramme = false
                                if (currentEnd >= windowStart && currentStart <= windowEnd && currentTitle.isNotEmpty()) {
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

    fun parse(xmlContent: String): Pair<List<EpgChannel>, List<EpgEntry>> {
        val channels = mutableListOf<EpgChannel>()
        val programs = mutableListOf<EpgEntry>()
        val now = System.currentTimeMillis()
        val windowStart = now - 6 * 3600_000L
        val windowEnd = now + 24 * 3600_000L

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
                        currentTag = parser.name ?: ""
                        when (currentTag) {
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
                                inProgramme && currentTag == "desc" -> currentDesc = text.take(300)
                                inProgramme && currentTag == "category" -> currentCategory = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                inChannel = false
                                if (currentChannelId.isNotEmpty()) {
                                    channels.add(EpgChannel(currentChannelId, currentChannelName, currentChannelIcon))
                                }
                            }
                            "programme" -> {
                                inProgramme = false
                                if (currentEnd >= windowStart && currentStart <= windowEnd && currentTitle.isNotEmpty()) {
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
        if (dateStr.isEmpty()) return 0L
        return try {
            dateFormatLocal.get()!!.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            try {
                dateFormatAltLocal.get()!!.parse(dateStr)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }
}
