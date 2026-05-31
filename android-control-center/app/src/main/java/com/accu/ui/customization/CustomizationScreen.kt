package com.accu.ui.customization

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    onNavigateToDarkMode: () -> Unit,
    onNavigateToColorEditor: () -> Unit,
    onNavigateToPerAppTheming: () -> Unit = {},
    onNavigateToSmartSpacerComplications: () -> Unit = {},
    onNavigateToDarQAppPicker: () -> Unit = {},
    onNavigateToColorBlendrStyles: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: CustomizationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Deep Customization", fontWeight = FontWeight.ExtraBold)
                        Text("Change every visual aspect of the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = viewModel::resetToDefaults) {
                        Icon(Icons.Default.Restore, "Reset to defaults")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ══════════════════════════════════════════
            //  SECTION 1 — Theme Presets
            // ══════════════════════════════════════════
            item {
                SectionHeader(
                    icon = Icons.Default.Palette,
                    title = "Theme Presets",
                    subtitle = "12 hand-crafted full-app color schemes",
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    items(ACCThemePreset.entries) { preset ->
                        ThemePresetCard(
                            preset = preset,
                            isSelected = state.monetStyle == preset.name,
                            onClick = { viewModel.setPreset(preset) },
                        )
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 2 — Dark / Light / AMOLED
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.DarkMode, title = "Display Mode", subtitle = "Choose how the app renders colors")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("Dark",      Icons.Default.NightsStay,   true to false),
                        Triple("Light",     Icons.Default.WbSunny,      false to false),
                        Triple("AMOLED",    Icons.Default.Brightness1,  true to true),
                    ).forEach { (label, icon, mode) ->
                        val (dark, amoled) = mode
                        val selected = state.isDark == dark && state.isAmoled == amoled
                        DisplayModeChip(label = label, icon = icon, selected = selected, onClick = { viewModel.setDisplayMode(dark, amoled) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 3 — Glass & Material Style
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.BlurOn, title = "Glass & Surface Style", subtitle = "Frosted glass, elevation, and blur effects")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BigToggleRow(
                        title = "Glass Morphism",
                        subtitle = "Translucent frosted-glass cards and surfaces",
                        icon = Icons.Default.BlurCircular,
                        checked = state.useGlassEffect,
                        onToggle = viewModel::toggleGlass,
                    )
                    BigToggleRow(
                        title = "Gradient Backgrounds",
                        subtitle = "Animated gradient on main screens",
                        icon = Icons.Default.Gradient,
                        checked = state.useGradientBackground,
                        onToggle = viewModel::toggleGradientBackground,
                    )
                    BigToggleRow(
                        title = "Material You Dynamic Color",
                        subtitle = "Use wallpaper colors (overrides preset)",
                        icon = Icons.Default.AutoAwesome,
                        checked = state.useDynamicColor,
                        onToggle = viewModel::toggleDynamicColor,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Card Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CardStyle.entries) { style ->
                        FilterChip(
                            selected = state.cardStyle == style,
                            onClick = { viewModel.setCardStyle(style) },
                            label = { Text(style.displayName) },
                            leadingIcon = if (state.cardStyle == style) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null,
                        )
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 4 — Shape & Corner Radius
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.RoundedCorner, title = "Shapes & Corners", subtitle = "Corner radius from sharp to pill")
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Corner Radius",
                    value = state.cornerRadiusScale,
                    onValueChange = viewModel::setCornerRadius,
                    valueRange = 0.2f..2.0f,
                    steps = 8,
                    displayValue = when {
                        state.cornerRadiusScale < 0.5f -> "Sharp"
                        state.cornerRadiusScale < 0.9f -> "Slightly Rounded"
                        state.cornerRadiusScale < 1.2f -> "Material Default"
                        state.cornerRadiusScale < 1.6f -> "Very Round"
                        else -> "Pill"
                    },
                )
                Spacer(Modifier.height(8.dp))
                // Shape preview
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(0.2f, 0.7f, 1.0f, 1.5f, 2.0f).forEach { r ->
                        val corners = (16f * r).dp
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(corners))
                                .background(if (state.cornerRadiusScale == r) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.setCornerRadius(r) }
                        )
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 5 — Typography & Font Scale
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.TextFields, title = "Typography", subtitle = "Font size scale for all text in the app")
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Font Scale",
                    value = state.fontScale,
                    onValueChange = viewModel::setFontScale,
                    valueRange = 0.7f..1.5f,
                    steps = 7,
                    displayValue = "${"%.2f".format(state.fontScale)}× — ${
                        when {
                            state.fontScale < 0.85f -> "Small"
                            state.fontScale < 1.05f -> "Default"
                            state.fontScale < 1.2f  -> "Large"
                            else                    -> "Extra Large"
                        }
                    }",
                )
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Preview — Title", style = MaterialTheme.typography.titleLarge.copy(fontSize = (22 * state.fontScale).sp), fontWeight = FontWeight.Bold)
                        Text("Body text preview at current scale", style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * state.fontScale).sp))
                        Text("Caption / label text", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * state.fontScale).sp), color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 6 — Elevation & Animation
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.Layers, title = "Elevation & Animation", subtitle = "Shadow depth and transition speed")
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Card Elevation",
                    value = state.elevationScale,
                    onValueChange = viewModel::setElevation,
                    valueRange = 0f..3f,
                    steps = 5,
                    displayValue = when {
                        state.elevationScale < 0.3f -> "Flat / No Shadow"
                        state.elevationScale < 0.8f -> "Subtle"
                        state.elevationScale < 1.3f -> "Normal"
                        state.elevationScale < 2.0f -> "Bold"
                        else -> "Maximum"
                    },
                )
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Animation Speed",
                    value = state.animationScale,
                    onValueChange = viewModel::setAnimationScale,
                    valueRange = 0.3f..2.0f,
                    steps = 7,
                    displayValue = when {
                        state.animationScale < 0.5f -> "Turbo Fast"
                        state.animationScale < 0.9f -> "Fast"
                        state.animationScale < 1.1f -> "Normal"
                        state.animationScale < 1.5f -> "Slow"
                        else -> "Very Slow"
                    },
                )
            }

            // ══════════════════════════════════════════
            //  SECTION 7 — Accent Intensity
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.Flare, title = "Accent Intensity", subtitle = "How vivid the theme accent colors appear")
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Intensity",
                    value = state.accentIntensity,
                    onValueChange = viewModel::setAccentIntensity,
                    valueRange = 0.4f..2.0f,
                    steps = 7,
                    displayValue = when {
                        state.accentIntensity < 0.6f -> "Very Subtle"
                        state.accentIntensity < 0.9f -> "Muted"
                        state.accentIntensity < 1.1f -> "Normal"
                        state.accentIntensity < 1.5f -> "Vivid"
                        else -> "Maximum Glow"
                    },
                )
                // Accent color swatch row
                Spacer(Modifier.height(8.dp))
                val previewColor = MaterialTheme.colorScheme.primary
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(7) { i ->
                        val a = 0.4f + (i * 0.25f)
                        Box(
                            Modifier.height(24.dp).weight(1f).clip(RoundedCornerShape(6.dp))
                                .background(previewColor.copy(alpha = a.coerceIn(0f, 1f)))
                        )
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 8 — Navigation Bar Style
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.NavigationRounded, title = "Navigation Bar Style", subtitle = "Bottom navigation bar appearance")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NavBarStyle.entries.forEach { style ->
                        val selected = state.navBarStyle == style
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.setNavBarStyle(style) },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(style.displayName, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 9 — Per-app DarQ & ColorBlendr
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.AutoFixHigh, title = "App-Level Overrides", subtitle = "Force dark mode or custom colors per app")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Per-App Dark Mode (DarQ)", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Force dark mode on apps that don't support it natively. Uses Shizuku to apply overlays.") },
                            leadingContent = { Icon(Icons.Default.NightlightRound, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable(onClick = onNavigateToDarkMode),
                        )
                    }
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("DarQ App Picker", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Pick which apps get forced dark mode treatment via DarQ overlays.") },
                            leadingContent = { Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp)) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable(onClick = onNavigateToDarQAppPicker),
                        )
                    }
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Custom Color Overlays (ColorBlendr)", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Change seed color, Monet style, and apply fabricated overlays via WRITE_SECURE_SETTINGS.") },
                            leadingContent = { Icon(Icons.Default.ColorLens, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp)) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable(onClick = onNavigateToColorEditor),
                        )
                    }
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("ColorBlendr Styles", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Browse and apply Monet color styles from the ColorBlendr library.") },
                            leadingContent = { Icon(Icons.Default.Style, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable(onClick = onNavigateToColorBlendrStyles),
                        )
                    }
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Per-App Theming (ColorBlendr)", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Apply individual seed colors to specific apps for targeted Material You theming.") },
                            leadingContent = { Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp)) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable(onClick = onNavigateToPerAppTheming),
                        )
                    }
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("SmartSpacer Complications", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Configure SmartSpacer widgets and complications for your lock screen and at-a-glance.") },
                            leadingContent = { Icon(Icons.Default.Widgets, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp)) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable(onClick = onNavigateToSmartSpacerComplications),
                        )
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 10 — Quick Seed Color Palette
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.InvertColors, title = "Seed Color", subtitle = "Quick seed color for Material You palette generation")
                Spacer(Modifier.height(10.dp))
                val seedColors = listOf(
                    0xFF4A56E2L to "Indigo",   0xFF00D4FFL to "Cyan",    0xFF00E676L to "Green",
                    0xFFFF6D00L to "Orange",   0xFFD500F9L to "Purple",  0xFFFF1744L to "Red",
                    0xFFFFD600L to "Yellow",   0xFFFF4081L to "Pink",    0xFF00BCD4L to "Teal",
                    0xFF8BC34AL to "Lime",     0xFF795548L to "Brown",   0xFF607D8BL to "Blue Grey",
                    0xFF1E88E5L to "Blue",     0xFF43A047L to "Forest",  0xFFE57373L to "Rose",
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.heightIn(max = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    items(seedColors) { (colorLong, name) ->
                        val color = Color(colorLong)
                        val isSelected = state.seedColor == colorLong.toInt()
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { viewModel.setSeedColor(colorLong.toInt()) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Text(name, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 11 — Monet / Material You Styles
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.Style, title = "Material You / Monet Style", subtitle = "Color generation algorithm from wallpaper")
                Spacer(Modifier.height(10.dp))
                val monetStyles = listOf(
                    "TONAL_SPOT"     to "Tonal Spot (Google Default)",
                    "VIBRANT"        to "Vibrant (Bold Colors)",
                    "EXPRESSIVE"     to "Expressive (Wide Range)",
                    "SPRITZ"         to "Spritz (Muted / Subtle)",
                    "RAINBOW"        to "Rainbow (Multi-hue)",
                    "FRUIT_SALAD"    to "Fruit Salad (Playful)",
                    "CONTENT"        to "Content (Source Accurate)",
                    "MONOCHROMATIC"  to "Monochromatic (Grey Only)",
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(monetStyles) { (key, label) ->
                        FilterChip(
                            selected = state.monetStyle == key,
                            onClick = { viewModel.setMonetStyle(key) },
                            label = { Text(label, maxLines = 1) },
                            leadingIcon = if (state.monetStyle == key) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null,
                        )
                    }
                }
            }

            // ══════════════════════════════════════════
            //  SECTION 12 — Apply & Save
            // ══════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = viewModel::resetToDefaults) {
                        Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset All")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { viewModel.applyTheme(); }) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply Theme")
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─── UI Component Helpers ────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ThemePresetCard(preset: ACCThemePreset, isSelected: Boolean, onClick: () -> Unit) {
    val border = if (isSelected) BorderStroke(2.dp, preset.primaryColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1.0f, label = "preset_scale")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) preset.primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = border,
        modifier = Modifier.width(130.dp).scale(scale).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Color swatches
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(preset.primaryColor, preset.secondaryColor, preset.tertiaryColor).forEach { c ->
                    Box(Modifier.size(20.dp).clip(CircleShape).background(c))
                }
                if (isSelected) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(preset.primaryColor), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Text(preset.emoji + " " + preset.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(preset.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 9.sp)
        }
    }
}

@Composable
private fun DisplayModeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BigToggleRow(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SliderSetting(label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int, displayValue: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Text(displayValue, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
