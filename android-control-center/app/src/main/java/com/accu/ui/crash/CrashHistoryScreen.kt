package com.accu.ui.crash

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.data.db.entities.CrashEntity
import com.accu.ui.components.ACCTopBar
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashHistoryScreen(
    onBack: () -> Unit,
    onCrashDetail: (String) -> Unit,
    viewModel: CrashHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var showSortSheet by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.clearSnackbar() }
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete All Crash Logs?") },
            text = { Text("Permanently deletes all ${state.crashes.size} records.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteAll(); showDeleteAllConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()) {
                Text("Sort & Filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                Text("Sort by", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                CrashSortOrder.entries.forEach { order ->
                    ListItem(
                        headlineContent = { Text(order.label()) },
                        leadingContent = {
                            if (state.sortOrder == order) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            else Spacer(Modifier.size(24.dp))
                        },
                        modifier = Modifier.clickable { viewModel.setSortOrder(order); showSortSheet = false },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Filter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                CrashFilter.entries.forEach { filter ->
                    ListItem(
                        headlineContent = { Text(filter.label()) },
                        leadingContent = {
                            if (state.filter == filter) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            else Spacer(Modifier.size(24.dp))
                        },
                        modifier = Modifier.clickable { viewModel.setFilter(filter); showSortSheet = false },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            if (state.isMultiSelect) {
                MultiSelectTopBar(
                    selectedCount = state.selectedIds.size,
                    total = state.crashes.size,
                    onSelectAll = viewModel::selectAll,
                    onClearSelection = viewModel::clearSelection,
                    onDeleteSelected = viewModel::deleteSelected,
                    onExportSelected = {
                        viewModel.exportSelected { intent ->
                            context.startActivity(Intent.createChooser(intent, "Export Selected Crashes"))
                        }
                    },
                )
            } else {
                ACCTopBar(
                    title = "Crash History",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showSortSheet = true }) { Icon(Icons.Default.FilterList, "Filter") }
                        IconButton(onClick = { showDeleteAllConfirm = true }) { Icon(Icons.Default.DeleteSweep, "Delete All") }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search crashes, exceptions, routes…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            // Filter chips
            FilterChipsRow(state.filter) { viewModel.setFilter(it) }

            // List
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.crashes.isEmpty() -> EmptyState(state.query)
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.crashes, key = { it.id }) { crash ->
                        CrashListItem(
                            crash = crash,
                            isSelected = crash.id in state.selectedIds,
                            isMultiSelectMode = state.isMultiSelect,
                            onClick = {
                                if (state.isMultiSelect) viewModel.toggleSelection(crash.id)
                                else onCrashDetail(crash.crashId)
                            },
                            onLongClick = { viewModel.toggleSelection(crash.id) },
                            onFavorite = { viewModel.toggleFavorite(crash) },
                            onPin = { viewModel.togglePin(crash) },
                            onDelete = { viewModel.delete(crash.crashId) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectTopBar(
    selectedCount: Int,
    total: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit,
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = { IconButton(onClick = onClearSelection) { Icon(Icons.Default.Close, null) } },
        actions = {
            TextButton(onClick = onSelectAll) { Text("All $total") }
            IconButton(onClick = onExportSelected) { Icon(Icons.Default.FileUpload, "Export") }
            IconButton(onClick = onDeleteSelected) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun FilterChipsRow(current: CrashFilter, onSelect: (CrashFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        items(CrashFilter.entries) { filter ->
            FilterChip(
                selected = current == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label(), fontSize = 12.sp) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CrashListItem(
    crash: CrashEntity,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }
    var showMenu by remember { mutableStateOf(false) }
    val riskColor = crash.riskLevel.riskColor()

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (crash.isPinned) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.5f)) else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isMultiSelectMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else {
                Surface(shape = CircleShape, color = riskColor.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.BugReport, null, tint = riskColor, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (crash.isPinned) Icon(Icons.Default.PushPin, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    if (crash.isFavorited) Icon(Icons.Default.Favorite, null, Modifier.size(12.dp), tint = Color(0xFFFF6B6B))
                    Text(crash.exceptionType.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text(crash.exceptionMessage.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniChip(crash.crashKind, Color(0xFF6B9FFF))
                    if (crash.isFatal) MiniChip("FATAL", Color(0xFFFF4444))
                    if (crash.isAnr) MiniChip("ANR", Color(0xFFFF8800))
                    if (crash.affectedModule.isNotBlank()) MiniChip(crash.affectedModule.take(16), MaterialTheme.colorScheme.secondary)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = RoundedCornerShape(4.dp), color = riskColor.copy(alpha = 0.15f)) {
                    Text(crash.riskLevel, style = MaterialTheme.typography.labelSmall, color = riskColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(dateFormat.format(Date(crash.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(if (crash.isFavorited) "Unfavorite" else "Favorite") },
                            leadingIcon = { Icon(if (crash.isFavorited) Icons.Default.FavoriteBorder else Icons.Default.Favorite, null) },
                            onClick = { onFavorite(); showMenu = false })
                        DropdownMenuItem(text = { Text(if (crash.isPinned) "Unpin" else "Pin to top") },
                            leadingIcon = { Icon(Icons.Default.PushPin, null) },
                            onClick = { onPin(); showMenu = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { onDelete(); showMenu = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(3.dp), color = color.copy(alpha = 0.12f)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

@Composable
private fun EmptyState(query: String) {
    Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (query.isNotBlank()) Icons.Default.SearchOff else Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(0.5f))
        Spacer(Modifier.height(16.dp))
        Text(if (query.isNotBlank()) "No crashes match \"$query\"" else "No crash records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(if (query.isNotBlank()) "Try a different search term" else "ACCU is running cleanly", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun CrashFilter.label(): String = when (this) {
    CrashFilter.ALL -> "All"; CrashFilter.FATAL -> "Fatal"; CrashFilter.NON_FATAL -> "Non-Fatal"
    CrashFilter.ANR -> "ANR"; CrashFilter.CRITICAL -> "Critical"; CrashFilter.FAVORITES -> "Favorites"
}

private fun CrashSortOrder.label(): String = when (this) {
    CrashSortOrder.NEWEST -> "Newest first"; CrashSortOrder.OLDEST -> "Oldest first"
    CrashSortOrder.RISK_HIGH -> "Risk: High → Low"; CrashSortOrder.RISK_LOW -> "Risk: Low → High"
}

private fun String.riskColor(): Color = when (this) {
    "CRITICAL" -> Color(0xFFFF4444); "HIGH" -> Color(0xFFFF8800)
    "MEDIUM" -> Color(0xFFFFD700); else -> Color(0xFF81C995)
}
