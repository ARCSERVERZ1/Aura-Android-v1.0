package com.aura.family.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aura.family.data.preferences.AuraPreferencesRepository
import com.aura.family.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppSettingsUiState(
    val userName: String = "",
    val baseUrl: String = "",
    val homeEndpoint: String = "/home",
    val nameError: String? = null,
    val urlError: String? = null,
    val isSaving: Boolean = false
) {
    val homeUrlPreview: String
        get() = NetworkModule.buildHomeUrl(baseUrl, homeEndpoint)
            .ifBlank { "Enter a valid URL to preview Home endpoint" }

    val telemetryEndpointPreview: String
        get() = NetworkModule.buildTelemetryEndpoint(baseUrl)
            ?: "Enter a valid URL to preview Telemetry endpoint"
}

class AppSettingsViewModel(
    private val preferencesRepository: AuraPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    private val _savedEvents = MutableSharedFlow<String>()
    val savedEvents: SharedFlow<String> = _savedEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val current = preferencesRepository.settingsFlow.first()
            _uiState.update {
                it.copy(
                    userName = current.userName,
                    baseUrl = current.baseUrl,
                    homeEndpoint = current.homeEndpoint
                )
            }
        }
    }

    fun updateUserName(name: String) {
        _uiState.update { it.copy(userName = name, nameError = null) }
    }

    fun updateBaseUrl(url: String) {
        _uiState.update { it.copy(baseUrl = url, urlError = null) }
    }

    fun updateHomeEndpoint(endpoint: String) {
        _uiState.update { it.copy(homeEndpoint = endpoint) }
    }

    fun saveSettings() {
        val current = _uiState.value
        val trimmedName = current.userName.trim()
        val normalizedUrl = NetworkModule.normalizeBaseUrl(current.baseUrl)
        val normalizedEndpoint = NetworkModule.normalizeHomeEndpoint(current.homeEndpoint)

        var hasError = false
        var nameErr: String? = null
        var urlErr: String? = null

        if (trimmedName.isBlank()) {
            nameErr = "User name cannot be empty"
            hasError = true
        }
        if (!NetworkModule.isValidWebUrl(normalizedUrl)) {
            urlErr = "Enter a valid http:// or https:// domain URL"
            hasError = true
        }

        if (hasError) {
            _uiState.update { it.copy(nameError = nameErr, urlError = urlErr) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            preferencesRepository.saveAppSettings(trimmedName, normalizedUrl, normalizedEndpoint)
            _uiState.update {
                it.copy(
                    userName = trimmedName,
                    baseUrl = normalizedUrl,
                    homeEndpoint = normalizedEndpoint,
                    isSaving = false
                )
            }
            _savedEvents.emit("Settings saved")
        }
    }

    class Factory(
        private val preferencesRepository: AuraPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppSettingsViewModel(preferencesRepository) as T
        }
    }
}
