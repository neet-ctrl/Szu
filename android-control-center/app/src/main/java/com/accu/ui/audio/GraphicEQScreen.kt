package com.accu.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphicEQScreen(onBack: () -> Unit = {}) {
    val bands15 = listOf(25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000)
    val bandLabels = bands15.map { if (it >= 1000) "${it/1000}k" else "$it" }
    var gains by remember { mutableStateOf(List(15) { 0f }) }
    var isEnabled by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf("Flat") }
    var showPresetMenu by remember { mutableStateOf(false) }

    val presets = mapOf(
        "Flat" to List(15) { 0f },
        "Bass Boost" to listOf(8f, 7f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f, 7f, 8f, 8f),
        "Rock" to listOf(5f, 4f, 3f, 1f, 0f, -1f, -1f, 0f, 1f, 3f, 4f, 5f, 5f, 5f, 4f),
        "Pop" to listOf(-1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 2f),
        "Classical" to listOf(5f, 4f, 3f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 2f, 3f, 4f),
        "Jazz" to listOf(3f, 2f, 0f, 2f, 3f, 2f, 0f, 0f, 0f, 2f, 3f, 3f, 2f, 1f, 2f),
        "Dance" to listOf(6f, 5f, 3f, 0f, 0f, 0f, -1f, -2f, -2f, 0f, 1f, 3f, 4f, 4f, 3f),
        "Hip Hop" to listOf(7f, 6f, 4f, 2f, 0f, -1f, -2f, -2f, -1f, 0f, 2f, 3f, 3f, 2f, 2f),
        "Electronic" to listOf(5f, 4f, 2f, 0f, -2f, -3f, -2f, 0f, 2f, 4f, 5f, 5f, 4f, 3f, 4f),
        "Vocal" to listOf(-2f, -1f, 0f, 1f, 3f, 4f, 4f, 3f, 2f, 1f, 0f, 0f, 1f, 2f, 1f),
        "Bass Reducer" to listOf(-6f, -5f, -4f, -2f, -1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Graphic Equalizer",
                onBack = onBack,
                actions = {
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    Box {
                        IconButton(onClick = { showPresetMenu = true }) { Icon(Icons.Default.Tune, "Presets") }
                        DropdownMenu(showPresetMenu, { showPresetMenu = false }) {
                            presets.keys.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset) },
                                    leadingIcon = { if (selectedPreset == preset) Icon(Icons.Default.Check, null) },
                                    onClick = {
                                        selectedPreset = preset
                                        gains = presets[preset] ?: List(15) { 0f }
                                        showPresetMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { gains = List(15) { 0f }; selectedPreset = "Flat" }) { Icon(Icons.Default.Refresh, "Reset") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            // EQ curve canvas
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(8.dp)
            ) {
                val w = size.width; val h = size.height
                // Grid lines at 0dB, ±6dB, ±12dB
                for (db in listOf(-12f, -6f, 0f, 6f, 12f)) {
                    val y = h / 2f - (db / 12f) * (h / 2f)
                    drawLine(if (db == 0f) Color.Gray else Color.Gray.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), strokeWidth = if (db == 0f) 2f else 1f)
                }
                // EQ curve
                val pts = gains.mapIndexed { i, g ->
                    val x = w * i / (gains.size - 1f)
                    val y = h / 2f - (g.coerceIn(-12f, 12f) / 12f) * (h / 2f)
                    Offset(x, y)
                }
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val ctrl = Offset((pts[i-1].x + pts[i].x) / 2f, (pts[i-1].y + pts[i].y) / 2f)
                        quadraticBezierTo(pts[i-1].x, pts[i-1].y, ctrl.x, ctrl.y)
                    }
                    lineTo(pts.last().x, pts.last().y)
                }
                drawPath(path, primaryColor.copy(alpha = if (isEnabled) 0.8f else 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth = 3f))
                // Fill under curve
                val fillPath = Path().apply { addPath(path); lineTo(pts.last().x, h); lineTo(pts.first().x, h); close() }
                drawPath(fillPath, Brush.verticalGradient(listOf(primaryColor.copy(alpha = if (isEnabled) 0.3f else 0.1f), Color.Transparent)))
            }

            Spacer(Modifier.height(8.dp))

            // Band sliders
            LazyRow(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                itemsIndexed(bandLabels) { i, label ->
                    Column(
                        Modifier.width(52.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${if (gains[i] >= 0) "+" else ""}${gains[i].roundToInt()}dB",
                            fontSize = 9.sp,
                            color = if (gains[i] != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = gains[i],
                            onValueChange = { v ->
                                gains = gains.toMutableList().also { it[i] = v.roundToInt().toFloat() }
                                selectedPreset = "Custom"
                            },
                            valueRange = -12f..12f,
                            steps = 23,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { rotationZ = -90f }
                                .width(160.dp),
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            )
                        )
                        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Preset: $selectedPreset", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(8.dp))

            // AutoEQ import
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudDownload, null)
                Spacer(Modifier.width(6.dp))
                Text("Import from AutoEQ database")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileOpen, null)
                Spacer(Modifier.width(6.dp))
                Text("Load from string (GraphicEQ DSP format)")
            }
        }
    }
}
