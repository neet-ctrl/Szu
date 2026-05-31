package com.accu.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlin.math.sin
import kotlin.random.Random

data class ImpulseFile(val name: String, val format: String, val sampleRate: String, val length: String, val channels: String, val filePath: String)

@Composable
fun ConvolutionScreen(onBack: () -> Unit = {}) {
    var isEnabled by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<ImpulseFile?>(null) }
    var gain by remember { mutableStateOf(0f) }
    var mixing by remember { mutableStateOf(100f) }
    var equalization by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            ImpulseFile("Large Concert Hall", "WAV", "48000 Hz", "2.8 s", "Stereo", "/impulses/concert_hall.wav"),
            ImpulseFile("Cathedral", "WAV", "48000 Hz", "4.2 s", "Stereo", "/impulses/cathedral.wav"),
            ImpulseFile("Small Club", "WAV", "44100 Hz", "0.9 s", "Stereo", "/impulses/small_club.wav"),
            ImpulseFile("Studio A", "WAV", "96000 Hz", "0.5 s", "Stereo", "/impulses/studio_a.wav"),
            ImpulseFile("Bathroom", "WAV", "44100 Hz", "0.3 s", "Mono", "/impulses/bathroom.wav"),
            ImpulseFile("Church", "WAV", "48000 Hz", "3.1 s", "Stereo", "/impulses/church.wav"),
            ImpulseFile("Plate Reverb", "WAV", "44100 Hz", "1.5 s", "Stereo", "/impulses/plate_reverb.wav"),
        )
    }
    var showPicker by remember { mutableStateOf(false) }

    val random = remember { Random(42) }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Choose Impulse Response") },
            text = {
                LazyColumn {
                    items(presets) { file ->
                        ListItem(
                            headlineContent = { Text(file.name) },
                            supportingContent = { Text("${file.format} · ${file.sampleRate} · ${file.length} · ${file.channels}") },
                            leadingContent = { Icon(Icons.Default.AudioFile, null) },
                            modifier = Modifier.fillMaxWidth().then(Modifier),
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Convolution Engine",
                onBack = onBack,
                actions = {
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                // IR waveform preview
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selectedFile?.name ?: "No file selected", fontWeight = FontWeight.SemiBold)
                                if (selectedFile != null) {
                                    Text("${selectedFile!!.format} · ${selectedFile!!.sampleRate} · ${selectedFile!!.length} · ${selectedFile!!.channels}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (selectedFile != null) {
                            Spacer(Modifier.height(8.dp))
                            Canvas(
                                Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            ) {
                                val w = size.width; val h = size.height
                                val mid = h / 2f
                                for (x in 0 until w.toInt() step 2) {
                                    val decay = 1f - x / w
                                    val amplitude = random.nextFloat() * decay * h * 0.45f
                                    drawLine(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), Offset(x.toFloat(), mid - amplitude), Offset(x.toFloat(), mid + amplitude))
                                }
                            }
                        }
                    }
                }
            }

            item {
                // File selection buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPicker = true }, Modifier.weight(1f)) {
                        Icon(Icons.Default.LibraryMusic, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Presets")
                    }
                    OutlinedButton(onClick = {
                        selectedFile = ImpulseFile("custom_ir.wav", "WAV", "48000 Hz", "1.2 s", "Stereo", "/storage/custom_ir.wav")
                        isEnabled = true
                    }, Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Browse File")
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text("Convolution Settings", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                // Gain
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gain: ${gain.toInt()} dB", Modifier.width(110.dp), fontSize = 13.sp)
                    Slider(value = gain, onValueChange = { gain = it }, valueRange = -20f..20f, modifier = Modifier.weight(1f), enabled = isEnabled)
                }

                // Wet/Dry Mix
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mix: ${mixing.toInt()}%", Modifier.width(110.dp), fontSize = 13.sp)
                    Slider(value = mixing, onValueChange = { mixing = it }, valueRange = 0f..100f, modifier = Modifier.weight(1f), enabled = isEnabled)
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Equalization (EQ correction)")
                        Text("Apply frequency EQ to compensate for IR coloration", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = equalization, onCheckedChange = { equalization = it }, enabled = isEnabled)
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("How It Works", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Convolution reverb applies the acoustic characteristics of a real space (captured as an Impulse Response) to your audio. " +
                    "Load a .wav/.flac IR file or choose a preset to simulate rooms, halls, plates, and more. " +
                    "The Mix slider blends the processed (wet) signal with the original (dry) signal.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.Indication.run() {}
