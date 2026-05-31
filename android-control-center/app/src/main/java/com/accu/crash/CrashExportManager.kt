package com.accu.crash

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.accu.data.db.entities.CrashEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val exportDir = File(context.cacheDir, "crash_exports").also { it.mkdirs() }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    // ─── Single crash exports ─────────────────────────────────────────────────

    suspend fun exportAsTxt(crash: CrashEntity): File = withContext(Dispatchers.IO) {
        val file = File(exportDir, "crash_${fileFormat.format(Date(crash.timestamp))}.txt")
        file.writeText(buildTxtReport(crash))
        file
    }

    suspend fun exportAsJson(crash: CrashEntity): File = withContext(Dispatchers.IO) {
        val file = File(exportDir, "crash_${fileFormat.format(Date(crash.timestamp))}.json")
        file.writeText(buildJsonObject(crash).toString(2))
        file
    }

    suspend fun exportAsMarkdown(crash: CrashEntity): File = withContext(Dispatchers.IO) {
        val file = File(exportDir, "crash_${fileFormat.format(Date(crash.timestamp))}.md")
        file.writeText(buildMarkdownReport(crash))
        file
    }

    suspend fun exportAsHtml(crash: CrashEntity): File = withContext(Dispatchers.IO) {
        val file = File(exportDir, "crash_${fileFormat.format(Date(crash.timestamp))}.html")
        file.writeText(buildHtmlReport(crash))
        file
    }

    suspend fun exportAsZip(crash: CrashEntity): File = withContext(Dispatchers.IO) {
        val ts = fileFormat.format(Date(crash.timestamp))
        val zip = File(exportDir, "crash_${ts}.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            fun addEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
            addEntry("crash.txt", buildTxtReport(crash))
            addEntry("crash.json", buildJsonObject(crash).toString(2))
            addEntry("crash.md", buildMarkdownReport(crash))
            addEntry("stacktrace.txt", crash.stackTrace)
        }
        zip
    }

    // ─── Bulk exports ─────────────────────────────────────────────────────────

    suspend fun exportAllAsJson(crashes: List<CrashEntity>): File = withContext(Dispatchers.IO) {
        val file = File(exportDir, "all_crashes_${fileFormat.format(Date())}.json")
        val arr = JSONArray()
        crashes.forEach { arr.put(buildJsonObject(it)) }
        file.writeText(JSONObject().put("crashes", arr).put("count", crashes.size)
            .put("exportedAt", dateFormat.format(Date())).toString(2))
        file
    }

    suspend fun exportAllAsZip(crashes: List<CrashEntity>): File = withContext(Dispatchers.IO) {
        val zip = File(exportDir, "all_crashes_${fileFormat.format(Date())}.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            // Summary
            zos.putNextEntry(ZipEntry("summary.txt"))
            zos.write(buildSummaryText(crashes).toByteArray())
            zos.closeEntry()
            // Individual crashes
            crashes.forEachIndexed { idx, crash ->
                val name = "crash_${String.format("%04d", idx + 1)}_${crash.exceptionType.substringAfterLast('.')}.txt"
                zos.putNextEntry(ZipEntry(name))
                zos.write(buildTxtReport(crash).toByteArray())
                zos.closeEntry()
            }
            // JSON bundle
            val arr = JSONArray()
            crashes.forEach { arr.put(buildJsonObject(it)) }
            zos.putNextEntry(ZipEntry("crashes.json"))
            zos.write(arr.toString(2).toByteArray())
            zos.closeEntry()
        }
        zip
    }

    // ─── Share Intent ─────────────────────────────────────────────────────────

    fun buildShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mime = when (file.extension) {
            "json" -> "application/json"
            "zip"  -> "application/zip"
            "html" -> "text/html"
            "md"   -> "text/markdown"
            else   -> "text/plain"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ACCU Crash Report — ${file.nameWithoutExtension}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearExportCache() = exportDir.listFiles()?.forEach { it.delete() }

    // ─── Formatters ──────────────────────────────────────────────────────────

    private fun buildTxtReport(c: CrashEntity): String = buildString {
        appendLine("═══════════════════════════════════════════════════════")
        appendLine("  ACCU CRASH REPORT")
        appendLine("═══════════════════════════════════════════════════════")
        appendLine("Crash ID   : ${c.crashId}")
        appendLine("Time       : ${dateFormat.format(Date(c.timestamp))}")
        appendLine("Session    : ${c.sessionId}")
        appendLine("───────────────────────────────────────────────────────")
        appendLine("APP & BUILD")
        appendLine("  Version  : ${c.appVersion} (${c.buildVersionCode}) [${c.buildType}]")
        appendLine("───────────────────────────────────────────────────────")
        appendLine("DEVICE")
        appendLine("  Model    : ${c.deviceModel}")
        appendLine("  Android  : ${c.androidVersion} (API ${c.sdkInt})")
        appendLine("  ABI      : ${c.abi}")
        appendLine("  RAM      : ${c.freeRamMb} MB free / ${c.totalRamMb} MB total")
        appendLine("  CPU      : ${c.cpuUsagePct.toInt()}%")
        appendLine("  Battery  : ${c.batteryPct}% ${if (c.batteryCharging) "[charging]" else ""}")
        appendLine("  LowMem   : ${c.isLowMemory}")
        appendLine("───────────────────────────────────────────────────────")
        appendLine("PROCESS")
        appendLine("  Process  : ${c.processName} (PID ${c.processId})")
        appendLine("  Thread   : ${c.threadName} (TID ${c.threadId})")
        appendLine("───────────────────────────────────────────────────────")
        appendLine("EXCEPTION")
        appendLine("  Type     : ${c.exceptionType}")
        appendLine("  Message  : ${c.exceptionMessage}")
        appendLine("  Kind     : ${c.crashKind} | Fatal: ${c.isFatal} | ANR: ${c.isAnr}")
        appendLine("───────────────────────────────────────────────────────")
        appendLine("CONTEXT AT CRASH")
        appendLine("  Activity : ${c.activityName}")
        appendLine("  Route    : ${c.screenRoute}")
        appendLine("  Fragment : ${c.fragmentName}")
        appendLine("  Service  : ${c.serviceName}")
        appendLine("  ViewModel: ${c.viewModelName}")
        appendLine("───────────────────────────────────────────────────────")
        appendLine("SYSTEM STATE")
        appendLine("  Network  : ${c.networkState}")
        appendLine("  Shizuku  : ${c.shizukuState}")
        appendLine("  Root     : ${c.rootState}")
        appendLine("  WiFi ADB : ${c.wirelessAdbState}")
        appendLine("───────────────────────────────────────────────────────")
        appendLine("ANALYSIS")
        appendLine("  Risk     : ${c.riskLevel}")
        appendLine("  Module   : ${c.affectedModule}")
        appendLine("  Cause    : ${c.possibleCause}")
        appendLine("  Fix      : ${c.suggestedFix}")
        appendLine("═══════════════════════════════════════════════════════")
        appendLine("STACK TRACE")
        appendLine("═══════════════════════════════════════════════════════")
        appendLine(c.stackTrace)
        if (c.causeChain.isNotBlank() && c.causeChain != "[]") {
            appendLine("───────────────────────────────────────────────────────")
            appendLine("CAUSE CHAIN")
            appendLine(c.causeChain)
        }
        if (c.userActionsJson.isNotBlank() && c.userActionsJson != "[]") {
            appendLine("───────────────────────────────────────────────────────")
            appendLine("USER ACTIONS BEFORE CRASH (most recent first)")
            appendLine(c.userActionsJson)
        }
    }

    private fun buildJsonObject(c: CrashEntity): JSONObject = JSONObject().apply {
        put("crashId", c.crashId); put("sessionId", c.sessionId)
        put("timestamp", c.timestamp); put("timestampHuman", dateFormat.format(Date(c.timestamp)))
        put("app", JSONObject().apply {
            put("version", c.appVersion); put("versionCode", c.buildVersionCode); put("buildType", c.buildType)
        })
        put("device", JSONObject().apply {
            put("model", c.deviceModel); put("manufacturer", c.deviceManufacturer)
            put("android", c.androidVersion); put("sdk", c.sdkInt); put("abi", c.abi)
            put("ramFreeMb", c.freeRamMb); put("ramTotalMb", c.totalRamMb)
            put("cpuPct", c.cpuUsagePct); put("batteryPct", c.batteryPct); put("isLowMemory", c.isLowMemory)
        })
        put("process", JSONObject().apply {
            put("name", c.processName); put("pid", c.processId)
            put("thread", c.threadName); put("tid", c.threadId)
        })
        put("exception", JSONObject().apply {
            put("type", c.exceptionType); put("message", c.exceptionMessage)
            put("stackTrace", c.stackTrace); put("causeChain", c.causeChain)
            put("kind", c.crashKind); put("isFatal", c.isFatal); put("isAnr", c.isAnr)
        })
        put("context", JSONObject().apply {
            put("activity", c.activityName); put("fragment", c.fragmentName)
            put("route", c.screenRoute); put("service", c.serviceName); put("viewModel", c.viewModelName)
        })
        put("systemState", JSONObject().apply {
            put("network", c.networkState); put("shizuku", c.shizukuState)
            put("root", c.rootState); put("wirelessAdb", c.wirelessAdbState)
        })
        put("analysis", JSONObject().apply {
            put("riskLevel", c.riskLevel); put("module", c.affectedModule)
            put("possibleCause", c.possibleCause); put("suggestedFix", c.suggestedFix)
            put("similarCrashCount", c.similarCrashCount)
        })
        put("userActions", c.userActionsJson)
        put("sessionDurationSec", c.sessionDurationSec)
    }

    private fun buildMarkdownReport(c: CrashEntity): String = buildString {
        appendLine("# ACCU Crash Report")
        appendLine("> **${c.exceptionType.substringAfterLast('.')}** · ${dateFormat.format(Date(c.timestamp))}")
        appendLine()
        appendLine("## Summary")
        appendLine("| Field | Value |")
        appendLine("|-------|-------|")
        appendLine("| Crash ID | `${c.crashId}` |")
        appendLine("| Risk | **${c.riskLevel}** |")
        appendLine("| Module | ${c.affectedModule} |")
        appendLine("| Kind | ${c.crashKind} |")
        appendLine("| Fatal | ${c.isFatal} |")
        appendLine("| ANR | ${c.isAnr} |")
        appendLine()
        appendLine("## Exception")
        appendLine("```")
        appendLine("${c.exceptionType}: ${c.exceptionMessage}")
        appendLine("```")
        appendLine()
        appendLine("## Stack Trace")
        appendLine("```")
        appendLine(c.stackTrace.take(4000))
        appendLine("```")
        appendLine()
        appendLine("## Analysis")
        appendLine("**Possible Cause:** ${c.possibleCause}")
        appendLine()
        appendLine("**Suggested Fix:** ${c.suggestedFix}")
        appendLine()
        appendLine("## Device")
        appendLine("- **Model:** ${c.deviceModel}")
        appendLine("- **Android:** ${c.androidVersion} (API ${c.sdkInt})")
        appendLine("- **RAM:** ${c.freeRamMb} MB free / ${c.totalRamMb} MB")
        appendLine("- **Battery:** ${c.batteryPct}%")
    }

    private fun buildHtmlReport(c: CrashEntity): String = """
<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">
<title>ACCU Crash Report</title>
<style>
  body{font-family:monospace;background:#0d0d0d;color:#e0e0e0;padding:20px}
  h1{color:#FF6B6B}h2{color:#81C995;border-bottom:1px solid #333;padding-bottom:4px}
  .badge{display:inline-block;padding:2px 8px;border-radius:4px;font-size:.8em}
  .critical{background:#FF4444;color:#fff}.high{background:#FF8800;color:#fff}
  .medium{background:#FFD700;color:#000}.low{background:#00AA44;color:#fff}
  pre{background:#1a1a1a;padding:16px;border-radius:8px;overflow-x:auto;font-size:.85em}
  table{width:100%;border-collapse:collapse}td,th{padding:6px 12px;border:1px solid #333;text-align:left}
  th{background:#1f1f1f}
</style></head><body>
<h1>⚠ ACCU Crash Report</h1>
<p><strong>${c.exceptionType.substringAfterLast('.')}</strong> &nbsp;
<span class="badge ${c.riskLevel.lowercase()}">${c.riskLevel}</span></p>
<p>${dateFormat.format(Date(c.timestamp))} &nbsp;·&nbsp; ${c.affectedModule}</p>
<h2>Exception</h2>
<pre>${c.exceptionType}: ${htmlEsc(c.exceptionMessage)}</pre>
<h2>Stack Trace</h2>
<pre>${htmlEsc(c.stackTrace.take(8000))}</pre>
<h2>Analysis</h2>
<table><tr><th>Possible Cause</th><td>${htmlEsc(c.possibleCause)}</td></tr>
<tr><th>Suggested Fix</th><td>${htmlEsc(c.suggestedFix)}</td></tr></table>
<h2>Device</h2>
<table><tr><th>Model</th><td>${htmlEsc(c.deviceModel)}</td></tr>
<tr><th>Android</th><td>${c.androidVersion} (API ${c.sdkInt})</td></tr>
<tr><th>RAM</th><td>${c.freeRamMb} MB free / ${c.totalRamMb} MB</td></tr>
<tr><th>CPU</th><td>${c.cpuUsagePct.toInt()}%</td></tr>
<tr><th>Battery</th><td>${c.batteryPct}%</td></tr></table>
</body></html>""".trimIndent()

    private fun buildSummaryText(crashes: List<CrashEntity>): String = buildString {
        appendLine("ACCU Crash Export Summary")
        appendLine("Generated: ${dateFormat.format(Date())}")
        appendLine("Total Crashes: ${crashes.size}")
        appendLine("Fatal: ${crashes.count { it.isFatal }}")
        appendLine("ANRs: ${crashes.count { it.isAnr }}")
        appendLine("Critical: ${crashes.count { it.riskLevel == "CRITICAL" }}")
    }

    private fun htmlEsc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
