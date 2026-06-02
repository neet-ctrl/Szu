package com.accu.ui.appmanager

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DisabledApp(
    val name: String,
    val pkg: String,
    val disabledVia: DisabledVia,
    val sizeBytes: Long = 0L,
    val isSystem: Boolean = false,
    val disabledTimestamp: Long = System.currentTimeMillis(),
)

enum class DisabledVia(val label: String) {
    SYSTEM_SETTINGS("System Settings"),
    CANTA_BLOCKER("ACCU/Blocker"),
    ADB("ADB"),
    ROOT("Root"),
    UNKNOWN("Unknown"),
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000     -> "${bytes / 1_000} KB"
    else               -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureDisabledAppsScreen(onBack: () -> Unit = {}) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<DisabledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var filterVia by remember { mutableStateOf<DisabledVia?>(null) }
    var sortBy by remember { mutableStateOf("name") }
    var showSortMenu by remember { mutableStateOf(false) }
    var confirmEnableApp by remember { mutableStateOf<DisabledApp?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    var enablingPkg by remember { mutableStateOf("") }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    fun reload() { loading = true; error = ""; apps = emptyList() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                // Get disabled packages from local PackageManager (covers the device ACCU is on)
                val disabledLocal = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                    .filter { pi ->
                        pi.applicationInfo?.let { ai ->
                            ai.enabled == false ||
                            (ai.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) ||
                            (ai.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
                        } ?: false
                    }

                // Also query via pm list packages -d on the target device
                val adbRaw = try {
                    connectionManager.exec("pm list packages -d 2>/dev/null").output
                } catch (_: Exception) { "" }
                val adbDisabled = adbRaw.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                val result = mutableListOf<DisabledApp>()

                // From local PackageManager
                disabledLocal.forEach { pi ->
                    val ai = pi.applicationInfo ?: return@forEach
                    val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val sizeBytes = try { java.io.File(ai.sourceDir ?: "").length() } catch (_: Exception) { 0L }
                    val appName = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pi.packageName }
                    val via = when (ai.enabledSetting) {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> DisabledVia.SYSTEM_SETTINGS
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> DisabledVia.ADB
                        else -> DisabledVia.UNKNOWN
                    }
                    result += DisabledApp(appName, pi.packageName, via, sizeBytes, isSystem)
                }

                // From ADB pm list packages -d — add those not already found locally
                val localPkgs = result.map { it.pkg }.toSet()
                adbDisabled.filter { it !in localPkgs }.forEach { pkg ->
                    val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                    val isSystem = try { (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0 } catch (_: Exception) { false }
                    result += DisabledApp(appName, pkg, DisabledVia.ADB, 0L, isSystem)
                }

                apps = result
            } catch (e: Exception) {
                error = e.message ?: "Failed to load disabled apps"
            }
            loading = false
        }
    }

    val filtered = apps.filter { app ->
        (filterVia == null || app.disabledVia == filterVia) &&
        (search.isBlank() || app.name.contains(search, ignoreCase = true) || app.pkg.contains(search, ignoreCase = true))
    }.let { list ->
        when (sortBy) {
            "size" -> list.sortedByDescending { it.sizeBytes }
            "via"  -> list.sortedBy { it.disabledVia.label }
            else   -> list.sortedBy { it.name }
        }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Disabled Apps${if (apps.isNotEmpty()) " (${apps.size})" else ""}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("By Name") }, leadingIcon = { if (sortBy == "name") Icon(Icons.Default.Check, null) }, onClick = { sortBy = "name"; showSortMenu = false })
                            DropdownMenuItem(text = { Text("By Size") }, leadingIcon = { if (sortBy == "size") Icon(Icons.Default.Check, null) }, onClick = { sortBy = "size"; showSortMenu = false })
                            DropdownMenuItem(text = { Text("By Source") }, leadingIcon = { if (sortBy == "via") Icon(Icons.Default.Check, null) }, onClick = { sortBy = "via"; showSortMenu = false })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Scanning disabled apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            if (error.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { error = ""; reload() }) { Text("Retry") }
                    }
                }
                return@Column
            }

            if (apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Text("No disabled apps found", style = MaterialTheme.typography.headlineSmall)
                        Text("All installed apps are currently enabled", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("${apps.size} disabled apps", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Re-enabling system apps may restore background services. Use with caution.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(0.8f))
                    }
                }
            }

            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search…") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, null) } },
                singleLine = true,
            )

            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = filterVia == null, onClick = { filterVia = null }, label = { Text("All (${apps.size})") })
                }
                items(DisabledVia.values().toList()) { via ->
                    val count = apps.count { it.disabledVia == via }
                    if (count > 0) {
                        FilterChip(
                            selected = filterVia == via,
                            onClick = { filterVia = if (filterVia == via) null else via },
                            label = { Text("${via.label} ($count)") },
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No disabled apps match filter", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.pkg }) { app ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = when (app.disabledVia) {
                                                DisabledVia.CANTA_BLOCKER   -> MaterialTheme.colorScheme.primaryContainer
                                                DisabledVia.ROOT             -> MaterialTheme.colorScheme.errorContainer
                                                DisabledVia.ADB              -> MaterialTheme.colorScheme.tertiaryContainer
                                                else                         -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        ) {
                                            Text(app.disabledVia.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                        if (app.sizeBytes > 0) Text(formatSize(app.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        if (app.isSystem) {
                                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                                Text("System", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                }
                                if (enablingPkg == app.pkg) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(
                                        onClick = { confirmEnableApp = app },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                    ) {
                                        Text("Enable", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val toEnable = confirmEnableApp
    if (toEnable != null) {
        AlertDialog(
            onDismissRequest = { confirmEnableApp = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Enable ${toEnable.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will re-enable the app using pm enable via ACCU.", style = MaterialTheme.typography.bodySmall)
                    if (toEnable.isSystem) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("System app — re-enabling may restore background services and data collection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pkg = toEnable.pkg
                    confirmEnableApp = null
                    enablingPkg = pkg
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        val result = try { connectionManager.exec("pm enable $pkg 2>&1").output } catch (e: Exception) { e.message ?: "Error" }
                        withContext(Dispatchers.Main) {
                            enablingPkg = ""
                            if (result.contains("enabled", ignoreCase = true) || result.contains("success", ignoreCase = true)) {
                                apps = apps.filter { it.pkg != pkg }
                                snackbar = "Enabled ${toEnable.name}"
                            } else {
                                snackbar = "Failed to enable: $result"
                            }
                        }
                    }
                }) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { confirmEnableApp = null }) { Text("Cancel") } },
        )
    }
}
