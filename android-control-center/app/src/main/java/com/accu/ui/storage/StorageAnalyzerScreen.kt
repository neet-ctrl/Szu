package com.accu.ui.storage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlin.math.roundToInt

data class StorageSegment(val label: String, val gbUsed: Float, val color: Color)
data class StorageAppEntry(val name: String, val pkg: String, val appSizeMb: Float, val dataSizeMb: Float, val cacheSizeMb: Float) {
    val totalMb get() = appSizeMb + dataSizeMb + cacheSizeMb
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(onBack: () -> Unit = {}) {
    var selectedVolume by remember { mutableStateOf("Internal Storage") }
    val volumes = listOf("Internal Storage", "SD Card (128 GB)")

    val segments = remember {
        listOf(
            StorageSegment("Apps", 18.4f, Color(0xFF6750A4)),
            StorageSegment("Media", 32.7f, Color(0xFF0288D1)),
            StorageSegment("Documents", 4.2f, Color(0xFF2E7D32)),
            StorageSegment("Downloads", 8.1f, Color(0xFFF57C00)),
            StorageSegment("System", 12.8f, Color(0xFFD32F2F)),
            StorageSegment("Other", 2.3f, Color(0xFF795548)),
            StorageSegment("Free", 49.5f, Color(0xFFE0E0E0)),
        )
    }
    val totalGb = 128f
    val usedGb = segments.dropLast(1).sumOf { it.gbUsed.toDouble() }.toFloat()
    val freeGb = totalGb - usedGb

    val apps = remember {
        listOf(
            StorageAppEntry("YouTube", "com.google.android.youtube", 102.4f, 2048f, 512f),
            StorageAppEntry("Instagram", "com.instagram.android", 87.3f, 1500f, 800f),
            StorageAppEntry("Chrome", "com.android.chrome", 145.2f, 890f, 380f),
            StorageAppEntry("Spotify", "com.spotify.music", 72.8f, 4200f, 120f),
            StorageAppEntry("WhatsApp", "com.whatsapp", 68.4f, 2048f, 180f),
            StorageAppEntry("Maps", "com.google.android.apps.maps", 188.6f, 1024f, 256f),
            StorageAppEntry("Slack", "com.slack", 65.1f, 380f, 94f),
            StorageAppEntry("Gmail", "com.google.android.gm", 38.2f, 210f, 48f),
            StorageAppEntry("Facebook", "com.facebook.katana", 155.8f, 3200f, 1400f),
            StorageAppEntry("Netflix", "com.netflix.mediaclient", 78.3f, 12800f, 200f),
        ).sortedByDescending { it.totalMb }
    }

    var viewMode by remember { mutableStateOf("Apps") }
    var sortMode by remember { mutableStateOf("Total") }

    val sortedApps = when (sortMode) {
        "App size" -> apps.sortedByDescending { it.appSizeMb }
        "Data" -> apps.sortedByDescending { it.dataSizeMb }
        "Cache" -> apps.sortedByDescending { it.cacheSizeMb }
        else -> apps.sortedByDescending { it.totalMb }
    }

    Scaffold(topBar = { ACCTopBar(title = "Storage Analyzer", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Volume selector
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    volumes.forEach { vol ->
                        FilterChip(selected = selectedVolume == vol, onClick = { selectedVolume = vol }, label = { Text(vol, fontSize = 12.sp) })
                    }
                }
            }

            // Usage overview card
            item {
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${"%.1f".format(usedGb)} GB used", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                                Text("of ${totalGb.roundToInt()} GB (${"%.1f".format(freeGb)} GB free)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Storage, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(12.dp))
                        // Bar chart
                        LinearProgressIndicator(
                            progress = { (usedGb / totalGb).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        // Pie chart via canvas
                        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                            val cx = size.width / 2f; val cy = size.height / 2f
                            val r = (size.height / 2f) * 0.85f
                            var startAngle = -90f
                            val total = segments.sumOf { it.gbUsed.toDouble() }.toFloat()
                            segments.forEach { seg ->
                                val sweep = (seg.gbUsed / total) * 360f
                                drawArc(seg.color, startAngle, sweep, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
                                startAngle += sweep
                            }
                            // Inner circle (donut)
                            drawCircle(Color.White, r * 0.6f, Offset(cx, cy))
                        }
                        // Legend
                        segments.dropLast(1).chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { seg ->
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).background(seg.color))
                                        Spacer(Modifier.width(3.dp))
                                        Text("${seg.label}\n${"%.1f".format(seg.gbUsed)}GB", fontSize = 9.sp, lineHeight = 12.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }

            // Tab: Apps / Categories
            item {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Apps", "Categories").forEach { mode ->
                        FilterChip(selected = viewMode == mode, onClick = { viewMode = mode }, label = { Text(mode) })
                    }
                    Spacer(Modifier.weight(1f))
                    if (viewMode == "Apps") {
                        var showSort by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { showSort = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Icon(Icons.Default.Sort, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(sortMode, fontSize = 12.sp)
                            }
                            DropdownMenu(showSort, { showSort = false }) {
                                listOf("Total", "App size", "Data", "Cache").forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = { sortMode = s; showSort = false })
                                }
                            }
                        }
                    }
                }
            }

            if (viewMode == "Apps") {
                items(sortedApps, key = { it.pkg }) { app ->
                    val totalGbApp = app.totalMb / 1024f
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = {
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("App: ${formatMb(app.appSizeMb)}", fontSize = 11.sp)
                                    Text("Data: ${formatMb(app.dataSizeMb)}", fontSize = 11.sp)
                                    Text("Cache: ${formatMb(app.cacheSizeMb)}", fontSize = 11.sp)
                                }
                                LinearProgressIndicator(progress = { (app.totalMb / sortedApps.first().totalMb).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp))
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Text(formatMb(app.totalMb), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) },
                        modifier = Modifier.clickable {}
                    )
                    HorizontalDivider()
                }
            } else {
                items(segments.dropLast(1)) { seg ->
                    ListItem(
                        headlineContent = { Text(seg.label) },
                        supportingContent = { LinearProgressIndicator(progress = { seg.gbUsed / usedGb }, modifier = Modifier.fillMaxWidth().height(4.dp), color = seg.color) },
                        leadingContent = { Box(Modifier.size(16.dp).background(seg.color)) },
                        trailingContent = { Text("${"%.1f".format(seg.gbUsed)} GB", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatMb(mb: Float) = if (mb >= 1024) "${"%.1f".format(mb / 1024f)} GB" else "${mb.roundToInt()} MB"
