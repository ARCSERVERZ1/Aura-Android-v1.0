package com.aura.family.telemetry

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.aura.family.data.remote.TelemetryPayload
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

class DeviceMetricsCollector(
    private val context: Context,
    private val simSignalMonitor: SimSignalMonitor
) {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    suspend fun collectPayload(
        username: String,
        remarks: String,
        sosFlag: Boolean
    ): TelemetryPayload {
        val location = fetchCurrentLocation()
        val battery = readBatterySnapshot()
        val networkType = readNetworkType()
        val signalStrength = readSignalStrength(networkType)
        val brightness = readScreenBrightness()

        return TelemetryPayload(
            username = username.ifBlank { "Aura User" },
            deviceModel = buildDeviceModel(),
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            isCharging = battery.isCharging,
            batteryLevel = battery.levelPercent,
            signalStrength = signalStrength,
            networkType = networkType,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            locale = Locale.getDefault().toString(),
            uptimeMs = SystemClock.elapsedRealtime(),
            screenBrightness = brightness,
            remarks = remarks,
            sosFlag = sosFlag,
            status = 1,
            timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocation(): Location? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        val priority = if (hasFine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val cts = CancellationTokenSource()
        val freshLocation = withTimeoutOrNull(8_000L) {
            try {
                fusedLocationClient.getCurrentLocation(priority, cts.token).await()
            } catch (_: Exception) {
                null
            }
        }
        if (freshLocation != null) return freshLocation

        return try {
            fusedLocationClient.lastLocation.await()
        } catch (_: Exception) {
            null
        }
    }

    data class BatterySnapshot(
        val levelPercent: Int,
        val isCharging: Boolean
    )

    fun readBatterySnapshot(): BatterySnapshot {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            return BatterySnapshot(levelPercent = pct.coerceIn(0, 100), isCharging = charging)
        }
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        return BatterySnapshot(
            levelPercent = capacity.coerceIn(0, 100),
            isCharging = bm?.isCharging ?: false
        )
    }

    private fun readNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "NONE"
        val network = cm.activeNetwork ?: return "NONE"
        val caps = cm.getNetworkCapabilities(network) ?: return "NONE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }

    @Suppress("DEPRECATION")
    private fun readSignalStrength(networkType: String): Int {
        if (networkType == "WIFI") {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps != null) {
                val dbm = caps.signalStrength
                if (dbm != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && dbm in -127..0) {
                    return dbm
                }
            }
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val rssi = wifiManager?.connectionInfo?.rssi
            if (rssi != null && rssi in -127..-1) {
                return rssi
            }
        }
        return simSignalMonitor.latestActiveDbm() ?: -120
    }

    private fun readScreenBrightness(): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            ).coerceIn(0, 255)
        } catch (_: Exception) {
            128
        }
    }

    private fun buildDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return when {
            model.isBlank() -> "Android Device"
            manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }
}
