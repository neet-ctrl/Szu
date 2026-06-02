package com.accu.ui.shizuku

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentRed
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentCyan
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuCenterScreen(
    onBack: () -> Unit = {},
    onNavigateToAdbPairing: () -> Unit = {},
    onNavigateToAccuApps: () -> Unit = {},
    onNavigateToAccuServiceHub: () -> Unit = {},
    viewModel: ShizukuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf("Status", "Apps", "Rish", "Settings", "Logs")
    val tabIcons = listOf(
        Icons.Outlined.Dashboard, Icons.Outlined.Apps, Icons.Outlined.Terminal,
        Icons.Outlined.Tune, Icons.Outlined.Article
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ACCU Connection", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onNavigateToAdbPairing) { Icon(Icons.Outlined.Usb, "ADB Pairing") }
                    IconButton(onClick = onNavigateToAccuApps) { Icon(Icons.Outlined.Apps, "ACCU Apps") }
                    IconButton(onClick = onNavigateToAccuServiceHub) { Icon(Icons.Outlined.Api, "ACCU Service Hub") }
                    if (state.isAvailable && state.isGranted) {
                        IconButton(onClick = viewModel::runDiagnostics) {
                            if (state.diagnosticsRunning) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.BugReport, "Diagnostics")
                        }
                    }
                    IconButton(onClick = viewModel::clearLogs) { Icon(Icons.Outlined.DeleteSweep, "Clear Logs") }
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]))
                }
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(tabIcons[idx], null, Modifier.size(18.dp)) },
                        text = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            when (selectedTab) {
                0 -> StatusTab(state, viewModel)
                1 -> AuthorizedAppsTab(state, viewModel)
                2 -> RishTab(state, viewModel)
                3 -> SettingsTab(state, viewModel)
                4 -> LogsTab(state, viewModel)
            }
        }
    }
}

// ── Tab 1: Status ─────────────────────────────────────────────────────────────

private enum class DeviceInfoCategory(val label: String, val icon: ImageVector, val accentColor: Color) {
    OVERVIEW ("Overview",  Icons.Outlined.Dashboard,          Color(0xFF4A90D9)),
    DEVICE   ("Device",    Icons.Outlined.PhoneAndroid,       Color(0xFF7C83FF)),
    HARDWARE ("Hardware",  Icons.Outlined.Memory,             Color(0xFF00BCD4)),
    BATTERY  ("Battery",   Icons.Outlined.BatteryFull,        Color(0xFF66BB6A)),
    STORAGE  ("Storage",   Icons.Outlined.Storage,            Color(0xFFFF8A65)),
    NETWORK  ("Network",   Icons.Outlined.Wifi,               Color(0xFF26C6DA)),
    SYSTEM   ("System",    Icons.Outlined.Android,            Color(0xFFAB47BC)),
    SECURITY ("Security",  Icons.Outlined.Security,           Color(0xFFEF5350)),
}

@Composable
private fun StatusTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    var category by remember { mutableStateOf(DeviceInfoCategory.OVERVIEW) }
    val info = state.targetInfo

    Column(Modifier.fillMaxSize()) {
        // ── Connection banner ─────────────────────────────────────────────────
        ConnectionBanner(state, vm)

        // ── Category filter strip ─────────────────────────────────────────────
        if (state.isGranted) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(DeviceInfoCategory.entries) { cat ->
                    val selected = category == cat
                    FilterChip(
                        selected = selected,
                        onClick  = { category = cat },
                        label    = { Text(cat.label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(cat.icon, null, Modifier.size(14.dp),
                                tint = if (selected) cat.accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cat.accentColor.copy(alpha = 0.18f),
                            selectedLabelColor     = cat.accentColor,
                            selectedLeadingIconColor = cat.accentColor,
                        ),
                    )
                }
            }
            HorizontalDivider()
        }

        // ── Category content ──────────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!state.isGranted) {
                // Not connected — show connect options
                item { ConnectOptions(state, vm) }
                if (!state.isAvailable) item { HowToConnectCard() }
            } else {
                when (category) {
                    DeviceInfoCategory.OVERVIEW  -> overviewItems(state, info, vm)
                    DeviceInfoCategory.DEVICE    -> deviceItems(info)
                    DeviceInfoCategory.HARDWARE  -> hardwareItems(info)
                    DeviceInfoCategory.BATTERY   -> batteryItems(info)
                    DeviceInfoCategory.STORAGE   -> storageItems(info)
                    DeviceInfoCategory.NETWORK   -> networkItems(state, info)
                    DeviceInfoCategory.SYSTEM    -> systemItems(info)
                    DeviceInfoCategory.SECURITY  -> securityItems(info)
                }
            }
        }
    }
}

// ── Connection Banner ─────────────────────────────────────────────────────────

@Composable
private fun ConnectionBanner(state: ShizukuUiState, vm: ShizukuViewModel) {
    val info = state.targetInfo
    val isConnected = state.isGranted
    val accentColor = when {
        state.isLoading           -> MaterialTheme.colorScheme.outline
        isConnected               -> AccentGreen
        state.isAvailable         -> Color(0xFF7C3AED)
        else                      -> AccentRed
    }

    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val gradient = Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.12f), Color.Transparent),
                    startY = 0f, endY = size.height,
                )
                drawRect(gradient)
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Animated status dot
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp, color = accentColor)
                    isConnected     -> Icon(Icons.Filled.CheckCircle, null, Modifier.size(30.dp), tint = AccentGreen)
                    state.isAvailable -> Icon(Icons.Outlined.Lock,    null, Modifier.size(28.dp), tint = Color(0xFF7C3AED))
                    else            -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(28.dp), tint = AccentRed)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        state.isLoading   -> "Checking…"
                        isConnected       -> if (info.model.isNotEmpty()) info.model else "ACCU Connected"
                        state.isAvailable -> "Connecting…"
                        else              -> "Not Connected"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        isConnected -> buildString {
                            if (info.manufacturer.isNotEmpty()) append("${info.manufacturer} · ")
                            append(state.serverStartMethod)
                        }
                        state.isAvailable -> "Establishing privilege connection…"
                        else -> "Set up Wireless ADB or use Root"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isConnected && info.androidVersion.isNotEmpty()) {
                    Text(
                        "Android ${info.androidVersion} (SDK ${info.sdkLevel}) · uid=${state.uid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor.copy(alpha = 0.85f),
                    )
                }
            }
            // Quick action icons
            if (isConnected) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = vm::refreshDeviceInfo, modifier = Modifier.size(36.dp)) {
                        if (info.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                    }
                    IconButton(onClick = vm::stopServer, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp), tint = AccentRed)
                    }
                }
            }
        }
        // Thin accent line at bottom
        Box(Modifier.fillMaxWidth().height(1.5.dp).align(Alignment.BottomStart).background(accentColor.copy(alpha = 0.4f)))
    }
}

// ── Overview items ────────────────────────────────────────────────────────────

private fun LazyListScope.overviewItems(state: ShizukuUiState, info: TargetDeviceInfo, vm: ShizukuViewModel) {
    if (info.isLoading) {
        item {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Fetching device info…", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A90D9))
                }
            }
        }
        return
    }

    // Quick stat cards
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.BatteryFull,
                label = "Battery",
                value = if (info.batteryLevel >= 0) "${info.batteryLevel}%" else "—",
                accent = when {
                    info.batteryLevel < 20 -> AccentRed
                    info.batteryLevel < 50 -> AccentOrange
                    else -> AccentGreen
                },
                sub = info.batteryStatus,
            )
            QuickStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Memory,
                label = "RAM",
                value = if (info.totalRamMb > 0) "${info.totalRamMb / 1024}GB" else "—",
                accent = Color(0xFF00BCD4),
                sub = if (info.availRamMb > 0) "${info.availRamMb}MB free" else "",
            )
            QuickStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Storage,
                label = "Storage",
                value = if (info.totalStorageMb > 0) "${"%.0f".format(info.totalStorageMb / 1024f)}GB" else "—",
                accent = Color(0xFFFF8A65),
                sub = if (info.availStorageMb > 0) "${"%.0f".format(info.availStorageMb / 1024f)}GB free" else "",
            )
        }
    }

    // Server details card
    item {
        DeviceInfoCard("ACCU Server", Icons.Outlined.Hub, Color(0xFF4A90D9)) {
            InfoRow2("Version",     "v${state.version} patch ${state.patchVersion}", Icons.Outlined.Info)
            InfoRow2("UID",         when (state.uid) { 0 -> "0 (root)" ; 2000 -> "2000 (adb)" ; else -> "${state.uid}" }, Icons.Outlined.Person)
            InfoRow2("Start method", state.serverStartMethod, Icons.Outlined.PlayCircle)
            InfoRow2("Permission",  if (state.permissionGranted) "Passed ✓" else "Failed", Icons.Outlined.VerifiedUser,
                if (state.permissionGranted) AccentGreen else AccentRed)
        }
    }

    // Target overview
    if (info.model.isNotEmpty()) {
        item {
            DeviceInfoCard("Target Device", Icons.Outlined.PhoneAndroid, Color(0xFF7C83FF)) {
                InfoRow2("Model",        "${info.manufacturer} ${info.model}", Icons.Outlined.PhoneAndroid)
                InfoRow2("Android",      "Android ${info.androidVersion} (SDK ${info.sdkLevel})", Icons.Outlined.Android)
                InfoRow2("CPU",          if (info.cpuCores > 0) "${info.cpuCores}-core ${info.cpuAbi}" else info.cpuAbi, Icons.Outlined.DeveloperBoard)
                InfoRow2("IP Address",   info.deviceIpAddress.ifEmpty { state.deviceIp }, Icons.Outlined.Language)
            }
        }
    }

    // Action buttons
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::restartServer, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Restart", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = vm::runDiagnostics, modifier = Modifier.weight(1f)) {
                if (state.diagnosticsRunning) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.BugReport, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Diagnostics", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Device info items ─────────────────────────────────────────────────────────

private fun LazyListScope.deviceItems(info: TargetDeviceInfo) {
    item {
        DeviceInfoCard("Identity", Icons.Outlined.Badge, Color(0xFF7C83FF)) {
            InfoRow2("Model",        info.model,           Icons.Outlined.PhoneAndroid)
            InfoRow2("Manufacturer", info.manufacturer,    Icons.Outlined.Business)
            InfoRow2("Brand",        info.brand,           Icons.Outlined.Label)
            InfoRow2("Codename",     info.codename,        Icons.Outlined.Code)
            InfoRow2("Serial No.",   info.serial,          Icons.Outlined.Pin)
        }
    }
    item {
        DeviceInfoCard("Display", Icons.Outlined.Tv, Color(0xFF9C27B0)) {
            InfoRow2("Resolution",  if (info.displayWidth > 0) "${info.displayWidth} × ${info.displayHeight}" else "—", Icons.Outlined.AspectRatio)
            InfoRow2("Density",     if (info.displayDensityDpi > 0) "${info.displayDensityDpi} dpi" else "—", Icons.Outlined.Tune)
        }
    }
}

// ── Hardware items ────────────────────────────────────────────────────────────

private fun LazyListScope.hardwareItems(info: TargetDeviceInfo) {
    item {
        DeviceInfoCard("Processor", Icons.Outlined.Memory, Color(0xFF00BCD4)) {
            InfoRow2("Architecture", info.cpuAbi,            Icons.Outlined.DeveloperBoard)
            InfoRow2("Cores",       if (info.cpuCores > 0) "${info.cpuCores} cores" else "—", Icons.Outlined.GridOn)
            InfoRow2("Max Freq",    if (info.cpuMaxFreqMhz > 0) "${info.cpuMaxFreqMhz} MHz" else "—", Icons.Outlined.Speed)
            InfoRow2("Governor",    info.cpuGovernor,         Icons.Outlined.Settings)
        }
    }
    item {
        val usedRam = (info.totalRamMb - info.availRamMb).coerceAtLeast(0)
        val ramPct  = if (info.totalRamMb > 0) usedRam.toFloat() / info.totalRamMb else 0f

        DeviceInfoCard("Memory (RAM)", Icons.Outlined.Memory, Color(0xFF00ACC1)) {
            InfoRow2("Total",     if (info.totalRamMb > 0) "${"%.1f".format(info.totalRamMb / 1024f)} GB" else "—", Icons.Outlined.Memory)
            InfoRow2("Available", if (info.availRamMb > 0) "${info.availRamMb} MB"  else "—", Icons.Outlined.CheckCircle, AccentGreen)
            InfoRow2("Used",      if (usedRam > 0) "${usedRam} MB" else "—",          Icons.Outlined.PieChart)
            InfoRow2("Heap limit", info.javaHeap,              Icons.Outlined.Code)
            if (info.totalRamMb > 0) {
                Spacer(Modifier.height(4.dp))
                GradientProgressBar(ramPct, Color(0xFF00BCD4))
                Text("${(ramPct * 100).roundToInt()}% used", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    item {
        DeviceInfoCard("Display", Icons.Outlined.Tv, Color(0xFF9C27B0)) {
            InfoRow2("Resolution",  if (info.displayWidth > 0) "${info.displayWidth} × ${info.displayHeight} px" else "—", Icons.Outlined.AspectRatio)
            InfoRow2("Density",     if (info.displayDensityDpi > 0) "${info.displayDensityDpi} dpi" else "—", Icons.Outlined.Tune)
        }
    }
}

// ── Battery items ─────────────────────────────────────────────────────────────

private fun LazyListScope.batteryItems(info: TargetDeviceInfo) {
    item {
        val batColor = when {
            info.batteryLevel < 20 -> AccentRed
            info.batteryLevel < 50 -> AccentOrange
            else                   -> AccentGreen
        }

        DeviceInfoCard("Battery", Icons.Outlined.BatteryFull, batColor) {
            // Big level display
            if (info.batteryLevel >= 0) {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${info.batteryLevel}%",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = batColor,
                        )
                        Text(info.batteryStatus, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                GradientProgressBar(info.batteryLevel / 100f, batColor)
                Spacer(Modifier.height(8.dp))
            }
            InfoRow2("Health",      info.batteryHealth,       Icons.Outlined.Favorite,
                if (info.batteryHealth == "Good") AccentGreen else AccentOrange)
            InfoRow2("Temperature", if (info.batteryTempC > 0) "${"%.1f".format(info.batteryTempC)}°C" else "—",
                Icons.Outlined.Whatshot, if (info.batteryTempC > 40) AccentRed else Color.Unspecified)
            InfoRow2("Voltage",     if (info.batteryVoltage > 0) "${info.batteryVoltage} mV" else "—", Icons.Outlined.FlashOn)
            InfoRow2("Technology",  info.batteryTechnology,   Icons.Outlined.Layers)
            InfoRow2("Plug type",   info.batteryPlugged,      Icons.Outlined.Power)
        }
    }
}

// ── Storage items ─────────────────────────────────────────────────────────────

private fun LazyListScope.storageItems(info: TargetDeviceInfo) {
    item {
        val usedSto = (info.totalStorageMb - info.availStorageMb).coerceAtLeast(0)
        val stoPct  = if (info.totalStorageMb > 0) usedSto.toFloat() / info.totalStorageMb else 0f

        DeviceInfoCard("Internal Storage", Icons.Outlined.Storage, Color(0xFFFF8A65)) {
            InfoRow2("Total",    if (info.totalStorageMb > 0) "${"%.1f".format(info.totalStorageMb / 1024f)} GB" else "—", Icons.Outlined.Storage)
            InfoRow2("Free",     if (info.availStorageMb > 0) "${"%.1f".format(info.availStorageMb / 1024f)} GB" else "—", Icons.Outlined.CheckCircle, AccentGreen)
            InfoRow2("Used",     if (usedSto > 0) "${"%.1f".format(usedSto / 1024f)} GB" else "—", Icons.Outlined.PieChart)
            if (info.totalStorageMb > 0) {
                Spacer(Modifier.height(4.dp))
                GradientProgressBar(stoPct, if (stoPct > 0.9f) AccentRed else Color(0xFFFF8A65))
                Text(
                    "${"%.0f".format(stoPct * 100)}% used · ${"%.1f".format(info.availStorageMb / 1024f)} GB free",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ── Network items ─────────────────────────────────────────────────────────────

private fun LazyListScope.networkItems(state: ShizukuUiState, info: TargetDeviceInfo) {
    item {
        DeviceInfoCard("Connection", Icons.Outlined.Wifi, Color(0xFF26C6DA)) {
            InfoRow2("IP Address",  info.deviceIpAddress.ifEmpty { state.deviceIp }, Icons.Outlined.Language)
            InfoRow2("ADB method",  state.serverStartMethod, Icons.Outlined.Usb)
            InfoRow2("ADB state",
                if (state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS) "Wireless ADB ✓"
                else if (state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_OTG) "USB ADB ✓"
                else "Connected",
                Icons.Outlined.Wifi,
                AccentGreen)
        }
    }
    if (info.wifiSsid.isNotEmpty()) {
        item {
            DeviceInfoCard("Wi-Fi", Icons.Outlined.Wifi, Color(0xFF4FC3F7)) {
                InfoRow2("Network (SSID)", info.wifiSsid.take(32), Icons.Outlined.NetworkWifi)
            }
        }
    }
    if (info.mobileOperator.isNotEmpty()) {
        item {
            DeviceInfoCard("Mobile", Icons.Outlined.SignalCellularAlt, Color(0xFF4CAF50)) {
                InfoRow2("Operator",     info.mobileOperator,     Icons.Outlined.Business)
                InfoRow2("Network type", info.mobileNetworkType,  Icons.Outlined.SignalCellularAlt)
            }
        }
    }
}

// ── System items ──────────────────────────────────────────────────────────────

private fun LazyListScope.systemItems(info: TargetDeviceInfo) {
    item {
        DeviceInfoCard("Android", Icons.Outlined.Android, Color(0xFFAB47BC)) {
            InfoRow2("Version",     "Android ${info.androidVersion}",   Icons.Outlined.Android)
            InfoRow2("API Level",   "SDK ${info.sdkLevel}",             Icons.Outlined.Code)
            InfoRow2("Build Type",  info.buildType,                     Icons.Outlined.Build)
            InfoRow2("Build Date",  info.buildDate,                     Icons.Outlined.CalendarToday)
            InfoRow2("Fingerprint", info.buildFingerprint,              Icons.Outlined.Fingerprint)
        }
    }
    item {
        DeviceInfoCard("Runtime", Icons.Outlined.Terminal, Color(0xFF7E57C2)) {
            InfoRow2("Kernel",   info.kernelVersion,  Icons.Outlined.DeveloperMode)
            InfoRow2("Uptime",   formatUptime(info.uptimeSecs), Icons.Outlined.Timer)
            InfoRow2("Java Heap", info.javaHeap,      Icons.Outlined.Code)
            InfoRow2("Locale",   info.locale,         Icons.Outlined.Language)
            InfoRow2("Timezone", info.timezone,       Icons.Outlined.Schedule)
        }
    }
}

// ── Security items ────────────────────────────────────────────────────────────

private fun LazyListScope.securityItems(info: TargetDeviceInfo) {
    item {
        DeviceInfoCard("Security", Icons.Outlined.Security, Color(0xFFEF5350)) {
            InfoRow2("Encryption",  info.encryptionState.replaceFirstChar { it.uppercase() },
                Icons.Outlined.Lock,
                if (info.encryptionState.lowercase() == "encrypted") AccentGreen else AccentOrange)
            InfoRow2("SELinux",     info.selinuxEnforce,
                Icons.Outlined.Shield,
                if (info.selinuxEnforce.lowercase() == "enforcing") AccentGreen else AccentOrange)
            InfoRow2("Bootloader",  info.bootloaderState.replaceFirstChar { it.uppercase() },
                Icons.Outlined.VerifiedUser,
                if (info.bootloaderState.lowercase() in listOf("green", "verified")) AccentGreen else AccentOrange)
            InfoRow2("ACCU UID",    when (0) { 0 -> "uid=0 (root)" ; else -> "uid (adb)" }, Icons.Outlined.Person)
        }
    }
}

// ── Connect options (shown when not connected) ─────────────────────────────────

@Composable
private fun ConnectOptions(state: ShizukuUiState, vm: ShizukuViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.isGranted && state.isAvailable) {
            Button(onClick = vm::requestPermission, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Key, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Grant Permission")
            }
        }
        if (!state.isAvailable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::startWithAdb, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Wifi, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Wireless ADB")
                }
                OutlinedButton(onClick = vm::connectOtg, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Usb, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("OTG / USB")
                }
            }
            if (state.isRootAvailable) {
                OutlinedButton(onClick = vm::startWithRoot, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use Root")
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::restartServer, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.RestartAlt, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Restart")
                }
                OutlinedButton(onClick = vm::stopServer, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)) {
                    Icon(Icons.Outlined.Stop, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Stop", color = AccentRed)
                }
            }
        }
    }
}

@Composable
private fun HowToConnectCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("How to Connect", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HowToStep(1, "Wireless ADB (recommended)", "Developer Options → Wireless debugging → Pair device")
            HowToStep(2, "Enter the pairing code", "Tap 'Wireless ADB' above, then enter the 6-digit code")
            HowToStep(3, "Or use Root", "Tap 'Use Root' if device is rooted — no PC needed")
        }
    }
}

// ── UI Components ─────────────────────────────────────────────────────────────

@Composable
private fun QuickStatCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    sub: String = "",
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeviceInfoCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier
                .drawBehind {
                    val glow = Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.08f), Color.Transparent),
                        startX = 0f, endX = size.width * 0.6f,
                    )
                    drawRect(glow)
                }
                .padding(14.dp),
        ) {
            // Card header
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(17.dp), tint = accent)
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = accent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow2(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color = Color.Unspecified,
) {
    val clipboard = LocalClipboardManager.current
    if (value.isBlank() || value == "—") return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp),
            tint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { clipboard.setText(AnnotatedString(value)) },
            modifier = Modifier.size(22.dp),
        ) {
            Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
        }
    }
}

@Composable
private fun GradientProgressBar(progress: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50)),
    ) {
        Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.18f)))
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(color.copy(0.7f), color)),
                )
        )
    }
}

private fun formatUptime(secs: Long): String {
    if (secs <= 0) return "—"
    val d = secs / 86400
    val h = (secs % 86400) / 3600
    val m = (secs % 3600) / 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (h > 0) append("${h}h ")
        append("${m}m")
    }.trim()
}

@Composable
private fun InfoRow(label: String, value: String, icon: ImageVector, tint: Color = Color.Unspecified) {
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { clipboard.setText(AnnotatedString(value)) },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

@Composable
private fun HowToStep(step: Int, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text("$step", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

// ── Tab 2: Authorized Apps ────────────────────────────────────────────────────

@Composable
private fun AuthorizedAppsTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    val filteredApps = remember(state.authorizedApps, state.authorizedAppsFilter, state.authorizedAppsSearch) {
        state.authorizedApps
            .filter { app ->
                when (state.authorizedAppsFilter) {
                    AppsFilter.GRANTED -> app.isGranted
                    AppsFilter.DENIED -> !app.isGranted
                    AppsFilter.ALL -> true
                }
            }
            .filter { app ->
                state.authorizedAppsSearch.isBlank() ||
                        app.appName.contains(state.authorizedAppsSearch, ignoreCase = true) ||
                        app.packageName.contains(state.authorizedAppsSearch, ignoreCase = true)
            }
    }

    Column(Modifier.fillMaxSize()) {
        // Search + actions bar
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.authorizedAppsSearch,
                onValueChange = vm::setAppsSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                trailingIcon = { if (state.authorizedAppsSearch.isNotEmpty()) IconButton(onClick = { vm.setAppsSearch("") }) { Icon(Icons.Outlined.Clear, null, Modifier.size(18.dp)) } },
                singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppsFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.authorizedAppsFilter == filter,
                        onClick = { vm.setAppsFilter(filter) },
                        label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = vm::loadApps) { Icon(Icons.Outlined.Refresh, "Refresh", Modifier.size(20.dp)) }
                if (state.authorizedApps.any { it.isGranted }) {
                    IconButton(onClick = vm::revokeAll) { Icon(Icons.Outlined.RemoveCircle, "Revoke all", Modifier.size(20.dp), tint = AccentRed) }
                }
            }
        }
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (!state.isAvailable || !state.isGranted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Shizuku permission required", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.AppsOutage, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.authorizedApps.isEmpty()) {
                        TextButton(onClick = vm::loadApps) { Text("Load apps") }
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text("${filteredApps.size} app(s) — ${state.authorizedApps.count { it.isGranted }} granted",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                }
                items(filteredApps, key = { it.packageName }) { app ->
                    AuthorizedAppCard(app, onGrant = { vm.grantApp(app) }, onRevoke = { vm.revokeApp(app) })
                }
            }
        }
    }
}

@Composable
private fun AuthorizedAppCard(app: AuthorizedApp, onGrant: () -> Unit, onRevoke: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(
                if (app.isGranted) AccentGreen.copy(0.15f) else AccentRed.copy(0.1f)
            ), contentAlignment = Alignment.Center) {
                Icon(
                    if (app.isGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                    null, tint = if (app.isGranted) AccentGreen else AccentRed, modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (app.isSystemApp) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("SYS", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.versionName.isNotEmpty()) Text("v${app.versionName} • uid ${app.uid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), fontSize = 10.sp)
            }
            if (app.isGranted) {
                OutlinedButton(onClick = onRevoke, colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed), modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Revoke", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(onClick = onGrant, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Grant", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Tab 5: Rish Shell ─────────────────────────────────────────────────────────

@Composable
private fun RishTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { vm.loadRishInfo() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Text("Rish Shell", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                    Text("ADB shell uid=2000", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // What is rish
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What is rish?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "rish is an interactive shell provided by Shizuku that runs with ADB-level privileges (uid=2000). " +
                                "Unlike regular terminals, rish can execute commands that require ADB shell access — including pm, am, wm, settings, dumpsys and more — " +
                                "without needing a computer connected.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.rishInfo.isAvailable) {
                        Surface(color = AccentGreen.copy(0.1f), shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                                Text("rish is available at ${state.rishInfo.path}", style = MaterialTheme.typography.bodySmall, color = AccentGreen)
                            }
                        }
                    }
                }
            }
        }

        // Setup steps
        item {
            Text("Setup in Termux", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val steps = listOf(
                        "Open Shizuku Manager app" to "Tap 'Use Shizuku in terminal apps'",
                        "Find the exported rish binary" to "Check /sdcard/Download/ or Shizuku manager",
                        "Copy to Termux home" to "cp /sdcard/rish ~/rish && cp /sdcard/rish_sh ~/rish_sh",
                        "Make them executable" to "chmod +x ~/rish ~/rish_sh",
                        "Launch rish shell" to "./rish",
                        "You get ADB privileges" to "uid=2000 (shell) — run pm, am, wm, settings freely",
                    )
                    steps.forEachIndexed { i, (title, cmd) ->
                        RishStep(i + 1, title, cmd, onCopy = { clipboard.setText(AnnotatedString(cmd)) })
                    }
                }
            }
        }

        // Quick commands
        item {
            Text("Quick Command Reference", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        val rishCmds = listOf(
            "pm list packages -3" to "List user apps",
            "pm uninstall --user 0 <pkg>" to "Remove bloatware (no root)",
            "pm disable-user --user 0 <pkg>" to "Disable app for user",
            "pm clear <pkg>" to "Clear app data",
            "pm grant <pkg> <perm>" to "Grant permission",
            "am force-stop <pkg>" to "Force stop app",
            "am start -n <pkg>/<activity>" to "Launch specific activity",
            "settings put global adb_wifi_enabled 1" to "Enable wireless ADB",
            "wm density 420" to "Set display density",
            "wm density reset" to "Reset density",
            "wm size 1080x1920" to "Set display size",
            "svc wifi enable" to "Enable WiFi",
            "svc data disable" to "Disable mobile data",
            "dumpsys battery" to "Battery info",
            "dumpsys meminfo" to "Memory info",
            "input tap 540 960" to "Tap screen center",
            "input keyevent 26" to "Power button",
            "screencap -p /sdcard/ss.png" to "Screenshot",
        )

        items(rishCmds) { (cmd, desc) ->
            RishCommandRow(cmd, desc, onCopy = { clipboard.setText(AnnotatedString(cmd)) })
        }
    }
}

@Composable
private fun RishStep(step: Int, title: String, cmd: String, onCopy: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("$step", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(cmd, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), fontSize = 11.sp)
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(14.dp)) }
            }
        }
    }
}

@Composable
private fun RishCommandRow(cmd: String, desc: String, onCopy: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Tab 6: Settings ───────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("App Behavior", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    ShizukuSettingRow(
                        title = "Auto-start on boot",
                        subtitle = "Automatically start Shizuku server when device boots (root required)",
                        icon = Icons.Outlined.PowerSettingsNew,
                        checked = state.autoStartOnBoot,
                        onToggle = vm::setAutoStartOnBoot,
                        enabled = state.isRootAvailable,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ShizukuSettingRow(
                        title = "Show notification",
                        subtitle = "Keep a persistent notification while Shizuku is running",
                        icon = Icons.Outlined.Notifications,
                        checked = state.showNotification,
                        onToggle = vm::setShowNotification,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ShizukuSettingRow(
                        title = "Require unlock for tiles",
                        subtitle = "Only execute Quick Settings tiles after device is unlocked",
                        icon = Icons.Outlined.LockClock,
                        checked = state.requireUnlockForTiles,
                        onToggle = vm::setRequireUnlockForTiles,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)); Text("Appearance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    ShizukuSettingRow(
                        title = "Black night mode",
                        subtitle = "Use pure black (#000000) background in dark mode",
                        icon = Icons.Outlined.DarkMode,
                        checked = state.blackNightMode,
                        onToggle = vm::setBlackNightMode,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ShizukuSettingRow(
                        title = "Use system colors",
                        subtitle = "Apply Material You dynamic color scheme from wallpaper",
                        icon = Icons.Outlined.ColorLens,
                        checked = state.useSystemColors,
                        onToggle = vm::setUseSystemColors,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)); Text("Advanced", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::runDiagnostics, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.BugReport, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Diagnostics")
                        }
                        OutlinedButton(onClick = vm::revokeAll, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed), enabled = state.authorizedApps.any { it.isGranted }) {
                            Icon(Icons.Outlined.RemoveCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Revoke All")
                        }
                    }
                    val clipboard = LocalClipboardManager.current
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.exportLogs())) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy All Logs to Clipboard")
                    }
                }
            }
        }

        // Server info summary
        if (state.isAvailable) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Server Info", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("v${state.version} patch:${state.patchVersion} • uid=${state.uid} • pid=${state.serverPid}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.seLinuxContext.isNotEmpty()) Text("SELinux: ${state.seLinuxContext}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShizukuSettingRow(
    title: String, subtitle: String,
    icon: ImageVector, checked: Boolean,
    onToggle: (Boolean) -> Unit, enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (enabled) 1f else 0.4f))
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

// ── Tab 7: Logs ───────────────────────────────────────────────────────────────

@Composable
private fun LogsTab(state: ShizukuUiState, vm: ShizukuViewModel) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val filteredLogs = remember(state.logs, state.logFilter) {
        if (state.logFilter == null) state.logs else state.logs.filter { it.level == state.logFilter }
    }
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(filteredLogs.size) {
        if (autoScroll && filteredLogs.isNotEmpty()) listState.animateScrollToItem(filteredLogs.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Controls
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${filteredLogs.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(2.dp))
            // Level filters
            FilterChip(selected = state.logFilter == null, onClick = { vm.setLogFilter(null) }, label = { Text("All", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
            FilterChip(selected = state.logFilter == LogLevel.ERROR, onClick = { vm.setLogFilter(if (state.logFilter == LogLevel.ERROR) null else LogLevel.ERROR) }, label = { Text("ERR", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
            FilterChip(selected = state.logFilter == LogLevel.WARNING, onClick = { vm.setLogFilter(if (state.logFilter == LogLevel.WARNING) null else LogLevel.WARNING) }, label = { Text("WARN", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
            FilterChip(selected = state.logFilter == LogLevel.SUCCESS, onClick = { vm.setLogFilter(if (state.logFilter == LogLevel.SUCCESS) null else LogLevel.SUCCESS) }, label = { Text("OK", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(vm.exportLogs())); }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy logs", Modifier.size(18.dp)) }
            IconButton(onClick = vm::clearLogs, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.DeleteSweep, "Clear", Modifier.size(18.dp)) }
        }
        HorizontalDivider()

        if (filteredLogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Article, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No logs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filteredLogs, key = { it.id }) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: ShizukuLogEntry) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Text(
            timeFmt.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), fontSize = 9.sp,
            modifier = Modifier.width(72.dp)
        )
        val (levelColor, levelTag) = when (entry.level) {
            LogLevel.SUCCESS -> AccentGreen to "OK "
            LogLevel.ERROR -> AccentRed to "ERR"
            LogLevel.WARNING -> AccentOrange to "WRN"
            LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) to "DBG"
            LogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f) to "VRB"
            LogLevel.INFO -> MaterialTheme.colorScheme.primary to "INF"
        }
        Text(levelTag, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = levelColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Text(entry.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = levelColor.copy(if (entry.level == LogLevel.VERBOSE || entry.level == LogLevel.DEBUG) 0.5f else 1f))
    }
}
