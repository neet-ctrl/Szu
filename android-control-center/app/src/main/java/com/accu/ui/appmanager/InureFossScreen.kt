package com.accu.ui.appmanager

import android.content.pm.ApplicationInfo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureFossScreen(
    onBack: () -> Unit = {},
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<FossAppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterBasis by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val result = mutableListOf<FossAppInfo>()

                for (pi in packages) {
                    val pkg = pi.packageName
                    val isSystem = (pi.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                    if (isSystem) continue  // skip system apps — they're not from FOSS stores

                    val installer = try {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(pkg) ?: ""
                    } catch (_: Exception) { "" }

                    val fossBasis = when {
                        installer in FOSS_INSTALLERS -> when (installer) {
                            "org.fdroid.fdroid", "org.fdroid.basic" -> "F-Droid"
                            "com.aurora.store" -> "Aurora Store"
                            else -> installer
                        }
                        FOSS_PACKAGE_PATTERNS.any { pkg.startsWith(it) } -> "Package Name"
                        else -> null
                    }

                    if (fossBasis != null) {
                        result += FossAppInfo(
                            packageName = pkg,
                            appName = pm.getApplicationLabel(pi.applicationInfo!!).toString(),
                            versionName = pi.versionName ?: "",
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
                    Text("FOSS Apps")
                    if (apps.isNotEmpty()) Text("${apps.size} detected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Info
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VolunteerActivism, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Free & Open Source apps detected by installer source (F-Droid, Aurora Store) and package name patterns.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
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

            // Basis filter chips
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                bases.forEach { basis ->
                    FilterChip(filterBasis == basis, { filterBasis = basis }, { Text(basis) })
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No FOSS apps detected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    FossAppCard(app, onClick = { onNavigateToAppDetail(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun FossAppCard(app: FossAppInfo, onClick: () -> Unit) {
    val badgeColor = when (app.fossBasis) {
        "F-Droid"        -> MaterialTheme.colorScheme.primaryContainer
        "Aurora Store"   -> MaterialTheme.colorScheme.tertiaryContainer
        else             -> MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeTextColor = when (app.fossBasis) {
        "F-Droid"        -> MaterialTheme.colorScheme.onPrimaryContainer
        "Aurora Store"   -> MaterialTheme.colorScheme.onTertiaryContainer
        else             -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.VolunteerActivism, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.versionName.isNotEmpty()) Text("v${app.versionName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Surface(color = badgeColor, shape = MaterialTheme.shapes.small, tonalElevation = 0.dp) {
                Text(app.fossBasis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = badgeTextColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
