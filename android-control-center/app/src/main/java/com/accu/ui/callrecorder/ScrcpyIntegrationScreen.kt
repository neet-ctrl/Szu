package com.accu.ui.callrecorder

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AudioCodec(val label: String, val extension: String, val description: String) {
    OPUS("Opus", ".ogg", "Best quality/size ratio — recommended"),
    AAC("AAC", ".m4a", "High compatibility with all players"),
    FLAC("FLAC", ".flac", "Lossless — largest files"),
    RAW("Raw PCM", ".wav", "Uncompressed — for maximum quality"),
}

enum class AudioSource(val label: String, val description: String) {
    SCRCPY_AUDIO("scrcpy Audio Capture", "Routes system audio via scrcpy — works without root"),
    MIC_ONLY("Microphone Only", "Records from microphone — no caller audio"),
    MERGED("Microphone + System", "Merges both streams — requires root or Shizuku"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrcpyIntegrationScreen(onBack: () -> Unit) {
    var selectedCodec by remember { mutableStateOf(AudioCodec.OPUS) }
    var selectedSource by remember { mutableStateOf(AudioSource.SCRCPY_AUDIO) }
    var bitrate by remember { mutableStateOf(128) }
    var sampleRate by remember { mutableStateOf(44100) }
    var serverExtracted by remember { mutableStateOf(false) }
    var serverVersion by remember { mutableStateOf("Not extracted") }
    var isExtracting by remember { mutableStateOf(false) }
    var autoStart by remember { mutableStateOf(true) }
    var recordIncoming by remember { mutableStateOf(true) }
    var recordOutgoing by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("scrcpy Integration") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (serverExtracted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (serverExtracted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            modifier = Modifier.size(28.dp),
                            tint = if (serverExtracted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("scrcpy Server", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Status: $serverVersion", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "scrcpy server is bundled with this app and needs to be extracted to device storage before first use. This enables rootless system audio capture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isExtracting = true
                                delay(2000)
                                serverExtracted = true
                                serverVersion = "v2.3.1 (bundled)"
                                isExtracting = false
                            }
                        },
                        enabled = !isExtracting && !serverExtracted,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (serverExtracted) "Server Ready" else "Extract scrcpy Server")
                    }
                }
            }

            SectionCard(title = "Audio Source") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioSource.entries.forEach { source ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedSource == source) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                            ),
                            onClick = { selectedSource = source },
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedSource == source, onClick = { selectedSource = source })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(source.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(source.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            SectionCard(title = "Audio Codec") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AudioCodec.entries.forEach { codec ->
                        ListItem(
                            headlineContent = { Text("${codec.label} (${codec.extension})") },
                            supportingContent = { Text(codec.description) },
                            trailingContent = { RadioButton(selected = selectedCodec == codec, onClick = { selectedCodec = codec }) },
                            modifier = Modifier.clickable(onClick = { selectedCodec = codec }),
                        )
                    }
                }
            }

            SectionCard(title = "Audio Quality") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Bitrate: ${bitrate}kbps", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = bitrate.toFloat(), onValueChange = { bitrate = it.toInt() }, valueRange = 64f..320f, steps = 7, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(44100, 48000, 96000).forEach { rate ->
                            FilterChip(
                                selected = sampleRate == rate,
                                onClick = { sampleRate = rate },
                                label = { Text("${rate / 1000}kHz") },
                            )
                        }
                    }
                }
            }

            SectionCard(title = "Recording Triggers") {
                Column {
                    ListItem(
                        headlineContent = { Text("Record Incoming Calls") },
                        trailingContent = { Switch(checked = recordIncoming, onCheckedChange = { recordIncoming = it }) }
                    )
                    ListItem(
                        headlineContent = { Text("Record Outgoing Calls") },
                        trailingContent = { Switch(checked = recordOutgoing, onCheckedChange = { recordOutgoing = it }) }
                    )
                    ListItem(
                        headlineContent = { Text("Auto-start on call") },
                        supportingContent = { Text("Begin recording immediately when call is answered") },
                        trailingContent = { Switch(checked = autoStart, onCheckedChange = { autoStart = it }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun Modifier.clickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.then(androidx.compose.foundation.Modifier.clickable(onClick = onClick))
)
