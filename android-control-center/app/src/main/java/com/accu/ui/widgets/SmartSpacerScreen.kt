package com.accu.ui.widgets

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.data.db.dao.SmartSpacerPluginDao
import com.accu.data.db.entities.SmartSpacerPluginEntity
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ──────────────────────────────────────────────
//  SmartSpacer — At-a-Glance / Smartspace
// ──────────────────────────────────────────────

enum class SmartSpacerTab { TARGETS, COMPLICATIONS, REPOSITORY, SETTINGS }
enum class SmartSpacerMode { NONE, ENHANCED, NATIVE }

data class TargetUiModel(
    val id: String, val name: String, val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isEnabled: Boolean = true,
    val requirements: List<String> = emptyList(),
)

data class ComplicationUiModel(
    val id: String, val name: String, val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isEnabled: Boolean = false,
    val slot: String = "Left",
)

data class SmartSpacerState(
    val plugins: List<SmartSpacerPluginEntity> = emptyList(),
    val mode: SmartSpacerMode = SmartSpacerMode.ENHANCED,
    val showNotificationWidget: Boolean = false,
    val expandedMode: Boolean = false,
    val hideSensitiveContent: Boolean = false,
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class SmartSpacerViewModel @Inject constructor(
    private val pluginDao: SmartSpacerPluginDao,
) : ViewModel() {
    private val _state = MutableStateFlow(SmartSpacerState())
    val state: StateFlow<SmartSpacerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            pluginDao.observeAll().collect { plugins -> _state.update { it.copy(plugins = plugins, isLoading = false) } }
        }
        seedDefaultPlugins()
    }

    private fun seedDefaultPlugins() {
        viewModelScope.launch {
            val existing = pluginDao.observeAll().firstOrNull() ?: emptyList()
            if (existing.isEmpty()) {
                val defaults = listOf(
                    SmartSpacerPluginEntity("weather",       "Weather",           "Current weather with temperature & conditions", displayOrder = 0),
                    SmartSpacerPluginEntity("calendar",      "Calendar",          "Next calendar event with countdown",             displayOrder = 1),
                    SmartSpacerPluginEntity("music",         "Now Playing",       "Currently playing music track",                  displayOrder = 2),
                    SmartSpacerPluginEntity("steps",         "Step Counter",      "Daily step count from Google Fit",               displayOrder = 3),
                    SmartSpacerPluginEntity("battery",       "Battery",           "Battery percentage and charging status",         displayOrder = 4),
                    SmartSpacerPluginEntity("notifications", "Notification Count","Unread notifications grouped by app",            displayOrder = 5),
                    SmartSpacerPluginEntity("shortcuts",     "App Shortcuts",     "Configurable app shortcuts row",                 displayOrder = 6),
                    SmartSpacerPluginEntity("datetime",      "Date & Time",       "Enhanced date/time with timezone",               displayOrder = 7),
                    SmartSpacerPluginEntity("greeting",      "Greeting",          "Time-based greeting message",                    displayOrder = 8),
                    SmartSpacerPluginEntity("flashlight",    "Flashlight",        "Quick flashlight toggle shortcut",               displayOrder = 9),
                )
                defaults.forEach { pluginDao.insert(it) }
            }
        }
    }

    fun togglePlugin(id: String, enabled: Boolean) { viewModelScope.launch { pluginDao.setEnabled(id, enabled) } }
    fun setMode(m: SmartSpacerMode) { _state.update { it.copy(mode = m) } }
    fun toggleNotificationWidget() { _state.update { it.copy(showNotificationWidget = !it.showNotificationWidget) } }
    fun toggleExpandedMode() { _state.update { it.copy(expandedMode = !it.expandedMode) } }
    fun toggleHideSensitive() { _state.update { it.copy(hideSensitiveContent = !it.hideSensitiveContent) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
    fun fetchRepository(url: String) {
        if (url.isBlank()) { _state.update { it.copy(snackbarMessage = "Enter a repository URL first") }; return }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _state.update { it.copy(snackbarMessage = "Fetching repository from $url…") }
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().readText(); conn.disconnect()
                val count = body.lines().count { it.trim().startsWith("{") || it.contains("\"name\"") }
                _state.update { it.copy(snackbarMessage = "Repository fetched — found ~$count plugins") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Fetch failed: ${e.message}") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSpacerScreen(
    onBack: () -> Unit,
    viewModel: SmartSpacerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(SmartSpacerTab.TARGETS) }

    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() } }

    Scaffold(
        topBar = {
            Column {
                ACCTopBar(title = "Smart Widgets", onBack = onBack, actions = {
                    IconButton(onClick = { selectedTab = SmartSpacerTab.REPOSITORY }) { Icon(Icons.Default.Add, "Add") }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(showMenu, { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { selectedTab = SmartSpacerTab.SETTINGS; showMenu = false })
                            DropdownMenuItem(text = { Text("Repository") }, leadingIcon = { Icon(Icons.Outlined.CloudDownload, null) }, onClick = { selectedTab = SmartSpacerTab.REPOSITORY; showMenu = false })
                        }
                    }
                })
                TabRow(selectedTabIndex = SmartSpacerTab.entries.indexOf(selectedTab)) {
                    SmartSpacerTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(when(tab) { SmartSpacerTab.TARGETS -> "Targets"; SmartSpacerTab.COMPLICATIONS -> "Complications"; SmartSpacerTab.REPOSITORY -> "Repository"; SmartSpacerTab.SETTINGS -> "Settings" }) },
                            icon = { Icon(when(tab) { SmartSpacerTab.TARGETS -> Icons.Outlined.Widgets; SmartSpacerTab.COMPLICATIONS -> Icons.Outlined.Info; SmartSpacerTab.REPOSITORY -> Icons.Outlined.CloudDownload; SmartSpacerTab.SETTINGS -> Icons.Outlined.Settings }, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (selectedTab) {
            SmartSpacerTab.TARGETS       -> TargetsTab(state, viewModel, padding)
            SmartSpacerTab.COMPLICATIONS -> ComplicationsTab(padding)
            SmartSpacerTab.REPOSITORY    -> RepositoryTab(viewModel, padding)
            SmartSpacerTab.SETTINGS      -> SmartSpacerSettingsTab(state, viewModel, padding)
        }
    }
}

// ─── Targets Tab ───
private val BUILTIN_TARGETS = listOf(
    TargetUiModel("weather",    "Weather",           "Shows temperature, conditions, and location",       Icons.Outlined.WbSunny,           true,  listOf("Internet", "Location")),
    TargetUiModel("calendar",   "Calendar Events",   "Upcoming calendar events with countdown timer",     Icons.Outlined.CalendarMonth,     true,  listOf("Calendar permission")),
    TargetUiModel("music",      "Now Playing",       "Track name, artist, and playback controls",         Icons.Outlined.MusicNote,         true,  listOf("Notification access")),
    TargetUiModel("greeting",   "Greeting",          "Good morning/afternoon/evening with your name",     Icons.Outlined.WavingHand,        false, emptyList()),
    TargetUiModel("flashlight", "Flashlight",        "One-tap flashlight toggle on the lockscreen",       Icons.Outlined.FlashlightOn,      false, emptyList()),
    TargetUiModel("notification","Notifications",    "Grouped notification count with app icons",         Icons.Outlined.Notifications,     false, listOf("Notification access")),
    TargetUiModel("step_count", "Step Counter",      "Daily steps from Google Fit / Health Connect",      Icons.Outlined.DirectionsRun,     false, listOf("Activity recognition")),
    TargetUiModel("battery",    "Battery Status",    "Percentage, charging state, time-to-full",          Icons.Outlined.BatteryFull,       false, emptyList()),
    TargetUiModel("alarms",     "Alarms",            "Next alarm time",                                   Icons.Outlined.Alarm,             false, listOf("Alarm access")),
    TargetUiModel("widget",     "Custom Widget",     "Embed any app widget in the smartspace",            Icons.Outlined.Widgets,           false, listOf("Widget host permission")),
    TargetUiModel("shortcuts",  "App Shortcuts",     "Row of configurable quick-launch app icons",        Icons.Outlined.Apps,              false, emptyList()),
)

@Composable
private fun TargetsTab(state: SmartSpacerState, viewModel: SmartSpacerViewModel, padding: PaddingValues) {
    var expandedId by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Targets appear as the main Smartspace content on the lock screen and At-a-Glance widget. Enable as many as you like — they cycle automatically.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(BUILTIN_TARGETS, key = { it.id }) { target ->
            val pluginEnabled = state.plugins.firstOrNull { it.id == target.id }?.isEnabled ?: target.isEnabled
            val expanded = expandedId == target.id
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (pluginEnabled) MaterialTheme.colorScheme.primaryContainer.copy(0.25f) else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(target.name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(target.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = {
                            Surface(shape = RoundedCornerShape(10.dp), color = if (pluginEnabled) MaterialTheme.colorScheme.primary.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(target.icon, null, Modifier.size(22.dp), tint = if (pluginEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (target.requirements.isNotEmpty()) {
                                    IconButton(onClick = { expandedId = if (expanded) null else target.id }, modifier = Modifier.size(32.dp)) {
                                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                                    }
                                }
                                Switch(checked = pluginEnabled, onCheckedChange = { viewModel.togglePlugin(target.id, it) })
                            }
                        },
                        modifier = Modifier.clickable { expandedId = if (expanded) null else target.id },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                    AnimatedVisibility(visible = expanded) {
                        Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Requirements:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            target.requirements.forEach { req ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = AccentGreen)
                                    Spacer(Modifier.width(4.dp))
                                    Text(req, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Complications Tab ───
private val BUILTIN_COMPLICATIONS = listOf(
    ComplicationUiModel("date",        "Date",             "Current date (e.g. Monday, Jan 1)",       Icons.Outlined.CalendarToday, true,  "Left"),
    ComplicationUiModel("battery",     "Battery",          "Battery % with charging icon",            Icons.Outlined.BatteryFull,   true,  "Right"),
    ComplicationUiModel("steps",       "Steps",            "Step counter from Health Connect",        Icons.Outlined.DirectionsRun, false, "Left"),
    ComplicationUiModel("weather_temp","Temperature",      "Current temp from weather provider",      Icons.Outlined.Thermostat,    false, "Right"),
    ComplicationUiModel("alarm",       "Next Alarm",       "Time of next set alarm",                  Icons.Outlined.Alarm,         false, "Left"),
    ComplicationUiModel("notification","Notifications",    "Count of unread notifications",           Icons.Outlined.Notifications, false, "Right"),
    ComplicationUiModel("gmail",       "Gmail",            "Unread Gmail count",                     Icons.Outlined.Email,         false, "Left"),
)

@Composable
private fun ComplicationsTab(padding: PaddingValues) {
    var compMap by remember { mutableStateOf(BUILTIN_COMPLICATIONS.associateBy { it.id }) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Complications are small informational chips shown alongside the main Smartspace target.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(BUILTIN_COMPLICATIONS, key = { it.id }) { comp ->
            val current = compMap[comp.id] ?: comp
            ListItem(
                headlineContent = { Text(comp.name, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(comp.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(current.slot, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                },
                leadingContent = {
                    Surface(shape = RoundedCornerShape(8.dp), color = if (current.isEnabled) AccentCyan.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(comp.icon, null, Modifier.size(20.dp), tint = if (current.isEnabled) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var showSlotMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { showSlotMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(current.slot, style = MaterialTheme.typography.labelSmall) }
                            DropdownMenu(showSlotMenu, { showSlotMenu = false }) {
                                listOf("Left","Right","Start","End").forEach { slot ->
                                    DropdownMenuItem(text = { Text(slot) }, onClick = { compMap = compMap + (comp.id to current.copy(slot = slot)); showSlotMenu = false })
                                }
                            }
                        }
                        Switch(checked = current.isEnabled, onCheckedChange = { compMap = compMap + (comp.id to current.copy(isEnabled = it)) })
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

// ─── Repository Tab ───
private val REPO_PLUGINS = listOf(
    Triple("SmartSpacer Weather",    "Live weather from Open-Meteo API",                 "plugin_weather"),
    Triple("Now Playing (Pixel)",    "Detect music like Pixel phones using ML",           "plugin_nowplaying"),
    Triple("Fenix Twitter",          "Latest tweet from Fenix for Twitter",               "plugin_twitter"),
    Triple("Gmail Counter",          "Unread Gmail complication",                         "plugin_gmail"),
    Triple("Reddit Widget",          "Top posts from subreddit of your choice",           "plugin_reddit"),
    Triple("Sleep Tracker",          "Sleep data from connected wear OS device",          "plugin_sleep"),
    Triple("Habit Tracker",          "Daily habit reminders",                             "plugin_habit"),
    Triple("AQI (Air Quality)",      "Real-time air quality index for your location",     "plugin_aqi"),
    Triple("Stock Ticker",           "Live stock prices and sparkline chart",             "plugin_stock"),
    Triple("Home Assistant",         "Entity state from Home Assistant",                 "plugin_hass"),
)

@Composable
private fun RepositoryTab(viewModel: SmartSpacerViewModel, padding: PaddingValues) {
    var installed by remember { mutableStateOf(setOf<String>()) }
    var repoUrl by remember { mutableStateOf("https://github.com/KieronQuinn/SmartSpacerPlugins") }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Plugin Repository", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Install additional Smartspace targets and complications from community repos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = repoUrl, onValueChange = { repoUrl = it }, label = { Text("Repository URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { IconButton(onClick = { viewModel.fetchRepository(repoUrl) }) { Icon(Icons.Default.Refresh, "Reload") } })
                    Button(onClick = { viewModel.fetchRepository(repoUrl) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.CloudDownload, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Fetch Repository") }
                }
            }
        }
        item { Text("Available Plugins", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        items(REPO_PLUGINS) { (name, desc, id) ->
            val isInstalled = id in installed
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), shape = RoundedCornerShape(10.dp)) {
                ListItem(
                    headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(desc, style = MaterialTheme.typography.bodySmall) },
                    leadingContent = {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Extension, null, Modifier.size(20.dp)) }
                        }
                    },
                    trailingContent = {
                        if (isInstalled) {
                            TextButton(onClick = { installed = installed - id }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
                        } else {
                            Button(onClick = { installed = installed + id }) { Text("Install") }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

// ─── Settings Tab ───
@Composable
private fun SmartSpacerSettingsTab(state: SmartSpacerState, viewModel: SmartSpacerViewModel, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        item {
            Text("Integration Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            SmartSpacerMode.entries.forEach { mode ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = if (state.mode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    onClick = { viewModel.setMode(mode) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.mode == mode, onClick = { viewModel.setMode(mode) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(when(mode) { SmartSpacerMode.NONE -> "Disabled"; SmartSpacerMode.ENHANCED -> "Enhanced Mode (Shizuku/Root)"; SmartSpacerMode.NATIVE -> "Native Smartspace (OEM Replacement)" }, fontWeight = FontWeight.Medium)
                            Text(when(mode) {
                                SmartSpacerMode.NONE     -> "No Smartspace modification"
                                SmartSpacerMode.ENHANCED -> "Best features. Requires Shizuku or root. Recommended."
                                SmartSpacerMode.NATIVE   -> "Replaces stock At-a-Glance widget completely"
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            Text("UI/UX", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    Triple("Notification Widget", "Show on lockscreen as notification panel", state.showNotificationWidget) to viewModel::toggleNotificationWidget,
                    Triple("Expanded Mode", "Grid of widgets below main Smartspace", state.expandedMode) to viewModel::toggleExpandedMode,
                    Triple("Hide Sensitive Content", "Blur private data on lockscreen", state.hideSensitiveContent) to viewModel::toggleHideSensitive,
                ).forEach { (info, action) ->
                    val (title, desc, value) = info
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        ListItem(
                            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(desc, style = MaterialTheme.typography.bodySmall) },
                            trailingContent = { Switch(checked = value, onCheckedChange = { action() }) },
                            modifier = Modifier.clickable { action() },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                }
            }
        }

        item {
            val context = LocalContext.current
            Text("Backup & Restore", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val configText = "SmartSpacer Config Export\nMode: ${state.mode}\nNotification Widget: ${state.showNotificationWidget}\nExpanded Mode: ${state.expandedMode}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "SmartSpacer Config"); putExtra(Intent.EXTRA_TEXT, configText) }, "Export Config"))
                }, Modifier.weight(1f)) { Icon(Icons.Default.IosShare, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export Config") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                }, Modifier.weight(1f)) { Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Import Config") }
            }
        }

        item {
            val context = LocalContext.current
            Text("Debug", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val dump = buildString {
                    appendLine("=== SmartSpacer State Dump ===")
                    appendLine("Mode: ${state.mode}")
                    appendLine("Notification Widget: ${state.showNotificationWidget}")
                    appendLine("Expanded Mode: ${state.expandedMode}")
                    appendLine("Hide Sensitive: ${state.hideSensitiveContent}")
                    appendLine("Active targets: ${state.plugins.count { it.isEnabled }}")
                    appendLine("Complications: ${state.plugins.count { it.isEnabled }}")
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, dump) }, "Share Debug Dump"))
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.BugReport, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Dump SmartSpacer State") }
        }
    }
}
