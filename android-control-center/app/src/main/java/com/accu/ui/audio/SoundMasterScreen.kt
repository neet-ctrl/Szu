package com.accu.ui.audio

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.audio.*
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.theme.AccentGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundMasterScreen(
    onBack: () -> Unit,
    viewModel: SoundMasterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.clearSnackbar() }
    }

    var showAddAppSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPresetSheet by remember { mutableStateOf<SoundMasterEntry?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Sound Master",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Sound Master",
                        description = "Per-app volume control with advanced EQ.\n\n" +
                            "• Control each app's volume independently (0–200%)\n" +
                            "• Stereo balance control per app\n" +
                            "• 3-band EQ: Lows / Mids / Highs\n" +
                            "• Route audio to any output device\n" +
                            "• Built-in presets (Flat, Bass, Vocal…)\n" +
                            "• Volume boost: >100% uses LoudnessEnhancer\n\n" +
                            "Uses Android AudioPlaybackCapture API via ACCU privilege. " +
                            "No Shizuku required."
                    )
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SoundMasterSort.values().forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = { viewModel.onSortChanged(sort); showSortMenu = false },
                                    leadingIcon = {
                                        if (state.sortBy == sort) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.loadInstalledApps()
                    showAddAppSheet = true
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add App") },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── Control Card ──
            ServiceControlCard(
                isRunning  = state.isControlActive,
                entryCount = state.entries.size,
                connectionStatus = state.connectionStatus,
                onStart    = { viewModel.activateControl() },
                onStop     = { viewModel.deactivateControl() },
            )

            // ── Search + Quick Actions ──
            SearchAndActionsBar(
                query = state.filterQuery,
                onQueryChange = viewModel::onFilterChanged,
                onMuteAll = { viewModel.muteAll() },
                onResetAllEq = { viewModel.resetAllEq() },
            )

            if (state.entries.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val filtered = viewModel.filteredEntries()
                    if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                Text("No apps match filter", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(filtered, key = { "${it.pkg}:${it.outputDeviceId}" }) { entry ->
                            SoundMasterEntryCard(
                                entry            = entry,
                                isControlActive  = state.isControlActive,
                                onVolumeChange   = { viewModel.updateVolume(entry, it) },
                                onBalanceChange = { viewModel.updateBalance(entry, it) },
                                onEqChange = { band, v -> viewModel.updateEqBand(entry, band, v) },
                                onRemove = { viewModel.removeEntry(entry) },
                                onReset = { viewModel.resetEntry(entry) },
                                onPreset = { showPresetSheet = entry },
                                onToggleLock = { viewModel.toggleLocked(entry) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Add App Bottom Sheet ──
    if (showAddAppSheet) {
        AddAppBottomSheet(
            apps            = state.availableApps,
            existingEntries = state.entries,
            isLoading       = state.isLoadingApps,
            onAdd           = { pkg -> viewModel.addEntry(pkg, pkg.split(".").last()) },
            onDismiss       = { showAddAppSheet = false },
        )
    }

    // ── Preset Sheet ──
    showPresetSheet?.let { entry ->
        PresetBottomSheet(
            entry = entry,
            presets = state.presets,
            onApply = { preset -> viewModel.applyPreset(entry, preset); showPresetSheet = null },
            onDismiss = { showPresetSheet = null },
        )
    }

    // ── Settings Sheet ──
    if (showSettingsSheet) {
        SoundMasterSettingsSheet(
            showNotification = state.showNotification,
            showOnVolumeChange = state.showOnVolumeChange,
            autoHide = state.autoHide,
            onSave = { n, v, a -> viewModel.saveSettings(n, v, a); showSettingsSheet = false },
            onDismiss = { showSettingsSheet = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServiceControlCard(
    isRunning: Boolean,
    entryCount: Int,
    connectionStatus: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isConnected = connectionStatus.startsWith("CONNECTED")
    val statusColor = when {
        isRunning   -> AccentGreen
        isConnected -> MaterialTheme.colorScheme.primary
        else        -> MaterialTheme.colorScheme.error
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(0.1f)),
        shape  = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isRunning   -> "Sound Master Active"
                            isConnected -> "Sound Master Ready"
                            else        -> "Not Connected"
                        },
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        isRunning   -> "Controlling $entryCount app${if (entryCount != 1) "s" else ""} on target device ($connectionStatus)"
                        isConnected -> "Add apps below, then tap Activate — all commands run on target device"
                        else        -> "Connect via ACCU Center first (Root / Wireless ADB / OTG)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(
                onClick  = { if (isRunning) onStop() else onStart() },
                enabled  = isConnected || isRunning,
                colors   = ButtonDefaults.filledTonalButtonColors(
                    containerColor = statusColor.copy(0.2f),
                    contentColor   = statusColor,
                ),
            ) {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    null,
                    Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isRunning) "Stop" else "Activate")
            }
        }
    }
}

@Composable
private fun SearchAndActionsBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMuteAll: () -> Unit,
    onResetAllEq: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search apps…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onMuteAll,
                label = { Text("Mute All", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.VolumeOff, null, Modifier.size(14.dp)) },
            )
            AssistChip(
                onClick = onResetAllEq,
                label = { Text("Reset EQ", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(14.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundMasterEntryCard(
    entry: SoundMasterEntry,
    isControlActive: Boolean,
    onVolumeChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit,
    onEqChange: (Int, Float) -> Unit,
    onRemove: () -> Unit,
    onReset: () -> Unit,
    onPreset: () -> Unit,
    onToggleLock: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val appLabel = remember(entry.pkg) { entry.pkg.split(".").last().replaceFirstChar { it.uppercase() } }

    // Local slider state — updates instantly during drag; syncs from external changes (preset, mute-all, reset)
    var localVolume  by remember(entry.volume)   { mutableFloatStateOf(entry.volume) }
    var localBalance by remember(entry.balance)  { mutableFloatStateOf(entry.balance) }
    var localEqLow   by remember(entry.eqLow)   { mutableFloatStateOf(entry.eqLow) }
    var localEqMid   by remember(entry.eqMid)   { mutableFloatStateOf(entry.eqMid) }
    var localEqHigh  by remember(entry.eqHigh)  { mutableFloatStateOf(entry.eqHigh) }

    val isBoost = localVolume > 100f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(if (expanded) 6.dp else 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            // ── Header ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (isBoost) {
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                Text("BOOST", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (entry.locked) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isControlActive) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(AccentGreen)
                            )
                        }
                    }
                    Text(
                        entry.pkg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (isControlActive) "● Active on target device" else "○ Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isControlActive) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPreset, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Tune, "Presets", Modifier.size(18.dp))
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        Modifier.size(18.dp),
                    )
                }
            }

            // ── Volume Slider ──
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = localVolume,
                    onValueChange = { if (!entry.locked) localVolume = it },
                    onValueChangeFinished = { if (!entry.locked) onVolumeChange(localVolume) },
                    valueRange = 0f..200f,
                    modifier = Modifier.weight(1f),
                    enabled = !entry.locked,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${"%.0f".format(localVolume)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isBoost) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(44.dp),
                )
            }

            // ── Expanded Controls ──
            AnimatedVisibility(visible = expanded) {
                Column {
                    Divider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Balance
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("L", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(14.dp))
                        Slider(
                            value = localBalance,
                            onValueChange = { if (!entry.locked) localBalance = it },
                            onValueChangeFinished = { if (!entry.locked) onBalanceChange(localBalance) },
                            valueRange = -100f..100f,
                            modifier = Modifier.weight(1f),
                            enabled = !entry.locked,
                        )
                        Text("R", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                    Text("Balance: ${"%.0f".format(localBalance)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                    Spacer(Modifier.height(8.dp))

                    // EQ Bands
                    Text("Equalizer", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data class EqBand(val label: String, val local: Float, val setLocal: (Float) -> Unit, val idx: Int)
                        listOf(
                            EqBand("Lows",  localEqLow,  { localEqLow  = it }, 0),
                            EqBand("Mids",  localEqMid,  { localEqMid  = it }, 1),
                            EqBand("Highs", localEqHigh, { localEqHigh = it }, 2),
                        ).forEach { band ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${if (band.local >= 50) "+" else ""}${"%.0f".format(band.local - 50)}dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = if (band.local != 50f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = band.local,
                                    onValueChange = { if (!entry.locked) band.setLocal(it) },
                                    onValueChangeFinished = { if (!entry.locked) onEqChange(band.idx, band.local) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.height(80.dp).width(32.dp),
                                    enabled = !entry.locked,
                                )
                                Text(band.label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))

                    // Action Row
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onToggleLock, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                            Icon(if (entry.locked) Icons.Default.LockOpen else Icons.Default.Lock, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (entry.locked) "Unlock" else "Lock", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onRemove,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppBottomSheet(
    apps: List<Pair<String, String>>,
    existingEntries: List<SoundMasterEntry>,
    isLoading: Boolean,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(
                "Add App (from target device)",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = search,
                onValueChange = { search = it },
                placeholder   = { Text("Search apps…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (apps.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                    Text(
                        "No apps loaded — tap the + button to refresh from target",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val filtered = apps.filter { (pkg, name) ->
                    search.isBlank() || name.contains(search, true) || pkg.contains(search, true)
                }
                LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
                    items(filtered) { (pkg, name) ->
                        val alreadyAdded = existingEntries.any { it.pkg == pkg }
                        ListItem(
                            headlineContent   = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent   = {
                                if (alreadyAdded) {
                                    Text("Added", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                                } else if (selectedPkg == pkg) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable(enabled = !alreadyAdded) {
                                selectedPkg = if (selectedPkg == pkg) null else pkg
                            },
                            colors = if (selectedPkg == pkg)
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f))
                            else
                                ListItemDefaults.colors(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = { selectedPkg?.let { pkg -> onAdd(pkg); onDismiss() } },
                enabled  = selectedPkg != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add to Sound Master")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetBottomSheet(
    entry: SoundMasterEntry,
    presets: List<SoundMasterPreset>,
    onApply: (SoundMasterPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("Presets — ${entry.pkg.split(".").last()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            presets.forEach { preset ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    onClick = { onApply(preset) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Vol: ${"%.0f".format(preset.volume)}% · Lows: ${"%.0f".format(preset.eqLow - 50)}dB · Mids: ${"%.0f".format(preset.eqMid - 50)}dB · Highs: ${"%.0f".format(preset.eqHigh - 50)}dB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundMasterSettingsSheet(
    showNotification: Boolean,
    showOnVolumeChange: Boolean,
    autoHide: Boolean,
    onSave: (Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var notif by remember { mutableStateOf(showNotification) }
    var volChange by remember { mutableStateOf(showOnVolumeChange) }
    var hide by remember { mutableStateOf(autoHide) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("Sound Master Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            SettingsSwitch("Show notification", "Display a persistent notification while active", notif) { notif = it }
            SettingsSwitch("Show on volume change", "Open Sound Master when system volume changes", volChange) { volChange = it }
            SettingsSwitch("Auto-hide after 3s", "Automatically close the controls after inactivity", hide) { hide = it }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(notif, volChange, hide) }, Modifier.fillMaxWidth()) {
                Text("Save Settings")
            }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VolumeUp, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            Spacer(Modifier.height(16.dp))
            Text("No apps added yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap ＋ Add App to control per-app volume, EQ, and output routing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
