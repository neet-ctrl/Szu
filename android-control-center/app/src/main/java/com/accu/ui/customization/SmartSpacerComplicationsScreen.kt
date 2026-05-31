package com.accu.ui.customization

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

data class Complication(
    val id: String,
    val name: String,
    val provider: String,
    val type: String, // "native", "plugin", "custom"
    val description: String,
    var isEnabled: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    var order: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSpacerComplicationsScreen(onBack: () -> Unit = {}) {
    var complications by remember {
        mutableStateOf(listOf(
            Complication("1", "Date", "System", "native", "Shows current date (day of week, month, day)", true, Icons.Default.CalendarToday, 0),
            Complication("2", "Time", "System", "native", "Shows current time in configured format", false, Icons.Default.Schedule, 1),
            Complication("3", "Alarm", "System", "native", "Shows next alarm time", true, Icons.Default.Alarm, 2),
            Complication("4", "Weather", "Google", "native", "Temperature and conditions from Google Weather", true, Icons.Default.WbSunny, 3),
            Complication("5", "Battery", "System", "native", "Device battery percentage", false, Icons.Default.Battery5Bar, 4),
            Complication("6", "Digital Wellbeing", "Digital Wellbeing", "native", "Screen time today", false, Icons.Default.SelfImprovement, 5),
            Complication("7", "Missed Calls", "Phone", "native", "Number of missed calls", false, Icons.Default.PhoneMissed, 6),
            Complication("8", "Unread SMS", "Messages", "native", "Unread SMS count", false, Icons.Default.Sms, 7),
            Complication("9", "Unread Gmail", "Gmail", "plugin", "Unread email count from Gmail", false, Icons.Default.Email, 8),
            Complication("10", "Spotify Now Playing", "Spotify", "plugin", "Current track playing in Spotify", false, Icons.Default.MusicNote, 9),
            Complication("11", "Steps Counter", "Health", "plugin", "Daily step count", false, Icons.Default.DirectionsWalk, 10),
            Complication("12", "Custom Text", "User", "custom", "Display any custom text or variable", false, Icons.Default.TextFields, 11),
        ))
    }
    var showAddSheet by remember { mutableStateOf(false) }
    var editingComplication by remember { mutableStateOf<Complication?>(null) }

    val enabledCount = complications.count { it.isEnabled }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Complications",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Info, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, "Add complication")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Preview card
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Smartspace Preview", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    // Simulated smartspace view
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        complications.filter { it.isEnabled }.take(4).forEach { c ->
                            ElevatedCard(Modifier.height(40.dp).weight(1f)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(c.icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("$enabledCount/${complications.size} complications enabled", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Tabs: Enabled | All
            var tabIndex by remember { mutableIntStateOf(0) }
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Active ($enabledCount)") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("All (${complications.size})") })
            }

            val displayList = if (tabIndex == 0) complications.filter { it.isEnabled } else complications

            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(displayList, key = { it.id }) { comp ->
                    ListItem(
                        headlineContent = { Text(comp.name) },
                        supportingContent = {
                            Column {
                                Text(comp.description, fontSize = 12.sp, maxLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SuggestionChip(onClick = {}, label = { Text(comp.provider, fontSize = 10.sp) }, modifier = Modifier.height(20.dp))
                                    SuggestionChip(onClick = {}, label = { Text(comp.type, fontSize = 10.sp) }, modifier = Modifier.height(20.dp))
                                }
                            }
                        },
                        leadingContent = { Icon(comp.icon, null, tint = if (comp.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (comp.isEnabled) IconButton(onClick = { editingComplication = comp }) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)) }
                                Switch(checked = comp.isEnabled, onCheckedChange = { en ->
                                    complications = complications.map { c -> if (c.id == comp.id) c.copy(isEnabled = en) else c }
                                })
                            }
                        },
                        modifier = Modifier.clickable { editingComplication = comp }
                    )
                    HorizontalDivider()
                }
            }
        }

        if (editingComplication != null) {
            val comp = editingComplication!!
            AlertDialog(
                onDismissRequest = { editingComplication = null },
                title = { Text("Configure: ${comp.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Provider: ${comp.provider}", fontSize = 13.sp)
                        Text("Type: ${comp.type}", fontSize = 13.sp)
                        HorizontalDivider()
                        Text("Options depend on the complication provider. Configure in the provider's app or via plugin settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (comp.type == "custom") {
                            OutlinedTextField("", {}, label = { Text("Custom text") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { editingComplication = null }) { Text("Done") } },
                dismissButton = {
                    TextButton(onClick = {
                        complications = complications.filter { it.id != comp.id }
                        editingComplication = null
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            )
        }
    }
}
