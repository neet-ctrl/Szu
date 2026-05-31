package com.accu.ui.customization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class DarQAppEntry(
    val name: String,
    val pkg: String,
    var mode: String, // "system", "force_dark", "force_light", "excluded"
    val isSystem: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarQAppPickerScreen(onBack: () -> Unit = {}) {
    var apps by remember {
        mutableStateOf(listOf(
            DarQAppEntry("Chrome", "com.android.chrome", "force_dark"),
            DarQAppEntry("Gmail", "com.google.android.gm", "system"),
            DarQAppEntry("YouTube", "com.google.android.youtube", "excluded"),
            DarQAppEntry("Google Maps", "com.google.android.apps.maps", "force_dark"),
            DarQAppEntry("WhatsApp", "com.whatsapp", "force_dark"),
            DarQAppEntry("Instagram", "com.instagram.android", "excluded"),
            DarQAppEntry("Twitter/X", "com.twitter.android", "system"),
            DarQAppEntry("Telegram", "org.telegram.messenger", "system"),
            DarQAppEntry("Spotify", "com.spotify.music", "force_dark"),
            DarQAppEntry("Slack", "com.slack", "system"),
            DarQAppEntry("Calculator", "com.android.calculator2", "force_light", true),
            DarQAppEntry("Settings", "com.android.settings", "force_dark", true),
        ))
    }
    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("All") }
    var showSystem by remember { mutableStateOf(false) }

    val filtered = apps.filter { app ->
        (showSystem || !app.isSystem) &&
        (filterMode == "All" || app.mode == filterMode) &&
        (search.isBlank() || app.name.contains(search, ignoreCase = true))
    }

    fun modeLabel(mode: String) = when (mode) {
        "force_dark" -> "Force Dark"
        "force_light" -> "Force Light"
        "excluded" -> "Excluded"
        else -> "System Default"
    }

    fun modeIcon(mode: String) = when (mode) {
        "force_dark" -> Icons.Default.DarkMode
        "force_light" -> Icons.Default.LightMode
        "excluded" -> Icons.Default.Block
        else -> Icons.Default.Contrast
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Per-App Dark Mode",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showSystem = !showSystem }) { Icon(if (showSystem) Icons.Default.Android else Icons.Default.PhoneAndroid, null) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("Override the dark/light mode for individual apps, regardless of system setting.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            ScrollableTabRow(
                selectedTabIndex = listOf("All", "force_dark", "force_light", "excluded", "system").indexOf(filterMode).coerceAtLeast(0),
                edgePadding = 16.dp,
            ) {
                listOf("All", "force_dark", "force_light", "excluded", "system").forEach { f ->
                    Tab(selected = filterMode == f, onClick = { filterMode = f }, text = { Text(modeLabel(f), fontSize = 11.sp) })
                }
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    var showMenu by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(modeLabel(app.mode), fontSize = 12.sp, color = when(app.mode) {
                            "force_dark" -> MaterialTheme.colorScheme.primary
                            "force_light" -> MaterialTheme.colorScheme.tertiary
                            "excluded" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }) },
                        leadingContent = { Icon(modeIcon(app.mode), null, tint = when(app.mode) {
                            "force_dark" -> MaterialTheme.colorScheme.primary
                            "force_light" -> MaterialTheme.colorScheme.tertiary
                            "excluded" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }) },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(showMenu, { showMenu = false }) {
                                    listOf("system", "force_dark", "force_light", "excluded").forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(modeLabel(mode)) },
                                            leadingIcon = {
                                                if (app.mode == mode) Icon(Icons.Default.Check, null)
                                                else Icon(modeIcon(mode), null)
                                            },
                                            onClick = { apps = apps.map { a -> if (a.pkg == app.pkg) a.copy(mode = mode) else a }; showMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
