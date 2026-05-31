package com.accu.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.navigation.NavController
import com.accu.navigation.Screen
import com.accu.ui.components.StatusBadge
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = 96.dp)
        ) {
            // ── Header ──
            DashboardHeader(
                shizukuStatus = state.shizukuStatus,
                onSearchClick = { viewModel.toggleCommandPalette() },
                onShizukuClick = { navController.navigate(Screen.ShizukuCenter.route) },
            )

            // ── Stats Row ──
            QuickStatsRow(stats = state.quickStats)

            Spacer(Modifier.height(8.dp))

            // ── Module Grid ──
            Text(
                "Control Center",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ModuleGrid(
                cards = state.moduleCards,
                onCardClick = { card ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController.navigate(card.route)
                },
            )

            // ── Recent Actions ──
            if (state.recentActions.isNotEmpty()) {
                Text(
                    "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                RecentActionsList(actions = state.recentActions, onActionClick = { action ->
                    action.route?.let { navController.navigate(it) }
                })
            }
        }

        // ── Command Palette Overlay ──
        AnimatedVisibility(
            visible = state.showCommandPalette,
            enter = fadeIn() + slideInVertically { -it / 4 },
            exit = fadeOut() + slideOutVertically { -it / 4 },
            modifier = Modifier.fillMaxSize(),
        ) {
            CommandPaletteOverlay(
                query = state.searchQuery,
                results = state.searchResults,
                onQueryChange = viewModel::onSearchQueryChanged,
                onResultClick = { result ->
                    viewModel.dismissCommandPalette()
                    navController.navigate(result.route)
                },
                onDismiss = viewModel::dismissCommandPalette,
            )
        }

        // ── FAB ──
        FloatingActionButton(
            onClick = { viewModel.toggleCommandPalette() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
    }
}

@Composable
private fun DashboardHeader(
    shizukuStatus: ShizukuStatus,
    onSearchClick: () -> Unit,
    onShizukuClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "glow",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Android Control Center",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Ultimate",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Shizuku status badge
                StatusBadge(
                    status = shizukuStatus,
                    onClick = onShizukuClick,
                    glowAlpha = glowAlpha,
                )
            }
            Spacer(Modifier.height(12.dp))
            // Search bar tap target
            Surface(
                onClick = onSearchClick,
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Search features, settings, commands…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "⌘K",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatsRow(stats: QuickStats) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { StatChip(label = "User Apps", value = "${stats.installedApps}") }
        item { StatChip(label = "System Apps", value = "${stats.systemApps}") }
        item { StatChip(label = "Storage", value = "${"%.1f".format(stats.freeStorageGb)}GB free") }
        item { StatChip(label = "RAM", value = "${stats.ramUsedMb / 1024}/${stats.ramTotalMb / 1024}GB") }
        item { StatChip(label = "Cores", value = "${stats.cpuCores}") }
        item { StatChip(label = "Android", value = stats.androidVersion) }
        item { StatChip(label = "Commands", value = "${stats.savedCommandCount}") }
        item { StatChip(label = "Recordings", value = "${stats.recordingsCount}") }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleGrid(cards: List<ModuleCard>, onCardClick: (ModuleCard) -> Unit) {
    val columns = when {
        LocalConfiguration.current.screenWidthDp >= 840 -> 4
        LocalConfiguration.current.screenWidthDp >= 600 -> 3
        else -> 2
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(max = 2000.dp),
        userScrollEnabled = false,
    ) {
        items(cards, key = { it.id }) { card ->
            ModuleCardItem(card = card, onClick = { onCardClick(card) }, modifier = Modifier.animateItemPlacement())
        }
    }
}

@Composable
private fun ModuleCardItem(card: ModuleCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "scale")
    val accentColor = Color(card.accentColor)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = modifier.scale(scale),
        tonalElevation = 2.dp,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        0f to accentColor.copy(alpha = 0.08f),
                        1f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    )
                )
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = iconForName(card.icon),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (card.badge != null) {
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.2f),
                        ) {
                            Text(
                                card.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentActionsList(actions: List<RecentAction>, onActionClick: (RecentAction) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(actions, key = { it.id }) { action ->
            Surface(
                onClick = { onActionClick(action) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(180.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val categoryAccent = mapOf(
    "Apps"          to Color(0xFF7C4DFF),
    "Shell & ADB"   to Color(0xFF00D4FF),
    "Storage"       to Color(0xFF00E676),
    "Audio"         to Color(0xFFE91E63),
    "Privacy"       to Color(0xFFFF6D00),
    "Customization" to Color(0xFFD500F9),
    "Network"       to Color(0xFF2196F3),
    "Automation"    to Color(0xFF9C27B0),
    "System"        to Color(0xFF607D8B),
)

private val categoryOrder = listOf(
    "Apps", "Shell & ADB", "Storage", "Audio",
    "Privacy", "Customization", "Network", "Automation", "System",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CommandPaletteOverlay(
    query: String,
    results: List<SearchResult>,
    onQueryChange: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val totalScreens = SearchIndex.entries.size
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .statusBarsPadding(),
        ) {
            Column {
                // ── Search field ────────────────────────────────────
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Search $totalScreens screens — type anything…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(0.15f),
                                ) {
                                    Text(
                                        "Esc",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }

                // ── Content area ─────────────────────────────────────
                LazyColumn(
                    Modifier.heightIn(max = 480.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    when {
                        // ── Empty query → Quick Launch ─────────────
                        query.isBlank() -> {
                            item {
                                Row(
                                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Quick Launch",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            items(SearchIndex.quickLaunch.chunked(2)) { pair ->
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { item ->
                                        QuickLaunchTile(
                                            item = item,
                                            onClick = { onResultClick(item) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            item {
                                Row(
                                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "$totalScreens screens across ${categoryOrder.size} categories",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    categoryOrder.forEach { cat ->
                                        val color = categoryAccent[cat] ?: MaterialTheme.colorScheme.primary
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .padding(1.dp)
                                                .background(color, CircleShape)
                                        )
                                    }
                                }
                            }
                        }

                        // ── No results ────────────────────────────
                        results.isEmpty() -> {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 36.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.Default.SearchOff,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "No screens match \"$query\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Try: audio, freeze, clean, adb, dark mode…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                    )
                                }
                            }
                        }

                        // ── Grouped results ───────────────────────
                        else -> {
                            val grouped = results.groupBy { it.category }
                            val orderedGroups = categoryOrder.mapNotNull { cat ->
                                grouped[cat]?.let { cat to it }
                            } + grouped.filterKeys { it !in categoryOrder }.entries.map { it.key to it.value }

                            orderedGroups.forEach { (category, items) ->
                                val accent = categoryAccent[category] ?: MaterialTheme.colorScheme.primary
                                stickyHeader(key = "header_$category") {
                                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .background(accent, CircleShape)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                category,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = accent,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "${items.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accent.copy(alpha = 0.7f),
                                            )
                                        }
                                    }
                                }
                                items(items, key = { "${category}_${it.route}_${it.title}" }) { result ->
                                    SearchResultRow(
                                        result = result,
                                        accent = accent,
                                        onClick = { onResultClick(result) },
                                    )
                                }
                            }

                            item(key = "footer") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${results.size} of $totalScreens screens",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickLaunchTile(
    item: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = categoryAccent[item.category] ?: MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        iconForName(item.icon),
                        null,
                        tint = accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    accent: Color,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(result.title, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(
                result.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        iconForName(result.icon),
                        null,
                        tint = accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.Default.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f),
                modifier = Modifier.size(16.dp),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private val LocalConfiguration @Composable get() = androidx.compose.ui.platform.LocalConfiguration.current

fun iconForName(name: String) = when (name) {
    // ── Original ───────────────────────────────────────────────
    "terminal"              -> Icons.Default.Terminal
    "apps"                  -> Icons.Default.Apps
    "security"              -> Icons.Default.Security
    "palette"               -> Icons.Default.Palette
    "storage"               -> Icons.Default.Storage
    "folder"                -> Icons.Default.Folder
    "install_mobile"        -> Icons.Default.InstallMobile
    "keyboard"              -> Icons.Default.Keyboard
    "language"              -> Icons.Default.Language
    "wifi"                  -> Icons.Default.Wifi
    "equalizer"             -> Icons.Default.Equalizer
    "call"                  -> Icons.Default.Call
    "widgets"               -> Icons.Default.Widgets
    "school"                -> Icons.Default.School
    "delete"                -> Icons.Default.Delete
    "ac_unit"               -> Icons.Default.AcUnit
    "color_lens"            -> Icons.Default.ColorLens
    "dark_mode"             -> Icons.Default.DarkMode
    "settings"              -> Icons.Default.Settings
    "home"                  -> Icons.Default.Home
    "admin_panel_settings"  -> Icons.Default.AdminPanelSettings
    "shizuku"               -> Icons.Default.Hub
    "search"                -> Icons.Default.Search
    // ── Extended for global search ─────────────────────────────
    "schedule"              -> Icons.Default.Schedule
    "battery_full"          -> Icons.Default.BatteryFull
    "power_settings_new"    -> Icons.Default.PowerSettingsNew
    "note"                  -> Icons.Default.Note
    "music_note"            -> Icons.Default.MusicNote
    "bar_chart"             -> Icons.Default.BarChart
    "pie_chart"             -> Icons.Default.PieChart
    "block"                 -> Icons.Default.Block
    "bookmark"              -> Icons.Default.Bookmark
    "history"               -> Icons.Default.History
    "work"                  -> Icons.Default.Work
    "flag"                  -> Icons.Default.Flag
    "content_copy"          -> Icons.Default.ContentCopy
    "compress"              -> Icons.Default.Compress
    "cloud_upload"          -> Icons.Default.CloudUpload
    "cloud"                 -> Icons.Default.Cloud
    "tune"                  -> Icons.Default.Tune
    "waves"                 -> Icons.Default.Waves
    "code"                  -> Icons.Default.Code
    "cast"                  -> Icons.Default.Cast
    "help"                  -> Icons.Default.Help
    "wb_sunny"              -> Icons.Default.WbSunny
    "style"                 -> Icons.Default.Style
    "grid_view"             -> Icons.Default.GridView
    "list"                  -> Icons.Default.List
    "notifications"         -> Icons.Default.Notifications
    "analytics"             -> Icons.Default.Analytics
    "checklist"             -> Icons.Default.Checklist
    "bolt"                  -> Icons.Default.Bolt
    else                    -> Icons.Default.Circle
}
