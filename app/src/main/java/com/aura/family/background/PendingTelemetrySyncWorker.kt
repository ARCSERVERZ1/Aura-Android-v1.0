package com.aura.family.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aura.family.AuraApplication
import kotlinx.coroutines.flow.first

class PendingTelemetrySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AuraApplication
        val settings = app.container.preferencesRepository.settingsFlow.first()

        if (settings.isPrivacyModeEnabled) {
            return Result.success()
        }

        val flushedAll = app.container.telemetryManager.flushPendingQueue()
        return if (flushedAll) Result.success() else Result.retry()
    }
}
