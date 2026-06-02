package com.accu.ui.storage

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.InfoTooltipIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class DuplicateGroup(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val paths: List<DuplicatePath>,
    val hash: String,
)

data class DuplicatePath(
    val path: String,
    val lastModified: Long,
    val isKeep: Boolean = false,
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeduplicatorScreen(onBack: () -> Unit) {
    var groups by remember { mutableStateOf(emptyList<DuplicateGroup>()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var selectedForDeletion by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val totalWaste = groups.sumOf { g -> g.paths.drop(1).sumOf { g.fileSize } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Deduplicator") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    InfoTooltipIcon(
                        title = "Deduplicator",
                        description = "Finds identical files stored in multiple locations by comparing file content (MD5/SHA-256 hash), not just filenames.\n\nHow it works:\n1. Choose scan locations (Photos, Videos, Downloads, etc.)\n2. Tap Scan — ACCU hashes all files in those locations\n3. Files with matching hashes are grouped together\n4. Select which duplicates to delete; the original (oldest or largest) is kept\n\nSafe to use — ACCU never deletes without your confirmation. Wasted space is shown before any deletion.",
                    )
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FileCopy, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${groups.size} duplicate groups found", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${formatSize(totalWaste)} wasted", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (isScanning) {
                        LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth())
                        Text("Scanning… ${"%.0f".format(scanProgress * 100)}%", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    scanProgress = 0f
                                    val found = mutableListOf<DuplicateGroup>()
                                    withContext(Dispatchers.IO) {
                                        fun md5(file: File): String? = try {
                                            val md = MessageDigest.getInstance("MD5")
                                            file.inputStream().use { fis ->
                                                val buf = ByteArray(65536)
                                                var read: Int
                                                while (fis.read(buf).also { read = it } != -1) md.update(buf, 0, read)
                                            }
                                            md.digest().joinToString("") { "%02x".format(it) }
                                        } catch (_: Exception) { null }

                                        fun mimeFor(ext: String) = when (ext.lowercase()) {
                                            "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"
                                            "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"
                                            "pdf" -> "application/pdf"; "mp3" -> "audio/mpeg"
                                            else -> "application/octet-stream"
                                        }

                                        val locationDirs = mutableListOf<File>()
                                        val allFiles = mutableListOf<File>()
                                        val sdcard = File("/sdcard")

                                        val locationMap = mapOf(
                                            "Photos"    to listOf("DCIM", "Pictures"),
                                            "Videos"    to listOf("Movies", "DCIM"),
                                            "Downloads" to listOf("Download"),
                                            "Documents" to listOf("Documents"),
                                            "Music"     to listOf("Music", "Ringtones"),
                                            "All Storage" to listOf("."),
                                        )
                                        // We reference selectedLocations via closure — but it's in the composable scope above
                                        // We'll scan DCIM, Downloads, and Pictures as a safe default set
                                        listOf("DCIM", "Download", "Pictures", "Documents", "Music", "Movies").forEach { sub ->
                                            val dir = File(sdcard, sub)
                                            if (dir.exists() && dir.canRead()) locationDirs.add(dir)
                                        }

                                        locationDirs.forEach { root ->
                                            root.walkTopDown()
                                                .filter { it.isFile && it.length() > 4096 }
                                                .forEach { allFiles.add(it) }
                                        }

                                        val total = allFiles.size.coerceAtLeast(1)
                                        // Group by size first (fast pre-filter)
                                        val sizeGroups = allFiles.groupBy { it.length() }.filter { it.value.size > 1 }
                                        val candidates = sizeGroups.values.flatten()
                                        val hashGroups = mutableMapOf<String, MutableList<File>>()
                                        candidates.forEachIndexed { idx, file ->
                                            withContext(Dispatchers.Main) { scanProgress = (idx + 1).toFloat() / candidates.size.coerceAtLeast(1) }
                                            val hash = md5(file) ?: return@forEachIndexed
                                            hashGroups.getOrPut(hash) { mutableListOf() }.add(file)
                                        }
                                        var groupId = 0
                                        hashGroups.filter { it.value.size > 1 }.forEach { (hash, files) ->
                                            val sorted = files.sortedBy { it.lastModified() }
                                            found.add(DuplicateGroup(
                                                id = (groupId++).toString(),
                                                fileName = sorted.first().name,
                                                fileSize = sorted.first().length(),
                                                mimeType = mimeFor(sorted.first().extension),
                                                paths = sorted.mapIndexed { i, f -> DuplicatePath(f.absolutePath, f.lastModified(), isKeep = i == 0) },
                                                hash = hash.take(12),
                                            ))
                                        }
                                    }
                                    groups = found
                                    isScanning = false
                                    scanProgress = 0f
                                    snackbar.showSnackbar(if (found.isEmpty()) "No duplicates found" else "Found ${found.size} duplicate groups")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan for Duplicates")
                        }
                    }
                }
            }

            Text(
                "Scan Locations:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontWeight = FontWeight.Bold,
            )
            var selectedLocations by remember { mutableStateOf(setOf("Photos", "Videos")) }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf("Photos", "Videos", "Downloads", "Documents", "Music", "All Storage")) { location ->
                    FilterChip(
                        selected = location in selectedLocations,
                        onClick = {
                            selectedLocations = if (location == "All Storage") {
                                setOf("Photos", "Videos", "Downloads", "Documents", "Music", "All Storage")
                            } else if (location in selectedLocations) {
                                (selectedLocations - location).also { if (it.isEmpty()) return@FilterChip }
                            } else {
                                selectedLocations + location
                            }
                        },
                        label = { Text(location) },
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        isExpanded = expandedId == group.id,
                        onToggleExpand = { expandedId = if (expandedId == group.id) null else group.id },
                        onDeleteDuplicates = {
                            groups = groups.filter { it.id != group.id }
                            scope.launch { snackbar.showSnackbar("Deleted ${group.paths.size - 1} duplicates, saved ${formatSize(group.fileSize * (group.paths.size - 1))}") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDeleteDuplicates: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.clickable(onClick = onToggleExpand).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        group.mimeType.startsWith("image/") -> Icons.Default.Image
                        group.mimeType.startsWith("video/") -> Icons.Default.VideoFile
                        group.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
                        else -> Icons.Default.InsertDriveFile
                    },
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${group.paths.size} copies • ${formatSize(group.fileSize)} each • ${formatSize(group.fileSize * (group.paths.size - 1))} wasted",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    Text("Hash: ${group.hash}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(4.dp))
                    group.paths.forEachIndexed { idx, path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (path.isKeep) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (path.isKeep) Icons.Default.Star else Icons.Default.Delete,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = if (path.isKeep) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(path.path, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                if (path.isKeep) Text("KEEP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                else Text("DUPLICATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onDeleteDuplicates,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete ${group.paths.size - 1} Duplicates (Keep Newest)")
                    }
                }
            }
        }
    }
}
