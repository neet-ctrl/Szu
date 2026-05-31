package com.accu.ui.appmanager

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.data.db.entities.FrozenAppEntity
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange

// ── Freeze enums ─────────────────────────────────────────────────────────────

enum class FreezeFilter { ALL, FROZEN, UNFROZEN, USER, SYSTEM }
enum class FreezeSortOrder { NAME, INSTALL_TIME, UPDATE_TIME }
enum class FreezeMethodType(val label: String, val description: String, val command: String) {
    DISABLE("Disable", "pm disable — app hidden but data kept, reversible", "pm disable-user --user 0"),
    SUSPEND("Suspend", "am suspend — greyed-out icon in launcher, fastest method", "pm suspend --user 0"),
    HIDE("Hide", "pm hide — fully invisible, like soft-uninstall without data loss", "pm hide --user 0"),
}

// ── Tags model ────────────────────────────────────────────────────────────────

data class FreezeTag(val id: Int, val name: String, val color: Long = 0xFF4FC3F7)

// ── QS Tile action ────────────────────────────────────────────────────────────

enum class QsTileAction(val label: String, val description: String) {
    FREEZE_ALL("Freeze All", "Freeze all checked apps when tile is tapped"),
    UNFREEZE_ALL("Unfreeze All", "Unfreeze all apps when tile is tapped"),
    FREEZE_NON_WHITELISTED("Freeze Non-Whitelisted", "Freeze all apps not marked as whitelisted"),
    LOCK("Lock Screen", "Lock device when tile is tapped"),
    LOCK_AND_FREEZE("Lock + Freeze All", "Freeze all apps then lock device"),
    AUTO_FREEZE_TOGGLE("Toggle Auto-Freeze", "Toggle auto-freeze-on-screen-off mode"),
}

// ── Search mode ───────────────────────────────────────────────────────────────

enum class SearchMode { NORMAL, FUZZY, T9 }

// ── Auto-freeze settings ──────────────────────────────────────────────────────

data class AutoFreezeSettings(
    val enabled: Boolean = false,
    val delaySeconds: Int = 0,
    val skipWhileCharging: Boolean = false,
    val skipForegroundApp: Boolean = false,
    val skipNotifyingApp: Boolean = false,
)

// ── App settings ──────────────────────────────────────────────────────────────

data class FreezeAppSettings(
    val grayscaleFrozenIcons: Boolean = true,
    val biometricLock: Boolean = false,
    val qsTileAction: QsTileAction = QsTileAction.FREEZE_ALL,
    val searchMode: SearchMode = SearchMode.NORMAL,
    val autoFreeze: AutoFreezeSettings = AutoFreezeSettings(),
    val tags: List<FreezeTag> = listOf(
        FreezeTag(0, "Default", 0xFF4FC3F7),
        FreezeTag(1, "Google", 0xFF81C784),
        FreezeTag(2, "Samsung", 0xFF64B5F6),
        FreezeTag(3, "Background", 0xFFFFB74D),
        FreezeTag(4, "Carrier", 0xFFBA68C8),
    ),
    val appTagMap: Map<String, Int> = emptyMap(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezeAppsScreen(
    onBack: () -> Unit,
    onNavigateToScheduler: () -> Unit = {},
    viewModel: AppManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf(FreezeFilter.ALL) }
    var sortOrder by remember { mutableStateOf(FreezeSortOrder.NAME) }
    var selectedMethod by remember { mutableStateOf(FreezeMethodType.DISABLE) }
    var showMethodPicker by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMethodInfo by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // ── Hail extra settings ──────────────────────────────────────────────────
    var appSettings by remember { mutableStateOf(FreezeAppSettings()) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showTagsManager by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var selectedTagFilter by remember { mutableStateOf<Int?>(null) }
    var showApkExtractMenu by remember { mutableStateOf<String?>(null) }
    var pendingBiometricAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showQsTileMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    // ── Biometric helper ─────────────────────────────────────────────────────
    fun runWithBiometric(action: () -> Unit) {
        if (!appSettings.biometricLock) { action(); return }
        val activity = context as? FragmentActivity ?: run { action(); return }
        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    action()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ACCU Freeze")
            .setSubtitle("Authenticate to perform freeze action")
            .setNegativeButtonText("Cancel")
            .build()
        biometricPrompt.authenticate(info)
    }

    // ── T9 / fuzzy search helper ─────────────────────────────────────────────
    val t9Map = remember {
        mapOf('a' to '2','b' to '2','c' to '2','d' to '3','e' to '3','f' to '3',
              'g' to '4','h' to '4','i' to '4','j' to '5','k' to '5','l' to '5',
              'm' to '6','n' to '6','o' to '6','p' to '7','q' to '7','r' to '7','s' to '7',
              't' to '8','u' to '8','v' to '8','w' to '9','x' to '9','y' to '9','z' to '9')
    }
    fun toT9(s: String) = s.lowercase().map { t9Map[it] ?: it }.joinToString("")
    fun matchesSearch(appName: String, pkg: String): Boolean {
        if (searchQuery.isBlank()) return true
        return when (appSettings.searchMode) {
            SearchMode.NORMAL -> appName.contains(searchQuery, true) || pkg.contains(searchQuery, true)
            SearchMode.FUZZY  -> {
                val q = searchQuery.lowercase()
                val name = appName.lowercase()
                var qi = 0
                for (ch in name) { if (qi < q.length && ch == q[qi]) qi++ }
                qi == q.length
            }
            SearchMode.T9     -> toT9(appName).contains(searchQuery) || toT9(pkg).contains(searchQuery)
        }
    }

    val displayApps = remember(state.apps, state.frozenApps, selectedFilter, sortOrder, searchQuery, appSettings, selectedTagFilter) {
        val frozenPkgs = state.frozenApps.map { it.packageName }.toSet()
        var list = when (selectedFilter) {
            FreezeFilter.ALL      -> state.apps
            FreezeFilter.FROZEN   -> state.apps.filter { it.packageName in frozenPkgs }
            FreezeFilter.UNFROZEN -> state.apps.filter { it.packageName !in frozenPkgs }
            FreezeFilter.USER     -> state.apps.filter { !it.isSystemApp }
            FreezeFilter.SYSTEM   -> state.apps.filter { it.isSystemApp }
        }
        if (selectedTagFilter != null) {
            list = list.filter { app -> appSettings.appTagMap[app.packageName] == selectedTagFilter }
        }
        list = list.filter { matchesSearch(it.appName, it.packageName) }
        when (sortOrder) {
            FreezeSortOrder.NAME         -> list.sortedBy { it.appName }
            FreezeSortOrder.INSTALL_TIME -> list.sortedByDescending { it.installTime }
            FreezeSortOrder.UPDATE_TIME  -> list.sortedByDescending { it.lastUpdateTime }
        }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Freeze Apps",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Freeze Apps — Hail",
                        description = "Freeze, suspend, or hide apps without uninstalling them.\n\nBased on Hail — three freeze methods available:\n\n• Disable (pm disable): app hidden from launcher, data kept\n• Suspend (pm suspend): greyed-out icon visible but can't be opened\n• Hide (pm hide): fully invisible, all data preserved\n\nAll methods use Shizuku (no root). Reversible at any time.\n\nFeatures: tags, auto-freeze on screen-off, biometric lock, fuzzy/T9 search, QS tile action, grayscale frozen icons."
                    )
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            FreezeSortOrder.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(when(sort) { FreezeSortOrder.NAME -> "Name"; FreezeSortOrder.INSTALL_TIME -> "Install Date"; FreezeSortOrder.UPDATE_TIME -> "Update Date" }) },
                                    leadingIcon = { if (sortOrder == sort) Icon(Icons.Default.Check, null) },
                                    onClick = { sortOrder = sort; showSortMenu = false },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showQsTileMenu = true }) { Icon(Icons.Default.ViewDay, "QS Tile Action") }
                        DropdownMenu(showQsTileMenu, { showQsTileMenu = false }) {
                            Text("  QS Tile Action", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            QsTileAction.entries.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = { if (appSettings.qsTileAction == action) Icon(Icons.Default.Check, null) },
                                    onClick = { appSettings = appSettings.copy(qsTileAction = action); showQsTileMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onNavigateToScheduler) { Icon(Icons.Default.Schedule, "Freeze Scheduler") }
                    IconButton(onClick = { showSettingsPanel = true }) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { showMethodInfo = true }) { Icon(Icons.Default.Info, "Method Info") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    runWithBiometric {
                        val toFreeze = displayApps.filter { app -> state.frozenApps.none { it.packageName == app.packageName } }
                        toFreeze.forEach { viewModel.freezeApp(it.packageName) }
                    }
                },
                icon = { Icon(Icons.Default.AcUnit, null) },
                text = { Text("Freeze All Visible") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Method selector
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                onClick = { showMethodPicker = true },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AcUnit, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Method: ${selectedMethod.label}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(selectedMethod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                }
            }

            // Stats row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val frozenCount = state.frozenApps.size
                StatFreezeCard("Total", "${state.apps.size}", Modifier.weight(1f))
                StatFreezeCard("Frozen", "$frozenCount", Modifier.weight(1f), AccentCyan)
                StatFreezeCard("Unfrozen", "${state.apps.size - frozenCount}", Modifier.weight(1f), AccentGreen)
            }

            Spacer(Modifier.height(4.dp))

            // Auto-freeze status banner
            if (appSettings.autoFreeze.enabled) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = AccentCyan.copy(0.12f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ScreenLockPortrait, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Auto-Freeze Active", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = AccentCyan)
                            val delaySuffix = if (appSettings.autoFreeze.delaySeconds > 0) " after ${appSettings.autoFreeze.delaySeconds}s" else " immediately"
                            Text("Freezes on screen-off$delaySuffix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showSettingsPanel = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(when (appSettings.searchMode) {
                    SearchMode.NORMAL -> "Search apps…"
                    SearchMode.FUZZY  -> "Fuzzy search apps…"
                    SearchMode.T9     -> "T9 search (type digits)…"
                }) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    Box {
                        var showSearchMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showSearchMenu = true }) {
                            Icon(Icons.Default.FilterList, null, tint = if (appSettings.searchMode != SearchMode.NORMAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(showSearchMenu, { showSearchMenu = false }) {
                            Text("  Search Mode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            SearchMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(when(mode) { SearchMode.NORMAL -> "Normal"; SearchMode.FUZZY -> "Fuzzy"; SearchMode.T9 -> "T9 / Numpad" }) },
                                    leadingIcon = { if (appSettings.searchMode == mode) Icon(Icons.Default.Check, null) },
                                    onClick = { appSettings = appSettings.copy(searchMode = mode); showSearchMenu = false },
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(4.dp))

            // Filter chips + Tags
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FreezeFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(when(filter) {
                            FreezeFilter.ALL      -> "All"
                            FreezeFilter.FROZEN   -> "Frozen (${state.frozenApps.size})"
                            FreezeFilter.UNFROZEN -> "Unfrozen"
                            FreezeFilter.USER     -> "User Apps"
                            FreezeFilter.SYSTEM   -> "System Apps"
                        }) },
                        leadingIcon = if (selectedFilter == filter) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null,
                    )
                }
            }

            // Tag filter row
            if (appSettings.tags.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedTagFilter == null,
                            onClick = { selectedTagFilter = null },
                            label = { Text("All Tags") },
                            leadingIcon = { Icon(Icons.Default.LocalOffer, null, Modifier.size(14.dp)) },
                        )
                    }
                    items(appSettings.tags) { tag ->
                        val tagColor = androidx.compose.ui.graphics.Color(tag.color)
                        FilterChip(
                            selected = selectedTagFilter == tag.id,
                            onClick = { selectedTagFilter = if (selectedTagFilter == tag.id) null else tag.id },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = tagColor,
                                    modifier = Modifier.size(8.dp),
                                ) {}
                            },
                            colors = if (selectedTagFilter == tag.id) FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tagColor.copy(0.15f),
                                selectedLabelColor = tagColor,
                            ) else FilterChipDefaults.filterChipColors(),
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { showTagsManager = true },
                            label = { Text("Edit Tags") },
                            leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // List
            if (displayApps.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.AcUnit,
                    title = "No apps match filter",
                    subtitle = "Change the filter or search query to see apps",
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(displayApps, key = { it.packageName }) { app ->
                        val frozen = state.frozenApps.firstOrNull { it.packageName == app.packageName }
                        val appTag = appSettings.tags.firstOrNull { it.id == appSettings.appTagMap[app.packageName] }
                        FreezeAppItem(
                            app = app,
                            frozen = frozen,
                            grayscale = appSettings.grayscaleFrozenIcons,
                            appTag = appTag,
                            availableTags = appSettings.tags,
                            onFreeze = { runWithBiometric { viewModel.freezeApp(app.packageName) } },
                            onUnfreeze = { runWithBiometric { viewModel.unfreezeApp(app.packageName) } },
                            onHide = { runWithBiometric { viewModel.hideApp(app.packageName) } },
                            onTagSelect = { tagId ->
                                val newMap = appSettings.appTagMap.toMutableMap()
                                if (tagId == null) newMap.remove(app.packageName) else newMap[app.packageName] = tagId
                                appSettings = appSettings.copy(appTagMap = newMap)
                            },
                            onExtractApk = { showApkExtractMenu = app.packageName },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // ── Method picker dialog ─────────────────────────────────────────────────
    if (showMethodPicker) {
        AlertDialog(
            onDismissRequest = { showMethodPicker = false },
            title = { Text("Choose Freeze Method") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FreezeMethodType.entries.forEach { method ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedMethod == method) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { selectedMethod = method; showMethodPicker = false },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedMethod == method, onClick = { selectedMethod = method; showMethodPicker = false })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(method.label, fontWeight = FontWeight.Bold)
                                    Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text(method.command, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMethodPicker = false }) { Text("Cancel") } },
        )
    }

    // ── Method info dialog ───────────────────────────────────────────────────
    if (showMethodInfo) {
        AlertDialog(
            onDismissRequest = { showMethodInfo = false },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text("About Freeze Methods") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ACCU supports 3 freeze techniques from Hail:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    listOf(
                        Triple("Disable (pm disable)", AccentOrange, "Marks the app disabled in PackageManager. The app icon disappears from launcher. All background work stops. Fully reversible. Best for bloatware."),
                        Triple("Suspend (am suspend)", AccentCyan, "Suspends the app via ActivityManager. Icon stays but is greyed out. App cannot run. Fastest — no PackageManager write needed."),
                        Triple("Hide (pm hide)", AccentGreen, "Makes the app invisible to the system (hidden flag). No launcher icon, no background, but data is preserved. Like a soft-uninstall."),
                    ).forEach { (title, color, desc) ->
                        Card(colors = CardDefaults.cardColors(containerColor = color.copy(0.1f))) {
                            Column(Modifier.padding(12.dp)) {
                                Text(title, fontWeight = FontWeight.Bold, color = color)
                                Text(desc, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("All methods require Shizuku or root access and are fully reversible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { showMethodInfo = false }) { Text("Got it") } },
        )
    }

    // ── Tags manager bottom sheet ─────────────────────────────────────────────
    if (showTagsManager) {
        TagsManagerDialog(
            tags = appSettings.tags,
            onDismiss = { showTagsManager = false },
            onAddTag = { tag -> appSettings = appSettings.copy(tags = appSettings.tags + tag) },
            onDeleteTag = { tag ->
                val newTags = appSettings.tags.filter { it.id != tag.id }
                val newMap = appSettings.appTagMap.filter { (_, v) -> v != tag.id }
                appSettings = appSettings.copy(tags = newTags, appTagMap = newMap)
            },
        )
    }

    // ── Create tag dialog ─────────────────────────────────────────────────────
    if (showCreateTagDialog) {
        CreateTagDialog(
            existingIds = appSettings.tags.map { it.id }.toSet(),
            onDismiss = { showCreateTagDialog = false },
            onCreate = { tag ->
                appSettings = appSettings.copy(tags = appSettings.tags + tag)
                showCreateTagDialog = false
            },
        )
    }

    // ── Settings panel ────────────────────────────────────────────────────────
    if (showSettingsPanel) {
        FreezeSettingsPanel(
            settings = appSettings,
            onDismiss = { showSettingsPanel = false },
            onSettingsChange = { appSettings = it },
        )
    }

    // ── APK extraction dialog ─────────────────────────────────────────────────
    val extractPkg = showApkExtractMenu
    if (extractPkg != null) {
        AlertDialog(
            onDismissRequest = { showApkExtractMenu = null },
            icon = { Icon(Icons.Default.Android, null) },
            title = { Text("Extract APK") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extract base APK from $extractPkg to Downloads?", style = MaterialTheme.typography.bodyMedium)
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("/data/app/$extractPkg/base.apk → Downloads/", style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.extractApk(extractPkg)
                    showApkExtractMenu = null
                }) { Text("Extract") }
            },
            dismissButton = { TextButton(onClick = { showApkExtractMenu = null }) { Text("Cancel") } },
        )
    }
}

// ── Tags manager dialog ────────────────────────────────────────────────────────

@Composable
private fun TagsManagerDialog(
    tags: List<FreezeTag>,
    onDismiss: () -> Unit,
    onAddTag: (FreezeTag) -> Unit,
    onDeleteTag: (FreezeTag) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalOffer, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Manage Tags", modifier = Modifier.weight(1f))
                IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Add Tag") }
            }
        },
        text = {
            if (tags.isEmpty()) {
                Text("No tags yet. Add tags to organize your frozen apps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(tags) { tag ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = androidx.compose.ui.graphics.Color(tag.color), modifier = Modifier.size(12.dp)) {}
                            Spacer(Modifier.width(12.dp))
                            Text(tag.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onDeleteTag(tag) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    if (showCreate) {
        CreateTagDialog(
            existingIds = tags.map { it.id }.toSet(),
            onDismiss = { showCreate = false },
            onCreate = { tag -> onAddTag(tag); showCreate = false },
        )
    }
}

@Composable
private fun CreateTagDialog(
    existingIds: Set<Int>,
    onDismiss: () -> Unit,
    onCreate: (FreezeTag) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val tagColors = listOf(0xFF4FC3F7L, 0xFF81C784L, 0xFF64B5F6L, 0xFFFFB74DL, 0xFFBA68C8L, 0xFFEF9A9AL, 0xFFA5D6A7L, 0xFFFFCC80L)
    var selectedColor by remember { mutableStateOf(tagColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Tag Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Text("Color:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tagColors) { color ->
                        val selected = color == selectedColor
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = androidx.compose.ui.graphics.Color(color),
                            modifier = Modifier.size(if (selected) 36.dp else 28.dp).clickable { selectedColor = color },
                            border = if (selected) ButtonDefaults.outlinedButtonBorder else null,
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newId = (existingIds.maxOrNull() ?: -1) + 1
                    onCreate(FreezeTag(id = newId, name = name, color = selectedColor))
                },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Freeze settings panel ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreezeSettingsPanel(
    settings: FreezeAppSettings,
    onDismiss: () -> Unit,
    onSettingsChange: (FreezeAppSettings) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Freeze Settings")
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {

                // ── Display ──────────────────────────────────────────────────
                item {
                    Text("Display", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 6.dp))
                    HorizontalDivider()
                }
                item {
                    ListItem(
                        headlineContent = { Text("Grayscale Frozen Icons") },
                        supportingContent = { Text("Show frozen app icons in grayscale") },
                        leadingContent = { Icon(Icons.Default.InvertColors, null) },
                        trailingContent = {
                            Switch(
                                checked = settings.grayscaleFrozenIcons,
                                onCheckedChange = { onSettingsChange(settings.copy(grayscaleFrozenIcons = it)) },
                            )
                        },
                    )
                }
                item { HorizontalDivider() }

                // ── Security ─────────────────────────────────────────────────
                item {
                    Text("Security", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    HorizontalDivider()
                }
                item {
                    ListItem(
                        headlineContent = { Text("Biometric Lock") },
                        supportingContent = { Text("Require fingerprint/face before freeze actions") },
                        leadingContent = { Icon(Icons.Default.Fingerprint, null) },
                        trailingContent = {
                            Switch(
                                checked = settings.biometricLock,
                                onCheckedChange = { onSettingsChange(settings.copy(biometricLock = it)) },
                            )
                        },
                    )
                }
                item { HorizontalDivider() }

                // ── Auto-freeze ───────────────────────────────────────────────
                item {
                    Text("Auto-Freeze on Screen Off", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    HorizontalDivider()
                }
                item {
                    ListItem(
                        headlineContent = { Text("Enable Auto-Freeze") },
                        supportingContent = { Text("Freeze checked apps when screen turns off") },
                        leadingContent = { Icon(Icons.Default.ScreenLockPortrait, null) },
                        trailingContent = {
                            Switch(
                                checked = settings.autoFreeze.enabled,
                                onCheckedChange = { onSettingsChange(settings.copy(autoFreeze = settings.autoFreeze.copy(enabled = it))) },
                            )
                        },
                    )
                }
                if (settings.autoFreeze.enabled) {
                    item { HorizontalDivider() }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("Delay: ${settings.autoFreeze.delaySeconds}s", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = settings.autoFreeze.delaySeconds.toFloat(),
                                onValueChange = { onSettingsChange(settings.copy(autoFreeze = settings.autoFreeze.copy(delaySeconds = it.toInt()))) },
                                valueRange = 0f..60f, steps = 11,
                            )
                        }
                    }
                    item { HorizontalDivider() }
                    item {
                        ListItem(
                            headlineContent = { Text("Skip While Charging") },
                            supportingContent = { Text("Don't auto-freeze when device is charging") },
                            leadingContent = { Icon(Icons.Default.BatteryChargingFull, null) },
                            trailingContent = {
                                Switch(
                                    checked = settings.autoFreeze.skipWhileCharging,
                                    onCheckedChange = { onSettingsChange(settings.copy(autoFreeze = settings.autoFreeze.copy(skipWhileCharging = it))) },
                                )
                            },
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        ListItem(
                            headlineContent = { Text("Skip Foreground App") },
                            supportingContent = { Text("Don't freeze the app currently in foreground") },
                            leadingContent = { Icon(Icons.Default.Layers, null) },
                            trailingContent = {
                                Switch(
                                    checked = settings.autoFreeze.skipForegroundApp,
                                    onCheckedChange = { onSettingsChange(settings.copy(autoFreeze = settings.autoFreeze.copy(skipForegroundApp = it))) },
                                )
                            },
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        ListItem(
                            headlineContent = { Text("Skip Apps with Notifications") },
                            supportingContent = { Text("Don't freeze apps showing active notifications") },
                            leadingContent = { Icon(Icons.Default.NotificationsActive, null) },
                            trailingContent = {
                                Switch(
                                    checked = settings.autoFreeze.skipNotifyingApp,
                                    onCheckedChange = { onSettingsChange(settings.copy(autoFreeze = settings.autoFreeze.copy(skipNotifyingApp = it))) },
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

// ── Per-app item ───────────────────────────────────────────────────────────────

@Composable
private fun FreezeAppItem(
    app: AppUiModel,
    frozen: FrozenAppEntity?,
    grayscale: Boolean,
    appTag: FreezeTag?,
    availableTags: List<FreezeTag>,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onHide: () -> Unit,
    onTagSelect: (Int?) -> Unit,
    onExtractApk: () -> Unit,
) {
    val context = LocalContext.current
    val isFrozen = frozen != null
    var showMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (isFrozen) {
                    Spacer(Modifier.width(4.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = AccentCyan.copy(0.15f)) {
                        Text(
                            frozen?.freezeMethod ?: "Frozen",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (appTag != null) {
                    Spacer(Modifier.width(4.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = androidx.compose.ui.graphics.Color(appTag.color).copy(0.18f)) {
                        Text(
                            appTag.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color(appTag.color),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                if (app.isSystemApp) {
                    Spacer(Modifier.width(4.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("System", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
        },
        supportingContent = {
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box {
                // Grayscale if frozen + setting enabled
                val imageModel = ImageRequest.Builder(context)
                    .data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null })
                    .crossfade(true).build()
                if (isFrozen && grayscale) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix().also { it.setToSaturation(0f) }
                        ),
                    )
                } else {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
                if (isFrozen) {
                    Icon(
                        Icons.Default.AcUnit, null,
                        modifier = Modifier.size(18.dp).align(Alignment.BottomEnd),
                        tint = AccentCyan,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFrozen) {
                    FilledTonalIconButton(onClick = onUnfreeze, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.PlayArrow, "Unfreeze", Modifier.size(18.dp), tint = AccentGreen)
                    }
                } else {
                    FilledTonalIconButton(onClick = onFreeze, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.AcUnit, "Freeze", Modifier.size(18.dp), tint = AccentCyan)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, "More", Modifier.size(18.dp))
                    }
                    DropdownMenu(showMenu, { showMenu = false }) {
                        if (!isFrozen) {
                            DropdownMenuItem(text = { Text("Freeze (Disable)") }, leadingIcon = { Icon(Icons.Default.AcUnit, null) }, onClick = { onFreeze(); showMenu = false })
                            DropdownMenuItem(text = { Text("Hide App") }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) }, onClick = { onHide(); showMenu = false })
                        } else {
                            DropdownMenuItem(text = { Text("Unfreeze") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { onUnfreeze(); showMenu = false })
                        }
                        DropdownMenuItem(text = { Text("Assign Tag") }, leadingIcon = { Icon(Icons.Default.LocalOffer, null) }, onClick = { showTagMenu = true; showMenu = false })
                        DropdownMenuItem(text = { Text("Extract APK") }, leadingIcon = { Icon(Icons.Default.Android, null) }, onClick = { onExtractApk(); showMenu = false })
                    }
                    DropdownMenu(showTagMenu, { showTagMenu = false }) {
                        Text("  Assign Tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        DropdownMenuItem(text = { Text("No Tag") }, leadingIcon = { if (appTag == null) Icon(Icons.Default.Check, null) }, onClick = { onTagSelect(null); showTagMenu = false })
                        availableTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag.name) },
                                leadingIcon = {
                                    if (appTag?.id == tag.id) Icon(Icons.Default.Check, null)
                                    else Surface(shape = androidx.compose.foundation.shape.CircleShape, color = androidx.compose.ui.graphics.Color(tag.color), modifier = Modifier.size(10.dp)) {}
                                },
                                onClick = { onTagSelect(tag.id); showTagMenu = false },
                            )
                        }
                    }
                }
            }
        },
        colors = if (isFrozen) ListItemDefaults.colors(containerColor = AccentCyan.copy(0.04f)) else ListItemDefaults.colors(),
    )
}

@Composable
private fun StatFreezeCard(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
