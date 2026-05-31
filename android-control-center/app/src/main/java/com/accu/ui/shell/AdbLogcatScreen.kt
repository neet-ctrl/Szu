package com.accu.ui.shell

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.accu.ui.components.ACCTopBar
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Log level ─────────────────────────────────────────────────────────────────

private enum class LogLevel(val tag: String, val color: Color, val priority: Int) {
    VERBOSE("V", Color(0xFFAAAAAA), 0),
    DEBUG  ("D", Color(0xFF64B5F6), 1),
    INFO   ("I", AccentGreen,       2),
    WARNING("W", AccentOrange,      3),
    ERROR  ("E", AccentRed,         4),
    FATAL  ("F", Color(0xFFFF1744), 5),
}

private data class LogLine(
    val id: Long,
    val timestamp: String,
    val pid: String,
    val tag: String,
    val level: LogLevel,
    val message: String,
)

private fun parseLoglevel(raw: String): LogLevel = when {
    raw.startsWith("V") -> LogLevel.VERBOSE
    raw.startsWith("D") -> LogLevel.DEBUG
    raw.startsWith("W") -> LogLevel.WARNING
    raw.startsWith("E") -> LogLevel.ERROR
    raw.startsWith("F") -> LogLevel.FATAL
    else                -> LogLevel.INFO
}

private val DEMO_LINES = listOf(
    "01-01 00:00:01.001  1234  1235 D ActivityManager: Process com.example.app (pid 5678) has died",
    "01-01 00:00:01.050  1234  1234 I SystemServer: Starting services...",
    "01-01 00:00:01.100  2001  2001 V ViewRootImpl: performTraversals mWidth=1080 mHeight=2340",
    "01-01 00:00:01.200  3000  3001 W PackageManager: Package com.bloat.app not found",
    "01-01 00:00:01.300  4000  4000 E SQLiteDatabase: Failed to open database /data/data/com.app/databases/db",
    "01-01 00:00:01.400  1234  1240 I Choreographer: Skipped 34 frames!  The application may be doing too much work on its main thread.",
    "01-01 00:00:01.500  5000  5000 D BluetoothGatt: onConnectionStateChange status=0 newState=2",
    "01-01 00:00:01.600  6000  6001 W art: Suspending all threads took: 45.321ms",
    "01-01 00:00:01.700  7000  7000 I AudioFlinger: AudioFlinger's thread 0xb4000071 ready to run",
    "01-01 00:00:01.800  8000  8000 E NetworkSecurityConfig: No Network Security Config specified",
    "01-01 00:00:01.900  1234  1234 I ActivityTaskManager: START u0 {act=android.intent.action.MAIN}",
    "01-01 00:00:02.000  9000  9001 D Volley: [1] 2.onErrorResponse: unexpected end of stream on Connection",
    "01-01 00:00:02.100  1234  1299 F AndroidRuntime: FATAL EXCEPTION: main Process: com.example, PID: 1234",
    "01-01 00:00:02.200  1000  1001 V InputReader: Input event received: id=123, type=1, code=330, value=1",
    "01-01 00:00:02.300  2500  2500 I Camera2ClientBase: Camera 0: Opened",
    "01-01 00:00:02.400  3500  3502 W Binder: Slow binder call to com.android.server took 512ms",
    "01-01 00:00:02.500  1234  1300 D WindowManager: Adding window Window{abc123} at 1",
    "01-01 00:00:02.600  4500  4500 E MediaPlayer: MediaPlayer error (1, -19)",
    "01-01 00:00:02.700  1234  1234 I PackageManager: Package installed: com.example.newapp",
    "01-01 00:00:02.800  5500  5500 D ConnectivityService: Network 100 interface eth0 available",
)

private fun parseLogLine(raw: String, id: Long): LogLine {
    return try {
        val parts = raw.split("\\s+".toRegex(), limit = 6)
        val levelTag = parts.getOrElse(4) { "I" }
        val level = parseLoglevel(levelTag)
        val tag = levelTag.substringAfter("/").substringBefore(":")
        LogLine(
            id        = id,
            timestamp = "${parts.getOrElse(0) { "" }} ${parts.getOrElse(1) { "" }}",
            pid       = parts.getOrElse(2) { "" },
            tag       = tag.ifBlank { "?" },
            level     = level,
            message   = parts.getOrElse(5) { raw },
        )
    } catch (_: Exception) {
        LogLine(id, "", "", "?", LogLevel.INFO, raw)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbLogcatScreen(onBack: () -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var lines by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf("") }
    var selectedLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var lineCounter by remember { mutableLongStateOf(0L) }
    var autoScroll by remember { mutableStateOf(true) }
    var copiedMsg by remember { mutableStateOf(false) }

    val filtered = remember(lines, searchQuery, tagFilter, selectedLevels) {
        lines.filter { line ->
            line.level in selectedLevels &&
            (tagFilter.isBlank() || line.tag.contains(tagFilter, ignoreCase = true)) &&
            (searchQuery.isBlank() || line.message.contains(searchQuery, ignoreCase = true) || line.tag.contains(searchQuery, ignoreCase = true))
        }
    }

    LaunchedEffect(isRunning, isPaused) {
        if (!isRunning || isPaused) return@LaunchedEffect
        var idx = 0
        while (isRunning && !isPaused) {
            val raw = DEMO_LINES[idx % DEMO_LINES.size]
            val parsed = parseLogLine(raw, lineCounter++)
            lines = (lines + parsed).takeLast(5000)
            idx++
            if (autoScroll && filtered.isNotEmpty()) {
                try { listState.scrollToItem(filtered.size - 1) } catch (_: Exception) {}
            }
            delay(300L + (idx * 41L) % 500L)
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Filter Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Text("Log Levels", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LogLevel.entries.forEach { level ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = level in selectedLevels, onCheckedChange = {
                            selectedLevels = if (it) selectedLevels + level else selectedLevels - level
                        })
                        Surface(shape = CircleShape, color = level.color.copy(0.15f), modifier = Modifier.size(22.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(level.tag, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = level.color, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(level.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
                Text("Tag filter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = tagFilter, onValueChange = { tagFilter = it }, placeholder = { Text("e.g. ActivityManager") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { tagFilter = ""; selectedLevels = LogLevel.entries.toSet() }, modifier = Modifier.weight(1f)) { Text("Reset") }
                    Button(onClick = { showFilterSheet = false }, modifier = Modifier.weight(1f)) { Text("Apply") }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                ACCTopBar(
                    title = "Logcat",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Outlined.Search, "Search") }
                        IconButton(onClick = { showFilterSheet = true }) {
                            BadgedBox(badge = {
                                if (selectedLevels.size < LogLevel.entries.size || tagFilter.isNotBlank()) Badge()
                            }) { Icon(Icons.Outlined.FilterList, "Filter") }
                        }
                        IconButton(onClick = {
                            val text = filtered.joinToString("\n") { "${it.timestamp} ${it.level.tag}/${it.tag}: ${it.message}" }
                            clipboard.setText(AnnotatedString(text))
                            scope.launch { copiedMsg = true; delay(2000); copiedMsg = false }
                        }) { Icon(Icons.Outlined.ContentCopy, "Copy all") }
                        IconButton(onClick = { lines = emptyList() }) { Icon(Icons.Outlined.DeleteSweep, "Clear") }
                    },
                )
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        placeholder = { Text("Search logs…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Clear, null, Modifier.size(16.dp)) } },
                        singleLine = true, shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                if (selectedLevels.size < LogLevel.entries.size || tagFilter.isNotBlank()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterAlt, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        selectedLevels.sortedBy { it.priority }.forEach { level ->
                            Surface(shape = RoundedCornerShape(4.dp), color = level.color.copy(0.15f)) {
                                Text(level.tag, style = MaterialTheme.typography.labelSmall, color = level.color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        if (tagFilter.isNotBlank()) Text("tag: $tagFilter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Text("${filtered.size} lines", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isRunning) {
                        Button(onClick = { isRunning = true; isPaused = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Start Logcat", color = Color.White)
                        }
                    } else {
                        if (!isPaused) {
                            OutlinedButton(onClick = { isPaused = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Pause, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Pause")
                            }
                        } else {
                            Button(onClick = { isPaused = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Resume", color = Color.White)
                            }
                        }
                        OutlinedButton(onClick = { isRunning = false; isPaused = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)) {
                            Icon(Icons.Default.Stop, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Stop")
                        }
                    }
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.PauseCircleOutline, "Auto-scroll", tint = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        snackbarHost = {
            if (copiedMsg) {
                SnackbarHost(remember { SnackbarHostState() }.also { scope.launch { it.showSnackbar("Copied ${filtered.size} lines") } })
            }
        },
    ) { padding ->
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Article, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (isRunning) "Waiting for logs…" else "Tap Start to begin", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!isRunning) Button(onClick = { isRunning = true }) { Text("Start Logcat") }
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(filtered, key = { it.id }) { line -> LogLineRow(line, clipboard) }
            }
        }
    }
}

@Composable
private fun LogLineRow(line: LogLine, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (line.level == LogLevel.FATAL || line.level == LogLevel.ERROR) line.level.color.copy(0.05f) else Color.Transparent)
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(shape = CircleShape, color = line.level.color.copy(0.15f), modifier = Modifier.size(18.dp).padding(top = 2.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(line.level.tag, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = line.level.color, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(line.tag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = line.level.color, fontSize = 10.sp)
                Text(line.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
            Text(line.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = if (expanded) Int.MAX_VALUE else 2)
        }
        if (expanded) {
            IconButton(onClick = { clipboard.setText(AnnotatedString("${line.timestamp} ${line.level.tag}/${line.tag}: ${line.message}")) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
