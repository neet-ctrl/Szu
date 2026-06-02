package com.accu.ui.storage

import android.content.Context
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.InfoTooltipIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────

data class LargeFileItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val category: FileCategory,
    val isSelected: Boolean = false,
)

enum class FileCategory(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color) {
    VIDEO("Video",    Icons.Default.VideoFile,    Color(0xFFE53935)),
    IMAGE("Image",    Icons.Default.Image,         Color(0xFF8E24AA)),
    AUDIO("Audio",    Icons.Default.AudioFile,     Color(0xFF1E88E5)),
    DOCUMENT("Doc",   Icons.Default.Description,  Color(0xFF43A047)),
    ARCHIVE("Archive",Icons.Default.Archive,       Color(0xFFFF8F00)),
    APK("APK",        Icons.Default.Android,       Color(0xFF3DDC84)),
    DATA("Data",      Icons.Default.Storage,       Color(0xFF607D8B)),
    OTHER("Other",    Icons.Default.InsertDriveFile,Color(0xFF757575)),
}

private fun extensionToCategory(ext: String): FileCategory = when (ext.lowercase()) {
    "mp4","mkv","avi","mov","webm","flv","wmv","3gp","ts" -> FileCategory.VIDEO
    "jpg","jpeg","png","gif","webp","bmp","tiff","heic","raw" -> FileCategory.IMAGE
    "mp3","wav","flac","aac","ogg","m4a","opus" -> FileCategory.AUDIO
    "pdf","doc","docx","xls","xlsx","ppt","pptx","txt","odt" -> FileCategory.DOCUMENT
    "zip","rar","7z","tar","gz","bz2","xz","lz4" -> FileCategory.ARCHIVE
    "apk","xapk","apks" -> FileCategory.APK
    "db","sqlite","dat","bin" -> FileCategory.DATA
    else -> FileCategory.OTHER
}

enum class SizeThreshold(val label: String, val bytes: Long) {
    MB10("10 MB", 10L * 1024 * 1024),
    MB50("50 MB", 50L * 1024 * 1024),
    MB100("100 MB", 100L * 1024 * 1024),
    MB500("500 MB", 500L * 1024 * 1024),
    GB1("1 GB", 1L * 1024 * 1024 * 1024),
}

enum class LFSortMode(val label: String) {
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME("Name"),
}

data class LargeFileState(
    val files: List<LargeFileItem> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val currentScanPath: String = "",
    val scannedCount: Int = 0,
    val threshold: SizeThreshold = SizeThreshold.MB50,
    val sortMode: LFSortMode = LFSortMode.SIZE_DESC,
    val filterCategory: FileCategory? = null,
    val searchQuery: String = "",
    val selectedFiles: Set<String> = emptySet(),
    val totalSizeBytes: Long = 0L,
    val snackbarMessage: String? = null,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class LargeFileFinderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LargeFileState())
    val state: StateFlow<LargeFileState> = _state.asStateFlow()

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isScanning = true, files = emptyList(), scannedCount = 0, selectedFiles = emptySet()) }
            if (connectionManager.isRemoteSession()) {
                scanViaAdb()
            } else {
                scanLocal()
            }
            _state.update { it.copy(isScanning = false, scanProgress = 1f) }
        }
    }

    private suspend fun scanViaAdb() {
        val threshold = _state.value.threshold
        val minKb = threshold.bytes / 1024
        _state.update { it.copy(currentScanPath = "Scanning via ADB…") }
        // Single stat call per file (format: "size mtime path") — avoids two-stat-per-file
        // overhead that made the old loop extremely slow and prone to exec timeouts.
        val result = connectionManager.exec(
            "find /sdcard -type f -size +${minKb}k 2>/dev/null | while IFS= read -r f; do" +
            " stat -c '%s %Y %n' \"\$f\" 2>/dev/null;" +
            " done"
        )
        val found = mutableListOf<LargeFileItem>()
        result.output.lines().filter { it.isNotBlank() }.forEach { line ->
            try {
                val parts = line.split(" ", limit = 3)
                if (parts.size < 3) return@forEach
                val sizeBytes = parts[0].toLongOrNull() ?: return@forEach
                val lastModifiedSec = parts[1].toLongOrNull() ?: 0L
                val path = parts[2].trim()
                if (path.isBlank()) return@forEach
                val name = path.substringAfterLast("/")
                val ext  = name.substringAfterLast(".", "")
                found.add(LargeFileItem(
                    path = path, name = name,
                    sizeBytes = sizeBytes, lastModified = lastModifiedSec * 1000L,
                    extension = ext, category = extensionToCategory(ext),
                ))
                _state.update { s ->
                    s.copy(
                        files = found.toList().sortedByDescending { it.sizeBytes },
                        totalSizeBytes = found.sumOf { it.sizeBytes },
                        scannedCount = found.size,
                        currentScanPath = path.takeLast(40),
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun scanLocal() {
        val threshold = _state.value.threshold.bytes
        val found = mutableListOf<LargeFileItem>()
        var scanned = 0

        // Iterative BFS instead of recursive — avoids StackOverflowError on deep trees
        val queue = ArrayDeque<File>()
        queue.addLast(Environment.getExternalStorageDirectory())
        queue.addLast(Environment.getDataDirectory())

        while (queue.isNotEmpty()) {
            yield()
            val dir = queue.removeFirst()
            if (!dir.exists() || !dir.canRead()) continue
            try {
                dir.listFiles()?.forEach { f ->
                    if (f.isDirectory) {
                        queue.addLast(f)
                        _state.update { it.copy(currentScanPath = f.path, scannedCount = scanned) }
                    } else if (f.length() >= threshold) {
                        scanned++
                        val ext = f.extension
                        found.add(LargeFileItem(
                            path = f.absolutePath, name = f.name,
                            sizeBytes = f.length(), lastModified = f.lastModified(),
                            extension = ext, category = extensionToCategory(ext),
                        ))
                        _state.update { s ->
                            s.copy(
                                files = found.toList().sortedByDescending { it.sizeBytes },
                                totalSizeBytes = found.sumOf { it.sizeBytes },
                                scannedCount = scanned,
                                scanProgress = (scanned % 1000) / 1000f,
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun toggleSelect(path: String) = _state.update { s ->
        val sel = if (path in s.selectedFiles) s.selectedFiles - path else s.selectedFiles + path
        s.copy(selectedFiles = sel)
    }
    fun selectAll() = _state.update { s -> s.copy(selectedFiles = s.files.map { it.path }.toSet()) }
    fun clearSelection() = _state.update { it.copy(selectedFiles = emptySet()) }

    fun deleteSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            val sel = _state.value.selectedFiles
            var deleted = 0L
            if (connectionManager.isRemoteSession()) {
                val paths = sel.joinToString(" ") { "\"$it\"" }
                connectionManager.exec("rm -f $paths 2>/dev/null")
                deleted = _state.value.files.filter { it.path in sel }.sumOf { it.sizeBytes }
            } else {
                sel.forEach { path ->
                    try { val f = File(path); if (f.exists()) { deleted += f.length(); f.delete() } } catch (_: Exception) {}
                }
            }
            _state.update { s ->
                s.copy(
                    files = s.files.filter { it.path !in sel },
                    selectedFiles = emptySet(),
                    totalSizeBytes = s.totalSizeBytes - deleted,
                    snackbarMessage = "Deleted ${sel.size} files (${formatBytes(deleted)} freed)",
                )
            }
        }
    }

    fun onThresholdChange(t: SizeThreshold) = _state.update { it.copy(threshold = t) }
    fun onSortChange(s: LFSortMode) = _state.update { it.copy(sortMode = s) }
    fun onCategoryFilter(c: FileCategory?) = _state.update { it.copy(filterCategory = c) }
    fun onSearch(q: String) = _state.update { it.copy(searchQuery = q) }
    fun clearSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    fun filteredFiles(state: LargeFileState): List<LargeFileItem> {
        var list = state.files
        state.filterCategory?.let { cat -> list = list.filter { it.category == cat } }
        if (state.searchQuery.isNotBlank()) list = list.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
        return when (state.sortMode) {
            LFSortMode.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            LFSortMode.SIZE_ASC  -> list.sortedBy { it.sizeBytes }
            LFSortMode.DATE_DESC -> list.sortedByDescending { it.lastModified }
            LFSortMode.DATE_ASC  -> list.sortedBy { it.lastModified }
            LFSortMode.NAME      -> list.sortedBy { it.name.lowercase() }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
    return DecimalFormat("#,##0.##").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

// ─────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFileFinderScreen(
    onBack: () -> Unit,
    viewModel: LargeFileFinderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val filtered = remember(state) { viewModel.filteredFiles(state) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Large File Finder", fontWeight = FontWeight.Bold)
                            if (state.files.isNotEmpty()) {
                                Text(
                                    "${state.files.size} files · ${formatBytes(state.totalSizeBytes)} total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                    },
                    actions = {
                        InfoTooltipIcon(
                            title = "Large File Finder",
                            description = "SD Maid SE feature. Scans internal storage for files above a size threshold.\n\n• Set minimum file size (10 MB – 1 GB)\n• Filter by category: Video, Image, Audio, Documents, APKs, etc.\n• Sort by size, date, or name\n• Multi-select and batch delete\n• Copy file paths\n\nRequires storage permission. Some system directories may not be accessible without root."
                        )
                        if (state.selectedFiles.isNotEmpty()) {
                            IconButton(onClick = { viewModel.deleteSelected() }) {
                                Icon(Icons.Default.DeleteForever, "Delete selected", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Outlined.Search, "Search")
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Outlined.Sort, "Sort")
                        }
                        IconButton(onClick = { showThresholdDialog = true }) {
                            Icon(Icons.Outlined.Tune, "Threshold")
                        }
                    },
                )

                // Search bar
                AnimatedVisibility(visible = showSearchBar) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearch,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search by filename…") },
                        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearch("") }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // Category filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.filterCategory == null,
                            onClick = { viewModel.onCategoryFilter(null) },
                            label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Apps, null, Modifier.size(14.dp)) },
                        )
                    }
                    items(FileCategory.entries) { cat ->
                        val count = state.files.count { it.category == cat }
                        if (count > 0) {
                            FilterChip(
                                selected = state.filterCategory == cat,
                                onClick = { viewModel.onCategoryFilter(if (state.filterCategory == cat) null else cat) },
                                label = { Text("${cat.label} ($count)", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(cat.icon, null, Modifier.size(14.dp), tint = cat.color) },
                            )
                        }
                    }
                }

                // Scan progress bar
                AnimatedVisibility(visible = state.isScanning) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Scanning… ${state.scannedCount} large files found",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                state.currentScanPath.takeLast(32),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.scan() },
                icon = { Icon(if (state.isScanning) Icons.Default.Stop else Icons.Default.Search, null) },
                text = { Text(if (state.isScanning) "Scanning…" else "Scan Storage") },
                expanded = filtered.isEmpty(),
            )
        },
    ) { padding ->
        if (filtered.isEmpty() && !state.isScanning) {
            EmptyLargeFileState(
                hasScanned = state.totalSizeBytes == 0L && state.scannedCount == 0,
                threshold = state.threshold,
                padding = padding,
                onScan = { viewModel.scan() },
                onThreshold = { showThresholdDialog = true },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Multi-select header
                if (state.selectedFiles.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${state.selectedFiles.size} selected · ${formatBytes(state.files.filter { it.path in state.selectedFiles }.sumOf { it.sizeBytes })}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = viewModel::selectAll) { Text("All") }
                                    TextButton(onClick = viewModel::clearSelection) { Text("Clear") }
                                    Button(
                                        onClick = viewModel::deleteSelected,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    ) {
                                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }

                items(filtered, key = { it.path }) { file ->
                    LargeFileCard(
                        file = file,
                        isSelected = file.path in state.selectedFiles,
                        dateFormat = dateFormat,
                        onToggleSelect = { viewModel.toggleSelect(file.path) },
                        onCopyPath = { clipboard.setText(AnnotatedString(file.path)); },
                    )
                }
            }
        }
    }

    // Threshold dialog
    if (showThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = { Text("Minimum File Size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Only show files larger than:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SizeThreshold.entries.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                viewModel.onThresholdChange(t); showThresholdDialog = false
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(t.label, fontWeight = if (t == state.threshold) FontWeight.Bold else FontWeight.Normal)
                            if (t == state.threshold) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThresholdDialog = false }) { Text("Close") } },
        )
    }

    // Sort dialog
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort Files") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LFSortMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                viewModel.onSortChange(mode); showSortDialog = false
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(mode.label, fontWeight = if (mode == state.sortMode) FontWeight.Bold else FontWeight.Normal)
                            if (mode == state.sortMode) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortDialog = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun LargeFileCard(
    file: LargeFileItem,
    isSelected: Boolean,
    dateFormat: SimpleDateFormat,
    onToggleSelect: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Category icon
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(file.category.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(file.category.icon, null, tint = file.category.color, modifier = Modifier.size(22.dp))
                }
                // Info
                Column(Modifier.weight(1f)) {
                    Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatBytes(file.sizeBytes)} · ${file.category.label} · ${dateFormat.format(Date(file.lastModified))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Size badge
                Surface(
                    color = file.category.color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        formatBytes(file.sizeBytes),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = file.category.color,
                    )
                }
                // Select checkbox
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
            }

            // Expanded details
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SelectionContainer {
                        Text(file.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCopyPath,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Path", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onToggleSelect() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                        ) {
                            Icon(if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isSelected) "Deselect" else "Select", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLargeFileState(
    hasScanned: Boolean,
    threshold: SizeThreshold,
    padding: PaddingValues,
    onScan: () -> Unit,
    onThreshold: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(Icons.Outlined.FindInPage, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            Text(
                if (hasScanned) "Tap 'Scan Storage' to find large files" else "No files above ${threshold.label} found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Current threshold: ${threshold.label}\nLarger threshold = faster scan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onThreshold) {
                    Icon(Icons.Default.Tune, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change Threshold")
                }
                Button(onClick = onScan) {
                    Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Now")
                }
            }
        }
    }
}
