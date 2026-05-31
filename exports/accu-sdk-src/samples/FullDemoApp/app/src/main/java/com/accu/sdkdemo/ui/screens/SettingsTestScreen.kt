package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdkdemo.data.SettingEntry
import com.accu.sdkdemo.ui.components.SectionHeader
import com.accu.sdkdemo.viewmodel.MainViewModel

private val SETTINGS = listOf(
    // Secure — read-only in test app (safe to read, careful with writes)
    SettingEntry("Secure", "bluetooth_on",                "Bluetooth radio state (0=off, 1=on)",          null, true),
    SettingEntry("Secure", "wifi_on",                     "Wi-Fi radio state",                             null, true),
    SettingEntry("Secure", "location_mode",               "Location mode (0–3)",                           null, true),
    SettingEntry("Secure", "default_input_method",        "Current IME package/class",                     null, true),
    SettingEntry("Secure", "user_setup_complete",         "First-run setup done flag",                     null, true),
    SettingEntry("Secure", "install_non_market_apps",     "Allow unknown sources (0/1)",                   "1",  false),
    SettingEntry("Secure", "android_id",                  "Device Android ID (hex string)",                null, true),

    // Global
    SettingEntry("Global", "adb_enabled",                 "ADB debugging enabled (0/1)",                   null, true),
    SettingEntry("Global", "development_settings_enabled","Developer Options visible (0/1)",               null, true),
    SettingEntry("Global", "stay_on_while_plugged_in",    "Stay awake when plugged in (0/1/2/3)",          null, true),
    SettingEntry("Global", "airplane_mode_on",            "Airplane mode state (0/1)",                     null, true),
    SettingEntry("Global", "package_verifier_enable",     "Play Protect scan (1=enabled)",                 "1",  false),
    SettingEntry("Global", "device_name",                 "Bluetooth/Wi-Fi device display name",           null, true),

    // System
    SettingEntry("System", "screen_brightness",           "Screen brightness level (0–255)",               "128",false),
    SettingEntry("System", "screen_brightness_mode",      "Brightness mode (0=manual, 1=auto)",            "1",  false),
    SettingEntry("System", "screen_off_timeout",          "Screen-off timeout in ms",                      null, true),
    SettingEntry("System", "vibrate_when_ringing",        "Vibrate on call (0/1)",                         null, true),
    SettingEntry("System", "sound_effects_enabled",       "Touch sound effects (0/1)",                     null, true),
    SettingEntry("System", "haptic_feedback_enabled",     "Haptic feedback (0/1)",                         null, true),
)

@Composable
fun SettingsTestScreen(vm: MainViewModel) {
    val results by vm.settingsResult.collectAsState()
    var selectedCategory by remember { mutableStateOf("Secure") }
    val categories = listOf("Secure", "Global", "System")

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Settings Test (requires SETTINGS scope)")
                TabRow(selectedTabIndex = categories.indexOf(selectedCategory)) {
                    categories.forEach { cat ->
                        Tab(selected = selectedCategory == cat, onClick = { selectedCategory = cat }) {
                            Text(cat, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        val filtered = SETTINGS.filter { it.category == selectedCategory }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { "$selectedCategory:${it.key}" }) { entry ->
                SettingCard(entry, results, vm)
            }
        }
    }
}

@Composable
private fun SettingCard(entry: SettingEntry, results: Map<String, String>, vm: MainViewModel) {
    val readKey  = "${entry.category}:${entry.key}"
    val writeKey = "$readKey:write"
    val readResult  = results[readKey]
    val writeResult = results[writeKey]

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(entry.key, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.readOnly) {
                    AssistChip(onClick = {}, label = { Text("read-only", fontSize = 10.sp) })
                }
            }

            if (readResult != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Value:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Text(readResult, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (writeResult != null) {
                Surface(color = if (writeResult == "SUCCESS") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text("Write: $writeResult", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.readSetting(entry.category, entry.key) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Download, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Read", fontSize = 12.sp)
                }
                if (!entry.readOnly && entry.safeWriteValue != null) {
                    Button(onClick = { vm.writeSetting(entry.category, entry.key, entry.safeWriteValue) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.Upload, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Write = ${entry.safeWriteValue}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
