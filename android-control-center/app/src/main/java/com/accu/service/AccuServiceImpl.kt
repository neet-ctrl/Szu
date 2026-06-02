package com.accu.service

import android.content.Context
import android.os.Binder
import android.os.Process
import com.accu.api.IAccuPermissionCallback
import com.accu.api.IAccuProcessCallback
import com.accu.api.IAccuService
import com.accu.connection.AccuConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * IAccuService.Stub — the actual binder implementation that runs inside
 * AccuSystemService (ACCU's process).
 *
 * Every privileged call:
 *  1. Resolves the calling package via Binder.getCallingUid()
 *  2. Checks that the caller has ACCU permission + the required scope
 *  3. Executes via AccuConnectionManager (root → wireless ADB → shell)
 *  4. Records the call for analytics
 */
class AccuServiceImpl(
    private val context: Context,
    private val permissionManager: AccuPermissionManager,
    private val connectionManager: AccuConnectionManager,
    private val onPermissionRequest: (callerPackage: String, callerLabel: String, callback: IAccuPermissionCallback) -> Unit,
) : IAccuService.Stub() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun callerPackage(): String {
        val uid = Binder.getCallingUid()
        val packages = context.packageManager.getPackagesForUid(uid)
        return packages?.firstOrNull() ?: "uid:$uid"
    }

    private fun requireScope(scope: String): String {
        val pkg = callerPackage()
        if (!permissionManager.isGranted(pkg)) {
            throw SecurityException("[$pkg] does not have ACCU permission. Call requestPermission() first.")
        }
        if (!permissionManager.hasScope(pkg, scope)) {
            throw SecurityException("[$pkg] lacks the '$scope' scope.")
        }
        permissionManager.recordCall(pkg)
        return pkg
    }

    /** Execute via the global privilege manager (root → wireless ADB → plain shell). */
    private fun runCmd(command: String): Array<String> {
        return try {
            val result = runBlocking { connectionManager.exec(command) }
            arrayOf(result.output, result.error, result.exitCode.toString())
        } catch (e: Exception) {
            arrayOf("", e.message ?: "error", "-1")
        }
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    override fun getVersion(): Int = 1
    override fun getUid(): Int = Process.myUid()
    override fun getPid(): Int = Process.myPid()
    override fun getAccuVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) { "unknown" }

    override fun ping(): Boolean = true

    // ── Permission System ─────────────────────────────────────────────────────

    override fun requestPermission(callback: IAccuPermissionCallback) {
        val pkg = callerPackage()
        val label = try {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) { pkg }
        Timber.i("ACCU: Permission request from $pkg ($label)")
        onPermissionRequest(pkg, label, callback)
    }

    override fun checkPermission(): Int = permissionManager.checkPermission(callerPackage())
    override fun hasScope(scope: String): Boolean = permissionManager.hasScope(callerPackage(), scope)

    override fun revokeSelf() {
        val pkg = callerPackage()
        permissionManager.revoke(pkg)
        Timber.i("ACCU: $pkg revoked its own permission")
    }

    // ── Shell Execution ───────────────────────────────────────────────────────

    override fun exec(command: String): Array<String> {
        requireScope(SCOPE_SHELL)
        Timber.d("ACCU exec: $command")
        return runCmd(command)
    }

    override fun execAsync(command: String, callback: IAccuProcessCallback) {
        requireScope(SCOPE_SHELL)
        scope.launch {
            try {
                val result = connectionManager.exec(command)
                result.output.lines().forEach { line ->
                    try { callback.onStdoutLine(line) } catch (_: Exception) {}
                }
                result.error.lines().filter { it.isNotBlank() }.forEach { line ->
                    try { callback.onStderrLine(line) } catch (_: Exception) {}
                }
                try { callback.onExit(result.exitCode) } catch (_: Exception) {}
            } catch (e: Exception) {
                try { callback.onStderrLine(e.message ?: "error"); callback.onExit(-1) } catch (_: Exception) {}
            }
        }
    }

    override fun execAndGetOutput(command: String): String {
        requireScope(SCOPE_SHELL)
        val result = runCmd(command)
        return buildString {
            if (result[0].isNotBlank()) append(result[0])
            if (result[1].isNotBlank()) { if (isNotEmpty()) append("\n"); append(result[1]) }
        }
    }

    // ── Package Manager ───────────────────────────────────────────────────────

    override fun installApk(apkPath: String, installerPackage: String?): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        val result = runCmd("pm install -r ${if (installerPackage != null) "-i $installerPackage" else ""} \"$apkPath\"")
        return result[2] == "0" || result[0].contains("Success", ignoreCase = true)
    }

    override fun uninstallPackage(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        val result = runCmd("pm uninstall --user 0 $packageName")
        return result[2] == "0" || result[0].contains("Success", ignoreCase = true)
    }

    override fun uninstallKeepData(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        val result = runCmd("pm uninstall -k --user 0 $packageName")
        return result[2] == "0" || result[0].contains("Success", ignoreCase = true)
    }

    override fun enablePackage(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("pm enable --user 0 $packageName")[2] == "0"
    }

    override fun disablePackage(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("pm disable-user --user 0 $packageName")[2] == "0"
    }

    override fun hidePackage(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("pm hide --user 0 $packageName")[2] == "0"
    }

    override fun unhidePackage(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("pm unhide --user 0 $packageName")[2] == "0"
    }

    override fun suspendPackage(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("pm suspend --user 0 $packageName")[2] == "0"
    }

    override fun unsuspendPackage(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("pm unsuspend --user 0 $packageName")[2] == "0"
    }

    override fun clearPackageData(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        val result = runCmd("pm clear $packageName")
        return result[2] == "0" || result[0].contains("Success", ignoreCase = true)
    }

    override fun enableComponent(packageName: String, componentName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("pm enable $packageName/$componentName")[2] == "0"
    }

    override fun disableComponent(packageName: String, componentName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("pm disable $packageName/$componentName")[2] == "0"
    }

    // ── Runtime Permissions ────────────────────────────────────────────────────

    override fun grantPermission(packageName: String, permission: String): Boolean {
        requireScope(SCOPE_PERMISSIONS)
        return runCmd("pm grant $packageName $permission")[2] == "0"
    }

    override fun revokePermission(packageName: String, permission: String): Boolean {
        requireScope(SCOPE_PERMISSIONS)
        return runCmd("pm revoke $packageName $permission")[2] == "0"
    }

    override fun setAppOp(packageName: String, op: String, mode: String): Boolean {
        requireScope(SCOPE_PERMISSIONS)
        return runCmd("appops set $packageName $op $mode")[2] == "0"
    }

    override fun getAppOp(packageName: String, op: String): String {
        requireScope(SCOPE_PERMISSIONS)
        return runCmd("appops get $packageName $op")[0].trim().ifBlank { "error" }
    }

    // ── Activity Manager ──────────────────────────────────────────────────────

    override fun forceStop(packageName: String): Boolean {
        requireScope(SCOPE_PACKAGE_MANAGE)
        return runCmd("am force-stop $packageName")[2] == "0"
    }

    override fun setApplicationLocale(packageName: String, locale: String): Boolean {
        requireScope(SCOPE_LOCALE)
        val localeArg = if (locale.isBlank()) "\"\"" else locale
        return runCmd("am set-app-locale --user 0 $packageName --locale $localeArg")[2] == "0"
    }

    // ── System Settings ────────────────────────────────────────────────────────

    override fun writeSecureSetting(name: String, value: String): Boolean {
        requireScope(SCOPE_SETTINGS)
        return runCmd("settings put secure $name $value")[2] == "0"
    }

    override fun readSecureSetting(name: String): String {
        requireScope(SCOPE_SETTINGS)
        return runCmd("settings get secure $name")[0].trim()
    }

    override fun writeGlobalSetting(name: String, value: String): Boolean {
        requireScope(SCOPE_SETTINGS)
        return runCmd("settings put global $name $value")[2] == "0"
    }

    override fun readGlobalSetting(name: String): String {
        requireScope(SCOPE_SETTINGS)
        return runCmd("settings get global $name")[0].trim()
    }

    override fun writeSystemSetting(name: String, value: String): Boolean {
        requireScope(SCOPE_SETTINGS)
        return runCmd("settings put system $name $value")[2] == "0"
    }

    override fun readSystemSetting(name: String): String {
        requireScope(SCOPE_SETTINGS)
        return runCmd("settings get system $name")[0].trim()
    }
}
