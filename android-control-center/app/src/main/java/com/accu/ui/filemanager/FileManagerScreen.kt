package com.accu.ui.filemanager

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─── State & Data Models ──────────────────────────────────────────────────────

data class FileManagerState(
    val currentPath: String = "/",
    val files: List<FileItem> = emptyList(),
    val allFiles: List<FileItem> = emptyList(),
    val breadcrumbs: List<String> = listOf("/"),
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

data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String = "",
    val isHidden: Boolean = false,
)

data class ClipboardAction(val files: List<String>, val isCut: Boolean)
enum class FileSortBy { NAME, SIZE, DATE, TYPE }
enum class ViewMode { LIST, GRID }

// ─── ViewModel ────────────────────────────────────────────────────────────────

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
            _state.update { it.copy(isLoading = true, searchQuery = "") }
            val result = shizukuUtils.execShizuku("ls -la \"$path\" 2>/dev/null")
            val raw = if (result.output.isNotBlank()) result.output
                      else shizukuUtils.execShizuku("ls -la $path 2>/dev/null").output
            val items = parseLsOutput(raw, path)
                .filter { _state.value.showHidden || !it.isHidden }
                .sortedWith(_state.value.sortBy)
            val breadcrumbs = buildBreadcrumbs(path)
            _state.update { it.copy(currentPath = path, files = items, allFiles = items, breadcrumbs = breadcrumbs, isLoading = false, isMultiSelect = false, selectedFiles = emptySet()) }
        }
    }

    fun navigateUp() {
        val parent = File(_state.value.currentPath).parent ?: return
        navigateTo(parent)
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val path = "${_state.value.currentPath}/$name"
            val result = shizukuUtils.execShizuku("mkdir -p \"$path\"")
            if (result.isSuccess) { navigateTo(_state.value.currentPath); _state.update { it.copy(snackbarMessage = "Folder '$name' created") } }
            else _state.update { it.copy(snackbarMessage = "Failed: ${result.error}") }
        }
    }

    fun deleteFiles(paths: List<String>) {
        viewModelScope.launch {
            val escaped = paths.joinToString(" ") { "\"$it\"" }
            val result = shizukuUtils.execShizuku("rm -rf $escaped")
            if (result.isSuccess) {
                navigateTo(_state.value.currentPath)
                _state.update { it.copy(snackbarMessage = "Deleted ${paths.size} item(s)", selectedFiles = emptySet(), isMultiSelect = false) }
            } else {
                _state.update { it.copy(snackbarMessage = "Delete failed — ${result.error.ifBlank { "check ACCU connection" }}") }
            }
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            val parent = File(oldPath).parent ?: return@launch
            val result = shizukuUtils.execShizuku("mv \"$oldPath\" \"$parent/$newName\"")
            if (result.isSuccess) navigateTo(_state.value.currentPath)
            else _state.update { it.copy(snackbarMessage = "Rename failed — ${result.error.ifBlank { "check ACCU connection" }}") }
        }
    }

    fun copy(paths: List<String>) { _state.update { it.copy(clipboard = ClipboardAction(paths, isCut = false), snackbarMessage = "${paths.size} item(s) copied") } }
    fun cut(paths: List<String>) { _state.update { it.copy(clipboard = ClipboardAction(paths, isCut = true), snackbarMessage = "${paths.size} item(s) cut") } }

    fun paste() {
        viewModelScope.launch {
            val clip = _state.value.clipboard ?: return@launch
            val dest = _state.value.currentPath
            val escaped = clip.files.joinToString(" ") { "\"$it\"" }
            val cmd = if (clip.isCut) "mv $escaped \"$dest\"" else "cp -r $escaped \"$dest\""
            val result = shizukuUtils.execShizuku(cmd)
            if (result.isSuccess) { navigateTo(dest); _state.update { it.copy(clipboard = null, snackbarMessage = "Pasted ${clip.files.size} item(s)") } }
            else _state.update { it.copy(snackbarMessage = "Paste failed: ${result.error}") }
        }
    }

    fun saveToControllerStorage(files: List<String>, destFolderUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var success = 0
                files.forEach { srcPath ->
                    val srcFile = File(srcPath)
                    val resolver = context.contentResolver
                    val destDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, destFolderUri) ?: return@forEach
                    val mime = getMimeTypeStr(srcFile)
                    if (srcFile.isDirectory) {
                        val newDir = destDir.createDirectory(srcFile.name) ?: return@forEach
                        srcFile.walkTopDown().drop(1).forEach { child ->
                            if (child.isFile) {
                                val relative = child.relativeTo(srcFile)
                                newDir.createFile(getMimeTypeStr(child), relative.path)?.let { docFile ->
                                    resolver.openOutputStream(docFile.uri)?.use { out ->
                                        child.inputStream().use { it.copyTo(out) }
                                    }
                                }
                            }
                        }
                        success++
                    } else {
                        val docFile = destDir.createFile(mime, srcFile.name) ?: return@forEach
                        resolver.openOutputStream(docFile.uri)?.use { out ->
                            srcFile.inputStream().use { it.copyTo(out) }
                        }
                        success++
                    }
                }
                _state.update { it.copy(snackbarMessage = "Saved $success item(s) to controller storage", selectedFiles = emptySet(), isMultiSelect = false) }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Save failed: ${e.message}") }
            }
        }
    }

    fun importFromController(sourceUris: List<Uri>, destFolder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var count = 0
                val resolver = context.contentResolver
                sourceUris.forEach { uri ->
                    val fileName = getFileNameFromUri(uri) ?: "imported_file"
                    val destFile = File(destFolder, fileName)
                    resolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        count++
                    }
                }
                navigateTo(_state.value.currentPath)
                _state.update { it.copy(snackbarMessage = "Imported $count file(s)") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Import failed: ${e.message}") }
            }
        }
    }

    fun importFolderFromController(treeUri: Uri, destFolder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    ?: return@launch
                val resolver = context.contentResolver
                var count = 0

                fun copyDocDir(src: androidx.documentfile.provider.DocumentFile, destPath: String) {
                    val dir = File(destPath, src.name ?: "folder")
                    dir.mkdirs()
                    src.listFiles().forEach { child ->
                        if (child.isDirectory) copyDocDir(child, dir.absolutePath)
                        else {
                            val outFile = File(dir, child.name ?: "file")
                            resolver.openInputStream(child.uri)?.use { input ->
                                outFile.outputStream().use { output -> input.copyTo(output) }
                                count++
                            }
                        }
                    }
                }
                copyDocDir(docFile, destFolder)
                navigateTo(_state.value.currentPath)
                _state.update { it.copy(snackbarMessage = "Imported folder ($count files)") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Folder import failed: ${e.message}") }
            }
        }
    }

    fun setSearchQuery(q: String) {
        _state.update { s ->
            val filtered = if (q.isBlank()) s.allFiles
                           else s.allFiles.filter { it.name.contains(q, ignoreCase = true) }
            s.copy(searchQuery = q, files = filtered)
        }
    }

    fun toggleSort(sort: FileSortBy) { _state.update { it.copy(sortBy = sort) }; navigateTo(_state.value.currentPath) }
    fun toggleHidden() { _state.update { it.copy(showHidden = !it.showHidden) }; navigateTo(_state.value.currentPath) }
    fun toggleViewMode() { _state.update { it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST) } }
    fun toggleMultiSelect() { _state.update { it.copy(isMultiSelect = !it.isMultiSelect, selectedFiles = emptySet()) } }
    fun toggleSelection(path: String) {
        _state.update { s ->
            val sel = s.selectedFiles.toMutableSet()
            if (!sel.add(path)) sel.remove(path)
            s.copy(selectedFiles = sel)
        }
    }
    fun selectAll() { _state.update { it.copy(selectedFiles = it.files.map { f -> f.path }.toSet()) } }
    fun clearSelection() { _state.update { it.copy(selectedFiles = emptySet()) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }

    private fun buildBreadcrumbs(path: String): List<String> {
        if (path == "/") return listOf("/")
        val parts = path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        return parts.foldIndexed(mutableListOf()) { i, acc, part ->
            acc.add(if (i == 0) "/$part" else "${acc.last()}/$part"); acc
        }
    }

    private fun parseLsOutput(output: String, basePath: String): List<FileItem> = output.lines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("total")) return@mapNotNull null
        // Use limit=9 so filenames with spaces are captured intact in the last element.
        // Android ls -la format: perms links owner group size date time name
        // (8 fields when name has no spaces → parts[7]; 9 when name has spaces → parts[8])
        val parts = trimmed.split(Regex("\\s+"), limit = 9)
        if (parts.size < 5) return@mapNotNull null
        val rawName = parts.getOrNull(8)?.trimStart() ?: parts.getOrNull(7) ?: return@mapNotNull null
        // Strip symlink target ("bin -> usr/bin" → "bin")
        val name = if (rawName.contains(" -> ")) rawName.substringBefore(" -> ") else rawName
        if (name in listOf(".", "..") || name.isEmpty()) return@mapNotNull null
        val isDir = parts[0].startsWith("d")
        val isLink = parts[0].startsWith("l")
        val size = if (!isDir) parts.getOrNull(4)?.toLongOrNull() ?: 0L else 0L
        val isHidden = name.startsWith(".")
        val fakeMime = getMimeTypeStr(java.io.File(name))
        // Avoid double-slash when basePath is "/"
        val fullPath = if (basePath == "/") "/$name" else "$basePath/$name"
        FileItem(name, fullPath, size, 0L, isDir || isLink, fakeMime, isHidden)
    }

    private fun List<FileItem>.sortedWith(sortBy: FileSortBy): List<FileItem> = when (sortBy) {
        FileSortBy.NAME -> sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        FileSortBy.SIZE -> sortedWith(compareBy({ !it.isDirectory }, { -it.size }))
        FileSortBy.DATE -> sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified }))
        FileSortBy.TYPE -> sortedWith(compareBy({ !it.isDirectory }, { it.mimeType }, { it.name }))
    }

    private fun getMimeType(file: File): String = getMimeTypeStr(file)

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(col)
            }
        } catch (_: Exception) { uri.lastPathSegment }
    }
}

private fun getMimeTypeStr(file: File): String {
    val ext = file.extension.lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "pdf" -> "application/pdf"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "tar" -> "application/x-tar"
        "gz" -> "application/gzip"
        "7z" -> "application/x-7z-compressed"
        "rar" -> "application/x-rar-compressed"
        "txt", "md", "log", "csv" -> "text/plain"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "kt", "java", "py", "js", "ts", "c", "cpp", "h", "sh", "go", "rs" -> "text/plain"
        else -> "application/octet-stream"
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onBack: () -> Unit,
    onNavigateToFtpServer: () -> Unit = {},
    onNavigateToFileProperties: (String) -> Unit = {},
    onNavigateToTextEditor: (String) -> Unit = {},
    onNavigateToFileViewer: (String) -> Unit = {},
    viewModel: FileManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteConfirmFiles by remember { mutableStateOf<List<String>?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var importTargetFolder by remember { mutableStateOf<String?>(null) }

    // Launcher: pick destination folder on controller for "Save to Controller"
    var saveSourceFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    val saveToControllerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.saveToControllerStorage(saveSourceFiles, it) }
    }

    // Launcher: import files from controller (multiple files)
    val importFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val folder = importTargetFolder ?: return@rememberLauncherForActivityResult
        if (uris.isNotEmpty()) viewModel.importFromController(uris, folder)
        importTargetFolder = null
    }

    // Launcher: import folder from controller
    val importFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val folder = importTargetFolder ?: return@rememberLauncherForActivityResult
        uri?.let { viewModel.importFolderFromController(it, folder) }
        importTargetFolder = null
    }

    var showImportSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            Column {
                if (state.isMultiSelect) {
                    // ── Contextual selection TopAppBar (Material 3 selection pattern) ──
                    TopAppBar(
                        title = {
                            Text(
                                if (state.selectedFiles.isEmpty()) "Select items"
                                else "${state.selectedFiles.size} of ${state.files.size} selected",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.toggleMultiSelect() }) {
                                Icon(Icons.Default.Close, "Exit selection", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        },
                        actions = {
                            if (state.selectedFiles.isNotEmpty()) {
                                IconButton(onClick = { viewModel.copy(state.selectedFiles.toList()) }) {
                                    Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                IconButton(onClick = { viewModel.cut(state.selectedFiles.toList()) }) {
                                    Icon(Icons.Default.ContentCut, "Cut", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                IconButton(onClick = { deleteConfirmFiles = state.selectedFiles.toList() }) {
                                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = {
                                    saveSourceFiles = state.selectedFiles.toList()
                                    saveToControllerLauncher.launch(null)
                                }) {
                                    Icon(Icons.Default.SaveAlt, "Save to Storage", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Default.SelectAll, "Select All", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            if (state.selectedFiles.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearSelection() },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(
                                        "Clear",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                } else {
                    // ── Normal TopAppBar ──
                    TopAppBar(
                        title = {
                            if (showSearchBar) {
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = viewModel::setSearchQuery,
                                    placeholder = { Text("Search in ${File(state.currentPath).name}…") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                )
                            } else {
                                Text("File Manager", fontWeight = FontWeight.Bold)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (showSearchBar) { showSearchBar = false; viewModel.setSearchQuery("") }
                                else if (state.breadcrumbs.size > 1) viewModel.navigateUp()
                                else onBack()
                            }) { Icon(Icons.Default.ArrowBack, "Back") }
                        },
                        actions = {
                            if (state.clipboard != null) {
                                IconButton(onClick = { viewModel.paste() }) { Icon(Icons.Default.ContentPaste, "Paste") }
                            }
                            IconButton(onClick = { showSearchBar = !showSearchBar }) { Icon(Icons.Default.Search, "Search") }
                            IconButton(onClick = { viewModel.toggleHidden() }) {
                                Icon(if (state.showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(if (state.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList, null)
                            }
                            IconButton(onClick = { viewModel.toggleMultiSelect() }) {
                                Icon(Icons.Default.CheckBoxOutlineBlank, "Select items")
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    FileSortBy.values().forEach { sort ->
                                        DropdownMenuItem(
                                            text = { Text(sort.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                            leadingIcon = {
                                                if (state.sortBy == sort) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                            },
                                            onClick = { viewModel.toggleSort(sort); showSortMenu = false },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { showCreateFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, "New Folder") }
                        },
                    )
                }
                // Breadcrumbs — always visible
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.breadcrumbs.size) { i ->
                        val crumb = state.breadcrumbs[i]
                        val label = crumb.substringAfterLast('/').ifBlank { "/" }
                        TextButton(
                            onClick = { viewModel.navigateTo(crumb) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (i == state.breadcrumbs.size - 1)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (i < state.breadcrumbs.size - 1) {
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            state.files.isEmpty() -> EmptyFolderState(Modifier.fillMaxSize().padding(padding))
            state.viewMode == ViewMode.GRID -> FileGridView(
                files = state.files,
                state = state,
                padding = padding,
                dateFormatter = dateFormatter,
                haptic = haptic,
                viewModel = viewModel,
                onOpenFile = onNavigateToFileViewer,
                onProperties = onNavigateToFileProperties,
                onRename = { file -> renameTarget = file; renameText = file.name },
                onDelete = { deleteConfirmFiles = listOf(it.path) },
                onSaveToController = { saveSourceFiles = listOf(it.path); saveToControllerLauncher.launch(null) },
                onImport = { folder -> importTargetFolder = folder; showImportSheet = true },
            )
            else -> FileListView(
                files = state.files,
                state = state,
                padding = padding,
                dateFormatter = dateFormatter,
                haptic = haptic,
                viewModel = viewModel,
                onOpenFile = onNavigateToFileViewer,
                onProperties = onNavigateToFileProperties,
                onRename = { file -> renameTarget = file; renameText = file.name },
                onDelete = { deleteConfirmFiles = listOf(it.path) },
                onSaveToController = { saveSourceFiles = listOf(it.path); saveToControllerLauncher.launch(null) },
                onImport = { folder -> importTargetFolder = folder; showImportSheet = true },
            )
        }
    }

    // ── Dialogs & Sheets ──────────────────────────────────────────────────────

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false; newFolderName = "" },
            icon = { Icon(Icons.Default.CreateNewFolder, null) },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFolderName.isNotBlank()) {
                        viewModel.createFolder(newFolderName)
                        showCreateFolderDialog = false
                        newFolderName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            icon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) {
                        viewModel.renameFile(target.path, renameText)
                        renameTarget = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    deleteConfirmFiles?.let { files ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFiles = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${files.size} item(s)?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteFiles(files); deleteConfirmFiles = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmFiles = null }) { Text("Cancel") } },
        )
    }

    if (showImportSheet) {
        ImportFromControllerSheet(
            onImportFiles = {
                showImportSheet = false
                importFilesLauncher.launch("*/*")
            },
            onImportFolder = {
                showImportSheet = false
                importFolderLauncher.launch(null)
            },
            onDismiss = { showImportSheet = false; importTargetFolder = null },
            targetFolder = importTargetFolder ?: state.currentPath,
        )
    }
}

// ─── List View ────────────────────────────────────────────────────────────────

@Composable
private fun FileListView(
    files: List<FileItem>,
    state: FileManagerState,
    padding: PaddingValues,
    dateFormatter: SimpleDateFormat,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    viewModel: FileManagerViewModel,
    onOpenFile: (String) -> Unit,
    onProperties: (String) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onSaveToController: (FileItem) -> Unit,
    onImport: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        items(files, key = { it.path }) { file ->
            FileListItem(
                file = file,
                isSelected = file.path in state.selectedFiles,
                isMultiSelect = state.isMultiSelect,
                dateFormatter = dateFormatter,
                haptic = haptic,
                onClick = {
                    if (state.isMultiSelect) viewModel.toggleSelection(file.path)
                    else if (file.isDirectory) viewModel.navigateTo(file.path)
                    else onOpenFile(file.path)
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!state.isMultiSelect) viewModel.toggleMultiSelect()
                    viewModel.toggleSelection(file.path)
                },
                onCheckChange = { viewModel.toggleSelection(file.path) },
                onRename = { onRename(file) },
                onDelete = { onDelete(file) },
                onProperties = { onProperties(file.path) },
                onSaveToController = { onSaveToController(file) },
                onImport = if (file.isDirectory) { { onImport(file.path) } } else null,
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: FileItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    dateFormatter: SimpleDateFormat,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onSaveToController: () -> Unit,
    onImport: (() -> Unit)?,
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                buildString {
                    if (!file.isDirectory) append("${formatBytes(file.size)} · ")
                    if (file.lastModified > 0) append(dateFormatter.format(Date(file.lastModified)))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            if (isMultiSelect) {
                Checkbox(checked = isSelected, onCheckedChange = onCheckChange)
            } else {
                FileIcon(file, size = 36.dp)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Per-item save-to-controller button
                IconButton(onClick = onSaveToController, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SaveAlt, "Save to Storage", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Folder import button
                if (onImport != null) {
                    IconButton(onClick = onImport, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.FileDownload, "Import Here", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, "More", Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, Modifier.size(18.dp)) }, onClick = { showMenu = false; onRename() })
                        DropdownMenuItem(text = { Text("Properties") }, leadingIcon = { Icon(Icons.Default.Info, null, Modifier.size(18.dp)) }, onClick = { showMenu = false; onProperties() })
                        DropdownMenuItem(text = { Text("Save to Storage") }, leadingIcon = { Icon(Icons.Default.SaveAlt, null, Modifier.size(18.dp)) }, onClick = { showMenu = false; onSaveToController() })
                        if (onImport != null) {
                            DropdownMenuItem(text = { Text("Import Here") }, leadingIcon = { Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)) }, onClick = { showMenu = false; onImport() })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }
            }
        },
        colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.4f)) else ListItemDefaults.colors(),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

// ─── Grid View ────────────────────────────────────────────────────────────────

@Composable
private fun FileGridView(
    files: List<FileItem>,
    state: FileManagerState,
    padding: PaddingValues,
    dateFormatter: SimpleDateFormat,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    viewModel: FileManagerViewModel,
    onOpenFile: (String) -> Unit,
    onProperties: (String) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onSaveToController: (FileItem) -> Unit,
    onImport: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(files, key = { it.path }) { file ->
            FileGridItem(
                file = file,
                isSelected = file.path in state.selectedFiles,
                isMultiSelect = state.isMultiSelect,
                haptic = haptic,
                onClick = {
                    if (state.isMultiSelect) viewModel.toggleSelection(file.path)
                    else if (file.isDirectory) viewModel.navigateTo(file.path)
                    else onOpenFile(file.path)
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!state.isMultiSelect) viewModel.toggleMultiSelect()
                    viewModel.toggleSelection(file.path)
                },
                onSaveToController = { onSaveToController(file) },
                onImport = if (file.isDirectory) { { onImport(file.path) } } else null,
                onRename = { onRename(file) },
                onDelete = { onDelete(file) },
                onProperties = { onProperties(file.path) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    file: FileItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSaveToController: () -> Unit,
    onImport: (() -> Unit)?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(0.7f)
            else MaterialTheme.colorScheme.surface,
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                FileIcon(file, size = 48.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    file.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!file.isDirectory) {
                    Text(
                        formatBytes(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                    )
                }
            }

            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.align(Alignment.TopStart).size(28.dp),
                )
            }

            // Action buttons (top-right)
            Column(
                Modifier.align(Alignment.TopEnd).padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, Modifier.size(18.dp)) }, onClick = { showMenu = false; onRename() })
                        DropdownMenuItem(text = { Text("Properties") }, leadingIcon = { Icon(Icons.Default.Info, null, Modifier.size(18.dp)) }, onClick = { showMenu = false; onProperties() })
                        DropdownMenuItem(text = { Text("Save to Storage") }, leadingIcon = { Icon(Icons.Default.SaveAlt, null, Modifier.size(18.dp)) }, onClick = { showMenu = false; onSaveToController() })
                        if (onImport != null) {
                            DropdownMenuItem(text = { Text("Import Here") }, leadingIcon = { Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)) }, onClick = { showMenu = false; onImport() })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }
            }

            // Quick action: Save
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp),
            ) {
                if (onImport != null) {
                    IconButton(onClick = onImport, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.FileDownload, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = onSaveToController, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.SaveAlt, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─── Import Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportFromControllerSheet(
    targetFolder: String,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Import from Controller Storage",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Destination: …${targetFolder.takeLast(40)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            // Files
            ElevatedCard(
                onClick = onImportFiles,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.InsertDriveFile, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Import Files", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Select one or more files to import here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Folder
            ElevatedCard(
                onClick = onImportFolder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Import Folder", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pick a folder — its entire contents will be copied",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

// ─── Shared Composables ───────────────────────────────────────────────────────

@Composable
private fun FileIcon(file: FileItem, size: Dp) {
    val (icon, tint) = remember(file.path, file.isDirectory, file.mimeType) {
        fileIconAndTint(file)
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(tint.copy(alpha = 0.12f)),
        Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.6f), tint = tint)
    }
}

private fun fileIconAndTint(file: FileItem): Pair<ImageVector, Color> = when {
    file.isDirectory -> Pair(Icons.Default.Folder, Color(0xFF2196F3))
    file.mimeType.startsWith("image") -> Pair(Icons.Default.Image, Color(0xFFE91E63))
    file.mimeType.startsWith("video") -> Pair(Icons.Default.VideoFile, Color(0xFF9C27B0))
    file.mimeType.startsWith("audio") -> Pair(Icons.Default.AudioFile, Color(0xFF4CAF50))
    file.mimeType == "application/pdf" -> Pair(Icons.Default.PictureAsPdf, Color(0xFFF44336))
    file.mimeType == "application/vnd.android.package-archive" -> Pair(Icons.Default.Android, Color(0xFF4CAF50))
    file.mimeType == "application/zip" || file.name.endsWith(".zip") || file.name.endsWith(".rar") || file.name.endsWith(".7z") -> Pair(Icons.Default.FolderZip, Color(0xFFFF9800))
    file.mimeType == "text/plain" || file.name.endsWith(".txt") || file.name.endsWith(".md") -> Pair(Icons.Default.TextSnippet, Color(0xFF607D8B))
    file.mimeType == "application/json" -> Pair(Icons.Default.DataObject, Color(0xFF009688))
    file.mimeType == "text/html" -> Pair(Icons.Default.Html, Color(0xFFFF5722))
    file.mimeType == "text/xml" || file.name.endsWith(".xml") -> Pair(Icons.Default.Code, Color(0xFF3F51B5))
    else -> Pair(Icons.Default.InsertDriveFile, Color(0xFF78909C))
}

@Composable
private fun EmptyFolderState(modifier: Modifier = Modifier) {
    Box(modifier, Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
            Spacer(Modifier.height(12.dp))
            Text("Empty Folder", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("This folder contains no files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1_000_000 -> "${"%.1f".format(bytes / 1024f)} KB"
    bytes < 1_000_000_000 -> "${"%.2f".format(bytes / 1_000_000f)} MB"
    else -> "${"%.2f".format(bytes / 1_000_000_000f)} GB"
}
