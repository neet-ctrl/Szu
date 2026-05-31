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

data class BootApp(val name: String, val pkg: String, val receiver: String, var isEnabled: Boolean, val isSystem: Boolean = false)

@Composable
fun InureBootManagerScreen(onBack: () -> Unit = {}) {
    var apps by remember {
        mutableStateOf(listOf(
            BootApp("WhatsApp", "com.whatsapp", "com.whatsapp.receiver.StartupReceiver", true),
            BootApp("Spotify", "com.spotify.music", "com.spotify.receiver.BootReceiver", true),
            BootApp("Google Play Services", "com.google.android.gms", "com.google.android.gms.BootCompleteReceiver", true, true),
            BootApp("Tasker", "net.dinglisch.android.taskerm", "net.dinglisch.android.taskerm.TaskerBootReceiver", true),
            BootApp("Syncthing", "com.nutomic.syncthingandroid", "com.nutomic.syncthingandroid.receiver.BootReceiver", false),
            BootApp("Automate", "com.llamalab.automate", "com.llamalab.automate.BootReceiver", false),
            BootApp("Shizuku", "moe.shizuku.privileged.api", "moe.shizuku.manager.receiver.BootCompleteReceiver", true, true),
            BootApp("ACCU Service", "com.accu.controlcenter", "com.accu.receivers.BootReceiver", true),
        ))
    }
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }

    val filtered = apps.filter {
        (showSystem || !it.isSystem) && (search.isBlank() || it.name.contains(search, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            ACCTopBar(title = "Boot Manager", onBack = onBack, actions = {
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, null) }
                IconButton(onClick = { showSystem = !showSystem }) { Icon(if (showSystem) Icons.Default.Android else Icons.Default.PhoneAndroid, null) }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showInfo) {
                AlertDialog(
                    onDismissRequest = { showInfo = false },
                    title = { Text("Boot Manager") },
                    text = { Text("Controls which apps receive the BOOT_COMPLETED broadcast. Disabling removes the app's ability to auto-start on device boot. Requires root or Shizuku. May affect notifications for disabled apps.") },
                    confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } }
                )
            }

            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("Controls BOOT_COMPLETED receivers. Disabling prevents auto-start on reboot.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            val enabledCount = apps.count { it.isEnabled }
            Text("${apps.size} receivers · $enabledCount auto-start · ${apps.size - enabledCount} disabled", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.receiver, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) },
                        leadingContent = {
                            Icon(if (app.isSystem) Icons.Default.Android else Icons.Default.Apps, null, tint = if (app.isSystem) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(checked = app.isEnabled, onCheckedChange = { en ->
                                apps = apps.map { if (it.pkg == app.pkg) it.copy(isEnabled = en) else it }
                            })
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
