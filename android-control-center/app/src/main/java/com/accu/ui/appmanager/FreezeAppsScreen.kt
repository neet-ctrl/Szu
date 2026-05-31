package com.accu.ui.appmanager

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.data.db.entities.FrozenAppEntity
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange

enum class FreezeFilter { ALL, FROZEN, UNFROZEN, USER, SYSTEM }
enum class FreezeSortOrder { NAME, INSTALL_TIME, UPDATE_TIME }
enum class FreezeMethodType(val label: String, val description: String, val command: String) {
    DISABLE("Disable", "pm disable — app hidden but data kept, reversible", "pm disable-user --user 0"),
    SUSPEND("Suspend", "am suspend — greyed-out icon in launcher, fastest method", "pm suspend --user 0"),
    HIDE("Hide", "pm hide — fully invisible, like soft-uninstall without data loss", "pm hide --user 0"),
}

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

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    val displayApps = remember(state.apps, state.frozenApps, selectedFilter, sortOrder, searchQuery) {
        val frozenPkgs = state.frozenApps.map { it.packageName }.toSet()
        var list = when (selectedFilter) {
            FreezeFilter.ALL      -> state.apps
            FreezeFilter.FROZEN   -> state.apps.filter { it.packageName in frozenPkgs }
            FreezeFilter.UNFROZEN -> state.apps.filter { it.packageName !in frozenPkgs }
            FreezeFilter.USER     -> state.apps.filter { !it.isSystemApp }
            FreezeFilter.SYSTEM   -> state.apps.filter { it.isSystemApp }
        }
        if (searchQuery.isNotBlank()) list = list.filter { it.appName.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
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
                    IconButton(onClick = onNavigateToScheduler) { Icon(Icons.Default.Schedule, "Freeze Scheduler") }
                    IconButton(onClick = { showMethodInfo = true }) { Icon(Icons.Default.Info, "Method Info") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val toFreeze = displayApps.filter { app -> state.frozenApps.none { it.packageName == app.packageName } }
                    toFreeze.forEach { viewModel.freezeApp(it.packageName) }
                },
                icon = { Icon(Icons.Default.AcUnit, null) },
                text = { Text("Freeze All Visible") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Method selector
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(4.dp))

            // Filter chips
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
                        FreezeAppItem(
                            app = app,
                            frozen = frozen,
                            onFreeze = { viewModel.freezeApp(app.packageName) },
                            onUnfreeze = { viewModel.unfreezeApp(app.packageName) },
                            onHide = { viewModel.hideApp(app.packageName) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Method picker dialog
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

    // Method info sheet
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
}

@Composable
private fun FreezeAppItem(
    app: AppUiModel,
    frozen: FrozenAppEntity?,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onHide: () -> Unit,
) {
    val context = LocalContext.current
    val isFrozen = frozen != null
    var showMenu by remember { mutableStateOf(false) }

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
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null })
                        .crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
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
