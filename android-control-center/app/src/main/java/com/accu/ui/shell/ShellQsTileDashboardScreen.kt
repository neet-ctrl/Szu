package com.accu.ui.shell

import android.content.Context
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.ACCTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

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

// ──────────────────────────────────────────────────────────────────
//  ViewModel — real ADB execution + SharedPrefs persistence
// ──────────────────────────────────────────────────────────────────

private const val PREF_TILES = "qs_tiles_prefs"
private const val KEY_TILES  = "tiles_json"
private const val KEY_LOGS   = "logs_json"
private const val MAX_LOGS   = 200

data class QsTilesUiState(
    val tiles: List<ShellQsTile> = emptyList(),
    val logs:  List<TileLog>    = emptyList(),
)

@HiltViewModel
class ShellQsTileDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREF_TILES, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(QsTilesUiState())
    val state: StateFlow<QsTilesUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        val tiles = parseTiles(prefs.getString(KEY_TILES, "[]") ?: "[]")
        val logs  = parseLogs(prefs.getString(KEY_LOGS,  "[]") ?: "[]")
        _state.update { it.copy(tiles = tiles, logs = logs) }
    }

    fun runTile(tile: ShellQsTile) {
        if (!tile.isEnabled) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { s -> s.copy(tiles = s.tiles.map { if (it.id == tile.id) it.copy(isRunning = true) else it }) }
            val result = try { connectionManager.exec(tile.command) } catch (e: Exception) {
                com.accu.connection.ShellResult(output = "", error = e.message ?: "Error", exitCode = 1)
            }
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val log = TileLog(
                tileId    = tile.id, tileLabel = tile.label, timestamp = ts,
                command   = tile.command,
                output    = result.output.trim().take(500).ifBlank { result.error.take(200) },
                exitCode  = if (result.isSuccess) 0 else 1,
            )
            _state.update { s ->
                val updated = s.tiles.map { t ->
                    if (t.id == tile.id) t.copy(
                        isRunning      = false,
                        executionCount = t.executionCount + 1,
                        lastRun        = ts,
                        lastOutput     = log.output.take(80),
                    ) else t
                }
                val newLogs = (listOf(log) + s.logs).take(MAX_LOGS)
                s.copy(tiles = updated, logs = newLogs)
            }
            persistAll()
        }
    }

    fun addTile(tile: ShellQsTile) { _state.update { it.copy(tiles = it.tiles + tile) }; persistAll() }
    fun updateTile(tile: ShellQsTile) { _state.update { it.copy(tiles = it.tiles.map { t -> if (t.id == tile.id) tile else t }) }; persistAll() }
    fun deleteTile(id: String) { _state.update { it.copy(tiles = it.tiles.filter { t -> t.id != id }) }; persistAll() }
    fun enableTiles(ids: Set<String>) { _state.update { it.copy(tiles = it.tiles.map { t -> if (t.id in ids) t.copy(isEnabled = true)  else t }) }; persistAll() }
    fun disableTiles(ids: Set<String>) { _state.update { it.copy(tiles = it.tiles.map { t -> if (t.id in ids) t.copy(isEnabled = false) else t }) }; persistAll() }
    fun clearLogs() { _state.update { it.copy(logs = emptyList()) }; prefs.edit().putString(KEY_LOGS, "[]").apply() }
    fun toggleEnabled(id: String) { _state.update { it.copy(tiles = it.tiles.map { t -> if (t.id == id) t.copy(isEnabled = !t.isEnabled) else t }) }; persistAll() }

    private fun persistAll() {
        prefs.edit()
            .putString(KEY_TILES, serializeTiles(_state.value.tiles))
            .putString(KEY_LOGS,  serializeLogs(_state.value.logs))
            .apply()
    }

    // ── Serialization ──────────────────────────────────────────────────────
    private fun serializeTiles(tiles: List<ShellQsTile>) = JSONArray().apply {
        tiles.forEach { t ->
            put(JSONObject().apply {
                put("id", t.id); put("label", t.label); put("command", t.command)
                put("description", t.description); put("iconName", t.iconName)
                put("isEnabled", t.isEnabled); put("confirmBeforeRun", t.confirmBeforeRun)
                put("executionCount", t.executionCount); put("lastRun", t.lastRun)
                put("lastOutput", t.lastOutput)
            })
        }
    }.toString()

    private fun serializeLogs(logs: List<TileLog>) = JSONArray().apply {
        logs.forEach { l ->
            put(JSONObject().apply {
                put("tileId", l.tileId); put("tileLabel", l.tileLabel)
                put("timestamp", l.timestamp); put("command", l.command)
                put("output", l.output); put("exitCode", l.exitCode)
            })
        }
    }.toString()

    private fun parseTiles(json: String): List<ShellQsTile> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ShellQsTile(
                id = o.getString("id"), label = o.getString("label"), command = o.getString("command"),
                description = o.optString("description"), iconName = o.optString("iconName", "terminal"),
                isEnabled = o.optBoolean("isEnabled", true), confirmBeforeRun = o.optBoolean("confirmBeforeRun", false),
                executionCount = o.optInt("executionCount", 0), lastRun = o.optString("lastRun", "Never"),
                lastOutput = o.optString("lastOutput"),
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun parseLogs(json: String): List<TileLog> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TileLog(o.getString("tileId"), o.getString("tileLabel"), o.getString("timestamp"),
                    o.getString("command"), o.optString("output"), o.optInt("exitCode", 0))
        }
    } catch (_: Exception) { emptyList() }
}

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
fun ShellQsTileDashboardScreen(
    onBack: () -> Unit = {},
    viewModel: ShellQsTileDashboardViewModel = hiltViewModel(),
) {
    val vmState by viewModel.state.collectAsStateWithLifecycle()
    val tiles = vmState.tiles
    val logs  = vmState.logs

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
                            viewModel.enableTiles(selectedIds)
                            snackbar = "Enabled ${selectedIds.size} tiles"
                            selectedIds = emptySet()
                        }) { Icon(Icons.Default.PlayArrow, "Enable") }
                        IconButton(onClick = {
                            viewModel.disableTiles(selectedIds)
                            snackbar = "Disabled ${selectedIds.size} tiles"
                            selectedIds = emptySet()
                        }) { Icon(Icons.Default.Pause, "Disable") }
                        IconButton(onClick = {
                            val count = selectedIds.size
                            selectedIds.forEach { viewModel.deleteTile(it) }
                            snackbar = "Deleted $count tiles"
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
                    onToggle = { tile -> viewModel.toggleEnabled(tile.id) },
                    onRun = { tile ->
                        if (tile.confirmBeforeRun) snackbar = "Confirm required for ${tile.label}"
                        else {
                            snackbar = "Running: ${tile.command.take(40)}"
                            viewModel.runTile(tile)
                        }
                    },
                    onEdit = { openEditor(it) },
                    onDelete = { showDeleteConfirm = it },
                    onSelectToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                    onLongPress = { id -> selectedIds = selectedIds + id },
                )
                1 -> LogsTab(
                    logs = logs,
                    onClear = { viewModel.clearLogs(); snackbar = "Logs cleared" },
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
                            viewModel.addTile(ShellQsTile(
                                id = "${System.currentTimeMillis()}",
                                label = editLabel,
                                command = editCommand,
                                description = editDescription,
                                iconName = editIconName,
                                isEnabled = editEnabled,
                                confirmBeforeRun = editConfirm,
                            ))
                            snackbar = "Tile '${editLabel}' created"
                        } else {
                            viewModel.updateTile(editingTile!!.copy(
                                label = editLabel, command = editCommand, description = editDescription,
                                iconName = editIconName, confirmBeforeRun = editConfirm, isEnabled = editEnabled,
                            ))
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
                    onClick = { viewModel.deleteTile(toDelete.id); snackbar = "Tile deleted"; showDeleteConfirm = null },
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
                                IconButton(onClick = { onRun(tile) }, modifier = Modifier.size(32.dp)) {
                                    if (tile.isRunning)
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else
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
