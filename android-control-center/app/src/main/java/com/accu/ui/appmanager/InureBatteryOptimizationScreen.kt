package com.accu.ui.appmanager

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatteryOptApp(
    val name: String,
    val pkg: String,
    val isOptimized: Boolean,
    val isSystem: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureBatteryOptimizationScreen(onBack: () -> Unit = {}) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<BatteryOptApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    var togglingPkg by remember { mutableStateOf("") }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Get whitelist from deviceidle — these packages are NOT optimized (exempt)
                val whitelistRaw = connectionManager.exec("dumpsys deviceidle whitelist 2>/dev/null").output
                val whitelistPkgs = whitelistRaw.lines()
                    .mapNotNull { line ->
                        val parts = line.trim().split(",")
                        parts.lastOrNull()?.trim()?.takeIf { it.contains('.') }
                    }.toSet()

                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

                val result = packages.map { pi ->
                    val ai = pi.applicationInfo ?: return@map null
                    val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val appName = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pi.packageName }
                    val isOptimized = pi.packageName !in whitelistPkgs
                    BatteryOptApp(appName, pi.packageName, isOptimized, isSystem)
                }.filterNotNull().sortedBy { it.name }

                apps = result
            } catch (e: Exception) {
                error = e.message ?: "Failed to load battery optimization info"
            }
            loading = false
        }
    }

    fun toggleOptimization(app: BatteryOptApp) {
        togglingPkg = app.pkg
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val cmd = if (app.isOptimized) {
                // Remove from optimization (add to whitelist)
                "dumpsys deviceidle whitelist +${app.pkg} 2>&1"
            } else {
                // Re-enable optimization (remove from whitelist)
                "dumpsys deviceidle whitelist -${app.pkg} 2>&1"
            }
            val result = try { connectionManager.exec(cmd).output } catch (e: Exception) { e.message ?: "Error" }
            withContext(Dispatchers.Main) {
                togglingPkg = ""
                if (result.contains("white", ignoreCase = true) || result.isEmpty() || result.contains("ok", ignoreCase = true)) {
                    apps = apps.map { if (it.pkg == app.pkg) it.copy(isOptimized = !it.isOptimized) else it }
                    snackbar = "${app.name}: ${if (app.isOptimized) "removed from optimization" else "battery optimized"}"
                } else {
                    snackbar = "Failed: $result"
                }
            }
        }
    }

    val filtered = apps.filter { app ->
        (showSystem || !app.isSystem) &&
        (filter == "All" || (filter == "Optimized") == app.isOptimized) &&
        (search.isBlank() || app.name.contains(search, ignoreCase = true) || app.pkg.contains(search, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            ACCTopBar(title = "Battery Optimization", onBack = onBack, actions = {
                IconButton(onClick = { showSystem = !showSystem }) {
                    Icon(
                        if (showSystem) Icons.Default.Android else Icons.Default.PhoneAndroid,
                        "Toggle system apps",
                        tint = if (showSystem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Battery Optimization", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Apps NOT optimized run background tasks freely. Disable optimization only for apps needing background activity (e.g. messaging).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading battery optimization state…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            OutlinedTextField(
                search, { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Optimized", "Not Optimized").forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f, fontSize = 12.sp) })
                }
            }

            val optimizedCount = apps.count { it.isOptimized }
            Text(
                "${apps.size} apps · $optimizedCount optimized · ${apps.size - optimizedCount} unrestricted",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )

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
                            if (togglingPkg == app.pkg) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Switch(
                                    checked = !app.isOptimized,
                                    onCheckedChange = { toggleOptimization(app) },
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
