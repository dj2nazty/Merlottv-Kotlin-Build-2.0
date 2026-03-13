package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.domain.model.DefaultData
import com.merlottv.kotlin.domain.model.Subtitle
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches subtitles from the OpenSubtitles Stremio addon.
 * Endpoint: {OPENSUBTITLES_ADDON_URL}/subtitles/{type}/{id}.json
 * Returns JSON: { "subtitles": [{ "id": "...", "url": "...", "lang": "eng", "SubEncoding": "UTF-8" }] }
 */
@Singleton
class SubtitleRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    private val fastClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetch subtitles for a given content type and ID.
     * For series episodes, use the video ID (e.g., "tt1234567:1:3" for S1E3).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getSubtitles(type: String, id: String): List<Subtitle> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${DefaultData.OPENSUBTITLES_ADDON_URL}/subtitles/$type/$id.json"
                val request = Request.Builder().url(url).build()
                val response = fastClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string() ?: return@withContext emptyList()
                val adapter = moshi.adapter(Map::class.java)
                val map = adapter.fromJson(body) as? Map<String, Any?> ?: return@withContext emptyList()
                val subtitles = map["subtitles"] as? List<Map<String, Any?>> ?: return@withContext emptyList()

                subtitles.mapNotNull { sub ->
                    val subUrl = sub["url"] as? String ?: return@mapNotNull null
                    val lang = sub["lang"] as? String ?: "eng"
                    val subId = sub["id"] as? String ?: subUrl.hashCode().toString()

                    Subtitle(
                        id = subId,
                        url = subUrl,
                        lang = lang,
                        label = getLanguageLabel(lang)
                    )
                }.distinctBy { "${it.lang}_${it.url}" }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Get available languages from a subtitle list (deduplicated).
     */
    fun getAvailableLanguages(subtitles: List<Subtitle>): List<String> {
        return subtitles.map { it.lang }.distinct().sorted()
    }

    companion object {
        /** Map common ISO 639-2/3 codes to human-readable names */
        fun getLanguageLabel(langCode: String): String {
            return when (langCode.lowercase()) {
                "eng", "en" -> "English"
                "spa", "es" -> "Spanish"
                "fre", "fra", "fr" -> "French"
                "ger", "deu", "de" -> "German"
                "por", "pt" -> "Portuguese"
                "ita", "it" -> "Italian"
                "jpn", "ja" -> "Japanese"
                "kor", "ko" -> "Korean"
                "chi", "zho", "zh" -> "Chinese"
                "rus", "ru" -> "Russian"
                "ara", "ar" -> "Arabic"
                "hin", "hi" -> "Hindi"
                "tur", "tr" -> "Turkish"
                "pol", "pl" -> "Polish"
                "dut", "nld", "nl" -> "Dutch"
                "swe", "sv" -> "Swedish"
                "nor", "no" -> "Norwegian"
                "dan", "da" -> "Danish"
                "fin", "fi" -> "Finnish"
                "tha", "th" -> "Thai"
                "vie", "vi" -> "Vietnamese"
                "ind", "id" -> "Indonesian"
                "rum", "ron", "ro" -> "Romanian"
                "heb", "he" -> "Hebrew"
                "gre", "ell", "el" -> "Greek"
                "cze", "ces", "cs" -> "Czech"
                "hun", "hu" -> "Hungarian"
                "bul", "bg" -> "Bulgarian"
                "hrv", "hr" -> "Croatian"
                "srp", "sr" -> "Serbian"
                "ukr", "uk" -> "Ukrainian"
                "may", "msa", "ms" -> "Malay"
                else -> langCode.uppercase()
            }
        }
    }
}
