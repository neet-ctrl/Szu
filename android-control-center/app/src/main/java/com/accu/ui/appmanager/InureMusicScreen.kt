package com.accu.ui.appmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlin.math.roundToInt

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val durationSecs: Int,
    val path: String,
    val albumColor: Color,
    val isFavorite: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureMusicScreen(onBack: () -> Unit = {}) {
    val tracks = remember {
        listOf(
            MusicTrack("1", "Midnight City", "M83", "Hurry Up, We're Dreaming", "4:03", 243, "/sdcard/Music/M83_MidnightCity.mp3", Color(0xFF1E3A5F)),
            MusicTrack("2", "Blinding Lights", "The Weeknd", "After Hours", "3:22", 202, "/sdcard/Music/TheWeeknd_BlindingLights.mp3", Color(0xFF8B0000)),
            MusicTrack("3", "Levitating", "Dua Lipa", "Future Nostalgia", "3:23", 203, "/sdcard/Music/DuaLipa_Levitating.mp3", Color(0xFF4B0082)),
            MusicTrack("4", "Stay", "The Kid LAROI", "F*CK LOVE", "2:21", 141, "/sdcard/Music/TKL_Stay.mp3", Color(0xFF2D5016)),
            MusicTrack("5", "As It Was", "Harry Styles", "Harry's House", "2:37", 157, "/sdcard/Music/HarryStyles_AsItWas.mp3", Color(0xFF1A3550)),
            MusicTrack("6", "Anti-Hero", "Taylor Swift", "Midnights", "3:21", 201, "/sdcard/Music/TaylorSwift_AntiHero.mp3", Color(0xFF5C2B8A)),
            MusicTrack("7", "Flowers", "Miley Cyrus", "Endless Summer Vacation", "3:21", 201, "/sdcard/Music/MileyCyrus_Flowers.mp3", Color(0xFF1B5E20)),
        )
    }

    var currentTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var isShuffled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) } // 0=off, 1=all, 2=one
    var volume by remember { mutableStateOf(0.8f) }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf("Title") }
    var starredIds by remember { mutableStateOf(setOf<String>()) }

    val currentSecs = currentTrack?.let { (it.durationSecs * progress).roundToInt() } ?: 0

    fun formatTime(secs: Int) = "${secs / 60}:${"%02d".format(secs % 60)}"

    val sortedTracks = when (sortMode) {
        "Artist" -> tracks.sortedBy { it.artist }
        "Album" -> tracks.sortedBy { it.album }
        "Duration" -> tracks.sortedBy { it.durationSecs }
        else -> tracks.sortedBy { it.title }
    }.filter { search.isBlank() || it.title.contains(search, ignoreCase = true) || it.artist.contains(search, ignoreCase = true) }

    Scaffold(
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search music…") }, singleLine = true) },
                    navigationIcon = { IconButton(onClick = { showSearch = false; search = "" }) { Icon(Icons.Default.Close, "Close") } },
                )
            } else {
                ACCTopBar(title = "Music Player", onBack = onBack, actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, null) }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            listOf("Title", "Artist", "Album", "Duration").forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, leadingIcon = { if (sortMode == m) Icon(Icons.Default.Check, null) }, onClick = { sortMode = m; showSortMenu = false })
                            }
                        }
                    }
                    IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, null) }
                })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Now Playing card
            if (currentTrack != null) {
                val track = currentTrack!!
                ElevatedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = track.albumColor.copy(alpha = 0.15f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(track.albumColor), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text(track.album, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            IconButton(onClick = { starredIds = if (track.id in starredIds) starredIds - track.id else starredIds + track.id }) {
                                Icon(if (track.id in starredIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (track.id in starredIds) MaterialTheme.colorScheme.error else LocalContentColor.current)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Progress slider
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(currentSecs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(35.dp))
                            Slider(value = progress, onValueChange = { progress = it }, modifier = Modifier.weight(1f))
                            Text(track.duration, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(35.dp))
                        }

                        // Controls
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isShuffled = !isShuffled }) {
                                Icon(Icons.Default.Shuffle, null, tint = if (isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                val idx = tracks.indexOf(currentTrack)
                                if (idx > 0) { currentTrack = tracks[idx - 1]; progress = 0f }
                            }) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(32.dp)) }

                            FloatingActionButton(onClick = { isPlaying = !isPlaying }, modifier = Modifier.size(56.dp)) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
                            }

                            IconButton(onClick = {
                                val idx = tracks.indexOf(currentTrack)
                                if (idx < tracks.size - 1) { currentTrack = tracks[idx + 1]; progress = 0f }
                            }) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(32.dp)) }

                            IconButton(onClick = { repeatMode = (repeatMode + 1) % 3 }) {
                                Icon(when (repeatMode) { 1 -> Icons.Default.Repeat; 2 -> Icons.Default.RepeatOne; else -> Icons.Default.RepeatOn }, null,
                                    tint = if (repeatMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Volume
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(value = volume, onValueChange = { volume = it }, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Tap any track to start playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search tracks…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            Text("${sortedTracks.size} tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(sortedTracks, key = { it.id }) { track ->
                    val isCurrentTrack = currentTrack?.id == track.id
                    ListItem(
                        headlineContent = { Text(track.title, fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal, color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                        supportingContent = { Text("${track.artist} · ${track.album}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(track.albumColor), contentAlignment = Alignment.Center) {
                                if (isCurrentTrack && isPlaying)
                                    Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                else
                                    Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(track.duration, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                var showTrackMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showTrackMenu = true }) { Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp)) }
                                    DropdownMenu(showTrackMenu, { showTrackMenu = false }) {
                                        DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { Icon(Icons.Default.QueueMusic, null) }, onClick = { showTrackMenu = false })
                                        DropdownMenuItem(text = { Text("Add to playlist…") }, leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) }, onClick = { showTrackMenu = false })
                                        DropdownMenuItem(text = { Text(if (track.isFavorite) "Remove favourite" else "Mark as favourite") }, leadingIcon = { Icon(Icons.Default.Favorite, null) }, onClick = { showTrackMenu = false })
                                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showTrackMenu = false })
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { currentTrack = track; isPlaying = true; progress = 0f }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
