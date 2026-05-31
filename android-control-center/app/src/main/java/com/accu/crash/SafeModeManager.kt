package com.accu.crash

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe Mode Manager — disables experimental modules when the app has crashed
 * repeatedly or the user manually enables safe mode from the crash screen.
 *
 * Uses SharedPreferences (not Room) so it's readable before Hilt starts.
 */
@Singleton
class SafeModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("accu_safe_mode", Context.MODE_PRIVATE)

    private val _isSafeModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isSafeModeEnabled: StateFlow<Boolean> = _isSafeModeEnabled.asStateFlow()

    val disabledModules: List<String>
        get() = if (_isSafeModeEnabled.value) SAFE_MODE_DISABLED_MODULES else emptyList()

    fun enableSafeMode() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        _isSafeModeEnabled.value = true
    }

    fun disableSafeMode() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        _isSafeModeEnabled.value = false
        prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()
    }

    fun recordCrash() {
        val count = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_CRASH_COUNT, count).apply()
        if (count >= AUTO_ENABLE_THRESHOLD) enableSafeMode()
    }

    fun resetCrashCount() = prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()

    fun getCrashCount(): Int = prefs.getInt(KEY_CRASH_COUNT, 0)

    fun shouldSuggestSafeMode(): Boolean = getCrashCount() >= SUGGEST_THRESHOLD

    companion object {
        private const val KEY_ENABLED     = "safe_mode_on"
        private const val KEY_CRASH_COUNT = "crash_count_since_reset"
        private const val AUTO_ENABLE_THRESHOLD = 5
        private const val SUGGEST_THRESHOLD = 3

        val SAFE_MODE_DISABLED_MODULES = listOf(
            "experimental",
            "automation",
            "keymapper",
            "overlay",
            "startup_services",
            "shell_qs_tiles",
            "liveprog",
        )
    }
}
