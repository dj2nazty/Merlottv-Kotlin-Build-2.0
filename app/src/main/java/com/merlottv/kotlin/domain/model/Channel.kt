package com.merlottv.kotlin.domain.model

data class Channel(
    val id: String,
    val name: String,
    val group: String = "",
    val logoUrl: String = "",
    val streamUrl: String,
    val epgId: String = "",
    val number: Int = 0,
    val isFavorite: Boolean = false
)

data class ChannelGroup(
    val name: String,
    val channels: List<Channel>
)
