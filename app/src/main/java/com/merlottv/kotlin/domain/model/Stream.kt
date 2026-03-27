package com.merlottv.kotlin.domain.model

data class Stream(
    val name: String = "",
    val title: String = "",
    val url: String = "",
    val ytId: String = "",
    val infoHash: String = "",
    val fileIdx: Int? = null,
    val externalUrl: String = "",
    val addonName: String = "",
    val addonLogo: String = ""
) {
    /** Extract language from stream name/title/description text */
    val language: String
        get() {
            val text = "$name $title"
            val textLower = text.lowercase()

            // 1. Check for explicit "Language: XYZ" line (TorBox format)
            val langLineMatch = Regex("""Language:\s*(\w[\w\s]*)""", RegexOption.IGNORE_CASE).find(text)
            if (langLineMatch != null) {
                val lang = langLineMatch.groupValues[1].trim()
                if (lang.equals("UNKNOWN", ignoreCase = true).not() && lang.isNotEmpty()) {
                    return lang.replaceFirstChar { it.uppercase() }
                }
            }

            // 2. Check filename patterns like "Rus.Eng" or "English.DDP"
            if (textLower.contains(".eng.") || textLower.contains(".english.") ||
                Regex("""[\.\s]eng[\.\s\]]""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                // Check if multi-language
                val hasRus = textLower.contains(".rus.") || textLower.contains("russian")
                return if (hasRus) "Multi (Eng/Rus)" else "English"
            }

            // 3. Check for multi/dual audio
            if (textLower.contains("multi") && (textLower.contains("audio") || textLower.contains("lang"))) return "Multi"
            if (textLower.contains("dual") && textLower.contains("audio")) return "Dual Audio"

            // 4. Flag emojis
            if (text.contains("\uD83C\uDDFA\uD83C\uDDF8") || text.contains("\uD83C\uDDEC\uD83C\uDDE7")) return "English"
            if (text.contains("\uD83C\uDDEA\uD83C\uDDF8")) return "Spanish"
            if (text.contains("\uD83C\uDDEB\uD83C\uDDF7")) return "French"
            if (text.contains("\uD83C\uDDE9\uD83C\uDDEA")) return "German"

            // 5. Explicit language words
            return when {
                textLower.contains("english") || Regex("""\beng\b""").containsMatchIn(textLower) -> "English"
                textLower.contains("spanish") || textLower.contains("español") || textLower.contains("latino") -> "Spanish"
                textLower.contains("french") || textLower.contains("français") -> "French"
                textLower.contains("german") || textLower.contains("deutsch") -> "German"
                textLower.contains("italian") || textLower.contains("italiano") -> "Italian"
                textLower.contains("portuguese") || textLower.contains("português") -> "Portuguese"
                textLower.contains("russian") || Regex("""\brus\b""").containsMatchIn(textLower) -> "Russian"
                textLower.contains("japanese") -> "Japanese"
                textLower.contains("korean") -> "Korean"
                textLower.contains("chinese") -> "Chinese"
                textLower.contains("hindi") -> "Hindi"
                textLower.contains("arabic") -> "Arabic"
                textLower.contains("turkish") -> "Turkish"
                textLower.contains("polish") -> "Polish"
                textLower.contains("dutch") -> "Dutch"
                textLower.contains("swedish") -> "Swedish"
                else -> ""
            }
        }

    /** Extract quality from stream name/title text */
    val quality: String
        get() {
            val text = "$name $title".lowercase()
            return when {
                text.contains("2160") || text.contains("4k") || text.contains("uhd") -> "4K"
                text.contains("1080") -> "1080p"
                text.contains("720") -> "720p"
                text.contains("480") -> "480p"
                text.contains("360") -> "360p"
                text.contains("remux") -> "REMUX"
                text.contains("bluray") || text.contains("blu-ray") -> "BluRay"
                text.contains("web-dl") || text.contains("webdl") -> "WEB-DL"
                text.contains("webrip") -> "WEBRip"
                text.contains("hdtv") -> "HDTV"
                text.contains("cam") || text.contains("hdcam") -> "CAM"
                else -> ""
            }
        }

    /** Extract file size from description (TorBox format: "Size: 2GB") */
    val fileSize: String
        get() {
            val match = Regex("""Size:\s*([\d.]+\s*[KMGT]?B)""", RegexOption.IGNORE_CASE).find(title)
            return match?.groupValues?.get(1) ?: ""
        }

    /** Whether this stream is likely English */
    val isLikelyEnglish: Boolean
        get() {
            val lang = language
            if (lang == "English" || lang.startsWith("Multi") || lang == "Dual Audio") return true
            if (lang.isNotEmpty()) return false // Detected a non-English language
            // No language detected — check for non-English indicators
            val text = "$name $title".lowercase()
            val nonEnglishIndicators = listOf(
                "latino", "castellano", "dublado",
                "\uD83C\uDDEA\uD83C\uDDF8", "\uD83C\uDDEB\uD83C\uDDF7",
                "\uD83C\uDDE9\uD83C\uDDEA", "\uD83C\uDDEE\uD83C\uDDF9",
                "\uD83C\uDDF7\uD83C\uDDFA", "\uD83C\uDDEF\uD83C\uDDF5",
                "\uD83C\uDDF0\uD83C\uDDF7", "\uD83C\uDDE8\uD83C\uDDF3"
            )
            return nonEnglishIndicators.none { text.contains(it) }
        }
}
