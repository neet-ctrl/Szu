package com.accu.ui.customization

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class AppThemeEntry(
    val name: String,
    val pkg: String,
    val isThemed: Boolean,
    val seedColor: Color?,
    val useSystemColor: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppThemingScreen(onBack: () -> Unit = {}) {
    var apps by remember {
        mutableStateOf(listOf(
            AppThemeEntry("Chrome", "com.android.chrome", true, Color(0xFF1A73E8), false),
            AppThemeEntry("Gmail", "com.google.android.gm", true, Color(0xFFEA4335), false),
            AppThemeEntry("YouTube", "com.google.android.youtube", true, Color(0xFFFF0000), false),
            AppThemeEntry("WhatsApp", "com.whatsapp", false, null, true),
            AppThemeEntry("Spotify", "com.spotify.music", true, Color(0xFF1DB954), false),
            AppThemeEntry("Maps", "com.google.android.apps.maps", false, null, true),
            AppThemeEntry("Slack", "com.slack", true, Color(0xFF4A154B), false),
            AppThemeEntry("Twitter/X", "com.twitter.android", false, null, true),
            AppThemeEntry("Instagram", "com.instagram.android", true, Color(0xFFE1306C), false),
            AppThemeEntry("Telegram", "org.telegram.messenger", true, Color(0xFF2CA5E0), false),
        ))
    }
    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("All") }
    var showWarning by remember { mutableStateOf(true) }
    var editingApp by remember { mutableStateOf<AppThemeEntry?>(null) }
    var selectedColor by remember { mutableStateOf(Color(0xFF6750A4)) }
    var useSystem by remember { mutableStateOf(true) }

    val filtered = apps.filter { app ->
        (filterMode == "All" || (filterMode == "Themed") == app.isThemed) &&
        (search.isBlank() || app.name.contains(search, ignoreCase = true))
    }

    val themedCount = apps.count { it.isThemed }

    if (editingApp != null) {
        val app = editingApp!!
        AlertDialog(
            onDismissRequest = { editingApp = null },
            title = { Text("Theme — ${app.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Use system theme color"); Text("Use device wallpaper color", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(checked = useSystem, onCheckedChange = { useSystem = it })
                    }
                    if (!useSystem) {
                        Text("Custom seed color:", fontWeight = FontWeight.SemiBold)
                        LazyColumn(Modifier.height(160.dp)) {
                            items(listOf(
                                Color(0xFF6750A4), Color(0xFF1A73E8), Color(0xFFEA4335), Color(0xFF1DB954),
                                Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFF4CAF50),
                                Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF4A154B), Color(0xFFFF5722),
                            ).chunked(4)) { row ->
                                item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { color ->
                                            Box(
                                                Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .clickable { selectedColor = color }
                                                    .then(if (selectedColor == color) Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape) else Modifier),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (selectedColor == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    apps = apps.map { a ->
                        if (a.pkg == app.pkg) a.copy(isThemed = true, seedColor = if (useSystem) null else selectedColor, useSystemColor = useSystem)
                        else a
                    }
                    editingApp = null
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = {
                    apps = apps.map { a -> if (a.pkg == app.pkg) a.copy(isThemed = false, seedColor = null) else a }
                    editingApp = null
                }) { Text("Clear theme") }
            }
        )
    }

    Scaffold(topBar = { ACCTopBar(title = "Per-App Theming", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Warning banner
            if (showWarning) {
                ElevatedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Root or Shizuku required. Some apps may not support per-app theming overlays.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showWarning = false }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            // Search
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            // Filters + stats
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Themed", "Default").forEach { f ->
                        FilterChip(selected = filterMode == f, onClick = { filterMode = f }, label = { Text(f, fontSize = 12.sp) })
                    }
                }
                Text("$themedCount themed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = {
                            if (app.isThemed) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (app.seedColor != null) {
                                        Box(Modifier.size(12.dp).clip(CircleShape).background(app.seedColor))
                                    }
                                    Text(if (app.useSystemColor) "System color" else "Custom seed color", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Text("Default theme (not themed)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Android, null, tint = if (app.isThemed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (app.isThemed && app.seedColor != null) {
                                    Box(Modifier.size(20.dp).clip(CircleShape).background(app.seedColor))
                                    Spacer(Modifier.width(4.dp))
                                }
                                IconButton(onClick = {
                                    editingApp = app
                                    useSystem = app.useSystemColor
                                    selectedColor = app.seedColor ?: Color(0xFF6750A4)
                                }) { Icon(Icons.Default.Edit, "Edit theme") }
                            }
                        },
                        modifier = Modifier.clickable {
                            editingApp = app
                            useSystem = app.useSystemColor
                            selectedColor = app.seedColor ?: Color(0xFF6750A4)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
