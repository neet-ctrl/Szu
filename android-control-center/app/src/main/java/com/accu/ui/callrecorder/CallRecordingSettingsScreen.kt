package com.accu.ui.callrecorder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class RecordingDirection(val label: String, val description: String) {
    BOTH("Both Directions", "Record both incoming and outgoing calls"),
    INCOMING_ONLY("Incoming Only", "Only record calls you receive"),
    OUTGOING_ONLY("Outgoing Only", "Only record calls you make"),
}

enum class FilenameFormat(val label: String, val template: String, val example: String) {
    DATE_NUMBER("Date + Number", "{date}_{number}", "2024-03-15_+1234567890"),
    DATE_DIRECTION("Date + Direction", "{date}_{direction}", "2024-03-15_incoming"),
    NUMBER_ONLY("Number Only", "{number}", "+1234567890"),
    DATE_ONLY("Date Only", "{date}", "2024-03-15_14-32-01"),
    CUSTOM("Custom", "{custom}", "my_recording"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRecordingSettingsScreen(onBack: () -> Unit) {
    var direction by remember { mutableStateOf(RecordingDirection.BOTH) }
    var filenameFormat by remember { mutableStateOf(FilenameFormat.DATE_NUMBER) }
    var customTemplate by remember { mutableStateOf("{date}_{number}_{direction}") }
    var saveLocation by remember { mutableStateOf("/sdcard/Recordings/ACC") }
    var showDisclaimerOnStart by remember { mutableStateOf(true) }
    var notifyOnRecord by remember { mutableStateOf(true) }
    var autoDeleteAfterDays by remember { mutableStateOf(0) }
    var contactFilterEnabled by remember { mutableStateOf(false) }
    var excludedContacts by remember { mutableStateOf(listOf<String>()) }
    var showContactDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle("Recording Direction")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecordingDirection.entries.forEach { dir ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = if (direction == dir) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                            onClick = { direction = dir },
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = direction == dir, onClick = { direction = dir })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(dir.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(dir.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle("Filename Format")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilenameFormat.entries.forEach { fmt ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = if (filenameFormat == fmt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                            onClick = { filenameFormat = fmt },
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = filenameFormat == fmt, onClick = { filenameFormat = fmt })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(fmt.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Example: ${fmt.example}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (filenameFormat == FilenameFormat.CUSTOM) {
                        OutlinedTextField(
                            value = customTemplate,
                            onValueChange = { customTemplate = it },
                            label = { Text("Custom Template") },
                            supportingText = { Text("Variables: {date} {number} {direction} {contact_name}") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }

            item {
                SectionTitle("Storage")
                OutlinedTextField(
                    value = saveLocation,
                    onValueChange = { saveLocation = it },
                    label = { Text("Save Location") },
                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                SectionTitle("Behavior")
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Show Disclaimer on Start") },
                            supportingContent = { Text("Display legal reminder before first recording") },
                            trailingContent = { Switch(checked = showDisclaimerOnStart, onCheckedChange = { showDisclaimerOnStart = it }) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Recording Notification") },
                            supportingContent = { Text("Show notification while recording is in progress") },
                            trailingContent = { Switch(checked = notifyOnRecord, onCheckedChange = { notifyOnRecord = it }) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Contact Filter") },
                            supportingContent = { Text("Exclude specific contacts from recording") },
                            trailingContent = { Switch(checked = contactFilterEnabled, onCheckedChange = { contactFilterEnabled = it }) }
                        )
                        if (contactFilterEnabled) {
                            ListItem(
                                headlineContent = { Text("Excluded Contacts (${excludedContacts.size})") },
                                supportingContent = { Text("Tap to manage excluded contacts") },
                                leadingContent = { Icon(Icons.Default.PersonOff, null) },
                                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                                modifier = Modifier.clickable(onClick = { showContactDialog = true }),
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("Auto Delete")
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Auto-delete recordings after:", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Never", 7 to "7 days", 30 to "30 days", 90 to "90 days").forEach { (days, label) ->
                                FilterChip(
                                    selected = autoDeleteAfterDays == days,
                                    onClick = { autoDeleteAfterDays = days },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Gavel, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        Column {
                            Text("Legal Notice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Recording phone calls may be illegal in your jurisdiction without consent of all parties. Check your local laws before using call recording. The developers of this app are not responsible for any legal consequences of misuse.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

private fun Modifier.clickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.then(androidx.compose.foundation.Modifier.clickable(onClick = onClick))
)

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = calculateTopPadding() + other.calculateTopPadding(),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)
