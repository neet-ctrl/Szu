package com.accu.ui.appmanager

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ── Models ────────────────────────────────────────────────────────────────────

data class DebloatPreset(
    val id: String,
    val name: String,
    val description: String,
    val packages: List<String>,
    val isDefault: Boolean = false,
    val safetyLevel: PresetSafety = PresetSafety.SAFE,
    val createdDate: Long = System.currentTimeMillis(),
    val version: String = "1.0",
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
    DebloatPreset(
        id = "miui_bloat",
        name = "MIUI / Xiaomi Bloatware",
        description = "Xiaomi/MIUI analytics and unnecessary services",
        isDefault = true,
        safetyLevel = PresetSafety.CAUTION,
        packages = listOf(
            "com.miui.analytics",
            "com.miui.msa.global",
            "com.miui.global.packageinstaller",
            "com.miui.player",
            "com.miui.video",
        )
    ),
    DebloatPreset(
        id = "oneplus_bloat",
        name = "OnePlus / OxygenOS Bloatware",
        description = "OnePlus-specific pre-installed apps",
        isDefault = true,
        safetyLevel = PresetSafety.CAUTION,
        packages = listOf(
            "com.oneplus.launcher",
            "com.oneplus.weather",
            "com.oneplus.note",
            "com.heytap.cloud",
            "com.coloros.gamespace",
        )
    ),
    DebloatPreset(
        id = "privacy_trackers",
        name = "Privacy Essentials",
        description = "Known data-harvesting and tracking services",
        isDefault = true,
        safetyLevel = PresetSafety.SAFE,
        packages = listOf(
            "com.amazon.mShop.android.shopping",
            "com.ebay.mobile",
            "com.spotify.music.lite",
        )
    ),
)

// ── JSON helpers ──────────────────────────────────────────────────────────────

fun DebloatPreset.toJson(): String {
    val appsArray = JSONArray().apply { packages.forEach { pkg -> put(JSONObject().put("packageName", pkg)) } }
    return JSONObject()
        .put("name", name)
        .put("description", description)
        .put("createdDate", createdDate)
        .put("version", version)
        .put("safetyLevel", safetyLevel.name)
        .put("apps", appsArray)
        .toString(2)
}

fun debloatPresetFromJson(json: String): DebloatPreset? = try {
    val obj = JSONObject(json)
    val appsArr = obj.getJSONArray("apps")
    val pkgs = (0 until appsArr.length()).map { appsArr.getJSONObject(it).getString("packageName") }
    DebloatPreset(
        id = "imported_${System.currentTimeMillis()}",
        name = obj.getString("name"),
        description = obj.optString("description", ""),
        packages = pkgs,
        createdDate = obj.optLong("createdDate", System.currentTimeMillis()),
        version = obj.optString("version", "1.0"),
        safetyLevel = runCatching { PresetSafety.valueOf(obj.optString("safetyLevel", "SAFE")) }.getOrDefault(PresetSafety.SAFE),
    )
} catch (_: Exception) { null }

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CantaPresetsScreen(
    onBack: () -> Unit,
    onApplyPreset: (DebloatPreset) -> Unit,
) {
    val context = LocalContext.current
    val customPresets = remember { mutableStateListOf<DebloatPreset>() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showNoWarranty by remember { mutableStateOf(true) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var editingPreset by remember { mutableStateOf<DebloatPreset?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it); snackbarMessage = null }
    }

    // ── Export launcher (SAF — user picks save location) ──────────────────
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                snackbarMessage = "Preset saved to selected location"
            } catch (e: Exception) {
                snackbarMessage = "Export failed: ${e.message}"
            }
        }
    }

    // ── Import launcher ────────────────────────────────────────────────────
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@let
                    val preset = debloatPresetFromJson(json)
                    if (preset != null) {
                        customPresets.add(preset)
                        snackbarMessage = "Imported preset: ${preset.name} (${preset.packages.size} apps)"
                    } else {
                        snackbarMessage = "Failed to parse preset file"
                    }
                } catch (e: Exception) {
                    snackbarMessage = "Import error: ${e.message}"
                }
            }
        }
    }

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
                    // Import from JSON
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/json" }
                        importLauncher.launch(intent)
                    }) {
                        Icon(Icons.Default.FileDownload, "Import Preset")
                    }
                    // Create new
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "Create Preset")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── UAD info card ──────────────────────────────────────────────
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Universal Android Debloater", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Presets based on UAD community safety database. Import/export JSON compatible with UAD format.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            item { PresetSectionHeader("Default Presets (${DEFAULT_PRESETS.size})", Icons.Default.Shield) }

            items(DEFAULT_PRESETS, key = { "default_${it.id}" }) { preset ->
                PresetCard(
                    preset = preset,
                    expanded = expandedId == preset.id,
                    onToggleExpand = { expandedId = if (expandedId == preset.id) null else preset.id },
                    onApply = { onApplyPreset(preset) },
                    onExport = {
                        pendingExportJson = preset.toJson()
                        exportLauncher.launch("accu_preset_${preset.name.replace(" ", "_").lowercase()}.json")
                    },
                    dateFormat = dateFormat,
                )
            }

            if (customPresets.isNotEmpty()) {
                item { PresetSectionHeader("Custom Presets (${customPresets.size})", Icons.Default.Edit) }
                items(customPresets, key = { "custom_${it.id}" }) { preset ->
                    PresetCard(
                        preset = preset,
                        expanded = expandedId == preset.id,
                        onToggleExpand = { expandedId = if (expandedId == preset.id) null else preset.id },
                        onApply = { onApplyPreset(preset) },
                        onDelete = { customPresets.remove(preset) },
                        onEdit = { editingPreset = preset },
                        onExport = {
                            pendingExportJson = preset.toJson()
                            exportLauncher.launch("accu_preset_${preset.name.replace(" ", "_").lowercase()}.json")
                        },
                        dateFormat = dateFormat,
                    )
                }
            }

            // ── No custom presets placeholder ──────────────────────────────
            if (customPresets.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.PlaylistAdd, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("No custom presets yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tap + to create one, or import a JSON file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/json" }
                                    importLauncher.launch(intent)
                                }) {
                                    Icon(Icons.Default.FileDownload, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Import JSON")
                                }
                                Button(onClick = { showCreateDialog = true }) {
                                    Icon(Icons.Default.Add, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Create")
                                }
                            }
                        }
                    }
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
                snackbarMessage = "Created preset: ${preset.name}"
            }
        )
    }

    // ── Edit dialog ────────────────────────────────────────────────────────
    val editing = editingPreset
    if (editing != null) {
        EditPresetDialog(
            preset = editing,
            onDismiss = { editingPreset = null },
            onSave = { updated ->
                val idx = customPresets.indexOfFirst { it.id == editing.id }
                if (idx != -1) customPresets[idx] = updated
                editingPreset = null
                snackbarMessage = "Preset updated"
            },
        )
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

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

// ── Preset card ────────────────────────────────────────────────────────────────

@Composable
private fun PresetCard(
    preset: DebloatPreset,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onApply: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onExport: () -> Unit,
    dateFormat: SimpleDateFormat,
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
                        Surface(color = preset.safetyLevel.color(), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                preset.safetyLevel.label,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.surface,
                            )
                        }
                        if (!preset.isDefault) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text("Custom", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${preset.packages.size} packages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (!preset.isDefault) Text("v${preset.version} · ${dateFormat.format(Date(preset.createdDate))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                // More menu
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(showMenu, { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Export JSON") }, leadingIcon = { Icon(Icons.Default.IosShare, null) }, onClick = { onExport(); showMenu = false })
                        if (onEdit != null) DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { onEdit(); showMenu = false })
                        if (onDelete != null) DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false })
                    }
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
                    Text("Packages (${preset.packages.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    preset.packages.forEach { pkg ->
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(pkg, style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.IosShare, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Export")
                        }
                        Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text("Apply Preset") }
                    }
                }
            }
        }
    }
}

// ── Create preset dialog ────────────────────────────────────────────────────────

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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = packagesText, onValueChange = { packagesText = it },
                    label = { Text("Package names (one per line)") },
                    placeholder = { Text("com.example.app\ncom.another.package") },
                    modifier = Modifier.fillMaxWidth().height(140.dp), maxLines = 8,
                )
                Text("Safety Level:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetSafety.entries.forEach { level ->
                        FilterChip(selected = safety == level, onClick = { safety = level }, label = { Text(level.label) })
                    }
                }
                // Package count helper
                val pkgCount = packagesText.lines().count { it.trim().isNotBlank() }
                if (pkgCount > 0) Text("$pkgCount package(s) will be included", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val packages = packagesText.lines().map { it.trim() }.filter { it.isNotBlank() && it.contains('.') }
                    onCreate(DebloatPreset(id = "custom_${System.currentTimeMillis()}", name = name, description = description, packages = packages, safetyLevel = safety))
                },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Edit preset dialog ──────────────────────────────────────────────────────────

@Composable
private fun EditPresetDialog(
    preset: DebloatPreset,
    onDismiss: () -> Unit,
    onSave: (DebloatPreset) -> Unit,
) {
    var name by remember { mutableStateOf(preset.name) }
    var description by remember { mutableStateOf(preset.description) }
    var packagesText by remember { mutableStateOf(preset.packages.joinToString("\n")) }
    var safety by remember { mutableStateOf(preset.safetyLevel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = packagesText, onValueChange = { packagesText = it },
                    label = { Text("Package names (one per line)") },
                    modifier = Modifier.fillMaxWidth().height(140.dp), maxLines = 8,
                )
                Text("Safety Level:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetSafety.entries.forEach { level ->
                        FilterChip(selected = safety == level, onClick = { safety = level }, label = { Text(level.label) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val packages = packagesText.lines().map { it.trim() }.filter { it.isNotBlank() && it.contains('.') }
                    onSave(preset.copy(name = name, description = description, packages = packages, safetyLevel = safety))
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
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
