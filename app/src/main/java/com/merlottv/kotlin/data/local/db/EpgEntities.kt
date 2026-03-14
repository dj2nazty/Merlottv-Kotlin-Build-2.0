package com.merlottv.kotlin.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry

@Entity(tableName = "epg_channels")
data class EpgChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["channelId", "startTime"]),
        Index(value = ["endTime"])
    ]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val category: String = "",
    val icon: String = ""
)

// Entity -> Domain mappers
fun EpgChannelEntity.toDomain(programs: List<EpgEntry> = emptyList()) =
    EpgChannel(id = id, name = name, icon = icon, programs = programs)

fun EpgProgramEntity.toDomain() =
    EpgEntry(
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
        icon = icon
    )

// Domain -> Entity mappers
fun EpgChannel.toEntity(lastUpdated: Long = System.currentTimeMillis()) =
    EpgChannelEntity(id = id, name = name, icon = icon, lastUpdated = lastUpdated)

fun EpgEntry.toEntity() =
    EpgProgramEntity(
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
        icon = icon
    )
