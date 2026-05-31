package com.accu.ui.audio

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

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Audio Center",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onNavigateToGraphicEQ) { Icon(Icons.Default.Equalizer, "Graphic EQ") }
                    IconButton(onClick = onNavigateToDSPControls) { Icon(Icons.Default.Tune, "DSP Controls") }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { viewModel.toggleDsp() }) {
                        Icon(if (state.dspEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            null, tint = if (state.dspEnabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant)
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

            // Equalizer (10-band)
            item { EqualizerCard(bands = state.eqBands, onBandChange = viewModel::updateEqBand) }

            // Graphic EQ (31-band, from JamesDSP)
            item { GraphicEqCard(bands = state.graphicEqBands, enabled = state.graphicEqEnabled, onToggle = viewModel::toggleGraphicEq, onBandChange = viewModel::updateGraphicEqBand) }

            // Bass & Treble Boost
            item { BassAndTrebleCard(bassStrength = state.bassBoostStrength, trebleStrength = state.trebleBoostStrength, onBassChange = viewModel::updateBassBoost, onTrebleChange = viewModel::updateTrebleBoost) }

            // Stereo Widener
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Stereo Widener", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                            Text("Virtualizer / Surround", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${state.virtualizerStrength}", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(value = state.virtualizerStrength.toFloat(), onValueChange = { viewModel.updateVirtualizer(it.toInt()) }, valueRange = 0f..1000f, steps = 99)
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
                            Text("Prevents clipping on high-gain presets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.limiterEnabled) Slider(value = state.limiterGain, onValueChange = viewModel::updateLimiterGain, valueRange = -12f..0f)
                        }
                        Switch(checked = state.limiterEnabled, onCheckedChange = { viewModel.toggleLimiter() })
                    }
                }
            }

            // Convolver
            item { ConvolverCard(enabled = state.convolverEnabled, irPath = state.convolverIrPath, onToggle = viewModel::toggleConvolver) }

            // Liveprog
            item { LiveprogCard(enabled = state.liveprogEnabled, script = state.liveprogScript, onToggle = viewModel::toggleLiveprog, onScriptChange = viewModel::updateLiveprogScript) }

            // AutoEQ
            item { AutoEqCard(enabled = state.autoEqEnabled, profileName = state.autoEqProfileName, onToggle = viewModel::toggleAutoEq) }

            // Per-app targeting
            item { PerAppTargetingCard(targetPackages = state.targetPackages, onAdd = {}, onRemove = viewModel::removeTargetPackage) }
        }
    }
}

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
                    style = MaterialTheme.typography.bodySmall, color = statusColor,
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
                        leadingIcon = if (preset.id == activeId) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun EqualizerCard(bands: List<Float>, onBandChange: (Int, Float) -> Unit) {
    val freqLabels = listOf("31", "63", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("10-Band Equalizer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                bands.forEachIndexed { i, value ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${if (value >= 0) "+" else ""}${"%.0f".format(value)}dB", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Flat", "Bass Boost", "Treble Boost", "V-Shape", "Vocal").forEach { preset ->
                    TextButton(onClick = {}, contentPadding = PaddingValues(4.dp)) { Text(preset, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun GraphicEqCard(bands: List<Float>, enabled: Boolean, onToggle: () -> Unit, onBandChange: (Int, Float) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("31-Band Graphic EQ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(bands) { i, value ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Slider(value = value, onValueChange = { onBandChange(i, it) }, valueRange = -12f..12f, modifier = Modifier.height(80.dp).width(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BassAndTrebleCard(bassStrength: Int, trebleStrength: Int, onBassChange: (Int) -> Unit, onTrebleChange: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Bass & Treble Enhancement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Bass Boost: $bassStrength", style = MaterialTheme.typography.bodySmall)
            Slider(value = bassStrength.toFloat(), onValueChange = { onBassChange(it.toInt()) }, valueRange = 0f..1000f, steps = 99)
            Text("Treble Boost: $trebleStrength", style = MaterialTheme.typography.bodySmall)
            Slider(value = trebleStrength.toFloat(), onValueChange = { onTrebleChange(it.toInt()) }, valueRange = 0f..1000f, steps = 99)
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
                Text("Dynamic Range Compression", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
private fun ConvolverCard(enabled: Boolean, irPath: String, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Convolver (Room Correction)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (irPath.isNotBlank()) irPath.substringAfterLast('/') else "No impulse response loaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
            if (!enabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {}, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Load Impulse Response (.irs / .wav)")
                }
            }
        }
    }
}

@Composable
private fun LiveprogCard(enabled: Boolean, script: String, onToggle: () -> Unit, onScriptChange: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Liveprog Script", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Custom EEL2 script for real-time processing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = script, onValueChange = onScriptChange, label = { Text("EEL2 Script") }, modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 15, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            }
        }
    }
}

@Composable
private fun AutoEqCard(enabled: Boolean, profileName: String, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AutoEQ Profiles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (profileName.isNotBlank()) "Active: $profileName" else "Headphone correction profiles from AutoEQ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {}, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Headphones, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Search AutoEQ Database")
                }
            }
        }
    }
}

@Composable
private fun PerAppTargetingCard(targetPackages: List<String>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Per-App Targeting", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add App") }
            }
            if (targetPackages.isEmpty()) {
                Text("DSP applies to all apps. Add specific apps to target only those.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                targetPackages.forEach { pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pkg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(pkg) }) { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
}
