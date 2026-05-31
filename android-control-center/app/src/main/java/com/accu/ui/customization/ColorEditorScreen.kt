package com.accu.ui.customization

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.ui.components.ACCTopBar
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen

// ──────────────────────────────────────────────
//  ColorBlendr — Monet / ColorSpec editor
// ──────────────────────────────────────────────

enum class ColorBlendrTab { PALETTE, MONET_SLIDERS, STYLES, PER_APP, ADVANCED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorEditorScreen(
    onBack: () -> Unit,
    viewModel: CustomizationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Local Monet slider state (ColorBlendr)
    var accentSaturation by remember { mutableFloatStateOf(100f) }
    var bgSaturation     by remember { mutableFloatStateOf(30f) }
    var bgLightness      by remember { mutableFloatStateOf(0f) }

    // RGB seed picker
    var red   by remember { mutableFloatStateOf(((state.seedColor shr 16) and 0xFF) / 255f) }
    var green by remember { mutableFloatStateOf(((state.seedColor shr 8)  and 0xFF) / 255f) }
    var blue  by remember { mutableFloatStateOf((state.seedColor          and 0xFF) / 255f) }
    val seedPreview = Color(red, green, blue)

    // Advanced toggles
    var accurateShades    by remember { mutableStateOf(false) }
    var pitchBlack        by remember { mutableStateOf(false) }
    var tintText          by remember { mutableStateOf(false) }
    var overrideManually  by remember { mutableStateOf(false) }
    var updateOnScreenOff by remember { mutableStateOf(false) }
    var separateLightDark by remember { mutableStateOf(false) }
    var colorSpecVersion  by remember { mutableStateOf("2024") }

    var selectedTab by remember { mutableStateOf(ColorBlendrTab.PALETTE) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            Column {
                ACCTopBar(
                    title = "Color & Theme Editor",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { viewModel.exportTheme() }) { Icon(Icons.Default.IosShare, "Export") }
                        IconButton(onClick = { viewModel.importTheme() }) { Icon(Icons.Default.FileOpen, "Import") }
                    },
                )
                ScrollableTabRow(selectedTabIndex = ColorBlendrTab.entries.indexOf(selectedTab), edgePadding = 16.dp) {
                    ColorBlendrTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(when(tab) {
                                ColorBlendrTab.PALETTE       -> "Palette"
                                ColorBlendrTab.MONET_SLIDERS -> "Monet"
                                ColorBlendrTab.STYLES        -> "Styles"
                                ColorBlendrTab.PER_APP       -> "Per-App"
                                ColorBlendrTab.ADVANCED      -> "Advanced"
                            }, fontSize = 12.sp) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (selectedTab) {
            ColorBlendrTab.PALETTE       -> PaletteTab(state, red, green, blue, seedPreview, { red = it }, { green = it }, { blue = it }, viewModel, padding)
            ColorBlendrTab.MONET_SLIDERS -> MonetSlidersTab(accentSaturation, bgSaturation, bgLightness, { accentSaturation = it }, { bgSaturation = it }, { bgLightness = it }, padding)
            ColorBlendrTab.STYLES        -> StylesTab(state, viewModel, padding)
            ColorBlendrTab.PER_APP       -> PerAppTab(padding)
            ColorBlendrTab.ADVANCED      -> AdvancedTab(accurateShades, pitchBlack, tintText, overrideManually, updateOnScreenOff, separateLightDark, colorSpecVersion,
                { accurateShades = it }, { pitchBlack = it }, { tintText = it }, { overrideManually = it }, { updateOnScreenOff = it }, { separateLightDark = it }, { colorSpecVersion = it }, padding)
        }
    }
}

// ────────────────────── TAB 1: Palette ──────────────────────
@Composable
private fun PaletteTab(
    state: CustomizationUiState,
    red: Float, green: Float, blue: Float, seedPreview: Color,
    onRed: (Float) -> Unit, onGreen: (Float) -> Unit, onBlue: (Float) -> Unit,
    viewModel: CustomizationViewModel, padding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Live preview swatch
        item {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    Box(
                        Modifier.fillMaxWidth().height(100.dp).background(seedPreview),
                        contentAlignment = Alignment.Center,
                    ) {
                        val hex = "#${Integer.toHexString(android.graphics.Color.rgb((red*255).toInt(),(green*255).toInt(),(blue*255).toInt())).uppercase().padStart(6,'0')}"
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Seed Color", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(hex, color = Color.White.copy(0.8f), fontFamily = FontFamily.Monospace)
                        }
                    }
                    // Generated tonal palette
                    Row(Modifier.fillMaxWidth().height(32.dp)) {
                        listOf(0.95f,0.85f,0.7f,0.55f,0.4f,0.25f,0.1f).forEach { a ->
                            Box(Modifier.weight(1f).fillMaxHeight().background(seedPreview.copy(alpha = a)))
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(24.dp)) {
                        listOf(0.08f,0.12f,0.18f,0.25f,0.35f,0.5f,0.7f).forEach { a ->
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black.copy(alpha = a)))
                        }
                    }
                }
            }
        }

        // RGB sliders
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("RGB Seed Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    ColorChannelRow("R", red, Color(1f, 0.2f, 0.2f), onRed)
                    ColorChannelRow("G", green, Color(0.1f, 0.8f, 0.1f), onGreen)
                    ColorChannelRow("B", blue, Color(0.2f, 0.5f, 1f), onBlue)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.setSeedColor(android.graphics.Color.rgb((red*255).toInt(),(green*255).toInt(),(blue*255).toInt()))
                        }, Modifier.weight(1f)) { Text("Apply Seed Color") }
                        OutlinedButton(onClick = { viewModel.setSeedColor(0xFF4A56E2.toInt()) }, Modifier.weight(1f)) { Text("Reset") }
                    }
                }
            }
        }

        // Quick presets
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Quick Palette Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val presets = listOf(
                        0xFF4A56E2L to "Indigo",  0xFF00D4FFL to "Cyan",    0xFF00E676L to "Green",
                        0xFFFF6D00L to "Orange",  0xFFD500F9L to "Purple",  0xFFFF1744L to "Red",
                        0xFFFFD600L to "Yellow",  0xFFFF4081L to "Pink",    0xFF00BCD4L to "Teal",
                        0xFF1E88E5L to "Blue",    0xFF43A047L to "Forest",  0xFF795548L to "Brown",
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { (colorLong, name) ->
                            val c = Color(colorLong)
                            val sel = state.seedColor == colorLong.toInt()
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(c)
                                        .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                        .clickable { viewModel.setSeedColor(colorLong.toInt()) },
                                    contentAlignment = Alignment.Center,
                                ) { if (sel) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                                Text(name, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────── TAB 2: Monet Sliders ──────────────────────
@Composable
private fun MonetSlidersTab(
    accentSat: Float, bgSat: Float, bgLight: Float,
    onAccent: (Float) -> Unit, onBgSat: (Float) -> Unit, onBgLight: (Float) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Monet Color Engine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Fine-tune how the Material You color engine generates palettes from your seed color.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            MonetSliderCard(
                title = "Accent Saturation",
                description = "How vivid primary and accent colors appear",
                value = accentSat, range = 0f..200f,
                valueLabel = "${"%.0f".format(accentSat)}%",
                onChange = onAccent,
                trackColor = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            MonetSliderCard(
                title = "Background Saturation",
                description = "Color tint amount for surface/background colors",
                value = bgSat, range = 0f..100f,
                valueLabel = "${"%.0f".format(bgSat)}%",
                onChange = onBgSat,
                trackColor = MaterialTheme.colorScheme.secondary,
            )
        }
        item {
            MonetSliderCard(
                title = "Background Lightness",
                description = "Shift background colors lighter (+) or darker (−)",
                value = bgLight, range = -50f..50f,
                valueLabel = "${if (bgLight >= 0) "+${"%.0f".format(bgLight)}" else "${"%.0f".format(bgLight)}"}",
                onChange = onBgLight,
                trackColor = MaterialTheme.colorScheme.tertiary,
            )
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Live Color Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Primary" to MaterialTheme.colorScheme.primary, "Secondary" to MaterialTheme.colorScheme.secondary, "Tertiary" to MaterialTheme.colorScheme.tertiary, "Neutral" to MaterialTheme.colorScheme.surfaceVariant, "Error" to MaterialTheme.colorScheme.error).forEach { (name, color) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(color))
                                Text(name, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonetSliderCard(title: String, description: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit, trackColor: Color) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = trackColor.copy(0.15f)) {
                    Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = trackColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                }
            }
            Slider(
                value = value, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${"%.0f".format(range.start)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${"%.0f".format(range.endInclusive)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ────────────────────── TAB 3: Styles ──────────────────────
@Composable
private fun StylesTab(state: CustomizationUiState, viewModel: CustomizationViewModel, padding: PaddingValues) {
    val styles = listOf(
        "TONAL_SPOT"    to Pair("Tonal Spot",     "Google default — well-balanced palette"),
        "VIBRANT"       to Pair("Vibrant",         "High saturation, vivid bold colors"),
        "EXPRESSIVE"    to Pair("Expressive",      "Wide range of colors, very colorful"),
        "SPRITZ"        to Pair("Spritz",          "Very muted / desaturated — nearly grey"),
        "RAINBOW"       to Pair("Rainbow",         "Full visible spectrum of colors"),
        "FRUIT_SALAD"   to Pair("Fruit Salad",     "Tropical and mixed multi-hue"),
        "CONTENT"       to Pair("Content",         "Extracted accurately from wallpaper"),
        "MONOCHROMATIC" to Pair("Monochromatic",   "Pure single-color greyscale scheme"),
    )

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Material You / Monet Styles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("Select how the Monet engine generates colors from your wallpaper or seed color.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(styles) { (key, pair) ->
            val (name, desc) = pair
            val selected = state.monetStyle == key
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                onClick = { viewModel.setMonetStyle(key) },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = { viewModel.setMonetStyle(key) })
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ────────────────────── TAB 4: Per-App ──────────────────────
@Composable
private fun PerAppTab(padding: PaddingValues) {
    val context = LocalContext.current
    val apps = remember {
        try {
            context.packageManager.getInstalledPackages(0).mapNotNull { pkg ->
                val ai = pkg.applicationInfo ?: return@mapNotNull null
                Triple(pkg.packageName, context.packageManager.getApplicationLabel(ai).toString(), ai)
            }.sortedBy { it.second }
        } catch (_: Exception) { emptyList() }
    }
    var themeMap by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var search by remember { mutableStateOf("") }
    val filtered = apps.filter { it.second.contains(search, true) || it.first.contains(search, true) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search apps…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
        )
        Text(
            "Enable/disable custom theming per-app (ColorBlendr per-app theme)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.first }) { (pkg, name, _) ->
                ListItem(
                    headlineContent = { Text(name, maxLines = 1) },
                    supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    leadingContent = {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(try { context.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }).crossfade(true).build(),
                            contentDescription = null, modifier = Modifier.size(40.dp),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = themeMap[pkg] ?: false,
                            onCheckedChange = { themeMap = themeMap.toMutableMap().apply { this[pkg] = it } },
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

// ────────────────────── TAB 5: Advanced ──────────────────────
@Composable
private fun AdvancedTab(
    accurateShades: Boolean, pitchBlack: Boolean, tintText: Boolean, overrideManually: Boolean,
    updateOnScreenOff: Boolean, separateLightDark: Boolean, colorSpecVersion: String,
    onAccurateShades: (Boolean) -> Unit, onPitchBlack: (Boolean) -> Unit, onTintText: (Boolean) -> Unit,
    onOverrideManually: (Boolean) -> Unit, onUpdateOnScreenOff: (Boolean) -> Unit,
    onSeparateLightDark: (Boolean) -> Unit, onColorSpecVersion: (String) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Advanced ColorBlendr Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }

        val toggles = listOf(
            Triple("Accurate Shades",           "More precise color shades (Root only)",                    accurateShades   ) to onAccurateShades,
            Triple("Pitch Black Theme",          "Makes dark backgrounds true black for AMOLED (Root only)", pitchBlack       ) to onPitchBlack,
            Triple("Tint Text Color",            "Apply seed color tint to text (Root only)",                tintText         ) to onTintText,
            Triple("Override Colors Manually",   "Manually override secondary/tertiary colors",              overrideManually ) to onOverrideManually,
            Triple("Update on Screen Off",        "Re-apply colors when screen turns off",                    updateOnScreenOff) to onUpdateOnScreenOff,
            Triple("Separate Light/Dark Settings","Use different settings per theme mode",                   separateLightDark) to onSeparateLightDark,
        )

        items(toggles) { (info, callback) ->
            val (title, desc, value) = info
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(desc, style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { Switch(checked = value, onCheckedChange = callback) },
                    modifier = Modifier.clickable { callback(!value) },
                )
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("ColorSpec Version", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("Determines which Android color specification to target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("2023","2024","2025","2026").forEach { ver ->
                            FilterChip(
                                selected = colorSpecVersion == ver,
                                onClick = { onColorSpecVersion(ver) },
                                label = { Text(ver) },
                                leadingIcon = if (colorSpecVersion == ver) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup & Restore", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {}, Modifier.weight(1f)) {
                            Icon(Icons.Default.IosShare, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export .colorblendr")
                        }
                        OutlinedButton(onClick = {}, Modifier.weight(1f)) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Import Config")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChannelRow(name: String, value: Float, color: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(20.dp))
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
        Text("${(value * 255).toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
    }
}
