package com.accu.crash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Crash notification — intentionally a plain object (no Hilt, no inject)
 * because it must work from the :crash process where Hilt is not started.
 *
 * Posts a MAX-priority sticky notification immediately when a crash occurs.
 * Notification actions are handled by [CrashBroadcastReceiver].
 */
object CrashNotificationManager {

    const val CHANNEL_ID  = "accu_crash"
    const val CHANNEL_NAME = "Crash Reports"
    private const val NOTIF_BASE_ID = 9900

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Instant crash alerts with copy, share, and restart actions"
            setShowBadge(true)
            enableLights(true)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun postCrashNotification(
        context: Context,
        crashId: String,
        exceptionTitle: String,
        exceptionMsg: String,
    ) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val notifId = NOTIF_BASE_ID + (crashId.hashCode() and 0x0FFF)

        // Open crash detail
        val openIntent = PendingIntent.getActivity(
            context, notifId,
            Intent().setClassName(context, "com.accu.ui.crash.CrashReportActivity")
                .putExtra("crash_id", crashId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Copy action
        val copyIntent = PendingIntent.getBroadcast(
            context, notifId + 1,
            Intent(CrashBroadcastReceiver.ACTION_COPY)
                .setPackage(context.packageName)
                .putExtra("crash_id", crashId)
                .putExtra("notif_id", notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Restart action
        val restartIntent = PendingIntent.getBroadcast(
            context, notifId + 2,
            Intent(CrashBroadcastReceiver.ACTION_RESTART)
                .setPackage(context.packageName)
                .putExtra("notif_id", notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Dismiss action
        val dismissIntent = PendingIntent.getBroadcast(
            context, notifId + 3,
            Intent(CrashBroadcastReceiver.ACTION_DISMISS)
                .setPackage(context.packageName)
                .putExtra("notif_id", notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠ ACCU Crashed — $exceptionTitle")
            .setContentText(exceptionMsg.take(100))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(exceptionMsg.take(300))
                .setBigContentTitle("⚠ ACCU Crashed")
                .setSummaryText(exceptionTitle))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_share, "Copy Log", copyIntent)
            .addAction(android.R.drawable.ic_media_play, "Restart", restartIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissIntent)

        try { nm.notify(notifId, builder.build()) } catch (_: SecurityException) {}
    }

    fun cancelNotification(context: Context, notifId: Int) {
        NotificationManagerCompat.from(context).cancel(notifId)
    }
}
