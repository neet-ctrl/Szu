package com.accu.ui.appmanager

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────────────────
//  Enums & Models
// ─────────────────────────────────────────────────────────

enum class ExplorerFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Default.Apps),
    USER("User", Icons.Default.PersonOutline),
    SYSTEM("System", Icons.Default.PhoneAndroid),
}

enum class AppSort(val label: String) {
    NAME("Name"), SIZE("Size"), INSTALL_DATE("Install Date"), LAST_UPDATE("Updated"),
}

enum class PermProtection(val label: String, val badgeColor: @Composable () -> Color, val icon: ImageVector) {
    DANGEROUS("Dangerous",       { Color(0xFFE53935) }, Icons.Default.GppBad),
    SIGNATURE("Signature",       { Color(0xFF1E88E5) }, Icons.Default.VpnKey),
    PRIVILEGED("Privileged",     { Color(0xFF8E24AA) }, Icons.Default.AdminPanelSettings),
    DEVELOPMENT("Development",   { Color(0xFFFF8F00) }, Icons.Default.DeveloperMode),
    PREINSTALLED("Pre-installed",{ Color(0xFF00897B) }, Icons.Default.InstallDesktop),
    NORMAL("Normal",             { Color(0xFF43A047) }, Icons.Default.CheckCircleOutline),
    UNKNOWN("Unknown",           { Color(0xFF757575) }, Icons.Default.Help),
}

data class AppPermission(
    val name: String,
    val simpleName: String,
    val description: String,
    val protection: PermProtection,
    val isGranted: Boolean,
    val isToggleable: Boolean,
)

data class AppCommand(
    val label: String,
    val command: String,
    val description: String,
    val icon: ImageVector,
    val isDangerous: Boolean = false,
)

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val installDate: Long,
    val updateDate: Long,
    val installerPackage: String?,
    val apkPath: String,
    val dataDir: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val sizeBytes: Long,
    val permissions: List<AppPermission>,
)

data class AppExplorerUiState(
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val filter: ExplorerFilter = ExplorerFilter.USER,
    val sort: AppSort = AppSort.NAME,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val commandOutputs: Map<String, Pair<String, String>> = emptyMap(),
    val snackbarMessage: String? = null,
)

// ─────────────────────────────────────────────────────────
//  Commands Builder
// ─────────────────────────────────────────────────────────

fun buildAppCommands(pkg: String): List<AppCommand> = listOf(
    AppCommand("Force Stop",          "am force-stop $pkg",                                  "Immediately kill all app processes",           Icons.Default.Stop),
    AppCommand("Clear Cache",         "pm clear --cache-only $pkg",                          "Delete cached data only (keeps user data)",     Icons.Default.CleaningServices),
    AppCommand("Kill Background",     "am kill $pkg",                                        "Kill background processes only",               Icons.Default.Close),
    AppCommand("Start App",           "monkey -p $pkg -c android.intent.category.LAUNCHER 1","Launch the app's main activity",               Icons.Default.PlayArrow),
    AppCommand("App Info Dump",       "dumpsys package $pkg",                                "Full package manager dump for this app",        Icons.Default.Info),
    AppCommand("Memory Stats",        "dumpsys meminfo $pkg",                                "Memory usage breakdown by category",           Icons.Default.Memory),
    AppCommand("Battery Stats",       "dumpsys batterystats $pkg",                           "Battery consumption statistics",               Icons.Default.BatteryFull),
    AppCommand("List Activities",     "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg", "All launchable activities", Icons.Default.List),
    AppCommand("APK Path",            "pm path $pkg",                                        "Show path to the installed APK file",          Icons.Default.FolderOpen),
    AppCommand("Version Info",        "dumpsys package $pkg | grep -E 'versionName|versionCode|targetSdk'", "Version and SDK info",         Icons.Default.Info),
    AppCommand("Permission List",     "dumpsys package $pkg | grep -A2 'requested permissions'", "All declared permissions with grant state", Icons.Default.Security),
    AppCommand("Network Usage",       "dumpsys netstats | grep $pkg",                        "Network data usage stats",                     Icons.Default.Wifi),
    AppCommand("CPU Usage",           "top -n 1 -b | grep $pkg",                             "Current CPU usage snapshot",                   Icons.Default.Speed),
    AppCommand("Process List",        "ps -A | grep $pkg",                                   "Running processes for this app",               Icons.Default.Terminal),
    AppCommand("Freeze (Suspend)",    "pm suspend --user 0 $pkg",                            "Suspend app like Hail (freeze)",               Icons.Default.AcUnit),
    AppCommand("Unfreeze (Resume)",   "pm unsuspend --user 0 $pkg",                          "Unsuspend / unfreeze the app",                 Icons.Default.Whatshot),
    AppCommand("Disable App",         "pm disable-user --user 0 $pkg",                       "Disable app without uninstalling",             Icons.Default.Block, isDangerous = true),
    AppCommand("Enable App",          "pm enable $pkg",                                      "Re-enable a disabled app",                     Icons.Default.CheckCircle),
    AppCommand("Clear All Data",      "pm clear $pkg",                                       "Wipe ALL app data — cannot be undone",         Icons.Default.DeleteForever, isDangerous = true),
    AppCommand("Uninstall (User)",    "pm uninstall --user 0 $pkg",                          "Remove for current user only",                 Icons.Default.DeleteOutline, isDangerous = true),
    AppCommand("Uninstall (All)",     "pm uninstall $pkg",                                   "Remove completely from all users",             Icons.Default.Delete, isDangerous = true),
    AppCommand("Hide App",            "pm hide --user 0 $pkg",                               "Hide from launcher (requires Device Owner)",   Icons.Default.VisibilityOff, isDangerous = true),
    AppCommand("Unhide App",          "pm unhide --user 0 $pkg",                             "Restore hidden app to launcher",               Icons.Default.Visibility),
    AppCommand("Grant All Perms",     "pm list permissions -d -g | grep permission | sed 's/.*permission://' | while read p; do pm grant $pkg \$p 2>/dev/null; done; echo Done", "Grant all dangerous permissions declared", Icons.Default.LockOpen),
    AppCommand("Revoke All Perms",    "pm reset-permissions $pkg",                           "Reset all permissions to install defaults",    Icons.Default.Lock, isDangerous = true),
    AppCommand("Backup Data",         "mkdir -p /sdcard/ACCUBackup && tar -czf /sdcard/ACCUBackup/${pkg}_\$(date +%Y%m%d_%H%M%S).tar.gz /data/data/$pkg 2>&1 && echo 'Saved to /sdcard/ACCUBackup/' || echo 'Failed — root required'", "Backup app data dir to /sdcard/ACCUBackup/ (root required)", Icons.Default.Backup),
    AppCommand("Storage Stats",       "stat /data/data/$pkg/ 2>/dev/null || echo 'Root required'", "Data directory stats",                  Icons.Default.Storage),
    AppCommand("Startup Time",        "am start-activity -W -n $(pm dump $pkg | grep 'android.intent.action.MAIN' | head -1 | awk '{print \$2}')", "Measure cold startup time", Icons.Default.Timer),
)

// ─────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────

@HiltViewModel
class AppExplorerViewModel @Inject constructor(
    private val shizuku: ShizukuUtils,
    private val connectionManager: com.accu.connection.AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AppExplorerUiState())
    val state: StateFlow<AppExplorerUiState> = _state.asStateFlow()

    init { loadApps() }

    fun loadApps() = viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isLoading = true) }
        // Always query the connected TARGET device via ADB/shell — never local PackageManager
        val appList = loadAppsViaShell()
        _state.update { s ->
            s.copy(apps = appList, isLoading = false).let { applyFilter(it) }
        }
    }

    /**
     * Loads package list from the TARGET device via shell commands routed through
     * AccuConnectionManager (Root / Wireless ADB / OTG ADB).  Enriches each entry
     * with version, SDK, APK size, install/update dates via a single batch shell loop
     * so the round-trip count is O(1) rather than O(N).
     */
    private suspend fun loadAppsViaShell(): List<AppInfo> {
        val rawAll      = connectionManager.exec("pm list packages --show-versioncode -i 2>/dev/null").output
        val rawSystem   = connectionManager.exec("pm list packages -s 2>/dev/null").output
        val rawDisabled = connectionManager.exec("pm list packages -d 2>/dev/null").output

        val systemPkgs  = rawSystem.lines().filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }.toSet()
        val disabledPkgs = rawDisabled.lines().filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }.toSet()

        data class Basic(val pkg: String, val vc: Long, val installer: String?)
        val basicList = rawAll.lines().filter { it.startsWith("package:") }.mapNotNull { line ->
            val pkg = line.substringAfter("package:").substringBefore(" ").substringBefore("\t").trim()
            if (pkg.isEmpty()) return@mapNotNull null
            val vc = Regex("versionCode:(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val installer = Regex("installer[=:]([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
                ?.trimEnd()?.let { if (it == "null" || it.isEmpty()) null else it }
            Basic(pkg, vc, installer)
        }
        if (basicList.isEmpty()) return emptyList()

        // Single shell loop on the target device — one round-trip for all packages
        val pkgList = basicList.take(150).joinToString(" ") { "\"${it.pkg}\"" }
        val batchScript = """for pkg in $pkgList; do path=$(pm path "${'$'}pkg" 2>/dev/null | sed 's/package://'); sz=$([ -n "${'$'}path" ] && stat -c %s "${'$'}path" 2>/dev/null || echo 0); dump=$(dumpsys package "${'$'}pkg" 2>/dev/null | grep -E 'versionName=|firstInstallTime=|lastUpdateTime=|targetSdk=|minSdk=' | head -6 | tr '\n' '|'); echo "PKG:${'$'}pkg|SZ:${'$'}sz|PATH:${'$'}path|${'$'}dump"; done 2>/dev/null"""
        val batchOut = connectionManager.exec(batchScript).output

        data class Enriched(val sz: Long, val path: String, val fields: Map<String, String>)
        val enriched = mutableMapOf<String, Enriched>()
        batchOut.lines().filter { it.startsWith("PKG:") }.forEach { line ->
            val pkg  = line.substringAfter("PKG:").substringBefore("|").trim()
            val sz   = Regex("\\|SZ:(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val path = Regex("\\|PATH:([^|]+)").find(line)?.groupValues?.getOrNull(1)?.trim() ?: ""
            val fields = mutableMapOf<String, String>()
            line.split("|").forEach { part ->
                val t = part.trim()
                if (t.contains("=") && !t.startsWith("PKG:") && !t.startsWith("SZ:") && !t.startsWith("PATH:")) {
                    val k = t.substringBefore("=").trim(); val v = t.substringAfter("=").trim()
                    if (k.isNotEmpty()) fields[k] = v
                }
            }
            if (pkg.isNotEmpty()) enriched[pkg] = Enriched(sz, path, fields)
        }

        return basicList.map { (pkg, vc, installer) ->
            val e = enriched[pkg]
            AppInfo(
                packageName      = pkg,
                appName          = guessAppLabel(pkg),
                versionName      = e?.fields?.get("versionName") ?: "",
                versionCode      = vc,
                targetSdk        = e?.fields?.get("targetSdk")?.toIntOrNull() ?: 0,
                minSdk           = e?.fields?.get("minSdk")?.toIntOrNull() ?: 0,
                installDate      = e?.fields?.get("firstInstallTime")?.let { parseDumpTime(it) } ?: 0L,
                updateDate       = e?.fields?.get("lastUpdateTime")?.let { parseDumpTime(it) } ?: 0L,
                installerPackage = installer,
                apkPath          = e?.path ?: "",
                dataDir          = "/data/data/$pkg",
                isSystemApp      = pkg in systemPkgs,
                isEnabled        = pkg !in disabledPkgs,
                sizeBytes        = e?.sz ?: 0L,
                permissions      = emptyList(), // loaded on demand from AppDetailScreen
            )
        }
    }

    /** Derive a human-readable label from the package name */
    private fun guessAppLabel(packageName: String): String =
        packageName.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName

    private fun parseDumpTime(raw: String): Long {
        val t = raw.trim()
        t.toLongOrNull()?.let { if (it > 1_000_000_000_000L) return it }
        return try { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(t)?.time ?: 0L } catch (_: Exception) { 0L }
    }

    fun setFilter(f: ExplorerFilter) = _state.update { applyFilter(it.copy(filter = f)) }
    fun setSort(s: AppSort)     = _state.update { applyFilter(it.copy(sort = s)) }
    fun setSearch(q: String)    = _state.update { applyFilter(it.copy(searchQuery = q)) }

    private fun applyFilter(s: AppExplorerUiState): AppExplorerUiState {
        var list = s.apps
        list = when (s.filter) {
            ExplorerFilter.USER   -> list.filter { !it.isSystemApp }
            ExplorerFilter.SYSTEM -> list.filter { it.isSystemApp }
            ExplorerFilter.ALL    -> list
        }
        if (s.searchQuery.isNotBlank()) list = list.filter {
            it.appName.contains(s.searchQuery, ignoreCase = true) ||
                    it.packageName.contains(s.searchQuery, ignoreCase = true)
        }
        list = when (s.sort) {
            AppSort.NAME         -> list.sortedBy { it.appName.lowercase() }
            AppSort.SIZE         -> list.sortedByDescending { it.sizeBytes }
            AppSort.INSTALL_DATE -> list.sortedByDescending { it.installDate }
            AppSort.LAST_UPDATE  -> list.sortedByDescending { it.updateDate }
        }
        return s.copy(filteredApps = list)
    }

    fun togglePermission(pkg: String, permName: String, grant: Boolean) = viewModelScope.launch {
        val action = if (grant) "grant" else "revoke"
        val result = shizuku.execShizuku("pm $action $pkg $permName")
        val ok = !result.output.contains("Exception", ignoreCase = true) && !result.output.contains("Error", ignoreCase = true)
        _state.update { it.copy(snackbarMessage = if (ok) "${if (grant) "Granted" else "Revoked"}: ${permName.substringAfterLast('.')}" else "Failed: ${result.output}") }
        loadApps()
    }

    fun runCommand(pkg: String, cmdLabel: String, rawCmd: String) = viewModelScope.launch {
        val key = "$pkg||$cmdLabel"
        _state.update { s -> s.copy(commandOutputs = s.commandOutputs + (key to Pair("running", ""))) }
        val out = shizuku.execShizuku(rawCmd)
        _state.update { s -> s.copy(commandOutputs = s.commandOutputs + (key to Pair("done", out.output.ifBlank { "(no output)" }))) }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbarMessage = null) }

}

// ─────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppExplorerScreen(
    onBack: () -> Unit,
    viewModel: AppExplorerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSortSheet by remember { mutableStateOf(false) }
    var expandedApps by remember { mutableStateOf(setOf<String>()) }
    var expandedSections by remember { mutableStateOf(mapOf<String, Set<String>>()) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Explorer", fontWeight = FontWeight.Bold)
                        if (!state.isLoading)
                            Text("${state.filteredApps.size} / ${state.apps.size} apps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) { Icon(Icons.Default.Sort, "Sort") }
                    IconButton(onClick = viewModel::loadApps) { Icon(Icons.Default.Refresh, "Refresh") }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery, onValueChange = viewModel::setSearch,
                placeholder = { Text("Search apps or package names…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.setSearch("") }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp), singleLine = true,
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ExplorerFilter.entries) { f ->
                    val count = when (f) {
                        ExplorerFilter.ALL    -> state.apps.size
                        ExplorerFilter.USER   -> state.apps.count { !it.isSystemApp }
                        ExplorerFilter.SYSTEM -> state.apps.count { it.isSystemApp }
                    }
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text("${f.label} ($count)") },
                        leadingIcon = { Icon(f.icon, null, modifier = Modifier.size(14.dp)) },
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text("Loading apps…", color = MaterialTheme.colorScheme.outline)
                    }
                }
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.filteredApps, key = { it.packageName }) { app ->
                    val isExpanded = app.packageName in expandedApps
                    val sections  = expandedSections[app.packageName] ?: emptySet()
                    AppExplorerCard(
                        app              = app,
                        isExpanded       = isExpanded,
                        expandedSections = sections,
                        commandOutputs   = state.commandOutputs,
                        onToggleExpand   = {
                            expandedApps = if (isExpanded) expandedApps - app.packageName
                            else expandedApps + app.packageName
                        },
                        onToggleSection  = { section ->
                            expandedSections = expandedSections + (app.packageName to
                                    if (section in sections) sections - section else sections + section)
                        },
                        onTogglePermission = { perm, grant -> viewModel.togglePermission(app.packageName, perm, grant) },
                        onRunCommand       = { cmd, label   -> viewModel.runCommand(app.packageName, label, cmd) },
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sort By", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                AppSort.entries.forEach { s ->
                    ListItem(
                        headlineContent = { Text(s.label) },
                        trailingContent = { if (state.sort == s) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { viewModel.setSort(s); showSortSheet = false }.clip(RoundedCornerShape(12.dp)),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  App Card
// ─────────────────────────────────────────────────────────

@Composable
private fun AppExplorerCard(
    app: AppInfo,
    isExpanded: Boolean,
    expandedSections: Set<String>,
    commandOutputs: Map<String, Pair<String, String>>,
    onToggleExpand: () -> Unit,
    onToggleSection: (String) -> Unit,
    onTogglePermission: (String, Boolean) -> Unit,
    onRunCommand: (String, String) -> Unit,
) {
    val elevation by animateDpAsState(if (isExpanded) 4.dp else 0.dp, label = "card_elev")
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(elevation),
    ) {
        Column {
            AppHeaderRow(app, isExpanded, onToggleExpand)
            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AppMetaGrid(app)
                    Spacer(Modifier.height(10.dp))

                    val dangerCount = app.permissions.count { it.protection == PermProtection.DANGEROUS }
                    CollapsibleSection(
                        title = "Permissions (${app.permissions.size})",
                        icon = Icons.Default.Security,
                        badge = if (dangerCount > 0) "$dangerCount dangerous" else null,
                        badgeColor = MaterialTheme.colorScheme.error,
                        isExpanded = "perms" in expandedSections,
                        onToggle = { onToggleSection("perms") },
                    ) { PermissionsSection(app.permissions, onTogglePermission) }

                    Spacer(Modifier.height(6.dp))

                    CollapsibleSection(
                        title = "Shell Commands (${buildAppCommands(app.packageName).size})",
                        icon = Icons.Default.Terminal,
                        isExpanded = "cmds" in expandedSections,
                        onToggle = { onToggleSection("cmds") },
                    ) { CommandsSection(app.packageName, commandOutputs, onRunCommand) }

                    Spacer(Modifier.height(6.dp))

                    CollapsibleSection(
                        title = "Technical Details",
                        icon = Icons.Default.Code,
                        isExpanded = "info" in expandedSections,
                        onToggle = { onToggleSection("info") },
                    ) { TechDetailsSection(app) }
                }
            }
        }
    }
}

@Composable
private fun AppHeaderRow(app: AppInfo, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp),
            color = if (app.isSystemApp) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (app.isSystemApp) Icons.Default.PhoneAndroid else Icons.Default.Apps, null,
                    tint = if (app.isSystemApp) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.isSystemApp) AppBadge("SYS", MaterialTheme.colorScheme.secondary)
                if (!app.isEnabled)  AppBadge("OFF", MaterialTheme.colorScheme.error)
            }
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("v${app.versionName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Dot()
                Text(fmtSize(app.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Dot()
                val dc = app.permissions.count { it.protection == PermProtection.DANGEROUS }
                Text("${app.permissions.size} perms${if (dc > 0) " · $dc ⚠" else ""}", style = MaterialTheme.typography.labelSmall, color = if (dc > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable private fun AppBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun Dot() { Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }

@Composable
private fun AppMetaGrid(app: AppInfo) {
    val fmt = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
    val installerLabel = when (app.installerPackage) {
        "com.android.vending" -> "Google Play"
        "com.amazon.venezia"  -> "Amazon"
        null                  -> "Sideload / ADB"
        else                  -> app.installerPackage.substringAfterLast('.')
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.heightIn(max = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        userScrollEnabled = false,
    ) {
        item { MetaChip("Target SDK", "API ${app.targetSdk}",                     Icons.Default.PhoneAndroid) }
        item { MetaChip("Min SDK",    "API ${app.minSdk}",                        Icons.Default.BrandingWatermark) }
        item { MetaChip("Installed",  fmt.format(Date(app.installDate)),           Icons.Default.CalendarMonth) }
        item { MetaChip("Updated",    fmt.format(Date(app.updateDate)),            Icons.Default.Update) }
        item { MetaChip("From",       installerLabel,                              Icons.Default.Download) }
        item { MetaChip("APK Size",   fmtSize(app.sizeBytes),                     Icons.Default.Storage) }
    }
}

@Composable
private fun MetaChip(label: String, value: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String, icon: ImageVector,
    isExpanded: Boolean, onToggle: () -> Unit,
    badge: String? = null, badgeColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                if (badge != null) {
                    Surface(color = badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(badge, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                    }
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    content()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Permissions Section
// ─────────────────────────────────────────────────────────

@Composable
private fun PermissionsSection(permissions: List<AppPermission>, onToggle: (String, Boolean) -> Unit) {
    val grouped = permissions.groupBy { it.protection }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermProtection.entries.forEach { prot ->
            val perms = grouped[prot] ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 2.dp)) {
                Icon(prot.icon, null, modifier = Modifier.size(13.dp), tint = prot.badgeColor())
                Text(prot.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = prot.badgeColor())
                Surface(color = prot.badgeColor().copy(alpha = 0.15f), shape = CircleShape) {
                    Text("${perms.size}", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = prot.badgeColor(), fontWeight = FontWeight.Bold)
                }
                if (prot == PermProtection.DANGEROUS) {
                    Spacer(Modifier.weight(1f))
                    Text("Toggle directly via ACCU ↓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                perms.forEach { perm -> PermissionRow(perm, onToggle) }
            }
        }
    }
}

@Composable
private fun PermissionRow(perm: AppPermission, onToggle: (String, Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val bgColor = when {
        perm.isGranted && perm.protection == PermProtection.DANGEROUS -> perm.protection.badgeColor().copy(alpha = 0.10f)
        perm.isGranted -> perm.protection.badgeColor().copy(alpha = 0.06f)
        else           -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(9.dp), color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (perm.isGranted) Icons.Default.LockOpen else Icons.Default.Lock, null,
                    modifier = Modifier.size(14.dp),
                    tint = if (perm.isGranted) perm.protection.badgeColor() else MaterialTheme.colorScheme.outline,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(perm.simpleName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedVisibility(visible = expanded) {
                        Text(perm.name, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                    }
                }
                if (perm.isToggleable) {
                    // Direct ACCU grant/revoke — no popup
                    Switch(
                        checked = perm.isGranted,
                        onCheckedChange = { onToggle(perm.name, it) },
                        modifier = Modifier.scale(0.7f).padding(0.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = perm.protection.badgeColor(),
                            checkedTrackColor = perm.protection.badgeColor().copy(alpha = 0.3f),
                        ),
                    )
                } else {
                    Surface(color = if (perm.isGranted) perm.protection.badgeColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            if (perm.isGranted) "ALLOWED" else "DENIED",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (perm.isGranted) perm.protection.badgeColor() else MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold, fontSize = 9.sp,
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    perm.description,
                    modifier = Modifier.padding(start = 32.dp, end = 10.dp, bottom = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Commands Section
// ─────────────────────────────────────────────────────────

@Composable
private fun CommandsSection(
    pkg: String,
    commandOutputs: Map<String, Pair<String, String>>,
    onRunCommand: (String, String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val commands = remember(pkg) { buildAppCommands(pkg) }
    var showDangerous by remember { mutableStateOf(false) }
    val safeCmds = commands.filter { !it.isDangerous }
    val dangerCmds = commands.filter { it.isDangerous }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("⚡ Safe Commands", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        safeCmds.forEach { cmd ->
            val key = "$pkg||${cmd.label}"
            val pair = commandOutputs[key]
            CommandRow(cmd, pair, onRun = { onRunCommand(cmd.command, cmd.label) }, onCopy = {
                clipboard.setText(AnnotatedString(pair?.second ?: cmd.command))
            })
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠ Dangerous (${dangerCmds.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            Text("Show", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(6.dp))
            Switch(checked = showDangerous, onCheckedChange = { showDangerous = it }, modifier = Modifier.scale(0.75f))
        }
        AnimatedVisibility(visible = showDangerous) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Text("These commands can permanently delete data.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                dangerCmds.forEach { cmd ->
                    val key = "$pkg||${cmd.label}"
                    val pair = commandOutputs[key]
                    CommandRow(cmd, pair, onRun = { onRunCommand(cmd.command, cmd.label) }, onCopy = {
                        clipboard.setText(AnnotatedString(pair?.second ?: cmd.command))
                    })
                }
            }
        }
    }
}

@Composable
private fun CommandRow(
    cmd: AppCommand,
    outputPair: Pair<String, String>?,
    onRun: () -> Unit,
    onCopy: () -> Unit,
) {
    val isRunning = outputPair?.first == "running"
    val output    = outputPair?.second?.takeIf { it.isNotEmpty() }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (cmd.isDangerous) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(cmd.icon, null, modifier = Modifier.size(18.dp), tint = if (cmd.isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(cmd.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(cmd.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    FilledTonalIconButton(onClick = onRun, modifier = Modifier.size(30.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (cmd.isDangerous) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
                        Icon(Icons.Default.PlayArrow, "Run", modifier = Modifier.size(16.dp), tint = if (cmd.isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
                if (output != null) {
                    FilledTonalIconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp))
                    }
                }
            }
            AnimatedVisibility(visible = output != null) {
                SelectionContainer {
                    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).padding(bottom = 6.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFF0D1117)) {
                        Text(
                            text = output ?: "",
                            modifier = Modifier.padding(10.dp).heightIn(max = 220.dp),
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                            color = Color(0xFF00FF88),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Technical Details
// ─────────────────────────────────────────────────────────

@Composable
private fun TechDetailsSection(app: AppInfo) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        DetailRow("Package",      app.packageName,        onCopy = { clipboard.setText(AnnotatedString(app.packageName)) })
        DetailRow("Version Code", "${app.versionCode}")
        DetailRow("Target SDK",   "API ${app.targetSdk} — Android ${sdkName(app.targetSdk)}")
        DetailRow("Min SDK",      "API ${app.minSdk} — Android ${sdkName(app.minSdk)}")
        DetailRow("APK Path",     app.apkPath,            onCopy = { clipboard.setText(AnnotatedString(app.apkPath)) })
        DetailRow("Data Dir",     app.dataDir,            onCopy = { clipboard.setText(AnnotatedString(app.dataDir)) })
        DetailRow("Enabled",      if (app.isEnabled) "Yes" else "No (disabled)")
        DetailRow("Type",         if (app.isSystemApp) "System" else "User-installed")
        val dc = app.permissions.count { it.protection == PermProtection.DANGEROUS }
        val gc = app.permissions.count { it.isGranted }
        DetailRow("Permissions",  "${app.permissions.size} total · $dc dangerous · $gc granted")
    }
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────

private fun sdkName(sdk: Int) = when {
    sdk >= 35 -> "15+"; sdk >= 34 -> "14"; sdk >= 33 -> "13"; sdk >= 32 -> "12L"
    sdk >= 31 -> "12";  sdk >= 30 -> "11"; sdk >= 29 -> "10"; sdk >= 28 -> "9"
    sdk >= 26 -> "8";   sdk >= 24 -> "7";  sdk >= 23 -> "6";  sdk >= 21 -> "5"
    else -> "$sdk"
}

private fun fmtSize(b: Long) = when {
    b <= 0          -> "—"
    b < 1_024       -> "$b B"
    b < 1_048_576   -> "${"%.1f".format(b / 1_024f)} KB"
    b < 1_073_741_824 -> "${"%.1f".format(b / 1_048_576f)} MB"
    else            -> "${"%.2f".format(b / 1_073_741_824.0)} GB"
}
