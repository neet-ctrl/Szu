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
import com.accu.ui.shizuku.ShizukuViewModel
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private fun parseProcessLine(line: String): ProcessEntry? {
    return try {
        val parts = line.trim().split(Regex("\\s+"))
        // toybox ps -A output: USER PID PPID VSZ RSS WCHAN ADDR S NAME
        if (parts.size < 9) return null
        val user   = parts[0]
        val pid    = parts[1].toIntOrNull() ?: return null
        val rssKb  = parts[4].toLongOrNull() ?: 0L
        val status = parts[7].firstOrNull()?.toString() ?: "S"
        val name   = parts[8].trimStart('[').trimEnd(']')
        if (name == "NAME" || pid <= 0) return null
        ProcessEntry(
            pid       = pid,
            name      = name,
            user      = user,
            memKb     = rssKb,
            status    = status,
            isSystemApp = user == "root" || user == "system" || user == "radio" || user == "media" || user.startsWith("audioserver") || user == "cameraserver",
        )
    } catch (_: Exception) { null }
}

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
    val vm: ShizukuViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(ProcessTab.PROCESSES) }
    var sortBy by remember { mutableStateOf(SortBy.CPU) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var processes by remember { mutableStateOf(emptyList<ProcessEntry>()) }
    var services by remember { mutableStateOf(emptyList<ServiceEntry>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var killTarget by remember { mutableStateOf<ProcessEntry?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var hideSystem by remember { mutableStateOf(false) }

    suspend fun loadProcesses() {
        val result = withContext(Dispatchers.IO) {
            vm.connectionManager.exec("ps -A 2>/dev/null || ps 2>/dev/null")
        }
        if (result.isSuccess && result.output.isNotBlank()) {
            processes = result.output.lines()
                .drop(1)
                .mapNotNull { parseProcessLine(it) }
                .distinctBy { it.pid }
        }
        // Load services via dumpsys activity services (compact)
        val svcResult = withContext(Dispatchers.IO) {
            vm.connectionManager.exec("dumpsys activity services -c 2>/dev/null | grep 'ServiceRecord{' | head -40")
        }
        if (svcResult.isSuccess && svcResult.output.isNotBlank()) {
            services = svcResult.output.lines()
                .mapNotNull { line ->
                    val nameMatch = Regex("""ServiceRecord\{[^ ]+ ([^ ]+)\}""").find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                    val pkg = nameMatch.substringBeforeLast("/")
                    val svcClass = nameMatch.substringAfterLast("/").let { if (it.startsWith(".")) pkg + it else it }
                    ServiceEntry(svcClass.substringAfterLast("."), pkg, 0, true)
                }
                .take(30)
        }
    }

    LaunchedEffect(Unit) {
        loadProcesses()
        isLoading = false
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
                    scope.launch {
                        withContext(Dispatchers.IO) { vm.connectionManager.exec("kill -9 ${proc.pid} 2>/dev/null") }
                        processes = processes.filter { it.pid != proc.pid }
                        killTarget = null
                    }
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
                            scope.launch { isRefreshing = true; loadProcesses(); isRefreshing = false }
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                when (selectedTab) {
                    ProcessTab.PROCESSES -> {
                        if (sorted.isEmpty()) {
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Memory, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Text("No processes found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("ACCU connection required for live process list", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                items(sorted, key = { it.pid }) { proc ->
                                    ProcessRow(proc, onKill = { killTarget = proc })
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                    ProcessTab.SERVICES -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(services, key = { it.name }) { svc -> ServiceRow(svc) }
                    }
                }
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

