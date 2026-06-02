package com.accu.ui.appmanager

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ComponentSearchResult(
    val componentName: String,
    val appName: String,
    val packageName: String,
    val type: String,
    var isBlocked: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerComponentSearchScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }
    var hasSearched by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ComponentSearchResult>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val displayResults = if (filterType == "All") results else results.filter { it.type == filterType }

    Scaffold(
        topBar = { ACCTopBar(title = "Component Search", onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (query.isBlank()) return@Button
                        isSearching = true
                        hasSearched = true
                        scope.launch {
                            val found = withContext(Dispatchers.IO) {
                                val pm = context.packageManager
                                val flags = PackageManager.GET_ACTIVITIES or
                                        PackageManager.GET_SERVICES or
                                        PackageManager.GET_RECEIVERS or
                                        PackageManager.GET_PROVIDERS
                                val packages = try { pm.getInstalledPackages(flags) } catch (_: Exception) { emptyList() }
                                val matches = mutableListOf<ComponentSearchResult>()
                                val q = query.lowercase()
                                for (pi in packages) {
                                    val appName = pi.applicationInfo?.loadLabel(pm)?.toString() ?: pi.packageName
                                    pi.activities?.filter { it.name.lowercase().contains(q) }
                                        ?.forEach { matches.add(ComponentSearchResult(it.name, appName, pi.packageName, "Activity", false)) }
                                    pi.services?.filter { it.name.lowercase().contains(q) }
                                        ?.forEach { matches.add(ComponentSearchResult(it.name, appName, pi.packageName, "Service", false)) }
                                    pi.receivers?.filter { it.name.lowercase().contains(q) }
                                        ?.forEach { matches.add(ComponentSearchResult(it.name, appName, pi.packageName, "Receiver", false)) }
                                    pi.providers?.filter { it.name.lowercase().contains(q) }
                                        ?.forEach { matches.add(ComponentSearchResult(it.name, appName, pi.packageName, "Provider", false)) }
                                }
                                matches.sortedBy { it.appName }
                            }
                            results = found
                            isSearching = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSearching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSearching) "Searching…" else "Search")
                }

                if (hasSearched && results.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        results = results.map { it.copy(isBlocked = true) }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Block, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Block All")
                    }
                }
            }

            if (hasSearched && !isSearching) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Activity", "Service", "Receiver", "Provider").forEach { t ->
                        FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(t, fontSize = 11.sp) })
                    }
                }

                Text(
                    "${displayResults.size} components found · ${displayResults.count { it.isBlocked }} blocked",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
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
            } else if (isSearching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Scanning all apps for \"$query\"…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No components matching \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Try a broader term like 'analytics' or 'ads'", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
