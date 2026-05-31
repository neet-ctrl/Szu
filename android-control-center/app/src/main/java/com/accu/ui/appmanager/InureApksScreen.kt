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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class ApkFile(
    val name: String,
    val path: String,
    val size: String,
    val packageName: String?,
    val versionName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val isInstalled: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureApksScreen(onBack: () -> Unit = {}) {
    var isScanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("Name") }
    var showSortMenu by remember { mutableStateOf(false) }

    val apks = remember {
        listOf(
            ApkFile("com.example.app.apk", "/sdcard/Download/com.example.app.apk", "28.4 MB", "com.example.app", "3.2.1", 21, 34, false),
            ApkFile("Instagram_v312.apk", "/sdcard/Download/Instagram_v312.apk", "72.1 MB", "com.instagram.android", "312.0.0.0.37", 26, 33, true),
            ApkFile("Shazam.apk", "/sdcard/Download/Shazam.apk", "45.2 MB", "com.shazam.android", "13.44.0", 23, 34, false),
            ApkFile("modded_game.apk", "/sdcard/Download/cracked/modded_game.apk", "120.5 MB", "com.game.adventure", "2.1.0", 21, 33, false),
            ApkFile("test_app_debug.apk", "/sdcard/Android/data/test_app_debug.apk", "3.8 MB", "com.test.debug", "1.0.0-debug", 24, 34, false),
            ApkFile("backup_chrome.apk", "/sdcard/Backups/backup_chrome.apk", "145.7 MB", "com.android.chrome", "124.0.6367.82", 29, 34, true),
            ApkFile("unknown_source.apk", "/sdcard/WhatsApp/Media/unknown_source.apk", "8.2 MB", null, null, null, null, false),
        )
    }

    val filtered = apks.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || (it.packageName?.contains(search, ignoreCase = true) == true) }
        .let { list -> when (sortMode) { "Size" -> list.sortedByDescending { it.size.replace(" MB", "").toFloatOrNull() ?: 0f }; "A–Z" -> list.sortedBy { it.name }; else -> list } }

    Scaffold(
        topBar = {
            ACCTopBar(title = "APK Scanner", onBack = onBack, actions = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, null) }
                    DropdownMenu(showSortMenu, { showSortMenu = false }) {
                        listOf("Name", "Size", "A–Z").forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, leadingIcon = { if (sortMode == m) Icon(Icons.Default.Check, null) }, onClick = { sortMode = m; showSortMenu = false })
                        }
                    }
                }
                IconButton(onClick = { isScanning = true; hasScanned = true; isScanning = false }) { Icon(Icons.Default.Search, "Scan") }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FindInPage, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("APK Scanner", fontWeight = FontWeight.SemiBold)
                        Text("Found ${apks.size} APK files in storage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { isScanning = true; hasScanned = true; isScanning = false }) {
                        if (isScanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Scan")
                    }
                }
            }

            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search APKs…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            if (hasScanned) {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.path }) { apk ->
                        var expanded by remember { mutableStateOf(false) }
                        ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clickable { expanded = !expanded }) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(apk.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                        Text("${apk.size} · ${if (apk.packageName != null) apk.packageName else "Unknown package"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (apk.isInstalled) SuggestionChip(onClick = {}, label = { Text("Installed", fontSize = 10.sp) }, modifier = Modifier.height(22.dp))
                                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }
                                if (expanded) {
                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))
                                    Text(apk.path, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (apk.versionName != null) {
                                        Text("Version: ${apk.versionName}", fontSize = 12.sp)
                                        Text("SDK: min ${apk.minSdk} · target ${apk.targetSdk}", fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {}, Modifier.weight(1f)) { Icon(Icons.Default.InstallMobile, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Install", fontSize = 12.sp) }
                                        OutlinedButton(onClick = {}, Modifier.weight(1f)) { Icon(Icons.Default.Share, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Share", fontSize = 12.sp) }
                                        OutlinedButton(onClick = {}, Modifier.weight(1f)) { Icon(Icons.Default.Delete, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Delete", fontSize = 12.sp) }
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
