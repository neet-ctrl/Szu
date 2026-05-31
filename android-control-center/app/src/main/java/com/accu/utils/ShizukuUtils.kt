package com.accu.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuUtils @Inject constructor() {

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 1001
    }

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
    }

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) { false }

    fun isShizukuGranted(): Boolean = try {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    } catch (_: Exception) { false }

    fun isRootAvailable(): Boolean = Shell.getShell().isRoot

    fun requestShizukuPermission() {
        try {
            if (Shizuku.isPreV11()) return
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to request Shizuku permission")
        }
    }

    fun getShizukuVersion(): Int = try { Shizuku.getVersion() } catch (_: Exception) { -1 }

    fun getShizukuUid(): Int = try { Shizuku.getUid() } catch (_: Exception) { -1 }

    /** Execute a shell command via Shizuku/root (elevated) */
    suspend fun execShizuku(command: String): ShellResult {
        return try {
            if (isRootAvailable()) {
                execRoot(command)
            } else {
                execAdb(command)
            }
        } catch (e: Exception) {
            Timber.e(e, "Shizuku exec failed: $command")
            ShellResult(output = "", error = e.message ?: "Error", exitCode = -1)
        }
    }

    /** Execute via root (LibSU) */
    suspend fun execRoot(command: String): ShellResult {
        return try {
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
    }

    /** Execute via ADB shell process (non-elevated fallback) */
    fun execAdb(command: String): ShellResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exit = process.waitFor()
            ShellResult(output = stdout, error = stderr, exitCode = exit)
        } catch (e: Exception) {
            ShellResult(output = "", error = e.message ?: "Error", exitCode = -1)
        }
    }

    fun getBestExecMethod(): ExecMethod {
        return when {
            isShizukuAvailable() && isShizukuGranted() -> ExecMethod.SHIZUKU
            isRootAvailable() -> ExecMethod.ROOT
            else -> ExecMethod.ADB
        }
    }
}

data class ShellResult(
    val output: String,
    val error: String,
    val exitCode: Int,
) {
    val isSuccess get() = exitCode == 0
    val combinedOutput get() = buildString {
        if (output.isNotBlank()) append(output)
        if (error.isNotBlank()) { if (isNotEmpty()) append("\n"); append("[ERR] $error") }
    }
}

enum class ExecMethod { SHIZUKU, ROOT, ADB }
