package com.accu.ui.appmanager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BootReceiver(
    val appName: String,
    val packageName: String,
    val receiverClass: String,
    val isEnabled: Boolean,
    val isSystem: Boolean = false,
    val disabledByAcf: Boolean = false,
    val action: String = "BOOT_COMPLETED",
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InureBootManagerScreen(onBack: () -> Unit = {}) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<BootReceiver>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var filterEnabled by remember { mutableStateOf<Boolean?>(null) }
    var selectedPkgs by remember { mutableStateOf(setOf<String>()) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    var togglingPkg by remember { mutableStateOf("") }

    val isSelecting = selectedPkgs.isNotEmpty()

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Query system packages on TARGET device via ADB
                val systemPkgsRaw = connectionManager.exec("pm list packages -s 2>/dev/null").output
                val systemPkgs = systemPkgsRaw.lines().filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }.toSet()

                // Query receivers for BOOT_COMPLETED on TARGET device
                val raw = connectionManager.exec("pm query-receivers --action android.intent.action.BOOT_COMPLETED -f 2>/dev/null").output

                val result = mutableListOf<BootReceiver>()
                val seenKeys = mutableSetOf<String>()

                for (line in raw.lines()) {
                    val trimmed = line.trim()
                    val match = Regex("([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+)/([a-zA-Z][a-zA-Z0-9_$.]*(?:\\.[a-zA-Z0-9_$.]+)*)").find(trimmed)
                    if (match != null) {
                        val pkg = match.groupValues[1]
                        var cls = match.groupValues[2]
                        if (cls.startsWith(".")) cls = pkg + cls
                        val key = "$pkg/$cls"
                        if (seenKeys.add(key) && pkg.isNotEmpty() && cls.isNotEmpty()) {
                            // Derive app name from package name — no local PM access needed
                            val appName = pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                            val isSystem = pkg in systemPkgs

                            val dumpLine = try {
                                connectionManager.exec("pm dump $pkg 2>/dev/null | grep -A1 '${cls.substringAfterLast('.')}'").output
                            } catch (_: Exception) { "" }
                            val isEnabled = !dumpLine.contains("enabled=false", ignoreCase = true)

                            result += BootReceiver(
                                appName       = appName,
                                packageName   = pkg,
                                receiverClass = cls,
                                isEnabled     = isEnabled,
                                isSystem      = isSystem,
                                disabledByAcf = false,
                            )
                        }
                    }
                }

                apps = result.sortedWith(compareBy({ it.appName }, { it.receiverClass }))
            } catch (e: Exception) {
                error = e.message ?: "Failed to load boot receivers"
            }
            loading = false
        }
    }

    fun toggleReceiver(app: BootReceiver, enable: Boolean) {
        togglingPkg = app.packageName + "/" + app.receiverClass
        scope.launch(Dispatchers.IO) {
            val cmd = if (enable) {
                "pm enable ${app.packageName}/${app.receiverClass} 2>&1"
            } else {
                "pm disable-user --user 0 ${app.packageName}/${app.receiverClass} 2>&1"
            }
            val result = try { connectionManager.exec(cmd).output } catch (e: Exception) { e.message ?: "Error" }
            withContext(Dispatchers.Main) {
                togglingPkg = ""
                val success = result.contains("enabled", ignoreCase = true) || result.contains("disabled", ignoreCase = true) || result.isEmpty()
                if (success) {
                    apps = apps.map {
                        if (it.packageName == app.packageName && it.receiverClass == app.receiverClass)
                            it.copy(isEnabled = enable, disabledByAcf = !enable)
                        else it
                    }
                    snackbar = "${app.appName} boot receiver ${if (enable) "enabled" else "disabled"}"
                } else {
                    snackbar = "Failed: $result"
                }
            }
        }
    }

    val filtered = apps.filter { app ->
        (showSystem || !app.isSystem) &&
        (filterEnabled == null || app.isEnabled == filterEnabled) &&
        (search.isBlank() || app.appName.contains(search, ignoreCase = true) || app.packageName.contains(search, ignoreCase = true))
    }.sortedWith(compareByDescending<BootReceiver> { !it.isEnabled }.thenBy { it.appName })

    val enabledCount = apps.count { it.isEnabled }
    val acfDisabledCount = apps.count { it.disabledByAcf }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = if (isSelecting) "${selectedPkgs.size} selected" else "Boot Manager",
                onBack = {
                    if (isSelecting) selectedPkgs = emptySet()
                    else onBack()
                },
                actions = {
                    if (isSelecting) {
                        IconButton(onClick = {
                            apps.filter { it.packageName in selectedPkgs }.forEach { toggleReceiver(it, true) }
                            snackbar = "Enabling ${selectedPkgs.size} receivers…"
                            selectedPkgs = emptySet()
                        }) { Icon(Icons.Default.PlayArrow, "Enable selected") }
                        IconButton(onClick = {
                            apps.filter { it.packageName in selectedPkgs }.forEach { toggleReceiver(it, false) }
                            snackbar = "Disabling ${selectedPkgs.size} receivers…"
                            selectedPkgs = emptySet()
                        }) { Icon(Icons.Default.Stop, "Disable selected") }
                        IconButton(onClick = { selectedPkgs = emptySet() }) { Icon(Icons.Default.Close, "Clear selection") }
                    } else {
                        IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, null) }
                        IconButton(onClick = { showSystem = !showSystem }) {
                            Icon(
                                if (showSystem) Icons.Default.Android else Icons.Default.PhoneAndroid,
                                null,
                                tint = if (!showSystem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
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
                        Text("Querying BOOT_COMPLETED receivers…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }
            if (error.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Column
            }

            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    BootStatItem("${apps.size}", "Total")
                    BootStatItem("$enabledCount", "Auto-start")
                    BootStatItem("${apps.size - enabledCount}", "Disabled")
                    BootStatItem("$acfDisabledCount", "By ACCU")
                }
            }

            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search receivers…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, null) } },
                singleLine = true,
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterEnabled == null, onClick = { filterEnabled = null }, label = { Text("All") })
                FilterChip(selected = filterEnabled == true, onClick = { filterEnabled = if (filterEnabled == true) null else true }, label = { Text("Enabled ($enabledCount)") })
                FilterChip(selected = filterEnabled == false, onClick = { filterEnabled = if (filterEnabled == false) null else false }, label = { Text("Disabled (${apps.size - enabledCount})") })
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No receivers match filter", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(filtered, key = { idx, it -> "${idx}_${it.packageName}/${it.receiverClass}" }) { _, app ->
                        val isSelected = app.packageName in selectedPkgs
                        val isToggling = togglingPkg == "${app.packageName}/${app.receiverClass}"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelecting) selectedPkgs = if (isSelected) selectedPkgs - app.packageName else selectedPkgs + app.packageName
                                    },
                                    onLongClick = { selectedPkgs = selectedPkgs + app.packageName },
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSelected   -> MaterialTheme.colorScheme.primaryContainer
                                    !app.isEnabled -> MaterialTheme.colorScheme.surfaceVariant
                                    else         -> MaterialTheme.colorScheme.surfaceContainer
                                }
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isSelecting) {
                                    Checkbox(checked = isSelected, onCheckedChange = { ch -> selectedPkgs = if (ch) selectedPkgs + app.packageName else selectedPkgs - app.packageName })
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(
                                    if (app.isSystem) Icons.Default.Android else Icons.Default.Apps,
                                    null,
                                    tint = when {
                                        !app.isEnabled -> MaterialTheme.colorScheme.outline
                                        app.isSystem   -> MaterialTheme.colorScheme.secondary
                                        else           -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (app.disabledByAcf) {
                                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                                Text("ACCU", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    Text(
                                        app.receiverClass.substringAfterLast('.'),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isToggling) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Switch(
                                        checked = app.isEnabled,
                                        onCheckedChange = { en -> toggleReceiver(app, en) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { Icon(Icons.Default.PowerSettingsNew, null) },
            title = { Text("Boot Manager") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Controls which apps receive the BOOT_COMPLETED broadcast. Disabling removes the app's ability to auto-start on device boot.", style = MaterialTheme.typography.bodySmall)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("How it works:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("• Queries pm query-receivers via ACCU connection\n• Uses pm disable-user / pm enable to toggle receivers\n• Long-press any app to enter batch selection mode\n• 'By ACCU' badge = disabled via ACCU previously", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showInfo = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun BootStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
    }
}
