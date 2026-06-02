package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.accu.sdk.AccuConnectionState
import com.accu.sdkdemo.data.DiagnosticItem
import com.accu.sdkdemo.data.DiagnosticStatus
import com.accu.sdkdemo.ui.components.*
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun ConnectionDiagnosticsScreen(vm: MainViewModel) {
    val diagnostics by vm.diagnostics.collectAsState()
    val isRunning   by vm.isDiagRunning.collectAsState()
    val accuState   by vm.accuState.collectAsState()
    val clipboard   = LocalClipboardManager.current
    var copied      by remember { mutableStateOf(false) }

    // Auto-reset "copied" feedback after 2 seconds
    LaunchedEffect(copied) {
        if (copied) { delay(2000); copied = false }
    }

    Column(Modifier.fillMaxSize()) {
        // Action bar
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.runDiagnostics() }, enabled = !isRunning, modifier = Modifier.weight(1f)) {
                        if (isRunning) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                        else Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isRunning) "Running…" else "Run Diagnostics")
                    }
                    // Copy diagnostics report
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(vm.buildDiagnosticsReport()))
                            copied = true
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                            "Copy diagnostics",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.connect() },    modifier = Modifier.weight(1f)) { Text("Connect") }
                    OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.weight(1f)) { Text("Disconnect") }
                    OutlinedButton(onClick = { vm.reconnect() },  modifier = Modifier.weight(1f)) { Text("Reconnect") }
                }
                OutlinedButton(onClick = { vm.ping() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Sensors, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ping Service")
                }
                if (copied) {
                    Text("✓ Diagnostics report copied to clipboard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Connection state banner
        val (statusColor, statusText) = when (val s = accuState) {
            is AccuConnectionState.Connected   -> StatusColor.GREEN  to "Connected — ACCU ${s.accuVersion}"
            AccuConnectionState.Connecting     -> StatusColor.YELLOW to "Connecting…"
            AccuConnectionState.Disconnected   -> StatusColor.RED    to "Disconnected"
            is AccuConnectionState.Error       -> StatusColor.RED    to "Error: ${s.reason}"
            else                               -> StatusColor.GREY   to "Idle"
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(statusColor)
            Text(statusText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }

        // Diagnostic list
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(diagnostics, key = { it.id }) { item ->
                DiagnosticCard(item)
            }
        }
    }
}

@Composable
private fun DiagnosticCard(item: DiagnosticItem) {
    val color = when (item.status) {
        DiagnosticStatus.PASS     -> StatusColor.GREEN
        DiagnosticStatus.FAIL     -> StatusColor.RED
        DiagnosticStatus.WARNING  -> StatusColor.YELLOW
        DiagnosticStatus.CHECKING -> StatusColor.YELLOW
        DiagnosticStatus.UNKNOWN  -> StatusColor.GREY
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.status == DiagnosticStatus.CHECKING) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                StatusBadge(item.status.name, color)
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.detail.isNotBlank()) {
                    Text(
                        item.detail,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = when (item.status) {
                            DiagnosticStatus.FAIL -> MaterialTheme.colorScheme.error
                            else                  -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
