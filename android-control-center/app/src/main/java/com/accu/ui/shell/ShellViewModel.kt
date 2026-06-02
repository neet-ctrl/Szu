package com.accu.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val shizukuUtils: ShizukuUtils,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    private val _output = MutableStateFlow<List<OutputLine>>(emptyList())
    val output: StateFlow<List<OutputLine>> = _output.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

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
                val result = withContext(Dispatchers.IO) {
                    when (mode) {
                        // LOCAL — best path: LibSU root (uid=0), like Shizuku server via Binder
                        ShellMode.LOCAL -> shizukuUtils.execShizuku(command)
                        // WIFI / OTG — `adb` binary does not exist on Android.
                        // ACCU is the privileged server; it uses LibSU the same way
                        // aShell uses Shizuku. Fall through to execShizuku (root/plain shell).
                        ShellMode.WIFI, ShellMode.OTG -> shizukuUtils.execShizuku(command)
                    }
                }
                val combined = result.combinedOutput
                if (combined.isNotBlank()) {
                    combined.lines().forEach { line ->
                        if (line.isNotBlank()) addLine(OutputLine(lineIdCounter.incrementAndGet(), line, isError = result.exitCode != 0 && result.output.isBlank()))
                    }
                } else {
                    addLine(OutputLine(lineIdCounter.incrementAndGet(), "(no output)", isError = false))
                }
            } catch (e: Exception) {
                addLine(OutputLine(lineIdCounter.incrementAndGet(), "Error: ${e.message}", isError = true))
            } finally {
                _uiState.update { it.copy(isRunning = false) }
            }
        }
    }

    fun sendInterrupt() {
        _uiState.update { it.copy(isRunning = false) }
        addLine(OutputLine(lineIdCounter.incrementAndGet(), "^C", isError = true))
    }

    fun connectWifi(host: String, port: Int) {
        viewModelScope.launch {
            addLine(OutputLine(lineIdCounter.incrementAndGet(), "Connecting to $host:$port…", isCommand = true))
            // Root gives LOCAL privilege — it does NOT mean $host:$port is connected.
            // Always try actual adb connect + echo verification.
            val adb = connectionManager.findAdbBinary()
            if (adb != null) {
                withContext(Dispatchers.IO) { connectionManager.execPlainShell("$adb connect $host:$port") }
                // Verify with an actual shell command — "adb connect" output is unreliable
                val verify = withContext(Dispatchers.IO) {
                    connectionManager.execPlainShell("$adb -s $host:$port shell echo ACCU_OK 2>&1")
                }
                val verified = verify.output.trim() == "ACCU_OK"
                if (verified) {
                    _uiState.update { it.copy(isWifiConnected = true, connectedHost = "$host:$port") }
                    addLine(OutputLine(lineIdCounter.incrementAndGet(), "Connected and verified ✓  $host:$port"))
                    connectionManager.checkAndUpdateState()
                } else {
                    addLine(OutputLine(lineIdCounter.incrementAndGet(),
                        "Connection failed — device unreachable: ${verify.combinedOutput.take(120)}", isError = true))
                    _uiState.update { it.copy(isWifiConnected = false) }
                }
            } else {
                // No adb binary on this device — must be done from PC
                addLine(OutputLine(lineIdCounter.incrementAndGet(),
                    "No adb binary on this device. Run from your PC:\n  adb connect $host:$port", isError = false))
                _uiState.update { it.copy(isWifiConnected = false) }
            }
        }
    }

    fun disconnectWifi() {
        viewModelScope.launch {
            val host = _uiState.value.connectedHost
            val adb  = connectionManager.findAdbBinary()
            if (host.isNotEmpty() && adb != null) {
                withContext(Dispatchers.IO) { connectionManager.execPlainShell("$adb disconnect $host") }
            }
            _uiState.update { it.copy(isWifiConnected = false, connectedHost = "") }
        }
    }

    fun showQrPairing()   { _navigationEvent.tryEmit("adb_pairing") }
    fun showCodePairing() { _navigationEvent.tryEmit("adb_pairing") }

    fun clearOutput() { _output.value = emptyList() }

    fun updateSuggestions(input: String) {
        if (input.isBlank()) { _suggestions.value = emptyList(); return }
        val filtered = preloadedExamples().map { it.command }
            .filter { it.startsWith(input, ignoreCase = true) && it != input }.take(5)
        _suggestions.value = filtered
    }

    fun onTabComplete(current: String, onComplete: (String) -> Unit) {
        val matches = preloadedExamples().map { it.command }.filter { it.startsWith(current, ignoreCase = true) }
        when {
            matches.size == 1 -> onComplete(matches.first())
            matches.isNotEmpty() -> {
                val common = matches.reduce { acc, s -> acc.commonPrefixWith(s) }
                if (common.length > current.length) onComplete(common)
            }
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
            val danger = when {
                command.contains("rm -rf") || command.contains("format") || command.contains("wipe") -> DangerLevel.CRITICAL
                command.contains("reboot") || command.contains("flash") -> DangerLevel.HIGH
                command.contains("pm disable") || command.contains("pm hide") || command.contains("pm uninstall") -> DangerLevel.MODERATE
                command.contains("pm suspend") || command.contains("am force-stop") -> DangerLevel.MODERATE
                else -> DangerLevel.SAFE
            }
            val explanation = analyzeCommand(command)
            val suggestions = generateSuggestions(command)
            _aiAnalysis.value = AiAnalysisState(isLoading = false, explanation = explanation, suggestions = suggestions, dangerLevel = danger)
        }
    }

    private fun analyzeCommand(cmd: String): String = when {
        cmd.startsWith("pm ") -> "Package Manager command. Manages app installation, permissions, components, and lifecycle."
        cmd.startsWith("am ") -> "Activity Manager command. Starts activities, services, and sends broadcasts."
        cmd.startsWith("wm ") -> "Window Manager command. Controls display density, resolution, and window configuration."
        cmd.startsWith("settings ") -> "Reads or writes Android system settings (global/secure/system namespaces)."
        cmd.startsWith("dumpsys ") -> "Dumps service state. Use for debugging battery, memory, WiFi, activity state, and more."
        cmd.startsWith("input ") -> "Simulates user touch and key input events on the device."
        cmd.startsWith("svc ") -> "Service command. Directly controls WiFi, mobile data, Bluetooth, NFC, power, USB mode."
        cmd.startsWith("appops ") -> "App operations command. Manages per-app special permission modes (allow/deny/ignore)."
        cmd.startsWith("device_config ") -> "Device configuration flags. Manages Android runtime feature flags by namespace."
        cmd.startsWith("cmd ") -> "Interacts directly with Android system services (bluetooth, statusbar, uimode, etc.)."
        cmd.startsWith("content ") -> "Content provider interface. Query, insert, or delete content URIs directly."
        cmd.startsWith("getprop") -> "Gets system properties. Read-only system configuration key-value pairs."
        cmd.startsWith("setprop") -> "Sets system properties. Some may require elevated privileges."
        cmd.startsWith("logcat") -> "Android log viewer. Shows live system and app debug output."
        cmd.startsWith("reboot") -> "Reboots the device. Use with caution — all unsaved data will be lost."
        cmd.startsWith("screencap") -> "Captures a screenshot to the specified file path."
        cmd.startsWith("screenrecord") -> "Records device screen to MP4. Press Ctrl+C or wait for time limit to stop."
        cmd.startsWith("monkey") -> "Stress testing tool. Sends random events to an app to test stability."
        cmd.startsWith("ls") -> "Lists directory contents. Use -la for detailed view including hidden files."
        cmd.startsWith("cat") -> "Outputs file contents to the terminal."
        cmd.startsWith("ip ") -> "IP routing/interface management. View addresses, routes, and rules."
        cmd.startsWith("netstat") || cmd.startsWith("ss ") -> "Shows network connections and listening ports."
        else -> "ADB shell command. Executes in the Android shell with ADB shell privileges (uid=2000)."
    }

    private fun generateSuggestions(cmd: String): List<String> {
        if (cmd.contains("pm list") && !cmd.contains("-")) return listOf("pm list packages -3", "pm list packages -s", "pm list packages -d", "pm list packages -e", "pm list packages -f")
        if (cmd.contains("settings get") && cmd.split(" ").size < 4) return listOf("settings get global wifi_on", "settings get secure location_mode", "settings get system screen_brightness")
        if (cmd.contains("dumpsys") && cmd.split(" ").size < 3) return listOf("dumpsys battery", "dumpsys wifi", "dumpsys meminfo", "dumpsys cpuinfo", "dumpsys activity top")
        if (cmd.contains("svc") && cmd.split(" ").size < 3) return listOf("svc wifi enable", "svc wifi disable", "svc data enable", "svc data disable", "svc bluetooth enable")
        if (cmd.contains("appops") && cmd.split(" ").size < 3) return listOf("appops get <package>", "appops set <package> <op> allow", "appops reset <package>")
        if (cmd.contains("am start") && !cmd.contains("-a") && !cmd.contains("-n")) return listOf("am start -n <package>/<activity>", "am start -a android.intent.action.VIEW -d <uri>")
        if (cmd.contains("wm ") && cmd.split(" ").size < 3) return listOf("wm density", "wm size", "wm density reset", "wm size reset")
        return emptyList()
    }

    fun saveOutputToUri(context: android.content.Context, treeUri: android.net.Uri, filename: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw Exception("Cannot open chosen folder")
                val existing = docDir.findFile(filename)
                existing?.delete()
                val docFile = docDir.createFile("text/plain", filename)
                    ?: throw Exception("Cannot create file in chosen folder")
                context.contentResolver.openOutputStream(docFile.uri)?.use { out ->
                    out.write(content.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    addLine(OutputLine(lineIdCounter.incrementAndGet(), "Saved '$filename' to chosen folder ✓"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLine(OutputLine(lineIdCounter.incrementAndGet(), "Save failed: ${e.message}", isError = true))
                }
            }
        }
    }

    private fun addLine(line: OutputLine) {
        _output.update { current ->
            val next = current + line
            if (next.size > 1500) next.takeLast(1500) else next
        }
    }

    companion object {
        fun preloadedExamples(): List<CommandExample> = PRELOADED_COMMANDS
    }
}
