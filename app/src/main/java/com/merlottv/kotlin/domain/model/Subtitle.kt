package com.merlottv.kotlin.domain.model

data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val label: String   // e.g. "English", "Spanish"
)
