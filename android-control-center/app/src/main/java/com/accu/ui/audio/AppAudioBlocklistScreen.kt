package com.accu.ui.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class BlocklistApp(
    val packageName: String,
    val appName: String,
    val blocked: Boolean = false,
)

val SAMPLE_BLOCKLIST_APPS = listOf(
    BlocklistApp("com.spotify.music", "Spotify"),
    BlocklistApp("com.google.android.youtube", "YouTube"),
    BlocklistApp("com.netflix.mediaclient", "Netflix"),
    BlocklistApp("com.amazon.mp3", "Amazon Music"),
    BlocklistApp("com.pandora.android", "Pandora"),
    BlocklistApp("com.soundcloud.android", "SoundCloud"),
    BlocklistApp("com.deezer.android", "Deezer"),
    BlocklistApp("com.tidal.android", "TIDAL"),
    BlocklistApp("com.apple.android.music", "Apple Music"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAudioBlocklistScreen(onBack: () -> Unit) {
    val apps = remember { mutableStateListOf(*SAMPLE_BLOCKLIST_APPS.toTypedArray()) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filtered = apps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    val blockedCount = apps.count { it.blocked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio App Blocklist") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeOff, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DSP Bypass List", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Apps in this list bypass JamesDSP processing", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("$blockedCount blocked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val idx = apps.indexOfFirst { it.packageName == app.packageName }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (app.blocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data("android.resource://${app.packageName}/mipmap/ic_launcher").crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                error = null,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (app.blocked) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                                    Text("Bypassed", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            Switch(
                                checked = app.blocked,
                                onCheckedChange = { apps[idx] = app.copy(blocked = it) },
                            )
                        }
                    }
                }
            }
        }
    }
}
