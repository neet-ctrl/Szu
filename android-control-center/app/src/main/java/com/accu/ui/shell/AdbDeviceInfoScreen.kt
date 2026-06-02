package com.accu.ui.shell

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.ui.components.ACCTopBar
import com.accu.ui.shizuku.ShizukuViewModel
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DeviceInfo(
    val manufacturer: String  = "",
    val model: String         = "",
    val brand: String         = "",
    val device: String        = "",
    val codename: String      = "",
    val serial: String        = "",
    val fingerprint: String   = "",
    val androidVersion: String= "",
    val androidBuildId: String= "",
    val securityPatch: String = "",
    val apiLevel: String      = "",
    val buildType: String     = "",
    val buildTags: String     = "",
    val cpuAbi: String        = "",
    val cpuCores: String      = "",
    val cpuGovernor: String   = "",
    val cpuMaxFreq: String    = "",
    val soc: String           = "",
    val ramTotal: Long        = 0L,
    val ramFree: Long         = 0L,
    val storageTotal: Long    = 0L,
    val storageFree: Long     = 0L,
    val resolution: String    = "",
    val density: String       = "",
    val refreshRate: String   = "",
    val screenSize: String    = "",
    val batteryLevel: Int     = 0,
    val batteryStatus: String = "",
    val batteryHealth: String = "",
    val batteryTemp: String   = "",
    val batteryVoltage: String= "",
    val batteryTech: String   = "",
    val wifiMac: String       = "",
    val bluetoothMac: String  = "",
    val imei: String          = "",
    val operator: String      = "",
)

private fun loadDeviceInfo(context: Context, shellExec: (String) -> String): DeviceInfo {
    fun prop(key: String) = shellExec("getprop $key 2>/dev/null").trim()
    fun readFile(path: String) = try { java.io.File(path).readText().trim() } catch (_: Exception) { "" }

    val manufacturer = Build.MANUFACTURER
    val model        = Build.MODEL
    val brand        = Build.BRAND
    val device       = Build.DEVICE
    val codename     = Build.VERSION.CODENAME
    val fingerprint  = Build.FINGERPRINT
    val androidVer   = Build.VERSION.RELEASE
    val buildId      = Build.ID
    val secPatch     = Build.VERSION.SECURITY_PATCH
    val apiLevel     = Build.VERSION.SDK_INT.toString()
    val buildType    = Build.TYPE
    val buildTags    = Build.TAGS
    val abi          = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
    val serial       = try { @Suppress("DEPRECATION") Build.SERIAL } catch (_: Exception) { prop("ro.serialno") }

    val coreCount = Runtime.getRuntime().availableProcessors()
    val cpuCores  = coreCount.toString()
    val cpuGov    = (0 until coreCount).firstNotNullOfOrNull { cpu ->
        readFile("/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor").ifBlank { null }
    } ?: prop("ro.boot.cpufreq_gov")
    val maxKhz    = (0 until coreCount).mapNotNull { cpu ->
        readFile("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").toLongOrNull()
    }.maxOrNull()
    val cpuMaxFreq = if (maxKhz != null && maxKhz > 0) "${"%.2f".format(maxKhz / 1_000_000.0)} GHz" else ""
    val soc       = prop("ro.board.platform").ifBlank { prop("ro.hardware").ifBlank { prop("ro.chipname") } }

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val ramTotalMb = memInfo.totalMem / 1024
    val ramFreeMb  = memInfo.availMem / 1024

    val sf = try { StatFs("/sdcard") } catch (_: Exception) { null }
    val storageTotalMb = sf?.let { it.totalBytes / 1024 } ?: 0L
    val storageFreeMb  = sf?.let { it.availableBytes / 1024 } ?: 0L

    @Suppress("DEPRECATION")
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val dm = DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
    val resolution = "${dm.widthPixels} × ${dm.heightPixels}"
    val density    = "${dm.densityDpi} dpi"

    val battIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val battLevel  = battIntent?.let { i ->
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    } ?: -1
    val battStatusInt = battIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val battStatus = when (battStatusInt) {
        BatteryManager.BATTERY_STATUS_CHARGING    -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL        -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING-> "Not charging"
        else -> "Unknown"
    }
    val battHealthInt = battIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
    val battHealth = when (battHealthInt) {
        BatteryManager.BATTERY_HEALTH_GOOD        -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT    -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD        -> "Dead"
        BatteryManager.BATTERY_HEALTH_COLD        -> "Cold"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE-> "Over Voltage"
        else -> "Unknown"
    }
    val battTempRaw  = battIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val battTemp     = "${"%.1f".format(battTempRaw / 10.0)}°C"
    val battVoltRaw  = battIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    val battVoltage  = "${"%.2f".format(battVoltRaw / 1000.0)} V"
    val battTech     = battIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

    val wifiMac  = readFile("/sys/class/net/wlan0/address").ifBlank { prop("ro.boot.wifimacaddr") }
    val btMac    = readFile("/sys/class/bluetooth/hci0/address").ifBlank { "" }
    val operator = prop("gsm.operator.alpha").ifBlank { prop("ro.cdma.home.operator.alpha") }

    return DeviceInfo(
        manufacturer   = manufacturer,
        model          = model,
        brand          = brand,
        device         = device,
        codename       = codename,
        serial         = serial,
        fingerprint    = fingerprint,
        androidVersion = androidVer,
        androidBuildId = buildId,
        securityPatch  = secPatch,
        apiLevel       = apiLevel,
        buildType      = buildType,
        buildTags      = buildTags,
        cpuAbi         = abi,
        cpuCores       = cpuCores,
        cpuGovernor    = cpuGov,
        cpuMaxFreq     = cpuMaxFreq,
        soc            = soc,
        ramTotal       = ramTotalMb,
        ramFree        = ramFreeMb,
        storageTotal   = storageTotalMb,
        storageFree    = storageFreeMb,
        resolution     = resolution,
        density        = density,
        batteryLevel   = if (battLevel >= 0) battLevel else 0,
        batteryStatus  = battStatus,
        batteryHealth  = battHealth,
        batteryTemp    = battTemp,
        batteryVoltage = battVoltage,
        batteryTech    = battTech,
        wifiMac        = wifiMac,
        bluetoothMac   = btMac,
        operator       = operator,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbDeviceInfoScreen(onBack: () -> Unit = {}) {
    val context   = LocalContext.current
    val vm: ShizukuViewModel = hiltViewModel()
    val clipboard = LocalClipboardManager.current
    val scope     = rememberCoroutineScope()

    var info by remember { mutableStateOf(DeviceInfo()) }
    var isLoading   by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var batteryLevel by remember { mutableIntStateOf(0) }
    var ramFree      by remember { mutableLongStateOf(0L) }

    suspend fun refresh() {
        val loaded = withContext(Dispatchers.IO) {
            loadDeviceInfo(context) { cmd -> vm.connectionManager.exec(cmd).output }
        }
        info         = loaded
        batteryLevel = loaded.batteryLevel
        ramFree      = loaded.ramFree
    }

    LaunchedEffect(Unit) {
        refresh()
        isLoading = false
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
                    IconButton(onClick = { scope.launch { isRefreshing = true; refresh(); isRefreshing = false } }) {
                        if (isRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else LazyColumn(
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

