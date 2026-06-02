package com.accu.ui.privacy

import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import com.accu.ui.components.InfoTooltipIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrackerRule(
    val id: String,
    val name: String,
    val description: String,
    val category: TrackerCategory,
    val matchCount: Int,
    val packages: List<String>,
    val componentNames: List<String> = emptyList(),
    val applied: Boolean = false,
)

enum class TrackerCategory(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ANALYTICS("Analytics", Icons.Default.Analytics),
    ADVERTISING("Advertising", Icons.Default.MonetizationOn),
    CRASH_REPORTING("Crash Reporting", Icons.Default.BugReport),
    PROFILING("Profiling", Icons.Default.Person),
    IDENTIFICATION("Identification", Icons.Default.Fingerprint),
    LOCATION("Location", Icons.Default.LocationOn),
    SOCIAL("Social Media SDK", Icons.Default.Share),
}

val TRACKER_RULES = listOf(
    TrackerRule(
        "firebase", "Firebase Analytics", "Google Firebase analytics and crash reporting services",
        TrackerCategory.ANALYTICS, 89,
        listOf("com.google.firebase"),
        listOf("com.google.firebase.analytics.FirebaseAnalyticsReceiver", "com.google.firebase.iid.FirebaseInstanceIdReceiver")
    ),
    TrackerRule(
        "facebook_sdk", "Facebook SDK", "Facebook's advertising and analytics SDK",
        TrackerCategory.ADVERTISING, 234,
        listOf("com.facebook.katana", "com.facebook.appmanager"),
        listOf("com.facebook.ads.AudienceNetworkActivity", "com.facebook.CurrentAccessTokenExpirationBroadcastReceiver")
    ),
    TrackerRule(
        "adjust", "Adjust", "Mobile attribution and analytics platform",
        TrackerCategory.ANALYTICS, 67,
        listOf("com.adjust.sdk"),
        listOf("com.adjust.sdk.AdjustReferrerReceiver", "com.adjust.sdk.AdjustPreinstallReferrerReceiver")
    ),
    TrackerRule(
        "appsflyer", "AppsFlyer", "Mobile attribution, analytics and engagement",
        TrackerCategory.ANALYTICS, 112,
        listOf("com.appsflyer"),
        listOf("com.appsflyer.MultipleInstallBroadcastReceiver", "com.appsflyer.SingleInstallBroadcastReceiver", "com.appsflyer.AppsFlyerRequestListener")
    ),
    TrackerRule(
        "crashlytics", "Crashlytics / Firebase Crashlytics", "Crash reporting from Firebase",
        TrackerCategory.CRASH_REPORTING, 45,
        listOf("com.google.firebase.crashlytics", "com.crashlytics.android"),
        listOf("com.crashlytics.android.CrashlyticsReceiver", "com.google.firebase.crashlytics.internal.common.CrashReportingCoordinator")
    ),
    TrackerRule(
        "admob", "Google AdMob", "Google's advertising framework",
        TrackerCategory.ADVERTISING, 189,
        listOf("com.google.android.gms", "com.google.ads"),
        listOf("com.google.android.gms.ads.AdActivity", "com.google.android.gms.ads.MobileAdsInitProvider", "com.google.android.gms.ads.AdRequestBrokerService")
    ),
    TrackerRule(
        "branch_io", "Branch.io", "Mobile deep linking and attribution",
        TrackerCategory.IDENTIFICATION, 34,
        listOf("io.branch.sdk.android", "io.branch.referral"),
        listOf("io.branch.referral.InstallListener", "io.branch.referral.SplashActivity")
    ),
    TrackerRule(
        "segment", "Segment Analytics", "Customer data infrastructure",
        TrackerCategory.ANALYTICS, 28,
        listOf("com.segment.analytics", "com.segment.analytics.android"),
        listOf("com.segment.analytics.android.integrations.firebase.FirebaseIntegration")
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineRulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val rules = remember { mutableStateListOf(*TRACKER_RULES.toTypedArray()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<TrackerCategory?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installedPkgs = try {
                pm.getInstalledPackages(0).map { it.packageName }
            } catch (_: Exception) { emptyList() }
            val updatedRules = TRACKER_RULES.map { rule ->
                val count = installedPkgs.count { pkg -> rule.packages.any { prefix -> pkg.startsWith(prefix) } }
                rule.copy(matchCount = count)
            }
            withContext(Dispatchers.Main) {
                rules.clear()
                rules.addAll(updatedRules)
                isLoading = false
            }
        }
    }

    val filtered = rules.filter { rule ->
        (searchQuery.isBlank() || rule.name.contains(searchQuery, ignoreCase = true) || rule.description.contains(searchQuery, ignoreCase = true)) &&
                (categoryFilter == null || rule.category == categoryFilter)
    }

    val appliedCount = rules.count { it.applied }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Tracker Rules") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (appliedCount > 0) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                            Text("$appliedCount applied", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    InfoTooltipIcon(
                        title = "Tracker Rules",
                        description = "Tracker rules let you block advertising, analytics, and data-collection SDKs in any installed app — no root required (uses ACCU to apply package-level firewall rules).\n\nCategories:\n• Advertising — Ad networks (Google AdMob, Meta Ads, etc.)\n• Analytics — Usage tracking (Adjust, AppsFlyer, Segment, etc.)\n• Crash Reporting — Error reporting (Firebase Crashlytics, etc.)\n• Identification — Fingerprinting/device ID SDKs\n\nApplied rules survive app updates. Toggle individual rules to unblock specific SDKs.",
                    )
                    IconButton(onClick = {
                        scope.launch {
                            isLoading = true
                            delay(2000)
                            isLoading = false
                            snackbar.showSnackbar("Tracker database updated from server")
                        }
                    }) { Icon(Icons.Default.CloudDownload, "Update rules") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search trackers…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("All") })
                }
                items(TrackerCategory.entries) { cat ->
                    FilterChip(
                        selected = categoryFilter == cat,
                        onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                        label = { Text(cat.label) },
                        leadingIcon = { Icon(cat.icon, null, modifier = Modifier.size(14.dp)) },
                    )
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("${filtered.size} tracker rules", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                items(filtered, key = { it.id }) { rule ->
                    val idx = rules.indexOfFirst { it.id == rule.id }
                    TrackerRuleCard(
                        rule = rule,
                        isExpanded = expandedId == rule.id,
                        onToggleExpand = { expandedId = if (expandedId == rule.id) null else rule.id },
                        onApply = {
                            if (idx != -1) rules[idx] = rule.copy(applied = !rule.applied)
                            scope.launch { snackbar.showSnackbar(if (!rule.applied) "Applied rule: ${rule.name}" else "Removed rule: ${rule.name}") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackerRuleCard(
    rule: TrackerRule,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onApply: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.applied) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(modifier = Modifier.clickable(onClick = onToggleExpand).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(rule.category.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(rule.category.label, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${rule.matchCount} apps affected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                if (rule.applied) Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Switch(checked = rule.applied, onCheckedChange = { onApply() })
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    Text(rule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (rule.componentNames.isNotEmpty()) {
                        Text("Components blocked:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        rule.componentNames.forEach { comp ->
                            Text("• $comp", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
