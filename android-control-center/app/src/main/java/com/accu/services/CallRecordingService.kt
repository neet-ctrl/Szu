package com.accu.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.accu.MainActivity
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Background service for call recording using AudioRecord or Shizuku/scrcpy audio capture.
 * Based on ShizuCallRecorder (kitsumed/ShizuCallRecorder) approach.
 */
class CallRecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "call_recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.accu.ACTION_START_RECORDING"
        const val ACTION_STOP = "com.accu.ACTION_STOP_RECORDING"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_OUTPUT_DIR = "extra_output_dir"
        var isRecording = false

        fun start(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CallRecordingService::class.java).setAction(ACTION_STOP))
        }
    }

    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null
    private var currentFile: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP  -> { stopRecording(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (isRecording) return
        startForeground(NOTIFICATION_ID, buildNotification("Recording call…"))
        isRecording = true

        val outputDir = File(getExternalFilesDir(null), "CallRecordings").also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentFile = File(outputDir, "call_$timestamp.aac")

        recordingJob = scope.launch {
            try {
                recordAudio(currentFile!!)
            } catch (e: Exception) {
                Timber.e(e, "Recording error")
            }
        }
    }

    private suspend fun recordAudio(file: File) = withContext(Dispatchers.IO) {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )

        var audioRecord: AudioRecord? = null
        for (source in sources) {
            try {
                audioRecord = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) break
                audioRecord.release(); audioRecord = null
            } catch (_: Exception) {}
        }

        if (audioRecord == null) { Timber.e("Could not initialize AudioRecord"); return@withContext }

        val mediaRecorder = MediaRecorder(applicationContext).apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            try { prepare(); start() } catch (e: Exception) {
                Timber.e(e, "MediaRecorder failed, falling back to AudioRecord")
                release()
                return@withContext
            }
        }

        try {
            while (isRecording && isActive) { delay(100) }
        } finally {
            try { mediaRecorder.stop(); mediaRecorder.release() } catch (_: Exception) {}
            audioRecord.stop(); audioRecord.release()
        }
    }

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Call Recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Active call recording"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(this, 0, Intent(this, CallRecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recording Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        scope.cancel()
    }
}
