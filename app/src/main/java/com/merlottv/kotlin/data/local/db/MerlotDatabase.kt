package com.merlottv.kotlin.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EpgChannelEntity::class, EpgProgramEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MerlotDatabase : RoomDatabase() {
    abstract fun epgDao(): EpgDao
}
