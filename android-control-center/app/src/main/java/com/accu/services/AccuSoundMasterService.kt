package com.accu.services

import android.app.Service
import android.content.ContentObserver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.accu.audio.AccuPlayBackThread
import com.accu.audio.SoundMasterEntry
import java.util.Timer
import kotlin.concurrent.timerTask

class AccuSoundMasterService : Service() {

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var mediaProjection: MediaProjection? = null
    private lateinit var volumeObserver: ContentObserver
    val packageThreads = hashMapOf<String, AccuPlayBackThread>()
    private var latencyTimer = Timer()

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        startLatencyTimer()
        initVolumeObserver()
        initAudioDevices()

        getAudioDevices = {
            var dev = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type !in arrayOf(1, 7, 18, 25) }
            if (dev.any { it.type in 3..4 }) dev = dev.filter { it.type != 2 }
            listOf(null) + dev
        }

        setVolumeOf = { pkg, outputId, vol ->
            packageThreads[pkg]?.setVolume(outputId, vol)
        }
        getVolumeOf = { pkg, outputId ->
            packageThreads[pkg]?.getVolume(outputId) ?: 100f
        }
        setBalanceOf = { pkg, outputId, val_ ->
            packageThreads[pkg]?.setBalance(outputId, val_)
        }
        getBalanceOf = { pkg, outputId ->
            packageThreads[pkg]?.getBalance(outputId) ?: 0f
        }
        setBandOf = { pkg, outputId, band, val_ ->
            packageThreads[pkg]?.setBand(outputId, band, val_)
        }
        getBandOf = { pkg, outputId, band ->
            packageThreads[pkg]?.getBand(outputId, band) ?: 50f
        }
        isAttachable = { pkg, outputId ->
            !(packageThreads.containsKey(pkg) && packageThreads[pkg]?.hasOutput(outputId) == true)
        }
        onDynamicAttach = { entry, device ->
            if (!packageThreads.containsKey(entry.pkg)) {
                val thread = AccuPlayBackThread(applicationContext, entry.pkg, mediaProjection!!)
                packageThreads[entry.pkg] = thread
                thread.start()
            }
            packageThreads[entry.pkg]?.createOutput(
                device = device,
                outputKey = entry.outputDeviceId,
                startVolume = entry.volume,
                balance = entry.balance,
                eqLow = entry.eqLow,
                eqMid = entry.eqMid,
                eqHigh = entry.eqHigh,
            )
        }
        onDynamicDetach = { pkg, outputId ->
            val thread = packageThreads[pkg] ?: return@run
            thread.deleteOutput(outputId, false)
            if (thread.mPlayers.isEmpty()) {
                thread.interrupt()
                packageThreads.remove(pkg)
            }
        }
        getLatencyMap = {
            packageThreads.map { it.key to it.value.getLatency() }.toMap()
        }
        getRmsMap = {
            packageThreads.map { it.key to it.value.calculateRMS() }.toMap()
        }
        getActivePackages = {
            packageThreads.keys.toList()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projData = projectionData
        if (projData != null) {
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = pm.getMediaProjection(android.app.Activity.RESULT_OK, projData)
            running = true
            pendingEntries.forEach { entry ->
                onDynamicAttach(entry, getAudioDevices().find { it?.id == entry.outputDeviceId })
            }
            pendingEntries.clear()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        latencyTimer.cancel()
        contentResolver.unregisterContentObserver(volumeObserver)
        packageThreads.forEach { it.value.interrupt() }
        packageThreads.clear()
        mediaProjection?.stop()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, "accu_notifications")
            .setContentTitle("Sound Master Active")
            .setContentText("Per-app audio control running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
        startForeground(NOTI_ID, notification)
    }

    private fun startLatencyTimer() {
        latencyTimer = Timer()
        latencyTimer.schedule(timerTask {
            val count = packageThreads.size
            val avgLatency = if (count > 0)
                packageThreads.values.map { it.getLatency() }.average().toInt() else 0
            val nm = androidx.core.app.NotificationManagerCompat.from(applicationContext)
            val notif = NotificationCompat.Builder(applicationContext, "accu_notifications")
                .setContentTitle("Sound Master · $count app${if (count != 1) "s" else ""} controlled")
                .setContentText("Avg latency: ${avgLatency}ms")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .build()
            try { nm.notify(NOTI_ID, notif) } catch (_: Exception) {}
        }, UPDATE_INTERVAL, UPDATE_INTERVAL)
    }

    private fun initVolumeObserver() {
        volumeObserver = object : ContentObserver(Handler(mainLooper)) {
            var prevVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            override fun onChange(selfChange: Boolean) {
                val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (newVol != prevVol) { prevVol = newVol; onVolumeChanged() }
            }
        }
        contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI, true, volumeObserver)
    }

    private fun initAudioDevices() {
        getAudioDevices = {
            var dev = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type !in arrayOf(1, 7, 18, 25) }
            if (dev.any { it.type in 3..4 }) dev = dev.filter { it.type != 2 }
            listOf(null) + dev
        }
    }

    companion object {
        const val NOTI_ID = 55
        const val UPDATE_INTERVAL = 30_000L

        var running = false
        var projectionData: Intent? = null
        var pendingEntries = mutableListOf<SoundMasterEntry>()

        var getAudioDevices: () -> List<AudioDeviceInfo?> = { listOf() }
        var setVolumeOf: (pkg: String, outputId: Int, vol: Float) -> Unit = { _, _, _ -> }
        var getVolumeOf: (pkg: String, outputId: Int) -> Float = { _, _ -> 100f }
        var setBalanceOf: (pkg: String, outputId: Int, value: Float) -> Unit = { _, _, _ -> }
        var getBalanceOf: (pkg: String, outputId: Int) -> Float = { _, _ -> 0f }
        var setBandOf: (pkg: String, outputId: Int, band: Int, value: Float) -> Unit = { _, _, _, _ -> }
        var getBandOf: (pkg: String, outputId: Int, band: Int) -> Float = { _, _, _ -> 50f }
        var isAttachable: (pkg: String, outputId: Int) -> Boolean = { _, _ -> true }
        var onDynamicAttach: (SoundMasterEntry, AudioDeviceInfo?) -> Unit = { _, _ -> }
        var onDynamicDetach: (pkg: String, outputId: Int) -> Unit = { _, _ -> }
        var getLatencyMap: () -> Map<String, Float> = { emptyMap() }
        var getRmsMap: () -> Map<String, Float> = { emptyMap() }
        var getActivePackages: () -> List<String> = { emptyList() }
        var onVolumeChanged: () -> Unit = {}
    }
}
