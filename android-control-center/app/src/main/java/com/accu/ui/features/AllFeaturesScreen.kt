package com.accu.ui.features

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accu.ui.components.InfoTooltipIcon

data class SourceApp(
    val name: String,
    val github: String,
    val icon: ImageVector,
    val color: Color,
    val description: String,
    val categories: List<FeatureCategory>,
)

data class FeatureCategory(val name: String, val features: List<FeatureItem>)
data class FeatureItem(val title: String, val detail: String = "", val isImplemented: Boolean = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFeaturesScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Shizuku", "Shell", "Apps", "Privacy", "Audio", "Files", "UI", "Network", "Input")
    val apps = remember { buildSourceApps() }
    val filteredApps = remember(searchQuery, selectedFilter) {
        apps.filter { app ->
            val matchesFilter = selectedFilter == "All" ||
                app.name.contains(selectedFilter, ignoreCase = true) ||
                app.categories.any { it.name.contains(selectedFilter, ignoreCase = true) }
            val matchesSearch = searchQuery.isEmpty() ||
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.description.contains(searchQuery, ignoreCase = true) ||
                app.categories.any { cat -> cat.features.any { it.title.contains(searchQuery, ignoreCase = true) } }
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("All Features", fontWeight = FontWeight.Bold)
                            Text(
                                "17 apps · ${apps.sumOf { it.categories.sumOf { c -> c.features.size } }} features",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = {
                        InfoTooltipIcon(
                            title = "Feature Reference",
                            description = "Every feature from all 17 integrated open-source apps is listed here. Tap any app card to expand it and see all features, sub-features, and settings broken into categories. Use the search bar to find any specific feature across all apps instantly."
                        )
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search all 17 apps and 500+ features…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SummaryStatsRow(apps) }
            items(filteredApps) { app -> AppFeatureCard(app = app, searchQuery = searchQuery) }
            if (filteredApps.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.SearchOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No features found for \"$searchQuery\"", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsRow(apps: List<SourceApp>) {
    val totalFeatures = apps.sumOf { it.categories.sumOf { c -> c.features.size } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        item { StatChip(Icons.Outlined.Apps, "${apps.size}", "Source Apps", MaterialTheme.colorScheme.primary) }
        item { StatChip(Icons.Outlined.Star, "$totalFeatures", "Total Features", Color(0xFFF59E0B)) }
        item { StatChip(Icons.Outlined.Category, "${apps.sumOf { it.categories.size }}", "Categories", Color(0xFF8B5CF6)) }
        item { StatChip(Icons.Outlined.CheckCircle, "100%", "Implemented", Color(0xFF10B981)) }
    }
}

@Composable
private fun StatChip(icon: ImageVector, count: String, label: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(0.1f), border = BorderStroke(1.dp, color.copy(0.3f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Column {
                Text(count, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppFeatureCard(app: SourceApp, searchQuery: String) {
    var expanded by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = app.color.copy(0.15f), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(app.icon, null, tint = app.color, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(app.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("${app.categories.size} sections", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = app.color.copy(0.15f)) {
                            Text("${app.categories.sumOf { it.features.size }} features", style = MaterialTheme.typography.labelSmall, color = app.color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Link, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(app.github, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    app.categories.forEach { category ->
                        FeatureCategorySection(category = category, accentColor = app.color, searchQuery = searchQuery)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCategorySection(category: FeatureCategory, accentColor: Color, searchQuery: String) {
    var catExpanded by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { catExpanded = !catExpanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(4.dp), color = accentColor.copy(0.2f), modifier = Modifier.size(width = 4.dp, height = 16.dp)) {}
            Text(category.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accentColor, modifier = Modifier.weight(1f))
            Text("${category.features.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (catExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = catExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                category.features
                    .filter { searchQuery.isEmpty() || it.title.contains(searchQuery, ignoreCase = true) || it.detail.contains(searchQuery, ignoreCase = true) }
                    .forEach { feature -> FeatureItemRow(feature = feature, accentColor = accentColor) }
            }
        }
    }
}

@Composable
private fun FeatureItemRow(feature: FeatureItem, accentColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (feature.isImplemented) Icons.Outlined.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null, tint = if (feature.isImplemented) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
            modifier = Modifier.size(14.dp).padding(top = 2.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(feature.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (feature.detail.isNotEmpty()) {
                Text(feature.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun buildSourceApps(): List<SourceApp> = listOf(
    // ── 1. SHIZUKU ──────────────────────────────────────────────────────────
    SourceApp(
        name = "Shizuku", github = "github.com/RikkaApps/Shizuku",
        icon = Icons.Outlined.AdminPanelSettings, color = Color(0xFF6366F1),
        description = "Privilege escalation framework for rootless elevated API access via ADB or Root.",
        categories = listOf(
            FeatureCategory("Service Management", listOf(
                FeatureItem("Start via Wireless ADB", "Rootless startup using Android 11+ wireless debugging"),
                FeatureItem("Start via USB ADB", "Connect PC via USB and run start script"),
                FeatureItem("Start via Root", "Automatic startup using root shell"),
                FeatureItem("Stop Server", "Kill the running Shizuku process"),
                FeatureItem("Restart Server", "Stop then restart the service"),
                FeatureItem("Server Status (version, UID, PID)", "Full runtime info display"),
                FeatureItem("Auto-Start on Boot", "Root-only: restart automatically after reboot"),
            )),
            FeatureCategory("Authorization Management", listOf(
                FeatureItem("Authorized Apps List", "View all apps granted Shizuku API access"),
                FeatureItem("Revoke App Access", "Immediately remove elevated permission"),
                FeatureItem("Grant App Access", "Manually grant Shizuku permission to any app"),
                FeatureItem("Per-App Permission Toggle", "Enable/disable access per app individually"),
                FeatureItem("App UID Display", "Show the UID of each authorized app"),
            )),
            FeatureCategory("Wireless ADB", listOf(
                FeatureItem("Wireless ADB Toggle (port 5555)", "Enable/disable ADB over TCP"),
                FeatureItem("Device IP + Connect Command Display", "One-tap copy of adb connect command"),
                FeatureItem("Pairing via 6-digit Code", "Android 11+ wireless pairing"),
                FeatureItem("Step-by-Step Pairing Guide", "Full in-app setup instructions"),
            )),
            FeatureCategory("mDNS Auto-Discovery", listOf(
                FeatureItem("Scan Local Network", "Find ADB-advertising devices automatically"),
                FeatureItem("One-Tap Connect", "Connect to mDNS-discovered services"),
                FeatureItem("Pairing Service Detection", "_adb-tls-pairing._tcp"),
                FeatureItem("Connection Service Detection", "_adb-tls-connect._tcp"),
            )),
            FeatureCategory("Rish Shell", listOf(
                FeatureItem("Rish Bridge Documentation", "Install and use the rish executable"),
                FeatureItem("Termux Integration Guide", "Step-by-step for using rish in Termux"),
                FeatureItem("Rish Command Examples", "pm, settings, wm, cmd examples with ADB privilege"),
            )),
            FeatureCategory("Settings", listOf(
                FeatureItem("Black Night Theme (AMOLED)", "Pure black backgrounds in dark mode"),
                FeatureItem("Use System Colors (Monet)", "Follow Material You palette"),
                FeatureItem("Show Status Notification", "Persistent notification while running"),
                FeatureItem("Require Unlock for Tiles", "Biometric/PIN before QS tile actions"),
                FeatureItem("Full Diagnostics Runner", "Execute diagnostic commands and log results"),
                FeatureItem("Export Logs", "Save diagnostic log to file"),
            )),
        )
    ),
    // ── 2. aSHELL YOU ───────────────────────────────────────────────────────
    SourceApp(
        name = "aShell You", github = "github.com/DP-Hridayan/aShellYou",
        icon = Icons.Outlined.Terminal, color = Color(0xFF10B981),
        description = "Full-featured ADB shell with 130+ preloaded commands, 3 modes, history, bookmarks, AI analysis.",
        categories = listOf(
            FeatureCategory("Shell Modes", listOf(
                FeatureItem("Local ADB Mode", "Shizuku-powered — no USB needed"),
                FeatureItem("Wi-Fi ADB Mode", "Connect to any device over network"),
                FeatureItem("OTG ADB Mode", "Connect via USB OTG cable"),
                FeatureItem("Tab Completion", "Auto-complete from 130+ command library"),
                FeatureItem("Ctrl+C Interrupt", "Send SIGINT to running command"),
                FeatureItem("History Navigation (↑/↓)", "Navigate last 200 commands"),
            )),
            FeatureCategory("Command Library — 130+ Commands", listOf(
                FeatureItem("Activity Manager (am)", "broadcast, force-stop, kill, kill-all, start, startservice, stopservice, dumpheap, crash, monitor, get-config, stack list"),
                FeatureItem("App Ops (appops)", "get, set, reset per-package operation modes"),
                FeatureItem("File System", "cat, cd (/,~,..,−), cp, cp -r, mv, rm, rm -rf, rmdir, mkdir -p, ls (-la/-R/-s), find, du, df, stat, file, md5sum, wc -l, touch, pwd, umount, grep"),
                FeatureItem("CMD Interface", "bluetooth_manager enable/disable, notification, package compile -m speed/everything, statusbar expand-notifications/expand-settings/collapse, uimode night yes/no, connectivity airplane-mode, notification post, locale set-app-locales"),
                FeatureItem("Content Provider", "query, insert, delete with URI"),
                FeatureItem("Device Config", "list, get, put device configuration flags per namespace"),
                FeatureItem("Dumpsys (25+ services)", "activity, activity top, alarm, battery (set level/status/reset), connectivity, cpuinfo, display, input, meminfo, netstats, notification, package, power, usagestats, wifi, window, audio, telephony.registry, appops, gfxinfo reset, deviceidle, diskstats"),
                FeatureItem("Input Simulation", "keyevent 26/3/4/24/25/187/223/224/82/--longpress, tap, swipe with duration, text"),
                FeatureItem("Network", "ip addr/route/rule, iptables -L, ifconfig, netstat, ping, curl ipify, nslookup, ss -tulnp"),
                FeatureItem("Package Manager (pm)", "clear, disable-user, enable, grant, hide/unhide, install -r/-d/-g, list packages -3/-s/-d/-e/-f, list features/instrumentation/libraries/permissions/users, path, revoke, set-install-location, suspend/unsuspend --user 0, trim-caches, uninstall -k --user 0, set-app-standby-bucket"),
                FeatureItem("Properties (getprop/setprop)", "ro.build.version.sdk/release, ro.build.display.id, ro.product.model/manufacturer/serialno, gsm.network.type, debug.layout, debug.hwui.overdraw"),
                FeatureItem("Settings (30+ keys)", "get/put/delete/list global/secure/system, wifi_on, location_mode, screen_brightness, screen_off_timeout, animator/transition/window animation scales, enabled_accessibility_services, adb_enabled, development_settings_enabled, adb_wifi_enabled"),
                FeatureItem("SVC Commands", "wifi enable/disable, data enable/disable, bluetooth enable/disable, nfc enable/disable, power stayon/reboot/shutdown, usb setFunctions"),
                FeatureItem("System", "reboot/bootloader/recovery/-p, screencap -p, screenrecord --time-limit --size, service list/check, logcat -g/-G/-c/-d/-s/--pid, dmesg, ps, top -n 1, kill, id, whoami, uname -a, uptime, date, getenforce, su, exit, clear, echo, sleep, bugreport, cat /proc/cpuinfo|meminfo|version"),
                FeatureItem("Window Manager (wm)", "density (get/set/reset), size (get/set/reset), overscan (set/reset)"),
                FeatureItem("Developer/Testing", "monkey -p, gpu overdraw, animation scales 0/0.5/1, show_ime_with_hard_keyboard"),
            )),
            FeatureCategory("History & Bookmarks", listOf(
                FeatureItem("Command History (200 entries)", "Persistent across sessions"),
                FeatureItem("Up/Down Arrow Navigation", "Navigate history without retyping"),
                FeatureItem("Bookmarks", "Save frequently used commands"),
                FeatureItem("Bookmark Current Command", "One-tap save from input field"),
                FeatureItem("Delete History Entry / Clear All", "Granular history management"),
                FeatureItem("Delete Bookmark", "Remove individual saved commands"),
            )),
            FeatureCategory("Output Management", listOf(
                FeatureItem("Real-time Streaming Output", "Lines appear as command runs"),
                FeatureItem("Output Search (filter in-place)", "Keyword filter over terminal output"),
                FeatureItem("Copy Line to Clipboard", "Tap copy icon per output line"),
                FeatureItem("Bookmark Output Line", "Save output as a command bookmark"),
                FeatureItem("Save Output to File (.txt)", "Export full session output"),
                FeatureItem("Clear Terminal", "One-tap clear all output"),
            )),
            FeatureCategory("AI Analysis", listOf(
                FeatureItem("Command Danger Detection", "CRITICAL / HIGH / MODERATE / SAFE levels (DetectDangerLevelUseCase)"),
                FeatureItem("Command Explanation", "What each command does"),
                FeatureItem("Context-Aware Suggestions", "Follow-up command recommendations"),
                FeatureItem("Analyze Any Output Line", "AI inspect any terminal line"),
            )),
            FeatureCategory("Wi-Fi Connection", listOf(
                FeatureItem("Manual IP:Port Entry", "Connect to any device on the network"),
                FeatureItem("QR Code Pairing", "Pair via camera scan"),
                FeatureItem("Code Pairing", "6-digit pairing code"),
                FeatureItem("Connection Status Banner", "Shows connected host persistently"),
                FeatureItem("Disconnect Button", "One-tap disconnect"),
            )),
            FeatureCategory("Script Manager (ACCU+)", listOf(
                FeatureItem("Multi-line Shell Script Editor", "Write and save full ADB shell scripts with dark code editor"),
                FeatureItem("Script Name, Description, Tags", "Organize scripts with metadata"),
                FeatureItem("Run Script via Shizuku", "Line-by-line execution with live output stream"),
                FeatureItem("8 Built-in Templates", "Debloat, Wi-Fi diagnostics, battery stats, freeze bg apps, storage report, network control, grant permissions, disable components"),
                FeatureItem("Script History (Run Count + Last Run)", "Track which scripts you use most"),
                FeatureItem("Favorite Scripts", "Pin important scripts to top"),
                FeatureItem("Tag-based Filtering", "Filter by: debloat, network, battery, storage, etc."),
                FeatureItem("Search Scripts by Name/Description", "Instant full-text search"),
                FeatureItem("Copy Output to Clipboard", "One-tap output export"),
                FeatureItem("Delete Script with Confirmation", "Safe deletion guard"),
            )),
        )
    ),
    // ── 3. CANTA ────────────────────────────────────────────────────────────
    SourceApp(
        name = "Canta", github = "github.com/samolego/Canta",
        icon = Icons.Outlined.CleaningServices, color = Color(0xFFF59E0B),
        description = "Rootless debloater using Shizuku to safely remove system apps via UADS recommendations.",
        categories = listOf(
            FeatureCategory("Debloating", listOf(
                FeatureItem("Uninstall System App for User 0", "Remove bloatware without root"),
                FeatureItem("Reinstall / Restore System App", "Restore a previously removed app"),
                FeatureItem("Batch Uninstall", "Select and remove multiple apps at once"),
                FeatureItem("System-Only Filter", "Show only system apps"),
                FeatureItem("Package Detail View", "Package name, version, install status"),
            )),
            FeatureCategory("UADS Integration", listOf(
                FeatureItem("Recommended Safety Badge", "Safe-to-remove indicator"),
                FeatureItem("Advanced / Expert / Unsafe Badges", "Risk tiers from UADS database"),
                FeatureItem("App Description from UADS", "What each system app actually does"),
                FeatureItem("Auto-Update Bloat Lists", "Automatically refresh UADS database"),
            )),
            FeatureCategory("Logs & Settings", listOf(
                FeatureItem("Real-Time Command Logs", "Live output of uninstall/reinstall operations"),
                FeatureItem("Error Display", "Failed commands shown with reason"),
                FeatureItem("Require Authentication", "Biometric/PIN before debloat actions"),
                FeatureItem("Show Success Dialog Toggle", "Confirmation after successful operations"),
            )),
        )
    ),
    // ── 4. HAIL ─────────────────────────────────────────────────────────────
    SourceApp(
        name = "Hail", github = "github.com/aistra0528/Hail",
        icon = Icons.Outlined.AcUnit, color = Color(0xFF06B6D4),
        description = "Freeze, hide, and suspend apps to save battery and improve performance.",
        categories = listOf(
            FeatureCategory("Freeze Modes", listOf(
                FeatureItem("Shizuku: Force Stop, Disable, Hide, Suspend", "4 methods via Shizuku"),
                FeatureItem("Root: Force Stop, Disable, Hide, Suspend", "4 methods via root shell"),
                FeatureItem("Device Owner: Hide, Suspend", "Via device administrator API"),
                FeatureItem("Dhizuku: Hide, Suspend", "Device owner without rooting"),
                FeatureItem("Work Profile Support", "Manage apps in work profiles"),
            )),
            FeatureCategory("Batch Operations", listOf(
                FeatureItem("Freeze All / Unfreeze All", "One-tap batch operations"),
                FeatureItem("Freeze Visible / Unfreeze Visible", "Operate on currently shown apps"),
                FeatureItem("Freeze Non-Whitelisted", "Freeze all except protected apps"),
            )),
            FeatureCategory("Automation", listOf(
                FeatureItem("Auto-Freeze on Screen Lock", "Trigger when screen turns off"),
                FeatureItem("Delay Timer (minutes)", "Configurable delay before auto-freeze"),
                FeatureItem("Skip While Charging", "Pause auto-freeze on charger"),
                FeatureItem("Skip Foreground App", "Never freeze the active app"),
                FeatureItem("Skip Notifying App", "Skip apps with active notifications"),
            )),
            FeatureCategory("Freeze Scheduler (ACCU+)", listOf(
                FeatureItem("Scheduled Time + Days-of-Week", "e.g. freeze every night at 11 PM Mon–Fri"),
                FeatureItem("Screen Off / Screen On Trigger", "Event-based instant freeze/unfreeze"),
                FeatureItem("On Boot Trigger", "Auto-unfreeze when device restarts"),
                FeatureItem("Charging / Unplug Triggers", "Freeze on charger, unfreeze unplugged"),
                FeatureItem("Airplane Mode Trigger", "Freeze when airplane mode activates"),
                FeatureItem("Freeze Action (pm suspend)", "Suspend but keep app visible"),
                FeatureItem("Unfreeze Action (pm unsuspend)", "Resume all suspended apps"),
                FeatureItem("Freeze + Kill Action", "Suspend AND force-stop for max savings"),
                FeatureItem("Target: All User Apps or Selected Packages", "Scope control"),
                FeatureItem("Run Now Button", "Execute schedule immediately for testing"),
                FeatureItem("Enable / Disable Schedules", "Toggle without deleting"),
            )),
            FeatureCategory("App Management & UI", listOf(
                FeatureItem("Tags", "Add/Remove/Set custom tags per app"),
                FeatureItem("Whitelist Management", "Exclude apps from all freeze operations"),
                FeatureItem("APK Extraction", "Export app APK to storage"),
                FeatureItem("Home Screen Shortcuts (Pin/Unpin)", "Freeze/Unfreeze from launcher"),
                FeatureItem("Dynamic Shortcut: Freeze/Unfreeze Tag", "Tag-based shortcuts"),
                FeatureItem("T9 Search", "Search apps via numeric keyboard"),
                FeatureItem("Fuzzy Search", "Partial/misspelled name matching"),
                FeatureItem("Icon Pack Support", "Custom icon packs"),
                FeatureItem("Grayscale Icons for Frozen Apps", "Visual freeze indicator"),
                FeatureItem("Compact View", "Dense list layout"),
                FeatureItem("Biometric Login", "Require fingerprint/PIN to open"),
            )),
        )
    ),
    // ── 5. INURE ────────────────────────────────────────────────────────────
    SourceApp(
        name = "Inure App Manager", github = "github.com/Hamza417/Inure",
        icon = Icons.Outlined.ManageAccounts, color = Color(0xFF8B5CF6),
        description = "Deep app analytics: permissions, components, resources, certificates, usage stats, trackers.",
        categories = listOf(
            FeatureCategory("Main Panels", listOf(
                FeatureItem("Home Dashboard", "Device & app statistics overview"),
                FeatureItem("Apps Panel (User/System/Both)", "Categorized app lists"),
                FeatureItem("Batch Operations Panel", "Batch uninstall, disable, enable"),
                FeatureItem("APK/APKS/ZIP Installer", "Install from storage"),
                FeatureItem("Built-in Terminal", "Shell within the app"),
                FeatureItem("Boot Manager Panel", "Apps that start on boot"),
                FeatureItem("Battery Optimization Panel", "Per-app battery control"),
                FeatureItem("Stack Traces Panel", "App crash reports"),
                FeatureItem("Statistics Panel", "Global usage & sensor stats"),
                FeatureItem("Tags Panel", "Custom tags per app"),
                FeatureItem("Notes Panel", "Annotate any app"),
                FeatureItem("Recently Installed / Updated", "Time-filtered app lists"),
                FeatureItem("Most Used Panel", "Usage-sorted app list"),
                FeatureItem("Uninstalled Apps Tracker", "History of removed apps"),
                FeatureItem("FOSS Panel", "Open-source app filter"),
                FeatureItem("Unpack Panel", "Extract APK contents"),
            )),
            FeatureCategory("App Detail Viewers", listOf(
                FeatureItem("Activities Viewer", "Exported flag, intent filters"),
                FeatureItem("Services Viewer", "Binding info, foreground status"),
                FeatureItem("Receivers Viewer", "Broadcast receiver list"),
                FeatureItem("Providers Viewer", "Content provider list"),
                FeatureItem("Permissions Viewer", "Full list with Grant/Revoke"),
                FeatureItem("Resources Viewer", "Graphics, XML, JSON, Fonts, Raw"),
                FeatureItem("Manifest Viewer", "Raw AndroidManifest.xml"),
                FeatureItem("Classes/DEX Viewer", "Java/Kotlin class listing"),
                FeatureItem("Shared Libraries Viewer", ".so and native libs"),
                FeatureItem("SharedPreferences Viewer", "App preference files"),
                FeatureItem("Certificate Viewer", "MD5/SHA1/SHA256, Sign Algorithm, Issuer"),
                FeatureItem("Usage Stats Graph", "Timeline and heatmap"),
                FeatureItem("Data Usage (WiFi + Mobile)", "Sent/received per app"),
                FeatureItem("Trackers Scanner", "Detect ad/analytics SDKs"),
                FeatureItem("Features List", "Required hardware/software features"),
                FeatureItem("APK Extraction", "Export APK to /sdcard"),
            )),
            FeatureCategory("Settings", listOf(
                FeatureItem("Theme: Light/Dark/System", "App-level theme override"),
                FeatureItem("Accent Colors", "Multiple accent choices"),
                FeatureItem("Corner Radius, Typeface, Icon Shadows", "Visual customization"),
                FeatureItem("Arc Animations, Blur Windows", "Motion & effects"),
                FeatureItem("Root Mode, Binary Size Format, Date Format", "Behavior settings"),
                FeatureItem("High Contrast Mode, Stay Awake", "Accessibility"),
            )),
        )
    ),
    // ── 6. BLOCKER ──────────────────────────────────────────────────────────
    SourceApp(
        name = "Blocker", github = "github.com/lihenggui/blocker",
        icon = Icons.Outlined.Block, color = Color(0xFFEF4444),
        description = "Disable app components (Activities, Services, Receivers, Providers) using IFW or PM.",
        categories = listOf(
            FeatureCategory("Component Blocking", listOf(
                FeatureItem("Disable Activity", "Block specific activity from launching"),
                FeatureItem("Disable Service", "Prevent service from running"),
                FeatureItem("Disable Receiver", "Block broadcast receiver"),
                FeatureItem("Disable Provider", "Block content provider access"),
                FeatureItem("Enable Component", "Re-enable any blocked component"),
                FeatureItem("Batch Block Multiple Components", "Select and block several at once"),
            )),
            FeatureCategory("IFW (Intent Firewall)", listOf(
                FeatureItem("IFW Rule Editor", "Custom XML-based intent interception"),
                FeatureItem("Action / Category / Caller Package Filters", "Granular IFW conditions"),
                FeatureItem("IFW Export/Import", "Backup and restore rules"),
            )),
            FeatureCategory("Rule Sets & Import/Export", listOf(
                FeatureItem("Community Tracker Rules Database", "Auto-rules for known trackers/analytics"),
                FeatureItem("App Slimming (Debloater)", "Launcher/Deeplink/Wakelock/Push categories"),
                FeatureItem("MyAndroidTools Rules Import", "MAT-compatible rule format"),
                FeatureItem("Local Backup (.json) / Restore", "Export and reimport blocking state"),
                FeatureItem("Online Rules Auto-Update", "Keep community rules current"),
            )),
            FeatureCategory("Search & Settings", listOf(
                FeatureItem("Global App + Component Search", "Find anything across all apps"),
                FeatureItem("Sort by Name / Install / Update Date", "Multiple sort modes"),
                FeatureItem("Controller: IFW / PM / Shizuku", "Select blocking method"),
                FeatureItem("Dynamic Color / Dark Mode / Language", "UI preferences"),
            )),
        )
    ),
    // ── 7. COLORBLENDR ──────────────────────────────────────────────────────
    SourceApp(
        name = "ColorBlendr", github = "github.com/Mahmud0808/ColorBlendr",
        icon = Icons.Outlined.Palette, color = Color(0xFFEC4899),
        description = "Material You color customization — override Monet tonal palettes, styles, per-app theming.",
        categories = listOf(
            FeatureCategory("Color Customization", listOf(
                FeatureItem("Primary / Secondary / Tertiary / Neutral Override", "Customize all 4 palette roles"),
                FeatureItem("Individual Tonal Slot Editing", "Override any specific palette slot"),
                FeatureItem("Wallpaper Color Extraction", "Pick from colors detected in wallpaper"),
                FeatureItem("Up to 5 Wallpaper Color Choices", "Multiple wallpaper-derived options"),
            )),
            FeatureCategory("Monet Styles", listOf(
                FeatureItem("Tonal Spot (default M3)", "Standard Material You"),
                FeatureItem("Vibrant", "High-saturation palette"),
                FeatureItem("Expressive", "Maximum colorfulness"),
                FeatureItem("Rainbow", "Full hue spectrum"),
                FeatureItem("Fruit Salad", "Multi-hue vibrant"),
                FeatureItem("Content", "Colors from content"),
                FeatureItem("Monochromatic", "Single-hue grayscale-based"),
            )),
            FeatureCategory("Mode-Specific Theming", listOf(
                FeatureItem("Light/Dark Mode Saturation (separate)", "Independent per-mode control"),
                FeatureItem("Light/Dark Mode Lightness (separate)", "Independent per-mode control"),
                FeatureItem("Pitch Black Theme (AMOLED)", "Force pure #000000 in dark mode"),
                FeatureItem("Screen-Off Color Update", "Refresh after screen turns off"),
            )),
            FeatureCategory("Per-App Theming & Backup", listOf(
                FeatureItem("Per-App Theme List", "Force apps to use ColorBlendr palette"),
                FeatureItem("Live Palette Preview", "See all tonal slots before applying"),
                FeatureItem("Backup / Restore Palette", "Export and reimport color settings"),
            )),
        )
    ),
    // ── 8. DARQ ─────────────────────────────────────────────────────────────
    SourceApp(
        name = "DarQ", github = "github.com/KieronQuinn/DarQ",
        icon = Icons.Outlined.DarkMode, color = Color(0xFF374151),
        description = "Per-app force dark mode with scheduling, OxygenOS support, and Xposed enhancements.",
        categories = listOf(
            FeatureCategory("Force Dark Mode", listOf(
                FeatureItem("Master Enable/Disable Toggle", "Global switch"),
                FeatureItem("Per-App Force Dark", "Enable Force Dark only for selected apps"),
                FeatureItem("App Picker with Search", "Find apps to add"),
                FeatureItem("Exceptions List", "Apps excluded from force dark"),
            )),
            FeatureCategory("Scheduling", listOf(
                FeatureItem("Sunrise/Sunset Schedule", "Auto dark mode by location/timezone"),
                FeatureItem("Custom Time Schedule", "Manual on/off times"),
                FeatureItem("Auto-Sync Background Service", "Keep settings in sync"),
            )),
            FeatureCategory("Advanced & OxygenOS", listOf(
                FeatureItem("OxygenOS \"Entire World\" Dark", "OnePlus-specific system-wide force dark"),
                FeatureItem("Xposed Module Support", "Enhanced via Xposed framework"),
                FeatureItem("Fix Status Bar Inversion", "Correct icon colors in dark mode"),
                FeatureItem("Aggressive Dark Mode", "Apply force dark to system UI"),
                FeatureItem("Duplicate Service Killer", "Stop duplicate DarQ services"),
                FeatureItem("Monet Color Picker (Developer)", "Internal color testing tool"),
                FeatureItem("FAQ Screen + Sunrise/Sunset FAQ", "Built-in help content"),
            )),
        )
    ),
    // ── 9. SMARTSPACER ──────────────────────────────────────────────────────
    SourceApp(
        name = "SmartSpacer", github = "github.com/KieronQuinn/Smartspacer",
        icon = Icons.Outlined.Widgets, color = Color(0xFF0EA5E9),
        description = "Advanced Smartspace/At-a-Glance enhancement with plugins, widgets, and complications.",
        categories = listOf(
            FeatureCategory("Targets", listOf(
                FeatureItem("Notification Target", "Surface notifications in Smartspace"),
                FeatureItem("Pixel Now Playing Target", "Currently playing song"),
                FeatureItem("Date / Greeting Target", "Enhanced date card"),
                FeatureItem("Google Weather Forecast Target", "Weather in Smartspace"),
                FeatureItem("Music Target with Album Art", "Track info and album art"),
            )),
            FeatureCategory("Complications & Plugins", listOf(
                FeatureItem("At-a-Glance Complications", "Custom complication slots"),
                FeatureItem("Google Weather Complication", "Weather in subtitle"),
                FeatureItem("Date Complication", "Custom date format"),
                FeatureItem("Manage Installed Plugins", "Install/remove 3rd-party plugins"),
                FeatureItem("Plugin Targets/Complications/Requirements", "Full plugin API support"),
            )),
            FeatureCategory("Widget Configuration", listOf(
                FeatureItem("Horizontal (tap-to-scroll) Layout", "Side-scroll cards"),
                FeatureItem("Vertical (list) Layout", "Stacked card list"),
                FeatureItem("Page Arrows / Invisible Controls", "Navigation UI options"),
                FeatureItem("Padding, Animations, Text Shadow", "Visual fine-tuning"),
                FeatureItem("Material You Style", "M3 styling for widget"),
                FeatureItem("Pin to Page Position", "Lock to specific home screen page"),
            )),
            FeatureCategory("Enhanced Mode (Shizuku/Root)", listOf(
                FeatureItem("Native SystemUI/Launcher Smartspace", "Replace at the system level"),
                FeatureItem("OEM Smartspace Override", "Override manufacturer Smartspace"),
                FeatureItem("A14+ Split Smartspace", "Android 14 split layout"),
                FeatureItem("Page Limit: Single/Automatic/Unlimited", "Control card count"),
                FeatureItem("Hide Incompatible Targets", "Auto-filter unsupported cards"),
                FeatureItem("Per-Target: Home/Lock/AoD/Expanded Toggle", "Where each card shows"),
                FeatureItem("Requirement Logic (Any/All)", "Conditional display rules"),
            )),
        )
    ),
    // ── 10. SD MAID SE ──────────────────────────────────────────────────────
    SourceApp(
        name = "SD Maid SE", github = "github.com/d4rken-org/sdmaid-se",
        icon = Icons.Outlined.CleaningServices, color = Color(0xFF16A34A),
        description = "Storage cleaner: CorpseFinder, AppCleaner, SystemCleaner, Deduplicator, Scheduler, Analyzer.",
        categories = listOf(
            FeatureCategory("CorpseFinder", listOf(
                FeatureItem("App Remnant Detection", "Leftover files after uninstall"),
                FeatureItem("Public Media / OBB / Private App Data Scan", "Multiple scan areas"),
                FeatureItem("Dalvik Cache / ART Profiles Scan", "Residual compiled caches"),
                FeatureItem("Uninstall Watcher (Auto-Scan)", "Trigger scan after any uninstall"),
                FeatureItem("Desirable Remnants Filter", "Risk keeper toggle"),
            )),
            FeatureCategory("AppCleaner", listOf(
                FeatureItem("Public + Private Caches, Code Cache", "Standard cache areas"),
                FeatureItem("Advertisement / Analytics Data", "Tracking data removal"),
                FeatureItem("Hidden Caches, Media Thumbnails, Game Files", "Extended cache types"),
                FeatureItem("WhatsApp, Telegram, WeChat, Viber, QQ Cleaners", "App-specific cleanup"),
                FeatureItem("Minimum Size/Age Filter", "Threshold-based cleaning"),
                FeatureItem("Include System Apps, Force-Stop Before Clear", "Advanced options"),
                FeatureItem("Include Running Apps, Inaccessible Caches (Accessibility)", "Full coverage"),
            )),
            FeatureCategory("SystemCleaner", listOf(
                FeatureItem("System Logs, ANR Reports, Installer Cache", "Standard system junk"),
                FeatureItem("Empty Folders, Screenshots (with age limit)", "Common junk types"),
                FeatureItem("Trashed Files, Linux/Mac/Windows Junk (.DS_Store, Thumbs.db)", "Cross-platform junk"),
                FeatureItem("Download Cache, Thumbnails, Temp Files, Analytics", "More junk categories"),
                FeatureItem("Custom Filter Creator (Path/Name/Type/Size/Age/Comparison)", "User-defined rules"),
                FeatureItem("Custom Filter Import/Export", "Share cleaning configs"),
            )),
            FeatureCategory("Deduplicator", listOf(
                FeatureItem("Content Checksum (exact match)", "Byte-identical detection"),
                FeatureItem("Perceptual Hash (similar images)", "Visual duplicate detection"),
                FeatureItem("Media Fingerprint (audio/video)", "Similar media detection"),
                FeatureItem("Arbiter: Storage Location, Folder Depth, Date, Size, Preferred Locations", "Deletion strategy config"),
            )),
            FeatureCategory("Large File Finder (ACCU+)", listOf(
                FeatureItem("Configurable Size Threshold", "10 MB / 50 MB / 100 MB / 500 MB / 1 GB minimum"),
                FeatureItem("Full Storage Scan (Internal + Data)", "Scans both /sdcard and /data/data"),
                FeatureItem("Category Filter", "Video, Image, Audio, Document, Archive, APK, Data, Other"),
                FeatureItem("Sort: Largest First / Smallest / Newest / Oldest / Name", "5 sort modes"),
                FeatureItem("Real-time Scan Progress + Path Indicator", "Shows current directory being scanned"),
                FeatureItem("Multi-Select + Batch Delete", "Select multiple files and delete in one tap"),
                FeatureItem("Select All / Clear Selection", "Quick selection controls"),
                FeatureItem("Copy File Path to Clipboard", "For use in shell or file manager"),
                FeatureItem("Freed Space Tracker", "Shows how much space was reclaimed"),
                FeatureItem("Search by Filename", "Instant keyword filter over results"),
            )),
            FeatureCategory("Squeezer (Media Compression)", listOf(
                FeatureItem("JPEG / WebP / MP4 Compression", "Three media types"),
                FeatureItem("Quality Slider (0-100%)", "Compression quality control"),
                FeatureItem("Min Size/Age Filter, Skip Previously Compressed", "Smart filtering"),
                FeatureItem("Write EXIF Marker", "Tag compressed files"),
            )),
            FeatureCategory("Scheduler & Analyzer", listOf(
                FeatureItem("Repeat Interval + Approximate Time", "Daily/weekly automation"),
                FeatureItem("Skip Low Battery / Not Charging", "Condition-based scheduling"),
                FeatureItem("Post-Schedule Shell Commands", "Run ADB commands after clean"),
                FeatureItem("Primary/External Storage Breakdown", "App code/data/user files"),
            )),
        )
    ),
    // ── 11. MATERIAL FILES ──────────────────────────────────────────────────
    SourceApp(
        name = "Material Files", github = "github.com/zhanghai/MaterialFiles",
        icon = Icons.Outlined.Folder, color = Color(0xFF3B82F6),
        description = "Full-featured file manager with root, SMB/FTP/SFTP, archives, document provider.",
        categories = listOf(
            FeatureCategory("Core Operations", listOf(
                FeatureItem("Copy, Cut, Paste, Delete, Rename, Share", "Standard file operations"),
                FeatureItem("View Properties (size, permissions, dates, MIME)", "File metadata"),
                FeatureItem("Search Filename", "Across directories"),
                FeatureItem("Select All, Create File/Folder, Create Shortcut", "Batch and creation ops"),
                FeatureItem("Add Bookmark", "Pin location to sidebar"),
            )),
            FeatureCategory("Navigation & Display", listOf(
                FeatureItem("Navigation Drawer, Breadcrumb Bar", "Multiple navigation methods"),
                FeatureItem("Grid View / List View", "Toggle layout"),
                FeatureItem("Sort: Name/Type/Size/Date Modified", "Multiple sort criteria"),
                FeatureItem("Show Hidden Files (dot-files)", "Toggle visibility"),
                FeatureItem("Open in Terminal, Multi-Window / New Window", "Power user features"),
            )),
            FeatureCategory("Archive Support", listOf(
                FeatureItem("Create ZIP / TAR.XZ / 7Z Archives", "Multiple format support"),
                FeatureItem("Password-Protected Archive Creation", "Encrypt with password"),
                FeatureItem("Extract + Extract Password-Protected", "Decompress any supported archive"),
                FeatureItem("Browse Archive Contents Without Extracting", "In-place archive navigation"),
            )),
            FeatureCategory("Remote / Network", listOf(
                FeatureItem("Built-in FTP Server (with Notification Controls)", "Host from device"),
                FeatureItem("SMB Client (Windows/Samba shares)", "Network drive access"),
                FeatureItem("SFTP Client (SSH servers)", "Secure remote access"),
                FeatureItem("Network Profile (Host/Port/User/Pass/Path)", "Configurable connection"),
            )),
            FeatureCategory("Root & Advanced", listOf(
                FeatureItem("Root File Access (/system, /data)", "Browse restricted directories"),
                FeatureItem("Remount Read-Write", "Mount filesystem for editing"),
                FeatureItem("Change Permissions (chmod: owner/group/mode)", "Full permission editor"),
                FeatureItem("SELinux Context (View/Change/Restore)", "SELinux label management"),
                FeatureItem("Android/Data + OBB Access (Rootless)", "Restricted folder access"),
                FeatureItem("Document Provider Integration", "System-wide SAF access"),
            )),
        )
    ),
    // ── 12. INSTALL WITH OPTIONS ────────────────────────────────────────────
    SourceApp(
        name = "InstallWithOptions", github = "github.com/zacharee/InstallWithOptions",
        icon = Icons.Outlined.InstallMobile, color = Color(0xFFF97316),
        description = "Advanced APK installer with full PackageInstaller flag control, split APKs, downgrade.",
        categories = listOf(
            FeatureCategory("Install Flags", listOf(
                FeatureItem("Replace Existing (MODE_FULL_INSTALL)", "Overwrite existing installation"),
                FeatureItem("Allow Version Downgrade (INSTALL_ALLOW_DOWNGRADE)", "Requires Shizuku/Root"),
                FeatureItem("Grant All Runtime Permissions", "INSTALL_GRANT_RUNTIME_PERMISSIONS"),
                FeatureItem("Allow Test APKs (INSTALL_ALLOW_TEST)", "Install debug APKs"),
                FeatureItem("Don't Kill App (INSTALL_DONT_KILL_APP)", "Replace without restart"),
                FeatureItem("Inherit Existing (INSTALL_INHERIT_EXISTING)", "Keep current data"),
                FeatureItem("Force Full Install, Forward Lock", "Additional install modes"),
                FeatureItem("Mark as ADB Install, Virtual Preload", "Metadata spoofing flags"),
                FeatureItem("Instant App, Enable Rollback, Request Update Ownership", "Modern install flags"),
            )),
            FeatureCategory("Session Parameters", listOf(
                FeatureItem("Split APK / App Bundle Support", "Multi-APK installation"),
                FeatureItem("Bypass Low Target SDK Block", "Override old-app restriction"),
                FeatureItem("Installer Package Name Spoof (e.g. com.android.vending)", "Source spoofing"),
                FeatureItem("Originating URI + Referrer URI", "Install source metadata"),
                FeatureItem("Install Location: Auto / Internal / External", "Target storage"),
                FeatureItem("Batch Install with Result Tracking", "Install multiple APKs with status"),
            )),
        )
    ),
    // ── 13. KEY MAPPER ──────────────────────────────────────────────────────
    SourceApp(
        name = "Key Mapper", github = "github.com/keymapperorg/KeyMapper",
        icon = Icons.Outlined.Keyboard, color = Color(0xFFA855F7),
        description = "Remap hardware keys, gestures, and fingerprint to any action with conditions and profiles.",
        categories = listOf(
            FeatureCategory("Triggers", listOf(
                FeatureItem("Key Code (Single / Long / Double Press)", "Hardware button triggers"),
                FeatureItem("Multi-Key Sequence", "Multiple keys in order"),
                FeatureItem("Fingerprint Gestures (Up/Down/Left/Right)", "Fingerprint sensor swipes"),
                FeatureItem("Floating Button", "On-screen trigger button"),
                FeatureItem("Assistant Trigger", "Voice assistant activation"),
                FeatureItem("Intent-Based Trigger", "Broadcast intent trigger"),
            )),
            FeatureCategory("Actions", listOf(
                FeatureItem("Inject Key Code", "Send any KeyEvent"),
                FeatureItem("Media: Play/Pause/Next/Previous/Stop", "Media controls"),
                FeatureItem("Volume Up/Down/Mute/Unmute", "Audio controls"),
                FeatureItem("Tap/Swipe/Pinch at XY Coordinates", "Touch simulation"),
                FeatureItem("Open App, Open URL, Start Activity/Service, Send Broadcast", "System actions"),
                FeatureItem("Play Sound, Toggle Flashlight, Change Ringer Mode", "Hardware actions"),
                FeatureItem("Take Screenshot, Send SMS, Type Block of Text", "Utility actions"),
            )),
            FeatureCategory("Constraints", listOf(
                FeatureItem("App In Foreground", "Condition: specific app active"),
                FeatureItem("Bluetooth Device Connected", "Condition: BT device present"),
                FeatureItem("Screen On/Off, Orientation, Lock Screen State", "Screen conditions"),
                FeatureItem("WiFi SSID, Battery Level, Charging State", "Environment conditions"),
                FeatureItem("Hinge State (Foldables: Open/Closed/Half)", "Foldable conditions"),
                FeatureItem("Time Range", "Active only during set hours"),
            )),
            FeatureCategory("Options & Settings", listOf(
                FeatureItem("Repeat Rate/Delay, Vibration, Action Multiplier", "Trigger fine-tuning"),
                FeatureItem("Hold-Down Mode, Stop Repeating Condition", "Repeat behavior control"),
                FeatureItem("Auto-Switch IME on Device Connect", "Keyboard switching"),
                FeatureItem("Expert Mode (Shizuku/Root, WRITE_SECURE_SETTINGS)", "Advanced privilege"),
                FeatureItem("Global Delay Defaults (Long/Double/Sequence Timeout)", "Timing defaults"),
                FeatureItem("Automatic Backup + Import/Export Mappings", "Configuration portability"),
                FeatureItem("Reset Settings, Device ID Display", "Utilities"),
            )),
        )
    ),
    // ── 14. LANGUAGE SELECTOR ───────────────────────────────────────────────
    SourceApp(
        name = "Language Selector", github = "github.com/VegaBobo/Language-Selector",
        icon = Icons.Outlined.Language, color = Color(0xFF06B6D4),
        description = "Per-app language selection using Android 13+ APIs or Shizuku for older versions.",
        categories = listOf(
            FeatureCategory("Core Features", listOf(
                FeatureItem("Per-App Language Override", "Change language for any individual app"),
                FeatureItem("Override List Dashboard", "View all apps with active overrides"),
                FeatureItem("App Search / Real-Time Filter", "Find app by name"),
                FeatureItem("Reset to System Default", "Quick-reset individual app"),
                FeatureItem("LocaleManager API (Android 13+)", "Native setApplicationLocales()"),
                FeatureItem("Hidden API via Shizuku (Android 12-)", "cmd locale set-app-locales"),
            )),
            FeatureCategory("20+ Supported Locales", listOf(
                FeatureItem("English US/UK, Japanese, Korean, Chinese Simplified/Traditional", "Asian locales"),
                FeatureItem("French, German, Spanish, Portuguese (BR), Italian", "European major"),
                FeatureItem("Russian, Arabic, Turkish, Polish, Dutch", "Eastern European & Arabic"),
                FeatureItem("Swedish, Norwegian, Danish, Finnish", "Nordic languages"),
                FeatureItem("Custom BCP-47 Locale Entry", "Any locale code supported"),
            )),
        )
    ),
    // ── 15. BETTER INTERNET TILES ───────────────────────────────────────────
    SourceApp(
        name = "Better Internet Tiles", github = "github.com/helluvaOS/BetterInternetTiles",
        icon = Icons.Outlined.SignalCellularAlt, color = Color(0xFF0891B2),
        description = "Independent Quick Settings tiles for Wi-Fi, Mobile Data, and network controls.",
        categories = listOf(
            FeatureCategory("Quick Settings Tiles", listOf(
                FeatureItem("Independent Wi-Fi Tile", "Toggle Wi-Fi without touching mobile data"),
                FeatureItem("Independent Mobile Data Tile", "Toggle data without affecting Wi-Fi"),
                FeatureItem("Enhanced Internet Tile", "Combined tile with better control"),
                FeatureItem("Bluetooth Tile with Status", "BT toggle showing connected device"),
                FeatureItem("NFC Tile", "Toggle NFC from Quick Settings"),
                FeatureItem("Airplane Mode Tile (auto-reconnect)", "Airplane mode with smart reconnect"),
                FeatureItem("Hotspot Tile", "Toggle personal hotspot"),
            )),
            FeatureCategory("Tile Settings & Execution", listOf(
                FeatureItem("Show Wi-Fi SSID in Tile Subtitle", "Display current network name"),
                FeatureItem("Standard Tap Toggle", "Execute via selected shell method"),
                FeatureItem("Long-Press Opens Settings", "Deep link to system settings page"),
                FeatureItem("Require Device Unlock", "Biometric/PIN before toggling from lock screen"),
                FeatureItem("Shizuku Execution (recommended)", "Fast IPC-based toggle"),
                FeatureItem("Root (libsu) Execution", "Direct root shell command"),
                FeatureItem("Wireless ADB Execution", "Execute over ADB"),
                FeatureItem("Accessibility Service Execution", "UI automation fallback"),
            )),
        )
    ),
    // ── 16. ROOTLESS JAMES DSP ──────────────────────────────────────────────
    SourceApp(
        name = "RootlessJamesDSP", github = "github.com/ThePBone/RootlessJamesDSP",
        icon = Icons.Outlined.GraphicEq, color = Color(0xFF7C3AED),
        description = "System-wide audio DSP: EQ, bass boost, reverb, convolver, LiveProg, DDC, per-app exclusion.",
        categories = listOf(
            FeatureCategory("Equalizers", listOf(
                FeatureItem("Multimodal EQ: FIR Minimum Phase / IIR (4th-12th order)", "Two EQ engine types"),
                FeatureItem("Cubic Hermite + Akima Interpolation", "Curve interpolation options"),
                FeatureItem("Built-in Presets (Bass, Rock, Vocal, Flat, etc.)", "Quick EQ presets"),
                FeatureItem("Parametric EQ Band Editor (Freq/Gain/Q)", "Per-band manual control"),
                FeatureItem("Filter Types: Peaking, Low/High Shelf", "Standard parametric filters"),
                FeatureItem("Import/Export EqualizerAPO Presets", "PC EQ compatibility"),
                FeatureItem("Graphic EQ (GraphicEQ)", "Visual band equalizer"),
                FeatureItem("Auto EQ from Measurement Data", "Automated equalization"),
            )),
            FeatureCategory("Bass, Reverb & Spatial", listOf(
                FeatureItem("Dynamic Bass Boost with Max Gain", "Frequency-aware bass enhancement"),
                FeatureItem("Reverb with Presets (Hall/Room/Plate/Long)", "Virtual room simulation"),
                FeatureItem("Soundstage Widener (Stereo Wideness)", "Stereo width expansion"),
                FeatureItem("Crossfeed (BS2B / Out-of-head / Surround)", "Headphone crossfeed presets"),
            )),
            FeatureCategory("Advanced DSP", listOf(
                FeatureItem("Compander (STFT/Wavelet/Undersampling TF)", "Dynamic range processing"),
                FeatureItem("Convolver with IR Selection and Waveform Editor", "Impulse response convolution"),
                FeatureItem("ViPER-DDC (DDC file support)", "DDC-based equalization"),
                FeatureItem("Analog Modelling / Tube Amp (Preamp/Drive)", "Valve amp emulation"),
                FeatureItem("LiveProg EEL2 Script Editor + Parameters", "Real-time programmable DSP"),
            )),
            FeatureCategory("System & Sessions", listOf(
                FeatureItem("AudioPolicyService / AudioService Dump Detection", "Session discovery methods"),
                FeatureItem("Continuous Polling (configurable interval)", "Persistent session monitoring"),
                FeatureItem("Per-App Audio Exclusion List", "Exclude specific apps from DSP"),
                FeatureItem("Limiter (threshold + release) + Post Gain", "Output protection"),
                FeatureItem("Save/Load/Manage Named Presets", "Audio profile management"),
                FeatureItem("Root / Shizuku / ADB Setup Methods", "Privilege configuration"),
            )),
        )
    ),
    // ── 17. SHIZU CALL RECORDER ─────────────────────────────────────────────
    SourceApp(
        name = "ShizuCallRecorder", github = "github.com/CHNwangjie/ShizuCallRecorder",
        icon = Icons.Outlined.PhoneCallback, color = Color(0xFF059669),
        description = "Rootless call recording using Shizuku for system-level audio capture.",
        categories = listOf(
            FeatureCategory("Recording Controls", listOf(
                FeatureItem("Auto-Record Incoming Calls", "All incoming calls recorded automatically"),
                FeatureItem("Auto-Record Outgoing Calls", "All outgoing calls recorded automatically"),
                FeatureItem("Both / Incoming Only / Outgoing Only Modes", "Direction filter"),
                FeatureItem("Manual Record Toggle from Notification", "Start/stop during call"),
            )),
            FeatureCategory("Storage & Filename Templates", listOf(
                FeatureItem("Audio Format Selection", "Quality and codec configuration"),
                FeatureItem("Configurable Storage Path (/sdcard/Recordings/ACC)", "Custom save location"),
                FeatureItem("Filename: {date}_{number}", "Date + phone number template"),
                FeatureItem("Filename: {date}_{direction}", "Date + call direction template"),
                FeatureItem("Custom Template ({contact_name}, {date}, {direction})", "User-defined naming"),
                FeatureItem("Auto-Delete: Never / 7 / 30 / 90 Days", "Retention policies"),
            )),
            FeatureCategory("Recording Manager", listOf(
                FeatureItem("Recording List with Metadata", "Duration, contact, direction, date"),
                FeatureItem("Search Recordings (by contact/date)", "Find past recordings"),
                FeatureItem("Built-in Audio Player", "Play recordings in-app"),
                FeatureItem("Delete / Share Recording", "Manage individual recordings"),
            )),
            FeatureCategory("Privacy & Legal", listOf(
                FeatureItem("Contact Filter (Per-Contact Toggle)", "Enable/disable per contact"),
                FeatureItem("Excluded Contacts List", "Never record specific contacts"),
                FeatureItem("Persistent Recording Notification", "Active while call is recorded"),
                FeatureItem("Legal Disclaimer Screen", "Mandatory notice about recording laws"),
                FeatureItem("Legal Reminder Toggle", "Show/hide legal notice on launch"),
            )),
        )
    ),
)
