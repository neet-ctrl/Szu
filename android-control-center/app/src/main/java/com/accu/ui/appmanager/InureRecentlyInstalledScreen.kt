package com.accu.ui.appmanager

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureRecentlyInstalledScreen(
    onBack: () -> Unit = {},
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val vm: ShizukuViewModel = hiltViewModel()
    val context = LocalContext.current

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
                val pm = context.packageManager
                val flags = PackageManager.GET_META_DATA
                val packages = pm.getInstalledPackages(flags)

                allApps = packages.map { pi ->
                    val isSystem = (pi.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdated = (pi.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    val installer = try {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(pi.packageName) ?: ""
                    } catch (_: Exception) { "" }
                    RecentAppEntry(
                        packageName = pi.packageName,
                        appName = pm.getApplicationLabel(pi.applicationInfo!!).toString(),
                        versionName = pi.versionName ?: "",
                        installTime = pi.firstInstallTime,
                        updateTime = pi.lastUpdateTime,
                        installerPackage = installer,
                        isSystem = isSystem,
                        isUpdatedSystemApp = isUpdated,
                    )
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load packages"
            }
            loading = false
        }
    }

    val cutoff = System.currentTimeMillis() - filterDays.toLong() * 86_400_000L

    val filtered = remember(allApps, searchQuery, selectedTab, filterDays) {
        allApps
            .filter { app ->
                val timeOk = when (selectedTab) {
                    0 -> app.installTime >= cutoff
                    1 -> app.updateTime >= cutoff && app.updateTime != app.installTime
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
                title = { Text("Recently Installed") },
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

            // Search
            OutlinedTextField(
                searchQuery, { searchQuery = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            // Period filter chips
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(7 to "7 Days", 30 to "30 Days", 90 to "90 Days", 365 to "1 Year").forEach { (d, label) ->
                    FilterChip(filterDays == d, { filterDays = d }, { Text(label) })
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("No apps found in this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            // Summary
            Text(
                "${filtered.size} app${if (filtered.size != 1) "s" else ""} in last $filterDays days",
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
    val dateStr = fmt.format(Date(if (tab == 1) app.updateTime else app.installTime))
    val installerLabel = when (app.installerPackage) {
        "com.android.vending"     -> "Play Store"
        "org.fdroid.fdroid"       -> "F-Droid"
        "com.aurora.store"        -> "Aurora Store"
        "com.amazon.venezia"      -> "Amazon"
        ""                        -> "Unknown"
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
