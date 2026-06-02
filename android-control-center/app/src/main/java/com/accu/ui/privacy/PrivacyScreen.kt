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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // SAF Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportRulesTo(it) }
    }

    // SAF Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@let
                    viewModel.importRulesFrom(json)
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Privacy Center",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        exportLauncher.launch("accu_privacy_rules_${System.currentTimeMillis()}.json")
                    }) { Icon(Icons.Default.IosShare, "Export rules") }
                    IconButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "*/*"
                            addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        }
                        importLauncher.launch(intent)
                    }) { Icon(Icons.Default.FileOpen, "Import rules") }
                    InfoTooltipIcon(
                        title = "Privacy Center",
                        description = "Block trackers, sensors, and network access per-app.\n\n• TRACKERS: Disable Firebase, AdMob, Crashlytics, etc.\n• SENSORS: Block camera/mic/location per-app via AppOps\n• FIREWALL: Restrict background network per-app\n• COMPONENTS: Manage disabled components\n• AUDIT: Privacy score for every installed app\n\nAll operations via ACCU connection (ADB/root required for changes)."
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Stats banner
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(AccentRed.copy(0.18f), MaterialTheme.colorScheme.surface, AccentCyan.copy(0.14f))))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrivacyStatChip("${state.blockedCount}", "Blocked", AccentRed)
                    PrivacyStatChip("${state.trackerCount}", "Trackers", AccentOrange)
                    PrivacyStatChip("${state.privacyRules.count { it.isEnabled }}", "Rules", AccentGreen)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (state.blockedCount > 0) AccentGreen.copy(0.15f) else MaterialTheme.colorScheme.errorContainer.copy(0.5f),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (state.blockedCount > 0) Icons.Default.Shield else Icons.Default.GppBad, null, Modifier.size(14.dp), tint = if (state.blockedCount > 0) AccentGreen else MaterialTheme.colorScheme.error)
                            Text(if (state.blockedCount > 0) "Protected" else "Exposed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (state.blockedCount > 0) AccentGreen else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Tab row
            val tabIndex = PrivacyTab.entries.indexOf(state.selectedTab)
            ScrollableTabRow(
                selectedTabIndex = tabIndex,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (tabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[tabIndex]), color = MaterialTheme.colorScheme.primary)
                    }
                },
                divider = {},
            ) {
                PrivacyTab.entries.forEachIndexed { _, tab ->
                    val selected = state.selectedTab == tab
                    Tab(
                        selected = selected,
                        onClick = { viewModel.onTabChange(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    PrivacyTab.DASHBOARD  -> "Dashboard"
                                    PrivacyTab.TRACKERS   -> "Trackers"
                                    PrivacyTab.SENSORS    -> "Sensors"
                                    PrivacyTab.FIREWALL   -> "Firewall"
                                    PrivacyTab.COMPONENTS -> "Components"
                                    PrivacyTab.RULES      -> "Rules"
                                    PrivacyTab.AUDIT      -> "Audit"
                                },
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }

            when (state.selectedTab) {
                PrivacyTab.DASHBOARD  -> PrivacyDashboard(state, viewModel)
                PrivacyTab.TRACKERS   -> TrackerBlockerTab(state, viewModel)
                PrivacyTab.SENSORS    -> SensorsTab(state, viewModel)
                PrivacyTab.FIREWALL   -> FirewallTab(state, viewModel)
                PrivacyTab.COMPONENTS -> ComponentsTab(state, viewModel)
                PrivacyTab.RULES      -> RulesTab(state, viewModel)
                PrivacyTab.AUDIT      -> AuditTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun PrivacyStatChip(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── DASHBOARD ─────────────────────────────────────────────────────────────────
@Composable
private fun PrivacyDashboard(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, null, Modifier.size(18.dp), tint = AccentOrange)
                        Spacer(Modifier.width(6.dp))
                        Text("Quick Block", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    val actions = listOf(
                        Triple("Analytics", Icons.Default.Analytics, AccentCyan),
                        Triple("Ads", Icons.Default.MoneyOff, AccentOrange),
                        Triple("Social", Icons.Default.People, AccentPurple),
                        Triple("Crash Reports", Icons.Default.BugReport, AccentRed),
                    )
                    actions.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, icon, color) ->
                                FilledTonalButton(
                                    onClick = { viewModel.blockTrackersInCategory(label) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(0.12f)),
                                ) {
                                    Icon(icon, null, Modifier.size(14.dp), tint = color)
                                    Spacer(Modifier.width(4.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.4f))) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Shield, null, Modifier.size(36.dp).align(Alignment.CenterVertically), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Intent Firewall Blocking", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "IFW blocks are invisible to the app — it never receives the intent. More secure than pm disable; apps cannot detect or work around it.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElevatedCard(Modifier.weight(1f), onClick = { viewModel.onTabChange(PrivacyTab.SENSORS) }) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Sensors, null, Modifier.size(28.dp), tint = AccentCyan)
                        Spacer(Modifier.height(4.dp))
                        Text("Sensor Control", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Block cam/mic/location per-app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    }
                }
                ElevatedCard(Modifier.weight(1f), onClick = { viewModel.onTabChange(PrivacyTab.FIREWALL) }) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Fireplace, null, Modifier.size(28.dp), tint = AccentOrange)
                        Spacer(Modifier.height(4.dp))
                        Text("Network Firewall", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Restrict background data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    }
                }
                ElevatedCard(Modifier.weight(1f), onClick = { viewModel.onTabChange(PrivacyTab.AUDIT) }) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Security, null, Modifier.size(28.dp), tint = AccentGreen)
                        Spacer(Modifier.height(4.dp))
                        Text("Privacy Audit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Score all installed apps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

// ─── TRACKERS ──────────────────────────────────────────────────────────────────
@Composable
private fun TrackerBlockerTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.trackerCategories) { cat ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${cat.trackerCount} known SDKs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(cat.packages.take(3).joinToString(", ") { it.substringAfterLast('.') } + if (cat.packages.size > 3) "…" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.blockTrackersInCategory(cat.name) }) { Text("Block All") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Exodus Privacy Integration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tracker signatures from Exodus Privacy database. Blocks at component level — apps cannot detect the block.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─── SENSORS / APPOPS ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensorsTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val context = LocalContext.current
    val filtered = remember(state.sensorApps, state.sensorSearchQuery) {
        state.sensorApps.filter {
            state.sensorSearchQuery.isBlank() ||
            it.appName.contains(state.sensorSearchQuery, true) ||
            it.packageName.contains(state.sensorSearchQuery, true)
        }
    }

    var expandedPkg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Info banner
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f)),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Uses AppOps to deny sensor access per-app. Works with ADB or Root. Tap an app to manage individual ops.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        if (state.sensorAppsLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Loading apps…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            OutlinedTextField(
                value = state.sensorSearchQuery, onValueChange = viewModel::onSensorSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (state.sensorSearchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onSensorSearch("") }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true, shape = RoundedCornerShape(12.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val isExpanded = expandedPkg == app.packageName
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                        onClick = {
                            expandedPkg = if (isExpanded) null else app.packageName
                            if (!isExpanded) viewModel.loadSensorStateForApp(app.packageName)
                        },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null }).crossfade(true).build(),
                                    contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.appName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                                if (app.blockedOps.isNotEmpty()) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = AccentRed.copy(0.12f)) {
                                        Text("${app.blockedOps.size} blocked", style = MaterialTheme.typography.labelSmall, color = AccentRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                }
                                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                            }
                            AnimatedVisibility(isExpanded) {
                                Column(Modifier.padding(top = 10.dp)) {
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))
                                    // Block all / Unblock all row
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = { viewModel.blockAllSensorsForApp(app.packageName) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentRed.copy(0.12f)),
                                        ) { Icon(Icons.Default.Block, null, Modifier.size(14.dp), tint = AccentRed); Spacer(Modifier.width(4.dp)); Text("Block All", fontSize = 12.sp, color = AccentRed) }
                                        OutlinedButton(onClick = { viewModel.unblockAllSensorsForApp(app.packageName) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Allow All", fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    // Individual ops
                                    SENSOR_OPS.forEach { (op, label) ->
                                        val isBlocked = op in app.blockedOps
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val opColor = when {
                                                op.contains("CAMERA") || op.contains("AUDIO") -> AccentRed
                                                op.contains("LOCATION") -> AccentOrange
                                                op.contains("CONTACT") || op.contains("CALL") || op.contains("SMS") -> AccentPurple
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                            Surface(shape = CircleShape, color = opColor.copy(0.12f), modifier = Modifier.size(26.dp)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(if (isBlocked) Icons.Default.Block else Icons.Default.Check, null, Modifier.size(14.dp), tint = if (isBlocked) AccentRed else AccentGreen)
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Switch(
                                                checked = !isBlocked,
                                                onCheckedChange = { allow -> viewModel.blockSensorOp(app.packageName, op, !allow) },
                                                modifier = Modifier.height(20.dp),
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

// ─── FIREWALL ─────────────────────────────────────────────────────────────────
@Composable
private fun FirewallTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val context = LocalContext.current
    val filtered = remember(state.firewallApps, state.firewallSearchQuery) {
        state.firewallApps.filter {
            state.firewallSearchQuery.isBlank() ||
            it.appName.contains(state.firewallSearchQuery, true) ||
            it.packageName.contains(state.firewallSearchQuery, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(0.08f)),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fireplace, null, Modifier.size(16.dp), tint = AccentOrange)
                Spacer(Modifier.width(8.dp))
                Text("Restricts background network access via Android netpolicy. Requires ADB/root. Apps can still access network in foreground.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (state.firewallLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Loading apps…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            OutlinedTextField(
                value = state.firewallSearchQuery, onValueChange = viewModel::onFirewallSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (state.firewallSearchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onFirewallSearch("") }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true, shape = RoundedCornerShape(12.dp),
            )
            // Stats row
            val blockedCount = state.firewallApps.count { it.isBlocked }
            if (blockedCount > 0) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, null, Modifier.size(14.dp), tint = AccentRed)
                    Spacer(Modifier.width(4.dp))
                    Text("$blockedCount apps with background network restricted", style = MaterialTheme.typography.labelSmall, color = AccentRed)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = { Text(app.appName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace) },
                        leadingContent = {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null }).crossfade(true).build(),
                                contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (app.isBlocked) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(0.12f)) {
                                        Text("Blocked", style = MaterialTheme.typography.labelSmall, color = AccentRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Switch(
                                    checked = !app.isBlocked,
                                    onCheckedChange = { allow -> viewModel.blockNetworkForApp(app.packageName, !allow) },
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ─── COMPONENTS ───────────────────────────────────────────────────────────────
@Composable
private fun ComponentsTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    if (state.blockedComponents.isEmpty()) {
        EmptyState(Icons.Default.Shield, "No blocked components", "Block trackers or disable components from App Manager")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.blockedComponents, key = { "${it.packageName}/${it.componentName}" }) { comp ->
                ListItem(
                    headlineContent = { Text(comp.componentName.substringAfterLast('.')) },
                    supportingContent = {
                        Column {
                            Text(comp.packageName, style = MaterialTheme.typography.bodySmall)
                            Text("${comp.componentType} · ${comp.ruleSource}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingContent = { Icon(if (comp.isTracker) Icons.Default.TrackChanges else Icons.Default.Block, null, tint = if (comp.isTracker) AccentOrange else MaterialTheme.colorScheme.primary) },
                    trailingContent = { IconButton(onClick = { viewModel.enableComponent(comp.packageName, comp.componentName) }) { Icon(Icons.Default.PlayArrow, "Enable") } },
                )
                HorizontalDivider()
            }
        }
    }
}

// ─── RULES ─────────────────────────────────────────────────────────────────────
@Composable
private fun RulesTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    if (state.privacyRules.isEmpty()) {
        EmptyState(Icons.Default.Rule, "No custom rules", "Privacy rules appear here when you create them")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.privacyRules, key = { it.id }) { rule ->
                ListItem(
                    headlineContent = { Text(rule.ruleName) },
                    supportingContent = { Text("${rule.ruleType} · ${rule.packageName}", style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        Row {
                            Switch(checked = rule.isEnabled, onCheckedChange = { viewModel.toggleRule(rule) })
                            IconButton(onClick = { viewModel.deleteRule(rule) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

// ─── AUDIT ─────────────────────────────────────────────────────────────────────
@Composable
private fun AuditTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    val context = LocalContext.current
    val filtered = remember(state.auditApps, state.auditSearchQuery) {
        state.auditApps.filter {
            state.auditSearchQuery.isBlank() ||
            it.appName.contains(state.auditSearchQuery, true) ||
            it.packageName.contains(state.auditSearchQuery, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!state.auditLoading && state.auditApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Security, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    Text("Privacy Audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Scan all installed apps and rank by privacy risk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.startPrivacyAudit() }) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Start Audit")
                    }
                }
            }
        } else if (state.auditLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Scanning apps…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            // Summary header
            val highRisk = state.auditApps.count { it.privacyScore < 50 }
            val mediumRisk = state.auditApps.count { it.privacyScore in 50..74 }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuditRiskChip("$highRisk", "High Risk", AccentRed, Modifier.weight(1f))
                AuditRiskChip("$mediumRisk", "Medium", AccentOrange, Modifier.weight(1f))
                AuditRiskChip("${state.auditApps.size - highRisk - mediumRisk}", "OK", AccentGreen, Modifier.weight(1f))
                FilledTonalButton(onClick = { viewModel.startPrivacyAudit() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Rescan", fontSize = 11.sp)
                }
            }
            OutlinedTextField(
                value = state.auditSearchQuery, onValueChange = viewModel::onAuditSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (state.auditSearchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onAuditSearch("") }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true, shape = RoundedCornerShape(12.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val riskColor = when {
                        app.privacyScore < 50 -> AccentRed
                        app.privacyScore < 75 -> AccentOrange
                        else -> AccentGreen
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                        onClick = { expanded = !expanded },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null }).crossfade(true).build(),
                                    contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.appName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                // Score badge
                                Surface(shape = CircleShape, color = riskColor.copy(0.15f), modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${app.privacyScore}", style = MaterialTheme.typography.labelSmall, color = riskColor, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            // Mini stats row
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (app.grantedPerms.isNotEmpty()) {
                                    AuditMiniChip("${app.grantedPerms.size} perms", AccentOrange)
                                }
                                if (app.trackerHits.isNotEmpty()) {
                                    AuditMiniChip("${app.trackerHits.size} trackers", AccentRed)
                                }
                                if (app.grantedPerms.isEmpty() && app.trackerHits.isEmpty()) {
                                    AuditMiniChip("Clean", AccentGreen)
                                }
                            }
                            AnimatedVisibility(expanded) {
                                Column(Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))
                                    if (app.grantedPerms.isNotEmpty()) {
                                        Text("Granted Dangerous Permissions", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentOrange)
                                        Spacer(Modifier.height(4.dp))
                                        app.grantedPerms.take(8).forEach { perm ->
                                            Text("• ${perm.substringAfterLast('.')}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (app.grantedPerms.size > 8) Text("  +${app.grantedPerms.size - 8} more…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (app.trackerHits.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text("Detected Trackers", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentRed)
                                        Spacer(Modifier.height(4.dp))
                                        app.trackerHits.forEach { t ->
                                            Text("• $t", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun AuditRiskChip(count: String, label: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(0.1f))) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 9.sp)
        }
    }
}

@Composable
private fun AuditMiniChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.1f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
    }
}
