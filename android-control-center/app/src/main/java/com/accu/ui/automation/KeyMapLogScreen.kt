package com.accu.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class KeyMapLogEntry(
    val timestamp: String,
    val level: String, // INFO, DEBUG, ERROR, WARN
    val message: String,
    val keyMap: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyMapLogScreen(onBack: () -> Unit = {}) {
    val entries = remember {
        listOf(
            KeyMapLogEntry("07:12:44.021", "INFO", "Key mapper service started", null),
            KeyMapLogEntry("07:12:44.215", "DEBUG", "Accessibility service connected", null),
            KeyMapLogEntry("07:12:44.310", "INFO", "Loaded 7 key maps", null),
            KeyMapLogEntry("07:13:02.440", "DEBUG", "KEY_DOWN VolumeDown detected (500ms hold)", "Vol Down Hold"),
            KeyMapLogEntry("07:13:02.943", "INFO", "Action performed: Toggle Flashlight", "Vol Down Hold"),
            KeyMapLogEntry("07:14:10.120", "DEBUG", "KEY_DOWN Power double-press detected", "Power Double"),
            KeyMapLogEntry("07:14:10.130", "INFO", "Constraint check: Screen on ✓", "Power Double"),
            KeyMapLogEntry("07:14:10.145", "INFO", "Action performed: Open Camera", "Power Double"),
            KeyMapLogEntry("07:14:55.300", "WARN", "Accessibility service lost focus briefly", null),
            KeyMapLogEntry("07:14:55.310", "INFO", "Accessibility service reconnected", null),
            KeyMapLogEntry("07:15:30.700", "DEBUG", "KEY_DOWN VolumeDown detected (short press)", "Vol Down Hold"),
            KeyMapLogEntry("07:15:30.710", "DEBUG", "Not a hold — below threshold 500ms", "Vol Down Hold"),
            KeyMapLogEntry("07:15:30.720", "DEBUG", "Key map not triggered (short press)", "Vol Down Hold"),
            KeyMapLogEntry("07:16:01.000", "ERROR", "Floating button trigger: Accessibility not running", "Floating Button"),
            KeyMapLogEntry("07:16:01.002", "ERROR", "Fix: Enable Key Mapper in Settings > Accessibility", "Floating Button"),
        ).reversed()
    }
    var filter by remember { mutableStateOf("ALL") }
    var showClearDialog by remember { mutableStateOf(false) }

    val filtered = if (filter == "ALL") entries else entries.filter { it.level == filter }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear log?") },
            text = { Text("This will remove all log entries.") },
            confirmButton = { TextButton(onClick = { showClearDialog = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Event Log",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showClearDialog = true }) { Icon(Icons.Default.DeleteSweep, "Clear log") }
                    IconButton(onClick = {}) { Icon(Icons.Default.Share, "Export log") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL", "INFO", "DEBUG", "WARN", "ERROR").forEach { level ->
                    FilterChip(
                        selected = filter == level,
                        onClick = { filter = level },
                        label = { Text(level, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (level) {
                                "ERROR" -> MaterialTheme.colorScheme.errorContainer
                                "WARN" -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                    )
                }
            }

            // Log
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No log entries", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(filtered) { entry ->
                        val levelColor = when (entry.level) {
                            "ERROR" -> MaterialTheme.colorScheme.error
                            "WARN" -> MaterialTheme.colorScheme.tertiary
                            "DEBUG" -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        entry.level,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = levelColor,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    if (entry.keyMap != null) {
                                        Spacer(Modifier.width(6.dp))
                                        SuggestionChip(onClick = {}, label = { Text(entry.keyMap, fontSize = 9.sp) }, modifier = Modifier.height(18.dp))
                                    }
                                }
                                Text(entry.message, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
