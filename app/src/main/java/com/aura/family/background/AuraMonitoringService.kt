package com.aura.family.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aura.family.AuraApplication
import com.aura.family.MainActivity
import com.aura.family.R
import com.aura.family.telemetry.SimSignalMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AuraMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batteryReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return

            val batteryPct = ((level * 100) / scale).coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            serviceScope.launch {
                handleBatteryUpdate(batteryPct, isCharging)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithAppropriateTypes(isPrivacyMode = false)
        registerBatteryReceiver()
        observePrivacyModeForNotification()
        startSimSignalMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            val settings = (application as AuraApplication).container.preferencesRepository.settingsFlow.first()
            startForegroundWithAppropriateTypes(isPrivacyMode = settings.isPrivacyModeEnabled)
            WorkScheduler.schedulePeriodicTelemetry(
                context = applicationContext,
                enabled = settings.isOnboardingCompleted && settings.isPeriodicEnabled,
                intervalMinutes = settings.periodicIntervalMinutes
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (batteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            batteryReceiverRegistered = false
        }
        (application as AuraApplication).container.simSignalMonitor.stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithAppropriateTypes(isPrivacyMode: Boolean) {
        val notification = buildNotification(isPrivacyMode)
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasLocation) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgType)
    }

    private fun observePrivacyModeForNotification() {
        val prefsRepo = (application as AuraApplication).container.preferencesRepository
        serviceScope.launch {
            prefsRepo.settingsFlow.collectLatest { settings ->
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NOTIFICATION_ID, buildNotification(settings.isPrivacyModeEnabled))
            }
        }
    }

    private fun registerBatteryReceiver() {
        if (!batteryReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            batteryReceiverRegistered = true
        }
    }

    private fun startSimSignalMonitoring() {
        val container = (application as AuraApplication).container
        container.simSignalMonitor.startMonitoring { slotIndex, dbm ->
            serviceScope.launch {
                handleSimSignalSample(slotIndex, dbm)
            }
        }
    }

    private suspend fun handleBatteryUpdate(batteryPct: Int, isCharging: Boolean) {
        val container = (application as AuraApplication).container
        val prefsRepo = container.preferencesRepository
        val settings = prefsRepo.settingsFlow.first()

        // Reset anti-spam flag once battery recovers above threshold + 2%
        if (settings.lowBatteryTriggered && batteryPct > settings.lowBatteryThreshold + 2) {
            prefsRepo.setLowBatteryTriggered(false)
            return
        }

        if (settings.isPrivacyModeEnabled || !settings.isLowBatteryEnabled) return

        if (batteryPct <= settings.lowBatteryThreshold && !isCharging && !settings.lowBatteryTriggered) {
            prefsRepo.setLowBatteryTriggered(true)
            container.telemetryManager.triggerTelemetry(
                remarks = "Battery Trigger at $batteryPct%",
                sosFlag = true
            )
        }
    }

    private suspend fun handleSimSignalSample(slotIndex: Int, dbm: Int) {
        val container = (application as AuraApplication).container
        val prefsRepo = container.preferencesRepository
        val settings = prefsRepo.settingsFlow.first()

        if (settings.isPrivacyModeEnabled) return

        val enabled = if (slotIndex == 0) settings.isSim1TriggerEnabled else settings.isSim2TriggerEnabled
        if (!enabled) return

        val threshold = if (slotIndex == 0) settings.sim1ThresholdDbm else settings.sim2ThresholdDbm
        val lastTriggerMs = if (slotIndex == 0) settings.sim1LastTriggerMs else settings.sim2LastTriggerMs

        val now = System.currentTimeMillis()
        if (dbm <= threshold && (now - lastTriggerMs) >= SimSignalMonitor.COOLDOWN_MILLIS) {
            prefsRepo.recordSimTriggerTime(slotIndex, now)
            val simLabel = if (slotIndex == 0) "SIM 1" else "SIM 2"
            container.telemetryManager.triggerTelemetry(
                remarks = "$simLabel Signal Trigger ($dbm dBm)",
                sosFlag = true
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isPrivacyMode: Boolean): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (isPrivacyMode) {
            getString(R.string.notification_body_privacy)
        } else {
            getString(R.string.notification_body_active)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "aura_monitoring_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AuraMonitoringService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AuraMonitoringService::class.java))
        }
    }
}
