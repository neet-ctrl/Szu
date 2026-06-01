package com.accu.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.ACCTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════
//  DATA MODELS
// ═══════════════════════════════════════════════════════════

enum class AccuPermCategory(val label: String, val icon: ImageVector) {
    ALL          ("All",          Icons.Default.Dashboard),
    CORE         ("Core",         Icons.Default.Hub),
    APP_MGMT     ("App Mgmt",     Icons.Default.Apps),
    SYSTEM       ("System",       Icons.Default.SettingsSystemDaydream),
    MEDIA        ("Media",        Icons.Default.PermMedia),
    NETWORK      ("Network",      Icons.Default.Wifi),
    BLUETOOTH    ("Bluetooth",    Icons.Default.Bluetooth),
    PRIVACY      ("Privacy",      Icons.Default.PrivacyTip),
    ADVANCED     ("Advanced",     Icons.Default.AdminPanelSettings),
}

enum class PermImportance(val label: String, val color: Color) {
    CRITICAL ("Critical", Color(0xFFE53935)),
    IMPORTANT("Important", Color(0xFFFF8F00)),
    OPTIONAL ("Optional",  Color(0xFF43A047)),
}

enum class GrantMethod(val label: String, val icon: ImageVector) {
    AUTOMATIC   ("Auto-granted",   Icons.Default.CheckCircle),
    NORMAL      ("Grant manually", Icons.Default.TouchApp),
    SHIZUKU     ("ACCU 1-tap",     Icons.Default.Hub),
    SETTINGS_APP("Open Settings",  Icons.Default.Settings),
    ADB_ONLY    ("ADB only",       Icons.Default.Terminal),
    ROOT_ONLY   ("Root required",  Icons.Default.AdminPanelSettings),
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
    val canRevoke: Boolean = true,
)

enum class SortMode(val label: String) {
    STATUS("By Status"),
    IMPORTANCE("By Importance"),
    CATEGORY("By Category"),
    NAME("By Name"),
}

/** 3-tier grant classification the user asked for. */
enum class GrantTier(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ALL          ("All",             Icons.Default.Dashboard),
    AUTO_INSTALL ("Auto on Install", Icons.Default.CheckCircle),
    MANUAL       ("Manual Grant",    Icons.Default.TouchApp),
    AUTO_ADB     ("Auto via ADB",    Icons.Default.Hub),
}

private fun permGrantTier(perm: AccuPerm): GrantTier = when (perm.grantMethod) {
    GrantMethod.AUTOMATIC -> GrantTier.AUTO_INSTALL
    GrantMethod.SHIZUKU   -> GrantTier.AUTO_ADB
    else                  -> GrantTier.MANUAL
}

// ═══════════════════════════════════════════════════════════
//  FULL PERMISSION CATALOGUE  (55 permissions)
// ═══════════════════════════════════════════════════════════

private val ALL_ACCU_PERMISSIONS = listOf(

    // ── CORE ────────────────────────────────────────────────
    AccuPerm("internet", "Internet Access", Manifest.permission.INTERNET,
        "Required for ACCU wireless-ADB connection, online rule downloads, VirusTotal scanning, and update checks.",
        "ACCU Center · Online Rules · VirusTotal · Updates",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("foreground_svc", "Foreground Service", Manifest.permission.FOREGROUND_SERVICE,
        "Keeps Call Recorder, Freeze Scheduler, DSP audio engine, and Key Mapper trigger service alive.",
        "Call Recorder · Freeze Scheduler · DSP · Key Mapper",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("foreground_svc_mic", "Foreground Service – Microphone",
        "android.permission.FOREGROUND_SERVICE_MICROPHONE",
        "Required on Android 14+ for the Call Recorder foreground service to hold a live microphone session.",
        "Call Recorder",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.AUTOMATIC,
        minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, canRevoke = false),

    AccuPerm("foreground_svc_media", "Foreground Service – Media Playback",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        "Required on Android 14+ so the DSP audio service can run in a media-playback foreground context.",
        "DSP / JamesDSP Audio Engine",
        AccuPermCategory.CORE, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC,
        minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, canRevoke = false),

    AccuPerm("foreground_svc_bt", "Foreground Service – Connected Device",
        "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
        "Required on Android 14+ for the Key Mapper service to maintain a Bluetooth device connection.",
        "Key Mapper · Bluetooth Triggers",
        AccuPermCategory.CORE, PermImportance.OPTIONAL, GrantMethod.AUTOMATIC,
        minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, canRevoke = false),

    AccuPerm("boot_completed", "Start on Boot", Manifest.permission.RECEIVE_BOOT_COMPLETED,
        "Restores Freeze Scheduler, Key Mapper triggers, and DSP engine after device restarts automatically.",
        "Freeze Scheduler · Key Mapper · DSP",
        AccuPermCategory.CORE, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("wake_lock", "Wake Lock", Manifest.permission.WAKE_LOCK,
        "Prevents CPU from sleeping during batch app operations, cache scanning, and file transfers.",
        "App Cleaner · Batch Ops · File Transfer",
        AccuPermCategory.CORE, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("request_install", "Request Install Packages", Manifest.permission.REQUEST_INSTALL_PACKAGES,
        "Allows ACCU to trigger the system APK installer dialog from App Explorer, File Manager, and Installer.",
        "Installer · App Explorer · File Manager",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.NORMAL),

    AccuPerm("post_notif", "Post Notifications", Manifest.permission.POST_NOTIFICATIONS,
        "Required on Android 13+ to show call-recording status, freeze-schedule alerts, and DSP notifications.",
        "Call Recorder · Freeze Scheduler · DSP",
        AccuPermCategory.CORE, PermImportance.CRITICAL, GrantMethod.NORMAL,
        minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("vibrate", "Vibrate", Manifest.permission.VIBRATE,
        "Used by Key Mapper haptic feedback actions and confirmation vibrations across the app.",
        "Key Mapper · UI Feedback",
        AccuPermCategory.CORE, PermImportance.OPTIONAL, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("exact_alarm", "Schedule Exact Alarm",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.SCHEDULE_EXACT_ALARM
        else "android.permission.SCHEDULE_EXACT_ALARM",
        "Lets the Freeze Scheduler trigger precisely at user-specified times, even in Doze mode.",
        "Freeze Scheduler · Timed Automations",
        AccuPermCategory.CORE, PermImportance.IMPORTANT, GrantMethod.SETTINGS_APP,
        minSdk = Build.VERSION_CODES.S),

    AccuPerm("use_exact_alarm", "Use Exact Alarm",
        "android.permission.USE_EXACT_ALARM",
        "Unrestricted exact alarm access for calendar-accurate freeze and automation triggers on Android 13+.",
        "Freeze Scheduler",
        AccuPermCategory.CORE, PermImportance.OPTIONAL, GrantMethod.AUTOMATIC,
        minSdk = Build.VERSION_CODES.TIRAMISU, canRevoke = false),

    // ── APP MANAGEMENT ──────────────────────────────────────
    AccuPerm("install_packages", "Install Packages (Silent)", "android.permission.INSTALL_PACKAGES",
        "Silent APK installation without system prompt — powers Installer Center, Blocker rule restore, and batch installs.",
        "Installer · Debloat restore · Batch install",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("delete_packages", "Delete Packages (Silent)", "android.permission.DELETE_PACKAGES",
        "Silent app removal without confirmation dialogs. Powers the Debloat screen and one-tap system-app removal.",
        "Debloat · App Manager · Canta",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("write_secure_settings", "Write Secure Settings", Manifest.permission.WRITE_SECURE_SETTINGS,
        "Sets system-level secure settings: per-app dark mode (DarQ), animation scales, gesture nav, and more.",
        "DarQ · Dark Mode · Developer Options · Language Selector",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("pkg_usage_stats", "Usage Stats Access", Manifest.permission.PACKAGE_USAGE_STATS,
        "Reads per-app battery, screen time, and launch counts for Inure analytics and the dashboard.",
        "Inure Analytics · Dashboard · Battery Opt",
        AccuPermCategory.APP_MGMT, PermImportance.IMPORTANT, GrantMethod.SETTINGS_APP),

    AccuPerm("query_all_packages", "Query All Packages", Manifest.permission.QUERY_ALL_PACKAGES,
        "Enumerates all installed apps including system packages. Required for App Manager, Debloat, Language Selector, and Component Manager.",
        "App Manager · Debloat · Component Manager · Language",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.AUTOMATIC,
        minSdk = Build.VERSION_CODES.R, canRevoke = false),

    AccuPerm("get_app_ops", "App Ops Stats", "android.permission.GET_APP_OPS_STATS",
        "Reads granular per-app permission usage logs for Privacy Center and Inure tracker screens.",
        "Privacy Center · Inure Trackers · App Detail",
        AccuPermCategory.APP_MGMT, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("change_component_state", "Change Component State",
        "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
        "Enables and disables individual activities, services, and broadcast receivers inside other apps.",
        "Component Manager · Blocker",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("interact_across_users", "Interact Across Users",
        "android.permission.INTERACT_ACROSS_USERS",
        "Manages apps in work profiles and secondary user accounts from Hail's freeze engine.",
        "Freeze Scheduler · Work Profile · Hail",
        AccuPermCategory.APP_MGMT, PermImportance.OPTIONAL, GrantMethod.SHIZUKU),

    AccuPerm("force_stop", "Force Stop Packages",
        "android.permission.FORCE_STOP_PACKAGES",
        "Force-stops background processes of any app. Used by Freeze Scheduler and App Manager.",
        "Freeze Scheduler · App Manager",
        AccuPermCategory.APP_MGMT, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("suspend_apps", "Suspend / Hide Apps",
        "android.permission.SUSPEND_APPS",
        "Suspends apps so they cannot be launched — the core of Hail freeze functionality via ACCU.",
        "Hail / Freeze Apps · Debloat",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("clear_app_cache", "Clear App Cache",
        "android.permission.CLEAR_APP_CACHE",
        "Clears individual app cache dirs without user interaction, used by SD Maid App Cleaner.",
        "SD Maid · App Cleaner",
        AccuPermCategory.APP_MGMT, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("kill_bg_processes", "Kill Background Processes",
        Manifest.permission.KILL_BACKGROUND_PROCESSES,
        "Terminates background processes for any app. Used by App Manager's force-stop and batch-kill actions.",
        "App Manager · Batch Ops",
        AccuPermCategory.APP_MGMT, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    AccuPerm("manage_app_ops", "Manage App Ops Modes",
        "android.permission.MANAGE_APP_OPS_MODES",
        "Grants and revokes individual app-ops flags (camera, mic, location) for any app via ACCU.",
        "Privacy Center · App Ops Manager",
        AccuPermCategory.APP_MGMT, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("set_preferred_apps", "Set Preferred Applications",
        "android.permission.SET_PREFERRED_APPLICATIONS",
        "Sets the default app for intents — used by Language Selector and advanced app routing.",
        "Language Selector · Default Apps",
        AccuPermCategory.APP_MGMT, PermImportance.OPTIONAL, GrantMethod.SHIZUKU),

    // ── SYSTEM ──────────────────────────────────────────────
    AccuPerm("write_settings", "Write System Settings", Manifest.permission.WRITE_SETTINGS,
        "Modifies screen brightness, font size, display timeout, and system panel settings.",
        "Dark Mode · System Tweaks · Key Mapper",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.SETTINGS_APP),

    AccuPerm("change_config", "Change Configuration",
        "android.permission.CHANGE_CONFIGURATION",
        "Applies locale/language changes per-app without a device restart.",
        "Language Center · Locale Switcher",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("dump", "System Dump", Manifest.permission.DUMP,
        "Reads system state dumps for diagnostics, Key Mapper log capture, and bug report generation.",
        "Key Mapper · Bug Report · Shell Diagnostics",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("read_logs", "Read System Logs", Manifest.permission.READ_LOGS,
        "Captures logcat for the ADB Shell, Key Mapper event log, and bug report attachment.",
        "Shell · Key Mapper Log · Bug Report",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("modify_audio", "Modify Audio Settings", Manifest.permission.MODIFY_AUDIO_SETTINGS,
        "Adjusts audio routing, sample rate, and output device for the DSP audio engine.",
        "DSP Controls · JamesDSP · EQ",
        AccuPermCategory.SYSTEM, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("accessibility_svc", "Accessibility Service",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "Required for Key Mapper to intercept hardware button and gesture events without root.",
        "Key Mapper · Gesture Triggers",
        AccuPermCategory.SYSTEM, PermImportance.CRITICAL, GrantMethod.SETTINGS_APP),

    AccuPerm("notification_policy", "Do Not Disturb Access",
        Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        "Allows Key Mapper actions to toggle DND mode and manage notification filters.",
        "Key Mapper · DND Action",
        AccuPermCategory.SYSTEM, PermImportance.OPTIONAL, GrantMethod.SETTINGS_APP),

    AccuPerm("device_power", "Device Power Control",
        "android.permission.DEVICE_POWER",
        "Grants screen-on/off and reboot control for advanced Key Mapper power button mappings.",
        "Key Mapper · Power Actions",
        AccuPermCategory.SYSTEM, PermImportance.OPTIONAL, GrantMethod.ROOT_ONLY),

    AccuPerm("status_bar", "Control Status Bar",
        "android.permission.STATUS_BAR",
        "Expands/collapses the status bar and quick settings from Key Mapper actions.",
        "Key Mapper · QS Actions",
        AccuPermCategory.SYSTEM, PermImportance.OPTIONAL, GrantMethod.SHIZUKU),

    AccuPerm("expand_status_bar", "Expand Status Bar",
        Manifest.permission.EXPAND_STATUS_BAR,
        "Opens the notification shade from Key Mapper trigger actions.",
        "Key Mapper",
        AccuPermCategory.SYSTEM, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    AccuPerm("manage_media", "Manage Media",
        "android.permission.MANAGE_MEDIA",
        "Allows File Manager to delete and modify media files without per-item confirmation on Android 12+.",
        "File Manager · Storage Cleaner",
        AccuPermCategory.SYSTEM, PermImportance.OPTIONAL, GrantMethod.SETTINGS_APP,
        minSdk = Build.VERSION_CODES.S),

    // ── MEDIA & STORAGE ─────────────────────────────────────
    AccuPerm("record_audio", "Record Audio", Manifest.permission.RECORD_AUDIO,
        "Captures the microphone stream for call recording. Core requirement of ShizuCallRecorder.",
        "Call Recorder · Audio Capture",
        AccuPermCategory.MEDIA, PermImportance.CRITICAL, GrantMethod.NORMAL),

    AccuPerm("capture_audio_output", "Capture Audio Output",
        "android.permission.CAPTURE_AUDIO_OUTPUT",
        "Captures the device speaker output for call recording both sides. Requires ACCU.",
        "Call Recorder (full duplex)",
        AccuPermCategory.MEDIA, PermImportance.CRITICAL, GrantMethod.SHIZUKU),

    AccuPerm("read_media_images", "Read Images / Video",
        Manifest.permission.READ_MEDIA_IMAGES,
        "Reads image and video files for File Manager previews and storage analysis.",
        "File Manager · Storage Analyzer",
        AccuPermCategory.MEDIA, PermImportance.IMPORTANT, GrantMethod.NORMAL,
        minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("read_media_video", "Read Video Files",
        Manifest.permission.READ_MEDIA_VIDEO,
        "Reads video files for File Manager previews and the deduplicator's video scanning.",
        "File Manager · Deduplicator",
        AccuPermCategory.MEDIA, PermImportance.OPTIONAL, GrantMethod.NORMAL,
        minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("read_media_audio", "Read Audio Files", Manifest.permission.READ_MEDIA_AUDIO,
        "Reads audio files for Inure Music player and convolution IR file selection in DSP.",
        "Inure Music · DSP Convolution",
        AccuPermCategory.MEDIA, PermImportance.OPTIONAL, GrantMethod.NORMAL,
        minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("manage_ext_storage", "Manage All Files",
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        "Full filesystem access needed by File Manager, APK scanner, and System Cleaner.",
        "File Manager · System Cleaner · App Cleaner",
        AccuPermCategory.MEDIA, PermImportance.CRITICAL, GrantMethod.SETTINGS_APP,
        minSdk = Build.VERSION_CODES.R),

    AccuPerm("read_ext_storage", "Read External Storage (Legacy)",
        Manifest.permission.READ_EXTERNAL_STORAGE,
        "Legacy storage access for devices on Android 9–12. Superseded by MANAGE_EXTERNAL_STORAGE on API 30+.",
        "File Manager · APK Scanner",
        AccuPermCategory.MEDIA, PermImportance.IMPORTANT, GrantMethod.NORMAL,
        maxSdk = Build.VERSION_CODES.S_V2),

    // ── NETWORK ─────────────────────────────────────────────
    AccuPerm("access_net_state", "Access Network State",
        Manifest.permission.ACCESS_NETWORK_STATE,
        "Reads current connectivity (Wi-Fi / mobile data) for status tiles and ACCU connection checks.",
        "Better Internet Tiles · Dashboard · ACCU Center",
        AccuPermCategory.NETWORK, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("access_wifi_state", "Access Wi-Fi State",
        Manifest.permission.ACCESS_WIFI_STATE,
        "Reads Wi-Fi SSID, signal strength, and connection state for Network Center and Wi-Fi ADB.",
        "Network Center · Wi-Fi ADB · Tiles",
        AccuPermCategory.NETWORK, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("change_net_state", "Change Network State",
        Manifest.permission.CHANGE_NETWORK_STATE,
        "Switches Wi-Fi and mobile data on/off from Better Internet Tiles and Key Mapper network actions.",
        "Better Internet Tiles · Key Mapper",
        AccuPermCategory.NETWORK, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("change_wifi_state", "Change Wi-Fi State",
        Manifest.permission.CHANGE_WIFI_STATE,
        "Enables/disables Wi-Fi and manages saved networks from the Network Center screen.",
        "Network Center · Better Internet Tiles",
        AccuPermCategory.NETWORK, PermImportance.IMPORTANT, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("change_wifi_multicast", "Wi-Fi Multicast",
        Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
        "Enables multicast/mDNS for the File Manager's SMB server discovery and FTP client.",
        "File Manager · SMB · FTP",
        AccuPermCategory.NETWORK, PermImportance.OPTIONAL, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("nfc", "NFC Access", Manifest.permission.NFC,
        "Optional — used for NFC-tag triggered Key Mapper automations and tag scanning.",
        "Key Mapper · NFC Trigger",
        AccuPermCategory.NETWORK, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    // ── BLUETOOTH ───────────────────────────────────────────
    AccuPerm("bt_connect", "Bluetooth Connect",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT
        else Manifest.permission.BLUETOOTH,
        "Connects to paired BT devices for Key Mapper BT-button trigger mappings.",
        "Key Mapper · BT Triggers",
        AccuPermCategory.BLUETOOTH, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    AccuPerm("bt_scan", "Bluetooth Scan",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
        else Manifest.permission.BLUETOOTH_ADMIN,
        "Scans for nearby BT devices so Key Mapper can list available trigger sources.",
        "Key Mapper · BT Device Picker",
        AccuPermCategory.BLUETOOTH, PermImportance.OPTIONAL, GrantMethod.NORMAL,
        minSdk = Build.VERSION_CODES.S),

    // ── PRIVACY ─────────────────────────────────────────────
    AccuPerm("read_phone_state", "Read Phone State",
        Manifest.permission.READ_PHONE_STATE,
        "Detects call start/end events so the Call Recorder triggers automatically.",
        "Call Recorder · Call State Detection",
        AccuPermCategory.PRIVACY, PermImportance.CRITICAL, GrantMethod.NORMAL),

    AccuPerm("modify_phone_state", "Modify Phone State",
        "android.permission.MODIFY_PHONE_STATE",
        "Required for advanced call management features in ShizuCallRecorder (hold, mute, routing).",
        "Call Recorder · Call Control",
        AccuPermCategory.PRIVACY, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("process_outgoing_calls", "Process Outgoing Calls",
        Manifest.permission.PROCESS_OUTGOING_CALLS,
        "Lets the Call Recorder intercept outgoing calls to start recording automatically.",
        "Call Recorder",
        AccuPermCategory.PRIVACY, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    AccuPerm("read_contacts", "Read Contacts",
        Manifest.permission.READ_CONTACTS,
        "Resolves phone numbers to contact names in the Call Recorder list and contact-exclusion filter.",
        "Call Recorder · Contact Filter",
        AccuPermCategory.PRIVACY, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    AccuPerm("read_call_log", "Read Call Log",
        Manifest.permission.READ_CALL_LOG,
        "Reads call history to pre-populate recording metadata and display call durations.",
        "Call Recorder · Call History",
        AccuPermCategory.PRIVACY, PermImportance.OPTIONAL, GrantMethod.NORMAL),

    AccuPerm("read_priv_phone_state", "Privileged Phone State",
        "android.permission.READ_PRIVILEGED_PHONE_STATE",
        "Reads IMEI, SIM state, and carrier info for advanced diagnostics in the Shell and ACCU Center.",
        "Shell · Diagnostics · ACCU Center",
        AccuPermCategory.PRIVACY, PermImportance.OPTIONAL, GrantMethod.SHIZUKU),

    // ── ADVANCED ────────────────────────────────────────────
    AccuPerm("accu_binding", "ACCU Service Binding",
        "com.accu.api.permission.BIND_ACCU_SERVICE",
        "Core ACCU binding permission. Without this, third-party apps cannot bind to AccuSystemService.",
        "ACCU Service Hub · All privileged features",
        AccuPermCategory.ADVANCED, PermImportance.CRITICAL, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("device_owner", "Device Owner / MDM",
        "android.permission.MANAGE_DEVICE_POLICY_PACKAGES",
        "Used by Hail's Device Owner freeze mode (DPM.setPackagesSuspended). Requires Device Owner setup via ADB.",
        "Freeze Scheduler · Device Owner Mode",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.ADB_ONLY,
        minSdk = Build.VERSION_CODES.TIRAMISU),

    AccuPerm("battery_stats", "Battery Stats",
        Manifest.permission.BATTERY_STATS,
        "Reads detailed per-app battery consumption for Inure Battery Optimization and Dashboard charts.",
        "Inure · Battery Opt · Dashboard",
        AccuPermCategory.ADVANCED, PermImportance.IMPORTANT, GrantMethod.SHIZUKU),

    AccuPerm("manage_overlay", "Display Over Other Apps",
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        "Draws Key Mapper floating button and DSP status overlay on top of all other apps.",
        "Key Mapper · Floating Button · DSP Overlay",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.SETTINGS_APP),

    AccuPerm("hide_overlay_windows", "Hide Overlay Windows",
        "android.permission.HIDE_OVERLAY_WINDOWS",
        "Hides all third-party overlay windows when ACCU's security dialogs are displayed.",
        "Security Dialogs · Install Prompt",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.NORMAL,
        minSdk = Build.VERSION_CODES.S),

    AccuPerm("use_biometric", "Use Biometric / Fingerprint",
        Manifest.permission.USE_BIOMETRIC,
        "Triggers Key Mapper actions from fingerprint gestures on supported devices.",
        "Key Mapper · Fingerprint Trigger",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.AUTOMATIC, canRevoke = false),

    AccuPerm("manage_users", "Manage Users",
        "android.permission.MANAGE_USERS",
        "Creates and manages secondary user accounts for Hail work-profile freeze functionality.",
        "Hail · Work Profile · Multi-User",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.SHIZUKU),

    AccuPerm("camera", "Camera", Manifest.permission.CAMERA,
        "Used for QR-code scanning during ACCU wireless-ADB pairing setup.",
        "ACCU Setup · QR Pairing",
        AccuPermCategory.ADVANCED, PermImportance.OPTIONAL, GrantMethod.NORMAL),
)

// ═══════════════════════════════════════════════════════════
//  RUNTIME STATUS CHECK
// ═══════════════════════════════════════════════════════════

private fun checkPermStatus(context: Context, perm: AccuPerm): PermStatus {
    if (Build.VERSION.SDK_INT < perm.minSdk || Build.VERSION.SDK_INT > perm.maxSdk)
        return PermStatus.NOT_APPLICABLE
    return try {
        when (perm.id) {
            "manage_ext_storage" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    Environment.isExternalStorageManager()) PermStatus.GRANTED
                else PermStatus.NOT_REQUESTED

            "write_settings" -> if (Settings.System.canWrite(context)) PermStatus.GRANTED
                else PermStatus.NOT_REQUESTED

            "manage_overlay" -> if (Settings.canDrawOverlays(context)) PermStatus.GRANTED
                else PermStatus.NOT_REQUESTED

            "notification_policy" -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted) PermStatus.GRANTED else PermStatus.NOT_REQUESTED
            }

            "exact_alarm" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (am.canScheduleExactAlarms()) PermStatus.GRANTED else PermStatus.NOT_REQUESTED
            } else PermStatus.GRANTED

            "pkg_usage_stats" -> {
                val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
                else
                    @Suppress("DEPRECATION")
                    ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
                if (mode == AppOpsManager.MODE_ALLOWED) PermStatus.GRANTED else PermStatus.NOT_REQUESTED
            }

            "accessibility_svc" -> {
                val enabled = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                if (enabled.contains(context.packageName, ignoreCase = true)) PermStatus.GRANTED
                else PermStatus.NOT_REQUESTED
            }

            else -> if (context.checkSelfPermission(perm.rawPermission) == PackageManager.PERMISSION_GRANTED)
                PermStatus.GRANTED else PermStatus.NOT_REQUESTED
        }
    } catch (_: Exception) { PermStatus.NOT_REQUESTED }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("adb_command", text))
}

// ═══════════════════════════════════════════════════════════
//  SCREEN
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuPermissionsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val vm: AccuPermissionsViewModel = hiltViewModel()

    // ── State ──────────────────────────────────────────────
    var perms by remember {
        mutableStateOf(ALL_ACCU_PERMISSIONS.map { it.copy(status = checkPermStatus(context, it)) })
    }
    var searchQuery      by remember { mutableStateOf("") }
    var selectedCat      by remember { mutableStateOf(AccuPermCategory.ALL) }
    var selectedTier     by remember { mutableStateOf(GrantTier.ALL) }
    var sortMode         by remember { mutableStateOf(SortMode.STATUS) }
    var expandedId       by remember { mutableStateOf<String?>(null) }
    var showGrantDialog  by remember { mutableStateOf(false) }
    var sortExpanded     by remember { mutableStateOf(false) }
    var grantingId       by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        perms = ALL_ACCU_PERMISSIONS.map { it.copy(status = checkPermStatus(context, it)) }
    }

    // Refresh status whenever the screen resumes (user may have granted in Settings)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Single launcher for NORMAL runtime permissions — result refreshes real status
    var pendingNormalPermRaw by remember { mutableStateOf<String?>(null) }
    val normalPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refresh()
        scope.launch {
            if (isGranted) snackbar.showSnackbar("Permission granted ✓")
            else snackbar.showSnackbar("Permission denied by user")
        }
        pendingNormalPermRaw = null
    }

    // ── Derived ────────────────────────────────────────────
    val filtered = remember(perms, searchQuery, selectedCat, selectedTier, sortMode) {
        perms
            .filter { p ->
                (selectedCat == AccuPermCategory.ALL || p.category == selectedCat) &&
                (selectedTier == GrantTier.ALL || permGrantTier(p) == selectedTier) &&
                (searchQuery.isBlank() ||
                    p.friendlyName.contains(searchQuery, ignoreCase = true) ||
                    p.description.contains(searchQuery, ignoreCase = true) ||
                    p.usedBy.contains(searchQuery, ignoreCase = true) ||
                    p.rawPermission.contains(searchQuery, ignoreCase = true))
            }
            .sortedWith(when (sortMode) {
                SortMode.STATUS     -> compareBy({ it.status != PermStatus.NOT_REQUESTED && it.status != PermStatus.DENIED }, { it.importance.ordinal })
                SortMode.IMPORTANCE -> compareBy({ it.importance.ordinal }, { it.friendlyName })
                SortMode.CATEGORY   -> compareBy({ it.category.ordinal }, { it.importance.ordinal })
                SortMode.NAME       -> compareBy { it.friendlyName }
            })
    }

    val totalGranted   = perms.count { it.status == PermStatus.GRANTED }
    val totalCritical  = perms.count { it.importance == PermImportance.CRITICAL }
    val critGranted    = perms.count { it.importance == PermImportance.CRITICAL && it.status == PermStatus.GRANTED }
    val shizukuPending = perms.filter { it.grantMethod == GrantMethod.SHIZUKU && it.status == PermStatus.NOT_REQUESTED }
    val healthPct      = if (perms.isNotEmpty()) totalGranted.toFloat() / perms.size else 0f

    // ── Grant all dialog ──────────────────────────────────
    if (showGrantDialog) {
        AlertDialog(
            onDismissRequest = { showGrantDialog = false },
            icon = {
                Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text("Grant All via ACCU", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shizukuPending.isEmpty()) {
                        Text(
                            "All ACCU-grantable permissions are already granted!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "${shizukuPending.size} permission(s) will be granted automatically:",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        shizukuPending.forEach { p ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.Hub, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Text(p.friendlyName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGrantDialog = false
                        scope.launch {
                            var successCount = 0
                            shizukuPending.forEach { p ->
                                grantingId = p.id
                                val ok = vm.grantPermission(context.packageName, p.rawPermission)
                                if (ok) successCount++
                            }
                            grantingId = null
                            refresh()
                            snackbar.showSnackbar(
                                if (successCount == shizukuPending.size)
                                    "Granted $successCount permission(s) via ACCU ✓"
                                else
                                    "Granted $successCount / ${shizukuPending.size} — check ACCU connection"
                            )
                        }
                    },
                    enabled = shizukuPending.isNotEmpty(),
                ) { Text("Grant All") }
            },
            dismissButton = {
                TextButton(onClick = { showGrantDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Permission Center",
                onBack = onBack,
                actions = {
                    // Sort
                    Box {
                        IconButton(onClick = { sortExpanded = !sortExpanded }) {
                            Icon(Icons.Default.Sort, "Sort")
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (sortMode == mode)
                                                Icon(Icons.Default.Check, null, Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary)
                                            else Spacer(Modifier.size(16.dp))
                                            Text(mode.label)
                                        }
                                    },
                                    onClick = { sortMode = mode; sortExpanded = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh status")
                    }
                    IconButton(onClick = { showGrantDialog = true }) {
                        Icon(Icons.Default.Hub, "Grant all via ACCU")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── Hero health card ───────────────────────────
            item {
                PermHealthCard(
                    totalGranted   = totalGranted,
                    totalPerms     = perms.size,
                    critGranted    = critGranted,
                    totalCritical  = totalCritical,
                    healthPct      = healthPct,
                    shizukuPending = shizukuPending.size,
                    onGrantAll     = { showGrantDialog = true },
                )
            }

            // ── Search ────────────────────────────────────
            item {
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("Search permissions, features, or raw names…") },
                    leadingIcon   = { Icon(Icons.Default.Search, null) },
                    trailingIcon  = {
                        if (searchQuery.isNotEmpty())
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                    },
                    singleLine = true,
                    shape      = RoundedCornerShape(16.dp),
                )
            }

            // ── Grant tier tabs (3 categories) ───────────
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Grant Method",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(GrantTier.entries) { tier ->
                                val tierCount = when (tier) {
                                    GrantTier.ALL -> perms.size
                                    else -> perms.count { permGrantTier(it) == tier }
                                }
                                val tierGranted = when (tier) {
                                    GrantTier.ALL -> perms.count { it.status == PermStatus.GRANTED }
                                    else -> perms.count { permGrantTier(it) == tier && it.status == PermStatus.GRANTED }
                                }
                                FilterChip(
                                    selected = selectedTier == tier,
                                    onClick  = { selectedTier = tier },
                                    label    = {
                                        Text(
                                            "${tier.label} $tierGranted/$tierCount",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    leadingIcon = { Icon(tier.icon, null, Modifier.size(14.dp)) },
                                )
                            }
                        }
                        // Tier descriptions
                        when (selectedTier) {
                            GrantTier.AUTO_INSTALL -> Text(
                                "Granted automatically when app is installed. No user action needed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            GrantTier.MANUAL -> Text(
                                "User must grant manually via Settings or a system dialog. Cannot be granted by ADB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            GrantTier.AUTO_ADB -> Text(
                                "Can be auto-granted by ACCU via ADB when connected. Tap 'Grant All' to grant all at once.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            else -> {}
                        }
                    }
                }
            }

            // ── Category filter chips ─────────────────────
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AccuPermCategory.entries) { cat ->
                        val count = if (cat == AccuPermCategory.ALL) perms.size
                                    else perms.count { it.category == cat }
                        FilterChip(
                            selected = selectedCat == cat,
                            onClick  = { selectedCat = cat },
                            label    = { Text("${cat.label} ($count)", maxLines = 1) },
                            leadingIcon = { Icon(cat.icon, null, Modifier.size(14.dp)) },
                        )
                    }
                }
            }

            // ── Summary strip ────────────────────────────
            item {
                PermSummaryStrip(filtered)
            }

            // ── Permission cards ──────────────────────────
            items(filtered, key = { it.id }) { perm ->
                AnimatedVisibility(
                    visible = true,
                    enter   = fadeIn() + expandVertically(),
                ) {
                    PermissionCard(
                        perm       = perm,
                        isExpanded = expandedId == perm.id,
                        isGranting = grantingId == perm.id,
                        onToggle   = { expandedId = if (expandedId == perm.id) null else perm.id },
                        onGrant    = {
                            when (perm.grantMethod) {
                                GrantMethod.SETTINGS_APP -> {
                                    val intent = when (perm.id) {
                                        "pkg_usage_stats"  -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        "write_settings"   -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                                            .setData(Uri.parse("package:${context.packageName}"))
                                        "manage_overlay"   -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                            .setData(Uri.parse("package:${context.packageName}"))
                                        "manage_ext_storage" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                                .setData(Uri.parse("package:${context.packageName}"))
                                            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(Uri.parse("package:${context.packageName}"))
                                        "accessibility_svc" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        "notification_policy" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                        "exact_alarm" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(Uri.parse("package:${context.packageName}"))
                                        "manage_media" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.parse("package:${context.packageName}"))
                                        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.parse("package:${context.packageName}"))
                                    }
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                GrantMethod.SHIZUKU -> {
                                    scope.launch {
                                        grantingId = perm.id
                                        val ok = vm.grantPermission(context.packageName, perm.rawPermission)
                                        grantingId = null
                                        refresh()
                                        snackbar.showSnackbar(
                                            if (ok) "${perm.friendlyName} granted via ACCU ✓"
                                            else "${perm.friendlyName}: grant failed — check ACCU connection"
                                        )
                                    }
                                }
                                GrantMethod.NORMAL -> {
                                    pendingNormalPermRaw = perm.rawPermission
                                    normalPermLauncher.launch(perm.rawPermission)
                                }
                                GrantMethod.ADB_ONLY -> {
                                    val cmd = "adb shell pm grant ${context.packageName} ${perm.rawPermission}"
                                    copyToClipboard(context, cmd)
                                    scope.launch { snackbar.showSnackbar("ADB command copied to clipboard") }
                                }
                                else -> {
                                    scope.launch { snackbar.showSnackbar("${perm.friendlyName}: ${perm.grantMethod.label}") }
                                }
                            }
                        },
                        onRevoke = {
                            scope.launch {
                                val ok = vm.revokePermission(context.packageName, perm.rawPermission)
                                refresh()
                                if (ok) {
                                    snackbar.showSnackbar("${perm.friendlyName} revoked ✓")
                                } else {
                                    val fallback = "adb shell pm revoke ${context.packageName} ${perm.rawPermission}"
                                    copyToClipboard(context, fallback)
                                    snackbar.showSnackbar("Revoke failed — ADB command copied to clipboard")
                                }
                            }
                        },
                        onCopyAdb = {
                            val cmd = "adb shell pm grant ${context.packageName} ${perm.rawPermission}"
                            copyToClipboard(context, cmd)
                            scope.launch { snackbar.showSnackbar("Copied: $cmd") }
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  SUMMARY STRIP
// ═══════════════════════════════════════════════════════════

@Composable
private fun PermSummaryStrip(filtered: List<AccuPerm>) {
    val granted   = filtered.count { it.status == PermStatus.GRANTED }
    val missing   = filtered.count { it.status == PermStatus.NOT_REQUESTED || it.status == PermStatus.DENIED }
    val na        = filtered.count { it.status == PermStatus.NOT_APPLICABLE }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryPill("$granted Granted",  Color(0xFF43A047), Modifier.weight(1f))
        SummaryPill("$missing Pending",  Color(0xFFFF8F00), Modifier.weight(1f))
        SummaryPill("$na N/A",           MaterialTheme.colorScheme.outline, Modifier.weight(1f))
        SummaryPill("${filtered.size} Total", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp),
        color    = color.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  HEALTH CARD
// ═══════════════════════════════════════════════════════════

@Composable
private fun PermHealthCard(
    totalGranted: Int,
    totalPerms: Int,
    critGranted: Int,
    totalCritical: Int,
    healthPct: Float,
    shizukuPending: Int,
    onGrantAll: () -> Unit,
) {
    val animPct by animateFloatAsState(
        targetValue  = healthPct,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label        = "health",
    )
    val healthColor = when {
        healthPct >= 0.85f -> Color(0xFF43A047)
        healthPct >= 0.55f -> Color(0xFFFF8F00)
        else               -> Color(0xFFE53935)
    }

    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(0.25f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(0.10f),
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    )
                )
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Arc ring
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color    = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 8.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        CircularProgressIndicator(
                            progress = { animPct },
                            modifier = Modifier.fillMaxSize(),
                            color    = healthColor,
                            strokeWidth = 8.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(animPct * 100).toInt()}%",
                                style  = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color  = healthColor,
                            )
                            Text("health", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Permission Health",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("$totalGranted / $totalPerms permissions granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatPill("$critGranted/$totalCritical Critical",
                                if (critGranted == totalCritical) Color(0xFF43A047) else Color(0xFFE53935))
                            if (shizukuPending > 0)
                                StatPill("$shizukuPending via ACCU", MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Per-importance mini bars
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PermImportance.entries.forEach { imp ->
                        val impTotal   = ALL_ACCU_PERMISSIONS.count { it.importance == imp }
                        val impGranted = ALL_ACCU_PERMISSIONS.count { it.importance == imp && checkPermStatus(
                            LocalContext.current, it) == PermStatus.GRANTED }
                        val barFraction = if (impTotal > 0) impGranted.toFloat() / impTotal else 0f
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment    = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = imp.color.copy(0.15f),
                                modifier = Modifier.width(72.dp),
                            ) {
                                Text(
                                    imp.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = imp.color,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            LinearProgressIndicator(
                                progress = { barFraction },
                                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                                color      = imp.color,
                                trackColor = imp.color.copy(0.15f),
                            )
                            Text("$impGranted/$impTotal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Grant all CTA
                if (ALL_ACCU_PERMISSIONS.any { it.grantMethod == GrantMethod.SHIZUKU &&
                        checkPermStatus(LocalContext.current, it) == PermStatus.NOT_REQUESTED }) {
                    Button(
                        onClick  = onGrantAll,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Default.Hub, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant All Missing via ACCU (1 tap)")
                    }
                } else {
                    OutlinedButton(onClick = onGrantAll, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp),
                            tint = Color(0xFF43A047))
                        Spacer(Modifier.width(8.dp))
                        Text("All ACCU Permissions Granted")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.12f)) {
        Text(
            text,
            style  = MaterialTheme.typography.labelSmall,
            color  = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  PERMISSION CARD
// ═══════════════════════════════════════════════════════════

@Composable
private fun PermissionCard(
    perm: AccuPerm,
    isExpanded: Boolean,
    isGranting: Boolean,
    onToggle: () -> Unit,
    onGrant: () -> Unit,
    onRevoke: () -> Unit,
    onCopyAdb: () -> Unit,
) {
    val context = LocalContext.current
    val statusColor = when (perm.status) {
        PermStatus.GRANTED        -> Color(0xFF43A047)
        PermStatus.DENIED         -> Color(0xFFE53935)
        PermStatus.NOT_APPLICABLE -> MaterialTheme.colorScheme.outline
        PermStatus.NOT_REQUESTED  -> Color(0xFFFF8F00)
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

    val cardBg = when {
        perm.status == PermStatus.NOT_REQUESTED && perm.importance == PermImportance.CRITICAL ->
            MaterialTheme.colorScheme.errorContainer.copy(0.10f)
        perm.status == PermStatus.GRANTED ->
            MaterialTheme.colorScheme.surface
        else ->
            MaterialTheme.colorScheme.surfaceContainerLow
    }

    // ACCU button scale — hoisted here (top-level) so it's never inside a conditional
    val shizukuBtnScale by animateFloatAsState(
        targetValue   = if (isGranting) 0.85f else 1f,
        animationSpec = tween(300),
        label         = "shizuku_btn",
    )

    // Pulsing animation while ACCU is granting
    val pulseScale by animateFloatAsState(
        targetValue   = if (isGranting) 1.04f else 1f,
        animationSpec = if (isGranting)
            infiniteRepeatable(tween(500), RepeatMode.Reverse)
        else tween(200),
        label = "pulse",
    )

    Card(
        modifier = Modifier.fillMaxWidth().scale(pulseScale),
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(if (isExpanded) 3.dp else 1.dp),
        onClick  = onToggle,
    ) {
        Column(Modifier.padding(14.dp)) {

            // ── Header row ────────────────────────────────
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isGranting) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(statusIcon, null, Modifier.size(22.dp), tint = statusColor)
                    }
                }

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            perm.friendlyName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = perm.importance.color.copy(0.15f),
                        ) {
                            Text(
                                perm.importance.label,
                                style  = MaterialTheme.typography.labelSmall,
                                color  = perm.importance.color,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        perm.description,
                        style   = MaterialTheme.typography.bodySmall,
                        color   = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Status + action row ───────────────────────
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Status chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(0.12f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(statusIcon, null, Modifier.size(11.dp), tint = statusColor)
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall,
                            color = statusColor, fontWeight = FontWeight.Bold)
                    }
                }

                // Grant-method chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(perm.grantMethod.icon, null, Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.secondary)
                        Text(perm.grantMethod.label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Spacer(Modifier.weight(1f))

                // Action button
                when {
                    perm.status == PermStatus.NOT_APPLICABLE -> {
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(0.1f)) {
                            Text("Not applicable", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    perm.status == PermStatus.GRANTED -> {
                        if (perm.canRevoke && perm.grantMethod != GrantMethod.AUTOMATIC) {
                            OutlinedButton(
                                onClick  = onRevoke,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.5f)),
                            ) {
                                Icon(Icons.Default.Block, null, Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(4.dp))
                                Text("Revoke", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF43A047).copy(0.1f)) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, Modifier.size(13.dp),
                                        tint = Color(0xFF43A047))
                                    Text("Active", style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF43A047), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    else -> {
                        when (perm.grantMethod) {
                            GrantMethod.SHIZUKU -> {
                                Button(
                                    onClick  = onGrant,
                                    modifier = Modifier.height(32.dp).scale(shizukuBtnScale),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    enabled  = !isGranting,
                                ) {
                                    if (isGranting) {
                                        CircularProgressIndicator(Modifier.size(12.dp),
                                            strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Icon(Icons.Default.Hub, null, Modifier.size(14.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text("ACCU 1-tap", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            GrantMethod.NORMAL -> {
                                Button(
                                    onClick  = onGrant,
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
                                    onClick  = onGrant,
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
                                    onClick  = onGrant,
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy ADB", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            GrantMethod.ROOT_ONLY -> {
                                OutlinedButton(
                                    onClick  = onGrant,
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    border   = ButtonDefaults.outlinedButtonBorder(enabled = false),
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Root only", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // ── Expanded detail panel ─────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider()
                    Spacer(Modifier.height(2.dp))

                    // Raw permission string + copy
                    DetailHeader(Icons.Default.Code, MaterialTheme.colorScheme.primary, "Raw permission name")
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape  = RoundedCornerShape(8.dp),
                            color  = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                perm.rawPermission,
                                modifier = Modifier.padding(10.dp),
                                style    = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        IconButton(
                            onClick  = { copyToClipboard(context, perm.rawPermission) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Used by features
                    DetailHeader(Icons.Default.Extension, MaterialTheme.colorScheme.secondary, "Used by features")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        perm.usedBy.split("·").forEach { feat ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    feat.trim(),
                                    style  = MaterialTheme.typography.labelSmall,
                                    color  = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    // ADB / ACCU command
                    if (perm.grantMethod == GrantMethod.ADB_ONLY ||
                        perm.grantMethod == GrantMethod.SHIZUKU) {
                        val cmd = "adb shell pm grant ${LocalContext.current.packageName} ${perm.rawPermission}"
                        DetailHeader(Icons.Default.Terminal, MaterialTheme.colorScheme.tertiary, "ADB command")
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape  = RoundedCornerShape(8.dp),
                                color  = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    cmd,
                                    modifier = Modifier.padding(10.dp),
                                    style    = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            IconButton(
                                onClick  = onCopyAdb,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.ContentCopy, "Copy ADB", Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }

                    // Category tag
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(perm.category.icon, null, Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Category: ${perm.category.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (perm.minSdk > 1 || perm.maxSdk < Int.MAX_VALUE) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Android, null, Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline)
                            val sdkNote = when {
                                perm.maxSdk < Int.MAX_VALUE -> "API ≤ ${perm.maxSdk}"
                                else -> "API ${perm.minSdk}+"
                            }
                            Text(sdkNote, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(icon: ImageVector, tint: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = tint, fontWeight = FontWeight.SemiBold)
    }
}

// ═══════════════════════════════════════════════════════════
//  VIEW MODEL
// ═══════════════════════════════════════════════════════════

@HiltViewModel
class AccuPermissionsViewModel @Inject constructor(
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    /**
     * Runs `pm grant <packageName> <permission>` via AccuConnectionManager (root/ADB).
     * Returns true if the command exited cleanly (exit code 0 or no error text).
     */
    suspend fun grantPermission(packageName: String, rawPermission: String): Boolean =
        runCommand("pm grant $packageName $rawPermission")

    /**
     * Runs `pm revoke <packageName> <permission>` via AccuConnectionManager.
     * Returns true on success.
     */
    suspend fun revokePermission(packageName: String, rawPermission: String): Boolean =
        runCommand("pm revoke $packageName $rawPermission")

    private suspend fun runCommand(command: String): Boolean = try {
        // Always execute on the LOCAL device (where ACCU runs), never on a remote target device.
        // ACCU's own permissions must be granted on its own device, not on the connected device.
        val result = connectionManager.execLocal(command)
        result.isSuccess
    } catch (_: Exception) {
        false
    }
}
