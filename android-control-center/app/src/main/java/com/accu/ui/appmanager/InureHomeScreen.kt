package com.accu.ui.appmanager

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

private val FOSS_INSTALLER_SOURCES = setOf("org.fdroid.fdroid", "org.fdroid.basic", "com.aurora.store")
private val FOSS_PACKAGE_PREFIXES = listOf(
    "org.fdroid.", "net.osmand", "org.videolan", "com.termux", "org.kde",
    "org.gnome", "io.github.", "net.gsantner", "de.k3b", "eu.faircode",
    "org.mozilla", "org.libreoffice", "org.thoughtcrime", "org.tasks",
    "com.nextcloud", "com.wireguard", "org.strongswan", "org.briarproject",
    "com.fsck", "com.machiav3llo", "org.fossify",
)

/** Derive a human-readable label from a package name. */
private fun labelFromPkg(pkg: String): String =
    pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg

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
            // ── Disabled apps from target device ─────────────────────────────
            try {
                val raw = connectionManager.exec("pm list packages -d 2>/dev/null").output
                disabledApps = raw.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() }
                    .take(5)
                    .map { pkg -> AppSummary(labelFromPkg(pkg), pkg) }
            } catch (_: Exception) { }

            // ── User (third-party) packages from target device ────────────────
            val userPkgs = try {
                val raw = connectionManager.exec("pm list packages -3 2>/dev/null").output
                raw.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() }
            } catch (_: Exception) { emptyList() }

            // Recently installed — show first N user packages (no timestamps available without per-pkg query)
            recentlyInstalled = userPkgs.take(5).map { pkg -> AppSummary(labelFromPkg(pkg), pkg) }

            // Recently updated — show next 5 user packages (as a proxy without timestamps)
            recentlyUpdated = userPkgs.drop(5).take(5).map { pkg -> AppSummary(labelFromPkg(pkg), pkg) }

            // FOSS — filter by package name patterns or installer from target
            val fossInstallerRaw = try {
                connectionManager.exec("pm list packages -3 -i 2>/dev/null").output
            } catch (_: Exception) { "" }
            // Parse "package:com.example  installer=org.fdroid.fdroid" lines
            val installerMap = fossInstallerRaw.lines()
                .filter { it.startsWith("package:") }
                .mapNotNull { line ->
                    val pkg = line.substringAfter("package:").substringBefore(" ").substringBefore("\t").trim()
                    val installer = if (line.contains("installer=")) line.substringAfter("installer=").trim() else ""
                    if (pkg.isNotEmpty()) pkg to installer else null
                }.toMap()

            fossList = userPkgs
                .filter { pkg ->
                    val installer = installerMap[pkg] ?: ""
                    installer in FOSS_INSTALLER_SOURCES ||
                    FOSS_PACKAGE_PREFIXES.any { pkg.startsWith(it) }
                }
                .take(5)
                .map { pkg -> AppSummary(labelFromPkg(pkg), pkg) }

            // ── Most used today — dumpsys usagestats on target device ──────────
            try {
                val raw = connectionManager.exec(
                    "dumpsys usagestats 2>/dev/null | grep -E 'package=|totalTimeInForeground|launchCount'"
                ).output
                val aggregated = mutableMapOf<String, Long>()
                var curPkg = ""
                raw.lines().forEach { line ->
                    val t = line.trim()
                    when {
                        t.startsWith("package=") -> curPkg = t.substringAfter("package=").substringBefore(" ").trim()
                        t.startsWith("totalTimeInForeground=") && curPkg.isNotEmpty() -> {
                            val ms = t.substringAfter("=").trim().toLongOrNull() ?: 0L
                            if (ms > 0) aggregated[curPkg] = (aggregated[curPkg] ?: 0L) + ms
                        }
                    }
                }
                if (aggregated.isNotEmpty()) {
                    mostUsed = aggregated.entries
                        .filter { it.value > 0 }
                        .sortedByDescending { it.value }
                        .take(5)
                        .map { (pkg, ms) -> AppSummary(labelFromPkg(pkg), pkg, (ms / 60_000).toInt()) }
                }
            } catch (_: Exception) { }

            // Fallback for most-used if usagestats unavailable: show top recently installed
            if (mostUsed.isEmpty()) {
                mostUsed = recentlyInstalled
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
                            Text("Loading from target device…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    EmptySection("No usage data from target device", Icons.Default.BarChart) { onNavigateToUsageStats() }
                }
            } else {
                val maxMins = mostUsed.maxOfOrNull { it.usageMins } ?: 1
                items(mostUsed, key = { "used_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(if (app.usageMins > 0) "${app.usageMins} min" else app.pkg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                SectionHeader("User Apps (Target Device)") { onNavigateToRecentlyInstalled() }
            }
            if (recentlyInstalled.isEmpty()) {
                item { EmptySection("No user apps on target — check ACCU connection", Icons.Default.InstallMobile) {} }
            } else {
                items(recentlyInstalled, key = { "installed_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.tertiary) },
                        modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) },
                    )
                }
            }

            // More User Apps
            if (recentlyUpdated.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    SectionHeader("More Apps") { onNavigateToRecentlyInstalled() }
                }
                items(recentlyUpdated, key = { "updated_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                item { EmptySection("No FOSS apps detected on target (F-Droid/Aurora/known prefixes)", Icons.Default.VolunteerActivism) { onNavigateToFoss() } }
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
                item { EmptySection("No disabled apps on target device", Icons.Default.CheckCircle) {} }
            } else {
                items(disabledApps, key = { "disabled_${it.pkg}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text("Disabled on target device", fontSize = 12.sp) },
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
