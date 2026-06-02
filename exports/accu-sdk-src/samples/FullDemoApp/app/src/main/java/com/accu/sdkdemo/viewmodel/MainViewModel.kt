package com.accu.sdkdemo.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accu.sdk.*
import com.accu.sdkdemo.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val accu = AccuClient(app)
    val accuState: StateFlow<AccuConnectionState> = accu.state
    val logs = LogManager.logs
    val crashes = CrashManager.crashes

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _shellOutput = MutableStateFlow("")
    val shellOutput: StateFlow<String> = _shellOutput.asStateFlow()
    private val _isShellRunning = MutableStateFlow(false)
    val isShellRunning: StateFlow<Boolean> = _isShellRunning.asStateFlow()

    private val _diagnostics = MutableStateFlow(buildInitialDiagnostics())
    val diagnostics: StateFlow<List<DiagnosticItem>> = _diagnostics.asStateFlow()
    private val _isDiagRunning = MutableStateFlow(false)
    val isDiagRunning: StateFlow<Boolean> = _isDiagRunning.asStateFlow()

    private val _testResults = MutableStateFlow(buildInitialTests())
    val testResults: StateFlow<List<TestResult>> = _testResults.asStateFlow()
    private val _isTestRunning = MutableStateFlow(false)
    val isTestRunning: StateFlow<Boolean> = _isTestRunning.asStateFlow()
    val testProgress = MutableStateFlow(0f)

    private val _apiExplorerResult = MutableStateFlow<Pair<String, String>?>(null)
    val apiExplorerResult: StateFlow<Pair<String, String>?> = _apiExplorerResult.asStateFlow()

    private val _permOpsResult = MutableStateFlow("")
    val permOpsResult: StateFlow<String> = _permOpsResult.asStateFlow()

    private val _settingsResult = MutableStateFlow<Map<String, String>>(emptyMap())
    val settingsResult: StateFlow<Map<String, String>> = _settingsResult.asStateFlow()

    private val _localeResult = MutableStateFlow("")
    val localeResult: StateFlow<String> = _localeResult.asStateFlow()

    init {
        accu.connect()
        LogManager.info("Connection", "AccuClient.connect() called — waiting for service")
        loadApps()
    }

    override fun onCleared() {
        accu.disconnect()
        LogManager.info("Connection", "AccuClient.disconnect() — ViewModel cleared")
    }

    // ── Connection ────────────────────────────────────────────────────────────

    fun connect() { LogManager.info("Connection", "Manual connect()"); accu.connect() }

    fun disconnect() { LogManager.info("Connection", "Manual disconnect()"); accu.disconnect() }

    fun reconnect() {
        LogManager.info("Connection", "Manual reconnect()")
        accu.disconnect()
        viewModelScope.launch { delay(300); accu.connect() }
    }

    fun ping() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { try { accu.ping() } catch (e: Exception) { CrashManager.record("ping()", e); false } }
            LogManager.log("Ping", if (ok) "Service alive — binder healthy" else "FAILED — service may be dead", if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    fun verifyEnvironment() {
        runDiagnostics()
    }

    // ── Permission ────────────────────────────────────────────────────────────

    fun requestPermission() {
        viewModelScope.launch {
            LogManager.info("Permission", "Requesting ACCU permission — dialog will appear")
            val result = try { accu.requestPermission() } catch (e: Exception) { CrashManager.record("requestPermission()", e); AccuConstants.PERMISSION_SERVICE_UNAVAILABLE }
            LogManager.log("Permission", "Result: ${result.toPermissionLabel()}", if (result.isGranted()) LogLevel.SUCCESS else LogLevel.WARNING)
        }
    }

    fun checkPermission() {
        val code = accu.checkPermission()
        LogManager.log("Permission", "checkPermission() = ${code.toPermissionLabel()}", if (code.isGranted()) LogLevel.SUCCESS else LogLevel.WARNING)
    }

    fun revokeSelf() {
        accu.revokeSelf()
        LogManager.warning("Permission", "revokeSelf() called — must request permission again")
    }

    fun checkAllScopes() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AccuScopes.ALL_SCOPES.forEach { scope ->
                    val has = try { accu.hasScope(scope) } catch (_: Exception) { false }
                    LogManager.log("Scope", "$scope → ${if (has) "GRANTED" else "NOT GRANTED"}", if (has) LogLevel.SUCCESS else LogLevel.WARNING)
                }
            }
        }
    }

    // ── Shell ─────────────────────────────────────────────────────────────────

    fun execShell(command: String) {
        if (command.isBlank()) return
        _isShellRunning.value = true
        _shellOutput.value = ""
        LogManager.info("Shell", "exec: $command")
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { accu.exec(command) }
                _shellOutput.value = buildString {
                    appendLine("$ $command")
                    appendLine()
                    if (result.stdout.isNotBlank()) { appendLine("── STDOUT ──"); appendLine(result.stdout.trim()) }
                    if (result.stderr.isNotBlank()) { appendLine(); appendLine("── STDERR ──"); appendLine(result.stderr.trim()) }
                    appendLine(); append("Exit Code: ${result.exitCode}")
                }
                LogManager.log("Shell", "exit=${result.exitCode} | ${result.stdout.take(80).trim()}", if (result.isSuccess) LogLevel.SUCCESS else LogLevel.WARNING)
            } catch (e: Exception) {
                _shellOutput.value = "ERROR: ${e.message}"
                CrashManager.record("exec(\"$command\")", e)
            } finally { _isShellRunning.value = false }
        }
    }

    fun execShellAsync(command: String) {
        if (command.isBlank()) return
        _isShellRunning.value = true
        _shellOutput.value = ""
        LogManager.info("Shell", "execAsync: $command")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    accu.execAsync(command,
                        onStdout = { line -> viewModelScope.launch(Dispatchers.Main) { _shellOutput.value += "$line\n" } },
                        onStderr = { line -> viewModelScope.launch(Dispatchers.Main) { _shellOutput.value += "[ERR] $line\n" } },
                        onExit   = { code -> viewModelScope.launch(Dispatchers.Main) {
                            _shellOutput.value += "\n─── Exit: $code ───"
                            _isShellRunning.value = false
                            LogManager.log("Shell", "execAsync done exit=$code", if (code == 0) LogLevel.SUCCESS else LogLevel.WARNING)
                        }}
                    )
                }
            } catch (e: Exception) {
                _shellOutput.value += "\nERROR: ${e.message}"
                _isShellRunning.value = false
                CrashManager.record("execAsync(\"$command\")", e)
            }
        }
    }

    fun clearShellOutput() { _shellOutput.value = "" }

    // ── Package Manager ───────────────────────────────────────────────────────

    fun loadApps() {
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            _apps.value = withContext(Dispatchers.IO) {
                try {
                    pm.getInstalledPackages(0)
                        .filter { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                        .map { pkg -> AppInfo(pkg.packageName, pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName, pkg.versionName ?: "?",
                            if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()) }
                        .sortedBy { it.label.lowercase() }
                } catch (_: Exception) { emptyList() }
            }
            LogManager.debug("PM", "Loaded ${_apps.value.size} user-installed apps")
        }
    }

    fun enablePackage(pkg: String)    { pmAction("enablePackage",    pkg) { accu.enablePackage(pkg) } }
    fun disablePackage(pkg: String)   { pmAction("disablePackage",   pkg) { accu.disablePackage(pkg) } }
    fun forceStop(pkg: String)        { pmAction("forceStop",        pkg) { accu.forceStop(pkg) } }
    fun clearData(pkg: String)        { pmAction("clearPackageData", pkg) { accu.clearPackageData(pkg) } }
    fun hidePackage(pkg: String)      { pmAction("hidePackage",      pkg) { accu.hidePackage(pkg) } }
    fun unhidePackage(pkg: String)    { pmAction("unhidePackage",    pkg) { accu.unhidePackage(pkg) } }
    fun suspendPackage(pkg: String)   { pmAction("suspendPackage",   pkg) { accu.suspendPackage(pkg) } }
    fun unsuspendPackage(pkg: String) { pmAction("unsuspendPackage", pkg) { accu.unsuspendPackage(pkg) } }

    private fun pmAction(name: String, pkg: String, block: () -> Boolean) {
        viewModelScope.launch {
            val ok = safeApiCall("$name($pkg)") { block() } ?: return@launch
            LogManager.log("PM", "$name($pkg): ${if (ok) "SUCCESS" else "FAILED"}", if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    // ── Permission Ops ────────────────────────────────────────────────────────

    fun grantPermission(pkg: String, perm: String) {
        viewModelScope.launch {
            val ok = safeApiCall("grantPermission") { accu.grantPermission(pkg, perm) } ?: return@launch
            val msg = "grantPermission($pkg, $perm): ${if (ok) "SUCCESS" else "FAILED"}"
            _permOpsResult.value = msg
            LogManager.log("Perms", msg, if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    fun revokePermission(pkg: String, perm: String) {
        viewModelScope.launch {
            val ok = safeApiCall("revokePermission") { accu.revokePermission(pkg, perm) } ?: return@launch
            val msg = "revokePermission($pkg, $perm): ${if (ok) "SUCCESS" else "FAILED"}"
            _permOpsResult.value = msg
            LogManager.log("Perms", msg, if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    fun getAppOp(pkg: String, op: String) {
        viewModelScope.launch {
            val result = safeApiCall("getAppOp") { accu.getAppOp(pkg, op) } ?: return@launch
            val msg = "getAppOp($pkg, $op) = $result"
            _permOpsResult.value = msg; LogManager.info("AppOp", msg)
        }
    }

    fun setAppOp(pkg: String, op: String, mode: String) {
        viewModelScope.launch {
            val ok = safeApiCall("setAppOp") { accu.setAppOp(pkg, op, mode) } ?: return@launch
            val msg = "setAppOp($pkg, $op, $mode): ${if (ok) "SUCCESS" else "FAILED"}"
            _permOpsResult.value = msg
            LogManager.log("AppOp", msg, if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun readSetting(category: String, key: String) {
        viewModelScope.launch {
            val value = safeApiCall("read${category}Setting") {
                when (category) { "Secure" -> accu.readSecureSetting(key); "Global" -> accu.readGlobalSetting(key); else -> accu.readSystemSetting(key) }
            } ?: return@launch
            _settingsResult.update { it + ("$category:$key" to value.ifBlank { "(empty)" }) }
            LogManager.info("Settings", "read $category/$key = ${value.ifBlank { "(empty)" }}")
        }
    }

    fun writeSetting(category: String, key: String, value: String) {
        viewModelScope.launch {
            val ok = safeApiCall("write${category}Setting") {
                when (category) { "Secure" -> accu.writeSecureSetting(key, value); "Global" -> accu.writeGlobalSetting(key, value); else -> accu.writeSystemSetting(key, value) }
            } ?: return@launch
            _settingsResult.update { it + ("$category:$key:write" to if (ok) "SUCCESS" else "FAILED") }
            LogManager.log("Settings", "write $category/$key=$value: ${if (ok) "SUCCESS" else "FAILED"}", if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    // ── Locale ────────────────────────────────────────────────────────────────

    fun setLocale(pkg: String, locale: String) {
        viewModelScope.launch {
            val ok = safeApiCall("setApplicationLocale") { accu.setApplicationLocale(pkg, locale) } ?: return@launch
            val desc = if (locale.isEmpty()) "system default" else locale
            val msg = "setLocale($pkg → $desc): ${if (ok) "SUCCESS" else "FAILED"}"
            _localeResult.value = msg
            LogManager.log("Locale", msg, if (ok) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    // ── API Explorer ──────────────────────────────────────────────────────────

    fun executeApiMethod(method: ApiMethod) {
        viewModelScope.launch {
            LogManager.info("APIExplorer", "Testing ${method.name}…")
            val result = withContext(Dispatchers.IO) {
                try {
                    when (method.name) {
                        "ping"              -> accu.ping().toString()
                        "getVersion"        -> accu.getVersion().toString()
                        "getUid"            -> accu.getUid().toString()
                        "getPid"            -> accu.getPid().toString()
                        "getAccuVersion"    -> accu.getAccuVersion()
                        "checkPermission"   -> accu.checkPermission().toPermissionLabel()
                        "hasScope(SHELL)"          -> accu.hasScope(AccuScopes.SHELL).toString()
                        "hasScope(PACKAGE_MANAGE)" -> accu.hasScope(AccuScopes.PACKAGE_MANAGE).toString()
                        "hasScope(PERMISSIONS)"    -> accu.hasScope(AccuScopes.PERMISSIONS).toString()
                        "hasScope(SETTINGS)"       -> accu.hasScope(AccuScopes.SETTINGS).toString()
                        "hasScope(LOCALE)"         -> accu.hasScope(AccuScopes.LOCALE).toString()
                        "exec"             -> { val r = accu.exec("id"); "stdout=${r.stdout.trim()} exit=${r.exitCode}" }
                        "execAndGetOutput" -> accu.execAndGetOutput("echo ACCU_API_EXPLORER_OK")
                        "readSecureSetting"-> accu.readSecureSetting("bluetooth_on")
                        "readGlobalSetting"-> accu.readGlobalSetting("adb_enabled")
                        "readSystemSetting"-> accu.readSystemSetting("screen_brightness")
                        else               -> "(requires target package — see LogCenter for details)"
                    }
                } catch (e: Exception) { CrashManager.record("APIExplorer.${method.name}", e); "ERROR: ${e.javaClass.simpleName}: ${e.message}" }
            }
            _apiExplorerResult.value = method.name to result
            LogManager.info("APIExplorer", "${method.name} → $result")
        }
    }

    // ── Connection Diagnostics ────────────────────────────────────────────────

    fun runDiagnostics() {
        if (_isDiagRunning.value) return
        _isDiagRunning.value = true
        LogManager.info("Diagnostics", "Starting deep connection diagnostics…")
        viewModelScope.launch {
            val items = buildInitialDiagnostics().toMutableList()
            _diagnostics.value = items.map { it.copy(status = DiagnosticStatus.CHECKING) }

            fun update(id: Int, status: DiagnosticStatus, detail: String) {
                items[id] = items[id].copy(status = status, detail = detail)
                _diagnostics.value = items.toList()
            }

            withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                delay(80)
                val installed = try { pm.getPackageInfo(AccuConstants.ACCU_PACKAGE, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
                update(0, if (installed) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (installed) AccuConstants.ACCU_PACKAGE else "Not installed")

                delay(80)
                update(1, if (installed) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (installed) "<queries> block effective" else "Add <queries> block to AndroidManifest")

                delay(80)
                val state = accu.state.value
                val connected = state is AccuConnectionState.Connected
                update(2, if (connected) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (connected) "IAccuService binder OK" else "$state")

                delay(80)
                val alive = try { accu.ping() } catch (_: Exception) { false }
                update(3, if (alive) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (alive) "ping() = true" else "Dead / not connected")

                delay(80)
                val ver = try { accu.getVersion() } catch (_: Exception) { -1 }
                update(4, if (ver == AccuConstants.PROTOCOL_VERSION) DiagnosticStatus.PASS else if (ver > 0) DiagnosticStatus.WARNING else DiagnosticStatus.FAIL, "version=$ver (expected ${AccuConstants.PROTOCOL_VERSION})")

                delay(80)
                val perm = try { accu.checkPermission() } catch (_: Exception) { AccuConstants.PERMISSION_SERVICE_UNAVAILABLE }
                update(5, if (perm.isGranted()) DiagnosticStatus.PASS else DiagnosticStatus.WARNING, perm.toPermissionLabel())

                delay(80)
                val scopes = AccuScopes.ALL_SCOPES.filter { try { accu.hasScope(it) } catch (_: Exception) { false } }
                update(6, if (scopes.size == AccuScopes.ALL_SCOPES.size) DiagnosticStatus.PASS else DiagnosticStatus.WARNING, "Granted: ${scopes.ifEmpty { listOf("none") }.joinToString()}")

                delay(80)
                val accuVer = try { accu.getAccuVersion() } catch (_: Exception) { "" }
                update(7, if (accuVer.isNotBlank() && accuVer != "unknown") DiagnosticStatus.PASS else DiagnosticStatus.WARNING, "ACCU $accuVer")

                delay(80)
                val uid = try { accu.getUid() } catch (_: Exception) { -1 }
                val backend = when (uid) { 0 -> "Root"; 2000 -> "Shizuku/shell"; else -> "uid=$uid" }
                update(8, if (uid >= 0) DiagnosticStatus.PASS else DiagnosticStatus.WARNING, backend)

                delay(80)
                val pid = try { accu.getPid() } catch (_: Exception) { -1 }
                update(9, if (pid > 0) DiagnosticStatus.PASS else DiagnosticStatus.WARNING, if (pid > 0) "PID: $pid" else "unavailable")
            }
            _isDiagRunning.value = false
            val pass = _diagnostics.value.count { it.status == DiagnosticStatus.PASS }
            LogManager.log("Diagnostics", "Done: $pass/${_diagnostics.value.size} passed", if (pass == _diagnostics.value.size) LogLevel.SUCCESS else LogLevel.WARNING)
        }
    }

    // ── Automated Tests ───────────────────────────────────────────────────────

    fun runFullValidation() {
        if (_isTestRunning.value) return
        _isTestRunning.value = true
        testProgress.value = 0f
        _testResults.value = buildInitialTests()
        LogManager.info("AutoTest", "Full validation suite starting…")
        viewModelScope.launch {
            val tests = buildInitialTests().toMutableList()
            val total = tests.size.toFloat()

            suspend fun runTest(id: Int, block: suspend () -> Pair<TestStatus, String>) {
                tests[id] = tests[id].copy(status = TestStatus.RUNNING); _testResults.value = tests.toList(); delay(150)
                try {
                    val (s, d) = block(); tests[id] = tests[id].copy(status = s, detail = d)
                    LogManager.log("AutoTest", "[${s.name}] ${tests[id].name}: $d", when (s) { TestStatus.PASS -> LogLevel.SUCCESS; TestStatus.FAIL -> LogLevel.ERROR; else -> LogLevel.WARNING })
                } catch (e: Exception) {
                    tests[id] = tests[id].copy(status = TestStatus.FAIL, detail = "${e.javaClass.simpleName}: ${e.message}")
                    CrashManager.record("AutoTest.${tests[id].name}", e)
                }
                _testResults.value = tests.toList(); testProgress.value = (id + 1) / total
            }

            withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                runTest(0) { try { pm.getPackageInfo(AccuConstants.ACCU_PACKAGE, 0); TestStatus.PASS to AccuConstants.ACCU_PACKAGE } catch (_: PackageManager.NameNotFoundException) { TestStatus.FAIL to "ACCU not installed" } }
                runTest(1) { val s = accu.state.value; if (s is AccuConnectionState.Connected) TestStatus.PASS to "ACCU ${s.accuVersion}" else TestStatus.FAIL to "$s" }
                runTest(2) { val ok = accu.ping(); if (ok) TestStatus.PASS to "alive" else TestStatus.FAIL to "ping failed" }
                runTest(3) { val v = accu.getVersion(); if (v == AccuConstants.PROTOCOL_VERSION) TestStatus.PASS to "v$v" else TestStatus.WARNING to "got=$v expected=${AccuConstants.PROTOCOL_VERSION}" }
                runTest(4) { val v = accu.getAccuVersion(); if (v.isNotBlank() && v != "unknown") TestStatus.PASS to "ACCU $v" else TestStatus.WARNING to "unknown" }
                runTest(5) { val c = accu.checkPermission(); if (c.isGranted()) TestStatus.PASS to "GRANTED" else TestStatus.FAIL to c.toPermissionLabel() }
                runTest(6) { val h = accu.hasScope(AccuScopes.SHELL); if (h) TestStatus.PASS to "SHELL granted" else TestStatus.FAIL to "missing" }
                runTest(7) { val h = accu.hasScope(AccuScopes.PACKAGE_MANAGE); if (h) TestStatus.PASS to "granted" else TestStatus.FAIL to "missing" }
                runTest(8) { val h = accu.hasScope(AccuScopes.PERMISSIONS); if (h) TestStatus.PASS to "granted" else TestStatus.FAIL to "missing" }
                runTest(9) { val h = accu.hasScope(AccuScopes.SETTINGS); if (h) TestStatus.PASS to "granted" else TestStatus.FAIL to "missing" }
                runTest(10) { val h = accu.hasScope(AccuScopes.LOCALE); if (h) TestStatus.PASS to "granted" else TestStatus.FAIL to "missing" }
                runTest(11) { val r = accu.exec("echo ACCU_TEST_OK"); if (r.isSuccess && r.stdout.contains("ACCU_TEST_OK")) TestStatus.PASS to "exit=0" else TestStatus.FAIL to "exit=${r.exitCode}" }
                runTest(12) { val r = accu.exec("id"); if (r.isSuccess) TestStatus.PASS to r.stdout.trim().take(60) else TestStatus.FAIL to "exit=${r.exitCode}" }
                runTest(13) { val v = accu.readSecureSetting("bluetooth_on"); TestStatus.PASS to "bluetooth_on=${v.ifBlank { "(empty)" }}" }
                runTest(14) { val v = accu.readGlobalSetting("adb_enabled"); TestStatus.PASS to "adb_enabled=${v.ifBlank { "(empty)" }}" }
                runTest(15) { val v = accu.readSystemSetting("screen_brightness"); TestStatus.PASS to "brightness=${v.ifBlank { "(empty)" }}" }
                runTest(16) { val uid = accu.getUid(); if (uid >= 0) TestStatus.PASS to "uid=$uid (${when(uid){ 0->"root"; 2000->"shizuku"; else->"other" }})" else TestStatus.WARNING to "uid=$uid" }
            }

            _isTestRunning.value = false; testProgress.value = 1f
            val pass = _testResults.value.count { it.status == TestStatus.PASS }
            val fail = _testResults.value.count { it.status == TestStatus.FAIL }
            LogManager.log("AutoTest", "DONE — $pass PASS | $fail FAIL | ${_testResults.value.count { it.status == TestStatus.WARNING }} WARN", if (fail == 0) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun buildDiagnosticsReport(): String {
        val state = accu.state.value
        return buildString {
            appendLine("=".repeat(60)); appendLine("  ACCU SDK Test App — Diagnostics Report"); appendLine("=".repeat(60))
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine()
            appendLine("── DEVICE ──")
            appendLine("Manufacturer: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("App: ${getApplication<Application>().packageName}")
            appendLine()
            appendLine("── CONNECTION ──")
            appendLine("State: $state")
            if (state is AccuConnectionState.Connected) {
                appendLine("ACCU Version: ${state.accuVersion}"); appendLine("Protocol: v${state.serviceVersion}"); appendLine("Permission: ${state.permissionCode.toPermissionLabel()}")
            }
            appendLine()
            appendLine("── SCOPES ──")
            AccuScopes.ALL_SCOPES.forEach { s -> appendLine("  $s: ${if (try { accu.hasScope(s) } catch (_: Exception) { false }) "GRANTED" else "NOT GRANTED"}") }
            appendLine()
            appendLine("── DIAGNOSTICS ──")
            _diagnostics.value.forEach { d -> appendLine("  [${d.status.name.padEnd(8)}] ${d.name}: ${d.detail}") }
            appendLine()
            appendLine("── TEST RESULTS ──")
            _testResults.value.forEach { t -> appendLine("  [${t.status.name.padEnd(7)}] ${t.name}: ${t.detail}") }
            appendLine()
            appendLine("── LOG SUMMARY ──")
            appendLine("Total entries: ${LogManager.logs.value.size}  |  Crashes: ${CrashManager.crashes.value.size}")
            appendLine()
            appendLine("── FULL LOG (${LogManager.logs.value.size} entries) ──")
            LogManager.logs.value.forEach { log -> appendLine("  [${log.formattedDate}] [${log.level.name.padEnd(7)}] [${log.tag}] ${log.message}") }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun <T> safeApiCall(label: String, block: suspend () -> T): T? =
        try { withContext(Dispatchers.IO) { block() } }
        catch (e: Exception) { CrashManager.record(label, e); LogManager.error(label, "${e.javaClass.simpleName}: ${e.message}"); null }

    private fun buildInitialDiagnostics() = listOf(
        DiagnosticItem(0, "ACCU Installed",       "PackageManager.getPackageInfo(com.accu.controlcenter)"),
        DiagnosticItem(1, "Package Visibility",   "<queries> entry in AndroidManifest lets your app see ACCU"),
        DiagnosticItem(2, "Binder Connected",     "bindService() → onServiceConnected() fired, IAccuService obtained"),
        DiagnosticItem(3, "Binder Alive",         "ping() called on live binder — must return true"),
        DiagnosticItem(4, "Protocol Version",     "getVersion() — expected ${AccuConstants.PROTOCOL_VERSION}"),
        DiagnosticItem(5, "Permission Granted",   "checkPermission() — must be PERMISSION_GRANTED (0)"),
        DiagnosticItem(6, "Scopes Available",     "hasScope() checked for all 5 scopes (SHELL, PKG, PERMS, SETTINGS, LOCALE)"),
        DiagnosticItem(7, "ACCU App Version",     "getAccuVersion() — readable non-empty string"),
        DiagnosticItem(8, "Backend Type",         "getUid() — uid=0 means root, uid=2000 means Shizuku/ADB"),
        DiagnosticItem(9, "Service PID",          "getPid() — positive PID of AccuSystemService process"),
    )

    private fun buildInitialTests() = listOf(
        TestResult(0,  "ACCU Installed",           "com.accu.controlcenter found"),
        TestResult(1,  "Service Connected",         "bindService() + onServiceConnected()"),
        TestResult(2,  "Ping",                      "ping() == true"),
        TestResult(3,  "Protocol Version",          "getVersion() == ${AccuConstants.PROTOCOL_VERSION}"),
        TestResult(4,  "ACCU Version Readable",     "getAccuVersion() not blank"),
        TestResult(5,  "Permission Granted",        "checkPermission() == 0"),
        TestResult(6,  "SHELL Scope",               "hasScope(SHELL) == true"),
        TestResult(7,  "PACKAGE_MANAGE Scope",      "hasScope(PACKAGE_MANAGE) == true"),
        TestResult(8,  "PERMISSIONS Scope",         "hasScope(PERMISSIONS) == true"),
        TestResult(9,  "SETTINGS Scope",            "hasScope(SETTINGS) == true"),
        TestResult(10, "LOCALE Scope",              "hasScope(LOCALE) == true"),
        TestResult(11, "Shell exec()",              "exec(\"echo ACCU_TEST_OK\") → exit 0"),
        TestResult(12, "Shell id command",          "exec(\"id\") returns UID info"),
        TestResult(13, "Read Secure Setting",       "readSecureSetting(\"bluetooth_on\")"),
        TestResult(14, "Read Global Setting",       "readGlobalSetting(\"adb_enabled\")"),
        TestResult(15, "Read System Setting",       "readSystemSetting(\"screen_brightness\")"),
        TestResult(16, "UID / Backend Type",        "getUid() → 0=root | 2000=shizuku"),
    )

    fun allApiMethods() = listOf(
        ApiMethod("ping",               "ping(): Boolean",                    "Returns true if service is alive",                   "None",           5,  "Identity"),
        ApiMethod("getVersion",         "getVersion(): Int",                  "IPC protocol version (currently 1)",                 "None",           1,  "Identity"),
        ApiMethod("getUid",             "getUid(): Int",                      "UID of ACCU process (0=root, 2000=shizuku)",         "None",           2,  "Identity"),
        ApiMethod("getPid",             "getPid(): Int",                      "PID of AccuSystemService process",                   "None",           3,  "Identity"),
        ApiMethod("getAccuVersion",     "getAccuVersion(): String",           "Human-readable ACCU app version string",             "None",           4,  "Identity"),
        ApiMethod("checkPermission",    "checkPermission(): Int",             "Check permission code without dialog",               "None",           11, "Permission"),
        ApiMethod("hasScope(SHELL)",    "hasScope(scope): Boolean",           "Check if SHELL scope granted",                       "None",           12, "Permission"),
        ApiMethod("hasScope(PACKAGE_MANAGE)", "hasScope(scope): Boolean",    "Check if PACKAGE_MANAGE scope granted",              "None",           12, "Permission"),
        ApiMethod("hasScope(PERMISSIONS)",    "hasScope(scope): Boolean",    "Check if PERMISSIONS scope granted",                 "None",           12, "Permission"),
        ApiMethod("hasScope(SETTINGS)", "hasScope(scope): Boolean",          "Check if SETTINGS scope granted",                    "None",           12, "Permission"),
        ApiMethod("hasScope(LOCALE)",   "hasScope(scope): Boolean",          "Check if LOCALE scope granted",                      "None",           12, "Permission"),
        ApiMethod("revokeSelf",         "revokeSelf()",                       "Revoke your own ACCU permission",                    "None",           13, "Permission"),
        ApiMethod("exec",               "exec(cmd): String[3]",               "Run shell command, returns [stdout, stderr, exit]",  "SHELL",          20, "Shell"),
        ApiMethod("execAsync",          "execAsync(cmd, callback)",           "Stream shell output line-by-line via callback",      "SHELL",          21, "Shell"),
        ApiMethod("execAndGetOutput",   "execAndGetOutput(cmd): String",      "Run command, return combined output",                "SHELL",          22, "Shell"),
        ApiMethod("installApk",         "installApk(path, installer): Boolean","Install APK from absolute filesystem path",        "PACKAGE_MANAGE", 30, "Package Manager"),
        ApiMethod("uninstallPackage",   "uninstallPackage(pkg): Boolean",     "Uninstall package, data removed",                   "PACKAGE_MANAGE", 31, "Package Manager"),
        ApiMethod("uninstallKeepData",  "uninstallKeepData(pkg): Boolean",    "Uninstall, keep app data",                           "PACKAGE_MANAGE", 32, "Package Manager"),
        ApiMethod("enablePackage",      "enablePackage(pkg): Boolean",        "Re-enable a disabled package",                       "PACKAGE_MANAGE", 33, "Package Manager"),
        ApiMethod("disablePackage",     "disablePackage(pkg): Boolean",       "Disable package (pm disable-user)",                  "PACKAGE_MANAGE", 34, "Package Manager"),
        ApiMethod("hidePackage",        "hidePackage(pkg): Boolean",          "Hide package completely (pm hide)",                  "PACKAGE_MANAGE", 35, "Package Manager"),
        ApiMethod("unhidePackage",      "unhidePackage(pkg): Boolean",        "Restore hidden package",                             "PACKAGE_MANAGE", 36, "Package Manager"),
        ApiMethod("suspendPackage",     "suspendPackage(pkg): Boolean",       "Suspend package — greyed out, not openable",         "PACKAGE_MANAGE", 37, "Package Manager"),
        ApiMethod("unsuspendPackage",   "unsuspendPackage(pkg): Boolean",     "Remove suspension",                                  "PACKAGE_MANAGE", 38, "Package Manager"),
        ApiMethod("clearPackageData",   "clearPackageData(pkg): Boolean",     "Clear all app data (pm clear)",                      "PACKAGE_MANAGE", 39, "Package Manager"),
        ApiMethod("enableComponent",    "enableComponent(pkg, comp): Boolean","Enable specific Activity/Service/Receiver",          "PACKAGE_MANAGE", 40, "Package Manager"),
        ApiMethod("disableComponent",   "disableComponent(pkg, comp): Boolean","Disable specific component",                        "PACKAGE_MANAGE", 41, "Package Manager"),
        ApiMethod("forceStop",          "forceStop(pkg): Boolean",            "Force-stop package (am force-stop)",                 "PACKAGE_MANAGE", 60, "Package Manager"),
        ApiMethod("grantPermission",    "grantPermission(pkg, perm): Boolean","Grant runtime permission to package",                "PERMISSIONS",    50, "Permissions"),
        ApiMethod("revokePermission",   "revokePermission(pkg, perm): Boolean","Revoke runtime permission",                        "PERMISSIONS",    51, "Permissions"),
        ApiMethod("setAppOp",           "setAppOp(pkg, op, mode): Boolean",   "Set App Op mode (allow/deny/ignore/default)",        "PERMISSIONS",    52, "Permissions"),
        ApiMethod("getAppOp",           "getAppOp(pkg, op): String",          "Get current App Op mode",                            "PERMISSIONS",    53, "Permissions"),
        ApiMethod("setApplicationLocale","setApplicationLocale(pkg, locale): Boolean","Set per-app locale override",                "LOCALE",         61, "Locale"),
        ApiMethod("writeSecureSetting", "writeSecureSetting(name, val): Boolean","Write to Settings.Secure",                       "SETTINGS",       70, "Settings"),
        ApiMethod("readSecureSetting",  "readSecureSetting(name): String",    "Read from Settings.Secure",                          "SETTINGS",       71, "Settings"),
        ApiMethod("writeGlobalSetting", "writeGlobalSetting(name, val): Boolean","Write to Settings.Global",                       "SETTINGS",       72, "Settings"),
        ApiMethod("readGlobalSetting",  "readGlobalSetting(name): String",    "Read from Settings.Global",                          "SETTINGS",       73, "Settings"),
        ApiMethod("writeSystemSetting", "writeSystemSetting(name, val): Boolean","Write to Settings.System",                       "SETTINGS",       74, "Settings"),
        ApiMethod("readSystemSetting",  "readSystemSetting(name): String",    "Read from Settings.System",                          "SETTINGS",       75, "Settings"),
    )
}
