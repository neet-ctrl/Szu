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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class JunkCategory(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val size: Long,
    val fileCount: Int,
    val enabled: Boolean = true,
    val extensions: List<String> = emptyList(),
)

val JUNK_CATEGORIES = listOf(
    JunkCategory("apk", "Downloaded APKs", "APK files in Downloads folder", Icons.Default.Android, 234 * 1024 * 1024L, 12, extensions = listOf(".apk", ".apks", ".xapk")),
    JunkCategory("tmp", "Temporary Files", "Temp files from apps and system", Icons.Default.Folder, 89 * 1024 * 1024L, 234, extensions = listOf(".tmp", ".temp", ".bak", "~")),
    JunkCategory("log", "Log Files", "System and app log files", Icons.Default.Article, 45 * 1024 * 1024L, 567, extensions = listOf(".log", ".log1", ".log2")),
    JunkCategory("thumb", "Thumbnail Cache", "Cached media thumbnails", Icons.Default.Image, 123 * 1024 * 1024L, 1234, extensions = listOf(".thumbdata")),
    JunkCategory("crash", "Crash Dumps", "App crash reports and traces", Icons.Default.BugReport, 34 * 1024 * 1024L, 89, extensions = listOf(".hprof", ".trace", ".anr")),
    JunkCategory("obsolete", "Obsolete Files", "Files from uninstalled apps", Icons.Default.Inventory, 178 * 1024 * 1024L, 456, extensions = listOf()),
    JunkCategory("empty", "Empty Folders", "Empty directories wasting inode space", Icons.Default.FolderOpen, 0L, 89),
    JunkCategory("analytics", "Analytics Data", "Crash analytics and telemetry cache", Icons.Default.Analytics, 23 * 1024 * 1024L, 34),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemCleanerScreen(onBack: () -> Unit) {
    val categories = remember { mutableStateListOf(*JUNK_CATEGORIES.toTypedArray()) }
    var isScanning by remember { mutableStateOf(false) }
    var isCleaning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val enabledCategories = categories.filter { it.enabled }
    val totalSize = enabledCategories.sumOf { it.size }
    val totalFiles = enabledCategories.sumOf { it.fileCount }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("System Cleaner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(formatSize(totalSize), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("$totalFiles files across ${enabledCategories.size} categories", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (isScanning) {
                        LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isScanning = true
                                        repeat(30) { i -> delay(80); scanProgress = i / 30f }
                                        scanProgress = 1f
                                        isScanning = false
                                        snackbar.showSnackbar("Scan complete — ${formatSize(totalSize)} found")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Scan") }
                            Button(
                                onClick = {
                                    scope.launch {
                                        isCleaning = true
                                        delay(2500)
                                        val cleaned = totalSize
                                        categories.replaceAll { it.copy(size = 0L, fileCount = 0) }
                                        isCleaning = false
                                        snackbar.showSnackbar("Cleaned ${formatSize(cleaned)} successfully!")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isCleaning && totalSize > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                if (isCleaning) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                                else Text("Clean All")
                            }
                        }
                    }
                }
            }

            Text("Junk Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { category ->
                    val idx = categories.indexOfFirst { it.id == category.id }
                    JunkCategoryCard(
                        category = category,
                        onToggle = { if (idx != -1) categories[idx] = category.copy(enabled = !category.enabled) },
                        onClearSingle = {
                            scope.launch {
                                if (idx != -1) categories[idx] = category.copy(size = 0L, fileCount = 0)
                                snackbar.showSnackbar("Cleaned ${category.name}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun JunkCategoryCard(
    category: JunkCategory,
    onToggle: () -> Unit,
    onClearSingle: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(category.icon, null, tint = if (category.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${formatSize(category.size)} • ${category.fileCount} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(checked = category.enabled, onCheckedChange = { onToggle() })
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(category.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (category.extensions.isNotEmpty()) {
                        Text("File types: ${category.extensions.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (category.size > 0) {
                        TextButton(onClick = onClearSingle) {
                            Text("Clear this category only")
                        }
                    }
                }
            }
        }
    }
}
