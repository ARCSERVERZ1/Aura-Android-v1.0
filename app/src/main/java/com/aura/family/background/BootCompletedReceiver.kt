package com.aura.family.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aura.family.AuraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as AuraApplication
                val settings = app.container.preferencesRepository.settingsFlow.first()
                if (settings.isOnboardingCompleted) {
                    AuraMonitoringService.start(context.applicationContext)
                    WorkScheduler.schedulePeriodicTelemetry(
                        context = context.applicationContext,
                        enabled = settings.isPeriodicEnabled,
                        intervalMinutes = settings.periodicIntervalMinutes
                    )
                    WorkScheduler.schedulePendingSync(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
