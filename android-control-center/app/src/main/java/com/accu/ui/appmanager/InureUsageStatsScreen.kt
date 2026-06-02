package com.accu.ui.appmanager

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UsageStat(
    val appName: String,
    val pkg: String,
    val todayMins: Int,
    val weekMins: Int,
    val launchCount: Int,
    val lastUsed: String,
    val category: String = "App",
    val dailyLimitMins: Int = 0,
)

private const val PREFS_LIMITS = "inure_usage_limits"

private fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }
}

private fun relativeTime(lastUsed: Long): String {
    val diff = System.currentTimeMillis() - lastUsed
    return when {
        lastUsed == 0L   -> "Never"
        diff < 60_000    -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000-> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "Yesterday"
        else             -> "${diff / 86_400_000}d ago"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureUsageStatsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    var period  by remember { mutableStateOf(0) }
    val periods = listOf("Today", "This Week", "This Month")
    var sortBy  by remember { mutableStateOf("usage") }
    var stats   by remember { mutableStateOf<List<UsageStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var permissionNeeded by remember { mutableStateOf(false) }
    var showLimitDialog  by remember { mutableStateOf<UsageStat?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    var limitMap by remember { mutableStateOf(mapOf<String, Int>()) }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    LaunchedEffect(period) {
        loading = true
        if (!hasUsageStatsPermission(context)) {
            permissionNeeded = true
            loading = false
            return@LaunchedEffect
        }
        permissionNeeded = false
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
                val limits = prefs.all.mapValues { (it.value as? Int) ?: 0 }
                limitMap = limits

                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val startTime = when (period) {
                    0    -> now - 86_400_000L
                    1    -> now - 7  * 86_400_000L
                    else -> now - 30 * 86_400_000L
                }
                val interval = when (period) {
                    0    -> UsageStatsManager.INTERVAL_DAILY
                    1    -> UsageStatsManager.INTERVAL_WEEKLY
                    else -> UsageStatsManager.INTERVAL_MONTHLY
                }
                val raw = usm.queryUsageStats(interval, startTime, now) ?: emptyList()

                val aggregated = mutableMapOf<String, Triple<Long, Int, Long>>()
                raw.forEach { s ->
                    if (s.totalTimeInForeground <= 0) return@forEach
                    val existing = aggregated[s.packageName]
                    aggregated[s.packageName] = if (existing != null) {
                        Triple(
                            existing.first + s.totalTimeInForeground,
                            existing.second,
                            maxOf(existing.third, s.lastTimeUsed),
                        )
                    } else {
                        Triple(s.totalTimeInForeground, 0, s.lastTimeUsed)
                    }
                }

                val pm = context.packageManager
                val result = aggregated.mapNotNull { (pkg, data) ->
                    val (totalMs, _, lastUsedMs) = data
                    val totalMins = (totalMs / 60_000).toInt()
                    if (totalMins <= 0) return@mapNotNull null
                    val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                    UsageStat(
                        appName      = appName,
                        pkg          = pkg,
                        todayMins    = totalMins,
                        weekMins     = totalMins,
                        launchCount  = 0,
                        lastUsed     = relativeTime(lastUsedMs),
                        category     = "App",
                        dailyLimitMins = limits[pkg] ?: 0,
                    )
                }.sortedByDescending { it.todayMins }.take(60)
                stats = result
            } catch (_: Exception) {
                permissionNeeded = true
            }
            loading = false
        }
    }

    val sorted = stats.sortedByDescending {
        when (sortBy) {
            "launches"  -> it.launchCount.toDouble()
            "last_used" -> 0.0
            else        -> if (period == 0) it.todayMins.toDouble() else it.weekMins.toDouble()
        }
    }
    val totalMins = if (period == 0) stats.sumOf { it.todayMins } else stats.sumOf { it.weekMins }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Usage Statistics",
                onBack = onBack,
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("By Usage Time") }, leadingIcon = { if (sortBy == "usage") Icon(Icons.Default.Check, null) }, onClick = { sortBy = "usage"; showSortMenu = false })
                            DropdownMenuItem(text = { Text("By Launch Count") }, leadingIcon = { if (sortBy == "launches") Icon(Icons.Default.Check, null) }, onClick = { sortBy = "launches"; showSortMenu = false })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (permissionNeeded) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Usage Access Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Grant 'Usage Access' to ACCU in Settings to view real usage stats.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Usage Access Settings")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(periods.size) { i ->
                        FilterChip(selected = period == i, onClick = { period = i }, label = { Text(periods[i]) })
                    }
                }
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                return@LazyColumn
            }

            if (stats.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BarChart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("No usage data for this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                return@LazyColumn
            }

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
                                    drawArc(Color.White.copy(0.2f), 0f, 360f, false, style = stroke)
                                    val fraction = (totalMins.toFloat() / (if (period == 0) 600 else 4200)).coerceIn(0f, 1f)
                                    drawArc(Color.White, -90f, 360f * fraction, false, style = stroke)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${totalMins / 60}h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("${totalMins % 60}m", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.8f))
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${periods[period]} Screen Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                val hrs = totalMins / 60; val mins = totalMins % 60
                                Text("${hrs}h ${mins}m total", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("${sorted.firstOrNull()?.appName ?: "—"} most used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                                Spacer(Modifier.height(4.dp))
                                Text("${stats.size} apps tracked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                            }
                        }
                    }
                }
            }

            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Per App", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${sorted.size} apps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(sorted, key = { it.pkg }) { stat ->
                val mins      = if (period == 0) stat.todayMins else stat.weekMins
                val maxMins   = (if (period == 0) sorted.firstOrNull()?.todayMins else sorted.firstOrNull()?.weekMins) ?: 1
                val overLimit = stat.dailyLimitMins > 0 && stat.todayMins > stat.dailyLimitMins

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = if (overLimit) MaterialTheme.colorScheme.errorContainer.copy(0.4f) else MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stat.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (overLimit) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
                                        Text("LIMIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                "${mins}m · last: ${stat.lastUsed}${if (stat.dailyLimitMins > 0) " · limit: ${stat.dailyLimitMins}m" else ""}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (mins.toFloat() / maxMins).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            IconButton(onClick = { showLimitDialog = stat }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Timer, "Set limit", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    val limitStat = showLimitDialog
    if (limitStat != null) {
        var limitText by remember(limitStat.pkg) { mutableStateOf(if (limitStat.dailyLimitMins > 0) limitStat.dailyLimitMins.toString() else "") }
        AlertDialog(
            onDismissRequest = { showLimitDialog = null },
            icon = { Icon(Icons.Default.Timer, null) },
            title = { Text("Daily Limit — ${limitStat.appName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set a daily usage limit. ACCU will highlight this app when the limit is exceeded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = limitText, onValueChange = { limitText = it.filter { c -> c.isDigit() } },
                        label = { Text("Limit (minutes)") },
                        placeholder = { Text("0 = no limit") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    if (limitText.isNotBlank() && (limitText.toIntOrNull() ?: 0) < limitStat.todayMins) {
                        Text("⚠ Today's usage (${limitStat.todayMins}m) already exceeds this limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val limit = limitText.toIntOrNull() ?: 0
                    context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
                        .edit().putInt(limitStat.pkg, limit).apply()
                    limitMap = limitMap + (limitStat.pkg to limit)
                    stats = stats.map { if (it.pkg == limitStat.pkg) it.copy(dailyLimitMins = limit) else it }
                    snackbar = "Limit for ${limitStat.appName}: ${limitText.ifBlank { "0" }}m"
                    showLimitDialog = null
                }) { Text("Set Limit") }
            },
            dismissButton = { TextButton(onClick = { showLimitDialog = null }) { Text("Cancel") } },
        )
    }
}

