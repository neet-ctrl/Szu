package com.accu.ui.storage

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.ACCTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class StorageSegment(val label: String, val gbUsed: Float, val color: Color)
data class StorageAppEntry(
    val name: String,
    val pkg: String,
    val appSizeMb: Float,
    val dataSizeMb: Float,
    val cacheSizeMb: Float,
) {
    val totalMb get() = appSizeMb + dataSizeMb + cacheSizeMb
}

data class StorageAnalyzerState(
    val isLoading: Boolean = false,
    val totalGb: Float = 0f,
    val usedGb: Float = 0f,
    val freeGb: Float = 0f,
    val segments: List<StorageSegment> = emptyList(),
    val apps: List<StorageAppEntry> = emptyList(),
    val errorMessage: String? = null,
    val lastUpdated: String = "",
)

@HiltViewModel
class StorageAnalyzerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageAnalyzerState())
    val state: StateFlow<StorageAnalyzerState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // ── 1. Disk usage overview ──────────────────────────────────────
                // Query /sdcard only — on modern Android /data and /sdcard are the
                // same physical partition; querying both doubles every number.
                val dfOut = connectionManager.exec("df -k /sdcard 2>/dev/null").output
                var totalKb = 0L; var usedKb = 0L; var freeKb = 0L
                dfOut.lines().drop(1).forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val t = parts[1].toLongOrNull() ?: 0L
                        val u = parts[2].toLongOrNull() ?: 0L
                        val f = parts[3].toLongOrNull() ?: 0L
                        totalKb += t; usedKb += u; freeKb += f
                    }
                }
                // Fallback: parse "df -h" human-readable if -k returns nothing
                if (totalKb == 0L) {
                    val dfH = connectionManager.exec("df -h /sdcard 2>/dev/null").output
                    dfH.lines().drop(1).firstOrNull()?.let { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            totalKb  = parseHumanSize(parts[1])
                            usedKb   = parseHumanSize(parts[2])
                            freeKb   = parseHumanSize(parts[3])
                        }
                    }
                }
                val totalGb = totalKb / 1048576f
                val usedGb  = usedKb / 1048576f
                val freeGb  = freeKb / 1048576f

                // ── 2. Per-directory breakdown on /sdcard ──────────────────────
                val duOut = connectionManager.exec(
                    "du -sk /sdcard/DCIM /sdcard/Pictures /sdcard/Movies /sdcard/Music " +
                    "/sdcard/Download /sdcard/Documents /sdcard/Android 2>/dev/null"
                ).output
                val dirMap = mutableMapOf<String, Float>()
                duOut.lines().forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex(), 2)
                    if (parts.size == 2) {
                        val kb = parts[0].toLongOrNull() ?: return@forEach
                        val dir = parts[1].substringAfterLast("/")
                        dirMap[dir] = kb / 1048576f
                    }
                }
                val catColors = mapOf(
                    "DCIM"      to Color(0xFF0288D1),
                    "Pictures"  to Color(0xFF8E24AA),
                    "Movies"    to Color(0xFFE53935),
                    "Music"     to Color(0xFF1E88E5),
                    "Download"  to Color(0xFFF57C00),
                    "Documents" to Color(0xFF2E7D32),
                    "Android"   to Color(0xFF607D8B),
                )
                val segList = mutableListOf<StorageSegment>()
                var assignedGb = 0f
                dirMap.forEach { (dir, gb) ->
                    if (gb > 0f) {
                        segList.add(StorageSegment(dir, gb, catColors[dir] ?: Color(0xFF757575)))
                        assignedGb += gb
                    }
                }
                val systemGb = (usedGb - assignedGb).coerceAtLeast(0f)
                if (systemGb > 0.01f)
                    segList.add(StorageSegment("System", systemGb, Color(0xFFD32F2F)))
                if (freeGb > 0f)
                    segList.add(StorageSegment("Free", freeGb, Color(0xFFE0E0E0)))

                // ── 3. Top apps by data size ───────────────────────────────────
                val pkgs = connectionManager.listPackages(thirdPartyOnly = true).take(30)
                val appEntries = mutableListOf<StorageAppEntry>()
                if (pkgs.isNotEmpty()) {
                    val pkgList = pkgs.joinToString(" ") { "/data/data/${it.packageName}" }
                    val duApps = connectionManager.exec(
                        "du -sk $pkgList 2>/dev/null"
                    ).output
                    duApps.lines().forEach { line ->
                        val parts = line.trim().split("\\s+".toRegex(), 2)
                        if (parts.size == 2) {
                            val kb  = parts[0].toLongOrNull() ?: return@forEach
                            val pkg = parts[1].substringAfterLast("/")
                            val label = pkg.split(".").lastOrNull()
                                ?.replaceFirstChar { it.uppercase() } ?: pkg
                            appEntries.add(
                                StorageAppEntry(
                                    name       = label,
                                    pkg        = pkg,
                                    appSizeMb  = 0f,
                                    dataSizeMb = kb / 1024f,
                                    cacheSizeMb= 0f,
                                )
                            )
                        }
                    }
                }

                _state.update {
                    it.copy(
                        isLoading   = false,
                        totalGb     = if (totalGb > 0f) totalGb else 128f,
                        usedGb      = if (usedGb > 0f) usedGb else 0f,
                        freeGb      = if (freeGb > 0f) freeGb else 0f,
                        segments    = segList,
                        apps        = appEntries.sortedByDescending { e -> e.totalMb },
                        lastUpdated = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date()),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "Failed: ${e.message?.take(80)}") }
            }
        }
    }

    private fun parseHumanSize(s: String): Long {
        val clean = s.uppercase().trim()
        return when {
            clean.endsWith("G") -> ((clean.dropLast(1).toFloatOrNull() ?: 0f) * 1_048_576).toLong()
            clean.endsWith("M") -> ((clean.dropLast(1).toFloatOrNull() ?: 0f) * 1_024).toLong()
            clean.endsWith("K") -> (clean.dropLast(1).toLongOrNull() ?: 0L)
            else                -> clean.toLongOrNull() ?: 0L
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(
    onBack: () -> Unit = {},
    viewModel: StorageAnalyzerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf("Apps") }
    var sortMode by remember { mutableStateOf("Total") }

    val sortedApps = remember(state.apps, sortMode) {
        when (sortMode) {
            "App size" -> state.apps.sortedByDescending { it.appSizeMb }
            "Data"     -> state.apps.sortedByDescending { it.dataSizeMb }
            "Cache"    -> state.apps.sortedByDescending { it.cacheSizeMb }
            else       -> state.apps.sortedByDescending { it.totalMb }
        }
    }

    val usedSegments = remember(state.segments) { state.segments.filter { it.label != "Free" } }
    val totalGb = state.totalGb
    val usedGb  = state.usedGb
    val freeGb  = state.freeGb

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Storage Analyzer",
                onBack = onBack,
                actions = {
                    if (state.lastUpdated.isNotBlank()) {
                        Text(
                            "Updated ${state.lastUpdated}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // Loading / error state
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Reading device storage via ADB…", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                return@LazyColumn
            }

            state.errorMessage?.let { msg ->
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Usage overview card
            item {
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (usedGb > 0f) {
                                    Text(
                                        "${"%.1f".format(usedGb)} GB used",
                                        fontWeight = FontWeight.Bold, fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "of ${totalGb.roundToInt()} GB (${"%.1f".format(freeGb)} GB free)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Text(
                                        "Connect to a device to see storage",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(Icons.Default.Storage, null, modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }

                        if (usedGb > 0f && totalGb > 0f) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { (usedGb / totalGb).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                            if (state.segments.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                // Pie / donut chart
                                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                                    val cx = size.width / 2f; val cy = size.height / 2f
                                    val r = (size.height / 2f) * 0.85f
                                    var startAngle = -90f
                                    val total = state.segments.sumOf { it.gbUsed.toDouble() }.toFloat()
                                    if (total > 0f) {
                                        state.segments.forEach { seg ->
                                            val sweep = (seg.gbUsed / total) * 360f
                                            drawArc(seg.color, startAngle, sweep, true,
                                                Offset(cx - r, cy - r), Size(r * 2, r * 2))
                                            startAngle += sweep
                                        }
                                        drawCircle(Color.White, r * 0.6f, Offset(cx, cy))
                                    }
                                }
                                // Legend
                                usedSegments.chunked(3).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        row.forEach { seg ->
                                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(10.dp).background(seg.color))
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    "${seg.label}\n${"%.1f".format(seg.gbUsed)}GB",
                                                    fontSize = 9.sp, lineHeight = 12.sp,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Tab + sort controls
            item {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Apps", "Categories").forEach { mode ->
                        FilterChip(selected = viewMode == mode, onClick = { viewMode = mode }, label = { Text(mode) })
                    }
                    Spacer(Modifier.weight(1f))
                    if (viewMode == "Apps") {
                        var showSort by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { showSort = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Icon(Icons.Default.Sort, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(sortMode, fontSize = 12.sp)
                            }
                            DropdownMenu(showSort, { showSort = false }) {
                                listOf("Total", "App size", "Data", "Cache").forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = { sortMode = s; showSort = false })
                                }
                            }
                        }
                    }
                }
            }

            if (viewMode == "Apps") {
                if (sortedApps.isEmpty() && !state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No app data — connect a device and tap refresh",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    items(sortedApps, key = { it.pkg }) { app ->
                        ListItem(
                            headlineContent = { Text(app.name) },
                            supportingContent = {
                                Column {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (app.appSizeMb > 0f)  Text("App: ${formatMb(app.appSizeMb)}",  fontSize = 11.sp)
                                        if (app.dataSizeMb > 0f) Text("Data: ${formatMb(app.dataSizeMb)}", fontSize = 11.sp)
                                        if (app.cacheSizeMb > 0f)Text("Cache: ${formatMb(app.cacheSizeMb)}", fontSize = 11.sp)
                                    }
                                    val maxMb = sortedApps.firstOrNull()?.totalMb ?: 1f
                                    LinearProgressIndicator(
                                        progress = { (app.totalMb / maxMb).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                    )
                                }
                            },
                            leadingContent = { Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Text(formatMb(app.totalMb), fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            },
                            modifier = Modifier.clickable {}
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                if (usedSegments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No storage data — connect a device and tap refresh",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    items(usedSegments) { seg ->
                        ListItem(
                            headlineContent = { Text(seg.label) },
                            supportingContent = {
                                LinearProgressIndicator(
                                    progress = { if (usedGb > 0f) (seg.gbUsed / usedGb).coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = seg.color,
                                )
                            },
                            leadingContent = { Box(Modifier.size(16.dp).background(seg.color)) },
                            trailingContent = {
                                Text("${"%.1f".format(seg.gbUsed)} GB", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun formatMb(mb: Float) = if (mb >= 1024) "${"%.1f".format(mb / 1024f)} GB" else "${mb.roundToInt()} MB"
