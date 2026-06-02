package com.accu.ui.shell

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Log level ──────────────────────────────────────────────────────────────────

private enum class LogLevel(val tag: String, val color: Color, val priority: Int) {
    VERBOSE("V", Color(0xFFAAAAAA), 0),
    DEBUG  ("D", Color(0xFF64B5F6), 1),
    INFO   ("I", AccentGreen,       2),
    WARNING("W", AccentOrange,      3),
    ERROR  ("E", AccentRed,         4),
    FATAL  ("F", Color(0xFFFF1744), 5),
}

// ── Logcat buffers ─────────────────────────────────────────────────────────────

private enum class LogBuffer(
    val flag: String,
    val label: String,
    val description: String,
    val color: Color,
    val requiresRoot: Boolean = false,
) {
    MAIN  ("main",   "Main",   "Default app & library output",      Color(0xFF64B5F6)),
    SYSTEM("system", "System", "Framework & system services",       Color(0xFF10B981)),
    CRASH ("crash",  "Crash",  "Crashes, ANRs & fatal exceptions",  Color(0xFFEF4444)),
    EVENTS("events", "Events", "Structured event log (EventLog)",   Color(0xFFF59E0B)),
    RADIO ("radio",  "Radio",  "Telephony & radio layer",           Color(0xFF8B5CF6)),
    KERNEL("kernel", "Kernel", "Linux kernel ring buffer (root)",   Color(0xFFEC4899), requiresRoot = true),
}

// Tags that always indicate a crash/ANR regardless of level
private val CRASH_TAGS = setOf(
    "AndroidRuntime", "Process", "DEBUG", "libc", "art", "CRASH", "signal",
    "ActivityManager", "WindowManager", "InputDispatcher", "Watchdog",
    "NativeCrashListener", "DropBoxManagerService",
)
private fun LogLine.isCrash(): Boolean =
    level == LogLevel.FATAL ||
    (level == LogLevel.ERROR && tag in CRASH_TAGS) ||
    message.contains("FATAL EXCEPTION", ignoreCase = true) ||
    message.contains("ANR in", ignoreCase = true) ||
    message.contains("beginning of crash", ignoreCase = true)

// ── Data ───────────────────────────────────────────────────────────────────────

private data class LogLine(
    val id: Long,
    val timestamp: String,
    val pid: String,
    val tid: String = "",
    val tag: String,
    val level: LogLevel,
    val message: String,
)

private fun parseLoglevel(raw: String): LogLevel = when (raw.firstOrNull()) {
    'V' -> LogLevel.VERBOSE
    'D' -> LogLevel.DEBUG
    'W' -> LogLevel.WARNING
    'E' -> LogLevel.ERROR
    'F' -> LogLevel.FATAL
    else -> LogLevel.INFO
}

// threadtime: "MM-DD HH:MM:SS.mmm  PID   TID  Level Tag  : Message"
private val THREADTIME_RE =
    Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)$""")
// brief fallback: "D/Tag( PID): Message"
private val BRIEF_RE = Regex("""^([VDIWEF])/(.+?)\(\s*(\d+)\):\s*(.*)$""")

private fun parseLogLine(raw: String, id: Long): LogLine? {
    val t = raw.trim()
    if (t.isBlank() || t.startsWith("-----")) return null
    THREADTIME_RE.find(t)?.destructured?.let { (ts, pid, tid, lv, tag, msg) ->
        return LogLine(id, ts, pid, tid, tag.trim(), parseLoglevel(lv), msg)
    }
    BRIEF_RE.find(t)?.destructured?.let { (lv, tag, pid, msg) ->
        return LogLine(id, "", pid, "", tag.trim(), parseLoglevel(lv), msg)
    }
    return LogLine(id, "", "", "", "logcat", LogLevel.INFO, t)
}

// ── Screen ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbLogcatScreen(onBack: () -> Unit = {}) {
    val vm        = hiltViewModel<ShizukuViewModel>()
    val cm        = vm.connectionManager
    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbar  = remember { SnackbarHostState() }

    // ── Core state ────────────────────────────────────────────────────────────
    var lines       by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var isRunning   by remember { mutableStateOf(false) }
    var isPaused    by remember { mutableStateOf(false) }
    var autoScroll  by remember { mutableStateOf(true) }
    var wordWrap    by remember { mutableStateOf(true) }
    var lineCounter by remember { mutableLongStateOf(0L) }
    var lastTs      by remember { mutableStateOf("") }

    // ── Filters ───────────────────────────────────────────────────────────────
    var searchQuery    by remember { mutableStateOf("") }
    var showSearch     by remember { mutableStateOf(false) }
    var tagFilter      by remember { mutableStateOf("") }
    var packageFilter  by remember { mutableStateOf("") }
    var selectedLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    // Default: Main + System + Crash for deep capture out of the box
    var selectedBuffers by remember { mutableStateOf(setOf(LogBuffer.MAIN, LogBuffer.SYSTEM, LogBuffer.CRASH)) }
    var deepCrashMode  by remember { mutableStateOf(false) }

    // ── Crash detection state ─────────────────────────────────────────────────
    var unseenCrashes by remember { mutableIntStateOf(0) }
    var lastCrashLine by remember { mutableStateOf<LogLine?>(null) }
    var showCrashAlert by remember { mutableStateOf(false) }

    // ── Sheets ────────────────────────────────────────────────────────────────
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAppPicker   by remember { mutableStateOf(false) }

    // ── App picker state ──────────────────────────────────────────────────────
    var appList       by remember { mutableStateOf<List<String>>(emptyList()) }
    var appSearch     by remember { mutableStateOf("") }
    var isLoadingApps by remember { mutableStateOf(false) }

    // ── SAF save ──────────────────────────────────────────────────────────────
    var saveFolderUri by remember { mutableStateOf<Uri?>(null) }
    val saveFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> saveFolderUri = uri }

    // ── Derived filtered list ─────────────────────────────────────────────────
    val filtered = remember(lines, searchQuery, tagFilter, packageFilter, selectedLevels) {
        lines.filter { ln ->
            ln.level in selectedLevels &&
            (tagFilter.isBlank()     || ln.tag.contains(tagFilter, ignoreCase = true)) &&
            (packageFilter.isBlank() || ln.tag.contains(packageFilter, ignoreCase = true)
                                     || ln.message.contains(packageFilter, ignoreCase = true)) &&
            (searchQuery.isBlank()   || ln.message.contains(searchQuery, ignoreCase = true)
                                     || ln.tag.contains(searchQuery, ignoreCase = true))
        }
    }
    val crashLines = remember(lines) { lines.filter { it.isCrash() } }

    // ── Command builder ───────────────────────────────────────────────────────
    fun buildLogcatCmd(count: Int = 300, sinceTs: String = ""): String {
        val bufFlag = if (selectedBuffers.isEmpty()) ""
                     else " -b ${selectedBuffers.joinToString(",") { it.flag }}"
        val base = if (sinceTs.isBlank())
            "logcat -v threadtime$bufFlag -t $count 2>/dev/null"
        else
            "logcat -v threadtime$bufFlag -T \"$sinceTs\" 2>/dev/null"
        return if (packageFilter.isNotBlank()) "$base | grep -iE \"$packageFilter\""
               else base
    }

    // ── Append parsed lines + crash detection ─────────────────────────────────
    suspend fun appendLines(output: String, drop1: Boolean = false) {
        val parsed = output.lines()
            .let { if (drop1) it.drop(1) else it }
            .filter { it.isNotBlank() && !it.startsWith("-----") }
            .mapIndexedNotNull { i, raw -> parseLogLine(raw, lineCounter + i) }
        if (parsed.isNotEmpty()) {
            lineCounter += parsed.size
            lines = (lines + parsed).takeLast(10000)
            lastTs = parsed.lastOrNull()?.timestamp?.trim() ?: lastTs
            // Detect new crashes
            val newCrashes = parsed.filter { it.isCrash() }
            if (newCrashes.isNotEmpty()) {
                unseenCrashes += newCrashes.size
                lastCrashLine = newCrashes.last()
                if (!showCrashAlert) showCrashAlert = true
            }
        }
    }

    // ── Initial load ──────────────────────────────────────────────────────────
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        val out = withContext(Dispatchers.IO) { cm.exec(buildLogcatCmd(300)).output }
        appendLines(out)
    }

    // ── Poll loop — 800ms in deep crash mode, 1.5s normally ──────────────────
    LaunchedEffect(isRunning, isPaused, packageFilter, selectedBuffers, deepCrashMode) {
        if (!isRunning || isPaused) return@LaunchedEffect
        val interval = if (deepCrashMode) 800L else 1500L
        while (isActive && isRunning && !isPaused) {
            delay(interval)
            if (!isRunning || isPaused) break
            val out = withContext(Dispatchers.IO) {
                cm.exec(buildLogcatCmd(sinceTs = lastTs)).output
            }
            appendLines(out, drop1 = lastTs.isNotBlank())
            if (autoScroll && filtered.isNotEmpty()) {
                try { listState.scrollToItem(filtered.size - 1) } catch (_: Exception) {} }
        }
    }

    // ── App list load ─────────────────────────────────────────────────────────
    LaunchedEffect(showAppPicker) {
        if (!showAppPicker || appList.isNotEmpty()) return@LaunchedEffect
        isLoadingApps = true
        val out = withContext(Dispatchers.IO) { cm.exec("pm list packages 2>/dev/null").output }
        appList = out.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .sorted()
        isLoadingApps = false
    }

    // ── Crash alert snackbar ──────────────────────────────────────────────────
    LaunchedEffect(showCrashAlert) {
        if (!showCrashAlert) return@LaunchedEffect
        val crash = lastCrashLine ?: return@LaunchedEffect
        val msg = "🔴 CRASH: ${crash.tag} — ${crash.message.take(60)}"
        snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
        showCrashAlert = false
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── App picker sheet ──────────────────────────────────────────────────────
    if (showAppPicker) {
        val visibleApps = remember(appList, appSearch) {
            if (appSearch.isBlank()) appList
            else appList.filter { it.contains(appSearch, ignoreCase = true) }
        }
        ModalBottomSheet(onDismissRequest = { showAppPicker = false; appSearch = "" }) {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filter by App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (packageFilter.isNotBlank()) {
                        TextButton(onClick = { packageFilter = ""; showAppPicker = false; appSearch = "" }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedTextField(
                    value = appSearch, onValueChange = { appSearch = it },
                    placeholder = { Text("Search packages…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = { if (appSearch.isNotEmpty()) IconButton(onClick = { appSearch = "" }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) } },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (packageFilter.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.FilterAlt, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Active: $packageFilter", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // ── Buffer selection inside app picker ───────────────────────
                HorizontalDivider()
                Text("Logcat Buffers", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Select which buffers to read from. Crash is always recommended.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LogBuffer.entries.forEach { buf ->
                    val sel = buf in selectedBuffers
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedBuffers = if (sel) (selectedBuffers - buf).ifEmpty { setOf(LogBuffer.MAIN) }
                                                 else selectedBuffers + buf
                                if (isRunning) { lines = emptyList(); lastTs = ""; lineCounter = 0L }
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = sel,
                            onCheckedChange = {
                                selectedBuffers = if (sel) (selectedBuffers - buf).ifEmpty { setOf(LogBuffer.MAIN) }
                                                 else selectedBuffers + buf
                                if (isRunning) { lines = emptyList(); lastTs = ""; lineCounter = 0L }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = buf.color),
                        )
                        Surface(shape = RoundedCornerShape(4.dp), color = buf.color.copy(0.15f)) {
                            Text(buf.label, fontSize = 11.sp, color = buf.color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(buf.description, style = MaterialTheme.typography.bodySmall)
                            if (buf.requiresRoot) Text("⚠ Requires root", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                        }
                    }
                }

                // ── Deep Crash Mode toggle ───────────────────────────────────
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (deepCrashMode) AccentRed.copy(0.08f) else Color.Transparent)
                        .clickable {
                            deepCrashMode = !deepCrashMode
                            if (deepCrashMode) {
                                selectedBuffers = setOf(LogBuffer.MAIN, LogBuffer.SYSTEM, LogBuffer.CRASH, LogBuffer.EVENTS)
                                selectedLevels  = LogLevel.entries.toSet()
                            }
                            if (isRunning) { lines = emptyList(); lastTs = ""; lineCounter = 0L }
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val pulse = remember { Animatable(1f) }
                    LaunchedEffect(deepCrashMode) {
                        if (!deepCrashMode) { pulse.snapTo(1f); return@LaunchedEffect }
                        while (true) {
                            pulse.animateTo(1.25f, animationSpec = tween(600))
                            pulse.animateTo(1f,    animationSpec = tween(600))
                        }
                    }
                    Icon(
                        Icons.Default.BugReport, null,
                        Modifier.size(22.dp).scale(if (deepCrashMode) pulse.value else 1f),
                        tint = if (deepCrashMode) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Deep Crash Mode", fontWeight = FontWeight.SemiBold, color = if (deepCrashMode) AccentRed else MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Main+System+Crash+Events buffers · polls every 800ms · alerts on any crash or ANR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = deepCrashMode,
                        onCheckedChange = {
                            deepCrashMode = it
                            if (it) {
                                selectedBuffers = setOf(LogBuffer.MAIN, LogBuffer.SYSTEM, LogBuffer.CRASH, LogBuffer.EVENTS)
                                selectedLevels  = LogLevel.entries.toSet()
                            }
                            if (isRunning) { lines = emptyList(); lastTs = ""; lineCounter = 0L }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentRed, checkedTrackColor = AccentRed.copy(0.4f)),
                    )
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    // ── Level + tag filter sheet ──────────────────────────────────────────────
    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Filter Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()

                Text("Log Levels", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LogLevel.entries.forEach { level ->
                        val sel = level in selectedLevels
                        FilterChip(
                            selected = sel,
                            onClick = {
                                selectedLevels = if (sel && selectedLevels.size > 1) selectedLevels - level
                                                 else selectedLevels + level
                            },
                            label = { Text(level.tag, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = level.color.copy(0.2f),
                                selectedLabelColor = level.color,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                HorizontalDivider()
                Text("Tag filter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = tagFilter, onValueChange = { tagFilter = it },
                    placeholder = { Text("e.g. ActivityManager, AndroidRuntime") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (tagFilter.isNotEmpty()) IconButton(onClick = { tagFilter = "" }) {
                            Icon(Icons.Default.Clear, null, Modifier.size(16.dp))
                        }
                    },
                )

                HorizontalDivider()
                Text("Quick crash tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CRASH_TAGS.sorted().toList()) { t ->
                        FilterChip(
                            selected = tagFilter == t,
                            onClick  = { tagFilter = if (tagFilter == t) "" else t },
                            label    = { Text(t, fontSize = 10.sp) },
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { tagFilter = ""; selectedLevels = LogLevel.entries.toSet() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Reset all") }
                    Button(onClick = { showFilterSheet = false }, modifier = Modifier.weight(1f)) { Text("Apply") }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                ACCTopBar(
                    title = "Logcat",
                    onBack = onBack,
                    actions = {
                        // App + buffer picker
                        IconButton(onClick = { showAppPicker = true }) {
                            BadgedBox(badge = {
                                val hasPkg = packageFilter.isNotBlank()
                                val nonDefaultBufs = selectedBuffers != setOf(LogBuffer.MAIN, LogBuffer.SYSTEM, LogBuffer.CRASH)
                                if (hasPkg || deepCrashMode || nonDefaultBufs) Badge()
                            }) { Icon(Icons.Outlined.Apps, "App & Buffer filter") }
                        }
                        // Search
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Outlined.Search, "Search")
                        }
                        // Level + tag filter
                        IconButton(onClick = { showFilterSheet = true }) {
                            BadgedBox(badge = {
                                if (selectedLevels.size < LogLevel.entries.size || tagFilter.isNotBlank()) Badge()
                            }) { Icon(Icons.Outlined.FilterList, "Level filter") }
                        }
                        // Word wrap
                        IconButton(onClick = { wordWrap = !wordWrap }) {
                            Icon(if (wordWrap) Icons.Outlined.WrapText else Icons.Outlined.Notes, "Word wrap")
                        }
                        // More overflow
                        var showMore by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMore = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                            DropdownMenu(showMore, { showMore = false }) {
                                DropdownMenuItem(
                                    text = { Text("Copy all (${filtered.size} lines)") },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                    onClick = {
                                        showMore = false
                                        clipboard.setText(AnnotatedString(
                                            filtered.joinToString("\n") { "${it.timestamp} ${it.level.tag}/${it.tag}(${it.pid}): ${it.message}" }
                                        ))
                                        scope.launch { snackbar.showSnackbar("Copied ${filtered.size} lines ✓") }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (saveFolderUri != null) "Save logcat (folder set)" else "Save logcat (choose folder)") },
                                    leadingIcon = { Icon(Icons.Outlined.Save, null) },
                                    onClick = {
                                        showMore = false
                                        val uri = saveFolderUri
                                        if (uri == null) {
                                            saveFolderLauncher.launch(null)
                                        } else {
                                            scope.launch(Dispatchers.IO) {
                                                val content = filtered.joinToString("\n") { "${it.timestamp} ${it.level.tag}/${it.tag}(${it.pid}): ${it.message}" }
                                                val fname = "logcat_${System.currentTimeMillis()}.txt"
                                                runCatching {
                                                    val dir = DocumentFile.fromTreeUri(context, uri)
                                                    val file = dir?.createFile("text/plain", fname)
                                                    context.contentResolver.openOutputStream(file?.uri ?: return@launch)?.use { it.write(content.toByteArray()) }
                                                    withContext(Dispatchers.Main) { snackbar.showSnackbar("Saved $fname ✓") }
                                                }.onFailure { e ->
                                                    withContext(Dispatchers.Main) { snackbar.showSnackbar("Save failed: ${e.message?.take(60)}") }
                                                }
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear logcat buffer on device") },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                                    onClick = {
                                        showMore = false
                                        scope.launch {
                                            lines = emptyList(); lastTs = ""; lineCounter = 0L; unseenCrashes = 0
                                            withContext(Dispatchers.IO) { cm.exec("logcat -c -b all 2>/dev/null") }
                                            snackbar.showSnackbar("All logcat buffers cleared on device ✓")
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear display") },
                                    leadingIcon = { Icon(Icons.Outlined.LayersClear, null) },
                                    onClick = {
                                        showMore = false
                                        lines = emptyList(); lastTs = ""; lineCounter = 0L; unseenCrashes = 0
                                    },
                                )
                            }
                        }
                    },
                )

                // ── Search bar ────────────────────────────────────────────────
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        placeholder = { Text("Search messages, tags, PIDs…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Clear, null, Modifier.size(16.dp))
                            }
                        },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }

                // ── Active filter + buffer chips row ──────────────────────────
                val hasAnyFilter = selectedLevels.size < LogLevel.entries.size ||
                                   tagFilter.isNotBlank() || packageFilter.isNotBlank() ||
                                   deepCrashMode ||
                                   selectedBuffers != setOf(LogBuffer.MAIN, LogBuffer.SYSTEM, LogBuffer.CRASH)
                if (hasAnyFilter || isRunning) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth()) {
                            // Buffer chips
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Deep crash mode badge
                                if (deepCrashMode) {
                                    item {
                                        val pulse = remember { Animatable(1f) }
                                        LaunchedEffect(Unit) {
                                            while (true) {
                                                pulse.animateTo(1.1f, tween(700))
                                                pulse.animateTo(1f, tween(700))
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AccentRed.copy(0.18f),
                                            modifier = Modifier.scale(pulse.value),
                                        ) {
                                            Row(
                                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(Icons.Default.BugReport, null, Modifier.size(11.dp), tint = AccentRed)
                                                Text("DEEP CRASH", fontSize = 9.sp, color = AccentRed, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    }
                                }
                                // Buffer pills
                                items(LogBuffer.entries.filter { it in selectedBuffers }) { buf ->
                                    Surface(shape = RoundedCornerShape(6.dp), color = buf.color.copy(0.15f)) {
                                        Text(buf.label.uppercase(), fontSize = 9.sp, color = buf.color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                                // Package filter chip
                                if (packageFilter.isNotBlank()) {
                                    item {
                                        InputChip(
                                            selected = true, onClick = { packageFilter = "" },
                                            label = { Text("⎇ $packageFilter", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) },
                                            modifier = Modifier.widthIn(max = 180.dp),
                                        )
                                    }
                                }
                                // Tag filter chip
                                if (tagFilter.isNotBlank()) {
                                    item {
                                        InputChip(
                                            selected = true, onClick = { tagFilter = "" },
                                            label = { Text("tag: $tagFilter", fontSize = 10.sp) },
                                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) },
                                        )
                                    }
                                }
                                // Level chips
                                if (selectedLevels.size < LogLevel.entries.size) {
                                    items(selectedLevels.sortedBy { it.priority }) { level ->
                                        Surface(shape = RoundedCornerShape(4.dp), color = level.color.copy(0.15f)) {
                                            Text(level.tag, fontSize = 9.sp, color = level.color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                // Crash counter badge
                                if (crashLines.isNotEmpty()) {
                                    item {
                                        Surface(shape = RoundedCornerShape(6.dp), color = AccentRed.copy(0.15f)) {
                                            Row(
                                                Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Icon(Icons.Default.Warning, null, Modifier.size(10.dp), tint = AccentRed)
                                                Text("${crashLines.size} crash${if (crashLines.size > 1) "es" else ""}", fontSize = 9.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                // Line count
                                item {
                                    Text("${filtered.size} lines", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        },

        // ── Bottom bar ────────────────────────────────────────────────────────
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    // Crash banner (shown when crashes detected and paused/viewing)
                    AnimatedVisibility(visible = unseenCrashes > 0) {
                        Surface(color = AccentRed.copy(0.12f)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        unseenCrashes = 0
                                        // Scroll to last crash
                                        val crashIdx = filtered.indexOfLast { it.isCrash() }
                                        if (crashIdx >= 0) scope.launch {
                                            try { listState.scrollToItem(crashIdx + 1) } catch (_: Exception) {}
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.BugReport, null, Modifier.size(16.dp), tint = AccentRed)
                                Text(
                                    "$unseenCrashes new crash/ANR detected — tap to jump",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentRed,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Default.Close, null, Modifier.size(14.dp).clickable { unseenCrashes = 0 }, tint = AccentRed)
                            }
                        }
                    }
                    // Status bar
                    if (isRunning) {
                        Surface(color = if (deepCrashMode) AccentRed.copy(0.06f) else if (isPaused) MaterialTheme.colorScheme.surfaceVariant else AccentGreen.copy(0.07f)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    Modifier.size(7.dp).background(
                                        if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant
                                        else if (deepCrashMode) AccentRed
                                        else AccentGreen,
                                        CircleShape,
                                    )
                                )
                                Text(
                                    buildString {
                                        append(if (isPaused) "Paused" else if (deepCrashMode) "Deep Crash Mode — 800ms poll" else "Live — 1.5s poll")
                                        append(" · ${lines.size} captured")
                                        if (selectedBuffers.isNotEmpty()) append(" · buffers: ${selectedBuffers.joinToString(",") { it.label }}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    // Control row
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isRunning) {
                            Button(
                                onClick = {
                                    lines = emptyList(); lastTs = ""; lineCounter = 0L; unseenCrashes = 0
                                    isRunning = true; isPaused = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (deepCrashMode) AccentRed else AccentGreen),
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (deepCrashMode) "Start Deep Capture" else "Start Logcat", color = Color.White)
                            }
                        } else {
                            if (!isPaused) {
                                OutlinedButton(onClick = { isPaused = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Pause, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pause")
                                }
                            } else {
                                Button(
                                    onClick = { isPaused = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Resume", color = Color.White)
                                }
                            }
                            OutlinedButton(
                                onClick = { isRunning = false; isPaused = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                            ) {
                                Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                        // Auto-scroll
                        IconButton(onClick = { autoScroll = !autoScroll }) {
                            Icon(
                                if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.PauseCircleOutline,
                                "Auto-scroll",
                                tint = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->

        // ── Empty state ───────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        if (deepCrashMode) Icons.Default.BugReport else Icons.Outlined.Article,
                        null,
                        Modifier.size(64.dp),
                        tint = if (deepCrashMode) AccentRed.copy(0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        when {
                            !isRunning ->  if (deepCrashMode) "Deep Crash Mode ready — tap to start" else "Tap Start to capture logs"
                            else       -> "Waiting for log entries from ${selectedBuffers.joinToString(", ") { it.label }} buffer${if (selectedBuffers.size > 1) "s" else ""}…"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!isRunning) {
                        Button(
                            onClick = {
                                lines = emptyList(); lastTs = ""; lineCounter = 0L; unseenCrashes = 0
                                isRunning = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (deepCrashMode) AccentRed else MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (deepCrashMode) "Start Deep Capture" else "Start Logcat")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Header card: shows the exact command run
                item(key = "header") {
                    LogcatCommandCard(
                        cmd = buildLogcatCmd(),
                        count = filtered.size,
                        packageFilter = packageFilter,
                        buffers = selectedBuffers,
                        deepCrash = deepCrashMode,
                        crashCount = crashLines.size,
                        clipboard = clipboard,
                        onCopyAll = {
                            clipboard.setText(AnnotatedString(
                                filtered.joinToString("\n") { "${it.timestamp} ${it.level.tag}/${it.tag}(${it.pid}): ${it.message}" }
                            ))
                            scope.launch { snackbar.showSnackbar("Copied ${filtered.size} lines ✓") }
                        },
                    )
                }
                items(filtered, key = { it.id }) { line ->
                    LogEntryCard(line = line, wordWrap = wordWrap, clipboard = clipboard)
                }
            }
        }
    }
}

// ── Shell-card-style logcat command header ─────────────────────────────────────

@Composable
private fun LogcatCommandCard(
    cmd: String,
    count: Int,
    packageFilter: String,
    buffers: Set<LogBuffer>,
    deepCrash: Boolean,
    crashCount: Int,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onCopyAll: () -> Unit,
) {
    val accent = if (deepCrash) AccentRed else Color(0xFF64B5F6)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f)) {
                // Dark header bar — exact ShellCommandCard style
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f))
                        .padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("$", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = accent, fontWeight = FontWeight.Bold)
                    Text(
                        cmd,
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.size(8.dp).clip(CircleShape).background(AccentGreen))
                    IconButton(onClick = onCopyAll, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.ContentCopy, "Copy all", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.inverseOnSurface.copy(0.55f))
                    }
                }
                // Info body
                Surface(modifier = Modifier.fillMaxWidth(), color = accent.copy(alpha = 0.04f)) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$count entries", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        // Buffer pills
                        buffers.forEach { buf ->
                            Surface(shape = RoundedCornerShape(4.dp), color = buf.color.copy(0.18f)) {
                                Text(buf.label, fontSize = 9.sp, color = buf.color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        if (crashCount > 0) {
                            Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(0.15f)) {
                                Row(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, Modifier.size(9.dp), tint = AccentRed)
                                    Text("$crashCount crash${if (crashCount > 1) "es" else ""}", fontSize = 9.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (packageFilter.isNotBlank()) {
                            Text("⎇ $packageFilter", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ── Per-log-entry card — exact Shell-card visual style ─────────────────────────

@Composable
private fun LogEntryCard(
    line: LogLine,
    wordWrap: Boolean,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
) {
    val isCrash   = line.isCrash()
    val levelColor = line.level.color
    val borderColor = if (isCrash) Color(0xFFFF1744) else levelColor
    val bodyBg = when {
        isCrash            -> Color(0xFFFF1744).copy(alpha = 0.10f)
        line.level == LogLevel.ERROR   -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.10f)
        line.level == LogLevel.WARNING -> AccentOrange.copy(alpha = 0.04f)
        else               -> levelColor.copy(alpha = 0.03f)
    }
    val msgColor = when (line.level) {
        LogLevel.FATAL, LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING               -> AccentOrange
        LogLevel.DEBUG                 -> Color(0xFF64B5F6)
        LogLevel.VERBOSE               -> MaterialTheme.colorScheme.onSurfaceVariant
        else                           -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(7.dp),
        elevation = CardDefaults.cardElevation(if (isCrash) 2.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Level-colored left border
            Box(Modifier.width(3.dp).fillMaxHeight().background(borderColor))
            Column(Modifier.weight(1f)) {
                // Dark header bar — ShellCommandCard header style
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCrash) Color(0xFFFF1744).copy(0.20f)
                            else MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f)
                        )
                        .padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Crash flame icon or level badge
                    if (isCrash) {
                        Icon(Icons.Default.BugReport, null, Modifier.size(14.dp), tint = Color(0xFFFF1744))
                    } else {
                        Surface(shape = CircleShape, color = levelColor.copy(0.22f), modifier = Modifier.size(18.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(line.level.tag, fontSize = 9.sp, color = levelColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    // Tag
                    Text(
                        line.tag,
                        fontSize = 11.sp,
                        fontWeight = if (isCrash) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isCrash) Color(0xFFFF6B6B) else levelColor,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // PID
                    if (line.pid.isNotBlank()) {
                        Text("pid:${line.pid}", fontSize = 9.sp, color = MaterialTheme.colorScheme.inverseOnSurface.copy(0.40f), fontFamily = FontFamily.Monospace)
                    }
                    // Timestamp
                    if (line.timestamp.isNotBlank()) {
                        Text(line.timestamp, fontSize = 9.sp, color = MaterialTheme.colorScheme.inverseOnSurface.copy(0.50f), fontFamily = FontFamily.Monospace)
                    }
                    // Per-line copy button — always visible
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(
                                "${line.timestamp} ${line.level.tag}/${line.tag}(${line.pid}): ${line.message}"
                            ))
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.inverseOnSurface.copy(0.45f))
                    }
                }
                // Message body
                Surface(modifier = Modifier.fillMaxWidth(), color = bodyBg) {
                    Text(
                        text = line.message,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = msgColor,
                        maxLines = if (wordWrap) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isCrash) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
