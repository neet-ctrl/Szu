package com.accu.ui.audio

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.components.*
import com.accu.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCenterScreen(
    onBack: () -> Unit,
    onNavigateToGraphicEQ: () -> Unit = {},
    onNavigateToConvolution: () -> Unit = {},
    onNavigateToDSPControls: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLiveprogParams: () -> Unit = {},
    onNavigateToParametricEQ: () -> Unit = {},
    onNavigateToAutoEQ: () -> Unit = {},
    viewModel: AudioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    val irFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.lastPathSegment?.let { viewModel.updateConvolverIrPath(it) }
        }
    }
    var showAutoEqPicker by remember { mutableStateOf(false) }
    val autoEqProfiles = listOf(
        "Harman In-Ear 2019", "Harman Over-Ear 2018", "JBL Tune 760NC",
        "Sony WH-1000XM4", "Sennheiser HD 650", "Bose QuietComfort 45",
    )
    if (showAutoEqPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAutoEqPicker = false }) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AutoEQ Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    autoEqProfiles.forEach { profile ->
                        TextButton(
                            onClick = { viewModel.updateAutoEqProfile(profile); showAutoEqPicker = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(profile) }
                    }
                    TextButton(onClick = { showAutoEqPicker = false }, Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Audio Center",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Audio Center — RootlessJamesDSP",
                        description = "Rootless system-wide audio processing powered by JamesDSP.\n\n• Equalizer: Graphic, Parametric, and AutoEQ profiles\n• Bass Boost, Stereo Widening, Reverberation\n• Convolution: load custom impulse response files\n• Liveprog: scriptable audio effects via Eel2\n• Per-app audio blocking\n\nNo root required — uses Shizuku + Android AudioEffect API.\n\nDSP toggle affects all audio output system-wide."
                    )
                    IconButton(onClick = onNavigateToGraphicEQ) { Icon(Icons.Default.Equalizer, "Graphic EQ") }
                    IconButton(onClick = onNavigateToDSPControls) { Icon(Icons.Default.Tune, "DSP Controls") }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { viewModel.toggleDsp() }) {
                        Icon(
                            if (state.dspEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            null,
                            tint = if (state.dspEnabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.saveCurrentPreset() }) { Icon(Icons.Default.Save, "Save Preset") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // DSP Engine Status
            item { DspStatusCard(state = state, onToggle = viewModel::toggleDsp) }

            // Presets
            item { PresetSelector(presets = state.presets, activeId = state.activePresetId, onSelect = viewModel::selectPreset) }

            // ── 10-Band Equalizer — collapsed by default (10 vertical sliders + preset row) ──
            item {
                ExpandableSection(
                    title = "10-Band Equalizer",
                    icon = Icons.Default.GraphicEq,
                    initiallyExpanded = false,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    EqualizerCard(bands = state.eqBands, onBandChange = viewModel::updateEqBand)
                }
            }

            // ── 31-Band Graphic EQ — collapsed by default (31 mini sliders in a row) ──
            item {
                ExpandableSection(
                    title = "31-Band Graphic EQ",
                    icon = Icons.Default.Equalizer,
                    badge = if (state.graphicEqEnabled) "ON" else null,
                    initiallyExpanded = false,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    GraphicEqCard(
                        bands = state.graphicEqBands,
                        enabled = state.graphicEqEnabled,
                        onToggle = viewModel::toggleGraphicEq,
                        onBandChange = viewModel::updateGraphicEqBand,
                    )
                }
            }

            // ── Bass & Treble — collapsed by default (two large sliders) ──
            item {
                ExpandableSection(
                    title = "Bass & Treble Enhancement",
                    icon = Icons.Default.VolumeUp,
                    initiallyExpanded = false,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    BassAndTrebleCard(
                        bassStrength = state.bassBoostStrength,
                        trebleStrength = state.trebleBoostStrength,
                        onBassChange = viewModel::updateBassBoost,
                        onTrebleChange = viewModel::updateTrebleBoost,
                    )
                }
            }

            // Stereo Widener
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Stereo Widener",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${"%.0f".format(state.stereoWideStrength * 100)}%", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(value = state.stereoWideStrength, onValueChange = viewModel::updateStereoWidener, valueRange = 0f..1f)
                    }
                }
            }

            // Virtualizer
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Virtualizer / Surround",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${state.virtualizerStrength}", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = state.virtualizerStrength.toFloat(),
                            onValueChange = { viewModel.updateVirtualizer(it.toInt()) },
                            valueRange = 0f..1000f,
                            steps = 99,
                        )
                    }
                }
            }

            // Reverb
            item { ReverbCard(preset = state.reverbPreset, onPresetChange = viewModel::updateReverbPreset) }

            // Dynamic Range Compression
            item { DrcCard(enabled = state.drcEnabled, gain = state.drcGain, onToggle = viewModel::toggleDrc, onGainChange = viewModel::updateDrcGain) }

            // Limiter
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Output Limiter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Prevents clipping on high-gain presets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.limiterEnabled) {
                                Slider(value = state.limiterGain, onValueChange = viewModel::updateLimiterGain, valueRange = -12f..0f)
                            }
                        }
                        Switch(checked = state.limiterEnabled, onCheckedChange = { viewModel.toggleLimiter() })
                    }
                }
            }

            // Convolver
            item {
                ConvolverCard(
                    enabled = state.convolverEnabled,
                    irPath = state.convolverIrPath,
                    onToggle = viewModel::toggleConvolver,
                    onPickFile = {
                        irFilePicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                    },
                )
            }

            // ── Liveprog Script — collapsed by default (multi-line code editor is very tall) ──
            item {
                ExpandableSection(
                    title = "Liveprog Script",
                    icon = Icons.Default.Code,
                    badge = if (state.liveprogEnabled) "ON" else null,
                    initiallyExpanded = false,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    LiveprogCard(
                        enabled = state.liveprogEnabled,
                        script = state.liveprogScript,
                        onToggle = viewModel::toggleLiveprog,
                        onScriptChange = viewModel::updateLiveprogScript,
                    )
                }
            }

            // AutoEQ
            item {
                AutoEqCard(
                    enabled = state.autoEqEnabled,
                    profileName = state.autoEqProfileName,
                    onToggle = viewModel::toggleAutoEq,
                    onSelectProfile = { showAutoEqPicker = true },
                )
            }

            // ── Per-App Targeting — collapsed by default (list can grow long) ──
            item {
                ExpandableSection(
                    title = "Per-App Targeting",
                    icon = Icons.Default.Apps,
                    badge = if (state.targetPackages.isNotEmpty()) "${state.targetPackages.size}" else null,
                    initiallyExpanded = false,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    PerAppTargetingCard(
                        targetPackages = state.targetPackages,
                        onAdd = {},
                        onRemove = viewModel::removeTargetPackage,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Always-visible cards (no ExpandableSection needed — compact/single-control)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DspStatusCard(state: AudioUiState, onToggle: () -> Unit) {
    val statusColor = if (state.dspEnabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(0.1f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Equalizer, null, tint = statusColor, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("JamesDSP Audio Engine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (state.dspEnabled) "Processing audio • ${state.activePresetName}" else "Audio DSP disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }
            Switch(checked = state.dspEnabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun PresetSelector(presets: List<PresetUiModel>, activeId: Long, onSelect: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets, key = { it.id }) { preset ->
                    FilterChip(
                        selected = preset.id == activeId,
                        onClick = { onSelect(preset.id) },
                        label = { Text(preset.name) },
                        leadingIcon = if (preset.id == activeId) ({
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReverbCard(preset: Int, onPresetChange: (Int) -> Unit) {
    val presets = listOf("None", "Small Room", "Medium Room", "Large Room", "Large Hall", "Plate", "Long Reverb")
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Reverb", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(presets) { i, name ->
                    FilterChip(selected = preset == i, onClick = { onPresetChange(i) }, label = { Text(name, fontSize = 11.sp) })
                }
            }
        }
    }
}

@Composable
private fun DrcCard(enabled: Boolean, gain: Float, onToggle: () -> Unit, onGainChange: (Float) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Dynamic Range Compression",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                Text("Gain: ${"%.1f".format(gain)} dB", style = MaterialTheme.typography.bodySmall)
                Slider(value = gain, onValueChange = onGainChange, valueRange = -12f..12f)
            }
        }
    }
}

@Composable
private fun ConvolverCard(enabled: Boolean, irPath: String, onToggle: () -> Unit, onPickFile: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Convolver (Room Correction)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (irPath.isNotBlank()) irPath.substringAfterLast('/') else "No impulse response loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPickFile, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Load Impulse Response (.irs / .wav)")
            }
        }
    }
}

@Composable
private fun AutoEqCard(enabled: Boolean, profileName: String, onToggle: () -> Unit, onSelectProfile: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AutoEQ Profiles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (profileName.isNotBlank()) "Active: $profileName" else "Headphone correction profiles from AutoEQ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSelectProfile, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Headphones, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Search AutoEQ Database")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dense-section content composables
//  These have NO outer Card wrapper — ExpandableSection provides the card shell.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EqualizerCard(bands: List<Float>, onBandChange: (Int, Float) -> Unit) {
    val freqLabels = listOf("31", "63", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            bands.forEachIndexed { i, value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${if (value >= 0) "+" else ""}${"%.0f".format(value)}dB",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                    )
                    Slider(
                        value = value,
                        onValueChange = { onBandChange(i, it) },
                        valueRange = -12f..12f,
                        modifier = Modifier.height(120.dp).width(28.dp),
                    )
                    Text(freqLabels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }
            }
        }
        val eqPresets = mapOf(
            "Flat"         to List(10) { 0f },
            "Bass Boost"   to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f),
            "V-Shape"      to listOf(4f, 3f, 1f, -1f, -3f, -3f, -1f, 1f, 3f, 4f),
            "Vocal"        to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -2f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            eqPresets.keys.forEach { presetName ->
                TextButton(
                    onClick = { eqPresets[presetName]?.forEachIndexed { i, v -> onBandChange(i, v) } },
                    contentPadding = PaddingValues(4.dp),
                ) { Text(presetName, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun GraphicEqCard(
    bands: List<Float>,
    enabled: Boolean,
    onToggle: () -> Unit,
    onBandChange: (Int, Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Enable 31-Band EQ",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(bands) { i, value ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Slider(
                            value = value,
                            onValueChange = { onBandChange(i, it) },
                            valueRange = -12f..12f,
                            modifier = Modifier.height(80.dp).width(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BassAndTrebleCard(
    bassStrength: Int,
    trebleStrength: Int,
    onBassChange: (Int) -> Unit,
    onTrebleChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Bass Boost: $bassStrength", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = bassStrength.toFloat(),
            onValueChange = { onBassChange(it.toInt()) },
            valueRange = 0f..1000f,
            steps = 99,
        )
        Spacer(Modifier.height(4.dp))
        Text("Treble Boost: $trebleStrength", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = trebleStrength.toFloat(),
            onValueChange = { onTrebleChange(it.toInt()) },
            valueRange = 0f..1000f,
            steps = 99,
        )
    }
}

@Composable
private fun LiveprogCard(
    enabled: Boolean,
    script: String,
    onToggle: () -> Unit,
    onScriptChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Custom EEL2 script for real-time processing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = script,
                onValueChange = onScriptChange,
                label = { Text("EEL2 Script") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 15,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
            )
        }
    }
}

@Composable
private fun PerAppTargetingCard(
    targetPackages: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Targeted Apps",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add App") }
        }
        if (targetPackages.isEmpty()) {
            Text(
                "DSP applies to all apps. Add specific apps to target only those.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            targetPackages.forEach { pkg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(pkg) }) {
                        Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
