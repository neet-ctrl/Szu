package com.accu.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class EqualizerBand(
    val band: Short,
    val centerFreqHz: Int,
    val levelMilliBel: Short
)

data class AudioDspState(
    val enabled: Boolean = false,
    val bassBoostStrength: Short = 0,
    val virtualizerStrength: Short = 0,
    val reverbPreset: Short = PresetReverb.PRESET_NONE,
    val loudnessGainMb: Int = 0,
    val equalizerBands: List<EqualizerBand> = emptyList(),
    val activePreset: Short = Equalizer.ERROR_BAD_VALUE
)

@AndroidEntryPoint
class AudioEffectService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "acc_audio_effect_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_ENABLE_DSP = "com.accu.action.ENABLE_DSP"
        const val ACTION_DISABLE_DSP = "com.accu.action.DISABLE_DSP"
        const val ACTION_SET_BASS = "com.accu.action.SET_BASS"
        const val ACTION_SET_EQ_BAND = "com.accu.action.SET_EQ_BAND"
        const val EXTRA_BASS_STRENGTH = "bass_strength"
        const val EXTRA_EQ_BAND = "eq_band"
        const val EXTRA_EQ_LEVEL = "eq_level"
    }

    inner class AudioEffectBinder : Binder() {
        fun getService(): AudioEffectService = this@AudioEffectService
    }

    private val binder = AudioEffectBinder()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _dspState = MutableStateFlow(AudioDspState())
    val dspState: StateFlow<AudioDspState> = _dspState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE_DSP -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                initEffects()
                setDspEnabled(true)
            }
            ACTION_DISABLE_DSP -> {
                setDspEnabled(false)
                releaseEffects()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SET_BASS -> {
                val strength = intent.getShortExtra(EXTRA_BASS_STRENGTH, 0)
                setBassBoostStrength(strength)
            }
            ACTION_SET_EQ_BAND -> {
                val band = intent.getShortExtra(EXTRA_EQ_BAND, 0)
                val level = intent.getShortExtra(EXTRA_EQ_LEVEL, 0)
                setEqualizerBand(band, level)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun initEffects(sessionId: Int = AudioEffect.ERROR_BAD_VALUE) {
        try {
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
            virtualizer = Virtualizer(0, sessionId).apply { enabled = true }
            reverb = PresetReverb(0, sessionId).apply { enabled = true }
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = true }
            updateEqualizerBands()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseEffects() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        reverb?.release()
        loudnessEnhancer?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
        reverb = null
        loudnessEnhancer = null
    }

    fun setDspEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        reverb?.enabled = enabled
        loudnessEnhancer?.enabled = enabled
        _dspState.value = _dspState.value.copy(enabled = enabled)
    }

    fun setBassBoostStrength(strength: Short) {
        bassBoost?.setStrength(strength)
        _dspState.value = _dspState.value.copy(bassBoostStrength = strength)
    }

    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.setStrength(strength)
        _dspState.value = _dspState.value.copy(virtualizerStrength = strength)
    }

    fun setReverbPreset(preset: Short) {
        reverb?.preset = preset
        _dspState.value = _dspState.value.copy(reverbPreset = preset)
    }

    fun setEqualizerBand(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
        updateEqualizerBands()
    }

    fun setEqualizerPreset(preset: Short) {
        equalizer?.usePreset(preset)
        updateEqualizerBands()
        _dspState.value = _dspState.value.copy(activePreset = preset)
    }

    private fun updateEqualizerBands() {
        val eq = equalizer ?: return
        val bands = (0 until eq.numberOfBands).map { i ->
            val band = i.toShort()
            EqualizerBand(
                band = band,
                centerFreqHz = eq.getCenterFreq(band) / 1000,
                levelMilliBel = eq.getBandLevel(band)
            )
        }
        _dspState.value = _dspState.value.copy(equalizerBands = bands)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Audio DSP",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "RootlessJamesDSP audio processing service"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ACC Audio DSP")
            .setContentText("Audio effects active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        releaseEffects()
        super.onDestroy()
    }
}
