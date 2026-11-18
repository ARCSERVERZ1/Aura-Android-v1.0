package com.aura.family.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aura.family.AuraApplication
import kotlinx.coroutines.flow.first

class PeriodicTelemetryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AuraApplication
        val settings = app.container.preferencesRepository.settingsFlow.first()

        if (!settings.isOnboardingCompleted || !settings.isPeriodicEnabled || settings.isPrivacyModeEnabled) {
            return Result.success()
        }

        app.container.telemetryManager.triggerTelemetry(
            remarks = "Periodic Update (${settings.periodicIntervalMinutes} min)",
            sosFlag = false
        )
        return Result.success()
    }
}
