package com.accu.ui.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class ConstraintEntry(val title: String, val desc: String, val icon: ImageVector)
data class ConstraintCat(val title: String, val icon: ImageVector, val items: List<ConstraintEntry>)

@Composable
fun ChooseConstraintScreen(onBack: () -> Unit = {}, onConstraintSelected: (String) -> Unit = {}) {
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }

    val categories = remember {
        listOf(
            ConstraintCat("App", Icons.Default.Apps, listOf(
                ConstraintEntry("App in foreground", "Only trigger when specified app is open", Icons.Default.Visibility),
                ConstraintEntry("App NOT in foreground", "Trigger when specified app is closed", Icons.Default.VisibilityOff),
                ConstraintEntry("App is playing media", "Only trigger when app is outputting audio", Icons.Default.MusicNote),
                ConstraintEntry("App is NOT playing media", "Opposite of above", Icons.Default.MusicOff),
            )),
            ConstraintCat("Screen State", Icons.Default.Brightness6, listOf(
                ConstraintEntry("Screen on", "Only when display is active", Icons.Default.LightMode),
                ConstraintEntry("Screen off", "Only when display is off/locked", Icons.Default.DarkMode),
                ConstraintEntry("Locked", "Device is locked", Icons.Default.Lock),
                ConstraintEntry("Unlocked", "Device is unlocked", Icons.Default.LockOpen),
                ConstraintEntry("Charging", "Device is plugged in", Icons.Default.BatteryChargingFull),
                ConstraintEntry("Not charging", "Device is on battery", Icons.Default.Battery4Bar),
            )),
            ConstraintCat("Bluetooth", Icons.Default.Bluetooth, listOf(
                ConstraintEntry("BT device connected", "Specific Bluetooth device is connected", Icons.Default.BluetoothConnected),
                ConstraintEntry("BT device NOT connected", "Specific Bluetooth device is disconnected", Icons.Default.BluetoothDisabled),
            )),
            ConstraintCat("Orientation", Icons.Default.ScreenRotation, listOf(
                ConstraintEntry("Portrait orientation", "Device is in portrait mode", Icons.Default.StayCurrentPortrait),
                ConstraintEntry("Landscape orientation", "Device is in landscape mode", Icons.Default.StayCurrentLandscape),
            )),
            ConstraintCat("Network", Icons.Default.Wifi, listOf(
                ConstraintEntry("Wi-Fi connected", "Wi-Fi is active and connected", Icons.Default.Wifi),
                ConstraintEntry("Wi-Fi NOT connected", "Wi-Fi is off or disconnected", Icons.Default.WifiOff),
                ConstraintEntry("Connected to network SSID", "Specific Wi-Fi network name", Icons.Default.WifiFind),
                ConstraintEntry("Mobile data enabled", "Mobile data is on", Icons.Default.SignalCellularAlt),
                ConstraintEntry("Mobile data disabled", "Mobile data is off", Icons.Default.MobileOff),
            )),
            ConstraintCat("Audio", Icons.Default.VolumeUp, listOf(
                ConstraintEntry("Ringer mode: normal", "Sound / vibrate is on", Icons.Default.VolumeUp),
                ConstraintEntry("Ringer mode: silent", "Phone is completely silent", Icons.Default.VolumeOff),
                ConstraintEntry("Ringer mode: vibrate", "Vibrate only, no sound", Icons.Default.Vibration),
                ConstraintEntry("Headset connected", "Audio output device is connected", Icons.Default.Headset),
                ConstraintEntry("Media playing", "Any app is outputting audio", Icons.Default.PlayCircle),
            )),
            ConstraintCat("Recent App", Icons.Default.History, listOf(
                ConstraintEntry("Specific recent app", "Target app was recently used", Icons.Default.History),
                ConstraintEntry("Not a specific recent app", "Target app was NOT recently used", Icons.Default.HistoryToggleOff),
            )),
            ConstraintCat("Other", Icons.Default.MoreHoriz, listOf(
                ConstraintEntry("Time range", "Only active between two times", Icons.Default.Schedule),
                ConstraintEntry("Do Not Disturb active", "DND mode is currently on", Icons.Default.DoNotDisturb),
                ConstraintEntry("Accessibility focus", "Specific element has focus", Icons.Default.Accessibility),
                ConstraintEntry("Notification active", "Specific notification is present", Icons.Default.Notifications),
                ConstraintEntry("Flashlight on", "Flashlight is currently on", Icons.Default.FlashlightOn),
            )),
        )
    }

    val filtered = if (search.isBlank()) categories else categories.map { cat ->
        cat.copy(items = cat.items.filter { it.title.contains(search, ignoreCase = true) || it.desc.contains(search, ignoreCase = true) })
    }.filter { it.items.isNotEmpty() }

    Scaffold(topBar = { ACCTopBar(title = "Choose Constraint", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search constraints…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                filtered.forEach { cat ->
                    item(key = cat.title) {
                        ListItem(
                            headlineContent = { Text(cat.title, fontWeight = FontWeight.SemiBold) },
                            leadingContent = { Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(if (expanded == cat.title) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
                            modifier = Modifier.clickable { expanded = if (expanded == cat.title) null else cat.title }
                        )
                        HorizontalDivider()
                    }
                    if (expanded == cat.title || search.isNotBlank()) {
                        items(cat.items, key = { "${cat.title}:${it.title}" }) { c ->
                            ListItem(
                                headlineContent = { Text(c.title) },
                                supportingContent = { Text(c.desc, fontSize = 12.sp) },
                                leadingContent = { Box(Modifier.padding(start = 16.dp)) { Icon(c.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary) } },
                                modifier = Modifier.clickable { onConstraintSelected(c.title); onBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
