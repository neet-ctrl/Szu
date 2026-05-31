package com.accu.ui.network

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.components.SectionHeaderWithInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkCenterScreen(
    onBack: () -> Unit = {},
    viewModel: NetworkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Network Center",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Network Center",
                        description = "Control all network connections and add Quick Settings tiles.\n\nBetter Internet Tiles features:\n• Wi-Fi, Mobile Data, Hotspot tiles\n• Bluetooth and NFC tiles\n• Airplane Mode tile\n\nAll tiles use Shizuku to actually toggle (unlike stock Android tiles that open settings).\n\nHow to add tiles: Pull down notification shade → tap pencil icon → drag ACC tiles to active area."
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network status card
            item {
                NetworkStatusCard(uiState = uiState)
            }

            // Quick toggles
            item {
                SectionHeaderWithInfo(
                    title = "Quick Toggles",
                    infoTitle = "Quick Toggles",
                    infoDescription = "Direct network toggles powered by Shizuku. These actually enable/disable radios — no dialog boxes.\n\nRequires Shizuku to be running.",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkToggleCard(
                            label = "Wi-Fi",
                            icon = Icons.Outlined.Wifi,
                            isEnabled = uiState.isWifiEnabled,
                            subtitle = uiState.wifiSsid ?: "Off",
                            onToggle = { viewModel.toggleWifi() },
                            modifier = Modifier.weight(1f),
                            infoText = "Directly enables/disables Wi-Fi via Shizuku. No Settings dialog."
                        )
                        NetworkToggleCard(
                            label = "Mobile Data",
                            icon = Icons.Outlined.SignalCellularAlt,
                            isEnabled = uiState.isMobileDataEnabled,
                            subtitle = uiState.carrierName ?: "Off",
                            onToggle = { viewModel.toggleMobileData() },
                            modifier = Modifier.weight(1f),
                            infoText = "Directly enables/disables mobile data via Shizuku."
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkToggleCard(
                            label = "Hotspot",
                            icon = Icons.Outlined.Wifi,
                            isEnabled = uiState.isHotspotEnabled,
                            subtitle = if (uiState.isHotspotEnabled) "Active" else "Off",
                            onToggle = { viewModel.toggleHotspot() },
                            modifier = Modifier.weight(1f),
                            infoText = "Enables/disables Wi-Fi hotspot sharing via Shizuku."
                        )
                        NetworkToggleCard(
                            label = "Bluetooth",
                            icon = Icons.Outlined.Bluetooth,
                            isEnabled = uiState.isBluetoothEnabled,
                            subtitle = if (uiState.isBluetoothEnabled) "On" else "Off",
                            onToggle = { viewModel.toggleBluetooth() },
                            modifier = Modifier.weight(1f),
                            infoText = "Directly enables/disables Bluetooth."
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkToggleCard(
                            label = "NFC",
                            icon = Icons.Outlined.Nfc,
                            isEnabled = uiState.isNfcEnabled,
                            subtitle = if (uiState.isNfcEnabled) "On" else "Off",
                            onToggle = { viewModel.toggleNfc() },
                            modifier = Modifier.weight(1f),
                            infoText = "Directly enables/disables NFC via Shizuku."
                        )
                        NetworkToggleCard(
                            label = "Airplane",
                            icon = Icons.Outlined.AirplanemodeActive,
                            isEnabled = uiState.isAirplaneModeEnabled,
                            subtitle = if (uiState.isAirplaneModeEnabled) "On" else "Off",
                            onToggle = { viewModel.toggleAirplaneMode() },
                            modifier = Modifier.weight(1f),
                            infoText = "Toggles airplane mode directly via Shizuku (settings + broadcast)."
                        )
                    }
                }
            }

            // Quick Settings Tiles guide
            item {
                SectionHeaderWithInfo(
                    title = "Quick Settings Tiles",
                    infoTitle = "Quick Settings Tiles",
                    infoDescription = "ACC provides 6 Quick Settings tiles that actually toggle radios instead of just opening Settings.\n\nTo add them:\n1. Swipe down twice to open full QS panel\n2. Tap pencil icon to edit\n3. Scroll down to find ACC tiles\n4. Drag them to your active tiles area",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            Triple(Icons.Outlined.Wifi, "ACC Wi-Fi Tile", "Directly toggles Wi-Fi on/off"),
                            Triple(Icons.Outlined.SignalCellularAlt, "ACC Mobile Data Tile", "Directly enables/disables mobile data"),
                            Triple(Icons.Outlined.Wifi, "ACC Hotspot Tile", "Starts/stops Wi-Fi hotspot"),
                            Triple(Icons.Outlined.Bluetooth, "ACC Bluetooth Tile", "Directly toggles Bluetooth"),
                            Triple(Icons.Outlined.Nfc, "ACC NFC Tile", "Directly toggles NFC"),
                            Triple(Icons.Outlined.AirplanemodeActive, "ACC Airplane Mode Tile", "Toggles airplane mode")
                        ).forEach { (icon, name, desc) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Network info
            item {
                SectionHeaderWithInfo(
                    title = "Network Information",
                    infoTitle = "Network Info",
                    infoDescription = "Detailed network statistics from the Android connectivity APIs. Updates automatically every 30 seconds.",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkInfoRow("IP Address", uiState.ipAddress ?: "N/A", Icons.Outlined.Language)
                        NetworkInfoRow("Wi-Fi Network", uiState.wifiSsid ?: "Not connected", Icons.Outlined.Wifi)
                        NetworkInfoRow("Signal Strength", "${uiState.wifiSignalStrength ?: "--"} dBm", Icons.Outlined.SignalWifi4Bar)
                        NetworkInfoRow("Mobile Carrier", uiState.carrierName ?: "No SIM", Icons.Outlined.SimCard)
                        NetworkInfoRow("Network Type", uiState.networkType ?: "Unknown", Icons.Outlined.NetworkCheck)
                        NetworkInfoRow("DNS Server", uiState.dnsServer ?: "Default", Icons.Outlined.Dns)
                        NetworkInfoRow("VPN Active", if (uiState.isVpnActive) "Yes" else "No", Icons.Outlined.VpnKey)
                    }
                }
            }

            // Private DNS
            item {
                SectionHeaderWithInfo(
                    title = "Private DNS",
                    infoTitle = "Private DNS / DNS over TLS",
                    infoDescription = "DNS over TLS (Private DNS) encrypts DNS queries, preventing ISPs and network eavesdroppers from seeing which domains you visit.\n\nPopular options:\n• cloudflare-dns.com (1.1.1.1)\n• dns.google\n• dns.adguard.com (blocks ads)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Private DNS Mode", style = MaterialTheme.typography.bodyMedium)
                            Text(uiState.privateDnsMode ?: "Off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (uiState.privateDnsHost != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("DNS Host", style = MaterialTheme.typography.bodyMedium)
                                Text(uiState.privateDnsHost, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        FilledTonalButton(
                            onClick = { viewModel.openPrivateDnsSettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Dns, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Configure Private DNS")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun NetworkStatusCard(uiState: NetworkUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.isWifiEnabled || uiState.isMobileDataEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                if (uiState.isWifiEnabled) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                null,
                Modifier.size(40.dp)
            )
            Column {
                Text(
                    if (uiState.isWifiEnabled) "Connected: ${uiState.wifiSsid ?: "Wi-Fi"}"
                    else if (uiState.isMobileDataEnabled) "Mobile Data Active"
                    else "No Internet Connection",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    uiState.ipAddress ?: "No IP",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun NetworkToggleCard(
    label: String,
    icon: ImageVector,
    isEnabled: Boolean,
    subtitle: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    infoText: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onToggle
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                InfoTooltipIcon(title = label, description = infoText, iconSize = 14)
            }
            Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun NetworkInfoRow(label: String, value: String, icon: ImageVector) {
    val clipboardManager = LocalClipboardManager.current
    val copyable = value != "N/A" && value != "Not connected" && value != "No SIM" && value != "Unknown" && value != "Default" && value != "Yes" && value != "No"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (copyable) {
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(value)) },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
