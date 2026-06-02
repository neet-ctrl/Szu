package com.accu.ui.shell

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.shizuku.ShizukuViewModel
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbScreenCaptureScreen(onBack: () -> Unit = {}) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // SAF folder picker — user picks where to save on CONTROLLER internal storage
    var saveFolderUri by remember { mutableStateOf<Uri?>(null) }
    val saveFolderName by remember(saveFolderUri) {
        derivedStateOf {
            saveFolderUri?.lastPathSegment?.substringAfterLast(':')?.let { "…/$it" } ?: "Not chosen (tap to pick)"
        }
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> saveFolderUri = uri }

    // Screenshot state — temp path uses /data/local/tmp/ on TARGET device
    var screenshotPath by remember { mutableStateOf("/data/local/tmp/accu_screenshot.png") }
    var isCapturing by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf("") }
    var capturedScreenshots by remember { mutableStateOf(listOf<String>()) }

    // Recording state — temp path on TARGET device
    var recordingPath by remember { mutableStateOf("/data/local/tmp/accu_record.mp4") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTimer by remember { mutableIntStateOf(0) }
    var recordingSize by remember { mutableIntStateOf(0) }
    var recordingBitrate by remember { mutableStateOf("8000000") }
    var recordingSize2 by remember { mutableStateOf("1280x720") }
    var recordingMaxTime by remember { mutableStateOf("180") }
    var capturedRecordings by remember { mutableStateOf(listOf<String>()) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var recordingPullStatus by remember { mutableStateOf("") }

    // Timer for recording
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        recordingTimer = 0
        recordingSize = 0
        while (isRecording) {
            delay(1000)
            recordingTimer++
            val bps = recordingBitrate.toLongOrNull() ?: 8_000_000L
            recordingSize = ((bps.toDouble() / 8_000_000.0) * recordingTimer).toInt()
        }
    }

    fun timerLabel(s: Int) = "%02d:%02d".format(s / 60, s % 60)

    // Helper: write bytes to SAF folder, returns filename on success
    suspend fun writeToSafFolder(folderUri: Uri, filename: String, bytes: ByteArray, mimeType: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val docDir = DocumentFile.fromTreeUri(context, folderUri)
                    ?: return@withContext null
                val existing = docDir.findFile(filename)
                existing?.delete()
                val docFile = docDir.createFile(mimeType, filename)
                    ?: return@withContext null
                context.contentResolver.openOutputStream(docFile.uri)?.use { out -> out.write(bytes) }
                filename
            } catch (_: Exception) { null }
        }
    }

    fun onCaptureScreenshot() {
        scope.launch {
            isCapturing = true
            captureStatus = "Capturing…"
            try {
                // 1. Run screencap on target device
                val captureResult = withContext(Dispatchers.IO) {
                    connectionManager.exec("screencap -p \"$screenshotPath\" 2>&1")
                }
                if (captureResult.error.isNotBlank() && captureResult.output.isBlank()) {
                    snackbar.showSnackbar("Capture failed: ${captureResult.error.take(100)}")
                    return@launch
                }

                captureStatus = "Reading screenshot…"
                // 2. Read file as base64 from target
                val b64Result = withContext(Dispatchers.IO) {
                    connectionManager.exec("base64 \"$screenshotPath\" 2>/dev/null")
                }
                if (b64Result.output.isBlank()) {
                    snackbar.showSnackbar("Failed to read screenshot from device")
                    return@launch
                }

                // 3. Decode base64 → bytes
                val bytes = try {
                    Base64.decode(b64Result.output.replace("\n", "").replace(" ", ""), Base64.DEFAULT)
                } catch (e: Exception) {
                    snackbar.showSnackbar("Decode error: ${e.message?.take(60)}")
                    return@launch
                }

                // 4. Cleanup temp file on target
                withContext(Dispatchers.IO) { connectionManager.exec("rm -f \"$screenshotPath\"") }

                // 5. Save to SAF folder on controller
                val ts = System.currentTimeMillis()
                val fname = "screenshot_$ts.png"
                captureStatus = "Saving…"
                val fUri = saveFolderUri
                if (fUri != null) {
                    val saved = writeToSafFolder(fUri, fname, bytes, "image/png")
                    if (saved != null) {
                        capturedScreenshots = capturedScreenshots + saved
                        snackbar.showSnackbar("Screenshot saved: $saved ✓")
                    } else {
                        snackbar.showSnackbar("Save failed — try choosing a different folder")
                    }
                } else {
                    // No folder chosen — prompt user to pick
                    snackbar.showSnackbar("Choose a save folder first, then capture again")
                    folderPickerLauncher.launch(null)
                }
            } finally {
                isCapturing = false
                captureStatus = ""
            }
        }
    }

    fun onStartRecording() {
        val maxTimeSecs = recordingMaxTime.toIntOrNull()?.coerceIn(1, 180) ?: 180
        val cmd = "screenrecord --size $recordingSize2 --bit-rate $recordingBitrate --time-limit $maxTimeSecs \"$recordingPath\" 2>&1"
        isRecording = true
        recordingPullStatus = ""
        recordingJob = scope.launch(Dispatchers.IO) {
            connectionManager.exec(cmd)
            // Recording ended (by time-limit or stop signal) — pull the file
            withContext(Dispatchers.Main) { recordingPullStatus = "Pulling recording…" }

            val ts = System.currentTimeMillis()
            val estimatedMB = recordingSize
            val fname = "recording_$ts.mp4"

            val fUri = saveFolderUri
            if (fUri != null && estimatedMB <= 60) {
                withContext(Dispatchers.Main) { recordingPullStatus = "Encoding (${estimatedMB}MB)…" }
                val b64Result = connectionManager.exec("base64 \"$recordingPath\" 2>/dev/null")
                if (b64Result.output.isNotBlank()) {
                    val bytes = try {
                        Base64.decode(b64Result.output.replace("\n", "").replace(" ", ""), Base64.DEFAULT)
                    } catch (_: Exception) { null }
                    if (bytes != null) {
                        val saved = writeToSafFolder(fUri, fname, bytes, "video/mp4")
                        connectionManager.exec("rm -f \"$recordingPath\"")
                        withContext(Dispatchers.Main) {
                            if (saved != null) {
                                capturedRecordings = capturedRecordings + saved
                                snackbar.showSnackbar("Recording saved: $saved ✓")
                            } else {
                                snackbar.showSnackbar("Save failed — file left at $recordingPath on target")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            snackbar.showSnackbar("Decode failed — file left at $recordingPath on target")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        snackbar.showSnackbar("Recording at $recordingPath on target — pull with: adb pull $recordingPath")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    val reason = if (fUri == null) "No save folder chosen" else "File too large (${estimatedMB}MB)"
                    snackbar.showSnackbar("$reason — pull manually: adb pull $recordingPath")
                    capturedRecordings = capturedRecordings + fname
                }
            }

            withContext(Dispatchers.Main) {
                isRecording = false
                recordingPullStatus = ""
                recordingJob = null
            }
        }
    }

    fun onStopRecording() {
        scope.launch(Dispatchers.IO) {
            // Send SIGINT to screenrecord so it finalises the MP4 cleanly
            connectionManager.exec("pkill -SIGINT screenrecord 2>/dev/null; killall -2 screenrecord 2>/dev/null")
        }
        // isRecording stays true until the recordingJob coroutine completes and resets it
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ACCTopBar(
                title = "Screenshot & Screen Record",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "How it works",
                        description = "Screenshots:\nscreencap -p <temp-path>\nbase64-pull → saved to chosen folder\nrm <temp-path>\n\nScreen recording:\nscreenrecord <temp-path> (blocking)\npkill -SIGINT screenrecord to stop\nbase64-pull → saved to chosen folder\n\nFiles ≤60 MB are pulled automatically.\nLarger recordings stay on target — use: adb pull $recordingPath",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Save folder picker ────────────────────────────────────────────
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (saveFolderUri != null)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    onClick = { folderPickerLauncher.launch(null) },
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            if (saveFolderUri != null) Icons.Default.FolderOpen else Icons.Default.Folder,
                            null,
                            tint = if (saveFolderUri != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Save folder (this device)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                saveFolderName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            if (saveFolderUri == null) {
                                Text(
                                    "Tap to choose — saves to internal storage, not SD card",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
            }

            // ── Screenshot section ────────────────────────────────────────────
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Screenshot, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
                            }
                            Column {
                                Text("Screenshot Capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Capture screen via ADB and pull to device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        OutlinedTextField(
                            value = screenshotPath,
                            onValueChange = { screenshotPath = it },
                            label = { Text("Temp path on TARGET device") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(16.dp)) },
                        )

                        // ADB commands breakdown
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Commands executed:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CmdRow("screencap -p $screenshotPath", clipboard)
                                CmdRow("base64 $screenshotPath  →  decode → SAF write", clipboard)
                                CmdRow("rm -f $screenshotPath", clipboard)
                            }
                        }

                        Button(
                            onClick = { onCaptureScreenshot() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCapturing && !isRecording,
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(captureStatus.ifBlank { "Capturing…" })
                            } else {
                                Icon(Icons.Default.Screenshot, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Capture Screenshot")
                            }
                        }

                        if (capturedScreenshots.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Saved (${capturedScreenshots.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            capturedScreenshots.reversed().forEach { name ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Image, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(name)) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.ContentCopy, "Copy name", Modifier.size(14.dp))
                                    }
                                    IconButton(onClick = { capturedScreenshots = capturedScreenshots - name }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.Delete, "Remove", Modifier.size(14.dp), tint = AccentRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Screen recording section ──────────────────────────────────────
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isRecording) AccentRed.copy(0.15f) else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.Videocam,
                                        null, Modifier.size(24.dp),
                                        tint = if (isRecording) AccentRed else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Column {
                                Text("Screen Recording", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Record device screen via screenrecord (Android 4.4+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Live recording indicator
                        AnimatedVisibility(visible = isRecording) {
                            Surface(shape = RoundedCornerShape(12.dp), color = AccentRed.copy(0.08f)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.size(10.dp).background(AccentRed, androidx.compose.foundation.shape.CircleShape))
                                        Text(
                                            "RECORDING  ${timerLabel(recordingTimer)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentRed,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text("~${recordingSize} MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (recordingPullStatus.isNotBlank()) {
                                        Text(recordingPullStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        if (!isRecording) {
                            OutlinedTextField(
                                value = recordingPath, onValueChange = { recordingPath = it },
                                label = { Text("Output path on TARGET device") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                leadingIcon = { Icon(Icons.Outlined.VideoFile, null, Modifier.size(16.dp)) }
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = recordingSize2, onValueChange = { recordingSize2 = it }, label = { Text("Size (e.g. 1280x720)") }, modifier = Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(value = recordingBitrate, onValueChange = { recordingBitrate = it }, label = { Text("Bitrate (bps)") }, modifier = Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(value = recordingMaxTime, onValueChange = { recordingMaxTime = it }, label = { Text("Max time (seconds, max 180)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                        }

                        // Command preview
                        val recordCmd = "screenrecord --size $recordingSize2 --bit-rate $recordingBitrate --time-limit $recordingMaxTime $recordingPath"
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Commands:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CmdRow(recordCmd, clipboard)
                                CmdRow("pkill -SIGINT screenrecord  (to stop)", clipboard)
                                CmdRow("base64 $recordingPath  →  SAF write  (≤60 MB)", clipboard)
                            }
                        }

                        if (!isRecording) {
                            Button(
                                onClick = { onStartRecording() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                enabled = !isCapturing,
                            ) {
                                Icon(Icons.Default.FiberManualRecord, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start Recording", color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = { onStopRecording() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Stop Recording", color = Color.White)
                            }
                        }

                        if (capturedRecordings.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Recordings (${capturedRecordings.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            capturedRecordings.reversed().forEach { name ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VideoFile, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { clipboard.setText(AnnotatedString("adb pull $recordingPath")) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.ContentCopy, "Copy pull cmd", Modifier.size(14.dp))
                                    }
                                    IconButton(onClick = { capturedRecordings = capturedRecordings - name }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.Delete, "Remove", Modifier.size(14.dp), tint = AccentRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Tips card ─────────────────────────────────────────────────────
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tips & Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        listOf(
                            "Choose a save folder first — screenshots and short recordings pull automatically.",
                            "Recordings ≤60 MB are pulled via base64. Larger files stay on target — use adb pull.",
                            "screenrecord max duration is 3 minutes (180 seconds) on most devices.",
                            "On Android 11+, use Wireless ADB — no USB cable required.",
                            "screencap saves PNG; screenrecord saves MP4 (H.264).",
                            "On Samsung: use 'wm size' to find native resolution first.",
                        ).forEach { tip ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(tip, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CmdRow(cmd: String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 2)
        IconButton(onClick = { clipboard.setText(AnnotatedString(cmd)) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
}
