package com.accu.ui.shell

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class MdnsDevice(
    val id: String = UUID.randomUUID().toString(),
    val hostname: String,
    val ip: String,
    val port: Int,
    val transport: String = "mDNS",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
)

data class WifiAdbUiState(
    val wifiAdbEnabled: Boolean = false,
    val wifiAdbPort: Int = 5555,
    val pairingCode: String = "",
    val pairingHost: String = "",
    val pairingPort: String = "",
    val isScanning: Boolean = false,
    val mdnsDevices: List<MdnsDevice> = emptyList(),
    val isPairing: Boolean = false,
    val pairingStatus: String = "",
    val connectionState: AccuConnectionManager.ConnectionState = AccuConnectionManager.ConnectionState.DISCONNECTED,
    val deviceIp: String = "",
    val snackbarMessage: String? = null,
    val lastError: String? = null,
)

@HiltViewModel
class WifiAdbMdnsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: AccuConnectionManager,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {

    private val _state = MutableStateFlow(WifiAdbUiState())
    val state: StateFlow<WifiAdbUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.state.collect { connState ->
                _state.update { it.copy(connectionState = connState) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ip = connectionManager.getDeviceIp()
            val port = connectionManager.getLastConnectedPort()
            _state.update { it.copy(deviceIp = ip, wifiAdbPort = port) }
        }
    }

    fun toggleWifiAdb(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (enable) {
                val r1 = shizukuUtils.execShizuku("settings put global adb_wifi_enabled 1")
                val r2 = shizukuUtils.execShizuku("setprop service.adb.tcp.port 5555")
                val r3 = shizukuUtils.execShizuku("stop adbd"); val r4 = shizukuUtils.execShizuku("start adbd")
                val ok = r1.isSuccess || r2.isSuccess
                _state.update { it.copy(
                    wifiAdbEnabled = ok,
                    snackbarMessage = if (ok) "Wireless ADB enabled on port 5555" else "Failed — ${r1.error.take(60).ifBlank { "check ACCU connection" }}",
                    lastError = if (!ok) "${r1.error}\n${r2.error}".trim() else null,
                )}
            } else {
                shizukuUtils.execShizuku("settings put global adb_wifi_enabled 0")
                shizukuUtils.execShizuku("setprop service.adb.tcp.port -1")
                shizukuUtils.execShizuku("stop adbd"); shizukuUtils.execShizuku("start adbd")
                _state.update { it.copy(wifiAdbEnabled = false, snackbarMessage = "Wireless ADB disabled") }
            }
        }
    }

    /**
     * Manual pair: user enters IP, port, and 6-digit code.
     *
     * Privilege priority (same logic as AccuConnectionManager / aShell / Shizuku):
     *   1. Root active → already privileged, no pairing needed
     *   2. System adb binary found (/system/bin/adb, /system/xbin/adb) → run adb pair
     *   3. Otherwise → show copyable PC command; can't run `adb` from Android itself
     */
    fun pairManual() {
        val s = _state.value
        if (s.pairingHost.isBlank() || s.pairingPort.isBlank() || s.pairingCode.isBlank()) {
            _state.update { it.copy(snackbarMessage = "Fill in IP, port, and pairing code") }
            return
        }
        val port = s.pairingPort.toIntOrNull()
        if (port == null || port <= 0 || port > 65535) {
            _state.update { it.copy(snackbarMessage = "Invalid port number") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isPairing = true, pairingStatus = "Checking privilege…", lastError = null) }

            // Path 1: root already active
            if (shizukuUtils.isRootAvailable()) {
                _state.update { it.copy(
                    isPairing = false,
                    pairingStatus = "Root access active — all features enabled ✓",
                    snackbarMessage = "Root connected — no ADB pairing required",
                )}
                connectionManager.checkAndUpdateState()
                return@launch
            }

            // Path 2: system adb binary (some ROMs/rooted devices ship it)
            val adb = connectionManager.findAdbBinary()
            if (adb != null) {
                _state.update { it.copy(pairingStatus = "Pairing with ${s.pairingHost}:$port via $adb…") }
                val pairResult = connectionManager.execPlainShell("$adb pair ${s.pairingHost}:$port ${s.pairingCode}")
                val pairOk = pairResult.output.contains("Successfully", ignoreCase = true) || pairResult.exitCode == 0
                if (pairOk) {
                    val connectResult = connectionManager.execPlainShell("$adb connect ${s.pairingHost}:5555")
                    val connectOk = connectResult.combinedOutput.contains("connected", ignoreCase = true)
                    _state.update { it.copy(
                        isPairing = false,
                        pairingStatus = if (connectOk) "Paired and connected to ${s.pairingHost}:5555 ✓"
                                        else "Paired — run on PC: $adb connect ${s.pairingHost}:5555",
                        snackbarMessage = if (connectOk) "Connected to ${s.pairingHost}" else "Paired but connect failed",
                        lastError = if (!connectOk) connectResult.combinedOutput else null,
                    )}
                    if (connectOk) connectionManager.checkAndUpdateState()
                } else {
                    _state.update { it.copy(
                        isPairing = false,
                        pairingStatus = "Pairing failed",
                        snackbarMessage = "Pairing failed — check code",
                        lastError = pairResult.combinedOutput.take(200).ifBlank { "Pairing rejected — check code" },
                    )}
                }
                return@launch
            }

            // Path 3: no adb binary on device — show PC command (don't show error)
            val pcPair    = "adb pair ${s.pairingHost}:$port ${s.pairingCode}"
            val pcConnect = "adb connect ${s.pairingHost}:5555"
            _state.update { it.copy(
                isPairing = false,
                pairingStatus = "Run on your PC:\n$pcPair\nThen: $pcConnect",
                snackbarMessage = "PC command copied — run on PC to complete pairing",
            )}
        }
    }

    /** Auto-discovery via NsdManager (no `adb mdns services` — that requires the adb binary) */
    fun startMdnsScan() {
        _state.update { it.copy(isScanning = true, mdnsDevices = emptyList()) }
        connectionManager.startPairingDiscovery()
        // NsdManager discovery runs async; stop indicator after timeout
        viewModelScope.launch(Dispatchers.IO) {
            delay(4000)
            _state.update { it.copy(
                isScanning = false,
                snackbarMessage = if (_state.value.mdnsDevices.isEmpty())
                    "mDNS discovery running — will notify when device found"
                else null,
            )}
        }
    }

    fun stopMdnsScan() {
        connectionManager.stopPairingDiscovery()
        _state.update { it.copy(isScanning = false) }
    }

    fun connectMdnsDevice(device: MdnsDevice) {
        _state.update { s -> s.copy(mdnsDevices = s.mdnsDevices.map { if (it.id == device.id) it.copy(isConnecting = true) else it }) }
        viewModelScope.launch(Dispatchers.IO) {
            // Root → already privileged
            if (shizukuUtils.isRootAvailable()) {
                _state.update { s -> s.copy(
                    mdnsDevices = s.mdnsDevices.map { if (it.id == device.id) it.copy(isConnecting = false, isConnected = true) else it },
                    snackbarMessage = "Root access active — device connected ✓",
                )}
                connectionManager.checkAndUpdateState()
                return@launch
            }
            // System adb binary
            val adb = connectionManager.findAdbBinary()
            if (adb != null) {
                val result = connectionManager.execPlainShell("$adb connect ${device.ip}:${device.port}")
                val ok = result.combinedOutput.contains("connected", ignoreCase = true)
                _state.update { s -> s.copy(
                    mdnsDevices = s.mdnsDevices.map { if (it.id == device.id) it.copy(isConnecting = false, isConnected = ok) else it },
                    snackbarMessage = if (ok) "Connected to ${device.hostname}" else "Failed: ${result.combinedOutput.take(80)}",
                    lastError = if (!ok) result.combinedOutput else null,
                )}
                if (ok) connectionManager.checkAndUpdateState()
            } else {
                // No adb on device — show PC command
                val pcCmd = "adb connect ${device.ip}:${device.port}"
                _state.update { s -> s.copy(
                    mdnsDevices = s.mdnsDevices.map { if (it.id == device.id) it.copy(isConnecting = false) else it },
                    snackbarMessage = "Run on PC: $pcCmd",
                    pairingStatus = "Run on your PC:\n$pcCmd",
                )}
            }
        }
    }

    fun onPairingHostChange(v: String) { _state.update { it.copy(pairingHost = v) } }
    fun onPairingPortChange(v: String) { _state.update { it.copy(pairingPort = v) } }
    fun onPairingCodeChange(v: String) { _state.update { it.copy(pairingCode = v) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
    fun clearError() { _state.update { it.copy(lastError = null) } }

    private fun parseMdnsOutput(output: String): List<MdnsDevice> {
        return output.lines()
            .filter { it.isNotBlank() && !it.startsWith("List") && !it.startsWith("adb") }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val hostPort = parts.last()
                    val hp = hostPort.split(":")
                    val ip = hp.getOrElse(0) { "" }
                    val port = hp.getOrElse(1) { "5555" }.toIntOrNull() ?: 5555
                    val name = parts.getOrElse(0) { "device" }
                    if (ip.isNotBlank()) MdnsDevice(hostname = "$name.local", ip = ip, port = port, transport = parts.getOrElse(1) { "mDNS" })
                    else null
                } else null
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAdbMdnsScreen(
    onBack: () -> Unit,
    onLaunchShell: () -> Unit = {},
    viewModel: WifiAdbMdnsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Wireless ADB") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Connection status ──────────────────────────────────────────────
            val isConnected = state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS ||
                              state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_ROOT ||
                              state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_OTG
            if (isConnected) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("ACCU Connected", fontWeight = FontWeight.Bold)
                                Text(
                                    when (state.connectionState) {
                                        AccuConnectionManager.ConnectionState.CONNECTED_ROOT     -> "Root access active"
                                        AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS -> "Wireless ADB — ${state.deviceIp}:${state.wifiAdbPort}"
                                        AccuConnectionManager.ConnectionState.CONNECTED_OTG      -> "USB/OTG ADB active"
                                        else -> "Connected"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // ── Wireless ADB toggle ────────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.wifiAdbEnabled) MaterialTheme.colorScheme.primaryContainer
                                         else MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, null, modifier = Modifier.size(28.dp),
                                tint = if (state.wifiAdbEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wireless ADB", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.wifiAdbEnabled) "Listening on port ${state.wifiAdbPort}" else "Requires ACCU connection to enable",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = state.wifiAdbEnabled, onCheckedChange = { viewModel.toggleWifiAdb(it) })
                        }
                        if (state.wifiAdbEnabled && state.deviceIp.isNotBlank()) {
                            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SelectionContainer {
                                        Text("adb connect ${state.deviceIp}:${state.wifiAdbPort}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString("adb connect ${state.deviceIp}:${state.wifiAdbPort}")) },
                                        modifier = Modifier.size(28.dp),
                                    ) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)) }
                                }
                            }
                        }
                    }
                }
            }

            // ── Manual pair ────────────────────────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pair New Device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "On the device: Settings → Developer Options → Wireless Debugging → Pair device with pairing code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = state.pairingHost,
                            onValueChange = viewModel::onPairingHostChange,
                            label = { Text("IP Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Wifi, null, Modifier.size(18.dp)) },
                        )
                        OutlinedTextField(
                            value = state.pairingPort,
                            onValueChange = viewModel::onPairingPortChange,
                            label = { Text("Pairing Port (shown on device)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.NetworkWifi, null, Modifier.size(18.dp)) },
                        )
                        OutlinedTextField(
                            value = state.pairingCode,
                            onValueChange = viewModel::onPairingCodeChange,
                            label = { Text("6-digit Pairing Code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Pin, null, Modifier.size(18.dp)) },
                        )

                        if (state.pairingStatus.isNotBlank()) {
                            Text(
                                state.pairingStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.pairingStatus.contains("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }

                        // Show detailed error if available
                        state.lastError?.let { err ->
                            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Error, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Error Detail", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.weight(1f))
                                        IconButton(onClick = viewModel::clearError, Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) }
                                    }
                                    SelectionContainer {
                                        Text(err, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = viewModel::pairManual,
                            enabled = state.pairingHost.isNotBlank() && state.pairingPort.isNotBlank() && state.pairingCode.isNotBlank() && !state.isPairing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isPairing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Pairing…")
                            } else {
                                Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pair & Connect")
                            }
                        }
                    }
                }
            }

            // ── mDNS Discovery ─────────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("mDNS Discovery", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (state.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = viewModel::stopMdnsScan) { Text("Stop") }
                    } else {
                        TextButton(onClick = viewModel::startMdnsScan) {
                            Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Scan")
                        }
                    }
                }
            }

            if (state.mdnsDevices.isEmpty() && !state.isScanning) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DevicesOther, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))
                                Text("No devices found", color = MaterialTheme.colorScheme.outline)
                                Text("Tap Scan to discover devices via mDNS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            items(state.mdnsDevices, key = { it.id }) { device ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (device.isConnected) MaterialTheme.colorScheme.primaryContainer
                                         else MaterialTheme.colorScheme.surfaceContainer
                    ),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (device.isConnected) Icons.Default.CheckCircle else Icons.Default.PhoneAndroid,
                            null,
                            tint = if (device.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.hostname, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${device.ip}:${device.port}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(device.transport, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (device.isConnecting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Button(onClick = { viewModel.connectMdnsDevice(device) }, enabled = !device.isConnected) {
                                Text(if (device.isConnected) "Connected" else "Connect")
                            }
                        }
                    }
                }
            }

            // ── OTG bridge ────────────────────────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("OTG / USB to Wi-Fi Bridge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Connect an OTG USB cable and use the shell to relay ADB commands between USB and network interfaces.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { onLaunchShell() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Usb, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Launch OTG Shell")
                        }
                    }
                }
            }
        }
    }
}

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = calculateTopPadding() + other.calculateTopPadding(),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)
