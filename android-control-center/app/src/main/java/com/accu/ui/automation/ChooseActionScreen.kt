package com.accu.ui.automation

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class ActionCategory(val title: String, val icon: ImageVector, val actions: List<ActionEntry>)
data class ActionEntry(val title: String, val desc: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseActionScreen(onBack: () -> Unit = {}, onActionSelected: (String) -> Unit = {}) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategory by remember { mutableStateOf<String?>(null) }

    val categories = remember {
        listOf(
            ActionCategory("App", Icons.Default.Apps, listOf(
                ActionEntry("Open App", "Launch any installed app", Icons.Default.OpenInNew),
                ActionEntry("Open App Shortcut", "App-specific shortcut", Icons.Default.Shortcut),
                ActionEntry("Open Menu", "Long-press home for App Switcher", Icons.Default.Menu),
                ActionEntry("App Info", "Open app info in Settings", Icons.Default.Info),
            )),
            ActionCategory("Keyboard / Text", Icons.Default.Keyboard, listOf(
                ActionEntry("Text", "Type text", Icons.Default.TextFields),
                ActionEntry("Key Event", "Send key event", Icons.Default.Keyboard),
                ActionEntry("Move Cursor", "Move cursor left/right/up/down", Icons.Default.SwapHoriz),
                ActionEntry("Select Word", "Select word under cursor", Icons.Default.SelectAll),
                ActionEntry("Cut", "Cut selection", Icons.Default.ContentCut),
                ActionEntry("Copy", "Copy selection", Icons.Default.ContentCopy),
                ActionEntry("Paste", "Paste clipboard", Icons.Default.ContentPaste),
                ActionEntry("Select All", "Select all text", Icons.Default.SelectAll),
                ActionEntry("Show Keyboard", "Open soft keyboard", Icons.Default.Keyboard),
                ActionEntry("Switch IME", "Switch to specific keyboard", Icons.Default.Translate),
            )),
            ActionCategory("System", Icons.Default.PhoneAndroid, listOf(
                ActionEntry("Toggle Flashlight", "On/off/toggle flashlight", Icons.Default.FlashlightOn),
                ActionEntry("Screenshot", "Take a screenshot", Icons.Default.Screenshot),
                ActionEntry("Screen Record", "Start/stop screen recording", Icons.Default.FiberManualRecord),
                ActionEntry("Lock Screen", "Lock the device screen", Icons.Default.Lock),
                ActionEntry("Power Dialog", "Show power menu", Icons.Default.PowerSettingsNew),
                ActionEntry("Quick Settings", "Expand notification shade", Icons.Default.Notifications),
                ActionEntry("Notification Shade", "Pull down notification shade", Icons.Default.ExpandMore),
                ActionEntry("Dismiss All Notifications", "Clear all notifications", Icons.Default.NotificationsOff),
                ActionEntry("Go Home", "Navigate to Home", Icons.Default.Home),
                ActionEntry("Go Back", "Navigate back", Icons.Default.ArrowBack),
                ActionEntry("Open Recents", "Open recent apps", Icons.Default.ViewCarousel),
                ActionEntry("Toggle Split Screen", "Enter split-screen mode", Icons.Default.SplitscreenPortrait),
                ActionEntry("Do Not Disturb", "Toggle DND mode", Icons.Default.DoNotDisturb),
                ActionEntry("Rotate Screen", "Rotate display orientation", Icons.Default.ScreenRotation),
                ActionEntry("Expand / Collapse", "Expand accessibility focus", Icons.Default.Fullscreen),
            )),
            ActionCategory("Volume / Media", Icons.Default.VolumeUp, listOf(
                ActionEntry("Volume Up", "Increase volume", Icons.Default.VolumeUp),
                ActionEntry("Volume Down", "Decrease volume", Icons.Default.VolumeDown),
                ActionEntry("Volume Mute", "Toggle mute", Icons.Default.VolumeOff),
                ActionEntry("Play / Pause", "Toggle media playback", Icons.Default.PlayArrow),
                ActionEntry("Next Track", "Skip to next media item", Icons.Default.SkipNext),
                ActionEntry("Previous Track", "Skip to previous", Icons.Default.SkipPrevious),
                ActionEntry("Fast Forward", "Fast-forward media", Icons.Default.FastForward),
                ActionEntry("Rewind", "Rewind media", Icons.Default.FastRewind),
                ActionEntry("Set Volume", "Set volume to specific level", Icons.Default.Tune),
                ActionEntry("Set Media Volume", "Set media stream volume", Icons.Default.MusicNote),
            )),
            ActionCategory("Network / Connectivity", Icons.Default.Wifi, listOf(
                ActionEntry("Toggle Wi-Fi", "Enable/disable Wi-Fi", Icons.Default.Wifi),
                ActionEntry("Toggle Mobile Data", "Enable/disable mobile data", Icons.Default.SignalCellularAlt),
                ActionEntry("Toggle Bluetooth", "Enable/disable Bluetooth", Icons.Default.Bluetooth),
                ActionEntry("Toggle NFC", "Enable/disable NFC", Icons.Default.Nfc),
                ActionEntry("Toggle Airplane Mode", "Enable/disable flight mode", Icons.Default.AirplanemodeActive),
                ActionEntry("Toggle Hotspot", "Enable/disable hotspot", Icons.Default.WifiTethering),
                ActionEntry("Connect Wi-Fi Network", "Connect to saved network", Icons.Default.WifiFind),
            )),
            ActionCategory("UI / Display", Icons.Default.Palette, listOf(
                ActionEntry("Toggle Dark Mode", "Switch light/dark theme", Icons.Default.DarkMode),
                ActionEntry("Set Brightness", "Set screen brightness 0–255", Icons.Default.Brightness6),
                ActionEntry("Toggle Auto-Brightness", "Toggle adaptive brightness", Icons.Default.BrightnessAuto),
                ActionEntry("Show UI Element", "Interact with UI element", Icons.Default.TouchApp),
                ActionEntry("Tap Screen", "Tap at coordinates", Icons.Default.TouchApp),
                ActionEntry("Swipe Screen", "Swipe gesture at coordinates", Icons.Default.SwipeRight),
                ActionEntry("Pinch Screen", "Two-finger pinch/zoom", Icons.Default.PinchZoomIn),
            )),
            ActionCategory("Phone / Communication", Icons.Default.Phone, listOf(
                ActionEntry("Answer Call", "Accept incoming call", Icons.Default.Call),
                ActionEntry("End Call", "Decline/end call", Icons.Default.CallEnd),
                ActionEntry("Open Dialer", "Open phone dialer", Icons.Default.DialerSip),
            )),
            ActionCategory("Sound / Ringtone", Icons.Default.Notifications, listOf(
                ActionEntry("Play Sound", "Play audio file from storage", Icons.Default.MusicNote),
                ActionEntry("Toggle Vibrate", "Toggle vibration mode", Icons.Default.Vibration),
                ActionEntry("Toggle Silent", "Toggle silent mode", Icons.Default.VolumeOff),
                ActionEntry("Set Ringtone Volume", "Set ringtone stream level", Icons.Default.NotificationsActive),
            )),
            ActionCategory("Advanced / Shell", Icons.Default.Terminal, listOf(
                ActionEntry("Shell Command (ADB)", "Run ADB shell command via Shizuku", Icons.Default.Terminal),
                ActionEntry("HTTP Request", "Send HTTP GET/POST request", Icons.Default.Http),
                ActionEntry("Intent", "Send a custom Android intent", Icons.Default.Send),
                ActionEntry("Create Notification", "Show a custom notification", Icons.Default.AddAlert),
            )),
        )
    }

    val filtered = if (searchQuery.isBlank()) categories else categories.map { cat ->
        cat.copy(actions = cat.actions.filter { it.title.contains(searchQuery, ignoreCase = true) || it.desc.contains(searchQuery, ignoreCase = true) })
    }.filter { it.actions.isNotEmpty() }

    Scaffold(topBar = { ACCTopBar(title = "Choose Action", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search actions…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                filtered.forEach { cat ->
                    item(key = cat.title) {
                        ListItem(
                            headlineContent = { Text(cat.title, fontWeight = FontWeight.SemiBold) },
                            leadingContent = { Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(if (expandedCategory == cat.title) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
                            modifier = Modifier.clickable { expandedCategory = if (expandedCategory == cat.title) null else cat.title }
                        )
                        HorizontalDivider()
                    }
                    if (expandedCategory == cat.title || searchQuery.isNotBlank()) {
                        items(cat.actions, key = { "${cat.title}:${it.title}" }) { action ->
                            ListItem(
                                headlineContent = { Text(action.title) },
                                supportingContent = { Text(action.desc, fontSize = 12.sp) },
                                leadingContent = {
                                    Box(Modifier.padding(start = 16.dp)) { Icon(action.icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary) }
                                },
                                modifier = Modifier.clickable { onActionSelected(action.title); onBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
