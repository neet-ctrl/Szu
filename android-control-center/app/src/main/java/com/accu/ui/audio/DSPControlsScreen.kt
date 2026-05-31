package com.accu.ui.audio

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DSPControlsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var masterEnabled by remember { mutableStateOf(true) }
    var ddcProfileName by remember { mutableStateOf<String?>(null) }
    val ddcFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> ddcProfileName = uri.lastPathSegment?.substringAfterLast("/") ?: uri.toString() }
        }
    }

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

    // Device profiles
    var selectedProfile by remember { mutableStateOf("Default") }
    var showProfileDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    val savedProfiles = remember { mutableStateListOf("Default", "Gaming", "Music", "Podcast") }

    // Export helper — serialises the entire DSP state to a JSON string
    fun buildExportJson(): String = """
        {
          "version": 1,
          "profile": "$selectedProfile",
          "masterEnabled": $masterEnabled,
          "output": { "gain": $outputGain, "limitOutput": $limitOutput, "limiterThreshold": $limiterThreshold },
          "bass": { "enabled": $bassEnabled, "mode": "$bassMode", "gain": $bassGain, "freq": $bassFreq, "trebleGain": $trebleGain },
          "stereo": { "enabled": $stereoEnabled, "mode": "$stereoMode", "strength": $stereoStrength },
          "surround": { "enabled": $surroundEnabled, "mode": "$surroundMode", "strength": $surroundStrength, "roomSize": $surroundRoomSize },
          "reverb": { "enabled": $reverbEnabled, "preset": "$reverbPreset", "roomSize": $reverbRoomSize, "decay": $reverbDecay, "damping": $reverbDamping, "wet": $reverbWet, "dry": $reverbDry },
          "compressor": { "enabled": $compressorEnabled, "threshold": $compressorThreshold, "ratio": $compressorRatio, "attack": $compressorAttack, "release": $compressorRelease, "makeup": $compressorMakeup },
          "analog": { "enabled": $analogEnabled, "drive": $analogDrive },
          "crossfeed": { "enabled": $crossfeedEnabled, "mode": "$crossfeedMode" },
          "ddc": { "enabled": $ddcEnabled, "profile": "$ddcProfileName" }
        }
    """.trimIndent()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@let
                    // Parse key values from the JSON string with simple regex extraction
                    fun jsonBool(key: String): Boolean? = Regex(""""$key"\s*:\s*(true|false)""").find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
                    fun jsonFloat(key: String): Float? = Regex(""""$key"\s*:\s*([\d.\-]+)""").find(json)?.groupValues?.get(1)?.toFloatOrNull()
                    fun jsonStr(key: String): String? = Regex(""""$key"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
                    jsonBool("masterEnabled")?.let { masterEnabled = it }
                    jsonFloat("gain")?.let { outputGain = it }
                    jsonBool("limitOutput")?.let { limitOutput = it }
                    jsonFloat("limiterThreshold")?.let { limiterThreshold = it }
                    jsonBool("enabled")?.let { bassEnabled = it }
                    jsonStr("mode")?.let { bassMode = it }
                    jsonFloat("trebleGain")?.let { trebleGain = it }
                    jsonBool("enabled")?.let { surroundEnabled = it }
                    jsonBool("enabled")?.let { reverbEnabled = it }
                    jsonStr("preset")?.let { reverbPreset = it }
                    jsonBool("enabled")?.let { compressorEnabled = it }
                    jsonBool("enabled")?.let { analogEnabled = it }
                    jsonBool("enabled")?.let { crossfeedEnabled = it }
                    jsonStr("mode")?.let { crossfeedMode = it }
                    jsonStr("profile")?.let { selectedProfile = it }
                } catch (_: Exception) { /* malformed file — ignore */ }
            }
        }
    }

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
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {

            // ─── Device Profiles ──────────────────────────────────────────
            item {
                DSPSection("Device Profiles", Icons.Default.Devices, null, null) {
                    Text(
                        "Save and restore complete DSP configurations per device or use-case.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedProfiles) { profile ->
                            FilterChip(
                                selected = selectedProfile == profile,
                                onClick = { selectedProfile = profile },
                                label = { Text(profile) },
                                leadingIcon = if (selectedProfile == profile) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { profileName = ""; showProfileDialog = true }, Modifier.weight(1f)) {
                            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Profile", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = {
                            val json = buildExportJson()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "DSP Profile — $selectedProfile")
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export DSP Profile"))
                        }, Modifier.weight(1f)) {
                            Icon(Icons.Default.IosShare, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = {
                            importLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                        }, Modifier.weight(1f)) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import", fontSize = 12.sp)
                        }
                    }
                }
            }

            // ─── Output Control ────────────────────────────────────────────
            item {
                DSPSection("Output Control", Icons.Default.VolumeUp, null, null, infoDescription = "Master output gain and hard limiter. Increase gain to boost overall volume; enable the limiter to prevent clipping/distortion at high volumes.\n\nLimiter threshold is in dBFS — values near 0 (e.g. −0.2 dB) prevent any clipping without audible pumping.") {
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
                DSPSection("Bass & Treble", Icons.Default.GraphicEq, bassEnabled, { bassEnabled = it }, infoDescription = "Bass boost adds low-frequency energy below the chosen frequency. Treble adds sparkle/air above 5 kHz.\n\nModes:\n• Natural Bass — gentle shelf, good for casual listening\n• Pure Bass — more aggressive sub-bass boost\n• Boom — extreme low-end for EDM/hip-hop\n\nTip: Bass boost >12 dB can cause distortion on drivers with limited excursion.") {
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
                DSPSection("Stereo Widening", Icons.Default.Headset, stereoEnabled, { stereoEnabled = it }, infoDescription = "Expands the perceived stereo field by processing the mid/side channels.\n\nModes:\n• Haas Effect — slight time delay to one channel for spaciousness\n• Dolby Stereo — Dolby-style matrix widening\n• Simple Stereo — straight mid/side width control\n\nHigh strength values (>80%) can cause mono compatibility issues and headache-inducing listening fatigue.") {
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
                DSPSection("Surround / 3D Sound", Icons.Default.GraphicEq, surroundEnabled, { surroundEnabled = it }) {
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
                DSPSection("Reverberation", Icons.Default.BlurOn, reverbEnabled, { reverbEnabled = it }, infoDescription = "Simulates listening in a physical space by adding reflections and decay.\n\nPresets:\n• Small Room — tight, close ambience\n• Large Hall — cathedral-style, long tail\n• Live Stage — concert-hall feel for music\n\nWet/Dry ratio: Wet controls reverb level; Dry controls original signal. Keep Dry at 100 for natural sound.") {
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
                DSPSection("Compander / Compressor", Icons.Default.Compress, compressorEnabled, { compressorEnabled = it }, infoDescription = "Reduces the dynamic range so quiet parts are louder and loud parts are softer — great for podcasts and spoken word.\n\n• Threshold — level above which compression kicks in (dBFS)\n• Ratio — how much compression to apply (4:1 is moderate, 10:1 is limiting)\n• Attack/Release — how fast the compressor responds\n• Makeup Gain — compensate for volume lost during compression") {
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
                DSPSection("Crossfeed (Headphone)", Icons.Default.Headphones, crossfeedEnabled, { crossfeedEnabled = it }, infoDescription = "Crossfeed blends a small amount of the left channel into the right and vice versa, simulating how our ears hear speakers in a room. This reduces listening fatigue caused by hard stereo panning in headphones.\n\nModes:\n• BS2B 700 Hz — Bauer stereophonic-to-binaural at 700 Hz cut-off (most natural)\n• BS2B 650 Hz — slightly wider crossfeed\n• CMoy — minimal crossfeed\n\nRecommended for long listening sessions.") {
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
                DSPSection("DDC (Device Correction)", Icons.Default.Equalizer, ddcEnabled, { ddcEnabled = it }, infoDescription = "DDC (Digital Device Correction) applies a correction filter designed specifically for your headphone or speaker model to flatten its frequency response.\n\nLoad a .vdc file generated for your device from databases like AutoEq or SquigLink. This is the most accurate way to EQ because it uses measured data rather than guesswork.\n\nCompatible formats: VDC files (RootlessJamesDSP format).") {
                    Text("Applies Digital Device Correction profiles to compensate for device-specific frequency response characteristics.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { ddcFilePicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("*/*")) }) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp))
                        Text(ddcProfileName?.let { "Loaded: $it" } ?: "Load DDC profile (.vdc file)")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Save profile dialog
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            icon = { Icon(Icons.Default.Save, null) },
            title = { Text("Save Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Save the current DSP settings as a named profile.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (profileName.isNotBlank()) {
                            if (!savedProfiles.contains(profileName)) savedProfiles.add(profileName)
                            selectedProfile = profileName
                        }
                        showProfileDialog = false
                    },
                    enabled = profileName.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showProfileDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DSPSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean?, onToggle: ((Boolean) -> Unit)?, infoDescription: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (infoDescription != null) {
                    InfoTooltipIcon(title = title, description = infoDescription)
                }
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
