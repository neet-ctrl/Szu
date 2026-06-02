package com.accu.ui.appmanager

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

// ── Log model ─────────────────────────────────────────────────────────────────

data class DebloatLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val action: LogAction,
    val success: Boolean,
    val errorMessage: String? = null,
    val deviceModel: String = android.os.Build.MODEL,
    val androidVersion: Int = android.os.Build.VERSION.SDK_INT,
)

enum class LogAction(val label: String, val color: @Composable () -> Color) {
    UNINSTALL("Uninstalled", { MaterialTheme.colorScheme.error }),
    FREEZE("Frozen", { MaterialTheme.colorScheme.primary }),
    HIDE("Hidden", { MaterialTheme.colorScheme.secondary }),
    RESTORE("Restored", { MaterialTheme.colorScheme.tertiary }),
    SUSPEND("Suspended", { MaterialTheme.colorScheme.primary }),
    REINSTALL("Reinstalled", { MaterialTheme.colorScheme.tertiary }),
}

// ── Global logger ─────────────────────────────────────────────────────────────

object DebloatLogger {
    private val _logs = mutableStateListOf<DebloatLogEntry>()
    val logs: List<DebloatLogEntry> get() = _logs

    fun log(packageName: String, action: LogAction, success: Boolean, error: String? = null) {
        _logs.add(0, DebloatLogEntry(packageName = packageName, action = action, success = success, errorMessage = error))
        if (_logs.size > 1000) _logs.removeAt(_logs.lastIndex)
    }

    fun clear() = _logs.clear()

    fun toText(filter: LogAction? = null): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return (if (filter == null) _logs else _logs.filter { it.action == filter })
            .joinToString("\n") { entry ->
                "[${sdf.format(Date(entry.timestamp))}] ${if (entry.success) "SUCCESS" else "FAILED "} ${entry.action.label.padEnd(12)} ${entry.packageName}${entry.errorMessage?.let { " — $it" } ?: ""}"
            }
    }
}

// ── Screen ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CantaLogsScreen(onBack: () -> Unit) {
    val logs = DebloatLogger.logs
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sdf = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
    var filterAction by remember { mutableStateOf<LogAction?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    // SAF launcher — system file picker, user chooses where to save
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val text = pendingExportText
        pendingExportText = null
        if (uri != null && text != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                snackbar = "Logs exported to selected location"
            } catch (e: Exception) {
                snackbar = "Export failed: ${e.message}"
            }
        }
    }

    val filtered = if (filterAction == null) logs else logs.filter { it.action == filterAction }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Debloat Logs")
                        Text("${filtered.size} entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showStats = true }) { Icon(Icons.Default.BarChart, "Stats") }
                    IconButton(onClick = {
                        val text = DebloatLogger.toText(filterAction)
                        clipboard.setText(AnnotatedString(text))
                        snackbar = "Copied ${filtered.size} log entries"
                    }) { Icon(Icons.Default.ContentCopy, "Copy logs") }
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) { Icon(Icons.Default.IosShare, "Export") }
                        DropdownMenu(showExportMenu, { showExportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Save to…") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                onClick = {
                                    showExportMenu = false
                                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    pendingExportText = DebloatLogger.toText()
                                    exportLauncher.launch("accu_debloat_logs_$ts.txt")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    showExportMenu = false
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, DebloatLogger.toText(filterAction))
                                        putExtra(Intent.EXTRA_SUBJECT, "ACCU Debloat Logs")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
                                },
                            )
                        }
                    }
                    IconButton(onClick = { DebloatLogger.clear() }) { Icon(Icons.Default.DeleteSweep, "Clear") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Stats banner ───────────────────────────────────────────────
            val successCount = logs.count { it.success }
            val failCount = logs.count { !it.success }
            if (logs.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogStatChip("${logs.size}", "Total", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    LogStatChip("$successCount", "Success", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    LogStatChip("$failCount", "Failed", MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }

            // ── Filter chips ───────────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filterAction == null,
                        onClick = { filterAction = null },
                        label = { Text("All (${logs.size})") },
                        leadingIcon = { Icon(Icons.Default.Article, null, Modifier.size(14.dp)) },
                    )
                }
                items(LogAction.entries) { action ->
                    val actionCount = logs.count { it.action == action }
                    FilterChip(
                        selected = filterAction == action,
                        onClick = { filterAction = if (filterAction == action) null else action },
                        label = { Text("${action.label} ($actionCount)") },
                    )
                }
            }

            // ── Log list ───────────────────────────────────────────────────
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Article, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(16.dp))
                        Text(if (logs.isEmpty()) "No logs yet" else "No logs for this filter", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                        if (logs.isEmpty()) Text("Perform debloat, freeze, or hide actions to see logs here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { "${it.timestamp}_${it.packageName}_${it.action}" }) { entry ->
                        LogEntryCard(entry = entry, sdf = sdf)
                    }
                }
            }
        }
    }

    // ── Stats dialog ───────────────────────────────────────────────────────
    if (showStats) {
        AlertDialog(
            onDismissRequest = { showStats = false },
            icon = { Icon(Icons.Default.BarChart, null) },
            title = { Text("Log Statistics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Total Actions: ${logs.size}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    LogAction.entries.forEach { action ->
                        val count = logs.count { it.action == action }
                        val succeedCount = logs.count { it.action == action && it.success }
                        if (count > 0) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(4.dp), color = action.color().copy(0.15f)) {
                                    Text(action.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = action.color(), fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("$count total", style = MaterialTheme.typography.bodySmall)
                                    Text("$succeedCount success, ${count - succeedCount} failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showStats = false }) { Text("Close") } },
        )
    }
}

// ── Log entry card ─────────────────────────────────────────────────────────────

@Composable
private fun LogEntryCard(entry: DebloatLogEntry, sdf: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.success)
                MaterialTheme.colorScheme.surfaceContainer
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (entry.success) Icons.Default.CheckCircle else Icons.Default.Error,
                null,
                tint = if (entry.success) entry.action.color() else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = entry.action.color().copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            entry.action.label,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = entry.action.color(),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!entry.success) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("Failed", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                if (entry.errorMessage != null) {
                    Text(
                        entry.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                sdf.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ── Stat chip ─────────────────────────────────────────────────────────────────

@Composable
private fun LogStatChip(value: String, label: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
