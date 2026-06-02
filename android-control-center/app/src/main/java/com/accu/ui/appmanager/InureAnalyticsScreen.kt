package com.accu.ui.appmanager

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

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
    val tabs = listOf("Usage", "Installers", "Size", "Permissions", "Min SDK", "Target SDK", "Sign Algo", "Pkg Type")

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
                0 -> UsageTab()
                1 -> InstallerTab()
                2 -> SizeTab()
                3 -> PermissionsTab()
                4 -> SdkDistributionTab(isMin = true)
                5 -> SdkDistributionTab(isMin = false)
                6 -> SignatureAlgorithmTab()
                7 -> PackageTypeTab()
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${stat.launchCount} launches", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("${"%.1f".format(fraction * 100)}% of total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun InstallerTab() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<List<InstallerStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(0)
                val installerMap = mutableMapOf<String, Int>()
                for (pi in packages) {
                    val installer = try {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(pi.packageName) ?: "Unknown"
                    } catch (_: Exception) { "Unknown" }
                    val label = when (installer) {
                        "com.android.vending"   -> "Google Play"
                        "org.fdroid.fdroid"     -> "F-Droid"
                        "com.aurora.store"      -> "Aurora Store"
                        "com.amazon.venezia"    -> "Amazon"
                        ""                      -> "Unknown"
                        "Unknown"               -> "Unknown"
                        else -> if ((pi.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0) "System" else "Sideloaded"
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
            val sweep = (count / total) * 360f
            drawArc(color = color, startAngle = startAngle, sweepAngle = sweep, useCenter = false, style = Stroke(width = 36f), topLeft = Offset(36f, 36f), size = Size(size.width - 72f, size.height - 72f))
            startAngle += sweep
        }
    }
}

@Composable
private fun SizeTab() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(0)
                apps = packages.mapNotNull { pi ->
                    try {
                        val srcDir = pi.applicationInfo?.sourceDir ?: return@mapNotNull null
                        val size = java.io.File(srcDir).length()
                        if (size > 0) pm.getApplicationLabel(pi.applicationInfo!!).toString() to size else null
                    } catch (_: Exception) { null }
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

@Composable
private fun PermissionsTab() {
    val context = LocalContext.current
    var permStats by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                val permMap = mutableMapOf<String, Int>()
                for (pi in packages) {
                    pi.requestedPermissions?.forEach { perm ->
                        val short = perm.substringAfterLast(".")
                        permMap[short] = (permMap[short] ?: 0) + 1
                    }
                }
                permStats = permMap.entries.sortedByDescending { it.value }.take(20).map { it.key to it.value }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Most Requested Permissions (real device data)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
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

@Composable
private fun SdkDistributionTab(isMin: Boolean) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(isMin) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(0)
                val sdkMap = mutableMapOf<Int, Int>()
                for (pi in packages) {
                    val sdk = if (isMin) pi.applicationInfo?.minSdkVersion ?: 0
                    else pi.applicationInfo?.targetSdkVersion ?: 0
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
        // Pie chart
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            PieChart(stats.take(8).mapIndexed { i, (_, count) -> count.toFloat() to CHART_COLORS[i % CHART_COLORS.size] })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(if (isMin) "Min SDK" else "Target SDK", style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(if (isMin) "Apps by Minimum Android Version" else "Apps by Target Android Version",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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

@Composable
private fun SignatureAlgorithmTab() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val packages = pm.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES)
                val algoMap = mutableMapOf<String, Int>()
                for (pi in packages) {
                    val algo = try {
                        val sigs = pi.signingInfo?.apkContentsSigners ?: pi.signingInfo?.signingCertificateHistory
                        val certBytes = sigs?.firstOrNull()?.toByteArray()
                        if (certBytes != null) {
                            val cf = CertificateFactory.getInstance("X.509")
                            val cert = cf.generateCertificate(certBytes.inputStream()) as X509Certificate
                            cert.sigAlgName
                        } else "Unknown"
                    } catch (_: Exception) { "Unknown" }
                    algoMap[algo] = (algoMap[algo] ?: 0) + 1
                }
                stats = algoMap.entries.sortedByDescending { it.value }.map { it.key to it.value }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val total = stats.sumOf { it.second }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            PieChart(stats.mapIndexed { i, (_, count) -> count.toFloat() to CHART_COLORS[i % CHART_COLORS.size] })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Apps", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Apps by Signing Algorithm", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            itemsIndexed(stats) { idx, (algo, count) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(CHART_COLORS[idx % CHART_COLORS.size]))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(algo, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            val secLevel = when {
                                algo.contains("SHA256", true) || algo.contains("SHA384", true) || algo.contains("SHA512", true) -> "Modern — Good"
                                algo.contains("SHA1", true) || algo.contains("MD5", true) -> "Legacy — Weak"
                                else -> "Check manually"
                            }
                            Text(secLevel, style = MaterialTheme.typography.labelSmall, color = when {
                                secLevel.contains("Good") -> MaterialTheme.colorScheme.primary
                                secLevel.contains("Weak") -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                        }
                        Text("$count (${"%.0f".format(count * 100f / total)}%)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageTypeTab() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<List<Triple<String, Int, Color>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(0)
                var systemApps = 0; var updatedSystemApps = 0; var userApps = 0; var carrierApps = 0; var otherApps = 0
                for (pi in packages) {
                    val flags = pi.applicationInfo?.flags ?: 0
                    when {
                        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 -> updatedSystemApps++
                        flags and ApplicationInfo.FLAG_SYSTEM != 0 -> systemApps++
                        pi.packageName.startsWith("com.google.android") && flags and ApplicationInfo.FLAG_SYSTEM == 0 -> userApps++
                        else -> userApps++
                    }
                }
                stats = listOf(
                    Triple("User Apps", userApps, CHART_COLORS[0]),
                    Triple("System Apps", systemApps, CHART_COLORS[1]),
                    Triple("Updated System", updatedSystemApps, CHART_COLORS[2]),
                ).filter { it.second > 0 }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val total = stats.sumOf { it.second }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            PieChart(stats.map { (_, count, color) -> count.toFloat() to color })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Total", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Apps by Package Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            val max = stats.maxOfOrNull { it.second }?.toFloat() ?: 1f
            items(stats) { (label, count, color) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(10.dp))
                            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("$count (${"%.0f".format(count * 100f / total)}%)", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { count / max }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = color)
                    }
                }
            }
        }
    }
}
