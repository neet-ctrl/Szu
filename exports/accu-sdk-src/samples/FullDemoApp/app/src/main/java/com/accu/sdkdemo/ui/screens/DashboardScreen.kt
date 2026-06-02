package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdk.AccuConnectionState
import com.accu.sdk.AccuConstants
import com.accu.sdk.isGranted
import com.accu.sdk.toPermissionLabel
import com.accu.sdkdemo.data.LogEntry
import com.accu.sdkdemo.data.LogLevel
import com.accu.sdkdemo.data.LogManager
import com.accu.sdkdemo.ui.components.*
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val state   by vm.accuState.collectAsState()
    val logs    by vm.logs.collectAsState()
    val crashes by vm.crashes.collectAsState()
    val clipboard  = LocalClipboardManager.current
    val termState  = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    // Auto-scroll to bottom of page (terminal is the last card) when new logs arrive
    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            try { termState.animateScrollToItem(6) } catch (_: Exception) {}
        }
    }

    // Outer scrollable list — all cards are separate items so the terminal LazyColumn
    // never sits inside a scrollable Column (avoids the infinite-height constraint crash)
    LazyColumn(
        state = termState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ── Connection overview ───────────────────────────────────────────────
        item {
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
        }

        // ── Quick actions ─────────────────────────────────────────────────────
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Quick Actions")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.reconnect() }, modifier = Modifier.weight(1f)) { Text("Reconnect") }
                        OutlinedButton(onClick = { vm.ping() },      modifier = Modifier.weight(1f)) { Text("Ping") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.requestPermission() }, modifier = Modifier.weight(1f)) { Text("Request Permission") }
                        OutlinedButton(onClick = { vm.checkPermission() }, modifier = Modifier.weight(1f)) { Text("Check Permission") }
                    }
                }
            }
        }

        // ── Summary stats ─────────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(modifier = Modifier.weight(1f), icon = Icons.Default.List,      label = "Log Entries", value = logs.size.toString(),    color = MaterialTheme.colorScheme.primary)
                SummaryCard(modifier = Modifier.weight(1f), icon = Icons.Default.BugReport, label = "Crashes",     value = crashes.size.toString(), color = if (crashes.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }

        // ── App info ──────────────────────────────────────────────────────────
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("App Info")
                    InfoRow("Test App Package", "com.accu.sdkdemo")
                    InfoRow("ACCU Package",     AccuConstants.ACCU_PACKAGE)
                    InfoRow("Protocol Version", "v${AccuConstants.PROTOCOL_VERSION}")
                    InfoRow("AIDL Methods",     "25 (5 identity + 4 permission + 3 shell + 12 package + 4 perm-ops + 1 locale + 6 settings)")
                }
            }
        }

        // ── Live log terminal header ───────────────────────────────────────────
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Title + controls row
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (logs.isEmpty()) "Live Log Terminal" else "Live Log Terminal  (${logs.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        // Auto-scroll toggle
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Auto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(checked = autoScroll, onCheckedChange = { autoScroll = it }, modifier = Modifier.height(22.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        // Copy all
                        IconButton(onClick = { clipboard.setText(AnnotatedString(LogManager.exportText())) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy all logs", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Clear
                        IconButton(onClick = { LogManager.clear() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteSweep, "Clear logs", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Status bar
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = if (logs.isNotEmpty()) Color(0xFF3FB950) else Color(0xFF6E7681)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(shape = RoundedCornerShape(50), color = dotColor, modifier = Modifier.size(6.dp)) {}
                            Text(if (logs.isNotEmpty()) "LIVE" else "IDLE", style = MaterialTheme.typography.labelSmall, color = dotColor, fontFamily = FontFamily.Monospace)
                        }
                        if (logs.isNotEmpty()) {
                            Text("last: ${logs.last().formattedTime}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6E7681), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // ── Log terminal entries (dark background, no card wrapper) ───────────
        item {
            Surface(
                color = Color(0xFF0D1117),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("Waiting for logs…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF6E7681))
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        // Show last 200 entries for performance — full log available in Log Center screen
                        logs.takeLast(200).forEach { entry -> TerminalLogRow(entry) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TerminalLogRow(entry: LogEntry) {
    val levelColor: Color = when (entry.level) {
        LogLevel.DEBUG   -> Color(0xFF8B949E)
        LogLevel.INFO    -> Color(0xFFCDD9E5)
        LogLevel.SUCCESS -> Color(0xFF3FB950)
        LogLevel.WARNING -> Color(0xFFD29922)
        LogLevel.ERROR   -> Color(0xFFF85149)
    }
    val levelTag: String = when (entry.level) {
        LogLevel.DEBUG   -> "D"
        LogLevel.INFO    -> "I"
        LogLevel.SUCCESS -> "✓"
        LogLevel.WARNING -> "W"
        LogLevel.ERROR   -> "E"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(entry.formattedTime, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF6E7681), modifier = Modifier.width(68.dp))
        Text(levelTag, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = levelColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(10.dp))
        Text("[${entry.tag}]", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF58A6FF), modifier = Modifier.width(72.dp))
        Text(entry.message, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = levelColor, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, icon: ImageVector, label: String, value: String, color: Color) {
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
