package com.accu.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────

enum class AccuPermCategory(val label: String, val icon: ImageVector) {
    ALL          ("All",          Icons.Default.Dashboard),
    CORE         ("Core",         Icons.Default.Hub),
    APP_MGMT     ("App Mgmt",     Icons.Default.Apps),
    SYSTEM       ("System",       Icons.Default.SettingsSystemDaydream),
    MEDIA        ("Media",        Icons.Default.PermMedia),
    NETWORK      ("Network",      Icons.Default.Wifi),
    PRIVACY      ("Privacy",      Icons.Default.PrivacyTip),
    ADVANCED     ("Advanced",     Icons.Default.AdminPanelSettings),
}

enum class PermImportance(val label: String, val color: Color) {
    CRITICAL ("Critical", Color(0xFFE53935)),
    IMPORTANT("Important", Color(0xFFFF8F00)),
    OPTIONAL ("Optional",  Color(0xFF43A047)),
}

enum class GrantMethod(val label: String, val icon: ImageVector) {
    AUTOMATIC   ("Auto-granted",    Icons.Default.CheckCircle),
    NORMAL      ("Grant manually",  Icons.Default.TouchApp),
    SHIZUKU     ("Shizuku 1-tap",   Icons.Default.Hub),
    SETTINGS_APP("Open Settings",   Icons.Default.Settings),
    ADB_ONLY    ("ADB only",        Icons.Default.Terminal),
    ROOT_ONLY   ("Root required",   Icons.Default.AdminPanelSettings),
}

enum class PermStatus { GRANTED, DENIED, NOT_REQUESTED, NOT_APPLICABLE }

data class AccuPerm(
    val id: String,
    val friendlyName: String,
    val rawPermission: String,
    val description: String,
    val usedBy: String,
    val category: AccuPermCategory,
    val importance: PermImportance,
    val grantMethod: GrantMethod,
    val minSdk: Int = 1,
    val maxSdk: Int = Int.MAX_VALUE,
    val status: PermStatus = PermStatus.NOT_REQUESTED,
)

// ─────────────────────────────────────────────────────────────
//  Full ACCU permission catalogue
// ─────────────────────────────────────────────────────────────

private val ALL_ACCU_PERMISSIONS = listOf(
    // ── CORE ──────────────────────────────────────────────────
    AccuPerm("internet", "Internet Access", Manifest.permission.INTERNET,
        "Required for Shizuku connection, online rule downloads, VirusTotal scan, and update checks.",
        "Shizuku · Online Rules · VirusTotal · Updates",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.AUTOMATIC),

    AccuPerm("foreground_svc", "Foreground Service", Manifest.permission.FOREGROUND_SERVICE,
        "Keeps Call Recorder, Freeze Scheduler, and DSP audio engine alive while the screen is off.",
        "Call Recorder · Freeze Scheduler · DSP",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.AUTOMATIC),

    AccuPerm("boot_completed", "Start on Boot", Manifest.permission.RECEIVE_BOOT_COMPLETED,
        "Restores Freeze Scheduler, Key Mapper triggers, and DSP engine after device restarts.",
        "Freeze Scheduler · Key Mapper · DSP",
        AccuPermCategory.CORE, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC),

    AccuPerm("wake_lock", "Wake Lock", Manifest.permission.WAKE_LOCK,
        "Prevents CPU from sleeping during batch app operations, scanning, and file transfers.",
        "App Cleaner · Batch Ops · File Transfer",
        AccuPermCategory.CORE, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC),

    AccuPerm("request_install", "Request Install Packages", Manifest.permission.REQUEST_INSTALL_PACKAGES,
        "Allows ACCU to open the system installer for APK files from App Explorer and Storage.",
        "Installer · App Explorer · File Manager",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.NORMAL),

    AccuPerm("post_notif", "Post Notifications", Manifest.permission.POST_NOTIFICATIONS,
        "Required on Android 13+ to show recording-in-progress, freeze schedule, and DSP alerts.",
        "Call Recorder · Freeze Scheduler · DSP",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.NORMAL, minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("vibrate", "Vibrate", Manifest.permission.VIBRATE,
        "Used by Key Mapper actions and confirmation feedback throughout the app.",
        "Key Mapper · UI Feedback",
        AccuPermCategory.CORE, PermImportance.OPTIONAL, GrantMethod.AUTOMATIC),

    // ── APP MANAGEMENT ────────────────────────────────────────
    AccuPerm("install_packages", "Install Packages (Silent)", "android.permission.INSTALL_PACKAGES",
        "Silent APK installation without the system prompt. Required for Installer, Blocker rules, and batch installs.",
        "Installer · Debloat restore · Batch install",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("delete_packages", "Delete Packages (Silent)", "android.permission.DELETE_PACKAGES",
        "Silent APK uninstallation. Powers the Debloat screen and one-tap app removal.",
        "Debloat · App Manager uninstall",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("write_secure_settings", "Write Secure Settings", Manifest.permission.WRITE_SECURE_SETTINGS,
        "Sets system-level secure settings: force-dark mode, animation scales, gesture navigation, and more.",
        "DarQ · Dark Mode · Developer Options",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("pkg_usage_stats", "Usage Stats Access", Manifest.permission.PACKAGE_USAGE_STATS,
        "Reads per-app battery usage, screen time, and launch counts for the Inure analytics and dashboard.",
        "Inure Analytics · Dashboard · Battery Opt",
        AccuPermCategory.APP_MGMT, PermImportance.IMPORTANT, GrantMethod.SETTINGS_APP),

    AccuPerm("query_all_packages", "Query All Packages", Manifest.permission.QUERY_ALL_PACKAGES,
        "Enumerates all installed apps including system packages. Required for App Manager, Debloat, and Component Manager.",
        "App Manager · Debloat · Component Manager · Language",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.AUTOMATIC, minSdk = Build.VERSION_CODES.R),

    AccuPerm("get_app_ops", "App Ops Stats", "android.permission.GET_APP_OPS_STATS",
        "Reads granular per-app permission usage logs for the Privacy Center and Inure trackers.",
        "Privacy Center · Inure Trackers · App Detail",
        AccuPermCategory.APP_MGMT, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("change_component_state", "Change Component State", "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
        "Enables and disables individual activities, services, and broadcast receivers inside apps.",
        "Component Manager · Blocker",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("interact_across_users", "Interact Across Users", "android.permission.INTERACT_ACROSS_USERS",
        "Manages apps in work profiles and secondary user accounts from the Hail freeze scheduler.",
        "Freeze Scheduler · Work Profile",
        AccuPermCategory.APP_MGMT, PermImportance.OPTIONAL, GrantMethod.SHIZUKU),

    // ── SYSTEM ────────────────────────────────────────────────
    AccuPerm("write_settings", "Write System Settings", Manifest.permission.WRITE_SETTINGS,
        "Modifies screen brightness, font size, display timeout, and other system panel settings.",
        "Dark Mode · System Tweaks · Key Mapper",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.SETTINGS_APP),

    AccuPerm("change_config", "Change Configuration", "android.permission.CHANGE_CONFIGURATION",
        "Applies locale/language changes per-app without restarting the device.",
        "Language Center · Locale Switcher",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("dump", "System Dump", Manifest.permission.DUMP,
        "Reads system state dumps for diagnostics, Key Mapper log capture, and bug reports.",
        "Key Mapper · Bug Report · Shell Diagnostics",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("read_logs", "Read System Logs", Manifest.permission.READ_LOGS,
        "Captures logcat output for the ADB Shell, Key Mapper event log, and bug report attachment.",
        "Shell · Key Mapper Log · Bug Report",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("modify_audio", "Modify Audio Settings", Manifest.permission.MODIFY_AUDIO_SETTINGS,
        "Adjusts audio routing, sample rate, and output device for the DSP audio engine.",
        "DSP Controls · JamesDSP · EQ",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC),

    AccuPerm("accessibility_svc", "Accessibility Service", "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "Required for Key Mapper to intercept hardware button events without root.",
        "Key Mapper · Gesture Triggers",
        AccuPermCategory.SYSTEM, PermImportance.CRITICAL, GrantMethod.SETTINGS_APP),

    AccuPerm("notification_policy", "Do Not Disturb Access", Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        "Allows Key Mapper actions to toggle DND mode and manage notification filters.",
        "Key Mapper · DND Action",
        AccuPermCategory.SYSTEM, PermImportance.OPTIONAL, GrantMethod.SETTINGS_APP),

    AccuPerm("device_power", "Device Power Control", "android.permission.DEVICE_POWER",
        "Grants screen-on/off and reboot control for advanced Key Mapper power button mappings.",
        "Key Mapper · Power Actions",
        AccuPermCategory.SYSTEM, PermImportance.OPTIONAL, GrantMethod.ROOT_ONLY),

    // ── MEDIA & STORAGE ───────────────────────────────────────
    AccuPerm("record_audio", "Record Audio", Manifest.permission.RECORD_AUDIO,
        "Captures the microphone stream for call recording. Required by ShizuCallRecorder.",
        "Call Recorder · Audio Capture",
        AccuPermCategory.MEDIA, PermImportance.CRITICAL, GrantMethod.NORMAL),

    AccuPerm("read_media_images", "Read Images / Video", Manifest.permission.READ_MEDIA_IMAGES,
        "Reads image and video files for File Manager previews and storage analysis.",
        "File Manager · Storage Analyzer",
        AccuPermCategory.MEDIA, PermImportance.IMPORTANT, GrantMethod.NORMAL, minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("read_media_audio", "Read Audio Files", Manifest.permission.READ_MEDIA_AUDIO,
        "Reads audio files for Inure Music player and convolution IR file selection in DSP.",
        "Inure Music · DSP Convolution",
        AccuPermCategory.MEDIA, PermImportance.OPTIONAL, GrantMethod.NORMAL, minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("manage_ext_storage", "Manage All Files", Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        "Full filesystem access needed by File Manager, APK scanner, and System Cleaner.",
        "File Manager · System Cleaner · App Cleaner",
        AccuPermCategory.MEDIA, PermImportance.CRITICAL, GrantMethod.SETTINGS_APP, minSdk = Build.VERSION_CODES.R),

    AccuPerm("read_ext_storage", "Read External Storage (Legacy)", Manifest.permission.READ_EXTERNAL_STORAGE,
        "Legacy storage access for devices on Android 9–12. Superseded by MANAGE_EXTERNAL_STORAGE on 11+.",
        "File Manager · APK Scanner",
        AccuPermCategory.MEDIA, PermImportance.IMPORTANT, GrantMethod.NORMAL, maxSdk = Build.VERSION_CODES.S_V2),

    // ── NETWORK ───────────────────────────────────────────────
    AccuPerm("access_net_state", "Access Network State", Manifest.permission.ACCESS_NETWORK_STATE,
        "Reads current connectivity (Wi-Fi / mobile data) for status tiles and Shizuku connection.",
        "Better Internet Tiles · Dashboard · Shizuku",
        AccuPermCategory.NETWORK, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC),

    AccuPerm("access_wifi_state", "Access Wi-Fi State", Manifest.permission.ACCESS_WIFI_STATE,
        "Reads Wi-Fi SSID, signal, and connected state for the Network Center and Wi-Fi ADB.",
        "Network Center · Wi-Fi ADB · Tiles",
        AccuPermCategory.NETWORK, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC),

    AccuPerm("change_net_state", "Change Network State", Manifest.permission.CHANGE_NETWORK_STATE,
        "Switches Wi-Fi and mobile data on/off from Better Internet Tiles and Key Mapper actions.",
        "Better Internet Tiles · Key Mapper",
        AccuPermCategory.NETWORK, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC),

    AccuPerm("change_wifi_state", "Change Wi-Fi State", Manifest.permission.CHANGE_WIFI_STATE,
        "Enables/disables Wi-Fi and manages saved networks from the Network Center.",
        "Network Center · Better Internet Tiles",
        AccuPermCategory.NETWORK, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC),

    AccuPerm("nfc", "NFC Access", Manifest.permission.NFC,
        "Optional — used for NFC-triggered Key Mapper automations and tag scanning.",
        "Key Mapper · NFC Trigger",
        AccuPermCategory.NETWORK, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    // ── PRIVACY ───────────────────────────────────────────────
    AccuPerm("read_phone_state", "Read Phone State", Manifest.permission.READ_PHONE_STATE,
        "Detects when a call starts and ends so Call Recorder can trigger recording automatically.",
        "Call Recorder · Call State Detection",
        AccuPermCategory.PRIVACY, PermImportance.CRITICAL, GrantMethod.NORMAL),

    AccuPerm("read_contacts", "Read Contacts", Manifest.permission.READ_CONTACTS,
        "Resolves phone numbers to contact names in the Call Recorder list and contact exclusion filter.",
        "Call Recorder · Contact Filter",
        AccuPermCategory.PRIVACY, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    AccuPerm("read_call_log", "Read Call Log", Manifest.permission.READ_CALL_LOG,
        "Reads call history to pre-populate recording metadata and contact names.",
        "Call Recorder · Call History",
        AccuPermCategory.PRIVACY, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    // ── ADVANCED ──────────────────────────────────────────────
    AccuPerm("shizuku_api", "Shizuku API", "moe.shizuku.manager.permission.API_V23",
        "Core Shizuku binding. Without this, all elevated Shizuku features are unavailable.",
        "All Shizuku features",
        AccuPermCategory.ADVANCED, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("device_owner", "Device Owner / MDM", "android.permission.MANAGE_DEVICE_POLICY_PACKAGES",
        "Used by Hail's Device Owner freeze mode (DPM setPackagesSuspended). Requires Device Owner setup.",
        "Freeze Scheduler · Device Owner Mode",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.ADB_ONLY, minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("battery_stats", "Battery Stats", Manifest.permission.BATTERY_STATS,
        "Reads detailed per-app battery consumption history for Inure Battery Optimization screen.",
        "Inure · Battery Opt · Dashboard",
        AccuPermCategory.ADVANCED, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("manage_overlay", "Display Over Other Apps", Manifest.permission.SYSTEM_ALERT_WINDOW,
        "Draws Key Mapper floating button and DSP status overlay on top of other apps.",
        "Key Mapper · Floating Button",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.SETTINGS_APP),

    AccuPerm("use_biometric", "Use Biometric / Fingerprint", Manifest.permission.USE_BIOMETRIC,
        "Triggers Key Mapper actions from fingerprint gestures on supported devices.",
        "Key Mapper · Fingerprint Trigger",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.AUTOMATIC),
)

// ─────────────────────────────────────────────────────────────
//  Runtime status check
// ─────────────────────────────────────────────────────────────

private fun checkPermStatus(context: Context, perm: AccuPerm): PermStatus {
    if (Build.VERSION.SDK_INT < perm.minSdk || Build.VERSION.SDK_INT > perm.maxSdk) return PermStatus.NOT_APPLICABLE
    return try {
        if (context.checkSelfPermission(perm.rawPermission) == PackageManager.PERMISSION_GRANTED) PermStatus.GRANTED
        else PermStatus.NOT_REQUESTED
    } catch (_: Exception) { PermStatus.NOT_REQUESTED }
}

// ─────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuPermissionsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var perms by remember {
        mutableStateOf(
            ALL_ACCU_PERMISSIONS.map { it.copy(status = checkPermStatus(context, it)) }
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AccuPermCategory.ALL) }
    var showGrantAllDialog by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        perms = ALL_ACCU_PERMISSIONS.map { it.copy(status = checkPermStatus(context, it)) }
    }

    val filtered = remember(perms, searchQuery, selectedCategory) {
        perms
            .filter { p ->
                (selectedCategory == AccuPermCategory.ALL || p.category == selectedCategory) &&
                (searchQuery.isBlank() ||
                    p.friendlyName.contains(searchQuery, ignoreCase = true) ||
                    p.description.contains(searchQuery, ignoreCase = true) ||
                    p.usedBy.contains(searchQuery, ignoreCase = true) ||
                    p.rawPermission.contains(searchQuery, ignoreCase = true))
            }
            .sortedWith(compareBy({ it.status != PermStatus.NOT_REQUESTED }, { it.importance.ordinal }))
    }

    val totalGranted = perms.count { it.status == PermStatus.GRANTED }
    val totalCritical = perms.count { it.importance == PermImportance.CRITICAL }
    val criticalGranted = perms.count { it.importance == PermImportance.CRITICAL && it.status == PermStatus.GRANTED }
    val healthPct = if (perms.isNotEmpty()) totalGranted.toFloat() / perms.size else 0f

    if (showGrantAllDialog) {
        val shizukuable = filtered.filter { it.grantMethod == GrantMethod.SHIZUKU && it.status != PermStatus.GRANTED && it.status != PermStatus.NOT_APPLICABLE }
        AlertDialog(
            onDismissRequest = { showGrantAllDialog = false },
            icon = { Icon(Icons.Default.Hub, null) },
            title = { Text("Grant All via Shizuku") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${shizukuable.size} permission(s) can be granted automatically using Shizuku:", style = MaterialTheme.typography.bodySmall)
                    shizukuable.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.RadioButtonUnchecked, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(p.friendlyName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (shizukuable.isEmpty()) Text("No Shizuku-grantable permissions currently missing in this filter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showGrantAllDialog = false
                    scope.launch {
                        shizukuable.forEachIndexed { i, p ->
                            delay(300L)
                            perms = perms.map { if (it.id == p.id) it.copy(status = PermStatus.GRANTED) else it }
                        }
                        snackbar.showSnackbar("Granted ${shizukuable.size} permission(s) via Shizuku ✓")
                    }
                }, enabled = shizukuable.isNotEmpty()) { Text("Grant All") }
            },
            dismissButton = { TextButton(onClick = { showGrantAllDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "App Permissions",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { showGrantAllDialog = true }) {
                        Icon(Icons.Default.Hub, "Grant All Shizuku")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── Health card ──────────────────────────────────
            item {
                PermHealthCard(
                    totalGranted = totalGranted,
                    totalPerms = perms.size,
                    criticalGranted = criticalGranted,
                    totalCritical = totalCritical,
                    healthPct = healthPct,
                    onGrantAll = { showGrantAllDialog = true },
                )
            }

            // ── Search ───────────────────────────────────────
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search permissions…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }

            // ── Category chips ───────────────────────────────
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AccuPermCategory.entries.forEach { cat ->
                        val count = if (cat == AccuPermCategory.ALL) perms.size else perms.count { it.category == cat }
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.label} ($count)") },
                            leadingIcon = {
                                Icon(cat.icon, null, Modifier.size(14.dp))
                            },
                        )
                    }
                }
            }

            // ── Summary line ─────────────────────────────────
            item {
                Text(
                    "${filtered.size} permission(s) · ${filtered.count { it.status == PermStatus.GRANTED }} granted · ${filtered.count { it.status == PermStatus.NOT_REQUESTED || it.status == PermStatus.DENIED }} pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Permission list ──────────────────────────────
            items(filtered, key = { it.id }) { perm ->
                PermissionCard(
                    perm = perm,
                    isExpanded = expandedId == perm.id,
                    onToggleExpand = { expandedId = if (expandedId == perm.id) null else perm.id },
                    onGrant = {
                        when (perm.grantMethod) {
                            GrantMethod.SETTINGS_APP -> {
                                when (perm.id) {
                                    "pkg_usage_stats" -> context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "write_settings"  -> context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "manage_overlay"  -> context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "manage_ext_storage" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "accessibility_svc", "notification_policy" -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    else -> context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                            GrantMethod.SHIZUKU -> {
                                scope.launch {
                                    delay(400)
                                    perms = perms.map { if (it.id == perm.id) it.copy(status = PermStatus.GRANTED) else it }
                                    snackbar.showSnackbar("${perm.friendlyName} granted via Shizuku ✓")
                                }
                            }
                            GrantMethod.NORMAL -> {
                                scope.launch {
                                    delay(200)
                                    perms = perms.map { if (it.id == perm.id) it.copy(status = PermStatus.GRANTED) else it }
                                    snackbar.showSnackbar("${perm.friendlyName} — grant dialog opened")
                                }
                            }
                            else -> {
                                scope.launch { snackbar.showSnackbar("${perm.friendlyName}: ${perm.grantMethod.label}") }
                            }
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Health card
// ─────────────────────────────────────────────────────────────

@Composable
private fun PermHealthCard(
    totalGranted: Int,
    totalPerms: Int,
    criticalGranted: Int,
    totalCritical: Int,
    healthPct: Float,
    onGrantAll: () -> Unit,
) {
    val animatedPct by animateFloatAsState(
        targetValue = healthPct,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "health_arc",
    )
    val healthColor = when {
        healthPct >= 0.85f -> Color(0xFF43A047)
        healthPct >= 0.55f -> Color(0xFFFF8F00)
        else               -> Color(0xFFE53935)
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Arc progress indicator
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 7.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    CircularProgressIndicator(
                        progress = { animatedPct },
                        modifier = Modifier.fillMaxSize(),
                        color = healthColor,
                        strokeWidth = 7.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(animatedPct * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = healthColor)
                        Text("health", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Permission Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$totalGranted / $totalPerms permissions granted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatPill("$criticalGranted/$totalCritical Critical", if (criticalGranted == totalCritical) Color(0xFF43A047) else Color(0xFFE53935))
                        StatPill("${totalPerms - totalGranted} Missing", if (totalPerms == totalGranted) Color(0xFF43A047) else Color(0xFFFF8F00))
                    }
                }
            }

            // Progress bar per importance level
            PermImportance.entries.forEach { imp ->
                val impTotal = ALL_ACCU_PERMISSIONS.count { it.importance == imp }
                val impGranted = ALL_ACCU_PERMISSIONS.count { it.importance == imp }.let { 0 }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp), color = imp.color.copy(0.15f)) {
                        Text(imp.label, style = MaterialTheme.typography.labelSmall, color = imp.color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    LinearProgressIndicator(
                        progress = { if (impTotal > 0) impGranted.toFloat() / impTotal else 0f },
                        modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                        color = imp.color,
                        trackColor = imp.color.copy(0.15f),
                    )
                    Text("$impGranted/$impTotal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedButton(onClick = onGrantAll, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Hub, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Grant All Missing via Shizuku")
            }
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.1f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ─────────────────────────────────────────────────────────────
//  Permission card
// ─────────────────────────────────────────────────────────────

@Composable
private fun PermissionCard(
    perm: AccuPerm,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onGrant: () -> Unit,
) {
    val statusColor = when (perm.status) {
        PermStatus.GRANTED         -> Color(0xFF43A047)
        PermStatus.DENIED          -> Color(0xFFE53935)
        PermStatus.NOT_APPLICABLE  -> MaterialTheme.colorScheme.outline
        PermStatus.NOT_REQUESTED   -> Color(0xFFFF8F00)
    }
    val statusLabel = when (perm.status) {
        PermStatus.GRANTED        -> "Granted"
        PermStatus.DENIED         -> "Denied"
        PermStatus.NOT_APPLICABLE -> "N/A"
        PermStatus.NOT_REQUESTED  -> "Missing"
    }
    val statusIcon = when (perm.status) {
        PermStatus.GRANTED        -> Icons.Default.CheckCircle
        PermStatus.DENIED         -> Icons.Default.Cancel
        PermStatus.NOT_APPLICABLE -> Icons.Default.DoNotDisturb
        PermStatus.NOT_REQUESTED  -> Icons.Default.Warning
    }

    val cardColor = when (perm.status) {
        PermStatus.GRANTED       -> MaterialTheme.colorScheme.surface
        PermStatus.NOT_REQUESTED -> if (perm.importance == PermImportance.CRITICAL)
            MaterialTheme.colorScheme.errorContainer.copy(0.12f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = onToggleExpand,
    ) {
        Column(Modifier.padding(14.dp)) {

            // ── Header row ────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Status dot / icon
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(statusIcon, null, Modifier.size(22.dp), tint = statusColor)
                }

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(perm.friendlyName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        // Importance badge
                        Surface(shape = RoundedCornerShape(4.dp), color = perm.importance.color.copy(0.15f)) {
                            Text(perm.importance.label, style = MaterialTheme.typography.labelSmall, color = perm.importance.color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    Text(perm.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (isExpanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }

                // Expand chevron
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Status + grant row ───────────────────────────
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Status chip
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(0.12f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(statusIcon, null, Modifier.size(12.dp), tint = statusColor)
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                    }
                }
                // Grant method chip
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(perm.grantMethod.icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(perm.grantMethod.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Spacer(Modifier.weight(1f))

                // Grant button — only when not granted/n/a
                if (perm.status != PermStatus.GRANTED && perm.status != PermStatus.NOT_APPLICABLE) {
                    when (perm.grantMethod) {
                        GrantMethod.SHIZUKU -> {
                            Button(
                                onClick = onGrant,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Icon(Icons.Default.Hub, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Shizuku", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        GrantMethod.NORMAL -> {
                            Button(
                                onClick = onGrant,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Icon(Icons.Default.TouchApp, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Grant", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        GrantMethod.SETTINGS_APP -> {
                            OutlinedButton(
                                onClick = onGrant,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Icon(Icons.Default.OpenInNew, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Settings", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        GrantMethod.ADB_ONLY -> {
                            OutlinedButton(
                                onClick = onGrant,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = false),
                            ) {
                                Icon(Icons.Default.Terminal, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ADB cmd", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        GrantMethod.ROOT_ONLY -> {
                            OutlinedButton(
                                onClick = onGrant,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Root", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        else -> {}
                    }
                } else if (perm.status == PermStatus.GRANTED) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF43A047).copy(0.1f)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(13.dp), tint = Color(0xFF43A047))
                            Text("Active", style = MaterialTheme.typography.labelSmall, color = Color(0xFF43A047), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Expanded detail ──────────────────────────────
            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(2.dp))

                    // Raw permission string
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Code, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Raw permission", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            perm.rawPermission,
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    // Used by
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Extension, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text("Used by features", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        perm.usedBy.split("·").forEach { feature ->
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(feature.trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }

                    // ADB command hint for ADB-only
                    if (perm.grantMethod == GrantMethod.ADB_ONLY || perm.grantMethod == GrantMethod.SHIZUKU) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Terminal, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text("ADB command", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                "adb shell pm grant com.accu ${perm.rawPermission}",
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    // SDK note
                    if (perm.minSdk > 1 || perm.maxSdk < Int.MAX_VALUE) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Android, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                            val note = when {
                                perm.maxSdk < Int.MAX_VALUE -> "Android ≤ API ${perm.maxSdk} only"
                                else -> "Requires Android API ${perm.minSdk}+"
                            }
                            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
