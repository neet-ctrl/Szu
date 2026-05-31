package com.accu.ui.appmanager

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

data class DebloatLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val action: LogAction,
    val success: Boolean,
    val errorMessage: String? = null,
)

enum class LogAction(val label: String, val color: @Composable () -> Color) {
    UNINSTALL("Uninstalled", { MaterialTheme.colorScheme.error }),
    FREEZE("Frozen", { MaterialTheme.colorScheme.primary }),
    HIDE("Hidden", { MaterialTheme.colorScheme.secondary }),
    RESTORE("Restored", { MaterialTheme.colorScheme.tertiary }),
    SUSPEND("Suspended", { MaterialTheme.colorScheme.primary }),
}

object DebloatLogger {
    private val _logs = mutableStateListOf<DebloatLogEntry>()
    val logs: List<DebloatLogEntry> get() = _logs

    fun log(packageName: String, action: LogAction, success: Boolean, error: String? = null) {
        _logs.add(0, DebloatLogEntry(packageName = packageName, action = action, success = success, errorMessage = error))
        if (_logs.size > 500) _logs.removeAt(_logs.lastIndex)
    }

    fun clear() = _logs.clear()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CantaLogsScreen(onBack: () -> Unit) {
    val logs = DebloatLogger.logs
    val clipboard = LocalClipboardManager.current
    val sdf = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
    var filterAction by remember { mutableStateOf<LogAction?>(null) }

    val filtered = if (filterAction == null) logs else logs.filter { it.action == filterAction }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debloat Logs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        val text = filtered.joinToString("\n") { entry ->
                            "[${sdf.format(Date(entry.timestamp))}] ${if (entry.success) "✓" else "✗"} ${entry.action.label}: ${entry.packageName}${entry.errorMessage?.let { " — $it" } ?: ""}"
                        }
                        clipboard.setText(AnnotatedString(text))
                    }) { Icon(Icons.Default.ContentCopy, "Copy logs") }
                    IconButton(onClick = { DebloatLogger.clear() }) { Icon(Icons.Default.DeleteSweep, "Clear") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filterAction == null,
                        onClick = { filterAction = null },
                        label = { Text("All (${logs.size})") },
                    )
                }
                items(LogAction.entries) { action ->
                    FilterChip(
                        selected = filterAction == action,
                        onClick = { filterAction = if (filterAction == action) null else action },
                        label = { Text(action.label) },
                    )
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Article, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(16.dp))
                        Text("No logs yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.timestamp.toString() + it.packageName }) { entry ->
                        LogEntryCard(entry = entry, sdf = sdf)
                    }
                }
            }
        }
    }
}

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
                    Surface(
                        color = entry.action.color().copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            entry.action.label,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = entry.action.color(),
                            fontWeight = FontWeight.Bold,
                        )
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
