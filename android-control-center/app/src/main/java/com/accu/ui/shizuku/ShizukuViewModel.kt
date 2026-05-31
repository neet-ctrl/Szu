package com.accu.ui.shizuku

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import rikka.shizuku.Shizuku
import javax.inject.Inject

data class ShizukuUiState(
    val isAvailable: Boolean = false,
    val isGranted: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isInstalled: Boolean = false,
    val version: Int = -1,
    val patchVersion: Int = -1,
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
    val path: String = "/data/local/tmp/rish",
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
) : ViewModel() {

    private val _state = MutableStateFlow(ShizukuUiState())
    val state: StateFlow<ShizukuUiState> = _state.asStateFlow()

    private val binderListener = Shizuku.OnBinderReceivedListener {
        viewModelScope.launch { refresh() }
    }
    private val deadListener = Shizuku.OnBinderDeadListener {
        _state.update { it.copy(isAvailable = false, isGranted = false, serverPid = -1) }
        addLog("Shizuku binder died — service stopped", LogLevel.ERROR)
    }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        _state.update { it.copy(isGranted = granted) }
        addLog(if (granted) "Permission granted ✓" else "Permission denied", if (granted) LogLevel.SUCCESS else LogLevel.ERROR)
        if (granted) viewModelScope.launch { refresh() }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permListener)
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val available = shizukuUtils.isShizukuAvailable()
            val granted = if (available) shizukuUtils.isShizukuGranted() else false
            val version = if (available) shizukuUtils.getShizukuVersion() else -1
            val uid = if (available) shizukuUtils.getShizukuUid() else -1
            val isRoot = shizukuUtils.isRootAvailable()
            val installed = shizukuUtils.isShizukuInstalled(context)
            val deviceIp = getDeviceIp()
            val port = if (available && granted) getAdbPort() else 5555
            val pid = if (available && granted) getServerPid() else -1
            val patchVer = if (available) { try { Shizuku.getServerPatchVersion().let { if (it < 0) 0 else it } } catch (_: Exception) { 0 } } else 0
            val seCtx = if (available && version >= 6) { try { Shizuku.getSELinuxContext() ?: "" } catch (_: Exception) { "" } } else ""
            val permTest = if (available) { try { Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false } } else false
            val apps = if (available && granted) loadAuthorizedApps() else _state.value.authorizedApps
            val adbDevices = if (available && granted) getConnectedAdbDevices() else emptyList()
            val wirelessEnabled = if (available && granted) {
                try { shizukuUtils.execShizuku("settings get global adb_wifi_enabled").output.trim() == "1" } catch (_: Exception) { false }
            } else false

            _state.update {
                it.copy(
                    isAvailable = available, isGranted = granted,
                    version = version, patchVersion = patchVer,
                    uid = uid, seLinuxContext = seCtx,
                    permissionGranted = permTest,
                    isRootAvailable = isRoot, isInstalled = installed,
                    deviceIp = deviceIp, wirelessAdbPort = port,
                    isWirelessAdbEnabled = wirelessEnabled,
                    serverPid = pid,
                    serverStartMethod = when { uid == 0 -> "Root" ; uid == 2000 -> "ADB" ; else -> "Unknown" },
                    authorizedApps = apps,
                    connectedAdbDevices = adbDevices,
                    isLoading = false,
                )
            }
            if (available && granted) {
                addLog("Shizuku v$version (patch $patchVer) running — uid=$uid pid=$pid", LogLevel.SUCCESS)
                if (seCtx.isNotEmpty()) addLog("SELinux context: $seCtx", LogLevel.VERBOSE)
            } else if (available) {
                addLog("Shizuku running but permission not granted", LogLevel.WARNING)
            } else {
                addLog("Shizuku service not running", LogLevel.WARNING)
            }
        }
    }

    fun requestPermission() {
        addLog("Requesting Shizuku permission…", LogLevel.INFO)
        shizukuUtils.requestShizukuPermission()
    }

    // ── Server Control ────────────────────────────────────────────

    fun startWithAdb() {
        viewModelScope.launch {
            addLog("Starting Shizuku via ADB…", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) {
                shizukuUtils.execAdb("adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")
            }
            addLog(
                if (result.isSuccess) "Started via ADB ✓" else "ADB start failed: ${result.error.take(200)}",
                if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR
            )
            if (result.isSuccess) { delay(1500); refresh() }
        }
    }

    fun startWithRoot() {
        viewModelScope.launch {
            addLog("Starting Shizuku via root…", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) {
                shizukuUtils.execRoot("sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")
            }
            addLog(
                if (result.isSuccess) "Started via root ✓" else "Root start failed: ${result.error.take(200)}",
                if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR
            )
            if (result.isSuccess) { delay(1500); refresh() }
        }
    }

    fun stopServer() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Stopping Shizuku server…", LogLevel.INFO)
            try {
                Shizuku.exit()
                addLog("Server stopped ✓", LogLevel.SUCCESS)
            } catch (e: Exception) {
                val pid = _state.value.serverPid
                if (pid > 0) {
                    val result = shizukuUtils.execShizuku("kill $pid")
                    addLog(if (result.isSuccess) "Server stopped (kill $pid) ✓" else "Stop failed: ${result.error}", if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR)
                } else {
                    addLog("Cannot stop: ${e.message}", LogLevel.ERROR)
                }
            }
            delay(500)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun restartServer() {
        viewModelScope.launch {
            addLog("Restarting Shizuku server…", LogLevel.INFO)
            stopServer()
            delay(1500)
            if (_state.value.isRootAvailable) startWithRoot() else startWithAdb()
        }
    }

    // ── Wireless ADB ─────────────────────────────────────────────

    fun enableWirelessAdb() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Enabling Wireless ADB…", LogLevel.INFO)
            shizukuUtils.execShizuku("settings put global adb_wifi_enabled 1")
            shizukuUtils.execShizuku("setprop service.adb.tcp.port 5555")
            shizukuUtils.execShizuku("stop adbd")
            shizukuUtils.execShizuku("start adbd")
            delay(800)
            val port = getAdbPort()
            _state.update { it.copy(isWirelessAdbEnabled = true, wirelessAdbPort = port) }
            addLog("Wireless ADB enabled on port $port — connect: adb connect ${_state.value.deviceIp}:$port", LogLevel.SUCCESS)
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

    fun pairWithCode(host: String, port: String, code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isPairing = true, pairingStatus = "Pairing with $host:$port…") }
            addLog("Pairing with $host:$port using code $code", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) {
                shizukuUtils.execAdb("adb pair $host:$port $code")
            }
            val success = result.combinedOutput.contains("Successfully", ignoreCase = true) || result.isSuccess
            val status = if (success) "Paired with $host:$port ✓" else "Pairing failed: ${result.combinedOutput.take(100)}"
            _state.update { it.copy(isPairing = false, pairingStatus = status) }
            addLog(status, if (success) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    fun connectWirelessAdb(host: String, port: String) {
        viewModelScope.launch {
            addLog("Connecting to $host:$port…", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) {
                shizukuUtils.execAdb("adb connect $host:$port")
            }
            val success = result.combinedOutput.contains("connected", ignoreCase = true)
            addLog(
                if (success) "Connected to $host:$port ✓" else "Connect failed: ${result.combinedOutput.take(100)}",
                if (success) LogLevel.SUCCESS else LogLevel.ERROR
            )
            if (success) delay(500); withContext(Dispatchers.IO) { refresh() }
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

    // ── mDNS ────────────────────────────────────────────────────

    fun startMdnsScan() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, mdnsServices = emptyList()) }
            addLog("Scanning for Wireless Debugging services via mDNS…", LogLevel.INFO)
            val result = withContext(Dispatchers.IO) {
                shizukuUtils.execAdb("adb mdns check")
            }
            addLog(result.combinedOutput.ifBlank { "mDNS daemon running" }, LogLevel.DEBUG)
            val servicesResult = withContext(Dispatchers.IO) {
                delay(2000)
                shizukuUtils.execAdb("adb mdns services")
            }
            val services = parseMdnsServices(servicesResult.output)
            _state.update { it.copy(isScanning = false, mdnsServices = services) }
            addLog("mDNS scan complete — ${services.size} service(s) found", if (services.isNotEmpty()) LogLevel.SUCCESS else LogLevel.INFO)
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
            val result = withContext(Dispatchers.IO) {
                shizukuUtils.execAdb("adb connect ${service.host}:${service.port}")
            }
            val ok = result.combinedOutput.contains("connected", ignoreCase = true)
            _state.update { st -> st.copy(mdnsServices = st.mdnsServices.map { if (it == service) it.copy(isConnecting = false) else it }) }
            addLog(if (ok) "Connected to ${service.serviceName} ✓" else "Failed: ${result.combinedOutput.take(80)}", if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    // ── Authorized Apps ──────────────────────────────────────────

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val apps = loadAuthorizedApps()
            _state.update { it.copy(authorizedApps = apps, isLoading = false) }
            addLog("Loaded ${apps.size} authorized app(s)", LogLevel.INFO)
        }
    }

    fun grantApp(app: AuthorizedApp) {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Granting Shizuku to ${app.appName} (${app.packageName})…", LogLevel.INFO)
            try {
                Shizuku.updateFlagsForUid(app.uid, 6, 2)
                _state.update { st -> st.copy(authorizedApps = st.authorizedApps.map { if (it.packageName == app.packageName) it.copy(isGranted = true) else it }) }
                addLog("Granted Shizuku to ${app.appName} ✓", LogLevel.SUCCESS)
            } catch (e: Exception) {
                addLog("Grant failed: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun revokeApp(app: AuthorizedApp) {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Revoking Shizuku from ${app.appName}…", LogLevel.WARNING)
            try {
                Shizuku.updateFlagsForUid(app.uid, 6, 0)
                _state.update { st -> st.copy(authorizedApps = st.authorizedApps.map { if (it.packageName == app.packageName) it.copy(isGranted = false) else it }) }
                addLog("Revoked Shizuku from ${app.appName}", LogLevel.INFO)
            } catch (e: Exception) {
                addLog("Revoke failed: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun revokeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Revoking all Shizuku authorizations…", LogLevel.WARNING)
            val granted = _state.value.authorizedApps.filter { it.isGranted }
            granted.forEach { app ->
                try { Shizuku.updateFlagsForUid(app.uid, 6, 0) } catch (_: Exception) {}
            }
            _state.update { st -> st.copy(authorizedApps = st.authorizedApps.map { it.copy(isGranted = false) }) }
            addLog("Revoked all (${granted.size}) authorizations", LogLevel.SUCCESS)
        }
    }

    fun setAppsFilter(filter: AppsFilter) {
        _state.update { it.copy(authorizedAppsFilter = filter) }
    }

    fun setAppsSearch(query: String) {
        _state.update { it.copy(authorizedAppsSearch = query) }
    }

    // ── Rish ─────────────────────────────────────────────────────

    fun loadRishInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_state.value.isGranted) return@launch
            val result = shizukuUtils.execShizuku("which rish 2>/dev/null || echo 'not found'")
            val path = result.output.trim().takeIf { it != "not found" && it.isNotBlank() } ?: ""
            val version = if (path.isNotEmpty()) {
                shizukuUtils.execShizuku("rish --version 2>/dev/null").output.trim()
            } else ""
            _state.update { it.copy(rishInfo = RishInfo(isAvailable = path.isNotEmpty(), version = version, path = path.ifEmpty { "/data/local/tmp/rish" })) }
        }
    }

    // ── Settings ─────────────────────────────────────────────────

    fun setBlackNightMode(v: Boolean) { _state.update { it.copy(blackNightMode = v) }; addLog("Black night mode: $v", LogLevel.DEBUG) }
    fun setUseSystemColors(v: Boolean) { _state.update { it.copy(useSystemColors = v) }; addLog("System colors: $v", LogLevel.DEBUG) }
    fun setAutoStartOnBoot(v: Boolean) {
        _state.update { it.copy(autoStartOnBoot = v) }
        addLog(if (v) "Auto-start on boot enabled" else "Auto-start on boot disabled", LogLevel.INFO)
        if (v) viewModelScope.launch(Dispatchers.IO) {
            shizukuUtils.execShizuku("settings put global shizuku_auto_start 1")
        }
    }
    fun setShowNotification(v: Boolean) { _state.update { it.copy(showNotification = v) } }
    fun setRequireUnlockForTiles(v: Boolean) { _state.update { it.copy(requireUnlockForTiles = v) } }

    // ── Diagnostics ──────────────────────────────────────────────

    fun runDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(diagnosticsRunning = true) }
            addLog("━━━━━ Shizuku Diagnostics ━━━━━", LogLevel.INFO)
            val checks = listOf(
                "Android version" to "getprop ro.build.version.release",
                "SDK level" to "getprop ro.build.version.sdk",
                "Device model" to "getprop ro.product.model",
                "Current UID" to "id",
                "SELinux status" to "getenforce",
                "ADB enabled" to "settings get global adb_enabled",
                "Wireless ADB" to "settings get global adb_wifi_enabled",
                "ADB TCP port" to "getprop service.adb.tcp.port",
                "Shizuku pkg" to "pm path moe.shizuku.privileged.api",
                "Root available" to "su -c id 2>/dev/null || echo 'no root'",
            )
            checks.forEach { (label, cmd) ->
                val result = shizukuUtils.execShizuku(cmd)
                addLog("$label: ${result.output.trim().ifEmpty { result.error.take(60).ifEmpty { "N/A" } }}", LogLevel.INFO)
            }
            addLog("Shizuku v${_state.value.version} patch:${_state.value.patchVersion} uid:${_state.value.uid} pid:${_state.value.serverPid}", LogLevel.INFO)
            addLog("SELinux context: ${_state.value.seLinuxContext.ifEmpty { "unknown" }}", LogLevel.INFO)
            addLog("Grant permission test: ${_state.value.permissionGranted}", LogLevel.INFO)
            addLog("━━━━━ Diagnostics Complete ━━━━━", LogLevel.SUCCESS)
            _state.update { it.copy(diagnosticsRunning = false) }
        }
    }

    // ── Logs ─────────────────────────────────────────────────────

    fun setLogFilter(level: LogLevel?) { _state.update { it.copy(logFilter = level) } }
    fun clearLogs() { _state.update { it.copy(logs = emptyList()) }; addLog("Logs cleared", LogLevel.DEBUG) }
    fun exportLogs(): String = _state.value.logs.joinToString("\n") { entry ->
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(entry.timestamp))
        "[$time][${entry.level}] ${entry.message}"
    }

    fun filteredLogs(): List<ShizukuLogEntry> {
        val filter = _state.value.logFilter ?: return _state.value.logs
        return _state.value.logs.filter { it.level == filter }
    }

    // ── Private helpers ──────────────────────────────────────────

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        _state.update { s -> s.copy(logs = (s.logs + ShizukuLogEntry(message = message, level = level)).takeLast(1000)) }
    }

    private fun getDeviceIp(): String = try {
        java.net.NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.firstOrNull()?.hostAddress ?: ""
    } catch (_: Exception) { "" }

    private fun getAdbPort(): Int = try {
        shizukuUtils.execAdb("getprop service.adb.tcp.port").output.trim().toIntOrNull() ?: 5555
    } catch (_: Exception) { 5555 }

    private suspend fun getServerPid(): Int = try {
        val result = shizukuUtils.execShizuku("ps -A | grep shizuku.server")
        val line = result.output.lines().firstOrNull { it.contains("shizuku", ignoreCase = true) } ?: return -1
        line.trim().split("\\s+".toRegex()).getOrNull(1)?.toIntOrNull() ?: -1
    } catch (_: Exception) { -1 }

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
                        appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg.substringAfterLast("."),
                        uid = appInfo?.uid ?: 0,
                        versionName = info.versionName ?: "",
                        isGranted = true,
                        isSystemApp = isSystem,
                    )
                } catch (_: Exception) {
                    AuthorizedApp(packageName = pkg, appName = pkg.substringAfterLast("."), uid = 0, isGranted = true)
                }
            }
    } catch (_: Exception) { emptyList() }

    private fun parseMdnsServices(raw: String): List<MdnsService> {
        if (raw.isBlank()) return emptyList()
        return raw.lines().filter { it.isNotBlank() && !it.startsWith("List") }.mapNotNull { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3) {
                val host = parts.getOrElse(1) { "?" }
                val port = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
                val type = parts.getOrElse(3) { "_adb-tls-connect._tcp" }
                MdnsService(serviceName = parts[0], host = host, port = port, type = type)
            } else null
        }
    }

    private fun getConnectedAdbDevices(): List<AdbDevice> = try {
        val result = shizukuUtils.execAdb("adb devices -l")
        result.output.lines()
            .drop(1)
            .filter { it.isNotBlank() && !it.startsWith("*") }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) AdbDevice(serial = parts[0], state = parts[1], model = parts.getOrElse(2) { "" }) else null
            }
    } catch (_: Exception) { emptyList() }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permListener)
    }
}
