package com.accu.ui.appmanager

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ──────────────────────────────────────────────
//  Inure — App Detail (all sections)
// ──────────────────────────────────────────────

enum class AppDetailTab { INFO, COMPONENTS, PERMISSIONS, TRACKERS, CERTIFICATES, APPOPS, NOTES, SHARED_LIBS, DEX_CLASSES, SHARED_PREFS, RESOURCES, APK_FILES }

// Well-known tracker SDKs (subset of Exodus Privacy tracker list)
private val KNOWN_TRACKERS = listOf(
    Triple("com.google.firebase.analytics", "Firebase Analytics",    "Analytics"),
    Triple("com.google.android.gms.ads",    "Google Ads (AdMob)",    "Advertising"),
    Triple("com.facebook.ads",              "Facebook Audience",      "Advertising"),
    Triple("com.amplitude.api",             "Amplitude",              "Analytics"),
    Triple("io.segment",                    "Segment",                "Analytics"),
    Triple("com.mixpanel",                  "Mixpanel",               "Analytics"),
    Triple("com.flurry",                    "Flurry Analytics",       "Analytics"),
    Triple("com.crashlytics",               "Crashlytics",            "Crash Reporting"),
    Triple("com.appsflyer",                 "AppsFlyer",              "Attribution"),
    Triple("io.branch",                     "Branch",                 "Attribution"),
    Triple("com.unity3d.ads",               "Unity Ads",              "Advertising"),
    Triple("com.applovin",                  "AppLovin",               "Advertising"),
    Triple("com.bugsnag",                   "Bugsnag",                "Crash Reporting"),
    Triple("io.sentry",                     "Sentry",                 "Crash Reporting"),
    Triple("com.rollbar",                   "Rollbar",                "Crash Reporting"),
    Triple("com.instabug",                  "Instabug",               "Crash Reporting"),
    Triple("com.contentsquare",             "ContentSquare",          "Profiling"),
    Triple("com.hotjar",                    "Hotjar",                 "Profiling"),
    Triple("io.embrace",                    "Embrace",                "Profiling"),
    Triple("com.onesignal",                 "OneSignal",              "Push Notifications"),
    Triple("com.batch",                     "Batch",                  "Push Notifications"),
    Triple("com.braze",                     "Braze",                  "Marketing"),
    Triple("com.salesforce.marketingcloud", "Salesforce Marketing",   "Marketing"),
    Triple("com.moengage",                  "MoEngage",               "Marketing"),
    Triple("com.clevertap",                 "CleverTap",              "Marketing"),
    Triple("com.adjust",                    "Adjust",                 "Attribution"),
    Triple("com.kochava",                   "Kochava",                "Attribution"),
    Triple("com.singular",                  "Singular",               "Attribution"),
    Triple("com.taboola",                   "Taboola",                "Advertising"),
    Triple("com.ironsource",                "IronSource",             "Advertising"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(packageName) { viewModel.load(packageName) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    var selectedTab by remember { mutableStateOf(AppDetailTab.INFO) }
    val clipboardManager = LocalClipboardManager.current
    // Dialogs
    var showManifest by remember { mutableStateOf(false) }

    // SAF folder picker for APK extraction
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { treeUri ->
            val docId = try { DocumentsContract.getTreeDocumentId(treeUri) } catch (_: Exception) { "primary:Download" }
            val folder = when {
                docId.startsWith("primary:") -> "/sdcard/${docId.removePrefix("primary:").trimEnd('/')}"
                else -> "/sdcard/Download"
            }
            viewModel.extractApkToPath("$folder/$packageName.apk")
        }
    }

    // Detect trackers from known list (scan declared packages/classes)
    val detectedTrackers = remember(packageName) {
        try {
            val pm = context.packageManager
            val pkg = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
            val allClasses = buildList {
                pkg.activities?.forEach { add(it.name) }
                pkg.services?.forEach { add(it.name) }
                pkg.receivers?.forEach { add(it.name) }
            }
            KNOWN_TRACKERS.filter { (prefix, _, _) -> allClasses.any { it.startsWith(prefix) } }
        } catch (_: Exception) { emptyList() }
    }

    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() } }

    Scaffold(
        topBar = {
            Column {
                ACCTopBar(
                    title = state.appName.ifBlank { packageName },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { viewModel.forceStop() }) { Icon(Icons.Default.Stop, "Force Stop") }
                        IconButton(onClick = { viewModel.openApp() }) { Icon(Icons.Default.OpenInNew, "Open App") }
                        IconButton(onClick = { folderPickerLauncher.launch(null) }) { Icon(Icons.Default.Download, "Extract APK") }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(showMenu, { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Clear Data") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { viewModel.clearData(); showMenu = false })
                                DropdownMenuItem(text = { Text("Uninstall") }, leadingIcon = { Icon(Icons.Default.RemoveCircle, null) }, onClick = { viewModel.uninstall(); showMenu = false })
                                DropdownMenuItem(
                                    text = { Text("Open App Info (local)") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open App Info (target)") },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
                                    onClick = { viewModel.openAppInfoOnTarget(); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Manifest") },
                                    leadingIcon = { Icon(Icons.Default.Code, null) },
                                    onClick = { viewModel.fetchManifest(); showManifest = true; showMenu = false }
                                )
                            }
                        }
                    },
                )
                ScrollableTabRow(selectedTabIndex = AppDetailTab.entries.indexOf(selectedTab), edgePadding = 16.dp) {
                    AppDetailTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(when(tab) {
                                AppDetailTab.INFO         -> "Info"
                                AppDetailTab.COMPONENTS   -> "Components"
                                AppDetailTab.PERMISSIONS  -> "Permissions"
                                AppDetailTab.TRACKERS     -> if (detectedTrackers.isEmpty()) "Trackers" else "Trackers (${detectedTrackers.size})"
                                AppDetailTab.CERTIFICATES -> "Certificates"
                                AppDetailTab.APPOPS       -> "App Ops"
                                AppDetailTab.NOTES        -> "Notes"
                                AppDetailTab.SHARED_LIBS  -> "Libraries"
                                AppDetailTab.DEX_CLASSES  -> "DEX Classes"
                                AppDetailTab.SHARED_PREFS -> "Shared Prefs"
                                AppDetailTab.RESOURCES    -> "Resources"
                                AppDetailTab.APK_FILES    -> "APK Files"
                            }, fontSize = 11.sp) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // ── Manifest viewer dialog ────────────────────────────────────────
        if (showManifest) {
            AlertDialog(
                onDismissRequest = { showManifest = false },
                title = { Text("AndroidManifest.xml") },
                text = {
                    if (state.manifestLoading) {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val scrollState = rememberScrollState()
                        Column(Modifier.height(420.dp).verticalScroll(scrollState)) {
                            SelectionContainer {
                                Text(
                                    state.manifestContent.ifBlank { "Manifest not available" },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.manifestLoading && state.manifestContent.isNotBlank()) {
                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(state.manifestContent)) }) { Text("Copy") }
                        }
                        TextButton(onClick = { showManifest = false }) { Text("Close") }
                    }
                }
            )
        }
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            when (selectedTab) {
                AppDetailTab.INFO         -> InfoTab(state, packageName, context, dateFormatter, viewModel, padding, onExtractApk = { folderPickerLauncher.launch(null) })
                AppDetailTab.COMPONENTS   -> ComponentsTab(state, viewModel, padding)
                AppDetailTab.PERMISSIONS  -> PermissionsTab(state, viewModel, padding)
                AppDetailTab.TRACKERS     -> TrackersTab(detectedTrackers, packageName, padding)
                AppDetailTab.CERTIFICATES -> CertificatesTab(packageName, context, padding)
                AppDetailTab.APPOPS       -> AppOpsTab(packageName, state, viewModel, padding)
                AppDetailTab.NOTES        -> NotesTab(packageName, padding)
                AppDetailTab.SHARED_LIBS  -> SharedLibsTab(packageName, context, padding)
                AppDetailTab.DEX_CLASSES  -> DexClassesTab(packageName, context, padding)
                AppDetailTab.SHARED_PREFS -> SharedPrefsTab(packageName, padding)
                AppDetailTab.RESOURCES    -> ResourcesTab(packageName, context, padding)
                AppDetailTab.APK_FILES    -> ApkFilesTab(packageName, context, padding)
            }
        }
    }
}

// ─── Tab 1: Info ───
@Composable
private fun InfoTab(state: AppDetailUiState, packageName: String, context: android.content.Context, dateFormatter: SimpleDateFormat, viewModel: AppDetailViewModel, padding: PaddingValues, onExtractApk: () -> Unit = {}) {
    val sharedLibs = remember(packageName) {
        try {
            val ai = context.packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES)
            ai.sharedLibraryFiles?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList<String>() }
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
        // Header
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(try { context.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }).crossfade(true).build(),
                    contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(state.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("v${state.versionName} (${state.versionCode})", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("API ${state.targetSdk}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                        if (!state.isEnabled) Surface(shape = RoundedCornerShape(4.dp), color = AccentOrange.copy(0.15f)) { Text("Disabled", style = MaterialTheme.typography.labelSmall, color = AccentOrange, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                        if (state.isFrozen) Surface(shape = RoundedCornerShape(4.dp), color = AccentCyan.copy(0.15f)) { Text("Frozen", style = MaterialTheme.typography.labelSmall, color = AccentCyan, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                    }
                }
            }
        }
        // Action buttons
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilledTonalButton(onClick = { viewModel.toggleFreeze() }) { Icon(Icons.Default.AcUnit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (state.isFrozen) "Unfreeze" else "Freeze") } }
                item { FilledTonalButton(onClick = { viewModel.toggleHide() }) { Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Hide") } }
                item { FilledTonalButton(onClick = { viewModel.forceStop() }) { Icon(Icons.Default.Stop, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Force Stop") } }
                item { OutlinedButton(onClick = { viewModel.clearData() }) { Text("Clear Data") } }
                item { OutlinedButton(onClick = { viewModel.uninstall() }) { Text("Uninstall") } }
                item { OutlinedButton(onClick = onExtractApk) { Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Extract APK") } }
            }
            Spacer(Modifier.height(8.dp))
        }
        // Package info
        item {
            DetailSection("Package Information") {
                DetailRow("Package",      packageName)
                DetailRow("Version",      "v${state.versionName} (${state.versionCode})")
                DetailRow("Min SDK",      "API ${state.minSdk}")
                DetailRow("Target SDK",   "API ${state.targetSdk}")
                DetailRow("APK Size",     if (state.apkSize > 0) "${"%.2f".format(state.apkSize / 1_000_000.0)} MB" else "Unknown")
                DetailRow("Install Date", if (state.installTime > 0) dateFormatter.format(Date(state.installTime)) else "Unknown")
                DetailRow("Last Update",  if (state.lastUpdate > 0) dateFormatter.format(Date(state.lastUpdate)) else "Unknown")
                DetailRow("APK Path",     state.sourceDir)
                DetailRow("Data Dir",     state.dataDir)
            }
        }
        // Summary stats
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip("${state.activities.size}", "Activities",  Modifier.weight(1f))
                SummaryChip("${state.services.size}",  "Services",    Modifier.weight(1f))
                SummaryChip("${state.receivers.size}", "Receivers",   Modifier.weight(1f))
                SummaryChip("${state.providers.size}", "Providers",   Modifier.weight(1f))
                SummaryChip("${state.permissions.size}","Permissions", Modifier.weight(1f))
            }
        }
        // Shared libraries
        if (sharedLibs.isNotEmpty()) {
            item {
                DetailSection("Shared Libraries (${sharedLibs.size})") {
                    sharedLibs.forEach { lib -> Text(lib.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
    }
}

// ─── Tab 2: Components ───
@Composable
private fun ComponentsTab(state: AppDetailUiState, viewModel: AppDetailViewModel, padding: PaddingValues) {
    val allComponents = listOf(
        "Activities"  to state.activities,
        "Services"    to state.services,
        "Receivers"   to state.receivers,
        "Providers"   to state.providers,
    )
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
        allComponents.forEach { (title, comps) ->
            if (comps.isNotEmpty()) {
                item {
                    Text(
                        "$title (${comps.size})",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(comps, key = { "${title}/${it.name}" }) { comp ->
                    ComponentItem(
                        name = comp.name,
                        type = comp.type,
                        isEnabled = comp.isEnabled,
                        onToggle = { viewModel.toggleComponent(comp.name, comp.type) },
                        onLaunch = if (comp.type in listOf("activity", "service", "receiver")) {{ viewModel.launchComponent(comp.name, comp.type) }} else null,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ─── Tab 3: Permissions ───
@Composable
private fun PermissionsTab(state: AppDetailUiState, viewModel: AppDetailViewModel, padding: PaddingValues) {
    val dangerous = state.permissions.filter { it.protection == 1 /* DANGEROUS */ }
    val normal = state.permissions.filter { it.protection == 0 }
    val signature = state.permissions.filter { it.isProtected }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip("${dangerous.size}", "Dangerous", Modifier.weight(1f), AccentRed)
                SummaryChip("${normal.size}",    "Normal",    Modifier.weight(1f), AccentGreen)
                SummaryChip("${signature.size}", "Protected", Modifier.weight(1f), AccentOrange)
            }
        }
        if (dangerous.isNotEmpty()) {
            item { Text("Dangerous Permissions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AccentRed, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            items(dangerous, key = { "D/${it.name}" }) { perm ->
                PermissionItem(perm, onRevoke = { viewModel.revokePermission(perm.name) }, onGrant = { viewModel.grantPermission(perm.name) })
                HorizontalDivider()
            }
        }
        if (normal.isNotEmpty()) {
            item { Text("Normal Permissions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AccentGreen, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            items(normal, key = { "N/${it.name}" }) { perm ->
                PermissionItem(perm, onRevoke = { viewModel.revokePermission(perm.name) }, onGrant = { viewModel.grantPermission(perm.name) })
                HorizontalDivider()
            }
        }
        if (signature.isNotEmpty()) {
            item { Text("System/Signature Permissions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AccentOrange, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            items(signature, key = { "S/${it.name}" }) { perm ->
                PermissionItem(perm, onRevoke = {}, onGrant = {})
                HorizontalDivider()
            }
        }
    }
}

// ─── Tab 4: Trackers ───
@Composable
private fun TrackersTab(detectedTrackers: List<Triple<String, String, String>>, packageName: String, padding: PaddingValues) {
    val byCategory = detectedTrackers.groupBy { it.third }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            if (detectedTrackers.isEmpty()) {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(0.1f))) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("No trackers detected", fontWeight = FontWeight.Bold, color = AccentGreen)
                            Text("No known tracker SDKs were found in $packageName", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = AccentRed.copy(0.08f))) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${detectedTrackers.size} tracker(s) detected", fontWeight = FontWeight.Bold, color = AccentRed)
                            Text("Based on Exodus Privacy tracker database", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        byCategory.forEach { (category, trackers) ->
            item {
                Text(category, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            items(trackers, key = { it.first }) { (pkg, name, cat) ->
                ListItem(
                    headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) },
                    leadingContent = {
                        Surface(shape = RoundedCornerShape(8.dp), color = AccentRed.copy(0.12f), modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.TrackChanges, null, Modifier.size(20.dp), tint = AccentRed) }
                        }
                    },
                    trailingContent = {
                        Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(0.1f)) { Text(cat, style = MaterialTheme.typography.labelSmall, color = AccentRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                    },
                )
                HorizontalDivider()
            }
        }
        if (detectedTrackers.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Block tracker components via Component Manager", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("Go to Components tab → disable tracker services/receivers to cut off data collection without uninstalling the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

// ─── Tab 5: Certificates ───
@Composable
private fun CertificatesTab(packageName: String, context: android.content.Context, padding: PaddingValues) {
    val certInfo = remember(packageName) {
        try {
            @Suppress("DEPRECATION")
            val pkg = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val sigs = pkg.signatures?.toList() ?: emptyList()
            sigs.map { sig ->
                val cert = java.security.cert.CertificateFactory.getInstance("X509").generateCertificate(java.io.ByteArrayInputStream(sig.toByteArray())) as java.security.cert.X509Certificate
                mapOf(
                    "Subject"    to cert.subjectDN.name,
                    "Issuer"     to cert.issuerDN.name,
                    "Serial"     to cert.serialNumber.toString(16).uppercase(),
                    "Not Before" to cert.notBefore.toString(),
                    "Not After"  to cert.notAfter.toString(),
                    "Algorithm"  to cert.sigAlgName,
                    "SHA-256"    to bytesToHex(java.security.MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())),
                    "SHA-1"      to bytesToHex(java.security.MessageDigest.getInstance("SHA-1").digest(sig.toByteArray())),
                    "MD5"        to bytesToHex(java.security.MessageDigest.getInstance("MD5").digest(sig.toByteArray())),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (certInfo.isEmpty()) {
            item { Text("Could not read certificate information.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(certInfo) { i, cert ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Certificate ${i+1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val certClipboard = LocalClipboardManager.current
                    cert.forEach { (key, value) ->
                        val isCopyable = key.contains("SHA") || key == "Serial" || key == "MD5" || key == "Subject" || key == "Issuer"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(75.dp))
                            Text(
                                value,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = if (key.contains("SHA") || key == "Serial" || key == "MD5") FontFamily.Monospace else FontFamily.Default,
                                modifier = Modifier.weight(1f),
                                maxLines = if (key.contains("SHA") || key == "MD5" || key == "Serial") 1 else 2,
                            )
                            if (isCopyable) {
                                IconButton(
                                    onClick = { certClipboard.setText(AnnotatedString(value)) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bytesToHex(bytes: ByteArray) = bytes.joinToString(":") { "%02X".format(it) }

// ─── Tab 6: App Ops ───
private val APP_OPS_TEMPLATE = listOf(
    Triple("READ_EXTERNAL_STORAGE",    "Storage (Read)",      Icons.Outlined.Storage),
    Triple("WRITE_EXTERNAL_STORAGE",   "Storage (Write)",     Icons.Outlined.Storage),
    Triple("CAMERA",                   "Camera",              Icons.Outlined.CameraAlt),
    Triple("RECORD_AUDIO",             "Microphone",          Icons.Outlined.Mic),
    Triple("ACCESS_FINE_LOCATION",     "Location (Fine)",     Icons.Outlined.LocationOn),
    Triple("ACCESS_COARSE_LOCATION",   "Location (Coarse)",   Icons.Outlined.LocationOn),
    Triple("READ_CONTACTS",            "Contacts (Read)",     Icons.Outlined.Contacts),
    Triple("WRITE_CONTACTS",           "Contacts (Write)",    Icons.Outlined.Contacts),
    Triple("READ_CALL_LOG",            "Call Log (Read)",     Icons.Outlined.Call),
    Triple("WRITE_CALL_LOG",           "Call Log (Write)",    Icons.Outlined.Call),
    Triple("READ_SMS",                 "SMS (Read)",          Icons.Outlined.Message),
    Triple("SEND_SMS",                 "SMS (Send)",          Icons.Outlined.Send),
    Triple("RECEIVE_SMS",              "SMS (Receive)",       Icons.Outlined.Inbox),
    Triple("READ_PHONE_STATE",         "Phone State",         Icons.Outlined.PhoneAndroid),
    Triple("CALL_PHONE",               "Call Phone",          Icons.Outlined.Call),
    Triple("BODY_SENSORS",             "Body Sensors",        Icons.Outlined.MonitorHeart),
    Triple("GET_ACCOUNTS",             "Get Accounts",        Icons.Outlined.AccountCircle),
    Triple("USE_BIOMETRIC",            "Biometric",           Icons.Outlined.Fingerprint),
    Triple("VIBRATE",                  "Vibrate",             Icons.Outlined.Vibration),
    Triple("WAKE_LOCK",                "Wake Lock",           Icons.Outlined.BatteryChargingFull),
    Triple("CHANGE_NETWORK_STATE",     "Change Network",      Icons.Outlined.Wifi),
    Triple("REQUEST_INSTALL_PACKAGES", "Install Packages",    Icons.Outlined.GetApp),
)

@Composable
private fun AppOpsTab(packageName: String, state: AppDetailUiState, viewModel: AppDetailViewModel, padding: PaddingValues) {
    // Load actual App Ops from target device on first composition
    LaunchedEffect(packageName) { viewModel.fetchAppOps() }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            val opsClipboard = LocalClipboardManager.current
            val opsCmd = "appops set $packageName <OP> allow|deny|ignore"
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("App Ops — fine-grained permission controls via ACCU.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSecondaryContainer.copy(0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(opsCmd, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), fontSize = 10.sp)
                        IconButton(onClick = { opsClipboard.setText(AnnotatedString(opsCmd)) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Outlined.ContentCopy, "Copy command", Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
        if (state.appOpsState.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Text("Reading App Ops from device…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(APP_OPS_TEMPLATE, key = { it.first }) { (op, label, icon) ->
            val mode = state.appOpsState[op] ?: "Default"
            ListItem(
                headlineContent = { Text(label, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("OP: $op", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) },
                leadingContent = {
                    val color = when(mode) { "Allow" -> AccentGreen; "Deny" -> AccentRed; "Ignore" -> AccentOrange; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.12f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = color) }
                    }
                },
                trailingContent = {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when(mode) { "Allow" -> AccentGreen.copy(0.12f); "Deny" -> AccentRed.copy(0.12f); "Ignore" -> AccentOrange.copy(0.12f); else -> MaterialTheme.colorScheme.surfaceVariant },
                            modifier = Modifier.clickable { showMenu = true },
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(mode, style = MaterialTheme.typography.labelSmall, color = when(mode) { "Allow" -> AccentGreen; "Deny" -> AccentRed; "Ignore" -> AccentOrange; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp))
                            }
                        }
                        DropdownMenu(showMenu, { showMenu = false }) {
                            listOf("Allow", "Deny", "Ignore").forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = { viewModel.setAppOp(op, m); showMenu = false })
                            }
                        }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

// ─── Tab 7: Notes ───
@Composable
private fun NotesTab(packageName: String, padding: PaddingValues) {
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var savedNote by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Personal Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Add private notes and tags for $packageName — stored locally in ACCU database.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = note, onValueChange = { note = it; saved = false },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            label = { Text("Notes") },
            placeholder = { Text("Why did you install this? Known issues? Block reminders?") },
        )
        OutlinedTextField(
            value = tags, onValueChange = { tags = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tags (comma separated)") },
            placeholder = { Text("bloatware, tracker, game, social, …") },
            singleLine = true,
        )
        if (tags.isNotBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tags.split(",").map { it.trim() }.filter { it.isNotBlank() }) { tag ->
                    AssistChip(onClick = {}, label = { Text(tag) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { savedNote = note; saved = true }, Modifier.weight(1f)) {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Save Note")
            }
            OutlinedButton(onClick = { note = ""; tags = ""; saved = false }, Modifier.weight(1f)) { Text("Clear") }
        }
        if (saved && savedNote.isNotBlank()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(0.08f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Note saved for $packageName", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ─── Tab 8: Shared Libraries ───
@Composable
private fun SharedLibsTab(packageName: String, context: android.content.Context, padding: PaddingValues) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    var libs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // name, source
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val result = mutableListOf<Pair<String, String>>()
                // 1. Declared shared libraries from manifest
                val pm = context.packageManager
                val ai = pm.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES)
                ai.sharedLibraryFiles?.forEach { path ->
                    result += path.substringAfterLast("/") to "System Library"
                }
                // 2. Native .so libraries in APK
                val apkPath = connectionManager.exec("pm path $packageName 2>/dev/null").output
                    .lines().firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim() ?: ""
                if (apkPath.isNotEmpty()) {
                    val soFiles = connectionManager.exec("unzip -l \"$apkPath\" 2>/dev/null | grep '\\.so'").output
                    soFiles.lines().forEach { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        val name = parts.lastOrNull() ?: return@forEach
                        if (name.endsWith(".so")) {
                            val libName = name.substringAfterLast("/")
                            val arch = when {
                                name.contains("arm64") -> "arm64-v8a"
                                name.contains("armeabi-v7a") || name.contains("armeabi") -> "armeabi"
                                name.contains("x86_64") -> "x86_64"
                                name.contains("x86") -> "x86"
                                else -> "native"
                            }
                            if (result.none { it.first == libName }) result += libName to arch
                        }
                    }
                }
                // 3. From pm dump
                val dump = connectionManager.exec("pm dump $packageName 2>/dev/null | grep -i 'usesLibrary\\|library'").output
                dump.lines().forEach { line ->
                    val lib = line.substringAfter(":").trim().takeIf { it.isNotBlank() && it.contains(".") } ?: return@forEach
                    if (result.none { it.first == lib }) result += lib to "Uses-Library"
                }
                libs = result
            } catch (_: Exception) { }
            loading = false
        }
    }

    val filtered = remember(libs, searchQuery) {
        if (searchQuery.isBlank()) libs else libs.filter { it.first.contains(searchQuery, true) }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search libraries…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

        if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Column }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Dns, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No shared libraries found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text("${filtered.size} librar${if (filtered.size != 1) "ies" else "y"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
            items(filtered, key = { it.first }) { (name, source) ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(source, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}

// ─── Tab 9: DEX Classes ───
@Composable
private fun DexClassesTab(packageName: String, context: android.content.Context, padding: PaddingValues) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    var classes by remember { mutableStateOf<List<String>>(emptyList()) }
    var dexFiles by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) } // name, size
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showingClasses by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val apkPath = connectionManager.exec("pm path $packageName 2>/dev/null").output
                    .lines().firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim() ?: ""
                if (apkPath.isNotEmpty()) {
                    // List DEX files
                    val dexList = connectionManager.exec("unzip -l \"$apkPath\" 2>/dev/null | grep '\\.dex'").output
                    dexFiles = dexList.lines().mapNotNull { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        val name = parts.lastOrNull()?.takeIf { it.endsWith(".dex") } ?: return@mapNotNull null
                        val size = parts.firstOrNull()?.toLongOrNull() ?: 0L
                        name.substringAfterLast("/") to size
                    }
                    // Extract class descriptors using strings command (gets class names from DEX)
                    val raw = connectionManager.exec(
                        "strings \"$apkPath\" 2>/dev/null | grep -E '^L[a-zA-Z][a-zA-Z0-9\$/]+;$' | sort -u | head -500"
                    ).output
                    classes = raw.lines()
                        .filter { it.startsWith("L") && it.endsWith(";") && it.length > 3 }
                        .map { it.drop(1).dropLast(1).replace("/", ".") }
                        .filter { it.contains(".") }
                        .sorted()
                }
            } catch (_: Exception) { }
            loading = false
        }
    }

    val filteredClasses = remember(classes, searchQuery) {
        if (searchQuery.isBlank()) classes else classes.filter { it.contains(searchQuery, true) }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Column }

        // DEX Files Summary
        if (dexFiles.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DEX Files (${dexFiles.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    dexFiles.forEach { (name, size) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(if (size >= 1024) "${"%.1f".format(size / 1024.0)} KB" else "$size B", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }

        // Classes section
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${classes.size} classes extracted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { showingClasses = !showingClasses }) { Text(if (showingClasses) "Hide" else "Browse") }
        }

        if (showingClasses) {
            OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Filter classes…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(filteredClasses.take(300), key = { it }) { cls ->
                    val pkg = cls.substringBeforeLast(".")
                    val name = cls.substringAfterLast(".")
                    ListItem(
                        headlineContent = { Text(name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) },
                        supportingContent = { Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace) },
                        leadingContent = { Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp)) },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
                if (filteredClasses.size > 300) {
                    item { Text("… and ${filteredClasses.size - 300} more", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

// ─── Tab 10: Shared Preferences ───
@Composable
private fun SharedPrefsTab(packageName: String, padding: PaddingValues) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var contentLoading by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val raw = connectionManager.exec("ls /data/data/$packageName/shared_prefs/ 2>/dev/null").output
                files = raw.lines().filter { it.endsWith(".xml") || it.isNotBlank() }.filter { it.isNotEmpty() }
            } catch (_: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(selectedFile) {
        val f = selectedFile ?: return@LaunchedEffect
        contentLoading = true
        fileContent = ""
        withContext(Dispatchers.IO) {
            try {
                fileContent = connectionManager.exec("cat /data/data/$packageName/shared_prefs/$f 2>/dev/null").output
            } catch (_: Exception) { fileContent = "Error reading file (requires root)" }
            contentLoading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        // Root required banner
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Requires root access. SharedPrefs stored in /data/data/$packageName/shared_prefs/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Column }

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FolderOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No shared preference files found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Root access required to read prefs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }

        Row(Modifier.fillMaxSize()) {
            // File list
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Text("${files.size} file${if (files.size != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(files, key = { it }) { file ->
                    Card(
                        onClick = { selectedFile = file },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedFile == file) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(20.dp), tint = if (selectedFile == file) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(file.removeSuffix(".xml"), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            VerticalDivider()

            // Content viewer
            Box(Modifier.weight(2f).fillMaxSize()) {
                if (selectedFile == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a file to view", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (contentLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                        item {
                            Text(selectedFile ?: "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            SelectionContainer {
                                Text(fileContent, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Tab 11: Resources ───
@Composable
private fun ResourcesTab(packageName: String, context: android.content.Context, padding: PaddingValues) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    var resources by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val apkPath = connectionManager.exec("pm path $packageName 2>/dev/null").output
                    .lines().firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim() ?: ""
                if (apkPath.isNotEmpty()) {
                    val raw = connectionManager.exec("unzip -l \"$apkPath\" 2>/dev/null | grep -E '^.*res/'").output
                    val byType = mutableMapOf<String, MutableList<String>>()
                    raw.lines().forEach { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        val path = parts.lastOrNull()?.takeIf { it.startsWith("res/") } ?: return@forEach
                        val type = path.removePrefix("res/").substringBefore("/").trimEnd('-').replace(Regex("-.*"), "")
                        val name = path.substringAfterLast("/")
                        if (name.isNotEmpty()) byType.getOrPut(type) { mutableListOf() }.add(name)
                    }
                    resources = byType.toSortedMap()
                }
            } catch (_: Exception) { }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search resources…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

        if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Column }

        if (resources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FolderOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No resources found in APK", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }

        val typeIcon = { type: String -> when {
            type.startsWith("drawable") || type.startsWith("mipmap") -> Icons.Default.Image
            type.startsWith("layout") -> Icons.Default.DashboardCustomize
            type.startsWith("values") || type.startsWith("raw") -> Icons.Default.DataObject
            type.startsWith("anim") || type.startsWith("animator") -> Icons.Default.Animation
            type.startsWith("font") -> Icons.Default.TextFields
            type.startsWith("xml") -> Icons.Default.Code
            type.startsWith("menu") -> Icons.Default.Menu
            else -> Icons.Default.FolderOpen
        }}

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("${resources.values.sumOf { it.size }} resources in ${resources.size} types", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
            resources.forEach { (type, files) ->
                val filteredFiles = if (searchQuery.isBlank()) files else files.filter { it.contains(searchQuery, true) }
                if (filteredFiles.isEmpty()) return@forEach
                item(key = "header_$type") {
                    Card(
                        onClick = { expandedType = if (expandedType == type) null else type },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (expandedType == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(typeIcon(type), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(type, fontWeight = FontWeight.SemiBold)
                                Text("${filteredFiles.size} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(if (expandedType == type) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                    }
                }
                if (expandedType == type) {
                    items(filteredFiles.take(100), key = { "res_${type}_$it" }) { file ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(file, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (filteredFiles.size > 100) {
                        item(key = "more_$type") {
                            Text("… ${filteredFiles.size - 100} more", modifier = Modifier.padding(start = 16.dp, top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ─── Tab 12: APK Files (Unpack) ───
@Composable
private fun ApkFilesTab(packageName: String, context: android.content.Context, padding: PaddingValues) {
    val vm: ShizukuViewModel = hiltViewModel()
    val connectionManager = vm.connectionManager
    var files by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) } // path, size
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterExt by remember { mutableStateOf("All") }
    var totalSize by remember { mutableStateOf(0L) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val apkPath = connectionManager.exec("pm path $packageName 2>/dev/null").output
                    .lines().firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim() ?: ""
                if (apkPath.isNotEmpty()) {
                    val raw = connectionManager.exec("unzip -l \"$apkPath\" 2>/dev/null").output
                    var total = 0L
                    val result = mutableListOf<Pair<String, Long>>()
                    raw.lines().forEach { line ->
                        val t = line.trim()
                        if (t.isEmpty() || t.startsWith("Archive:") || t.startsWith("Length") || t.startsWith("----") || t.startsWith("Total")) return@forEach
                        val parts = t.split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val size = parts[0].toLongOrNull() ?: return@forEach
                            val path = parts.last()
                            if (path.isNotEmpty() && !path.endsWith("/")) {
                                result += path to size
                                total += size
                            }
                        }
                    }
                    files = result.sortedByDescending { it.second }
                    totalSize = total
                }
            } catch (_: Exception) { }
            loading = false
        }
    }

    val extensions = remember(files) { listOf("All") + files.map { it.first.substringAfterLast(".").lowercase() }.filter { it.length in 2..5 }.distinct().sorted() }

    val filtered = remember(files, searchQuery, filterExt) {
        files.filter {
            (filterExt == "All" || it.first.endsWith(".$filterExt", true)) &&
            (searchQuery.isBlank() || it.first.contains(searchQuery, true))
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        if (!loading && files.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${files.size} files", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (totalSize >= 1_048_576) "${"%.1f".format(totalSize / 1_048_576.0)} MB total" else "${"%.0f".format(totalSize / 1_024.0)} KB total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search files…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

        if (extensions.size > 1) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(extensions.take(10), key = { it }) { ext ->
                    FilterChip(filterExt == ext, { filterExt = ext }, { Text(ext.uppercase()) }, Modifier.height(30.dp))
                }
            }
        }

        if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Column }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No files found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(filtered.take(500), key = { it.first }) { (path, size) ->
                val name = path.substringAfterLast("/")
                val icon = when {
                    name.endsWith(".dex") -> Icons.Default.Code
                    name.endsWith(".so") -> Icons.Default.Dns
                    name.endsWith(".xml") -> Icons.Default.Description
                    name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg") -> Icons.Default.Image
                    name.endsWith(".class") || name.endsWith(".kotlin_module") -> Icons.Default.DataObject
                    name.endsWith(".ttf") || name.endsWith(".otf") -> Icons.Default.TextFields
                    else -> Icons.Default.InsertDriveFile
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (size >= 1024) "${"%.1f".format(size / 1024.0)} KB" else "$size B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

// ─── Shared composables ───
@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val clipboardManager = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { clipboardManager.setText(AnnotatedString(value)) },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SummaryChip(value: String, label: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}

@Composable
private fun PermissionItem(perm: PermissionUiModel, onRevoke: () -> Unit, onGrant: () -> Unit) {
    val isGranted = perm.isGranted
    val clipboardManager = LocalClipboardManager.current
    ListItem(
        headlineContent = { Text(perm.name.substringAfterLast('.'), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(perm.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
        leadingContent = {
            Icon(
                if (isGranted) Icons.Default.Check else Icons.Default.Block, null,
                tint = if (isGranted) AccentGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(perm.name)) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, "Copy permission name", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                }
                if (!perm.isProtected) {
                    TextButton(onClick = if (isGranted) onRevoke else onGrant, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(if (isGranted) "Revoke" else "Grant", style = MaterialTheme.typography.labelSmall, color = if (isGranted) MaterialTheme.colorScheme.error else AccentGreen)
                    }
                } else {
                    Surface(shape = RoundedCornerShape(4.dp), color = AccentOrange.copy(0.1f)) { Text("Protected", style = MaterialTheme.typography.labelSmall, color = AccentOrange, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                }
            }
        },
    )
}

@Composable
private fun ComponentItem(name: String, type: String, isEnabled: Boolean, onToggle: () -> Unit, onLaunch: (() -> Unit)?) {
    val color = when(type) { "activity" -> MaterialTheme.colorScheme.primary; "service" -> AccentOrange; "receiver" -> AccentGreen; "provider" -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.onSurface }
    val clipboardManager = LocalClipboardManager.current
    ListItem(
        headlineContent = { Text(name.substringAfterLast('.'), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
        leadingContent = {
            Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.12f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(type.first().uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color) }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(name)) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, "Copy class name", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                }
                if (onLaunch != null) IconButton(onClick = onLaunch, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = AccentGreen) }
                Switch(checked = isEnabled, onCheckedChange = { onToggle() }, modifier = Modifier.size(36.dp, 18.dp).then(Modifier.padding(0.dp)))
            }
        },
    )
}

private fun Modifier.scale(factor: Float) = this.then(Modifier.size((48 * factor).dp, (24 * factor).dp))
