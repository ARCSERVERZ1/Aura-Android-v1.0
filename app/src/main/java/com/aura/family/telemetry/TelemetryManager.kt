package com.aura.family.telemetry

import android.content.Context
import com.aura.family.background.WorkScheduler
import com.aura.family.data.local.PendingTelemetryDao
import com.aura.family.data.local.PendingTelemetryEntity
import com.aura.family.data.preferences.AuraPreferencesRepository
import com.aura.family.data.remote.NetworkModule
import com.aura.family.data.remote.TelemetryApiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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
    private val metricsCollector: DeviceMetricsCollector,
    private val apiService: TelemetryApiService
) {

    private val flushMutex = Mutex()

    suspend fun triggerTelemetry(
        remarks: String,
        sosFlag: Boolean = true
    ): TelemetryDispatchResult {
        val settings = preferencesRepository.settingsFlow.first()

        // STRICT PRIVACY CHECK: Block ALL automated and manual location/telemetry triggers
        if (settings.isPrivacyModeEnabled) {
            return TelemetryDispatchResult.BlockedByPrivacyMode
        }

        val endpointUrl = NetworkModule.buildTelemetryEndpoint(settings.baseUrl)
            ?: return TelemetryDispatchResult.MissingBaseUrl

        val payload = metricsCollector.collectPayload(
            username = settings.userName,
            remarks = remarks,
            sosFlag = sosFlag
        )
        val payloadJson = NetworkModule.gson.toJson(payload)

        return try {
            val response = apiService.postTelemetry(endpointUrl, payload)
            if (response.isSuccessful) {
                preferencesRepository.updateLastTelemetrySummary(
                    "Sent ($remarks) at ${payload.timestamp}"
                )
                TelemetryDispatchResult.Sent(endpointUrl = endpointUrl, timestamp = payload.timestamp)
            } else {
                queueOffline(endpointUrl, payloadJson, remarks)
                TelemetryDispatchResult.QueuedOffline("HTTP ${response.code()} — queued for retry")
            }
        } catch (ex: Exception) {
            queueOffline(endpointUrl, payloadJson, remarks)
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

        val mediaType = "application/json; charset=utf-8".toMediaType()
        var allSucceeded = true

        for (item in pendingItems) {
            val latestSettings = preferencesRepository.settingsFlow.first()
            if (latestSettings.isPrivacyModeEnabled) {
                return false
            }

            try {
                val body = item.payloadJson.toRequestBody(mediaType)
                val response = apiService.postRawTelemetry(item.endpointUrl, body)
                if (response.isSuccessful) {
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
}
