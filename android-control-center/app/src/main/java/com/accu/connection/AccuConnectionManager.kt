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
import dadb.AdbKeyPair
import dadb.Dadb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
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
    private var sessionPort: Int    = 0

    // ─── dadb (pure-Kotlin ADB protocol) ──────────────────────────────────────
    /** Live dadb connection — non-null when CONNECTED_WIRELESS via dadb (no adb binary). */
    private var dadbConnection: Dadb? = null

    /**
     * Live TLS ADB connection — preferred over [dadbConnection] for wireless sessions.
     * [dadb.Dadb.create()] speaks plain TCP ADB; Android 11+ wireless ADB session ports
     * require mTLS. [AdbWifiConnectClient] opens the TLS socket using the same
     * [AdbKey.sslContext] used during SPAKE2 pairing, so the device accepts our cert.
     */
    private var wifiConnectClient: AdbWifiConnectClient? = null

    /**
     * Persistent ADB RSA key pair stored in app-private storage.
     * dadb's AdbKeyPair.generate(privFile, pubFile) writes PKCS8 PEM + ADB-format base64 pub key.
     * The SAME key pair must be used for both [completePairing] and [reconnect].
     */
    private val adbKeyPair: AdbKeyPair by lazy {
        val privFile = File(context.filesDir, "accu_adb_key")
        val pubFile  = File(context.filesDir, "accu_adb_key.pub")
        if (!privFile.exists() || !pubFile.exists()) {
            try { AdbKeyPair.generate(privFile, pubFile) } catch (_: Exception) {}
        }
        AdbKeyPair.read(privFile, pubFile)
    }

    /**
     * RSA identity for wireless ADB pairing (TLS cert + SPAKE2 PeerInfo).
     *
     * IMPORTANT: This loads from the SAME "accu_adb_key" PKCS8 PEM file that
     * [adbKeyPair] uses. This guarantees that the key the device authorises
     * during pairing is identical to the key dadb uses to sign the ADB AUTH
     * challenge during [Dadb.create] — they MUST be the same key or connection
     * will fail with "RSA verify failed" even after a successful pairing.
     */
    private val adbKey: AdbKey by lazy {
        @Suppress("UNUSED_EXPRESSION") adbKeyPair  // ensure dadb creates the file first
        AdbKey.fromFile(File(context.filesDir, "accu_adb_key"), "ACCU")
    }

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

                // Priority 2a: TLS wireless ADB client (Android 11+, no adb binary needed)
                // NOTE: dadb.Dadb.create() uses plain TCP — it cannot connect to Android 11+
                // wireless ADB session ports which require mTLS. AdbWifiConnectClient opens a
                // TLS socket using the same AdbKey registered during SPAKE2 pairing.
                _state.value == ConnectionState.CONNECTED_WIRELESS && wifiConnectClient != null -> {
                    try {
                        val out = wifiConnectClient!!.shell(command)
                        ShellResult(out, "", 0)
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG wifiConnectClient exec failed — clearing connection")
                        wifiConnectClient?.close()
                        wifiConnectClient = null
                        _state.value = ConnectionState.DISCONNECTED
                        ShellResult("", e.message ?: "wifi connect error", -1)
                    }
                }

                // Priority 2b: dadb connection (legacy / USB OTG plain-TCP ADB)
                _state.value == ConnectionState.CONNECTED_WIRELESS && dadbConnection != null -> {
                    try {
                        val result = dadbConnection!!.shell(command)
                        ShellResult(result.output, result.errorOutput, result.exitCode)
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG dadb exec failed — clearing connection")
                        dadbConnection = null
                        _state.value = ConnectionState.DISCONNECTED
                        ShellResult("", e.message ?: "dadb error", -1)
                    }
                }

                // Priority 2b: system adb binary → execute on the TARGET device
                _state.value == ConnectionState.CONNECTED_WIRELESS -> {
                    val adb = findAdbBinary()
                    val ip  = getLastConnectedIp()
                    if (adb != null && ip.isNotBlank()) {
                        execPlainShell("$adb -s $ip:${getLastConnectedPort()} shell $command")
                    } else {
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
     * CONNECTED_WIRELESS works via dadb (preferred) or system adb binary.
     */
    fun checkAndUpdateState() {
        val isRoot = try { Shell.getShell().isRoot } catch (_: Exception) { false }
        if (isRoot) {
            _state.value = ConnectionState.CONNECTED_ROOT
            return
        }
        // Check TLS wireless client first (preferred for Android 11+ wireless ADB)
        val existingWifi = wifiConnectClient
        if (existingWifi != null) {
            try {
                if (existingWifi.shell("echo ok").trim() == "ok") {
                    _state.value = ConnectionState.CONNECTED_WIRELESS
                    return
                }
            } catch (_: Exception) {
                wifiConnectClient = null
            }
        }

        // Fall back to dadb plain-TCP connection (legacy / USB OTG)
        val existingDadb = dadbConnection
        if (existingDadb != null) {
            try {
                val result = existingDadb.shell("echo ok")
                if (result.output.trim() == "ok") {
                    _state.value = ConnectionState.CONNECTED_WIRELESS
                    return
                }
            } catch (_: Exception) {
                dadbConnection = null
            }
        }
        // Check system adb binary path (rare — only on some ROMs)
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
     * Priority:
     *   1. Root (LibSU) — always works
     *   2. dadb with persisted key pair — works without any adb binary
     *   3. System adb binary (rare on consumer devices)
     *
     * IMPORTANT: we ALWAYS verify with an actual echo command — never trust connect output alone.
     */
    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (Shell.getShell().isRoot) {
                _state.value = ConnectionState.CONNECTED_ROOT
                Timber.i("$TAG reconnect: root verified")
                return@withContext true
            }

            val ip   = getLastConnectedIp()
            val port = getLastConnectedPort()
            if (ip.isBlank()) return@withContext false

            // Priority 2: TLS wireless ADB (Android 11+ — requires mTLS, not plain dadb)
            try {
                val conn   = AdbWifiConnectClient.connect(ip, port, adbKey)
                val verify = conn.shell("echo ACCU_OK")
                if (verify.trim() == "ACCU_OK") {
                    wifiConnectClient = conn
                    _state.value = ConnectionState.CONNECTED_WIRELESS
                    Timber.i("$TAG reconnect: TLS ADB verified at $ip:$port")
                    return@withContext true
                } else {
                    conn.close()
                }
            } catch (e: Exception) {
                Timber.w("$TAG reconnect: TLS ADB failed ($ip:$port) — ${e.message?.take(80)}")
            }

            // Priority 3: dadb plain TCP (legacy, USB/OTG non-TLS ADB)
            try {
                val conn = Dadb.create(ip, port, adbKeyPair)
                val verify = conn.shell("echo ACCU_OK")
                if (verify.output.trim() == "ACCU_OK") {
                    dadbConnection = conn
                    _state.value = ConnectionState.CONNECTED_WIRELESS
                    Timber.i("$TAG reconnect: dadb verified at $ip:$port")
                    return@withContext true
                }
            } catch (e: Exception) {
                Timber.w("$TAG reconnect: dadb failed ($ip:$port) — ${e.message?.take(80)}")
            }

            // Priority 3: system adb binary
            val adb = findAdbBinary()
            if (adb != null) {
                execPlainShell("$adb connect $ip:$port")
                val verify = execPlainShell("$adb -s $ip:$port shell echo ACCU_OK 2>&1")
                val verified = verify.output.trim() == "ACCU_OK"
                if (verified) {
                    _state.value = ConnectionState.CONNECTED_WIRELESS
                    Timber.i("$TAG reconnect: adb binary verified at $ip:$port")
                }
                return@withContext verified
            }

            false
        } catch (_: Exception) { false }
    }

    fun disconnect() {
        try { wifiConnectClient?.close() } catch (_: Exception) {}
        wifiConnectClient = null
        try { dadbConnection?.close() } catch (_: Exception) {}
        dadbConnection = null
        // Also try adb disconnect if binary is available
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

        // Reset all discovery state so a fresh attempt doesn't reuse stale ports
        pairingHost = ""
        pairingPort = 0
        sessionPort = 0

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

        // ── Path A: system adb binary (rare — only on some ROMs) ─────────────────
        val adb = findAdbBinary()
        if (adb != null) {
            Timber.i("$TAG completePairing (adb binary): $adb pair $host:$port ***")
            _state.value = ConnectionState.CONNECTING
            val pairResult = execPlainShell("$adb pair $host:$port $code", stdinInput = code)
            Timber.d("$TAG adb pair stdout='${pairResult.output.take(120)}' stderr='${pairResult.error.take(80)}'")
            val pairOk = pairResult.output.contains("Successfully paired", ignoreCase = true)
                      || pairResult.error.contains("Successfully paired", ignoreCase = true)
            if (!pairOk) {
                _state.value = ConnectionState.AWAITING_CODE
                return@withContext PairingResult.WrongCode(pairResult.combinedOutput.take(200))
            }
            return@withContext connectAfterPair(host, adb)
        }

        // ── Path B: AdbWifiPairingClient — BoringSSL SPAKE2 + Conscrypt TLS ────────────────
        Timber.i("$TAG completePairing (SPAKE2): pairing $host:$port ***")
        _state.value = ConnectionState.CONNECTING
        val pairingOk = AdbWifiPairingClient.pair(host, port, code, adbKey)
        if (!pairingOk) {
            Timber.w("$TAG SPAKE2 pairing failed — likely wrong code")
            _state.value = ConnectionState.AWAITING_CODE
            return@withContext PairingResult.WrongCode("SPAKE2 pairing rejected — check the 6-digit code")
        }
        Timber.i("$TAG SPAKE2 pairing succeeded ✓")

        // Give adbd ~1.5 s to flush the new key into adb_keys before we attempt
        // the TLS session connection.  Without this pause the connect can arrive
        // before adbd has written the authorised-key file and we get rejected.
        kotlinx.coroutines.delay(1_500)

        // Pairing succeeded — wait for session port then connect
        val deadline = System.currentTimeMillis() + 8_000L
        while (sessionPort <= 0 && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(300)
        }
        val connectPort = sessionPort.takeIf { it > 0 }
            ?: run {
                Timber.w("$TAG sessionPort still 0 — session mDNS did not resolve")
                return@withContext PairingResult.ConnectionFailed(
                    host, 0,
                    "Pairing succeeded ✓ but session port not discovered.\nTry: adb connect $host"
                )
            }

        // Use TLS ADB connection — Dadb.create() speaks plain TCP and fails on Android 11+
        // wireless ADB session ports which require mTLS after SPAKE2 pairing.
        return@withContext try {
            Timber.i("$TAG TLS ADB connect → $host:$connectPort")
            val conn   = AdbWifiConnectClient.connect(host, connectPort, adbKey)
            val verify = conn.shell("echo ACCU_OK")
            if (verify.trim() == "ACCU_OK") {
                wifiConnectClient = conn
                _state.value = ConnectionState.CONNECTED_WIRELESS
                prefs.edit().putString(KEY_LAST_IP, host).putInt(KEY_LAST_PORT, connectPort).apply()
                showConnectedNotification(host, connectPort)
                stopPairingDiscovery()
                Timber.i("$TAG TLS ADB verified live connection to $host:$connectPort ✓")
                PairingResult.Success
            } else {
                conn.close()
                _state.value = ConnectionState.AWAITING_CODE
                PairingResult.ConnectionFailed(host, connectPort,
                    "TLS ADB connected but echo check returned unexpected output")
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG TLS ADB connect failed: ${e.message?.take(200)}")
            _state.value = ConnectionState.AWAITING_CODE
            PairingResult.ConnectionFailed(host, connectPort, e.stackTraceToString())
        }
    }

    /**
     * Re-attempt ONLY the TLS connection phase — no re-pairing needed.
     *
     * Call this when pairing already succeeded but the subsequent [AdbWifiConnectClient.connect]
     * failed (e.g. transient network glitch, stale session port). The device's trust relationship
     * (registered RSA key) persists until the user manually removes it from Developer Options.
     *
     * Uses the last persisted IP/port (written to prefs on every successful connect attempt).
     * Falls back to the in-memory [sessionPort] if the persisted port is 0.
     */
    suspend fun retryConnectionOnly(): PairingResult = withContext(Dispatchers.IO) {
        val host = getLastConnectedIp().ifBlank { pairingHost }
        val port = getLastConnectedPort().takeIf { it > 0 }
            ?: sessionPort.takeIf { it > 0 }
            ?: return@withContext PairingResult.ConnectionFailed(
                host, 0,
                "No session port known — please re-pair from the pairing screen"
            )

        if (host.isBlank()) return@withContext PairingResult.ConnectionFailed(
            "", port,
            "No host IP known — please re-pair from the pairing screen"
        )

        Timber.i("$TAG retryConnectionOnly → $host:$port")
        _state.value = ConnectionState.CONNECTING
        return@withContext try {
            val conn   = AdbWifiConnectClient.connect(host, port, adbKey)
            val verify = conn.shell("echo ACCU_OK")
            if (verify.trim() == "ACCU_OK") {
                wifiConnectClient = conn
                _state.value = ConnectionState.CONNECTED_WIRELESS
                prefs.edit().putString(KEY_LAST_IP, host).putInt(KEY_LAST_PORT, port).apply()
                showConnectedNotification(host, port)
                Timber.i("$TAG retryConnectionOnly: TLS ADB verified ✓ $host:$port")
                PairingResult.Success
            } else {
                conn.close()
                _state.value = ConnectionState.AWAITING_CODE
                PairingResult.ConnectionFailed(host, port, "TLS connected but echo check failed")
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG retryConnectionOnly failed: ${e.message?.take(200)}")
            _state.value = ConnectionState.AWAITING_CODE
            PairingResult.ConnectionFailed(host, port, e.stackTraceToString())
        }
    }

    /** Called after a successful [adb pair] to connect + verify. */
    private suspend fun connectAfterPair(host: String, adb: String): PairingResult {
        // Wait for session port from mDNS _adb-tls-connect._tcp
        val deadline = System.currentTimeMillis() + 8_000L
        while (sessionPort <= 0 && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(300)
        }
        val connectPort = sessionPort.takeIf { it > 0 }
            ?: return PairingResult.ConnectionFailed(host, 0,
                "Pairing succeeded ✓ but session port not discovered.\nTry: adb connect $host")

        val connectResult = execPlainShell("$adb connect $host:$connectPort")
        Timber.d("$TAG adb connect → '${connectResult.combinedOutput.take(80)}'")
        val verify = execPlainShell("$adb -s $host:$connectPort shell echo ACCU_OK 2>&1")
        return if (verify.output.trim() == "ACCU_OK") {
            _state.value = ConnectionState.CONNECTED_WIRELESS
            prefs.edit().putString(KEY_LAST_IP, host).putInt(KEY_LAST_PORT, connectPort).apply()
            showConnectedNotification(host, connectPort)
            stopPairingDiscovery()
            Timber.i("$TAG adb binary: verified live connection to $host:$connectPort ✓")
            PairingResult.Success
        } else {
            _state.value = ConnectionState.AWAITING_CODE
            PairingResult.ConnectionFailed(host, connectPort, connectResult.combinedOutput.take(200))
        }
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
