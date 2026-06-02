package com.accu.ui.appmanager

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class RecentAppEntry(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val installTime: Long,
    val updateTime: Long,
    val installerPackage: String,
    val isSystem: Boolean,
    val isUpdatedSystemApp: Boolean,
)

/** Derive a readable label from a package name. */
private fun labelFromPackage(pkg: String): String =
    pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg

/**
 * Parse a date string from `dumpsys package` into epoch milliseconds.
 * The format on Android is typically "YYYY-MM-DD HH:MM:SS" or a raw millisecond value.
 */
private fun parsePkgTime(raw: String): Long {
    val trimmed = raw.trim()
    // Try raw millis first
    trimmed.toLongOrNull()?.let { if (it > 1_000_000_000_000L) return it }
    // Try "YYYY-MM-DD HH:MM:SS"
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(trimmed)?.time ?: 0L
    } catch (_: Exception) { 0L }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureRecentlyInstalledScreen(
    onBack: () -> Unit = {},
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager

    var allApps by remember { mutableStateOf<List<RecentAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0=installed, 1=updated
    var filterDays by remember { mutableStateOf(30) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        loading = true
        withContext(Dispatchers.IO) {
            try {
                // Get all user (non-system) packages with installer info from target device
                val pkgsWithInstaller = connectionManager.exec("pm list packages -3 -i --show-versioncode 2>/dev/null").output
                val systemPkgs = connectionManager.exec("pm list packages -s 2>/dev/null").output
                    .lines().filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }.toSet()

                // Parse the package+installer+versioncode lines
                // Format varies: "package:com.example  versionCode:123  installer:com.android.vending"
                // or:           "package:com.example  installer:com.android.vending"
                val parsedPkgs = pkgsWithInstaller.lines()
                    .filter { it.startsWith("package:") }
                    .map { line ->
                        val pkg = line.substringAfter("package:").substringBefore(" ").substringBefore("\t").trim()
                        val versionCode = Regex("versionCode:(\\d+)").find(line)?.groupValues?.getOrNull(1) ?: ""
                        val installer = Regex("installer:([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
                            ?: Regex("installer=([^\\s]+)").find(line)?.groupValues?.getOrNull(1) ?: ""
                        Triple(pkg, versionCode, installer)
                    }
                    .filter { (pkg, _, _) -> pkg.isNotEmpty() }

                // Batch-fetch install/update times and version names via a shell loop on the target device
                // This runs as ONE ADB call — the loop executes on the target device shell
                val batchScript = buildString {
                    append("for pkg in ")
                    parsedPkgs.take(80).forEach { (pkg, _, _) -> append("\"$pkg\" ") }
                    append("; do ")
                    append("info=\$(dumpsys package \"\$pkg\" 2>/dev/null | grep -E 'firstInstallTime=|lastUpdateTime=|versionName=' | head -4 | tr '\\n' '|'); ")
                    append("echo \"PKG:\$pkg|\$info\"; ")
                    append("done 2>/dev/null")
                }
                val batchOut = connectionManager.exec(batchScript).output

                // Parse batch output: "PKG:com.example|firstInstallTime=2023-01-01 12:00:00|lastUpdateTime=...|versionName=1.0|"
                val timeMap = mutableMapOf<String, Triple<Long, Long, String>>() // pkg -> (installTime, updateTime, versionName)
                batchOut.lines().filter { it.startsWith("PKG:") }.forEach { line ->
                    val pkg = line.substringAfter("PKG:").substringBefore("|").trim()
                    val rest = line.substringAfter("|")
                    val fit = Regex("firstInstallTime=([^|]+)").find(rest)?.groupValues?.getOrNull(1)?.trim() ?: ""
                    val lut = Regex("lastUpdateTime=([^|]+)").find(rest)?.groupValues?.getOrNull(1)?.trim() ?: ""
                    val vn  = Regex("versionName=([^|\\s]+)").find(rest)?.groupValues?.getOrNull(1)?.trim() ?: ""
                    if (pkg.isNotEmpty()) {
                        timeMap[pkg] = Triple(parsePkgTime(fit), parsePkgTime(lut), vn)
                    }
                }

                // Build RecentAppEntry list
                allApps = parsedPkgs.map { (pkg, _, installer) ->
                    val (installTime, updateTime, versionName) = timeMap[pkg] ?: Triple(0L, 0L, "")
                    RecentAppEntry(
                        packageName = pkg,
                        appName = labelFromPackage(pkg),
                        versionName = versionName,
                        installTime = installTime,
                        updateTime = updateTime,
                        installerPackage = installer.trimEnd().let { if (it == "null") "" else it },
                        isSystem = pkg in systemPkgs,
                        isUpdatedSystemApp = false,
                    )
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load packages from target device"
            }
            loading = false
        }
    }

    val cutoff = System.currentTimeMillis() - filterDays.toLong() * 86_400_000L

    val filtered = remember(allApps, searchQuery, selectedTab, filterDays) {
        allApps
            .filter { app ->
                // If we have no timestamp data, show everything (don't exclude by time)
                val timeOk = when (selectedTab) {
                    0 -> app.installTime == 0L || app.installTime >= cutoff
                    1 -> app.updateTime == 0L || (app.updateTime >= cutoff && app.updateTime != app.installTime)
                    else -> true
                }
                val searchOk = searchQuery.isBlank() ||
                        app.appName.contains(searchQuery, true) ||
                        app.packageName.contains(searchQuery, true)
                timeOk && searchOk
            }
            .sortedByDescending { if (selectedTab == 1) it.updateTime else it.installTime }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recently Installed (Target)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("Installed") })
                Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("Updated") })
            }

            OutlinedTextField(
                searchQuery, { searchQuery = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(7 to "7 Days", 30 to "30 Days", 90 to "90 Days", 365 to "1 Year").forEach { (d, label) ->
                    FilterChip(filterDays == d, { filterDays = d }, { Text(label) })
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Querying target device…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            if (error.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Column
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("No apps found in this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("(Timestamps may not be available for all apps)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            Text(
                "${filtered.size} app${if (filtered.size != 1) "s" else ""} · target device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    RecentAppCard(app, selectedTab, dateFormatter, onNavigateToAppDetail)
                }
            }
        }
    }
}

@Composable
private fun RecentAppCard(app: RecentAppEntry, tab: Int, fmt: SimpleDateFormat, onClick: (String) -> Unit) {
    val timestamp = if (tab == 1) app.updateTime else app.installTime
    val dateStr = if (timestamp > 0) fmt.format(Date(timestamp)) else "Unknown"
    val installerLabel = when (app.installerPackage) {
        "com.android.vending"     -> "Play Store"
        "org.fdroid.fdroid"       -> "F-Droid"
        "org.fdroid.basic"        -> "F-Droid Basic"
        "com.aurora.store"        -> "Aurora Store"
        "com.amazon.venezia"      -> "Amazon"
        "", "null"                -> "Unknown"
        else                      -> app.installerPackage.substringAfterLast(".")
    }

    Card(
        onClick = { onClick(app.packageName) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (tab == 1) Icons.Default.Update else Icons.Default.InstallMobile,
                null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (app.versionName.isNotEmpty()) Text("v${app.versionName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("via $installerLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(dateStr, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                if (app.isSystem) {
                    Spacer(Modifier.height(2.dp))
                    SuggestionChip({}, { Text("System") }, Modifier.height(20.dp))
                }
            }
        }
    }
}
