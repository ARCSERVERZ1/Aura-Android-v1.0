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

        if (!settings.isOnboardingCompleted) {
            return Result.success()
        }

        // Ensure background service stays alive
        AuraMonitoringService.start(applicationContext)

        if (!settings.isPeriodicEnabled || settings.isPrivacyModeEnabled) {
            return Result.success()
        }

        val intervalMs = settings.periodicIntervalMinutes.coerceAtLeast(1) * 60_000L
        val elapsedSinceLast = PeriodicAlarmReceiver.msSinceLastPeriodicDispatch(applicationContext)

        // Fire only if overdue (avoids duplicating PeriodicAlarmReceiver)
        if (elapsedSinceLast >= (intervalMs * 0.85).toLong()) {
            PeriodicAlarmReceiver.recordLastPeriodicDispatch(applicationContext)
            PeriodicAlarmReceiver.scheduleNext(applicationContext, settings.periodicIntervalMinutes)
            app.container.telemetryManager.triggerTelemetry(
                remarks = "PeriodicUpdate | ${settings.periodicIntervalMinutes}m",
                sosFlag = false
            )
        }

        return Result.success()
    }
}
