package com.accu.ui.widgets

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class SmartSpacerTarget(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isDefault: Boolean = false,
    val isPlugin: Boolean = false,
    val enabled: Boolean = true,
    val requirements: List<String> = emptyList(),
)

data class SmartSpacerComplication(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean = true,
)

val DEFAULT_TARGETS = listOf(
    SmartSpacerTarget("weather", "Weather", "Current conditions + today's forecast", Icons.Default.WbSunny, isDefault = true),
    SmartSpacerTarget("calendar", "Next Calendar Event", "Upcoming calendar events with countdown", Icons.Default.CalendarToday, isDefault = true),
    SmartSpacerTarget("date", "Date", "Current date in customizable format", Icons.Default.Today, isDefault = true),
    SmartSpacerTarget("alarm", "Next Alarm", "Shows next scheduled alarm time", Icons.Default.Alarm, isDefault = true),
    SmartSpacerTarget("media", "Media", "Now playing information", Icons.Default.MusicNote, isDefault = true),
    SmartSpacerTarget("battery", "Battery", "Battery level and charging status", Icons.Default.BatteryFull, isDefault = true),
    SmartSpacerTarget("step_count", "Step Counter", "Daily step count from sensors", Icons.Default.DirectionsWalk),
    SmartSpacerTarget("gmail", "Gmail", "Unread email count", Icons.Default.Email, requirements = listOf("Gmail installed")),
    SmartSpacerTarget("notifications", "Notification Count", "Unread notification summary", Icons.Default.Notifications),
    SmartSpacerTarget("at_a_glance", "At-a-Glance Override", "Replace Google's At-a-Glance widget", Icons.Default.Widgets, requirements = listOf("Pixel Device or custom launcher")),
    SmartSpacerTarget("location", "Location", "Current location or nearby info", Icons.Default.LocationOn, requirements = listOf("Location permission")),
    SmartSpacerTarget("calls", "Missed Calls", "Missed call notification", Icons.Default.Phone, requirements = listOf("READ_CALL_LOG")),
)

val DEFAULT_COMPLICATIONS = listOf(
    SmartSpacerComplication("time", "Time", "Displays current time", Icons.Default.AccessTime),
    SmartSpacerComplication("battery_compl", "Battery Level", "Battery percentage indicator", Icons.Default.Battery5Bar),
    SmartSpacerComplication("wifi_compl", "Wi-Fi Network", "Current Wi-Fi connection", Icons.Default.Wifi),
    SmartSpacerComplication("steps_compl", "Steps Today", "Daily step count ring", Icons.Default.DirectionsRun),
    SmartSpacerComplication("date_compl", "Date", "Current date in short format", Icons.Default.DateRange),
    SmartSpacerComplication("weather_compl", "Weather", "Temperature + condition icon", Icons.Default.WbCloudy),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSpacerTargetsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Targets", "Complications", "Requirements", "Plugins")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smartspacer") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Backup, "Backup") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, tab -> Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(tab) }) }
            }
            when (selectedTab) {
                0 -> TargetsTab()
                1 -> ComplicationsTab()
                2 -> RequirementsTab()
                3 -> PluginsTab()
            }
        }
    }
}

@Composable
private fun TargetsTab() {
    val targets = remember { mutableStateListOf(*DEFAULT_TARGETS.toTypedArray()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Widgets, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${targets.count { it.enabled }} active targets", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Long-press to reorder. Drag to change priority.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(targets, key = { it.id }) { target ->
            val idx = targets.indexOfFirst { it.id == target.id }
            TargetCard(target = target, onToggle = { if (idx != -1) targets[idx] = target.copy(enabled = !target.enabled) })
        }
    }
}

@Composable
private fun TargetCard(target: SmartSpacerTarget, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (target.enabled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Icon(target.icon, null, tint = if (target.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(target.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (target.isDefault) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) { Text("Built-in", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) }
                    if (target.isPlugin) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) { Text("Plugin", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) }
                }
                Text(target.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (target.requirements.isNotEmpty()) {
                    Text("Requires: ${target.requirements.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Switch(checked = target.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun ComplicationsTab() {
    val complications = remember { mutableStateListOf(*DEFAULT_COMPLICATIONS.toTypedArray()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(complications, key = { it.id }) { comp ->
            val idx = complications.indexOfFirst { it.id == comp.id }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(comp.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(comp.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(comp.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = comp.enabled, onCheckedChange = { if (idx != -1) complications[idx] = comp.copy(enabled = !comp.enabled) })
                }
            }
        }
    }
}

@Composable
private fun RequirementsTab() {
    val requirements = listOf(
        "Location Permission" to "Required for weather and location targets",
        "Notification Access" to "Required for notification count complication",
        "Calendar Permission" to "Required for calendar event target",
        "Device Admin" to "Required for some advanced trigger conditions",
        "Accessibility Service" to "Required for gesture-based conditions",
        "Battery Optimization Exempt" to "Ensures Smartspacer runs in background",
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Permission Requirements", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(requirements) { (req, desc) ->
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(req, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = {}) { Text("Grant") }
                }
            }
        }
    }
}

@Composable
private fun PluginsTab() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Plugin Architecture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Smartspacer supports third-party plugins that add new targets, complications, and requirements. Install plugin APKs and they appear here automatically.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Extension, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Browse Plugin Repository")
                    }
                }
            }
        }
        item { Text("Installed Plugins", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Extension, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("No plugins installed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        Text("Install plugin APKs to extend Smartspacer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
