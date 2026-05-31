package com.accu.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.*

data class EQBand(
    val id: Int,
    val label: String,
    val frequency: Float,
    var gainDb: Float = 0f,
    var qFactor: Float = 1.0f,
    var type: BandType = BandType.PEAK,
    val color: Color = Color.Cyan,
)

enum class BandType(val label: String) {
    PEAK("Peak"), LOW_SHELF("Low Shelf"), HIGH_SHELF("High Shelf"), LOW_PASS("Low Pass"), HIGH_PASS("High Pass"), NOTCH("Notch")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametricEQScreen(onBack: () -> Unit) {
    val bands = remember {
        mutableStateListOf(
            EQBand(1, "Sub Bass", 60f, color = Color(0xFFFF4444)),
            EQBand(2, "Bass", 200f, color = Color(0xFFFF8800)),
            EQBand(3, "Low Mid", 800f, color = Color(0xFFFFFF00)),
            EQBand(4, "Mid", 2500f, color = Color(0xFF44FF44)),
            EQBand(5, "High Mid", 6000f, color = Color(0xFF00FFFF)),
            EQBand(6, "Treble", 12000f, color = Color(0xFF8844FF)),
        )
    }
    var selectedBand by remember { mutableStateOf<EQBand?>(bands.first()) }
    var isEnabled by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf("Flat") }
    val presets = listOf("Flat", "Bass Boost", "Vocal", "Classical", "Electronic", "Pop", "Rock", "Custom")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parametric EQ") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            EQCurveCanvas(bands = bands, isEnabled = isEnabled, modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF0D0D0D)))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(presets) { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = {
                            selectedPreset = preset
                            if (preset == "Flat") bands.forEach { it.gainDb = 0f }
                            else if (preset == "Bass Boost") {
                                bands[0].gainDb = 6f; bands[1].gainDb = 4f; bands[2].gainDb = 2f
                                bands[3].gainDb = 0f; bands[4].gainDb = -1f; bands[5].gainDb = -2f
                            }
                        },
                        label = { Text(preset) },
                    )
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bands) { band ->
                    EQBandChip(
                        band = band,
                        isSelected = selectedBand?.id == band.id,
                        onClick = { selectedBand = band },
                    )
                }
            }

            selectedBand?.let { band ->
                BandControls(
                    band = band,
                    onGainChange = { band.gainDb = it; selectedPreset = "Custom" },
                    onQChange = { band.qFactor = it },
                    onTypeChange = { band.type = it },
                )
            }
        }
    }
}

@Composable
private fun EQCurveCanvas(bands: List<EQBand>, isEnabled: Boolean, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val gridColor = Color.White.copy(alpha = 0.08f)

        listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).forEach { y ->
            drawLine(gridColor, Offset(0f, h * y), Offset(w, h * y))
        }
        listOf(20f, 100f, 1000f, 10000f, 20000f).forEach { freq ->
            val x = freqToX(freq, w)
            drawLine(gridColor, Offset(x, 0f), Offset(x, h))
        }
        drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, h / 2), Offset(w, h / 2))

        if (isEnabled) {
            val path = Path()
            val points = (0 until w.toInt()).map { px ->
                val freq = xToFreq(px.toFloat(), w)
                val gain = bands.sumOf { band -> calculateBandGain(freq, band).toDouble() }.toFloat()
                Offset(px.toFloat(), gainToY(gain, h))
            }
            path.moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { path.lineTo(it.x, it.y) }
            drawPath(path, primary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

            val fillPath = Path()
            fillPath.moveTo(0f, h / 2)
            points.forEach { fillPath.lineTo(it.x, it.y) }
            fillPath.lineTo(w, h / 2)
            fillPath.close()
            drawPath(fillPath, Brush.verticalGradient(listOf(primary.copy(alpha = 0.3f), Color.Transparent)))
        }
    }
}

private fun freqToX(freq: Float, width: Float): Float {
    val logMin = log10(20f); val logMax = log10(20000f)
    return ((log10(freq) - logMin) / (logMax - logMin)) * width
}

private fun xToFreq(x: Float, width: Float): Float {
    val logMin = log10(20f); val logMax = log10(20000f)
    return 10f.pow(logMin + (x / width) * (logMax - logMin))
}

private fun gainToY(gain: Float, height: Float): Float = height / 2 - (gain / 24f) * (height / 2)
private fun calculateBandGain(freq: Float, band: EQBand): Float {
    val ratio = freq / band.frequency
    val logRatio = log10(ratio.coerceAtLeast(0.0001f))
    val bell = exp(-logRatio * logRatio * band.qFactor * 5f)
    return band.gainDb * bell
}

@Composable
private fun EQBandChip(band: EQBand, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) band.color.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainer)
            .then(Modifier.clickable(onClick = onClick))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(band.color))
        Spacer(Modifier.height(4.dp))
        Text(band.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        Text("${if (band.gainDb >= 0) "+" else ""}${"%.1f".format(band.gainDb)}dB", style = MaterialTheme.typography.labelSmall, color = band.color)
    }
}

@Composable
private fun BandControls(
    band: EQBand,
    onGainChange: (Float) -> Unit,
    onQChange: (Float) -> Unit,
    onTypeChange: (BandType) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(band.color))
                Spacer(Modifier.width(8.dp))
                Text("${band.label} — ${"%.0f".format(band.frequency)}Hz", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gain: ${if (band.gainDb >= 0) "+" else ""}${"%.1f".format(band.gainDb)}dB", modifier = Modifier.width(100.dp))
                Slider(value = band.gainDb, onValueChange = onGainChange, valueRange = -24f..24f, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Q: ${"%.2f".format(band.qFactor)}", modifier = Modifier.width(100.dp))
                Slider(value = band.qFactor, onValueChange = onQChange, valueRange = 0.1f..10f, modifier = Modifier.weight(1f))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(BandType.entries) { type ->
                    FilterChip(selected = band.type == type, onClick = { onTypeChange(type) }, label = { Text(type.label) })
                }
            }
        }
    }
}

private fun Path.moveTo(point: Offset) = moveTo(point.x, point.y)
private fun Path.lineTo(point: Offset) = lineTo(point.x, point.y)
private fun DrawScope.drawPath(path: Path, brush: Brush) = drawPath(path, brush)
