package com.accu.ui.shizuku

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.components.*
import com.accu.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuCenterScreen(
    onBack: () -> Unit,
    viewModel: ShizukuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Status", "Authorized Apps", "Wireless ADB", "mDNS", "Rish Shell", "Settings", "Logs")

    var showPairingDialog by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("") }
    var showRevokeDialog by remember { mutableStateOf<AuthorizedApp?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Column {
                                Text("Shizuku Center", fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.isAvailable && state.isGranted) "Running • v${state.version}" else if (state.isAvailable) "Running — Needs Permission" else "Not Running",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isAvailable && state.isGranted) AccentGreen else if (state.isAvailable) AccentOrange else AccentRed
                                )
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = {
                        InfoTooltipIcon(
                            title = "What is Shizuku?",
                            description = "Shizuku is a privilege escalation framework. It lets apps run with ADB-level permissions without needing a connected computer.\n\n• Start via Wireless Debugging (Android 11+)\n• Or via USB ADB from a computer\n• On rooted devices it starts automatically on boot\n\nOnce running, it grants ACCU elevated system access for freezing apps, managing components, customizing UI, and much more."
                        )
                        IconButton(onClick = viewModel::runDiagnostics) { Icon(Icons.Outlined.BugReport, "Diagnostics") }
                        IconButton(onClick = viewModel::clearLogs) { Icon(Icons.Outlined.DeleteSweep, "Clear Logs") }
                        IconButton(onClick = { viewModel.restartServer() }) { Icon(Icons.Outlined.RestartAlt, "Restart") }
                    }
                )
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            when (selectedTab) {
                0 -> statusTab(state, viewModel, onShowPairing = { showPairingDialog = true })
                1 -> authorizedAppsTab(state, onRevoke = { showRevokeDialog = it }, onGrant = { viewModel.grantApp(it.packageName) })
                2 -> wirelessAdbTab(state, viewModel, onShowPairing = { showPairingDialog = true })
                3 -> mdnsTab(state, viewModel)
                4 -> rishShellTab()
                5 -> settingsTab(state, viewModel)
                6 -> logsTab(state, viewModel)
            }
        }
    }

    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.QrCode, null)
                    Text("ADB Wireless Pairing")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Steps to pair:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    listOf(
                        "Go to Settings → Developer Options",
                        "Tap \"Wireless Debugging\"",
                        "Tap \"Pair device with pairing code\"",
                        "Enter the 6-digit code shown below"
                    ).forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(20.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("${i+1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it.take(6) },
                        label = { Text("Pairing Code (6 digits)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPairingDialog = false; viewModel.startPairing(pairingCode) }) { Text("Pair Device") }
            },
            dismissButton = { TextButton(onClick = { showPairingDialog = false }) { Text("Cancel") } },
        )
    }

    showRevokeDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showRevokeDialog = null },
            icon = { Icon(Icons.Default.Warning, null, tint = AccentOrange) },
            title = { Text("Revoke Access?") },
            text = { Text("Remove Shizuku permission for ${app.appName} (${app.packageName})?\n\nThis app will lose elevated access immediately.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.revokeApp(app.packageName); showRevokeDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Revoke") }
            },
            dismissButton = { TextButton(onClick = { showRevokeDialog = null }) { Text("Cancel") } }
        )
    }
}

private fun LazyListScope.statusTab(
    state: ShizukuUiState,
    viewModel: ShizukuViewModel,
    onShowPairing: () -> Unit
) {
    item { ShizukuStatusCard(state = state, onRequestPermission = viewModel::requestPermission, onStartWithAdb = viewModel::startWithAdb, onStartWithRoot = viewModel::startWithRoot, onStop = viewModel::stopServer) }

    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Server Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Divider()
                val rows = listOf(
                    "Service Status" to if (state.isAvailable) "Running" else "Stopped",
                    "Permission" to if (state.isGranted) "Granted" else "Not Granted",
                    "Shizuku Version" to if (state.version > 0) "v${state.version}" else "N/A",
                    "Server UID" to if (state.uid > 0) "${state.uid}" else "N/A",
                    "Server PID" to if (state.serverPid > 0) "${state.serverPid}" else "N/A",
                    "Root Access" to if (state.isRootAvailable) "Available" else "Not Available",
                    "Device IP" to state.deviceIp.ifEmpty { "Unknown" },
                    "ADB Port" to if (state.wirelessAdbPort > 0) ":${state.wirelessAdbPort}" else "Not Set",
                    "Wireless ADB" to if (state.isWirelessAdbEnabled) "Enabled" else "Disabled",
                    "Authorized Apps" to "${state.authorizedApps.size} apps",
                )
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Quick Commands", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    InfoTooltipIcon("Quick Commands", "These commands run immediately via Shizuku. Results appear in the Logs tab.")
                }
                val cmds = listOf(
                    "Check Shizuku permission" to "dumpsys package moe.shizuku.privileged.api | grep permission",
                    "List Shizuku clients" to "dumpsys activity services moe.shizuku",
                    "Connect ADB local" to "adb connect 127.0.0.1:5555",
                    "Get Android version" to "getprop ro.build.version.release",
                    "Battery info" to "dumpsys battery",
                    "Get device IP" to "ip addr show wlan0",
                    "List authorized packages" to "pm list packages | grep -i shizuku",
                )
                cmds.forEach { (label, cmd) ->
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    item { SetupGuideCard() }
}

private fun LazyListScope.authorizedAppsTab(
    state: ShizukuUiState,
    onRevoke: (AuthorizedApp) -> Unit,
    onGrant: (AuthorizedApp) -> Unit,
) {
    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Apps, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Authorized Apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    InfoTooltipIcon("Authorized Apps", "Apps that have been granted Shizuku API access. Each app must request permission at runtime. You can revoke access at any time — the app will immediately lose elevated privileges.")
                    Text("${state.authorizedApps.size} apps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("These apps have been granted access to the Shizuku API.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (state.authorizedApps.isEmpty()) {
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.AppsOutage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("No authorized apps", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Apps will appear here after they request and are granted Shizuku permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(0.8f))
                }
            }
        }
    } else {
        items(state.authorizedApps) { app ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                        Text("UID: ${app.uid}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusBadge(if (app.isGranted) "Granted" else "Revoked", if (app.isGranted) AccentGreen else AccentRed)
                    IconButton(onClick = { if (app.isGranted) onRevoke(app) else onGrant(app) }) {
                        Icon(if (app.isGranted) Icons.Default.LockOpen else Icons.Default.Lock, if (app.isGranted) "Revoke" else "Grant", tint = if (app.isGranted) AccentRed else AccentGreen)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.wirelessAdbTab(
    state: ShizukuUiState,
    viewModel: ShizukuViewModel,
    onShowPairing: () -> Unit,
) {
    item {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = if (state.isWirelessAdbEnabled) AccentGreen.copy(0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)),
            border = BorderStroke(1.dp, if (state.isWirelessAdbEnabled) AccentGreen.copy(0.4f) else Color.Transparent)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Wifi, null, tint = if (state.isWirelessAdbEnabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Wireless ADB", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Requires Android 11+ and Shizuku permission", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    InfoTooltipIcon("Wireless ADB", "Enables ADB over TCP/IP so you can connect from a computer without a USB cable. ACCU uses Shizuku to toggle this without requiring you to manually run ADB commands.")
                    Switch(checked = state.isWirelessAdbEnabled, onCheckedChange = { if (it) viewModel.enableWirelessAdb() else viewModel.disableWirelessAdb() })
                }
                if (state.deviceIp.isNotEmpty() && state.isWirelessAdbEnabled) {
                    val clipboardManager = LocalClipboardManager.current
                    val connectCmd = "adb connect ${state.deviceIp}:${state.wirelessAdbPort}"
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(connectCmd, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(connectCmd)) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text("Run this on your computer to connect via ADB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowPairing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.QrCode, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pair Device")
                    }
                    if (state.isPairing) {
                        OutlinedButton(onClick = {}, modifier = Modifier.weight(1f), enabled = false) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("Pairing…")
                        }
                    }
                }
                if (state.pairingStatus.isNotEmpty()) {
                    Text(state.pairingStatus, style = MaterialTheme.typography.bodySmall, color = if (state.pairingStatus == "Paired!") AccentGreen else AccentRed)
                }
            }
        }
    }

    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Step-by-Step Pairing Guide", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val steps = listOf(
                    "1" to "Open Settings → Developer Options",
                    "2" to "Enable \"Wireless Debugging\"",
                    "3" to "Tap \"Pair device with pairing code\"",
                    "4" to "Note the 6-digit code and IP/Port shown",
                    "5" to "Enter the 6-digit code in the Pair Device dialog",
                    "6" to "Once paired, tap the port number and enable Wireless ADB toggle above",
                    "7" to "Run the connect command on your computer"
                )
                steps.forEach { (num, step) ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(num, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                        }
                        Text(step, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.mdnsTab(state: ShizukuUiState, viewModel: ShizukuViewModel) {
    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.NetworkCheck, null, tint = MaterialTheme.colorScheme.primary)
                    Text("mDNS Auto-Discovery", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    InfoTooltipIcon("ADB mDNS", "mDNS (Multicast DNS) allows automatic discovery of Android devices advertising Wireless Debugging services on the local network. No need to manually enter IP addresses — devices are discovered automatically.")
                }
                Text(
                    "Automatically discovers Android devices advertising ADB over Wi-Fi using mDNS/Bonjour. Both pairing (_adb-tls-pairing._tcp) and connection (_adb-tls-connect._tcp) services are detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = viewModel::scanMdns,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isScanning
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning…")
                    } else {
                        Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan for Devices")
                    }
                }
            }
        }
    }

    if (state.mdnsServices.isEmpty() && !state.isScanning) {
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.TravelExplore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("No devices found", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Make sure target device has Wireless Debugging enabled and is on the same Wi-Fi network.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        items(state.mdnsServices) { service ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.DeviceHub, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(service.serviceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${service.host}:${service.port}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(service.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    FilledTonalButton(onClick = { viewModel.connectMdnsService(service) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("Connect", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.rishShellTab() {
    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Rish — Remote Interactive Shell", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    InfoTooltipIcon("Rish Shell", "Rish is Shizuku's built-in remote shell tool. It bridges Shizuku's privileges to standard terminal emulators. If you have Termux or another terminal app installed, you can use rish to get an interactive ADB shell without needing a computer.")
                }
                Text(
                    "Rish lets terminal apps (like Termux) use Shizuku's elevated privileges. It works as a bridge between Shizuku and standard POSIX shells.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    val rishSections = listOf(
        "Install Rish" to listOf(
            "1. Ensure Shizuku is running and permission is granted." to Icons.Outlined.CheckCircle,
            "2. Extract the rish executables from the Shizuku APK or download from GitHub." to Icons.Outlined.Download,
            "3. Place rish and rish_sh in your PATH (e.g., /data/local/tmp or Termux home)." to Icons.Outlined.FolderOpen,
            "4. Make them executable: chmod +x rish rish_sh" to Icons.Outlined.Terminal,
        ),
        "Use Rish in Termux" to listOf(
            "Open Termux and type: ./rish" to Icons.Outlined.PlayArrow,
            "You'll get a shell running with ADB privileges (uid=2000)." to Icons.Outlined.AdminPanelSettings,
            "Run any ADB shell command directly, e.g.: pm list packages" to Icons.Outlined.List,
            "Type 'exit' to leave the rish session." to Icons.Outlined.ExitToApp,
        ),
        "Example Commands in Rish" to listOf(
            "pm list packages -3    → list user apps" to Icons.Outlined.Apps,
            "settings put global wifi_on 1   → enable WiFi" to Icons.Outlined.Wifi,
            "wm density 420   → set screen DPI" to Icons.Outlined.ScreenRotation,
            "cmd uimode night yes   → enable dark mode" to Icons.Outlined.DarkMode,
        ),
    )

    rishSections.forEach { (title, items) ->
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    items.forEach { (text, icon) ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = if (text.contains("→") || text.contains("./") || text.startsWith("pm") || text.startsWith("settings") || text.startsWith("wm") || text.startsWith("cmd")) FontFamily.Monospace else FontFamily.Default)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.settingsTab(state: ShizukuUiState, viewModel: ShizukuViewModel) {
    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))

                SettingsSwitchRow(
                    icon = Icons.Outlined.Palette,
                    title = "Use System Colors",
                    subtitle = "Follow Material You / Monet color palette",
                    checked = state.useSystemColors,
                    onCheckedChange = viewModel::setUseSystemColors
                )
                SettingsSwitchRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Black Night Theme",
                    subtitle = "Use pure black background in dark mode (AMOLED)",
                    checked = state.blackNightMode,
                    onCheckedChange = viewModel::setBlackNightMode
                )
            }
        }
    }

    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Behavior", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                SettingsSwitchRow(
                    icon = Icons.Outlined.PlayArrow,
                    title = "Auto-Start on Boot",
                    subtitle = "Automatically start Shizuku when device boots (Root only)",
                    checked = state.autoStartOnBoot,
                    onCheckedChange = viewModel::setAutoStartOnBoot,
                    infoTitle = "Auto-Start on Boot",
                    infoDesc = "On rooted devices, Shizuku can restart automatically after every reboot. This requires root access. Without root, you need to restart Shizuku manually via Wireless Debugging each boot."
                )
                SettingsSwitchRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Show Status Notification",
                    subtitle = "Display persistent notification while Shizuku is running",
                    checked = state.showNotification,
                    onCheckedChange = viewModel::setShowNotification
                )
                SettingsSwitchRow(
                    icon = Icons.Outlined.Lock,
                    title = "Require Unlock for Tiles",
                    subtitle = "Require biometric/PIN before toggling Quick Settings tiles",
                    checked = state.requireUnlockForTiles,
                    onCheckedChange = viewModel::setRequireUnlockForTiles,
                    infoTitle = "Tile Security",
                    infoDesc = "When enabled, Quick Settings tiles (WiFi, Mobile Data, etc.) will require device authentication before executing. Prevents accidental or unauthorized toggles from the lock screen."
                )
            }
        }
    }

    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = viewModel::runDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.BugReport, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run Full Diagnostics")
                }
                OutlinedButton(onClick = viewModel::stopServer, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Shizuku Server")
                }
                OutlinedButton(onClick = viewModel::restartServer, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.RestartAlt, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restart Shizuku Server")
                }
            }
        }
    }
}

private fun LazyListScope.logsTab(state: ShizukuUiState, viewModel: ShizukuViewModel) {
    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Article, null, tint = MaterialTheme.colorScheme.primary)
                Text("Diagnostics Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${state.logs.size} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = viewModel::clearLogs, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.DeleteSweep, "Clear", modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (state.logs.isEmpty()) {
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Article, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("No logs yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Logs appear here as Shizuku events occur. Run diagnostics to populate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        items(state.logs.reversed().take(200)) { entry ->
            val color = when (entry.level) {
                LogLevel.SUCCESS -> AccentGreen
                LogLevel.ERROR -> AccentRed
                LogLevel.WARNING -> AccentOrange
                else -> MaterialTheme.colorScheme.onSurface
            }
            val prefix = when (entry.level) {
                LogLevel.SUCCESS -> "✓"
                LogLevel.ERROR -> "✗"
                LogLevel.WARNING -> "⚠"
                else -> "ℹ"
            }
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp)) {
                Text(
                    "$prefix ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    infoTitle: String? = null,
    infoDesc: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (infoTitle != null && infoDesc != null) {
            InfoTooltipIcon(infoTitle, infoDesc)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ShizukuStatusCard(
    state: ShizukuUiState,
    onRequestPermission: () -> Unit,
    onStartWithAdb: () -> Unit,
    onStartWithRoot: () -> Unit,
    onStop: () -> Unit,
) {
    val statusColor = when {
        state.isAvailable && state.isGranted -> AccentGreen
        state.isAvailable -> AccentOrange
        state.isRootAvailable -> AccentCyan
        else -> AccentRed
    }
    val statusText = when {
        state.isAvailable && state.isGranted -> "Shizuku Running & Authorized"
        state.isAvailable -> "Shizuku Running — Permission Required"
        state.isRootAvailable -> "Root Mode Available"
        state.isInstalled -> "Shizuku Installed — Not Running"
        else -> "Shizuku Not Installed"
    }

    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(0.08f)),
        border = BorderStroke(1.dp, statusColor.copy(0.4f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.isAvailable && state.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    null, tint = statusColor, modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Service Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor, fontWeight = FontWeight.Medium)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = statusColor.copy(0.15f)) {
                    Text(
                        if (state.isAvailable) "ONLINE" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            when {
                !state.isAvailable && !state.isInstalled -> {
                    Text("Install Shizuku from Play Store or F-Droid first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onStartWithAdb, Modifier.weight(1f)) {
                            Icon(Icons.Default.Usb, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("via ADB")
                        }
                        if (state.isRootAvailable) {
                            Button(onClick = onStartWithRoot, Modifier.weight(1f)) {
                                Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("via Root")
                            }
                        }
                    }
                }
                !state.isAvailable -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onStartWithAdb, Modifier.weight(1f)) {
                            Icon(Icons.Default.Usb, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Start via ADB")
                        }
                        if (state.isRootAvailable) {
                            Button(onClick = onStartWithRoot, Modifier.weight(1f)) {
                                Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Start via Root")
                            }
                        }
                    }
                }
                !state.isGranted -> {
                    Button(onClick = onRequestPermission, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Key, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Grant Permission to ACCU")
                    }
                }
                else -> {
                    OutlinedButton(onClick = onStop, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Stop Server")
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupGuideCard() {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.clickable { expanded = !expanded }.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Text("Complete Setup Guide", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Expand")
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Method 1: Wireless ADB (No root, Android 11+)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    listOf(
                        "1" to "Go to Settings → About Phone → tap Build Number 7 times",
                        "2" to "Settings → Developer Options → Enable Developer Options",
                        "3" to "Enable \"Wireless Debugging\" in Developer Options",
                        "4" to "Install Shizuku from Play Store or F-Droid",
                        "5" to "Open Shizuku → tap \"Pairing code\" → note the code",
                        "6" to "In ACCU Shizuku Center → Wireless ADB tab → Pair Device",
                        "7" to "Enter the 6-digit code → tap Pair",
                        "8" to "Shizuku will start — grant ACCU permission when prompted",
                    ).forEach { (num, step) ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(18.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(num, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Method 2: Rooted Device (Auto-start)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    listOf(
                        "1" to "Install Shizuku from Play Store or F-Droid",
                        "2" to "Open Shizuku → tap \"Start via root\"",
                        "3" to "Grant root access when prompted by your root manager",
                        "4" to "Enable \"Auto-Start\" in Shizuku Settings for persistence on reboot",
                        "5" to "Grant ACCU permission when prompted",
                    ).forEach { (num, step) ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(18.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(num, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
