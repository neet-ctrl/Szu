package com.accu.ui.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

@Composable
fun DSPControlsScreen(onBack: () -> Unit = {}) {
    var masterEnabled by remember { mutableStateOf(true) }

    // Output control
    var outputGain by remember { mutableStateOf(0f) }
    var limitOutput by remember { mutableStateOf(true) }
    var limiterThreshold by remember { mutableStateOf(-0.2f) }

    // Bass & treble
    var bassEnabled by remember { mutableStateOf(false) }
    var bassMode by remember { mutableStateOf("Natural Bass") }
    var bassGain by remember { mutableStateOf(5f) }
    var bassFreq by remember { mutableStateOf(80f) }
    var trebleGain by remember { mutableStateOf(0f) }

    // Stereo widening
    var stereoEnabled by remember { mutableStateOf(false) }
    var stereoMode by remember { mutableStateOf("Stereoize II") }
    var stereoStrength by remember { mutableStateOf(60f) }

    // Surround / 3D
    var surroundEnabled by remember { mutableStateOf(false) }
    var surroundMode by remember { mutableStateOf("Pro Logic") }
    var surroundStrength by remember { mutableStateOf(50f) }
    var surroundRoomSize by remember { mutableStateOf(6f) }

    // Reverberation
    var reverbEnabled by remember { mutableStateOf(false) }
    var reverbPreset by remember { mutableStateOf("Small Room") }
    var reverbRoomSize by remember { mutableStateOf(10f) }
    var reverbDecay by remember { mutableStateOf(1000f) }
    var reverbDamping by remember { mutableStateOf(50f) }
    var reverbWet by remember { mutableStateOf(30f) }
    var reverbDry by remember { mutableStateOf(90f) }

    // Compressor
    var compressorEnabled by remember { mutableStateOf(false) }
    var compressorThreshold by remember { mutableStateOf(-20f) }
    var compressorRatio by remember { mutableStateOf(4f) }
    var compressorAttack by remember { mutableStateOf(10f) }
    var compressorRelease by remember { mutableStateOf(200f) }
    var compressorMakeup by remember { mutableStateOf(6f) }

    // Analog modeling
    var analogEnabled by remember { mutableStateOf(false) }
    var analogDrive by remember { mutableStateOf(30f) }

    // Crossfeed
    var crossfeedEnabled by remember { mutableStateOf(false) }
    var crossfeedMode by remember { mutableStateOf("BS2B 700 Hz") }

    // DDC (Digital Device Correction)
    var ddcEnabled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "DSP Controls",
                onBack = onBack,
                actions = {
                    Text("Master", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                    Switch(checked = masterEnabled, onCheckedChange = { masterEnabled = it })
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp, bottom = 24.dp)) {

            // ─── Output Control ────────────────────────────────────────────
            item {
                DSPSection("Output Control", Icons.Default.VolumeUp, null, null) {
                    DspSlider("Output gain", outputGain, -10f..10f, "%.1f dB") { outputGain = it }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Limiter"); Text("Prevent clipping at output", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(checked = limitOutput, onCheckedChange = { limitOutput = it })
                    }
                    if (limitOutput) DspSlider("Limiter threshold", limiterThreshold, -3f..0f, "%.1f dBFS") { limiterThreshold = it }
                }
            }

            // ─── Bass & Treble ─────────────────────────────────────────────
            item {
                DSPSection("Bass & Treble", Icons.Default.GraphicEq, bassEnabled, { bassEnabled = it }) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(bassMode, {}, readOnly = true, label = { Text("Bass mode") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            listOf("Natural Bass", "Super Bass", "Dynamic Bass").forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = { bassMode = m; expanded = false })
                            }
                        }
                    }
                    DspSlider("Bass gain", bassGain, 0f..15f, "+%.0f dB") { bassGain = it }
                    DspSlider("Bass frequency", bassFreq, 40f..200f, "%.0f Hz") { bassFreq = it }
                    DspSlider("Treble gain", trebleGain, -6f..6f, "%.0f dB") { trebleGain = it }
                }
            }

            // ─── Stereo Widening ──────────────────────────────────────────
            item {
                DSPSection("Stereo Widening", Icons.Default.Headset, stereoEnabled, { stereoEnabled = it }) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(stereoMode, {}, readOnly = true, label = { Text("Mode") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            listOf("Stereoize I", "Stereoize II", "Stereoize III", "Wide").forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = { stereoMode = m; expanded = false })
                            }
                        }
                    }
                    DspSlider("Strength", stereoStrength, 0f..100f, "%.0f%%") { stereoStrength = it }
                }
            }

            // ─── Surround / 3D ────────────────────────────────────────────
            item {
                DSPSection("Surround / 3D Sound", Icons.Default.Surround, surroundEnabled, { surroundEnabled = it }) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(surroundMode, {}, readOnly = true, label = { Text("Mode") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            listOf("Pro Logic", "Haas", "SRS", "Focal").forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = { surroundMode = m; expanded = false })
                            }
                        }
                    }
                    DspSlider("Strength", surroundStrength, 0f..100f, "%.0f%%") { surroundStrength = it }
                    DspSlider("Room size", surroundRoomSize, 1f..15f, "%.0f m") { surroundRoomSize = it }
                }
            }

            // ─── Reverberation ─────────────────────────────────────────────
            item {
                DSPSection("Reverberation", Icons.Default.BlurOn, reverbEnabled, { reverbEnabled = it }) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(reverbPreset, {}, readOnly = true, label = { Text("Preset") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            listOf("Small Room", "Medium Room", "Large Room", "Hall", "Cathedral", "Plate", "Spring").forEach { p ->
                                DropdownMenuItem(text = { Text(p) }, onClick = { reverbPreset = p; expanded = false })
                            }
                        }
                    }
                    DspSlider("Room size", reverbRoomSize, 1f..30f, "%.0f m²") { reverbRoomSize = it }
                    DspSlider("Decay time", reverbDecay, 100f..5000f, "%.0f ms") { reverbDecay = it }
                    DspSlider("Damping", reverbDamping, 0f..100f, "%.0f%%") { reverbDamping = it }
                    DspSlider("Wet level", reverbWet, 0f..100f, "%.0f%%") { reverbWet = it }
                    DspSlider("Dry level", reverbDry, 0f..100f, "%.0f%%") { reverbDry = it }
                }
            }

            // ─── Compressor ───────────────────────────────────────────────
            item {
                DSPSection("Compander / Compressor", Icons.Default.Compress, compressorEnabled, { compressorEnabled = it }) {
                    DspSlider("Threshold", compressorThreshold, -60f..0f, "%.0f dB") { compressorThreshold = it }
                    DspSlider("Ratio", compressorRatio, 1f..20f, "%.0f:1") { compressorRatio = it }
                    DspSlider("Attack", compressorAttack, 1f..100f, "%.0f ms") { compressorAttack = it }
                    DspSlider("Release", compressorRelease, 50f..3000f, "%.0f ms") { compressorRelease = it }
                    DspSlider("Makeup gain", compressorMakeup, 0f..24f, "+%.0f dB") { compressorMakeup = it }
                }
            }

            // ─── Analog Modeling ──────────────────────────────────────────
            item {
                DSPSection("Analog Modeling", Icons.Default.SettingsInputComponent, analogEnabled, { analogEnabled = it }) {
                    Text("Simulates the harmonic distortion characteristics of analog tube/tape equipment.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    DspSlider("Drive", analogDrive, 0f..100f, "%.0f%%") { analogDrive = it }
                }
            }

            // ─── Crossfeed ────────────────────────────────────────────────
            item {
                DSPSection("Crossfeed (Headphone)", Icons.Default.Headphones, crossfeedEnabled, { crossfeedEnabled = it }) {
                    Text("Reduces stereo separation for headphone listening to simulate speaker sound-stage.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = it }) {
                        OutlinedTextField(crossfeedMode, {}, readOnly = true, label = { Text("Mode") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            listOf("BS2B 700 Hz", "BS2B 650 Hz", "Bauer Moderate", "Bauer Wide").forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = { crossfeedMode = m; expanded = false })
                            }
                        }
                    }
                }
            }

            // ─── DDC ──────────────────────────────────────────────────────
            item {
                DSPSection("DDC (Device Correction)", Icons.Default.Equalizer, ddcEnabled, { ddcEnabled = it }) {
                    Text("Applies Digital Device Correction profiles to compensate for device-specific frequency response characteristics.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {}, Modifier.fillMaxWidth()) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp)); Text("Load DDC profile (.vdc file)") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DSPSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean?, onToggle: ((Boolean) -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (enabled != null && onToggle != null) Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DspSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, onChanged: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${format.format(value)}", modifier = Modifier.width(180.dp), fontSize = 12.sp)
        Slider(value = value, onValueChange = onChanged, valueRange = range, modifier = Modifier.weight(1f))
    }
}
