package com.accu.ui.appmanager

import android.content.ContentUris
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val durationMs: Long,
    val uri: Uri,
    val albumId: Long,
)

private val ALBUM_COLORS = listOf(
    Color(0xFF1E3A5F), Color(0xFF8B0000), Color(0xFF4B0082), Color(0xFF2D5016),
    Color(0xFF1A3550), Color(0xFF5C2B8A), Color(0xFF1B5E20), Color(0xFF33691E),
    Color(0xFF880E4F), Color(0xFF006064),
)

private fun albumColor(albumId: Long): Color =
    ALBUM_COLORS[(albumId % ALBUM_COLORS.size).toInt().coerceAtLeast(0)]

private fun formatMs(ms: Long): String {
    val secs = (ms / 1000).toInt()
    return "${secs / 60}:${"%02d".format(secs % 60)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureMusicScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    var tracks      by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading     by remember { mutableStateOf(true) }
    var currentTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var isPlaying   by remember { mutableStateOf(false) }
    var progress    by remember { mutableStateOf(0f) }
    var isShuffled  by remember { mutableStateOf(false) }
    var repeatMode  by remember { mutableStateOf(0) }
    var volume      by remember { mutableStateOf(0.8f) }
    var search      by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortMode    by remember { mutableStateOf("Title") }
    var starredIds  by remember { mutableStateOf(setOf<Long>()) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID,
                )
                val cursor = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC",
                )
                val result = mutableListOf<MusicTrack>()
                cursor?.use { c ->
                    val idCol      = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durCol     = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    while (c.moveToNext()) {
                        val id        = c.getLong(idCol)
                        val durationMs = c.getLong(durCol)
                        result += MusicTrack(
                            id         = id,
                            title      = c.getString(titleCol) ?: "Unknown",
                            artist     = c.getString(artistCol) ?: "Unknown Artist",
                            album      = c.getString(albumCol) ?: "Unknown Album",
                            duration   = formatMs(durationMs),
                            durationMs = durationMs,
                            uri        = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            albumId    = c.getLong(albumIdCol),
                        )
                    }
                }
                tracks = result
            } catch (_: Exception) { }
            loading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release(); mediaPlayer = null }
    }

    fun playTrack(track: MusicTrack) {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, track.uri)
                prepare()
                setVolume(volume, volume)
                setOnCompletionListener {
                    isPlaying = false
                    progress = 0f
                    mediaPlayer = null
                }
                start()
            }
            mediaPlayer = mp
            isPlaying = true
            progress = 0f
        } catch (_: Exception) {}
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                val dur = currentTrack?.durationMs?.toFloat() ?: 1f
                if (dur > 0) progress = mp.currentPosition.toFloat() / dur
            }
            delay(500)
        }
    }

    val currentSecs = currentTrack?.let { ((it.durationMs / 1000) * progress).roundToInt() } ?: 0

    val sortedTracks = when (sortMode) {
        "Artist"   -> tracks.sortedBy { it.artist }
        "Album"    -> tracks.sortedBy { it.album }
        "Duration" -> tracks.sortedBy { it.durationMs }
        else       -> tracks.sortedBy { it.title }
    }.filter { search.isBlank() || it.title.contains(search, ignoreCase = true) || it.artist.contains(search, ignoreCase = true) }

    Scaffold(
        topBar = {
            ACCTopBar(title = "Music Player", onBack = {
                mediaPlayer?.release(); mediaPlayer = null; onBack()
            }, actions = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, null) }
                    DropdownMenu(showSortMenu, { showSortMenu = false }) {
                        listOf("Title", "Artist", "Album", "Duration").forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                leadingIcon = { if (sortMode == m) Icon(Icons.Default.Check, null) },
                                onClick = { sortMode = m; showSortMenu = false },
                            )
                        }
                    }
                }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.MusicOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No music found on device", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Add MP3/FLAC/M4A files to your device storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            val track = currentTrack
            if (track != null) {
                val ac = albumColor(track.albumId)
                ElevatedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = ac.copy(alpha = 0.15f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(ac), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text(track.album, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            IconButton(onClick = {
                                starredIds = if (track.id in starredIds) starredIds - track.id else starredIds + track.id
                            }) {
                                Icon(
                                    if (track.id in starredIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    null,
                                    tint = if (track.id in starredIds) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatMs(currentSecs * 1000L), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(35.dp))
                            Slider(
                                value = progress,
                                onValueChange = { p ->
                                    progress = p
                                    mediaPlayer?.seekTo((track.durationMs * p).toInt())
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Text(track.duration, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(35.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isShuffled = !isShuffled }) {
                                Icon(Icons.Default.Shuffle, null, tint = if (isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                val idx = sortedTracks.indexOfFirst { it.id == currentTrack?.id }
                                if (idx > 0) { val prev = sortedTracks[idx - 1]; currentTrack = prev; playTrack(prev) }
                            }) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(32.dp)) }
                            FloatingActionButton(onClick = {
                                val mp = mediaPlayer
                                if (mp != null) {
                                    if (isPlaying) { mp.pause(); isPlaying = false }
                                    else { mp.start(); isPlaying = true }
                                } else { playTrack(track) }
                            }, modifier = Modifier.size(56.dp)) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
                            }
                            IconButton(onClick = {
                                val idx = sortedTracks.indexOfFirst { it.id == currentTrack?.id }
                                if (idx < sortedTracks.size - 1) {
                                    val next = sortedTracks[idx + 1]; currentTrack = next; playTrack(next)
                                } else if (repeatMode == 1 && sortedTracks.isNotEmpty()) {
                                    val first = sortedTracks.first(); currentTrack = first; playTrack(first)
                                }
                            }) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(32.dp)) }
                            IconButton(onClick = { repeatMode = (repeatMode + 1) % 3 }) {
                                Icon(
                                    when (repeatMode) { 1 -> Icons.Default.Repeat; 2 -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat },
                                    null,
                                    tint = if (repeatMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(value = volume, onValueChange = { v -> volume = v; mediaPlayer?.setVolume(v, v) }, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("${tracks.size} tracks — tap any to play", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            OutlinedTextField(
                search, { search = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search tracks…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )
            Text("${sortedTracks.size} tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(sortedTracks, key = { it.id }) { t ->
                    val isCurrentTrack = currentTrack?.id == t.id
                    val ac = albumColor(t.albumId)
                    ListItem(
                        headlineContent = {
                            Text(
                                t.title,
                                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = { Text("${t.artist} · ${t.album}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(ac), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isCurrentTrack && isPlaying) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.duration, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (t.id in starredIds) {
                                    Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp).padding(start = 4.dp))
                                }
                            }
                        },
                        modifier = Modifier.clickable { currentTrack = t; playTrack(t) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
