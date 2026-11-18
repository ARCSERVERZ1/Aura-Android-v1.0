package com.aura.family.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aura.family.background.AuraMonitoringService
import com.aura.family.background.WorkScheduler
import com.aura.family.data.preferences.AuraPreferencesRepository
import com.aura.family.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionStatusState(
    val foregroundLocationGranted: Boolean = false,
    val backgroundLocationGranted: Boolean = false,
    val phoneStateGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationExempted: Boolean = false
) {
    val allGranted: Boolean
        get() = foregroundLocationGranted &&
            backgroundLocationGranted &&
            phoneStateGranted &&
            notificationsGranted &&
            batteryOptimizationExempted
}

data class OnboardingUiState(
    val userName: String = "",
    val baseUrl: String = "",
    val nameError: String? = null,
    val urlError: String? = null,
    val permissions: PermissionStatusState = PermissionStatusState(),
    val isSaving: Boolean = false
)

class OnboardingViewModel(
    private val preferencesRepository: AuraPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun updateUserName(name: String) {
        _uiState.update { it.copy(userName = name, nameError = null) }
    }

    fun updateBaseUrl(url: String) {
        _uiState.update { it.copy(baseUrl = url, urlError = null) }
    }

    fun refreshPermissions(context: Context) {
        val fgGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val bgGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val phoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true

        _uiState.update {
            it.copy(
                permissions = PermissionStatusState(
                    foregroundLocationGranted = fgGranted,
                    backgroundLocationGranted = bgGranted,
                    phoneStateGranted = phoneGranted,
                    notificationsGranted = notifGranted,
                    batteryOptimizationExempted = batteryExempt
                )
            )
        }
    }

    fun completeOnboarding(context: Context, onCompleted: () -> Unit) {
        val current = _uiState.value
        val trimmedName = current.userName.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(nameError = "Please enter your name") }
            return
        }

        val trimmedUrl = current.baseUrl.trim()
        if (trimmedUrl.isNotBlank() && !NetworkModule.isValidWebUrl(trimmedUrl)) {
            _uiState.update {
                it.copy(urlError = "Enter a valid http:// or https:// website URL")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            preferencesRepository.saveOnboarding(trimmedName, trimmedUrl)
            val settings = preferencesRepository.settingsFlow.first()
            AuraMonitoringService.start(context.applicationContext)
            WorkScheduler.schedulePeriodicTelemetry(
                context = context.applicationContext,
                enabled = settings.isPeriodicEnabled,
                intervalMinutes = settings.periodicIntervalMinutes
            )
            _uiState.update { it.copy(isSaving = false) }
            onCompleted()
        }
    }

    class Factory(
        private val preferencesRepository: AuraPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnboardingViewModel(preferencesRepository) as T
        }
    }
}
