package com.accu.ui.storage

import androidx.compose.animation.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AppCacheInfo(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    val dataSize: Long,
    val selected: Boolean = false,
)

fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}

val SAMPLE_CACHE_APPS = listOf(
    AppCacheInfo("com.google.android.youtube", "YouTube", 487 * 1024 * 1024L, 120 * 1024 * 1024L),
    AppCacheInfo("com.instagram.android", "Instagram", 312 * 1024 * 1024L, 89 * 1024 * 1024L),
    AppCacheInfo("com.twitter.android", "X (Twitter)", 198 * 1024 * 1024L, 45 * 1024 * 1024L),
    AppCacheInfo("com.facebook.katana", "Facebook", 567 * 1024 * 1024L, 234 * 1024 * 1024L),
    AppCacheInfo("com.spotify.music", "Spotify", 234 * 1024 * 1024L, 1024 * 1024 * 1024L),
    AppCacheInfo("com.netflix.mediaclient", "Netflix", 892 * 1024 * 1024L, 3L * 1024 * 1024 * 1024),
    AppCacheInfo("com.whatsapp", "WhatsApp", 156 * 1024 * 1024L, 67 * 1024 * 1024L),
    AppCacheInfo("com.snapchat.android", "Snapchat", 423 * 1024 * 1024L, 78 * 1024 * 1024L),
    AppCacheInfo("com.tiktok.musically", "TikTok", 678 * 1024 * 1024L, 123 * 1024 * 1024L),
    AppCacheInfo("com.google.android.gms", "Google Play Services", 89 * 1024 * 1024L, 34 * 1024 * 1024L),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCleanerScreen(onBack: () -> Unit) {
    val apps = remember { mutableStateListOf(*SAMPLE_CACHE_APPS.toTypedArray()) }
    var isScanning by remember { mutableStateOf(false) }
    var isCleaning by remember { mutableStateOf(false) }
    var cleanedBytes by remember { mutableStateOf(0L) }
    var sortBy by remember { mutableStateOf("Cache Size") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val selectedApps = apps.filter { it.selected }
    val totalCacheSelected = selectedApps.sumOf { it.cacheSize }
    val sortedApps = when (sortBy) {
        "Cache Size" -> apps.sortedByDescending { it.cacheSize }
        "App Name" -> apps.sortedBy { it.appName }
        "Data Size" -> apps.sortedByDescending { it.dataSize }
        else -> apps.toList()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("App Cleaner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    var showSort by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSort = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(showSort, { showSort = false }) {
                            listOf("Cache Size", "App Name", "Data Size").forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = { sortBy = s; showSort = false },
                                    leadingIcon = { if (sortBy == s) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            isScanning = true
                            delay(1500)
                            isScanning = false
                            snackbar.showSnackbar("Scan complete — ${apps.size} apps analyzed")
                        }
                    }) { Icon(Icons.Default.Refresh, "Rescan") }
                }
            )
        },
        bottomBar = {
            if (selectedApps.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${selectedApps.size} apps selected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${formatSize(totalCacheSelected)} to free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isCleaning = true
                                    delay(2000)
                                    cleanedBytes += totalCacheSelected
                                    val selected = apps.filter { it.selected }
                                    selected.forEach { app ->
                                        val idx = apps.indexOfFirst { it.packageName == app.packageName }
                                        if (idx != -1) apps[idx] = app.copy(cacheSize = 0L, selected = false)
                                    }
                                    isCleaning = false
                                    snackbar.showSnackbar("Cleaned ${formatSize(totalCacheSelected)} from ${selected.size} apps")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !isCleaning,
                        ) {
                            if (isCleaning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Clear Cache")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (cleanedBytes > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("${formatSize(cleanedBytes)} freed this session", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${apps.size} apps • ${formatSize(apps.sumOf { it.cacheSize })} total cache", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = {
                    val allSelected = apps.all { it.selected }
                    apps.replaceAll { it.copy(selected = !allSelected) }
                }) { Text(if (apps.all { it.selected }) "Deselect All" else "Select All") }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sortedApps, key = { it.packageName }) { app ->
                    val idx = apps.indexOfFirst { it.packageName == app.packageName }
                    AppCacheCard(
                        app = app,
                        context = context,
                        onToggle = { if (idx != -1) apps[idx] = app.copy(selected = !app.selected) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCacheCard(app: AppCacheInfo, context: android.content.Context, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = app.selected, onCheckedChange = { onToggle() })
            AsyncImage(
                model = ImageRequest.Builder(context).data("android.resource://${app.packageName}/mipmap/ic_launcher").build(),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatSize(app.cacheSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (app.cacheSize > 100 * 1024 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text("cache", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
