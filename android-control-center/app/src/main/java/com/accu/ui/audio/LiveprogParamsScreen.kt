package com.accu.ui.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class LiveprogParam(val name: String, val label: String, val description: String, val min: Float, val max: Float, val default: Float, var value: Float, val unit: String = "")

@Composable
fun LiveprogParamsScreen(onBack: () -> Unit = {}) {
    val scriptName = remember { "tube_saturation_v2.eel" }
    var params by remember {
        mutableStateOf(listOf(
            LiveprogParam("drive", "Drive", "Amount of harmonic distortion/saturation", 0f, 100f, 30f, 30f, "%"),
            LiveprogParam("tone", "Tone", "High-frequency roll-off control", 0f, 100f, 70f, 70f, "%"),
            LiveprogParam("mix", "Wet/Dry Mix", "Balance between processed and clean signal", 0f, 100f, 50f, 50f, "%"),
            LiveprogParam("input_gain", "Input Gain", "Gain applied before saturation stage", -12f, 12f, 0f, 0f, " dB"),
            LiveprogParam("output_gain", "Output Gain", "Gain applied after saturation stage", -12f, 12f, -3f, -3f, " dB"),
            LiveprogParam("even_harmonics", "Even Harmonics", "Level of 2nd-order harmonic distortion (tube-like)", 0f, 100f, 60f, 60f, "%"),
            LiveprogParam("odd_harmonics", "Odd Harmonics", "Level of 3rd-order harmonic distortion (transistor-like)", 0f, 100f, 20f, 20f, "%"),
            LiveprogParam("bias", "Bias", "Operating point of the simulated tube stage", -50f, 50f, 0f, 0f, ""),
        ))
    }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to defaults?") },
            text = { Text("All parameter values will be reset to their default values.") },
            confirmButton = {
                TextButton(onClick = {
                    params = params.map { it.copy(value = it.default) }
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Liveprog Parameters",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showResetDialog = true }) { Icon(Icons.Default.Refresh, "Reset to defaults") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Script info
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(scriptName, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        Text("Tube saturation EEL2 script · ${params.size} parameters", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, bottom = 24.dp)) {
                items(params, key = { it.name }) { param ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(param.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(param.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "${if (param.value == param.value.toLong().toFloat()) param.value.toInt().toString() else "%.1f".format(param.value)}${param.unit}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${if (param.min == param.min.toLong().toFloat()) param.min.toInt().toString() else "%.1f".format(param.min)}${param.unit}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Slider(
                                    value = param.value,
                                    onValueChange = { v ->
                                        params = params.map { p -> if (p.name == param.name) p.copy(value = v) else p }
                                    },
                                    valueRange = param.min..param.max,
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                )
                                Text("${if (param.max == param.max.toLong().toFloat()) param.max.toInt().toString() else "%.1f".format(param.max)}${param.unit}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Default indicator
                            if (param.value != param.default) {
                                TextButton(
                                    onClick = { params = params.map { p -> if (p.name == param.name) p.copy(value = p.default) else p } },
                                    contentPadding = PaddingValues(horizontal = 0.dp)
                                ) { Text("Reset to default (${if (param.default == param.default.toLong().toFloat()) param.default.toInt().toString() else "%.1f".format(param.default)}${param.unit})", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}
