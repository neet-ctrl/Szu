package com.accu.ui.shizuku

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentRed
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentCyan
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuCenterScreen(
    onBack: () -> Unit = {},
    onNavigateToAdbPairing: () -> Unit = {},
    onNavigateToAccuApps: () -> Unit = {},
    onNavigateToAccuServiceHub: () -> Unit = {},
    viewModel: ShizukuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf("Status", "Apps", "Rish", "Settings", "Logs")
    val tabIcons = listOf(
        Icons.Outlined.Dashboard, Icons.Outlined.Apps, Icons.Outlined.Terminal,
        Icons.Outlined.Tune, Icons.Outlined.Article
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ACCU Connection", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onNavigateToAdbPairing) { Icon(Icons.Outlined.Usb, "ADB Pairing") }
                    IconButton(onClick = onNavigateToAccuApps) { Icon(Icons.Outlined.Apps, "ACCU Apps") }
                    IconButton(onClick = onNavigateToAccuServiceHub) { Icon(Icons.Outlined.Api, "ACCU Service Hub") }
                    if (state.isAvailable && state.isGranted) {
                        IconButton(onClick = viewModel::runDiagnostics) {
                            if (state.diagnosticsRunning) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.BugReport, "Diagnostics")
                        }
                    }
                    IconButton(onClick = viewModel::clearLogs) { Icon(Icons.Outlined.DeleteSweep, "Clear Logs") }
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]))
                }
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(tabIcons[idx], null, Modifier.size(18.dp)) },
                        text = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            when (selectedTab) {
                0 -> StatusTab(state, viewModel)
                1 -> AuthorizedAppsTab(state, viewModel)
                2 -> RishTab(state, viewModel)
                3 -> SettingsTab(state, viewModel)
                4 -> LogsTab(state, viewModel)
            }
        }
    }
}

// ── Tab 1: Status ─────────────────────────────────────────────────────────────

@Composable
private fun StatusTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Main status card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        state.isLoading -> MaterialTheme.colorScheme.surfaceVariant
                        state.isAvailable && state.isGranted -> Color(0xFF14532D).copy(alpha = 0.15f)
                        state.isAvailable -> Color(0xFF7C3AED).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(
                        when {
                            state.isLoading -> MaterialTheme.colorScheme.outline.copy(0.2f)
                            state.isAvailable && state.isGranted -> AccentGreen.copy(0.2f)
                            state.isAvailable -> Color(0xFF7C3AED).copy(0.2f)
                            else -> AccentRed.copy(0.2f)
                        }
                    ), contentAlignment = Alignment.Center) {
                        when {
                            state.isLoading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            state.isAvailable && state.isGranted -> Icon(Icons.Filled.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(32.dp))
                            state.isAvailable -> Icon(Icons.Outlined.Lock, null, tint = Color(0xFF7C3AED), modifier = Modifier.size(32.dp))
                            else -> Icon(Icons.Outlined.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(32.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                state.isLoading -> "Checking…"
                                state.isAvailable && state.isGranted -> "ACCU Connected"
                                state.isAvailable -> "Connecting…"
                                else -> "Not Connected"
                            },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                state.isAvailable && state.isGranted -> "${state.serverStartMethod} • uid=${state.uid}"
                                state.isAvailable -> "Establishing privilege connection…"
                                else -> "Setup wireless ADB or use root"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Action buttons
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.isGranted && state.isAvailable) {
                    Button(onClick = vm::requestPermission, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Key, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Grant Permission")
                    }
                }
                if (!state.isAvailable) {
                    OutlinedButton(onClick = vm::startWithAdb, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Wifi, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wireless ADB")
                    }
                    OutlinedButton(onClick = vm::connectOtg, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Usb, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("OTG / USB")
                    }
                    if (state.isRootAvailable) {
                        OutlinedButton(onClick = vm::startWithRoot, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Use Root")
                        }
                    }
                } else {
                    OutlinedButton(onClick = vm::restartServer, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.RestartAlt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restart")
                    }
                    OutlinedButton(onClick = vm::stopServer, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }

        // Server info grid
        if (state.isAvailable) {
            item {
                Text("Server Details", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoRow("Version", "v${state.version} (patch ${state.patchVersion})", Icons.Outlined.Info)
                        InfoRow("UID", when (state.uid) { 0 -> "0 (root)" ; 2000 -> "2000 (adb shell)" ; else -> "${state.uid}" }, Icons.Outlined.Person)
                        InfoRow("PID", if (state.serverPid > 0) "${state.serverPid}" else "Unknown", Icons.Outlined.Memory)
                        InfoRow("Start method", state.serverStartMethod.ifEmpty { "Unknown" }, Icons.Outlined.PlayCircle)
                        if (state.seLinuxContext.isNotEmpty()) InfoRow("SELinux", state.seLinuxContext, Icons.Outlined.Security)
                        InfoRow("Permission test", if (state.permissionGranted) "Passed ✓" else "Failed", Icons.Outlined.VerifiedUser, if (state.permissionGranted) AccentGreen else AccentRed)
                        InfoRow("Authorized apps", "${state.authorizedApps.count { it.isGranted }}", Icons.Outlined.Apps)
                    }
                }
            }
        }

        // Device info
        if (state.deviceIp.isNotEmpty()) {
            item {
                Text("Network", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoRow("Device IP", state.deviceIp, Icons.Outlined.Language)
                        InfoRow("Wireless ADB", if (state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS) "Connected" else "Not connected", Icons.Outlined.Wifi, if (state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.connectedAdbDevices.isNotEmpty()) {
                            InfoRow("ADB devices", "${state.connectedAdbDevices.size} connected", Icons.Outlined.DeviceHub, AccentCyan)
                        }
                    }
                }
            }
        }

        // How to start
        if (!state.isAvailable) {
            item {
                Text("How to Start Shizuku", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HowToStep(1, "Wireless ADB (recommended)", "Enable Developer Options → Wireless debugging → Pair device")
                        HowToStep(2, "Run the ADB command", "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")
                        HowToStep(3, "Or use Root", "Tap 'Start via Root' above if your device is rooted")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, icon: ImageVector, tint: Color = Color.Unspecified) {
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { clipboard.setText(AnnotatedString(value)) },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

@Composable
private fun HowToStep(step: Int, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text("$step", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

// ── Tab 2: Authorized Apps ────────────────────────────────────────────────────

@Composable
private fun AuthorizedAppsTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    val filteredApps = remember(state.authorizedApps, state.authorizedAppsFilter, state.authorizedAppsSearch) {
        state.authorizedApps
            .filter { app ->
                when (state.authorizedAppsFilter) {
                    AppsFilter.GRANTED -> app.isGranted
                    AppsFilter.DENIED -> !app.isGranted
                    AppsFilter.ALL -> true
                }
            }
            .filter { app ->
                state.authorizedAppsSearch.isBlank() ||
                        app.appName.contains(state.authorizedAppsSearch, ignoreCase = true) ||
                        app.packageName.contains(state.authorizedAppsSearch, ignoreCase = true)
            }
    }

    Column(Modifier.fillMaxSize()) {
        // Search + actions bar
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.authorizedAppsSearch,
                onValueChange = vm::setAppsSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                trailingIcon = { if (state.authorizedAppsSearch.isNotEmpty()) IconButton(onClick = { vm.setAppsSearch("") }) { Icon(Icons.Outlined.Clear, null, Modifier.size(18.dp)) } },
                singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppsFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.authorizedAppsFilter == filter,
                        onClick = { vm.setAppsFilter(filter) },
                        label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = vm::loadApps) { Icon(Icons.Outlined.Refresh, "Refresh", Modifier.size(20.dp)) }
                if (state.authorizedApps.any { it.isGranted }) {
                    IconButton(onClick = vm::revokeAll) { Icon(Icons.Outlined.RemoveCircle, "Revoke all", Modifier.size(20.dp), tint = AccentRed) }
                }
            }
        }
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (!state.isAvailable || !state.isGranted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Shizuku permission required", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.AppsOutage, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.authorizedApps.isEmpty()) {
                        TextButton(onClick = vm::loadApps) { Text("Load apps") }
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text("${filteredApps.size} app(s) — ${state.authorizedApps.count { it.isGranted }} granted",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                }
                items(filteredApps, key = { it.packageName }) { app ->
                    AuthorizedAppCard(app, onGrant = { vm.grantApp(app) }, onRevoke = { vm.revokeApp(app) })
                }
            }
        }
    }
}

@Composable
private fun AuthorizedAppCard(app: AuthorizedApp, onGrant: () -> Unit, onRevoke: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(
                if (app.isGranted) AccentGreen.copy(0.15f) else AccentRed.copy(0.1f)
            ), contentAlignment = Alignment.Center) {
                Icon(
                    if (app.isGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                    null, tint = if (app.isGranted) AccentGreen else AccentRed, modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (app.isSystemApp) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("SYS", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.versionName.isNotEmpty()) Text("v${app.versionName} • uid ${app.uid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), fontSize = 10.sp)
            }
            if (app.isGranted) {
                OutlinedButton(onClick = onRevoke, colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed), modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Revoke", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(onClick = onGrant, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Grant", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Tab 5: Rish Shell ─────────────────────────────────────────────────────────

@Composable
private fun RishTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { vm.loadRishInfo() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Text("Rish Shell", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                    Text("ADB shell uid=2000", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // What is rish
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What is rish?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "rish is an interactive shell provided by Shizuku that runs with ADB-level privileges (uid=2000). " +
                                "Unlike regular terminals, rish can execute commands that require ADB shell access — including pm, am, wm, settings, dumpsys and more — " +
                                "without needing a computer connected.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.rishInfo.isAvailable) {
                        Surface(color = AccentGreen.copy(0.1f), shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                                Text("rish is available at ${state.rishInfo.path}", style = MaterialTheme.typography.bodySmall, color = AccentGreen)
                            }
                        }
                    }
                }
            }
        }

        // Setup steps
        item {
            Text("Setup in Termux", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val steps = listOf(
                        "Open Shizuku Manager app" to "Tap 'Use Shizuku in terminal apps'",
                        "Find the exported rish binary" to "Check /sdcard/Download/ or Shizuku manager",
                        "Copy to Termux home" to "cp /sdcard/rish ~/rish && cp /sdcard/rish_sh ~/rish_sh",
                        "Make them executable" to "chmod +x ~/rish ~/rish_sh",
                        "Launch rish shell" to "./rish",
                        "You get ADB privileges" to "uid=2000 (shell) — run pm, am, wm, settings freely",
                    )
                    steps.forEachIndexed { i, (title, cmd) ->
                        RishStep(i + 1, title, cmd, onCopy = { clipboard.setText(AnnotatedString(cmd)) })
                    }
                }
            }
        }

        // Quick commands
        item {
            Text("Quick Command Reference", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        val rishCmds = listOf(
            "pm list packages -3" to "List user apps",
            "pm uninstall --user 0 <pkg>" to "Remove bloatware (no root)",
            "pm disable-user --user 0 <pkg>" to "Disable app for user",
            "pm clear <pkg>" to "Clear app data",
            "pm grant <pkg> <perm>" to "Grant permission",
            "am force-stop <pkg>" to "Force stop app",
            "am start -n <pkg>/<activity>" to "Launch specific activity",
            "settings put global adb_wifi_enabled 1" to "Enable wireless ADB",
            "wm density 420" to "Set display density",
            "wm density reset" to "Reset density",
            "wm size 1080x1920" to "Set display size",
            "svc wifi enable" to "Enable WiFi",
            "svc data disable" to "Disable mobile data",
            "dumpsys battery" to "Battery info",
            "dumpsys meminfo" to "Memory info",
            "input tap 540 960" to "Tap screen center",
            "input keyevent 26" to "Power button",
            "screencap -p /sdcard/ss.png" to "Screenshot",
        )

        items(rishCmds) { (cmd, desc) ->
            RishCommandRow(cmd, desc, onCopy = { clipboard.setText(AnnotatedString(cmd)) })
        }
    }
}

@Composable
private fun RishStep(step: Int, title: String, cmd: String, onCopy: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("$step", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(cmd, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), fontSize = 11.sp)
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(14.dp)) }
            }
        }
    }
}

@Composable
private fun RishCommandRow(cmd: String, desc: String, onCopy: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Tab 6: Settings ───────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("App Behavior", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    ShizukuSettingRow(
                        title = "Auto-start on boot",
                        subtitle = "Automatically start Shizuku server when device boots (root required)",
                        icon = Icons.Outlined.PowerSettingsNew,
                        checked = state.autoStartOnBoot,
                        onToggle = vm::setAutoStartOnBoot,
                        enabled = state.isRootAvailable,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ShizukuSettingRow(
                        title = "Show notification",
                        subtitle = "Keep a persistent notification while Shizuku is running",
                        icon = Icons.Outlined.Notifications,
                        checked = state.showNotification,
                        onToggle = vm::setShowNotification,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ShizukuSettingRow(
                        title = "Require unlock for tiles",
                        subtitle = "Only execute Quick Settings tiles after device is unlocked",
                        icon = Icons.Outlined.LockClock,
                        checked = state.requireUnlockForTiles,
                        onToggle = vm::setRequireUnlockForTiles,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)); Text("Appearance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    ShizukuSettingRow(
                        title = "Black night mode",
                        subtitle = "Use pure black (#000000) background in dark mode",
                        icon = Icons.Outlined.DarkMode,
                        checked = state.blackNightMode,
                        onToggle = vm::setBlackNightMode,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ShizukuSettingRow(
                        title = "Use system colors",
                        subtitle = "Apply Material You dynamic color scheme from wallpaper",
                        icon = Icons.Outlined.ColorLens,
                        checked = state.useSystemColors,
                        onToggle = vm::setUseSystemColors,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)); Text("Advanced", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::runDiagnostics, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.BugReport, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Diagnostics")
                        }
                        OutlinedButton(onClick = vm::revokeAll, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed), enabled = state.authorizedApps.any { it.isGranted }) {
                            Icon(Icons.Outlined.RemoveCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Revoke All")
                        }
                    }
                    val clipboard = LocalClipboardManager.current
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.exportLogs())) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy All Logs to Clipboard")
                    }
                }
            }
        }

        // Server info summary
        if (state.isAvailable) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Server Info", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("v${state.version} patch:${state.patchVersion} • uid=${state.uid} • pid=${state.serverPid}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.seLinuxContext.isNotEmpty()) Text("SELinux: ${state.seLinuxContext}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShizukuSettingRow(
    title: String, subtitle: String,
    icon: ImageVector, checked: Boolean,
    onToggle: (Boolean) -> Unit, enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (enabled) 1f else 0.4f))
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

// ── Tab 7: Logs ───────────────────────────────────────────────────────────────

@Composable
private fun LogsTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val filteredLogs = remember(state.logs, state.logFilter) {
        if (state.logFilter == null) state.logs else state.logs.filter { it.level == state.logFilter }
    }
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(filteredLogs.size) {
        if (autoScroll && filteredLogs.isNotEmpty()) listState.animateScrollToItem(filteredLogs.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Controls
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${filteredLogs.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(2.dp))
            // Level filters
            FilterChip(selected = state.logFilter == null, onClick = { vm.setLogFilter(null) }, label = { Text("All", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
            FilterChip(selected = state.logFilter == LogLevel.ERROR, onClick = { vm.setLogFilter(if (state.logFilter == LogLevel.ERROR) null else LogLevel.ERROR) }, label = { Text("ERR", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
            FilterChip(selected = state.logFilter == LogLevel.WARNING, onClick = { vm.setLogFilter(if (state.logFilter == LogLevel.WARNING) null else LogLevel.WARNING) }, label = { Text("WARN", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
            FilterChip(selected = state.logFilter == LogLevel.SUCCESS, onClick = { vm.setLogFilter(if (state.logFilter == LogLevel.SUCCESS) null else LogLevel.SUCCESS) }, label = { Text("OK", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(vm.exportLogs())); }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy logs", Modifier.size(18.dp)) }
            IconButton(onClick = vm::clearLogs, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.DeleteSweep, "Clear", Modifier.size(18.dp)) }
        }
        HorizontalDivider()

        if (filteredLogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Article, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No logs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filteredLogs, key = { it.id }) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: ShizukuLogEntry) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Text(
            timeFmt.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), fontSize = 9.sp,
            modifier = Modifier.width(72.dp)
        )
        val (levelColor, levelTag) = when (entry.level) {
            LogLevel.SUCCESS -> AccentGreen to "OK "
            LogLevel.ERROR -> AccentRed to "ERR"
            LogLevel.WARNING -> AccentOrange to "WRN"
            LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) to "DBG"
            LogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f) to "VRB"
            LogLevel.INFO -> MaterialTheme.colorScheme.primary to "INF"
        }
        Text(levelTag, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = levelColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Text(entry.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = levelColor.copy(if (entry.level == LogLevel.VERBOSE || entry.level == LogLevel.DEBUG) 0.5f else 1f))
    }
}
