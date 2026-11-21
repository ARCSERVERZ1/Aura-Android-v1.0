package com.aura.family.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import com.aura.family.AuraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PeriodicAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PERIODIC_TELEMETRY) return

        val appContext = context.applicationContext
        val app = appContext as AuraApplication
        val pendingResult = goAsync()

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Aura:PeriodicTelemetryWakeLock"
        )?.apply {
            acquire(20_000L)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = app.container.preferencesRepository.settingsFlow.first()
                if (!settings.isOnboardingCompleted) return@launch

                // Keep foreground monitoring service alive
                AuraMonitoringService.start(appContext)

                if (settings.isPeriodicEnabled) {
                    scheduleNext(appContext, settings.periodicIntervalMinutes)

                    if (!settings.isPrivacyModeEnabled) {
                        recordLastPeriodicDispatch(appContext)
                        app.container.telemetryManager.triggerTelemetry(
                            remarks = "PeriodicUpdate | ${settings.periodicIntervalMinutes}m",
                            sosFlag = false
                        )
                    }
                }
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_PERIODIC_TELEMETRY = "com.aura.family.ACTION_PERIODIC_TELEMETRY"
        private const val REQUEST_CODE = 2001
        private const val PREFS_NAME = "aura_alarm_state"
        private const val KEY_LAST_PERIODIC_ELAPSED_MS = "last_periodic_elapsed_ms"

        fun scheduleNext(context: Context, intervalMinutes: Int) {
            val safeMinutes = intervalMinutes.coerceAtLeast(1)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerAt = SystemClock.elapsedRealtime() + safeMinutes * 60_000L
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                buildPendingIntent(context)
            )
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            alarmManager.cancel(buildPendingIntent(context))
        }

        fun recordLastPeriodicDispatch(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_PERIODIC_ELAPSED_MS, SystemClock.elapsedRealtime())
                .apply()
        }

        fun msSinceLastPeriodicDispatch(context: Context): Long {
            val last = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_PERIODIC_ELAPSED_MS, 0L)
            if (last <= 0L || SystemClock.elapsedRealtime() < last) return Long.MAX_VALUE
            return SystemClock.elapsedRealtime() - last
        }

        private fun buildPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, PeriodicAlarmReceiver::class.java).apply {
                action = ACTION_PERIODIC_TELEMETRY
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
