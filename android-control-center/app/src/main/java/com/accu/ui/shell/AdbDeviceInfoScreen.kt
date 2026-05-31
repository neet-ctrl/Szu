package com.accu.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class DeviceInfo(
    val manufacturer: String  = "Samsung",
    val model: String         = "Galaxy S23 Ultra",
    val brand: String         = "Samsung",
    val device: String        = "dm3q",
    val codename: String      = "dm3q",
    val serial: String        = "R3CXB00ABCD",
    val fingerprint: String   = "samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S918BXXS3CXC1:user/release-keys",
    val androidVersion: String= "14",
    val androidBuildId: String= "UP1A.231005.007",
    val securityPatch: String = "2024-03-01",
    val apiLevel: String      = "34",
    val buildType: String     = "user",
    val buildTags: String     = "release-keys",
    val cpuAbi: String        = "arm64-v8a",
    val cpuCores: String      = "8",
    val cpuGovernor: String   = "schedutil",
    val cpuMaxFreq: String    = "3.36 GHz",
    val soc: String           = "Snapdragon 8 Gen 2",
    val ramTotal: Long        = 12288L,
    val ramFree: Long         = 4096L,
    val storageTotal: Long    = 256 * 1024L,
    val storageFree: Long     = 183 * 1024L,
    val resolution: String    = "3088 × 1440",
    val density: String       = "500 dpi",
    val refreshRate: String   = "120 Hz",
    val screenSize: String    = "6.8\"",
    val batteryLevel: Int     = 73,
    val batteryStatus: String = "Discharging",
    val batteryHealth: String = "Good",
    val batteryTemp: String   = "32.5°C",
    val batteryVoltage: String= "4.12 V",
    val batteryTech: String   = "Li-ion",
    val wifiMac: String       = "AA:BB:CC:DD:EE:FF",
    val bluetoothMac: String  = "11:22:33:44:55:66",
    val imei: String          = "35*******12345",
    val operator: String      = "T-Mobile",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbDeviceInfoScreen(onBack: () -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val info = remember { DeviceInfo() }
    var isRefreshing by remember { mutableStateOf(false) }
    var batteryLevel by remember { mutableIntStateOf(info.batteryLevel) }
    var ramFree by remember { mutableLongStateOf(info.ramFree) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            batteryLevel = (batteryLevel + if (Math.random() > 0.85) -1 else 0).coerceIn(0, 100)
            ramFree = (ramFree + ((-300L..300L).random())).coerceIn(512L, info.ramTotal - 512L)
        }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Device Information",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(buildFullReport(info, batteryLevel, ramFree))) }) {
                        Icon(Icons.Outlined.ContentCopy, "Copy report")
                    }
                    IconButton(onClick = { scope.launch { isRefreshing = true; delay(1200); isRefreshing = false } }) {
                        if (isRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // Hero
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhoneAndroid, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Column {
                            Text(info.model, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${info.manufacturer} · Android ${info.androidVersion}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("API ${info.apiLevel} · ${info.device}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Live gauges
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GaugeCard(Modifier.weight(1f), Icons.Outlined.BatteryChargingFull, "Battery", batteryLevel / 100f, "$batteryLevel%", info.batteryStatus, when { batteryLevel > 60 -> AccentGreen; batteryLevel > 20 -> AccentOrange; else -> AccentRed })
                    GaugeCard(Modifier.weight(1f), Icons.Outlined.Memory, "RAM", 1f - ramFree / info.ramTotal.toFloat(), "${info.ramTotal / 1024} GB", "${ramFree / 1024} GB free", AccentCyan)
                    GaugeCard(Modifier.weight(1f), Icons.Outlined.Storage, "Storage", 1f - info.storageFree / info.storageTotal.toFloat(), "${info.storageTotal / 1024} GB", "${info.storageFree / 1024} GB free", AccentOrange)
                }
            }

            // Android & Build
            item {
                InfoSection("Android & Build", Icons.Default.Android) {
                    InfoRow("Android Version", info.androidVersion, clipboard)
                    InfoRow("API Level",        info.apiLevel, clipboard)
                    InfoRow("Build ID",         info.androidBuildId, clipboard)
                    InfoRow("Security Patch",   info.securityPatch, clipboard)
                    InfoRow("Build Type",       info.buildType, clipboard)
                    InfoRow("Build Tags",       info.buildTags, clipboard)
                    InfoRow("Fingerprint",      info.fingerprint, clipboard, monospace = true)
                }
            }

            // Identity
            item {
                InfoSection("Device Identity", Icons.Default.PhoneAndroid) {
                    InfoRow("Manufacturer", info.manufacturer, clipboard)
                    InfoRow("Model",        info.model, clipboard)
                    InfoRow("Brand",        info.brand, clipboard)
                    InfoRow("Device",       info.device, clipboard)
                    InfoRow("Codename",     info.codename, clipboard)
                    InfoRow("Serial",       info.serial, clipboard, monospace = true)
                }
            }

            // CPU
            item {
                InfoSection("Processor (CPU)", Icons.Default.Memory) {
                    InfoRow("SoC",      info.soc, clipboard)
                    InfoRow("ABI",      info.cpuAbi, clipboard)
                    InfoRow("Cores",    info.cpuCores, clipboard)
                    InfoRow("Max Freq", info.cpuMaxFreq, clipboard)
                    InfoRow("Governor", info.cpuGovernor, clipboard)
                }
            }

            // Display
            item {
                InfoSection("Display", Icons.Default.Monitor) {
                    InfoRow("Resolution",   info.resolution, clipboard)
                    InfoRow("Density",      info.density, clipboard)
                    InfoRow("Refresh Rate", info.refreshRate, clipboard)
                    InfoRow("Screen Size",  info.screenSize, clipboard)
                }
            }

            // Battery
            item {
                InfoSection("Battery", Icons.Default.Battery5Bar) {
                    InfoRow("Level",       "$batteryLevel%", clipboard)
                    InfoRow("Status",      info.batteryStatus, clipboard)
                    InfoRow("Health",      info.batteryHealth, clipboard)
                    InfoRow("Temperature", info.batteryTemp, clipboard)
                    InfoRow("Voltage",     info.batteryVoltage, clipboard)
                    InfoRow("Technology",  info.batteryTech, clipboard)
                }
            }

            // Network
            item {
                InfoSection("Network / Radio", Icons.Default.Wifi) {
                    InfoRow("Wi-Fi MAC",  info.wifiMac, clipboard, monospace = true)
                    InfoRow("BT MAC",     info.bluetoothMac, clipboard, monospace = true)
                    InfoRow("IMEI",       info.imei, clipboard, monospace = true)
                    InfoRow("Operator",   info.operator, clipboard)
                }
            }

            // ADB commands reference
            item {
                InfoSection("ADB Commands to Read This Info", Icons.Default.Terminal) {
                    val cmds = listOf(
                        "getprop ro.product.manufacturer" to "Manufacturer",
                        "getprop ro.product.model"        to "Model",
                        "getprop ro.build.version.release" to "Android version",
                        "getprop ro.build.version.sdk"    to "API level",
                        "getprop ro.build.id"             to "Build ID",
                        "getprop ro.build.fingerprint"    to "Fingerprint",
                        "cat /proc/cpuinfo"               to "Full CPU info",
                        "cat /proc/meminfo"               to "RAM/memory details",
                        "dumpsys battery"                 to "Battery status & health",
                        "wm size"                         to "Screen resolution",
                        "wm density"                      to "Screen density (dpi)",
                        "settings get system screen_off_timeout" to "Screen timeout",
                        "getprop ro.serialno"             to "Device serial number",
                        "dumpsys telephony.registry | grep mSignalStrength" to "Signal info",
                    )
                    cmds.forEach { (cmd, desc) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(cmd)) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GaugeCard(modifier: Modifier, icon: ImageVector, title: String, value: Float, label: String, sublabel: String, color: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { value.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                strokeCap = StrokeCap.Round,
            )
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(sublabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) { content() }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, clipboard: androidx.compose.ui.platform.ClipboardManager, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(106.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = if (monospace) FontFamily.Monospace else null, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2)
        IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
        }
    }
}

private fun buildFullReport(info: DeviceInfo, battery: Int, ramFreeMB: Long) = buildString {
    appendLine("ACCU Device Report")
    appendLine("==================")
    appendLine("Model:           ${info.model}")
    appendLine("Manufacturer:    ${info.manufacturer}")
    appendLine("Serial:          ${info.serial}")
    appendLine("Android Version: ${info.androidVersion}")
    appendLine("API Level:       ${info.apiLevel}")
    appendLine("Build ID:        ${info.androidBuildId}")
    appendLine("Security Patch:  ${info.securityPatch}")
    appendLine("Fingerprint:     ${info.fingerprint}")
    appendLine()
    appendLine("SoC:             ${info.soc}")
    appendLine("CPU ABI:         ${info.cpuAbi}")
    appendLine("CPU Cores:       ${info.cpuCores}")
    appendLine("Max Freq:        ${info.cpuMaxFreq}")
    appendLine()
    appendLine("RAM:             ${info.ramTotal / 1024} GB total, ${ramFreeMB / 1024} GB free")
    appendLine("Storage:         ${info.storageTotal / 1024} GB total, ${info.storageFree / 1024} GB free")
    appendLine()
    appendLine("Resolution:      ${info.resolution}")
    appendLine("Density:         ${info.density}")
    appendLine("Refresh Rate:    ${info.refreshRate}")
    appendLine()
    appendLine("Battery:         $battery% (${info.batteryStatus})")
    appendLine("Temp:            ${info.batteryTemp}")
    appendLine()
    appendLine("Wi-Fi MAC:       ${info.wifiMac}")
    appendLine("BT MAC:          ${info.bluetoothMac}")
    appendLine("Operator:        ${info.operator}")
}

private fun LongRange.random(): Long = (Math.random() * (endInclusive - start) + start).toLong()
