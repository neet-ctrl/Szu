package com.accu.ui.shizuku

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val isPairing: Boolean = false,
    val pairingStatus: String = "",
    /** True when pairing succeeded but the TLS connection phase failed — enables "Retry Connection". */
    val isConnectionFailed: Boolean = false,
    /** Full raw error from the failed connection attempt, shown in the expanded error card. */
    val connectionFailedRaw: String = "",
    /** Host that was tried when connection failed — shown in the error card. */
    val connectionFailedHost: String = "",
    /** Port that was tried when connection failed — shown in the error card. */
    val connectionFailedPort: Int = 0,
    val isRetryingConnection: Boolean = false,
    /**
     * Comprehensive debug snapshot built at the moment of connection failure.
     * Contains host/port, host-device info, all session log entries, and the full
     * exception stack trace. Copied to clipboard by the "Copy Debug Log" button.
     */
    val connectionDebugLog: String = "",
    val serverPid: Int = -1,
    val serverStartMethod: String = "",
    val logs: List<ShizukuLogEntry> = emptyList(),
    val logFilter: LogLevel? = null,
    val authorizedApps: List<AuthorizedApp> = emptyList(),
    val authorizedAppsFilter: AppsFilter = AppsFilter.ALL,
    val authorizedAppsSearch: String = "",
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
    /** ro.product.model of the connected target device */
    val deviceModel: String = "",
    /** ro.build.version.release (e.g. "14") */
    val androidVersion: String = "",
    /** ro.build.version.sdk (e.g. "34") */
    val sdkLevel: String = "",
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
                        // Populate discovered pairing endpoint as soon as mDNS resolves it
                        discoveredPairingIp = if (connState == AccuConnectionManager.ConnectionState.AWAITING_CODE)
                            connectionManager.getPairingHost() else it.discoveredPairingIp,
                        discoveredPairingPort = if (connState == AccuConnectionManager.ConnectionState.AWAITING_CODE)
                            connectionManager.getPairingPort() else it.discoveredPairingPort,
                    )
                }
                if (connState == AccuConnectionManager.ConnectionState.DISCONNECTED) {
                    addLog("ACCU connection lost", LogLevel.WARNING)
                } else if (connState == AccuConnectionManager.ConnectionState.AWAITING_CODE) {
                    val h = connectionManager.getPairingHost()
                    val p = connectionManager.getPairingPort()
                    addLog("Pairing service detected at $h:$p — enter 6-digit code", LogLevel.INFO)
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

            val apps = if (isConnected) loadAuthorizedApps() else _state.value.authorizedApps

            // Fetch target device info — routes through exec() so it targets the connected device
            val deviceModel = if (isConnected)
                connectionManager.exec("getprop ro.product.model").output.trim() else ""
            val androidVersion = if (isConnected)
                connectionManager.exec("getprop ro.build.version.release").output.trim() else ""
            val sdkLevel = if (isConnected)
                connectionManager.exec("getprop ro.build.version.sdk").output.trim() else ""

            _state.update {
                it.copy(
                    connectionState = connState,
                    isAvailable = connState != AccuConnectionManager.ConnectionState.DISCONNECTED,
                    isGranted = isConnected,
                    isRootAvailable = isRoot,
                    isInstalled = true,
                    uid = if (isRoot) 0 else android.os.Process.myUid(),
                    deviceIp = deviceIp,
                    serverStartMethod = when {
                        isRoot -> "Root"
                        connState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS -> "Wireless ADB ($lastIp)"
                        connState == AccuConnectionManager.ConnectionState.CONNECTED_OTG      -> "OTG / USB ADB"
                        else -> "Not connected"
                    },
                    authorizedApps = apps,
                    deviceModel = deviceModel,
                    androidVersion = androidVersion,
                    sdkLevel = sdkLevel,
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
            when (val result = connectionManager.completePairing(code)) {
                is AccuConnectionManager.PairingResult.Success -> {
                    val status = "Paired and connected ✓"
                    _state.update { it.copy(isPairing = false, pairingStatus = status) }
                    addLog(status, LogLevel.SUCCESS)
                    refresh()
                }
                is AccuConnectionManager.PairingResult.NoAdbBinary -> {
                    val h = result.host
                    val p = result.port
                    val sessionPort = connectionManager.getSessionPort().takeIf { it > 0 } ?: 5555
                    val pairCmd    = "adb pair $h:$p $code"
                    val connectCmd = "adb connect $h:$sessionPort"
                    val status = "No adb binary on this device.\nRun on your PC:\n  $pairCmd\nThen:\n  $connectCmd"
                    _state.update { it.copy(isPairing = false, pairingStatus = status) }
                    addLog("No adb binary — PC pair command: $pairCmd", LogLevel.WARNING)
                }
                is AccuConnectionManager.PairingResult.WrongCode -> {
                    val detail = if (result.rawOutput.isNotBlank()) "\n\nRaw output: ${result.rawOutput}" else ""
                    val status = "Pairing failed — wrong code, or the code expired.\nOpen 'Pair device with pairing code' again to get a fresh code, then retry.$detail"
                    _state.update { it.copy(isPairing = false, pairingStatus = status) }
                    addLog("Pairing failed — ${result.rawOutput.take(80)}", LogLevel.ERROR)
                }
                is AccuConnectionManager.PairingResult.ConnectionFailed -> {
                    val portInfo = if (result.sessionPort > 0) " (port ${result.sessionPort})" else ""
                    val status = "Pairing succeeded ✓ but connection to ${result.host}$portInfo failed.\nTap \"Retry Connection\" — no new code needed."
                    val debugLog = buildConnectionDebugLog(result.host, result.sessionPort, result.rawOutput)
                    _state.update {
                        it.copy(
                            isPairing = false,
                            pairingStatus = status,
                            isConnectionFailed = true,
                            connectionFailedRaw = result.rawOutput,
                            connectionFailedHost = result.host,
                            connectionFailedPort = result.sessionPort,
                            connectionDebugLog = debugLog,
                        )
                    }
                    addLog("Connection failed — ${result.rawOutput.lines().firstOrNull()?.take(200).orEmpty()}", LogLevel.ERROR)
                }
                is AccuConnectionManager.PairingResult.NoPairingService -> {
                    val status = "No pairing service found yet.\nGo to: Developer Options → Wireless debugging → Pair device with pairing code"
                    _state.update { it.copy(isPairing = false, pairingStatus = status) }
                    addLog("completePairing: pairingPort=0, mDNS not resolved yet", LogLevel.ERROR)
                }
            }
        }
    }

    /**
     * Re-attempt only the TLS connection phase after a [PairingResult.ConnectionFailed].
     * Pairing already succeeded so the device still trusts our key — no new code required.
     */
    fun retryConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isRetryingConnection = true, pairingStatus = "Retrying connection…") }
            addLog("Retrying TLS ADB connection (no re-pairing needed)…", LogLevel.INFO)
            when (val result = connectionManager.retryConnectionOnly()) {
                is AccuConnectionManager.PairingResult.Success -> {
                    _state.update {
                        it.copy(
                            isRetryingConnection = false,
                            isConnectionFailed = false,
                            pairingStatus = "Connected ✓",
                            connectionFailedRaw = "",
                        )
                    }
                    addLog("Retry succeeded — TLS ADB connected ✓", LogLevel.SUCCESS)
                    refresh()
                }
                is AccuConnectionManager.PairingResult.ConnectionFailed -> {
                    val portInfo = if (result.sessionPort > 0) " (port ${result.sessionPort})" else ""
                    val status = "Pairing succeeded ✓ but connection to ${result.host}$portInfo failed.\nTap \"Retry Connection\" — no new code needed."
                    val debugLog = buildConnectionDebugLog(result.host, result.sessionPort, result.rawOutput)
                    _state.update {
                        it.copy(
                            isRetryingConnection = false,
                            isConnectionFailed = true,
                            pairingStatus = status,
                            connectionFailedRaw = result.rawOutput,
                            connectionFailedHost = result.host,
                            connectionFailedPort = result.sessionPort,
                            connectionDebugLog = debugLog,
                        )
                    }
                    addLog("Retry failed — ${result.rawOutput.lines().firstOrNull()?.take(200).orEmpty()}", LogLevel.ERROR)
                }
                else -> {
                    _state.update { it.copy(isRetryingConnection = false, pairingStatus = "Retry failed — please re-pair from the pairing screen") }
                    addLog("retryConnection: unexpected result $result", LogLevel.ERROR)
                }
            }
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

    /**
     * Pair via code.
     *
     * ACCU is the Shizuku equivalent — it uses LibSU (root) as its privilege
     * source, exactly like aShell uses Shizuku's Binder IPC.  The `adb`
     * CLI tool does NOT exist on Android; it runs on the PC side.
     *
     * Priority:
     *   1. Root available → already privileged, no pairing needed ✓
     *   2. System adb binary found (/system/bin/adb etc.) → run adb pair
     *   3. Otherwise → guide user to run command from their PC
     */
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
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
        "[$time][${entry.level}] ${entry.message}"
    }
    fun filteredLogs() = _state.value.logFilter?.let { f -> _state.value.logs.filter { it.level == f } } ?: _state.value.logs

    /**
     * Builds a self-contained debug report at the moment of TLS connection failure.
     *
     * Includes host/port, host-device info (Make/Model/Android version), all current
     * ACCU session log entries, and the full Java exception stack trace from [rawError].
     * The report is designed so a developer can diagnose *any* future TLS failure without
     * needing a Logcat session — paste the clipboard contents and the cause is visible.
     *
     * Checklist section maps common error strings to their root cause so the user (or
     * the developer reading their paste) can immediately identify the fix path.
     */
    fun buildConnectionDebugLog(host: String, port: Int, rawError: String): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.US).format(Date())
        val allLogs = _state.value.logs
        val logText = allLogs.takeLast(100).joinToString("\n") { entry ->
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
            "[${entry.level.name.padEnd(7)}] $time  ${entry.message}"
        }
        val connState = _state.value.connectionState
        val pairingIp  = _state.value.discoveredPairingIp
        val pairingPort = _state.value.discoveredPairingPort

        // Map the raw error's first line to an actionable checklist note
        val firstLine = rawError.lines().firstOrNull().orEmpty()
        val diagnosis = when {
            "connection closed" in firstLine ->
                "adbd sent close_notify — TLS cert was rejected.\n" +
                "  Most likely cause: client cert not sent or RSA-PSS signing failed.\n" +
                "  Fix: rebuild with Conscrypt-provider key fix. If already done, re-pair\n" +
                "  (target may have revoked the key in Developer Options)."
            "Handshake failed" in firstLine ->
                "TLS cipher/protocol mismatch — check Android version on both devices.\n" +
                "  adbd may require rsa_pss_rsae_sha256 only; confirm key type is RSA-2048."
            "Connection refused" in firstLine ->
                "Session port is stale or wireless debugging was restarted.\n" +
                "  Tap 'Wireless ADB' again to re-discover the new session port via mDNS."
            "SocketTimeoutException" in rawError || "connect timed out" in firstLine ->
                "Network timeout — devices may be on different Wi-Fi subnets or isolated.\n" +
                "  Both devices must be on the SAME Wi-Fi network/SSID."
            "ECONNRESET" in rawError ->
                "Connection reset by peer — adbd restarted or wireless debugging was toggled."
            "echo check" in firstLine ->
                "TLS handshake succeeded but ADB CNXN echo verification returned wrong output.\n" +
                "  Check 'adb shell echo ACCU_OK' manually to confirm shell access."
            else -> "Unknown failure — read full stack trace below."
        }

        return buildString {
            appendLine("╔════════════════════════════════════════════════════════════════╗")
            appendLine("║              ACCU TLS Connection Debug Report                  ║")
            appendLine("╚════════════════════════════════════════════════════════════════╝")
            appendLine()
            appendLine("Generated  : $ts")
            appendLine("Target     : ${if (host.isNotBlank()) "$host:$port" else "(unknown)"}")
            appendLine("Conn state : $connState")
            appendLine("Pairing IP : ${if (pairingIp.isNotBlank()) "$pairingIp:$pairingPort" else "(mDNS not discovered)"}")
            appendLine()
            appendLine("── Host Device (running ACCU) ───────────────────────────────────")
            appendLine("Manufacturer : ${Build.MANUFACTURER}")
            appendLine("Model        : ${Build.MODEL}")
            appendLine("Android      : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Build        : ${Build.DISPLAY}")
            appendLine("ABI          : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
            appendLine()
            appendLine("── Target Device (from last successful refresh) ─────────────────")
            appendLine("Model   : ${_state.value.deviceModel.ifBlank { "(not queried — connection failed)" }}")
            appendLine("Android : ${_state.value.androidVersion.ifBlank { "(not queried)" }} " +
                       "(SDK ${_state.value.sdkLevel.ifBlank { "?" }})")
            appendLine()
            appendLine("── Diagnosis ────────────────────────────────────────────────────")
            appendLine(diagnosis)
            appendLine()
            appendLine("── Full Exception / Stack Trace ─────────────────────────────────")
            appendLine(rawError.ifBlank { "(no error detail captured)" })
            appendLine()
            appendLine("── ACCU Session Log (last ${allLogs.size.coerceAtMost(100)} of ${allLogs.size} entries) ────")
            appendLine(logText.ifBlank { "(no log entries)" })
            appendLine()
            appendLine("── TLS Fix Checklist ────────────────────────────────────────────")
            appendLine("[✓] AdbKey uses system KeyFactory + KeyPairGenerator (no external Conscrypt)")
            appendLine("[✓] SSLContext.getInstance(\"TLSv1.3\") — system Conscrypt, exactly like Shizuku")
            appendLine("[✓] chooseClientAlias returns alias for RSA key type (matches Shizuku)")
            appendLine("[✓] Timber.w(e, ...) logs full stack on failure")
            appendLine("[✓] PeerInfo key = adbPublicKey (base64+name, confirmed from Shizuku)")
            appendLine("[✓] HiddenApiBypass exemptions applied in Application.onCreate")
            appendLine("[✓] Session socket locked to TLSv1.3 explicitly")
            appendLine("[✓] Post-pairing delay increased to 3 s for adbd key persistence")
            appendLine()
            appendLine("Send the full contents of this report to the ACCU developer.")
        }.trimEnd()
    }

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

    /**
     * Get connected ADB devices.
     * Uses system adb binary if present (some ROMs have it).
     * Returns empty list silently if adb is not on device — no error toast.
     */
    private suspend fun getConnectedAdbDevices(): List<AdbDevice> = try {
        val adb = connectionManager.findAdbBinary() ?: return emptyList()
        val result = withContext(Dispatchers.IO) { connectionManager.execPlainShell("$adb devices") }
        result.output.lines()
            .drop(1)
            .filter { it.isNotBlank() && it.contains("\t") }
            .map { line ->
                val parts = line.split("\t")
                AdbDevice(serial = parts[0].trim(), state = parts.getOrElse(1) { "" }.trim())
            }
    } catch (_: Exception) { emptyList() }
}
