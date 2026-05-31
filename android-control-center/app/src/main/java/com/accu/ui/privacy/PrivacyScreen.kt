package com.accu.ui.privacy

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.components.LoadingScreen
import com.accu.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Privacy Center",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Privacy Center — Blocker",
                        description = "Fine-grained component blocker inspired by Blocker app.\n\n• Disable trackers: Firebase, AdMob, AppsFlyer, Crashlytics, Branch.io, etc.\n• Disable individual receivers, services, activities, providers\n• Privacy audit: shows what every app is collecting\n• Online rules: import community block lists\n\nAll changes apply via Shizuku (no root needed). Components can be re-enabled at any time."
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Gradient threat level banner
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AccentRed.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.surface,
                                AccentCyan.copy(alpha = 0.14f),
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrivacyStatChip("${state.blockedCount}", "Blocked", AccentRed)
                    PrivacyStatChip("${state.trackerCount}", "Trackers", AccentOrange)
                    PrivacyStatChip("${state.privacyRules.count { it.isEnabled }}", "Rules", AccentGreen)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (state.blockedCount > 0) AccentGreen.copy(0.15f)
                                else MaterialTheme.colorScheme.errorContainer.copy(0.5f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                if (state.blockedCount > 0) Icons.Default.Shield else Icons.Default.GppBad,
                                null, Modifier.size(14.dp),
                                tint = if (state.blockedCount > 0) AccentGreen else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                if (state.blockedCount > 0) "Protected" else "Exposed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.blockedCount > 0) AccentGreen else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Modern scrollable tab row
            val tabIndex = PrivacyTab.entries.indexOf(state.selectedTab)
            ScrollableTabRow(
                selectedTabIndex = tabIndex,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (tabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                divider = {},
            ) {
                PrivacyTab.entries.forEachIndexed { i, tab ->
                    val selected = state.selectedTab == tab
                    Tab(
                        selected = selected,
                        onClick = { viewModel.onTabChange(tab) },
                        text = {
                            Text(
                                tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            when (state.selectedTab) {
                PrivacyTab.DASHBOARD   -> PrivacyDashboard(state = state, viewModel = viewModel)
                PrivacyTab.TRACKERS    -> TrackerBlockerTab(state = state, viewModel = viewModel)
                PrivacyTab.COMPONENTS  -> ComponentsTab(state = state, viewModel = viewModel)
                PrivacyTab.RULES       -> RulesTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PrivacyStatChip(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyDashboard(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Quick actions
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, null, Modifier.size(18.dp), tint = AccentOrange)
                        Spacer(Modifier.width(6.dp))
                        Text("Quick Block", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    val actions = listOf(
                        Triple("Analytics", Icons.Default.Analytics, AccentCyan),
                        Triple("Ads", Icons.Default.MoneyOff, AccentOrange),
                        Triple("Social", Icons.Default.People, AccentPurple),
                        Triple("Crash Reports", Icons.Default.BugReport, AccentRed),
                    )
                    actions.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, icon, color) ->
                                FilledTonalButton(
                                    onClick = { viewModel.blockTrackersInCategory(label) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(0.12f)),
                                ) {
                                    Icon(icon, null, Modifier.size(14.dp), tint = color)
                                    Spacer(Modifier.width(4.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // IFW rules info
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.4f)),
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Shield, null, Modifier.size(36.dp).align(Alignment.CenterVertically), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Intent Firewall Blocking", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "IFW blocks are invisible to the app — it never receives the intent. " +
                            "More secure than pm disable; apps cannot detect or work around it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerBlockerTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.trackerCategories) { cat ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${cat.trackerCount} trackers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(cat.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { viewModel.blockTrackersInCategory(cat.name) }) { Text("Block All") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Exodus Privacy Integration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tracker signatures sourced from Exodus Privacy database. Blocks at component level — apps cannot detect the block.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ComponentsTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    if (state.blockedComponents.isEmpty()) {
        EmptyState(Icons.Default.Shield, "No blocked components", "Block trackers or disable components from App Manager")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.blockedComponents, key = { "${it.packageName}/${it.componentName}" }) { comp ->
                ListItem(
                    headlineContent = { Text(comp.componentName.substringAfterLast('.')) },
                    supportingContent = { Column { Text(comp.packageName, style = MaterialTheme.typography.bodySmall); Text("${comp.componentType} · ${comp.ruleSource}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    leadingContent = { Icon(if (comp.isTracker) Icons.Default.TrackChanges else Icons.Default.Block, null, tint = if (comp.isTracker) AccentOrange else MaterialTheme.colorScheme.primary) },
                    trailingContent = { IconButton(onClick = { viewModel.enableComponent(comp.packageName, comp.componentName) }) { Icon(Icons.Default.PlayArrow, "Enable") } },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RulesTab(state: PrivacyUiState, viewModel: PrivacyViewModel) {
    if (state.privacyRules.isEmpty()) {
        EmptyState(Icons.Default.Rule, "No custom rules", "Privacy rules appear here when you create them")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.privacyRules, key = { it.id }) { rule ->
                ListItem(
                    headlineContent = { Text(rule.ruleName) },
                    supportingContent = { Text("${rule.ruleType} · ${rule.packageName}", style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        Row {
                            Switch(checked = rule.isEnabled, onCheckedChange = {})
                            IconButton(onClick = { viewModel.deleteRule(rule) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

