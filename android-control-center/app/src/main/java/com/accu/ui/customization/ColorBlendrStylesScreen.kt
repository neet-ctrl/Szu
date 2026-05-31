package com.accu.ui.customization

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class MonetStyle(val label: String, val description: String) {
    TONAL_SPOT("Tonal Spot", "Default Material You — subtle, balanced"),
    VIBRANT("Vibrant", "High-energy, bold color palette"),
    EXPRESSIVE("Expressive", "Broad, eclectic colors"),
    SPRITZ("Spritz", "Desaturated, muted tones"),
    RAINBOW("Rainbow", "Multi-hue, wide variance"),
    FRUIT_SALAD("Fruit Salad", "Analogous colors, cheerful"),
    CONTENT("Content", "Color derived from wallpaper content"),
    FIDELITY("Fidelity", "True-to-wallpaper reproduction"),
    MONOCHROME("Monochrome", "Grayscale + accent only"),
}

data class CustomColorStyle(
    val id: String,
    val name: String,
    val seedColor: Color,
    val monetStyle: MonetStyle,
    val chroma: Float = 1.0f,
    val pitchBlack: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorBlendrStylesScreen(onBack: () -> Unit) {
    val savedStyles = remember {
        mutableStateListOf(
            CustomColorStyle("1", "Ocean Blue", Color(0xFF0066CC), MonetStyle.TONAL_SPOT),
            CustomColorStyle("2", "Forest Green", Color(0xFF2E7D32), MonetStyle.VIBRANT),
            CustomColorStyle("3", "Sunset Rose", Color(0xFFE91E63), MonetStyle.EXPRESSIVE),
        )
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var activeStyleId by remember { mutableStateOf<String?>(null) }
    var showMonetPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Styles") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "New Style") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonetStylePicker()
            }
            item {
                Text("Saved Styles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(savedStyles, key = { it.id }) { style ->
                CustomStyleCard(
                    style = style,
                    isActive = activeStyleId == style.id,
                    onApply = { activeStyleId = style.id },
                    onDelete = { savedStyles.remove(style) },
                    onEdit = {},
                )
            }
            item {
                PerPackageColorsSection()
            }
        }
    }

    if (showCreateDialog) {
        CreateStyleDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { style ->
                savedStyles.add(style)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun MonetStylePicker() {
    var selected by remember { mutableStateOf(MonetStyle.TONAL_SPOT) }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Monet Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonetStyle.entries.forEach { style ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected == style) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { selected = style }
                            .padding(10.dp)
                            .width(80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = if (selected == style) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            style.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected == style) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                        )
                    }
                }
            }
            AnimatedContent(targetState = selected) { s ->
                Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CustomStyleCard(
    style: CustomColorStyle,
    isActive: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(style.seedColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(style.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (isActive) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                            Text("Active", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Text(style.monetStyle.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)) }
            Button(onClick = onApply) { Text(if (isActive) "Applied" else "Apply") }
        }
    }
}

@Composable
private fun PerPackageColorsSection() {
    var expanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Per-App Color Overrides", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Apply different Material You color seeds to specific apps. Each app can have its own color scheme independent of system theme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {}) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add App Override")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateStyleDialog(onDismiss: () -> Unit, onCreate: (CustomColorStyle) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(MonetStyle.TONAL_SPOT) }
    var seedColorHex by remember { mutableStateOf("0066CC") }
    val sampleColors = listOf(Color(0xFF0066CC), Color(0xFF2E7D32), Color(0xFFE91E63), Color(0xFFFF6F00), Color(0xFF7B1FA2), Color(0xFF00838F))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Style") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Style Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Seed Color:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sampleColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { seedColorHex = "%06X".format(color.toArgb() and 0xFFFFFF) }
                        )
                    }
                }
                Text("Monet Style:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MonetStyle.entries) { style ->
                        FilterChip(selected = selectedStyle == style, onClick = { selectedStyle = style }, label = { Text(style.label) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = seedColorHex.substring(0, 2).toInt(16)
                    val g = seedColorHex.substring(2, 4).toInt(16)
                    val b = seedColorHex.substring(4, 6).toInt(16)
                    onCreate(CustomColorStyle(System.currentTimeMillis().toString(), name, Color(r, g, b), selectedStyle))
                },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = calculateTopPadding() + other.calculateTopPadding(),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)
