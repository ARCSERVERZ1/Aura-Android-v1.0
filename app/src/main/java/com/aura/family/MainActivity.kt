package com.aura.family

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aura.family.background.AuraMonitoringService
import com.aura.family.background.WorkScheduler
import com.aura.family.ui.home.HomeScreen
import com.aura.family.ui.home.HomeViewModel
import com.aura.family.ui.navigation.AuraRoutes
import com.aura.family.ui.onboarding.OnboardingScreen
import com.aura.family.ui.onboarding.OnboardingViewModel
import com.aura.family.ui.settings.AppSettingsScreen
import com.aura.family.ui.settings.AppSettingsViewModel
import com.aura.family.ui.theme.AuraTheme
import com.aura.family.ui.triggers.LocationTriggersScreen
import com.aura.family.ui.triggers.LocationTriggersViewModel
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AuraApplication).container

        setContent {
            AuraTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val settings = container.preferencesRepository.settingsFlow.first()
                    if (settings.isOnboardingCompleted) {
                        AuraMonitoringService.start(applicationContext)
                        WorkScheduler.schedulePeriodicTelemetry(
                            context = applicationContext,
                            enabled = settings.isPeriodicEnabled,
                            intervalMinutes = settings.periodicIntervalMinutes
                        )
                        startDestination = AuraRoutes.HOME
                    } else {
                        startDestination = AuraRoutes.ONBOARDING
                    }
                }

                val destination = startDestination
                if (destination == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    AuraNavHost(
                        startDestination = destination,
                        container = container
                    )
                }
            }
        }
    }
}

@Composable
private fun AuraNavHost(
    startDestination: String,
    container: AppContainer
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AuraRoutes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.Factory(container.preferencesRepository)
            )
            OnboardingScreen(
                viewModel = vm,
                onOnboardingComplete = {
                    navController.navigate(AuraRoutes.HOME) {
                        popUpTo(AuraRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(AuraRoutes.HOME) {
            val vm: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    preferencesRepository = container.preferencesRepository,
                    pendingTelemetryDao = container.database.pendingTelemetryDao(),
                    telemetryManager = container.telemetryManager
                )
            )
            HomeScreen(
                viewModel = vm,
                onNavigateToAppSettings = {
                    navController.navigate(AuraRoutes.APP_SETTINGS)
                },
                onNavigateToLocationTriggers = {
                    navController.navigate(AuraRoutes.LOCATION_TRIGGERS)
                }
            )
        }

        composable(AuraRoutes.APP_SETTINGS) {
            val vm: AppSettingsViewModel = viewModel(
                factory = AppSettingsViewModel.Factory(container.preferencesRepository)
            )
            AppSettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AuraRoutes.LOCATION_TRIGGERS) {
            val vm: LocationTriggersViewModel = viewModel(
                factory = LocationTriggersViewModel.Factory(
                    preferencesRepository = container.preferencesRepository,
                    pendingTelemetryDao = container.database.pendingTelemetryDao(),
                    simSignalMonitor = container.simSignalMonitor,
                    telemetryManager = container.telemetryManager
                )
            )
            LocationTriggersScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
