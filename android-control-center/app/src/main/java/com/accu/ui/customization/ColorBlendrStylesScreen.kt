package com.accu.ui.customization

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ── Enums & Models ─────────────────────────────────────────────────────────────

enum class MonetStyle(val label: String, val description: String) {
    TONAL_SPOT("Tonal Spot", "Default Material You — subtle, balanced tones around seed color"),
    VIBRANT("Vibrant", "High-energy, bold color palette with maximum vibrancy"),
    EXPRESSIVE("Expressive", "Broad, eclectic colors — more adventurous combinations"),
    SPRITZ("Spritz", "Desaturated, muted tones — almost neutral with a hint of color"),
    RAINBOW("Rainbow", "Multi-hue spread with wide variance across the palette"),
    FRUIT_SALAD("Fruit Salad", "Analogous colors — cheerful, approachable combinations"),
    CONTENT("Content", "Color derived from wallpaper content analysis"),
    FIDELITY("Fidelity", "True-to-wallpaper color reproduction with maximum accuracy"),
    MONOCHROME("Monochrome", "Grayscale palette with single accent color only"),
    CMF("CMF (Nothing)", "Nothing Phone CMF aesthetic — restrained, industrial neutrals"),
}

enum class ColorBlendrWorkMethod(val label: String, val description: String) {
    ROOT("Root", "Uses root access for highest reliability across all Android versions"),
    SHIZUKU("ACCU IPC", "Uses ACCU IPC — no root needed, requires ACCU service running"),
    WIRELESS_ADB("Wireless ADB", "Commands over wireless ADB — slower but doesn't need root"),
}

data class CustomColorStyle(
    val id: String,
    val name: String,
    val seedColor: Color,
    val monetStyle: MonetStyle,
    val chromaFactor: Float = 1.0f,
    val luminanceFactor: Float = 1.0f,
    val pitchBlack: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

// ── Style JSON helpers ─────────────────────────────────────────────────────────

fun CustomColorStyle.toJson(): String = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("seedColor", "#%08X".format(seedColor.toArgb()))
    .put("monetStyle", monetStyle.name)
    .put("chromaFactor", chromaFactor)
    .put("luminanceFactor", luminanceFactor)
    .put("pitchBlack", pitchBlack)
    .put("createdAt", createdAt)
    .toString(2)

fun List<CustomColorStyle>.toJsonArray(): String {
    val arr = JSONArray()
    forEach { arr.put(JSONObject(it.toJson())) }
    return JSONObject().put("version", 1).put("styles", arr).toString(2)
}

fun customColorStyleFromJson(obj: JSONObject): CustomColorStyle? = runCatching {
    val seedHex = obj.getString("seedColor").removePrefix("#")
    val seed = Color(seedHex.toLong(16) or 0xFF000000)
    CustomColorStyle(
        id = obj.optString("id", "imported_${System.currentTimeMillis()}"),
        name = obj.getString("name"),
        seedColor = seed,
        monetStyle = runCatching { MonetStyle.valueOf(obj.getString("monetStyle")) }.getOrDefault(MonetStyle.TONAL_SPOT),
        chromaFactor = obj.optDouble("chromaFactor", 1.0).toFloat(),
        luminanceFactor = obj.optDouble("luminanceFactor", 1.0).toFloat(),
        pitchBlack = obj.optBoolean("pitchBlack", false),
        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
    )
}.getOrNull()

// ── Tonal palette generation helper (simplified) ─────────────────────────────

fun generateTonalPalette(seedColor: Color, monetStyle: MonetStyle, pitchBlack: Boolean): List<Color> {
    val base = seedColor.toArgb()
    val r = (base shr 16) and 0xFF
    val g = (base shr 8) and 0xFF
    val b = base and 0xFF
    val factor = when (monetStyle) {
        MonetStyle.VIBRANT    -> 1.3f
        MonetStyle.SPRITZ     -> 0.3f
        MonetStyle.MONOCHROME -> 0.1f
        MonetStyle.RAINBOW    -> 1.5f
        MonetStyle.CMF        -> 0.2f
        else -> 1.0f
    }
    return (0..12).map { i ->
        val t = i / 12f
        val lightness = if (pitchBlack && i == 0) 0f else t
        val cr = (r * (1 - t) * factor + 255 * t).coerceIn(0f, 255f).toInt()
        val cg = (g * (1 - t) * factor + 255 * t).coerceIn(0f, 255f).toInt()
        val cb = (b * (1 - t) * factor + 255 * t).coerceIn(0f, 255f).toInt()
        Color(cr, cg, cb)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorBlendrStylesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val savedStyles = remember {
        mutableStateListOf(
            CustomColorStyle("1", "Ocean Blue", Color(0xFF0066CC), MonetStyle.TONAL_SPOT),
            CustomColorStyle("2", "Forest Green", Color(0xFF2E7D32), MonetStyle.VIBRANT),
            CustomColorStyle("3", "Sunset Rose", Color(0xFFE91E63), MonetStyle.EXPRESSIVE),
            CustomColorStyle("4", "Midnight", Color(0xFF1A1A2E), MonetStyle.MONOCHROME, pitchBlack = true),
        )
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingStyle by remember { mutableStateOf<CustomColorStyle?>(null) }
    var activeStyleId by remember { mutableStateOf<String?>(null) }
    var workMethod by remember { mutableStateOf(ColorBlendrWorkMethod.SHIZUKU) }
    var globalMonetStyle by remember { mutableStateOf(MonetStyle.TONAL_SPOT) }
    var globalChroma by remember { mutableStateOf(1.0f) }
    var globalLuminance by remember { mutableStateOf(1.0f) }
    var globalPitchBlack by remember { mutableStateOf(false) }
    var showPalettePreview by remember { mutableStateOf<CustomColorStyle?>(null) }
    var excludedPackages by remember { mutableStateOf(setOf<String>()) }
    var showExclusionsManager by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@let
                    val obj = JSONObject(json)
                    val stylesArr = obj.optJSONArray("styles") ?: JSONArray().also { it.put(JSONObject(json)) }
                    var imported = 0
                    for (i in 0 until stylesArr.length()) {
                        customColorStyleFromJson(stylesArr.getJSONObject(i))?.let { savedStyles.add(it); imported++ }
                    }
                    snackbar = "Imported $imported style(s)"
                } catch (e: Exception) {
                    snackbar = "Import failed: ${e.message}"
                }
            }
        }
    }

    // SAF export launcher — user picks where to save
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                snackbar = "Styles saved to selected location"
            } catch (e: Exception) {
                snackbar = "Export failed: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Styles") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/json" }
                        importLauncher.launch(intent)
                    }) { Icon(Icons.Default.FileDownload, "Import") }
                    IconButton(onClick = {
                        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        pendingExportJson = savedStyles.toList().toJsonArray()
                        exportLauncher.launch("accu_colorblendr_styles_$ts.json")
                    }) { Icon(Icons.Default.IosShare, "Export") }
                    IconButton(onClick = { showExclusionsManager = true }) { Icon(Icons.Default.Block, "Exclusions") }
                    IconButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "New Style") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Work method ────────────────────────────────────────────────
            item { WorkMethodCard(selected = workMethod, onSelect = { workMethod = it }) }

            // ── Global settings ────────────────────────────────────────────
            item {
                GlobalColorSettings(
                    monetStyle = globalMonetStyle,
                    chroma = globalChroma,
                    luminance = globalLuminance,
                    pitchBlack = globalPitchBlack,
                    onStyleChange = { globalMonetStyle = it },
                    onChromaChange = { globalChroma = it },
                    onLuminanceChange = { globalLuminance = it },
                    onPitchBlackChange = { globalPitchBlack = it },
                )
            }

            // ── Palette preview strip ──────────────────────────────────────
            if (showPalettePreview != null) {
                item { PalettePreviewCard(style = showPalettePreview!!, onDismiss = { showPalettePreview = null }) }
            }

            // ── Saved styles ───────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved Styles (${savedStyles.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (savedStyles.isNotEmpty()) {
                        TextButton(onClick = {
                            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            pendingExportJson = savedStyles.toList().toJsonArray()
                            exportLauncher.launch("accu_colorblendr_styles_$ts.json")
                        }) { Text("Export All") }
                    }
                }
            }

            if (savedStyles.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Palette, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))
                                Text("No saved styles. Tap + to create one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                items(savedStyles, key = { it.id }) { style ->
                    CustomStyleCard(
                        style = style,
                        isActive = activeStyleId == style.id,
                        onApply = {
                            activeStyleId = style.id
                            snackbar = "Applied style: ${style.name}"
                        },
                        onDelete = { savedStyles.remove(style); if (activeStyleId == style.id) activeStyleId = null },
                        onEdit = { editingStyle = style },
                        onPreviewPalette = { showPalettePreview = style },
                        onDuplicate = { savedStyles.add(style.copy(id = System.currentTimeMillis().toString(), name = "${style.name} Copy")) },
                    )
                }
            }

            // ── Per-package overrides ──────────────────────────────────────
            item { PerPackageColorsSection(excludedPackages = excludedPackages, onShowExclusions = { showExclusionsManager = true }) }
        }
    }

    if (showCreateDialog) {
        CreateEditStyleDialog(
            existing = null,
            onDismiss = { showCreateDialog = false },
            onSave = { style -> savedStyles.add(style); showCreateDialog = false; snackbar = "Created style: ${style.name}" },
        )
    }

    if (editingStyle != null) {
        CreateEditStyleDialog(
            existing = editingStyle,
            onDismiss = { editingStyle = null },
            onSave = { style ->
                val idx = savedStyles.indexOfFirst { it.id == style.id }
                if (idx != -1) savedStyles[idx] = style
                editingStyle = null
                snackbar = "Updated style: ${style.name}"
            },
        )
    }

    if (showExclusionsManager) {
        ExclusionsManagerDialog(
            excluded = excludedPackages,
            onDismiss = { showExclusionsManager = false },
            onAdd = { excludedPackages = excludedPackages + it },
            onRemove = { excludedPackages = excludedPackages - it },
        )
    }
}

// ── Work method card ────────────────────────────────────────────────────────────

@Composable
private fun WorkMethodCard(selected: ColorBlendrWorkMethod, onSelect: (ColorBlendrWorkMethod) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Work Method: ${selected.label}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(selected.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    ColorBlendrWorkMethod.entries.forEach { method ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(method); expanded = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == method, onClick = { onSelect(method); expanded = false })
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(method.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(method.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Global color settings ───────────────────────────────────────────────────────

@Composable
private fun GlobalColorSettings(
    monetStyle: MonetStyle,
    chroma: Float,
    luminance: Float,
    pitchBlack: Boolean,
    onStyleChange: (MonetStyle) -> Unit,
    onChromaChange: (Float) -> Unit,
    onLuminanceChange: (Float) -> Unit,
    onPitchBlackChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text("Global Color Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Monet style picker
                    Text("Monet Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MonetStyle.entries.forEach { style ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (monetStyle == style) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { onStyleChange(style) }
                                    .padding(10.dp)
                                    .width(80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Default.Palette, null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (monetStyle == style) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    style.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (monetStyle == style) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                    AnimatedContent(targetState = monetStyle) { s ->
                        Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider()

                    // Chroma slider
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InvertColors, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Color Chroma", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("${(chroma * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                        Text("Controls color saturation — lower = more neutral, higher = more vivid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = chroma, onValueChange = onChromaChange, valueRange = 0f..2f, steps = 19, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Muted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Vivid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider()

                    // Luminance slider
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Brightness6, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Color Luminance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("${(luminance * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                        Text("Controls brightness of generated tonal palette", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = luminance, onValueChange = onLuminanceChange, valueRange = 0.5f..1.5f, steps = 9, modifier = Modifier.fillMaxWidth())
                    }

                    HorizontalDivider()

                    // Pitch black toggle
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pitch Black (OLED)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Force pure #000000 backgrounds — maximum OLED power savings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = pitchBlack, onCheckedChange = onPitchBlackChange)
                    }
                }
            }
        }
    }
}

// ── Palette preview card ────────────────────────────────────────────────────────

@Composable
private fun PalettePreviewCard(style: CustomColorStyle, onDismiss: () -> Unit) {
    val palette = remember(style) { generateTonalPalette(style.seedColor, style.monetStyle, style.pitchBlack) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tonal Palette — ${style.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
            }
            Text("${style.monetStyle.label} · Chroma ${(style.chromaFactor * 100).toInt()}% · Luminance ${(style.luminanceFactor * 100).toInt()}%${if (style.pitchBlack) " · Pitch Black" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(
                Modifier.fillMaxWidth().height(60.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                palette.forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(when (i) {
                                0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                palette.lastIndex -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                else -> RoundedCornerShape(0.dp)
                            })
                            .background(color),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            "${i * 100}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
            Text("Tap a swatch to inspect the hex value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f))
        }
    }
}

// ── Style card ─────────────────────────────────────────────────────────────────

@Composable
private fun CustomStyleCard(
    style: CustomColorStyle,
    isActive: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onPreviewPalette: () -> Unit,
    onDuplicate: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Seed color circle
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(style.seedColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline.copy(0.3f), CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(style.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (isActive) {
                            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                                Text("Active", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        if (style.pitchBlack) {
                            Surface(color = Color.Black, shape = RoundedCornerShape(4.dp)) {
                                Text("OLED", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                    Text(style.monetStyle.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Chroma: ${(style.chromaFactor * 100).toInt()}%  Luminance: ${(style.luminanceFactor * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    // Mini palette strip
                    Row(Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        generateTonalPalette(style.seedColor, style.monetStyle, style.pitchBlack).take(7).forEach { color ->
                            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
                        }
                    }
                }
                // Actions menu
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(showMenu, { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Preview Palette") }, leadingIcon = { Icon(Icons.Default.Palette, null) }, onClick = { onPreviewPalette(); showMenu = false })
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { onEdit(); showMenu = false })
                        DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Default.CopyAll, null) }, onClick = { onDuplicate(); showMenu = false })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreviewPalette, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Palette, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Preview")
                }
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text(if (isActive) "✓ Applied" else "Apply Style")
                }
            }
        }
    }
}

// ── Per-package overrides section ──────────────────────────────────────────────

@Composable
private fun PerPackageColorsSection(excludedPackages: Set<String>, onShowExclusions: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Per-App Color Overrides", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (excludedPackages.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("${excludedPackages.size} excluded", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Apply different Material You color seeds to specific apps. Each app can have its own color scheme independent of the system theme.\n\nExcluded apps retain their original theme and are not affected by ACCU color changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onShowExclusions, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage Exclusions (${excludedPackages.size} apps)")
                    }
                }
            }
        }
    }
}

// ── Create/edit style dialog ────────────────────────────────────────────────────

@Composable
private fun CreateEditStyleDialog(
    existing: CustomColorStyle?,
    onDismiss: () -> Unit,
    onSave: (CustomColorStyle) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedStyle by remember { mutableStateOf(existing?.monetStyle ?: MonetStyle.TONAL_SPOT) }
    var chromaFactor by remember { mutableStateOf(existing?.chromaFactor ?: 1.0f) }
    var luminanceFactor by remember { mutableStateOf(existing?.luminanceFactor ?: 1.0f) }
    var pitchBlack by remember { mutableStateOf(existing?.pitchBlack ?: false) }
    var seedColorHex by remember { mutableStateOf(if (existing != null) "%06X".format(existing.seedColor.toArgb() and 0xFFFFFF) else "0066CC") }
    val sampleColors = listOf(
        Color(0xFF0066CC), Color(0xFF2E7D32), Color(0xFFE91E63), Color(0xFFFF6F00),
        Color(0xFF7B1FA2), Color(0xFF00838F), Color(0xFF1A1A2E), Color(0xFF8B4513),
        Color(0xFF006400), Color(0xFF8B0000), Color(0xFF4169E1), Color(0xFFFF1493),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create Style" else "Edit Style") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Style Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }

                item {
                    Text("Seed Color:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        sampleColors.forEach { color ->
                            val hex = "%06X".format(color.toArgb() and 0xFFFFFF)
                            Box(
                                modifier = Modifier
                                    .size(if (seedColorHex == hex) 40.dp else 32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(if (seedColorHex == hex) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { seedColorHex = hex },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (seedColorHex == hex) Icon(Icons.Default.Check, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = seedColorHex, onValueChange = { if (it.length <= 6) seedColorHex = it.uppercase() },
                        label = { Text("Hex (without #)") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                        leadingIcon = {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(runCatching { Color(("FF$seedColorHex").toLong(16)) }.getOrDefault(MaterialTheme.colorScheme.primary)))
                        }
                    )
                }

                item {
                    Text("Monet Style:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MonetStyle.entries) { style ->
                            FilterChip(selected = selectedStyle == style, onClick = { selectedStyle = style }, label = { Text(style.label) })
                        }
                    }
                    if (selectedStyle != null) Text(selectedStyle.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }

                item {
                    Text("Chroma: ${(chromaFactor * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Slider(value = chromaFactor, onValueChange = { chromaFactor = it }, valueRange = 0f..2f, steps = 19)
                }

                item {
                    Text("Luminance: ${(luminanceFactor * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Slider(value = luminanceFactor, onValueChange = { luminanceFactor = it }, valueRange = 0.5f..1.5f, steps = 9)
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pitch Black (OLED)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Force pure #000000 backgrounds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = pitchBlack, onCheckedChange = { pitchBlack = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val seedColor = runCatching { Color(("FF$seedColorHex").toLong(16)) }.getOrDefault(Color(0xFF0066CC))
                    onSave(CustomColorStyle(
                        id = existing?.id ?: System.currentTimeMillis().toString(),
                        name = name,
                        seedColor = seedColor,
                        monetStyle = selectedStyle,
                        chromaFactor = chromaFactor,
                        luminanceFactor = luminanceFactor,
                        pitchBlack = pitchBlack,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    ))
                },
                enabled = name.isNotBlank(),
            ) { Text(if (existing == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Exclusions manager dialog ───────────────────────────────────────────────────

@Composable
private fun ExclusionsManagerDialog(
    excluded: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var newPackage by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Color Exclusions")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Apps in this list will not have their theme changed by ACCU. Use this for banking, camera, or design apps where color accuracy matters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPackage, onValueChange = { newPackage = it },
                        label = { Text("Package name") }, modifier = Modifier.weight(1f),
                        singleLine = true, placeholder = { Text("com.example.app") },
                    )
                    IconButton(onClick = { if (newPackage.contains('.')) { onAdd(newPackage); newPackage = "" } }) {
                        Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (excluded.isEmpty()) {
                    Text("No excluded apps. All apps will be themed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(excluded.toList()) { pkg ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(pkg, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onRemove(pkg) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

private fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = calculateTopPadding() + other.calculateTopPadding(),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)
