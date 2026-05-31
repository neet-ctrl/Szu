package com.accu.ui.features

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accu.navigation.Screen

data class AppFeature(
    val name: String,
    val description: String,
    val howToUse: String,
    val requirement: FeatureRequirement = FeatureRequirement.NONE,
    val route: String? = null,
)

enum class FeatureRequirement(val label: String, val color: @Composable () -> Color) {
    NONE("", { Color.Transparent }),
    SHIZUKU("Shizuku", { MaterialTheme.colorScheme.primaryContainer }),
    ROOT("Root", { MaterialTheme.colorScheme.errorContainer }),
    OPTIONAL_ROOT("Root Optional", { MaterialTheme.colorScheme.tertiaryContainer }),
}

data class SourceApp(
    val name: String,
    val description: String,
    val githubUrl: String,
    val icon: ImageVector,
    val accentColor: Color,
    val features: List<AppFeature>,
    val navigationRoute: String? = null,
)

val ALL_SOURCE_APPS = listOf(
    SourceApp(
        name = "Shizuku",
        description = "Use system APIs directly with adb/root privileges through a middleware service — the foundation of all elevated features",
        githubUrl = "https://github.com/RikkaApps/Shizuku",
        icon = Icons.Default.Shield,
        accentColor = Color(0xFF6200EE),
        navigationRoute = Screen.ShizukuCenter.route,
        features = listOf(
            AppFeature("Shizuku Service Manager", "Start/stop/restart the Shizuku service", "Go to Shizuku Center and toggle the service", FeatureRequirement.SHIZUKU, Screen.ShizukuCenter.route),
            AppFeature("Permission Grant", "Grant Shizuku access to ACC features", "First launch prompts permission; manage from Shizuku Center", FeatureRequirement.SHIZUKU),
            AppFeature("UserService Bridge", "Elevated UserService for privileged operations", "Automatically started when Shizuku is running", FeatureRequirement.SHIZUKU),
            AppFeature("Wireless ADB Setup", "Configure wireless ADB without a computer", "Shell → Wireless ADB tab", FeatureRequirement.SHIZUKU),
            AppFeature("ADB mDNS Discovery", "Auto-discover ADB devices on local network", "Shell → Wireless ADB → Scan", FeatureRequirement.SHIZUKU),
        )
    ),
    SourceApp(
        name = "aShellYou",
        description = "Material 3 ADB shell client — run shell commands locally with Shizuku or via wireless ADB",
        githubUrl = "https://github.com/DP-Hridayan/aShellYou",
        icon = Icons.Default.Terminal,
        accentColor = Color(0xFF00897B),
        navigationRoute = Screen.Shell.route,
        features = listOf(
            AppFeature("Interactive Shell", "Run ADB/shell commands in an interactive terminal", "Tap Shell in the bottom navigation", FeatureRequirement.SHIZUKU, Screen.Shell.route),
            AppFeature("Command History", "Browse and replay previous commands", "Arrow up in the terminal or use the history button", FeatureRequirement.SHIZUKU),
            AppFeature("Favorites & Bookmarks", "Save frequently used commands", "Long-press a command in the shell to save", FeatureRequirement.SHIZUKU),
            AppFeature("Syntax Highlighting", "Color-coded shell command syntax", "Enabled by default in the shell", FeatureRequirement.SHIZUKU),
            AppFeature("OTG / Wireless ADB", "Connect via USB OTG or wireless ADB", "Shell → Wireless ADB", FeatureRequirement.SHIZUKU),
            AppFeature("mDNS Device Discovery", "Auto-discover ADB devices on Wi-Fi", "Shell → Wireless ADB → Scan", FeatureRequirement.SHIZUKU),
        )
    ),
    SourceApp(
        name = "Canta",
        description = "Uninstall any app — even system apps — safely with curated removal guides and safety ratings",
        githubUrl = "https://github.com/samolego/Canta",
        icon = Icons.Default.AppBlocking,
        accentColor = Color(0xFFE53935),
        navigationRoute = Screen.Debloat.route,
        features = listOf(
            AppFeature("Safe Uninstall", "Remove any app with safety rating guidance", "App Manager → Debloat", FeatureRequirement.SHIZUKU, Screen.Debloat.route),
            AppFeature("Restore Apps", "Re-install removed system apps", "App Manager → Debloat → Removed tab", FeatureRequirement.SHIZUKU),
            AppFeature("Bloatware Presets", "Community-curated debloat presets per manufacturer", "Debloat → Presets", FeatureRequirement.SHIZUKU, Screen.CantaPresets.route),
            AppFeature("Operation Logs", "Full log of all install/uninstall operations", "Debloat → Logs", FeatureRequirement.NONE, Screen.CantaLogs.route),
            AppFeature("User / System App Split", "Separate view of user-installed vs system apps", "Filter chips in Debloat screen", FeatureRequirement.SHIZUKU),
            AppFeature("No-Warranty Mode", "Bypass warnings for advanced removals", "Settings in Debloat screen", FeatureRequirement.ROOT),
        )
    ),
    SourceApp(
        name = "Hail",
        description = "Freeze, hide, or suspend apps to stop them from running in the background without uninstalling",
        githubUrl = "https://github.com/aistra0528/Hail",
        icon = Icons.Default.AcUnit,
        accentColor = Color(0xFF1565C0),
        navigationRoute = Screen.FreezeApps.route,
        features = listOf(
            AppFeature("Freeze Apps", "Suspend apps to stop background activity", "App Manager → Freeze Apps", FeatureRequirement.SHIZUKU, Screen.FreezeApps.route),
            AppFeature("Unfreeze", "Restore frozen apps to active state", "Freeze Apps → tap any frozen app", FeatureRequirement.SHIZUKU),
            AppFeature("Auto-Freeze on Screen Off", "Automatically freeze selected apps when screen turns off", "Freeze → Work Profile → Auto-Freeze on Screen Off", FeatureRequirement.SHIZUKU, Screen.HailWorkProfile.route),
            AppFeature("Work Profile Freeze", "Freeze apps using work profile + Island integration", "Freeze → Work Profile", FeatureRequirement.NONE, Screen.HailWorkProfile.route),
            AppFeature("Freeze All Tile", "Quick Settings tile to freeze all apps at once", "Add 'Freeze All' to Quick Settings", FeatureRequirement.SHIZUKU),
            AppFeature("Device Admin Freeze", "Freeze via device admin (no root needed)", "Freeze → Work Profile → Freeze Method → Device Admin", FeatureRequirement.NONE, Screen.HailWorkProfile.route),
            AppFeature("Scheduled Auto-Freeze", "Freeze apps on a daily schedule", "Freeze → Work Profile → Scheduled Auto-Freeze", FeatureRequirement.SHIZUKU, Screen.HailWorkProfile.route),
            AppFeature("Freeze Tags", "Group apps for batch freeze/unfreeze", "Long-press apps in Freeze screen", FeatureRequirement.SHIZUKU),
        )
    ),
    SourceApp(
        name = "Inure",
        description = "Powerful app manager with deep analytics, batch operations, component explorer, and permission management",
        githubUrl = "https://github.com/Hamza417/Inure",
        icon = Icons.Default.Analytics,
        accentColor = Color(0xFF7B1FA2),
        navigationRoute = Screen.AppManager.route,
        features = listOf(
            AppFeature("App Analytics Dashboard", "Usage statistics, install source breakdown, size analysis", "App Manager → Analytics", FeatureRequirement.NONE, Screen.InureAnalytics.route),
            AppFeature("Batch Operations", "Apply actions (freeze, clear cache, extract APK) to multiple apps", "App Manager → select apps → Batch Ops", FeatureRequirement.SHIZUKU, Screen.AppBatchOps.withPackage("")),
            AppFeature("Component Manager", "Enable/disable activities, services, receivers, providers", "App Manager → Component Manager", FeatureRequirement.SHIZUKU, Screen.ComponentManager.route),
            AppFeature("Permission Manager", "Grant/revoke runtime permissions", "App Manager → Permission Manager", FeatureRequirement.SHIZUKU, Screen.PermissionManager.route),
            AppFeature("Deep App Detail", "Full manifest, permissions, components in one view", "Tap any app → App Detail", FeatureRequirement.NONE, Screen.AppDetail.withPackage("com.example")),
            AppFeature("APK Extraction", "Extract installed APK to storage", "App Detail → Extract APK", FeatureRequirement.NONE),
            AppFeature("App Usage Tracking", "Screen time and launch count per app", "Analytics → Usage tab", FeatureRequirement.NONE, Screen.InureAnalytics.route),
            AppFeature("Installer Source Analysis", "See what installed each app (Play, ADB, sideload)", "Analytics → Installers tab", FeatureRequirement.NONE, Screen.InureAnalytics.route),
        )
    ),
    SourceApp(
        name = "Blocker",
        description = "Block components and trackers inside apps using online rule databases (IFW, PackageManager, Shizuku)",
        githubUrl = "https://github.com/lihenggui/blocker",
        icon = Icons.Default.Block,
        accentColor = Color(0xFFD32F2F),
        navigationRoute = Screen.Privacy.route,
        features = listOf(
            AppFeature("Component Blocker", "Disable specific app components (services, receivers)", "App Manager → Component Manager", FeatureRequirement.SHIZUKU, Screen.ComponentManager.route),
            AppFeature("Tracker Blocking", "Block tracking SDKs using curated online rules", "Privacy → Tracker Rules", FeatureRequirement.SHIZUKU, Screen.OnlineRules.route),
            AppFeature("Online Rule Library", "Database of 8,000+ tracker component signatures", "Privacy → Tracker Rules → Update rules", FeatureRequirement.NONE, Screen.OnlineRules.route),
            AppFeature("IFW Rules", "Intent Firewall rules for advanced component blocking", "Privacy → Component Manager → IFW mode", FeatureRequirement.SHIZUKU),
            AppFeature("Rule Export/Import", "Share block rules between devices", "Component Manager → Export", FeatureRequirement.NONE),
            AppFeature("Per-App Component List", "View all components of any app", "App Detail → Components tab", FeatureRequirement.NONE),
        )
    ),
    SourceApp(
        name = "ColorBlendr",
        description = "Apply custom Material You color schemes with fabricated overlay support — without root",
        githubUrl = "https://github.com/Mahmud0808/ColorBlendr",
        icon = Icons.Default.Palette,
        accentColor = Color(0xFFFF6F00),
        navigationRoute = Screen.Customization.route,
        features = listOf(
            AppFeature("Custom Monet Color", "Override Material You seed color system-wide", "Customization → Color Editor", FeatureRequirement.SHIZUKU, Screen.ColorEditor.route),
            AppFeature("Style Presets", "Apply pre-made color style combinations", "Customization → Color Styles", FeatureRequirement.SHIZUKU, Screen.ColorBlendrStyles.route),
            AppFeature("Fabricated Overlays", "Apply color changes via Android overlay system", "Customization → Color Editor → Apply", FeatureRequirement.SHIZUKU, Screen.ColorEditor.route),
            AppFeature("Per-App Theming", "Different color scheme per app", "Color Editor → Per-App tab", FeatureRequirement.SHIZUKU),
            AppFeature("Live Preview", "See color changes before applying", "Color Editor → Preview panel", FeatureRequirement.NONE),
            AppFeature("Backup/Restore Themes", "Save and restore color configurations", "Customization → Backup", FeatureRequirement.NONE),
        )
    ),
    SourceApp(
        name = "DarQ",
        description = "Per-app dark mode forcing — enable force-dark on any app individually, with scheduling and exclusions",
        githubUrl = "https://github.com/KieronQuinn/DarQ",
        icon = Icons.Default.DarkMode,
        accentColor = Color(0xFF212121),
        navigationRoute = Screen.DarkMode.route,
        features = listOf(
            AppFeature("Force Dark Mode", "Force dark mode on any app individually", "Customization → Dark Mode", FeatureRequirement.SHIZUKU, Screen.DarkMode.route),
            AppFeature("Sunrise/Sunset Schedule", "Auto-switch dark mode based on local sunrise/sunset", "Dark Mode → Sunrise/Sunset", FeatureRequirement.NONE, Screen.DarQSunriseSunset.route),
            AppFeature("Time-Based Schedule", "Custom daily schedule for dark mode activation", "Dark Mode → Schedule", FeatureRequirement.SHIZUKU),
            AppFeature("Per-App Exclusions", "Keep specific apps in light mode", "Dark Mode → App exceptions", FeatureRequirement.SHIZUKU),
            AppFeature("DarQ FAQ", "Detailed explanation of how force-dark works", "Dark Mode → FAQ", FeatureRequirement.NONE, Screen.DarQFaq.route),
        )
    ),
    SourceApp(
        name = "SmartSpacer",
        description = "Customizable lock screen / At-a-Glance widget with targets, complications, requirements, and plugins",
        githubUrl = "https://github.com/KieronQuinn/Smartspacer",
        icon = Icons.Default.Widgets,
        accentColor = Color(0xFF1976D2),
        navigationRoute = Screen.Widgets.route,
        features = listOf(
            AppFeature("SmartSpacer Targets", "Add/remove/reorder information targets on lock screen", "Widgets → Targets", FeatureRequirement.NONE, Screen.SmartSpacerTargets.route),
            AppFeature("Complications", "Add quick-info complications (battery, steps, weather)", "Widgets → Complications", FeatureRequirement.NONE, Screen.SmartSpacerTargets.route),
            AppFeature("Requirements System", "Set conditions for when targets appear", "Widgets → Requirements", FeatureRequirement.NONE, Screen.SmartSpacerTargets.route),
            AppFeature("Plugin Architecture", "Install third-party plugins for new targets", "Widgets → Plugins", FeatureRequirement.NONE, Screen.SmartSpacerTargets.route),
            AppFeature("At-a-Glance Override", "Replace Google's At-a-Glance on Pixel devices", "Widgets → Targets → At-a-Glance", FeatureRequirement.NONE, Screen.SmartSpacerTargets.route),
            AppFeature("Weather Integration", "Live weather on lock screen", "Widgets → Targets → Weather", FeatureRequirement.NONE),
            AppFeature("Calendar Events", "Upcoming events on lock screen", "Widgets → Targets → Calendar", FeatureRequirement.NONE),
        )
    ),
    SourceApp(
        name = "SD Maid SE",
        description = "System cleaning tool — app cache cleaner, deduplicator, corpse finder, system junk remover",
        githubUrl = "https://github.com/d4rken-org/sdmaid-se",
        icon = Icons.Default.CleaningServices,
        accentColor = Color(0xFF388E3C),
        navigationRoute = Screen.Storage.route,
        features = listOf(
            AppFeature("App Cleaner", "Clear cache for all apps with per-app breakdown", "Storage → App Cleaner", FeatureRequirement.SHIZUKU, Screen.AppCleaner.route),
            AppFeature("System Cleaner", "Remove temp files, logs, crash dumps, obsolete data", "Storage → System Cleaner", FeatureRequirement.SHIZUKU, Screen.SystemCleaner.route),
            AppFeature("Deduplicator", "Find and remove duplicate files by content hash", "Storage → Deduplicator", FeatureRequirement.NONE, Screen.Deduplicator.route),
            AppFeature("Corpse Finder", "Find orphaned data from uninstalled apps", "Storage → Corpse Finder", FeatureRequirement.SHIZUKU, Screen.CorpseFinder.route),
            AppFeature("Storage Analysis", "Visualize what's using storage space", "Storage → Overview card", FeatureRequirement.NONE),
            AppFeature("Custom Junk Filters", "Define custom file patterns to clean", "System Cleaner → Custom category", FeatureRequirement.NONE),
        )
    ),
    SourceApp(
        name = "Material Files",
        description = "Open-source Material Design file manager with root/FTP/SMB/SFTP access and archive support",
        githubUrl = "https://github.com/zhanghai/MaterialFiles",
        icon = Icons.Default.Folder,
        accentColor = Color(0xFF0097A7),
        navigationRoute = Screen.FileManager.route,
        features = listOf(
            AppFeature("File Manager", "Full Material Design file manager experience", "Storage → File Manager", FeatureRequirement.NONE, Screen.FileManager.route),
            AppFeature("Remote Connections", "Connect to FTP, SFTP, SMB, WebDAV servers", "Storage → Advanced → Remote", FeatureRequirement.NONE, Screen.FileManagerAdvanced.route),
            AppFeature("FTP Server", "Host files over FTP from your device", "Storage → Advanced → FTP Server", FeatureRequirement.NONE, Screen.FileManagerAdvanced.route),
            AppFeature("Archive Support", "Open/create ZIP, 7Z, TAR.GZ, RAR archives", "Storage → Advanced → Archives", FeatureRequirement.NONE, Screen.FileManagerAdvanced.route),
            AppFeature("Root Access", "Browse /system, /data, /proc as root", "File Manager → Root location", FeatureRequirement.ROOT),
            AppFeature("Bookmarks", "Save frequently visited folders as bookmarks", "Storage → Advanced → Bookmarks", FeatureRequirement.NONE, Screen.FileManagerAdvanced.route),
        )
    ),
    SourceApp(
        name = "InstallWithOptions",
        description = "Advanced APK installer with all PackageInstaller session flags, session params, and install options exposed",
        githubUrl = "https://github.com/Donnnno/InstallWithOptions",
        icon = Icons.Default.InstallMobile,
        accentColor = Color(0xFFF57F17),
        navigationRoute = Screen.Installer.route,
        features = listOf(
            AppFeature("Advanced APK Installer", "Install APKs with full flag control", "Installer screen", FeatureRequirement.NONE, Screen.Installer.route),
            AppFeature("Install Flags", "Toggle all PackageInstaller flags (downgrade, grant perms, etc.)", "Installer → Install Flags", FeatureRequirement.SHIZUKU, Screen.InstallFlags.route),
            AppFeature("Session Parameters", "Set installer package name, URI, size reservation", "Installer → Session Params tab", FeatureRequirement.NONE, Screen.InstallFlags.route),
            AppFeature("Allow Downgrade", "Install older version than currently installed", "Install Flags → Allow Version Downgrade", FeatureRequirement.SHIZUKU, Screen.InstallFlags.route),
            AppFeature("Grant All Permissions", "Auto-grant all runtime permissions on install", "Install Flags → Grant All Permissions", FeatureRequirement.SHIZUKU, Screen.InstallFlags.route),
            AppFeature("Don't Kill on Update", "Keep app running during update install", "Install Flags → Don't Kill App", FeatureRequirement.SHIZUKU, Screen.InstallFlags.route),
        )
    ),
    SourceApp(
        name = "Key Mapper",
        description = "Remap hardware buttons, volume keys, and gestures to any action — no root required via accessibility",
        githubUrl = "https://github.com/keymapperorg/KeyMapper",
        icon = Icons.Default.Keyboard,
        accentColor = Color(0xFF0288D1),
        navigationRoute = Screen.KeyMapper.route,
        features = listOf(
            AppFeature("Key Remapping", "Map any hardware key to any action", "Automation → Key Mapper", FeatureRequirement.NONE, Screen.KeyMapperAdvanced.route),
            AppFeature("Long Press Actions", "Different action for long-press vs short-press", "Key Mapper → New Mapping → Long Press", FeatureRequirement.NONE, Screen.KeyMapperAdvanced.route),
            AppFeature("Double Tap", "Double-tap key for separate action", "Key Mapper → New Mapping → Double Tap", FeatureRequirement.NONE, Screen.KeyMapperAdvanced.route),
            AppFeature("Multiple Actions", "Chain multiple actions per trigger", "Key Mapper → Add Action chain", FeatureRequirement.NONE, Screen.KeyMapperAdvanced.route),
            AppFeature("Shell Commands", "Execute shell commands via key press", "Key Mapper → Action → Shell Command", FeatureRequirement.SHIZUKU, Screen.KeyMapperAdvanced.route),
            AppFeature("Profile System", "Different mappings per use-case profile", "Key Mapper → Profiles", FeatureRequirement.NONE, Screen.KeyMapperAdvanced.route),
            AppFeature("Volume Key Mapping", "Map volume up/down to anything", "Key Mapper → Trigger → Volume keys", FeatureRequirement.NONE, Screen.KeyMapperAdvanced.route),
            AppFeature("Floating Button Trigger", "Floating on-screen button as trigger", "Key Mapper → Trigger → Floating Button", FeatureRequirement.NONE, Screen.KeyMapperAdvanced.route),
        )
    ),
    SourceApp(
        name = "Language Selector",
        description = "Set per-app language locale without relying on Android 13+ system settings — works back to Android 9",
        githubUrl = "https://github.com/VegaBobo/Language-Selector",
        icon = Icons.Default.Language,
        accentColor = Color(0xFF00695C),
        navigationRoute = Screen.LanguageCenter.route,
        features = listOf(
            AppFeature("Per-App Language", "Set a different language for each app individually", "Language Center", FeatureRequirement.SHIZUKU, Screen.LanguageCenter.route),
            AppFeature("34 Language Support", "Search and select from 34+ locale options", "Language Center → search bar", FeatureRequirement.SHIZUKU),
            AppFeature("Language Detail", "Change locale for a specific app with full locale list", "Language Center → tap any app", FeatureRequirement.SHIZUKU, Screen.LanguageDetail.withApp("com.example", "App")),
            AppFeature("Language QS Tile", "Quick-cycle through favorite languages from Quick Settings", "Add 'Language' tile to Quick Settings", FeatureRequirement.SHIZUKU),
            AppFeature("System Locale Override", "Override entire system language", "Language Center → System Locale", FeatureRequirement.SHIZUKU),
        )
    ),
    SourceApp(
        name = "Better Internet Tiles",
        description = "Independent Wi-Fi and Mobile Data Quick Settings tiles that don't interfere with each other",
        githubUrl = "https://github.com/CasperVerswijvelt/Better-Internet-Tiles",
        icon = Icons.Default.Wifi,
        accentColor = Color(0xFF1565C0),
        navigationRoute = Screen.NetworkCenter.route,
        features = listOf(
            AppFeature("Wi-Fi Tile", "Standalone Wi-Fi toggle that doesn't touch mobile data", "Add 'ACC Wi-Fi' to Quick Settings", FeatureRequirement.SHIZUKU),
            AppFeature("Mobile Data Tile", "Standalone mobile data toggle", "Add 'ACC Mobile Data' to Quick Settings", FeatureRequirement.SHIZUKU),
            AppFeature("Internet Tile", "Combined internet status tile", "Add 'ACC Internet' to Quick Settings", FeatureRequirement.SHIZUKU),
            AppFeature("Bluetooth Tile", "Quick Bluetooth toggle", "Add 'ACC Bluetooth' to Quick Settings", FeatureRequirement.SHIZUKU),
            AppFeature("NFC Tile", "Quick NFC toggle", "Add 'ACC NFC' to Quick Settings", FeatureRequirement.SHIZUKU),
            AppFeature("Hotspot Tile", "Quick mobile hotspot toggle", "Add 'ACC Hotspot' to Quick Settings", FeatureRequirement.SHIZUKU),
            AppFeature("Tile Settings", "Configure shell method, unlock requirement, SSID display", "Network → Tiles Settings", FeatureRequirement.NONE, Screen.TilesSettings.route),
            AppFeature("Shell Method Selection", "Choose between Shizuku, Root, ADB, or Accessibility", "Tiles Settings → Shell Method", FeatureRequirement.NONE, Screen.TilesSettings.route),
        )
    ),
    SourceApp(
        name = "RootlessJamesDSP",
        description = "Full-featured audio DSP without root — parametric EQ, convolver, bass boost, reverb via Android AudioEffect API",
        githubUrl = "https://github.com/ThePBone/RootlessJamesDSP",
        icon = Icons.Default.GraphicEq,
        accentColor = Color(0xFF6A1B9A),
        navigationRoute = Screen.AudioCenter.route,
        features = listOf(
            AppFeature("Parametric EQ", "Multi-band parametric equalizer with visual frequency display", "Audio → Parametric EQ", FeatureRequirement.NONE, Screen.ParametricEQ.route),
            AppFeature("AutoEQ Integration", "Apply AutoEQ headphone correction profiles", "Audio → AutoEQ", FeatureRequirement.NONE, Screen.AutoEQ.route),
            AppFeature("Liveprog Scripts", "Custom EEL/LiveProg DSP script editor and loader", "Audio → Liveprog Editor", FeatureRequirement.NONE, Screen.LiveprogEditor.route),
            AppFeature("Bass Boost", "Dedicated bass enhancement filter", "Audio → Bass Boost", FeatureRequirement.NONE, Screen.AudioCenter.route),
            AppFeature("Reverb / Room Effects", "Room reverb and spatial audio processing", "Audio → Reverb", FeatureRequirement.NONE, Screen.AudioCenter.route),
            AppFeature("Stereo Widening", "Stereo field expansion effect", "Audio → Stereo Widening", FeatureRequirement.NONE, Screen.AudioCenter.route),
            AppFeature("Dynamic Range Compressor", "Compression/limiting for consistent volume", "Audio → Compressor", FeatureRequirement.NONE, Screen.AudioCenter.route),
            AppFeature("App Audio Blocklist", "Exclude specific apps from DSP processing", "Audio → App Blocklist", FeatureRequirement.NONE, Screen.AppAudioBlocklist.route),
            AppFeature("Convolution Engine", "Load custom impulse response (IR) files for speaker simulation", "Audio → Convolver", FeatureRequirement.NONE, Screen.AudioCenter.route),
            AppFeature("Preset Manager", "Save and load EQ/DSP configuration presets", "Audio → Presets", FeatureRequirement.NONE, Screen.AudioCenter.route),
        )
    ),
    SourceApp(
        name = "ShizuCallRecorder",
        description = "Rootless call recorder using Shizuku + scrcpy audio capture — records both sides of calls",
        githubUrl = "https://github.com/chenxiaolong/BCR",
        icon = Icons.Default.RecordVoiceOver,
        accentColor = Color(0xFFC62828),
        navigationRoute = Screen.CallRecorder.route,
        features = listOf(
            AppFeature("Call Recording", "Record phone calls with both-direction audio", "Call Recorder", FeatureRequirement.SHIZUKU, Screen.CallRecorder.route),
            AppFeature("scrcpy Integration", "Use scrcpy audio capture for system audio recording", "Call Recorder → scrcpy Setup", FeatureRequirement.SHIZUKU, Screen.ScrcpyIntegration.route),
            AppFeature("Audio Codec Selection", "Choose Opus, AAC, FLAC, or PCM format", "Call Recorder → scrcpy → Audio Codec", FeatureRequirement.NONE, Screen.ScrcpyIntegration.route),
            AppFeature("Filename Format", "Customize recording filenames with date, number, direction", "Call Recorder → Settings → Filename Format", FeatureRequirement.NONE, Screen.CallRecordingSettings.route),
            AppFeature("Recording Direction", "Record incoming, outgoing, or both call directions", "Call Recorder → Settings → Direction", FeatureRequirement.NONE, Screen.CallRecordingSettings.route),
            AppFeature("Contact Exclusions", "Exclude specific contacts from recording", "Call Recorder → Settings → Contact Filter", FeatureRequirement.NONE, Screen.CallRecordingSettings.route),
            AppFeature("Auto-Delete", "Automatically delete recordings after N days", "Call Recorder → Settings → Auto Delete", FeatureRequirement.NONE, Screen.CallRecordingSettings.route),
            AppFeature("Recording Quality", "Configure bitrate and sample rate", "scrcpy → Audio Quality sliders", FeatureRequirement.NONE, Screen.ScrcpyIntegration.route),
        )
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFeaturesScreen(onNavigateTo: (String) -> Unit = {}) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedRequirement by remember { mutableStateOf<FeatureRequirement?>(null) }
    var expandedApp by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.BY_APP) }

    val filteredApps = ALL_SOURCE_APPS.filter { app ->
        (searchQuery.isBlank() ||
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.description.contains(searchQuery, ignoreCase = true) ||
                app.features.any { f -> f.name.contains(searchQuery, ignoreCase = true) || f.description.contains(searchQuery, ignoreCase = true) }) &&
                (selectedRequirement == null || app.features.any { f -> f.requirement == selectedRequirement })
    }

    val allFeatures = ALL_SOURCE_APPS.flatMap { app ->
        app.features.map { feature -> Triple(app, feature, feature.name) }
    }.filter { (app, feature, _) ->
        (searchQuery.isBlank() ||
                feature.name.contains(searchQuery, ignoreCase = true) ||
                feature.description.contains(searchQuery, ignoreCase = true) ||
                app.name.contains(searchQuery, ignoreCase = true)) &&
                (selectedRequirement == null || feature.requirement == selectedRequirement)
    }.sortedBy { (_, feature, _) -> feature.name }

    val totalFeatures = ALL_SOURCE_APPS.sumOf { it.features.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Features") },
                actions = {
                    IconButton(onClick = { viewMode = if (viewMode == ViewMode.BY_APP) ViewMode.ALL_FEATURES else ViewMode.BY_APP }) {
                        Icon(if (viewMode == ViewMode.BY_APP) Icons.Default.ViewList else Icons.Default.Apps, "Toggle view")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search ${totalFeatures} features across 17 repos…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { FilterChip(selected = selectedRequirement == null, onClick = { selectedRequirement = null }, label = { Text("All") }) }
                items(FeatureRequirement.entries.filter { it != FeatureRequirement.NONE }) { req ->
                    FilterChip(
                        selected = selectedRequirement == req,
                        onClick = { selectedRequirement = if (selectedRequirement == req) null else req },
                        label = { Text(req.label) },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when (viewMode) {
                        ViewMode.BY_APP -> "${filteredApps.size} repos"
                        ViewMode.ALL_FEATURES -> "${allFeatures.size} features"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text("$totalFeatures total features · 17 source repos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            when (viewMode) {
                ViewMode.BY_APP -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredApps, key = { it.name }) { app ->
                        SourceAppCard(
                            app = app,
                            isExpanded = expandedApp == app.name,
                            onToggleExpand = { expandedApp = if (expandedApp == app.name) null else app.name },
                            onNavigateTo = onNavigateTo,
                            searchQuery = searchQuery,
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                ViewMode.ALL_FEATURES -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allFeatures, key = { (app, feat, _) -> "${app.name}-${feat.name}" }) { (app, feature, _) ->
                        FlatFeatureCard(app = app, feature = feature, onNavigateTo = onNavigateTo)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

enum class ViewMode { BY_APP, ALL_FEATURES }

@Composable
private fun SourceAppCard(
    app: SourceApp,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNavigateTo: (String) -> Unit,
    searchQuery: String,
) {
    val filteredFeatures = if (searchQuery.isBlank()) app.features
    else app.features.filter { f -> f.name.contains(searchQuery, ignoreCase = true) || f.description.contains(searchQuery, ignoreCase = true) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 1.dp),
    ) {
        Column(modifier = Modifier.clickable(onClick = onToggleExpand).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(app.accentColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(app.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("${filteredFeatures.size} feature${if (filteredFeatures.size != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                if (app.navigationRoute != null) {
                    IconButton(onClick = { onNavigateTo(app.navigationRoute) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.outline)
            }
            AnimatedVisibility(visible = !isExpanded) {
                Text(
                    app.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    filteredFeatures.forEach { feature ->
                        FeatureRow(feature = feature, onNavigateTo = onNavigateTo)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: AppFeature, onNavigateTo: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().then(
            if (feature.route != null) Modifier.clickable { onNavigateTo(feature.route) } else Modifier
        ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Circle, null, modifier = Modifier.size(6.dp).padding(top = 6.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(feature.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                if (feature.requirement != FeatureRequirement.NONE) {
                    Surface(
                        color = feature.requirement.color().copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(feature.requirement.label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (feature.route != null) {
                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(feature.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("How: ${feature.howToUse}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun FlatFeatureCard(app: SourceApp, feature: AppFeature, onNavigateTo: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.then(
            if (feature.route != null) Modifier.clickable { onNavigateTo(feature.route) } else Modifier
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(app.accentColor), contentAlignment = Alignment.Center) {
                Icon(app.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(feature.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(feature.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (feature.requirement != FeatureRequirement.NONE) {
                    Surface(color = feature.requirement.color().copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp)) {
                        Text(feature.requirement.label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (feature.route != null) {
                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
