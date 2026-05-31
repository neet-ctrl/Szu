package com.accu.ui.appmanager

import androidx.compose.animation.*
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
import kotlin.math.cos
import kotlin.math.sin

data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val launchCount: Int,
    val lastUsed: Long,
    val color: Color,
)

data class InstallerStat(val installer: String, val count: Int, val color: Color)

val SAMPLE_USAGE_STATS = listOf(
    AppUsageStat("com.google.android.youtube", "YouTube", 12_600_000L, 45, System.currentTimeMillis() - 3600000, Color(0xFFFF0000)),
    AppUsageStat("com.instagram.android", "Instagram", 8_400_000L, 89, System.currentTimeMillis() - 1800000, Color(0xFFE91E63)),
    AppUsageStat("com.twitter.android", "X (Twitter)", 4_200_000L, 34, System.currentTimeMillis() - 7200000, Color(0xFF1DA1F2)),
    AppUsageStat("com.whatsapp", "WhatsApp", 3_600_000L, 120, System.currentTimeMillis() - 900000, Color(0xFF25D366)),
    AppUsageStat("com.google.android.gm", "Gmail", 2_100_000L, 28, System.currentTimeMillis() - 14400000, Color(0xFFEA4335)),
)

val SAMPLE_INSTALLER_STATS = listOf(
    InstallerStat("Google Play", 78, Color(0xFF4285F4)),
    InstallerStat("ADB/Unknown", 12, Color(0xFF34A853)),
    InstallerStat("System", 45, Color(0xFFEA4335)),
    InstallerStat("Sideloaded", 8, Color(0xFFFBBC05)),
)

fun formatDuration(ms: Long): String {
    val hours = ms / 3600000
    val minutes = (ms % 3600000) / 60000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureAnalyticsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Usage", "Installers", "Size", "Permissions")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Analytics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, tab ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(tab) })
                }
            }
            when (selectedTab) {
                0 -> UsageTab()
                1 -> InstallerTab()
                2 -> SizeTab()
                3 -> PermissionsTab()
            }
        }
    }
}

@Composable
private fun UsageTab() {
    val totalMs = SAMPLE_USAGE_STATS.sumOf { it.usageTimeMs }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Today's Screen Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(formatDuration(totalMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    BarChart(stats = SAMPLE_USAGE_STATS, total = totalMs)
                }
            }
        }
        item { Text("Top Apps by Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(SAMPLE_USAGE_STATS.sortedByDescending { it.usageTimeMs }) { stat ->
            UsageStatCard(stat = stat, total = totalMs)
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
            Box(
                modifier = Modifier
                    .weight(fraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .background(stat.color)
            )
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
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = stat.color,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${stat.launchCount} launches", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("${"%.1f".format(fraction * 100)}% of total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun InstallerTab() {
    val total = SAMPLE_INSTALLER_STATS.sumOf { it.count }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            PieChart(stats = SAMPLE_INSTALLER_STATS)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Total Apps", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SAMPLE_INSTALLER_STATS) { stat ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(stat.color))
                        Spacer(Modifier.width(10.dp))
                        Text(stat.installer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${stat.count} apps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("${"%.0f".format(stat.count * 100f / total)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PieChart(stats: List<InstallerStat>) {
    val total = stats.sumOf { it.count }.toFloat()
    Canvas(modifier = Modifier.size(160.dp)) {
        var startAngle = -90f
        stats.forEach { stat ->
            val sweep = (stat.count / total) * 360f
            drawArc(color = stat.color, startAngle = startAngle, sweepAngle = sweep, useCenter = false, style = Stroke(width = 36f), topLeft = Offset(36f, 36f), size = Size(size.width - 72f, size.height - 72f))
            startAngle += sweep
        }
    }
}

@Composable
private fun SizeTab() {
    val apps = remember {
        listOf(
            "Google Chrome" to "234 MB", "YouTube" to "128 MB", "WhatsApp" to "89 MB",
            "Instagram" to "156 MB", "Maps" to "67 MB", "Spotify" to "78 MB",
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SizeMetric("User Apps", "4.2 GB"); SizeMetric("System Apps", "3.1 GB"); SizeMetric("Cache", "1.8 GB")
                }
            }
        }
        items(apps) { (name, size) ->
            Card(shape = RoundedCornerShape(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(name, modifier = Modifier.weight(1f))
                    Text(size, fontWeight = FontWeight.Bold)
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

@Composable
private fun PermissionsTab() {
    val permissions = remember {
        listOf(
            "CAMERA" to 23, "LOCATION" to 45, "MICROPHONE" to 18, "CONTACTS" to 34,
            "STORAGE" to 67, "CALL_LOG" to 12, "SMS" to 8, "PHONE" to 21,
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Most Requested Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(permissions.sortedByDescending { it.second }) { (perm, count) ->
            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(perm, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("$count apps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
