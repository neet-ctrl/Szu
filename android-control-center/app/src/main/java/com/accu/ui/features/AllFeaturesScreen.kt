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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accu.ui.components.InfoTooltipIcon

data class AppFeature(
    val name: String,
    val description: String,
    val howToUse: String,
    val requirement: FeatureRequirement = FeatureRequirement.NONE
)

enum class FeatureRequirement(val label: String, val color: @Composable () -> Color) {
    NONE("", { Color.Transparent }),
    SHIZUKU("Shizuku", { MaterialTheme.colorScheme.primaryContainer }),
    ROOT("Root", { MaterialTheme.colorScheme.errorContainer }),
    OPTIONAL_ROOT("Root Optional", { MaterialTheme.colorScheme.tertiaryContainer })
}

data class SourceApp(
    val name: String,
    val description: String,
    val githubUrl: String,
    val icon: ImageVector,
    val accentColor: Color,
    val features: List<AppFeature>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFeaturesScreen(onNavigateTo: (String) -> Unit = {}) {
    val apps = remember { buildAppList() }
    var searchQuery by remember { mutableStateOf("") }
    var filterRequirement by remember { mutableStateOf<FeatureRequirement?>(null) }
    val filtered = apps.filter { app ->
        searchQuery.isBlank() ||
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.features.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("All Features", fontWeight = FontWeight.Bold)
                            InfoTooltipIcon(
                                title = "All Features",
                                description = "Comprehensive list of every feature from all 17 integrated open-source apps. Expand each app card to see the full feature list with descriptions and usage instructions.\n\nTap the ⓘ icon on any feature for detailed help."
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search features…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Clear, null, Modifier.size(16.dp))
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                // Filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filterRequirement == null,
                            onClick = { filterRequirement = null },
                            label = { Text("All") }
                        )
                    }
                    items(FeatureRequirement.values().filter { it != FeatureRequirement.NONE }) { req ->
                        FilterChip(
                            selected = filterRequirement == req,
                            onClick = { filterRequirement = if (filterRequirement == req) null else req },
                            label = { Text(req.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Stats banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("17", "Apps merged", Icons.Outlined.Apps)
                        StatItem("${apps.sumOf { it.features.size }}+", "Features", Icons.Outlined.Star)
                        StatItem("1", "App to rule them", Icons.Outlined.Android)
                    }
                }
            }
            items(filtered, key = { it.name }) { app ->
                SourceAppCard(app = app, onNavigateTo = onNavigateTo)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceAppCard(app: SourceApp, onNavigateTo: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Header
            ListItem(
                headlineContent = {
                    Text(app.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = {
                    Text(app.description, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                },
                leadingContent = {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(app.accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(app.icon, null, modifier = Modifier.size(28.dp), tint = app.accentColor)
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("${app.features.size}", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                modifier = Modifier.clickable { expanded = !expanded }
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    app.features.forEach { feature ->
                        FeatureListItem(feature = feature)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureListItem(feature: AppFeature) {
    var showDetail by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable { showDetail = !showDetail }.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Circle, null, modifier = Modifier.size(6.dp), tint = MaterialTheme.colorScheme.primary)
            Text(feature.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (feature.requirement != FeatureRequirement.NONE) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = feature.requirement.color(),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        feature.requirement.label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            InfoTooltipIcon(
                title = feature.name,
                description = "${feature.description}\n\nHow to use: ${feature.howToUse}${if (feature.requirement != FeatureRequirement.NONE) "\n\nRequires: ${feature.requirement.label}" else ""}",
                iconSize = 16
            )
        }
        Text(feature.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedVisibility(visible = showDetail) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("How to use:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(feature.howToUse, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Suppress("LongMethod")
fun buildAppList(): List<SourceApp> = listOf(
    SourceApp(
        name = "Shizuku",
        description = "A powerful API that lets apps use system-level APIs with user consent via ADB — no root required. The backbone of ACC's privileged operations.",
        githubUrl = "https://github.com/RikkaApps/Shizuku",
        icon = Icons.Outlined.Security,
        accentColor = Color(0xFF6750A4),
        features = listOf(
            AppFeature("Shizuku Permission Request", "Request and manage Shizuku runtime permission", "Go to Shizuku Center → Grant Permission button", FeatureRequirement.SHIZUKU),
            AppFeature("Shizuku Status Monitor", "Real-time monitoring of Shizuku service state", "Dashboard → Shizuku status card shows running/stopped state", FeatureRequirement.SHIZUKU),
            AppFeature("ADB Setup Guide", "Step-by-step guide to start Shizuku over ADB", "Shizuku Center → 'How to Start' section", FeatureRequirement.NONE),
            AppFeature("ShizukuProvider Integration", "Embed Shizuku provider for seamless API access", "Automatic — handled in app manifest and ACCApplication", FeatureRequirement.NONE),
            AppFeature("User Service Binding", "Bind elevated UserService for privileged IPC calls", "Shizuku Center → Advanced → Bind User Service", FeatureRequirement.SHIZUKU),
            AppFeature("Permission Grant via Shizuku", "Grant system-level permissions without root", "Use pm grant via Shell or Permission Manager", FeatureRequirement.SHIZUKU),
            AppFeature("Wireless ADB Pairing Guide", "Guide for pairing Shizuku over Wi-Fi ADB", "Shizuku Center → Wi-Fi ADB section", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "aShellYou",
        description = "Full-featured ADB shell client with Local, Wi-Fi, and OTG modes, command history, bookmarks, AI analysis, file browser, and 200+ pre-loaded examples.",
        githubUrl = "https://github.com/MajesticWayne/aShellYou",
        icon = Icons.Outlined.Terminal,
        accentColor = Color(0xFF00897B),
        features = listOf(
            AppFeature("Local ADB Shell (Shizuku)", "Execute ADB commands locally without a PC using Shizuku", "Shell tab → Local mode → type command → Send", FeatureRequirement.SHIZUKU),
            AppFeature("Wi-Fi ADB Shell", "Connect to any device on the network via wireless ADB", "Shell tab → Wi-Fi mode → Connect → enter IP:port", FeatureRequirement.NONE),
            AppFeature("OTG ADB Shell", "Run ADB commands over USB OTG cable", "Shell tab → OTG mode → connect USB OTG cable", FeatureRequirement.NONE),
            AppFeature("Command History (200 entries)", "Browse and reuse previously run commands", "Shell → History icon, or use ↑/↓ arrow keys in input", FeatureRequirement.NONE),
            AppFeature("Command Bookmarks", "Save frequently used commands for quick access", "Long-press output line → Bookmark, or tap bookmark icon", FeatureRequirement.NONE),
            AppFeature("Command Examples Library", "200+ pre-loaded ADB commands organized by category", "Shell → Book icon → browse by category or search", FeatureRequirement.NONE),
            AppFeature("Tab Autocomplete", "Auto-complete partial commands from history/examples", "Type partial command → tap Tab button", FeatureRequirement.NONE),
            AppFeature("Smart Suggestions", "Real-time command suggestions as you type", "Suggestions appear above keyboard as you type", FeatureRequirement.NONE),
            AppFeature("Ctrl+C / Interrupt", "Send interrupt signal to kill running command", "Shell → ↑/↓/Ctrl+C utility buttons, or Stop FAB", FeatureRequirement.NONE),
            AppFeature("Output Search", "Search through terminal output text", "Shell → Search icon → type query to highlight matches", FeatureRequirement.NONE),
            AppFeature("Save Output to File", "Export terminal session to a text file", "Shell → Save icon → enter filename", FeatureRequirement.NONE),
            AppFeature("Copy Output Line", "Copy individual output lines to clipboard", "Long-press any output line → Copy", FeatureRequirement.NONE),
            AppFeature("AI Command Analysis", "AI-powered command explanation and danger detection", "Type command → tap AI sparkle icon → view analysis", FeatureRequirement.NONE),
            AppFeature("AI Danger Level Detection", "Warns about dangerous commands before execution", "Automatically shown when AI analysis runs", FeatureRequirement.NONE),
            AppFeature("AI Correction Suggestions", "Suggests command corrections for typos/errors", "AI analysis → Suggested corrections section", FeatureRequirement.NONE),
            AppFeature("ADB File Browser", "Browse device filesystem via ADB shell", "Shell → file browser (ls/cd commands or dedicated view)", FeatureRequirement.NONE),
            AppFeature("Wi-Fi Device QR Pairing", "Pair Wi-Fi ADB using QR code scan", "Shell → Wi-Fi mode → Pair via QR", FeatureRequirement.NONE),
            AppFeature("Wi-Fi Device Code Pairing", "Pair Wi-Fi ADB using 6-digit code", "Shell → Wi-Fi mode → Pair via code", FeatureRequirement.NONE),
            AppFeature("Saved Wi-Fi Devices", "Remember previously connected Wi-Fi ADB devices", "Shell → Wi-Fi mode → tap saved device to reconnect", FeatureRequirement.NONE),
            AppFeature("Crash History", "View app crash log history", "Settings → Crash History", FeatureRequirement.NONE),
            AppFeature("Look & Feel", "Customize shell font, palette style, UI scale", "Settings → Look & Feel", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "Canta",
        description = "Safely debloat Android devices by uninstalling system, carrier, and OEM apps using community-curated removal lists and custom presets.",
        githubUrl = "https://github.com/samolego/Canta",
        icon = Icons.Outlined.CleaningServices,
        accentColor = Color(0xFFE91E63),
        features = listOf(
            AppFeature("System App Uninstall", "Uninstall pre-installed system apps per-user (keeps OTA updates)", "App Manager → Debloat → select apps → Uninstall", FeatureRequirement.SHIZUKU),
            AppFeature("Carrier/OEM Bloatware List", "Community-sourced list of known bloatware with safety ratings", "Debloat screen shows safe/recommended/advanced badges", FeatureRequirement.NONE),
            AppFeature("Debloat Presets", "Pre-defined sets of apps to remove (e.g. Google, Samsung, MIUI)", "Debloat → Presets button → pick preset", FeatureRequirement.NONE),
            AppFeature("Custom Preset Creation", "Create and save your own debloat presets", "Debloat → FAB → create preset → add apps", FeatureRequirement.NONE),
            AppFeature("Preset Import/Export", "Share debloat presets as JSON files", "Debloat → More options → Export/Import preset", FeatureRequirement.NONE),
            AppFeature("App Safety Badges", "Color-coded badges showing removal risk level", "Debloat list → colored chips (safe/recommended/expert/unsafe)", FeatureRequirement.NONE),
            AppFeature("Uninstall Action Log", "Log of all debloat operations with timestamps", "App Manager → Debloat → Logs tab", FeatureRequirement.NONE),
            AppFeature("Filter by Removal Safety", "Filter debloat list by safe/recommended/advanced/expert", "Debloat → Filter icon → select safety level", FeatureRequirement.NONE),
            AppFeature("Reinstall System Apps", "Restore previously uninstalled system apps", "Debloat → show uninstalled → select → Reinstall", FeatureRequirement.SHIZUKU)
        )
    ),
    SourceApp(
        name = "Hail",
        description = "Freeze (suspend) apps without uninstalling. Suspended apps can't run in background but remain installed and data is preserved.",
        githubUrl = "https://github.com/aistra0528/Hail",
        icon = Icons.Outlined.AcUnit,
        accentColor = Color(0xFF42A5F5),
        features = listOf(
            AppFeature("Freeze Apps via Shizuku", "Suspend apps using pm disable-user without root", "App Manager → Freeze → select app → Freeze", FeatureRequirement.SHIZUKU),
            AppFeature("Freeze via Root", "Suspend apps using root for deeper freezing", "App Manager → Freeze → works automatically if root available", FeatureRequirement.ROOT),
            AppFeature("Freeze via Device Admin", "Freeze apps using device admin (no Shizuku/root needed)", "Settings → Activate Device Admin → use Freeze", FeatureRequirement.NONE),
            AppFeature("Auto-Freeze on Screen Off", "Automatically freeze tagged apps when screen turns off", "Freeze list → app → enable Auto-freeze", FeatureRequirement.NONE),
            AppFeature("Freeze Groups / Tags", "Organize apps into freeze groups for batch operations", "Freeze list → long-press → Add to group", FeatureRequirement.NONE),
            AppFeature("Quick Settings Freeze Tile", "Freeze/unfreeze all tagged apps from Quick Settings", "Add 'Hail' tile from QS edit panel", FeatureRequirement.NONE),
            AppFeature("Unfreeze to Launch", "Temporarily unfreeze an app to use it, re-freeze on exit", "Tap frozen app → Unfreeze to launch", FeatureRequirement.NONE),
            AppFeature("Freeze App List", "Dedicated list showing all frozen apps", "App Manager → Freeze Apps tab", FeatureRequirement.NONE),
            AppFeature("Hail API Integration", "Allow other apps to trigger freeze operations", "Handled via broadcast intents", FeatureRequirement.SHIZUKU)
        )
    ),
    SourceApp(
        name = "Inure",
        description = "Advanced app manager with full APK analysis, component inspection, and detailed storage/permission breakdown for every installed app.",
        githubUrl = "https://github.com/Hamza417/Inure",
        icon = Icons.Outlined.Analytics,
        accentColor = Color(0xFF7E57C2),
        features = listOf(
            AppFeature("Deep App Inspector", "View complete app details: version, paths, SDK, signatures", "App Manager → tap any app → App Detail", FeatureRequirement.NONE),
            AppFeature("Activity List", "Browse and launch all app activities including hidden ones", "App Detail → Activities tab → tap to launch", FeatureRequirement.SHIZUKU),
            AppFeature("Services List", "View and start/stop app services", "App Detail → Services tab", FeatureRequirement.SHIZUKU),
            AppFeature("Receivers List", "Inspect broadcast receivers with their intent filters", "App Detail → Receivers tab", FeatureRequirement.NONE),
            AppFeature("Content Providers List", "View all app content providers and their authorities", "App Detail → Providers tab", FeatureRequirement.NONE),
            AppFeature("Permissions Detail", "Full list of declared and granted permissions with status", "App Detail → Permissions tab", FeatureRequirement.NONE),
            AppFeature("App Storage Breakdown", "Detailed split of app size: APK, data, cache, OBB", "App Detail → Storage tab", FeatureRequirement.NONE),
            AppFeature("APK Manifest Viewer", "View decoded AndroidManifest.xml of any installed app", "App Detail → Manifest tab", FeatureRequirement.NONE),
            AppFeature("Certificate / Signature Info", "View APK signing certificate details", "App Detail → Signing tab", FeatureRequirement.NONE),
            AppFeature("Shared Libraries", "List native .so libraries used by the app", "App Detail → Libraries tab", FeatureRequirement.NONE),
            AppFeature("Operations Log", "History of all actions taken on each app", "App Detail → Operations tab", FeatureRequirement.NONE),
            AppFeature("App Search with Filters", "Search apps by name, package, or metadata with advanced filters", "App Manager → search bar → filter icon", FeatureRequirement.NONE),
            AppFeature("Batch Operations", "Apply operations to multiple apps at once", "App Manager → long-press → select multiple", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "Blocker",
        description = "Block individual app components (activities, services, receivers) to prevent unwanted background activity without fully disabling apps.",
        githubUrl = "https://github.com/lihenggui/blocker",
        icon = Icons.Outlined.Block,
        accentColor = Color(0xFFFF5722),
        features = listOf(
            AppFeature("Block Activities", "Prevent specific activities from launching", "App Detail → Components → Activities → toggle off", FeatureRequirement.SHIZUKU),
            AppFeature("Block Services", "Prevent background services from starting", "App Detail → Components → Services → toggle off", FeatureRequirement.SHIZUKU),
            AppFeature("Block Receivers", "Disable broadcast receivers to stop background triggers", "App Detail → Components → Receivers → toggle off", FeatureRequirement.SHIZUKU),
            AppFeature("Block Providers", "Disable content providers from being queried", "App Detail → Components → Providers → toggle off", FeatureRequirement.SHIZUKU),
            AppFeature("Component Rules Import", "Import component blocking rules from Blocker/IFW format", "Component Manager → Import rules", FeatureRequirement.NONE),
            AppFeature("Component Rules Export", "Export blocking rules as JSON/IFW for sharing/backup", "Component Manager → Export rules", FeatureRequirement.NONE),
            AppFeature("IFW (Intent Firewall) Mode", "Use Android's Intent Firewall for component blocking", "Component Manager → Mode → IFW", FeatureRequirement.ROOT),
            AppFeature("Component Search", "Search components by name across all apps", "Component Manager → Search", FeatureRequirement.NONE),
            AppFeature("Online Rules Database", "Download community component blocking rules", "Component Manager → Online rules", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "ColorBlendr",
        description = "Deep Material You color customization — change seed color, Monet style, palette, and apply per-surface color overrides system-wide.",
        githubUrl = "https://github.com/Mahmud0808/ColorBlendr",
        icon = Icons.Outlined.Palette,
        accentColor = Color(0xFFE91E63),
        features = listOf(
            AppFeature("Seed Color Picker", "Set a custom seed color for the entire Material You palette", "Customization → Color Scheme → pick seed color", FeatureRequirement.SHIZUKU),
            AppFeature("Monet Style Selector", "Choose from TONAL_SPOT, VIBRANT, EXPRESSIVE, SPRITZ, RAINBOW, FRUIT_SALAD", "Customization → Color Scheme → Monet Style dropdown", FeatureRequirement.SHIZUKU),
            AppFeature("Per-Surface Color Override", "Customize individual color roles (primary, secondary, tertiary…)", "Customization → Color Editor → tap any color role", FeatureRequirement.SHIZUKU),
            AppFeature("Custom Style Creation", "Save a named color style to apply/restore later", "Color Editor → Save Style button", FeatureRequirement.NONE),
            AppFeature("Dynamic Color Toggle", "Enable/disable Material You dynamic color system-wide", "Customization → Dynamic Color toggle", FeatureRequirement.SHIZUKU),
            AppFeature("Color Palette Preview", "Live preview of all 24 Material You color slots", "Customization → Color Scheme → full palette grid", FeatureRequirement.NONE),
            AppFeature("Wallpaper-based Color Extraction", "Auto-generate palette from current wallpaper", "Customization → Reset to wallpaper colors", FeatureRequirement.NONE),
            AppFeature("Chroma/Tone Sliders", "Fine-tune chroma and tone independently per color role", "Color Editor → select role → chroma/tone sliders", FeatureRequirement.SHIZUKU),
            AppFeature("Style Backup/Restore", "Backup current color style and restore later", "Customization → Backup styles", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "DarQ",
        description = "Force dark mode on apps that don't support it natively. Schedule dark mode by time or sunrise/sunset, and control per-app overrides.",
        githubUrl = "https://github.com/KieronQuinn/DarQ",
        icon = Icons.Outlined.DarkMode,
        accentColor = Color(0xFF1976D2),
        features = listOf(
            AppFeature("Force Dark Mode on Any App", "Apply force-dark rendering to unsupported apps", "Customization → Dark Mode → Per-App list → toggle on app", FeatureRequirement.SHIZUKU),
            AppFeature("Scheduled Dark Mode", "Set specific start/end times for dark mode activation", "Dark Mode → Schedule → set start and end time", FeatureRequirement.SHIZUKU),
            AppFeature("Sunrise/Sunset Auto Schedule", "Automatically switch dark mode at sunrise/sunset", "Dark Mode → Schedule → Sunrise/Sunset mode", FeatureRequirement.NONE),
            AppFeature("Per-App Dark Mode List", "Choose which apps get force-dark applied", "Dark Mode → App Picker → select apps", FeatureRequirement.SHIZUKU),
            AppFeature("System Dark Mode Toggle", "Toggle system-wide dark mode from the app", "Customization → Dark Mode → main toggle", FeatureRequirement.SHIZUKU),
            AppFeature("Dark Mode Widget", "Quick-toggle dark mode from the homescreen", "Widgets section → Dark Mode tile widget", FeatureRequirement.NONE),
            AppFeature("Backup/Restore Dark Settings", "Export and restore dark mode configuration", "Dark Mode → Settings → Backup/Restore", FeatureRequirement.NONE),
            AppFeature("Developer Options (Window Flags)", "Advanced window flag control for dark mode", "Dark Mode → Developer options section", FeatureRequirement.SHIZUKU),
            AppFeature("Blur Background Support", "Enable blur effects on supported devices (API 31+)", "Dark Mode → Advanced → Blur", FeatureRequirement.OPTIONAL_ROOT)
        )
    ),
    SourceApp(
        name = "SmartSpacer",
        description = "Customize the at-a-glance / smartspace area on the lock screen and notification bar with modular widgets.",
        githubUrl = "https://github.com/KieronQuinn/Smartspacer",
        icon = Icons.Outlined.Widgets,
        accentColor = Color(0xFF00BCD4),
        features = listOf(
            AppFeature("Lock Screen Widgets", "Add custom widgets to the lock screen smartspace area", "Widgets → Lock Screen tab → add widget", FeatureRequirement.NONE),
            AppFeature("Notification Bar Widgets", "Add widgets to the notification bar at-a-glance area", "Widgets → Notification tab → add widget", FeatureRequirement.NONE),
            AppFeature("Clock Widget", "Customizable clock widget for lock screen", "Widgets → Add → Clock type", FeatureRequirement.NONE),
            AppFeature("Weather Widget", "Show current weather in smartspace", "Widgets → Add → Weather → configure location", FeatureRequirement.NONE),
            AppFeature("Battery Widget", "Battery percentage widget in smartspace", "Widgets → Add → Battery type", FeatureRequirement.NONE),
            AppFeature("Now Playing Widget", "Show currently playing media in smartspace", "Widgets → Add → Media/Now Playing", FeatureRequirement.NONE),
            AppFeature("Calendar Widget", "Show next calendar event in smartspace", "Widgets → Add → Calendar → grant permission", FeatureRequirement.NONE),
            AppFeature("Notification Count Widget", "Show unread notification count", "Widgets → Add → Notifications", FeatureRequirement.NONE),
            AppFeature("Widget Ordering", "Drag-and-drop to reorder widgets", "Widgets → long-press and drag to reorder", FeatureRequirement.NONE),
            AppFeature("Widget Visibility Conditions", "Set conditions for when widgets appear", "Widgets → widget settings → Conditions", FeatureRequirement.NONE),
            AppFeature("Third-party Plugin Support", "Install SmartSpacer plugin apps for more widget types", "Widgets → Plugins section", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "SD Maid SE",
        description = "Clean junk files, find duplicates and corpse files left by uninstalled apps, analyze storage usage per category and app.",
        githubUrl = "https://github.com/d4rken-org/sdmaid-se",
        icon = Icons.Outlined.CleaningServices,
        accentColor = Color(0xFF4CAF50),
        features = listOf(
            AppFeature("Corpse Finder", "Find files left behind by uninstalled apps", "Storage → Corpse Finder → Scan → delete leftovers", FeatureRequirement.OPTIONAL_ROOT),
            AppFeature("App Junk Cleaner", "Clean cache, temp files, and junk per app", "Storage → Clean Junk → scan and clean", FeatureRequirement.NONE),
            AppFeature("System Cleaner", "Deep-clean system temp and log directories", "Storage → System Cleaner", FeatureRequirement.ROOT),
            AppFeature("Duplicate File Finder", "Detect and remove exact duplicate files", "Storage → Duplicate Files → scan → review → delete", FeatureRequirement.NONE),
            AppFeature("Large Files Browser", "Browse files by size to find space hogs", "Storage → Large Files → sorted list", FeatureRequirement.NONE),
            AppFeature("App Size Analyzer", "Breakdown of each app's storage: APK + data + cache", "Storage → App Sizes → sorted list", FeatureRequirement.NONE),
            AppFeature("Storage Usage Chart", "Visual pie/bar chart of storage usage by category", "Storage → Usage chart at top", FeatureRequirement.NONE),
            AppFeature("Empty Folder Cleaner", "Find and remove empty directories", "Storage → Empty Folders → clean", FeatureRequirement.NONE),
            AppFeature("Exclusion Rules", "Exclude paths from being scanned or deleted", "Storage → Settings → Exclusions → add path", FeatureRequirement.NONE),
            AppFeature("Scheduled Cleanup", "Run cleanup automatically on a schedule", "Storage → Schedule cleanup → weekly via WorkManager", FeatureRequirement.NONE),
            AppFeature("Volume Multi-Storage", "Scan internal + external SD card separately", "Storage → select volume", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "Material Files",
        description = "Full-featured material design file manager with root access, archive support, SMB/SFTP, and file operations.",
        githubUrl = "https://github.com/zhanghai/MaterialFiles",
        icon = Icons.Outlined.Folder,
        accentColor = Color(0xFF2196F3),
        features = listOf(
            AppFeature("Internal Storage Browse", "Browse device internal storage with material UI", "File Manager → Internal Storage", FeatureRequirement.NONE),
            AppFeature("Root Filesystem Access", "Browse /system, /data, and root directories", "File Manager → Root Filesystem (requires root)", FeatureRequirement.ROOT),
            AppFeature("Copy / Cut / Paste", "Multi-select file copy, cut, paste operations", "File Manager → select files → long-press → operation", FeatureRequirement.NONE),
            AppFeature("Rename / Move", "Rename files and move them to new locations", "File Manager → long-press file → Rename", FeatureRequirement.NONE),
            AppFeature("Delete with Confirm", "Delete files with confirmation dialog", "File Manager → long-press → Delete", FeatureRequirement.NONE),
            AppFeature("Create Folder", "Create new directories", "File Manager → + button → New Folder", FeatureRequirement.NONE),
            AppFeature("Archive Support (zip/tar/gz)", "Create and extract archives", "File Manager → long-press file → Compress/Extract", FeatureRequirement.NONE),
            AppFeature("File Properties", "View size, permissions, owner, dates, MIME type", "File Manager → long-press → Properties", FeatureRequirement.NONE),
            AppFeature("Share Files", "Share files to other apps via Android intent", "File Manager → select → Share", FeatureRequirement.NONE),
            AppFeature("Show Hidden Files", "Toggle visibility of dot-prefixed hidden files", "File Manager → menu → Show Hidden", FeatureRequirement.NONE),
            AppFeature("Sort by Name/Size/Date", "Sort file list by name, size, or modification date", "File Manager → Sort menu", FeatureRequirement.NONE),
            AppFeature("Grid / List View Toggle", "Switch between grid and list file view", "File Manager → layout toggle button", FeatureRequirement.NONE),
            AppFeature("SMB Network Browse", "Browse Windows file shares (SMB/CIFS)", "File Manager → Add Storage → SMB", FeatureRequirement.NONE),
            AppFeature("FTP/SFTP Browse", "Browse remote FTP/SFTP servers", "File Manager → Add Storage → FTP/SFTP", FeatureRequirement.NONE),
            AppFeature("Breadcrumb Navigation", "Tap any path segment to jump to that level", "File Manager → path bar at top", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "InstallWithOptions",
        description = "Install APKs with advanced flags: allow downgrade, bypass low-target SDK restriction, grant all runtime permissions at install time.",
        githubUrl = "https://github.com/zacharee/InstallWithOptions",
        icon = Icons.Outlined.InstallMobile,
        accentColor = Color(0xFF9C27B0),
        features = listOf(
            AppFeature("APK File Selection", "Pick any APK file from storage to install", "Installer → Select APK → pick from file picker", FeatureRequirement.NONE),
            AppFeature("Allow Version Downgrade", "Install older version over newer (normally blocked)", "Installer → Options → Allow Downgrade → Install", FeatureRequirement.SHIZUKU),
            AppFeature("Allow Test Packages", "Install APKs built with android:testOnly=true", "Installer → Options → Allow Test Packages", FeatureRequirement.SHIZUKU),
            AppFeature("Grant All Runtime Permissions", "Automatically grant all requested permissions on install", "Installer → Options → Grant All Runtime Permissions", FeatureRequirement.SHIZUKU),
            AppFeature("Bypass Low Target SDK Block", "Install apps targeting API < 23 on modern Android", "Installer → Options → Bypass Low Target SDK", FeatureRequirement.SHIZUKU),
            AppFeature("Replace Existing App", "Force replace an existing installed app", "Installer → Options → Replace Existing", FeatureRequirement.SHIZUKU),
            AppFeature("APK Info Preview", "Show package name, version, size before installing", "Installer → select APK → info shown before install", FeatureRequirement.NONE),
            AppFeature("Split APK / APKS Support", "Install split APK bundles (.apks/.xapk files)", "Installer → select .apks file", FeatureRequirement.SHIZUKU),
            AppFeature("Install via ROOT", "Use root shell for the most permissive installs", "Installer → uses root automatically if available", FeatureRequirement.ROOT),
            AppFeature("Install via ADB", "Install through adb pm session API", "Installer → ADB mode via Shizuku", FeatureRequirement.SHIZUKU),
            AppFeature("Content URI Support", "Install APKs from content:// URIs (e.g. from Downloads)", "Automatic when APK shared from another app", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "Key Mapper",
        description = "Remap hardware buttons (volume, power, camera) to any action without root, using the Accessibility Service.",
        githubUrl = "https://github.com/keymapperorg/KeyMapper",
        icon = Icons.Outlined.Keyboard,
        accentColor = Color(0xFFFF9800),
        features = listOf(
            AppFeature("Hardware Key Remapping", "Remap volume/power/other keys to any action", "Automation → Key Mapper → Add Mapping → set trigger key", FeatureRequirement.NONE),
            AppFeature("Key Combination Triggers", "Trigger actions with multi-key combos (e.g. Vol+Vol-)", "Add Mapping → Trigger → hold both keys", FeatureRequirement.NONE),
            AppFeature("Long Press Triggers", "Separate action for long-press vs short-press", "Add Mapping → Trigger → Long press toggle", FeatureRequirement.NONE),
            AppFeature("Double Press Triggers", "Trigger on double-press of a key", "Add Mapping → Trigger → Double press", FeatureRequirement.NONE),
            AppFeature("App Action: Open App", "Map key to launch a specific app", "Add Mapping → Action → Open App → select app", FeatureRequirement.NONE),
            AppFeature("App Action: Open URL", "Map key to open a URL/deeplink", "Add Mapping → Action → Open URL", FeatureRequirement.NONE),
            AppFeature("System Action: Toggle WiFi", "Map key to toggle Wi-Fi on/off", "Add Mapping → Action → System → Toggle WiFi", FeatureRequirement.SHIZUKU),
            AppFeature("System Action: Screenshot", "Map key to take a screenshot", "Add Mapping → Action → System → Screenshot", FeatureRequirement.NONE),
            AppFeature("System Action: Media Controls", "Play/pause/skip via hardware key remapping", "Add Mapping → Action → Media → Play/Pause", FeatureRequirement.NONE),
            AppFeature("Constraint: App Is Open", "Only activate mapping when specific app is open", "Add Mapping → Constraint → App is open", FeatureRequirement.NONE),
            AppFeature("Constraint: Screen On/Off", "Activate mapping based on screen state", "Add Mapping → Constraint → Screen is on/off", FeatureRequirement.NONE),
            AppFeature("Enable/Disable Mappings", "Toggle individual key mappings on/off", "Automation → Key Mapper → toggle switch on mapping", FeatureRequirement.NONE),
            AppFeature("Export/Import Mappings", "Backup and restore key mappings", "Automation → Key Mapper → Export/Import", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "Language Selector",
        description = "Override the system language on a per-app basis, or change the global device language without navigating through system settings.",
        githubUrl = "https://github.com/VegaBobo/Language-Selector",
        icon = Icons.Outlined.Language,
        accentColor = Color(0xFF00BCD4),
        features = listOf(
            AppFeature("Per-App Language Override", "Set a different language for each app independently", "Language Center → select app → pick language", FeatureRequirement.SHIZUKU),
            AppFeature("System Language Change", "Change device language without entering Settings", "Language Center → System Language → pick new locale", FeatureRequirement.SHIZUKU),
            AppFeature("Language Search", "Search 200+ supported locales/languages", "Language Center → search field → type language name", FeatureRequirement.NONE),
            AppFeature("Language Reset to System", "Remove per-app override and use system language", "Language Center → app → Reset to system default", FeatureRequirement.SHIZUKU),
            AppFeature("Language Quick Settings Tile", "Toggle to previously set language from Quick Settings", "Add Language Selector tile from QS edit", FeatureRequirement.NONE),
            AppFeature("Language Persistence", "Remember per-app language selections across reboots", "Automatic — stored in Room database", FeatureRequirement.NONE),
            AppFeature("Shizuku vs Root Mode", "Works with both Shizuku and root to apply locale changes", "Automatic detection in Language Center", FeatureRequirement.SHIZUKU)
        )
    ),
    SourceApp(
        name = "Better Internet Tiles",
        description = "Quick Settings tiles that actually toggle Wi-Fi, Mobile Data, Hotspot, Bluetooth, NFC, and Airplane Mode — as the stock tiles should.",
        githubUrl = "https://github.com/CasperVerswijvelt/Better-Internet-Tiles",
        icon = Icons.Outlined.SettingsEthernet,
        accentColor = Color(0xFF2196F3),
        features = listOf(
            AppFeature("Wi-Fi Toggle Tile", "QS tile that directly enables/disables Wi-Fi", "Add 'ACC Wi-Fi' tile from QS edit panel", FeatureRequirement.SHIZUKU),
            AppFeature("Mobile Data Toggle Tile", "QS tile that directly enables/disables mobile data", "Add 'ACC Mobile Data' tile from QS edit panel", FeatureRequirement.SHIZUKU),
            AppFeature("Hotspot Toggle Tile", "QS tile that directly enables/disables hotspot", "Add 'ACC Hotspot' tile from QS edit panel", FeatureRequirement.SHIZUKU),
            AppFeature("Bluetooth Toggle Tile", "QS tile that directly enables/disables Bluetooth", "Add 'ACC Bluetooth' tile from QS edit panel", FeatureRequirement.SHIZUKU),
            AppFeature("NFC Toggle Tile", "QS tile that directly enables/disables NFC", "Add 'ACC NFC' tile from QS edit panel", FeatureRequirement.SHIZUKU),
            AppFeature("Airplane Mode Tile", "QS tile that directly toggles airplane mode", "Add 'ACC Airplane Mode' tile from QS edit panel", FeatureRequirement.SHIZUKU),
            AppFeature("Require Unlock Option", "Optionally require device unlock before toggling", "Network Center → tile settings → Require Unlock", FeatureRequirement.NONE),
            AppFeature("Wi-Fi SSID Display", "Show connected Wi-Fi network name on the tile", "Wi-Fi tile settings → Show SSID", FeatureRequirement.NONE),
            AppFeature("Long-press for Settings", "Long-press tile to open full network settings", "Long-press any BIT tile in Quick Settings", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "RootlessJamesDSP",
        description = "System-wide audio DSP: parametric/graphic EQ, bass boost, convolver (impulse response), reverb, stereo widener, and limiter — all without root.",
        githubUrl = "https://github.com/ThePBone/RootlessJamesDSP",
        icon = Icons.Outlined.GraphicEq,
        accentColor = Color(0xFF3F51B5),
        features = listOf(
            AppFeature("DSP Enable/Disable Toggle", "Master on/off switch for all audio effects", "Audio Center → DSP master toggle", FeatureRequirement.NONE),
            AppFeature("Parametric Equalizer", "5-band parametric EQ with frequency/gain/Q controls", "Audio Center → Equalizer → Parametric mode", FeatureRequirement.NONE),
            AppFeature("Graphic Equalizer", "31-band graphic equalizer with visual frequency response", "Audio Center → Equalizer → Graphic mode", FeatureRequirement.NONE),
            AppFeature("EQ Presets", "Flat, Rock, Pop, Classical, Vocal, Bass, Treble presets", "Audio Center → Equalizer → Presets dropdown", FeatureRequirement.NONE),
            AppFeature("Bass Boost", "Dedicated bass boost with 0–1000 strength slider", "Audio Center → Bass Boost → strength slider", FeatureRequirement.NONE),
            AppFeature("Stereo Widener / Virtualizer", "Stereo widening effect with strength control", "Audio Center → Virtualizer → strength slider", FeatureRequirement.NONE),
            AppFeature("Reverb / Room Effect", "Simulate different acoustic environments", "Audio Center → Reverb → room preset selector", FeatureRequirement.NONE),
            AppFeature("Convolver (IR files)", "Load impulse response files for speaker/headphone profiles", "Audio Center → Convolver → load IR file", FeatureRequirement.NONE),
            AppFeature("Loudness Enhancer", "Boost perceived loudness without clipping", "Audio Center → Loudness → gain slider", FeatureRequirement.NONE),
            AppFeature("Per-App DSP Blocklist", "Exclude specific apps from DSP processing", "Audio Center → DSP Blocklist → add apps to exclude", FeatureRequirement.NONE),
            AppFeature("AutoEQ Profile Search", "Search and download AutoEQ headphone profiles", "Audio Center → AutoEQ → search headphone model", FeatureRequirement.NONE),
            AppFeature("Liveprog Scripting", "Run custom Faust DSP scripts for advanced effects", "Audio Center → Liveprog → load/edit script", FeatureRequirement.NONE),
            AppFeature("Foreground DSP Service", "Runs as persistent foreground service for system-wide effect", "Audio Center → enable DSP (auto-starts service)", FeatureRequirement.NONE)
        )
    ),
    SourceApp(
        name = "ShizuCallRecorder",
        description = "Rootless call recording using scrcpy audio capture + Shizuku. Records calls using the MediaProjection API workaround without root.",
        githubUrl = "https://github.com/kitsumed/ShizuCallRecorder",
        icon = Icons.Outlined.PhoneCallback,
        accentColor = Color(0xFF4CAF50),
        features = listOf(
            AppFeature("Auto Call Recording", "Automatically start recording when a call begins", "Call Recorder → Auto-record toggle → enable", FeatureRequirement.SHIZUKU),
            AppFeature("Manual Call Recording", "Manually trigger call recording from notification or app", "Call Recorder → Record button, or tap notification", FeatureRequirement.SHIZUKU),
            AppFeature("scrcpy Audio Capture", "Uses scrcpy audio source for rootless call audio capture", "Automatic — scrcpy server bundled in app", FeatureRequirement.SHIZUKU),
            AppFeature("Recording Playback", "Play back recorded calls from the recordings list", "Call Recorder → Recordings → tap recording → Play", FeatureRequirement.NONE),
            AppFeature("Recording Delete", "Delete individual or all recordings", "Call Recorder → Recordings → long-press → Delete", FeatureRequirement.NONE),
            AppFeature("Audio Format Selection", "Choose recording format: AAC, OPUS, FLAC", "Call Recorder → Settings → Format", FeatureRequirement.NONE),
            AppFeature("Audio Quality Setting", "Select recording bitrate/quality level", "Call Recorder → Settings → Quality", FeatureRequirement.NONE),
            AppFeature("Custom Save Location", "Choose where recordings are saved via SAF", "Call Recorder → Settings → Save location → pick folder", FeatureRequirement.NONE),
            AppFeature("File Name Format", "Customize recording filename with date/time/number templates", "Call Recorder → Settings → Filename format", FeatureRequirement.NONE),
            AppFeature("Contact-based Filtering", "Record only specific contacts or exclude some", "Call Recorder → Settings → Contact filter", FeatureRequirement.NONE),
            AppFeature("Call Direction Detection", "Tag recordings as incoming or outgoing", "Automatic — shown as badge on recording entry", FeatureRequirement.NONE),
            AppFeature("Recording Foreground Notification", "Persistent notification while recording is active", "Shows automatically during active recording", FeatureRequirement.NONE),
            AppFeature("Recording Metadata Storage", "Store contact name, number, duration, direction per recording", "Shown in recording detail view", FeatureRequirement.NONE)
        )
    )
)
