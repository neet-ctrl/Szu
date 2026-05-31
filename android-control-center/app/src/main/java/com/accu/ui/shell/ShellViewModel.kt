package com.accu.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                val result = withContext(Dispatchers.IO) {
                    when (mode) {
                        ShellMode.LOCAL -> shizukuUtils.execShizuku(command)
                        ShellMode.WIFI -> shizukuUtils.execAdb(
                            "adb -s ${_uiState.value.connectedHost} shell $command"
                        )
                        ShellMode.OTG -> shizukuUtils.execAdb("adb shell $command")
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
            val result = withContext(Dispatchers.IO) {
                shizukuUtils.execAdb("adb connect $host:$port")
            }
            val success = result.isSuccess && result.combinedOutput.contains("connected", ignoreCase = true)
            if (success) {
                _uiState.update { it.copy(isWifiConnected = true, connectedHost = "$host:$port") }
                addLine(OutputLine(lineIdCounter.incrementAndGet(), "Connected to $host:$port"))
            } else {
                addLine(OutputLine(lineIdCounter.incrementAndGet(), "Failed: ${result.combinedOutput}", isError = true))
            }
        }
    }

    fun disconnectWifi() {
        viewModelScope.launch {
            val host = _uiState.value.connectedHost
            if (host.isNotEmpty()) {
                withContext(Dispatchers.IO) { shizukuUtils.execAdb("adb disconnect $host") }
            }
            _uiState.update { it.copy(isWifiConnected = false, connectedHost = "") }
        }
    }

    fun showQrPairing() {}
    fun showCodePairing() {}

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
        else -> "ADB shell command. Executes in the Android shell with Shizuku-level privileges (uid=2000)."
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

    fun saveOutputToFile(filename: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File("/sdcard/$filename")
                file.writeText(content)
                withContext(Dispatchers.Main) {
                    addLine(OutputLine(lineIdCounter.incrementAndGet(), "Saved to /sdcard/$filename"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLine(OutputLine(lineIdCounter.incrementAndGet(), "Save failed: ${e.message}", isError = true))
                }
            }
        }
    }

    private fun addLine(line: OutputLine) {
        _output.update { current -> current + line }
    }

    companion object {
        fun preloadedExamples(): List<CommandExample> = listOf(
            // ── Activity Manager (am) ───────────────────────────────────
            CommandExample("am broadcast -a <action>", "Sends a broadcast intent with the specified action.", "Activity Manager"),
            CommandExample("am force-stop <package>", "Force stops a specific package, terminating its processes.", "Activity Manager"),
            CommandExample("am kill <package>", "Kills the background processes of a specific package.", "Activity Manager"),
            CommandExample("am kill-all", "Kills all background processes.", "Activity Manager"),
            CommandExample("am start -n <package>/<activity>", "Starts a specific activity of an application.", "Activity Manager"),
            CommandExample("am start -a android.intent.action.VIEW -d <uri>", "Opens a URI using the VIEW intent action.", "Activity Manager"),
            CommandExample("am startservice <package>/<service>", "Starts a specific service of an application.", "Activity Manager"),
            CommandExample("am stopservice <package>/<service>", "Stops a specific running service.", "Activity Manager"),
            CommandExample("am start -a android.settings.SETTINGS", "Opens the system Settings app.", "Activity Manager"),
            CommandExample("am start -a android.settings.DEVELOPMENT_SETTINGS", "Opens Developer Options.", "Activity Manager"),
            CommandExample("am stack list", "Lists all activity stacks.", "Activity Manager"),
            CommandExample("am get-config", "Gets the current device configuration.", "Activity Manager"),
            CommandExample("am monitor", "Monitors for crashes and ANRs in real-time.", "Activity Manager"),
            CommandExample("am dumpheap <pid> /sdcard/heap.hprof", "Dumps heap of a process to a file.", "Activity Manager"),
            CommandExample("am crash <package>", "Forces an app to crash (for testing).", "Activity Manager"),
            // ── App Ops ─────────────────────────────────────────────────
            CommandExample("appops get <package>", "Gets the app operations (permissions state) for a package.", "App Ops"),
            CommandExample("appops set <package> <operation> <mode>", "Sets an app operation mode (allow/deny/ignore).", "App Ops"),
            CommandExample("appops reset <package>", "Resets all app operations for a package to defaults.", "App Ops"),
            CommandExample("cmd appops set <package> <op> allow", "Allows a specific app op via cmd interface.", "App Ops"),
            CommandExample("cmd appops set <package> <op> deny", "Denies a specific app op.", "App Ops"),
            CommandExample("cmd appops set <package> <op> ignore", "Ignores a specific app op.", "App Ops"),
            // ── File System ──────────────────────────────────────────────
            CommandExample("cat <file_path>", "Displays the contents of a file.", "File System"),
            CommandExample("cd <directory_path>", "Changes the current directory.", "File System"),
            CommandExample("cd /", "Changes to the root directory.", "File System"),
            CommandExample("cd ~", "Changes to the home directory.", "File System"),
            CommandExample("cd ..", "Moves up one directory level.", "File System"),
            CommandExample("cd -", "Changes to the previous directory.", "File System"),
            CommandExample("cp <from> <to>", "Copies a file or directory.", "File System"),
            CommandExample("cp -r <from> <to>", "Recursively copies a directory and its contents.", "File System"),
            CommandExample("mv <from> <to>", "Moves or renames a file or directory.", "File System"),
            CommandExample("rm <file_path>", "Deletes a file.", "File System"),
            CommandExample("rm -rf <path>", "Recursively and forcefully deletes a file or directory.", "File System"),
            CommandExample("rmdir <directory_path>", "Deletes an empty directory.", "File System"),
            CommandExample("mkdir <file_path>", "Creates a new directory.", "File System"),
            CommandExample("mkdir -p <path>", "Creates a directory and any necessary parent directories.", "File System"),
            CommandExample("ls", "Lists files and directories in the current path.", "File System"),
            CommandExample("ls -la", "Lists all files including hidden ones with details.", "File System"),
            CommandExample("ls -R", "Recursively lists files and directories.", "File System"),
            CommandExample("ls -s", "Lists files with their sizes.", "File System"),
            CommandExample("ls -la /sdcard/", "Lists files in external storage with details.", "File System"),
            CommandExample("ls -la /data/data/", "Lists all app data directories.", "File System"),
            CommandExample("find <path> -name <pattern>", "Searches for files matching a name pattern.", "File System"),
            CommandExample("find /sdcard -name '*.apk'", "Finds all APK files on sdcard.", "File System"),
            CommandExample("du -h", "Displays disk usage of files and directories.", "File System"),
            CommandExample("du -sh /sdcard/*", "Shows sizes of all sdcard contents.", "File System"),
            CommandExample("du -sh /system/*", "Shows disk usage for /system.", "File System"),
            CommandExample("df -h /system", "Displays disk usage for the /system partition.", "File System"),
            CommandExample("stat <file_path>", "Displays detailed status information about a file.", "File System"),
            CommandExample("file <file_path>", "Determines the type of a file.", "File System"),
            CommandExample("md5sum <file_path>", "Computes the MD5 hash of a file.", "File System"),
            CommandExample("wc -l <file_path>", "Counts the number of lines in a file.", "File System"),
            CommandExample("touch <file_path>", "Creates a new empty file or updates timestamp.", "File System"),
            CommandExample("grep", "Searches for a pattern in files or input.", "File System"),
            CommandExample("umount <mount_point>", "Unmounts a filesystem.", "File System"),
            CommandExample("pwd", "Displays the current working directory.", "File System"),
            // ── CMD ──────────────────────────────────────────────────────
            CommandExample("cmd activity", "Interacts with the activity manager service.", "CMD"),
            CommandExample("cmd bluetooth_manager enable", "Enables Bluetooth.", "CMD"),
            CommandExample("cmd bluetooth_manager disable", "Disables Bluetooth.", "CMD"),
            CommandExample("cmd notification", "Interacts with the notification manager service.", "CMD"),
            CommandExample("cmd package compile -m speed -f <package>", "Force compiles a package with speed optimization.", "CMD"),
            CommandExample("cmd package compile -m everything -f <package>", "Force compiles a package (AOT, everything).", "CMD"),
            CommandExample("cmd statusbar expand-notifications", "Expands the notification shade.", "CMD"),
            CommandExample("cmd statusbar expand-settings", "Expands the quick settings panel.", "CMD"),
            CommandExample("cmd statusbar collapse", "Collapses the status bar.", "CMD"),
            CommandExample("cmd uimode night no", "Disables night mode in the system UI.", "CMD"),
            CommandExample("cmd uimode night yes", "Enables night mode in the system UI.", "CMD"),
            CommandExample("cmd connectivity airplane-mode enable", "Enables airplane mode.", "CMD"),
            CommandExample("cmd connectivity airplane-mode disable", "Disables airplane mode.", "CMD"),
            CommandExample("cmd notification post -S bigtext -t 'Test' 'Tag' 'Body'", "Posts a test notification.", "CMD"),
            CommandExample("cmd locale set-app-locales <package> --locales <locale>", "Sets per-app locale (Android 13+).", "CMD"),
            // ── Content Provider ─────────────────────────────────────────
            CommandExample("content query --uri content://settings/system", "Queries system settings via content provider.", "Content"),
            CommandExample("content insert --uri <uri> --bind <key>:<type>:<value>", "Inserts a value into a content provider.", "Content"),
            CommandExample("content delete --uri <uri>", "Deletes entries from a content provider.", "Content"),
            // ── Device Config ────────────────────────────────────────────
            CommandExample("device_config list <namespace>", "Lists all device config flags in a namespace.", "Device Config"),
            CommandExample("device_config get <namespace> <key>", "Gets a specific device config flag value.", "Device Config"),
            CommandExample("device_config put <namespace> <key> <value>", "Sets a device config flag value.", "Device Config"),
            // ── Dumpsys ──────────────────────────────────────────────────
            CommandExample("dumpsys activity", "Displays activity and task stack information.", "Dumpsys"),
            CommandExample("dumpsys activity top", "Displays the currently running top activity.", "Dumpsys"),
            CommandExample("dumpsys alarm", "Displays all pending alarms.", "Dumpsys"),
            CommandExample("dumpsys battery", "Displays battery status and charging info.", "Dumpsys"),
            CommandExample("dumpsys battery set level <n>", "Sets the battery level for testing.", "Dumpsys"),
            CommandExample("dumpsys battery set status <n>", "Sets battery charging status for testing.", "Dumpsys"),
            CommandExample("dumpsys battery reset", "Resets the battery statistics.", "Dumpsys"),
            CommandExample("dumpsys connectivity", "Displays network connectivity state.", "Dumpsys"),
            CommandExample("dumpsys cpuinfo", "Displays CPU usage information.", "Dumpsys"),
            CommandExample("dumpsys display", "Displays display system information.", "Dumpsys"),
            CommandExample("dumpsys input", "Displays input device and event info.", "Dumpsys"),
            CommandExample("dumpsys meminfo", "Displays memory usage for all processes.", "Dumpsys"),
            CommandExample("dumpsys meminfo <package>", "Displays detailed memory for a specific package.", "Dumpsys"),
            CommandExample("dumpsys netstats", "Displays network usage statistics.", "Dumpsys"),
            CommandExample("dumpsys notification", "Displays notification system state.", "Dumpsys"),
            CommandExample("dumpsys package <package>", "Displays detailed info about a package.", "Dumpsys"),
            CommandExample("dumpsys power", "Displays power management and wake locks.", "Dumpsys"),
            CommandExample("dumpsys usagestats", "Displays app usage statistics.", "Dumpsys"),
            CommandExample("dumpsys wifi", "Displays Wi-Fi state and info.", "Dumpsys"),
            CommandExample("dumpsys window", "Displays window manager information.", "Dumpsys"),
            CommandExample("dumpsys audio", "Displays audio focus and routing.", "Dumpsys"),
            CommandExample("dumpsys telephony.registry", "Displays telephony/SIM information.", "Dumpsys"),
            CommandExample("dumpsys diskstats", "Displays disk statistics.", "Dumpsys"),
            CommandExample("dumpsys appops", "Displays app ops state.", "Dumpsys"),
            CommandExample("dumpsys gfxinfo <package> reset", "Resets GPU profiling stats.", "Dumpsys"),
            CommandExample("dumpsys deviceidle", "Displays doze mode state.", "Dumpsys"),
            // ── Input ────────────────────────────────────────────────────
            CommandExample("input keyevent <keycode>", "Simulates pressing a hardware key.", "Input"),
            CommandExample("input keyevent 26", "Simulates pressing the Power button.", "Input"),
            CommandExample("input keyevent 3", "Simulates pressing the Home button.", "Input"),
            CommandExample("input keyevent 4", "Simulates pressing the Back button.", "Input"),
            CommandExample("input keyevent 24", "Simulates pressing Volume Up.", "Input"),
            CommandExample("input keyevent 25", "Simulates pressing Volume Down.", "Input"),
            CommandExample("input keyevent 187", "Simulates pressing the Recents button.", "Input"),
            CommandExample("input keyevent 223", "Puts the device to sleep.", "Input"),
            CommandExample("input keyevent 224", "Wakes the device up.", "Input"),
            CommandExample("input keyevent 82", "Opens the menu / locks the device.", "Input"),
            CommandExample("input keyevent --longpress 26", "Long presses the Power button.", "Input"),
            CommandExample("input tap <x> <y>", "Simulates a screen tap at coordinates.", "Input"),
            CommandExample("input swipe <x1> <y1> <x2> <y2>", "Simulates a swipe gesture.", "Input"),
            CommandExample("input swipe <x1> <y1> <x2> <y2> <duration_ms>", "Swipe with specified duration.", "Input"),
            CommandExample("input text <text>", "Types text on the focused input field.", "Input"),
            // ── Network ──────────────────────────────────────────────────
            CommandExample("ip addr", "Displays IP addresses on all network interfaces.", "Network"),
            CommandExample("ip route", "Displays the routing table.", "Network"),
            CommandExample("ip rule", "Displays routing policy rules.", "Network"),
            CommandExample("iptables -L", "Lists all iptables firewall rules.", "Network"),
            CommandExample("ifconfig", "Displays network interface configurations.", "Network"),
            CommandExample("netstat", "Displays network connections and statistics.", "Network"),
            CommandExample("ping -c 4 8.8.8.8", "Pings Google DNS 4 times.", "Network"),
            CommandExample("curl -s https://api.ipify.org", "Gets the public IP address.", "Network"),
            CommandExample("nslookup google.com", "Performs a DNS lookup.", "Network"),
            CommandExample("ss -tulnp", "Shows socket statistics (listening ports).", "Network"),
            // ── Package Manager (pm) ─────────────────────────────────────
            CommandExample("pm clear <package>", "Clears the data and cache of a package.", "Package Manager"),
            CommandExample("pm disable-user --user 0 <package>", "Disables a package for the current user.", "Package Manager"),
            CommandExample("pm disable <package/component>", "Disables a specific component.", "Package Manager"),
            CommandExample("pm enable <package/component>", "Enables a specific component.", "Package Manager"),
            CommandExample("pm grant <package> <permission>", "Grants a permission to a package.", "Package Manager"),
            CommandExample("pm hide --user 0 <package>", "Hides a package from the launcher.", "Package Manager"),
            CommandExample("pm unhide --user 0 <package>", "Unhides a previously hidden package.", "Package Manager"),
            CommandExample("pm install <apk_path>", "Installs an APK from the specified path.", "Package Manager"),
            CommandExample("pm install -r <apk_path>", "Reinstalls an existing app, keeping its data.", "Package Manager"),
            CommandExample("pm install -d <apk_path>", "Allows version code downgrade when installing.", "Package Manager"),
            CommandExample("pm install -g <apk_path>", "Installs APK and grants all runtime permissions.", "Package Manager"),
            CommandExample("pm list features", "Lists all hardware and software features.", "Package Manager"),
            CommandExample("pm list instrumentation", "Lists all test instrumentation packages.", "Package Manager"),
            CommandExample("pm list libraries", "Lists all shared libraries on the device.", "Package Manager"),
            CommandExample("pm list packages", "Lists all installed packages.", "Package Manager"),
            CommandExample("pm list packages -3", "Lists only third-party (user-installed) packages.", "Package Manager"),
            CommandExample("pm list packages -s", "Lists only system packages.", "Package Manager"),
            CommandExample("pm list packages -d", "Lists only disabled packages.", "Package Manager"),
            CommandExample("pm list packages -e", "Lists only enabled packages.", "Package Manager"),
            CommandExample("pm list packages -f", "Lists packages with their APK file paths.", "Package Manager"),
            CommandExample("pm list permissions", "Lists all permissions on the device.", "Package Manager"),
            CommandExample("pm list users", "Lists all user profiles on the device.", "Package Manager"),
            CommandExample("pm path <package>", "Displays the APK file path of a package.", "Package Manager"),
            CommandExample("pm revoke <package> <permission>", "Revokes a permission from a package.", "Package Manager"),
            CommandExample("pm set-install-location <location>", "Sets default install location (0=auto, 1=internal, 2=external).", "Package Manager"),
            CommandExample("pm get-install-location", "Gets the current default install location.", "Package Manager"),
            CommandExample("pm suspend --user 0 <package>", "Suspends a package, making it unusable.", "Package Manager"),
            CommandExample("pm unsuspend --user 0 <package>", "Unsuspends a previously suspended package.", "Package Manager"),
            CommandExample("pm trim-caches <desired_free_space>", "Trims cache files to reach desired free space.", "Package Manager"),
            CommandExample("pm uninstall <package>", "Fully uninstalls a package from the device.", "Package Manager"),
            CommandExample("pm uninstall -k <package>", "Uninstalls a package but keeps its data.", "Package Manager"),
            CommandExample("pm uninstall --user 0 <package>", "Uninstalls for current user (removes bloatware without root).", "Package Manager"),
            CommandExample("pm uninstall -k --user 0 <package>", "Uninstalls for current user while keeping data.", "Package Manager"),
            CommandExample("pm set-app-standby-bucket <package> active", "Sets app standby bucket to active (prevents battery restrictions).", "Package Manager"),
            CommandExample("pm list permission-groups", "Lists all permission groups.", "Package Manager"),
            // ── System Properties ────────────────────────────────────────
            CommandExample("getprop", "Lists all system properties.", "Properties"),
            CommandExample("getprop <property>", "Gets a specific system property value.", "Properties"),
            CommandExample("getprop ro.build.version.sdk", "Displays the Android SDK version.", "Properties"),
            CommandExample("getprop ro.build.display.id", "Displays the build display ID.", "Properties"),
            CommandExample("getprop ro.build.version.release", "Gets the Android version number.", "Properties"),
            CommandExample("getprop ro.product.model", "Displays the device model name.", "Properties"),
            CommandExample("getprop ro.product.manufacturer", "Displays the device manufacturer.", "Properties"),
            CommandExample("getprop ro.serialno", "Displays the device serial number.", "Properties"),
            CommandExample("getprop gsm.network.type", "Gets the current cellular network type.", "Properties"),
            CommandExample("setprop <property> <value>", "Sets a system property.", "Properties"),
            CommandExample("setprop debug.layout true", "Enables layout bounds overlay for debugging.", "Properties"),
            CommandExample("setprop debug.layout false", "Disables layout bounds overlay.", "Properties"),
            CommandExample("setprop debug.hwui.overdraw show", "Enables GPU overdraw highlighting.", "Properties"),
            // ── Settings ─────────────────────────────────────────────────
            CommandExample("settings get <namespace> <key>", "Gets the value of a specific system setting.", "Settings"),
            CommandExample("settings list <namespace>", "Lists all settings in a namespace (system/secure/global).", "Settings"),
            CommandExample("settings put <namespace> <key> <value>", "Sets the value of a specific system setting.", "Settings"),
            CommandExample("settings delete <namespace> <key>", "Deletes a specific system setting.", "Settings"),
            CommandExample("settings list global", "Lists all global settings.", "Settings"),
            CommandExample("settings list secure", "Lists all secure settings.", "Settings"),
            CommandExample("settings list system", "Lists all system settings.", "Settings"),
            CommandExample("settings get global wifi_on", "Checks if WiFi is enabled.", "Settings"),
            CommandExample("settings put global wifi_on 1", "Enables WiFi via settings.", "Settings"),
            CommandExample("settings get secure location_mode", "Gets the current location mode.", "Settings"),
            CommandExample("settings put secure location_mode 3", "Enables high-accuracy location.", "Settings"),
            CommandExample("settings get system screen_brightness", "Gets the current screen brightness.", "Settings"),
            CommandExample("settings put system screen_brightness <0-255>", "Sets the screen brightness (0=darkest, 255=brightest).", "Settings"),
            CommandExample("settings put system screen_off_timeout <ms>", "Sets the screen timeout in milliseconds.", "Settings"),
            CommandExample("settings put global animator_duration_scale <value>", "Sets animator duration scale (0=off, 0.5x, 1x).", "Settings"),
            CommandExample("settings put global transition_animation_scale <value>", "Sets transition animation scale.", "Settings"),
            CommandExample("settings put global window_animation_scale <value>", "Sets window animation scale.", "Settings"),
            CommandExample("settings put secure enabled_accessibility_services <service>", "Enables an accessibility service.", "Settings"),
            CommandExample("settings put global adb_enabled <0|1>", "Enables or disables ADB.", "Settings"),
            CommandExample("settings put global development_settings_enabled <0|1>", "Enables or disables developer options.", "Settings"),
            CommandExample("settings get secure bluetooth_on", "Checks Bluetooth status.", "Settings"),
            CommandExample("settings put global adb_wifi_enabled 1", "Enables wireless ADB.", "Settings"),
            // ── SVC ──────────────────────────────────────────────────────
            CommandExample("svc bluetooth enable", "Enables Bluetooth.", "SVC"),
            CommandExample("svc bluetooth disable", "Disables Bluetooth.", "SVC"),
            CommandExample("svc data enable", "Enables mobile data.", "SVC"),
            CommandExample("svc data disable", "Disables mobile data.", "SVC"),
            CommandExample("svc nfc enable", "Enables NFC.", "SVC"),
            CommandExample("svc nfc disable", "Disables NFC.", "SVC"),
            CommandExample("svc power stayon true", "Keeps the screen always on while connected.", "SVC"),
            CommandExample("svc power stayon false", "Restores normal screen timeout.", "SVC"),
            CommandExample("svc power reboot", "Reboots the device via the power service.", "SVC"),
            CommandExample("svc power shutdown", "Shuts down the device.", "SVC"),
            CommandExample("svc usb setFunctions <function>", "Sets USB mode (mtp/ptp/rndis/midi/etc.).", "SVC"),
            CommandExample("svc wifi enable", "Enables Wi-Fi.", "SVC"),
            CommandExample("svc wifi disable", "Disables Wi-Fi.", "SVC"),
            // ── System ───────────────────────────────────────────────────
            CommandExample("reboot", "Reboots the device.", "System"),
            CommandExample("reboot bootloader", "Reboots into the bootloader (fastboot) mode.", "System"),
            CommandExample("reboot recovery", "Reboots into recovery mode.", "System"),
            CommandExample("reboot -p", "Powers off the device.", "System"),
            CommandExample("screencap <file_path>", "Captures a screenshot to the specified path.", "System"),
            CommandExample("screencap -p /sdcard/screenshot.png", "Captures a PNG screenshot to sdcard.", "System"),
            CommandExample("screenrecord /sdcard/recording.mp4", "Records the device screen.", "System"),
            CommandExample("screenrecord --time-limit <seconds> /sdcard/recording.mp4", "Records for a specified duration.", "System"),
            CommandExample("screenrecord --size <width>x<height> /sdcard/recording.mp4", "Records at a specific resolution.", "System"),
            CommandExample("service list", "Lists all running system services.", "System"),
            CommandExample("service check <name>", "Checks if a specific service is running.", "System"),
            CommandExample("logcat", "Displays system logs.", "System"),
            CommandExample("logcat -g", "Displays the size of the log buffer.", "System"),
            CommandExample("logcat -G <size>", "Sets the size of the log buffer.", "System"),
            CommandExample("logcat -c", "Clears the log buffer.", "System"),
            CommandExample("logcat -d -v brief", "Dumps recent logcat with brief format.", "System"),
            CommandExample("logcat -s TAG", "Filters logcat by tag.", "System"),
            CommandExample("logcat --pid=$(pidof -s <package>)", "Shows logcat for a specific app.", "System"),
            CommandExample("dmesg", "Displays kernel messages.", "System"),
            CommandExample("ps", "Displays information about running processes.", "System"),
            CommandExample("top", "Displays running processes in real-time.", "System"),
            CommandExample("top -n 1", "Displays a single snapshot of running processes.", "System"),
            CommandExample("kill <pid>", "Terminates a process by PID.", "System"),
            CommandExample("id", "Displays the current user ID and groups.", "System"),
            CommandExample("whoami", "Displays the current user.", "System"),
            CommandExample("uname -a", "Displays system information.", "System"),
            CommandExample("uptime", "Displays device uptime since last reboot.", "System"),
            CommandExample("date", "Displays the current date and time.", "System"),
            CommandExample("getenforce", "Displays the current SELinux mode.", "System"),
            CommandExample("su", "Switches to superuser mode (root).", "System"),
            CommandExample("exit", "Exits the current shell session.", "System"),
            CommandExample("clear", "Clears the terminal screen.", "System"),
            CommandExample("echo <message>", "Prints a message to the terminal.", "System"),
            CommandExample("sleep <seconds>", "Pauses execution for a specified number of seconds.", "System"),
            CommandExample("bugreport /sdcard/bugreport.zip", "Generates a full bug report.", "System"),
            CommandExample("cat /proc/cpuinfo", "Gets CPU information.", "System"),
            CommandExample("cat /proc/meminfo", "Gets memory information.", "System"),
            CommandExample("cat /proc/version", "Gets the kernel version.", "System"),
            // ── Window Manager (wm) ──────────────────────────────────────
            CommandExample("wm density", "Gets the current screen density.", "Window Manager"),
            CommandExample("wm density <dpi>", "Sets the screen density to a custom DPI.", "Window Manager"),
            CommandExample("wm density reset", "Resets the screen density to default.", "Window Manager"),
            CommandExample("wm size", "Gets the current screen resolution.", "Window Manager"),
            CommandExample("wm size <width>x<height>", "Sets the screen resolution.", "Window Manager"),
            CommandExample("wm size reset", "Resets the screen resolution to default.", "Window Manager"),
            CommandExample("wm overscan <left>,<top>,<right>,<bottom>", "Sets overscan margins.", "Window Manager"),
            CommandExample("wm overscan reset", "Resets overscan to default.", "Window Manager"),
            // ── Developer / Testing ──────────────────────────────────────
            CommandExample("monkey -p <package> -v <count>", "Generates random user events for stress testing.", "Developer"),
            CommandExample("cmd gpu overdraw --enable", "Enables GPU overdraw highlighting.", "Developer"),
            CommandExample("settings put global animator_duration_scale 0", "Disables all animations.", "Developer"),
            CommandExample("settings put global animator_duration_scale 1", "Resets animations to normal speed.", "Developer"),
            CommandExample("settings put global window_animation_scale 0", "Disables window animations.", "Developer"),
            CommandExample("settings put global transition_animation_scale 0", "Disables transition animations.", "Developer"),
            CommandExample("settings put secure show_ime_with_hard_keyboard 1", "Shows IME with hardware keyboard.", "Developer"),
        )
    }
}
