package com.accu.utils

import android.content.Context
import com.accu.connection.AccuConnectionManager
import com.accu.connection.ShellResult
import com.topjohnwu.superuser.Shell
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privilege execution utility — ACCU-native, no Shizuku dependency.
 *
 * Execution priority (managed by AccuConnectionManager):
 *   1. Root (LibSU)
 *   2. Wireless ADB (auto-paired via mDNS — commands routed via `adb -s ip:port shell`)
 *   3. OTG/USB ADB  (commands routed via `adb shell`)
 *   4. Plain shell  (unprivileged fallback)
 */
@Singleton
class ShizukuUtils @Inject constructor(
    private val connectionManager: AccuConnectionManager,
) {
    // ── Status checks (Shizuku-compatible names kept for call-site compatibility) ──

    /** Returns true if any privilege source is available (root or wireless ADB). */
    fun isShizukuAvailable(): Boolean = connectionManager.isPrivilegeAvailable()

    /** Alias — same as isShizukuAvailable() in ACCU's model. */
    fun isShizukuGranted(): Boolean = connectionManager.isPrivilegeAvailable()

    /** ACCU is always "installed" since it IS the service. */
    fun isShizukuInstalled(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = true

    fun isRootAvailable(): Boolean = Shell.getShell().isRoot

    /** ACCU's own version — returned where Shizuku version was previously used. */
    fun getShizukuVersion(): Int = 1

    /** ACCU runs in-process; returns 0 (root UID) when root is active. */
    fun getShizukuUid(): Int = if (isRootAvailable()) 0 else android.os.Process.myUid()

    /**
     * Instead of requesting Shizuku permission, this starts ACCU's own
     * wireless ADB pairing discovery flow (Shizuku-like UX).
     */
    fun requestShizukuPermission() {
        connectionManager.startPairingDiscovery()
    }

    fun getBestExecMethod(): ExecMethod = when {
        isRootAvailable() -> ExecMethod.ROOT
        connectionManager.isPrivilegeAvailable() -> ExecMethod.WIRELESS_ADB
        else -> ExecMethod.SHELL
    }

    // ── Privileged execution ────────────────────────────────────────────────────

    /**
     * Execute a shell command using the best available privilege source.
     * Name kept for call-site compatibility; internally routes through ACCU.
     */
    suspend fun execShizuku(command: String): ShellResult = connectionManager.exec(command)

    /** Execute via root (LibSU). */
    suspend fun execRoot(command: String): ShellResult = try {
        val result = Shell.cmd(command).exec()
        ShellResult(
            output = result.out.joinToString("\n"),
            error = result.err.joinToString("\n"),
            exitCode = if (result.isSuccess) 0 else 1,
        )
    } catch (e: Exception) {
        Timber.e(e, "Root exec failed: $command")
        ShellResult(output = "", error = e.message ?: "Error", exitCode = -1)
    }

    /** Execute via plain ADB/shell process. */
    fun execAdb(command: String): ShellResult = connectionManager.execPlainShell(command)

    fun getDeviceIp(): String = connectionManager.getDeviceIp()
}

enum class ExecMethod { ROOT, WIRELESS_ADB, SHELL }
