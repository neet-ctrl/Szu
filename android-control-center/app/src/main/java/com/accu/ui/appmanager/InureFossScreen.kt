package com.accu.ui.appmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.shizuku.ShizukuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Known open-source/FOSS app package prefixes and exact names
private val FOSS_PACKAGE_PATTERNS = listOf(
    "org.fdroid", "org.mozilla", "org.videolan", "org.libreoffice",
    "com.github", "net.sourceforge", "io.github", "com.nextcloud",
    "org.thoughtcrime", "org.tasks", "com.termux", "org.telegram",
    "net.osmand", "com.simplemobiletools", "com.tutanota", "org.kde",
    "org.sufficientlysecure", "de.monocles", "com.aurora.store",
    "com.wireguard", "org.strongswan", "org.briarproject",
    "com.fsck", "com.machiav3llo", "org.fossify", "de.danoeh",
    "org.andstatus", "com.nicologis", "net.frju.flym",
)

private val FOSS_INSTALLERS = setOf(
    "org.fdroid.fdroid",
    "org.fdroid.basic",
    "com.aurora.store",
)

data class FossAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val fossBasis: String,  // "F-Droid", "Pattern", "Aurora Store"
    val installerPackage: String,
)

/** Derive a human-readable label from a package name. */
private fun labelFromPackage(pkg: String): String =
    pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureFossScreen(
    onBack: () -> Unit = {},
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager

    var apps by remember { mutableStateOf<List<FossAppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterBasis by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Get all non-system packages from target device with installer info
                val raw = connectionManager.exec("pm list packages -3 -i --show-versioncode 2>/dev/null").output
                val result = mutableListOf<FossAppInfo>()

                raw.lines().filter { it.startsWith("package:") }.forEach { line ->
                    val pkg = line.substringAfter("package:").substringBefore(" ").substringBefore("\t").trim()
                    if (pkg.isEmpty()) return@forEach

                    // Parse installer from "installer=xxx" or "installer:xxx"
                    val installer = Regex("installer[=:]([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
                        ?.trimEnd()?.let { if (it == "null" || it.isBlank()) "" else it } ?: ""

                    // Parse version code (version name requires per-package query — omit for speed)
                    // val versionCode = Regex("versionCode:(\\d+)").find(line)?.groupValues?.getOrNull(1) ?: ""

                    val fossBasis = when {
                        installer in FOSS_INSTALLERS -> when (installer) {
                            "org.fdroid.fdroid", "org.fdroid.basic" -> "F-Droid"
                            "com.aurora.store" -> "Aurora Store"
                            else -> installer.substringAfterLast(".")
                        }
                        FOSS_PACKAGE_PATTERNS.any { pkg.startsWith(it) } -> "Package Name"
                        else -> null
                    }

                    if (fossBasis != null) {
                        result += FossAppInfo(
                            packageName = pkg,
                            appName = labelFromPackage(pkg),
                            versionName = "",
                            fossBasis = fossBasis,
                            installerPackage = installer,
                        )
                    }
                }

                apps = result.sortedBy { it.appName }
            } catch (_: Exception) { }
            loading = false
        }
    }

    val bases = remember(apps) { listOf("All") + apps.map { it.fossBasis }.distinct() }

    val filtered = remember(apps, searchQuery, filterBasis) {
        apps.filter {
            (filterBasis == "All" || it.fossBasis == filterBasis) &&
            (searchQuery.isBlank() || it.appName.contains(searchQuery, true) || it.packageName.contains(searchQuery, true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("FOSS Apps (Target Device)")
                    if (apps.isNotEmpty()) Text("${apps.size} detected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VolunteerActivism, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "Free & Open Source apps on the target device — detected by installer (F-Droid, Aurora Store) and package name patterns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            OutlinedTextField(
                searchQuery, { searchQuery = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search FOSS apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            // Filter chips for FOSS basis
            if (bases.size > 1) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    bases.forEach { basis ->
                        FilterChip(filterBasis == basis, { filterBasis = basis }, { Text(basis) })
                    }
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Scanning target device for FOSS apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (apps.isEmpty()) "No FOSS apps detected on target device" else "No results for \"$searchQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = { Text(app.appName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.VolunteerActivism, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = when (app.fossBasis) {
                                        "F-Droid"      -> MaterialTheme.colorScheme.primaryContainer
                                        "Aurora Store" -> MaterialTheme.colorScheme.secondaryContainer
                                        else           -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ) {
                                    Text(app.fossBasis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                }
                            },
                            modifier = Modifier.clickable { onNavigateToAppDetail(app.packageName) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
