package com.accu.connection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.accu.MainActivity
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    ACCU CONNECTION MANAGER                               ║
 * ║                                                                          ║
 * ║  Single global privilege source for all ACCU features.                  ║
 * ║                                                                          ║
 * ║  HOW PRIVILEGE WORKS (like Shizuku / aShell):                           ║
 * ║  aShell does NOT run the `adb` binary — it uses Shizuku's Binder IPC   ║
 * ║  to execute commands through a privileged server process.               ║
 * ║  ACCU is that server. Its privilege comes from LibSU (root), which      ║
 * ║  gives uid=0 — stronger than ADB shell (uid=2000).                     ║
 * ║                                                                          ║
 * ║  Privilege priority:                                                     ║
 * ║    1. Root (LibSU)   — execRoot() via Shell.cmd()                       ║
 * ║    2. System adb     — if /system/bin/adb or /system/xbin/adb exists   ║
 * ║    3. Plain shell    — unprivileged, app UID only                       ║
 * ║                                                                          ║
 * ║  NOTE: The `adb` CLI tool does NOT exist on Android devices.            ║
 * ║  `adb pair/connect/devices` must be run from a PC. ACCU uses LibSU     ║
 * ║  (root) as its self-sufficient privilege path instead.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@Singleton
class AccuConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AccuConnectionManager"
        private const val PREFS_NAME = "accu_connection_prefs"
        private const val KEY_LAST_IP   = "last_adb_ip"
        private const val KEY_LAST_PORT = "last_adb_port"

        // Android Wireless Debugging mDNS service types
        private const val MDNS_PAIRING = "_adb-tls-pairing._tcp"
        private const val MDNS_CONNECT = "_adb-tls-connect._tcp"

        // Common paths where some ROMs ship the adb binary
        private val ADB_BINARY_PATHS = listOf(
            "/system/bin/adb",
            "/system/xbin/adb",
            "/data/local/tmp/adb",
            "/sbin/adb",
            "/vendor/bin/adb",
        )

        // Notification
        const val CHANNEL_ID   = "accu_connection"
        const val CHANNEL_NAME = "ACCU Privileged Connection"
        const val NOTIF_ID_PAIRING      = 7001
        const val NOTIF_ID_CONNECTED    = 7002
        const val NOTIF_ID_DISCONNECTED = 7003

        const val ACTION_OPEN_PAIRING    = "com.accu.ACTION_OPEN_PAIRING"
        const val ACTION_OPEN_CONNECTION = "com.accu.OPEN_CONNECTION"
    }

    enum class ConnectionState {
        /** No privilege at all — limited functionality */
        DISCONNECTED,
        /** Listening for Android Wireless Debugging pairing mDNS service */
        DISCOVERING,
        /** Pairing service found; waiting for user to enter 6-digit code */
        AWAITING_CODE,
        /** Executing pairing + connect */
        CONNECTING,
        /** Wireless ADB session active (requires adb binary on device or PC setup) */
        CONNECTED_WIRELESS,
        /** LibSU root available — full privilege, primary path */
        CONNECTED_ROOT,
        /** OTG / USB ADB */
        CONNECTED_OTG,
    }

    /** Result of a [completePairing] call — distinguishes failure reasons precisely. */
    sealed class PairingResult {
        /** Paired, connected, and verified via echo. */
        object Success : PairingResult()
        /**
         * No adb binary found on this device.
         * The user must run the pairing command from their PC instead.
         * [host] and [port] are the discovered pairing endpoint so the UI can
         * construct the exact `adb pair host:port <code>` command for copy-paste.
         */
        data class NoAdbBinary(val host: String, val port: Int) : PairingResult()
        /** adb binary is present but `adb pair` did not print "Successfully paired" — likely wrong code or expired. */
        data class WrongCode(val rawOutput: String = "") : PairingResult()
        /** Pairing succeeded but the follow-up `adb connect` or echo verification failed. [sessionPort] shows what was tried. */
        data class ConnectionFailed(val host: String, val sessionPort: Int, val rawOutput: String = "") : PairingResult()
        /** mDNS discovery has not resolved a pairing port yet. */
        object NoPairingService : PairingResult()
    }

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Discovered pairing host + port via mDNS */
    private var pairingHost: String = ""
    private var pairingPort: Int    = 0
    private var sessionPort: Int    = 5555

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val nm: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null

    // ─── Public API ────────────────────────────────────────────────────────────

    /** Returns true if root or wireless ADB privilege is available */
    fun isPrivilegeAvailable(): Boolean {
        if (_state.value == ConnectionState.CONNECTED_ROOT) return true
        if (_state.value == ConnectionState.CONNECTED_WIRELESS || _state.value == ConnectionState.CONNECTED_OTG) return true
        return try {
            val isRoot = Shell.getShell().isRoot
            if (isRoot) _state.value = ConnectionState.CONNECTED_ROOT
            isRoot
        } catch (_: Exception) { false }
    }

    fun getDeviceIp(): String = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.firstOrNull()?.hostAddress ?: ""
    } catch (_: Exception) { "" }

    fun getLastConnectedIp(): String = prefs.getString(KEY_LAST_IP, "") ?: ""
    fun getLastConnectedPort(): Int  = prefs.getInt(KEY_LAST_PORT, 5555)
    fun getConnectionState(): ConnectionState = _state.value

    /**
     * Find the adb binary if it exists on this device (some ROMs ship it).
     * Most consumer Android devices do NOT have adb — this is a bonus path only.
     */
    fun findAdbBinary(): String? = ADB_BINARY_PATHS.firstOrNull {
        try { java.io.File(it).let { f -> f.exists() && f.canExecute() } } catch (_: Exception) { false }
    }

    /** Returns the IP of the currently discovered pairing service (empty if none). */
    fun getPairingHost(): String = pairingHost

    /** Returns the PORT of the currently discovered pairing service (0 if none). */
    fun getPairingPort(): Int = pairingPort

    /** Returns the connection port that will be used after pairing (0 if not yet discovered). */
    fun getSessionPort(): Int = sessionPort

    /**
     * True when commands should execute on a REMOTE device (wireless ADB / OTG) rather
     * than the local device running ACCU.
     */
    fun isRemoteSession(): Boolean =
        _state.value == ConnectionState.CONNECTED_WIRELESS ||
        _state.value == ConnectionState.CONNECTED_OTG

    /**
     * Execute a shell command using the best available privilege source.
     *
     * Priority:
     *   1. Root (LibSU) — Shell.cmd(command).exec() as uid=0 on LOCAL device
     *   2. CONNECTED_WIRELESS with system adb binary → "$adb -s ip:port shell command"
     *      on the TARGET device (the device ACCU is connected to via ADB)
     *   3. Plain shell — Runtime.exec(sh -c command) as app UID, local device
     *
     * This means: when connected to a target phone via wireless ADB, ALL execShizuku()
     * calls route to THAT device, not the device running ACCU.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            when {
                // Priority 1: LibSU root — uid=0 on local device
                Shell.getShell().isRoot -> execRoot(command)

                // Priority 2: CONNECTED_WIRELESS → execute on the TARGET device
                _state.value == ConnectionState.CONNECTED_WIRELESS -> {
                    val adb = findAdbBinary()
                    val ip  = getLastConnectedIp()
                    if (adb != null && ip.isNotBlank()) {
                        execPlainShell("$adb -s $ip:${getLastConnectedPort()} shell $command")
                    } else {
                        // No system adb binary — fall through to local shell
                        execPlainShell(command)
                    }
                }

                // Priority 3: plain local shell
                else -> execPlainShell(command)
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG exec failed: $command")
            ShellResult("", e.message ?: "error", -1)
        }
    }

    // ─── Shell-based package discovery (works on both local root and remote ADB) ─

    data class ShellPackage(
        val packageName: String,
        val apkPath: String,
        val isSystem: Boolean,
        val isEnabled: Boolean,
    )

    /**
     * List installed packages via shell — routes through exec(), so it targets the
     * connected device (remote when CONNECTED_WIRELESS, local when CONNECTED_ROOT).
     *
     * Parses `pm list packages -f` output:
     *   "package:/data/app/com.example-xyz.apk=com.example"
     */
    suspend fun listPackages(thirdPartyOnly: Boolean = false): List<ShellPackage> =
        withContext(Dispatchers.IO) {
            val flags = if (thirdPartyOnly) "-f -3" else "-f"
            val allLines   = exec("pm list packages $flags 2>/dev/null").output
            val disabledPkgs = exec("pm list packages -d 2>/dev/null").output
                .lines()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .toSet()

            allLines.lines()
                .filter { it.startsWith("package:") }
                .mapNotNull { line ->
                    try {
                        val content = line.removePrefix("package:")
                        val eqIdx = content.lastIndexOf('=')
                        if (eqIdx < 0) return@mapNotNull null
                        val apkPath = content.substring(0, eqIdx).trim()
                        val pkgName = content.substring(eqIdx + 1).trim()
                        if (pkgName.isBlank()) return@mapNotNull null
                        ShellPackage(
                            packageName = pkgName,
                            apkPath     = apkPath,
                            isSystem    = apkPath.startsWith("/system/") ||
                                          apkPath.startsWith("/vendor/") ||
                                          apkPath.startsWith("/product/"),
                            isEnabled   = pkgName !in disabledPkgs,
                        )
                    } catch (_: Exception) { null }
                }
        }

    /** Execute via LibSU root — uid=0, full privilege (equivalent to Shizuku server process) */
    fun execRoot(command: String): ShellResult = try {
        val result = Shell.cmd(command).exec()
        ShellResult(
            output   = result.out.joinToString("\n"),
            error    = result.err.joinToString("\n"),
            exitCode = if (result.isSuccess) 0 else 1,
        )
    } catch (e: Exception) {
        Timber.e(e, "$TAG execRoot failed: $command")
        ShellResult("", e.message ?: "error", -1)
    }

    /**
     * Execute via plain shell (Runtime.exec) as app UID.
     * Good for read-only commands; privileged writes require root.
     *
     * [stdinInput] is written to the process stdin before closing — used for
     * interactive `adb pair` builds that prompt for the code via stdin.
     *
     * Reads stdout and stderr on separate threads to prevent pipe-buffer deadlock.
     */
    fun execPlainShell(command: String, stdinInput: String? = null): ShellResult = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        // Write stdin then close — essential for interactive adb pair builds
        try {
            if (stdinInput != null) {
                process.outputStream.bufferedWriter().use { w -> w.write(stdinInput + "\n"); w.flush() }
            } else {
                process.outputStream.close()
            }
        } catch (_: Exception) {}

        // Read stdout + stderr concurrently to prevent pipe-buffer deadlock
        val stdoutRef = java.util.concurrent.atomic.AtomicReference("")
        val stderrRef = java.util.concurrent.atomic.AtomicReference("")
        val t1 = Thread { stdoutRef.set(process.inputStream.bufferedReader().readText()) }
        val t2 = Thread { stderrRef.set(process.errorStream.bufferedReader().readText()) }
        t1.start(); t2.start()
        val exit = process.waitFor()
        t1.join(10_000); t2.join(10_000)
        ShellResult(stdoutRef.get(), stderrRef.get(), exit)
    } catch (e: Exception) {
        ShellResult("", e.message ?: "error", -1)
    }

    /**
     * Execute using system adb binary (only on ROMs that ship it).
     * Returns null result with error message if adb is not available.
     */
    fun execSystemAdb(args: String): ShellResult {
        val adb = findAdbBinary()
            ?: return ShellResult(
                output   = "",
                error    = "adb binary not found on this device. Use `adb $args` from your PC instead.",
                exitCode = -1,
            )
        return execPlainShell("$adb $args")
    }

    // ─── Connection state ──────────────────────────────────────────────────────

    /**
     * Check real privilege availability and update state.
     * CONNECTED_ROOT requires LibSU root to actually work.
     * CONNECTED_WIRELESS requires a saved session AND the adb binary (rare).
     */
    fun checkAndUpdateState() {
        val isRoot = try { Shell.getShell().isRoot } catch (_: Exception) { false }
        if (isRoot) {
            _state.value = ConnectionState.CONNECTED_ROOT
            return
        }
        // Check if we have a saved wireless session and the adb binary to verify it
        val savedIp = getLastConnectedIp()
        val adb = findAdbBinary()
        if (savedIp.isNotBlank() && adb != null) {
            val result = execPlainShell("$adb -s $savedIp:${getLastConnectedPort()} shell echo ok 2>&1")
            if (result.output.trim() == "ok") {
                _state.value = ConnectionState.CONNECTED_WIRELESS
                return
            }
        }
        _state.value = ConnectionState.DISCONNECTED
    }

    /**
     * Reconnect to last known session. Returns true if privilege is actually verified.
     *
     * IMPORTANT: `adb connect` can return "already connected" from ADB's stale cache
     * even when the device is offline. We ALWAYS verify with an actual `echo ok` shell
     * command after connecting — never trust the connect output alone.
     */
    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (Shell.getShell().isRoot) {
                _state.value = ConnectionState.CONNECTED_ROOT
                Timber.i("$TAG reconnect: root verified")
                true
            } else {
                val ip  = getLastConnectedIp()
                val adb = findAdbBinary()
                if (ip.isNotBlank() && adb != null) {
                    val port = getLastConnectedPort()
                    execPlainShell("$adb connect $ip:$port")
                    // Verify the connection is actually live — never trust connect output alone
                    val verify = execPlainShell("$adb -s $ip:$port shell echo ACCU_OK 2>&1")
                    val verified = verify.output.trim() == "ACCU_OK"
                    if (verified) {
                        _state.value = ConnectionState.CONNECTED_WIRELESS
                        Timber.i("$TAG reconnect: wireless ADB verified at $ip:$port")
                    } else {
                        Timber.w("$TAG reconnect: verification failed for $ip:$port — ${verify.combinedOutput.take(80)}")
                    }
                    verified
                } else {
                    false
                }
            }
        } catch (_: Exception) { false }
    }

    fun disconnect() {
        val adb = findAdbBinary()
        val ip  = getLastConnectedIp()
        if (adb != null && ip.isNotBlank()) {
            try { execPlainShell("$adb disconnect $ip") } catch (_: Exception) {}
        }
        prefs.edit().remove(KEY_LAST_IP).remove(KEY_LAST_PORT).apply()
        _state.value = ConnectionState.DISCONNECTED
        showDisconnectedNotification()
    }

    /**
     * Connect via OTG / USB ADB.
     * Requires adb binary on device (uncommon) or root.
     */
    suspend fun connectOtg(): Boolean = withContext(Dispatchers.IO) {
        if (Shell.getShell().isRoot) {
            _state.value = ConnectionState.CONNECTED_OTG
            return@withContext true
        }
        val adb = findAdbBinary() ?: return@withContext false
        val result = execPlainShell("$adb devices")
        val hasUsb = result.output.lines()
            .drop(1)
            .filter { it.isNotBlank() && !it.startsWith("*") }
            .any { it.contains("\t") && it.contains("device") && !it.contains(":") }
        if (hasUsb) _state.value = ConnectionState.CONNECTED_OTG
        hasUsb
    }

    // ─── mDNS Pairing discovery ────────────────────────────────────────────────

    /**
     * Start listening for Android's Wireless Debugging pairing service via NsdManager.
     *
     * When the user opens Settings → Developer Options → Wireless Debugging →
     * "Pair device with pairing code", Android advertises _adb-tls-pairing._tcp.
     * ACCU's NsdManager picks this up and fires a notification immediately.
     *
     * The user then enters only the 6-digit PIN — IP and port are auto-detected.
     */
    fun startPairingDiscovery() {
        if (_state.value == ConnectionState.DISCOVERING || _state.value == ConnectionState.AWAITING_CODE) return
        Timber.i("$TAG: starting mDNS pairing discovery ($MDNS_PAIRING)")

        // If root is already available, no pairing is needed — update state and return
        if (try { Shell.getShell().isRoot } catch (_: Exception) { false }) {
            _state.value = ConnectionState.CONNECTED_ROOT
            Timber.i("$TAG: root already available — skipping pairing discovery")
            return
        }

        _state.value = ConnectionState.DISCOVERING
        ensureNotificationChannel()

        pairingListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                Timber.w("$TAG mDNS pairing discovery start failed: $code")
                _state.value = ConnectionState.DISCONNECTED
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String)  { Timber.d("$TAG mDNS pairing discovery started") }
            override fun onDiscoveryStopped(type: String)  {}
            override fun onServiceFound(info: NsdServiceInfo) {
                Timber.i("$TAG mDNS pairing service found: ${info.serviceName}")
                nm.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo, code: Int) {
                        Timber.w("$TAG mDNS pairing resolve failed: $code")
                    }
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        // Force IPv4 — IPv6 link-local addresses (fe80::...) cause adb pair to fail
                        val host = resolved.host?.let { addr ->
                            if (addr is java.net.Inet4Address) addr.hostAddress else null
                        } ?: getDeviceIp()
                        val port = resolved.port
                        Timber.i("$TAG pairing service resolved: $host:$port")
                        pairingHost = host
                        pairingPort = port
                        _state.value = ConnectionState.AWAITING_CODE
                        // Notification → user enters 6-digit PIN → completePairing(code)
                        showPairingCodeNotification(host, port)
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                // Keep AWAITING_CODE — the user may still have time to enter the code they
                // already saw on screen. Returning to DISCOVERING would be confusing mid-entry.
                Timber.d("$TAG mDNS pairing service lost (keeping state=${_state.value})")
            }
        }
        try {
            nm.discoverServices(MDNS_PAIRING, NsdManager.PROTOCOL_DNS_SD, pairingListener)
        } catch (e: Exception) {
            Timber.e(e, "$TAG failed to start mDNS pairing discovery")
            _state.value = ConnectionState.DISCONNECTED
        }
        startSessionDiscovery()
    }

    private fun startSessionDiscovery() {
        connectListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                nm.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo, code: Int) {
                        Timber.w("$TAG mDNS session resolve failed: $code")
                    }
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        // Force IPv4 — same reason as pairing listener
                        val ip = resolved.host?.let { addr ->
                            if (addr is java.net.Inet4Address) addr.hostAddress else null
                        } ?: pairingHost.ifBlank { getDeviceIp() }
                        sessionPort = resolved.port
                        Timber.i("$TAG session service resolved: $ip:$sessionPort")
                        if (ip.isNotBlank()) {
                            prefs.edit().putString(KEY_LAST_IP, ip).putInt(KEY_LAST_PORT, sessionPort).apply()
                        }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }
        try {
            nm.discoverServices(MDNS_CONNECT, NsdManager.PROTOCOL_DNS_SD, connectListener)
        } catch (e: Exception) {
            Timber.w(e, "$TAG session discovery failed (non-fatal)")
        }
    }

    fun stopPairingDiscovery() {
        pairingListener?.let { try { nm.stopServiceDiscovery(it) } catch (_: Exception) {} }
        connectListener?.let { try { nm.stopServiceDiscovery(it) } catch (_: Exception) {} }
        pairingListener = null
        connectListener = null
        if (_state.value == ConnectionState.DISCOVERING || _state.value == ConnectionState.AWAITING_CODE) {
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * Step 2 of the pairing flow — called when user enters the 6-digit PIN.
     *
     * Returns a [PairingResult] so callers can show accurate error messages:
     *   - [PairingResult.Success]          — paired, connected, and verified
     *   - [PairingResult.NoAdbBinary]      — no adb binary; user must run command from PC
     *   - [PairingResult.WrongCode]        — adb ran but "Successfully paired" not in output
     *   - [PairingResult.NoPairingService] — mDNS hasn't resolved a pairing port yet
     */
    suspend fun completePairing(code: String): PairingResult = withContext(Dispatchers.IO) {
        // Root is the primary success path — no pairing needed
        if (try { Shell.getShell().isRoot } catch (_: Exception) { false }) {
            _state.value = ConnectionState.CONNECTED_ROOT
            Timber.i("$TAG completePairing: root available — no pairing required")
            stopPairingDiscovery()
            return@withContext PairingResult.Success
        }

        val host = pairingHost.ifBlank { getDeviceIp() }
        val port = if (pairingPort > 0) pairingPort else {
            Timber.w("$TAG completePairing: pairingPort is 0 — mDNS hasn't resolved yet")
            return@withContext PairingResult.NoPairingService
        }

        val adb = findAdbBinary()
        if (adb == null) {
            Timber.w("$TAG completePairing: no adb binary on device ($host:$port) — user must pair from PC")
            return@withContext PairingResult.NoAdbBinary(host, port)
        }

        Timber.i("$TAG completePairing: running $adb pair $host:$port ***")
        _state.value = ConnectionState.CONNECTING

        // Pass code BOTH as CLI arg AND via stdin — some ROM adb builds only read from stdin
        val pairResult = execPlainShell("$adb pair $host:$port $code", stdinInput = code)
        Timber.d("$TAG adb pair stdout='${pairResult.output.take(120)}' stderr='${pairResult.error.take(80)}'")
        // Only trust explicit "Successfully paired" — exitCode alone is unreliable across ROM adb builds
        val pairOk = pairResult.output.contains("Successfully paired", ignoreCase = true)
                  || pairResult.error.contains("Successfully paired", ignoreCase = true)

        if (!pairOk) {
            Timber.w("$TAG pairing failed — ${pairResult.combinedOutput.take(160)}")
            _state.value = ConnectionState.AWAITING_CODE
            return@withContext PairingResult.WrongCode(pairResult.combinedOutput.take(200))
        }

        // Pairing succeeded — now connect via the TLS session port (NOT 5555; wireless ADB TLS uses
        // a random port discovered via _adb-tls-connect._tcp mDNS).
        // Wait up to 8 s for the session port to be resolved (it's discovered in parallel).
        val deadline = System.currentTimeMillis() + 8_000L
        while (sessionPort <= 0 && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(300)
        }
        val connectPort = sessionPort.takeIf { it > 0 }
            ?: run {
                Timber.w("$TAG sessionPort still 0 after waiting — session mDNS did not resolve")
                return@withContext PairingResult.ConnectionFailed(
                    host, 0,
                    "Pairing succeeded but session port was not discovered via mDNS. " +
                    "Run from your PC: adb connect $host"
                )
            }

        val connectResult = execPlainShell("$adb connect $host:$connectPort")
        Timber.d("$TAG adb connect → '${connectResult.combinedOutput.take(80)}'")

        // Verify the connection is ACTUALLY live — never trust "adb connect" output alone
        // (it can say "already connected" from stale cache when device is offline)
        val verify = execPlainShell("$adb -s $host:$connectPort shell echo ACCU_OK 2>&1")
        val verified = verify.output.trim() == "ACCU_OK"
        if (verified) {
            _state.value = ConnectionState.CONNECTED_WIRELESS
            prefs.edit().putString(KEY_LAST_IP, host).putInt(KEY_LAST_PORT, connectPort).apply()
            showConnectedNotification(host, connectPort)
            stopPairingDiscovery()
            Timber.i("$TAG completePairing: verified live connection to $host:$connectPort")
            return@withContext PairingResult.Success
        }

        Timber.w("$TAG completePairing: paired but echo verification failed — ${verify.combinedOutput.take(80)}")
        _state.value = ConnectionState.AWAITING_CODE
        PairingResult.ConnectionFailed(host, connectPort, connectResult.combinedOutput.take(200))
    }

    // ─── Notification helpers ──────────────────────────────────────────────────

    /**
     * Fired as soon as NsdManager detects the pairing service.
     * User opens ACCU → sees detected IP:port → enters 6-digit PIN → done.
     */
    private fun showPairingCodeNotification(host: String, port: Int) {
        val openIntent = PendingIntent.getActivity(
            context, NOTIF_ID_PAIRING,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_PAIRING
                flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val adbAvailable = findAdbBinary() != null
        val bodyText = if (adbAvailable) {
            "Pairing service at $host:$port — tap to enter PIN"
        } else {
            "Pairing service at $host:$port detected — open ACCU to continue"
        }

        val notif = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("📡 Wireless Debugging Detected")
            .setContentText(bodyText)
            .setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle().bigText(
                    "Pairing service found:\n  Host: $host\n  Port: $port\n\n" +
                    if (adbAvailable) "Tap 'Enter PIN →' and type the 6-digit code from your device screen."
                    else "If root is enabled, ACCU connects automatically.\n" +
                         "Otherwise run from your PC:\n  adb pair $host:$port <code>"
                )
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_input_add, "Enter PIN →", openIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_PAIRING, notif)
    }

    private fun showConnectedNotification(ip: String, port: Int) {
        val nm2 = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm2.cancel(NOTIF_ID_PAIRING)
        val notif = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("✓ ACCU Connected")
            .setContentText("Wireless ADB active on $ip:$port — all privileged features enabled")
            .setAutoCancel(true)
            .build()
        nm2.notify(NOTIF_ID_CONNECTED, notif)
    }

    private fun showDisconnectedNotification() {
        val openIntent = PendingIntent.getActivity(
            context, NOTIF_ID_DISCONNECTED,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_PAIRING
                flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("ACCU Disconnected")
            .setContentText("Privileged access lost. Tap to reconnect.")
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_dialog_info, "Reconnect", openIntent)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_DISCONNECTED, notif)
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "ACCU wireless ADB connection status and pairing codes" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}

data class ShellResult(
    val output:   String,
    val error:    String,
    val exitCode: Int,
) {
    val isSuccess get() = exitCode == 0
    val combinedOutput get() = buildString {
        if (output.isNotBlank()) append(output)
        if (error.isNotBlank()) { if (isNotEmpty()) append("\n"); append("[ERR] $error") }
    }
}
