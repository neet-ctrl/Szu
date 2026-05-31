package com.accu.data.db.entities

import androidx.room.*

@Entity(
    tableName = "crash_logs",
    indices = [
        Index("timestamp"),
        Index("exceptionType"),
        Index("isFatal"),
        Index("isAnr"),
        Index("isPinned"),
        Index("isFavorited"),
        Index("riskLevel"),
    ],
)
data class CrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // ── Identity ──────────────────────────────────────────────
    val crashId: String,
    val sessionId: String,
    val timestamp: Long,

    // ── App / Build ───────────────────────────────────────────
    val appVersion: String,
    val buildVersionCode: Int,
    val buildType: String,           // debug / release

    // ── Device ────────────────────────────────────────────────
    val deviceModel: String,
    val deviceManufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val abi: String,
    val totalRamMb: Long,
    val freeRamMb: Long,
    val cpuUsagePct: Float,
    val batteryPct: Int,
    val batteryCharging: Boolean,
    val isLowMemory: Boolean,

    // ── Process / Thread ──────────────────────────────────────
    val processName: String,
    val processId: Int,
    val threadName: String,
    val threadId: Long,

    // ── Exception ─────────────────────────────────────────────
    val exceptionType: String,
    val exceptionMessage: String,
    val stackTrace: String,          // full text
    val causeChain: String,          // JSON array of cause messages

    // ── Context ───────────────────────────────────────────────
    val activityName: String,
    val fragmentName: String,
    val screenRoute: String,
    val serviceName: String,
    val viewModelName: String,

    // ── System State ──────────────────────────────────────────
    val networkState: String,        // WIFI / CELLULAR / NONE
    val shizukuState: String,        // RUNNING / STOPPED / NOT_INSTALLED / UNKNOWN
    val rootState: String,           // GRANTED / DENIED / NONE
    val wirelessAdbState: String,    // CONNECTED / DISCONNECTED

    // ── Session ───────────────────────────────────────────────
    val userActionsJson: String,     // JSON array of recent nav + actions (last 20)
    val sessionDurationSec: Long,

    // ── Classification ────────────────────────────────────────
    val isFatal: Boolean,            // uncaught → always true; handled exceptions → false
    val isAnr: Boolean,
    val crashKind: String,           // JAVA / KOTLIN / COMPOSE / COROUTINE / ANR / NATIVE / IPC

    // ── Analysis (auto-filled by CrashAnalyzer) ───────────────
    val riskLevel: String,           // CRITICAL / HIGH / MEDIUM / LOW
    val possibleCause: String,
    val affectedModule: String,
    val suggestedFix: String,
    val similarCrashCount: Int,      // how many prior crashes match same exceptionType

    // ── User Actions ──────────────────────────────────────────
    val isFavorited: Boolean = false,
    val isPinned: Boolean = false,
    val userNotes: String = "",
)
