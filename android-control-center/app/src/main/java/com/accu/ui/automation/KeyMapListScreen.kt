package com.accu.ui.automation

import androidx.compose.animation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    val filtered = keyMaps.filter {
        searchQuery.isBlank() || it.trigger.contains(searchQuery, ignoreCase = true) ||
        it.actions.any { a -> a.contains(searchQuery, ignoreCase = true) }
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
                        IconButton(onClick = onNavigateToLog) { Icon(Icons.Default.List, "Event log") }
                        IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings") }
                    }
                )
            }
        },
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
                    TextButton(onClick = {}) { Text("Enable", color = MaterialTheme.colorScheme.onErrorContainer) }
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
}

private fun Modifier.scale(scale: Float) = this.then(Modifier)
