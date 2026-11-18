package com.aura.family.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aura.family.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.auraDataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_preferences")

data class AuraSettings(
    val userName: String = "",
    val baseUrl: String = "",
    val homeEndpoint: String = "/home",
    val isOnboardingCompleted: Boolean = false,
    val isPrivacyModeEnabled: Boolean = false,
    val isLowBatteryEnabled: Boolean = true,
    val lowBatteryThreshold: Int = 20,
    val lowBatteryTriggered: Boolean = false,
    val isSim1TriggerEnabled: Boolean = true,
    val sim1ThresholdDbm: Int = -105,
    val sim1LastTriggerMs: Long = 0L,
    val isSim2TriggerEnabled: Boolean = false,
    val sim2ThresholdDbm: Int = -105,
    val sim2LastTriggerMs: Long = 0L,
    val isPeriodicEnabled: Boolean = true,
    val periodicIntervalMinutes: Int = 60,
    val lastTelemetrySummary: String = ""
) {
    val effectiveHomeUrl: String
        get() = NetworkModule.buildHomeUrl(baseUrl, homeEndpoint)
}

class AuraPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val BASE_URL = stringPreferencesKey("base_url")
        val HOME_ENDPOINT = stringPreferencesKey("home_endpoint")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PRIVACY_MODE_ENABLED = booleanPreferencesKey("privacy_mode_enabled")

        val LOW_BATTERY_ENABLED = booleanPreferencesKey("low_battery_enabled")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val LOW_BATTERY_TRIGGERED = booleanPreferencesKey("low_battery_triggered")

        val SIM1_TRIGGER_ENABLED = booleanPreferencesKey("sim1_trigger_enabled")
        val SIM1_THRESHOLD_DBM = intPreferencesKey("sim1_threshold_dbm")
        val SIM1_LAST_TRIGGER_MS = longPreferencesKey("sim1_last_trigger_ms")

        val SIM2_TRIGGER_ENABLED = booleanPreferencesKey("sim2_trigger_enabled")
        val SIM2_THRESHOLD_DBM = intPreferencesKey("sim2_threshold_dbm")
        val SIM2_LAST_TRIGGER_MS = longPreferencesKey("sim2_last_trigger_ms")

        val PERIODIC_ENABLED = booleanPreferencesKey("periodic_enabled")
        val PERIODIC_INTERVAL_MINUTES = intPreferencesKey("periodic_interval_minutes")

        val LAST_TELEMETRY_SUMMARY = stringPreferencesKey("last_telemetry_summary")
    }

    val settingsFlow: Flow<AuraSettings> = context.auraDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AuraSettings(
                userName = prefs[Keys.USER_NAME] ?: "",
                baseUrl = prefs[Keys.BASE_URL] ?: "",
                homeEndpoint = prefs[Keys.HOME_ENDPOINT] ?: "/home",
                isOnboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                isPrivacyModeEnabled = prefs[Keys.PRIVACY_MODE_ENABLED] ?: false,
                isLowBatteryEnabled = prefs[Keys.LOW_BATTERY_ENABLED] ?: true,
                lowBatteryThreshold = (prefs[Keys.LOW_BATTERY_THRESHOLD] ?: 20).coerceIn(5, 50),
                lowBatteryTriggered = prefs[Keys.LOW_BATTERY_TRIGGERED] ?: false,
                isSim1TriggerEnabled = prefs[Keys.SIM1_TRIGGER_ENABLED] ?: true,
                sim1ThresholdDbm = (prefs[Keys.SIM1_THRESHOLD_DBM] ?: -105).coerceIn(-120, -50),
                sim1LastTriggerMs = prefs[Keys.SIM1_LAST_TRIGGER_MS] ?: 0L,
                isSim2TriggerEnabled = prefs[Keys.SIM2_TRIGGER_ENABLED] ?: false,
                sim2ThresholdDbm = (prefs[Keys.SIM2_THRESHOLD_DBM] ?: -105).coerceIn(-120, -50),
                sim2LastTriggerMs = prefs[Keys.SIM2_LAST_TRIGGER_MS] ?: 0L,
                isPeriodicEnabled = prefs[Keys.PERIODIC_ENABLED] ?: true,
                periodicIntervalMinutes = (prefs[Keys.PERIODIC_INTERVAL_MINUTES] ?: 60).coerceAtLeast(1),
                lastTelemetrySummary = prefs[Keys.LAST_TELEMETRY_SUMMARY] ?: ""
            )
        }

    suspend fun saveOnboarding(userName: String, baseUrl: String) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = userName.trim()
            if (baseUrl.isNotBlank()) {
                prefs[Keys.BASE_URL] = NetworkModule.normalizeBaseUrl(baseUrl)
            }
            if (prefs[Keys.HOME_ENDPOINT] == null) {
                prefs[Keys.HOME_ENDPOINT] = "/home"
            }
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun saveAppSettings(userName: String, baseUrl: String, homeEndpoint: String) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = userName.trim()
            prefs[Keys.BASE_URL] = NetworkModule.normalizeBaseUrl(baseUrl)
            prefs[Keys.HOME_ENDPOINT] = NetworkModule.normalizeHomeEndpoint(homeEndpoint)
        }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.PRIVACY_MODE_ENABLED] = enabled
        }
    }

    suspend fun updateLowBatteryConfig(enabled: Boolean, thresholdPercent: Int) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.LOW_BATTERY_ENABLED] = enabled
            prefs[Keys.LOW_BATTERY_THRESHOLD] = thresholdPercent.coerceIn(5, 50)
        }
    }

    suspend fun setLowBatteryTriggered(triggered: Boolean) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.LOW_BATTERY_TRIGGERED] = triggered
        }
    }

    suspend fun updateSimConfig(slotIndex: Int, enabled: Boolean, thresholdDbm: Int) {
        context.auraDataStore.edit { prefs ->
            val clamped = thresholdDbm.coerceIn(-120, -50)
            if (slotIndex == 0) {
                prefs[Keys.SIM1_TRIGGER_ENABLED] = enabled
                prefs[Keys.SIM1_THRESHOLD_DBM] = clamped
            } else {
                prefs[Keys.SIM2_TRIGGER_ENABLED] = enabled
                prefs[Keys.SIM2_THRESHOLD_DBM] = clamped
            }
        }
    }

    suspend fun recordSimTriggerTime(slotIndex: Int, timestampMs: Long) {
        context.auraDataStore.edit { prefs ->
            if (slotIndex == 0) {
                prefs[Keys.SIM1_LAST_TRIGGER_MS] = timestampMs
            } else {
                prefs[Keys.SIM2_LAST_TRIGGER_MS] = timestampMs
            }
        }
    }

    suspend fun updatePeriodicConfig(enabled: Boolean, intervalMinutes: Int) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.PERIODIC_ENABLED] = enabled
            prefs[Keys.PERIODIC_INTERVAL_MINUTES] = intervalMinutes.coerceAtLeast(1)
        }
    }

    suspend fun updateLastTelemetrySummary(summary: String) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.LAST_TELEMETRY_SUMMARY] = summary
        }
    }
}
