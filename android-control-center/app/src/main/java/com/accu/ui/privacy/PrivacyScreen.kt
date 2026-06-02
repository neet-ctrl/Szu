package com.accu.ui.privacy

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.theme.*
import kotlin.math.abs

// ── Colored app avatar (used everywhere instead of remote icon) ────────────────

@Composable
private fun AppAvatar(packageName: String, size: Dp = 36.dp) {
    val colors = listOf(AccentRed, AccentOrange, AccentGreen, AccentCyan, AccentPurple, Color(0xFF64B5F6), Color(0xFFEC407A))
    val bg = colors[abs(packageName.hashCode()) % colors.size]
    val letter = packageName.toDisplayName().firstOrNull()?.uppercase() ?: "?"
    Surface(shape = RoundedCornerShape(8.dp), color = bg.copy(0.22f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(letter, fontWeight = FontWeight.ExtraBold, color = bg, fontSize = (size.value * 0.42f).sp)
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportRulesTo(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                runCatching {
                    val json = uri.let { u -> android.content.ContentResolver::class.java.getMethod("openInputStream", android.net.Uri::class.java) }.let { _ -> "" }
                    viewModel.importRulesFrom(json)
                }
            }
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    // Stop auto-refresh when leaving Network tab
    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab != PrivacyTab.NETWORK && state.netAutoRefresh) {
            viewModel.stopNetAutoRefresh()
        }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Privacy Center — Target Device",
                onBack = onBack,
                actions = {
                    if (state.targetAppsLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.loadTargetAppsAndScan() }) {
                            Icon(Icons.Default.Refresh, "Reload target apps")
                        }
                    }
                    IconButton(onClick = { exportLauncher.launch("accu_privacy_${System.currentTimeMillis()}.json") }) {
                        Icon(Icons.Default.IosShare, "Export rules")
                    }
                    InfoTooltipIcon(
                        title = "Privacy Center",
                        description = "ALL data comes from the CONNECTED target device via ADB commands — zero host-device data.\n\n• Apps: pm list packages -U\n• Trackers: package-name matching vs 150+ SDK prefixes\n• Sensors: appops get/set\n• Firewall: cmd netpolicy list uid-rules\n• Boot: pm query-receivers BOOT_COMPLETED\n• Security: settings get secure + dumpsys device_policy\n• Network: ss -tuap / /proc/net/tcp\n• Audit: dumpsys package | grep granted=true\n\nADB or root required for write operations.",
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Stats banner ──────────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(AccentRed.copy(0.13f), MaterialTheme.colorScheme.surface, AccentCyan.copy(0.11f))))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatChip("${state.targetApps.size}", "Apps\non Device", AccentCyan)
                    StatChip("${state.trackerCount}", "Trackers\nFound", AccentRed)
                    StatChip("${state.firewallApps.count { it.isBlocked }}", "Firewall\nBlocked", AccentOrange)
                    StatChip("${state.blockedCount}", "Components\nBlocked", AccentPurple)
                    Spacer(Modifier.weight(1f))
                    val danger = state.trackerCount + state.securityEntries.count { it.kind == SecurityKind.DEVICE_ADMIN }
                    Surface(shape = RoundedCornerShape(16.dp), color = if (danger > 0) MaterialTheme.colorScheme.errorContainer.copy(0.6f) else AccentGreen.copy(0.15f)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(if (danger > 0) Icons.Default.GppBad else Icons.Default.VerifiedUser, null, Modifier.size(13.dp), tint = if (danger > 0) MaterialTheme.colorScheme.error else AccentGreen)
                            Text(if (danger > 0) "$danger risk" else "Clean", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (danger > 0) MaterialTheme.colorScheme.error else AccentGreen)
                        }
                    }
                }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            val tabIndex = PrivacyTab.entries.indexOf(state.selectedTab)
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 0.dp,
                indicator = { positions -> if (tabIndex < positions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tabIndex]), color = MaterialTheme.colorScheme.primary) },
                divider = {},
            ) {
                PrivacyTab.entries.forEach { tab ->
                    val sel = state.selectedTab == tab
                    Tab(selected = sel, onClick = { viewModel.onTabChange(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    PrivacyTab.DASHBOARD  -> "Dashboard"
                                    PrivacyTab.TRACKERS   -> "Trackers"
                                    PrivacyTab.SENSORS    -> "Sensors"
                                    PrivacyTab.FIREWALL   -> "Firewall"
                                    PrivacyTab.BOOT       -> "Boot"
                                    PrivacyTab.SECURITY   -> "Security"
                                    PrivacyTab.NETWORK    -> "Network"
                                    PrivacyTab.COMPONENTS -> "Components"
                                    PrivacyTab.RULES      -> "Rules"
                                    PrivacyTab.AUDIT      -> "Audit"
                                },
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                    )
                }
            }

            when (state.selectedTab) {
                PrivacyTab.DASHBOARD  -> DashboardTab(state, viewModel)
                PrivacyTab.TRACKERS   -> TrackersTab(state, viewModel)
                PrivacyTab.SENSORS    -> SensorsTab(state, viewModel)
                PrivacyTab.FIREWALL   -> FirewallTab(state, viewModel)
                PrivacyTab.BOOT       -> BootTab(state, viewModel)
                PrivacyTab.SECURITY   -> SecurityServicesTab(state, viewModel)
                PrivacyTab.NETWORK    -> NetworkTab(state, viewModel)
                PrivacyTab.COMPONENTS -> ComponentsTab(state, viewModel)
                PrivacyTab.RULES      -> RulesTab(state, viewModel)
                PrivacyTab.AUDIT      -> AuditTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 11.sp, fontSize = 9.sp)
    }
}

// ─── DASHBOARD ──────────────────────────────────────────────────────────────────

@Composable
private fun DashboardTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Connection + scan status
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
                if (state.trackerCount > 0) AccentRed.copy(0.07f) else AccentGreen.copy(0.07f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (state.targetAppsLoading) Icons.Default.Sync
                         else if (state.trackerCount > 0) Icons.Default.TrackChanges
                         else Icons.Default.VerifiedUser,
                        null, Modifier.size(30.dp),
                        tint = if (state.targetAppsLoading) MaterialTheme.colorScheme.primary
                               else if (state.trackerCount > 0) AccentRed else AccentGreen)
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                state.targetAppsLoading -> "Scanning target device…"
                                state.trackerCount > 0 -> "${state.trackerCount} tracker SDK(s) found on target device"
                                state.trackerScanDone -> "No known trackers found on target device"
                                else -> "Tap Reload to scan target device"
                            },
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = if (state.trackerCount > 0) AccentRed else AccentGreen,
                        )
                        Text("${state.targetApps.size} apps · ${TRACKER_DB.values.flatten().size} tracker signatures across ${TRACKER_DB.size} categories",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.targetAppsLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else FilledTonalButton(onClick = { viewModel.loadTargetAppsAndScan() }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Rescan", fontSize = 12.sp)
                    }
                }
            }
        }

        // Quick-block row (only shows categories with found trackers)
        if (state.trackerCategories.any { it.trackerCount > 0 }) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FlashOn, null, Modifier.size(16.dp), tint = AccentOrange)
                            Spacer(Modifier.width(6.dp))
                            Text("Quick Block — Found on Target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        state.trackerCategories.filter { it.trackerCount > 0 }.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { cat ->
                                    FilledTonalButton(
                                        onClick = { viewModel.blockTrackersInCategory(cat.name) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentRed.copy(0.12f)),
                                    ) {
                                        Icon(Icons.Default.Block, null, Modifier.size(13.dp), tint = AccentRed)
                                        Spacer(Modifier.width(4.dp))
                                        Text("${cat.name} (${cat.trackerCount})", style = MaterialTheme.typography.labelSmall, color = AccentRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // Security alerts
        val admins = state.securityEntries.filter { it.kind == SecurityKind.DEVICE_ADMIN }
        val accessApps = state.securityEntries.filter { it.kind == SecurityKind.ACCESSIBILITY && it.isEnabled }
        if (admins.isNotEmpty() || accessApps.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccentRed.copy(0.07f)), border = BorderStroke(1.dp, AccentRed.copy(0.3f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = AccentRed)
                            Spacer(Modifier.width(6.dp))
                            Text("Security Alerts", fontWeight = FontWeight.Bold, color = AccentRed, style = MaterialTheme.typography.titleSmall)
                        }
                        if (admins.isNotEmpty()) Text("⚠ ${admins.size} Device Admin app(s): ${admins.joinToString(", ") { it.displayName }}", style = MaterialTheme.typography.bodySmall, color = AccentRed)
                        if (accessApps.isNotEmpty()) Text("⚠ ${accessApps.size} Accessibility Service(s) enabled", style = MaterialTheme.typography.bodySmall, color = AccentOrange)
                    }
                }
            }
        }

        // Nav cards
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashNav(Icons.Default.Sensors, "Sensors", "${state.sensorApps.count { it.blockedOps.isNotEmpty() }} blocked", AccentCyan, Modifier.weight(1f)) { viewModel.onTabChange(PrivacyTab.SENSORS) }
                DashNav(Icons.Default.Fireplace, "Firewall", "${state.firewallApps.count { it.isBlocked }} restricted", AccentOrange, Modifier.weight(1f)) { viewModel.onTabChange(PrivacyTab.FIREWALL) }
                DashNav(Icons.Default.RocketLaunch, "Boot", "${state.bootReceivers.size} receivers", AccentPurple, Modifier.weight(1f)) { viewModel.onTabChange(PrivacyTab.BOOT) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashNav(Icons.Default.AdminPanelSettings, "Security", "${state.securityEntries.size} entries", AccentRed, Modifier.weight(1f)) { viewModel.onTabChange(PrivacyTab.SECURITY) }
                DashNav(Icons.Default.NetworkCheck, "Network", "${state.netConnections.size} active", Color(0xFF64B5F6), Modifier.weight(1f)) { viewModel.onTabChange(PrivacyTab.NETWORK) }
                DashNav(Icons.Default.Security, "Audit", "${state.auditApps.count { it.privacyScore < 50 }} high risk", AccentGreen, Modifier.weight(1f)) { viewModel.onTabChange(PrivacyTab.AUDIT) }
            }
        }
    }
}

@Composable
private fun DashNav(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(22.dp), tint = color)
            Spacer(Modifier.height(3.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 9.sp)
        }
    }
}

// ─── TRACKERS ───────────────────────────────────────────────────────────────────

@Composable
private fun TrackersTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    Column(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxWidth(), color = if (state.trackerCount > 0) AccentRed.copy(0.07f) else MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.trackerScanDone || state.targetAppsLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Scanning ${TRACKER_DB.values.flatten().size} tracker signatures on target…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                } else {
                    Icon(if (state.trackerCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = if (state.trackerCount > 0) AccentRed else AccentGreen)
                    Text(
                        if (state.trackerCount > 0) "${state.trackerCount} SDK(s) found across ${state.trackerCategories.count { it.trackerCount > 0 }} categories on target device"
                        else "No known tracker SDKs found on target device",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                        color = if (state.trackerCount > 0) AccentRed else AccentGreen,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.scanInstalledTrackers() }) { Text("Rescan") }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.trackerCategories.sortedByDescending { it.trackerCount }) { cat ->
                val found = cat.trackerCount > 0
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (found) AccentRed.copy(0.06f) else MaterialTheme.colorScheme.surfaceContainer),
                    border = if (found) BorderStroke(1.dp, AccentRed.copy(0.25f)) else null) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(cat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (found) Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(0.15f)) {
                                        Text("${cat.trackerCount} found", style = MaterialTheme.typography.labelSmall, color = AccentRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                }
                                Text("${cat.knownSdkCount} known SDK signatures", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (found) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(cat.foundPackages.joinToString("\n") { "• $it" }, style = MaterialTheme.typography.labelSmall, color = AccentRed.copy(0.85f), fontFamily = FontFamily.Monospace)
                                } else {
                                    Text(cat.packages.take(3).joinToString(", ") { it.substringAfterLast('.') } + if (cat.packages.size > 3) "…" else "",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (found) Button(onClick = { viewModel.blockTrackersInCategory(cat.name) }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                                Text("Block ${cat.trackerCount}", color = Color.White)
                            } else OutlinedButton(onClick = { viewModel.blockTrackersInCategory(cat.name) }) { Text("Pre-block") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Info, null, Modifier.size(16.dp).align(Alignment.Top), tint = MaterialTheme.colorScheme.primary)
                        Text("Detection via package name matching against ${TRACKER_DB.values.flatten().size} known SDK prefixes. All data from target device via ADB.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─── SENSORS ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensorsTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val filtered = remember(state.sensorApps, state.sensorSearchQuery) {
        state.sensorApps.filter { state.sensorSearchQuery.isBlank() || it.displayName.contains(state.sensorSearchQuery, true) || it.packageName.contains(state.sensorSearchQuery, true) }
    }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(0.35f)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text("AppOps from target device via 'appops get/set'. Tap app to load per-op state.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val blocked = state.sensorApps.sumOf { it.blockedOps.size }
                    if (blocked > 0) Surface(shape = RoundedCornerShape(6.dp), color = AccentRed.copy(0.12f)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Block, null, Modifier.size(11.dp), tint = AccentRed)
                            Text("$blocked ops blocked on target", style = MaterialTheme.typography.labelSmall, color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { viewModel.scanAllSensorOps() }, enabled = !state.sensorScanInProgress && state.sensorApps.isNotEmpty()) {
                        if (state.sensorScanInProgress) { CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp); Spacer(Modifier.width(4.dp)); Text("Scanning…", fontSize = 11.sp) }
                        else { Icon(Icons.Default.Search, null, Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text("Scan All", fontSize = 11.sp) }
                    }
                }
            }
        }
        if (state.sensorAppsLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Loading apps from target device…") } }
        } else {
            OutlinedTextField(value = state.sensorSearchQuery, onValueChange = viewModel::onSensorSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                placeholder = { Text("Search…") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = { if (state.sensorSearchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onSensorSearch("") }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) } },
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val expanded = expandedPkg == app.packageName
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = if (app.blockedOps.isNotEmpty()) AccentRed.copy(0.04f) else MaterialTheme.colorScheme.surfaceContainer),
                        onClick = { expandedPkg = if (expanded) null else app.packageName; if (!expanded && !app.checkedOps) viewModel.loadSensorStateForApp(app.packageName) }) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AppAvatar(app.packageName, 34.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(app.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                }
                                when {
                                    app.blockedOps.isNotEmpty() -> Surface(shape = RoundedCornerShape(5.dp), color = AccentRed.copy(0.12f)) { Text("${app.blockedOps.size} blocked", style = MaterialTheme.typography.labelSmall, color = AccentRed, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontWeight = FontWeight.Bold) }
                                    app.checkedOps -> Surface(shape = RoundedCornerShape(5.dp), color = AccentGreen.copy(0.10f)) { Text("clean", style = MaterialTheme.typography.labelSmall, color = AccentGreen, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) }
                                }
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                            }
                            AnimatedVisibility(expanded) {
                                Column(Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))
                                    if (!app.checkedOps) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text("Loading from target via appops get…", style = MaterialTheme.typography.bodySmall) }
                                    } else {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(onClick = { viewModel.blockAllSensorsForApp(app.packageName) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentRed.copy(0.10f))) { Text("Block All", fontSize = 11.sp, color = AccentRed) }
                                            OutlinedButton(onClick = { viewModel.unblockAllSensorsForApp(app.packageName) }, modifier = Modifier.weight(1f)) { Text("Allow All", fontSize = 11.sp) }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        SENSOR_OPS.forEach { (op, label) ->
                                            val blocked = op in app.blockedOps
                                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                val c = when { op.contains("CAMERA") || op.contains("AUDIO") -> AccentRed; op.contains("LOCATION") -> AccentOrange; op.contains("SMS") || op.contains("CALL") || op.contains("CONTACT") -> AccentPurple; else -> MaterialTheme.colorScheme.primary }
                                                Icon(if (blocked) Icons.Default.Block else Icons.Default.Check, null, Modifier.size(14.dp), tint = if (blocked) AccentRed else AccentGreen)
                                                Spacer(Modifier.width(6.dp))
                                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = if (blocked) AccentRed else MaterialTheme.colorScheme.onSurface)
                                                Switch(checked = !blocked, onCheckedChange = { allow -> viewModel.blockSensorOp(app.packageName, op, !allow) }, modifier = Modifier.height(18.dp),
                                                    colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen, uncheckedThumbColor = AccentRed))
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

// ─── FIREWALL ───────────────────────────────────────────────────────────────────

@Composable
private fun FirewallTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val filtered = remember(state.firewallApps, state.firewallSearchQuery) {
        state.firewallApps.filter { state.firewallSearchQuery.isBlank() || it.displayName.contains(state.firewallSearchQuery, true) || it.packageName.contains(state.firewallSearchQuery, true) }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = AccentOrange.copy(0.06f)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Fireplace, null, Modifier.size(14.dp), tint = AccentOrange)
                    Text("Background data via 'cmd netpolicy list uid-rules' from target. Toggle = cmd netpolicy set restrict-background.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.loadFirewallApps() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(15.dp)) }
                }
                val blocked = state.firewallApps.count { it.isBlocked }
                if (blocked > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Block, null, Modifier.size(11.dp), tint = AccentRed)
                    Text("$blocked app${if (blocked != 1) "s" else ""} with background network restricted on target", style = MaterialTheme.typography.labelSmall, color = AccentRed, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (state.firewallLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Reading netpolicy from target device…") } }
        else {
            OutlinedTextField(value = state.firewallSearchQuery, onValueChange = viewModel::onFirewallSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                placeholder = { Text("Search…") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = { if (state.firewallSearchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onFirewallSearch("") }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) } })
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = { Text(app.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                                if (app.uid > 0) Text("UID ${app.uid}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = { AppAvatar(app.packageName, 38.dp) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (app.isBlocked) Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(0.12f)) { Text("Restricted", style = MaterialTheme.typography.labelSmall, color = AccentRed, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontWeight = FontWeight.Bold) }
                                Switch(checked = !app.isBlocked, onCheckedChange = { allow -> viewModel.blockNetworkForApp(app.packageName, !allow) })
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = if (app.isBlocked) AccentRed.copy(0.04f) else Color.Transparent),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ─── BOOT AUTO-START ────────────────────────────────────────────────────────────

@Composable
private fun BootTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val filtered = remember(state.bootReceivers, state.bootSearchQuery) {
        state.bootReceivers.filter { state.bootSearchQuery.isBlank() || it.displayName.contains(state.bootSearchQuery, true) || it.packageName.contains(state.bootSearchQuery, true) }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = AccentPurple.copy(0.07f)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.RocketLaunch, null, Modifier.size(14.dp), tint = AccentPurple)
                Text("Real BOOT_COMPLETED receivers from target via 'pm query-receivers -a android.intent.action.BOOT_COMPLETED'. Disable via pm disable.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.loadBootReceivers() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(15.dp)) }
            }
        }
        if (state.bootLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Querying boot receivers from target…") } }
        else if (state.bootReceivers.isEmpty()) EmptyState(Icons.Default.RocketLaunch, "No boot receivers found", "Target device returned no BOOT_COMPLETED receivers\nor tap Reload to query again")
        else {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RocketLaunch, null, Modifier.size(14.dp), tint = AccentPurple)
                    Spacer(Modifier.width(6.dp))
                    Text("${state.bootReceivers.size} auto-start receivers · ${state.bootReceivers.count { it.isDisabled }} disabled", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            }
            OutlinedTextField(value = state.bootSearchQuery, onValueChange = viewModel::onBootSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                placeholder = { Text("Search…") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = { if (state.bootSearchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onBootSearch("") }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) } })
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filtered, key = { "${it.packageName}/${it.componentClass}" }) { recv ->
                    ListItem(
                        headlineContent = { Text(recv.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(recv.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(recv.componentClass.substringAfterLast('.'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                            }
                        },
                        leadingContent = {
                            Surface(shape = CircleShape, color = if (recv.isDisabled) MaterialTheme.colorScheme.surfaceVariant else AccentPurple.copy(0.15f), modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(if (recv.isDisabled) Icons.Default.Block else Icons.Default.RocketLaunch, null, Modifier.size(18.dp), tint = if (recv.isDisabled) MaterialTheme.colorScheme.onSurfaceVariant else AccentPurple) }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (recv.isDisabled) Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Text("Disabled", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) }
                                Switch(checked = !recv.isDisabled, onCheckedChange = { enabled -> viewModel.toggleBootReceiver(recv, !enabled) })
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = if (recv.isDisabled) MaterialTheme.colorScheme.surfaceVariant.copy(0.5f) else Color.Transparent),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ─── SECURITY SERVICES ──────────────────────────────────────────────────────────

@Composable
private fun SecurityServicesTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val grouped = remember(state.securityEntries) {
        state.securityEntries.groupBy { it.kind }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = AccentRed.copy(0.07f)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(14.dp), tint = AccentRed)
                Text("Real data: enabled_accessibility_services setting · dumpsys device_policy · enabled_notification_listeners — all from target device.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.loadSecurityServices() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(15.dp)) }
            }
        }
        if (state.securityLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Reading security services from target…") } }
        else if (state.securityEntries.isEmpty()) EmptyState(Icons.Default.VerifiedUser, "No privileged services found", "No accessibility services, device admins,\nor notification listeners active on target")
        else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecurityKind.entries.forEach { kind ->
                    val entries = grouped[kind] ?: return@forEach
                    item(key = kind) {
                        val (title, icon, color) = when (kind) {
                            SecurityKind.ACCESSIBILITY -> Triple("Accessibility Services (${entries.size})", Icons.Default.Accessibility, AccentOrange)
                            SecurityKind.DEVICE_ADMIN -> Triple("Device Admin Apps (${entries.size})", Icons.Default.AdminPanelSettings, AccentRed)
                            SecurityKind.NOTIFICATION_LISTENER -> Triple("Notification Listeners (${entries.size})", Icons.Default.Notifications, AccentPurple)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(icon, null, Modifier.size(16.dp), tint = color)
                                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                                if (kind == SecurityKind.DEVICE_ADMIN) Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(0.15f)) { Text("HIGH RISK", style = MaterialTheme.typography.labelSmall, color = AccentRed, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                            }
                            entries.forEach { entry ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (kind == SecurityKind.DEVICE_ADMIN && entry.isEnabled) AccentRed.copy(0.06f) else MaterialTheme.colorScheme.surfaceContainer),
                                    border = if (kind == SecurityKind.DEVICE_ADMIN && entry.isEnabled) BorderStroke(1.dp, AccentRed.copy(0.3f)) else null) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        AppAvatar(entry.packageName, 32.dp)
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(entry.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (entry.componentName != entry.packageName) Text(entry.componentName.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                        }
                                        if (entry.isEnabled) FilledTonalButton(onClick = { viewModel.revokeSecurityService(entry) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentRed.copy(0.12f))) { Text("Revoke", color = AccentRed, fontSize = 11.sp) }
                                        else Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Text("Inactive", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)) }
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

// ─── LIVE NETWORK ───────────────────────────────────────────────────────────────

@Composable
private fun NetworkTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val established = state.netConnections.filter { it.state == "ESTABLISHED" || it.state == "ESTAB" }
    val listening   = state.netConnections.filter { it.state == "LISTEN" || it.state == "UNCONN" }
    val other       = state.netConnections.filter { it !in established && it !in listening }

    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF64B5F6).copy(0.07f)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NetworkCheck, null, Modifier.size(14.dp), tint = Color(0xFF64B5F6))
                    Text("Real connections from target via 'ss -tuap'. Fallback: /proc/net/tcp.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.netConnections.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(5.dp), color = AccentGreen.copy(0.12f)) { Text("${established.size} established", style = MaterialTheme.typography.labelSmall, color = AccentGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) }
                        Surface(shape = RoundedCornerShape(5.dp), color = AccentOrange.copy(0.12f)) { Text("${listening.size} listening", style = MaterialTheme.typography.labelSmall, color = AccentOrange, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { viewModel.loadNetworkConnections() }, enabled = !state.netLoading) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text("Refresh", fontSize = 11.sp)
                    }
                    val autoColor = if (state.netAutoRefresh) AccentGreen else MaterialTheme.colorScheme.onSurface
                    FilledTonalButton(
                        onClick = { if (state.netAutoRefresh) viewModel.stopNetAutoRefresh() else viewModel.startNetAutoRefresh() },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (state.netAutoRefresh) AccentGreen.copy(0.15f) else MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Icon(if (state.netAutoRefresh) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(13.dp), tint = autoColor)
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.netAutoRefresh) "Live" else "Auto", fontSize = 11.sp, color = autoColor)
                    }
                }
            }
        }
        if (state.netLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Reading live connections from target…") } }
        else if (state.netConnections.isEmpty()) EmptyState(Icons.Default.WifiOff, "No network connections found", "Target returned empty. Try Refresh or Auto-live mode.")
        else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                if (established.isNotEmpty()) {
                    item { NetSectionHeader("Established (${established.size})", AccentGreen) }
                    items(established, key = { "${it.localAddress}-${it.remoteAddress}-${it.uid}" }) { conn -> NetConnRow(conn) }
                }
                if (other.isNotEmpty()) {
                    item { NetSectionHeader("Other (${other.size})", AccentOrange) }
                    items(other, key = { "${it.localAddress}-${it.remoteAddress}-${it.state}" }) { conn -> NetConnRow(conn) }
                }
                if (listening.isNotEmpty()) {
                    item { NetSectionHeader("Listening/Unbound (${listening.size})", MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(listening, key = { "${it.localAddress}-${it.proto}-${it.uid}" }) { conn -> NetConnRow(conn) }
                }
            }
        }
    }
}

@Composable
private fun NetSectionHeader(title: String, color: Color) {
    Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
}

@Composable
private fun NetConnRow(conn: NetConn) {
    val stateColor = when {
        conn.state == "ESTABLISHED" || conn.state == "ESTAB" -> AccentGreen
        conn.state == "LISTEN" || conn.state == "UNCONN" -> AccentOrange
        conn.state == "TIME_WAIT" || conn.state == "CLOSE_WAIT" -> AccentRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(Modifier.fillMaxWidth(), color = Color.Transparent) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(conn.proto, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Surface(shape = RoundedCornerShape(4.dp), color = stateColor.copy(0.13f)) {
                    Text(conn.state, fontSize = 9.sp, color = stateColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
                if (conn.process.isNotBlank()) {
                    AppAvatar(conn.process, 20.dp)
                    Text(conn.process.toDisplayName(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } else if (conn.uid > 0) {
                    Text("UID ${conn.uid}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(conn.localAddress, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("→", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(conn.remoteAddress, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (conn.remoteAddress.startsWith("0.0.0.0") || conn.remoteAddress.startsWith("*")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(Modifier.align(Alignment.Bottom))
    }
}

// ─── COMPONENTS ─────────────────────────────────────────────────────────────────

@Composable
private fun ComponentsTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    if (state.blockedComponents.isEmpty()) EmptyState(Icons.Default.Shield, "No blocked components", "Block trackers or disable components from App Manager")
    else LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(state.blockedComponents, key = { idx, it -> "${idx}_${it.packageName}/${it.componentName}" }) { _, comp ->
            ListItem(
                headlineContent = { Text(comp.componentName.substringAfterLast('.')) },
                supportingContent = { Column { Text(comp.packageName, style = MaterialTheme.typography.bodySmall); Text("${comp.componentType} · ${comp.ruleSource}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                leadingContent = { Icon(if (comp.isTracker) Icons.Default.TrackChanges else Icons.Default.Block, null, tint = if (comp.isTracker) AccentOrange else MaterialTheme.colorScheme.primary) },
                trailingContent = { IconButton(onClick = { viewModel.enableComponent(comp.packageName, comp.componentName) }) { Icon(Icons.Default.PlayArrow, "Enable") } },
            )
            HorizontalDivider()
        }
    }
}

// ─── RULES ──────────────────────────────────────────────────────────────────────

@Composable
private fun RulesTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    if (state.privacyRules.isEmpty()) EmptyState(Icons.Default.Rule, "No custom rules", "Privacy rules appear here when you create them")
    else LazyColumn(Modifier.fillMaxSize()) {
        items(state.privacyRules, key = { it.id }) { rule ->
            ListItem(
                headlineContent = { Text(rule.ruleName) },
                supportingContent = { Text("${rule.ruleType} · ${rule.packageName}", style = MaterialTheme.typography.bodySmall) },
                trailingContent = { Row { Switch(checked = rule.isEnabled, onCheckedChange = { viewModel.toggleRule(rule) }); IconButton(onClick = { viewModel.deleteRule(rule) }) { Icon(Icons.Default.Delete, "Delete") } } },
            )
            HorizontalDivider()
        }
    }
}

// ─── AUDIT ──────────────────────────────────────────────────────────────────────

@Composable
private fun AuditTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val filtered = remember(state.auditApps, state.auditSearchQuery) {
        state.auditApps.filter { state.auditSearchQuery.isBlank() || it.displayName.contains(state.auditSearchQuery, true) || it.packageName.contains(state.auditSearchQuery, true) }
    }
    Column(Modifier.fillMaxSize()) {
        if (!state.auditLoading && state.auditApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Security, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    Text("Privacy Audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Scans every app on the target device for:\n• Granted dangerous permissions (dumpsys package)\n• Known tracker SDK embeddings\nComputes a privacy score per app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Button(onClick = { viewModel.startPrivacyAudit() }) { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Start Audit") }
                }
            }
        } else if (state.auditLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Running: pm list packages + dumpsys package on target…", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        } else {
            val highRisk = state.auditApps.count { it.privacyScore < 50 }
            val medRisk  = state.auditApps.count { it.privacyScore in 50..74 }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuditChip("$highRisk", "High Risk", AccentRed, Modifier.weight(1f))
                AuditChip("$medRisk", "Medium", AccentOrange, Modifier.weight(1f))
                AuditChip("${state.auditApps.size - highRisk - medRisk}", "Low Risk", AccentGreen, Modifier.weight(1f))
                FilledTonalButton(onClick = { viewModel.startPrivacyAudit() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null, Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text("Rescan", fontSize = 11.sp) }
            }
            OutlinedTextField(value = state.auditSearchQuery, onValueChange = viewModel::onAuditSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                placeholder = { Text("Search…") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = { if (state.auditSearchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onAuditSearch("") }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) } })
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val riskColor = when { app.privacyScore < 50 -> AccentRed; app.privacyScore < 75 -> AccentOrange; else -> AccentGreen }
                    var expanded by remember { mutableStateOf(false) }
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = if (app.privacyScore < 50) AccentRed.copy(0.04f) else MaterialTheme.colorScheme.surfaceContainer),
                        onClick = { expanded = !expanded }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AppAvatar(app.packageName, 34.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(app.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                }
                                Surface(shape = CircleShape, color = riskColor.copy(0.15f), modifier = Modifier.size(38.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text("${app.privacyScore}", style = MaterialTheme.typography.labelSmall, color = riskColor, fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (app.grantedDangerousPerms.isNotEmpty()) AuditMini("${app.grantedDangerousPerms.size} dangerous perms", AccentOrange)
                                if (app.trackerHits.isNotEmpty()) AuditMini("${app.trackerHits.size} tracker SDK${if (app.trackerHits.size > 1) "s" else ""}", AccentRed)
                                if (app.grantedDangerousPerms.isEmpty() && app.trackerHits.isEmpty()) AuditMini("Clean", AccentGreen)
                            }
                            AnimatedVisibility(expanded) {
                                Column(Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))
                                    if (app.grantedDangerousPerms.isNotEmpty()) {
                                        Text("Granted Dangerous Permissions on Target (${app.grantedDangerousPerms.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentOrange)
                                        Spacer(Modifier.height(3.dp))
                                        app.grantedDangerousPerms.forEach { Text("• ${it.substringAfterLast('.')}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    if (app.trackerHits.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text("Detected Tracker SDKs (${app.trackerHits.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentRed)
                                        Spacer(Modifier.height(3.dp))
                                        app.trackerHits.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

@Composable private fun AuditChip(v: String, l: String, c: Color, mod: Modifier) {
    Card(mod, colors = CardDefaults.cardColors(containerColor = c.copy(0.10f))) {
        Column(Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(v, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c)
            Text(l, style = MaterialTheme.typography.labelSmall, color = c, fontSize = 9.sp)
        }
    }
}
@Composable private fun AuditMini(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.10f)) { Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp) }
}
