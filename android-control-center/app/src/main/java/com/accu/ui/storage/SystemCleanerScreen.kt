package com.accu.ui.storage

import android.content.Context
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class CleanSchedule(val label: String, val description: String) {
    NEVER("Never", "Manual cleaning only"),
    DAILY("Daily", "Auto-clean at midnight every day"),
    WEEKLY("Weekly", "Auto-clean every Sunday at midnight"),
    ON_CHARGE("On Charge", "Auto-clean when device starts charging"),
    ON_BOOT("On Boot", "Auto-clean after every device restart"),
}

data class JunkCategory(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val size: Long = 0L,
    val fileCount: Int = 0,
    val enabled: Boolean = true,
    val extensions: List<String> = emptyList(),
    /** Shell command to find files in this category (prints matching paths) */
    val findCmd: String = "",
    /** Shell command to delete files in this category */
    val deleteCmd: String = "",
    val isScanning: Boolean = false,
)

private val CATEGORY_DEFINITIONS = listOf(
    JunkCategory(
        id = "apk", name = "Downloaded APKs", description = "APK files in Downloads folder",
        icon = Icons.Default.Android, extensions = listOf(".apk", ".apks", ".xapk"),
        findCmd = "find /sdcard/Download -maxdepth 2 \\( -name '*.apk' -o -name '*.apks' -o -name '*.xapk' \\) 2>/dev/null",
        deleteCmd = "find /sdcard/Download -maxdepth 2 \\( -name '*.apk' -o -name '*.apks' -o -name '*.xapk' \\) -delete 2>/dev/null",
    ),
    JunkCategory(
        id = "tmp", name = "Temporary Files", description = "Temp files from apps and system",
        icon = Icons.Default.Folder, extensions = listOf(".tmp", ".temp", ".bak"),
        findCmd = "find /sdcard -maxdepth 5 \\( -name '*.tmp' -o -name '*.temp' -o -name '*.bak' \\) 2>/dev/null",
        deleteCmd = "find /sdcard -maxdepth 5 \\( -name '*.tmp' -o -name '*.temp' -o -name '*.bak' \\) -delete 2>/dev/null",
    ),
    JunkCategory(
        id = "log", name = "Log Files", description = "System and app log files",
        icon = Icons.Default.Article, extensions = listOf(".log", ".log1", ".log2"),
        findCmd = "find /sdcard -maxdepth 5 \\( -name '*.log' -o -name '*.log1' -o -name '*.log2' \\) 2>/dev/null",
        deleteCmd = "find /sdcard -maxdepth 5 \\( -name '*.log' -o -name '*.log1' -o -name '*.log2' \\) -delete 2>/dev/null",
    ),
    JunkCategory(
        id = "thumb", name = "Thumbnail Cache", description = "Cached media thumbnails",
        icon = Icons.Default.Image, extensions = listOf(".thumbdata"),
        findCmd = "find /sdcard/DCIM -maxdepth 3 -name '.thumbdata*' 2>/dev/null; find /sdcard/.thumbnails -maxdepth 3 2>/dev/null",
        deleteCmd = "find /sdcard/DCIM -maxdepth 3 -name '.thumbdata*' -delete 2>/dev/null; rm -rf /sdcard/.thumbnails 2>/dev/null",
    ),
    JunkCategory(
        id = "crash", name = "Crash Dumps", description = "App crash reports and traces",
        icon = Icons.Default.BugReport, extensions = listOf(".hprof", ".trace", ".anr"),
        findCmd = "find /sdcard -maxdepth 4 \\( -name '*.hprof' -o -name '*.trace' -o -name '*.anr' \\) 2>/dev/null",
        deleteCmd = "find /sdcard -maxdepth 4 \\( -name '*.hprof' -o -name '*.trace' -o -name '*.anr' \\) -delete 2>/dev/null",
    ),
    JunkCategory(
        id = "analytics", name = "Analytics Cache", description = "Crash analytics and telemetry cache",
        icon = Icons.Default.Analytics,
        findCmd = "find /data/data -maxdepth 3 -name 'analytics*' -o -name 'crashlytics*' -o -name 'firebase_crash*' 2>/dev/null",
        deleteCmd = "find /data/data -maxdepth 3 \\( -name 'analytics*' -o -name 'crashlytics*' -o -name 'firebase_crash*' \\) -delete 2>/dev/null",
    ),
)

data class SystemCleanerUiState(
    val categories: List<JunkCategory> = CATEGORY_DEFINITIONS,
    val isScanning: Boolean = false,
    val isCleaning: Boolean = false,
    val hasScanned: Boolean = false,
    val scanProgress: Float = 0f,
    val cleanSchedule: CleanSchedule = CleanSchedule.NEVER,
    val whitelist: List<String> = emptyList(),
    val snackbarMessage: String? = null,
    val lastCleanedBytes: Long = 0L,
)

@HiltViewModel
class SystemCleanerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {

    private val _state = MutableStateFlow(SystemCleanerUiState())
    val state: StateFlow<SystemCleanerUiState> = _state.asStateFlow()

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isScanning = true, scanProgress = 0f) }
            val cats = _state.value.categories
            val updated = cats.toMutableList()
            cats.forEachIndexed { i, cat ->
                _state.update { it.copy(scanProgress = i.toFloat() / cats.size) }
                if (cat.findCmd.isBlank()) {
                    // skip
                } else {
                    try {
                        // Count files and total size
                        val countResult = shizukuUtils.execShizuku("${cat.findCmd} | wc -l")
                        val count = countResult.output.trim().toIntOrNull() ?: 0
                        // Sum sizes
                        val sizeResult = shizukuUtils.execShizuku("${cat.findCmd} | xargs du -sb 2>/dev/null | awk '{s+=\$1} END{print s+0}'")
                        val size = sizeResult.output.trim().toLongOrNull() ?: 0L
                        updated[i] = cat.copy(size = size, fileCount = count)
                    } catch (e: Exception) {
                        Timber.w(e, "Scan failed for category ${cat.id}")
                    }
                }
            }
            _state.update { it.copy(
                categories = updated,
                isScanning = false,
                scanProgress = 1f,
                hasScanned = true,
                snackbarMessage = "Scan complete — ${formatSize(updated.sumOf { it.size })} found in ${updated.count { it.fileCount > 0 }} categories",
            )}
        }
    }

    fun cleanAll() {
        val enabled = _state.value.categories.filter { it.enabled && it.size > 0 }
        if (enabled.isEmpty()) { _state.update { it.copy(snackbarMessage = "Nothing to clean — run Scan first") }; return }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCleaning = true) }
            var totalCleaned = 0L
            var succeededCats = 0
            val updated = _state.value.categories.toMutableList()
            enabled.forEachIndexed { _, cat ->
                val idx = updated.indexOfFirst { it.id == cat.id }
                if (idx < 0 || cat.deleteCmd.isBlank()) return@forEachIndexed
                val result = shizukuUtils.execShizuku(cat.deleteCmd)
                if (result.exitCode == 0 || result.error.isBlank()) {
                    totalCleaned += cat.size
                    succeededCats++
                    updated[idx] = cat.copy(size = 0L, fileCount = 0)
                } else {
                    Timber.w("Clean failed for ${cat.id}: ${result.error}")
                }
            }
            val msg = if (succeededCats == enabled.size) "Cleaned ${formatSize(totalCleaned)} from $succeededCats categories"
                      else "Cleaned $succeededCats/${enabled.size} categories — ${enabled.size - succeededCats} failed (check ACCU connection)"
            _state.update { it.copy(
                categories = updated,
                isCleaning = false,
                lastCleanedBytes = it.lastCleanedBytes + totalCleaned,
                snackbarMessage = msg,
            )}
        }
    }

    fun cleanCategory(catId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cats = _state.value.categories.toMutableList()
            val idx = cats.indexOfFirst { it.id == catId }
            if (idx < 0) return@launch
            val cat = cats[idx]
            if (cat.deleteCmd.isBlank()) {
                _state.update { it.copy(snackbarMessage = "No delete command for ${cat.name}") }
                return@launch
            }
            cats[idx] = cat.copy(isScanning = true)
            _state.update { it.copy(categories = cats.toList()) }
            val result = shizukuUtils.execShizuku(cat.deleteCmd)
            val ok = result.exitCode == 0 || result.error.isBlank()
            cats[idx] = cat.copy(
                size = if (ok) 0L else cat.size,
                fileCount = if (ok) 0 else cat.fileCount,
                isScanning = false,
            )
            _state.update { it.copy(
                categories = cats.toList(),
                snackbarMessage = if (ok) "Cleaned ${cat.name} (${formatSize(cat.size)})"
                                  else "Failed to clean ${cat.name}: ${result.error.take(80)}",
            )}
        }
    }

    fun toggleCategory(catId: String) {
        _state.update { s -> s.copy(categories = s.categories.map { if (it.id == catId) it.copy(enabled = !it.enabled) else it }) }
    }

    fun setSchedule(sched: CleanSchedule) { _state.update { it.copy(cleanSchedule = sched) } }
    fun addWhitelist(path: String) { if (path.isNotBlank()) _state.update { it.copy(whitelist = it.whitelist + path.trim()) } }
    fun removeWhitelist(path: String) { _state.update { it.copy(whitelist = it.whitelist - path) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemCleanerScreen(
    onBack: () -> Unit,
    viewModel: SystemCleanerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var newWhitelistEntry by remember { mutableStateOf("") }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    val enabledCategories = state.categories.filter { it.enabled }
    val totalSize = enabledCategories.sumOf { it.size }
    val totalFiles = enabledCategories.sumOf { it.fileCount }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("System Cleaner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showWhitelistDialog = true }) { Icon(Icons.Default.PlaylistRemove, "Whitelist") }
                    IconButton(onClick = { showScheduleSheet = true }) { Icon(Icons.Default.Schedule, "Schedule") }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Summary card
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
                            Text(
                                if (state.hasScanned) formatSize(totalSize) else "Tap Scan to detect junk",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (state.hasScanned) "$totalFiles files across ${enabledCategories.count { it.size > 0 }} categories"
                                else "Analyzes Downloads, temp files, logs, and more",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (state.isScanning || state.isCleaning) {
                        LinearProgressIndicator(
                            progress = { if (state.isScanning) state.scanProgress else 1f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (state.isScanning) "Scanning… ${(state.scanProgress * 100).toInt()}%" else "Cleaning…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::scan,
                                modifier = Modifier.weight(1f),
                            ) { Icon(Icons.Default.Search, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Scan") }
                            Button(
                                onClick = viewModel::cleanAll,
                                modifier = Modifier.weight(1f),
                                enabled = totalSize > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) { Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Clean All") }
                        }
                    }

                    if (state.lastCleanedBytes > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("${formatSize(state.lastCleanedBytes)} freed this session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (state.cleanSchedule != CleanSchedule.NEVER) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text("Auto-clean: ${state.cleanSchedule.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showScheduleSheet = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Change", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (state.whitelist.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.PlaylistRemove, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text("${state.whitelist.size} path(s) whitelisted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showWhitelistDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Manage", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Text(
                "Junk Categories",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    JunkCategoryCard(
                        category = category,
                        hasScanned = state.hasScanned,
                        onToggle = { viewModel.toggleCategory(category.id) },
                        onClearSingle = { viewModel.cleanCategory(category.id) },
                    )
                }
            }
        }
    }

    // Schedule bottom sheet
    if (showScheduleSheet) {
        ModalBottomSheet(onDismissRequest = { showScheduleSheet = false }) {
            Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Auto-Clean Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("ACCU will automatically clean selected categories on this schedule using WorkManager.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                CleanSchedule.entries.forEach { sched ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.setSchedule(sched); showScheduleSheet = false },
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.cleanSchedule == sched) MaterialTheme.colorScheme.primaryContainer
                                             else MaterialTheme.colorScheme.surfaceContainer
                        ),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.cleanSchedule == sched, onClick = { viewModel.setSchedule(sched); showScheduleSheet = false })
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sched.label, fontWeight = FontWeight.SemiBold)
                                Text(sched.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Whitelist dialog
    if (showWhitelistDialog) {
        AlertDialog(
            onDismissRequest = { showWhitelistDialog = false },
            icon = { Icon(Icons.Default.PlaylistRemove, null) },
            title = { Text("Path Whitelist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paths listed here will never be cleaned, even if they match a junk category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newWhitelistEntry,
                            onValueChange = { newWhitelistEntry = it },
                            placeholder = { Text("/sdcard/important/", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        IconButton(onClick = {
                            viewModel.addWhitelist(newWhitelistEntry)
                            newWhitelistEntry = ""
                        }) { Icon(Icons.Default.Add, null) }
                    }
                    if (state.whitelist.isEmpty()) {
                        Text("No whitelisted paths — all categories cleaned.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        state.whitelist.forEach { path ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderOpen, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(path, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.removeWhitelist(path) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showWhitelistDialog = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun JunkCategoryCard(
    category: JunkCategory,
    hasScanned: Boolean,
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
                if (category.isScanning) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        category.icon, null,
                        tint = if (category.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasScanned) "${formatSize(category.size)} · ${category.fileCount} files"
                        else category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasScanned && category.size > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(checked = category.enabled, onCheckedChange = { onToggle() })
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!hasScanned) Text(category.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (category.extensions.isNotEmpty()) {
                        Text("File types: ${category.extensions.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (hasScanned && category.size > 0) {
                        TextButton(onClick = onClearSingle, enabled = !category.isScanning) {
                            Text("Clear this category only (${formatSize(category.size)})")
                        }
                    } else if (!hasScanned) {
                        Text("Run Scan to see real file counts and sizes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
