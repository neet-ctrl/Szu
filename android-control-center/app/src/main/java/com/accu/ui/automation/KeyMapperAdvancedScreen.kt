package com.accu.ui.automation

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class KeyMapping(
    val id: String,
    val name: String,
    val trigger: TriggerConfig,
    val actions: List<MappingAction>,
    val isEnabled: Boolean = true,
    val profile: String = "Default",
)

data class TriggerConfig(
    val type: TriggerType,
    val key: String? = null,
    val keyCode: Int = 0,
    val longPress: Boolean = false,
    val doubleTap: Boolean = false,
    val clickCount: Int = 1,
)

data class MappingAction(
    val type: ActionType,
    val value: String = "",
    val delayMs: Int = 0,
    val repeatCount: Int = 0,
)

enum class TriggerType(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    KEY_PRESS("Key/Button Press", Icons.Default.KeyboardArrowUp),
    VOLUME_UP("Volume Up", Icons.Default.VolumeUp),
    VOLUME_DOWN("Volume Down", Icons.Default.VolumeDown),
    POWER("Power Button", Icons.Default.PowerSettingsNew),
    HEADSET_BUTTON("Headset Button", Icons.Default.Headphones),
    FINGERPRINT("Fingerprint Gesture", Icons.Default.Fingerprint),
    FLOATING_BUTTON("Floating Button", Icons.Default.TouchApp),
    ASSISTANT("Assistant Button", Icons.Default.Assistant),
    CAMERA_BUTTON("Camera Button", Icons.Default.Camera),
    KEYBOARD_SHORTCUT("Keyboard Shortcut", Icons.Default.Keyboard),
}

enum class ActionType(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LAUNCH_APP("Launch App", Icons.Default.Apps),
    LAUNCH_SHORTCUT("Launch Shortcut", Icons.Default.Star),
    KEY_EVENT("Send Key Event", Icons.Default.Keyboard),
    TEXT_INPUT("Type Text", Icons.Default.TextFields),
    SHELL_COMMAND("Shell Command", Icons.Default.Terminal),
    VOLUME_UP("Volume Up", Icons.Default.VolumeUp),
    VOLUME_DOWN("Volume Down", Icons.Default.VolumeDown),
    BRIGHTNESS_UP("Brightness Up", Icons.Default.Brightness7),
    BRIGHTNESS_DOWN("Brightness Down", Icons.Default.Brightness4),
    TOGGLE_WIFI("Toggle Wi-Fi", Icons.Default.Wifi),
    TOGGLE_BLUETOOTH("Toggle Bluetooth", Icons.Default.Bluetooth),
    SCREENSHOT("Take Screenshot", Icons.Default.Screenshot),
    FLASHLIGHT("Flashlight Toggle", Icons.Default.FlashlightOn),
    HTTP_REQUEST("HTTP Request", Icons.Default.Http),
    NOTIFICATION("Show Notification", Icons.Default.Notifications),
    MEDIA_PLAY_PAUSE("Play/Pause Media", Icons.Default.PlayArrow),
    MEDIA_NEXT("Next Track", Icons.Default.SkipNext),
    MEDIA_PREVIOUS("Previous Track", Icons.Default.SkipPrevious),
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyMapperAdvancedScreen(onBack: () -> Unit) {
    val mappings = remember { mutableStateListOf<KeyMapping>() }
    var showCreateSheet by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf("Default") }
    val profiles = listOf("Default", "Gaming", "Work", "Driving", "Sleep")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Key Mapper") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showCreateSheet = true }) { Icon(Icons.Default.Add, "Add mapping") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Active Profile", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(profiles) { profile ->
                            FilterChip(
                                selected = selectedProfile == profile,
                                onClick = { selectedProfile = profile },
                                label = { Text(profile) },
                                leadingIcon = if (selectedProfile == profile) {{ Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }} else null,
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${mappings.count { it.isEnabled }} active mappings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                val ctx = LocalContext.current
                TextButton(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Key Mapper Backups"); putExtra(Intent.EXTRA_TEXT, "Key Mapper Backup — ${mappings.size} mappings exported") }, "Export Key Maps"))
                }) { Text("Import/Export") }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(mappings, key = { it.id }) { mapping ->
                    val idx = mappings.indexOfFirst { it.id == mapping.id }
                    KeyMappingCard(
                        mapping = mapping,
                        onToggle = { if (idx != -1) mappings[idx] = mapping.copy(isEnabled = !mapping.isEnabled) },
                        onDelete = { if (idx != -1) mappings.removeAt(idx) },
                    )
                }
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        onClick = { showCreateSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Add New Mapping", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateMappingSheet(onDismiss = { showCreateSheet = false }, onCreate = { mapping ->
            mappings.add(mapping)
            showCreateSheet = false
        })
    }
}

@Composable
private fun KeyMappingCard(mapping: KeyMapping, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mapping.isEnabled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(mapping.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(mapping.profile, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)) }
                Switch(checked = mapping.isEnabled, onCheckedChange = { onToggle() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TriggerChip(mapping.trigger)
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                mapping.actions.forEach { action -> ActionChip(action) }
            }
        }
    }
}

@Composable
private fun TriggerChip(trigger: TriggerConfig) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(trigger.type.icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                buildString {
                    append(trigger.type.label)
                    if (trigger.longPress) append(" (Long)")
                    if (trigger.doubleTap) append(" (×2)")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ActionChip(action: MappingAction) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(action.type.icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(action.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateMappingSheet(onDismiss: () -> Unit, onCreate: (KeyMapping) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedTrigger by remember { mutableStateOf(TriggerType.VOLUME_UP) }
    var selectedAction by remember { mutableStateOf(ActionType.SCREENSHOT) }
    var longPress by remember { mutableStateOf(false) }
    var doubleTap by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("New Key Mapping", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Mapping Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Trigger", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TriggerType.entries) { type ->
                    FilterChip(selected = selectedTrigger == type, onClick = { selectedTrigger = type }, label = { Text(type.label) }, leadingIcon = { Icon(type.icon, null, modifier = Modifier.size(14.dp)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = longPress, onClick = { longPress = !longPress; if (longPress) doubleTap = false }, label = { Text("Long Press") })
                FilterChip(selected = doubleTap, onClick = { doubleTap = !doubleTap; if (doubleTap) longPress = false }, label = { Text("Double Tap") })
            }
            Text("Action", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ActionType.entries) { type ->
                    FilterChip(selected = selectedAction == type, onClick = { selectedAction = type }, label = { Text(type.label) })
                }
            }
            Button(
                onClick = {
                    onCreate(KeyMapping(
                        id = System.currentTimeMillis().toString(),
                        name = name.ifBlank { "${selectedTrigger.label} → ${selectedAction.label}" },
                        trigger = TriggerConfig(selectedTrigger, longPress = longPress, doubleTap = doubleTap),
                        actions = listOf(MappingAction(selectedAction)),
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create Mapping") }
        }
    }
}
