package com.accu.ui.storage

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class OrphanedData(
    val id: String,
    val path: String,
    val size: Long,
    val type: OrphanType,
    val relatedPackage: String? = null,
    val isSelected: Boolean = false,
)

enum class OrphanType(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val description: String) {
    APP_DATA("App Data", Icons.Default.FolderOff, "Data folder from uninstalled app"),
    CACHE("Orphaned Cache", Icons.Default.Cached, "Cache from app no longer installed"),
    ACCOUNT("Account Data", Icons.Default.AccountCircle, "Account data from removed app"),
    DOWNLOAD("Incomplete Download", Icons.Default.DownloadForOffline, "Partial download files"),
    MEDIA("Orphaned Media", Icons.Default.BrokenImage, "Media files without parent app"),
    SDK_DATA("SDK Data", Icons.Default.Extension, "Analytics/SDK data from removed app"),
}

val SAMPLE_ORPHANS = listOf(
    OrphanedData("1", "/sdcard/Android/data/com.deleted.app1/", 45_000_000L, OrphanType.APP_DATA, "com.deleted.app1"),
    OrphanedData("2", "/sdcard/Android/data/com.deleted.app2/files/", 12_000_000L, OrphanType.APP_DATA, "com.deleted.app2"),
    OrphanedData("3", "/sdcard/.com.facebook.ads/", 8_000_000L, OrphanType.SDK_DATA, "com.facebook.katana"),
    OrphanedData("4", "/sdcard/Android/data/com.old.game/cache/", 234_000_000L, OrphanType.CACHE, "com.old.game"),
    OrphanedData("5", "/sdcard/Download/.part_video.mp4", 1_500_000_000L, OrphanType.DOWNLOAD),
    OrphanedData("6", "/sdcard/Android/data/com.uninstalled.music/media/", 3_400_000_000L, OrphanType.MEDIA, "com.uninstalled.music"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorpseFinderScreen(onBack: () -> Unit) {
    var orphans by remember { mutableStateOf(SAMPLE_ORPHANS) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf<OrphanType?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val filtered = if (typeFilter == null) orphans else orphans.filter { it.type == typeFilter }
    val selectedItems = orphans.filter { it.isSelected }
    val totalWaste = filtered.sumOf { it.size }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Corpse Finder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        },
        bottomBar = {
            if (selectedItems.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${selectedItems.size} selected", fontWeight = FontWeight.Bold)
                            Text(formatSize(selectedItems.sumOf { it.size }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val selected = orphans.filter { it.isSelected }
                                    val totalSize = selected.sumOf { it.size }
                                    orphans = orphans.filter { !it.isSelected }
                                    snackbar.showSnackbar("Deleted ${selected.size} orphaned items (${formatSize(totalSize)} freed)")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Delete Selected") }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${orphans.size} orphaned items • ${formatSize(totalWaste)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Data from apps that are no longer installed", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (isScanning) {
                        LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth())
                        Text("Scanning for orphaned data…", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    repeat(25) { i -> delay(80); scanProgress = (i + 1) / 25f }
                                    isScanning = false
                                    scanProgress = 0f
                                    snackbar.showSnackbar("Found ${orphans.size} orphaned data entries")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan for Orphaned Data")
                        }
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("All") }) }
                items(OrphanType.entries) { type ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { typeFilter = if (typeFilter == type) null else type },
                        label = { Text(type.label) },
                        leadingIcon = { Icon(type.icon, null, modifier = Modifier.size(14.dp)) },
                    )
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { orphan ->
                    val idx = orphans.indexOfFirst { it.id == orphan.id }
                    OrphanCard(
                        orphan = orphan,
                        isExpanded = expandedId == orphan.id,
                        onToggleExpand = { expandedId = if (expandedId == orphan.id) null else orphan.id },
                        onToggleSelect = { if (idx != -1) orphans = orphans.toMutableList().also { it[idx] = orphan.copy(isSelected = !orphan.isSelected) } },
                        onDelete = {
                            scope.launch {
                                orphans = orphans.filter { it.id != orphan.id }
                                snackbar.showSnackbar("Deleted ${formatSize(orphan.size)}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrphanCard(orphan: OrphanedData, isExpanded: Boolean, onToggleExpand: () -> Unit, onToggleSelect: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (orphan.isSelected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.clickable(onClick = onToggleExpand).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = orphan.isSelected, onCheckedChange = { onToggleSelect() })
                Spacer(Modifier.width(6.dp))
                Icon(orphan.type.icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(orphan.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(formatSize(orphan.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    orphan.relatedPackage?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline) }
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Text(orphan.type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(orphan.path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {}) { Text("View Files") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                    }
                }
            }
        }
    }
}
