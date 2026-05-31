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

data class DisabledApp(val name: String, val pkg: String, val disabledVia: String, val size: String)

@Composable
fun InureDisabledAppsScreen(onBack: () -> Unit = {}) {
    var apps by remember {
        mutableStateOf(listOf(
            DisabledApp("Google Play Movies & TV", "com.google.android.videos", "System Settings", "14 MB"),
            DisabledApp("Carrier Services", "com.google.android.ims", "Canta/Blocker", "8 MB"),
            DisabledApp("Device Health Services", "com.google.android.apps.turbo", "Canta/Blocker", "6 MB"),
            DisabledApp("Android Auto", "com.google.android.projection.gearhead", "System Settings", "45 MB"),
            DisabledApp("Face Unlock", "com.android.facelock", "ADB", "3 MB"),
            DisabledApp("Digital Wellbeing", "com.google.android.apps.wellbeing", "System Settings", "28 MB"),
        ))
    }
    var search by remember { mutableStateOf("") }
    var selectedPkg by remember { mutableStateOf<String?>(null) }

    val filtered = apps.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }

    Scaffold(
        topBar = { ACCTopBar(title = "Disabled Apps", onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("${apps.size} disabled apps. Re-enabling system apps may restore functionality. Use with caution.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = {
                            Column {
                                Text(app.pkg, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SuggestionChip(onClick = {}, label = { Text(app.disabledVia, fontSize = 10.sp) }, modifier = Modifier.height(20.dp))
                                    Text(app.size, fontSize = 11.sp)
                                }
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
                        trailingContent = {
                            TextButton(onClick = { apps = apps.filter { it.pkg != app.pkg } }) {
                                Text("Enable", fontSize = 12.sp)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
