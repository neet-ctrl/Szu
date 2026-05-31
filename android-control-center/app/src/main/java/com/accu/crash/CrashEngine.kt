package com.accu.crash

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import com.accu.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

/**
 * ACCU Crash Engine — the single funnel for every crash type.
 *
 * Install once in [com.accu.ACCApplication.onCreate]:
 *     CrashEngine.install(this)
 *
 * Architecture:
 *  1. Captures crash synchronously via Thread.UncaughtExceptionHandler
 *  2. Writes crash JSON to filesDir/crashes/pending/<crashId>.json
 *     (plain blocking IO — no Room, no Hilt, no coroutines during crash)
 *  3. Posts an instant high-priority notification via [CrashNotificationManager]
 *  4. Launches [com.accu.ui.crash.CrashReportActivity] in its own :crash process
 *  5. Kills the main process
 *
 * On next main-process launch, [CrashRepository.migratePendingCrashes] moves
 * files → Room and deletes them.
 */
object CrashEngine {

    private const val PENDING_DIR = "crashes/pending"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable, isFatal = true, crashKind = detectKind(throwable))
        }
    }

    /**
     * Record a handled (non-fatal) exception — from try/catch blocks,
     * Compose error handlers, CoroutineExceptionHandlers, etc.
     */
    fun recordNonFatal(throwable: Throwable, context: String = "") {
        val kind = if (context.isNotBlank()) context else detectKind(throwable)
        handleCrash(Thread.currentThread(), throwable, isFatal = false, crashKind = kind)
    }

    /** Called from ANR watchdog service. */
    fun recordAnr(msg: String) {
        val fake = Throwable("ANR: $msg")
        handleCrash(Thread.currentThread(), fake, isFatal = true, crashKind = "ANR", isAnr = true)
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun handleCrash(
        thread: Thread,
        throwable: Throwable,
        isFatal: Boolean,
        crashKind: String,
        isAnr: Boolean = false,
    ) {
        try {
            val ctx = appContext ?: return
            val crashId = UUID.randomUUID().toString()
            val json = buildCrashJson(crashId, thread, throwable, isFatal, isAnr, crashKind, ctx)
            writePendingCrash(ctx, crashId, json)
            CrashNotificationManager.postCrashNotification(ctx, crashId,
                throwable.javaClass.simpleName, throwable.message ?: "No message")
            launchCrashActivity(ctx, crashId)
        } catch (_: Throwable) {
            // Never let the crash handler itself crash silently
        } finally {
            if (isFatal) {
                defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(2)
            }
        }
    }

    private fun buildCrashJson(
        crashId: String,
        thread: Thread,
        throwable: Throwable,
        isFatal: Boolean,
        isAnr: Boolean,
        crashKind: String,
        ctx: Context,
    ): JSONObject {
        val ctx2 = CrashContextCollector
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val battery = getBatteryPct(ctx)
        val causeChain = buildCauseChain(throwable)

        return JSONObject().apply {
            put("crashId", crashId)
            put("sessionId", ctx2.sessionId)
            put("timestamp", System.currentTimeMillis())
            // App / Build
            put("appVersion", BuildConfig.VERSION_NAME)
            put("buildVersionCode", BuildConfig.VERSION_CODE)
            put("buildType", if (BuildConfig.DEBUG) "debug" else "release")
            // Device
            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("deviceManufacturer", Build.MANUFACTURER)
            put("androidVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            put("totalRamMb", mi.totalMem / 1_048_576)
            put("freeRamMb", mi.availMem / 1_048_576)
            put("cpuUsagePct", getCpuUsage())
            put("batteryPct", battery.first)
            put("batteryCharging", battery.second)
            put("isLowMemory", mi.lowMemory)
            // Thread
            put("processName", getProcessName())
            put("processId", android.os.Process.myPid())
            put("threadName", thread.name)
            put("threadId", thread.id)
            // Exception
            put("exceptionType", throwable.javaClass.name)
            put("exceptionMessage", throwable.message ?: "")
            put("stackTrace", throwable.stackTraceToString())
            put("causeChain", causeChain.toString())
            // Context
            put("activityName", ctx2.currentActivity)
            put("fragmentName", ctx2.currentFragment)
            put("screenRoute", ctx2.currentRoute)
            put("serviceName", ctx2.currentService)
            put("viewModelName", ctx2.currentViewModel)
            // System state
            put("networkState", ctx2.networkState)
            put("shizukuState", ctx2.shizukuState)
            put("rootState", ctx2.rootState)
            put("wirelessAdbState", ctx2.wirelessAdbState)
            // Session
            put("userActionsJson", ctx2.getRecentActionsJson())
            put("sessionDurationSec", ctx2.sessionDurationSec())
            // Classification
            put("isFatal", isFatal)
            put("isAnr", isAnr)
            put("crashKind", crashKind)
        }
    }

    private fun buildCauseChain(t: Throwable): JSONArray {
        val arr = JSONArray()
        var cause: Throwable? = t.cause
        var depth = 0
        while (cause != null && depth < 10) {
            arr.put(JSONObject().apply {
                put("type", cause.javaClass.name)
                put("message", cause.message ?: "")
            })
            cause = cause.cause
            depth++
        }
        return arr
    }

    private fun writePendingCrash(ctx: Context, crashId: String, json: JSONObject) {
        val dir = File(ctx.filesDir, PENDING_DIR).also { it.mkdirs() }
        File(dir, "$crashId.json").writeText(json.toString(2))
    }

    private fun launchCrashActivity(ctx: Context, crashId: String) {
        try {
            val intent = Intent()
                .setClassName(ctx, "com.accu.ui.crash.CrashReportActivity")
                .putExtra("crash_id", crashId)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            ctx.startActivity(intent)
        } catch (_: Throwable) {}
    }

    fun readPendingCrashFile(ctx: Context, crashId: String): JSONObject? = try {
        val file = File(ctx.filesDir, "$PENDING_DIR/$crashId.json")
        if (file.exists()) JSONObject(file.readText()) else null
    } catch (_: Throwable) { null }

    fun getAllPendingFiles(ctx: Context): List<File> {
        val dir = File(ctx.filesDir, PENDING_DIR)
        return if (dir.exists()) dir.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        else emptyList()
    }

    fun deletePendingFile(ctx: Context, crashId: String) {
        File(ctx.filesDir, "$PENDING_DIR/$crashId.json").delete()
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private fun detectKind(t: Throwable): String = when {
        t.stackTrace.any { it.className.contains("compose", ignoreCase = true) } -> "COMPOSE"
        t.stackTrace.any { it.className.contains("coroutine", ignoreCase = true) } -> "COROUTINE"
        t.stackTrace.any { it.className.contains("Binder") || it.className.contains("IPC") } -> "IPC"
        t is OutOfMemoryError -> "OOM"
        else -> "JAVA"
    }

    private fun getProcessName(): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            android.app.Application.getProcessName()
        else {
            val cmdline = File("/proc/${android.os.Process.myPid()}/cmdline").readText()
            cmdline.trimEnd('\u0000')
        }
    } catch (_: Throwable) { "com.accu.controlcenter" }

    private fun getCpuUsage(): Float = try {
        val stat = File("/proc/stat").readLines().first()
        val parts = stat.split(" ").filter { it.isNotBlank() }.drop(1).map { it.toLongOrNull() ?: 0L }
        if (parts.size >= 4) {
            val total = parts.sum()
            val idle = parts[3]
            if (total > 0) ((total - idle) * 100f / total) else 0f
        } else 0f
    } catch (_: Throwable) { 0f }

    private fun getBatteryPct(ctx: Context): Pair<Int, Boolean> = try {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        Pair(pct, charging)
    } catch (_: Throwable) { Pair(-1, false) }
}
