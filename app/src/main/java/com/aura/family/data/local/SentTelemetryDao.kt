package com.aura.family.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SentTelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SentTelemetryEntity): Long

    @Query("SELECT * FROM sent_telemetry_log ORDER BY sentAtMs DESC LIMIT 10")
    fun observeLastTen(): Flow<List<SentTelemetryEntity>>

    @Query(
        "DELETE FROM sent_telemetry_log WHERE id NOT IN " +
            "(SELECT id FROM sent_telemetry_log ORDER BY sentAtMs DESC LIMIT 10)"
    )
    suspend fun pruneOlderThanTen()

    @Query("DELETE FROM sent_telemetry_log")
    suspend fun clearAll()
}
