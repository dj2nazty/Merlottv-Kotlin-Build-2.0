package com.merlottv.kotlin.domain.model

data class YouTubeChannel(
    val channelId: String,
    val channelName: String,
    val handle: String
)

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val publishedDate: String = ""
)
