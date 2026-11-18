package com.aura.family.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingTelemetryEntity): Long

    @Query("SELECT * FROM pending_telemetry ORDER BY createdAtMs ASC")
    suspend fun getAllPending(): List<PendingTelemetryEntity>

    @Query("SELECT * FROM pending_telemetry ORDER BY createdAtMs ASC")
    fun observeAllPending(): Flow<List<PendingTelemetryEntity>>

    @Query("SELECT COUNT(*) FROM pending_telemetry")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM pending_telemetry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_telemetry")
    suspend fun clearAll()

    @Query("UPDATE pending_telemetry SET attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun incrementAttempt(id: Long)
}
