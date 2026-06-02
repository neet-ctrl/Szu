package com.accu.ui.appmanager

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.data.db.entities.BlockedComponentEntity
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.privacy.PrivacyViewModel
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed

// ──────────────────────────────────────────────
//  Blocker — Component Manager (IFW + PM)
// ──────────────────────────────────────────────

enum class BlockMethod(val label: String, val description: String) {
    IFW("IFW (Intent Firewall)", "Block at system level via intent firewall rules. No PackageManager change. Most compatible."),
    PACKAGE_MANAGER("Package Manager", "Disable via pm disable-user. Component appears disabled to all callers."),
    AUTO("Auto (Recommended)", "Use IFW for activities/receivers/services, PM for providers."),
}

enum class BlockerImportFormat(val label: String) {
    BLOCKER("Blocker Format (.json)"),
    MAT("MyAndroidTools Format (.txt)"),
    IFW_XML("IFW XML (.xml)"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentManagerScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedType by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(BlockMethod.AUTO) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var cloudUrl by remember { mutableStateOf("") }
    var showSortSheet by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("name") }      // "name" | "package"
    var sortAsc by remember { mutableStateOf(true) }
    var priorityMode by remember { mutableStateOf("none") } // "none" | "disabled_first" | "enabled_first"
    val selectedComponents = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    val types = listOf("all", "activity", "service", "receiver", "provider")

    val filtered = remember(state.blockedComponents, selectedType, searchQuery, sortBy, sortAsc, priorityMode) {
        state.blockedComponents
            .let { if (selectedType == "all") it else it.filter { c -> c.componentType == selectedType } }
            .filter { c -> searchQuery.isEmpty() || c.componentName.contains(searchQuery, true) || c.packageName.contains(searchQuery, true) }
            .let { list ->
                val sorted = when (sortBy) {
                    "package" -> if (sortAsc) list.sortedBy { it.packageName } else list.sortedByDescending { it.packageName }
                    else      -> if (sortAsc) list.sortedBy { it.componentName.substringAfterLast('.') } else list.sortedByDescending { it.componentName.substringAfterLast('.') }
                }
                when (priorityMode) {
                    "disabled_first", "enabled_first" -> sorted
                    else                              -> sorted
                }
            }
    }

    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() } }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Component Manager",
                onBack = onBack,
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val allKeys = filtered.map { "${it.packageName}/${it.componentName}" }
                            if (selectedComponents.containsAll(allKeys)) selectedComponents.clear()
                            else { selectedComponents.clear(); selectedComponents.addAll(allKeys) }
                        }) { Icon(Icons.Default.SelectAll, "Select All") }
                        IconButton(onClick = { selectedComponents.forEach { key ->
                            val comp = filtered.firstOrNull { "${it.packageName}/${it.componentName}" == key }
                            comp?.let { viewModel.enableComponent(it.packageName, it.componentName) }
                        }; selectedComponents.clear(); isSelectionMode = false }) { Icon(Icons.Default.PlayArrow, "Enable All Selected", tint = androidx.compose.ui.graphics.Color(0xFF43A047)) }
                        IconButton(onClick = { selectedComponents.clear(); isSelectionMode = false }) { Icon(Icons.Default.Close, "Cancel Selection") }
                    } else {
                        IconButton(onClick = { showSortSheet = true }) { Icon(Icons.Default.Sort, "Sort") }
                        IconButton(onClick = { isSelectionMode = true }) { Icon(Icons.Default.Checklist, "Select") }
                        IconButton(onClick = { showImportDialog = true }) { Icon(Icons.Default.FileOpen, "Import Rules") }
                        IconButton(onClick = { viewModel.exportRules() }) { Icon(Icons.Default.IosShare, "Export Rules") }
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(showMenu, { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Cloud Rules…") }, leadingIcon = { Icon(Icons.Default.CloudDownload, null) }, onClick = { showCloudDialog = true; showMenu = false })
                            DropdownMenuItem(text = { Text("Backup All Rules") }, leadingIcon = { Icon(Icons.Default.Backup, null) }, onClick = { viewModel.backupRules(); showMenu = false })
                            DropdownMenuItem(text = { Text("Restore Rules") }, leadingIcon = { Icon(Icons.Default.Restore, null) }, onClick = { viewModel.restoreRules(); showMenu = false })
                            DropdownMenuItem(text = { Text("Clear All Blocks") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { viewModel.clearAllRules(); showMenu = false })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Stats row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val activities = state.blockedComponents.count { it.componentType == "activity" }
                val services = state.blockedComponents.count { it.componentType == "service" }
                val receivers = state.blockedComponents.count { it.componentType == "receiver" }
                val trackers = state.blockedComponents.count { it.isTracker }
                StatCard("Blocked", "${state.blockedComponents.size}", Modifier.weight(1f))
                StatCard("Trackers", "$trackers", Modifier.weight(1f), AccentRed)
                StatCard("Apps", "${state.blockedComponents.map { it.packageName }.distinct().size}", Modifier.weight(1f), AccentGreen)
            }

            // Method selector card
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { showMethodDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Block Method: ${selectedMethod.label}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(selectedMethod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Search
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search components or packages…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(4.dp))

            // Type filter tabs
            ScrollableTabRow(selectedTabIndex = types.indexOf(selectedType), edgePadding = 16.dp) {
                types.forEach { type ->
                    val count = when(type) {
                        "all"      -> state.blockedComponents.size
                        else -> state.blockedComponents.count { it.componentType == type }
                    }
                    Tab(selected = selectedType == type, onClick = { selectedType = type }, text = { Text("${type.replaceFirstChar { it.uppercase() }} ($count)", fontSize = 11.sp) })
                }
            }

            // List
            if (isSelectionMode && selectedComponents.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedComponents.size} selected", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            selectedComponents.forEach { key ->
                                val comp = filtered.firstOrNull { "${it.packageName}/${it.componentName}" == key }
                                comp?.let { viewModel.enableComponent(it.packageName, it.componentName) }
                            }; selectedComponents.clear()
                        }) { Text("Enable All", color = androidx.compose.ui.graphics.Color(0xFF43A047)) }
                        TextButton(onClick = { selectedComponents.clear() }) { Text("Clear") }
                    }
                }
            }
            if (filtered.isEmpty()) {
                EmptyState(Icons.Default.CheckCircle, if (state.blockedComponents.isEmpty()) "No blocked components" else "No components match filter",
                    if (state.blockedComponents.isEmpty()) "Block app components from the App Detail screen" else "Try a different filter or search")
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(filtered, key = { idx, it -> "${idx}_${it.packageName}/${it.componentName}" }) { _, comp ->
                        val key = "${comp.packageName}/${comp.componentName}"
                        val isSelected = key in selectedComponents
                        BlockedComponentItem(
                            comp = comp,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) selectedComponents.remove(key) else selectedComponents.add(key)
                            },
                            onEnable = { viewModel.enableComponent(comp.packageName, comp.componentName) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Block method dialog
    if (showMethodDialog) {
        AlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text("Component Block Method") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockMethod.entries.forEach { method ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedMethod == method) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().clickable { selectedMethod = method; showMethodDialog = false },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedMethod == method, onClick = { selectedMethod = method; showMethodDialog = false })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(method.label, fontWeight = FontWeight.Bold)
                                    Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("IFW rules survive app updates. PM changes revert when app reinstalls. For best results, use Auto.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showMethodDialog = false }) { Text("Done") } },
        )
    }

    // Import dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = { Icon(Icons.Default.FileOpen, null) },
            title = { Text("Import Block Rules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose the format of the rules file to import:", style = MaterialTheme.typography.bodyMedium)
                    BlockerImportFormat.entries.forEach { fmt ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.importRules(fmt.name); showImportDialog = false },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(fmt.label, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showImportDialog = false }) { Text("Cancel") } },
        )
    }

    // Sort bottom sheet
    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Sort Components", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Sort by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sortBy == "name", onClick = { sortBy = "name" }, label = { Text("Component Name") })
                    FilterChip(selected = sortBy == "package", onClick = { sortBy = "package" }, label = { Text("Package Name") })
                }
                Text("Order", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sortAsc, onClick = { sortAsc = true }, label = { Text("Ascending") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(14.dp)) })
                    FilterChip(selected = !sortAsc, onClick = { sortAsc = false }, label = { Text("Descending") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null, Modifier.size(14.dp)) })
                }
                Text("Priority", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = priorityMode == "none", onClick = { priorityMode = "none" }, label = { Text("None") })
                    FilterChip(selected = priorityMode == "disabled_first", onClick = { priorityMode = "disabled_first" }, label = { Text("Blocked first") })
                    FilterChip(selected = priorityMode == "enabled_first", onClick = { priorityMode = "enabled_first" }, label = { Text("Enabled first") })
                }
                Button(onClick = { showSortSheet = false }, Modifier.fillMaxWidth()) { Text("Apply") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Cloud rules dialog
    if (showCloudDialog) {
        AlertDialog(
            onDismissRequest = { showCloudDialog = false },
            icon = { Icon(Icons.Default.CloudDownload, null) },
            title = { Text("Sync Cloud Rules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sync component blocking rules from a GitHub/GitLab repository URL.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = cloudUrl, onValueChange = { cloudUrl = it }, label = { Text("Repository or File URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Common sources:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    listOf(
                        "TrackerControl Blocklist" to "https://raw.githubusercontent.com/TrackerControl/tracker-control-android/main/tc-ifw.json",
                        "Datura Firewall Rules" to "https://gitlab.com/CalyxOS/device_google_bonito_overlay/-/raw/main/ifw/",
                    ).forEach { (name, url) ->
                        TextButton(onClick = { cloudUrl = url }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(name, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            },
            confirmButton = { Button(onClick = { viewModel.syncCloudRules(cloudUrl); showCloudDialog = false }) { Text("Sync Now") } },
            dismissButton = { TextButton(onClick = { showCloudDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BlockedComponentItem(
    comp: BlockedComponentEntity,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEnable: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = if (isSelectionMode) Modifier.clickable { onToggleSelect() } else Modifier,
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() }, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(comp.componentName.substringAfterLast('.'), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (comp.isTracker) {
                        Spacer(Modifier.width(4.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(0.15f)) {
                            Text("Tracker", style = MaterialTheme.typography.labelSmall, color = AccentRed, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
            },
            supportingContent = {
                Column {
                    Text(comp.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(comp.componentName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            leadingContent = {
                val (icon, color) = when(comp.componentType) {
                    "activity" -> Icons.Outlined.Smartphone  to MaterialTheme.colorScheme.primary
                    "service"  -> Icons.Outlined.Sync        to AccentOrange
                    "receiver" -> Icons.Outlined.Notifications to AccentGreen
                    "provider" -> Icons.Outlined.Storage     to MaterialTheme.colorScheme.secondary
                    else       -> Icons.Outlined.Block       to AccentRed
                }
                Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.12f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = color) }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(comp.componentType.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onEnable, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PlayArrow, "Unblock", Modifier.size(16.dp), tint = AccentGreen)
                    }
                    IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(comp.componentName)) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.ContentCopy, "Copy full name", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                    }
                }
            },
        )
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Full class: ${comp.componentName}", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Package: ${comp.packageName}", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Type: ${comp.componentType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (comp.isTracker) {
                    Text("⚠ Known tracker component — flagged by tracker database", style = MaterialTheme.typography.bodySmall, color = AccentRed)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
