package com.accu.ui.appmanager

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppSummary(val name: String, val pkg: String, val usageMins: Int = 0, val size: String = "")

private val FOSS_INSTALLER_SOURCES = setOf("org.fdroid.fdroid", "com.aurora.store")
private val FOSS_PACKAGE_PREFIXES = listOf("org.fdroid.", "net.osmand", "org.videolan", "com.termux", "org.kde", "org.gnome", "io.github.", "net.gsantner", "de.k3b", "eu.faircode")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureHomeScreen(
    onBack: () -> Unit = {},
    onNavigateToBatteryOpt: () -> Unit = {},
    onNavigateToBootManager: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToMusic: () -> Unit = {},
    onNavigateToApks: () -> Unit = {},
    onNavigateToTrackers: () -> Unit = {},
    onNavigateToUsageStats: () -> Unit = {},
    onNavigateToDisabled: () -> Unit = {},
    onNavigateToMostUsed: () -> Unit = {},
    onNavigateToRecentlyInstalled: () -> Unit = {},
    onNavigateToUninstalled: () -> Unit = {},
    onNavigateToTags: () -> Unit = {},
    onNavigateToFoss: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager

    var mostUsed by remember { mutableStateOf<List<AppSummary>>(emptyList()) }
    var recentlyInstalled by remember { mutableStateOf<List<AppSummary>>(emptyList()) }
    var recentlyUpdated by remember { mutableStateOf<List<AppSummary>>(emptyList()) }
    var fossList by remember { mutableStateOf<List<AppSummary>>(emptyList()) }
    var disabledApps by remember { mutableStateOf<List<AppSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = try {
                pm.getInstalledPackages(PackageManager.GET_META_DATA)
            } catch (_: Exception) { emptyList() }

            // Recently installed (top 5, user apps only, sorted by firstInstallTime desc)
            val userPackages = packages.filter {
                it.applicationInfo?.let { ai -> (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } ?: false
            }
            recentlyInstalled = userPackages
                .sortedByDescending { it.firstInstallTime }
                .take(5)
                .map { pi ->
                    val ai = pi.applicationInfo ?: return@map null
                    val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pi.packageName }
                    val sizeBytes = try { java.io.File(ai.sourceDir ?: "").length() } catch (_: Exception) { 0L }
                    AppSummary(name, pi.packageName, size = if (sizeBytes > 1_000_000) "${"%.0f".format(sizeBytes / 1_000_000.0)} MB" else "?")
                }.filterNotNull()

            // Recently updated (top 5, different from firstInstall, user apps)
            recentlyUpdated = userPackages
                .filter { it.lastUpdateTime != it.firstInstallTime }
                .sortedByDescending { it.lastUpdateTime }
                .take(5)
                .map { pi ->
                    val ai = pi.applicationInfo ?: return@map null
                    val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pi.packageName }
                    val sizeBytes = try { java.io.File(ai.sourceDir ?: "").length() } catch (_: Exception) { 0L }
                    AppSummary(name, pi.packageName, size = if (sizeBytes > 1_000_000) "${"%.0f".format(sizeBytes / 1_000_000.0)} MB" else "?")
                }.filterNotNull()

            // FOSS apps (installed from F-Droid or Aurora, or matching FOSS pkg prefixes)
            fossList = userPackages
                .filter { pi ->
                    val installer = try { pm.getInstallerPackageName(pi.packageName) } catch (_: Exception) { null }
                    installer in FOSS_INSTALLER_SOURCES ||
                    FOSS_PACKAGE_PREFIXES.any { pi.packageName.startsWith(it) }
                }
                .take(5)
                .map { pi ->
                    val ai = pi.applicationInfo ?: return@map null
                    val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pi.packageName }
                    AppSummary(name, pi.packageName)
                }.filterNotNull()

            // Disabled apps — from PackageManager (enabled state check)
            disabledApps = packages
                .filter { pi ->
                    pi.applicationInfo?.let { ai ->
                        !ai.enabled ||
                        ai.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                        ai.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    } ?: false
                }
                .take(5)
                .map { pi ->
                    val ai = pi.applicationInfo ?: return@map null
                    val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pi.packageName }
                    AppSummary(name, pi.packageName)
                }.filterNotNull()

            // Also try to get disabled apps from ADB if connected
            if (disabledApps.isEmpty()) {
                try {
                    val raw = connectionManager.exec("pm list packages -d 2>/dev/null").output
                    disabledApps = raw.lines()
                        .filter { it.startsWith("package:") }
                        .take(5)
                        .map { line ->
                            val pkg = line.removePrefix("package:").trim()
                            val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                            AppSummary(name, pkg)
                        }
                } catch (_: Exception) {}
            }

            // Most used today — from UsageStatsManager if permission granted
            val hasUsagePerm = try {
                val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) { false }

            if (hasUsagePerm) {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now) ?: emptyList()
                val aggregated = mutableMapOf<String, Long>()
                stats.forEach { s -> aggregated[s.packageName] = (aggregated[s.packageName] ?: 0L) + s.totalTimeInForeground }
                mostUsed = aggregated.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                    .take(5)
                    .mapNotNull { (pkg, ms) ->
                        val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { return@mapNotNull null }
                        AppSummary(name, pkg, (ms / 60_000).toInt())
                    }
            }

            // Fallback: if no usage stats, show recently used from PackageManager
            if (mostUsed.isEmpty()) {
                mostUsed = userPackages
                    .sortedByDescending { it.lastUpdateTime }
                    .take(5)
                    .mapNotNull { pi ->
                        val ai = pi.applicationInfo ?: return@mapNotNull null
                        val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pi.packageName }
                        AppSummary(name, pi.packageName, 0)
                    }
            }

            loading = false
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val quickPanels: List<Triple<String, ImageVector, () -> Unit>> = listOf(
        Triple("Battery Opt.",       Icons.Default.BatteryChargingFull,   onNavigateToBatteryOpt),
        Triple("Boot Manager",       Icons.Default.PowerSettingsNew,       onNavigateToBootManager),
        Triple("Usage Stats",        Icons.Default.BarChart,               onNavigateToUsageStats),
        Triple("Most Used",          Icons.Default.TrendingUp,             onNavigateToMostUsed),
        Triple("Trackers",           Icons.Default.TrackChanges,           onNavigateToTrackers),
        Triple("Disabled Apps",      Icons.Default.Block,                  onNavigateToDisabled),
        Triple("FOSS Apps",          Icons.Default.VolunteerActivism,      onNavigateToFoss),
        Triple("App Tags",           Icons.Default.Label,                  onNavigateToTags),
        Triple("Recently Installed", Icons.Default.InstallMobile,          onNavigateToRecentlyInstalled),
        Triple("Uninstalled",        Icons.Default.DeleteOutline,          onNavigateToUninstalled),
        Triple("APK Scanner",        Icons.Default.FindInPage,             onNavigateToApks),
        Triple("Analytics",          Icons.Default.PieChart,               onNavigateToAnalytics),
        Triple("Notes",              Icons.Default.Note,                   onNavigateToNotes),
        Triple("Music",              Icons.Default.MusicNote,              onNavigateToMusic),
    )

    Scaffold(
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            searchQuery, { searchQuery = it },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("Search apps…") },
                            singleLine = true,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { showSearch = false; searchQuery = "" }) { Icon(Icons.Default.Close, "Close") }
                    },
                )
            } else {
                ACCTopBar(
                    title = "Inure Home",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, "Search") }
                    },
                )
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Quick panels grid
            item {
                Spacer(Modifier.height(8.dp))
                Text("Manage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickPanels) { (label, icon, action) ->
                        ElevatedCard(Modifier.width(100.dp).clickable { action() }) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Loading real app data…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                return@LazyColumn
            }

            // Most Used
            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader(if (mostUsed.any { it.usageMins > 0 }) "Most Used Today" else "Recently Active") { onNavigateToMostUsed() }
            }
            if (mostUsed.isEmpty()) {
                item {
                    EmptySection("No usage data — grant Usage Access in Settings", Icons.Default.BarChart) { onNavigateToUsageStats() }
                }
            } else {
                val maxMins = mostUsed.maxOfOrNull { it.usageMins } ?: 1
                items(mostUsed, key = { "used_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(if (app.usageMins > 0) "${app.usageMins} min" else "Recently active", fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            if (app.usageMins > 0) {
                                LinearProgressIndicator(
                                    progress = { (app.usageMins.toFloat() / maxMins).coerceIn(0f, 1f) },
                                    modifier = Modifier.width(80.dp),
                                )
                            }
                        },
                        modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) },
                    )
                }
            }

            // Recently Installed
            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                SectionHeader("Recently Installed") { onNavigateToRecentlyInstalled() }
            }
            if (recentlyInstalled.isEmpty()) {
                item { EmptySection("No recently installed user apps", Icons.Default.InstallMobile) {} }
            } else {
                items(recentlyInstalled, key = { "installed_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.size.ifEmpty { app.pkg }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.tertiary) },
                        modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) },
                    )
                }
            }

            // Recently Updated
            item {
                HorizontalDivider()
                SectionHeader("Recently Updated") { onNavigateToRecentlyInstalled() }
            }
            if (recentlyUpdated.isEmpty()) {
                item { EmptySection("No recently updated apps", Icons.Default.Update) {} }
            } else {
                items(recentlyUpdated, key = { "updated_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.size.ifEmpty { app.pkg }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.Update, null, tint = MaterialTheme.colorScheme.secondary) },
                        modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) },
                    )
                }
            }

            // FOSS
            item {
                HorizontalDivider()
                SectionHeader("FOSS Apps") { onNavigateToFoss() }
            }
            if (fossList.isEmpty()) {
                item { EmptySection("No FOSS apps detected (install from F-Droid)", Icons.Default.VolunteerActivism) { onNavigateToFoss() } }
            } else {
                items(fossList, key = { "foss_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text("Open Source", fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.VolunteerActivism, null, tint = Color(0xFF22C55E)) },
                        modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) },
                    )
                }
            }

            // Disabled
            item {
                HorizontalDivider()
                SectionHeader("Disabled Apps") { onNavigateToDisabled() }
            }
            if (disabledApps.isEmpty()) {
                item { EmptySection("No disabled apps found", Icons.Default.CheckCircle) {} }
            } else {
                items(disabledApps, key = { "disabled_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text("Disabled", fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll) { Text("See all", fontSize = 12.sp) }
    }
}

@Composable
private fun EmptySection(message: String, icon: ImageVector, action: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { action() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
