package com.aura.family.data.remote

import com.google.gson.annotations.SerializedName

data class TelemetryPayload(
    @SerializedName("username")
    val username: String,
    @SerializedName("device_model")
    val deviceModel: String,
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("is_charging")
    val isCharging: Boolean,
    @SerializedName("battery_level")
    val batteryLevel: Int,
    @SerializedName("signal_strength")
    val signalStrength: Int,
    @SerializedName("network_type")
    val networkType: String,
    @SerializedName("os_version")
    val osVersion: String,
    @SerializedName("locale")
    val locale: String,
    @SerializedName("uptime_ms")
    val uptimeMs: Long,
    @SerializedName("screen_brightness")
    val screenBrightness: Int,
    @SerializedName("remarks")
    val remarks: String,
    @SerializedName("sos_flag")
    val sosFlag: Boolean,
    @SerializedName("status")
    val status: Int,
    @SerializedName("timestamp")
    val timestamp: String
)
