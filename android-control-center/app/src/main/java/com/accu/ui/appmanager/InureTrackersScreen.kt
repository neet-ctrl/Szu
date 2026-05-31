package com.accu.ui.appmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class TrackerEntry(
    val appName: String,
    val pkg: String,
    val trackerCount: Int,
    val trackers: List<TrackerDetail>,
)
data class TrackerDetail(val name: String, val category: String, val signature: String, val isBlocked: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureTrackersScreen(onBack: () -> Unit = {}) {
    val apps = remember {
        listOf(
            TrackerEntry("YouTube", "com.google.android.youtube", 5, listOf(
                TrackerDetail("Google Analytics", "Analytics", "com.google.android.gms.analytics"),
                TrackerDetail("Firebase Analytics", "Analytics", "com.google.firebase.analytics"),
                TrackerDetail("Google Crashlytics", "Crash Reporting", "com.google.firebase.crashlytics"),
                TrackerDetail("DoubleClick", "Advertising", "com.google.android.gms.ads"),
                TrackerDetail("Google Ads", "Advertising", "com.google.android.gms.ads.identifier"),
            )),
            TrackerEntry("Facebook/Meta", "com.facebook.katana", 8, listOf(
                TrackerDetail("Facebook Analytics", "Analytics", "com.facebook.analytics"),
                TrackerDetail("Facebook Ads", "Advertising", "com.facebook.ads"),
                TrackerDetail("Amplitude", "Analytics", "com.amplitude.api"),
                TrackerDetail("AppsFlyer", "Attribution", "com.appsflyer.sdk"),
                TrackerDetail("Mixpanel", "Analytics", "com.mixpanel.android"),
                TrackerDetail("Meta Pixel", "Advertising", "com.facebook.meta.pixel"),
                TrackerDetail("Branch", "Deep Linking", "io.branch.referral"),
                TrackerDetail("Sentry", "Crash Reporting", "io.sentry.android"),
            )),
            TrackerEntry("Instagram", "com.instagram.android", 6, listOf(
                TrackerDetail("Facebook Analytics", "Analytics", "com.facebook.analytics"),
                TrackerDetail("Facebook Ads", "Advertising", "com.facebook.ads"),
                TrackerDetail("AppsFlyer", "Attribution", "com.appsflyer.sdk"),
                TrackerDetail("Amplitude", "Analytics", "com.amplitude.api"),
                TrackerDetail("Crashlytics", "Crash Reporting", "com.google.firebase.crashlytics"),
                TrackerDetail("Firebase Remote Config", "Configuration", "com.google.firebase.remoteconfig"),
            )),
            TrackerEntry("WhatsApp", "com.whatsapp", 2, listOf(
                TrackerDetail("Facebook Analytics", "Analytics", "com.facebook.analytics"),
                TrackerDetail("Meta SDK", "Advertising", "com.facebook.meta"),
            )),
            TrackerEntry("Telegram", "org.telegram.messenger", 0, emptyList()),
            TrackerEntry("Chrome", "com.android.chrome", 3, listOf(
                TrackerDetail("Google Analytics", "Analytics", "com.google.android.gms.analytics"),
                TrackerDetail("Firebase", "Analytics", "com.google.firebase"),
                TrackerDetail("Google Ads", "Advertising", "com.google.android.gms.ads"),
            )),
        )
    }
    var search by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    val filtered = apps.filter { search.isBlank() || it.appName.contains(search, ignoreCase = true) }
    val totalTrackers = apps.sumOf { it.trackerCount }

    Scaffold(topBar = { ACCTopBar(title = "Tracker Detector", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrackChanges, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("$totalTrackers trackers detected across ${apps.count { it.trackerCount > 0 }} apps", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Data sourced from Exodus Privacy database", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    val isExpanded = expandedPkg == app.pkg
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clickable {
                        expandedPkg = if (isExpanded) null else app.pkg
                    }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (app.trackerCount == 0) Icons.Default.Security else Icons.Default.WarningAmber, null,
                                    tint = if (app.trackerCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(app.appName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Badge(
                                    containerColor = if (app.trackerCount == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                ) { Text("${app.trackerCount}", fontSize = 11.sp) }
                                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                            if (isExpanded && app.trackers.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                app.trackers.forEach { tracker ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(tracker.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text(tracker.signature, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        SuggestionChip(onClick = {}, label = { Text(tracker.category, fontSize = 10.sp) }, modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                            if (isExpanded && app.trackerCount == 0) {
                                Spacer(Modifier.height(4.dp))
                                Text("No known trackers detected in this app.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
