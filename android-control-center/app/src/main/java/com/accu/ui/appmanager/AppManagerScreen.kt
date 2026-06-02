package com.accu.ui.appmanager

import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.ui.components.*
import com.accu.ui.components.InfoTooltipIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToDebloat: () -> Unit,
    onNavigateToFreeze: () -> Unit,
    onNavigateToComponents: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToAppExplorer: () -> Unit = {},
    onNavigateToInureHome: () -> Unit = {},
    onNavigateToBlockerSearch: () -> Unit = {},
    viewModel: AppManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("App Manager", fontWeight = FontWeight.Bold)
                            InfoTooltipIcon(
                                title = "App Manager",
                                description = "Unified app manager combining features from:\n\n• Inure — deep app inspection (permissions, components, certificates, trackers)\n• Canta — safe system app removal\n• Hail — freeze/hide/suspend apps\n• Blocker — disable trackers, receivers and services\n\nTap any app to open its full detail view. Long-press to enter multi-select mode for batch operations."
                            )
                        }
                    },
                    actions = {
                        if (state.isMultiSelect) {
                            IconButton(onClick = viewModel::batchFreeze) { Icon(Icons.Default.AcUnit, "Batch Freeze") }
                            IconButton(onClick = viewModel::batchUninstall) { Icon(Icons.Default.Delete, "Batch Remove") }
                        }
                        IconButton(onClick = { viewModel.toggleMultiSelect() }) {
                            Icon(if (state.isMultiSelect) Icons.Default.CheckCircle else Icons.Default.SelectAll, "Select")
                        }
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                            DropdownMenu(showSortMenu, { showSortMenu = false }) {
                                SortOrder.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = { viewModel.onSortChange(sort); showSortMenu = false },
                                        leadingIcon = { if (state.sortOrder == sort) Icon(Icons.Default.Check, null) },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                    },
                )
                // Quick action bar
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { AssistChip(onClick = onNavigateToAppExplorer, label = { Text("Explorer") }, leadingIcon = { Icon(Icons.Default.Explore, null, Modifier.size(16.dp)) }) }
                    item { AssistChip(onClick = onNavigateToDebloat, label = { Text("Debloat") }, leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) }) }
                    item { AssistChip(onClick = onNavigateToFreeze, label = { Text("Freeze") }, leadingIcon = { Icon(Icons.Default.AcUnit, null, Modifier.size(16.dp)) }) }
                    item { AssistChip(onClick = onNavigateToComponents, label = { Text("Components") }, leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) }) }
                    item { AssistChip(onClick = onNavigateToPermissions, label = { Text("Permissions") }, leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(16.dp)) }) }
                    item { AssistChip(onClick = onNavigateToInureHome, label = { Text("Inure") }, leadingIcon = { Icon(Icons.Default.Analytics, null, Modifier.size(16.dp)) }) }
                    item { AssistChip(onClick = onNavigateToBlockerSearch, label = { Text("Block Search") }, leadingIcon = { Icon(Icons.Default.Block, null, Modifier.size(16.dp)) }) }
                }
                // Search
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::onSearchChange,
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Search apps…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {}
                // Tabs
                TabRow(selectedTabIndex = AppTab.entries.indexOf(state.selectedTab)) {
                    AppTab.entries.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.onTabChange(tab) },
                            text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            LoadingScreen("Loading apps…")
        } else if (state.filteredApps.isEmpty()) {
            EmptyState(Icons.Default.Apps, "No apps found", "Try a different search or filter")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.filteredApps, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        isSelected = app.packageName in state.selectedApps,
                        isMultiSelect = state.isMultiSelect,
                        onClick = {
                            if (state.isMultiSelect) viewModel.toggleAppSelection(app.packageName)
                            else onNavigateToDetail(app.packageName)
                        },
                        onLongClick = { viewModel.toggleMultiSelect(); viewModel.toggleAppSelection(app.packageName) },
                        onFreeze = { viewModel.freezeApp(app.packageName) },
                        onUnfreeze = { viewModel.unfreezeApp(app.packageName) },
                        onHide = { viewModel.hideApp(app.packageName) },
                        onForceStop = { viewModel.forceStop(app.packageName) },
                        onClearData = { viewModel.clearData(app.packageName) },
                        onExtractApk = { viewModel.extractApk(app.packageName) },
                        onUninstall = { viewModel.uninstallForUser(app.packageName) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    app: AppUiModel,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onHide: () -> Unit,
    onForceStop: () -> Unit,
    onClearData: () -> Unit,
    onExtractApk: () -> Unit,
    onUninstall: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isMultiSelect) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(8.dp))
            }
            // App icon
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null })
                    .crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (app.isFrozen) Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("Frozen", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                    if (!app.isEnabled) Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) { Text("Disabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                }
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "v${app.versionName} · ${if (app.apkSize > 0) "${"%.1f".format(app.apkSize / 1_000_000f)} MB" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(showMenu, { showMenu = false }) {
                    DropdownMenuItem(Icons.Default.Info, "App Info") { showMenu = false; onClick() }
                    DropdownMenuItem(Icons.Default.AcUnit, if (app.isFrozen) "Unfreeze" else "Freeze") {
                        showMenu = false; if (app.isFrozen) onUnfreeze() else onFreeze()
                    }
                    DropdownMenuItem(Icons.Default.VisibilityOff, "Hide") { showMenu = false; onHide() }
                    DropdownMenuItem(Icons.Default.Stop, "Force Stop") { showMenu = false; onForceStop() }
                    DropdownMenuItem(Icons.Default.Delete, "Clear Data") { showMenu = false; onClearData() }
                    DropdownMenuItem(Icons.Default.Download, "Extract APK") { showMenu = false; onExtractApk() }
                    DropdownMenuItem(Icons.Default.RemoveCircle, "Remove for User") { showMenu = false; onUninstall() }
                }
            }
        }
    }
}

@Composable
private fun DropdownMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick,
    )
}
