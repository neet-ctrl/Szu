package com.accu.ui.notifications

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.notifications.AccuChannels
import com.accu.notifications.ALL_NOTIFICATION_FEATURES
import com.accu.notifications.NotificationFeature
import com.accu.ui.theme.*

// ─────────────────────────────────────────────────────────────────
//  Notification Center Screen
// ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    viewModel: NotificationCenterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Permission launcher (Android 13+)
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.load() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {

            // ── Hero header ───────────────────────────────────────
            item { NotificationHeroHeader(state = state, viewModel = viewModel, onBack = onBack) }

            // ── Permission banner ─────────────────────────────────
            if (!state.hasPermission && Build.VERSION.SDK_INT >= 33) {
                item {
                    PermissionBanner(
                        onGrant = { permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
            }

            // ── Snooze active banner ──────────────────────────────
            if (state.snoozeUntilMs > System.currentTimeMillis()) {
                item { SnoozeBanner(until = state.snoozeUntilMs, onClear = viewModel::clearSnooze) }
            }

            // ── Search bar ────────────────────────────────────────
            item {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::onSearchChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ── Quick actions row ─────────────────────────────────
            item {
                QuickActionsRow(
                    onEnableAll  = viewModel::enableAll,
                    onDisableAll = viewModel::disableAll,
                    onSnooze     = viewModel::showSnoozeDialog,
                    onReset      = viewModel::resetToDefaults,
                    onSystem     = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    },
                )
            }

            // ── Feature channel cards ─────────────────────────────
            val filtered = if (state.searchQuery.isBlank()) state.channels
            else state.channels.filter {
                it.feature.featureName.contains(state.searchQuery, ignoreCase = true) ||
                it.feature.moduleSource.contains(state.searchQuery, ignoreCase = true) ||
                it.feature.description.contains(state.searchQuery, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                            Spacer(Modifier.height(8.dp))
                            Text("No features match \"${state.searchQuery}\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "${filtered.size} notification source${if (filtered.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                items(filtered, key = { it.feature.channelId }) { ch ->
                    val masterOn = state.masterEnabled && !state.snoozeUntilMs.let { it > System.currentTimeMillis() }
                    ChannelCard(
                        ch = ch,
                        masterEnabled = masterOn,
                        onToggle = { viewModel.setChannelEnabled(ch.feature.channelId, it) },
                        onImportance = { viewModel.setImportance(ch.feature.channelId, it) },
                        onExpand = { viewModel.toggleExpanded(ch.feature.channelId) },
                        onTest = { viewModel.sendTestNotification(ch.feature.channelId) },
                    )
                }
            }

            // ── Stats footer ──────────────────────────────────────
            item { StatsFooter(channels = state.channels) }
        }
    }

    // Snooze dialog
    if (state.showSnoozeDialog) {
        SnoozeDialog(
            onDismiss = viewModel::dismissSnoozeDialog,
            onSnooze  = viewModel::snoozeAll,
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  Hero header
// ─────────────────────────────────────────────────────────────────
@Composable
private fun NotificationHeroHeader(
    state: NotificationCenterUiState,
    viewModel: NotificationCenterViewModel,
    onBack: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bell")
    val bellScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (state.masterEnabled) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bellScale",
    )
    val activeCount = state.channels.count { it.isEnabled && state.masterEnabled }
    val totalCount  = state.channels.size

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(0.7f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
        ) { Icon(Icons.Default.ArrowBack, "Back") }

        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Animated bell
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                Surface(
                    shape = CircleShape,
                    color = if (state.masterEnabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(80.dp),
                ) {}
                Icon(
                    if (state.masterEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    null,
                    Modifier.size(40.dp).scale(bellScale),
                    tint = if (state.masterEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeCount > 0 && state.masterEnabled) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp),
                        containerColor = AccentGreen,
                    ) {
                        Text("$activeCount", color = Color.Black, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Notification Center",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "$activeCount of $totalCount channels active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Master toggle
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (state.masterEnabled) AccentGreen.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        if (state.masterEnabled) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                        null, Modifier.size(28.dp),
                        tint = if (state.masterEnabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.masterEnabled) "All Notifications ON" else "All Notifications OFF",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Master kill switch for every channel below",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.masterEnabled,
                        onCheckedChange = viewModel::setMasterEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen, checkedTrackColor = AccentGreen.copy(0.4f)),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Permission banner
// ─────────────────────────────────────────────────────────────────
@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.NotificationsOff, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(Modifier.weight(1f)) {
                Text("Permission required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Grant notification permission to receive alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(0.8f))
            }
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Grant") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Snooze banner
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SnoozeBanner(until: Long, onClear: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(0.15f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Snooze, null, Modifier.size(20.dp), tint = AccentOrange)
            Text(
                "Snoozed until ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(until))}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = AccentOrange,
            )
            TextButton(onClick = onClear) { Text("Clear", color = AccentOrange) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Search bar
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search channels…", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
    )
}

// ─────────────────────────────────────────────────────────────────
//  Quick actions row
// ─────────────────────────────────────────────────────────────────
@Composable
private fun QuickActionsRow(
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onSnooze: () -> Unit,
    onReset: () -> Unit,
    onSystem: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onEnableAll,
            label = { Text("Enable All") },
            leadingIcon = { Icon(Icons.Default.DoneAll, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = onDisableAll,
            label = { Text("Disable All") },
            leadingIcon = { Icon(Icons.Default.NotificationsOff, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = onSnooze,
            label = { Text("Snooze…") },
            leadingIcon = { Icon(Icons.Default.Snooze, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = onSystem,
            label = { Text("System Settings") },
            leadingIcon = { Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = onReset,
            label = { Text("Reset Defaults") },
            leadingIcon = { Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp)) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  Channel card
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    ch: ChannelUiState,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onImportance: (Int) -> Unit,
    onExpand: () -> Unit,
    onTest: () -> Unit,
) {
    val effectivelyEnabled = masterEnabled && ch.isEnabled
    val cardColor = when {
        !masterEnabled           -> MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        effectivelyEnabled       -> MaterialTheme.colorScheme.surfaceContainer
        else                     -> MaterialTheme.colorScheme.surfaceVariant
    }
    val accentColor = featureAccent(ch.feature.channelId)

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .animateItem(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (effectivelyEnabled) BorderStroke(1.dp, accentColor.copy(0.3f)) else null,
    ) {
        Column {
            // ── Main row ──────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (effectivelyEnabled) accentColor.copy(0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            featureIcon(ch.feature.channelId),
                            null,
                            Modifier.size(22.dp),
                            tint = if (effectivelyEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            ch.feature.featureName,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!masterEnabled) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("Master OFF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text(
                        ch.feature.moduleSource,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        ch.feature.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Switch(
                        checked = ch.isEnabled,
                        onCheckedChange = onToggle,
                        enabled = masterEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(0.4f),
                        ),
                    )
                    Text(
                        if (ch.notifCount > 0) "${ch.notifCount} sent" else "None yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Expanded details ──────────────────────────────────
            AnimatedVisibility(visible = ch.isExpanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(color = accentColor.copy(0.2f))

                    // Channel ID
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tag, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Channel ID: ${ch.feature.channelId}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Last fired
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Last sent: ${formatRelativeTime(ch.lastFiredMs)}  ·  Total: ${ch.notifCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Importance selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Importance level", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val levels = listOf(
                            NotificationManager.IMPORTANCE_MIN     to "Silent",
                            NotificationManager.IMPORTANCE_LOW     to "Low",
                            NotificationManager.IMPORTANCE_DEFAULT to "Normal",
                            NotificationManager.IMPORTANCE_HIGH    to "Urgent",
                        )
                        val effectiveImportance = ch.importanceOverride ?: ch.feature.importance
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            levels.forEach { (imp, label) ->
                                val selected = effectiveImportance == imp
                                val impColor = importanceColor(imp)
                                FilterChip(
                                    selected = selected,
                                    onClick = { onImportance(imp) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = impColor.copy(0.2f),
                                        selectedLabelColor = impColor,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        selectedBorderColor = impColor.copy(0.6f),
                                        borderColor = MaterialTheme.colorScheme.outline.copy(0.3f),
                                    ),
                                )
                            }
                        }
                    }

                    // Test button
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(
                            onClick = onTest,
                            enabled = ch.isEnabled && masterEnabled,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = accentColor.copy(0.15f),
                            ),
                        ) {
                            AnimatedContent(targetState = ch.testSent, label = "testBtn") { sent ->
                                if (sent) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = AccentGreen)
                                        Text("Sent!", style = MaterialTheme.typography.labelMedium, color = AccentGreen)
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.NotificationAdd, null, Modifier.size(14.dp), tint = accentColor)
                                        Text("Send Test", style = MaterialTheme.typography.labelMedium, color = accentColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Stats footer
// ─────────────────────────────────────────────────────────────────
@Composable
private fun StatsFooter(channels: List<ChannelUiState>) {
    val totalSent = channels.sumOf { it.notifCount }
    val activeChannels = channels.count { it.isEnabled }
    if (totalSent == 0 && activeChannels == 0) return

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Lifetime Stats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatPill("Total sent", "$totalSent", AccentCyan)
                StatPill("Active channels", "$activeChannels", AccentGreen)
                StatPill("Disabled", "${channels.size - activeChannels}", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            // Top sender bar
            val topChannel = channels.maxByOrNull { it.notifCount }
            if (topChannel != null && topChannel.notifCount > 0) {
                Text("Most active: ${topChannel.feature.featureName} (${topChannel.notifCount})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────────
//  Snooze dialog
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SnoozeDialog(onDismiss: () -> Unit, onSnooze: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Snooze, null) },
        title = { Text("Snooze All Notifications") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Temporarily silence all ACC notifications.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                listOf(1, 2, 4, 8, 24).forEach { hrs ->
                    OutlinedButton(onClick = { onSnooze(hrs) }, Modifier.fillMaxWidth()) {
                        Text("For $hrs hour${if (hrs != 1) "s" else ""}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─────────────────────────────────────────────────────────────────
//  Helpers — icon + color per channel
// ─────────────────────────────────────────────────────────────────
@Composable
private fun featureIcon(channelId: String) = when (channelId) {
    AccuChannels.CALL_RECORDING   -> Icons.Default.Call
    AccuChannels.AUDIO_DSP        -> Icons.Default.Equalizer
    AccuChannels.SHIZUKU_SERVICE  -> Icons.Default.Hub
    AccuChannels.STORAGE_ALERTS   -> Icons.Default.Storage
    AccuChannels.CLEANUP_WORKER   -> Icons.Default.CleaningServices
    AccuChannels.PRIVACY_TRACKER  -> Icons.Default.Shield
    AccuChannels.FREEZE_SCHEDULER -> Icons.Default.AcUnit
    AccuChannels.KEY_MAPPER       -> Icons.Default.Keyboard
    AccuChannels.APP_MANAGER      -> Icons.Default.Apps
    AccuChannels.NETWORK_CHANGES  -> Icons.Default.Wifi
    AccuChannels.SHELL_COMPLETE   -> Icons.Default.Terminal
    else                          -> Icons.Default.Notifications
}

@Composable
private fun featureAccent(channelId: String): Color = when (channelId) {
    AccuChannels.CALL_RECORDING   -> AccentGreen
    AccuChannels.AUDIO_DSP        -> AccentPurple
    AccuChannels.SHIZUKU_SERVICE  -> AccentCyan
    AccuChannels.STORAGE_ALERTS   -> AccentRed
    AccuChannels.CLEANUP_WORKER   -> AccentOrange
    AccuChannels.PRIVACY_TRACKER  -> AccentOrange
    AccuChannels.FREEZE_SCHEDULER -> AccentCyan
    AccuChannels.KEY_MAPPER       -> AccentYellow
    AccuChannels.APP_MANAGER      -> MaterialTheme.colorScheme.primary
    AccuChannels.NETWORK_CHANGES  -> AccentCyan
    AccuChannels.SHELL_COMPLETE   -> AccentGreen
    else                          -> MaterialTheme.colorScheme.primary
}

private fun importanceColor(importance: Int): Color = when (importance) {
    NotificationManager.IMPORTANCE_MIN     -> Color(0xFF607D8B)
    NotificationManager.IMPORTANCE_LOW     -> Color(0xFF2196F3)
    NotificationManager.IMPORTANCE_DEFAULT -> Color(0xFF4CAF50)
    NotificationManager.IMPORTANCE_HIGH    -> Color(0xFFFF9800)
    NotificationManager.IMPORTANCE_MAX     -> Color(0xFFE53935)
    else                                   -> Color(0xFF9E9E9E)
}
