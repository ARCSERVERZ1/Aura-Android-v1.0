package com.aura.family.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aura.family.background.WorkScheduler
import com.aura.family.data.local.PendingTelemetryDao
import com.aura.family.data.preferences.AuraPreferencesRepository
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
    val userName: String = "",
    val baseUrl: String = "",
    val isPrivacyModeEnabled: Boolean = false,
    val pendingQueueCount: Int = 0,
    val isSendingManualAlert: Boolean = false
)

class HomeViewModel(
    private val preferencesRepository: AuraPreferencesRepository,
    pendingTelemetryDao: PendingTelemetryDao,
    private val telemetryManager: TelemetryManager
) : ViewModel() {

    private val isSendingAlert = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesRepository.settingsFlow,
        pendingTelemetryDao.observePendingCount(),
        isSendingAlert
    ) { settings, pendingCount, sending ->
        HomeUiState(
            userName = settings.userName,
            baseUrl = settings.baseUrl,
            isPrivacyModeEnabled = settings.isPrivacyModeEnabled,
            pendingQueueCount = pendingCount,
            isSendingManualAlert = sending
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
                    remarks = "Manual SOS Trigger",
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

    class Factory(
        private val preferencesRepository: AuraPreferencesRepository,
        private val pendingTelemetryDao: PendingTelemetryDao,
        private val telemetryManager: TelemetryManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                preferencesRepository = preferencesRepository,
                pendingTelemetryDao = pendingTelemetryDao,
                telemetryManager = telemetryManager
            ) as T
        }
    }
}
