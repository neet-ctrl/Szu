package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accu.sdk.AccuConnectionState
import com.accu.sdk.AccuConstants
import com.accu.sdk.isGranted
import com.accu.sdk.toPermissionLabel
import com.accu.sdkdemo.ui.components.*
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val state by vm.accuState.collectAsState()
    val logs  by vm.logs.collectAsState()
    val crashes by vm.crashes.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Connection overview card ──────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Connection Status")
                val (connColor, connLabel) = when (state) {
                    is AccuConnectionState.Connected   -> StatusColor.GREEN  to "CONNECTED"
                    AccuConnectionState.Connecting     -> StatusColor.YELLOW to "CONNECTING…"
                    AccuConnectionState.Disconnected   -> StatusColor.RED    to "DISCONNECTED"
                    is AccuConnectionState.Error       -> StatusColor.RED    to "ERROR"
                    else                               -> StatusColor.GREY   to "IDLE"
                }
                StatusRow("Service", connLabel, connColor)

                if (state is AccuConnectionState.Connected) {
                    val c = state as AccuConnectionState.Connected
                    StatusRow("Permission", c.permissionCode.toPermissionLabel(), if (c.permissionCode.isGranted()) StatusColor.GREEN else StatusColor.YELLOW)
                    StatusRow("ACCU Version", c.accuVersion, StatusColor.GREEN)
                    StatusRow("Protocol", "v${c.serviceVersion}", if (c.serviceVersion == AccuConstants.PROTOCOL_VERSION) StatusColor.GREEN else StatusColor.YELLOW)
                } else if (state is AccuConnectionState.Error) {
                    Text((state as AccuConnectionState.Error).reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ── Quick actions ─────────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Quick Actions")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.reconnect() }, modifier = Modifier.weight(1f)) { Text("Reconnect") }
                    OutlinedButton(onClick = { vm.ping() }, modifier = Modifier.weight(1f)) { Text("Ping") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.requestPermission() }, modifier = Modifier.weight(1f)) { Text("Request Permission") }
                    OutlinedButton(onClick = { vm.checkPermission() }, modifier = Modifier.weight(1f)) { Text("Check Permission") }
                }
            }
        }

        // ── Summary stats ─────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(modifier = Modifier.weight(1f), icon = Icons.Default.List,   label = "Log Entries", value = logs.size.toString(),    color = MaterialTheme.colorScheme.primary)
            SummaryCard(modifier = Modifier.weight(1f), icon = Icons.Default.BugReport, label = "Crashes",  value = crashes.size.toString(), color = if (crashes.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }

        // ── App info ──────────────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader("App Info")
                InfoRow("Test App Package", "com.accu.sdkdemo")
                InfoRow("ACCU Package",     AccuConstants.ACCU_PACKAGE)
                InfoRow("Protocol Version", "v${AccuConstants.PROTOCOL_VERSION}")
                InfoRow("AIDL Methods",     "25 (5 identity + 4 permission + 3 shell + 12 package + 4 perm-ops + 1 locale + 6 settings)")
            }
        }

        // ── Recent log preview ────────────────────────────────────────────────
        if (logs.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader("Recent Logs (last 5)")
                    logs.takeLast(5).reversed().forEach { entry ->
                        Text("[${entry.formattedTime}] [${entry.tag}] ${entry.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, icon: ImageVector, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = color))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    }
}
