package com.accu.crash

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/**
 * Handles notification action intents for crash notifications.
 * Must be registered in AndroidManifest.xml with explicit exported=false.
 */
class CrashBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra("notif_id", -1)
        val crashId = intent.getStringExtra("crash_id") ?: ""

        when (intent.action) {
            ACTION_COPY -> {
                val json = CrashEngine.readPendingCrashFile(context, crashId)
                    ?.toString(2)
                    ?: "Crash log not available (already migrated to database)"
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ACCU Crash Log", json))
                Toast.makeText(context, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
                if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId)
            }
            ACTION_RESTART -> {
                if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId)
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (launch != null) context.startActivity(launch)
            }
            ACTION_DISMISS -> {
                if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId)
            }
        }
    }

    companion object {
        const val ACTION_COPY    = "com.accu.crash.ACTION_COPY"
        const val ACTION_RESTART = "com.accu.crash.ACTION_RESTART"
        const val ACTION_DISMISS = "com.accu.crash.ACTION_DISMISS"
    }
}
