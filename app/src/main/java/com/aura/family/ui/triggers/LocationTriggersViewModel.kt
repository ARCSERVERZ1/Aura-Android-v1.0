package com.aura.family.ui.triggers

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aura.family.background.WorkScheduler
import com.aura.family.data.local.PendingTelemetryDao
import com.aura.family.data.preferences.AuraPreferencesRepository
import com.aura.family.data.preferences.AuraSettings
import com.aura.family.telemetry.DualSimStatus
import com.aura.family.telemetry.SimSignalMonitor
import com.aura.family.telemetry.TelemetryDispatchResult
import com.aura.family.telemetry.TelemetryManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocationTriggersUiState(
    val settings: AuraSettings = AuraSettings(),
    val dualSimStatus: DualSimStatus = DualSimStatus(),
    val pendingQueueCount: Int = 0,
    val isSendingNow: Boolean = false
)

class LocationTriggersViewModel(
    private val preferencesRepository: AuraPreferencesRepository,
    pendingTelemetryDao: PendingTelemetryDao,
    private val simSignalMonitor: SimSignalMonitor,
    private val telemetryManager: TelemetryManager
) : ViewModel() {

    private val isSendingNow = MutableStateFlow(false)

    val uiState: StateFlow<LocationTriggersUiState> = combine(
        preferencesRepository.settingsFlow,
        simSignalMonitor.dualSimStatus,
        pendingTelemetryDao.observePendingCount(),
        isSendingNow
    ) { settings, simStatus, pendingCount, sending ->
        LocationTriggersUiState(
            settings = settings,
            dualSimStatus = simStatus,
            pendingQueueCount = pendingCount,
            isSendingNow = sending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocationTriggersUiState()
    )

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        simSignalMonitor.refreshSubscriptions()
    }

    fun refreshSignals() {
        simSignalMonitor.refreshSubscriptions()
    }

    fun updateLowBattery(enabled: Boolean, thresholdPercent: Int) {
        viewModelScope.launch {
            val snapped = ((thresholdPercent + 2) / 5) * 5
            preferencesRepository.updateLowBatteryConfig(enabled, snapped.coerceIn(5, 50))
        }
    }

    fun updateSimTrigger(slotIndex: Int, enabled: Boolean, thresholdDbm: Int) {
        viewModelScope.launch {
            preferencesRepository.updateSimConfig(slotIndex, enabled, thresholdDbm)
        }
    }

    fun updatePeriodicTrigger(context: Context, enabled: Boolean, intervalMinutes: Int) {
        viewModelScope.launch {
            val clamped = intervalMinutes.coerceAtLeast(1)
            preferencesRepository.updatePeriodicConfig(enabled, clamped)
            WorkScheduler.schedulePeriodicTelemetry(
                context = context.applicationContext,
                enabled = enabled,
                intervalMinutes = clamped
            )
        }
    }

    fun sendNow() {
        viewModelScope.launch {
            if (isSendingNow.value) return@launch
            isSendingNow.value = true
            try {
                when (val result = telemetryManager.triggerTelemetry(
                    remarks = "On-Demand Manual Trigger",
                    sosFlag = true
                )) {
                    is TelemetryDispatchResult.Sent -> {
                        _messages.emit("Location & status sent (${result.timestamp})")
                    }
                    is TelemetryDispatchResult.QueuedOffline -> {
                        _messages.emit(result.reason)
                    }
                    TelemetryDispatchResult.BlockedByPrivacyMode -> {
                        _messages.emit("Privacy Mode is ON — location sharing is paused")
                    }
                    TelemetryDispatchResult.MissingBaseUrl -> {
                        _messages.emit("Configure your Family Portal URL in App Settings first")
                    }
                }
            } finally {
                isSendingNow.value = false
            }
        }
    }

    fun retryQueuedAlerts() {
        viewModelScope.launch {
            if (uiState.value.settings.isPrivacyModeEnabled) {
                _messages.emit("Turn off Privacy Mode before syncing queued alerts")
                return@launch
            }
            val synced = telemetryManager.flushPendingQueue()
            _messages.emit(
                if (synced) "All queued alerts synced" else "Some alerts remain queued — will retry online"
            )
        }
    }

    class Factory(
        private val preferencesRepository: AuraPreferencesRepository,
        private val pendingTelemetryDao: PendingTelemetryDao,
        private val simSignalMonitor: SimSignalMonitor,
        private val telemetryManager: TelemetryManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LocationTriggersViewModel(
                preferencesRepository = preferencesRepository,
                pendingTelemetryDao = pendingTelemetryDao,
                simSignalMonitor = simSignalMonitor,
                telemetryManager = telemetryManager
            ) as T
        }
    }
}
