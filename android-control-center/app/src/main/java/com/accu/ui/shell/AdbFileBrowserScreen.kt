package com.accu.ui.shell

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AdbConnectionMode(val label: String) { WIFI("Wi-Fi ADB"), OTG("OTG ADB"), LOCAL("Local") }
enum class FileOp { COPY, MOVE }

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: String = "",
    val permissions: String = "",
    val modifiedDate: String = "",
    val isSymlink: Boolean = false,
)

private fun fileIcon(item: RemoteFileItem) = when {
    item.isDir -> Icons.Default.Folder
    item.name.endsWith(".apk") -> Icons.Default.Android
    item.name.endsWith(".zip") || item.name.endsWith(".tar") || item.name.endsWith(".gz") -> Icons.Default.Archive
    item.name.endsWith(".mp4") || item.name.endsWith(".mkv") || item.name.endsWith(".avi") -> Icons.Default.VideoFile
    item.name.endsWith(".mp3") || item.name.endsWith(".ogg") || item.name.endsWith(".flac") -> Icons.Default.AudioFile
    item.name.endsWith(".png") || item.name.endsWith(".jpg") || item.name.endsWith(".webp") -> Icons.Default.Image
    item.name.endsWith(".txt") || item.name.endsWith(".log") || item.name.endsWith(".xml") -> Icons.Default.Description
    else -> Icons.Default.InsertDriveFile
}

private fun parseLsLine(line: String, parentPath: String): RemoteFileItem? {
    val trimmed = line.trim()
    if (trimmed.isBlank() || trimmed.startsWith("total ")) return null
    return try {
        val parts = trimmed.split(Regex("\\s+"), limit = 9)
        if (parts.size < 7) return null
        val perms = parts[0]
        val isDir = perms.startsWith("d")
        val isLink = perms.startsWith("l")
        val sizePart = parts[4].toLongOrNull()
        val datePart = "${parts[5]} ${parts[6]}"
        val rawName = parts.getOrNull(8)?.trimStart() ?: parts.getOrNull(7) ?: return null
        val name = if (isLink && rawName.contains(" -> ")) rawName.substringBefore(" -> ") else rawName
        if (name == "." || name == "..") return null
        val sizeStr = when {
            isDir  -> ""
            sizePart == null -> ""
            sizePart >= 1_073_741_824 -> "${"%.1f".format(sizePart / 1_073_741_824.0)} GB"
            sizePart >= 1_048_576     -> "${"%.1f".format(sizePart / 1_048_576.0)} MB"
            sizePart >= 1_024         -> "${"%.1f".format(sizePart / 1_024.0)} KB"
            else                      -> "$sizePart B"
        }
        RemoteFileItem(
            name = name,
            path = if (parentPath.endsWith("/")) "$parentPath$name" else "$parentPath/$name",
            isDir = isDir || isLink,
            size = sizeStr,
            permissions = perms,
            modifiedDate = datePart,
            isSymlink = isLink,
        )
    } catch (_: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbFileBrowserScreen(
    connectionMode: AdbConnectionMode = AdbConnectionMode.WIFI,
    deviceAddress: String = "",
    onBack: () -> Unit = {},
) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var currentPath by remember { mutableStateOf("/") }
    var pathStack by remember { mutableStateOf(listOf("/")) }
    var files by remember { mutableStateOf(emptyList<RemoteFileItem>()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var clipboardFile by remember { mutableStateOf<RemoteFileItem?>(null) }
    var clipboardOp by remember { mutableStateOf<FileOp?>(null) }
    var sortBy by remember { mutableStateOf("Name") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("list") }

    val snackbarHostState = remember { SnackbarHostState() }

    // Download flow: pending bytes set before CreateDocument launcher is fired
    var pendingDownloadBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingDownloadName by remember { mutableStateOf("") }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { u ->
            val bytes = pendingDownloadBytes ?: return@let
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(u)?.use { it.write(bytes) }
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Downloaded \"${pendingDownloadName}\" ✓")
                }
                pendingDownloadBytes = null
            }
        }
    }

    // Dialogs
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<RemoteFileItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf<List<RemoteFileItem>>(emptyList()) }
    var showInfoDialog by remember { mutableStateOf<RemoteFileItem?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }

    // Load directory listing via real shell command
    fun loadPath(path: String) {
        scope.launch {
            isLoading = true
            // Resolve symlinks before listing — on Android /sdcard is a symlink chain.
            // From root context, `ls -la /sdcard` returns the symlink line, not directory contents.
            val realPath = withContext(Dispatchers.IO) {
                val r = connectionManager.exec(
                    "readlink -f \"$path\" 2>/dev/null || realpath \"$path\" 2>/dev/null"
                ).output.trim()
                if (r.isNotBlank() && r.startsWith("/")) r else path
            }
            val result = withContext(Dispatchers.IO) {
                connectionManager.exec("ls -la \"$realPath\" 2>/dev/null || ls -l \"$realPath\" 2>/dev/null")
            }
            // Keep currentPath display as the user-typed path but use realPath for listing
            if (realPath != path) currentPath = realPath
            files = if (result.isSuccess && result.output.isNotBlank()) {
                result.output.lines()
                    .mapNotNull { parseLsLine(it, realPath) }
                    .filter { !it.name.startsWith(".") || showHidden }
            } else {
                // Fallback to Java File API when no ADB connection (local browsing)
                withContext(Dispatchers.IO) {
                    try {
                        java.io.File(path).listFiles()?.map { f ->
                            val s = f.length()
                            val size = when {
                                s >= 1_048_576 -> "${"%.1f".format(s / 1_048_576.0)} MB"
                                s >= 1024 -> "${"%.1f".format(s / 1024.0)} KB"
                                else -> "$s B"
                            }.takeIf { f.isFile } ?: ""
                            RemoteFileItem(f.name, f.absolutePath, f.isDirectory, size, "", "")
                        }?.filter { showHidden || !it.name.startsWith(".") } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadPath(currentPath) }

    fun navigateTo(path: String) {
        pathStack = pathStack + path
        currentPath = path
        loadPath(path)
        selectedFiles = emptySet()
        isSelectionMode = false
    }

    fun navigateUp() {
        if (pathStack.size > 1) {
            val newStack = pathStack.dropLast(1)
            pathStack = newStack
            currentPath = newStack.last()
            loadPath(currentPath)
        } else {
            onBack()
        }
        selectedFiles = emptySet()
        isSelectionMode = false
    }

    val filteredFiles = remember(files, searchQuery, showHidden, sortBy) {
        files.filter { f ->
            (showHidden || !f.name.startsWith(".")) &&
            (searchQuery.isBlank() || f.name.contains(searchQuery, ignoreCase = true))
        }.let { list ->
            when (sortBy) {
                "Name" -> list.sortedWith(compareByDescending<RemoteFileItem> { it.isDir }.thenBy { it.name.lowercase() })
                "Size" -> list.sortedWith(compareByDescending<RemoteFileItem> { it.isDir }.thenBy { it.size })
                "Date" -> list.sortedWith(compareByDescending<RemoteFileItem> { it.isDir }.thenByDescending { it.modifiedDate })
                "Type" -> list.sortedWith(compareByDescending<RemoteFileItem> { it.isDir }.thenBy { it.name.substringAfterLast(".") })
                else -> list
            }
        }
    }

    // Upload: read file from SAF, base64-encode, push to target via shell
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u ->
            scope.launch {
                val cr = context.contentResolver
                val fileInfo = cr.query(
                    u,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0) ?: "upload_${System.currentTimeMillis()}"
                        val size = if (!c.isNull(1)) c.getLong(1) else -1L
                        name to size
                    } else null
                } ?: ("upload_${System.currentTimeMillis()}" to -1L)
                val fileName  = fileInfo.first
                val fileSize  = fileInfo.second

                // Check size BEFORE reading — readBytes() on a huge file OOMs before we get a chance to check
                val limitBytes = 2L * 1024 * 1024 // 2 MB
                if (fileSize > limitBytes) {
                    snackbarHostState.showSnackbar("File too large (max 2 MB). Use adb push for bigger files.")
                    return@launch
                }

                val bytes = withContext(Dispatchers.IO) {
                    cr.openInputStream(u)?.use { it.readBytes() }
                } ?: run { snackbarHostState.showSnackbar("Cannot read file"); return@launch }

                // Secondary guard for files whose SAF size was unknown (-1)
                if (bytes.size > limitBytes) {
                    snackbarHostState.showSnackbar("File too large (max 2 MB). Use adb push for bigger files.")
                    return@launch
                }

                val destPath = "$currentPath/$fileName"
                snackbarHostState.showSnackbar("Uploading $fileName…")
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val result = withContext(Dispatchers.IO) {
                    // Write base64 string to a temp file on target, then decode it
                    val tmpPath = "/data/local/tmp/accu_up_${System.currentTimeMillis()}.b64"
                    connectionManager.exec("printf '%s' '$b64' > '$tmpPath' && base64 -d '$tmpPath' > '$destPath' && rm -f '$tmpPath'")
                }
                if (result.exitCode == 0 || result.error.isBlank()) {
                    loadPath(currentPath)
                    snackbarHostState.showSnackbar("Uploaded \"$fileName\" ✓")
                } else {
                    snackbarHostState.showSnackbar("Upload failed: ${result.error.take(80)}")
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false; newFolderName = "" },
            icon = { Icon(Icons.Default.CreateNewFolder, null) },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    newFolderName, { newFolderName = it },
                    label = { Text("Folder name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        val newPath = "$currentPath/$newFolderName"
                        val name = newFolderName
                        showCreateFolderDialog = false
                        newFolderName = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                connectionManager.exec("mkdir -p \"$newPath\" 2>&1")
                            }
                            if (result.exitCode == 0 || result.error.isBlank()) {
                                loadPath(currentPath)
                                snackbarHostState.showSnackbar("Folder \"$name\" created ✓")
                            } else {
                                snackbarHostState.showSnackbar("mkdir failed: ${result.error.take(80)}")
                            }
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false; newFolderName = "" }) { Text("Cancel") } }
        )
    }

    // Rename Dialog
    showRenameDialog?.let { fileToRename ->
        LaunchedEffect(fileToRename) { renameText = fileToRename.name }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null; renameText = "" },
            icon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    renameText, { renameText = it },
                    label = { Text("New name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank() && renameText != fileToRename.name) {
                        val newPath = "${currentPath.trimEnd('/')}/$renameText"
                        val newName = renameText
                        showRenameDialog = null
                        renameText = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                connectionManager.exec("mv \"${fileToRename.path}\" \"$newPath\" 2>&1")
                            }
                            if (result.exitCode == 0 || result.error.isBlank()) {
                                loadPath(currentPath)
                                snackbarHostState.showSnackbar("Renamed to \"$newName\" ✓")
                            } else {
                                snackbarHostState.showSnackbar("Rename failed: ${result.error.take(80)}")
                            }
                        }
                    } else {
                        showRenameDialog = null
                        renameText = ""
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null; renameText = "" }) { Text("Cancel") } }
        )
    }

    // Delete Dialog
    if (showDeleteDialog.isNotEmpty()) {
        val targets = showDeleteDialog
        AlertDialog(
            onDismissRequest = { showDeleteDialog = emptyList() },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (targets.size == 1) "Delete \"${targets[0].name}\"?" else "Delete ${targets.size} items?") },
            text = { Text("This action cannot be undone. The ${if (targets.size == 1) "item" else "items"} will be permanently deleted from the device.") },
            confirmButton = {
                TextButton(onClick = {
                    val pathArgs = targets.joinToString(" ") { "\"${it.path}\"" }
                    val count = targets.size
                    showDeleteDialog = emptyList()
                    selectedFiles = selectedFiles - targets.map { it.path }.toSet()
                    if (selectedFiles.isEmpty()) isSelectionMode = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            connectionManager.exec("rm -rf $pathArgs 2>&1")
                        }
                        if (result.exitCode == 0 || result.error.isBlank()) {
                            loadPath(currentPath)
                            snackbarHostState.showSnackbar("Deleted $count item(s) ✓")
                        } else {
                            snackbarHostState.showSnackbar("Delete failed: ${result.error.take(80)}")
                            loadPath(currentPath)
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = emptyList() }) { Text("Cancel") } }
        )
    }

    // File Info Dialog
    showInfoDialog?.let { f ->
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            icon = { Icon(if (f.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile, null) },
            title = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Path", f.path)
                    InfoRow("Type", if (f.isDir) "Directory" else "File")
                    if (f.size.isNotBlank()) InfoRow("Size", f.size)
                    if (f.permissions.isNotBlank()) InfoRow("Permissions", f.permissions)
                    if (f.modifiedDate.isNotBlank()) InfoRow("Modified", f.modifiedDate)
                    if (f.isSymlink) InfoRow("Type", "Symbolic link")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(f.path)); showInfoDialog = null }) { Text("Copy path") }
                    TextButton(onClick = { showInfoDialog = null }) { Text("Close") }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedFiles.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { isSelectionMode = false; selectedFiles = emptySet() }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val sel = files.filter { it.path in selectedFiles }
                            clipboardFile = sel.firstOrNull(); clipboardOp = FileOp.COPY
                            scope.launch { snackbarHostState.showSnackbar("${sel.size} item(s) ready to copy") }
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val sel = files.filter { it.path in selectedFiles }
                            clipboardFile = sel.firstOrNull(); clipboardOp = FileOp.MOVE
                            scope.launch { snackbarHostState.showSnackbar("${sel.size} item(s) ready to move") }
                        }) { Icon(Icons.Default.ContentCut, "Cut") }
                        IconButton(onClick = {
                            showDeleteDialog = files.filter { it.path in selectedFiles }
                        }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        IconButton(onClick = {
                            selectedFiles = filteredFiles.map { it.path }.toSet()
                        }) { Icon(Icons.Default.SelectAll, "Select all") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(connectionMode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (deviceAddress.isNotBlank()) Text(deviceAddress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                            Icon(if (showSearch) Icons.Default.SearchOff else Icons.Default.Search, "Search")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                            DropdownMenu(showSortMenu, { showSortMenu = false }) {
                                listOf("Name", "Size", "Date", "Type").forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        leadingIcon = { if (sortBy == s) Icon(Icons.Default.Check, null) },
                                        onClick = { sortBy = s; showSortMenu = false }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Show hidden files") },
                                    leadingIcon = { if (showHidden) Icon(Icons.Default.Check, null) },
                                    onClick = { showHidden = !showHidden; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (viewMode == "list") "Grid view" else "List view") },
                                    leadingIcon = { Icon(if (viewMode == "list") Icons.Default.GridView else Icons.Default.ViewList, null) },
                                    onClick = { viewMode = if (viewMode == "list") "grid" else "list"; showSortMenu = false }
                                )
                            }
                        }
                        IconButton(onClick = {
                            isRefreshing = true
                            scope.launch { loadPath(currentPath); isRefreshing = false }
                        }) {
                            if (isRefreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(onClick = { uploadLauncher.launch("*/*") }) {
                        Icon(Icons.Default.Upload, "Upload file")
                    }
                    if (clipboardFile != null && clipboardOp != null) {
                        SmallFloatingActionButton(onClick = {
                            val src = clipboardFile ?: return@SmallFloatingActionButton
                            val op = clipboardOp ?: return@SmallFloatingActionButton
                            val destPath = "${currentPath.trimEnd('/')}/${src.name}"
                            val opLabel = if (op == FileOp.COPY) "Copying" else "Moving"
                            scope.launch {
                                snackbarHostState.showSnackbar("$opLabel \"${src.name}\"…")
                                val cmd = if (op == FileOp.COPY)
                                    "cp -r \"${src.path}\" \"$destPath\" 2>&1"
                                else
                                    "mv \"${src.path}\" \"$destPath\" 2>&1"
                                val result = withContext(Dispatchers.IO) { connectionManager.exec(cmd) }
                                if (op == FileOp.MOVE) { clipboardFile = null; clipboardOp = null }
                                if (result.exitCode == 0 || result.error.isBlank()) {
                                    loadPath(currentPath)
                                    snackbarHostState.showSnackbar("${if (op == FileOp.COPY) "Copied" else "Moved"} \"${src.name}\" ✓")
                                } else {
                                    snackbarHostState.showSnackbar("${if (op == FileOp.COPY) "Copy" else "Move"} failed: ${result.error.take(80)}")
                                }
                            }
                        }) { Icon(Icons.Default.ContentPaste, "Paste") }
                    }
                    FloatingActionButton(onClick = { showCreateFolderDialog = true; newFolderName = "" }) {
                        Icon(Icons.Default.CreateNewFolder, "New folder")
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Path breadcrumbs
            PathBreadcrumbs(
                pathStack = pathStack,
                onNavigateTo = { path ->
                    val idx = pathStack.indexOf(path)
                    if (idx >= 0) {
                        pathStack = pathStack.take(idx + 1)
                        currentPath = path
                        loadPath(path)
                    }
                }
            )

            // Search bar
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    searchQuery, { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Search in ${currentPath.substringAfterLast('/')}…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp)) } },
                    singleLine = true, shape = RoundedCornerShape(12.dp)
                )
            }

            // Clipboard banner
            AnimatedVisibility(visible = clipboardFile != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (clipboardOp == FileOp.COPY) Icons.Default.ContentCopy else Icons.Default.ContentCut, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${if (clipboardOp == FileOp.COPY) "Copy" else "Cut"}: ${clipboardFile?.name}", fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { clipboardFile = null; clipboardOp = null }, contentPadding = PaddingValues(4.dp)) { Text("Clear", fontSize = 11.sp) }
                    }
                }
            }

            // File count
            if (!isLoading) {
                Text(
                    "${filteredFiles.size} item(s)${if (searchQuery.isNotEmpty()) " matching \"$searchQuery\"" else ""}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Loading indicator
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FolderOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (searchQuery.isNotEmpty()) "No files match \"$searchQuery\"" else "Empty directory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(filteredFiles, key = { it.path }) { file ->
                        FileListItem(
                            file = file,
                            isSelected = file.path in selectedFiles,
                            isSelectionMode = isSelectionMode,
                            onLongClick = {
                                isSelectionMode = true
                                selectedFiles = selectedFiles + file.path
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedFiles = if (file.path in selectedFiles) selectedFiles - file.path else selectedFiles + file.path
                                    if (selectedFiles.isEmpty()) isSelectionMode = false
                                } else if (file.isDir) {
                                    navigateTo(file.path)
                                }
                            },
                            onCopy = {
                                clipboardFile = file; clipboardOp = FileOp.COPY
                                scope.launch { snackbarHostState.showSnackbar("\"${file.name}\" ready to copy — navigate to destination and tap Paste") }
                            },
                            onCut = {
                                clipboardFile = file; clipboardOp = FileOp.MOVE
                                scope.launch { snackbarHostState.showSnackbar("\"${file.name}\" ready to move — navigate to destination and tap Paste") }
                            },
                            onRename = { showRenameDialog = file; renameText = file.name },
                            onDelete = { showDeleteDialog = listOf(file) },
                            onInfo = { showInfoDialog = file },
                            onDownload = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Reading \"${file.name}\"…")
                                    val b64Result = withContext(Dispatchers.IO) {
                                        connectionManager.exec("base64 \"${file.path}\" 2>/dev/null")
                                    }
                                    if (b64Result.output.isBlank()) {
                                        snackbarHostState.showSnackbar("Download failed: ${b64Result.error.take(80)}")
                                        return@launch
                                    }
                                    val bytes = try {
                                        Base64.decode(b64Result.output.replace("\n", "").replace(" ", ""), Base64.DEFAULT)
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Decode error: ${e.message?.take(60)}")
                                        return@launch
                                    }
                                    if (bytes.size > 50 * 1024 * 1024) {
                                        snackbarHostState.showSnackbar("File too large to download (>50 MB). Use adb pull.")
                                        return@launch
                                    }
                                    pendingDownloadBytes = bytes
                                    pendingDownloadName = file.name
                                    downloadLauncher.launch(file.name)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: RemoteFileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onDownload: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                fileIcon(file), null,
                modifier = Modifier.size(36.dp),
                tint = if (file.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = if (file.isDir) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (file.size.isNotBlank()) Text(file.size, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (file.permissions.isNotBlank()) Text(file.permissions, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (file.modifiedDate.isNotBlank()) Text(file.modifiedDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(showMenu, { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { showMenu = false; onCopy() })
                    DropdownMenuItem(text = { Text("Cut") }, leadingIcon = { Icon(Icons.Default.ContentCut, null) }, onClick = { showMenu = false; onCut() })
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }, onClick = { showMenu = false; onRename() })
                    if (!file.isDir) DropdownMenuItem(text = { Text("Download to this device") }, leadingIcon = { Icon(Icons.Default.Download, null) }, onClick = { showMenu = false; onDownload() })
                    DropdownMenuItem(text = { Text("Properties") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { showMenu = false; onInfo() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun PathBreadcrumbs(pathStack: List<String>, onNavigateTo: (String) -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(pathStack) { scrollState.animateScrollTo(scrollState.maxValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pathStack.forEachIndexed { index, path ->
            val label = if (index == 0) "/" else path.substringAfterLast("/")
            val isLast = index == pathStack.size - 1
            Text(
                label,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = !isLast) { onNavigateTo(path) }
            )
            if (!isLast) {
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("$label:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 12.sp, fontFamily = if (label == "Path" || label == "Permissions") FontFamily.Monospace else FontFamily.Default, modifier = Modifier.weight(1f))
    }
}
