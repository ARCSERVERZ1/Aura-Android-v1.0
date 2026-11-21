package com.aura.family.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sent_telemetry_log")
data class SentTelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val endpointUrl: String,
    val remarks: String,
    val sosFlag: Boolean,
    val batteryLevel: Int,
    val networkType: String,
    val httpCode: Int,
    val sentAtMs: Long,
    val payloadJson: String
)
