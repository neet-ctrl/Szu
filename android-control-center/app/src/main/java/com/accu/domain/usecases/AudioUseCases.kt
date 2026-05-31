package com.accu.domain.usecases

import android.content.Context
import android.content.Intent
import com.accu.services.AudioEffectService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ToggleAudioDspUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(enable: Boolean) {
        val intent = Intent(
            context,
            AudioEffectService::class.java
        ).apply {
            action = if (enable) AudioEffectService.ACTION_ENABLE_DSP
            else AudioEffectService.ACTION_DISABLE_DSP
        }
        if (enable) context.startForegroundService(intent)
        else context.startService(intent)
    }
}

class SetBassBoostUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(strength: Short) {
        val intent = Intent(context, AudioEffectService::class.java).apply {
            action = AudioEffectService.ACTION_SET_BASS
            putExtra(AudioEffectService.EXTRA_BASS_STRENGTH, strength)
        }
        context.startService(intent)
    }
}

class SetEqualizerBandUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(band: Short, levelMilliBel: Short) {
        val intent = Intent(context, AudioEffectService::class.java).apply {
            action = AudioEffectService.ACTION_SET_EQ_BAND
            putExtra(AudioEffectService.EXTRA_EQ_BAND, band)
            putExtra(AudioEffectService.EXTRA_EQ_LEVEL, levelMilliBel)
        }
        context.startService(intent)
    }
}
