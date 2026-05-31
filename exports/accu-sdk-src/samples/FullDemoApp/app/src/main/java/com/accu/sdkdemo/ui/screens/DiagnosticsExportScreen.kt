package com.accu.sdkdemo.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdk.AccuConnectionState
import com.accu.sdk.toPermissionLabel
import com.accu.sdkdemo.data.LogManager
import com.accu.sdkdemo.data.CrashManager
import com.accu.sdkdemo.ui.components.SectionHeader
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun DiagnosticsExportScreen(vm: MainViewModel) {
    val accuState  by vm.accuState.collectAsState()
    val logs       by vm.logs.collectAsState()
    val crashes    by vm.crashes.collectAsState()
    val tests      by vm.testResults.collectAsState()
    val diags      by vm.diagnostics.collectAsState()
    val clipboard  = LocalClipboardManager.current
    val context    = LocalContext.current
    var preview    by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ── Summary header card ───────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Diagnostics Export")
                val pass = tests.count { it.status.name == "PASS" }
                val fail = tests.count { it.status.name == "FAIL" }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip(Icons.Default.CheckCircle, "Tests", "$pass / ${tests.size}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatChip(Icons.Default.List,         "Logs",    logs.size.toString(),    MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    StatChip(Icons.Default.BugReport,    "Crashes", crashes.size.toString(), if (crashes.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }
        }

        // ── Connection status ─────────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader("Current Connection State")
                Text("State: $accuState", style = MaterialTheme.typography.bodySmall)
                if (accuState is AccuConnectionState.Connected) {
                    val c = accuState as AccuConnectionState.Connected
                    Text("ACCU: ${c.accuVersion}", style = MaterialTheme.typography.bodySmall)
                    Text("Permission: ${c.permissionCode.toPermissionLabel()}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ── Actions ────────────────────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Export Actions")
                Button(onClick = {
                    preview = vm.buildDiagnosticsReport()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Full Diagnostics Report")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val text = vm.buildDiagnosticsReport()
                        clipboard.setText(AnnotatedString(text))
                        preview = text
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Report")
                    }
                    OutlinedButton(onClick = {
                        val json = LogManager.exportJson()
                        clipboard.setText(AnnotatedString(json))
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Code, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Logs JSON")
                    }
                }
                OutlinedButton(onClick = {
                    val report = vm.buildDiagnosticsReport()
                    shareText(context, report, "ACCU Diagnostics Report")
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share via Android Share Sheet")
                }
                OutlinedButton(onClick = {
                    val crashReport = CrashManager.exportText()
                    clipboard.setText(AnnotatedString(crashReport))
                }, modifier = Modifier.fillMaxWidth(), enabled = crashes.isNotEmpty()) {
                    Icon(Icons.Default.BugReport, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Crash Report (${crashes.size} crashes)")
                }
            }
        }

        // ── Preview ────────────────────────────────────────────────────────────
        if (preview != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Report Preview")
                        TextButton(onClick = { preview = null }) { Text("Close") }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text(preview!!, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = color))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shareText(context: Context, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, title) }
    context.startActivity(Intent.createChooser(intent, title))
}
