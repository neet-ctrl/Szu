package com.accu.ui.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

@Composable
fun JamesDSPSettingsScreen(onBack: () -> Unit = {}) {
    // Audio format settings
    var audioFormat by remember { mutableStateOf("Float 32-bit") }
    var sampleRate by remember { mutableStateOf("48000 Hz") }
    var bufferSize by remember { mutableStateOf("256 frames") }
    var legacyMode by remember { mutableStateOf(false) }

    // Device profiles
    var deviceProfile by remember { mutableStateOf("Default") }
    var autoSwitchProfile by remember { mutableStateOf(true) }

    // Appearance
    var darkTheme by remember { mutableStateOf(true) }
    var showWaveform by remember { mutableStateOf(true) }
    var reducedAnimations by remember { mutableStateOf(false) }

    // Misc
    var showNotification by remember { mutableStateOf(true) }
    var enableOnBoot by remember { mutableStateOf(true) }
    var ignoreAudioFocus by remember { mutableStateOf(false) }
    var excludeCallAudio by remember { mutableStateOf(true) }

    Scaffold(topBar = { ACCTopBar(title = "JamesDSP Settings", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SectionLabel("Audio Format")
                var formatExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(formatExpanded, { formatExpanded = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(audioFormat, {}, readOnly = true, label = { Text("Sample format") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(formatExpanded) })
                    ExposedDropdownMenu(formatExpanded, { formatExpanded = false }) {
                        listOf("Float 32-bit", "Int 16-bit", "Int 24-bit", "Int 32-bit").forEach { f ->
                            DropdownMenuItem(text = { Text(f) }, onClick = { audioFormat = f; formatExpanded = false })
                        }
                    }
                }
                var srExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(srExpanded, { srExpanded = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(sampleRate, {}, readOnly = true, label = { Text("Sample rate") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(srExpanded) })
                    ExposedDropdownMenu(srExpanded, { srExpanded = false }) {
                        listOf("44100 Hz", "48000 Hz", "96000 Hz", "192000 Hz").forEach { r ->
                            DropdownMenuItem(text = { Text(r) }, onClick = { sampleRate = r; srExpanded = false })
                        }
                    }
                }
                var bufExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(bufExpanded, { bufExpanded = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(bufferSize, {}, readOnly = true, label = { Text("Buffer size (lower = less latency)") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bufExpanded) })
                    ExposedDropdownMenu(bufExpanded, { bufExpanded = false }) {
                        listOf("64 frames", "128 frames", "256 frames", "512 frames", "1024 frames").forEach { b ->
                            DropdownMenuItem(text = { Text(b) }, onClick = { bufferSize = b; bufExpanded = false })
                        }
                    }
                }
                PrefToggle("Legacy mode", "Use older processing engine for compatibility", legacyMode) { legacyMode = it }
                HorizontalDivider()
            }

            item {
                SectionLabel("Device Profiles")
                Text("Different audio presets for headphones, speakers, BT devices, USB audio.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(4.dp))
                var profileExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(profileExpanded, { profileExpanded = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(deviceProfile, {}, readOnly = true, label = { Text("Active profile") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileExpanded) })
                    ExposedDropdownMenu(profileExpanded, { profileExpanded = false }) {
                        listOf("Default", "Headphones", "Bluetooth A2DP", "USB Audio", "Speakers", "Custom").forEach { p ->
                            DropdownMenuItem(text = { Text(p) }, onClick = { deviceProfile = p; profileExpanded = false })
                        }
                    }
                }
                PrefToggle("Auto-switch profile", "Switch profile based on connected audio device", autoSwitchProfile) { autoSwitchProfile = it }
                ListItem(headlineContent = { Text("Manage profiles") }, supportingContent = { Text("Create, edit, or delete custom profiles") }, leadingContent = { Icon(Icons.Default.Tune, null) })
                HorizontalDivider()
            }

            item {
                SectionLabel("Appearance")
                PrefToggle("Dark theme", "Use dark UI for this section", darkTheme) { darkTheme = it }
                PrefToggle("Show waveform visualizer", "Real-time audio spectrum in UI", showWaveform) { showWaveform = it }
                PrefToggle("Reduced animations", "Disable animations for performance", reducedAnimations) { reducedAnimations = it }
                HorizontalDivider()
            }

            item {
                SectionLabel("Miscellaneous")
                PrefToggle("Show persistent notification", "Status bar indicator when DSP is active", showNotification) { showNotification = it }
                PrefToggle("Enable on boot", "Start DSP engine automatically on device boot", enableOnBoot) { enableOnBoot = it }
                PrefToggle("Ignore audio focus", "Process all audio streams including navigation/calls", ignoreAudioFocus) { ignoreAudioFocus = it }
                PrefToggle("Exclude phone calls", "Do not apply DSP to call audio", excludeCallAudio) { excludeCallAudio = it }
                HorizontalDivider()
            }

            item {
                SectionLabel("Backup & Restore")
                ListItem(headlineContent = { Text("Export settings") }, supportingContent = { Text("Save all DSP configuration to file") }, leadingContent = { Icon(Icons.Default.Upload, null) })
                ListItem(headlineContent = { Text("Import settings") }, supportingContent = { Text("Restore from backup file") }, leadingContent = { Icon(Icons.Default.Download, null) })
                ListItem(headlineContent = { Text("Reset to defaults") }, supportingContent = { Text("Clear all custom settings") }, leadingContent = { Icon(Icons.Default.Refresh, null) })
                HorizontalDivider()
            }

            item {
                SectionLabel("Troubleshooting")
                ListItem(headlineContent = { Text("App compatibility") }, supportingContent = { Text("Check which apps work with JamesDSP rootless") }, leadingContent = { Icon(Icons.Default.BugReport, null) })
                ListItem(headlineContent = { Text("Restart DSP engine") }, supportingContent = { Text("Force restart audio processing service") }, leadingContent = { Icon(Icons.Default.RestartAlt, null) })
                ListItem(headlineContent = { Text("View engine log") }, supportingContent = { Text("Debug output from DSP service") }, leadingContent = { Icon(Icons.Default.List, null) })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PrefToggle(title: String, sub: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(sub, fontSize = 12.sp) }, trailingContent = { Switch(checked = checked, onCheckedChange = onChanged) })
}
