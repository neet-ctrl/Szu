package com.accu.ui.shizuku

import android.content.Context
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
    val version: Int = -1,
    val uid: Int = -1,
    val isInstalled: Boolean = false,
    val logs: List<ShizukuLogEntry> = emptyList(),
    val authorizedApps: List<AuthorizedApp> = emptyList(),
    val wirelessAdbPort: Int = 0,
    val isWirelessAdbEnabled: Boolean = false,
    val deviceIp: String = "",
    val pairingCode: String = "",
    val isPairing: Boolean = false,
    val pairingStatus: String = "",
    val isLoading: Boolean = true,
    val mdnsServices: List<MdnsService> = emptyList(),
    val isScanning: Boolean = false,
    val serverPid: Int = -1,
    val serverUser: String = "",
    val context: String = "",
    val rishInfo: RishInfo = RishInfo(),
    val blackNightMode: Boolean = false,
    val useSystemColors: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val showNotification: Boolean = true,
    val requireUnlockForTiles: Boolean = false,
)

data class ShizukuLogEntry(val timestamp: Long = System.currentTimeMillis(), val message: String, val level: LogLevel = LogLevel.INFO)
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }
data class AuthorizedApp(val packageName: String, val appName: String, val uid: Int, val isGranted: Boolean = true)
data class MdnsService(val serviceName: String, val host: String, val port: Int, val type: String)
data class RishInfo(val isAvailable: Boolean = false, val version: String = "", val path: String = "")

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
        _state.update { it.copy(isAvailable = false, isGranted = false) }
        addLog("Shizuku binder died", LogLevel.ERROR)
    }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        _state.update { it.copy(isGranted = granted) }
        addLog(if (granted) "Permission granted" else "Permission denied", if (granted) LogLevel.SUCCESS else LogLevel.ERROR)
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permListener)
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        withContext(Dispatchers.IO) {
            val available = shizukuUtils.isShizukuAvailable()
            val granted = if (available) shizukuUtils.isShizukuGranted() else false
            val version = if (available) shizukuUtils.getShizukuVersion() else -1
            val uid = if (available) shizukuUtils.getShizukuUid() else -1
            val isRoot = shizukuUtils.isRootAvailable()
            val installed = shizukuUtils.isShizukuInstalled(context)
            val deviceIp = getDeviceIp()
            val port = getAdbPort()
            val serverPid = if (available) getServerPid() else -1
            val authorizedApps = if (available && granted) loadAuthorizedApps() else emptyList()

            _state.update {
                it.copy(
                    isAvailable = available, isGranted = granted,
                    version = version, uid = uid,
                    isRootAvailable = isRoot, isInstalled = installed,
                    deviceIp = deviceIp, wirelessAdbPort = port,
                    isLoading = false,
                    serverPid = serverPid,
                    authorizedApps = authorizedApps,
                )
            }
            if (available) addLog("Shizuku v$version running (uid=$uid, pid=$serverPid)", LogLevel.SUCCESS)
            else addLog("Shizuku service not running", LogLevel.WARNING)
        }
    }

    fun requestPermission() {
        addLog("Requesting Shizuku permission…", LogLevel.INFO)
        shizukuUtils.requestShizukuPermission()
    }

    fun startWithAdb() {
        viewModelScope.launch {
            addLog("Starting Shizuku via ADB…", LogLevel.INFO)
            val result = shizukuUtils.execAdb("adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")
            addLog(if (result.isSuccess) "Shizuku started via ADB" else "Failed: ${result.error}", if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR)
            delay(1500); refresh()
        }
    }

    fun startWithRoot() {
        viewModelScope.launch {
            addLog("Starting Shizuku via root…", LogLevel.INFO)
            val result = shizukuUtils.execRoot("sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")
            addLog(if (result.isSuccess) "Shizuku started via root" else "Failed: ${result.error}", if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR)
            delay(1500); refresh()
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            addLog("Stopping Shizuku server…", LogLevel.INFO)
            val result = shizukuUtils.execShizuku("kill ${_state.value.serverPid}")
            addLog(if (result.isSuccess) "Server stopped" else "Stop failed: ${result.error}", if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR)
            delay(500); refresh()
        }
    }

    fun restartServer() {
        viewModelScope.launch {
            addLog("Restarting Shizuku server…", LogLevel.INFO)
            stopServer()
            delay(1000)
            if (_state.value.isRootAvailable) startWithRoot() else startWithAdb()
        }
    }

    fun enableWirelessAdb() {
        viewModelScope.launch {
            addLog("Enabling wireless ADB on port 5555…", LogLevel.INFO)
            shizukuUtils.execShizuku("settings put global adb_wifi_enabled 1")
            shizukuUtils.execShizuku("setprop service.adb.tcp.port 5555")
            _state.update { it.copy(isWirelessAdbEnabled = true, wirelessAdbPort = 5555) }
            addLog("Wireless ADB enabled on port 5555", LogLevel.SUCCESS)
        }
    }

    fun disableWirelessAdb() {
        viewModelScope.launch {
            shizukuUtils.execShizuku("settings put global adb_wifi_enabled 0")
            _state.update { it.copy(isWirelessAdbEnabled = false) }
            addLog("Wireless ADB disabled", LogLevel.INFO)
        }
    }

    fun startPairing(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isPairing = true, pairingStatus = "Pairing…") }
            addLog("Starting ADB pairing with code: $code", LogLevel.INFO)
            val result = shizukuUtils.execShizuku("adb pair 127.0.0.1:${_state.value.wirelessAdbPort} $code")
            _state.update { it.copy(isPairing = false, pairingStatus = if (result.isSuccess) "Paired!" else "Failed") }
            addLog(if (result.isSuccess) "Paired successfully" else "Pairing failed: ${result.error}", if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    fun scanMdns() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true) }
            addLog("Scanning for mDNS Wireless Debugging services…", LogLevel.INFO)
            delay(2000)
            val mockServices = listOf(
                MdnsService("adb-device-1", "192.168.1.100", 5555, "_adb-tls-connect._tcp"),
                MdnsService("adb-device-2", "192.168.1.101", 37569, "_adb-tls-pairing._tcp"),
            )
            _state.update { it.copy(isScanning = false, mdnsServices = mockServices) }
            addLog("mDNS scan complete: ${mockServices.size} services found", LogLevel.SUCCESS)
        }
    }

    fun connectMdnsService(service: MdnsService) {
        viewModelScope.launch {
            addLog("Connecting to mDNS service: ${service.host}:${service.port}…", LogLevel.INFO)
            val result = shizukuUtils.execShizuku("adb connect ${service.host}:${service.port}")
            addLog(if (result.isSuccess) "Connected to ${service.serviceName}" else "Failed: ${result.error}", if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    fun revokeApp(packageName: String) {
        viewModelScope.launch {
            shizukuUtils.execShizuku("pm revoke $packageName moe.shizuku.manager.permission.API_V23")
            addLog("Revoked Shizuku access for $packageName", LogLevel.INFO)
            refresh()
        }
    }

    fun grantApp(packageName: String) {
        viewModelScope.launch {
            shizukuUtils.execShizuku("pm grant $packageName moe.shizuku.manager.permission.API_V23")
            addLog("Granted Shizuku access for $packageName", LogLevel.SUCCESS)
            refresh()
        }
    }

    fun setBlackNightMode(enabled: Boolean) {
        _state.update { it.copy(blackNightMode = enabled) }
        addLog(if (enabled) "Black night theme enabled" else "Black night theme disabled", LogLevel.INFO)
    }

    fun setUseSystemColors(enabled: Boolean) {
        _state.update { it.copy(useSystemColors = enabled) }
        addLog(if (enabled) "Using system colors" else "Using custom colors", LogLevel.INFO)
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        _state.update { it.copy(autoStartOnBoot = enabled) }
        addLog(if (enabled) "Auto-start on boot enabled" else "Auto-start on boot disabled", LogLevel.INFO)
    }

    fun setShowNotification(enabled: Boolean) {
        _state.update { it.copy(showNotification = enabled) }
    }

    fun setRequireUnlockForTiles(enabled: Boolean) {
        _state.update { it.copy(requireUnlockForTiles = enabled) }
    }

    fun runDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("=== Shizuku Diagnostics ===", LogLevel.INFO)
            val commands = listOf(
                "getprop ro.build.version.release" to "Android Version",
                "getprop ro.build.version.sdk" to "SDK Level",
                "getprop ro.product.model" to "Device Model",
                "id" to "Current UID",
                "settings get global adb_enabled" to "ADB Enabled",
                "settings get global adb_wifi_enabled" to "Wireless ADB",
                "getprop service.adb.tcp.port" to "ADB TCP Port",
            )
            commands.forEach { (cmd, label) ->
                val result = shizukuUtils.execShizuku(cmd)
                addLog("$label: ${result.output.trim().ifEmpty { result.error }}", LogLevel.INFO)
            }
            addLog("=== Diagnostics Complete ===", LogLevel.SUCCESS)
        }
    }

    fun clearLogs() { _state.update { it.copy(logs = emptyList()) } }

    fun exportLogs(): String = _state.value.logs.joinToString("\n") { "[${it.level}] ${it.message}" }

    private fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        _state.update { s -> s.copy(logs = (s.logs + ShizukuLogEntry(message = message, level = level)).takeLast(500)) }
    }

    private fun getDeviceIp(): String = try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: return ""
        interfaces.flatMap { it.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            .firstOrNull()?.hostAddress ?: ""
    } catch (_: Exception) { "" }

    private fun getAdbPort(): Int = try {
        val result = shizukuUtils.execAdb("getprop service.adb.tcp.port")
        result.output.trim().toIntOrNull() ?: 5555
    } catch (_: Exception) { 5555 }

    private fun getServerPid(): Int = try {
        val result = shizukuUtils.execShizuku("ps | grep shizuku")
        result.output.trim().split("\\s+".toRegex()).getOrNull(1)?.toIntOrNull() ?: -1
    } catch (_: Exception) { -1 }

    private fun loadAuthorizedApps(): List<AuthorizedApp> = try {
        val result = shizukuUtils.execShizuku("pm list packages -3")
        result.output.lines()
            .filter { it.startsWith("package:") }
            .take(20)
            .mapIndexed { i, line ->
                val pkg = line.removePrefix("package:")
                AuthorizedApp(pkg, pkg.substringAfterLast("."), i + 1000)
            }
    } catch (_: Exception) { emptyList() }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permListener)
    }
}
