package com.accu.ui.network

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

data class TileConfig(
    val id: String,
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val requireUnlockEnabled: Boolean = false,
    val showSSIDEnabled: Boolean = false,
    val longPressAction: String = "Open Settings",
)

val TILE_CONFIGS = listOf(
    TileConfig("wifi", "Wi-Fi Tile", Icons.Default.Wifi, "Independent Wi-Fi toggle — switches Wi-Fi without touching mobile data"),
    TileConfig("mobile", "Mobile Data Tile", Icons.Default.SignalCellularAlt, "Independent mobile data toggle — no accidental Wi-Fi changes"),
    TileConfig("internet", "Internet Tile (Combined)", Icons.Default.Language, "Combined internet tile like stock Android but with enhanced controls"),
    TileConfig("bluetooth", "Bluetooth Tile", Icons.Default.Bluetooth, "Quick bluetooth toggle with device status"),
    TileConfig("nfc", "NFC Tile", Icons.Default.Nfc, "NFC quick toggle"),
    TileConfig("airplane", "Airplane Mode Tile", Icons.Default.AirplanemodeActive, "Airplane mode toggle with auto-reconnect option"),
    TileConfig("hotspot", "Hotspot Tile", Icons.Default.Wifi, "Mobile hotspot toggle"),
)

enum class ShellMethod(val label: String, val description: String) {
    SHIZUKU("Shizuku (Recommended)", "Uses Shizuku IPC for fastest, most reliable toggle"),
    ROOT("Root (libsu)", "Direct root shell commands — requires root access"),
    ADB("Wireless ADB", "Commands via wireless ADB — slower but doesn't need root or Shizuku"),
    ACCESSIBILITY("Accessibility Service", "Uses taps/UI automation — slowest, works without any special access"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetterInternetTilesSettingsScreen(onBack: () -> Unit) {
    var selectedMethod by remember { mutableStateOf(ShellMethod.SHIZUKU) }
    val tileConfigs = remember { mutableStateListOf(*TILE_CONFIGS.toTypedArray()) }
    var expandedTile by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Internet Tiles Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Shell Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Choose how tiles execute toggle commands", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(ShellMethod.entries) { method ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMethod == method) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    onClick = { selectedMethod = method },
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedMethod == method, onClick = { selectedMethod = method })
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(method.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Tile Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(tileConfigs, key = { it.id }) { tile ->
                val idx = tileConfigs.indexOfFirst { it.id == tile.id }
                TileSettingsCard(
                    tile = tile,
                    isExpanded = expandedTile == tile.id,
                    onToggleExpand = { expandedTile = if (expandedTile == tile.id) null else tile.id },
                    onRequireUnlockChange = { if (idx != -1) tileConfigs[idx] = tile.copy(requireUnlockEnabled = it) },
                    onShowSSIDChange = { if (idx != -1) tileConfigs[idx] = tile.copy(showSSIDEnabled = it) },
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("How to Add Tiles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        InfoStep(1, "Swipe down twice to open Quick Settings fully")
                        InfoStep(2, "Tap the edit (pencil) icon")
                        InfoStep(3, "Scroll down to find ACC tiles")
                        InfoStep(4, "Drag tiles to your active area")
                        InfoStep(5, "Long-press any tile to configure it")
                    }
                }
            }
        }
    }
}

@Composable
private fun TileSettingsCard(
    tile: TileConfig,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRequireUnlockChange: (Boolean) -> Unit,
    onShowSSIDChange: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
            ) {
                Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(tile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Require Device Unlock") },
                        supportingContent = { Text("Prompt for biometric/PIN before toggling") },
                        leadingContent = { Icon(Icons.Default.Lock, null) },
                        trailingContent = { Switch(checked = tile.requireUnlockEnabled, onCheckedChange = onRequireUnlockChange) }
                    )
                    if (tile.id == "wifi") {
                        ListItem(
                            headlineContent = { Text("Show Wi-Fi SSID") },
                            supportingContent = { Text("Display current network name in tile subtitle") },
                            leadingContent = { Icon(Icons.Default.Wifi, null) },
                            trailingContent = { Switch(checked = tile.showSSIDEnabled, onCheckedChange = onShowSSIDChange) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStep(step: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.secondary, shape = androidx.compose.foundation.shape.CircleShape) {
            Text(
                "$step",
                modifier = Modifier.size(24.dp).wrapContentSize(Alignment.Center),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
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
