package com.accu.ui.callrecorder

import android.text.format.DateUtils
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.accu.data.db.entities.CallRecordingEntity
import com.accu.ui.components.*
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRecorderScreen(
    onBack: () -> Unit,
    viewModel: CallRecorderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Call Recorder",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Call Recorder — ShizuCallRecorder",
                        description = "Rootless call recording powered by Shizuku.\n\n• Automatically records all incoming/outgoing calls\n• Uses scrcpy audio capture for Android 10+ (no root required)\n• Formats: AAC, MP3, PCM, OGG\n• Audio source: MIC, VOICE_CALL, VOICE_COMMUNICATION\n\nRequires: Shizuku running + RECORD_AUDIO permission.\n\nAll recordings are stored locally and never uploaded."
                    )
                    IconButton(onClick = { viewModel.toggleSearch() }) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = { viewModel.toggleSettingsPanel() }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // Status card
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isRecordingEnabled) AccentGreen.copy(0.1f) else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Pulsing REC indicator
                            val recPulse = rememberInfiniteTransition(label = "rec")
                            val pulseScale by recPulse.animateFloat(
                                initialValue = 1f,
                                targetValue = if (state.isRecordingEnabled) 1.3f else 1f,
                                animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "pulse",
                            )
                            val pulseAlpha by recPulse.animateFloat(
                                initialValue = 1f,
                                targetValue = if (state.isRecordingEnabled) 0.4f else 1f,
                                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                                label = "alpha",
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(44.dp),
                            ) {
                                if (state.isRecordingEnabled) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentGreen.copy(pulseAlpha * 0.25f),
                                        modifier = Modifier.size(44.dp).scale(pulseScale),
                                    ) {}
                                }
                                Surface(shape = CircleShape, color = if (state.isRecordingEnabled) AccentGreen.copy(0.2f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(32.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.RadioButtonChecked,
                                            null,
                                            tint = if (state.isRecordingEnabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Call Recording", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (state.isRecordingEnabled) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = AccentGreen.copy(0.2f)) {
                                            Text("● REC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentGreen, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text(
                                    if (state.isRecordingEnabled) "Active — calls will be recorded automatically"
                                    else "Inactive — enable to record calls",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.isRecordingEnabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = state.isRecordingEnabled, onCheckedChange = { viewModel.toggleRecording() })
                        }
                        Spacer(Modifier.height(12.dp))
                        // Settings row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Format selector
                            var showFormatMenu by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { showFormatMenu = true }, Modifier.fillMaxWidth()) {
                                    Text("Format: ${state.recordingFormat}", style = MaterialTheme.typography.labelMedium)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(showFormatMenu, { showFormatMenu = false }) {
                                    listOf("AAC", "MP3", "PCM", "OGG").forEach { fmt ->
                                        DropdownMenuItem(text = { Text(fmt) }, onClick = { viewModel.setFormat(fmt); showFormatMenu = false })
                                    }
                                }
                            }
                            // Source selector
                            var showSourceMenu by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { showSourceMenu = true }, Modifier.fillMaxWidth()) {
                                    Text("Source: ${state.audioSource}", style = MaterialTheme.typography.labelMedium)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(showSourceMenu, { showSourceMenu = false }) {
                                    listOf("Default", "MIC", "VOICE_CALL", "VOICE_COMMUNICATION", "VOICE_RECOGNITION").forEach { src ->
                                        DropdownMenuItem(text = { Text(src) }, onClick = { viewModel.setAudioSource(src); showSourceMenu = false })
                                    }
                                }
                            }
                        }
                        // Shizuku method info
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Uses Shizuku + scrcpy audio capture for rootless recording on Android 10+",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            // Stats
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard2("Total", "${state.recordings.size}", Modifier.weight(1f))
                    StatCard2("Starred", "${state.recordings.count { it.isStarred }}", Modifier.weight(1f))
                    StatCard2("Size", formatSize(state.totalSizeBytes), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }

            // Recordings list
            if (state.isLoading) {
                item { LoadingScreen("Loading recordings…") }
            } else if (state.recordings.isEmpty()) {
                item { EmptyState(Icons.Default.Call, "No Recordings", "Enable call recording and make a call to see recordings here.") }
            } else {
                items(state.recordings, key = { it.id }) { rec ->
                    RecordingItem(
                        recording = rec,
                        onPlay = { viewModel.playRecording(rec) },
                        onStar = { viewModel.toggleStar(rec) },
                        onDelete = { viewModel.deleteRecording(rec) },
                        onShare = { viewModel.shareRecording(rec) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RecordingItem(
    recording: CallRecordingEntity,
    onPlay: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(recording.contactName.ifBlank { recording.phoneNumber.ifBlank { "Unknown" } }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatDuration(recording.durationSeconds), style = MaterialTheme.typography.bodySmall)
                    Text("·", style = MaterialTheme.typography.bodySmall)
                    Text(formatSize(recording.fileSizeBytes), style = MaterialTheme.typography.bodySmall)
                    Text("·", style = MaterialTheme.typography.bodySmall)
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), color = when(recording.callType) {
                        "INCOMING" -> AccentGreen.copy(0.2f)
                        "OUTGOING" -> AccentCyan.copy(0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }) {
                        Text(recording.callType, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Text(DateUtils.getRelativeTimeSpanString(recording.recordedAt).toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        recording.contactName.firstOrNull()?.uppercase() ?: recording.phoneNumber.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.PlayArrow, "Play", Modifier.size(20.dp)) }
                IconButton(onClick = onStar, modifier = Modifier.size(36.dp)) {
                    Icon(if (recording.isStarred) Icons.Default.Star else Icons.Default.StarBorder, "Star", Modifier.size(20.dp), tint = if (recording.isStarred) AccentYellow else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, "More", Modifier.size(20.dp)) }
                    DropdownMenu(showMenu, { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMenu = false; onShare() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
        },
    )
}

@Composable
private fun StatCard2(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60; val s = seconds % 60
    return "${m}:${s.toString().padStart(2, '0')}"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    else -> "${"%.1f".format(bytes / 1_000_000f)} MB"
}
