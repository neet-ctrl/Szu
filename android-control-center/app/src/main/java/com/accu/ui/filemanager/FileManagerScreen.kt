package com.accu.ui.filemanager

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class FileManagerState(
    val currentPath: String = "/sdcard",
    val files: List<FileItem> = emptyList(),
    val breadcrumbs: List<String> = listOf("/sdcard"),
    val selectedFiles: Set<String> = emptySet(),
    val isMultiSelect: Boolean = false,
    val isLoading: Boolean = false,
    val sortBy: FileSortBy = FileSortBy.NAME,
    val showHidden: Boolean = false,
    val viewMode: ViewMode = ViewMode.LIST,
    val clipboard: ClipboardAction? = null,
    val searchQuery: String = "",
    val snackbarMessage: String? = null,
)
data class FileItem(val name: String, val path: String, val size: Long, val lastModified: Long, val isDirectory: Boolean, val mimeType: String = "", val isHidden: Boolean = false)
data class ClipboardAction(val files: List<String>, val isCut: Boolean)
enum class FileSortBy { NAME, SIZE, DATE, TYPE }
enum class ViewMode { LIST, GRID }

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {
    private val _state = MutableStateFlow(FileManagerState())
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    init { navigateTo("/sdcard") }

    fun navigateTo(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val dir = File(path)
                val files = (dir.listFiles()?.toList() ?: emptyList())
                    .filter { _state.value.showHidden || !it.isHidden }
                    .map { f -> FileItem(f.name, f.absolutePath, if (f.isFile) f.length() else 0L, f.lastModified(), f.isDirectory, getMimeType(f), f.isHidden) }
                    .let { list ->
                        when (_state.value.sortBy) {
                            FileSortBy.NAME -> list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                            FileSortBy.SIZE -> list.sortedWith(compareBy({ !it.isDirectory }, { -it.size }))
                            FileSortBy.DATE -> list.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified }))
                            FileSortBy.TYPE -> list.sortedWith(compareBy({ !it.isDirectory }, { it.mimeType }, { it.name }))
                        }
                    }
                val breadcrumbs = buildBreadcrumbs(path)
                _state.update { it.copy(currentPath = path, files = files, breadcrumbs = breadcrumbs, isLoading = false) }
            } catch (e: Exception) {
                // Try via ACCU for restricted dirs
                val result = shizukuUtils.execShizuku("ls -la $path")
                val items = parseLsOutput(result.output, path)
                _state.update { it.copy(currentPath = path, files = items, isLoading = false) }
            }
        }
    }

    fun navigateUp() {
        val parent = File(_state.value.currentPath).parent ?: return
        navigateTo(parent)
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val path = "${_state.value.currentPath}/$name"
            val result = shizukuUtils.execShizuku("mkdir -p $path")
            if (result.isSuccess) { navigateTo(_state.value.currentPath); _state.update { it.copy(snackbarMessage = "Folder created") } }
            else _state.update { it.copy(snackbarMessage = "Failed: ${result.error}") }
        }
    }

    fun deleteFiles(paths: List<String>) {
        viewModelScope.launch {
            val joined = paths.joinToString(" ")
            val result = shizukuUtils.execShizuku("rm -rf $joined")
            if (result.isSuccess) {
                navigateTo(_state.value.currentPath)
                _state.update { it.copy(snackbarMessage = "Deleted ${paths.size} items", selectedFiles = emptySet(), isMultiSelect = false) }
            } else {
                _state.update { it.copy(snackbarMessage = "Delete failed — ${result.error.ifBlank { "check ACCU connection" }}") }
            }
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            val parent = File(oldPath).parent ?: return@launch
            val result = shizukuUtils.execShizuku("mv $oldPath $parent/$newName")
            if (result.isSuccess) navigateTo(_state.value.currentPath)
            else _state.update { it.copy(snackbarMessage = "Rename failed — ${result.error.ifBlank { "check ACCU connection" }}") }
        }
    }

    fun copy(paths: List<String>) { _state.update { it.copy(clipboard = ClipboardAction(paths, isCut = false), snackbarMessage = "${paths.size} items copied to clipboard") } }
    fun cut(paths: List<String>) { _state.update { it.copy(clipboard = ClipboardAction(paths, isCut = true), snackbarMessage = "${paths.size} items cut to clipboard") } }

    fun paste() {
        viewModelScope.launch {
            val clip = _state.value.clipboard ?: return@launch
            val dest = _state.value.currentPath
            val joined = clip.files.joinToString(" ")
            val cmd = if (clip.isCut) "mv $joined $dest" else "cp -r $joined $dest"
            val result = shizukuUtils.execShizuku(cmd)
            if (result.isSuccess) { navigateTo(dest); _state.update { it.copy(clipboard = null, snackbarMessage = "Pasted") } }
        }
    }

    fun toggleSort(sort: FileSortBy) { _state.update { it.copy(sortBy = sort) }; navigateTo(_state.value.currentPath) }
    fun toggleHidden() { _state.update { it.copy(showHidden = !it.showHidden) }; navigateTo(_state.value.currentPath) }
    fun toggleViewMode() { _state.update { it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST) } }
    fun toggleMultiSelect() { _state.update { it.copy(isMultiSelect = !it.isMultiSelect, selectedFiles = emptySet()) } }
    fun toggleSelection(path: String) { _state.update { s -> val sel = s.selectedFiles.toMutableSet(); if (!sel.add(path)) sel.remove(path); s.copy(selectedFiles = sel) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }

    private fun buildBreadcrumbs(path: String): List<String> {
        val parts = path.removePrefix("/").split("/")
        return parts.foldIndexed(mutableListOf<String>()) { i, acc, part ->
            acc.add(if (i == 0) "/$part" else "${acc.last()}/$part"); acc
        }
    }

    private fun parseLsOutput(output: String, basePath: String): List<FileItem> = output.lines().mapNotNull { line ->
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 9) return@mapNotNull null
        val name = parts.last()
        val isDir = parts[0].startsWith("d")
        FileItem(name, "$basePath/$name", 0L, 0L, isDir)
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "image/$ext"
            "mp4", "mkv", "avi", "mov", "webm" -> "video/$ext"
            "mp3", "wav", "ogg", "flac", "aac" -> "audio/$ext"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip", "tar", "gz", "7z", "rar" -> "application/zip"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "kt", "java", "py", "js", "ts" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onBack: () -> Unit,
    onNavigateToFtpServer: () -> Unit = {},
    onNavigateToFileProperties: (String) -> Unit = {},
    onNavigateToTextEditor: (String) -> Unit = {},
    viewModel: FileManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() } }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("File Manager", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { if (state.breadcrumbs.size > 1) viewModel.navigateUp() else onBack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = {
                        if (state.isMultiSelect && state.selectedFiles.isNotEmpty()) {
                            IconButton(onClick = { viewModel.copy(state.selectedFiles.toList()) }) { Icon(Icons.Default.ContentCopy, "Copy") }
                            IconButton(onClick = { viewModel.cut(state.selectedFiles.toList()) }) { Icon(Icons.Default.ContentCut, "Cut") }
                            IconButton(onClick = { viewModel.deleteFiles(state.selectedFiles.toList()) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                        if (state.clipboard != null) IconButton(onClick = { viewModel.paste() }) { Icon(Icons.Default.ContentPaste, "Paste") }
                        IconButton(onClick = { viewModel.toggleHidden() }) { Icon(if (state.showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) }
                        IconButton(onClick = { viewModel.toggleViewMode() }) { Icon(if (state.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList, null) }
                        IconButton(onClick = { viewModel.toggleMultiSelect() }) { Icon(if (state.isMultiSelect) Icons.Default.CheckCircle else Icons.Default.SelectAll, null) }
                        IconButton(onClick = { showCreateFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, "New Folder") }
                        IconButton(onClick = onNavigateToFtpServer) { Icon(Icons.Default.Cloud, "FTP Server") }
                    },
                )
                // Breadcrumbs
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(state.breadcrumbs.size) { i ->
                        val crumb = state.breadcrumbs[i]
                        val label = crumb.substringAfterLast('/').ifBlank { "/" }
                        TextButton(onClick = { viewModel.navigateTo(crumb) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = if (i == state.breadcrumbs.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i < state.breadcrumbs.size - 1) Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.files.isEmpty() && !state.isLoading) {
            EmptyState(Icons.Default.FolderOpen, "Empty Folder", "This folder contains no files")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.files, key = { it.path }) { file ->
                    val isSelected = file.path in state.selectedFiles
                    ListItem(
                        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                buildString {
                                    if (!file.isDirectory) append("${formatBytes(file.size)} · ")
                                    if (file.lastModified > 0) append(dateFormatter.format(Date(file.lastModified)))
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            if (state.isMultiSelect) Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleSelection(file.path) })
                            else Icon(
                                when {
                                    file.isDirectory -> Icons.Default.Folder
                                    file.mimeType.startsWith("image") -> Icons.Default.Image
                                    file.mimeType.startsWith("video") -> Icons.Default.VideoFile
                                    file.mimeType.startsWith("audio") -> Icons.Default.AudioFile
                                    file.mimeType == "application/vnd.android.package-archive" -> Icons.Default.Android
                                    else -> Icons.Default.InsertDriveFile
                                },
                                null,
                                tint = when {
                                    file.isDirectory -> MaterialTheme.colorScheme.primary
                                    file.mimeType.startsWith("image") -> androidx.compose.ui.graphics.Color(0xFFE91E63)
                                    file.mimeType == "application/vnd.android.package-archive" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f)) else ListItemDefaults.colors(),
                        modifier = Modifier.clickable {
                            if (state.isMultiSelect) viewModel.toggleSelection(file.path)
                            else if (file.isDirectory) viewModel.navigateTo(file.path)
                        }.combinedClickable(onClick = {
                            if (state.isMultiSelect) viewModel.toggleSelection(file.path)
                            else if (file.isDirectory) viewModel.navigateTo(file.path)
                        }, onLongClick = { viewModel.toggleMultiSelect(); viewModel.toggleSelection(file.path) }),
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false; newFolderName = "" },
            title = { Text("New Folder") },
            text = { OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("Folder name") }, singleLine = true) },
            confirmButton = { Button(onClick = { viewModel.createFolder(newFolderName); showCreateFolderDialog = false; newFolderName = "" }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } },
        )
    }
}

private fun formatBytes(bytes: Long) = when { bytes < 1024 -> "$bytes B"; bytes < 1_000_000 -> "${"%.1f".format(bytes / 1024f)} KB"; else -> "${"%.1f".format(bytes / 1_000_000f)} MB" }
