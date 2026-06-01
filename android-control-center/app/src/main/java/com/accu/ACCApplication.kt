package com.accu

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.accu.connection.AccuConnectionManager
import com.accu.crash.CrashEngine
import com.accu.crash.CrashNotificationManager
import com.accu.crash.CrashRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.conscrypt.Conscrypt
import timber.log.Timber
import java.security.Security
import javax.inject.Inject

@HiltAndroidApp
class ACCApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var crashRepository: CrashRepository
    @Inject lateinit var connectionManager: AccuConnectionManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Install Conscrypt as the top-priority security provider globally.
        // This ensures that every JCA operation (KeyFactory, KeyPairGenerator, Signature,
        // SSLContext) uses the same OpenSSL-backed implementation.  Without this, the
        // platform's default provider may be chosen for some operations, leading to
        // key-type mismatches where Conscrypt's TLS engine cannot perform RSA-PSS signing
        // on the private key during TLS 1.3 CertificateVerify.
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
            Timber.d("ACCApplication: Conscrypt installed as provider #1")
        } catch (e: Exception) {
            Timber.w(e, "ACCApplication: Conscrypt provider install failed (non-fatal)")
        }

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        createNotificationChannels()

        // Install crash engine FIRST — before anything else can fail
        CrashEngine.install(this)

        // Migrate any crash files written by CrashEngine (runs before Hilt was up)
        appScope.launch {
            crashRepository.migratePendingCrashes()
        }

        // Check root/wireless ADB state on startup; reconnect if previously paired
        appScope.launch(Dispatchers.IO) {
            connectionManager.checkAndUpdateState()
            // If not already connected, try to reconnect to the last known ADB session
            if (!connectionManager.isPrivilegeAvailable()) {
                val reconnected = connectionManager.reconnect()
                if (reconnected) {
                    Timber.i("ACCApplication: auto-reconnected to last ADB session")
                }
            }
        }

        // Always listen for the Android Wireless Debugging pairing mDNS service in the background.
        // When the user opens Settings → Developer Options → Wireless Debugging → Pair device with
        // pairing code on their device, Android broadcasts the pairing service via mDNS.
        // AccuConnectionManager picks it up and posts a notification so the user only needs to
        // enter the 6-digit code — IP and port are auto-detected.
        appScope.launch(Dispatchers.IO) {
            // Only start discovery if not already privileged — avoids redundant mDNS listeners
            // when the device is already rooted or connected.
            if (!connectionManager.isPrivilegeAvailable()) {
                Timber.i("ACCApplication: starting background mDNS pairing discovery")
                connectionManager.startPairingDiscovery()
            }
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel("call_recording", "Call Recorder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Recording started, stopped, and file saved"
                setShowBadge(true)
            },
            NotificationChannel("audio_dsp", "Audio DSP Engine", NotificationManager.IMPORTANCE_LOW).apply {
                description = "JamesDSP processing service status and preset changes"
                setShowBadge(false)
            },
            // ACCU Connection — self-sufficient privilege engine
            NotificationChannel(AccuConnectionManager.CHANNEL_ID, AccuConnectionManager.CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "ACCU wireless ADB connection status, pairing codes, and privilege alerts"
                setShowBadge(true)
            },
            NotificationChannel("storage_alerts", "Storage Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Low storage warning (<15%) and critical alert (<5%)"
                setShowBadge(true)
            },
            NotificationChannel("cleanup_worker", "Storage Cleanup", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background cache and junk file cleanup completed"
                setShowBadge(false)
            },
            NotificationChannel("privacy_tracker", "Tracker Blocker", NotificationManager.IMPORTANCE_LOW).apply {
                description = "New trackers blocked, batched max once per hour"
                setShowBadge(true)
            },
            NotificationChannel("freeze_scheduler", "App Freeze Scheduler", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Apps auto-frozen or unfrozen by schedule"
                setShowBadge(false)
            },
            NotificationChannel("key_mapper", "Key Mapper", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Key mapping service active and trigger fired"
                setShowBadge(false)
            },
            NotificationChannel("app_manager", "App Manager", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Install, uninstall, and batch operation results"
                setShowBadge(true)
            },
            NotificationChannel("network_changes", "Network Changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "VPN disconnected, network type change"
                setShowBadge(true)
            },
            NotificationChannel("shell_complete", "Shell Command Done", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Long-running ADB/shell command (>5s) completed"
                setShowBadge(false)
            },
            NotificationChannel("general", "General", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "General ACCU alerts and status messages"
            },
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
