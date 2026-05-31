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
    data object ShizukuCenter       : Screen("shizuku_center")
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
    data object AudioCenter         : Screen("audio_center")
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

    // RootlessJamesDSP
    data object LiveprogEditor      : Screen("liveprog_editor")
    data object ParametricEQ        : Screen("parametric_eq")
    data object AutoEQ              : Screen("auto_eq")
    data object AppAudioBlocklist   : Screen("app_audio_blocklist")

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
