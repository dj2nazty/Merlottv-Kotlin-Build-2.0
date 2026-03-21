package com.merlottv.kotlin.domain.model

/**
 * A live TV channel from the USA TV Stremio addon backup source.
 * Each channel has multiple stream URLs for redundancy (HD/SD from different providers).
 */
data class BackupTvChannel(
    val id: String,
    val name: String,
    val genre: String,
    val logoUrl: String,
    val posterUrl: String,
    val streams: List<BackupStream>
)

data class BackupStream(
    val url: String,
    val name: String,       // "HD", "SD"
    val description: String // Provider label like "AX", "CV", "MJ"
)
