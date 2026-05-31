package com.accu.ui.appmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class AppSummary(val name: String, val pkg: String, val usageMins: Int = 0, val size: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureHomeScreen(
    onBack: () -> Unit = {},
    onNavigateToBatteryOpt: () -> Unit = {},
    onNavigateToBootManager: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToMusic: () -> Unit = {},
    onNavigateToApks: () -> Unit = {},
    onNavigateToTrackers: () -> Unit = {},
    onNavigateToUsageStats: () -> Unit = {},
    onNavigateToDisabled: () -> Unit = {},
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val mostUsed = remember {
        listOf(
            AppSummary("YouTube", "com.google.android.youtube", 142),
            AppSummary("Chrome", "com.android.chrome", 87),
            AppSummary("Gmail", "com.google.android.gm", 43),
            AppSummary("Maps", "com.google.android.apps.maps", 31),
            AppSummary("WhatsApp", "com.whatsapp", 28),
        )
    }
    val recentlyInstalled = remember {
        listOf(
            AppSummary("Shazam", "com.shazam.android", size = "28 MB"),
            AppSummary("Spotify", "com.spotify.music", size = "72 MB"),
            AppSummary("Slack", "com.slack", size = "65 MB"),
        )
    }
    val recentlyUpdated = remember {
        listOf(
            AppSummary("Chrome", "com.android.chrome", size = "145 MB"),
            AppSummary("YouTube", "com.google.android.youtube", size = "102 MB"),
            AppSummary("Gmail", "com.google.android.gm", size = "38 MB"),
        )
    }
    val fossList = remember {
        listOf("VLC", "F-Droid", "NewPipe", "AntennaPod").map { AppSummary(it, "org.$it") }
    }
    val disabledApps = remember {
        listOf("Carrier Services", "Device Health Services", "Google One").map { AppSummary(it, "com.google.$it") }
    }
    val quickApps = remember {
        listOf("Settings", "Calculator", "Clock", "Camera").map { AppSummary(it, "com.android.$it") }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Inure Home",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Search, "Search") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Quick panels grid
            item {
                Spacer(Modifier.height(8.dp))
                Text("Manage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(
                        Triple("Battery Opt.", Icons.Default.BatteryChargingFull, onNavigateToBatteryOpt),
                        Triple("Boot Manager", Icons.Default.PowerSettingsNew, onNavigateToBootManager),
                        Triple("Usage Stats", Icons.Default.BarChart, onNavigateToUsageStats),
                        Triple("Trackers", Icons.Default.TrackChanges, onNavigateToTrackers),
                        Triple("Disabled Apps", Icons.Default.Block, onNavigateToDisabled),
                        Triple("APK Scanner", Icons.Default.FindInPage, onNavigateToApks),
                        Triple("Notes", Icons.Default.Note, onNavigateToNotes),
                        Triple("Music", Icons.Default.MusicNote, onNavigateToMusic),
                    )) { (label, icon, action) ->
                        ElevatedCard(Modifier.width(100.dp).clickable { action() }) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Quick Apps
            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader("Quick Apps") {}
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickApps) { app ->
                        ElevatedCard(Modifier.width(80.dp).clickable { onNavigateToAppDetail(app.pkg) }) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Android, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(4.dp))
                                Text(app.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Most Used
            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader("Most Used Today") { onNavigateToUsageStats() }
            }
            items(mostUsed) { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    supportingContent = { Text("${app.usageMins} min", fontSize = 12.sp) },
                    leadingContent = { Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { LinearProgressIndicator(progress = { app.usageMins / 150f }, modifier = Modifier.width(80.dp)) },
                    modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) }
                )
            }

            // Recently Installed
            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                SectionHeader("Recently Installed") {}
            }
            items(recentlyInstalled) { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    supportingContent = { Text(app.size) },
                    leadingContent = { Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.tertiary) },
                    modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) }
                )
            }

            // Recently Updated
            item {
                HorizontalDivider()
                SectionHeader("Recently Updated") {}
            }
            items(recentlyUpdated) { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    supportingContent = { Text(app.size) },
                    leadingContent = { Icon(Icons.Default.Update, null, tint = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) }
                )
            }

            // FOSS
            item {
                HorizontalDivider()
                SectionHeader("FOSS Apps") {}
            }
            items(fossList) { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    supportingContent = { Text("Open Source") },
                    leadingContent = { Icon(Icons.Default.Code, null, tint = Color(0xFF22C55E)) },
                    modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) }
                )
            }

            // Disabled
            item {
                HorizontalDivider()
                SectionHeader("Disabled Apps") { onNavigateToDisabled() }
            }
            items(disabledApps) { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    supportingContent = { Text("Disabled") },
                    leadingContent = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onNavigateToAppDetail(app.pkg) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll) { Text("See all", fontSize = 12.sp) }
    }
}
