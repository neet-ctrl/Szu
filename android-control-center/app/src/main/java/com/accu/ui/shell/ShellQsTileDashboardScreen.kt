package com.accu.ui.shell

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

// ──────────────────────────────────────────────
//  aShellYou — Shell QS Tile Dashboard
//  Manage custom QS tiles that execute ADB/shell
//  commands on tap, with logs, icons, and editor.
// ──────────────────────────────────────────────

data class ShellQsTile(
    val id: String,
    val label: String,
    val command: String,
    val description: String = "",
    val iconName: String = "terminal",
    val isEnabled: Boolean = true,
    val confirmBeforeRun: Boolean = false,
    val executionCount: Int = 0,
    val lastRun: String = "Never",
    val lastOutput: String = "",
    val isRunning: Boolean = false,
)

data class TileLog(
    val tileId: String,
    val tileLabel: String,
    val timestamp: String,
    val command: String,
    val output: String,
    val exitCode: Int,
)

private val TILE_ICON_OPTIONS = listOf(
    "terminal" to Icons.Default.Terminal,
    "wifi" to Icons.Default.Wifi,
    "flashlight" to Icons.Default.FlashlightOn,
    "volume" to Icons.Default.VolumeUp,
    "battery" to Icons.Default.BatteryChargingFull,
    "screenshot" to Icons.Default.Screenshot,
    "restart" to Icons.Default.RestartAlt,
    "lock" to Icons.Default.Lock,
    "network" to Icons.Default.NetworkCell,
    "bluetooth" to Icons.Default.Bluetooth,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShellQsTileDashboardScreen(onBack: () -> Unit = {}) {
    var tiles by remember {
        mutableStateOf(listOf(
            ShellQsTile("1", "Toggle Wi-Fi", "svc wifi disable && svc wifi enable", "Cycles Wi-Fi off/on to fix connectivity", "wifi", executionCount = 12, lastRun = "2 hr ago", lastOutput = "Done"),
            ShellQsTile("2", "Clear All Notifications", "service call notification 1", "Clears all active notifications", "terminal", executionCount = 8, lastRun = "Yesterday", lastOutput = "Result: Parcel"),
            ShellQsTile("3", "Screenshot", "screencap /sdcard/DCIM/screenshot_$(date +%s).png", "Captures full screen to DCIM", "screenshot", executionCount = 31, lastRun = "5 min ago", lastOutput = "Saved"),
            ShellQsTile("4", "Restart System UI", "am crash com.android.systemui", "Restarts SystemUI process", "restart", true, true, 3, "Last week", ""),
            ShellQsTile("5", "Toggle Airplane", "cmd connectivity airplane-mode enable", "Toggles airplane mode", "network", true, true, 0, "Never", ""),
            ShellQsTile("6", "ADB Secure", "setprop persist.adb.tcp.port -1", "Disables TCP/IP ADB", "lock", executionCount = 2, lastRun = "3 days ago", lastOutput = "Done"),
        ))
    }
    var logs by remember {
        mutableStateOf(listOf(
            TileLog("3", "Screenshot", "14:22:01", "screencap /sdcard/DCIM/screenshot_1717158121.png", "Saved to /sdcard/DCIM/screenshot_1717158121.png", 0),
            TileLog("1", "Toggle Wi-Fi", "12:05:33", "svc wifi disable && svc wifi enable", "Done", 0),
            TileLog("2", "Clear Notifications", "11:44:10", "service call notification 1", "Result: Parcel(00000000 00000001   '........')", 0),
            TileLog("4", "Restart System UI", "2024-05-28 09:00", "am crash com.android.systemui", "Exception: Process crashed", 1),
        ))
    }

    var showCreateSheet by remember { mutableStateOf(false) }
    var editingTile by remember { mutableStateOf<ShellQsTile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<ShellQsTile?>(null) }
    var showLogsForTile by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    val isSelecting = selectedIds.isNotEmpty()

    // Editor state
    var editLabel by remember { mutableStateOf("") }
    var editCommand by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editIconName by remember { mutableStateOf("terminal") }
    var editConfirm by remember { mutableStateOf(false) }
    var editEnabled by remember { mutableStateOf(true) }

    fun openEditor(tile: ShellQsTile? = null) {
        editLabel = tile?.label ?: ""
        editCommand = tile?.command ?: ""
        editDescription = tile?.description ?: ""
        editIconName = tile?.iconName ?: "terminal"
        editConfirm = tile?.confirmBeforeRun ?: false
        editEnabled = tile?.isEnabled ?: true
        editingTile = tile
        showCreateSheet = true
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = if (isSelecting) "${selectedIds.size} selected" else "Shell QS Tiles",
                onBack = { if (isSelecting) selectedIds = emptySet() else onBack() },
                actions = {
                    if (isSelecting) {
                        IconButton(onClick = {
                            tiles = tiles.map { if (it.id in selectedIds) it.copy(isEnabled = true) else it }
                            snackbar = "Enabled ${selectedIds.size} tiles"
                            selectedIds = emptySet()
                        }) { Icon(Icons.Default.PlayArrow, "Enable") }
                        IconButton(onClick = {
                            tiles = tiles.map { if (it.id in selectedIds) it.copy(isEnabled = false) else it }
                            snackbar = "Disabled ${selectedIds.size} tiles"
                            selectedIds = emptySet()
                        }) { Icon(Icons.Default.Pause, "Disable") }
                        IconButton(onClick = {
                            tiles = tiles.filter { it.id !in selectedIds }
                            snackbar = "Deleted ${selectedIds.size} tiles"
                            selectedIds = emptySet()
                        }) { Icon(Icons.Default.Delete, "Delete") }
                        IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, "Cancel") }
                    } else {
                        IconButton(onClick = { snackbar = "Syncing QS tile slots…" }) { Icon(Icons.Default.Sync, "Sync") }
                        IconButton(onClick = { openEditor(null) }) { Icon(Icons.Default.Add, "New Tile") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isSelecting && selectedTab == 0) {
                FloatingActionButton(onClick = { openEditor(null) }) {
                    Icon(Icons.Default.Add, "New Tile")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Info card
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.ViewDay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Custom Shell QS Tiles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${tiles.count { it.isEnabled }}/${tiles.size} tiles active · Tap a tile to run its command · Long-press to select", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                    }
                    TextButton(onClick = { snackbar = "Opening QS panel setup…" }) { Text("Add to QS", style = MaterialTheme.typography.labelSmall) }
                }
            }

            // Tabs: Tiles | Logs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tiles (${tiles.size})") }, icon = { Icon(Icons.Default.ViewDay, null, Modifier.size(16.dp)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Logs (${logs.size})") }, icon = { Icon(Icons.Default.Article, null, Modifier.size(16.dp)) })
            }

            when (selectedTab) {
                0 -> TilesTab(
                    tiles = tiles,
                    selectedIds = selectedIds,
                    isSelecting = isSelecting,
                    onToggle = { tile -> tiles = tiles.map { if (it.id == tile.id) it.copy(isEnabled = !it.isEnabled) else it } },
                    onRun = { tile ->
                        if (tile.confirmBeforeRun) snackbar = "Confirm required for ${tile.label}"
                        else {
                            tiles = tiles.map { if (it.id == tile.id) it.copy(executionCount = it.executionCount + 1, lastRun = "Just now", lastOutput = "Running…") else it }
                            snackbar = "Running: ${tile.command.take(40)}"
                        }
                    },
                    onEdit = { openEditor(it) },
                    onDelete = { showDeleteConfirm = it },
                    onSelectToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                    onLongPress = { id -> selectedIds = selectedIds + id },
                )
                1 -> LogsTab(
                    logs = logs,
                    onClear = { logs = emptyList(); snackbar = "Logs cleared" },
                )
            }
        }
    }

    // ── Create / Edit Sheet ──────────────────────────────────────────────────
    if (showCreateSheet) {
        AlertDialog(
            onDismissRequest = { showCreateSheet = false; editingTile = null },
            title = { Text(if (editingTile == null) "New Shell QS Tile" else "Edit: ${editingTile!!.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editLabel, onValueChange = { editLabel = it },
                        label = { Text("Label*") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    OutlinedTextField(
                        value = editCommand, onValueChange = { editCommand = it },
                        label = { Text("Command*") }, modifier = Modifier.fillMaxWidth().height(90.dp),
                        placeholder = { Text("e.g. svc wifi disable && svc wifi enable") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    OutlinedTextField(
                        value = editDescription, onValueChange = { editDescription = it },
                        label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )

                    // Icon picker
                    Text("Icon", style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TILE_ICON_OPTIONS) { (name, icon) ->
                            FilterChip(
                                selected = editIconName == name,
                                onClick = { editIconName = name },
                                label = { Icon(icon, name, Modifier.size(16.dp)) },
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Confirm before run", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("Show confirmation dialog before executing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = editConfirm, onCheckedChange = { editConfirm = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Switch(checked = editEnabled, onCheckedChange = { editEnabled = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editLabel.isBlank() || editCommand.isBlank()) {
                            snackbar = "Label and command are required"
                            return@Button
                        }
                        if (editingTile == null) {
                            tiles = tiles + ShellQsTile(
                                id = "${System.currentTimeMillis()}",
                                label = editLabel,
                                command = editCommand,
                                description = editDescription,
                                iconName = editIconName,
                                isEnabled = editEnabled,
                                confirmBeforeRun = editConfirm,
                            )
                            snackbar = "Tile '${editLabel}' created"
                        } else {
                            tiles = tiles.map { t ->
                                if (t.id == editingTile!!.id) t.copy(
                                    label = editLabel, command = editCommand, description = editDescription,
                                    iconName = editIconName, confirmBeforeRun = editConfirm, isEnabled = editEnabled,
                                ) else t
                            }
                            snackbar = "Tile '${editLabel}' updated"
                        }
                        showCreateSheet = false; editingTile = null
                    },
                ) { Text(if (editingTile == null) "Create" else "Save") }
            },
            dismissButton = { TextButton(onClick = { showCreateSheet = false; editingTile = null }) { Text("Cancel") } },
        )
    }

    // Delete confirm
    val toDelete = showDeleteConfirm
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete '${toDelete.label}'?") },
            text = { Text("This QS tile and its execution history will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = { tiles = tiles.filter { it.id != toDelete.id }; snackbar = "Tile deleted"; showDeleteConfirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TilesTab(
    tiles: List<ShellQsTile>,
    selectedIds: Set<String>,
    isSelecting: Boolean,
    onToggle: (ShellQsTile) -> Unit,
    onRun: (ShellQsTile) -> Unit,
    onEdit: (ShellQsTile) -> Unit,
    onDelete: (ShellQsTile) -> Unit,
    onSelectToggle: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    if (tiles.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ViewDay, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("No QS tiles yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap + to create a tile that runs any shell command", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tiles, key = { it.id }) { tile ->
            val isSelected = tile.id in selectedIds
            val icon = TILE_ICON_OPTIONS.find { it.first == tile.iconName }?.second ?: Icons.Default.Terminal

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (isSelecting) onSelectToggle(tile.id) else onRun(tile) },
                        onLongClick = { onLongPress(tile.id) },
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        !tile.isEnabled -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (isSelecting) {
                            Checkbox(checked = isSelected, onCheckedChange = { onSelectToggle(tile.id) })
                            Spacer(Modifier.width(6.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (tile.isEnabled) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.outline.copy(0.1f),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = if (tile.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(tile.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!tile.isEnabled) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.outline.copy(0.2f)) {
                                        Text("off", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                if (tile.confirmBeforeRun) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text("confirm", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(tile.command, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        Switch(checked = tile.isEnabled, onCheckedChange = { onToggle(tile) })
                    }

                    // Stats + actions row
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${tile.executionCount}x runs · Last: ${tile.lastRun}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (tile.lastOutput.isNotBlank()) {
                                Text("→ ${tile.lastOutput.take(50)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        if (!isSelecting) {
                            IconButton(onClick = { onEdit(tile) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDelete(tile) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                            if (tile.isEnabled) {
                                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.PlayArrow, "Run now", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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
private fun LogsTab(logs: List<TileLog>, onClear: () -> Unit) {
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No logs yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${logs.size} entries", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClear, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Clear") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(logs, key = { "${it.tileId}_${it.timestamp}" }) { log ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (log.exitCode == 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer.copy(0.3f)
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (log.exitCode == 0) Icons.Default.CheckCircle else Icons.Default.Error,
                                null, tint = if (log.exitCode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(log.tileLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(log.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("$ ${log.command}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (log.output.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(log.output, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (log.exitCode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Text("Exit: ${log.exitCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}
