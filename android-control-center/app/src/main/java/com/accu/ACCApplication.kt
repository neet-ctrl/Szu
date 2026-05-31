package com.accu

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.accu.crash.CrashEngine
import com.accu.crash.CrashNotificationManager
import com.accu.crash.CrashRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ACCApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var crashRepository: CrashRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        createNotificationChannels()

        // Install crash engine FIRST — before anything else can fail
        CrashEngine.install(this)

        // Migrate any crash files written by CrashEngine (runs before Hilt was up)
        appScope.launch {
            crashRepository.migratePendingCrashes()
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel("call_recording", "Call Recorder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Recording started, stopped, and file saved (ShizuCallRecorder)"
                setShowBadge(true)
            },
            NotificationChannel("audio_dsp", "Audio DSP Engine", NotificationManager.IMPORTANCE_LOW).apply {
                description = "JamesDSP processing service status and preset changes"
                setShowBadge(false)
            },
            NotificationChannel("shizuku_service", "Shizuku Service", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shizuku started, stopped, needs restart, or ADB disconnected"
                setShowBadge(true)
            },
            NotificationChannel("storage_alerts", "Storage Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Low storage warning (<15%) and critical alert (<5%)"
                setShowBadge(true)
            },
            NotificationChannel("cleanup_worker", "Storage Cleanup", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background cache and junk file cleanup completed (SD Maid SE)"
                setShowBadge(false)
            },
            NotificationChannel("privacy_tracker", "Tracker Blocker", NotificationManager.IMPORTANCE_LOW).apply {
                description = "New trackers blocked, batched max once per hour (Blocker)"
                setShowBadge(true)
            },
            NotificationChannel("freeze_scheduler", "App Freeze Scheduler", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Apps auto-frozen or unfrozen by schedule (Hail)"
                setShowBadge(false)
            },
            NotificationChannel("key_mapper", "Key Mapper", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Key mapping service active and trigger fired"
                setShowBadge(false)
            },
            NotificationChannel("app_manager", "App Manager", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Install, uninstall, and batch operation results (Inure / Canta)"
                setShowBadge(true)
            },
            NotificationChannel("network_changes", "Network Changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "VPN disconnected, network type change (Better Internet Tiles)"
                setShowBadge(true)
            },
            NotificationChannel("shell_complete", "Shell Command Done", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Long-running ADB/shell command (>5s) completed (aShellYou)"
                setShowBadge(false)
            },
            NotificationChannel("general", "General", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "General ACC alerts and status messages"
            },
            // ── Crash Reports ──────────────────────────────────────────────────────
            NotificationChannel(CrashNotificationManager.CHANNEL_ID, CrashNotificationManager.CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Instant crash alerts with copy, share, and restart actions"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            },
        )
        nm.createNotificationChannels(channels)
    }
}
