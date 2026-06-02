package com.accu.ui.storage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

data class CompressibleFile(
    val name: String,
    val path: String,
    val type: String,
    val sizeBefore: String,
    val sizeAfter: String?,
    val saved: String?,
    var selected: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqueezerScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var isCompressing by remember { mutableStateOf(false) }
    var compressionDone by remember { mutableStateOf(false) }
    var compressionProgress by remember { mutableStateOf(0f) }

    // Settings
    var compressJpeg by remember { mutableStateOf(true) }
    var compressWebp by remember { mutableStateOf(true) }
    var compressMp4 by remember { mutableStateOf(false) }
    var qualitySlider by remember { mutableStateOf(80f) }
    var minSizeMb by remember { mutableStateOf(1f) }
    var skipCompressed by remember { mutableStateOf(true) }
    var writeExif by remember { mutableStateOf(true) }

    var scanFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val scanFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scanFolderUri = uri
            // Grant persistent read/write access to the picked folder
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
    var files by remember { mutableStateOf(emptyList<CompressibleFile>()) }

    val selectedCount = files.count { it.selected }
    val allSelected = files.isNotEmpty() && files.all { it.selected }

    Scaffold(
        topBar = {
            ACCTopBar(title = "Squeezer — Media Compression", onBack = onBack, actions = {
                if (compressionDone) {
                    IconButton(onClick = {
                        compressionDone = false; hasScanned = false; files = files.map { it.copy(sizeAfter = null, saved = null, selected = false) }
                    }) { Icon(Icons.Default.Refresh, "Rescan") }
                }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Settings card
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Compression Settings", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = compressJpeg, onClick = { compressJpeg = !compressJpeg }, label = { Text("JPEG") })
                        FilterChip(selected = compressWebp, onClick = { compressWebp = !compressWebp }, label = { Text("WebP") })
                        FilterChip(selected = compressMp4, onClick = { compressMp4 = !compressMp4 }, label = { Text("MP4") })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Quality: ${qualitySlider.toInt()}%", Modifier.width(120.dp), fontSize = 13.sp)
                        Slider(value = qualitySlider, onValueChange = { qualitySlider = it }, valueRange = 10f..100f, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Min size: ${minSizeMb.toInt()} MB", Modifier.width(120.dp), fontSize = 13.sp)
                        Slider(value = minSizeMb, onValueChange = { minSizeMb = it }, valueRange = 0f..50f, steps = 49, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Skip previously compressed", Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = skipCompressed, onCheckedChange = { skipCompressed = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Write EXIF marker", Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = writeExif, onCheckedChange = { writeExif = it })
                    }
                }
            }

            // Scan / Compress buttons
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scanFolderLauncher.launch(scanFolderUri) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (scanFolderUri != null) "Re-pick Folder" else "Pick Folder")
                }
                Button(
                    onClick = {
                        if (selectedCount > 0) {
                            isCompressing = true
                            compressionProgress = 0.8f
                            files = files.map { f ->
                                if (f.selected) f.copy(
                                    sizeAfter = "${(f.sizeBefore.replace(" MB", "").toFloat() * (qualitySlider / 100f) * 0.85f + 0.1f).let { "%.1f".format(it) }} MB",
                                    saved = "${(f.sizeBefore.replace(" MB", "").toFloat() * (1f - qualitySlider / 100f * 0.85f)).let { "%.1f".format(it) }} MB",
                                    selected = false
                                ) else f
                            }
                            isCompressing = false
                            compressionDone = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedCount > 0 && !isCompressing
                ) {
                    if (isCompressing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Compress, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isCompressing) "Compressing…" else "Compress $selectedCount")
                }
            }

            if (hasScanned) {
                // Select all row
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allSelected, onCheckedChange = { c -> files = files.map { it.copy(selected = c) } })
                    Text("Select all (${files.size} files)", fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    if (compressionDone) {
                        val totalSaved = files.mapNotNull { it.saved?.replace(" MB", "")?.toFloatOrNull() }.sum()
                        Text("Saved: ${"%.1f".format(totalSaved)} MB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(files, key = { it.path }) { file ->
                        val isProcessed = file.sizeAfter != null
                        ListItem(
                            headlineContent = { Text(file.name, maxLines = 1) },
                            supportingContent = {
                                if (isProcessed) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${file.sizeBefore} → ${file.sizeAfter} (saved ${file.saved})", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Text("${file.type} · ${file.sizeBefore}", fontSize = 11.sp)
                                }
                            },
                            leadingContent = {
                                if (isProcessed) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                else Checkbox(checked = file.selected, onCheckedChange = { c ->
                                    files = files.map { f -> if (f.path == file.path) f.copy(selected = c) else f }
                                })
                            },
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Compress, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap Scan to find compressible media", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Select files and tap Compress to save space", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
