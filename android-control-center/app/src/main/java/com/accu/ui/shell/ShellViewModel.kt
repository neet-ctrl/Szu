package com.accu.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.repositories.ShellRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class ShellUiState(
    val isRunning: Boolean = false,
    val isWifiConnected: Boolean = false,
    val connectedHost: String = "",
    val lastAnalyzedCommand: String = "",
    val wifiDevices: List<WifiDevice> = emptyList()
)

data class WifiDevice(val host: String, val port: Int, val isConnected: Boolean)

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val shellRepository: ShellRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    private val _output = MutableStateFlow<List<OutputLine>>(emptyList())
    val output: StateFlow<List<OutputLine>> = _output.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<String>>(emptyList())
    val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _commandExamples = MutableStateFlow<List<CommandExample>>(preloadedExamples())
    val commandExamples: StateFlow<List<CommandExample>> = _commandExamples.asStateFlow()

    private val _aiAnalysis = MutableStateFlow(AiAnalysisState())
    val aiAnalysis: StateFlow<AiAnalysisState> = _aiAnalysis.asStateFlow()

    private val lineIdCounter = AtomicLong(0)
    private var historyIndex = -1

    fun executeCommand(command: String, mode: ShellMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }
            addLine(OutputLine(lineIdCounter.incrementAndGet(), command, isCommand = true))
            addToHistory(command)
            historyIndex = -1
            try {
                val result = when (mode) {
                    ShellMode.LOCAL -> shellRepository.execute(command)
                    ShellMode.WIFI -> shellRepository.executeOnWifi(_uiState.value.connectedHost, command)
                    ShellMode.OTG -> shellRepository.executeOnOtg(command)
                }
                result.lines().forEach { line ->
                    if (line.isNotBlank()) addLine(OutputLine(lineIdCounter.incrementAndGet(), line))
                }
            } catch (e: Exception) {
                addLine(OutputLine(lineIdCounter.incrementAndGet(), "Error: ${e.message}", isError = true))
            } finally {
                _uiState.update { it.copy(isRunning = false) }
            }
        }
    }

    fun sendInterrupt() {
        viewModelScope.launch {
            shellRepository.sendInterrupt()
            _uiState.update { it.copy(isRunning = false) }
            addLine(OutputLine(lineIdCounter.incrementAndGet(), "^C", isError = true))
        }
    }

    fun connectWifi(host: String, port: Int) {
        viewModelScope.launch {
            addLine(OutputLine(lineIdCounter.incrementAndGet(), "Connecting to $host:$port…", isCommand = true))
            try {
                val success = shellRepository.connectWifi(host, port)
                if (success) {
                    _uiState.update { it.copy(isWifiConnected = true, connectedHost = "$host:$port") }
                    addLine(OutputLine(lineIdCounter.incrementAndGet(), "Connected to $host:$port"))
                } else {
                    addLine(OutputLine(lineIdCounter.incrementAndGet(), "Failed to connect to $host:$port", isError = true))
                }
            } catch (e: Exception) {
                addLine(OutputLine(lineIdCounter.incrementAndGet(), "Connection error: ${e.message}", isError = true))
            }
        }
    }

    fun disconnectWifi() {
        viewModelScope.launch {
            shellRepository.disconnectWifi()
            _uiState.update { it.copy(isWifiConnected = false, connectedHost = "") }
        }
    }

    fun showQrPairing() {}
    fun showCodePairing() {}

    fun clearOutput() {
        _output.value = emptyList()
    }

    fun updateSuggestions(input: String) {
        if (input.isBlank()) { _suggestions.value = emptyList(); return }
        val all = preloadedExamples().map { it.command }
        val filtered = all.filter { it.startsWith(input, ignoreCase = true) && it != input }.take(5)
        _suggestions.value = filtered
    }

    fun onTabComplete(current: String, onComplete: (String) -> Unit) {
        val matches = preloadedExamples().map { it.command }.filter { it.startsWith(current, ignoreCase = true) }
        if (matches.size == 1) onComplete(matches.first())
        else if (matches.isNotEmpty()) {
            val common = matches.reduce { acc, s -> acc.commonPrefixWith(s) }
            if (common.length > current.length) onComplete(common)
        }
    }

    fun navigateHistory(up: Boolean, onResult: (String) -> Unit) {
        val h = _commandHistory.value
        if (h.isEmpty()) return
        if (up && historyIndex < h.size - 1) historyIndex++
        else if (!up && historyIndex > -1) historyIndex--
        if (historyIndex >= 0 && historyIndex < h.size) onResult(h.reversed()[historyIndex])
    }

    fun addToHistory(command: String) {
        val current = _commandHistory.value.toMutableList()
        current.remove(command)
        current.add(0, command)
        _commandHistory.value = current.take(200)
    }

    fun deleteHistoryEntry(command: String) {
        _commandHistory.value = _commandHistory.value.filter { it != command }
    }

    fun clearHistory() {
        _commandHistory.value = emptyList()
        historyIndex = -1
    }

    fun addBookmark(command: String) {
        if (!_bookmarks.value.contains(command)) {
            _bookmarks.value = listOf(command) + _bookmarks.value
        }
    }

    fun deleteBookmark(command: String) {
        _bookmarks.value = _bookmarks.value.filter { it != command }
    }

    fun analyzeWithAi(command: String) {
        _uiState.update { it.copy(lastAnalyzedCommand = command) }
        _aiAnalysis.value = AiAnalysisState(isLoading = true)
        viewModelScope.launch {
            // Heuristic danger detection (matches aShellYou's DetectDangerLevelUseCase)
            val danger = when {
                command.contains("rm -rf") || command.contains("format") || command.contains("wipe") -> DangerLevel.CRITICAL
                command.contains("reboot") || command.contains("flash") -> DangerLevel.HIGH
                command.contains("pm disable") || command.contains("pm hide") -> DangerLevel.MODERATE
                else -> DangerLevel.SAFE
            }
            val explanation = analyzeCommand(command)
            val suggestions = generateSuggestions(command)
            _aiAnalysis.value = AiAnalysisState(
                isLoading = false,
                explanation = explanation,
                suggestions = suggestions,
                dangerLevel = danger
            )
        }
    }

    private fun analyzeCommand(cmd: String): String = when {
        cmd.startsWith("pm ") -> "Package Manager command. Manages app installation, permissions, and components."
        cmd.startsWith("am ") -> "Activity Manager command. Starts activities, services, and broadcasts."
        cmd.startsWith("wm ") -> "Window Manager command. Controls display density, resolution, and window settings."
        cmd.startsWith("settings ") -> "Reads or writes Android system settings (global/secure/system namespaces)."
        cmd.startsWith("dumpsys ") -> "Dumps service state information. Useful for debugging and monitoring."
        cmd.startsWith("input ") -> "Simulates touch/key input events on the device."
        cmd.startsWith("adb ") -> "ADB meta-command. Note: in shell mode, 'adb' prefix is not needed."
        cmd.startsWith("su ") || cmd == "su" -> "Requests superuser (root) shell. Requires rooted device."
        else -> "ADB shell command. Executes on the Android system."
    }

    private fun generateSuggestions(cmd: String): List<String> {
        if (cmd.contains("pm list") && !cmd.contains("-")) return listOf("pm list packages -3", "pm list packages -s", "pm list packages -d")
        if (cmd.contains("settings get") && cmd.split(" ").size < 4) return listOf("settings get global wifi_on", "settings get secure location_mode", "settings get system screen_brightness")
        return emptyList()
    }

    fun saveOutputToFile(filename: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            shellRepository.saveOutputToFile(filename, content)
        }
    }

    private fun addLine(line: OutputLine) {
        _output.update { current -> current + line }
    }

    companion object {
        fun preloadedExamples(): List<CommandExample> = listOf(
            // Package Management
            CommandExample("pm list packages", "List all installed packages", "Package Manager"),
            CommandExample("pm list packages -3", "List third-party packages only", "Package Manager"),
            CommandExample("pm list packages -s", "List system packages only", "Package Manager"),
            CommandExample("pm list packages -d", "List disabled packages", "Package Manager"),
            CommandExample("pm disable-user --user 0 <pkg>", "Disable a package for current user", "Package Manager"),
            CommandExample("pm enable <pkg>", "Re-enable a disabled package", "Package Manager"),
            CommandExample("pm hide --user 0 <pkg>", "Hide a package (freeze)", "Package Manager"),
            CommandExample("pm unhide --user 0 <pkg>", "Unhide a package", "Package Manager"),
            CommandExample("pm uninstall --user 0 <pkg>", "Uninstall package for current user (keeps data)", "Package Manager"),
            CommandExample("pm uninstall -k --user 0 <pkg>", "Uninstall keeping data", "Package Manager"),
            CommandExample("pm clear <pkg>", "Clear app data", "Package Manager"),
            CommandExample("pm grant <pkg> <permission>", "Grant runtime permission", "Package Manager"),
            CommandExample("pm revoke <pkg> <permission>", "Revoke runtime permission", "Package Manager"),
            CommandExample("pm list permission-groups", "List all permission groups", "Package Manager"),
            CommandExample("pm set-install-location 0", "Set install location to auto", "Package Manager"),
            CommandExample("pm get-install-location", "Get current install location", "Package Manager"),
            CommandExample("pm path <pkg>", "Get APK path of a package", "Package Manager"),
            CommandExample("cmd package compile -m everything -f <pkg>", "Force compile package (AOT)", "Package Manager"),
            // Activity Manager
            CommandExample("am start -n <pkg>/<activity>", "Start an activity", "Activity Manager"),
            CommandExample("am force-stop <pkg>", "Force stop an app", "Activity Manager"),
            CommandExample("am kill <pkg>", "Kill background app", "Activity Manager"),
            CommandExample("am broadcast -a android.intent.action.BOOT_COMPLETED", "Send boot broadcast", "Activity Manager"),
            CommandExample("am start -a android.settings.SETTINGS", "Open system settings", "Activity Manager"),
            CommandExample("am start -a android.settings.DEVELOPMENT_SETTINGS", "Open developer options", "Activity Manager"),
            CommandExample("am stack list", "List activity stacks", "Activity Manager"),
            CommandExample("am get-config", "Get current device configuration", "Activity Manager"),
            CommandExample("am monitor", "Monitor for crashes and ANRs", "Activity Manager"),
            // Window Manager
            CommandExample("wm density", "Get current screen density", "Window Manager"),
            CommandExample("wm density 420", "Set screen density to 420 dpi", "Window Manager"),
            CommandExample("wm density reset", "Reset density to default", "Window Manager"),
            CommandExample("wm size", "Get current screen resolution", "Window Manager"),
            CommandExample("wm size 1080x2340", "Set screen resolution", "Window Manager"),
            CommandExample("wm size reset", "Reset screen resolution", "Window Manager"),
            CommandExample("wm overscan 0,0,0,0", "Reset overscan margins", "Window Manager"),
            // Settings
            CommandExample("settings get global wifi_on", "Check if WiFi is enabled", "Settings"),
            CommandExample("settings put global wifi_on 1", "Enable WiFi via settings", "Settings"),
            CommandExample("settings get secure location_mode", "Get location mode", "Settings"),
            CommandExample("settings put secure location_mode 3", "Enable high-accuracy location", "Settings"),
            CommandExample("settings get system screen_brightness", "Get screen brightness value", "Settings"),
            CommandExample("settings put system screen_brightness 128", "Set brightness to 128 (0-255)", "Settings"),
            CommandExample("settings get secure bluetooth_on", "Check Bluetooth status", "Settings"),
            CommandExample("settings list global", "List all global settings", "Settings"),
            CommandExample("settings list secure", "List all secure settings", "Settings"),
            CommandExample("settings list system", "List all system settings", "Settings"),
            // Dumpsys
            CommandExample("dumpsys battery", "Get battery status and statistics", "Dumpsys"),
            CommandExample("dumpsys wifi", "Get WiFi status and connections", "Dumpsys"),
            CommandExample("dumpsys telephony.registry", "Get telephony/SIM information", "Dumpsys"),
            CommandExample("dumpsys activity | head -50", "Get activity manager state", "Dumpsys"),
            CommandExample("dumpsys meminfo <pkg>", "Get memory usage of a package", "Dumpsys"),
            CommandExample("dumpsys cpuinfo", "Get CPU usage info", "Dumpsys"),
            CommandExample("dumpsys diskstats", "Get disk statistics", "Dumpsys"),
            CommandExample("dumpsys notification", "Get notification manager state", "Dumpsys"),
            CommandExample("dumpsys display", "Get display manager info", "Dumpsys"),
            CommandExample("dumpsys audio", "Get audio focus and routing info", "Dumpsys"),
            CommandExample("dumpsys power", "Get power manager state", "Dumpsys"),
            CommandExample("dumpsys package <pkg>", "Get detailed package info", "Dumpsys"),
            CommandExample("dumpsys usagestats", "Get app usage statistics", "Dumpsys"),
            CommandExample("dumpsys deviceidle", "Get doze mode state", "Dumpsys"),
            CommandExample("dumpsys appops", "Get app ops (permissions) state", "Dumpsys"),
            // Input simulation
            CommandExample("input tap 540 960", "Tap at screen coordinates", "Input"),
            CommandExample("input swipe 100 900 100 300 500", "Swipe up (scroll down)", "Input"),
            CommandExample("input keyevent 26", "Power key event", "Input"),
            CommandExample("input keyevent 3", "Home key event", "Input"),
            CommandExample("input keyevent 4", "Back key event", "Input"),
            CommandExample("input keyevent 24", "Volume up key event", "Input"),
            CommandExample("input keyevent 25", "Volume down key event", "Input"),
            CommandExample("input keyevent 82", "Menu key event", "Input"),
            CommandExample("input text 'Hello World'", "Type text", "Input"),
            CommandExample("input keyevent --longpress 26", "Long press power key", "Input"),
            // Network
            CommandExample("ip addr show", "Show network interfaces and IPs", "Network"),
            CommandExample("ip route show", "Show routing table", "Network"),
            CommandExample("netstat -tulnp", "Show listening ports", "Network"),
            CommandExample("ss -tulnp", "Socket statistics", "Network"),
            CommandExample("nslookup google.com", "DNS lookup", "Network"),
            CommandExample("ping -c 4 8.8.8.8", "Ping Google DNS 4 times", "Network"),
            CommandExample("curl -s https://api.ipify.org", "Get public IP address", "Network"),
            CommandExample("iptables -L", "List firewall rules", "Network"),
            CommandExample("ifconfig wlan0", "Show wlan0 interface info", "Network"),
            CommandExample("cmd connectivity airplane-mode enable", "Enable airplane mode", "Network"),
            CommandExample("cmd connectivity airplane-mode disable", "Disable airplane mode", "Network"),
            CommandExample("svc wifi enable", "Enable WiFi via service command", "Network"),
            CommandExample("svc wifi disable", "Disable WiFi via service command", "Network"),
            CommandExample("svc data enable", "Enable mobile data", "Network"),
            CommandExample("svc data disable", "Disable mobile data", "Network"),
            // File System
            CommandExample("ls -la /sdcard/", "List files in external storage", "File System"),
            CommandExample("ls -la /data/data/", "List app data directories", "File System"),
            CommandExample("find /sdcard -name '*.apk'", "Find all APK files", "File System"),
            CommandExample("df -h", "Show disk usage", "File System"),
            CommandExample("du -sh /sdcard/*", "Show sizes of sdcard contents", "File System"),
            CommandExample("cat /proc/cpuinfo", "Get CPU information", "File System"),
            CommandExample("cat /proc/meminfo", "Get memory information", "File System"),
            CommandExample("cat /proc/version", "Get kernel version", "File System"),
            CommandExample("getprop", "List all system properties", "Properties"),
            CommandExample("getprop ro.build.version.release", "Get Android version", "Properties"),
            CommandExample("getprop ro.product.model", "Get device model", "Properties"),
            CommandExample("getprop ro.product.manufacturer", "Get device manufacturer", "Properties"),
            CommandExample("getprop gsm.network.type", "Get network type", "Properties"),
            CommandExample("setprop debug.hwui.overdraw show", "Enable overdraw highlighting", "Properties"),
            // Component management
            CommandExample("pm list packages | xargs -I{} pm dump {} | grep -E 'Activity|Service|Receiver'", "List all components", "Components"),
            CommandExample("pm disable <pkg>/<component>", "Disable an app component", "Components"),
            CommandExample("pm enable <pkg>/<component>", "Enable an app component", "Components"),
            // System
            CommandExample("reboot", "Reboot device", "System"),
            CommandExample("reboot recovery", "Reboot to recovery mode", "System"),
            CommandExample("reboot bootloader", "Reboot to bootloader/fastboot", "System"),
            CommandExample("reboot -p", "Power off device", "System"),
            CommandExample("screencap /sdcard/screenshot.png", "Take screenshot", "System"),
            CommandExample("screenrecord /sdcard/recording.mp4", "Record screen (Ctrl+C to stop)", "System"),
            CommandExample("logcat -d -v brief", "Dump recent logcat", "System"),
            CommandExample("logcat -s TAG", "Filter logcat by tag", "System"),
            CommandExample("logcat --pid=$(pidof -s <pkg>)", "Logcat for specific app", "System"),
            CommandExample("bugreport /sdcard/bugreport.zip", "Generate bug report", "System"),
            CommandExample("cmd notification post -S bigtext -t 'Test' 'Tag' 'Body'", "Send test notification", "System"),
            CommandExample("cmd statusbar expand-notifications", "Expand notification panel", "System"),
            CommandExample("cmd statusbar collapse", "Collapse notification panel", "System"),
            CommandExample("cmd alarm set 10 com.test/.Receiver", "Set alarm intent", "System"),
            CommandExample("service list", "List all running services", "System"),
            CommandExample("service check <name>", "Check if service is running", "System"),
            // Developer
            CommandExample("setprop debug.layout true", "Enable layout bounds overlay", "Developer"),
            CommandExample("setprop debug.layout false", "Disable layout bounds", "Developer"),
            CommandExample("cmd gpu overdraw --enable", "Enable GPU overdraw", "Developer"),
            CommandExample("settings put global animator_duration_scale 0", "Disable animations", "Developer"),
            CommandExample("settings put global animator_duration_scale 1", "Reset animations to normal", "Developer"),
            CommandExample("settings put global window_animation_scale 0", "Disable window animations", "Developer"),
            CommandExample("settings put global transition_animation_scale 0", "Disable transition animations", "Developer"),
            CommandExample("settings put secure show_ime_with_hard_keyboard 1", "Show IME with hardware keyboard", "Developer"),
            CommandExample("pm set-app-standby-bucket <pkg> active", "Set app standby bucket to active", "Developer"),
            CommandExample("dumpsys gfxinfo <pkg> reset", "Reset GPU profiling stats", "Developer")
        )
    }
}
