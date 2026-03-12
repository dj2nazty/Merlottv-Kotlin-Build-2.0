package com.merlottv.kotlin.domain.model

data class EpgEntry(
    val channelId: String,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val category: String = "",
    val icon: String = ""
)

data class EpgChannel(
    val id: String,
    val name: String,
    val icon: String = "",
    val programs: List<EpgEntry> = emptyList()
)
