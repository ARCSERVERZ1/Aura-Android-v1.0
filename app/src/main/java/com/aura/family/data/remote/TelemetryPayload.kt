package com.aura.family.data.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import org.json.JSONObject

@Keep
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
) {
    fun toJsonString(): String {
        val safeRemarks = remarks.trim().ifBlank { "LocationCheckIn | $networkType" }
        return JSONObject().apply {
            put("username", username)
            put("device_model", deviceModel)
            put("latitude", latitude)
            put("longitude", longitude)
            put("is_charging", isCharging)
            put("battery_level", batteryLevel)
            put("signal_strength", signalStrength)
            put("network_type", networkType)
            put("os_version", osVersion)
            put("locale", locale)
            put("uptime_ms", uptimeMs)
            put("screen_brightness", screenBrightness)
            put("remarks", safeRemarks)
            put("sos_flag", sosFlag)
            put("status", status)
            put("timestamp", timestamp)
        }.toString()
    }
}
