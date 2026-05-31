package com.accu.ui.automation

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigKeyMapScreen(
    keyMapId: String? = null,
    onBack: () -> Unit = {},
    onNavigateToChooseAction: () -> Unit = {},
    onNavigateToChooseConstraint: () -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Triggers", "Actions", "Constraints", "Options")
    var isEnabled by remember { mutableStateOf(true) }

    // Triggers state
    val triggers = remember { mutableStateListOf("Volume Down (hold 500ms)") }
    val triggerMode = remember { mutableStateOf("Sequence") }

    // Actions state
    val actions = remember { mutableStateListOf("Toggle Flashlight") }

    // Constraints state
    val constraints = remember { mutableStateListOf<String>() }
    val constraintMode = remember { mutableStateOf("AND") }

    // Options state
    var vibrateDuration by remember { mutableStateOf("50") }
    var repeatDelay by remember { mutableStateOf("") }
    var repeatRate by remember { mutableStateOf("") }
    var holdDownBeforeRepeat by remember { mutableStateOf(false) }
    var vibrateOnTrigger by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }
    var longPressDoubleVibrate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = if (keyMapId == null) "New Key Map" else "Edit Key Map",
                onBack = onBack,
                actions = {
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onBack) { Text("Save", color = MaterialTheme.colorScheme.primary) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title, fontSize = 12.sp) })
                }
            }
            when (selectedTab) {
                0 -> TriggersTab(triggers, triggerMode)
                1 -> ActionsTab(actions, onNavigateToChooseAction)
                2 -> ConstraintsTab(constraints, constraintMode, onNavigateToChooseConstraint)
                3 -> OptionsTab(vibrateDuration, { vibrateDuration = it }, repeatDelay, { repeatDelay = it },
                    repeatRate, { repeatRate = it }, holdDownBeforeRepeat, { holdDownBeforeRepeat = it },
                    vibrateOnTrigger, { vibrateOnTrigger = it }, showToast, { showToast = it },
                    longPressDoubleVibrate, { longPressDoubleVibrate = it })
            }
        }
    }
}

@Composable
private fun TriggersTab(triggers: MutableList<String>, triggerMode: MutableState<String>) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Mode toggle
        Text("Trigger Mode", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sequence", "Parallel", "Undefined").forEach { mode ->
                FilterChip(
                    selected = triggerMode.value == mode,
                    onClick = { triggerMode.value = mode },
                    label = { Text(mode) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Keys / Buttons", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        triggers.forEachIndexed { i, t ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t, fontWeight = FontWeight.Medium)
                        Text("Click type: Short Press", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { triggers.removeAt(i) }) { Icon(Icons.Default.Delete, null) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { triggers.add("Volume Up (press)") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(4.dp))
            Text("Record Trigger Key")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { triggers.add("Floating Button") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.TouchApp, null)
            Spacer(Modifier.width(4.dp))
            Text("Add Floating Button Trigger")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { triggers.add("Assistant Gesture") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Assistant, null)
            Spacer(Modifier.width(4.dp))
            Text("Add Assistant Trigger")
        }

        // Trigger options
        Spacer(Modifier.height(12.dp))
        Text("Click Type", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Short Press", "Long Press", "Double Press").forEach { type ->
                SuggestionChip(onClick = {}, label = { Text(type, fontSize = 12.sp) })
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Long Press Duration", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("200ms", "500ms", "1000ms", "Custom").forEach { d ->
                SuggestionChip(onClick = {}, label = { Text(d, fontSize = 12.sp) })
            }
        }
    }
}

@Composable
private fun ActionsTab(actions: MutableList<String>, onChooseAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Actions", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        actions.forEachIndexed { i, action ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(action, Modifier.weight(1f))
                    IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Options", modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { actions.removeAt(i) }) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onChooseAction, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(4.dp))
            Text("Add Action")
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Action Options", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        var repeatActions by remember { mutableStateOf(false) }
        var stopOnRelease by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Repeat actions while held", Modifier.weight(1f))
            Switch(checked = repeatActions, onCheckedChange = { repeatActions = it })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Stop repeating when released", Modifier.weight(1f))
            Switch(checked = stopOnRelease, onCheckedChange = { stopOnRelease = it })
        }
    }
}

@Composable
private fun ConstraintsTab(constraints: MutableList<String>, constraintMode: MutableState<String>, onChoose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mode:", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            listOf("AND", "OR").forEach { m ->
                FilterChip(
                    selected = constraintMode.value == m,
                    onClick = { constraintMode.value = m },
                    label = { Text(m) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (constraints.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("No constraints — always runs", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            constraints.forEachIndexed { i, c ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Rule, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(c, Modifier.weight(1f))
                        IconButton(onClick = { constraints.removeAt(i) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(4.dp))
            Text("Add Constraint")
        }
    }
}

@Composable
private fun OptionsTab(
    vibrateDuration: String, onVibrate: (String) -> Unit,
    repeatDelay: String, onRepeatDelay: (String) -> Unit,
    repeatRate: String, onRepeatRate: (String) -> Unit,
    holdRepeat: Boolean, onHoldRepeat: (Boolean) -> Unit,
    vibrateOnTrigger: Boolean, onVibrateTrigger: (Boolean) -> Unit,
    showToast: Boolean, onToast: (Boolean) -> Unit,
    doubleVibrate: Boolean, onDoubleVibrate: (Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Text("Feedback", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Vibrate on trigger"); Text("Haptic feedback when activated", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = vibrateOnTrigger, onCheckedChange = onVibrateTrigger)
            }
            Spacer(Modifier.height(4.dp))
            if (vibrateOnTrigger) {
                OutlinedTextField(vibrateDuration, onVibrate, label = { Text("Vibrate duration (ms)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Double vibrate on long press"); Text("Extra haptic on long-press trigger", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = doubleVibrate, onCheckedChange = onDoubleVibrate)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Show toast on trigger"); Text("Brief on-screen notification", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = showToast, onCheckedChange = onToast)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Repeat", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Hold down to repeat"); Text("Keep triggering while key held", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = holdRepeat, onCheckedChange = onHoldRepeat)
            }
            if (holdRepeat) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(repeatDelay, onRepeatDelay, label = { Text("Repeat delay (ms)") }, placeholder = { Text("Default: 400ms") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(repeatRate, onRepeatRate, label = { Text("Repeat rate (ms)") }, placeholder = { Text("Default: 50ms") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Keyboard / IME", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Change keyboard automatically when this key map is triggered.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Keyboard, null); Spacer(Modifier.width(4.dp)); Text("Select IME to switch to") }
        }
    }
}
