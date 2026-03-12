package com.merlottv.kotlin.data.parser

import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XmltvParser @Inject constructor() {

    // Thread-local Calendar for date calculations — avoids SimpleDateFormat entirely.
    // Manual parsing of "yyyyMMddHHmmss +HHMM" is ~10x faster than SimpleDateFormat.parse()
    // and avoids creating Date objects, ParsePosition, etc.
    private val calendarLocal = ThreadLocal.withInitial {
        Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }

    /**
     * Stream-based parsing - reads directly from InputStream to avoid loading
     * entire multi-MB XML files into memory as a String.
     */
    fun parseStream(inputStream: InputStream): Pair<List<EpgChannel>, List<EpgEntry>> {
        val channels = ArrayList<EpgChannel>(500)
        val programs = ArrayList<EpgEntry>(10000)

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
                                // Early skip: if end is before window or start is after window, skip parsing children
                                if (currentEnd > 0 && currentEnd < windowStart) {
                                    inProgramme = false // will skip text content gathering
                                }
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
                        if (inChannel || inProgramme) {
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
                                if (inProgramme && currentEnd >= windowStart && currentStart <= windowEnd && currentTitle.isNotEmpty()) {
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
                                inProgramme = false
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
        val channels = ArrayList<EpgChannel>(500)
        val programs = ArrayList<EpgEntry>(10000)
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

    /**
     * Manual date parsing for XMLTV format: "20260312143000 +0000" or "20260312143000+0000"
     * ~10x faster than SimpleDateFormat.parse() — avoids Date/ParsePosition/Calendar allocations.
     * Format is always: YYYY MM DD HH mm ss [space] +/-HHMM
     */
    private fun parseDate(dateStr: String): Long {
        if (dateStr.length < 14) return 0L
        return try {
            val year = dateStr.substring(0, 4).toInt()
            val month = dateStr.substring(4, 6).toInt() - 1 // Calendar months are 0-based
            val day = dateStr.substring(6, 8).toInt()
            val hour = dateStr.substring(8, 10).toInt()
            val minute = dateStr.substring(10, 12).toInt()
            val second = dateStr.substring(12, 14).toInt()

            // Parse timezone offset if present
            var tzOffsetMs = 0
            val remaining = dateStr.substring(14).trim()
            if (remaining.isNotEmpty()) {
                val sign = if (remaining[0] == '-') -1 else 1
                val tzStr = remaining.removePrefix("+").removePrefix("-")
                if (tzStr.length >= 4) {
                    val tzHour = tzStr.substring(0, 2).toInt()
                    val tzMin = tzStr.substring(2, 4).toInt()
                    tzOffsetMs = sign * (tzHour * 3600_000 + tzMin * 60_000)
                }
            }

            val cal = calendarLocal.get()!!
            cal.clear()
            cal.timeZone = TimeZone.getTimeZone("UTC")
            cal.set(year, month, day, hour, minute, second)
            cal.timeInMillis - tzOffsetMs
        } catch (_: Exception) {
            0L
        }
    }
}
