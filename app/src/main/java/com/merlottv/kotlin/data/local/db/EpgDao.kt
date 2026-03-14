package com.merlottv.kotlin.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {

    // -- Channels --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Query("SELECT * FROM epg_channels ORDER BY name COLLATE NOCASE")
    fun getAllChannels(): Flow<List<EpgChannelEntity>>

    @Query("SELECT lastUpdated FROM epg_channels ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getLastUpdateTime(): Long?

    // -- Programs --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("""
        SELECT * FROM epg_programs
        WHERE endTime > :windowStart AND startTime < :windowEnd
        ORDER BY channelId, startTime
    """)
    fun getAllProgramsInWindow(windowStart: Long, windowEnd: Long): Flow<List<EpgProgramEntity>>

    @Query("""
        SELECT * FROM epg_programs
        WHERE LOWER(channelId) = LOWER(:channelId)
          AND startTime <= :now AND endTime >= :now
        LIMIT 1
    """)
    suspend fun getCurrentProgram(channelId: String, now: Long): EpgProgramEntity?

    @Query("""
        SELECT * FROM epg_programs
        WHERE LOWER(channelId) = LOWER(:channelId)
          AND endTime > :windowStart AND startTime < :windowEnd
        ORDER BY startTime
    """)
    fun getProgramsForChannel(
        channelId: String,
        windowStart: Long,
        windowEnd: Long
    ): Flow<List<EpgProgramEntity>>

    // -- Cleanup --
    @Query("DELETE FROM epg_programs WHERE endTime < :cutoff")
    suspend fun deleteExpiredPrograms(cutoff: Long)

    @Query("DELETE FROM epg_programs")
    suspend fun deleteAllPrograms()

    @Query("DELETE FROM epg_channels")
    suspend fun deleteAllChannels()

    @Transaction
    suspend fun replaceAll(channels: List<EpgChannelEntity>, programs: List<EpgProgramEntity>) {
        deleteAllPrograms()
        deleteAllChannels()
        insertChannels(channels)
        // Insert in batches of 500 to stay under SQLite variable limit
        programs.chunked(500).forEach { batch ->
            insertPrograms(batch)
        }
    }
}
