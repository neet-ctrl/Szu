package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdkdemo.data.LogEntry
import com.accu.sdkdemo.data.LogLevel
import com.accu.sdkdemo.data.LogManager
import com.accu.sdkdemo.ui.theme.*
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun LogCenterScreen(vm: MainViewModel) {
    val logs      by vm.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    var search    by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val filtered = remember(logs, search, levelFilter) {
        logs.filter {
            (levelFilter == null || it.level == levelFilter) &&
            (search.isBlank() || it.message.contains(search, true) || it.tag.contains(search, true))
        }
    }

    // Auto-scroll to bottom (newest) on new entries
    LaunchedEffect(filtered.size) {
        if (autoScroll && filtered.isNotEmpty()) {
            try { listState.animateScrollToItem(filtered.size - 1) } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Toolbar ───────────────────────────────────────────────────────────
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        label = { Text("Search logs…") }, modifier = Modifier.weight(1f), singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } },
                    )
                    // Copy full log
                    IconButton(onClick = { clipboard.setText(AnnotatedString(LogManager.exportText())) }) {
                        Icon(Icons.Default.ContentCopy, "Copy all logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Clear
                    IconButton(onClick = { LogManager.clear() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear logs", tint = MaterialTheme.colorScheme.error)
                    }
                }

                // Level filter chips
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = levelFilter == null, onClick = { levelFilter = null }, label = { Text("All", fontSize = 10.sp) })
                    LogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = levelFilter == level,
                            onClick = { levelFilter = if (levelFilter == level) null else level },
                            label = { Text(level.name, fontSize = 10.sp, color = levelColor(level)) },
                        )
                    }
                }

                // Status row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Live indicator
                        val dotColor = if (logs.isNotEmpty()) Color(0xFF3FB950) else Color(0xFF6E7681)
                        Surface(shape = RoundedCornerShape(50), color = dotColor, modifier = Modifier.size(7.dp)) {}
                        Text(
                            if (filtered.size == logs.size) "${filtered.size} entries" else "${filtered.size} / ${logs.size} entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Auto-scroll", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = autoScroll, onCheckedChange = { autoScroll = it }, modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        // ── Terminal body ──────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Terminal, null, Modifier.size(48.dp), tint = Color(0xFF6E7681))
                        Text(
                            if (search.isNotBlank() || levelFilter != null) "No matching log entries" else "No logs yet — use any screen to generate them",
                            color = Color(0xFF6E7681),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        TerminalRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalRow(entry: LogEntry) {
    val bgColor: Color = when (entry.level) {
        LogLevel.ERROR   -> Color(0x18F85149)
        LogLevel.WARNING -> Color(0x10D29922)
        else             -> Color.Transparent
    }
    val levelColor: Color = levelColor(entry.level)
    val levelTag: String = when (entry.level) {
        LogLevel.DEBUG   -> "D"
        LogLevel.INFO    -> "I"
        LogLevel.SUCCESS -> "✓"
        LogLevel.WARNING -> "W"
        LogLevel.ERROR   -> "E"
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(bgColor).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(entry.formattedTime, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF6E7681), modifier = Modifier.width(76.dp))
        Text(levelTag, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = levelColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(10.dp))
        Text("[${entry.tag}]", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF58A6FF), modifier = Modifier.width(80.dp))
        Text(entry.message, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = levelColor, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG   -> Color(0xFF8B949E)
    LogLevel.INFO    -> Color(0xFFCDD9E5)
    LogLevel.SUCCESS -> Color(0xFF3FB950)
    LogLevel.WARNING -> Color(0xFFD29922)
    LogLevel.ERROR   -> Color(0xFFF85149)
}
