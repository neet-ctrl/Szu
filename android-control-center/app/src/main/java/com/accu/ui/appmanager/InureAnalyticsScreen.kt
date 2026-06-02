package com.accu.ui.appmanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.connection.AccuConnectionManager
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val launchCount: Int,
    val lastUsed: Long,
    val color: Color,
)

data class InstallerStat(val installer: String, val count: Int, val color: Color)


fun formatDuration(ms: Long): String {
    val hours = ms / 3600000
    val minutes = (ms % 3600000) / 60000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

// Chart colors palette
private val CHART_COLORS = listOf(
    Color(0xFF6750A4), Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFEA4335),
    Color(0xFFFBBC05), Color(0xFFE91E63), Color(0xFF00BCD4), Color(0xFF9C27B0),
    Color(0xFFFF9800), Color(0xFF607D8B),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureAnalyticsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Usage", "Installers", "Size", "Permissions", "Min SDK", "Target SDK", "Pkg Type")
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Analytics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { idx, tab ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(tab) })
                }
            }
            when (selectedTab) {
                0 -> UsageTab(connectionManager)
                1 -> InstallerTab(connectionManager)
                2 -> SizeTab(connectionManager)
                3 -> PermissionsTab(connectionManager)
                4 -> SdkDistributionTab(connectionManager, isMin = true)
                5 -> SdkDistributionTab(connectionManager, isMin = false)
                6 -> PackageTypeTab(connectionManager)
            }
        }
    }
}

// ─── Usage Tab ───────────────────────────────────────────────────────────────

@Composable
private fun UsageTab(connectionManager: AccuConnectionManager) {
    var stats by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Query usage stats from TARGET device via ADB dumpsys — never local UsageStatsManager
                val raw = connectionManager.exec("dumpsys usagestats 2>/dev/null").output
                val pkgMap = mutableMapOf<String, Long>()
                var currentPkg = ""
                raw.lines().forEach { line ->
                    val t = line.trim()
                    when {
                        t.startsWith("package=") -> {
                            currentPkg = t.substringAfter("package=").substringBefore(" ").trim()
                        }
                        (t.startsWith("totalTime=") || t.startsWith("totalTimeInForeground=")) && currentPkg.isNotEmpty() -> {
                            val ms = t.substringAfter("=").substringBefore(" ").toLongOrNull() ?: 0L
                            pkgMap[currentPkg] = (pkgMap[currentPkg] ?: 0L) + ms
                        }
                    }
                }
                stats = pkgMap.entries
                    .filter { it.value > 60_000L }
                    .sortedByDescending { it.value }
                    .take(10)
                    .mapIndexed { idx, (pkg, ms) ->
                        val name = pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                        AppUsageStat(pkg, name, ms, 0, System.currentTimeMillis(), CHART_COLORS[idx % CHART_COLORS.size])
                    }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val totalMs = stats.sumOf { it.usageTimeMs }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Today's Screen Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(formatDuration(totalMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (stats.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        BarChart(stats = stats, total = totalMs)
                    }
                }
            }
        }
        if (stats.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BarChart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No usage data available from target device", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Requires root or ADB connection to target device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item { Text("Top Apps by Usage (Target Device)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(stats.sortedByDescending { it.usageTimeMs }) { stat ->
                UsageStatCard(stat = stat, total = totalMs)
            }
        }
    }
}

@Composable
private fun BarChart(stats: List<AppUsageStat>, total: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stats.forEach { stat ->
            val fraction = stat.usageTimeMs.toFloat() / total
            Box(modifier = Modifier.weight(fraction.coerceAtLeast(0.02f)).fillMaxHeight().background(stat.color))
        }
        val usedFraction = stats.sumOf { it.usageTimeMs }.toFloat() / total
        if (usedFraction < 1f) {
            Box(modifier = Modifier.weight(1f - usedFraction).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(stats) { stat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(stat.color))
                Text(stat.appName.take(8), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun UsageStatCard(stat: AppUsageStat, total: Long) {
    val fraction = stat.usageTimeMs.toFloat() / total
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(stat.color))
                Spacer(Modifier.width(8.dp))
                Text(stat.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(formatDuration(stat.usageTimeMs), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = stat.color)
            Text("${"%.1f".format(fraction * 100)}% of total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ─── Installer Tab ────────────────────────────────────────────────────────────

@Composable
private fun InstallerTab(connectionManager: AccuConnectionManager) {
    var stats by remember { mutableStateOf<List<InstallerStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Query packages and installers from TARGET device via ADB
                val rawAll = connectionManager.exec("pm list packages -i 2>/dev/null").output
                val systemRaw = connectionManager.exec("pm list packages -s 2>/dev/null").output
                val systemPkgs = systemRaw.lines().filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }.toSet()

                val installerMap = mutableMapOf<String, Int>()
                rawAll.lines().filter { it.startsWith("package:") }.forEach { line ->
                    val pkg = line.substringAfter("package:").substringBefore(" ").trim()
                    val installerRaw = Regex("installer[=:]([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
                        ?.let { if (it == "null" || it.isEmpty()) null else it }
                    val label = when (installerRaw) {
                        "com.android.vending"   -> "Google Play"
                        "org.fdroid.fdroid"     -> "F-Droid"
                        "com.aurora.store"      -> "Aurora Store"
                        "com.amazon.venezia"    -> "Amazon"
                        null -> if (pkg in systemPkgs) "System" else "Unknown"
                        else -> if (pkg in systemPkgs) "System" else "Sideloaded"
                    }
                    installerMap[label] = (installerMap[label] ?: 0) + 1
                }
                stats = installerMap.entries.sortedByDescending { it.value }.mapIndexed { i, e ->
                    InstallerStat(e.key, e.value, CHART_COLORS[i % CHART_COLORS.size])
                }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val total = stats.sumOf { it.count }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            PieChart(segments = stats.map { it.count.toFloat() to it.color })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Total Apps", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stats) { stat ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(stat.color))
                        Spacer(Modifier.width(10.dp))
                        Text(stat.installer, modifier = Modifier.weight(1f))
                        Text("${stat.count}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("${"%.0f".format(stat.count * 100f / total)}%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PieChart(segments: List<Pair<Float, Color>>) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat()
    Canvas(modifier = Modifier.size(160.dp)) {
        var startAngle = -90f
        segments.forEach { (count, color) ->
            val sweep = if (total > 0f) (count / total) * 360f else 0f
            drawArc(color = color, startAngle = startAngle, sweepAngle = sweep, useCenter = false, style = Stroke(width = 36f), topLeft = Offset(36f, 36f), size = Size(size.width - 72f, size.height - 72f))
            startAngle += sweep
        }
    }
}

// ─── Size Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun SizeTab(connectionManager: AccuConnectionManager) {
    var apps by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Get APK paths and sizes from TARGET device via ADB shell loop
                val script = """pm list packages -3 2>/dev/null | sed 's/package://' | while read pkg; do path=$(pm path "${'$'}pkg" 2>/dev/null | sed 's/package://'); [ -n "${'$'}path" ] && sz=$(stat -c %s "${'$'}path" 2>/dev/null) && [ "${'$'}sz" -gt 0 ] && echo "${'$'}pkg|${'$'}sz"; done 2>/dev/null"""
                val raw = connectionManager.exec(script).output
                apps = raw.lines().filter { it.contains("|") }.mapNotNull { line ->
                    val parts = line.split("|")
                    val pkg  = parts.getOrNull(0)?.trim() ?: return@mapNotNull null
                    val sz   = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: return@mapNotNull null
                    val name = pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                    name to sz
                }.sortedByDescending { it.second }.take(20)
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val maxSize = apps.firstOrNull()?.second ?: 1L
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val totalSize = apps.sumOf { it.second }
                    SizeMetric("${apps.size} Apps", "${totalSize / 1_048_576} MB total")
                    SizeMetric("Largest", if (apps.isNotEmpty()) "${apps.first().second / 1_048_576} MB" else "—")
                    SizeMetric("Average", if (apps.isNotEmpty()) "${(apps.sumOf { it.second } / apps.size) / 1_048_576} MB" else "—")
                }
            }
        }
        items(apps) { (name, size) ->
            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(name, modifier = Modifier.weight(1f))
                        Text(if (size >= 1_048_576) "${"%.1f".format(size / 1_048_576.0)} MB" else "${"%.0f".format(size / 1024.0)} KB", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { size.toFloat() / maxSize }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SizeMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ─── Permissions Tab ──────────────────────────────────────────────────────────

@Composable
private fun PermissionsTab(connectionManager: AccuConnectionManager) {
    var permStats by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Enumerate declared permissions across all user apps on TARGET device via ADB
                val script = """pm list packages -3 2>/dev/null | sed 's/package://' | while read pkg; do pm dump "${'$'}pkg" 2>/dev/null | grep -o 'android.permission.[A-Z_]*'; done 2>/dev/null"""
                val raw = connectionManager.exec(script).output
                val permMap = mutableMapOf<String, Int>()
                raw.lines().filter { it.startsWith("android.permission.") }.forEach { perm ->
                    val short = perm.substringAfterLast(".")
                    permMap[short] = (permMap[short] ?: 0) + 1
                }
                permStats = permMap.entries.sortedByDescending { it.value }.take(20).map { it.key to it.value }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Most Requested Permissions (Target Device)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        val max = permStats.firstOrNull()?.second?.toFloat() ?: 1f
        items(permStats) { (perm, count) ->
            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(perm, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("$count apps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { count / max }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

// ─── SDK Distribution Tab ─────────────────────────────────────────────────────

@Composable
private fun SdkDistributionTab(connectionManager: AccuConnectionManager, isMin: Boolean) {
    var stats by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(isMin) {
        withContext(Dispatchers.IO) {
            try {
                // Query SDK distribution from TARGET device via ADB — O(1) batch shell script
                val field = if (isMin) "minSdk" else "targetSdk"
                val script = """pm list packages 2>/dev/null | sed 's/package://' | while read pkg; do dumpsys package "${'$'}pkg" 2>/dev/null | grep -m1 '$field=' | grep -o '$field=[0-9]*'; done 2>/dev/null"""
                val raw = connectionManager.exec(script).output
                val sdkMap = mutableMapOf<Int, Int>()
                raw.lines().forEach { line ->
                    val sdk = line.substringAfter("$field=").toIntOrNull() ?: return@forEach
                    if (sdk > 0) sdkMap[sdk] = (sdkMap[sdk] ?: 0) + 1
                }
                val sdkNames = mapOf(
                    21 to "5.0 (L)", 22 to "5.1 (L MR1)", 23 to "6.0 (M)", 24 to "7.0 (N)",
                    25 to "7.1 (N MR1)", 26 to "8.0 (O)", 27 to "8.1 (O MR1)", 28 to "9.0 (P)",
                    29 to "10 (Q)", 30 to "11 (R)", 31 to "12 (S)", 32 to "12L (S v2)",
                    33 to "13 (T)", 34 to "14 (U)", 35 to "15 (V)",
                )
                stats = sdkMap.entries.sortedByDescending { it.value }
                    .map { (sdk, count) -> "${sdkNames[sdk] ?: "API $sdk"}" to count }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val total = stats.sumOf { it.second }
    val maxCount = stats.firstOrNull()?.second?.toFloat() ?: 1f

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            PieChart(stats.take(8).mapIndexed { i, (_, count) -> count.toFloat() to CHART_COLORS[i % CHART_COLORS.size] })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(if (isMin) "Min SDK" else "Target SDK", style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    if (isMin) "Apps by Minimum Android Version (Target Device)"
                    else "Apps by Target Android Version (Target Device)",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                )
            }
            itemsIndexed(stats) { idx, (label, count) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(CHART_COLORS[idx % CHART_COLORS.size]))
                            Spacer(Modifier.width(8.dp))
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("$count", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("${"%.0f".format(count * 100f / total)}%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { count / maxCount }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = CHART_COLORS[idx % CHART_COLORS.size])
                    }
                }
            }
        }
    }
}

// ─── Package Type Tab ─────────────────────────────────────────────────────────

@Composable
private fun PackageTypeTab(connectionManager: AccuConnectionManager) {
    var stats by remember { mutableStateOf<List<Triple<String, Int, Color>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Count package categories on TARGET device via ADB
                val userCount   = connectionManager.exec("pm list packages -3 2>/dev/null | wc -l").output.trim().toIntOrNull() ?: 0
                val systemCount = connectionManager.exec("pm list packages -s 2>/dev/null | wc -l").output.trim().toIntOrNull() ?: 0
                stats = listOf(
                    Triple("User Apps",    userCount,   CHART_COLORS[0]),
                    Triple("System Apps",  systemCount, CHART_COLORS[1]),
                ).filter { it.second > 0 }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val total = stats.sumOf { it.second }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            PieChart(stats.map { it.second.toFloat() to it.third })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Total Apps", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("App Categories (Target Device)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(stats) { (label, count, color) ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(10.dp))
                        Text(label, modifier = Modifier.weight(1f))
                        Text("$count", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        if (total > 0) Text("${"%.0f".format(count * 100f / total)}%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
