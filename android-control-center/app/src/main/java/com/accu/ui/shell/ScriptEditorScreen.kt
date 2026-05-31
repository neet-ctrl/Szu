package com.accu.ui.shell

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.ui.components.InfoTooltipIcon
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────

data class ShellScript(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastRun: Long = 0L,
    val runCount: Int = 0,
    val isFavorite: Boolean = false,
    val lastOutput: String = "",
)

data class ScriptRunResult(
    val scriptId: String,
    val output: String,
    val exitCode: Int,
    val durationMs: Long,
    val ranAt: Long = System.currentTimeMillis(),
)

// Built-in script templates (aShellYou inspired)
val SCRIPT_TEMPLATES = listOf(
    ShellScript(
        id = "tpl_debloat",
        name = "Debloat Common Bloatware",
        description = "Uninstall common bloatware packages for current user",
        content = """#!/system/bin/sh
# Debloat script — removes common bloatware for current user
PKGS=(
  "com.facebook.appmanager"
  "com.facebook.services"
  "com.facebook.system"
  "com.microsoft.skydrive"
  "com.amazon.mShop.android.shopping"
  "com.netflix.partner.activation"
)
for pkg in "${'$'}{PKGS[@]}"; do
  result=$(pm uninstall --user 0 ${'$'}pkg 2>&1)
  echo "[${'$'}pkg] ${'$'}result"
done
echo "Done."
""",
        tags = listOf("debloat", "cleanup"),
        isFavorite = true,
    ),
    ShellScript(
        id = "tpl_wifi_diag",
        name = "Wi-Fi Diagnostics",
        description = "Show detailed Wi-Fi connection info and signal stats",
        content = """#!/system/bin/sh
# Wi-Fi diagnostics
echo "=== Wi-Fi Info ==="
dumpsys wifi | grep -E "mNetworkInfo|mWifiInfo|SSID|BSSID|rssi|linkSpeed|frequency|ipAddress" | head -20
echo ""
echo "=== IP Configuration ==="
ip addr show wlan0 2>/dev/null || ifconfig wlan0 2>/dev/null
echo ""
echo "=== DNS ==="
getprop net.dns1
getprop net.dns2
echo ""
echo "=== Network Stats ==="
cat /proc/net/dev | grep wlan0
""",
        tags = listOf("network", "diagnostics"),
    ),
    ShellScript(
        id = "tpl_battery_stats",
        name = "Battery Statistics",
        description = "Comprehensive battery health and usage report",
        content = """#!/system/bin/sh
# Battery statistics
echo "=== Battery Info ==="
dumpsys battery
echo ""
echo "=== Battery Level History ==="
dumpsys batterystats | grep -E "level:|voltage:|temperature:|technology:" | head -20
echo ""
echo "=== Top Battery Consumers ==="
dumpsys batterystats | grep "cpu=" | sort -t= -k2 -rn | head -15
""",
        tags = listOf("battery", "diagnostics"),
    ),
    ShellScript(
        id = "tpl_freeze_bg",
        name = "Freeze Background Apps",
        description = "Suspend all non-essential background applications",
        content = """#!/system/bin/sh
# Freeze background apps (Hail-style)
KEEP=(
  "com.android.systemui"
  "com.android.phone"
  "com.google.android.gms"
)
pm list packages -3 | sed 's/package://' | while read pkg; do
  skip=false
  for k in "${'$'}{KEEP[@]}"; do [ "${'$'}pkg" = "${'$'}k" ] && skip=true; done
  if [ "${'$'}skip" = false ]; then
    pm suspend --user 0 "${'$'}pkg" 2>/dev/null && echo "Froze: ${'$'}pkg"
  fi
done
echo "Done."
""",
        tags = listOf("freeze", "battery", "performance"),
    ),
    ShellScript(
        id = "tpl_storage_report",
        name = "Storage Usage Report",
        description = "Detailed breakdown of storage usage by app",
        content = """#!/system/bin/sh
# Storage usage report (SD Maid SE style)
echo "=== Storage Overview ==="
df -h
echo ""
echo "=== Largest Files in /sdcard ==="
find /sdcard -maxdepth 4 -type f -size +50M 2>/dev/null | xargs -I{} ls -lh {} 2>/dev/null | sort -k5 -rh | head -20
echo ""
echo "=== App Data Sizes ==="
du -sh /data/data/* 2>/dev/null | sort -rh | head -20
""",
        tags = listOf("storage", "cleanup"),
    ),
    ShellScript(
        id = "tpl_network_control",
        name = "Network Toggle (Better Internet Tiles)",
        description = "Toggle Wi-Fi or mobile data via ADB commands",
        content = """#!/system/bin/sh
# Network control — Better Internet Tiles style
echo "Current Wi-Fi state:"
settings get global wifi_on

# Toggle Wi-Fi: uncomment desired action
# cmd wifi set-wifi-enabled enabled    # enable
# cmd wifi set-wifi-enabled disabled   # disable

echo ""
echo "Current mobile data state:"
settings get global mobile_data

# Toggle mobile data: uncomment desired action
# svc data enable    # enable
# svc data disable   # disable

echo ""
echo "To apply changes, uncomment the desired line and re-run."
""",
        tags = listOf("network", "wifi", "tiles"),
    ),
    ShellScript(
        id = "tpl_perm_grant",
        name = "Grant All Permissions to App",
        description = "Grant every declared dangerous permission to a package",
        content = """#!/system/bin/sh
# Grant all dangerous permissions to an app (ACCU-powered)
PKG="com.example.app"   # <-- change this to your package name

echo "Granting all dangerous permissions to: ${'$'}PKG"
pm list permissions -d -g | grep "permission:" | sed 's/  permission://' | while read perm; do
  result=$(pm grant "${'$'}PKG" "${'$'}perm" 2>&1)
  echo "[${'$'}perm]: ${'$'}result"
done
echo "Done."
""",
        tags = listOf("permissions", "accu"),
    ),
    ShellScript(
        id = "tpl_component_disable",
        name = "Disable App Components (Blocker)",
        description = "Disable specific activities, services and receivers",
        content = """#!/system/bin/sh
# Disable components — Blocker style (requires ACCU)
PKG="com.example.app"    # <-- change this

# Disable a specific component:
# pm disable-user --user 0 "${'$'}PKG/.SomeTrackerService"
# pm disable-user --user 0 "${'$'}PKG/.AnalyticsReceiver"

# List all components:
echo "=== Activities ==="
pm dump "${'$'}PKG" | grep "Activity{" | head -20
echo ""
echo "=== Services ==="
pm dump "${'$'}PKG" | grep "ServiceRecord{" | head -20
echo ""
echo "=== Receivers ==="
pm dump "${'$'}PKG" | grep "ReceiverRecord{" | head -20
""",
        tags = listOf("blocker", "privacy", "components"),
    ),
)

data class ScriptEditorState(
    val scripts: List<ShellScript> = SCRIPT_TEMPLATES,
    val selectedScript: ShellScript? = null,
    val editorContent: String = "",
    val editorName: String = "",
    val editorDescription: String = "",
    val editorTags: String = "",
    val isRunning: Boolean = false,
    val runOutput: String = "",
    val runResults: List<ScriptRunResult> = emptyList(),
    val searchQuery: String = "",
    val filterTag: String? = null,
    val mode: ScriptEditorMode = ScriptEditorMode.LIST,
    val snackbarMessage: String? = null,
)

enum class ScriptEditorMode { LIST, EDITOR, RUNNING }

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class ScriptEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {

    private val _state = MutableStateFlow(ScriptEditorState())
    val state: StateFlow<ScriptEditorState> = _state.asStateFlow()

    fun newScript() {
        val s = ShellScript(name = "New Script", content = "#!/system/bin/sh\n# My script\necho 'Hello from ACCU!'")
        _state.update { it.copy(
            selectedScript = s, editorContent = s.content, editorName = s.name,
            editorDescription = s.description, editorTags = "", mode = ScriptEditorMode.EDITOR,
        )}
    }

    fun openScript(script: ShellScript) {
        _state.update { it.copy(
            selectedScript = script, editorContent = script.content, editorName = script.name,
            editorDescription = script.description, editorTags = script.tags.joinToString(","),
            mode = ScriptEditorMode.EDITOR, runOutput = "",
        )}
    }

    fun saveScript() {
        val s = _state.value
        val script = (s.selectedScript ?: ShellScript(name = s.editorName, content = s.editorContent)).copy(
            name = s.editorName.ifBlank { "Untitled" },
            description = s.editorDescription,
            content = s.editorContent,
            tags = s.editorTags.split(",").map { it.trim() }.filter { it.isNotBlank() },
        )
        val existing = s.scripts.indexOfFirst { it.id == script.id }
        val updated = if (existing >= 0) s.scripts.toMutableList().also { it[existing] = script }
                      else s.scripts + script
        _state.update { it.copy(scripts = updated, selectedScript = script, snackbarMessage = "Script saved") }
    }

    fun deleteScript(script: ShellScript) {
        _state.update { s -> s.copy(
            scripts = s.scripts.filter { it.id != script.id },
            mode = ScriptEditorMode.LIST,
            snackbarMessage = "Script deleted",
        )}
    }

    fun runScript(script: ShellScript? = null) {
        val content = script?.content ?: _state.value.editorContent
        val id = script?.id ?: _state.value.selectedScript?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRunning = true, runOutput = "Running…\n", mode = ScriptEditorMode.RUNNING) }
            val start = System.currentTimeMillis()
            val lines = content.lines().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("#!/") }
            val outputBuilder = StringBuilder()
            var failedLines = 0
            lines.forEachIndexed { i, line ->
                _state.update { it.copy(runOutput = outputBuilder.toString() + "$ $line\n") }
                val shellResult = try {
                    shizukuUtils.execShizuku(line.trim())
                } catch (e: Exception) {
                    null
                }
                val lineOk = shellResult?.isSuccess ?: false
                if (!lineOk) failedLines++
                outputBuilder.appendLine("$ ${line.trim()}")
                if (shellResult != null) {
                    if (shellResult.output.isNotBlank()) outputBuilder.appendLine(shellResult.output.trim())
                    if (!shellResult.isSuccess && shellResult.error.isNotBlank()) outputBuilder.appendLine("error: ${shellResult.error.trim()}")
                } else {
                    outputBuilder.appendLine("error: command failed")
                }
                outputBuilder.appendLine()
                _state.update { it.copy(runOutput = outputBuilder.toString()) }
                delay(50)
            }
            val duration = System.currentTimeMillis() - start
            val exitCode = if (failedLines == 0) 0 else 1
            val result = ScriptRunResult(scriptId = id, output = outputBuilder.toString(), exitCode = exitCode, durationMs = duration)
            val updatedScripts = _state.value.scripts.map {
                if (it.id == id) it.copy(lastRun = System.currentTimeMillis(), runCount = it.runCount + 1, lastOutput = outputBuilder.toString().take(500))
                else it
            }
            val msg = if (failedLines == 0) "Script completed in ${duration}ms"
                      else "Script finished with $failedLines error(s) — ${duration}ms"
            _state.update { it.copy(
                isRunning = false, runResults = it.runResults + result, scripts = updatedScripts,
                snackbarMessage = msg,
            )}
        }
    }

    fun onContentChange(s: String) = _state.update { it.copy(editorContent = s) }
    fun onNameChange(s: String) = _state.update { it.copy(editorName = s) }
    fun onDescChange(s: String) = _state.update { it.copy(editorDescription = s) }
    fun onTagsChange(s: String) = _state.update { it.copy(editorTags = s) }
    fun onSearch(q: String) = _state.update { it.copy(searchQuery = q) }
    fun onTagFilter(t: String?) = _state.update { it.copy(filterTag = t) }
    fun onMode(m: ScriptEditorMode) = _state.update { it.copy(mode = m) }
    fun toggleFavorite(id: String) = _state.update { s ->
        s.copy(scripts = s.scripts.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it })
    }
    fun clearSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    fun allTags(scripts: List<ShellScript>) = scripts.flatMap { it.tags }.distinct().sorted()
    fun filteredScripts(state: ScriptEditorState): List<ShellScript> {
        var list = state.scripts
        state.filterTag?.let { tag -> list = list.filter { tag in it.tags } }
        if (state.searchQuery.isNotBlank()) list = list.filter {
            it.name.contains(state.searchQuery, true) || it.description.contains(state.searchQuery, true)
        }
        return list.sortedWith(compareByDescending<ShellScript> { it.isFavorite }.thenByDescending { it.lastRun })
    }
}

// ─────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    onBack: () -> Unit,
    viewModel: ScriptEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()) }
    val filtered = remember(state) { viewModel.filteredScripts(state) }
    val allTags = remember(state.scripts) { viewModel.allTags(state.scripts) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.mode) {
                            ScriptEditorMode.LIST    -> "Script Manager"
                            ScriptEditorMode.EDITOR  -> state.editorName.ifBlank { "New Script" }
                            ScriptEditorMode.RUNNING -> "Running Script…"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.mode == ScriptEditorMode.LIST) onBack()
                        else viewModel.onMode(ScriptEditorMode.LIST)
                    }) {
                        Icon(if (state.mode == ScriptEditorMode.LIST) Icons.Default.ArrowBack else Icons.Default.Close, null)
                    }
                },
                actions = {
                    InfoTooltipIcon(
                        title = "Script Manager — aShellYou",
                        description = "Full script editor and runner, replicating aShellYou's script management:\n\n• Write multi-line ADB shell scripts\n• Save with name, description, and tags\n• Run line-by-line via ACCU with live output\n• 8 built-in templates: debloat, freeze, network, battery, storage, etc.\n• Favorite and organize scripts\n• Copy output to clipboard\n\nAll scripts run through ACCU — no root required."
                    )
                    when (state.mode) {
                        ScriptEditorMode.LIST -> {
                            IconButton(onClick = viewModel::newScript) { Icon(Icons.Default.Add, "New Script") }
                        }
                        ScriptEditorMode.EDITOR -> {
                            IconButton(onClick = viewModel::saveScript) { Icon(Icons.Default.Save, "Save") }
                            state.selectedScript?.let { script ->
                                IconButton(onClick = { viewModel.runScript() }) {
                                    Icon(Icons.Default.PlayArrow, "Run", tint = Color(0xFF3DDC84))
                                }
                            }
                        }
                        ScriptEditorMode.RUNNING -> {
                            if (!state.isRunning) {
                                IconButton(onClick = { clipboard.setText(AnnotatedString(state.runOutput)) }) {
                                    Icon(Icons.Default.ContentCopy, "Copy output")
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(targetState = state.mode, transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
        }) { mode ->
            when (mode) {
                ScriptEditorMode.LIST -> ScriptListContent(
                    scripts = filtered, allTags = allTags, state = state, dateFormat = dateFormat, padding = padding,
                    onOpen = viewModel::openScript, onNew = viewModel::newScript,
                    onToggleFavorite = { viewModel.toggleFavorite(it.id) },
                    onDelete = viewModel::deleteScript,
                    onRun = { viewModel.runScript(it) },
                    onSearch = viewModel::onSearch,
                    onTagFilter = viewModel::onTagFilter,
                )
                ScriptEditorMode.EDITOR -> ScriptEditorContent(
                    state = state, padding = padding,
                    onContentChange = viewModel::onContentChange,
                    onNameChange = viewModel::onNameChange,
                    onDescChange = viewModel::onDescChange,
                    onTagsChange = viewModel::onTagsChange,
                    onRun = { viewModel.runScript() },
                )
                ScriptEditorMode.RUNNING -> ScriptRunContent(
                    state = state, padding = padding, clipboard = clipboard,
                    onBack = { viewModel.onMode(ScriptEditorMode.EDITOR) },
                )
            }
        }
    }
}

@Composable
private fun ScriptListContent(
    scripts: List<ShellScript>, allTags: List<String>, state: ScriptEditorState,
    dateFormat: SimpleDateFormat, padding: PaddingValues,
    onOpen: (ShellScript) -> Unit, onNew: () -> Unit,
    onToggleFavorite: (ShellScript) -> Unit, onDelete: (ShellScript) -> Unit,
    onRun: (ShellScript) -> Unit, onSearch: (String) -> Unit, onTagFilter: (String?) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)) {
        // Search
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search scripts…") },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) }
                },
                singleLine = true, shape = RoundedCornerShape(12.dp),
            )
        }
        // Tags
        if (allTags.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = state.filterTag == null, onClick = { onTagFilter(null) }, label = { Text("All", style = MaterialTheme.typography.labelSmall) })
                    }
                    items(allTags) { tag ->
                        FilterChip(selected = state.filterTag == tag, onClick = { onTagFilter(if (state.filterTag == tag) null else tag) }, label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
        // Stats row
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScriptStatChip(Icons.Outlined.Code, "${scripts.size}", "Scripts", MaterialTheme.colorScheme.primary)
                ScriptStatChip(Icons.Outlined.Star, "${scripts.count { it.isFavorite }}", "Favorites", Color(0xFFF59E0B))
                ScriptStatChip(Icons.Outlined.PlayArrow, "${scripts.sumOf { it.runCount }}", "Total Runs", Color(0xFF3DDC84))
            }
        }
        // Script items
        items(scripts, key = { it.id }) { script ->
            ScriptCard(
                script = script, dateFormat = dateFormat,
                onOpen = { onOpen(script) }, onToggleFavorite = { onToggleFavorite(script) },
                onDelete = { onDelete(script) }, onRun = { onRun(script) },
            )
        }
        if (scripts.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Code, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Text("No scripts found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onNew) { Text("Create Script") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptStatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, count: String, label: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(0.1f), border = BorderStroke(1.dp, color.copy(0.3f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(count, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScriptCard(
    script: ShellScript, dateFormat: SimpleDateFormat,
    onOpen: () -> Unit, onToggleFavorite: () -> Unit, onDelete: () -> Unit, onRun: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onOpen,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(script.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (script.description.isNotBlank()) Text(script.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (script.isFavorite) Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                if (script.runCount > 0) Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF3DDC84).copy(0.15f)) {
                    Text("${script.runCount}×", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF3DDC84))
                }
            }
            // Tags
            if (script.tags.isNotEmpty()) {
                LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(script.tags) { tag ->
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("#$tag", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            // Lines preview
            Text(
                script.content.lines().take(2).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            // Actions
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (script.lastRun > 0) Text(
                    "Last: ${dateFormat.format(Date(script.lastRun))}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(if (script.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline, null, tint = if (script.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                }
                FilledIconButton(onClick = onRun, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Script?") },
            text = { Text("Delete '${script.name}'? This cannot be undone.") },
            confirmButton = { Button(onClick = { onDelete(); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScriptEditorContent(
    state: ScriptEditorState, padding: PaddingValues,
    onContentChange: (String) -> Unit, onNameChange: (String) -> Unit,
    onDescChange: (String) -> Unit, onTagsChange: (String) -> Unit, onRun: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
        // Metadata
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.editorName, onValueChange = onNameChange,
                label = { Text("Script Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Code, null, Modifier.size(18.dp)) },
            )
            OutlinedTextField(
                value = state.editorDescription, onValueChange = onDescChange,
                label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.editorTags, onValueChange = onTagsChange,
                label = { Text("Tags (comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("network, cleanup, battery") },
            )
        }

        HorizontalDivider()

        // Code editor area
        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0D1117)).padding(16.dp),
        ) {
            BasicTextField(
                value = state.editorContent,
                onValueChange = onContentChange,
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE6EDF3),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(Color(0xFF58A6FF)),
            )
            if (state.editorContent.isEmpty()) {
                Text(
                    "#!/system/bin/sh\n# Write your shell script here…",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    color = Color(0xFF484F58),
                )
            }
        }

        // Bottom bar
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.editorContent.lines().size} lines · ${state.editorContent.length} chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run Script")
                }
            }
        }
    }
}

@Composable
private fun ScriptRunContent(
    state: ScriptEditorState, padding: PaddingValues,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        // Status banner
        Surface(
            color = if (state.isRunning) Color(0xFF161B22) else Color(0xFF0D1117),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.isRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF3DDC84))
                else Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF3DDC84), modifier = Modifier.size(16.dp))
                Text(
                    if (state.isRunning) "Executing script…" else "Script finished",
                    style = MaterialTheme.typography.labelMedium, color = Color(0xFF3DDC84),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(state.runOutput)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, null, tint = Color(0xFFE6EDF3), modifier = Modifier.size(16.dp))
                }
            }
        }

        // Output terminal
        val scrollState = rememberScrollState()
        LaunchedEffect(state.runOutput) { scrollState.animateScrollTo(scrollState.maxValue) }

        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0D1117)).padding(16.dp).verticalScroll(scrollState)) {
            SelectionContainer {
                Text(
                    state.runOutput,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
                    color = Color(0xFFE6EDF3),
                )
            }
        }

        // Footer
        if (!state.isRunning) {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back to Editor")
                    }
                    Button(onClick = { clipboard.setText(AnnotatedString(state.runOutput)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Output")
                    }
                }
            }
        }
    }
}
