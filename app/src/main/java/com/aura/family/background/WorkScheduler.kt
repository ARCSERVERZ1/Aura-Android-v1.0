package com.aura.family.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val PERIODIC_WORK_NAME = "aura_periodic_telemetry"
    private const val PENDING_SYNC_WORK_NAME = "aura_pending_telemetry_sync"

    fun schedulePeriodicTelemetry(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Int
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val safeInterval = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<PeriodicTelemetryWorker>(
            safeInterval,
            TimeUnit.MINUTES
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun schedulePendingSync(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PendingTelemetrySyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            PENDING_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
