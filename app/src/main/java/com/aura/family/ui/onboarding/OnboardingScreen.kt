package com.aura.family.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.family.ui.theme.DeepNavy
import com.aura.family.ui.theme.SoftTeal
import com.aura.family.ui.theme.SuccessEmerald
import com.aura.family.ui.theme.SuccessEmeraldContainer

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        viewModel.refreshPermissions(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions(context)
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions(context)
    }

    val phoneStateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions(context)
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Brand Hero Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DeepNavy),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.headlineMedium,
                        color = SoftTeal,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = "Welcome to Aura",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Set up your profile and safety permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Profile Setup Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Your Profile",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = uiState.userName,
                        onValueChange = viewModel::updateUserName,
                        label = { Text("Your Name") },
                        placeholder = { Text("e.g., Sanjay") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        isError = uiState.nameError != null,
                        supportingText = if (uiState.nameError != null) {
                            { Text(uiState.nameError.orEmpty()) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uiState.baseUrl,
                        onValueChange = viewModel::updateBaseUrl,
                        label = { Text("Family Portal URL (Optional)") },
                        placeholder = { Text("https://familywebsite.com") },
                        leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null) },
                        isError = uiState.urlError != null,
                        supportingText = {
                            Text(uiState.urlError ?: "You can update this anytime in App Settings.")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Permissions Checklist
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Required Safety Permissions",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Aura needs these permissions to share reliable coordinates during low battery or weak signal events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PermissionCardItem(
                    icon = Icons.Rounded.LocationOn,
                    title = "Precise & Approximate Location",
                    description = "Captures your current GPS coordinates when an alert or scheduled check-in triggers.",
                    isGranted = uiState.permissions.foregroundLocationGranted,
                    buttonLabel = "Allow Location",
                    onGrantClick = {
                        foregroundLocationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )

                PermissionCardItem(
                    icon = Icons.Rounded.MyLocation,
                    title = "Background Location Access",
                    description = if (uiState.permissions.foregroundLocationGranted) {
                        "Select \"Allow all the time\" so safety alerts work even when Aura is closed."
                    } else {
                        "Grant foreground location first, then allow background access."
                    },
                    isGranted = uiState.permissions.backgroundLocationGranted,
                    enabled = uiState.permissions.foregroundLocationGranted,
                    buttonLabel = "Allow All Time",
                    onGrantClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
                        }
                    }
                )

                PermissionCardItem(
                    icon = Icons.Rounded.SimCard,
                    title = "Phone & Dual-SIM Signal State",
                    description = "Allows Aura to monitor SIM 1 and SIM 2 signal strength in dBm.",
                    isGranted = uiState.permissions.phoneStateGranted,
                    buttonLabel = "Allow Phone State",
                    onGrantClick = {
                        phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                )

                PermissionCardItem(
                    icon = Icons.Rounded.Notifications,
                    title = "Service Notifications",
                    description = "Shows the quiet persistent status indicator required by Android.",
                    isGranted = uiState.permissions.notificationsGranted,
                    buttonLabel = "Allow Notifications",
                    onGrantClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )

                PermissionCardItem(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Battery Optimization Exemption",
                    description = "Prevents Android from pausing Aura's background safety service during sleep.",
                    isGranted = uiState.permissions.batteryOptimizationExempted,
                    buttonLabel = "Exempt Aura",
                    onGrantClick = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            )
                        }
                    }
                )
            }

            Button(
                onClick = { viewModel.completeOnboarding(context, onOnboardingComplete) },
                enabled = uiState.userName.isNotBlank() && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepNavy,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (uiState.isSaving) "Starting Aura..." else "Continue to Aura",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PermissionCardItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonLabel: String,
    enabled: Boolean = true,
    onGrantClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isGranted) SuccessEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) SuccessEmeraldContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) SuccessEmerald else MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (isGranted) {
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelLarge,
                    color = SuccessEmerald
                )
            } else {
                FilledTonalButton(
                    onClick = onGrantClick,
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = buttonLabel)
                }
            }
        }
    }
}
