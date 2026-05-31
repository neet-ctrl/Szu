package com.accu.ui.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ── Models ─────────────────────────────────────────────────────────────────────

data class TileConfig(
    val id: String,
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val requireUnlock: RequireUnlockOption = RequireUnlockOption.FOLLOW,
    val showSSIDEnabled: Boolean = true,
    val longPressAction: TileLongPressAction = TileLongPressAction.OPEN_SETTINGS,
)

// ── Require unlock: 3-way (Follow default / Always / Never) ───────────────────

enum class RequireUnlockOption(val label: String, val description: String) {
    FOLLOW("Follow Default", "Use the global default unlock requirement"),
    YES("Always Require", "Always prompt for biometric/PIN before toggling"),
    NO("Never Require", "Toggle immediately without unlock prompt"),
}

// ── Long-press action per tile ────────────────────────────────────────────────

enum class TileLongPressAction(val label: String) {
    OPEN_SETTINGS("Open System Settings"),
    OPEN_WIFI_SETTINGS("Open Wi-Fi Settings"),
    OPEN_BT_SETTINGS("Open Bluetooth Settings"),
    OPEN_NETWORK_SETTINGS("Open Network Settings"),
    DO_NOTHING("Do Nothing"),
}

// ── Shell method ───────────────────────────────────────────────────────────────

enum class ShellMethod(val label: String, val description: String) {
    SHIZUKU("Shizuku (Recommended)", "Uses Shizuku IPC for fastest, most reliable toggle"),
    ROOT("Root (libsu)", "Direct root shell commands — requires root access"),
    ADB("Wireless ADB", "Commands via wireless ADB — slower but doesn't need root or Shizuku"),
    ACCESSIBILITY("Accessibility Service", "Uses taps/UI automation — slowest, works without any special access"),
}

// ── Tile sync mode ─────────────────────────────────────────────────────────────

enum class TileSyncMode(val label: String, val description: String) {
    REAL_TIME("Real-Time", "Tile updates immediately when connectivity changes (uses background service)"),
    ON_OPEN("On Panel Open", "Tile updates when you open the Quick Settings panel"),
    MANUAL("Manual", "Tile only updates when you tap it"),
}

// ── All tiles ─────────────────────────────────────────────────────────────────

val TILE_CONFIGS = listOf(
    TileConfig("wifi", "Wi-Fi Tile", Icons.Default.Wifi, "Independent Wi-Fi toggle — switches Wi-Fi without touching mobile data"),
    TileConfig("mobile", "Mobile Data Tile", Icons.Default.SignalCellularAlt, "Independent mobile data toggle — no accidental Wi-Fi changes"),
    TileConfig("internet", "Internet Tile (Combined)", Icons.Default.Language, "Combined internet tile like stock Android but with enhanced controls", showSSIDEnabled = true),
    TileConfig("bluetooth", "Bluetooth Tile", Icons.Default.Bluetooth, "Quick bluetooth toggle with device status"),
    TileConfig("nfc", "NFC Tile", Icons.Default.Nfc, "NFC quick toggle"),
    TileConfig("airplane", "Airplane Mode Tile", Icons.Default.AirplanemodeActive, "Airplane mode toggle with auto-reconnect option"),
    TileConfig("hotspot", "Hotspot Tile", Icons.Default.Wifi, "Mobile hotspot toggle"),
)

// ── Screen ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetterInternetTilesSettingsScreen(onBack: () -> Unit) {
    var selectedMethod by remember { mutableStateOf(ShellMethod.SHIZUKU) }
    var globalRequireUnlock by remember { mutableStateOf(RequireUnlockOption.NO) }
    var syncMode by remember { mutableStateOf(TileSyncMode.REAL_TIME) }
    val tileConfigs = remember { mutableStateListOf(*TILE_CONFIGS.toTypedArray()) }
    var expandedTile by remember { mutableStateOf<String?>(null) }
    var showSyncInfo by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Internet Tiles Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showSyncInfo = true }) { Icon(Icons.Default.Info, "Sync Info") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Shell method ───────────────────────────────────────────────
            item {
                Text("Shell Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Choose how tiles execute toggle commands", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(ShellMethod.entries, key = { "method_${it.name}" }) { method ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMethod == method) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    onClick = { selectedMethod = method },
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedMethod == method, onClick = { selectedMethod = method })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(method.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (method == ShellMethod.SHIZUKU) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text("Fastest", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // ── Tile sync mode ─────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Tile Sync Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Controls how often tile state is refreshed from the system", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(TileSyncMode.entries, key = { "syncmode_${it.name}" }) { mode ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (syncMode == mode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    onClick = { syncMode = mode },
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = syncMode == mode, onClick = { syncMode = mode })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (mode == TileSyncMode.REAL_TIME) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("Recommended", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // ── Global unlock setting ──────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Global Require Unlock Default", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Default unlock requirement for tiles — individual tiles can override this", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        RequireUnlockOption.entries.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { globalRequireUnlock = option },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = globalRequireUnlock == option, onClick = { globalRequireUnlock = option })
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(option.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (option != RequireUnlockOption.entries.last()) HorizontalDivider()
                        }
                    }
                }
            }

            // ── Per-tile settings ──────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Per-Tile Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Override settings for individual tiles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(tileConfigs, key = { it.id }) { tile ->
                val idx = tileConfigs.indexOfFirst { it.id == tile.id }
                TileSettingsCard(
                    tile = tile,
                    globalDefault = globalRequireUnlock,
                    isExpanded = expandedTile == tile.id,
                    onToggleExpand = { expandedTile = if (expandedTile == tile.id) null else tile.id },
                    onRequireUnlockChange = { if (idx != -1) tileConfigs[idx] = tile.copy(requireUnlock = it) },
                    onShowSSIDChange = { if (idx != -1) tileConfigs[idx] = tile.copy(showSSIDEnabled = it) },
                    onLongPressChange = { if (idx != -1) tileConfigs[idx] = tile.copy(longPressAction = it) },
                )
            }

            // ── How to add ─────────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("How to Add Tiles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        InfoStep(1, "Swipe down twice to open Quick Settings fully")
                        InfoStep(2, "Tap the edit (pencil) icon")
                        InfoStep(3, "Scroll down to find ACCU tiles")
                        InfoStep(4, "Drag tiles to your active area")
                        InfoStep(5, "Long-press any tile to configure it")
                    }
                }
            }
        }
    }

    // ── Sync info dialog ───────────────────────────────────────────────────
    if (showSyncInfo) {
        AlertDialog(
            onDismissRequest = { showSyncInfo = false },
            icon = { Icon(Icons.Default.Sync, null) },
            title = { Text("Tile Sync Service") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ACCU maintains a TileSyncService that monitors network state changes and updates Quick Settings tiles in real-time.", style = MaterialTheme.typography.bodySmall)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Service monitors:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("• Wi-Fi state changes (connected/disconnected/SSID)\n• Mobile data state (on/off/type)\n• Airplane mode toggle\n• Bluetooth state\n• NFC state", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("The service runs as a foreground service when at least one tile is active. Battery impact is minimal (uses registered broadcast receivers).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { showSyncInfo = false }) { Text("Got it") } },
        )
    }
}

// ── Per-tile settings card ─────────────────────────────────────────────────────

@Composable
private fun TileSettingsCard(
    tile: TileConfig,
    globalDefault: RequireUnlockOption,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRequireUnlockChange: (RequireUnlockOption) -> Unit,
    onShowSSIDChange: (Boolean) -> Unit,
    onLongPressChange: (TileLongPressAction) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
            ) {
                Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(tile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // ── Require unlock (3-way) ─────────────────────────────
                    Text("Require Device Unlock", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Override the global default (${globalDefault.label})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                    RequireUnlockOption.entries.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onRequireUnlockChange(option) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = tile.requireUnlock == option, onClick = { onRequireUnlockChange(option) }, modifier = Modifier.size(36.dp))
                            Column(Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(option.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // ── Show SSID (Wi-Fi tiles) ────────────────────────────
                    if (tile.id == "wifi" || tile.id == "internet") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        ListItem(
                            headlineContent = { Text("Show Wi-Fi SSID") },
                            supportingContent = { Text("Display current network name in tile subtitle") },
                            leadingContent = { Icon(Icons.Default.Wifi, null) },
                            trailingContent = { Switch(checked = tile.showSSIDEnabled, onCheckedChange = onShowSSIDChange) },
                        )
                    }

                    // ── Long-press action ──────────────────────────────────
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text("Long-Press Action", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(4.dp))
                    var showLongPressMenu by remember { mutableStateOf(false) }
                    OutlinedCard(
                        onClick = { showLongPressMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(tile.longPressAction.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(showLongPressMenu, { showLongPressMenu = false }) {
                        TileLongPressAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                leadingIcon = { if (tile.longPressAction == action) Icon(Icons.Default.Check, null) },
                                onClick = { onLongPressChange(action); showLongPressMenu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStep(step: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.secondary, shape = androidx.compose.foundation.shape.CircleShape) {
            Text(
                "$step",
                modifier = Modifier.size(24.dp).wrapContentSize(Alignment.Center),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}


private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = calculateTopPadding() + other.calculateTopPadding(),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)
