package com.accu.ui.shizuku

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

data class ShizukuApp(
    val name: String,
    val pkg: String,
    val version: String,
    val permissionType: String, // "full" or "limited"
    var isGranted: Boolean,
    val lastUsed: String,
)

@Composable
fun ShizukuAppsScreen(onBack: () -> Unit = {}) {
    var apps by remember {
        mutableStateOf(listOf(
            ShizukuApp("Android Control Center", "com.accu.controlcenter", "1.0.0", "full", true, "Now"),
            ShizukuApp("aShell You", "in.hridayan.ashell", "1.3.0", "full", true, "5 min ago"),
            ShizukuApp("Canta", "io.github.samolego.canta", "1.4.1", "full", true, "1 hr ago"),
            ShizukuApp("Hail", "com.aistra.hail", "0.10.0", "full", true, "30 min ago"),
            ShizukuApp("Language Selector", "vegabobo.languageselector", "1.2.0", "limited", true, "Yesterday"),
            ShizukuApp("Better Internet Tiles", "be.casperverswijvelt.unifiedinternetqs", "2.3.0", "full", false, "3 days ago"),
            ShizukuApp("Blocker", "com.merxury.blocker", "2.0.0", "full", true, "2 hr ago"),
            ShizukuApp("InureApp", "app.simple.inure.github", "7.1.0", "full", false, "Never"),
        ))
    }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var showRevokeAll by remember { mutableStateOf(false) }

    val filtered = apps.filter { app ->
        (filter == "All" || (filter == "Granted") == app.isGranted) &&
        (search.isBlank() || app.name.contains(search, ignoreCase = true))
    }
    val grantedCount = apps.count { it.isGranted }

    if (showRevokeAll) {
        AlertDialog(
            onDismissRequest = { showRevokeAll = false },
            title = { Text("Revoke all permissions?") },
            text = { Text("All apps will lose Shizuku access. They will need to re-request permission.") },
            confirmButton = {
                TextButton(onClick = { apps = apps.map { it.copy(isGranted = false) }; showRevokeAll = false }) {
                    Text("Revoke All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showRevokeAll = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Authorized Apps",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showRevokeAll = true }) { Icon(Icons.Default.BlockFlipped, "Revoke all") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Shizuku status
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Shizuku is running", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("v13.5.4 · Started via ADB · UID: 2000", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick = {}) { Text("Restart") }
                }
            }

            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search authorized apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Granted", "Revoked").forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f, fontSize = 12.sp) })
                    }
                }
                Text("$grantedCount/${apps.size} granted", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = {
                            Column {
                                Text("v${app.version} · ${app.permissionType} API", fontSize = 11.sp)
                                Text("Last used: ${app.lastUsed}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            Icon(
                                if (app.isGranted) Icons.Default.Verified else Icons.Default.Block,
                                null,
                                tint = if (app.isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = app.isGranted,
                                onCheckedChange = { granted -> apps = apps.map { a -> if (a.pkg == app.pkg) a.copy(isGranted = granted) else a } }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
