package com.accu.ui.shell

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Data model ────────────────────────────────────────────────────────────────

private data class ProcessEntry(
    val pid: Int,
    val name: String,
    val packageName: String = "",
    val user: String = "system",
    val cpuPercent: Float = 0f,
    val memKb: Long = 0L,
    val status: String = "S",   // R=running, S=sleeping, Z=zombie, T=stopped
    val threads: Int = 1,
    val isSystemApp: Boolean = false,
)

private data class ServiceEntry(
    val name: String,
    val packageName: String,
    val pid: Int,
    val isRunning: Boolean,
)

private val DEMO_PROCESSES = listOf(
    ProcessEntry(1,    "init",                          user = "root",    cpuPercent = 0.0f,  memKb = 4096,    status = "S", threads = 1,  isSystemApp = true),
    ProcessEntry(234,  "surfaceflinger",                user = "system",  cpuPercent = 2.1f,  memKb = 98304,   status = "S", threads = 12, isSystemApp = true),
    ProcessEntry(435,  "system_server",                 user = "system",  cpuPercent = 3.4f,  memKb = 204800,  status = "S", threads = 98, isSystemApp = true),
    ProcessEntry(876,  "com.android.launcher3",         user = "u0_a10",  cpuPercent = 1.2f,  memKb = 143360,  status = "S", threads = 22, isSystemApp = true),
    ProcessEntry(1001, "com.google.android.gms",        user = "u0_a20",  cpuPercent = 4.7f,  memKb = 319488,  status = "S", threads = 47, isSystemApp = false),
    ProcessEntry(1120, "com.android.settings",          user = "u0_a30",  cpuPercent = 0.3f,  memKb = 110592,  status = "S", threads = 15, isSystemApp = true),
    ProcessEntry(1234, "com.accu.controlcenter",        user = "u0_a50",  cpuPercent = 0.8f,  memKb = 87040,   status = "R", threads = 28, isSystemApp = false),
    ProcessEntry(1456, "com.whatsapp",                  user = "u0_a80",  cpuPercent = 0.5f,  memKb = 262144,  status = "S", threads = 38, isSystemApp = false),
    ProcessEntry(1678, "com.instagram.android",         user = "u0_a90",  cpuPercent = 0.2f,  memKb = 221184,  status = "S", threads = 32, isSystemApp = false),
    ProcessEntry(1890, "com.spotify.music",             user = "u0_a100", cpuPercent = 1.9f,  memKb = 180224,  status = "S", threads = 25, isSystemApp = false),
    ProcessEntry(2100, "com.google.android.youtube",    user = "u0_a110", cpuPercent = 5.3f,  memKb = 344064,  status = "R", threads = 41, isSystemApp = false),
    ProcessEntry(2300, "kworker/0:1",                   user = "root",    cpuPercent = 0.1f,  memKb = 0,       status = "S", threads = 1,  isSystemApp = true),
    ProcessEntry(2450, "com.android.phone",             user = "radio",   cpuPercent = 0.0f,  memKb = 77824,   status = "S", threads = 18, isSystemApp = true),
    ProcessEntry(2600, "com.google.android.apps.maps",  user = "u0_a120", cpuPercent = 0.4f,  memKb = 204800,  status = "S", threads = 29, isSystemApp = false),
    ProcessEntry(2800, "audioserver",                   user = "audioserver", cpuPercent = 0.7f, memKb = 32768, status = "S", threads = 6, isSystemApp = true),
    ProcessEntry(2950, "cameraserver",                  user = "cameraserver", cpuPercent = 0.0f, memKb = 16384, status = "S", threads = 4, isSystemApp = true),
    ProcessEntry(3100, "com.chrome.android",            user = "u0_a130", cpuPercent = 2.8f,  memKb = 409600,  status = "R", threads = 55, isSystemApp = false),
    ProcessEntry(3300, "mediaserver",                   user = "media",   cpuPercent = 0.3f,  memKb = 24576,   status = "S", threads = 8,  isSystemApp = true),
    ProcessEntry(3500, "com.samsung.android.dialer",    user = "u0_a140", cpuPercent = 0.0f,  memKb = 57344,   status = "S", threads = 14, isSystemApp = true),
    ProcessEntry(3700, "vold",                          user = "root",    cpuPercent = 0.0f,  memKb = 8192,    status = "S", threads = 2,  isSystemApp = true),
)

private val DEMO_SERVICES = listOf(
    ServiceEntry("ActivityManagerService",     "android",                       435,  true),
    ServiceEntry("WindowManagerService",       "android",                       435,  true),
    ServiceEntry("PackageManagerService",      "android",                       435,  true),
    ServiceEntry("TelephonyManager",           "com.android.phone",             2450, true),
    ServiceEntry("WifiService",                "com.android.server",            435,  true),
    ServiceEntry("NotificationManagerService", "android",                       435,  true),
    ServiceEntry("AccessibilityManagerService","android",                       435,  true),
    ServiceEntry("LocationManagerService",     "com.android.server",            435,  true),
    ServiceEntry("MediaPlaybackService",       "com.spotify.music",             1890, true),
    ServiceEntry("FirebaseMessagingService",   "com.google.android.gms",        1001, true),
    ServiceEntry("DownloadManagerService",     "com.android.providers.download",435,  false),
    ServiceEntry("BackupManagerService",       "android",                       435,  false),
)

private fun formatMem(kb: Long): String {
    return when {
        kb == 0L     -> "0 B"
        kb < 1024L   -> "$kb KB"
        kb < 1048576L -> "${kb / 1024} MB"
        else         -> String.format("%.1f GB", kb / 1048576.0)
    }
}

private fun statusColor(status: String): Color = when (status) {
    "R" -> AccentGreen
    "S" -> Color(0xFF64B5F6)
    "Z" -> AccentRed
    "T" -> AccentOrange
    else -> Color(0xFFAAAAAA)
}

// ── Screen ────────────────────────────────────────────────────────────────────

private enum class ProcessTab { PROCESSES, SERVICES }
private enum class SortBy { CPU, MEMORY, PID, NAME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbProcessScreen(onBack: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(ProcessTab.PROCESSES) }
    var sortBy by remember { mutableStateOf(SortBy.CPU) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var processes by remember { mutableStateOf(DEMO_PROCESSES) }
    var services by remember { mutableStateOf(DEMO_SERVICES) }
    var isRefreshing by remember { mutableStateOf(false) }
    var killTarget by remember { mutableStateOf<ProcessEntry?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var hideSystem by remember { mutableStateOf(false) }

    // Live CPU simulation
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            processes = processes.map { p ->
                p.copy(cpuPercent = (p.cpuPercent + (-0.8f..0.8f).random()).coerceIn(0f, 100f))
            }
        }
    }

    val sorted = remember(processes, sortBy, searchQuery, hideSystem) {
        processes
            .filter { p ->
                !hideSystem || !p.isSystemApp
            }
            .filter { p ->
                searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || p.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(when (sortBy) {
                SortBy.CPU    -> compareByDescending { it.cpuPercent }
                SortBy.MEMORY -> compareByDescending { it.memKb }
                SortBy.PID    -> compareBy { it.pid }
                SortBy.NAME   -> compareBy { it.name }
            })
    }

    // Kill confirmation dialog
    killTarget?.let { proc ->
        AlertDialog(
            onDismissRequest = { killTarget = null },
            icon = { Icon(Icons.Default.Warning, null, tint = AccentRed) },
            title = { Text("Kill Process?") },
            text = { Text("Force-kill ${proc.name} (PID ${proc.pid})?\n\nThis will terminate the process immediately. Data loss may occur.") },
            confirmButton = {
                Button(onClick = {
                    processes = processes.filter { it.pid != proc.pid }
                    killTarget = null
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) { Text("Kill", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { killTarget = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            Column {
                ACCTopBar(
                    title = "Process Manager",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Outlined.Search, "Search") }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Outlined.Sort, "Sort") }
                            DropdownMenu(showSortMenu, { showSortMenu = false }) {
                                SortBy.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("Sort by ${s.name.lowercase().replaceFirstChar { it.uppercase() }}") },
                                        leadingIcon = { if (sortBy == s) Icon(Icons.Default.Check, null) },
                                        onClick = { sortBy = s; showSortMenu = false },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            scope.launch { isRefreshing = true; delay(800); isRefreshing = false }
                        }) {
                            if (isRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, "Refresh")
                        }
                    },
                )
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        placeholder = { Text("Search processes…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Clear, null, Modifier.size(16.dp)) } },
                        singleLine = true, shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                // Stats bar
                if (selectedTab == ProcessTab.PROCESSES) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("${sorted.size} processes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("CPU: ${String.format("%.1f", sorted.sumOf { it.cpuPercent.toDouble() })}%", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                            Text("RAM: ${formatMem(sorted.sumOf { it.memKb })}", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                            Spacer(Modifier.weight(1f))
                            FilterChip(selected = hideSystem, onClick = { hideSystem = !hideSystem }, label = { Text("User apps only", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(24.dp))
                        }
                    }
                }
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    ProcessTab.entries.forEach { tab ->
                        Tab(selected = selectedTab == tab, onClick = { selectedTab = tab }, text = {
                            Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                        })
                    }
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            ProcessTab.PROCESSES -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(sorted, key = { it.pid }) { proc ->
                    ProcessRow(proc, onKill = { killTarget = proc })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
            ProcessTab.SERVICES -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(services, key = { it.name }) { svc -> ServiceRow(svc) }
            }
        }
    }
}

@Composable
private fun ProcessRow(proc: ProcessEntry, onKill: () -> Unit) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(proc.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(shape = CircleShape, color = statusColor(proc.status).copy(0.15f), modifier = Modifier.size(18.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(proc.status, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = statusColor(proc.status), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("PID: ${proc.pid} · user: ${proc.user} · threads: ${proc.threads}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Outlined.Memory, null, Modifier.size(10.dp), tint = AccentCyan)
                        Text(formatMem(proc.memKb), style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Outlined.Speed, null, Modifier.size(10.dp), tint = AccentOrange)
                        Text("${String.format("%.1f", proc.cpuPercent)}% CPU", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                    }
                }
            }
        },
        leadingContent = {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (proc.isSystemApp) Icons.Default.Memory else Icons.Default.Android, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            if (!proc.isSystemApp || proc.user != "root") {
                IconButton(onClick = onKill) {
                    Icon(Icons.Outlined.Close, "Kill", tint = AccentRed, modifier = Modifier.size(20.dp))
                }
            }
        },
    )
}

@Composable
private fun ServiceRow(svc: ServiceEntry) {
    ListItem(
        headlineContent = { Text(svc.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(svc.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("PID: ${svc.pid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = {
            Surface(shape = CircleShape, color = if (svc.isRunning) AccentGreen.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, null, Modifier.size(20.dp), tint = if (svc.isRunning) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = {
            Surface(shape = RoundedCornerShape(6.dp), color = if (svc.isRunning) AccentGreen.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant) {
                Text(if (svc.isRunning) "RUNNING" else "STOPPED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (svc.isRunning) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        },
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

private fun ClosedRange<Float>.random(): Float = (Math.random() * (endInclusive - start) + start).toFloat()
