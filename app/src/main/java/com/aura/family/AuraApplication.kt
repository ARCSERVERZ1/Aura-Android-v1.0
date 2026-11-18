package com.aura.family

import android.app.Application
import android.content.Context
import com.aura.family.data.local.AuraDatabase
import com.aura.family.data.preferences.AuraPreferencesRepository
import com.aura.family.data.remote.NetworkModule
import com.aura.family.telemetry.DeviceMetricsCollector
import com.aura.family.telemetry.SimSignalMonitor
import com.aura.family.telemetry.TelemetryManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val preferencesRepository: AuraPreferencesRepository by lazy {
        AuraPreferencesRepository(appContext)
    }

    val database: AuraDatabase by lazy {
        AuraDatabase.getInstance(appContext)
    }

    val simSignalMonitor: SimSignalMonitor by lazy {
        SimSignalMonitor(appContext)
    }

    val deviceMetricsCollector: DeviceMetricsCollector by lazy {
        DeviceMetricsCollector(
            context = appContext,
            simSignalMonitor = simSignalMonitor
        )
    }

    val telemetryManager: TelemetryManager by lazy {
        TelemetryManager(
            context = appContext,
            preferencesRepository = preferencesRepository,
            pendingTelemetryDao = database.pendingTelemetryDao(),
            metricsCollector = deviceMetricsCollector,
            apiService = NetworkModule.telemetryApiService
        )
    }
}

class AuraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
