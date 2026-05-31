package com.accu.crash

import com.accu.data.db.entities.CrashEntity

/**
 * Stateless crash analysis engine.
 *
 * Given the raw crash fields it produces:
 *  - riskLevel  : CRITICAL / HIGH / MEDIUM / LOW
 *  - possibleCause : human-readable root cause hypothesis
 *  - affectedModule: ACCU module that most likely caused the crash
 *  - suggestedFix  : actionable first-response fix
 */
object CrashAnalyzer {

    data class Analysis(
        val riskLevel: String,
        val possibleCause: String,
        val affectedModule: String,
        val suggestedFix: String,
    )

    fun analyze(
        exceptionType: String,
        exceptionMessage: String,
        stackTrace: String,
        crashKind: String,
        isAnr: Boolean,
        isFatal: Boolean,
    ): Analysis {
        if (isAnr) return Analysis(
            riskLevel = "HIGH",
            possibleCause = "Application Not Responding — main thread blocked for >5 s. Likely heavy I/O, lock contention, or a synchronous Room/network call on the UI thread.",
            affectedModule = detectModule(stackTrace),
            suggestedFix = "Move blocking operations to Dispatchers.IO. Check for synchronized blocks or Mutex.lock() on the main thread.",
        )

        val risk = computeRisk(exceptionType, isFatal)
        val cause = computeCause(exceptionType, exceptionMessage, stackTrace, crashKind)
        val module = detectModule(stackTrace)
        val fix = computeFix(exceptionType, exceptionMessage, stackTrace, module)

        return Analysis(
            riskLevel = risk,
            possibleCause = cause,
            affectedModule = module,
            suggestedFix = fix,
        )
    }

    fun analyze(entity: CrashEntity) = analyze(
        exceptionType = entity.exceptionType,
        exceptionMessage = entity.exceptionMessage,
        stackTrace = entity.stackTrace,
        crashKind = entity.crashKind,
        isAnr = entity.isAnr,
        isFatal = entity.isFatal,
    )

    // ─── Risk ─────────────────────────────────────────────────────────────────

    private fun computeRisk(type: String, fatal: Boolean): String {
        if (!fatal) return "LOW"
        return when {
            type.endsWith("OutOfMemoryError") || type.endsWith("StackOverflowError") -> "CRITICAL"
            type.endsWith("NullPointerException") || type.endsWith("IllegalStateException") -> "HIGH"
            type.endsWith("SecurityException") || type.endsWith("IllegalArgumentException") -> "HIGH"
            type.endsWith("RuntimeException") || type.contains("android.os") -> "HIGH"
            type.endsWith("IOException") || type.contains("Binder") -> "MEDIUM"
            else -> "MEDIUM"
        }
    }

    // ─── Cause ────────────────────────────────────────────────────────────────

    private fun computeCause(type: String, message: String, stack: String, kind: String): String {
        val msg = message.lowercase()
        val stk = stack.lowercase()
        return when {
            type.endsWith("NullPointerException") ->
                "A null object reference was dereferenced. The object was not initialised before use — common when collecting a StateFlow before the ViewModel emits its first value."
            type.endsWith("OutOfMemoryError") ->
                "JVM heap or native memory exhausted. The app allocated more memory than the device limit allows — typically caused by bitmap leaks, large collections, or memory-intensive operations."
            type.endsWith("StackOverflowError") ->
                "Infinite recursion — a function called itself (directly or indirectly) without a base case."
            type.endsWith("SecurityException") ->
                "A required Android permission was not granted or was revoked. Check that the permission is declared in AndroidManifest and granted at runtime before calling this API."
            type.endsWith("DeadObjectException") || (stk.contains("binder") && type.contains("Remote")) ->
                "The remote Binder connection died (Shizuku service or ACCU system service crashed). The IPC call was made after the service stopped."
            type.endsWith("NetworkOnMainThreadException") ->
                "Network I/O was performed on the main/UI thread. Move it to Dispatchers.IO."
            type.endsWith("IllegalStateException") && msg.contains("coroutine") ->
                "A coroutine was resumed after its scope was cancelled, or a StateFlow was collected in the wrong lifecycle state."
            type.endsWith("IllegalStateException") && stk.contains("compose") ->
                "Compose state was read or written outside a Composition scope, or the LazyList key was duplicated."
            type.endsWith("CancellationException") ->
                "A coroutine was cancelled — this is usually intentional (navigating away), but the exception propagated through a missing try/catch."
            type.endsWith("FileNotFoundException") ->
                "A file path does not exist or the app lacks read/write permission. Verify the path and that MANAGE_EXTERNAL_STORAGE or WRITE_EXTERNAL_STORAGE is granted."
            kind == "IPC" ->
                "Binder IPC call failed — the bound service (Shizuku, ACCU API, or system service) was unreachable."
            kind == "COMPOSE" ->
                "Jetpack Compose rendering error. A Composable function threw an unhandled exception during recomposition."
            kind == "COROUTINE" ->
                "Unhandled exception in a coroutine. The coroutine had no CoroutineExceptionHandler and the exception was not caught."
            else ->
                "Unhandled exception: ${type.substringAfterLast('.')}. ${message.take(120)}"
        }
    }

    // ─── Module detection ────────────────────────────────────────────────────

    private fun detectModule(stack: String): String {
        val stk = stack.lowercase()
        return when {
            stk.contains("audio") || stk.contains("jamesdsp") || stk.contains("liveprog") -> "Audio / JamesDSP"
            stk.contains("shell") || stk.contains("ashell") -> "Shell / ADB"
            stk.contains("storage") || stk.contains("sdmaid") || stk.contains("filemanager") -> "Storage / Files"
            stk.contains("appmanager") || stk.contains("inure") || stk.contains("canta") || stk.contains("hail") -> "App Manager"
            stk.contains("shizuku") || stk.contains("binder") || stk.contains("aidl") -> "Shizuku / IPC"
            stk.contains("automation") || stk.contains("keymapper") -> "Automation / Key Mapper"
            stk.contains("customization") || stk.contains("colorblendr") || stk.contains("darq") -> "Customization"
            stk.contains("network") || stk.contains("tiles") -> "Network / Tiles"
            stk.contains("privacy") || stk.contains("blocker") -> "Privacy / Blocker"
            stk.contains("callrecorder") || stk.contains("scrcpy") -> "Call Recorder"
            stk.contains("language") || stk.contains("locale") -> "Language Selector"
            stk.contains("dashboard") -> "Dashboard"
            stk.contains("apiservice") || stk.contains("accusdk") -> "ACCU Service / SDK"
            stk.contains("navigation") || stk.contains("navhost") -> "Navigation"
            stk.contains("database") || stk.contains("room") -> "Database / Room"
            else -> "Core / Unknown"
        }
    }

    // ─── Fix ─────────────────────────────────────────────────────────────────

    private fun computeFix(type: String, message: String, stack: String, module: String): String {
        val stk = stack.lowercase()
        return when {
            type.endsWith("NullPointerException") ->
                "1. Add null-safety checks (?.let, ?: return). 2. Ensure the ViewModel emits an initial value before the screen collects the StateFlow. 3. Use collectAsStateWithLifecycle instead of collectAsState."
            type.endsWith("OutOfMemoryError") ->
                "1. Profile heap with Android Studio Memory Profiler. 2. Recycle Bitmaps after use. 3. Replace large in-memory lists with paging. 4. Enable largeHeap in AndroidManifest only as a last resort."
            type.endsWith("SecurityException") ->
                "1. Open Settings → Permission Center and grant the required permission. 2. For WRITE_SECURE_SETTINGS / adb-level permissions, ensure Shizuku is running. 3. Declare the permission in AndroidManifest."
            type.endsWith("DeadObjectException") || (stk.contains("binder") && type.contains("Remote")) ->
                "1. Wrap IPC calls in try/catch(RemoteException). 2. Check Shizuku connection state before calling. 3. Use AccuClient's connection callback to re-bind when service dies."
            type.endsWith("StackOverflowError") ->
                "1. Add a base case to the recursive function. 2. Convert recursion to an iterative loop. 3. Increase stack size only as a last resort."
            type.endsWith("NetworkOnMainThreadException") ->
                "1. Wrap the call in withContext(Dispatchers.IO) { }. 2. Use a Repository pattern that runs on IO dispatcher."
            module.contains("Shizuku") ->
                "1. Ensure Shizuku service is running (Settings → Shizuku Center). 2. Re-bind to the ACCU service after app resumes. 3. Handle RemoteException around all AIDL calls."
            module.contains("Audio") ->
                "1. Verify JamesDSP service is running. 2. Check that the audio session ID is valid before applying effects."
            module.contains("App Manager") ->
                "1. Ensure INSTALL_PACKAGES / DELETE_PACKAGES permissions are granted via Shizuku. 2. Check that the target package still exists before operating on it."
            else ->
                "1. Check the full stack trace for the root cause. 2. Wrap the failing call in try/catch. 3. Use Safe Mode (Restart → Safe Mode) to disable experimental modules and isolate the issue."
        }
    }
}
