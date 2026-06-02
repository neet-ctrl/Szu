package com.accu.ui.appmanager

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ApkFile(
    val name: String,
    val path: String,
    val size: String,
    val sizeBytes: Long,
    val packageName: String?,
    val versionName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val isInstalled: Boolean,
)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000.0)} GB"
    bytes >= 1_000_000     -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000         -> "${bytes / 1_000} KB"
    else                   -> "$bytes B"
}

private fun scanDirectoryForApks(root: File, maxDepth: Int = 6): List<File> {
    if (!root.exists() || !root.canRead() || maxDepth <= 0) return emptyList()
    val result = mutableListOf<File>()
    try {
        root.listFiles()?.forEach { file ->
            when {
                file.isFile && file.extension.equals("apk", ignoreCase = true) -> result.add(file)
                file.isDirectory -> result.addAll(scanDirectoryForApks(file, maxDepth - 1))
            }
        }
    } catch (_: SecurityException) {}
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureApksScreen(onBack: () -> Unit = {}, onNavigateToAppDetail: (String) -> Unit = {}) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    val context = LocalContext.current

    var apks by remember { mutableStateOf<List<ApkFile>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("Size") }
    var showSortMenu by remember { mutableStateOf(false) }
    var deletedPaths by remember { mutableStateOf(setOf<String>()) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    fun installApk(apk: ApkFile) {
        try {
            val file = File(apk.path)
            if (!file.exists()) { snackbar = "File not found: ${apk.path}"; return }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) { snackbar = "Cannot install: ${e.message}" }
    }

    fun shareApk(apk: ApkFile) {
        try {
            val file = File(apk.path)
            if (!file.exists()) { snackbar = "File not found"; return }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share APK",
                )
            )
        } catch (e: Exception) { snackbar = "Cannot share: ${e.message}" }
    }

    fun startScan() {
        isScanning = true
        hasScanned = false
        apks = emptyList()
        deletedPaths = emptySet()

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val installedPkgs = try { pm.getInstalledPackages(0).map { it.packageName }.toSet() } catch (_: Exception) { emptySet() }
            val result = mutableListOf<ApkFile>()

            // 1. Scan accessible local storage dirs
            withContext(Dispatchers.Main) { scanStatus = "Scanning local storage…" }
            val scanDirs = listOf(
                Environment.getExternalStorageDirectory(),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                context.getExternalFilesDir(null)?.parentFile?.parentFile,
                File("/sdcard"),
            ).filterNotNull().distinctBy { it.absolutePath }.filter { it.exists() }

            val foundFiles = mutableSetOf<String>()
            scanDirs.forEach { dir ->
                scanDirectoryForApks(dir).forEach { file ->
                    if (foundFiles.add(file.absolutePath)) {
                        val sizeBytes = file.length()
                        val pkg = try {
                            pm.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
                        } catch (_: Exception) { null }
                        val archiveInfo = try {
                            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
                        } catch (_: Exception) { null }
                        val versionName = archiveInfo?.versionName
                        val minSdk = archiveInfo?.applicationInfo?.minSdkVersion
                        @Suppress("DEPRECATION")
                        val targetSdk = archiveInfo?.applicationInfo?.targetSdkVersion
                        result += ApkFile(
                            name        = file.name,
                            path        = file.absolutePath,
                            size        = formatBytes(sizeBytes),
                            sizeBytes   = sizeBytes,
                            packageName = pkg,
                            versionName = versionName,
                            minSdk      = minSdk,
                            targetSdk   = targetSdk,
                            isInstalled = pkg != null && pkg in installedPkgs,
                        )
                    }
                }
            }

            // 2. Extend with ADB find if connected
            withContext(Dispatchers.Main) { scanStatus = "Extending scan via ADB…" }
            try {
                val adbRaw = connectionManager.exec("find /sdcard /data/local/tmp /storage -name '*.apk' -maxdepth 8 2>/dev/null").output
                adbRaw.lines().filter { it.endsWith(".apk") }.forEach { path ->
                    val trimmed = path.trim()
                    if (foundFiles.add(trimmed)) {
                        val file = File(trimmed)
                        val sizeBytes = file.length()
                        val pkg = try { pm.getPackageArchiveInfo(trimmed, 0)?.packageName } catch (_: Exception) { null }
                        result += ApkFile(
                            name        = file.name,
                            path        = trimmed,
                            size        = if (sizeBytes > 0) formatBytes(sizeBytes) else "? MB",
                            sizeBytes   = sizeBytes,
                            packageName = pkg,
                            versionName = null,
                            minSdk      = null,
                            targetSdk   = null,
                            isInstalled = pkg != null && pkg in installedPkgs,
                        )
                    }
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                apks = result
                isScanning = false
                hasScanned = true
                scanStatus = "Found ${result.size} APK files"
            }
        }
    }

    val visible = apks.filter { it.path !in deletedPaths }
    val filtered = visible
        .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || (it.packageName?.contains(search, ignoreCase = true) == true) }
        .let { list ->
            when (sortMode) {
                "Size" -> list.sortedByDescending { it.sizeBytes }
                "Name" -> list.sortedBy { it.name }
                "Installed" -> list.sortedByDescending { it.isInstalled }
                else   -> list
            }
        }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "APK Scanner",
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, null) }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            listOf("Size", "Name", "Installed").forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    leadingIcon = { if (sortMode == m) Icon(Icons.Default.Check, null) },
                                    onClick = { sortMode = m; showSortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { if (!isScanning) startScan() }) {
                        Icon(if (isScanning) Icons.Default.Sync else Icons.Default.Search, "Scan")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FindInPage, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("APK Scanner", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                isScanning  -> scanStatus
                                hasScanned  -> "Found ${visible.size} APK files"
                                else        -> "Tap Scan to find APK files in storage"
                            },
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { if (!isScanning) startScan() }) {
                        if (isScanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (hasScanned) "Re-scan" else "Scan")
                    }
                }
                if (isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp))
                }
            }

            if (!hasScanned && !isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.FindInPage, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No scan yet", style = MaterialTheme.typography.titleMedium)
                        Text("Tap 'Scan' to find all APK files on your device storage.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { startScan() }) { Text("Start Scan") }
                    }
                }
                return@Scaffold
            }

            if (hasScanned) {
                OutlinedTextField(
                    search, { search = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search APKs…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Clear, null) } },
                    singleLine = true,
                )

                if (apks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No APK files found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Try granting Storage permission or connecting via ACCU", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    return@Scaffold
                }

                Text("${filtered.size} APKs · ${formatBytes(visible.sumOf { it.sizeBytes })} total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.path }) { apk ->
                        var expanded by remember { mutableStateOf(false) }
                        ElevatedCard(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                                .clickable { expanded = !expanded },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Android, null,
                                        tint = if (apk.packageName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(apk.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${apk.size} · ${apk.packageName ?: "Unknown package"}",
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (apk.isInstalled && apk.packageName != null) {
                                        SuggestionChip(
                                            onClick = { onNavigateToAppDetail(apk.packageName) },
                                            label = { Text("Installed", fontSize = 10.sp) },
                                            modifier = Modifier.height(22.dp),
                                        )
                                    }
                                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }

                                if (expanded) {
                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))
                                    Text(apk.path, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    if (apk.versionName != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("Version: ${apk.versionName}", fontSize = 12.sp)
                                    }
                                    if (apk.minSdk != null) {
                                        Text("SDK: min ${apk.minSdk} · target ${apk.targetSdk ?: "?"}", fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { installApk(apk) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.InstallMobile, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Install", fontSize = 12.sp)
                                        }
                                        OutlinedButton(onClick = { shareApk(apk) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Share, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Share", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val deleted = File(apk.path).delete()
                                                if (deleted) {
                                                    deletedPaths = deletedPaths + apk.path
                                                    snackbar = "Deleted ${apk.name}"
                                                } else {
                                                    snackbar = "Cannot delete — use ADB or root"
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        ) {
                                            Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Delete", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
