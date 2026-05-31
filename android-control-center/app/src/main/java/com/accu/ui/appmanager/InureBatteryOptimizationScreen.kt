package com.accu.ui.appmanager

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

data class BatteryOptApp(val name: String, val pkg: String, var isOptimized: Boolean, val isSystem: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureBatteryOptimizationScreen(onBack: () -> Unit = {}) {
    var apps by remember {
        mutableStateOf(listOf(
            BatteryOptApp("WhatsApp", "com.whatsapp", false),
            BatteryOptApp("Chrome", "com.android.chrome", true),
            BatteryOptApp("Gmail", "com.google.android.gm", false),
            BatteryOptApp("Spotify", "com.spotify.music", true),
            BatteryOptApp("Slack", "com.slack", false),
            BatteryOptApp("Maps", "com.google.android.apps.maps", true),
            BatteryOptApp("Carrier Services", "com.google.android.ims", true, true),
            BatteryOptApp("Device Health", "com.google.android.apps.turbo", true, true),
            BatteryOptApp("Phone", "com.android.dialer", false, true),
            BatteryOptApp("Messages", "com.google.android.apps.messaging", false),
            BatteryOptApp("YouTube", "com.google.android.youtube", true),
            BatteryOptApp("Camera", "com.android.camera2", true, true),
        ))
    }
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }

    val filtered = apps.filter { app ->
        (showSystem || !app.isSystem) &&
        (filter == "All" || (filter == "Optimized") == app.isOptimized) &&
        (search.isBlank() || app.name.contains(search, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            ACCTopBar(title = "Battery Optimization", onBack = onBack, actions = {
                IconButton(onClick = { showSystem = !showSystem }) { Icon(if (showSystem) Icons.Default.Android else Icons.Default.PhoneAndroid, "Toggle system apps") }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Info card
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Battery Optimization", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Optimized apps are restricted in background. Disable for apps needing background activity.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            // Search
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            // Filter
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Optimized", "Not Optimized").forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f, fontSize = 12.sp) })
                }
            }

            // Stats
            val optimizedCount = apps.count { it.isOptimized }
            Text("${apps.size} apps · $optimizedCount optimized · ${apps.size - optimizedCount} unrestricted",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = {
                            Text(
                                if (app.isOptimized) "Optimized — background restricted" else "Not optimized — unrestricted background",
                                fontSize = 12.sp,
                                color = if (app.isOptimized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (app.isSystem) Icons.Default.Android else Icons.Default.Apps, null,
                                tint = if (app.isSystem) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = !app.isOptimized,
                                onCheckedChange = { unrestricted ->
                                    apps = apps.map { if (it.pkg == app.pkg) it.copy(isOptimized = !unrestricted) else it }
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
