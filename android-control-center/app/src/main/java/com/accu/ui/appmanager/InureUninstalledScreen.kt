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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class UninstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureUninstalledScreen(
    onBack: () -> Unit = {},
) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager

    var apps by remember { mutableStateOf<List<UninstalledAppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemOnly by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<UninstalledAppInfo?>(null) }
    var restoring by remember { mutableStateOf("") }
    var restoreResult by remember { mutableStateOf("") }

    fun reload() {
        loading = true; error = ""; apps = emptyList()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // pm list packages -u lists all packages including uninstalled/removed for user
                val raw = connectionManager.exec("pm list packages -u 2>/dev/null").output
                // pm list packages (without -u) = only installed
                val installed = connectionManager.exec("pm list packages 2>/dev/null").output
                    .lines().map { it.removePrefix("package:").trim() }.toSet()

                val uninstalledPkgs = raw.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() && it !in installed }

                // Try to get labels via pm dump
                val result = mutableListOf<UninstalledAppInfo>()
                for (pkg in uninstalledPkgs.take(200)) {
                    val dumpInfo = connectionManager.exec("pm dump $pkg 2>/dev/null | grep -E 'userId=|flags='").output
                    val isSystem = dumpInfo.contains("SYSTEM") || dumpInfo.contains("FLAG_SYSTEM")
                    result += UninstalledAppInfo(pkg, pkg, isSystem)
                }
                apps = result.sortedBy { it.packageName }
            } catch (e: Exception) {
                error = e.message ?: "Failed"
            }
            loading = false
        }
    }

    val filtered = remember(apps, searchQuery, showSystemOnly) {
        apps.filter {
            (if (showSystemOnly) it.isSystem else true) &&
            (searchQuery.isBlank() || it.packageName.contains(searchQuery, true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Uninstalled Apps")
                    if (apps.isNotEmpty()) Text("${apps.size} packages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Info banner
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Shows packages removed for current user via `pm list packages -u`. Restore reinstalls from Play Store or sideload.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            OutlinedTextField(
                searchQuery, { searchQuery = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search packages…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(showSystemOnly, { showSystemOnly = !showSystemOnly }, { Text("System Only") })
            }

            if (restoreResult.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(restoreResult, modifier = Modifier.weight(1f))
                        IconButton({ restoreResult = "" }) { Icon(Icons.Default.Close, null) }
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (error.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Column
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("No uninstalled packages found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (app.isSystem) Text("System package", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Re-enable for current user (restore package visibility)
                                OutlinedButton(
                                    onClick = {
                                        restoring = app.packageName
                                        restoreResult = ""
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    if (restoring == app.packageName) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restore", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Handle restore
    LaunchedEffect(restoring) {
        if (restoring.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val result = connectionManager.exec("pm install-existing $restoring 2>&1").output
                restoreResult = if (result.contains("Success", true) || result.contains("installed", true)) {
                    "$restoring restored successfully"
                } else {
                    "Restore output: $result"
                }
            } catch (e: Exception) {
                restoreResult = "Error: ${e.message}"
            }
            restoring = ""
        }
    }
}
