package com.aura.family.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

object WorkScheduler {

    const val PERIODIC_WORK_NAME = "aura_periodic_telemetry"
    const val PENDING_SYNC_WORK_NAME = "aura_pending_telemetry_sync"

    fun schedulePeriodicTelemetry(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Int
    ) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        if (!enabled) {
            PeriodicAlarmReceiver.cancel(appContext)
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        // Exact-interval background alarm (works for 1m, 2m, 15m, 60m+ even in Doze)
        PeriodicAlarmReceiver.scheduleNext(appContext, intervalMinutes)

        // OS-managed WorkManager watchdog (15m minimum enforced by Android WorkManager)
        val watchdogInterval = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<PeriodicTelemetryWorker>(
            watchdogInterval,
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

    fun observeWorkInfo(context: Context, uniqueWorkName: String): Flow<WorkInfo?> {
        return WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWorkFlow(uniqueWorkName)
            .map { list -> list.firstOrNull() }
    }
}
