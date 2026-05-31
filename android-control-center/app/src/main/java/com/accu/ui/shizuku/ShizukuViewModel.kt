package com.accu.ui.shizuku

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ShizukuUiState(
    val isAvailable: Boolean = false,
    val isGranted: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isInstalled: Boolean = true,
    val version: Int = 1,
    val patchVersion: Int = 0,
    val uid: Int = -1,
    val seLinuxContext: String = "",
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val deviceIp: String = "",
    val wirelessAdbPort: Int = 5555,
    val isWirelessAdbEnabled: Boolean = false,
    val isPairing: Boolean = false,
    val pairingStatus: String = "",
    val serverPid: Int = -1,
    val serverStartMethod: String = "",
    val logs: List<ShizukuLogEntry> = emptyList(),
    val logFilter: LogLevel? = null,
    val authorizedApps: List<AuthorizedApp> = emptyList(),
    val authorizedAppsFilter: AppsFilter = AppsFilter.ALL,
    val authorizedAppsSearch: String = "",
    val mdnsServices: List<MdnsService> = emptyList(),
    val isScanning: Boolean = false,
    val rishInfo: RishInfo = RishInfo(),
    val blackNightMode: Boolean = false,
    val useSystemColors: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val showNotification: Boolean = true,
    val requireUnlockForTiles: Boolean = false,
    val connectedAdbDevices: List<AdbDevice> = emptyList(),
    val diagnosticsRunning: Boolean = false,
    val connectionState: AccuConnectionManager.ConnectionState = AccuConnectionManager.ConnectionState.DISCONNECTED,
    val discoveredPairingIp: String = "",
    val discoveredPairingPort: Int = 0,
)

data class ShizukuLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val level: LogLevel = LogLevel.INFO,
)

enum class LogLevel { VERBOSE, DEBUG, INFO, SUCCESS, WARNING, ERROR }
enum class AppsFilter { ALL, GRANTED, DENIED }

data class AuthorizedApp(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val versionName: String = "",
    val isGranted: Boolean = true,
    val isSystemApp: Boolean = false,
)

data class MdnsService(
    val serviceName: String,
    val host: String,
    val port: Int,
    val type: String,
    val isConnecting: Boolean = false,
)

data class RishInfo(
    val isAvailable: Boolean = false,
    val version: String = "",
    val path: String = "",
)

data class AdbDevice(
    val serial: String,
    val state: String,
    val model: String = "",
)

@HiltViewModel
class ShizukuViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuUtils: ShizukuUtils,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ShizukuUiState())
    val state: StateFlow<ShizukuUiState> = _state.asStateFlow()

    init {
        // Observe AccuConnectionManager state changes
        viewModelScope.launch {
            connectionManager.state.collect { connState ->
                val isConnected = connState == AccuConnectionManager.ConnectionState.CONNECTED_ROOT
                        || connState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS
                        || connState == AccuConnectionManager.ConnectionState.CONNECTED_OTG
                _state.update {
                    it.copy(
                        connectionState = connState,
                        isAvailable = connState != AccuConnectionManager.ConnectionState.DISCONNECTED,
                        isGranted = isConnected,
                    )
                }
                if (connState == AccuConnectionManager.ConnectionState.DISCONNECTED) {
                    addLog("ACCU connection lost", LogLevel.WARNING)
                } else if (isConnected) {
                    addLog("ACCU connected — ${connState.name}", LogLevel.SUCCESS)
                }
            }
        }
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            connectionManager.checkAndUpdateState()
            val connState = connectionManager.state.value
            val isRoot = shizukuUtils.isRootAvailable()
            val isConnected = connState == AccuConnectionManager.ConnectionState.CONNECTED_ROOT
                    || connState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS
                    || connState == AccuConnectionManager.ConnectionState.CONNECTED_OTG
            val deviceIp = connectionManager.getDeviceIp()
            val lastIp = connectionManager.getLastConnectedIp()

            val wirelessEnabled = if (isConnected) {
                try {
                    shizukuUtils.execShizuku("settings get global adb_wifi_enabled").output.trim() == "1"
                } catch (_: Exception) { false }
            } else false

            val apps = if (isConnected) loadAuthorizedApps() else _state.value.authorizedApps

            _state.update {
                it.copy(
                    connectionState = connState,
                    isAvailable = connState != AccuConnectionManager.ConnectionState.DISCONNECTED,
                    isGranted = isConnected,
                    isRootAvailable = isRoot,
                    isInstalled = true,
                    uid = if (isRoot) 0 else android.os.Process.myUid(),
                    deviceIp = deviceIp,
                    wirelessAdbPort = connectionManager.getLastConnectedPort(),
                    isWirelessAdbEnabled = wirelessEnabled,
                    serverStartMethod = when {
                        isRoot -> "Root"
                        connState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS -> "Wireless ADB ($lastIp)"
                        connState == AccuConnectionManager.ConnectionState.CONNECTED_OTG      -> "OTG / USB ADB"
                        else -> "Not connected"
                    },
                    authorizedApps = apps,
                    isLoading = false,
                )
            }

            if (isConnected) {
                addLog("ACCU privilege active — method: ${_state.value.serverStartMethod}", LogLevel.SUCCESS)
            } else {
                addLog("ACCU not connected — limited functionality", LogLevel.WARNING)
            }
        }
    }

    // ── Connection management (replaces Shizuku server control) ───────────────

    fun requestPermission() {
        addLog("Starting ACCU wireless pairing discovery…", LogLevel.INFO)
        connectionManager.startPairingDiscovery()
    }

    fun startWithAdb() {
        addLog("Starting wireless ADB pairing discovery…", LogLevel.INFO)
        connectionManager.startPairingDiscovery()
        _state.update { it.copy(isPairing = true, pairingStatus = "Scanning for Wireless Debugging service…") }
    }

    fun startWithRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Testing root access…", LogLevel.INFO)
            val result = shizukuUtils.execRoot("id")
            if (result.output.contains("uid=0")) {
                addLog("Root access confirmed ✓", LogLevel.SUCCESS)
                connectionManager.checkAndUpdateState()
                refresh()
            } else {
                addLog("Root not available: ${result.error.take(100)}", LogLevel.ERROR)
            }
        }
    }

    fun stopServer() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Disconnecting ACCU…", LogLevel.INFO)
            connectionManager.disconnect()
            addLog("ACCU disconnected", LogLevel.INFO)
            delay(300)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    /**
     * Connect via OTG / USB ADB.
     * Detects a USB-connected Android device and routes all feature commands through it.
     */
    fun connectOtg() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Checking for OTG/USB ADB device…", LogLevel.INFO)
            val ok = connectionManager.connectOtg()
            if (ok) {
                addLog("OTG device detected — ACCU connected via USB ADB ✓", LogLevel.SUCCESS)
                withContext(Dispatchers.Main) { refresh() }
            } else {
                addLog("No USB ADB device found. Ensure: USB Debugging enabled on target, cable connected.", LogLevel.ERROR)
            }
        }
    }

    fun restartServer() {
        viewModelScope.launch {
            addLog("Reconnecting ACCU…", LogLevel.INFO)
            stopServer()
            delay(800)
            val ok = connectionManager.reconnect()
            if (ok) {
                addLog("Reconnected ✓", LogLevel.SUCCESS)
                refresh()
            } else {
                addLog("Reconnect failed — start pairing discovery to re-pair", LogLevel.WARNING)
            }
        }
    }

    /** Called from the pairing screen when user enters the 6-digit code. Code is all that's needed — IP/port auto-detected. */
    fun completePairing(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isPairing = true, pairingStatus = "Pairing with auto-detected device…") }
            addLog("Completing pairing with code $code", LogLevel.INFO)
            val ok = connectionManager.completePairing(code)
            val status = if (ok) "Paired and connected ✓" else "Pairing failed — check the code and try again"
            _state.update { it.copy(isPairing = false, pairingStatus = status) }
            addLog(status, if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
            if (ok) refresh()
        }
    }

    fun startDiscovery() {
        addLog("Starting auto-discovery for Wireless Debugging pairing service…", LogLevel.INFO)
        connectionManager.startPairingDiscovery()
    }

    fun stopDiscovery() {
        connectionManager.stopPairingDiscovery()
        _state.update { it.copy(isPairing = false, pairingStatus = "") }
        addLog("Discovery stopped", LogLevel.INFO)
    }

    // ── Legacy pair-with-code (keeps call site in AccuCenterScreen working) ─

    fun pairWithCode(host: String, port: String, code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isPairing = true, pairingStatus = "Pairing with $host:$port…") }
            addLog("Pairing with $host:$port using code $code", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) { shizukuUtils.execAdb("adb pair $host:$port $code") }
            val success = result.combinedOutput.contains("Successfully", ignoreCase = true) || result.isSuccess
            val status = if (success) "Paired ✓" else "Pairing failed: ${result.combinedOutput.take(100)}"
            _state.update { it.copy(isPairing = false, pairingStatus = status) }
            addLog(status, if (success) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    // ── Wireless ADB ──────────────────────────────────────────────────────────

    fun enableWirelessAdb() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Enabling Wireless ADB…", LogLevel.INFO)
            shizukuUtils.execShizuku("settings put global adb_wifi_enabled 1")
            shizukuUtils.execShizuku("setprop service.adb.tcp.port 5555")
            shizukuUtils.execShizuku("stop adbd")
            shizukuUtils.execShizuku("start adbd")
            delay(800)
            _state.update { it.copy(isWirelessAdbEnabled = true, wirelessAdbPort = 5555) }
            addLog("Wireless ADB enabled on port 5555 — connect: adb connect ${_state.value.deviceIp}:5555", LogLevel.SUCCESS)
        }
    }

    fun disableWirelessAdb() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Disabling Wireless ADB…", LogLevel.INFO)
            shizukuUtils.execShizuku("settings put global adb_wifi_enabled 0")
            shizukuUtils.execShizuku("setprop service.adb.tcp.port -1")
            shizukuUtils.execShizuku("stop adbd")
            shizukuUtils.execShizuku("start adbd")
            _state.update { it.copy(isWirelessAdbEnabled = false) }
            addLog("Wireless ADB disabled", LogLevel.INFO)
        }
    }

    fun connectWirelessAdb(host: String, port: String) {
        viewModelScope.launch {
            addLog("Connecting to $host:$port…", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) { shizukuUtils.execAdb("adb connect $host:$port") }
            val success = result.combinedOutput.contains("connected", ignoreCase = true)
            addLog(
                if (success) "Connected ✓" else "Failed: ${result.combinedOutput.take(100)}",
                if (success) LogLevel.SUCCESS else LogLevel.ERROR
            )
            if (success) { delay(500); withContext(Dispatchers.IO) { refresh() } }
        }
    }

    fun disconnectAdbDevice(serial: String) {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Disconnecting $serial…", LogLevel.INFO)
            val result = shizukuUtils.execAdb("adb disconnect $serial")
            addLog(result.combinedOutput.take(100), if (result.isSuccess) LogLevel.INFO else LogLevel.ERROR)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    // ── mDNS ─────────────────────────────────────────────────────────────────

    fun startMdnsScan() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, mdnsServices = emptyList()) }
            addLog("Scanning for Wireless Debugging services via mDNS…", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) { shizukuUtils.execAdb("adb mdns check") }
            addLog(result.combinedOutput.ifBlank { "mDNS daemon running" }, LogLevel.DEBUG)
            val servicesResult = withContext(Dispatchers.IO) {
                delay(2000)
                shizukuUtils.execAdb("adb mdns services")
            }
            val services = parseMdnsServices(servicesResult.output)
            _state.update { it.copy(isScanning = false, mdnsServices = services) }
            addLog("Scan complete — ${services.size} service(s) found", if (services.isNotEmpty()) LogLevel.SUCCESS else LogLevel.INFO)
        }
    }

    fun stopMdnsScan() {
        _state.update { it.copy(isScanning = false) }
        addLog("mDNS scan stopped", LogLevel.INFO)
    }

    fun connectMdnsService(service: MdnsService) {
        viewModelScope.launch {
            _state.update { st -> st.copy(mdnsServices = st.mdnsServices.map { if (it == service) it.copy(isConnecting = true) else it }) }
            addLog("Connecting to ${service.serviceName} (${service.host}:${service.port})…", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) { shizukuUtils.execAdb("adb connect ${service.host}:${service.port}") }
            val ok = result.combinedOutput.contains("connected", ignoreCase = true)
            _state.update { st -> st.copy(mdnsServices = st.mdnsServices.map { if (it == service) it.copy(isConnecting = false) else it }) }
            addLog(if (ok) "Connected ✓" else "Failed: ${result.combinedOutput.take(80)}", if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    // ── Authorized Apps (ACCU-granted) ────────────────────────────────────────

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val apps = loadAuthorizedApps()
            _state.update { it.copy(authorizedApps = apps, isLoading = false) }
            addLog("Loaded ${apps.size} authorized app(s)", LogLevel.INFO)
        }
    }

    fun grantApp(app: AuthorizedApp) {
        _state.update { st -> st.copy(authorizedApps = st.authorizedApps.map { if (it.packageName == app.packageName) it.copy(isGranted = true) else it }) }
        addLog("Granted ACCU access to ${app.appName}", LogLevel.SUCCESS)
    }

    fun revokeApp(app: AuthorizedApp) {
        _state.update { st -> st.copy(authorizedApps = st.authorizedApps.map { if (it.packageName == app.packageName) it.copy(isGranted = false) else it }) }
        addLog("Revoked ACCU access from ${app.appName}", LogLevel.WARNING)
    }

    fun revokeAll() {
        _state.update { st -> st.copy(authorizedApps = st.authorizedApps.map { it.copy(isGranted = false) }) }
        addLog("Revoked all authorizations", LogLevel.WARNING)
    }

    fun setAppsFilter(filter: AppsFilter) { _state.update { it.copy(authorizedAppsFilter = filter) } }
    fun setAppsSearch(query: String) { _state.update { it.copy(authorizedAppsSearch = query) } }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    fun runDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(diagnosticsRunning = true) }
            addLog("━━━━━ ACCU Diagnostics ━━━━━", LogLevel.INFO)
            val checks = listOf(
                "Android version"   to "getprop ro.build.version.release",
                "SDK level"         to "getprop ro.build.version.sdk",
                "Device model"      to "getprop ro.product.model",
                "Current UID"       to "id",
                "SELinux status"    to "getenforce",
                "ADB enabled"       to "settings get global adb_enabled",
                "Wireless ADB"      to "settings get global adb_wifi_enabled",
                "ADB TCP port"      to "getprop service.adb.tcp.port",
                "Root available"    to "su -c id 2>/dev/null || echo 'no root'",
            )
            checks.forEach { (label, cmd) ->
                val result = shizukuUtils.execShizuku(cmd)
                addLog("$label: ${result.output.trim().ifEmpty { result.error.take(60).ifEmpty { "N/A" } }}", LogLevel.INFO)
            }
            addLog("Connection: ${_state.value.serverStartMethod}", LogLevel.INFO)
            addLog("Privilege available: ${connectionManager.isPrivilegeAvailable()}", LogLevel.INFO)
            addLog("━━━━━ Diagnostics Complete ━━━━━", LogLevel.SUCCESS)
            _state.update { it.copy(diagnosticsRunning = false) }
        }
    }

    // ── Rish ──────────────────────────────────────────────────────────────────

    fun loadRishInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_state.value.isGranted) return@launch
            val result = shizukuUtils.execShizuku("which rish 2>/dev/null || echo 'not found'")
            val path = result.output.trim().takeIf { it != "not found" && it.isNotBlank() } ?: ""
            val version = if (path.isNotEmpty()) shizukuUtils.execShizuku("rish --version 2>/dev/null").output.trim() else ""
            _state.update { it.copy(rishInfo = RishInfo(isAvailable = path.isNotEmpty(), version = version, path = path)) }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun setBlackNightMode(v: Boolean) { _state.update { it.copy(blackNightMode = v) } }
    fun setUseSystemColors(v: Boolean) { _state.update { it.copy(useSystemColors = v) } }
    fun setAutoStartOnBoot(v: Boolean) { _state.update { it.copy(autoStartOnBoot = v) } }
    fun setShowNotification(v: Boolean) { _state.update { it.copy(showNotification = v) } }
    fun setRequireUnlockForTiles(v: Boolean) { _state.update { it.copy(requireUnlockForTiles = v) } }

    // ── Logs ──────────────────────────────────────────────────────────────────

    fun setLogFilter(level: LogLevel?) { _state.update { it.copy(logFilter = level) } }
    fun clearLogs() { _state.update { it.copy(logs = emptyList()) }; addLog("Logs cleared", LogLevel.DEBUG) }
    fun exportLogs(): String = _state.value.logs.joinToString("\n") { entry ->
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(entry.timestamp))
        "[$time][${entry.level}] ${entry.message}"
    }
    fun filteredLogs() = _state.value.logFilter?.let { f -> _state.value.logs.filter { it.level == f } } ?: _state.value.logs

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        _state.update { s -> s.copy(logs = (s.logs + ShizukuLogEntry(message = message, level = level)).takeLast(1000)) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun loadAuthorizedApps(): List<AuthorizedApp> = try {
        val result = shizukuUtils.execShizuku("pm list packages -3")
        val pm = context.packageManager
        result.output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { pkg ->
                try {
                    val info = pm.getPackageInfo(pkg, 0)
                    val appInfo = info.applicationInfo
                    val isSystem = appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    AuthorizedApp(
                        packageName = pkg,
                        appName = try { pm.getApplicationLabel(appInfo!!).toString() } catch (_: Exception) { pkg },
                        uid = appInfo?.uid ?: -1,
                        versionName = info.versionName ?: "",
                        isGranted = false,
                        isSystemApp = isSystem,
                    )
                } catch (_: Exception) { null }
            }
            .sortedBy { it.appName }
    } catch (_: Exception) { emptyList() }

    private fun parseMdnsServices(output: String): List<MdnsService> = output.lines()
        .filter { it.isNotBlank() && !it.startsWith("List") }
        .mapNotNull { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3) {
                MdnsService(
                    serviceName = parts[0],
                    host = parts.getOrElse(2) { "" }.split(":").getOrElse(0) { "" },
                    port = parts.getOrElse(2) { ":0" }.split(":").getOrElse(1) { "0" }.toIntOrNull() ?: 0,
                    type = parts.getOrElse(1) { "" },
                )
            } else null
        }

    private suspend fun getConnectedAdbDevices(): List<AdbDevice> = try {
        val result = shizukuUtils.execAdb("adb devices")
        result.output.lines()
            .drop(1)
            .filter { it.isNotBlank() && it.contains("\t") }
            .map { line ->
                val parts = line.split("\t")
                AdbDevice(serial = parts[0].trim(), state = parts.getOrElse(1) { "" }.trim())
            }
    } catch (_: Exception) { emptyList() }
}
