package com.aura.family.telemetry

import android.content.Context
import com.aura.family.background.WorkScheduler
import com.aura.family.data.local.PendingTelemetryDao
import com.aura.family.data.local.PendingTelemetryEntity
import com.aura.family.data.local.SentTelemetryDao
import com.aura.family.data.local.SentTelemetryEntity
import com.aura.family.data.preferences.AuraPreferencesRepository
import com.aura.family.data.remote.NetworkModule
import com.aura.family.data.remote.TelemetryApiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

sealed class TelemetryDispatchResult {
    data class Sent(val endpointUrl: String, val timestamp: String) : TelemetryDispatchResult()
    data class QueuedOffline(val reason: String) : TelemetryDispatchResult()
    data object BlockedByPrivacyMode : TelemetryDispatchResult()
    data object MissingBaseUrl : TelemetryDispatchResult()
}

class TelemetryManager(
    private val context: Context,
    private val preferencesRepository: AuraPreferencesRepository,
    private val pendingTelemetryDao: PendingTelemetryDao,
    private val sentTelemetryDao: SentTelemetryDao,
    private val metricsCollector: DeviceMetricsCollector,
    private val apiService: TelemetryApiService
) {

    private val flushMutex = Mutex()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun triggerTelemetry(
        remarks: String,
        sosFlag: Boolean = false
    ): TelemetryDispatchResult {
        val settings = preferencesRepository.settingsFlow.first()

        // STRICT PRIVACY CHECK: Block ALL automated and manual location/telemetry triggers
        if (settings.isPrivacyModeEnabled) {
            return TelemetryDispatchResult.BlockedByPrivacyMode
        }

        val endpointUrl = NetworkModule.buildTelemetryEndpoint(settings.baseUrl)
            ?: return TelemetryDispatchResult.MissingBaseUrl

        val effectiveRemarks = remarks.trim().ifBlank { "LocationCheckIn | Telemetry" }
        val payload = metricsCollector.collectPayload(
            username = settings.userName,
            remarks = effectiveRemarks,
            sosFlag = sosFlag
        )
        val payloadJson = payload.toJsonString()

        return try {
            val body = payloadJson.toRequestBody(jsonMediaType)
            val response = apiService.postRawTelemetry(endpointUrl, body)
            if (response.isSuccessful) {
                recordSentLog(
                    endpointUrl = endpointUrl,
                    remarks = payload.remarks,
                    sosFlag = payload.sosFlag,
                    batteryLevel = payload.batteryLevel,
                    networkType = payload.networkType,
                    httpCode = response.code(),
                    payloadJson = payloadJson
                )
                preferencesRepository.updateLastTelemetrySummary(
                    "Sent (${payload.remarks}) at ${payload.timestamp}"
                )
                TelemetryDispatchResult.Sent(endpointUrl = endpointUrl, timestamp = payload.timestamp)
            } else {
                queueOffline(endpointUrl, payloadJson, payload.remarks)
                TelemetryDispatchResult.QueuedOffline("HTTP ${response.code()} — queued for retry")
            }
        } catch (ex: Exception) {
            queueOffline(endpointUrl, payloadJson, payload.remarks)
            TelemetryDispatchResult.QueuedOffline(
                ex.localizedMessage ?: "Network unavailable — queued for retry"
            )
        }
    }

    private suspend fun queueOffline(endpointUrl: String, payloadJson: String, remarks: String) {
        pendingTelemetryDao.insert(
            PendingTelemetryEntity(
                endpointUrl = endpointUrl,
                payloadJson = payloadJson,
                remarks = remarks,
                createdAtMs = System.currentTimeMillis()
            )
        )
        preferencesRepository.updateLastTelemetrySummary("Queued offline ($remarks)")
        WorkScheduler.schedulePendingSync(context)
    }

    suspend fun flushPendingQueue(): Boolean = flushMutex.withLock {
        val settings = preferencesRepository.settingsFlow.first()
        if (settings.isPrivacyModeEnabled) {
            return false
        }

        val pendingItems = pendingTelemetryDao.getAllPending()
        if (pendingItems.isEmpty()) return true

        var allSucceeded = true

        for (item in pendingItems) {
            val latestSettings = preferencesRepository.settingsFlow.first()
            if (latestSettings.isPrivacyModeEnabled) {
                return false
            }

            try {
                // Ensure legacy queued items never have null/blank remarks
                val sanitizedJson = ensureRemarksInJson(item.payloadJson, item.remarks)
                val body = sanitizedJson.toRequestBody(jsonMediaType)
                val response = apiService.postRawTelemetry(item.endpointUrl, body)
                if (response.isSuccessful) {
                    val parsed = runCatching { JSONObject(sanitizedJson) }.getOrNull()
                    recordSentLog(
                        endpointUrl = item.endpointUrl,
                        remarks = parsed?.optString("remarks", item.remarks).orEmpty()
                            .ifBlank { item.remarks.ifBlank { "QueuedSync" } },
                        sosFlag = parsed?.optBoolean("sos_flag", false) ?: false,
                        batteryLevel = parsed?.optInt("battery_level", 0) ?: 0,
                        networkType = parsed?.optString("network_type", "UNKNOWN").orEmpty(),
                        httpCode = response.code(),
                        payloadJson = sanitizedJson
                    )
                    pendingTelemetryDao.deleteById(item.id)
                    preferencesRepository.updateLastTelemetrySummary(
                        "Synced queued alert (${item.remarks})"
                    )
                } else {
                    pendingTelemetryDao.incrementAttempt(item.id)
                    allSucceeded = false
                }
            } catch (_: Exception) {
                pendingTelemetryDao.incrementAttempt(item.id)
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private suspend fun recordSentLog(
        endpointUrl: String,
        remarks: String,
        sosFlag: Boolean,
        batteryLevel: Int,
        networkType: String,
        httpCode: Int,
        payloadJson: String
    ) {
        sentTelemetryDao.insert(
            SentTelemetryEntity(
                endpointUrl = endpointUrl,
                remarks = remarks,
                sosFlag = sosFlag,
                batteryLevel = batteryLevel,
                networkType = networkType,
                httpCode = httpCode,
                sentAtMs = System.currentTimeMillis(),
                payloadJson = payloadJson
            )
        )
        sentTelemetryDao.pruneOlderThanTen()
    }

    private fun ensureRemarksInJson(rawJson: String, fallbackRemarks: String): String {
        return try {
            val obj = JSONObject(rawJson)
            val current = obj.optString("remarks", "").trim()
            if (current.isBlank() || current == "null") {
                obj.put("remarks", fallbackRemarks.ifBlank { "QueuedTelemetry | Sync" })
            }
            obj.toString()
        } catch (_: Exception) {
            rawJson
        }
    }
}
