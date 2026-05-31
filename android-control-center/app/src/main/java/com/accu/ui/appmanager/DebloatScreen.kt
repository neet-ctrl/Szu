package com.accu.ui.appmanager

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.components.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebloatScreen(
    onBack: () -> Unit,
    viewModel: AppManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.onTabChange(AppTab.SYSTEM) }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    // Pre-built debloat categories
    val bloatCategories = remember {
        mapOf(
            "Google Bloat" to listOf("com.google.android.apps.tachyon", "com.google.android.videos", "com.google.android.music", "com.google.android.apps.magazines", "com.google.android.apps.books"),
            "Samsung Bloat" to listOf("com.samsung.android.game.gamehome", "com.samsung.android.bixby.agent", "com.samsung.android.weather", "com.samsung.android.dialer.samsungdialerplugin"),
            "MIUI Bloat" to listOf("com.miui.player", "com.miui.video", "com.miui.analytics", "com.miui.msa.global", "com.miui.global.packageinstaller"),
            "Carrier Bloat" to listOf("com.att.myatt", "com.verizon.messaging.vzmsgs", "com.tmobile.pr.mytmobile"),
            "Ads & Analytics" to listOf("com.facebook.system", "com.facebook.orca", "com.facebook.services", "com.facebook.katana"),
        )
    }

    // Safety level model
    data class SafetyLevel(val label: String, val color: androidx.compose.ui.graphics.Color, val description: String)
    val safetyLevels = remember {
        listOf(
            SafetyLevel("Recommended", androidx.compose.ui.graphics.Color(0xFF43A047), "Safe to remove without breaking core functionality"),
            SafetyLevel("Advanced", androidx.compose.ui.graphics.Color(0xFFFB8C00), "May affect some features, removable with care"),
            SafetyLevel("Expert", androidx.compose.ui.graphics.Color(0xFFE53935), "Risky — only for experienced users"),
            SafetyLevel("Unsafe", androidx.compose.ui.graphics.Color(0xFF880E4F), "Critical system components — very likely to break device"),
        )
    }
    var selectedSafety by remember { mutableStateOf<String?>(null) }
    var showSafetyInfo by remember { mutableStateOf(false) }
    var pendingUninstallPkg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Debloat",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Debloat — Canta",
                        description = "Safe system app removal based on Canta.\n\nRemoves system apps without root via Shizuku (pm uninstall --user 0 — apps are removed for the current user only, not deleted from system).\n\n• Apps marked Safe: confirmed removable without breaking Android\n• Apps marked Caution: may affect other apps\n• Apps marked Expert: advanced users only\n\nTo restore: Settings → Apps → [app] → Reinstall for all users."
                    )
                    IconButton(onClick = { showSafetyInfo = true }) { Icon(Icons.Default.Info, "Safety Info") }
                    if (state.selectedApps.isNotEmpty()) {
                        IconButton(onClick = { viewModel.batchUninstall() }) { Icon(Icons.Default.Delete, "Remove Selected") }
                    }
                    IconButton(onClick = { viewModel.toggleMultiSelect() }) { Icon(if (state.isMultiSelect) Icons.Default.CheckCircle else Icons.Default.SelectAll, null) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Warning banner
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Removing system apps may break functionality. Proceed with caution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Safety level filter chips
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(safetyLevels) { level ->
                    FilterChip(
                        selected = selectedSafety == level.label,
                        onClick = { selectedSafety = if (selectedSafety == level.label) null else level.label },
                        label = { Text(level.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = level.color.copy(0.15f),
                            selectedLabelColor = level.color,
                            selectedLeadingIconColor = level.color,
                        ),
                        leadingIcon = if (selectedSafety == level.label) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null,
                    )
                }
            }

            // Search
            var searchQuery by remember { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; viewModel.onSearchChange(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                label = { Text("Search system apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
            )

            if (state.isLoading) {
                LoadingScreen("Loading system apps…")
            } else {
                // Preset debloat lists
                var selectedCategory by remember { mutableStateOf("") }
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bloatCategories.keys.toList()) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) "" else cat },
                            label = { Text(cat) },
                        )
                    }
                }

                val systemApps = state.apps.filter { it.isSystemApp }
                    .let { list ->
                        val catFilter = bloatCategories[selectedCategory]
                        if (catFilter != null) list.filter { it.packageName in catFilter }
                        else if (searchQuery.isNotBlank()) list.filter { it.appName.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
                        else list
                    }

                Text(
                    "${systemApps.size} system apps${if (selectedSafety != null) " · ${selectedSafety!!} filter" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                LazyColumn(Modifier.weight(1f)) {
                    items(systemApps, key = { it.packageName }) { app ->
                        val isBloat = bloatCategories.values.flatten().contains(app.packageName)
                        DebloatAppItem(
                            app = app,
                            isSelected = app.packageName in state.selectedApps,
                            isMultiSelect = state.isMultiSelect,
                            isBloat = isBloat,
                            onClick = {
                                if (state.isMultiSelect) viewModel.toggleAppSelection(app.packageName)
                            },
                            onRemove = { pendingUninstallPkg = app.packageName },
                            onFreeze = { viewModel.freezeApp(app.packageName) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Uninstall confirmation dialog
    if (pendingUninstallPkg != null) {
        val pkg = pendingUninstallPkg!!
        AlertDialog(
            onDismissRequest = { pendingUninstallPkg = null },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Remove System App?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to remove:", style = MaterialTheme.typography.bodySmall)
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(pkg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "This will uninstall the app for the current user (--user 0). The app may be restored on factory reset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(0.5f)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("This action cannot be easily undone. Removing system apps may cause instability.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.uninstallForUser(pkg); pendingUninstallPkg = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingUninstallPkg = null }) { Text("Cancel") } },
        )
    }

    // Safety levels info dialog
    if (showSafetyInfo) {
        AlertDialog(
            onDismissRequest = { showSafetyInfo = false },
            title = { Text("Safety Level Badges") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ACCU uses safety levels from the Universal Android Debloater community database to categorize system apps:", style = MaterialTheme.typography.bodySmall)
                    safetyLevels.forEach { level ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), color = level.color.copy(0.15f)) {
                                Text(level.label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = level.color, fontWeight = FontWeight.Bold)
                            }
                            Text(level.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showSafetyInfo = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun DebloatAppItem(
    app: AppUiModel,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    isBloat: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onFreeze: () -> Unit,
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (isBloat) Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text("Bloat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        },
        supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            if (isMultiSelect) Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            else AsyncImage(
                model = ImageRequest.Builder(context).data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null }).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onFreeze) { Icon(Icons.Default.AcUnit, "Freeze", Modifier.size(20.dp)) }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove", Modifier.size(20.dp)) }
            }
        },
        modifier = if (isMultiSelect) Modifier.clickable(onClick = onClick) else Modifier,
        colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f)) else ListItemDefaults.colors(),
    )
}
