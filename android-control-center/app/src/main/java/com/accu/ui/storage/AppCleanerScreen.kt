package com.accu.ui.storage

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AppCacheInfo(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    val selected: Boolean = false,
)

fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}

data class AppCleanerUiState(
    val apps: List<AppCacheInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false,
    val cleanedBytes: Long = 0L,
    val snackbarMessage: String? = null,
    val scanError: String? = null,
    val sortBy: String = "Cache Size",
)

@HiltViewModel
class AppCleanerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {

    private val _state = MutableStateFlow(AppCleanerUiState())
    val state: StateFlow<AppCleanerUiState> = _state.asStateFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, scanError = null) }
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

                // One privileged command to get all cache sizes at once
                val duResult = shizukuUtils.execShizuku(
                    "du -sb /data/data/*/cache 2>/dev/null; du -sb /data/user/0/*/cache 2>/dev/null"
                )

                // Parse: "12345\t/data/data/com.example.app/cache"
                val sizeMap = mutableMapOf<String, Long>()
                duResult.output.lines().forEach { line ->
                    val parts = line.trim().split("\t")
                    if (parts.size >= 2) {
                        val size = parts[0].toLongOrNull() ?: 0L
                        val path = parts[1]
                        val pkg = path
                            .removePrefix("/data/data/").removePrefix("/data/user/0/")
                            .removeSuffix("/cache")
                        if (pkg.isNotBlank() && pkg.contains(".")) {
                            // Keep larger size if both paths found
                            sizeMap[pkg] = maxOf(sizeMap.getOrDefault(pkg, 0L), size)
                        }
                    }
                }

                // If privileged shell not available, fall back to readable cache dirs
                if (sizeMap.isEmpty()) {
                    packages.forEach { pkg ->
                        try {
                            val ai = pkg.applicationInfo ?: return@forEach
                            val cacheDir = java.io.File("${ai.dataDir}/cache")
                            if (cacheDir.exists() && cacheDir.canRead()) {
                                val size = cacheDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
                                if (size > 0) sizeMap[pkg.packageName] = size
                            }
                        } catch (_: Exception) {}
                    }
                }

                val apps = packages.mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    val cacheBytes = sizeMap[pkg.packageName] ?: 0L
                    if (cacheBytes == 0L) return@mapNotNull null
                    val appName = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pkg.packageName }
                    AppCacheInfo(pkg.packageName, appName, cacheBytes)
                }.sortedByDescending { it.cacheSize }

                val errorMsg = if (apps.isEmpty() && !shizukuUtils.isShizukuAvailable()) {
                    "ACCU not connected — connect via Wireless ADB or root for full cache visibility"
                } else null

                _state.update { it.copy(apps = apps, isLoading = false, scanError = errorMsg) }
            } catch (e: Exception) {
                Timber.e(e, "AppCleaner scan failed")
                _state.update { it.copy(isLoading = false, scanError = "Scan failed: ${e.message}") }
            }
        }
    }

    fun clearSelected() {
        val selected = _state.value.apps.filter { it.selected }
        if (selected.isEmpty()) return
        val totalBytes = selected.sumOf { it.cacheSize }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCleaning = true) }
            var cleared = 0
            var totalFreed = 0L
            selected.forEach { app ->
                val result = shizukuUtils.execShizuku("pm clear --cache-only ${app.packageName}")
                if (result.isSuccess) {
                    cleared++
                    totalFreed += app.cacheSize
                } else {
                    Timber.w("Failed to clear cache for ${app.packageName}: ${result.error}")
                }
            }
            val msg = when {
                cleared == selected.size -> "Cleared ${formatSize(totalFreed)} from $cleared apps"
                cleared > 0 -> "Cleared $cleared/${selected.size} apps — ${selected.size - cleared} failed (check ACCU connection)"
                else -> "Failed to clear cache — ensure ACCU is connected"
            }
            _state.update { s ->
                val updatedApps = s.apps.map { app ->
                    if (app.selected && cleared > 0) app.copy(cacheSize = 0L, selected = false) else app
                }.filter { it.cacheSize > 0 }
                s.copy(
                    isCleaning = false,
                    apps = updatedApps,
                    cleanedBytes = s.cleanedBytes + totalFreed,
                    snackbarMessage = msg,
                )
            }
        }
    }

    fun toggleSelection(pkg: String) {
        _state.update { s -> s.copy(apps = s.apps.map { if (it.packageName == pkg) it.copy(selected = !it.selected) else it }) }
    }

    fun toggleSelectAll() {
        val allSelected = _state.value.apps.all { it.selected }
        _state.update { s -> s.copy(apps = s.apps.map { it.copy(selected = !allSelected) }) }
    }

    fun setSortBy(sort: String) { _state.update { it.copy(sortBy = sort) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCleanerScreen(
    onBack: () -> Unit,
    viewModel: AppCleanerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    val sortedApps = remember(state.apps, state.sortBy) {
        when (state.sortBy) {
            "App Name" -> state.apps.sortedBy { it.appName }
            else -> state.apps.sortedByDescending { it.cacheSize }
        }
    }
    val selectedApps = state.apps.filter { it.selected }
    val totalCacheSelected = selectedApps.sumOf { it.cacheSize }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("App Cleaner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    var showSort by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSort = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(showSort, { showSort = false }) {
                            listOf("Cache Size", "App Name").forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = { viewModel.setSortBy(s); showSort = false },
                                    leadingIcon = { if (state.sortBy == s) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = viewModel::scan) { Icon(Icons.Default.Refresh, "Rescan") }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedApps.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${selectedApps.size} apps selected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${formatSize(totalCacheSelected)} to free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = viewModel::clearSelected,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !state.isCleaning,
                        ) {
                            if (state.isCleaning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Clearing…")
                            } else {
                                Icon(Icons.Default.CleaningServices, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear Cache")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // Session freed banner
            if (state.cleanedBytes > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("${formatSize(state.cleanedBytes)} freed this session", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Error / no privilege warning
            state.scanError?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (!state.isLoading && state.apps.isEmpty() && state.scanError == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("No cache found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("All app caches are empty or inaccessible", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${state.apps.size} apps · ${formatSize(state.apps.sumOf { it.cacheSize })} total cache",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    TextButton(onClick = viewModel::toggleSelectAll) {
                        Text(if (state.apps.all { it.selected }) "Deselect All" else "Select All")
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedApps, key = { it.packageName }) { app ->
                        AppCacheCard(app = app, context = context, onToggle = { viewModel.toggleSelection(app.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCacheCard(app: AppCacheInfo, context: android.content.Context, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                             else MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = app.selected, onCheckedChange = { onToggle() })
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("android.resource://${app.packageName}/mipmap/ic_launcher")
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatSize(app.cacheSize),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (app.cacheSize > 100 * 1024 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text("cache", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
