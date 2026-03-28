package com.merlottv.kotlin.domain.model

data class YouTubeChannel(
    val channelId: String,
    val channelName: String,
    val handle: String,
    val avatarUrl: String = ""
)

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val publishedDate: String = "",
    val viewCount: String = "",       // e.g. "38K views"
    val publishedTimeText: String = "" // e.g. "3 days ago"
)
