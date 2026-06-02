package com.accu.services

import android.app.*
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.accu.MainActivity
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecordingService : Service() {

    companion object {
        const val CHANNEL_ID      = "call_recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START    = "com.accu.ACTION_START_RECORDING"
        const val ACTION_STOP     = "com.accu.ACTION_STOP_RECORDING"
        const val EXTRA_FORMAT    = "extra_format"
        const val EXTRA_SOURCE    = "extra_source"
        const val EXTRA_OUTPUT_URI = "extra_output_uri"

        @Volatile var isRecording = false

        fun start(
            context: Context,
            format: String = "AAC",
            source: String = "VOICE_CALL",
            outputFolderUri: String = "",
        ) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FORMAT, format)
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_OUTPUT_URI, outputFolderUri)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CallRecordingService::class.java).setAction(ACTION_STOP))
        }
    }

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var parcelFd: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val format    = intent.getStringExtra(EXTRA_FORMAT)  ?: "AAC"
                val source    = intent.getStringExtra(EXTRA_SOURCE)  ?: "VOICE_CALL"
                val outputUri = intent.getStringExtra(EXTRA_OUTPUT_URI) ?: ""
                startRecording(format, source, outputUri)
            }
            ACTION_STOP -> { stopRecording(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(format: String, source: String, outputFolderUri: String) {
        if (isRecording) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Recording call…"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Recording call…"))
        }

        isRecording = true

        recordingJob = scope.launch {
            try {
                recordAudio(format, source, outputFolderUri)
            } catch (e: Exception) {
                Timber.e(e, "Call recording error")
            } finally {
                isRecording = false
            }
        }
    }

    private suspend fun recordAudio(
        format: String,
        source: String,
        outputFolderUri: String,
    ) = withContext(Dispatchers.IO) {
        val timestamp  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext        = when (format.uppercase()) { "MP3" -> "mp3"; "WAV", "PCM" -> "wav"; "OGG", "OPUS" -> "ogg"; else -> "aac" }
        val filename   = "call_$timestamp.$ext"
        val mimeType   = when (ext) { "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"; else -> "audio/aac" }

        val outputFmt  = when (format.uppercase()) {
            "MP3"        -> MediaRecorder.OutputFormat.MPEG_4
            "WAV", "PCM" -> MediaRecorder.OutputFormat.DEFAULT
            "OGG","OPUS" -> if (Build.VERSION.SDK_INT >= 29) MediaRecorder.OutputFormat.OGG else MediaRecorder.OutputFormat.THREE_GPP
            else         -> MediaRecorder.OutputFormat.AAC_ADTS
        }
        val audioEnc   = when (format.uppercase()) {
            "MP3"        -> MediaRecorder.AudioEncoder.AAC
            "WAV", "PCM" -> MediaRecorder.AudioEncoder.DEFAULT
            "OGG","OPUS" -> if (Build.VERSION.SDK_INT >= 29) MediaRecorder.AudioEncoder.OPUS else MediaRecorder.AudioEncoder.AAC
            else         -> MediaRecorder.AudioEncoder.AAC
        }

        val audioSources = buildAudioSourceList(source)

        for (audioSrc in audioSources) {
            if (!isActive) return@withContext
            val mr = createMediaRecorder()
            try {
                mr.setAudioSource(audioSrc)
                mr.setOutputFormat(outputFmt)
                mr.setAudioEncoder(audioEnc)
                mr.setAudioSamplingRate(44100)
                mr.setAudioEncodingBitRate(128_000)

                val pfd = openOutputFile(outputFolderUri, filename, mimeType)
                if (pfd != null) {
                    parcelFd = pfd
                    mr.setOutputFile(pfd.fileDescriptor)
                } else {
                    val fallbackDir = File(getExternalFilesDir(null), "CallRecordings").also { it.mkdirs() }
                    mr.setOutputFile(File(fallbackDir, filename).absolutePath)
                }

                mr.prepare()
                mr.start()
                mediaRecorder = mr
                Timber.d("Call recording started with source=$audioSrc")
                break
            } catch (e: Exception) {
                Timber.w("Audio source $audioSrc failed: ${e.message}")
                try { mr.reset(); mr.release() } catch (_: Exception) {}
                parcelFd?.close(); parcelFd = null
            }
        }

        if (mediaRecorder == null) {
            Timber.e("All audio sources failed — recording aborted")
            return@withContext
        }

        try {
            while (isRecording && isActive) { delay(200) }
        } finally {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            try { parcelFd?.close() } catch (_: Exception) {}
            parcelFd = null
        }
    }

    private fun buildAudioSourceList(preferred: String): List<Int> {
        val preferredSrc = when (preferred.uppercase()) {
            "VOICE_CALL"          -> MediaRecorder.AudioSource.VOICE_CALL
            "VOICE_COMMUNICATION" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            "VOICE_RECOGNITION"   -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            "MIC", "MICROPHONE"   -> MediaRecorder.AudioSource.MIC
            else                  -> MediaRecorder.AudioSource.VOICE_CALL
        }
        return listOf(
            preferredSrc,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        ).distinct()
    }

    private fun openOutputFile(folderUri: String, filename: String, mime: String): ParcelFileDescriptor? {
        if (folderUri.isBlank()) return null
        return try {
            val treeUri = android.net.Uri.parse(folderUri)
            val docDir  = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return null
            val docFile = docDir.createFile(mime, filename) ?: return null
            contentResolver.openFileDescriptor(docFile.uri, "w")
        } catch (e: Exception) {
            Timber.w("SAF output open failed: ${e.message}")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(applicationContext)
        else
            MediaRecorder()

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Call Recording", NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Active call recording"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, CallRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recording Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        scope.cancel()
    }
}
