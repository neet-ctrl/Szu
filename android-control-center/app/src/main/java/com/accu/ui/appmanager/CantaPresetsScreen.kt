package com.accu.ui.appmanager

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class DebloatPreset(
    val id: String,
    val name: String,
    val description: String,
    val packages: List<String>,
    val isDefault: Boolean = false,
    val safetyLevel: PresetSafety = PresetSafety.SAFE
)

enum class PresetSafety(val label: String, val color: @Composable () -> androidx.compose.ui.graphics.Color) {
    SAFE("Safe", { MaterialTheme.colorScheme.tertiary }),
    CAUTION("Caution", { MaterialTheme.colorScheme.primary }),
    ADVANCED("Advanced", { MaterialTheme.colorScheme.error })
}

val DEFAULT_PRESETS = listOf(
    DebloatPreset(
        id = "google_bloat",
        name = "Google Bloatware",
        description = "Remove rarely-used Google apps that run in background",
        isDefault = true,
        safetyLevel = PresetSafety.SAFE,
        packages = listOf(
            "com.google.android.apps.tachyon",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.marvin.talkback",
            "com.google.android.apps.magazines",
            "com.google.android.videos",
            "com.google.android.music",
            "com.google.android.apps.nbu.files",
        )
    ),
    DebloatPreset(
        id = "samsung_bloat",
        name = "Samsung Bloatware",
        description = "Samsung-specific pre-installed apps",
        isDefault = true,
        safetyLevel = PresetSafety.CAUTION,
        packages = listOf(
            "com.samsung.android.app.tips",
            "com.samsung.android.game.gamehome",
            "com.samsung.android.bixby.agent",
            "com.samsung.android.app.social",
            "com.sec.android.app.samsungapps",
        )
    ),
    DebloatPreset(
        id = "carrier_bloat",
        name = "Carrier Bloatware",
        description = "Carrier-installed apps and services",
        isDefault = true,
        safetyLevel = PresetSafety.CAUTION,
        packages = listOf(
            "com.att.myWireless",
            "com.verizon.mips.services",
            "com.vzw.apnservice",
            "com.tmobile.pr.mytmobile",
        )
    ),
    DebloatPreset(
        id = "advertising",
        name = "Advertising & Trackers",
        description = "Remove advertising frameworks and trackers",
        isDefault = true,
        safetyLevel = PresetSafety.SAFE,
        packages = listOf(
            "com.facebook.appmanager",
            "com.facebook.services",
            "com.facebook.system",
        )
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CantaPresetsScreen(
    onBack: () -> Unit,
    onApplyPreset: (DebloatPreset) -> Unit,
) {
    val customPresets = remember { mutableStateListOf<DebloatPreset>() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showNoWarranty by remember { mutableStateOf(true) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    if (showNoWarranty) {
        AlertDialog(
            onDismissRequest = { showNoWarranty = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("No Warranty") },
            text = {
                Text(
                    "Removing system apps can break functionality. Always have a recovery plan.\n\n" +
                    "• Advanced presets may remove system-critical components\n" +
                    "• Removal is permanent unless you have a backup\n" +
                    "• Caution presets may affect stability\n\n" +
                    "Proceed at your own risk."
                )
            },
            confirmButton = { TextButton(onClick = { showNoWarranty = false }) { Text("I Understand") } },
            dismissButton = { TextButton(onClick = onBack) { Text("Go Back") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debloat Presets") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "Create Preset")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PresetSectionHeader("Default Presets", Icons.Default.Shield)
            }
            items(DEFAULT_PRESETS) { preset ->
                PresetCard(
                    preset = preset,
                    expanded = expandedId == preset.id,
                    onToggleExpand = { expandedId = if (expandedId == preset.id) null else preset.id },
                    onApply = { onApplyPreset(preset) },
                )
            }
            if (customPresets.isNotEmpty()) {
                item { PresetSectionHeader("Custom Presets", Icons.Default.Edit) }
                items(customPresets) { preset ->
                    PresetCard(
                        preset = preset,
                        expanded = expandedId == preset.id,
                        onToggleExpand = { expandedId = if (expandedId == preset.id) null else preset.id },
                        onApply = { onApplyPreset(preset) },
                        onDelete = { customPresets.remove(preset) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePresetDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { preset ->
                customPresets.add(preset)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PresetSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PresetCard(
    preset: DebloatPreset,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onApply: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.clickable(onClick = onToggleExpand).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Surface(
                            color = preset.safetyLevel.color(),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                preset.safetyLevel.label,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${preset.packages.size} packages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Packages to remove:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    preset.packages.forEach { pkg ->
                        Text(
                            "• $pkg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onDelete != null) {
                            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
                        }
                        Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text("Apply Preset") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePresetDialog(
    onDismiss: () -> Unit,
    onCreate: (DebloatPreset) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var packagesText by remember { mutableStateOf("") }
    var safety by remember { mutableStateOf(PresetSafety.SAFE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = packagesText,
                    onValueChange = { packagesText = it },
                    label = { Text("Package names (one per line)") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 6,
                )
                Text("Safety Level:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetSafety.entries.forEach { level ->
                        FilterChip(
                            selected = safety == level,
                            onClick = { safety = level },
                            label = { Text(level.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val packages = packagesText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    onCreate(
                        DebloatPreset(
                            id = "custom_${System.currentTimeMillis()}",
                            name = name,
                            description = description,
                            packages = packages,
                            safetyLevel = safety,
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding(),
    start = this.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = this.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)
