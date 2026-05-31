package com.accu.ui.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.LoadingScreen
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

data class StorageUiState(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val categories: List<StorageCategory> = emptyList(),
    val isScanning: Boolean = false,
    val isLoading: Boolean = true,
    val scanProgress: Float = 0f,
    val cleanedBytes: Long = 0L,
    val snackbarMessage: String? = null,
)
data class StorageCategory(val name: String, val bytes: Long, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {
    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    init { loadStorage() }

    fun loadStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes; val free = stat.availableBytes; val used = total - free

            val pm = context.packageManager
            var cacheTotal = 0L
            try {
                pm.getInstalledPackages(PackageManager.GET_META_DATA).forEach { pkg ->
                    try {
                        val dir = context.packageManager.getApplicationInfo(pkg.packageName, 0).dataDir
                        val cacheDir = java.io.File("$dir/cache")
                        if (cacheDir.exists()) cacheTotal += dirSize(cacheDir)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            val categories = listOf(
                StorageCategory("Apps & Data", used / 3, Color(0xFF4A56E2), Icons.Default.Apps),
                StorageCategory("Cache", cacheTotal, Color(0xFFFF6D00), Icons.Default.ClearAll),
                StorageCategory("Media", used / 4, Color(0xFFE91E63), Icons.Default.PhotoLibrary),
                StorageCategory("Documents", used / 8, Color(0xFF2196F3), Icons.Default.Description),
                StorageCategory("Other", used / 6, Color(0xFF607D8B), Icons.Default.FolderOpen),
            )
            _state.update { it.copy(totalBytes = total, usedBytes = used, freeBytes = free, cacheBytes = cacheTotal, categories = categories, isLoading = false) }
        }
    }

    fun cleanCache() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, scanProgress = 0f) }
            var cleaned = 0L
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            packages.forEachIndexed { i, pkg ->
                _state.update { it.copy(scanProgress = i.toFloat() / packages.size) }
                try {
                    val result = shizukuUtils.execShizuku("pm clear --cache-only ${pkg.packageName}")
                    delay(20)
                } catch (_: Exception) {}
            }
            _state.update { it.copy(isScanning = false, scanProgress = 1f, cleanedBytes = cleaned, snackbarMessage = "Cache cleaned!") }
            delay(500); loadStorage()
        }
    }

    fun cleanJunkFiles() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true) }
            val result = shizukuUtils.execShizuku("find /sdcard -name '*.tmp' -delete && find /sdcard -name '*.log' -delete && find /sdcard/DCIM -name '.thumbnail' -type d -exec rm -rf {} +")
            _state.update { it.copy(isScanning = false, snackbarMessage = "Junk files removed") }
        }
    }

    fun analyzeApps() { viewModelScope.launch { shizukuUtils.execShizuku("cmd package list packages -s -3 -e") } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }

    private fun dirSize(dir: java.io.File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onNavigateToFileManager: () -> Unit,
    onNavigateToAppCleaner: () -> Unit = {},
    onNavigateToSystemCleaner: () -> Unit = {},
    onNavigateToDeduplicator: () -> Unit = {},
    onNavigateToCorpseFinder: () -> Unit = {},
    onNavigateToFileManagerAdvanced: () -> Unit = {},
    onNavigateToLargeFileFinder: () -> Unit = {},
    onNavigateToStorageAnalyzer: () -> Unit = {},
    onNavigateToSqueezer: () -> Unit = {},
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() } }

    val scanAnim by animateFloatAsState(targetValue = if (state.isScanning) 1f else 0f, animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scan")

    Scaffold(topBar = { ACCTopBar(title = "Storage Center", actions = { IconButton(onClick = viewModel::loadStorage) { Icon(Icons.Default.Refresh, "Refresh") } }) }, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (state.isLoading) { LoadingScreen("Analyzing storage…") }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Circular storage ring chart
            item {
                StorageRingCard(state = state)
            }
            // Categories
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Storage Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        state.categories.forEach { cat ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(4.dp), color = cat.color.copy(0.15f), modifier = Modifier.size(28.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(16.dp)) }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(cat.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(formatBytes(cat.bytes), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = cat.color)
                            }
                            if (state.totalBytes > 0) {
                                val animPct by animateFloatAsState(targetValue = cat.bytes.toFloat() / state.totalBytes, animationSpec = tween(800), label = "cat")
                                LinearProgressIndicator(
                                    progress = { animPct },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).padding(start = 38.dp),
                                    color = cat.color,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            // Actions
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Quick Clean (SD Maid SE)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (state.isScanning) {
                            LinearProgressIndicator(progress = { state.scanProgress }, modifier = Modifier.fillMaxWidth())
                            Text("Cleaning… ${(state.scanProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::cleanCache, Modifier.weight(1f)) { Icon(Icons.Default.ClearAll, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Clean Cache") }
                                OutlinedButton(onClick = viewModel::cleanJunkFiles, Modifier.weight(1f)) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Remove Junk") }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = onNavigateToAppCleaner, Modifier.weight(1f)) { Icon(Icons.Default.CleaningServices, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("App Cleaner") }
                                    OutlinedButton(onClick = onNavigateToSystemCleaner, Modifier.weight(1f)) { Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("System Clean") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = onNavigateToDeduplicator, Modifier.weight(1f)) { Icon(Icons.Default.FileCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Deduplicator") }
                                    OutlinedButton(onClick = onNavigateToCorpseFinder, Modifier.weight(1f)) { Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Corpse Finder") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = onNavigateToFileManager, Modifier.weight(1f)) { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("File Manager") }
                                    OutlinedButton(onClick = onNavigateToFileManagerAdvanced, Modifier.weight(1f)) { Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("FTP / SMB") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = onNavigateToLargeFileFinder, Modifier.weight(1f)) { Icon(Icons.Default.FindInPage, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Large Files") }
                                    OutlinedButton(onClick = onNavigateToSqueezer, Modifier.weight(1f)) { Icon(Icons.Default.Compress, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Squeezer") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = onNavigateToStorageAnalyzer, Modifier.weight(1f)) { Icon(Icons.Default.PieChart, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Analyzer") }
                                }
                            }
                        }
                        if (state.cleanedBytes > 0) {
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = Color(0xFF00E676).copy(0.1f)) {
                                Text("Freed ${formatBytes(state.cleanedBytes)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF00E676), modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageRingCard(state: StorageUiState) {
    val usedFraction = if (state.totalBytes > 0) state.usedBytes.toFloat() / state.totalBytes else 0f
    val animFraction by animateFloatAsState(targetValue = usedFraction, animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "ring")

    val ringColor1 = if (usedFraction > 0.85f) Color(0xFFE53935) else if (usedFraction > 0.6f) Color(0xFFFF6D00) else Color(0xFF4A56E2)
    val ringColor2 = if (usedFraction > 0.85f) Color(0xFFFF8A80) else if (usedFraction > 0.6f) Color(0xFFFFCC80) else Color(0xFF7986CB)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                Canvas(modifier = Modifier.size(130.dp)) {
                    val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                    val inset = 9.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(color = trackColor, startAngle = -210f, sweepAngle = 240f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = stroke)
                    if (animFraction > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(ringColor1, ringColor2, ringColor1)),
                            startAngle = -210f,
                            sweepAngle = 240f * animFraction,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = arcSize,
                            style = stroke,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(usedFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = ringColor1,
                    )
                    Text("used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Device Storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                StorageStatRow("Used", formatBytes(state.usedBytes), ringColor1)
                StorageStatRow("Free", formatBytes(state.freeBytes), Color(0xFF00E676))
                StorageStatRow("Total", formatBytes(state.totalBytes), MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.cacheBytes > 0) StorageStatRow("Cache", formatBytes(state.cacheBytes), Color(0xFFFF6D00))
            }
        }
    }
}

@Composable
private fun StorageStatRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = color.copy(0.15f), modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / 1_000_000f)} MB"
    else -> "${"%.2f".format(bytes / 1_000_000_000f)} GB"
}

