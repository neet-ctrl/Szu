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
 * ║  ACCU is its own self-sufficient privilege broker.                       ║
 * ║                                                                          ║
 * ║  Privilege priority:                                                     ║
 * ║    1. Root (LibSU)        — preferred, no setup needed on rooted devices ║
 * ║    2. Wireless ADB        — auto-discovered via mDNS, standard ADB flow  ║
 * ║    3. OTG (USB) ADB       — device connected via USB to another device   ║
 * ║    4. Plain shell         — unprivileged fallback                        ║
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

        // Notification
        const val CHANNEL_ID   = "accu_connection"
        const val CHANNEL_NAME = "ACCU Privileged Connection"
        const val NOTIF_ID_PAIRING      = 7001
        const val NOTIF_ID_CONNECTED    = 7002
        const val NOTIF_ID_DISCONNECTED = 7003

        // Intent extras / actions used by the pairing notification
        const val ACTION_OPEN_PAIRING   = "com.accu.ACTION_OPEN_PAIRING"
        const val ACTION_OPEN_CONNECTION = "com.accu.OPEN_CONNECTION"
    }

    enum class ConnectionState {
        /** No privilege at all — limited functionality */
        DISCONNECTED,
        /** Listening for the Android Wireless Debugging pairing mDNS service */
        DISCOVERING,
        /** Pairing service found; waiting for user to enter the 6-digit code */
        AWAITING_CODE,
        /** Executing `adb pair` + `adb connect` */
        CONNECTING,
        /** ADB wireless session active — commands route through `adb -s ip:port shell` */
        CONNECTED_WIRELESS,
        /** LibSU root available — full privilege without ADB */
        CONNECTED_ROOT,
        /** OTG / USB ADB — device connected via USB, commands route through `adb shell` */
        CONNECTED_OTG,
    }

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Discovered pairing host + port (auto-filled via mDNS, no manual entry) */
    private var pairingHost: String = ""
    private var pairingPort: Int    = 0

    /** Discovered ADB session port (from _adb-tls-connect._tcp mDNS) */
    private var sessionPort: Int = 5555

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val nm: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null

    // ─── Public API ────────────────────────────────────────────────────────────

    /** true if root, wireless ADB, or OTG ADB is available */
    fun isPrivilegeAvailable(): Boolean = when (_state.value) {
        ConnectionState.CONNECTED_ROOT,
        ConnectionState.CONNECTED_WIRELESS,
        ConnectionState.CONNECTED_OTG -> true
        else -> Shell.getShell().isRoot.also { if (it) _state.value = ConnectionState.CONNECTED_ROOT }
    }

    fun getDeviceIp(): String = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.firstOrNull()?.hostAddress ?: ""
    } catch (_: Exception) { "" }

    fun getLastConnectedIp(): String   = prefs.getString(KEY_LAST_IP, "") ?: ""
    fun getLastConnectedPort(): Int    = prefs.getInt(KEY_LAST_PORT, 5555)
    fun getConnectionState(): ConnectionState = _state.value

    /**
     * Execute a shell command using the best available privilege source.
     * Priority: root → wireless ADB (`adb -s ip:port shell`) → OTG ADB (`adb shell`) → plain shell
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            when {
                Shell.getShell().isRoot -> execRoot(command)
                _state.value == ConnectionState.CONNECTED_WIRELESS -> execWirelessAdb(command)
                _state.value == ConnectionState.CONNECTED_OTG      -> execOtgAdb(command)
                else -> execPlainShell(command)
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG exec failed: $command")
            ShellResult("", e.message ?: "error", -1)
        }
    }

    fun execRoot(command: String): ShellResult = try {
        val result = Shell.cmd(command).exec()
        ShellResult(
            output   = result.out.joinToString("\n"),
            error    = result.err.joinToString("\n"),
            exitCode = if (result.isSuccess) 0 else 1,
        )
    } catch (e: Exception) {
        ShellResult("", e.message ?: "error", -1)
    }

    /**
     * Execute via the active wireless ADB session.
     * Routes through `adb -s <ip>:<port> shell <command>` so commands run with
     * ADB shell uid=2000 privileges — equivalent to what Shizuku provided.
     */
    private fun execWirelessAdb(command: String): ShellResult {
        val ip   = getLastConnectedIp()
        val port = getLastConnectedPort()
        if (ip.isBlank()) {
            Timber.w("$TAG execWirelessAdb: no IP saved, falling back to plain shell")
            return execPlainShell(command)
        }
        Timber.d("$TAG execWirelessAdb: adb -s $ip:$port shell $command")
        return execPlainShell("adb -s $ip:$port shell $command")
    }

    /**
     * Execute via OTG / USB ADB.
     * Routes through `adb shell <command>` — adb picks up the USB-connected device automatically.
     */
    private fun execOtgAdb(command: String): ShellResult {
        Timber.d("$TAG execOtgAdb: adb shell $command")
        return execPlainShell("adb shell $command")
    }

    fun execPlainShell(command: String): ShellResult = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdout  = process.inputStream.bufferedReader().readText()
        val stderr  = process.errorStream.bufferedReader().readText()
        val exit    = process.waitFor()
        ShellResult(stdout, stderr, exit)
    } catch (e: Exception) {
        ShellResult("", e.message ?: "error", -1)
    }

    // ─── Pairing flow (wireless ADB setup) ────────────────────────────────────

    /**
     * Step 1a — Start auto-discovery of the Android Wireless Debugging *pairing* service.
     *
     * When the user opens Settings → Developer Options → Wireless Debugging →
     * "Pair device with pairing code", Android broadcasts a `_adb-tls-pairing._tcp`
     * mDNS service.  This method listens for exactly that event and fires a
     * notification as soon as it is detected, so the user only needs to enter
     * the 6-digit code — no manual IP/port entry required.
     */
    fun startPairingDiscovery() {
        if (_state.value == ConnectionState.DISCOVERING ||
            _state.value == ConnectionState.AWAITING_CODE) return
        Timber.i("$TAG: starting mDNS pairing discovery ($MDNS_PAIRING)")
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
                        val host = resolved.host?.hostAddress ?: getDeviceIp()
                        val port = resolved.port
                        Timber.i("$TAG pairing service resolved: $host:$port")
                        pairingHost = host
                        pairingPort = port
                        _state.value = ConnectionState.AWAITING_CODE
                        // Post a high-priority notification so the user can tap → enter PIN → connect
                        showPairingCodeNotification(host, port)
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                Timber.d("$TAG mDNS pairing service lost")
                if (_state.value == ConnectionState.AWAITING_CODE) {
                    _state.value = ConnectionState.DISCOVERING
                }
            }
        }
        try {
            nm.discoverServices(MDNS_PAIRING, NsdManager.PROTOCOL_DNS_SD, pairingListener)
        } catch (e: Exception) {
            Timber.e(e, "$TAG failed to start mDNS pairing discovery")
            _state.value = ConnectionState.DISCONNECTED
        }

        // Also start discovering the ADB session service so we get the real connection port
        startSessionDiscovery()
    }

    /**
     * Step 1b — Discovers the `_adb-tls-connect._tcp` mDNS service to learn the
     * active ADB session port. This is the port used for `adb -s ip:port shell`.
     */
    private fun startSessionDiscovery() {
        connectListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) { Timber.d("$TAG ADB session discovery started") }
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                nm.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val port = resolved.port
                        Timber.i("$TAG ADB session service resolved on port $port")
                        sessionPort = port
                        // Update saved port so exec() uses the correct one
                        val ip = pairingHost.ifBlank { getDeviceIp() }
                        if (ip.isNotBlank()) {
                            prefs.edit().putString(KEY_LAST_IP, ip).putInt(KEY_LAST_PORT, port).apply()
                        }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }
        try {
            nm.discoverServices(MDNS_CONNECT, NsdManager.PROTOCOL_DNS_SD, connectListener)
        } catch (e: Exception) {
            Timber.w(e, "$TAG session discovery failed (non-fatal, will use port 5555)")
        }
    }

    fun stopPairingDiscovery() {
        pairingListener?.let {
            try { nm.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        connectListener?.let {
            try { nm.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        pairingListener = null
        connectListener = null
        if (_state.value == ConnectionState.DISCOVERING ||
            _state.value == ConnectionState.AWAITING_CODE) {
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * Step 2 — Called when user enters the 6-digit code shown in Wireless Debugging.
     * ACCU auto-pairs using the IP/port discovered via mDNS — no manual entry needed.
     */
    suspend fun completePairing(code: String): Boolean = withContext(Dispatchers.IO) {
        val host = pairingHost.ifBlank { getDeviceIp() }
        val port = if (pairingPort > 0) pairingPort else return@withContext false
        Timber.i("$TAG: pairing with $host:$port using code $code")
        _state.value = ConnectionState.CONNECTING

        val pairResult = execPlainShell("adb pair $host:$port $code")
        val pairOk = pairResult.output.contains("Successfully", ignoreCase = true) ||
                     pairResult.exitCode == 0

        if (pairOk) {
            // Use session port discovered from _adb-tls-connect._tcp if available, else 5555
            val connectPort = if (sessionPort > 0) sessionPort else 5555
            val connectOk   = connectToWirelessAdb(host, connectPort)
            if (connectOk) {
                _state.value = ConnectionState.CONNECTED_WIRELESS
                prefs.edit()
                    .putString(KEY_LAST_IP, host)
                    .putInt(KEY_LAST_PORT, connectPort)
                    .apply()
                showConnectedNotification(host, connectPort)
                stopPairingDiscovery()
                return@withContext true
            }
        }

        Timber.w("$TAG pairing failed — ${pairResult.combinedOutput}")
        _state.value = ConnectionState.AWAITING_CODE
        false
    }

    /** Reconnect to the last known wireless ADB session (e.g. after app restart) */
    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        val ip   = getLastConnectedIp()
        if (ip.isBlank()) return@withContext false
        val port = getLastConnectedPort()
        val result = execPlainShell("adb connect $ip:$port")
        val ok = result.combinedOutput.contains("connected", ignoreCase = true)
        if (ok) {
            _state.value = ConnectionState.CONNECTED_WIRELESS
            Timber.i("$TAG reconnected to $ip:$port")
        }
        ok
    }

    /**
     * Connect via OTG (USB ADB).
     * Checks whether `adb devices` sees a USB-connected device and marks state accordingly.
     */
    suspend fun connectOtg(): Boolean = withContext(Dispatchers.IO) {
        val result  = execPlainShell("adb devices")
        val devices = result.output.lines()
            .drop(1)                                        // skip "List of devices attached"
            .filter { it.isNotBlank() && !it.startsWith("*") }
            .filter { it.contains("device") && !it.contains("offline") }
        val hasUsb = devices.any { it.contains("\t") && !it.startsWith("localhost") && !it.contains(":") }
        if (hasUsb) {
            _state.value = ConnectionState.CONNECTED_OTG
            Timber.i("$TAG OTG/USB ADB device detected")
        } else {
            Timber.w("$TAG no USB ADB device found: ${result.output.trim()}")
        }
        hasUsb
    }

    fun disconnect() {
        val ip = getLastConnectedIp()
        if (ip.isNotBlank()) {
            try { Runtime.getRuntime().exec(arrayOf("sh", "-c", "adb disconnect $ip")) } catch (_: Exception) {}
        }
        prefs.edit().remove(KEY_LAST_IP).remove(KEY_LAST_PORT).apply()
        _state.value = ConnectionState.DISCONNECTED
        showDisconnectedNotification()
    }

    fun checkAndUpdateState() {
        _state.value = when {
            Shell.getShell().isRoot          -> ConnectionState.CONNECTED_ROOT
            getLastConnectedIp().isNotBlank() -> ConnectionState.CONNECTED_WIRELESS
            else                             -> ConnectionState.DISCONNECTED
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private fun connectToWirelessAdb(host: String, port: Int): Boolean {
        val result = execPlainShell("adb connect $host:$port")
        val ok = result.combinedOutput.contains("connected", ignoreCase = true)
        if (!ok) {
            // Fallback: try default port 5555
            val fallback = execPlainShell("adb connect $host:5555")
            return fallback.combinedOutput.contains("connected", ignoreCase = true)
        }
        return true
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "ACCU wireless ADB connection status and pairing codes" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    /**
     * High-priority notification shown as soon as the device's Wireless Debugging
     * pairing mDNS service is detected.
     *
     * The notification has two actions:
     *   • Tap body  → opens AdbPairingScreen (step 3 — enter code)
     *   • "Connect" → same destination (the screen pre-fills IP/port from mDNS)
     *
     * The user only needs to type the 6-digit code shown on their screen.
     */
    private fun showPairingCodeNotification(host: String, port: Int) {
        // Deep-link intent: open ACCU → AdbPairingScreen
        val openIntent = PendingIntent.getActivity(
            context, NOTIF_ID_PAIRING,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_PAIRING
                flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("📡 Wireless Debugging Detected — Enter PIN")
            .setContentText("IP $host · Port $port auto-detected. Tap to enter the 6-digit code.")
            .setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(
                        "Pairing service found at $host:$port\n\n" +
                        "Tap \'Enter PIN\' and type the 6-digit code shown in:\n" +
                        "Settings → Developer Options → Wireless Debugging → Pair device with pairing code"
                    )
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_input_add,
                "Enter PIN →",
                openIntent,
            )
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_PAIRING, notif)
    }

    private fun showConnectedNotification(ip: String, port: Int) {
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.cancel(NOTIF_ID_PAIRING) // dismiss the "enter PIN" notification

        val notif = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("✓ ACCU Connected")
            .setContentText("Wireless ADB active on $ip:$port — all privileged features enabled")
            .setAutoCancel(true)
            .build()
        notifManager.notify(NOTIF_ID_CONNECTED, notif)
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
