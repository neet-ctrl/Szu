package com.accu.ui.automation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class KeyMapItem(
    val id: String,
    val trigger: String,
    val actions: List<String>,
    val constraints: List<String>,
    val isEnabled: Boolean,
    val triggerIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val hasError: Boolean = false,
    val errorMsg: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyMapListScreen(
    onBack: () -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    onNavigateToEdit: (String) -> Unit = {},
    onNavigateToLog: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val sampleMaps = remember {
        listOf(
            KeyMapItem("1", "Vol Down (500ms hold)", listOf("Toggle flashlight"), emptyList(), true, Icons.Default.VolumeDown),
            KeyMapItem("2", "Vol Up + Vol Down (press)", listOf("Screenshot", "Vibrate 50ms"), listOf("Screen on"), false, Icons.Default.VolumeUp),
            KeyMapItem("3", "Power Button (double-press)", listOf("Open Camera"), listOf("Screen off"), true, Icons.Default.PowerSettingsNew),
            KeyMapItem("4", "Vol Down + Power (hold)", listOf("Toggle DND"), emptyList(), true, Icons.Default.DoNotDisturb),
            KeyMapItem("5", "Headset Button (triple-press)", listOf("Play/Pause media"), listOf("Headset connected"), true, Icons.Default.Headset),
            KeyMapItem("6", "Floating Button Tap", listOf("Toggle Wi-Fi"), emptyList(), false, Icons.Default.TouchApp, true, "Accessibility service not running"),
            KeyMapItem("7", "Assistant Button (press)", listOf("Open Chrome", "Vibrate 30ms"), listOf("Screen on", "App: Launcher"), true, Icons.Default.Assistant),
        )
    }
    var keyMaps by remember { mutableStateOf(sampleMaps) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelecting = selectedIds.isNotEmpty()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortSheet by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("trigger") } // trigger | enabled | error
    var sortAsc by remember { mutableStateOf(true) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showBugReportDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // SAF launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                val json = buildString {
                    appendLine("""{"version":1,"keyMaps":[""")
                    keyMaps.forEachIndexed { i, km ->
                        val actions = km.actions.joinToString(",") { a -> "\"$a\"" }
                        val constraints = km.constraints.joinToString(",") { c -> "\"$c\"" }
                        append("""  {"id":"${km.id}","trigger":"${km.trigger}","actions":[$actions],"constraints":[$constraints],"enabled":${km.isEnabled}}""")
                        if (i < keyMaps.lastIndex) appendLine(",") else appendLine()
                    }
                    append("]}")
                }
                context.contentResolver.openOutputStream(uri)?.use { out -> out.write(json.toByteArray()) }
                snackbarMessage = "Exported ${keyMaps.size} key maps"
            } catch (e: Exception) {
                snackbarMessage = "Export failed: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@let
                val parsed = mutableListOf<KeyMapItem>()
                Regex("""\{[^{}]*"trigger"[^{}]*\}""").findAll(json).forEach { m ->
                    val obj = m.value
                    fun String.jStr(key: String) = Regex(""""$key"\s*:\s*"([^"]+)"""").find(this)?.groupValues?.getOrNull(1)
                    fun String.jBool(key: String) = Regex(""""$key"\s*:\s*(true|false)""").find(this)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
                    fun String.jList(key: String) = Regex(""""$key"\s*:\s*\[([^\]]*)\]""").find(this)?.groupValues?.getOrNull(1)
                        ?.let { content -> Regex(""""([^"]+)"""").findAll(content).map { r -> r.groupValues[1] }.toList() } ?: emptyList()
                    val id = obj.jStr("id") ?: "km_${System.currentTimeMillis()}"
                    val trigger = obj.jStr("trigger") ?: return@forEach
                    val actions = obj.jList("actions")
                    val constraints = obj.jList("constraints")
                    val enabled = obj.jBool("enabled") ?: true
                    parsed.add(KeyMapItem(id = id, trigger = trigger, actions = actions, constraints = constraints, isEnabled = enabled, triggerIcon = androidx.compose.material.icons.Icons.Default.Key))
                }
                if (parsed.isNotEmpty()) {
                    keyMaps = keyMaps + parsed
                    snackbarMessage = "Imported ${parsed.size} key maps"
                } else {
                    snackbarMessage = "No valid key maps found in file"
                }
            } catch (e: Exception) {
                snackbarMessage = "Import failed: ${e.message}"
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it); snackbarMessage = null }
    }

    val filtered = keyMaps
        .filter {
            searchQuery.isBlank() || it.trigger.contains(searchQuery, ignoreCase = true) ||
            it.actions.any { a -> a.contains(searchQuery, ignoreCase = true) }
        }
        .let { list ->
            when (sortBy) {
                "enabled" -> if (sortAsc) list.sortedByDescending { it.isEnabled } else list.sortedBy { it.isEnabled }
                "error" -> list.sortedByDescending { it.hasError }
                else -> if (sortAsc) list.sortedBy { it.trigger } else list.sortedByDescending { it.trigger }
            }
        }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${selectedIds.size} key map(s)?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    keyMaps = keyMaps.filter { it.id !in selectedIds }
                    selectedIds = emptySet()
                    showDeleteDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            if (isSelecting) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, "Cancel") }
                    },
                    actions = {
                        IconButton(onClick = {
                            keyMaps = keyMaps.map { if (it.id in selectedIds) it.copy(isEnabled = true) else it }
                            selectedIds = emptySet()
                        }) { Icon(Icons.Default.CheckCircle, "Enable all") }
                        IconButton(onClick = {
                            keyMaps = keyMaps.map { if (it.id in selectedIds) it.copy(isEnabled = false) else it }
                            selectedIds = emptySet()
                        }) { Icon(Icons.Default.Cancel, "Disable all") }
                        IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                )
            } else {
                ACCTopBar(
                    title = "Key Maps",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showSortSheet = true }) { Icon(Icons.Default.Sort, "Sort") }
                        IconButton(onClick = onNavigateToLog) { Icon(Icons.Default.List, "Event log") }
                        IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings") }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(showMenu, { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Export all key maps") }, leadingIcon = { Icon(Icons.Default.IosShare, null) }, onClick = { showExportDialog = true; showMenu = false })
                                DropdownMenuItem(text = { Text("Import key maps") }, leadingIcon = { Icon(Icons.Default.FileOpen, null) }, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")); showMenu = false })
                                DropdownMenuItem(text = { Text("Report a bug") }, leadingIcon = { Icon(Icons.Default.BugReport, null) }, onClick = { showBugReportDialog = true; showMenu = false })
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Key Map") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Accessibility service warning
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Accessibility Service", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                        Text("Enable Key Mapper accessibility service to activate key maps.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    val ctx = LocalContext.current
                    TextButton(onClick = { ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }) { Text("Enable", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search key maps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            // Stats row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val enabled = keyMaps.count { it.isEnabled }
                SuggestionChip(onClick = {}, label = { Text("${keyMaps.size} total") })
                SuggestionChip(onClick = {}, label = { Text("$enabled enabled") })
                SuggestionChip(onClick = {}, label = { Text("${keyMaps.size - enabled} disabled") })
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(filtered, key = { it.id }) { km ->
                    val isSelected = km.id in selectedIds
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    if (isSelecting) selectedIds = if (isSelected) selectedIds - km.id else selectedIds + km.id
                                    else onNavigateToEdit(km.id)
                                },
                                onLongClick = { selectedIds = selectedIds + km.id }
                            ),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                !km.isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelecting) {
                                    Checkbox(checked = isSelected, onCheckedChange = { c -> selectedIds = if (c) selectedIds + km.id else selectedIds - km.id })
                                } else {
                                    Icon(km.triggerIcon, null, modifier = Modifier.size(20.dp), tint = if (km.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(km.trigger, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(km.actions.joinToString(" → "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Switch(
                                    checked = km.isEnabled,
                                    onCheckedChange = { keyMaps = keyMaps.map { m -> if (m.id == km.id) m.copy(isEnabled = it) else m } },
                                    modifier = Modifier.scale(0.8f)
                                )
                                if (!isSelecting) {
                                    var showKmMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { showKmMenu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(16.dp)) }
                                        DropdownMenu(showKmMenu, { showKmMenu = false }) {
                                            DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                                                val dup = km.copy(id = "${km.id}_dup_${System.currentTimeMillis()}", trigger = "${km.trigger} (copy)")
                                                keyMaps = keyMaps + dup
                                                showKmMenu = false
                                            })
                                            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { onNavigateToEdit(km.id); showKmMenu = false })
                                            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { keyMaps = keyMaps.filter { it.id != km.id }; showKmMenu = false })
                                        }
                                    }
                                }
                            }
                            if (km.constraints.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    km.constraints.take(3).forEach { c ->
                                        SuggestionChip(onClick = {}, label = { Text(c, fontSize = 10.sp) }, modifier = Modifier.height(22.dp))
                                    }
                                }
                            }
                            if (km.hasError) {
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(4.dp))
                                    Text(km.errorMsg, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Sort bottom sheet
    if (showSortSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Sort Key Maps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Sort by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sortBy == "trigger", onClick = { sortBy = "trigger" }, label = { Text("Trigger") })
                    FilterChip(selected = sortBy == "enabled", onClick = { sortBy = "enabled" }, label = { Text("Status") })
                    FilterChip(selected = sortBy == "error", onClick = { sortBy = "error" }, label = { Text("Errors first") })
                }
                Text("Order", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sortAsc, onClick = { sortAsc = true }, label = { Text("Ascending") })
                    FilterChip(selected = !sortAsc, onClick = { sortAsc = false }, label = { Text("Descending") })
                }
                Button(onClick = { showSortSheet = false }, Modifier.fillMaxWidth()) { Text("Apply") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Export dialog — use SAF to choose save location
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = { Icon(Icons.Default.IosShare, null) },
            title = { Text("Export Key Maps") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${keyMaps.size} key maps will be exported as a JSON file. Tap Export to choose where to save it.", style = MaterialTheme.typography.bodySmall)
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Android file picker will open to choose location", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    exportLauncher.launch("keymapper_export_${System.currentTimeMillis()}.json")
                    showExportDialog = false
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } },
        )
    }

    // Bug report dialog
    if (showBugReportDialog) {
        var bugDescription by remember { mutableStateOf("") }
        var includeLog by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showBugReportDialog = false },
            icon = { Icon(Icons.Default.BugReport, null) },
            title = { Text("Report a Bug") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = bugDescription,
                        onValueChange = { bugDescription = it },
                        label = { Text("Describe the issue") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 5,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Include key map log", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("Helps diagnose the problem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = includeLog, onCheckedChange = { includeLog = it })
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Report will be opened on GitHub Issues", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showBugReportDialog = false }, enabled = bugDescription.isNotBlank()) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { showBugReportDialog = false }) { Text("Cancel") } },
        )
    }
} // end KeyMapListScreen

private fun Modifier.scale(scale: Float) = this.then(Modifier)
