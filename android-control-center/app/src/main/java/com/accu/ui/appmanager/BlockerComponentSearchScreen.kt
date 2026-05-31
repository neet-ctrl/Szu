package com.accu.ui.appmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class ComponentSearchResult(
    val componentName: String,
    val appName: String,
    val packageName: String,
    val type: String, // Activity, Service, Receiver, Provider
    var isBlocked: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerComponentSearchScreen(onBack: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }
    var hasSearched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ComponentSearchResult>>(emptyList()) }

    val sampleResults = remember {
        listOf(
            ComponentSearchResult("com.google.analytics.AnalyticsReceiver", "YouTube", "com.google.android.youtube", "Receiver", false),
            ComponentSearchResult("com.google.analytics.AnalyticsService", "YouTube", "com.google.android.youtube", "Service", false),
            ComponentSearchResult("com.facebook.analytics.AnalyticsService", "Instagram", "com.instagram.android", "Service", true),
            ComponentSearchResult("com.google.android.gms.analytics.CampaignTrackingReceiver", "Chrome", "com.android.chrome", "Receiver", false),
            ComponentSearchResult("com.amplitude.api.AmplitudeService", "Facebook", "com.facebook.katana", "Service", true),
            ComponentSearchResult("io.branch.referral.InstallListener", "Slack", "com.slack", "Receiver", false),
            ComponentSearchResult("com.appsflyer.AppsFlyerInitProvider", "Instagram", "com.instagram.android", "Provider", true),
            ComponentSearchResult("com.mixpanel.android.mpmetrics.MixpanelAPI", "Facebook", "com.facebook.katana", "Service", false),
            ComponentSearchResult("com.google.firebase.crashlytics.CrashlyticsService", "WhatsApp", "com.whatsapp", "Service", false),
            ComponentSearchResult("com.google.android.gms.ads.AdService", "Gmail", "com.google.android.gm", "Service", false),
        )
    }

    val displayResults = if (filterType == "All") results else results.filter { it.type == filterType }

    Scaffold(
        topBar = { ACCTopBar(title = "Component Search", onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search components (e.g. analytics, ads, tracker)…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                singleLine = true,
            )

            // Search / filter buttons
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        results = sampleResults.filter { r ->
                            query.isBlank() || r.componentName.contains(query, ignoreCase = true) || r.appName.contains(query, ignoreCase = true)
                        }
                        hasSearched = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.Search, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Search") }

                if (hasSearched) {
                    OutlinedButton(onClick = {
                        results = results.map { it.copy(isBlocked = true) }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Block, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Block All")
                    }
                }
            }

            // Type filters
            if (hasSearched) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Activity", "Service", "Receiver", "Provider").forEach { t ->
                        FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(t, fontSize = 11.sp) })
                    }
                }

                Text("${displayResults.size} components found · ${displayResults.count { it.isBlocked }} blocked", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }

            if (!hasSearched) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ManageSearch, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Search across all installed apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Find and block specific components by name", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Examples: analytics, firebase, ads, tracker", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(displayResults, key = { "${it.packageName}:${it.componentName}" }) { comp ->
                        val typeColor = when (comp.type) {
                            "Activity" -> MaterialTheme.colorScheme.primary
                            "Service" -> MaterialTheme.colorScheme.secondary
                            "Receiver" -> MaterialTheme.colorScheme.tertiary
                            "Provider" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    comp.componentName.substringAfterLast("."),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            supportingContent = {
                                Column {
                                    Text(comp.componentName, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(comp.appName, fontSize = 11.sp)
                                        SuggestionChip(onClick = {}, label = { Text(comp.type, fontSize = 10.sp) }, modifier = Modifier.height(20.dp))
                                    }
                                }
                            },
                            leadingContent = {
                                Icon(when (comp.type) {
                                    "Activity" -> Icons.Default.OpenInNew
                                    "Service" -> Icons.Default.Settings
                                    "Receiver" -> Icons.Default.Radio
                                    "Provider" -> Icons.Default.Storage
                                    else -> Icons.Default.Code
                                }, null, tint = typeColor)
                            },
                            trailingContent = {
                                Switch(
                                    checked = comp.isBlocked,
                                    onCheckedChange = { blocked ->
                                        results = results.map { r ->
                                            if (r.componentName == comp.componentName && r.packageName == comp.packageName) r.copy(isBlocked = blocked) else r
                                        }
                                    },
                                    thumbContent = if (comp.isBlocked) { { Icon(Icons.Default.Block, null, Modifier.size(12.dp)) } } else null,
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
