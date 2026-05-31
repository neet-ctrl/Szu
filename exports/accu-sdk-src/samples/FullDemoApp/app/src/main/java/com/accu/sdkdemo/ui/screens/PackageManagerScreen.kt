package com.accu.sdkdemo.ui.screens

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
import com.accu.sdkdemo.data.AppInfo
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun PackageManagerScreen(vm: MainViewModel) {
    val apps    by vm.apps.collectAsState()
    var search  by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<AppInfo, String>?>(null) }

    val filtered = remember(apps, search) {
        if (search.isBlank()) apps else apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
    }

    // Confirmation dialog
    confirm?.let { (app, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title   = { Text(action) },
            text    = { Text("Package: ${app.packageName}\n\nThis action cannot be easily undone.") },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        "Enable"    -> vm.enablePackage(app.packageName)
                        "Disable"   -> vm.disablePackage(app.packageName)
                        "Force Stop"-> vm.forceStop(app.packageName)
                        "Clear Data"-> vm.clearData(app.packageName)
                        "Hide"      -> vm.hidePackage(app.packageName)
                        "Unhide"    -> vm.unhidePackage(app.packageName)
                        "Suspend"   -> vm.suspendPackage(app.packageName)
                        "Unsuspend" -> vm.unsuspendPackage(app.packageName)
                    }
                    confirm = null
                }) { Text("Confirm", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Search + Refresh
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    label = { Text("Search apps…") }, modifier = Modifier.weight(1f), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } },
                )
                IconButton(onClick = { vm.loadApps() }) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        }
        Text("${filtered.size} apps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                AppCard(app) { action -> confirm = app to action }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppInfo, onAction: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Android, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text("v${app.versionName} (${app.versionCode})", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Actions (require PACKAGE_MANAGE scope):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                val actions = listOf("Enable", "Disable", "Force Stop", "Clear Data", "Hide", "Unhide", "Suspend", "Unsuspend")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    actions.forEach { action ->
                        OutlinedButton(onClick = { onAction(action) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(action, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
