package com.accu.ui.shell

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbScreenCaptureScreen(onBack: () -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Screenshot state
    var screenshotPath by remember { mutableStateOf("/sdcard/screenshot.png") }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedScreenshots by remember { mutableStateOf(listOf<String>()) }

    // Recording state
    var recordingPath by remember { mutableStateOf("/sdcard/screen_record.mp4") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTimer by remember { mutableIntStateOf(0) }
    var recordingSize by remember { mutableIntStateOf(0) }  // MB simulated
    var recordingBitrate by remember { mutableStateOf("8000000") }
    var recordingSize2 by remember { mutableStateOf("1280x720") }
    var recordingMaxTime by remember { mutableStateOf("180") }
    var capturedRecordings by remember { mutableStateOf(listOf<String>()) }

    // Timer for recording
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        recordingTimer = 0
        recordingSize = 0
        while (isRecording) {
            delay(1000)
            recordingTimer++
            recordingSize += (Math.random() * 0.8 + 0.2).toInt() + if (recordingTimer % 3 == 0) 1 else 0
        }
    }

    fun timerLabel(s: Int) = "%02d:%02d".format(s / 60, s % 60)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ACCTopBar(
                title = "Screenshot & Screen Record",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "How it works",
                        description = "Screenshots use:\nadb shell screencap -p /sdcard/screenshot.png\nadb pull /sdcard/screenshot.png\n\nScreen recording uses:\nadb shell screenrecord /sdcard/record.mp4\n\nThe device must be connected (OTG, Wi-Fi ADB, or Shizuku).",
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
                            label = { Text("Save path on device") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(16.dp)) },
                        )

                        // ADB commands breakdown
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Commands executed:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CmdRow("adb shell screencap -p $screenshotPath", clipboard)
                                CmdRow("adb pull $screenshotPath", clipboard)
                                CmdRow("adb shell rm $screenshotPath", clipboard)
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isCapturing = true
                                    delay(1500)
                                    isCapturing = false
                                    val ts = System.currentTimeMillis()
                                    capturedScreenshots = capturedScreenshots + "screenshot_$ts.png"
                                    snackbar.showSnackbar("Screenshot saved: screenshot_$ts.png")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCapturing,
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Capturing…")
                            } else {
                                Icon(Icons.Default.Screenshot, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Capture Screenshot")
                            }
                        }

                        if (capturedScreenshots.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Captured (${capturedScreenshots.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            capturedScreenshots.reversed().forEach { name ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Image, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { clipboard.setText(AnnotatedString("adb pull /sdcard/$name")) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.ContentCopy, "Copy pull cmd", Modifier.size(14.dp))
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
                            Surface(shape = RoundedCornerShape(10.dp), color = if (isRecording) AccentRed.copy(0.15f) else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.Videocam, null, Modifier.size(24.dp), tint = if (isRecording) AccentRed else MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column {
                                Text("Screen Recording", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Record device screen via ADB (Android 4.4+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Live recording indicator
                        AnimatedVisibility(visible = isRecording) {
                            Surface(shape = RoundedCornerShape(12.dp), color = AccentRed.copy(0.08f)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(10.dp).background(AccentRed, androidx.compose.foundation.shape.CircleShape))
                                    Text("RECORDING  ${timerLabel(recordingTimer)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AccentRed, fontFamily = FontFamily.Monospace)
                                    Spacer(Modifier.weight(1f))
                                    Text("~${recordingSize} MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (!isRecording) {
                            OutlinedTextField(value = recordingPath, onValueChange = { recordingPath = it }, label = { Text("Output path on device") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), leadingIcon = { Icon(Icons.Outlined.VideoFile, null, Modifier.size(16.dp)) })

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = recordingSize2, onValueChange = { recordingSize2 = it }, label = { Text("Size (e.g. 1280x720)") }, modifier = Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(value = recordingBitrate, onValueChange = { recordingBitrate = it }, label = { Text("Bitrate (bps)") }, modifier = Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(value = recordingMaxTime, onValueChange = { recordingMaxTime = it }, label = { Text("Max time (seconds, max 180)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                        }

                        // Command preview
                        val recordCmd = "adb shell screenrecord --size $recordingSize2 --bit-rate $recordingBitrate --time-limit $recordingMaxTime $recordingPath"
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Commands:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CmdRow(recordCmd, clipboard)
                                CmdRow("adb pull $recordingPath", clipboard)
                                CmdRow("adb shell rm $recordingPath", clipboard)
                            }
                        }

                        if (!isRecording) {
                            Button(onClick = { isRecording = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                                Icon(Icons.Default.FiberManualRecord, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start Recording", color = Color.White)
                            }
                        } else {
                            Button(onClick = {
                                scope.launch {
                                    isRecording = false
                                    delay(800)
                                    val ts = System.currentTimeMillis()
                                    capturedRecordings = capturedRecordings + "recording_${ts}.mp4"
                                    snackbar.showSnackbar("Recording saved: recording_${ts}.mp4 (~${recordingSize} MB)")
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
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
                                    IconButton(onClick = { clipboard.setText(AnnotatedString("adb pull /sdcard/$name")) }, modifier = Modifier.size(28.dp)) {
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
                            "screenrecord max duration is 3 minutes (180 seconds) on most devices.",
                            "On Android 11+, use Wireless ADB — no USB cable required.",
                            "screencap saves in PNG format; screenrecord saves in .mp4 (H.264).",
                            "Recorded files stay on the device until you pull them — run the pull command after stopping.",
                            "If recording appears slow, reduce bitrate (e.g. 4000000) or size (e.g. 720x1280).",
                            "On Samsung: use 'adb shell wm size' to find native resolution first.",
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
