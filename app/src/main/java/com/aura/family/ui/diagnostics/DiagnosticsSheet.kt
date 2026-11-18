package com.aura.family.ui.diagnostics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import com.aura.family.background.ServiceDiagnosticsState
import com.aura.family.data.local.PendingTelemetryEntity
import com.aura.family.data.preferences.AuraSettings
import com.aura.family.data.remote.NetworkModule
import com.aura.family.telemetry.DualSimStatus
import com.aura.family.ui.theme.AlertCrimson
import com.aura.family.ui.theme.DeepNavy
import com.aura.family.ui.theme.SoftTeal
import com.aura.family.ui.theme.SuccessEmerald
import com.aura.family.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsDialog(
    settings: AuraSettings,
    serviceDiagnostics: ServiceDiagnosticsState,
    dualSimStatus: DualSimStatus,
    pendingItems: List<PendingTelemetryEntity>,
    periodicWorkState: WorkInfo.State?,
    syncWorkState: WorkInfo.State?,
    onFlushQueue: () -> Unit,
    onClearQueue: () -> Unit,
    onRestartService: () -> Unit,
    onRefreshSignals: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    val fineLocationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val bgLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val phoneStateGranted = ContextCompat.checkSelfPermission(
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Aura Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Background verification & offline Room queue",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close diagnostics")
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Background Service Verification
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Background Execution Proof",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (serviceDiagnostics.isRunning) "RUNNING" else "STOPPED",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (serviceDiagnostics.isRunning) SuccessEmerald else AlertCrimson
                            )
                        }

                        DiagnosticRow(
                            label = "Foreground Service",
                            value = if (serviceDiagnostics.isRunning) {
                                "AuraMonitoringService Active"
                            } else {
                                "Not Running"
                            }
                        )
                        DiagnosticRow(
                            label = "Service Started",
                            value = formatEpoch(serviceDiagnostics.startedAtMs)
                        )
                        DiagnosticRow(
                            label = "Last Heartbeat",
                            value = formatEpoch(serviceDiagnostics.lastHeartbeatMs)
                        )
                        DiagnosticRow(
                            label = "Scheduler Mode",
                            value = serviceDiagnostics.activeSchedulerMode
                        )
                        DiagnosticRow(
                            label = "WorkManager Periodic",
                            value = periodicWorkState?.name ?: if (settings.periodicIntervalMinutes < 15) {
                                "Bypassed (<15m uses Service Loop)"
                            } else {
                                "Not Enqueued"
                            }
                        )
                        DiagnosticRow(
                            label = "WorkManager Sync",
                            value = syncWorkState?.name ?: "Idle"
                        )
                        DiagnosticRow(
                            label = "Battery Optimization",
                            value = if (batteryExempt) "Exempted (Unrestricted)" else "Optimized (At Risk)"
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRestartService,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start / Ping Service")
                            }
                            if (!batteryExempt) {
                                OutlinedButton(
                                    onClick = {
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
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Exempt Battery")
                                }
                            }
                        }
                    }
                }

                // 2. Room Queue Inspector
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Offline Queue (Room)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${pendingItems.size} queued",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (pendingItems.isEmpty()) SuccessEmerald else WarningAmber
                            )
                        }

                        if (pendingItems.isEmpty()) {
                            Text(
                                text = "Queue is empty. Failed or offline telemetry payloads appear here until delivered.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onFlushQueue,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DeepNavy,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Rounded.Sync, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Flush Queue")
                                }
                                OutlinedButton(
                                    onClick = onClearQueue,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear")
                                }
                            }

                            pendingItems.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "#${item.id} · ${item.remarks}",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Text(
                                                text = "Retries: ${item.attemptCount}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "Queued: ${formatEpoch(item.createdAtMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "POST ${item.endpointUrl}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = item.payloadJson,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = Color(0xFFE2E8F0),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DeepNavy, RoundedCornerShape(8.dp))
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Live Signals, Endpoints & Permissions
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Device & Endpoint Info",
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = onRefreshSignals) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Refresh signals",
                                    tint = SoftTeal
                                )
                            }
                        }

                        DiagnosticRow(
                            label = "Home WebView URL",
                            value = settings.effectiveHomeUrl.ifBlank { "Not configured" }
                        )
                        DiagnosticRow(
                            label = "Telemetry Endpoint",
                            value = NetworkModule.buildTelemetryEndpoint(settings.baseUrl)
                                ?: "Not configured"
                        )
                        DiagnosticRow(
                            label = "SIM 1 Signal",
                            value = if (dualSimStatus.sim1.isDetected) {
                                "${dualSimStatus.sim1.carrierName.ifBlank { "SIM 1" }} · ${dualSimStatus.sim1.currentDbm?.let { "$it dBm" } ?: "Awaiting sample"}"
                            } else {
                                "Not Detected"
                            }
                        )
                        DiagnosticRow(
                            label = "SIM 2 Signal",
                            value = if (dualSimStatus.sim2.isDetected) {
                                "${dualSimStatus.sim2.carrierName.ifBlank { "SIM 2" }} · ${dualSimStatus.sim2.currentDbm?.let { "$it dBm" } ?: "Awaiting sample"}"
                            } else {
                                "Not Detected"
                            }
                        )
                        DiagnosticRow(
                            label = "Low Battery Latch",
                            value = if (settings.lowBatteryTriggered) {
                                "Triggered (resets > ${settings.lowBatteryThreshold + 2}%)"
                            } else {
                                "Armed (≤ ${settings.lowBatteryThreshold}%)"
                            }
                        )
                        DiagnosticRow(
                            label = "Last Telemetry",
                            value = settings.lastTelemetrySummary.ifBlank { "None yet" }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DiagnosticRow(
                            label = "Fine / Bg Location",
                            value = "${yesNo(fineLocationGranted)} / ${yesNo(bgLocationGranted)}"
                        )
                        DiagnosticRow(
                            label = "Phone / Notifications",
                            value = "${yesNo(phoneStateGranted)} / ${yesNo(notifGranted)}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

private fun yesNo(granted: Boolean): String = if (granted) "Granted" else "Missing"

private fun formatEpoch(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
