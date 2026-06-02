package com.accu.ui.appmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApkEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val compressedSize: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureUnpackScreen(
    packageName: String,
    onBack: () -> Unit = {},
) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    val context = LocalContext.current

    var entries by remember { mutableStateOf<List<ApkEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var currentPath by remember { mutableStateOf("") }
    var filterExt by remember { mutableStateOf("All") }
    var apkPath by remember { mutableStateOf("") }
    var totalSize by remember { mutableStateOf(0L) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("name") } // name | size

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                // Get APK path
                val pathOutput = connectionManager.exec("pm path $packageName 2>/dev/null").output
                apkPath = pathOutput.lines().firstOrNull { it.startsWith("package:") }
                    ?.removePrefix("package:")?.trim() ?: ""

                if (apkPath.isEmpty()) {
                    error = "Could not find APK path for $packageName"
                    loading = false
                    return@withContext
                }

                // List files in APK using unzip -l
                val raw = connectionManager.exec("unzip -l \"$apkPath\" 2>/dev/null").output
                val result = mutableListOf<ApkEntry>()
                var totalBytes = 0L

                raw.lines().forEach { line ->
                    val trimmed = line.trim()
                    // unzip -l format: "   <size>  <date> <time>   <name>"
                    // Or: "  <size>  <compressed>  <date> <time>  <name>"
                    if (trimmed.isEmpty() || trimmed.startsWith("Archive:") ||
                        trimmed.startsWith("Length") || trimmed.startsWith("-----") ||
                        trimmed.startsWith("Total") || trimmed.contains("files")) return@forEach

                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val size = parts[0].toLongOrNull() ?: 0L
                        val name = parts.last()
                        if (name.isNotEmpty() && !name.contains("Date") && !name.contains("Time")) {
                            val isDir = name.endsWith("/")
                            result += ApkEntry(
                                name = name.substringAfterLast("/").ifEmpty { name },
                                path = name,
                                sizeBytes = size,
                                isDirectory = isDir,
                                compressedSize = parts.getOrNull(1)?.toLongOrNull() ?: size,
                            )
                            if (!isDir) totalBytes += size
                        }
                    }
                }

                entries = result
                totalSize = totalBytes
            } catch (e: Exception) {
                error = e.message ?: "Failed to unpack APK"
            }
            loading = false
        }
    }

    // Available extensions filter
    val extensions = remember(entries) {
        listOf("All") + entries.filter { !it.isDirectory }
            .map { it.path.substringAfterLast(".").lowercase() }
            .filter { it.length in 2..5 }
            .distinct().sorted()
    }

    val filtered = remember(entries, searchQuery, currentPath, filterExt, sortBy) {
        entries
            .filter { entry ->
                val matchPath = if (currentPath.isEmpty()) true
                else entry.path.startsWith(currentPath) && entry.path != currentPath
                val searchOk = searchQuery.isBlank() || entry.path.contains(searchQuery, true)
                val extOk = filterExt == "All" || entry.path.endsWith(".$filterExt", true)
                matchPath && searchOk && extOk
            }
            .let { list ->
                when (sortBy) {
                    "size" -> list.sortedByDescending { it.sizeBytes }
                    else   -> list.sortedWith(compareByDescending<ApkEntry> { it.isDirectory }.thenBy { it.name })
                }
            }
    }

    // Group by folder at current level
    val displayItems = remember(filtered, currentPath) {
        if (searchQuery.isNotEmpty() || filterExt != "All") {
            filtered
        } else {
            // Show only direct children at current level
            val prefix = if (currentPath.isEmpty()) "" else "$currentPath/"
            val seen = mutableSetOf<String>()
            val result = mutableListOf<ApkEntry>()
            filtered.forEach { entry ->
                val relative = if (prefix.isEmpty()) entry.path else entry.path.removePrefix(prefix)
                val firstSegment = relative.substringBefore("/")
                val key = "$prefix$firstSegment"
                if (seen.add(key)) {
                    val hasChildren = filtered.any { it.path.startsWith("$key/") }
                    if (hasChildren || firstSegment == relative) {
                        result += if (hasChildren && firstSegment != relative) {
                            entry.copy(name = firstSegment, path = "$prefix$firstSegment", isDirectory = true)
                        } else {
                            entry
                        }
                    }
                }
            }
            result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("APK Explorer")
                    if (apkPath.isNotEmpty()) Text(apkPath.substringAfterLast("/"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }},
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath.isEmpty()) onBack()
                        else currentPath = currentPath.substringBeforeLast("/", "")
                    }) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    Box {
                        IconButton({ showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            DropdownMenuItem({ Text("By Name") }, { sortBy = "name"; showSortMenu = false })
                            DropdownMenuItem({ Text("By Size") }, { sortBy = "size"; showSortMenu = false })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Stats row
            if (!loading && entries.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("${entries.count { !it.isDirectory }} files", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatFileSize(totalSize), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (currentPath.isNotEmpty()) Text("/$currentPath", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }

            // Search
            OutlinedTextField(
                searchQuery, { searchQuery = it; if (it.isNotEmpty()) currentPath = "" },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search files in APK…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            // Extension filters
            if (extensions.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    extensions.take(8).forEach { ext ->
                        FilterChip(filterExt == ext, { filterExt = ext; currentPath = "" }, { Text(ext.uppercase()) }, Modifier.height(32.dp))
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Reading APK structure…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            if (error.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Column
            }

            if (displayItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(displayItems, key = { "${it.path}_${it.isDirectory}" }) { entry ->
                    ApkEntryRow(entry) {
                        if (entry.isDirectory) currentPath = entry.path.trimEnd('/')
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkEntryRow(entry: ApkEntry, onClick: () -> Unit) {
    val icon = when {
        entry.isDirectory -> Icons.Default.Folder
        entry.name.endsWith(".dex") -> Icons.Default.Code
        entry.name.endsWith(".so") -> Icons.Default.Dns
        entry.name.endsWith(".xml") -> Icons.Default.Description
        entry.name.endsWith(".png") || entry.name.endsWith(".webp") || entry.name.endsWith(".jpg") -> Icons.Default.Image
        entry.name.endsWith(".kotlin_module") || entry.name.endsWith(".class") -> Icons.Default.DataObject
        entry.name.endsWith(".properties") -> Icons.Default.Settings
        entry.name.endsWith(".ttf") || entry.name.endsWith(".otf") -> Icons.Default.TextFields
        entry.name.endsWith(".js") -> Icons.Default.Javascript
        else -> Icons.Default.InsertDriveFile
    }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!entry.isDirectory) Text(entry.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!entry.isDirectory) {
            Text(formatFileSize(entry.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024     -> "%.1f KB".format(bytes / 1_024.0)
    else               -> "$bytes B"
}
