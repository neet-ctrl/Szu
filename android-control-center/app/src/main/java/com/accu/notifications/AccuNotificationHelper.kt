package com.accu.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.accu.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────
//  Channel IDs — canonical single source of truth
// ─────────────────────────────────────────────────────────────────
object AccuChannels {
    const val CALL_RECORDING    = "call_recording"
    const val AUDIO_DSP         = "audio_dsp"
    const val SHIZUKU_SERVICE   = "shizuku_service"
    const val STORAGE_ALERTS    = "storage_alerts"
    const val CLEANUP_WORKER    = "cleanup_worker"
    const val PRIVACY_TRACKER   = "privacy_tracker"
    const val FREEZE_SCHEDULER  = "freeze_scheduler"
    const val KEY_MAPPER        = "key_mapper"
    const val APP_MANAGER       = "app_manager"
    const val NETWORK_CHANGES   = "network_changes"
    const val SHELL_COMPLETE    = "shell_complete"
    const val GENERAL           = "general"
    const val CRASH_REPORTS     = "accu_crash"

    // Notification IDs
    const val ID_CALL_REC_ACTIVE    = 1001
    const val ID_CALL_REC_SAVED     = 1002
    const val ID_DSP_ACTIVE         = 2001
    const val ID_SHIZUKU_DOWN       = 3001
    const val ID_SHIZUKU_READY      = 3002
    const val ID_STORAGE_WARN       = 4001
    const val ID_STORAGE_CRITICAL   = 4002
    const val ID_CLEANUP_DONE       = 4003
    const val ID_TRACKER_BLOCKED    = 5001
    const val ID_FREEZE_AUTO        = 6001
    const val ID_KEY_MAPPER_ACTIVE  = 7001
    const val ID_APP_INSTALLED      = 8001
    const val ID_APP_BATCH_DONE     = 8002
    const val ID_NETWORK_VPN_DROP   = 9001
    const val ID_SHELL_DONE         = 10001
    const val ID_CRASH_REPORT       = 9900
}

// ─────────────────────────────────────────────────────────────────
//  Feature definition — drives the UI and channel registration
// ─────────────────────────────────────────────────────────────────
data class NotificationFeature(
    val channelId: String,
    val featureName: String,
    val description: String,
    val importance: Int,
    val moduleSource: String,
    val defaultEnabled: Boolean = true,
)

val ALL_NOTIFICATION_FEATURES = listOf(
    NotificationFeature(
        AccuChannels.CALL_RECORDING, "Call Recorder",
        "Recording started, stopped, and file saved",
        NotificationManager.IMPORTANCE_DEFAULT, "ShizuCallRecorder",
    ),
    NotificationFeature(
        AccuChannels.AUDIO_DSP, "Audio DSP Engine",
        "JamesDSP processing service status & preset changes",
        NotificationManager.IMPORTANCE_LOW, "RootlessJamesDSP",
    ),
    NotificationFeature(
        AccuChannels.SHIZUKU_SERVICE, "Shizuku Service",
        "Shizuku started, stopped, needs restart, or ADB disconnected",
        NotificationManager.IMPORTANCE_HIGH, "Shizuku",
    ),
    NotificationFeature(
        AccuChannels.STORAGE_ALERTS, "Storage Alerts",
        "Low storage warning (<15%) and critical alert (<5%)",
        NotificationManager.IMPORTANCE_HIGH, "SD Maid SE",
    ),
    NotificationFeature(
        AccuChannels.CLEANUP_WORKER, "Storage Cleanup",
        "Background cache & junk file cleanup completed",
        NotificationManager.IMPORTANCE_LOW, "SD Maid SE",
    ),
    NotificationFeature(
        AccuChannels.PRIVACY_TRACKER, "Tracker Blocker",
        "New trackers blocked (batched, max once per hour)",
        NotificationManager.IMPORTANCE_LOW, "Blocker",
    ),
    NotificationFeature(
        AccuChannels.FREEZE_SCHEDULER, "App Freeze Scheduler",
        "Apps auto-frozen or unfrozen by schedule",
        NotificationManager.IMPORTANCE_DEFAULT, "Hail",
    ),
    NotificationFeature(
        AccuChannels.KEY_MAPPER, "Key Mapper",
        "Key mapping service active, trigger fired",
        NotificationManager.IMPORTANCE_LOW, "Key Mapper",
    ),
    NotificationFeature(
        AccuChannels.APP_MANAGER, "App Manager",
        "Install, uninstall, and batch operation results",
        NotificationManager.IMPORTANCE_DEFAULT, "Inure / Canta",
    ),
    NotificationFeature(
        AccuChannels.NETWORK_CHANGES, "Network Changes",
        "VPN disconnected, network type change (Wi-Fi ↔ cellular)",
        NotificationManager.IMPORTANCE_DEFAULT, "Better Internet Tiles",
    ),
    NotificationFeature(
        AccuChannels.SHELL_COMPLETE, "Shell Command Done",
        "Long-running ADB/shell command (>5 s) completed",
        NotificationManager.IMPORTANCE_LOW, "aShellYou",
        defaultEnabled = false,
    ),
)

// ─────────────────────────────────────────────────────────────────
//  Helper — posts notifications guarded by per-channel prefs
// ─────────────────────────────────────────────────────────────────
@Singleton
class AccuNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: NotificationPreferences,
) {
    private val nm = NotificationManagerCompat.from(context)

    fun post(channelId: String, notifId: Int, block: NotificationCompat.Builder.() -> Unit) {
        if (!prefs.isChannelEnabled(channelId)) return
        postRaw(channelId, notifId, block)
    }

    /** Bypasses the per-channel enabled check — used only by the Notification Center test button. */
    fun postTest(channelId: String, notifId: Int, block: NotificationCompat.Builder.() -> Unit) {
        postRaw(channelId, notifId, block)
    }

    private fun postRaw(channelId: String, notifId: Int, block: NotificationCompat.Builder.() -> Unit) {
        if (!nm.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply(block)
        try { nm.notify(notifId, builder.build()) } catch (_: SecurityException) {}
    }

    private fun launchIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ── Shizuku ──────────────────────────────────────────────────
    fun notifyShizukuDown() = post(AccuChannels.SHIZUKU_SERVICE, AccuChannels.ID_SHIZUKU_DOWN) {
        setContentTitle("Shizuku Service Stopped")
        setContentText("Elevated access lost. Tap to restart Shizuku.")
        setPriority(NotificationCompat.PRIORITY_HIGH)
        setContentIntent(launchIntent())
        setAutoCancel(true)
    }

    fun notifyShizukuReady() = post(AccuChannels.SHIZUKU_SERVICE, AccuChannels.ID_SHIZUKU_READY) {
        setContentTitle("Shizuku Ready")
        setContentText("Elevated access granted. All ACC features active.")
        setAutoCancel(true)
    }

    // ── Call Recorder ─────────────────────────────────────────────
    fun notifyRecordingStarted(number: String) = post(AccuChannels.CALL_RECORDING, AccuChannels.ID_CALL_REC_ACTIVE) {
        setContentTitle("Recording call…")
        setContentText(number.ifBlank { "Unknown number" })
        setOngoing(true)
        setUsesChronometer(true)
    }

    fun notifyRecordingSaved(fileSizeKb: Long) = post(AccuChannels.CALL_RECORDING, AccuChannels.ID_CALL_REC_SAVED) {
        setContentTitle("Call recording saved")
        setContentText("File size: ${fileSizeKb} KB · Tap to open")
        setAutoCancel(true)
        setContentIntent(launchIntent())
    }

    // ── Storage ───────────────────────────────────────────────────
    fun notifyStorageLow(pct: Int) = post(AccuChannels.STORAGE_ALERTS, AccuChannels.ID_STORAGE_WARN) {
        setContentTitle("Storage Low — ${pct}% used")
        setContentText("Free some space to keep ACC features working normally.")
        setPriority(NotificationCompat.PRIORITY_HIGH)
        setContentIntent(launchIntent())
        setAutoCancel(true)
    }

    fun notifyStorageCritical(pct: Int) = post(AccuChannels.STORAGE_ALERTS, AccuChannels.ID_STORAGE_CRITICAL) {
        setContentTitle("⚠ Storage Critical — ${pct}% used")
        setContentText("Less than 5% free. Device may become unstable.")
        setPriority(NotificationCompat.PRIORITY_MAX)
        setAutoCancel(false)
    }

    fun notifyCleanupDone(freedMb: Long) = post(AccuChannels.CLEANUP_WORKER, AccuChannels.ID_CLEANUP_DONE) {
        setContentTitle("Cleanup complete")
        setContentText("Freed ${freedMb} MB of cache and junk files.")
        setAutoCancel(true)
    }

    // ── Privacy / Tracker Blocker ─────────────────────────────────
    fun notifyTrackersBlocked(count: Int, appName: String) = post(AccuChannels.PRIVACY_TRACKER, AccuChannels.ID_TRACKER_BLOCKED) {
        setContentTitle("$count tracker${if (count != 1) "s" else ""} blocked")
        setContentText("In $appName — tap for details.")
        setAutoCancel(true)
        setContentIntent(launchIntent())
    }

    // ── App Freeze Scheduler ──────────────────────────────────────
    fun notifyAppFrozen(appName: String) = post(AccuChannels.FREEZE_SCHEDULER, AccuChannels.ID_FREEZE_AUTO) {
        setContentTitle("App frozen by schedule")
        setContentText("$appName frozen to save battery/data.")
        setAutoCancel(true)
    }

    // ── App Manager ───────────────────────────────────────────────
    fun notifyAppInstalled(appName: String) = post(AccuChannels.APP_MANAGER, AccuChannels.ID_APP_INSTALLED) {
        setContentTitle("App installed")
        setContentText("$appName was installed successfully.")
        setAutoCancel(true)
    }

    fun notifyBatchOpDone(opName: String, count: Int) = post(AccuChannels.APP_MANAGER, AccuChannels.ID_APP_BATCH_DONE) {
        setContentTitle("Batch $opName complete")
        setContentText("$count apps processed.")
        setAutoCancel(true)
    }

    // ── Network ───────────────────────────────────────────────────
    fun notifyVpnDropped() = post(AccuChannels.NETWORK_CHANGES, AccuChannels.ID_NETWORK_VPN_DROP) {
        setContentTitle("VPN disconnected")
        setContentText("Your VPN connection dropped. Traffic is unprotected.")
        setPriority(NotificationCompat.PRIORITY_HIGH)
        setAutoCancel(true)
    }

    // ── Shell ─────────────────────────────────────────────────────
    fun notifyShellDone(command: String, exitCode: Int) = post(AccuChannels.SHELL_COMPLETE, AccuChannels.ID_SHELL_DONE) {
        setContentTitle(if (exitCode == 0) "Command succeeded" else "Command failed (exit $exitCode)")
        setContentText(command.take(80))
        setAutoCancel(true)
        setContentIntent(launchIntent())
    }
}
