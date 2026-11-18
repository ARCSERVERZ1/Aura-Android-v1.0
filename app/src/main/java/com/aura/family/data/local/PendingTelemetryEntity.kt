package com.aura.family.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_telemetry")
data class PendingTelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val endpointUrl: String,
    val payloadJson: String,
    val remarks: String,
    val createdAtMs: Long,
    val attemptCount: Int = 0
)
