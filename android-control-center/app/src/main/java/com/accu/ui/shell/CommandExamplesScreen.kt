package com.accu.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class CommandExample(
    val id: String,
    val title: String,
    val command: String,
    val description: String,
    val labels: List<String>,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandExamplesScreen(
    onBack: () -> Unit = {},
    onCommandSelected: (String) -> Unit = {},
) {
    var commands by remember {
        mutableStateOf(
            listOf(
                // Activity Manager
                CommandExample("1", "Force-stop app", "am force-stop com.example.app", "Force stops a running app by package name", listOf("am", "apps")),
                CommandExample("2", "Start activity", "am start -n com.example.app/.MainActivity", "Launch a specific Activity", listOf("am", "apps")),
                CommandExample("3", "Start service", "am startservice com.example.app/.MyService", "Start a background service", listOf("am", "services")),
                CommandExample("4", "Send broadcast", "am broadcast -a android.intent.action.SCREEN_ON", "Send a broadcast intent", listOf("am", "broadcast")),
                CommandExample("5", "Kill background apps", "am kill-all", "Kill all background processes", listOf("am", "apps")),
                // Package Manager
                CommandExample("6", "List installed packages", "pm list packages -3", "List user-installed packages", listOf("pm", "packages")),
                CommandExample("7", "List system packages", "pm list packages -s", "List system packages", listOf("pm", "packages")),
                CommandExample("8", "Uninstall for user 0", "pm uninstall -k --user 0 com.example.app", "Remove app from current user", listOf("pm", "packages")),
                CommandExample("9", "Clear app data", "pm clear com.example.app", "Clear app's data and cache", listOf("pm", "apps")),
                CommandExample("10", "Disable app", "pm disable-user --user 0 com.example.app", "Disable app for current user", listOf("pm", "packages")),
                CommandExample("11", "Enable app", "pm enable com.example.app", "Re-enable a disabled app", listOf("pm", "packages")),
                CommandExample("12", "Grant permission", "pm grant com.example.app android.permission.READ_CONTACTS", "Grant runtime permission", listOf("pm", "permissions")),
                CommandExample("13", "Revoke permission", "pm revoke com.example.app android.permission.CAMERA", "Revoke runtime permission", listOf("pm", "permissions")),
                CommandExample("14", "Suspend app", "pm suspend --user 0 com.example.app", "Suspend app (shows suspended overlay)", listOf("pm", "packages")),
                CommandExample("15", "Set standby bucket", "pm set-app-standby-bucket com.example.app active", "Override app standby bucket", listOf("pm", "battery")),
                // Settings
                CommandExample("16", "Get global setting", "settings get global wifi_on", "Read a global setting value", listOf("settings", "network")),
                CommandExample("17", "Set screen brightness", "settings put system screen_brightness 200", "Set screen brightness (0-255)", listOf("settings", "display")),
                CommandExample("18", "Enable dark mode", "settings put secure ui_night_mode 2", "Force dark mode on", listOf("settings", "display")),
                CommandExample("19", "Set animation scales to 0", "settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0", "Disable all animations for speed", listOf("settings", "display", "performance")),
                CommandExample("20", "Enable developer options", "settings put global development_settings_enabled 1", "Turn on developer options", listOf("settings", "developer")),
                // Battery
                CommandExample("21", "Battery stats", "dumpsys battery", "Current battery status and health", listOf("battery", "dumpsys")),
                CommandExample("22", "Simulate battery level", "dumpsys battery set level 50", "Set battery display to specific %", listOf("battery", "dumpsys")),
                CommandExample("23", "Reset battery stats", "dumpsys battery reset", "Reset battery simulation", listOf("battery", "dumpsys")),
                CommandExample("24", "Battery usage stats", "dumpsys batterystats", "Detailed battery consumption data", listOf("battery", "dumpsys")),
                // Network
                CommandExample("25", "Show IP addresses", "ip addr show", "List all network interfaces and IPs", listOf("network", "ip")),
                CommandExample("26", "Wi-Fi enable", "svc wifi enable", "Enable Wi-Fi", listOf("network", "wifi", "svc")),
                CommandExample("27", "Wi-Fi disable", "svc wifi disable", "Disable Wi-Fi", listOf("network", "wifi", "svc")),
                CommandExample("28", "Mobile data enable", "svc data enable", "Enable mobile data", listOf("network", "data", "svc")),
                CommandExample("29", "Bluetooth enable", "svc bluetooth enable", "Enable Bluetooth", listOf("network", "bluetooth", "svc")),
                CommandExample("30", "Ping test", "ping -c 4 8.8.8.8", "Send 4 ICMP pings to Google DNS", listOf("network", "ping")),
                // Display / WM
                CommandExample("31", "Get screen resolution", "wm size", "Get current display size", listOf("wm", "display")),
                CommandExample("32", "Set DPI", "wm density 400", "Override display density (DPI)", listOf("wm", "display")),
                CommandExample("33", "Reset DPI", "wm density reset", "Reset display density to default", listOf("wm", "display")),
                CommandExample("34", "Screenshot", "screencap -p /sdcard/screenshot.png", "Take a screenshot and save to storage", listOf("display", "screenshot")),
                CommandExample("35", "Screen record", "screenrecord --time-limit 30 /sdcard/recording.mp4", "Record screen for 30 seconds", listOf("display", "screenrecord")),
                // System
                CommandExample("36", "List running services", "service list", "Show all running Android services", listOf("system", "services")),
                CommandExample("37", "Memory info", "cat /proc/meminfo", "Show RAM usage statistics", listOf("system", "memory")),
                CommandExample("38", "CPU info", "cat /proc/cpuinfo", "Show CPU specifications", listOf("system", "cpu")),
                CommandExample("39", "Reboot device", "reboot", "Restart the Android device", listOf("system", "power")),
                CommandExample("40", "Reboot to recovery", "reboot recovery", "Boot into recovery mode", listOf("system", "power")),
                CommandExample("41", "Show process list", "ps -A", "List all running processes", listOf("system", "processes")),
                // Logcat
                CommandExample("42", "View recent logs", "logcat -d -t 100", "Last 100 log lines then exit", listOf("logcat", "debugging")),
                CommandExample("43", "Filter by tag", "logcat -s ActivityManager", "Show only ActivityManager logs", listOf("logcat", "debugging")),
                CommandExample("44", "Clear logcat", "logcat -c", "Clear the logcat buffer", listOf("logcat", "debugging")),
                // File System
                CommandExample("45", "List files", "ls -la /sdcard/", "List all files with permissions", listOf("files", "ls")),
                CommandExample("46", "Disk usage", "df -h", "Show storage usage for all partitions", listOf("files", "storage")),
                CommandExample("47", "Find large files", "find /sdcard -size +100M -type f 2>/dev/null", "Find files larger than 100MB", listOf("files", "storage")),
                CommandExample("48", "Copy file", "cp /sdcard/file.txt /sdcard/backup/file.txt", "Copy a file to another location", listOf("files", "cp")),
                CommandExample("49", "Delete directory", "rm -rf /sdcard/temp_folder/", "Delete folder and all contents", listOf("files", "rm")),
                // Input
                CommandExample("50", "Tap screen", "input tap 500 1200", "Simulate touch at coordinates x=500 y=1200", listOf("input", "automation")),
                CommandExample("51", "Swipe gesture", "input swipe 300 900 300 300 500", "Swipe up gesture in 500ms", listOf("input", "automation")),
                CommandExample("52", "Type text", "input text 'Hello World'", "Type text as if from keyboard", listOf("input", "automation")),
                CommandExample("53", "Press back button", "input keyevent 4", "Simulate back button press", listOf("input", "keyevent")),
                CommandExample("54", "Press home button", "input keyevent 3", "Simulate home button press", listOf("input", "keyevent")),
                CommandExample("55", "Long press power", "input keyevent --longpress 26", "Long-press power button", listOf("input", "keyevent")),
            )
        )
    }

    var search by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf("Default") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<CommandExample?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newCommand by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }

    val allLabels = listOf("all") + commands.flatMap { it.labels }.distinct().sorted()

    val filtered = commands.filter { cmd ->
        (selectedLabel == "all" || selectedLabel in cmd.labels) &&
        (search.isBlank() || cmd.title.contains(search, ignoreCase = true) || cmd.command.contains(search, ignoreCase = true) || cmd.description.contains(search, ignoreCase = true))
    }.let { list ->
        when (sortMode) {
            "A–Z" -> list.sortedBy { it.title }
            "Custom first" -> list.sortedWith(compareByDescending { it.isCustom })
            "Favorites" -> list.sortedWith(compareByDescending { it.isFavorite })
            else -> list
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Command") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newTitle, { newTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(newCommand, { newCommand = it }, label = { Text("Command") }, modifier = Modifier.fillMaxWidth().height(120.dp), fontFamily = FontFamily.Monospace)
                    OutlinedTextField(newDescription, { newDescription = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank() && newCommand.isNotBlank()) {
                        commands = commands + CommandExample("custom_${commands.size}", newTitle, newCommand, newDescription, listOf("custom"), isCustom = true)
                        newTitle = ""; newCommand = ""; newDescription = ""
                        showAddDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Command Library",
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, null) }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            listOf("Default", "A–Z", "Custom first", "Favorites").forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    leadingIcon = { if (sortMode == m) Icon(Icons.Default.Check, null) },
                                    onClick = { sortMode = m; showSortMenu = false }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                search, { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search ${commands.size} commands…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )

            // Label filter
            ScrollableTabRow(
                selectedTabIndex = allLabels.indexOf(selectedLabel).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp,
            ) {
                allLabels.forEach { label ->
                    Tab(
                        selected = selectedLabel == label,
                        onClick = { selectedLabel = label },
                        text = { Text(if (label == "all") "All (${commands.size})" else "$label (${commands.count { label in it.labels }})", fontSize = 11.sp) }
                    )
                }
            }

            Text("${filtered.size} commands", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(filtered, key = { it.id }) { cmd ->
                    ElevatedCard(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clickable { onCommandSelected(cmd.command); onBack() }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(cmd.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(cmd.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                if (cmd.isCustom) Icon(Icons.Default.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                if (cmd.isFavorite) Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = {
                                    commands = commands.map { c -> if (c.id == cmd.id) c.copy(isFavorite = !c.isFavorite) else c }
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(if (cmd.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null, Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(cmd.command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                cmd.labels.forEach { label ->
                                    SuggestionChip(onClick = { selectedLabel = label }, label = { Text(label, fontSize = 10.sp) }, modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
