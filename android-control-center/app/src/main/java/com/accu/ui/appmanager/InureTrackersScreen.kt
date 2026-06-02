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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrackerEntry(
    val appName: String,
    val pkg: String,
    val trackerCount: Int,
    val trackers: List<TrackerDetail>,
)
data class TrackerDetail(
    val name: String,
    val category: String,
    val signature: String,
    val isBlocked: Boolean = false,
)

private data class KnownTracker(
    val name: String,
    val category: String,
    val classPathFragment: String,
)

private val KNOWN_TRACKERS = listOf(
    KnownTracker("Google Analytics",         "Analytics",        "com/google/android/gms/analytics"),
    KnownTracker("Firebase Analytics",        "Analytics",        "com/google/firebase/analytics"),
    KnownTracker("Firebase Crashlytics",      "Crash Reporting",  "com/google/firebase/crashlytics"),
    KnownTracker("Google AdMob",              "Advertising",      "com/google/android/gms/ads"),
    KnownTracker("Google Ad ID",              "Advertising",      "com/google/android/gms/ads/identifier"),
    KnownTracker("DoubleClick",               "Advertising",      "com/google/android/gms/ads/doubleclick"),
    KnownTracker("Firebase Remote Config",    "Configuration",    "com/google/firebase/remoteconfig"),
    KnownTracker("Facebook Analytics",        "Analytics",        "com/facebook/analytics"),
    KnownTracker("Facebook Ads",              "Advertising",      "com/facebook/ads"),
    KnownTracker("Meta Audience Network",     "Advertising",      "com/facebook/audiencenetwork"),
    KnownTracker("Amplitude",                 "Analytics",        "com/amplitude/api"),
    KnownTracker("Amplitude SDK",             "Analytics",        "com/amplitude/android"),
    KnownTracker("AppsFlyer",                 "Attribution",      "com/appsflyer"),
    KnownTracker("Mixpanel",                  "Analytics",        "com/mixpanel/android"),
    KnownTracker("Branch",                    "Deep Linking",     "io/branch/referral"),
    KnownTracker("Sentry",                    "Crash Reporting",  "io/sentry"),
    KnownTracker("Flurry",                    "Analytics",        "com/flurry/android"),
    KnownTracker("Adjust",                    "Attribution",      "com/adjust/sdk"),
    KnownTracker("Segment",                   "Analytics",        "com/segment/analytics"),
    KnownTracker("MoEngage",                  "Marketing",        "com/moengage"),
    KnownTracker("Intercom",                  "CRM",              "io/intercom/android"),
    KnownTracker("Heap Analytics",            "Analytics",        "com/heapanalytics"),
    KnownTracker("Clevertap",                 "Marketing",        "com/clevertap/android"),
    KnownTracker("Singular",                  "Attribution",      "com/singular/sdk"),
    KnownTracker("Kochava",                   "Attribution",      "com/kochava/base"),
    KnownTracker("Chartboost",               "Advertising",      "com/chartboost"),
    KnownTracker("IronSource",               "Advertising",      "com/ironsource"),
    KnownTracker("AppLovin",                  "Advertising",      "com/applovin"),
    KnownTracker("Vungle",                    "Advertising",      "com/vungle"),
    KnownTracker("InMobi",                    "Advertising",      "com/inmobi"),
    KnownTracker("Unity Ads",                 "Advertising",      "com/unity3d/ads"),
    KnownTracker("MoPub",                     "Advertising",      "com/mopub"),
    KnownTracker("Bugsnag",                   "Crash Reporting",  "com/bugsnag/android"),
    KnownTracker("Datadog",                   "Monitoring",       "com/datadog/android"),
    KnownTracker("New Relic",                 "Monitoring",       "com/newrelic/agent"),
    KnownTracker("Pendo",                     "Analytics",        "sdk/pendo"),
    KnownTracker("Leanplum",                  "Marketing",        "com/leanplum"),
    KnownTracker("Braze / Appboy",            "Marketing",        "com/appboy"),
    KnownTracker("Localytics",                "Analytics",        "com/localytics"),
    KnownTracker("Swrve",                     "Marketing",        "com/swrve/sdk"),
)

private suspend fun scanApkForTrackers(
    apkPath: String,
    connectionManager: com.accu.connection.AccuConnectionManager,
): List<TrackerDetail> = withContext(Dispatchers.IO) {
    val trackerDetails = mutableListOf<TrackerDetail>()
    try {
        // Always scan via ADB on the target device — never open local ZipFile
        val raw = try {
            connectionManager.exec("unzip -l '$apkPath' 2>/dev/null | awk '{print \$4}'").output
        } catch (_: Exception) { "" }
        val entries = raw.lines().filter { it.isNotBlank() }
        KNOWN_TRACKERS.forEach { tracker ->
            if (entries.any { e -> e.contains(tracker.classPathFragment, ignoreCase = true) }) {
                trackerDetails += TrackerDetail(tracker.name, tracker.category, tracker.classPathFragment.replace('/', '.'))
            }
        }
    } catch (_: Exception) {}
    trackerDetails
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureTrackersScreen(onBack: () -> Unit = {}) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<TrackerEntry>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanStatus by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    var filterCategory by remember { mutableStateOf("All") }
    var showSystemApps by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    fun startScan() {
        isScanning = true
        hasScanned = false
        apps = emptyList()
        scope.launch(Dispatchers.IO) {
            // Get packages from TARGET device via ADB — never local PackageManager
            val filter = if (showSystemApps) "pm list packages" else "pm list packages -3"
            val pkgsRaw = connectionManager.exec("$filter 2>/dev/null").output
            val packages = pkgsRaw.lines().filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }

            val result = mutableListOf<TrackerEntry>()
            packages.forEachIndexed { idx, pkg ->
                val appName = pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                withContext(Dispatchers.Main) {
                    scanProgress = idx.toFloat() / packages.size
                    scanStatus = "Scanning $appName…"
                }
                // Get APK path on TARGET device, then scan it via ADB
                val apkPath = try {
                    connectionManager.exec("pm path \"$pkg\" 2>/dev/null").output
                        .lines().firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim() ?: ""
                } catch (_: Exception) { "" }
                val trackers = if (apkPath.isNotEmpty()) scanApkForTrackers(apkPath, connectionManager) else emptyList()
                result += TrackerEntry(appName, pkg, trackers.size, trackers)
            }
            withContext(Dispatchers.Main) {
                apps = result.sortedByDescending { it.trackerCount }
                isScanning = false
                hasScanned = true
                scanProgress = 1f
                scanStatus = "Scan complete — ${result.sumOf { it.trackerCount }} trackers in ${result.count { it.trackerCount > 0 }} apps"
            }
        }
    }

    val categories = listOf("All") + apps.flatMap { it.trackers.map { t -> t.category } }.distinct().sorted()

    val filtered = apps.filter { app ->
        (filterCategory == "All" || app.trackers.any { it.category == filterCategory }) &&
        (search.isBlank() || app.appName.contains(search, ignoreCase = true) || app.pkg.contains(search, ignoreCase = true))
    }

    val totalTrackers = apps.sumOf { it.trackerCount }
    val appsWithTrackers = apps.count { it.trackerCount > 0 }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Tracker Detector",
                onBack = onBack,
                actions = {
                    if (!isScanning) {
                        IconButton(onClick = { showSystemApps = !showSystemApps }) {
                            Icon(
                                if (showSystemApps) Icons.Default.Android else Icons.Default.PhoneAndroid,
                                null,
                                tint = if (showSystemApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(onClick = { if (!isScanning) startScan() }) {
                        Icon(if (isScanning) Icons.Default.Sync else Icons.Default.DocumentScanner, "Scan")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isScanning) {
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Scanning APKs for tracker signatures…", style = MaterialTheme.typography.bodyMedium)
                        }
                        LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth())
                        Text(scanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (!hasScanned && !isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(Icons.Default.TrackChanges, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Tracker Detector", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Scans all installed app APKs for ${KNOWN_TRACKERS.size} known tracker signatures (analytics, advertising, attribution, crash reporting).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Android, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Toggle system apps with the phone icon", style = MaterialTheme.typography.bodySmall) }
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Timer, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Scanning may take 1–3 minutes for many apps", style = MaterialTheme.typography.bodySmall) }
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Security, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Database: ${KNOWN_TRACKERS.size} known tracker signatures", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.DocumentScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan ${if (showSystemApps) "All" else "User"} Apps Now")
                        }
                    }
                }
                return@Scaffold
            }

            if (hasScanned) {
                ElevatedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (totalTrackers > 0) MaterialTheme.colorScheme.errorContainer.copy(0.6f)
                        else MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (totalTrackers > 0) Icons.Default.TrackChanges else Icons.Default.Security,
                            null,
                            tint = if (totalTrackers > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "$totalTrackers trackers detected across $appsWithTrackers apps",
                                fontWeight = FontWeight.SemiBold,
                                color = if (totalTrackers > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "${apps.count { it.trackerCount == 0 }} clean apps · ${apps.size} scanned",
                                fontSize = 11.sp,
                                color = if (totalTrackers > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { startScan() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, "Re-scan", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                OutlinedTextField(
                    search, { search = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search apps…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Clear, null) } },
                    singleLine = true,
                )

                if (categories.size > 1) {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(categories) { cat ->
                            FilterChip(selected = filterCategory == cat, onClick = { filterCategory = cat }, label = { Text(cat, fontSize = 12.sp) })
                        }
                    }
                }

                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.pkg }) { app ->
                        val isExpanded = expandedPkg == app.pkg
                        ElevatedCard(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                                .clickable { expandedPkg = if (isExpanded) null else app.pkg },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (app.trackerCount == 0) Icons.Default.Security else Icons.Default.WarningAmber,
                                        null,
                                        tint = when {
                                            app.trackerCount == 0   -> MaterialTheme.colorScheme.primary
                                            app.trackerCount <= 3   -> MaterialTheme.colorScheme.secondary
                                            else                    -> MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.appName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(app.pkg, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Badge(
                                        containerColor = when {
                                            app.trackerCount == 0 -> MaterialTheme.colorScheme.primaryContainer
                                            app.trackerCount <= 3 -> MaterialTheme.colorScheme.secondaryContainer
                                            else                  -> MaterialTheme.colorScheme.errorContainer
                                        },
                                        contentColor = when {
                                            app.trackerCount == 0 -> MaterialTheme.colorScheme.primary
                                            app.trackerCount <= 3 -> MaterialTheme.colorScheme.secondary
                                            else                  -> MaterialTheme.colorScheme.error
                                        },
                                    ) { Text("${app.trackerCount}", fontSize = 11.sp) }
                                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }
                                if (isExpanded) {
                                    Spacer(Modifier.height(8.dp))
                                    if (app.trackers.isEmpty()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("No known trackers detected", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        HorizontalDivider()
                                        Spacer(Modifier.height(8.dp))
                                        app.trackers.forEach { tracker ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Circle, null, modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.error)
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(tracker.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                    Text(tracker.signature, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(tracker.category, fontSize = 10.sp) },
                                                    modifier = Modifier.height(22.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
