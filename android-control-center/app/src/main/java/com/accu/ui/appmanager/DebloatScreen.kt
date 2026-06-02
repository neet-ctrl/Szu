package com.accu.ui.appmanager

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebloatScreen(
    onBack: () -> Unit,
    viewModel: DebloatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialogs
    var showSafetyInfo by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<Pair<DebloatAppModel, DebloatAction>?>(null) }
    var showBatchConfirm by remember { mutableStateOf<String?>(null) } // action label
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Debloat",
                onBack = onBack,
                actions = {
                    // Sort button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            Text("Sort by", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            DebloatSortOrder.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(s)) },
                                    leadingIcon = {
                                        if (state.sortOrder == s)
                                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                        else Spacer(Modifier.size(16.dp))
                                    },
                                    onClick = { viewModel.onSortChange(s); showSortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showSafetyInfo = true }) {
                        Icon(Icons.Default.Info, "Safety Info")
                    }
                    // Multi-select toggle
                    IconButton(onClick = { viewModel.toggleMultiSelect() }) {
                        Icon(
                            if (state.isMultiSelect) Icons.Default.CheckCircle else Icons.Default.SelectAll,
                            "Multi-select",
                        )
                    }
                    // Refresh
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Batch FAB when items selected
            if (state.selectedApps.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.selectedTab == DebloatTab.REMOVED) {
                        ExtendedFloatingActionButton(
                            onClick = { showBatchConfirm = "restore" },
                            icon = { Icon(Icons.Default.RestoreFromTrash, null) },
                            text = { Text("Restore ${state.selectedApps.size}") },
                            containerColor = MaterialTheme.colorScheme.secondary,
                        )
                    } else {
                        SmallFloatingActionButton(
                            onClick = { showBatchConfirm = "disable" },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ) { Icon(Icons.Default.AcUnit, "Freeze Selected") }
                        ExtendedFloatingActionButton(
                            onClick = { showBatchConfirm = "remove" },
                            icon = { Icon(Icons.Default.DeleteSweep, null) },
                            text = { Text("Remove ${state.selectedApps.size}") },
                            containerColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Warning banner ────────────────────────────────────────────────
            Surface(color = MaterialTheme.colorScheme.errorContainer.copy(0.7f)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Removing system apps is irreversible until factory reset. Read safety ratings carefully.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // ── Tab row ───────────────────────────────────────────────────────
            val tabs = DebloatTab.entries
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                tabs.forEach { tab ->
                    val count = when (tab) {
                        DebloatTab.SYSTEM  -> state.apps.count { it.isSystemApp }
                        DebloatTab.USER    -> state.apps.count { !it.isSystemApp }
                        DebloatTab.REMOVED -> state.removedApps.size
                    }
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.onTabChange(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(tabLabel(tab))
                                if (count > 0) {
                                    Surface(shape = CircleShape, color = if (state.selectedTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                        Text("$count", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (state.selectedTab == tab) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        },
                        icon = { Icon(tabIcon(tab), null, Modifier.size(18.dp)) },
                    )
                }
            }

            // ── Safety filter chips ───────────────────────────────────────────
            val safetyLevels = listOf(
                Triple("Recommended", Color(0xFF43A047), Icons.Default.CheckCircle),
                Triple("Advanced", Color(0xFFFB8C00), Icons.Default.Warning),
                Triple("Expert", Color(0xFFE53935), Icons.Default.Dangerous),
                Triple("Unsafe", Color(0xFF6A1B9A), Icons.Default.Block),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text(
                        "Safety:",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp, end = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(safetyLevels) { (label, color, icon) ->
                    val selected = label in state.selectedSafety
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleSafety(label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(icon, null, Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(0.15f),
                            selectedLabelColor = color,
                            selectedLeadingIconColor = color,
                        ),
                    )
                }
            }

            // ── Label preset chips ────────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text(
                        "Filter:",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp, end = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(viewModel.allLabels) { label ->
                    val selected = state.selectedLabel == label
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectLabel(label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
                // Select preset batch
                if (state.selectedLabel != null && state.selectedTab != DebloatTab.REMOVED) {
                    item {
                        AssistChip(
                            onClick = { viewModel.selectPreset(state.selectedLabel!!) },
                            label = { Text("Select all ${state.selectedLabel}", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.SelectAll, null, Modifier.size(14.dp)) },
                        )
                    }
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchChange(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search name, package, description…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) IconButton(onClick = { viewModel.onSearchChange("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    },
                    singleLine = true,
                )
                if (state.selectedSafety.isNotEmpty() || state.selectedLabel != null || state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.clearFilters() }) {
                        Icon(Icons.Default.FilterListOff, "Clear filters")
                    }
                }
            }

            // ── Stats row + multi-select toolbar ──────────────────────────────
            AnimatedVisibility(state.isMultiSelect && state.filteredApps.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(0.5f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.selectedApps.size} selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.selectAll() }) { Text("All") }
                        TextButton(onClick = { viewModel.clearSelection() }) { Text("None") }
                    }
                }
            }

            // Stats header
            val listLabel = when (state.selectedTab) {
                DebloatTab.SYSTEM  -> "system"
                DebloatTab.USER    -> "user"
                DebloatTab.REMOVED -> "removed"
            }
            val knownCount = state.filteredApps.count { it.uadData != null }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${state.filteredApps.size} $listLabel apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (knownCount > 0) {
                    Text(
                        "$knownCount in UAD database",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ── Batch progress ────────────────────────────────────────────────
            AnimatedVisibility(state.isBatchRunning) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Processing…", style = MaterialTheme.typography.labelSmall)
                        Text("${state.batchProgress}/${state.batchTotal}", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (state.batchTotal > 0) state.batchProgress.toFloat() / state.batchTotal else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── App list ──────────────────────────────────────────────────────
            if (state.isLoading || (state.selectedTab == DebloatTab.REMOVED && state.isLoadingRemoved)) {
                LoadingScreen("Loading packages…")
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.filteredApps, key = { it.packageName }) { app ->
                        DebloatAppCard(
                            app = app,
                            isSelected = app.packageName in state.selectedApps,
                            isMultiSelect = state.isMultiSelect,
                            onCardClick = {
                                if (state.isMultiSelect) viewModel.toggleSelection(app.packageName)
                                else viewModel.showDetail(app)
                            },
                            onLongClick = {
                                if (!state.isMultiSelect) {
                                    viewModel.toggleMultiSelect()
                                    viewModel.toggleSelection(app.packageName)
                                }
                            },
                            onQuickRemove = {
                                pendingAction = app to DebloatAction.REMOVE_USER
                            },
                            onQuickRestore = {
                                pendingAction = app to DebloatAction.RESTORE
                            },
                            isRemoved = state.selectedTab == DebloatTab.REMOVED,
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }

    // ── Detail bottom sheet ───────────────────────────────────────────────────
    state.detailApp?.let { app ->
        ModalBottomSheet(onDismissRequest = { viewModel.showDetail(null) }) {
            DebloatDetailSheet(
                app = app,
                onAction = { action ->
                    when (action) {
                        DebloatAction.REMOVE_USER      -> pendingAction = app to action
                        DebloatAction.REMOVE_ALL_USERS -> pendingAction = app to action
                        DebloatAction.DISABLE          -> { viewModel.disableApp(app.packageName) }
                        DebloatAction.SUSPEND          -> { viewModel.suspendApp(app.packageName) }
                        DebloatAction.HIDE             -> { viewModel.hideApp(app.packageName) }
                        DebloatAction.RESTORE          -> { viewModel.restoreApp(app.packageName) }
                    }
                },
                onDeepFreeze  = { viewModel.deepFreeze(app.packageName) },
                onForceStop   = { viewModel.forceStop(app.packageName) },
                onClearData   = { viewModel.clearData(app.packageName) },
                onEnableApp   = { viewModel.enableApp(app.packageName) },
                onUnsuspend   = { viewModel.unsuspendApp(app.packageName) },
                onDismiss     = { viewModel.showDetail(null) },
            )
        }
    }

    // ── Confirm action dialog ─────────────────────────────────────────────────
    pendingAction?.let { (app, action) ->
        val (title, body, confirmLabel, confirmColor) = when (action) {
            DebloatAction.REMOVE_USER -> Quad(
                "Remove for Current User?",
                "This runs:\npm uninstall --user 0 ${app.packageName}\n\nThe app is removed for user 0 only. Restore is possible via the Removed tab.",
                "Remove", MaterialTheme.colorScheme.error,
            )
            DebloatAction.REMOVE_ALL_USERS -> Quad(
                "Remove for ALL Users? (Root)",
                "This runs:\npm uninstall ${app.packageName}\n\nRequires root. This completely removes the app from the system. May not be restorable without flashing.",
                "Force Remove", MaterialTheme.colorScheme.error,
            )
            DebloatAction.RESTORE -> Quad(
                "Restore App?",
                "This runs:\ncmd package install-existing --user 0 ${app.packageName}\n\nRestores the app for the current user.",
                "Restore", MaterialTheme.colorScheme.primary,
            )
            else -> null
        } ?: run { pendingAction = null; return@let }

        AlertDialog(
            onDismissRequest = { pendingAction = null },
            icon = { Icon(if (action == DebloatAction.RESTORE) Icons.Default.RestoreFromTrash else Icons.Default.Delete, null) },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // App header
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SafetyBadge(app.safetyLabel, Color(app.safetyColor))
                        Column {
                            Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // Command preview
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(body, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    // UAD warning
                    if (app.safetyLabel == "Expert" || app.safetyLabel == "Unsafe") {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(0.6f)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Text(
                                    "UAD rates this app as ${app.safetyLabel}. Removing it may cause system instability.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (app.uadDependencies.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Depends on:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                app.uadDependencies.forEach { dep ->
                                    Text("• $dep", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    if (app.uadNeededBy.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Needed by:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                app.uadNeededBy.forEach { dep ->
                                    Text("• $dep", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            DebloatAction.REMOVE_USER      -> viewModel.removeForUser(app.packageName)
                            DebloatAction.REMOVE_ALL_USERS -> viewModel.removeAllUsers(app.packageName)
                            DebloatAction.RESTORE          -> viewModel.restoreApp(app.packageName)
                            else -> {}
                        }
                        pendingAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = confirmColor),
                ) { Text(confirmLabel) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } },
        )
    }

    // ── Batch confirm dialog ──────────────────────────────────────────────────
    showBatchConfirm?.let { action ->
        val count = state.selectedApps.size
        AlertDialog(
            onDismissRequest = { showBatchConfirm = null },
            icon = { Icon(when (action) { "restore" -> Icons.Default.RestoreFromTrash; "disable" -> Icons.Default.AcUnit; else -> Icons.Default.DeleteSweep }, null) },
            title = { Text(when (action) { "restore" -> "Restore $count Apps?"; "disable" -> "Freeze $count Apps?"; else -> "Remove $count Apps?" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when (action) {
                        "restore" -> "Runs cmd package install-existing --user 0 for each selected app."
                        "disable" -> "Runs pm disable-user --user 0 for each selected app. Apps can be re-enabled later."
                        else ->      "Runs pm uninstall --user 0 for each selected app. This is reversible via the Removed tab."
                    }, style = MaterialTheme.typography.bodySmall)
                    if (action == "remove" || action == "disable") {
                        val expertCount = state.selectedApps.count { pkg ->
                            val app = state.filteredApps.find { it.packageName == pkg }
                            app?.safetyLabel == "Expert" || app?.safetyLabel == "Unsafe"
                        }
                        if (expertCount > 0) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(0.5f)) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    Text("$expertCount of the selected apps are rated Expert or Unsafe!", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            "restore" -> viewModel.batchRestoreSelected()
                            "disable" -> viewModel.batchDisable()
                            else      -> viewModel.batchRemoveForUser()
                        }
                        showBatchConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == "restore") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    ),
                ) { Text(when (action) { "restore" -> "Restore All"; "disable" -> "Freeze All"; else -> "Remove All" }) }
            },
            dismissButton = { TextButton(onClick = { showBatchConfirm = null }) { Text("Cancel") } },
        )
    }

    // ── Safety info dialog ────────────────────────────────────────────────────
    if (showSafetyInfo) {
        AlertDialog(
            onDismissRequest = { showSafetyInfo = false },
            title = { Text("Safety Levels (UAD)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "ACCU uses safety ratings from the Universal Android Debloater community database to categorize apps.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    listOf(
                        Triple("Recommended", Color(0xFF43A047), "Safe to remove. No impact on device functionality."),
                        Triple("Advanced", Color(0xFFFB8C00), "May affect non-core features. Remove with care."),
                        Triple("Expert", Color(0xFFE53935), "High risk. Only experienced users should remove this."),
                        Triple("Unsafe", Color(0xFF6A1B9A), "Critical system component. Do NOT remove."),
                        Triple("Unknown", Color(0xFF757575), "Not in UAD database. Research before removing."),
                    ).forEach { (label, color, desc) ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SafetyBadge(label, color)
                            Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider()
                    Text(
                        "All removals use:\npm uninstall --user 0 <package>\n\nThis removes the app for the current user only (user 0). The app can be restored via the Removed tab.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { Button(onClick = { showSafetyInfo = false }) { Text("Got it") } },
        )
    }
}

// ── Detail bottom sheet ───────────────────────────────────────────────────────

@Composable
private fun DebloatDetailSheet(
    app: DebloatAppModel,
    onAction: (DebloatAction) -> Unit,
    onDeepFreeze: () -> Unit,
    onForceStop: () -> Unit,
    onClearData: () -> Unit,
    onEnableApp: () -> Unit,
    onUnsuspend: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SafetyBadge(app.safetyLabel, Color(app.safetyColor), large = true)
            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(12.dp))

        // App state chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!app.isEnabled) SuggestionChip(
                onClick = {},
                label = { Text("Disabled", style = MaterialTheme.typography.labelSmall) },
                icon = { Icon(Icons.Default.AcUnit, null, Modifier.size(14.dp)) },
            )
            if (app.isFrozen) SuggestionChip(
                onClick = {},
                label = { Text("Frozen", style = MaterialTheme.typography.labelSmall) },
                icon = { Icon(Icons.Default.AcUnit, null, Modifier.size(14.dp)) },
            )
            if (app.isSystemApp) SuggestionChip(
                onClick = {},
                label = { Text("System", style = MaterialTheme.typography.labelSmall) },
                icon = { Icon(Icons.Default.Security, null, Modifier.size(14.dp)) },
            )
            if (app.isRemoved) SuggestionChip(
                onClick = {},
                label = { Text("Removed", style = MaterialTheme.typography.labelSmall) },
                icon = { Icon(Icons.Default.DeleteSweep, null, Modifier.size(14.dp)) },
            )
        }

        // UAD labels
        if (app.uadLabels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(app.uadLabels) { label ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        // UAD description
        if (app.uadDescription.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.7f)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("UAD Description", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(app.uadDescription, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Help, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Not in UAD database. Research this package before removing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Dependencies
        if (app.uadDependencies.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            UadRelationCard("Depends on", app.uadDependencies, MaterialTheme.colorScheme.tertiaryContainer)
        }
        if (app.uadNeededBy.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            UadRelationCard("Needed by", app.uadNeededBy, MaterialTheme.colorScheme.secondaryContainer)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // Primary actions
        Text("Actions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (app.isRemoved) {
            // Restore
            Button(
                onClick = { onAction(DebloatAction.RESTORE); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Icon(Icons.Default.RestoreFromTrash, null)
                Spacer(Modifier.width(8.dp))
                Text("Restore for Current User")
            }
        } else {
            // Remove for user
            Button(
                onClick = { onAction(DebloatAction.REMOVE_USER) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Remove for Current User")
            }
            Spacer(Modifier.height(6.dp))

            // Disable / Enable
            if (app.isEnabled) {
                OutlinedButton(
                    onClick = { onAction(DebloatAction.DISABLE); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(Icons.Default.AcUnit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Disable (Freeze)")
                }
            } else {
                OutlinedButton(
                    onClick = { onEnableApp(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable App")
                }
            }
            Spacer(Modifier.height(6.dp))

            // More actions row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Suspend
                OutlinedButton(
                    onClick = { onAction(DebloatAction.SUSPEND); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Suspend", style = MaterialTheme.typography.labelSmall)
                }
                // Hide
                OutlinedButton(
                    onClick = { onAction(DebloatAction.HIDE); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Hide", style = MaterialTheme.typography.labelSmall)
                }
                // Deep Freeze
                OutlinedButton(
                    onClick = { onDeepFreeze(); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Deep Freeze", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))

            // Force Stop + Clear Data row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onForceStop(); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Force Stop", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onClearData(); onDismiss() },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.5f)),
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear Data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(6.dp))

            // Remove all users (root)
            TextButton(
                onClick = { onAction(DebloatAction.REMOVE_ALL_USERS) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove for ALL Users (Root Only)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── App card ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DebloatAppCard(
    app: DebloatAppModel,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onCardClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickRemove: () -> Unit,
    onQuickRestore: () -> Unit,
    isRemoved: Boolean,
) {
    val safetyColor = Color(app.safetyColor)
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (app.uadData != null) SafetyDot(safetyColor)
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    app.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (app.uadDescription.isNotBlank()) {
                    Text(
                        app.uadDescription,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f),
                    )
                }
                if (app.uadLabels.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                        app.uadLabels.take(3).forEach { label ->
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(0.6f)) {
                                Text(label, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
        },
        leadingContent = {
            if (isMultiSelect) {
                Checkbox(checked = isSelected, onCheckedChange = { onCardClick() })
            } else {
                // Safety color left border indicator + initials avatar
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (app.uadData != null) safetyColor.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            app.appName.take(2).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (app.uadData != null) safetyColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!app.isEnabled || app.isFrozen) {
                        Box(
                            Modifier.size(12.dp).align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color(0xFFFB8C00)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.AcUnit, null, Modifier.size(8.dp), tint = Color.White)
                        }
                    }
                }
            }
        },
        trailingContent = {
            if (!isMultiSelect) {
                if (isRemoved) {
                    IconButton(onClick = onQuickRestore) {
                        Icon(Icons.Default.RestoreFromTrash, "Restore", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    IconButton(onClick = onQuickRemove) {
                        Icon(Icons.Default.Delete, "Remove", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        modifier = Modifier
            .combinedClickable(onClick = onCardClick, onLongClick = onLongClick)
            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(0.3f)) else Modifier),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
private fun SafetyBadge(label: String, color: Color, large: Boolean = false) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.15f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = if (large) 10.dp else 6.dp, vertical = if (large) 4.dp else 2.dp),
            style = if (large) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SafetyDot(color: Color) {
    Box(
        modifier = Modifier.size(8.dp).clip(CircleShape).background(color),
    )
}

@Composable
private fun UadRelationCard(title: String, items: List<String>, bgColor: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = bgColor.copy(0.5f)) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            items.forEach { dep ->
                Text("• $dep", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ── Small util ────────────────────────────────────────────────────────────────

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun tabLabel(tab: DebloatTab) = when (tab) {
    DebloatTab.SYSTEM  -> "System"
    DebloatTab.USER    -> "User"
    DebloatTab.REMOVED -> "Removed"
}

private fun tabIcon(tab: DebloatTab): ImageVector = when (tab) {
    DebloatTab.SYSTEM  -> Icons.Default.Security
    DebloatTab.USER    -> Icons.Default.Person
    DebloatTab.REMOVED -> Icons.Default.RestoreFromTrash
}

private fun sortLabel(sort: DebloatSortOrder) = when (sort) {
    DebloatSortOrder.NAME_ASC  -> "Name A→Z"
    DebloatSortOrder.NAME_DESC -> "Name Z→A"
    DebloatSortOrder.SAFETY    -> "Safety Level"
    DebloatSortOrder.HAS_UAD   -> "UAD Known First"
}
