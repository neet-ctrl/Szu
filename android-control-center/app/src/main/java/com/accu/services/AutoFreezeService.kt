package com.accu.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber

class AutoFreezeService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            freezeAllFrozenApps()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun freezeAllFrozenApps() = withContext(Dispatchers.IO) {
        try {
            val prefs = getSharedPreferences("accu_freeze_prefs", MODE_PRIVATE)
            val frozenPackages = prefs.getStringSet("frozen_packages", emptySet()) ?: return@withContext
            if (frozenPackages.isEmpty()) return@withContext

            if (Shizuku.pingBinder()) {
                for (pkg in frozenPackages) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("pm", "suspend", pkg))
                        Timber.d("AutoFreeze: suspended $pkg")
                    } catch (e: Exception) {
                        Timber.e(e, "AutoFreeze: failed to suspend $pkg")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "AutoFreezeService error")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Auto Freeze", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Freeze Running")
            .setContentText("Freezing apps on screen off…")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "auto_freeze"
        private const val NOTIF_ID = 9001

        fun triggerFreeze(context: Context) {
            val intent = Intent(context, AutoFreezeService::class.java)
            context.startForegroundService(intent)
        }
    }
}
