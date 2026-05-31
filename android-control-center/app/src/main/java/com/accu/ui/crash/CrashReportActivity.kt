package com.accu.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.accu.crash.CrashEngine
import com.accu.crash.CrashNotificationManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen crash report Activity.
 * Runs in :crash process — no Hilt, no Room, no injected dependencies.
 * Reads crash data from the pending JSON file written by CrashEngine.
 */
class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val crashId = intent.getStringExtra("crash_id") ?: ""
        val crash = if (crashId.isNotBlank()) {
            CrashEngine.readPendingCrashFile(this, crashId)
        } else null

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF6B6B),
                    background = Color(0xFF0D0D0D),
                    surface = Color(0xFF1A1A1A),
                    onBackground = Color(0xFFE0E0E0),
                    onSurface = Color(0xFFE0E0E0),
                )
            ) {
                CrashReportScreen(
                    crash = crash,
                    onClose = { finishAndRemoveTask() },
                    onRestart = { restartApp() },
                    onOpenInApp = { openInMainApp(crashId) },
                    onCopy = { text -> copyToClipboard(text) },
                    onShare = { text -> shareText(text) },
                )
            }
        }
    }

    private fun restartApp() {
        CrashNotificationManager.cancelNotification(this, 9900 + (intent.getStringExtra("crash_id")?.hashCode()?.and(0x0FFF) ?: 0))
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) startActivity(launch)
        finishAndRemoveTask()
    }

    private fun openInMainApp(crashId: String) {
        val intent = Intent().setClassName(this, "com.accu.MainActivity")
            .putExtra("navigate_to", "crash_detail/$crashId")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finishAndRemoveTask()
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("ACCU Crash Log", text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "ACCU Crash Report")
            }, "Share Crash Report"
        ))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashReportScreen(
    crash: JSONObject?,
    onClose: () -> Unit,
    onRestart: () -> Unit,
    onOpenInApp: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US) }
    val exceptionType = crash?.optString("exceptionType") ?: "Unknown Exception"
    val exceptionMsg = crash?.optString("exceptionMessage") ?: "No message"
    val stackTrace = crash?.optString("stackTrace") ?: ""
    val timestamp = crash?.optLong("timestamp") ?: System.currentTimeMillis()
    val deviceModel = crash?.optString("deviceModel") ?: "Unknown device"
    val androidVer = crash?.optString("androidVersion") ?: "?"
    val appVersion = crash?.optString("appVersion") ?: "?"
    val route = crash?.optString("screenRoute") ?: ""
    val thread = crash?.optString("threadName") ?: ""
    val kind = crash?.optString("crashKind") ?: "JAVA"
    val isFatal = crash?.optBoolean("isFatal") ?: true
    val isAnr = crash?.optBoolean("isAnr") ?: false

    var showFullTrace by remember { mutableStateOf(false) }

    val fullText = buildString {
        appendLine("ACCU Crash Report — ${dateFormat.format(Date(timestamp))}")
        appendLine("${exceptionType}: $exceptionMsg")
        appendLine()
        appendLine(stackTrace)
        crash?.optString("causeChain")?.let {
            if (it.isNotBlank() && it != "[]") { appendLine(); appendLine("Cause chain: $it") }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D0D0D),
        topBar = {
            TopAppBar(
                title = { Text("App Crashed", fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFFE0E0E0))
                    }
                },
                actions = {
                    IconButton(onClick = { onCopy(fullText) }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = Color(0xFFE0E0E0))
                    }
                    IconButton(onClick = { onShare(fullText) }) {
                        Icon(Icons.Default.Share, "Share", tint = Color(0xFFE0E0E0))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A)),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Hero crash banner ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F0D0D))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(28.dp))
                        Column {
                            Text(
                                exceptionType.substringAfterLast('.'),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B6B),
                            )
                            Text(
                                dateFormat.format(Date(timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9E9E9E),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CrashBadge(if (isFatal) "FATAL" else "NON-FATAL", if (isFatal) Color(0xFFFF4444) else Color(0xFFFF8800))
                        CrashBadge(kind, Color(0xFF6B9FFF))
                        if (isAnr) CrashBadge("ANR", Color(0xFFFF6B00))
                        if (route.isNotBlank()) CrashBadge(route, Color(0xFF888888))
                    }
                    Text(
                        exceptionMsg.take(200),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFB3B3),
                    )
                }
            }

            // ── Info chips ──
            CrashSection(title = "Device") {
                InfoRow("Device", deviceModel)
                InfoRow("Android", androidVer)
                InfoRow("App Version", appVersion)
                InfoRow("Thread", thread)
            }

            // ── Stack Trace ──
            CrashSection(title = "Stack Trace") {
                val tracePreview = if (showFullTrace) stackTrace else stackTrace.lines().take(20).joinToString("\n")
                Text(
                    tracePreview,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFCCCCCC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (stackTrace.lines().size > 20) {
                    TextButton(
                        onClick = { showFullTrace = !showFullTrace },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            if (showFullTrace) "Show less" else "Show full trace (${stackTrace.lines().size} lines)",
                            color = Color(0xFF81C995),
                        )
                    }
                }
            }

            // ── Actions ──
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRestart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restart ACCU")
                    }
                    OutlinedButton(
                        onClick = onOpenInApp,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF555555)),
                    ) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp), tint = Color(0xFFE0E0E0))
                        Spacer(Modifier.width(6.dp))
                        Text("Full Details", color = Color(0xFFE0E0E0))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onCopy(fullText) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF555555)),
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp), tint = Color(0xFFE0E0E0))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Log", color = Color(0xFFE0E0E0), fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { onShare(fullText) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF555555)),
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(16.dp), tint = Color(0xFFE0E0E0))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", color = Color(0xFFE0E0E0), fontSize = 13.sp)
                    }
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                ) {
                    Text("Close Without Restarting", color = Color(0xFF9E9E9E), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CrashSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF81C995),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1A1A1A),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9E9E9E))
        Text(value.take(40), style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0E0E0), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CrashBadge(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.2f)) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
