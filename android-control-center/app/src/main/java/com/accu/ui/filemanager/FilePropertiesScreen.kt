package com.accu.ui.filemanager

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.ACCTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class FilePropertiesViewModel @Inject constructor(
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    fun chmod(path: String, modeStr: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = connectionManager.exec("chmod $modeStr \"$path\"")
            onResult(
                if (result.isSuccess) "Permissions applied: $modeStr"
                else "Failed — ACCU connection required: ${result.error.take(80)}"
            )
        }
    }

    suspend fun statFile(path: String): Map<String, String> = withContext(Dispatchers.IO) {
        val result = connectionManager.exec("stat -c '%A|%U|%G|%s|%y|%x|%z' \"$path\" 2>/dev/null")
        val parts = result.output.trim().split("|")
        mapOf(
            "perms"    to (parts.getOrNull(0) ?: ""),
            "owner"    to (parts.getOrNull(1) ?: ""),
            "group"    to (parts.getOrNull(2) ?: ""),
            "size"     to (parts.getOrNull(3) ?: ""),
            "modified" to (parts.getOrNull(4)?.take(19) ?: ""),
            "accessed" to (parts.getOrNull(5)?.take(19) ?: ""),
            "created"  to (parts.getOrNull(6)?.take(19) ?: ""),
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${"%.2f".format(bytes / 1_073_741_824.0)} GB"
    bytes >= 1_048_576     -> "${"%.2f".format(bytes / 1_048_576.0)} MB"
    bytes >= 1_024         -> "${"%.1f".format(bytes / 1_024.0)} KB"
    else                   -> "$bytes B"
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault()).format(Date(millis))

private fun mimeForExt(ext: String) = when (ext) {
    "JPG", "JPEG" -> "image/jpeg"
    "PNG"  -> "image/png"
    "WEBP" -> "image/webp"
    "GIF"  -> "image/gif"
    "MP4"  -> "video/mp4"
    "MKV"  -> "video/x-matroska"
    "AVI"  -> "video/avi"
    "WEBM" -> "video/webm"
    "MP3"  -> "audio/mpeg"
    "FLAC" -> "audio/flac"
    "OGG"  -> "audio/ogg"
    "M4A"  -> "audio/mp4"
    "WAV"  -> "audio/wav"
    "APK"  -> "application/vnd.android.package-archive"
    "PDF"  -> "application/pdf"
    "ZIP"  -> "application/zip"
    else   -> "application/octet-stream"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePropertiesScreen(
    filePath: String = "/sdcard/DCIM/Camera/IMG_20240530_143022.jpg",
    onBack: () -> Unit = {},
    vm: FilePropertiesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val fileName = filePath.substringAfterLast("/")
    val fileExt = fileName.substringAfterLast(".", "").uppercase()
    val isApk = fileExt == "APK"
    val isImage = fileExt in listOf("JPG", "JPEG", "PNG", "WEBP", "GIF")
    val isAudio = fileExt in listOf("MP3", "FLAC", "OGG", "M4A", "WAV")
    val isVideo = fileExt in listOf("MP4", "MKV", "AVI", "WEBM")

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        add("Basic")
        if (isApk) add("APK Info")
        add("Permissions")
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
                "Basic"      -> BasicTab(fileName, filePath, fileExt, context)
                "APK Info"   -> ApkInfoTab(filePath, context)
                "Permissions"-> PermissionsTab(filePath, vm)
                "Checksums"  -> ChecksumsTab(filePath)
                "Media Info" -> MediaInfoTab(filePath, fileExt, isImage, isAudio, isVideo, context)
                else         -> BasicTab(fileName, filePath, fileExt, context)
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
private fun BasicTab(name: String, path: String, ext: String, context: android.content.Context) {
    val file = remember(path) { File(path) }
    val size = remember(path) { if (file.exists()) file.length() else 0L }
    val modified = remember(path) { if (file.exists()) formatDate(file.lastModified()) else "Unknown" }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item { PropRow("Name", name) }
        item { PropRow("Extension", ext) }
        item { PropRow("Size", if (size > 0) "${formatBytes(size)} (${"%,d".format(size)} bytes)" else "Unknown") }
        item { PropRow("Location", path.substringBeforeLast("/")) }
        item { PropRow("Full path", path) }
        item { PropRow("Modified", modified) }
        item { PropRow("MIME type", mimeForExt(ext)) }
        item { PropRow("Exists", if (file.exists()) "Yes" else "No") }
        if (file.isDirectory) item { PropRow("Contents", "${file.listFiles()?.size ?: 0} items") }
    }
}

@Composable
private fun PermissionsTab(path: String, vm: FilePropertiesViewModel = hiltViewModel()) {
    var statInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(path) {
        statInfo = vm.statFile(path)
        isLoading = false
    }

    val perms = statInfo["perms"] ?: ""
    val parsePerms = { idx: Int -> perms.getOrElse(idx) { '-' } != '-' }

    var oR by remember(perms) { mutableStateOf(parsePerms(1)) }
    var oW by remember(perms) { mutableStateOf(parsePerms(2)) }
    var oX by remember(perms) { mutableStateOf(parsePerms(3)) }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        if (isLoading) {
            item { Box(Modifier.fillParentMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            item { PropRow("Permissions", statInfo["perms"]?.ifBlank { "Unknown" } ?: "Unknown") }
            item { PropRow("Owner", statInfo["owner"]?.ifBlank { "Unknown" } ?: "Unknown") }
            item { PropRow("Group", statInfo["group"]?.ifBlank { "Unknown" } ?: "Unknown") }
            if (perms.length >= 10) {
                item { PropRow("Owner read",    if (parsePerms(1)) "Yes" else "No") }
                item { PropRow("Owner write",   if (parsePerms(2)) "Yes" else "No") }
                item { PropRow("Owner exec",    if (parsePerms(3)) "Yes" else "No") }
                item { PropRow("Group read",    if (parsePerms(4)) "Yes" else "No") }
                item { PropRow("Group write",   if (parsePerms(5)) "Yes" else "No") }
                item { PropRow("Group exec",    if (parsePerms(6)) "Yes" else "No") }
                item { PropRow("Others read",   if (parsePerms(7)) "Yes" else "No") }
                item { PropRow("Others write",  if (parsePerms(8)) "Yes" else "No") }
                item { PropRow("Others exec",   if (parsePerms(9)) "Yes" else "No") }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("Change Permissions", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Owner", fontWeight = FontWeight.Medium, fontSize = 13.sp)
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
                        vm.chmod(path, modeStr) { msg -> snackMsg = msg }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Apply via ACCU") }
            }
        }
    }
}

@Composable
private fun ChecksumsTab(filePath: String) {
    var computing by remember { mutableStateOf(false) }
    var md5 by remember { mutableStateOf("") }
    var sha1 by remember { mutableStateOf("") }
    var sha256 by remember { mutableStateOf("") }
    var sha512 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun computeHash(algorithm: String): String = try {
        val md = MessageDigest.getInstance(algorithm)
        File(filePath).inputStream().use { fis ->
            val buf = ByteArray(65536)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) md.update(buf, 0, read)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "Error: ${e.message}" }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            if (md5.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        computing = true
                        error = ""
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                if (!File(filePath).exists()) {
                                    withContext(Dispatchers.Main) { error = "File not accessible: $filePath"; computing = false }
                                    return@withContext
                                }
                                val md5r  = computeHash("MD5")
                                val sha1r = computeHash("SHA-1")
                                val sha256r = computeHash("SHA-256")
                                val sha512r = computeHash("SHA-512")
                                withContext(Dispatchers.Main) {
                                    md5 = md5r; sha1 = sha1r; sha256 = sha256r; sha512 = sha512r
                                    computing = false
                                }
                            }
                        }
                    }, enabled = !computing) {
                        if (computing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Calculate, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (computing) "Computing…" else "Compute checksums")
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
        if (md5.isNotEmpty()) {
            item { PropRow("MD5",    md5) }
            item { PropRow("SHA-1",  sha1) }
            item { PropRow("SHA-256", sha256) }
            item { PropRow("SHA-512", sha512) }
        }
    }
}

@Composable
private fun ApkInfoTab(filePath: String, context: android.content.Context) {
    var isLoading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val pi = try { pm.getPackageArchiveInfo(filePath, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS) } catch (_: Exception) { null }
            val map = if (pi != null) {
                val ai = pi.applicationInfo
                ai?.sourceDir = filePath
                ai?.publicSourceDir = filePath
                val label = try { ai?.loadLabel(pm)?.toString() ?: pi.packageName } catch (_: Exception) { pi.packageName }
                mapOf(
                    "App name"     to label,
                    "Package name" to pi.packageName,
                    "Version name" to (pi.versionName ?: "Unknown"),
                    "Version code" to pi.longVersionCode.toString(),
                    "Min SDK"      to (ai?.minSdkVersion?.let { "$it (Android ${sdkToName(it)})" } ?: "Unknown"),
                    "Target SDK"   to (ai?.targetSdkVersion?.let { "$it (Android ${sdkToName(it)})" } ?: "Unknown"),
                    "Activities"   to (pi.activities?.size?.toString() ?: "0"),
                    "Services"     to (pi.services?.size?.toString() ?: "0"),
                    "Receivers"    to (pi.receivers?.size?.toString() ?: "0"),
                    "Providers"    to (pi.providers?.size?.toString() ?: "0"),
                    "Permissions"  to (pi.requestedPermissions?.size?.let { "$it requested" } ?: "0"),
                )
            } else mapOf("Error" to "Could not read APK info from $filePath")
            withContext(Dispatchers.Main) { info = map; isLoading = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        if (isLoading) {
            item { Box(Modifier.fillParentMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            info.forEach { (k, v) -> item { PropRow(k, v) } }
        }
    }
}

@Composable
private fun MediaInfoTab(filePath: String, ext: String, isImage: Boolean, isAudio: Boolean, isVideo: Boolean, context: android.content.Context) {
    var isLoading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            val map = mutableMapOf<String, String>()
            if (!file.exists()) {
                map["Error"] = "File not accessible"
            } else if (isAudio || isVideo) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(filePath)
                    val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    if (durationMs != null) {
                        val mins = durationMs / 60000
                        val secs = (durationMs % 60000) / 1000
                        map["Duration"] = "$mins:${secs.toString().padStart(2, '0')}"
                    }
                    if (isAudio) {
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { map["Bit rate"] = "${it / 1000} kbps" }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { if (it.isNotBlank()) map["Title"] = it }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { if (it.isNotBlank()) map["Artist"] = it }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { if (it.isNotBlank()) map["Album"] = it }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { if (it.isNotBlank()) map["Year"] = it }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { if (it.isNotBlank()) map["Genre"] = it }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let { if (it.isNotBlank()) map["Album artist"] = it }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { if (it.isNotBlank()) map["Disc"] = it }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { if (it.isNotBlank()) map["Track"] = it }
                    }
                    if (isVideo) {
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let { w ->
                            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let { h ->
                                map["Resolution"] = "$w × $h"
                            }
                        }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let { if (it.isNotBlank()) map["Frame rate"] = "$it fps" }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { map["Bit rate"] = "${"%.1f".format(it / 1_000_000.0)} Mbps" }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let { if (it != "0") map["Rotation"] = "$it°" }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { map["MIME type"] = it }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.let { map["Has audio"] = if (it == "yes") "Yes" else "No" }
                    }
                } catch (e: Exception) {
                    map["Error"] = "Could not read media metadata: ${e.message?.take(100)}"
                } finally {
                    mmr.release()
                }
            } else if (isImage) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(filePath, opts)
                if (opts.outWidth > 0) map["Resolution"] = "${opts.outWidth} × ${opts.outHeight} px"
                opts.outMimeType?.let { if (it.isNotBlank()) map["MIME type"] = it }
                map["File size"] = formatBytes(file.length())
            }
            withContext(Dispatchers.Main) { info = map; isLoading = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        if (isLoading) {
            item { Box(Modifier.fillParentMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (info.isEmpty()) {
            item { Box(Modifier.fillParentMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No media metadata available", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        } else {
            info.forEach { (k, v) -> item { PropRow(k, v) } }
        }
    }
}

private fun sdkToName(sdk: Int) = when (sdk) {
    34 -> "14"; 33 -> "13"; 32 -> "12L"; 31 -> "12"; 30 -> "11"; 29 -> "10"
    28 -> "9"; 27 -> "8.1"; 26 -> "8.0"; 25 -> "7.1"; 24 -> "7.0"; 23 -> "6.0"
    22 -> "5.1"; 21 -> "5.0"; 19 -> "4.4"; else -> sdk.toString()
}
