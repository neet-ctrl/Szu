package com.accu.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    // Onboarding
    data object Onboarding : Screen("onboarding")

    // Top-level destinations (bottom nav / rail)
    data object Dashboard   : Screen("dashboard")
    data object AppManager  : Screen("app_manager")
    data object Shell       : Screen("shell")
    data object Storage     : Screen("storage")
    data object Settings    : Screen("settings")

    // Sub-screens (all navigable from dashboard or nav rail)
    data object AccuCenter          : Screen("accu_center")
    data object Privacy             : Screen("privacy")
    data object Customization       : Screen("customization")
    data object DarkMode            : Screen("dark_mode")
    data object ColorEditor         : Screen("color_editor")
    data object Widgets             : Screen("widgets")
    data object FileManager         : Screen("file_manager")
    data object Installer           : Screen("installer")
    data object Automation          : Screen("automation")
    data object KeyMapper           : Screen("key_mapper")
    data object LanguageCenter      : Screen("language_center")
    data object NetworkCenter       : Screen("network_center")
    data object SoundMaster         : Screen("sound_master")
    data object MixedAudio          : Screen("mixed_audio")
    data object CallRecorder        : Screen("call_recorder")
    data object LearningCenter      : Screen("learning_center")
    data object Debloat             : Screen("debloat")
    data object FreezeApps          : Screen("freeze_apps")
    data object ComponentManager    : Screen("component_manager")
    data object PermissionManager   : Screen("permission_manager")
    data object AppDetail           : Screen("app_detail/{packageName}") {
        fun withPackage(pkg: String) = "app_detail/$pkg"
    }
    data object SmartSpacer         : Screen("smartspacer")
    data object VirusScan           : Screen("virus_scan")
    data object AllFeatures         : Screen("all_features")
    data object Tutorial            : Screen("tutorial")

    // === NEW SCREENS (all 17 repos) ===

    // Canta
    data object CantaPresets        : Screen("canta_presets")
    data object CantaLogs           : Screen("canta_logs")

    // Hail
    data object HailWorkProfile     : Screen("hail_work_profile")

    // DarQ
    data object DarQFaq             : Screen("darq_faq")
    data object DarQSunriseSunset   : Screen("darq_sunrise_sunset")

    // ColorBlendr
    data object ColorBlendrStyles   : Screen("colorblendr_styles")

    // SDMaid SE
    data object AppCleaner          : Screen("app_cleaner")
    data object SystemCleaner       : Screen("system_cleaner")
    data object Deduplicator        : Screen("deduplicator")
    data object CorpseFinder        : Screen("corpse_finder")

    // InstallWithOptions
    data object InstallFlags        : Screen("install_flags")

    // Blocker
    data object OnlineRules         : Screen("online_rules")

    // Inure
    data object InureAnalytics      : Screen("inure_analytics")
    data object AppBatchOps         : Screen("app_batch_ops/{packageName}") {
        fun withPackage(pkg: String) = "app_batch_ops/$pkg"
    }

    // Key Mapper
    data object KeyMapperAdvanced   : Screen("keymapper_advanced")

    // MaterialFiles
    data object FileManagerAdvanced : Screen("filemanager_advanced")

    // SmartSpacer
    data object SmartSpacerTargets  : Screen("smartspacer_targets")

    // ShizuCallRecorder
    data object ScrcpyIntegration   : Screen("scrcpy_integration")
    data object CallRecordingSettings : Screen("call_recording_settings")

    // BetterInternetTiles
    data object TilesSettings       : Screen("tiles_settings")

    // LanguageSelector
    data object LanguageDetail      : Screen("language_detail/{packageName}/{appName}") {
        fun withApp(pkg: String, appName: String) = "language_detail/$pkg/$appName"
    }

    // App Explorer (new flagship feature)
    data object AppExplorer         : Screen("app_explorer")

    // SD Maid SE — missing feature
    data object LargeFileFinder     : Screen("large_file_finder")

    // aShellYou — missing feature
    data object ScriptEditor        : Screen("script_editor")

    // aShellYou — QS Tile Dashboard (custom shell-command QS tiles)
    data object ShellQsTileDashboard : Screen("shell_qs_tile_dashboard")

    // Hail — missing feature
    data object FreezeScheduler     : Screen("freeze_scheduler")

    // ========= BATCH 2: 32 New Screens =========

    // Key Mapper
    data object KeyMapList          : Screen("key_map_list")
    data object ConfigKeyMap        : Screen("config_key_map/{mapId}") {
        fun create() = "config_key_map/new"
        fun edit(id: String) = "config_key_map/$id"
    }
    data object ChooseAction        : Screen("choose_action")
    data object ChooseConstraint    : Screen("choose_constraint")
    data object KeyMapLog           : Screen("key_map_log")
    data object KeyMapperSettings   : Screen("keymapper_settings")

    // Inure
    data object InureHome              : Screen("inure_home")
    data object InureBatteryOpt        : Screen("inure_battery_opt")
    data object InureBootManager       : Screen("inure_boot_manager")
    data object InureNotes             : Screen("inure_notes")
    data object InureMusic             : Screen("inure_music")
    data object InureApks              : Screen("inure_apks")
    data object InureTrackers          : Screen("inure_trackers")
    data object InureUsageStats        : Screen("inure_usage_stats")
    data object InureDisabledApps      : Screen("inure_disabled_apps")
    data object InureMostUsed          : Screen("inure_most_used")
    data object InureRecentlyInstalled : Screen("inure_recently_installed")
    data object InureUninstalled       : Screen("inure_uninstalled")
    data object InureTags              : Screen("inure_tags")
    data object InureFoss              : Screen("inure_foss")
    data object InureUnpack            : Screen("inure_unpack/{packageName}") {
        fun withPackage(pkg: String) = "inure_unpack/$pkg"
    }

    // ColorBlendr
    data object PerAppTheming       : Screen("per_app_theming")

    // SD Maid SE
    data object Squeezer            : Screen("squeezer")
    data object StorageAnalyzer     : Screen("storage_analyzer")

    // Material Files
    data object FtpServer           : Screen("ftp_server")
    data object FileProperties      : Screen("file_properties/{filePath}") {
        fun withPath(path: String) = "file_properties/${java.net.URLEncoder.encode(path, "UTF-8")}"
    }
    data object TextEditor          : Screen("text_editor/{filePath}") {
        fun withPath(path: String) = "text_editor/${java.net.URLEncoder.encode(path, "UTF-8")}"
    }

    // aShellYou
    data object CommandExamples     : Screen("command_examples")
    data object AdbFileBrowser      : Screen("adb_file_browser/{connectionMode}/{deviceAddress}") {
        fun withArgs(mode: String, address: String) = "adb_file_browser/$mode/${java.net.URLEncoder.encode(address, "UTF-8")}"
        fun wifi(address: String) = withArgs("wifi", address)
        fun otg(address: String) = withArgs("otg", address)
        fun local() = withArgs("local", "localhost")
    }

    // SmartSpacer
    data object SmartSpacerComplications : Screen("smartspacer_complications")

    // ACCU Connection
    data object AdbPairing         : Screen("adb_pairing")
    data object AccuApps           : Screen("accu_apps")

    // DarQ
    data object DarQAppPicker      : Screen("darq_app_picker")

    // Blocker
    data object BlockerComponentSearch : Screen("blocker_component_search")

    // Advanced Permission Center
    data object PermissionCenter   : Screen("permission_center")

    // Notification Center
    data object NotificationCenter : Screen("notification_center")

    // ── Crash Center ──────────────────────────────────────────────────────────
    data object CrashCenter        : Screen("crash_center")
    data object CrashHistory       : Screen("crash_history")
    data object CrashDetail        : Screen("crash_detail/{crashId}") {
        fun withId(id: String) = "crash_detail/$id"
    }

    // ── ACCU System Service (IPC privilege hub) ───────────────────────────────
    /** Main service hub: shows connected apps, pending requests, quick docs */
    data object AccuServiceHub     : Screen("accu_service_hub")
    /** Full SDK documentation for third-party developers */
    data object AccuSdkDocs        : Screen("accu_sdk_docs")

    // ── ADB Toolkit screens ───────────────────────────────────────────────────
    data object AdbLogcat          : Screen("adb_logcat")
    data object AdbProcesses       : Screen("adb_processes")
    data object AdbDeviceInfo      : Screen("adb_device_info")
    data object AdbFastboot        : Screen("adb_fastboot")
    data object AdbScreenCapture   : Screen("adb_screen_capture")
    data object AdbTutorial        : Screen("adb_tutorial")

    // ── Connection gate (always shown on app open until connected) ────────────
    data object ConnectionGate     : Screen("connection_gate")

    // ── Universal File Viewer ─────────────────────────────────────────────────
    data object FileViewer         : Screen("file_viewer/{filePath}") {
        fun withPath(path: String) = "file_viewer/${java.net.URLEncoder.encode(path, "UTF-8")}"
    }
}

data class TopLevelDestination(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Screen.Dashboard,   Icons.Default.Home,          "Dashboard"),
    TopLevelDestination(Screen.AppManager,  Icons.Default.Apps,          "Apps"),
    TopLevelDestination(Screen.Shell,       Icons.Default.Terminal,      "Shell"),
    TopLevelDestination(Screen.Storage,     Icons.Default.Storage,       "Storage"),
    TopLevelDestination(Screen.Settings,    Icons.Default.Settings,      "Settings"),
)
