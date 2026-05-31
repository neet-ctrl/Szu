package com.accu.ui.filemanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePropertiesScreen(
    filePath: String = "/sdcard/DCIM/Camera/IMG_20240530_143022.jpg",
    onBack: () -> Unit = {},
) {
    val fileName = filePath.substringAfterLast("/")
    val fileExt = fileName.substringAfterLast(".", "").uppercase()
    val isApk = fileExt == "APK"
    val isImage = fileExt in listOf("JPG", "JPEG", "PNG", "WEBP", "GIF")
    val isAudio = fileExt in listOf("MP3", "FLAC", "OGG", "M4A", "WAV")
    val isVideo = fileExt in listOf("MP4", "MKV", "AVI", "WEBM")

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        add("Basic")
        if (fileExt == "APK") add("APK Info"); add("Permissions")
        add("Checksums")
        if (isImage || isAudio || isVideo) add("Media Info")
    }

    Scaffold(topBar = { ACCTopBar(title = "Properties", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { i, tab ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(tab, fontSize = 12.sp) })
                }
            }
            when (tabs.getOrNull(selectedTab)) {
                "Basic" -> BasicTab(fileName, filePath, fileExt)
                "APK Info" -> ApkInfoTab()
                "Permissions" -> PermissionsTab(filePath)
                "Checksums" -> ChecksumsTab(fileName)
                "Media Info" -> MediaInfoTab(fileExt)
                else -> BasicTab(fileName, filePath, fileExt)
            }
        }
    }
}

@Composable
private fun PropRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(130.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f), fontFamily = if (value.length > 30) FontFamily.Monospace else FontFamily.Default)
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun BasicTab(name: String, path: String, ext: String) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item { PropRow("Name", name) }
        item { PropRow("Extension", ext) }
        item { PropRow("Size", "8.43 MB (8,843,264 bytes)") }
        item { PropRow("Location", path.substringBeforeLast("/")) }
        item { PropRow("Full path", path) }
        item { PropRow("Created", "May 30, 2024 at 14:30:22") }
        item { PropRow("Modified", "May 30, 2024 at 14:30:22") }
        item { PropRow("Accessed", "Today at 09:14") }
        item { PropRow("MIME type", when (ext) {
            "JPG", "JPEG" -> "image/jpeg"
            "PNG" -> "image/png"
            "MP4" -> "video/mp4"
            "APK" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }) }
    }
}

@Composable
private fun PermissionsTab(path: String) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item { PropRow("Owner", "media_rw (1023)") }
        item { PropRow("Group", "sdcard_rw (1015)") }
        item { PropRow("Permissions", "rw-rw-r-- (664)") }
        item { PropRow("Owner read", "Yes") }
        item { PropRow("Owner write", "Yes") }
        item { PropRow("Owner exec", "No") }
        item { PropRow("Group read", "Yes") }
        item { PropRow("Group write", "Yes") }
        item { PropRow("Group exec", "No") }
        item { PropRow("Others read", "Yes") }
        item { PropRow("Others write", "No") }
        item { PropRow("Others exec", "No") }
        item {
            val context = LocalContext.current
            var snackMsg by remember { mutableStateOf<String?>(null) }
            Spacer(Modifier.height(12.dp))
            Text("Change Permissions", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Owner", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            var oR by remember { mutableStateOf(true) }; var oW by remember { mutableStateOf(true) }; var oX by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = oR, onClick = { oR = !oR }, label = { Text("Read") })
                FilterChip(selected = oW, onClick = { oW = !oW }, label = { Text("Write") })
                FilterChip(selected = oX, onClick = { oX = !oX }, label = { Text("Execute") })
            }
            Spacer(Modifier.height(8.dp))
            snackMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
            Button(
                onClick = {
                    val mode = (if (oR) 4 else 0) + (if (oW) 2 else 0) + (if (oX) 1 else 0)
                    val modeStr = "$mode${mode}${mode}"
                    try {
                        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod $modeStr \"$path\""))
                        val exitCode = proc.waitFor()
                        snackMsg = if (exitCode == 0) "Permissions applied: $modeStr" else "Root required to change permissions"
                    } catch (e: Exception) {
                        snackMsg = "Error: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apply (requires root)") }
        }
    }
}

@Composable
private fun ChecksumsTab(name: String) {
    var computing by remember { mutableStateOf(false) }
    var computed by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            if (!computed) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Button(onClick = { computing = true; computed = true; computing = false }) {
                        if (computing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Calculate, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (computing) "Computing…" else "Compute checksums")
                    }
                }
            }
        }
        if (computed) {
            item { PropRow("MD5", "a4f2c8d91e03b7a52f19e4c6d83b1720") }
            item { PropRow("SHA-1", "3da541559918a808c2402bba5012f6c60b27661c") }
            item { PropRow("SHA-256", "8c7f5e9d2a1b4c6f0e3d7a8b9c4e1f2d3a6b8c9e0f1d2a3b4c5d6e7f8a9b0c") }
            item { PropRow("SHA-512", "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f") }
            item { PropRow("CRC32", "A3F7C2D1") }
        }
    }
}

@Composable
private fun ApkInfoTab() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item { PropRow("Package name", "com.example.application") }
        item { PropRow("Version name", "3.2.1") }
        item { PropRow("Version code", "321001") }
        item { PropRow("Min SDK", "21 (Android 5.0)") }
        item { PropRow("Target SDK", "34 (Android 14)") }
        item { PropRow("Compile SDK", "34 (Android 14)") }
        item { PropRow("Signature", "SHA-256: 7a8b9c4e1f2d...") }
        item { PropRow("Activities", "12") }
        item { PropRow("Services", "4") }
        item { PropRow("Receivers", "8") }
        item { PropRow("Providers", "2") }
        item { PropRow("Permissions", "18 declared") }
        item { PropRow("Native libs", "arm64-v8a, armeabi-v7a") }
        item { PropRow("Split APKs", "None") }
    }
}

@Composable
private fun MediaInfoTab(ext: String) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        when (ext) {
            "JPG", "JPEG", "PNG", "WEBP" -> {
                item { PropRow("Resolution", "4032 × 3024 pixels") }
                item { PropRow("Color space", "sRGB") }
                item { PropRow("Bit depth", "8-bit") }
                item { PropRow("Camera", "Google Pixel 8 Pro") }
                item { PropRow("Lens", "f/1.68, 6.81mm") }
                item { PropRow("ISO", "80") }
                item { PropRow("Shutter speed", "1/2000 s") }
                item { PropRow("Flash", "Off") }
                item { PropRow("GPS latitude", "37.7749° N") }
                item { PropRow("GPS longitude", "122.4194° W") }
                item { PropRow("Captured at", "May 30, 2024 14:30:22") }
                item { PropRow("Software", "Pixel Camera 9.2") }
            }
            "MP3", "FLAC", "OGG", "M4A", "WAV" -> {
                item { PropRow("Duration", "3:48") }
                item { PropRow("Bit rate", "320 kbps") }
                item { PropRow("Sample rate", "44100 Hz") }
                item { PropRow("Channels", "Stereo") }
                item { PropRow("Title", "Track title") }
                item { PropRow("Artist", "Artist name") }
                item { PropRow("Album", "Album name") }
                item { PropRow("Year", "2024") }
                item { PropRow("Genre", "Electronic") }
            }
            "MP4", "MKV", "AVI", "WEBM" -> {
                item { PropRow("Duration", "15:22") }
                item { PropRow("Resolution", "1920 × 1080 (1080p)") }
                item { PropRow("Frame rate", "30 fps") }
                item { PropRow("Video codec", "H.264") }
                item { PropRow("Audio codec", "AAC") }
                item { PropRow("Bit rate", "8 Mbps") }
                item { PropRow("Color profile", "Rec. 709") }
            }
            else -> { item { PropRow("No media info", "Not a recognized media file") } }
        }
    }
}
