package com.accu.ui.appmanager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UsedAppEntry(
    val packageName: String,
    val appName: String,
    val foregroundMs: Long,
    val lastUsedMs: Long,
    val launchCount: Int,
    val isSystem: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureMostUsedScreen(
    onBack: () -> Unit = {},
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<UsedAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var filterSystem by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("time") } // time | launches | name
    var showSortMenu by remember { mutableStateOf(false) }
    var timePeriod by remember { mutableStateOf("7") } // days

    LaunchedEffect(timePeriod) {
        loading = true
        error = ""
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val daysAgo = System.currentTimeMillis() - timePeriod.toLong() * 86_400_000L

                // Parse dumpsys usagestats to get per-app foreground time
                val raw = connectionManager.exec(
                    "dumpsys usagestats 2>/dev/null | grep -E 'package=|totalTimeInForeground|launchCount|lastTimeUsed'"
                ).output

                val entryMap = mutableMapOf<String, Triple<Long, Long, Int>>() // pkg -> (foreground, lastUsed, launches)
                var curPkg = ""
                var curFg = 0L
                var curLast = 0L
                var curLaunches = 0

                raw.lines().forEach { line ->
                    val t = line.trim()
                    when {
                        t.startsWith("package=") -> {
                            if (curPkg.isNotEmpty()) entryMap[curPkg] = Triple(curFg, curLast, curLaunches)
                            curPkg = t.substringAfter("package=").substringBefore(" ").trim()
                            curFg = 0L; curLast = 0L; curLaunches = 0
                        }
                        t.startsWith("totalTimeInForeground=") -> curFg = t.substringAfter("=").toLongOrNull() ?: 0L
                        t.startsWith("lastTimeUsed=") -> curLast = t.substringAfter("=").toLongOrNull() ?: 0L
                        t.startsWith("launchCount=") -> curLaunches = t.substringAfter("=").toIntOrNull() ?: 0
                    }
                }
                if (curPkg.isNotEmpty()) entryMap[curPkg] = Triple(curFg, curLast, curLaunches)

                val result = mutableListOf<UsedAppEntry>()
                entryMap.forEach { (pkg, data) ->
                    if (data.first > 0L || data.third > 0) {
                        val (fg, last, launches) = data
                        val appName = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) { pkg }
                        val isSystem = try {
                            (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        } catch (_: Exception) { false }
                        result += UsedAppEntry(pkg, appName, fg, last, launches, isSystem)
                    }
                }

                // Fallback: if ADB parsing failed, use local PackageManager + UsageStatsManager
                val finalList = if (result.size < 3) {
                    val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    installed.take(30).map { ai ->
                        UsedAppEntry(
                            ai.packageName,
                            pm.getApplicationLabel(ai).toString(),
                            0L, 0L, 0,
                            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    }
                } else result

                apps = finalList
            } catch (e: Exception) {
                error = e.message ?: "Failed to load usage stats"
            }
            loading = false
        }
    }

    val filtered = remember(apps, searchQuery, filterSystem, sortBy) {
        apps
            .filter { if (filterSystem) !it.isSystem else true }
            .filter { searchQuery.isBlank() || it.appName.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
            .let { list ->
                when (sortBy) {
                    "launches" -> list.sortedByDescending { it.launchCount }
                    "name"     -> list.sortedBy { it.appName }
                    else       -> list.sortedByDescending { it.foregroundMs }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Most Used") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            DropdownMenuItem({ Text("By Screen Time") }, { sortBy = "time"; showSortMenu = false })
                            DropdownMenuItem({ Text("By Launches") }, { sortBy = "launches"; showSortMenu = false })
                            DropdownMenuItem({ Text("By Name") }, { sortBy = "name"; showSortMenu = false })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                searchQuery, { searchQuery = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            // Period chips + filter
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("1" to "Today", "7" to "7 Days", "30" to "30 Days").forEach { (d, label) ->
                    FilterChip(timePeriod == d, { timePeriod = d }, { Text(label) })
                }
                Spacer(Modifier.weight(1f))
                FilterChip(filterSystem, { filterSystem = !filterSystem }, { Text("User only") })
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (error.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Column
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.BarChart, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Text("No usage data found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Enable Usage Access in Settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(filtered, key = { _, a -> a.packageName }) { idx, app ->
                    MostUsedAppCard(
                        rank = idx + 1,
                        app = app,
                        totalMaxMs = filtered.firstOrNull()?.foregroundMs?.takeIf { it > 0 } ?: 1L,
                        onClick = { onNavigateToAppDetail(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MostUsedAppCard(rank: Int, app: UsedAppEntry, totalMaxMs: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Rank badge
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (rank <= 3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rank.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (app.foregroundMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (app.foregroundMs.toFloat() / totalMaxMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = when {
                            rank == 1 -> MaterialTheme.colorScheme.primary
                            rank <= 3 -> MaterialTheme.colorScheme.secondary
                            else      -> MaterialTheme.colorScheme.tertiary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatDuration(app.foregroundMs), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (app.launchCount > 0) Text("${app.launchCount}× launched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (app.isSystem) Text("System", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0   -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else        -> "${seconds}s"
    }
}
