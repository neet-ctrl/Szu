package com.accu.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

@Composable
fun KeyMapperSettingsScreen(onBack: () -> Unit = {}) {
    var autoShowKeyboard by remember { mutableStateOf(true) }
    var showCursorMode by remember { mutableStateOf(false) }
    var rootMode by remember { mutableStateOf(false) }
    var shizukuMode by remember { mutableStateOf(true) }
    var deviceId by remember { mutableStateOf("") }
    var longPressDefault by remember { mutableStateOf("500") }
    var doublePressTimeout by remember { mutableStateOf("300") }
    var sequenceTimeout by remember { mutableStateOf("1000") }
    var vibrateDuration by remember { mutableStateOf("50") }
    var showOverlay by remember { mutableStateOf(false) }
    var notificationInService by remember { mutableStateOf(true) }
    var darkTheme by remember { mutableStateOf(false) }

    Scaffold(topBar = { ACCTopBar(title = "Key Mapper Settings", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                SectionHeader("Accessibility / Permission Mode")
                PreferenceToggle("Use Shizuku (recommended)", "ADB commands without root", shizukuMode) { shizukuMode = it }
                PreferenceToggle("Root mode", "Use root shell for privileged actions", rootMode) { rootMode = it }
                HorizontalDivider()
            }
            item {
                SectionHeader("Keyboard / IME")
                PreferenceToggle("Auto show/hide keyboard", "Automatically switch IME when triggered", autoShowKeyboard) { autoShowKeyboard = it }
                PreferenceToggle("Cursor mode for Shizuku", "Use InputManager for cursor control", showCursorMode) { showCursorMode = it }
                HorizontalDivider()
            }
            item {
                SectionHeader("Default Timing Options")
                OutlinedTextField(longPressDefault, { longPressDefault = it }, label = { Text("Long press duration (ms)") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true)
                OutlinedTextField(doublePressTimeout, { doublePressTimeout = it }, label = { Text("Double press timeout (ms)") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true)
                OutlinedTextField(sequenceTimeout, { sequenceTimeout = it }, label = { Text("Sequence key timeout (ms)") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true)
                OutlinedTextField(vibrateDuration, { vibrateDuration = it }, label = { Text("Default vibrate duration (ms)") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true)
                HorizontalDivider()
            }
            item {
                SectionHeader("Notification / UI")
                PreferenceToggle("Show persistent notification", "Service always-on indicator in status bar", notificationInService) { notificationInService = it }
                PreferenceToggle("Floating overlay button", "Show floating button on screen", showOverlay) { showOverlay = it }
                HorizontalDivider()
            }
            item {
                SectionHeader("Backup & Restore")
                ListItem(
                    headlineContent = { Text("Export all key maps") },
                    supportingContent = { Text("Save key maps to a JSON file") },
                    leadingContent = { Icon(Icons.Default.Upload, null) },
                    modifier = Modifier,
                )
                ListItem(
                    headlineContent = { Text("Import key maps") },
                    supportingContent = { Text("Restore from previously exported file") },
                    leadingContent = { Icon(Icons.Default.Download, null) },
                    modifier = Modifier,
                )
                HorizontalDivider()
            }
            item {
                SectionHeader("Advanced")
                OutlinedTextField(deviceId, { deviceId = it }, label = { Text("Device ID (for multi-device)") }, placeholder = { Text("Optional – leave blank for any device") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true)
                ListItem(
                    headlineContent = { Text("Clear all key maps") },
                    supportingContent = { Text("Irreversible — deletes everything") },
                    leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                )
                ListItem(
                    headlineContent = { Text("View accessibility info") },
                    supportingContent = { Text("Open accessibility settings for Key Mapper") },
                    leadingContent = { Icon(Icons.Default.Accessibility, null) },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PreferenceToggle(title: String, sub: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub, fontSize = 12.sp) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChanged) }
    )
}
