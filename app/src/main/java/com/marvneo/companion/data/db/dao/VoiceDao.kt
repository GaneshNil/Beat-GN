package com.marvneo.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marvneo.companion.data.db.entity.VoiceMessageRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceDao {
    @Query("SELECT * FROM voice_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<VoiceMessageRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(record: VoiceMessageRecord): Long

    @Query("DELETE FROM voice_messages")
    suspend fun clearHistory()
}

