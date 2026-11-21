package com.aura.family.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.aura.family.background.AuraMonitoringService
import com.aura.family.background.ServiceDiagnosticsState
import com.aura.family.background.WorkScheduler
import com.aura.family.data.local.PendingTelemetryDao
import com.aura.family.data.local.PendingTelemetryEntity
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

data class HomeUiState(
    val settings: AuraSettings = AuraSettings(),
    val pendingItems: List<PendingTelemetryEntity> = emptyList(),
    val serviceDiagnostics: ServiceDiagnosticsState = ServiceDiagnosticsState(),
    val dualSimStatus: DualSimStatus = DualSimStatus(),
    val periodicWorkState: WorkInfo.State? = null,
    val syncWorkState: WorkInfo.State? = null,
    val isSendingManualAlert: Boolean = false
) {
    val userName: String get() = settings.userName
    val baseUrl: String get() = settings.baseUrl
    val homeUrl: String get() = settings.effectiveHomeUrl
    val isPrivacyModeEnabled: Boolean get() = settings.isPrivacyModeEnabled
    val pendingQueueCount: Int get() = pendingItems.size
}

class HomeViewModel(
    appContext: Context,
    private val preferencesRepository: AuraPreferencesRepository,
    private val pendingTelemetryDao: PendingTelemetryDao,
    private val simSignalMonitor: SimSignalMonitor,
    private val telemetryManager: TelemetryManager
) : ViewModel() {

    private val isSendingAlert = MutableStateFlow(false)

    private val coreFlow = combine(
        preferencesRepository.settingsFlow,
        pendingTelemetryDao.observeAllPending(),
        AuraMonitoringService.diagnosticsState
    ) { settings, pendingItems, serviceDiag ->
        Triple(settings, pendingItems, serviceDiag)
    }

    private val workAndSimFlow = combine(
        simSignalMonitor.dualSimStatus,
        WorkScheduler.observeWorkInfo(appContext, WorkScheduler.PERIODIC_WORK_NAME),
        WorkScheduler.observeWorkInfo(appContext, WorkScheduler.PENDING_SYNC_WORK_NAME),
        isSendingAlert
    ) { simStatus, periodicInfo, syncInfo, sending ->
        Quad(simStatus, periodicInfo?.state, syncInfo?.state, sending)
    }

    val uiState: StateFlow<HomeUiState> = combine(coreFlow, workAndSimFlow) { core, extra ->
        HomeUiState(
            settings = core.first,
            pendingItems = core.second,
            serviceDiagnostics = core.third,
            dualSimStatus = extra.first,
            periodicWorkState = extra.second,
            syncWorkState = extra.third,
            isSendingManualAlert = extra.fourth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    fun togglePrivacyMode(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPrivacyMode(enabled)
            if (!enabled) {
                WorkScheduler.schedulePendingSync(context.applicationContext)
                _snackbarMessages.emit("Privacy Mode disabled — safety monitoring active")
            } else {
                _snackbarMessages.emit("Privacy Mode enabled — location sharing paused")
            }
        }
    }

    fun sendManualAlertNow() {
        viewModelScope.launch {
            if (isSendingAlert.value) return@launch
            isSendingAlert.value = true
            try {
                when (val result = telemetryManager.triggerTelemetry(
                    remarks = "ManualAlert | SOS",
                    sosFlag = true
                )) {
                    is TelemetryDispatchResult.Sent -> {
                        _snackbarMessages.emit("Alert sent to family portal")
                    }
                    is TelemetryDispatchResult.QueuedOffline -> {
                        _snackbarMessages.emit("Offline — alert queued for automatic retry")
                    }
                    TelemetryDispatchResult.BlockedByPrivacyMode -> {
                        _snackbarMessages.emit("Privacy Mode is ON — turn it off to send an alert")
                    }
                    TelemetryDispatchResult.MissingBaseUrl -> {
                        _snackbarMessages.emit("Please configure the Family Portal URL in App Settings")
                    }
                }
            } finally {
                isSendingAlert.value = false
            }
        }
    }

    fun flushPendingQueue() {
        viewModelScope.launch {
            if (uiState.value.isPrivacyModeEnabled) {
                _snackbarMessages.emit("Turn off Privacy Mode before syncing queued alerts")
                return@launch
            }
            val synced = telemetryManager.flushPendingQueue()
            _snackbarMessages.emit(
                if (synced) "Offline queue synced" else "Some items failed — will retry online"
            )
        }
    }

    fun clearPendingQueue() {
        viewModelScope.launch {
            pendingTelemetryDao.clearAll()
            _snackbarMessages.emit("Offline queue cleared")
        }
    }

    fun restartBackgroundService(context: Context) {
        AuraMonitoringService.start(context.applicationContext)
        simSignalMonitor.refreshSubscriptions()
    }

    fun refreshSignals() {
        simSignalMonitor.refreshSubscriptions()
    }

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    class Factory(
        private val appContext: Context,
        private val preferencesRepository: AuraPreferencesRepository,
        private val pendingTelemetryDao: PendingTelemetryDao,
        private val simSignalMonitor: SimSignalMonitor,
        private val telemetryManager: TelemetryManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                appContext = appContext.applicationContext,
                preferencesRepository = preferencesRepository,
                pendingTelemetryDao = pendingTelemetryDao,
                simSignalMonitor = simSignalMonitor,
                telemetryManager = telemetryManager
            ) as T
        }
    }
}
