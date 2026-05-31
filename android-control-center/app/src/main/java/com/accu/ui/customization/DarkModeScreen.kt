package com.accu.ui.customization

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.ui.components.ACCTopBar
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ──────────────────────────────────────────────
//  DarQ — Per-app Force Dark Mode
// ──────────────────────────────────────────────

enum class DarkScheduleMode { DISABLED, SUNRISE_SUNSET, CUSTOM_TIME }
enum class DarQAppFilter { ALL, USER, SYSTEM }

data class DarkModeState(
    val apps: List<DarkModeApp> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val appFilter: DarQAppFilter = DarQAppFilter.USER,
    val globalDarkForced: Boolean = false,
    val alwaysForceDark: Boolean = false,
    val sendAppCloses: Boolean = false,
    val scheduleMode: DarkScheduleMode = DarkScheduleMode.DISABLED,
    val scheduleStart: String = "20:00",
    val scheduleEnd: String = "07:00",
    val snackbarMessage: String? = null,
    // Xposed-specific
    val aggressiveForceDark: Boolean = false,
    val fixStatusBarInversion: Boolean = false,
    val oxygenOsForceDark: Boolean = false,
    // Developer options
    val showDebugInfo: Boolean = false,
    val useMonetColors: Boolean = false,
)
data class DarkModeApp(val packageName: String, val appName: String, val forceDark: Boolean = false, val isSystemApp: Boolean = false)

@HiltViewModel
class DarkModeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {
    private val _state = MutableStateFlow(DarkModeState())
    val state: StateFlow<DarkModeState> = _state.asStateFlow()

    init { loadApps() }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledPackages(0).mapNotNull { pkg ->
                val ai = pkg.applicationInfo ?: return@mapNotNull null
                DarkModeApp(pkg.packageName, pm.getApplicationLabel(ai).toString(), isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            }.sortedBy { it.appName }
            _state.update { it.copy(apps = apps, isLoading = false) }
        }
    }

    fun toggleForceDark(packageName: String) {
        viewModelScope.launch {
            val app = _state.value.apps.firstOrNull { it.packageName == packageName } ?: return@launch
            val newState = !app.forceDark
            shizukuUtils.execShizuku("settings put global force_dark_mode_${packageName} ${if (newState) "1" else "0"}")
            _state.update { s -> s.copy(apps = s.apps.map { if (it.packageName == packageName) it.copy(forceDark = newState) else it }) }
        }
    }

    fun toggleGlobalDark() {
        viewModelScope.launch {
            val newState = !_state.value.globalDarkForced
            shizukuUtils.execShizuku("settings put secure ui_night_mode ${if (newState) "2" else "1"}")
            _state.update { it.copy(globalDarkForced = newState, snackbarMessage = if (newState) "System dark mode forced ON" else "System dark mode reset") }
        }
    }

    fun toggleAlwaysForceDark() {
        viewModelScope.launch {
            val v = !_state.value.alwaysForceDark
            shizukuUtils.execShizuku("settings put global hw_force_dark ${if (v) "1" else "0"}")
            _state.update { it.copy(alwaysForceDark = v, snackbarMessage = if (v) "Force dark enabled system-wide" else "Force dark disabled") }
        }
    }

    fun toggleSendAppCloses() { _state.update { it.copy(sendAppCloses = !it.sendAppCloses) } }
    fun setScheduleMode(mode: DarkScheduleMode) { _state.update { it.copy(scheduleMode = mode) } }
    fun setScheduleStart(t: String) { _state.update { it.copy(scheduleStart = t) } }
    fun setScheduleEnd(t: String) { _state.update { it.copy(scheduleEnd = t) } }
    fun setAppFilter(f: DarQAppFilter) { _state.update { it.copy(appFilter = f) } }
    fun enableAllVisible() {
        val visible = _state.value.apps.filter { if (_state.value.appFilter == DarQAppFilter.USER) !it.isSystemApp else if (_state.value.appFilter == DarQAppFilter.SYSTEM) it.isSystemApp else true }
        viewModelScope.launch { visible.forEach { if (!it.forceDark) toggleForceDark(it.packageName) } }
    }
    fun disableAllVisible() {
        val visible = _state.value.apps.filter { it.forceDark }
        viewModelScope.launch { visible.forEach { toggleForceDark(it.packageName) } }
    }
    fun exportSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val forcedApps = _state.value.apps.filter { it.forceDark }
                val pkgList = forcedApps.joinToString(",") { "\"${it.packageName}\"" }
                val json = """{"version":1,"forcedApps":[$pkgList],"globalDarkForced":${_state.value.globalDarkForced},"scheduleMode":"${_state.value.scheduleMode}","scheduleStart":"${_state.value.scheduleStart}","scheduleEnd":"${_state.value.scheduleEnd}"}"""
                val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                outDir.mkdirs()
                File(outDir, "darq_backup.json").writeText(json)
                _state.update { it.copy(snackbarMessage = "Exported ${forcedApps.size} apps → Downloads/darq_backup.json") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Export failed: ${e.message}") }
            }
        }
    }
    fun applyImportedJson(json: String) {
        viewModelScope.launch {
            try {
                val packageNames = Regex(""""([a-z][a-zA-Z0-9._]+)"""").findAll(json)
                    .map { it.groupValues[1] }
                    .filter { it.contains('.') }
                    .toSet()
                val updated = _state.value.apps.map { app ->
                    if (app.packageName in packageNames && !app.forceDark) {
                        shizukuUtils.execShizuku("settings put global force_dark_mode_${app.packageName} 1")
                        app.copy(forceDark = true)
                    } else app
                }
                _state.update { it.copy(apps = updated, snackbarMessage = "Imported ${packageNames.size} force-dark app(s)") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Import failed: ${e.message}") }
            }
        }
    }
    fun onSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
    fun toggleAggressiveForceDark() {
        viewModelScope.launch {
            val v = !_state.value.aggressiveForceDark
            shizukuUtils.execShizuku("settings put global aggressive_force_dark ${if (v) "1" else "0"}")
            _state.update { it.copy(aggressiveForceDark = v, snackbarMessage = if (v) "Aggressive force dark enabled" else "Aggressive force dark disabled") }
        }
    }
    fun toggleFixStatusBarInversion() { _state.update { it.copy(fixStatusBarInversion = !it.fixStatusBarInversion, snackbarMessage = "Requires Xposed module reload") } }
    fun toggleOxygenOsForceDark() {
        viewModelScope.launch {
            val v = !_state.value.oxygenOsForceDark
            shizukuUtils.execShizuku("settings put global op_force_dark ${if (v) "1" else "0"}")
            _state.update { it.copy(oxygenOsForceDark = v, snackbarMessage = if (v) "OxygenOS force dark on" else "OxygenOS force dark off") }
        }
    }
    fun toggleShowDebugInfo() { _state.update { it.copy(showDebugInfo = !it.showDebugInfo) } }
    fun toggleUseMonetColors() { _state.update { it.copy(useMonetColors = !it.useMonetColors) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkModeScreen(
    onBack: () -> Unit,
    onNavigateToSunriseSunset: () -> Unit = {},
    viewModel: DarkModeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showAdvancedInfo by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@let
                    viewModel.applyImportedJson(json)
                } catch (_: Exception) { /* malformed file */ }
            }
        }
    }

    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() } }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Per-App Dark Mode",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.exportSettings() }) { Icon(Icons.Default.IosShare, "Export") }
                    IconButton(onClick = { showAdvancedInfo = true }) { Icon(Icons.Default.Info, "Advanced") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Global controls card
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NightlightRound, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("DarQ — Force Dark Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.15f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("System Dark Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Force entire system to dark mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        }
                        Switch(checked = state.globalDarkForced, onCheckedChange = { viewModel.toggleGlobalDark() })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Always Force Dark", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("System-wide hw_force_dark flag (apps without dark support)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        }
                        Switch(checked = state.alwaysForceDark, onCheckedChange = { viewModel.toggleAlwaysForceDark() })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Send App Closes", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Better compatibility on some devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        }
                        Switch(checked = state.sendAppCloses, onCheckedChange = { viewModel.toggleSendAppCloses() })
                    }
                }
            }

            // ── Xposed / Advanced settings ────────────────────────────────────
            var showXposedSection by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = { showXposedSection = !showXposedSection },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Xposed / Advanced Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Aggressive force dark, status bar fix, OxygenOS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (showXposedSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    androidx.compose.animation.AnimatedVisibility(showXposedSection) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            HorizontalDivider()
                            Spacer(Modifier.height(6.dp))
                            // Aggressive force dark
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Aggressive Force Dark", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("More aggressive HWUI force-dark pass (may cause glitches)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.aggressiveForceDark, onCheckedChange = { viewModel.toggleAggressiveForceDark() })
                            }
                            HorizontalDivider()
                            // Fix status bar inversion
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Fix Status Bar Inversion", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Prevents icons from inverting in status bar (Xposed module)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.fixStatusBarInversion, onCheckedChange = { viewModel.toggleFixStatusBarInversion() })
                            }
                            HorizontalDivider()
                            // OxygenOS
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("OxygenOS Force Dark", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("OnePlus/OxygenOS specific force dark flag (op_force_dark)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.oxygenOsForceDark, onCheckedChange = { viewModel.toggleOxygenOsForceDark() })
                            }
                            HorizontalDivider()
                            // Developer options
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Monet / Material You Colors", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Apply dynamic color scheme to force-dark overlays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.useMonetColors, onCheckedChange = { viewModel.toggleUseMonetColors() })
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Show Debug Info", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Display force-dark status overlay on each app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.showDebugInfo, onCheckedChange = { viewModel.toggleShowDebugInfo() })
                            }
                        }
                    }
                }
            }

            // Schedule card
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { showScheduleDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Auto-Schedule", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(when(state.scheduleMode) {
                            DarkScheduleMode.DISABLED       -> "Disabled"
                            DarkScheduleMode.SUNRISE_SUNSET -> "Sunrise/Sunset (requires location)"
                            DarkScheduleMode.CUSTOM_TIME    -> "${state.scheduleStart} → ${state.scheduleEnd}"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }

            // Bulk actions
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.enableAllVisible() }, Modifier.weight(1f)) {
                    Icon(Icons.Default.DarkMode, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Enable All", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { viewModel.disableAllVisible() }, Modifier.weight(1f)) {
                    Icon(Icons.Default.LightMode, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Disable All", fontSize = 12.sp)
                }
            }

            // App filter chips
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DarQAppFilter.entries) { f ->
                    FilterChip(
                        selected = state.appFilter == f,
                        onClick = { viewModel.setAppFilter(f) },
                        label = { Text(when(f) { DarQAppFilter.ALL -> "All Apps"; DarQAppFilter.USER -> "User Apps"; DarQAppFilter.SYSTEM -> "System Apps" }) },
                        leadingIcon = if (state.appFilter == f) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // Search
            OutlinedTextField(
                value = state.searchQuery, onValueChange = viewModel::onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(4.dp))

            // App list
            val filtered = remember(state.apps, state.searchQuery, state.appFilter) {
                state.apps
                    .filter { when(state.appFilter) { DarQAppFilter.USER -> !it.isSystemApp; DarQAppFilter.SYSTEM -> it.isSystemApp; DarQAppFilter.ALL -> true } }
                    .filter { it.appName.contains(state.searchQuery, true) || it.packageName.contains(state.searchQuery, true) }
            }

            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = { Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null }).crossfade(true).build(),
                                contentDescription = null, modifier = Modifier.size(40.dp),
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Switch(checked = app.forceDark, onCheckedChange = { viewModel.toggleForceDark(app.packageName) })
                            }
                        },
                        colors = if (app.forceDark) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.08f)) else ListItemDefaults.colors(),
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Schedule picker dialog
    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            icon = { Icon(Icons.Default.Schedule, null) },
            title = { Text("Auto Dark Schedule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DarkScheduleMode.entries.forEach { mode ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = if (state.scheduleMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            onClick = { viewModel.setScheduleMode(mode) },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.scheduleMode == mode, onClick = { viewModel.setScheduleMode(mode) })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(when(mode) { DarkScheduleMode.DISABLED -> "Disabled"; DarkScheduleMode.SUNRISE_SUNSET -> "Sunrise / Sunset"; DarkScheduleMode.CUSTOM_TIME -> "Custom Time Range" }, fontWeight = FontWeight.Medium)
                                    Text(when(mode) { DarkScheduleMode.DISABLED -> "No automatic switching"; DarkScheduleMode.SUNRISE_SUNSET -> "Uses your location"; DarkScheduleMode.CUSTOM_TIME -> "Pick start/end time below" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (state.scheduleMode == DarkScheduleMode.CUSTOM_TIME) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = state.scheduleStart, onValueChange = viewModel::setScheduleStart, label = { Text("Start (HH:MM)") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = state.scheduleEnd, onValueChange = viewModel::setScheduleEnd, label = { Text("End (HH:MM)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                    if (state.scheduleMode == DarkScheduleMode.SUNRISE_SUNSET) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Location permission required for accurate sunrise/sunset times.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showScheduleDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showScheduleDialog = false }) { Text("Cancel") } },
        )
    }

    // Advanced info dialog
    if (showAdvancedInfo) {
        AlertDialog(
            onDismissRequest = { showAdvancedInfo = false },
            title = { Text("About DarQ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Force dark mode applies a dark color filter to app UIs that don't support dark mode natively. Works best on white/light apps.", style = MaterialTheme.typography.bodySmall)
                    Text("Methods: ACCU uses Android's HWUI force_dark flag via Shizuku (no root needed). Results vary by app.", style = MaterialTheme.typography.bodySmall)
                    Text("Backup & Restore", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { viewModel.exportSettings(); showAdvancedInfo = false }, Modifier.weight(1f)) { Text("Export") }
                        OutlinedButton(onClick = {
                            showAdvancedInfo = false
                            importLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/json" })
                        }, Modifier.weight(1f)) { Text("Import") }
                    }
                }
            },
            confirmButton = { Button(onClick = { showAdvancedInfo = false }) { Text("Close") } },
        )
    }
}
