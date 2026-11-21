package com.aura.family.ui.triggers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.family.telemetry.SimSlotStatus
import com.aura.family.ui.theme.AlertCrimson
import com.aura.family.ui.theme.DeepNavy
import com.aura.family.ui.theme.SoftTeal
import com.aura.family.ui.theme.SuccessEmerald
import com.aura.family.ui.theme.WarningAmber
import com.aura.family.ui.theme.WarningAmberContainer
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocationTriggersScreen(
    viewModel: LocationTriggersViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val snackbarHostState = remember { SnackbarHostState() }

    var customIntervalText by remember(settings.periodicIntervalMinutes) {
        mutableStateOf(settings.periodicIntervalMinutes.toString())
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Location Triggers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshSignals) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh current SIM signal strength"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (settings.isPrivacyModeEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmberContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Privacy Mode is currently ON. All automatic and manual triggers are paused until you turn it off from the Home screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // 1. Low Battery Trigger
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatteryAlert,
                                contentDescription = null,
                                tint = WarningAmber
                            )
                            Text(
                                text = "Low Battery Trigger",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Switch(
                            checked = settings.isLowBatteryEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateLowBattery(
                                    context,
                                    enabled,
                                    settings.lowBatteryThreshold
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Trigger when battery \u2264 ${settings.lowBatteryThreshold}%",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalIconButton(
                                enabled = settings.isLowBatteryEnabled && settings.lowBatteryThreshold > 1,
                                onClick = {
                                    viewModel.updateLowBattery(
                                        context,
                                        settings.isLowBatteryEnabled,
                                        (settings.lowBatteryThreshold - 1).coerceAtLeast(1)
                                    )
                                }
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Decrease threshold")
                            }
                            FilledTonalIconButton(
                                enabled = settings.isLowBatteryEnabled && settings.lowBatteryThreshold < 99,
                                onClick = {
                                    viewModel.updateLowBattery(
                                        context,
                                        settings.isLowBatteryEnabled,
                                        (settings.lowBatteryThreshold + 1).coerceAtMost(99)
                                    )
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Increase threshold")
                            }
                        }
                    }

                    Slider(
                        value = settings.lowBatteryThreshold.toFloat(),
                        onValueChange = { raw ->
                            viewModel.updateLowBattery(
                                context,
                                settings.isLowBatteryEnabled,
                                raw.roundToInt().coerceIn(1, 99)
                            )
                        },
                        valueRange = 1f..99f,
                        enabled = settings.isLowBatteryEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = SoftTeal,
                            activeTrackColor = SoftTeal
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1%", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Fires once; resets above ${(settings.lowBatteryThreshold + 2).coerceAtMost(100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("99%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 2. SIM 1 Signal Trigger (with live signal strength readout)
            SimSignalCard(
                title = "SIM 1 Signal Trigger",
                slotStatus = uiState.dualSimStatus.sim1,
                isEnabled = settings.isSim1TriggerEnabled,
                thresholdDbm = settings.sim1ThresholdDbm,
                notDetectedText = "SIM 1 Not Detected",
                onToggle = { enabled ->
                    viewModel.updateSimTrigger(context, 0, enabled, settings.sim1ThresholdDbm)
                },
                onThresholdChange = { dbm ->
                    viewModel.updateSimTrigger(context, 0, settings.isSim1TriggerEnabled, dbm)
                }
            )

            // 3. SIM 2 Signal Trigger (with live signal strength readout)
            SimSignalCard(
                title = "SIM 2 Signal Trigger",
                slotStatus = uiState.dualSimStatus.sim2,
                isEnabled = settings.isSim2TriggerEnabled,
                thresholdDbm = settings.sim2ThresholdDbm,
                notDetectedText = "SIM 2 Not Detected",
                onToggle = { enabled ->
                    viewModel.updateSimTrigger(context, 1, enabled, settings.sim2ThresholdDbm)
                },
                onThresholdChange = { dbm ->
                    viewModel.updateSimTrigger(context, 1, settings.isSim2TriggerEnabled, dbm)
                }
            )

            // 4. Time-Based Periodic Trigger (2 min preset + custom 1+ min stepper/input)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = SoftTeal
                            )
                            Text(
                                text = "Periodic Location Updates",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Switch(
                            checked = settings.isPeriodicEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePeriodicTrigger(
                                    context,
                                    enabled,
                                    settings.periodicIntervalMinutes
                                )
                            }
                        )
                    }

                    Text(
                        text = if (settings.periodicIntervalMinutes < 15) {
                            "Every ${settings.periodicIntervalMinutes} min · Running via Foreground Service precision loop"
                        } else {
                            "Every ${settings.periodicIntervalMinutes} min · Scheduled via WorkManager"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val presetIntervals = listOf(2, 5, 15, 30, 60)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetIntervals.forEach { minutes ->
                            FilterChip(
                                selected = settings.periodicIntervalMinutes == minutes,
                                enabled = settings.isPeriodicEnabled,
                                onClick = {
                                    customIntervalText = minutes.toString()
                                    viewModel.updatePeriodicTrigger(
                                        context,
                                        settings.isPeriodicEnabled,
                                        minutes
                                    )
                                },
                                label = { Text("$minutes min") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }

                    // Custom number input + stepper (supports 1 min or any custom integer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(
                            enabled = settings.isPeriodicEnabled && settings.periodicIntervalMinutes > 1,
                            onClick = {
                                val next = (settings.periodicIntervalMinutes - 1).coerceAtLeast(1)
                                customIntervalText = next.toString()
                                viewModel.updatePeriodicTrigger(
                                    context,
                                    settings.isPeriodicEnabled,
                                    next
                                )
                            }
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease interval")
                        }

                        OutlinedTextField(
                            value = customIntervalText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                customIntervalText = digits
                                val parsed = digits.toIntOrNull()
                                if (parsed != null && parsed >= 1) {
                                    viewModel.updatePeriodicTrigger(
                                        context,
                                        settings.isPeriodicEnabled,
                                        parsed
                                    )
                                }
                            },
                            enabled = settings.isPeriodicEnabled,
                            label = { Text("Custom Interval (min)") },
                            placeholder = { Text("e.g. 1 or 2") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalIconButton(
                            enabled = settings.isPeriodicEnabled,
                            onClick = {
                                val next = settings.periodicIntervalMinutes + 1
                                customIntervalText = next.toString()
                                viewModel.updatePeriodicTrigger(
                                    context,
                                    settings.isPeriodicEnabled,
                                    next
                                )
                            }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase interval")
                        }
                    }
                }
            }

            // 5. On-Demand Manual Trigger
            Button(
                onClick = viewModel::sendNow,
                enabled = !uiState.isSendingNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepNavy,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isSendingNow) {
                        "Sending Location & Status..."
                    } else {
                        "Send Location & Status Now"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Offline Queue Card (if items are queued in Room)
            if (uiState.pendingQueueCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.pendingQueueCount} Offline Alert(s) Queued",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Stored in Room; auto-syncs when connected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = viewModel::retryQueuedAlerts) {
                            Icon(Icons.Rounded.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimSignalCard(
    title: String,
    slotStatus: SimSlotStatus,
    isEnabled: Boolean,
    thresholdDbm: Int,
    notDetectedText: String,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SimCard,
                        contentDescription = null,
                        tint = SoftTeal
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (slotStatus.isDetected) {
                                slotStatus.carrierName.ifBlank { "Active SIM" }
                            } else {
                                notDetectedText
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }

            // Live Current Signal Strength Readout
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Signal Strength",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dbm = slotStatus.currentDbm
                    val (readingText, readingColor) = when {
                        !slotStatus.isDetected -> "Not Detected" to MaterialTheme.colorScheme.onSurfaceVariant
                        dbm == null -> "Reading..." to MaterialTheme.colorScheme.onSurfaceVariant
                        dbm >= -85 -> "$dbm dBm (Strong)" to SuccessEmerald
                        dbm >= -100 -> "$dbm dBm (Fair)" to WarningAmber
                        else -> "$dbm dBm (Weak)" to AlertCrimson
                    }
                    Text(
                        text = readingText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = readingColor
                    )
                }
            }

            Text(
                text = "Trigger when signal \u2264 $thresholdDbm dBm",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Slider(
                value = thresholdDbm.toFloat(),
                onValueChange = { raw -> onThresholdChange(raw.roundToInt()) },
                valueRange = -120f..-50f,
                enabled = isEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = SoftTeal,
                    activeTrackColor = SoftTeal
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-120 dBm (Very Weak)", style = MaterialTheme.typography.labelSmall)
                Text("15 min cooldown", style = MaterialTheme.typography.labelSmall)
                Text("-50 dBm (Strong)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
