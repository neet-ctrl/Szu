package com.accu.sdkdemo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LogManager {
    // No cap — keep all logs for the session (GC'd when app is killed)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        _logs.update { current -> current + LogEntry(level = level, tag = tag, message = message) }
    }

    fun debug(tag: String, message: String)   = log(tag, message, LogLevel.DEBUG)
    fun info(tag: String, message: String)    = log(tag, message, LogLevel.INFO)
    fun success(tag: String, message: String) = log(tag, message, LogLevel.SUCCESS)
    fun warning(tag: String, message: String) = log(tag, message, LogLevel.WARNING)
    fun error(tag: String, message: String)   = log(tag, message, LogLevel.ERROR)

    fun clear() { _logs.value = emptyList() }

    fun exportText(): String = buildString {
        appendLine("=== ACCU SDK Test App — Log Export ===")
        appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        appendLine("Total entries: ${_logs.value.size}")
        appendLine("=".repeat(50))
        appendLine()
        _logs.value.forEach { entry ->
            appendLine("[${entry.formattedDate}] [${entry.level.name.padEnd(7)}] [${entry.tag}] ${entry.message}")
        }
    }

    fun exportJson(): String {
        val sb = StringBuilder()
        sb.append("[\n")
        _logs.value.forEachIndexed { i, entry ->
            sb.append("  {\n")
            sb.append("    \"id\": ${entry.id},\n")
            sb.append("    \"timestamp\": ${entry.timestamp},\n")
            sb.append("    \"time\": \"${entry.formattedDate}\",\n")
            sb.append("    \"level\": \"${entry.level.name}\",\n")
            sb.append("    \"tag\": \"${entry.tag.replace("\"", "\\\"")}\",\n")
            sb.append("    \"message\": \"${entry.message.replace("\"", "\\\"").replace("\n", "\\n")}\"\n")
            sb.append("  }")
            if (i < _logs.value.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }
}
