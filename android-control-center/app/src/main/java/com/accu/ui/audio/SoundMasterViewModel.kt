package com.accu.ui.audio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.projection.MediaProjectionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.audio.DEFAULT_PRESETS
import com.accu.audio.SoundMasterEntry
import com.accu.audio.SoundMasterPreset
import com.accu.audio.displayName
import com.accu.connection.AccuConnectionManager
import com.accu.services.AccuSoundMasterService
import com.accu.services.SoundMasterOverlayService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SoundMasterUiState(
    val isServiceRunning: Boolean = false,
    val entries: List<SoundMasterEntry> = emptyList(),
    val availableApps: List<Pair<String, String>> = emptyList(),
    val audioDevices: List<AudioDeviceInfo?> = listOf(null),
    val latencyMap: Map<String, Float> = emptyMap(),
    val rmsMap: Map<String, Float> = emptyMap(),
    val presets: List<SoundMasterPreset> = DEFAULT_PRESETS,
    val filterQuery: String = "",
    val sortBy: SoundMasterSort = SoundMasterSort.NAME,
    val showNotification: Boolean = false,
    val showOnVolumeChange: Boolean = false,
    val autoHide: Boolean = true,
    val isLoadingApps: Boolean = false,
    val snackbar: String? = null,
    val requiresMediaProjection: Boolean = false,
)

enum class SoundMasterSort { NAME, VOLUME, PACKAGE }

@HiltViewModel
class SoundMasterViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SoundMasterUiState())
    val state: StateFlow<SoundMasterUiState> = _state.asStateFlow()

    private val prefs by lazy { ctx.getSharedPreferences("soundmaster_accu", Context.MODE_PRIVATE) }
    private val entrilesFile get() = File(ctx.filesDir, "accu_soundmaster_entries.txt")

    init {
        loadPersistedEntries()
        loadSettings()
        refreshDevices()
        startPolling()
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                showNotification = prefs.getBoolean("show_notification", false),
                showOnVolumeChange = prefs.getBoolean("show_on_volume_change", false),
                autoHide = prefs.getBoolean("auto_hide", true),
            )
        }
    }

    private fun loadPersistedEntries() {
        try {
            val entries = if (entrilesFile.exists()) {
                entrilesFile.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size >= 7) SoundMasterEntry(
                        pkg = parts[0],
                        outputDeviceId = parts[1].toIntOrNull() ?: -1,
                        volume = parts[2].toFloatOrNull() ?: 100f,
                        balance = parts[3].toFloatOrNull() ?: 0f,
                        eqLow = parts[4].toFloatOrNull() ?: 50f,
                        eqMid = parts[5].toFloatOrNull() ?: 50f,
                        eqHigh = parts[6].toFloatOrNull() ?: 50f,
                        locked = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: false,
                    ) else null
                }
            } else emptyList()
            _state.update { it.copy(entries = entries) }
        } catch (_: Exception) {}
    }

    private fun persistEntries(entries: List<SoundMasterEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                entrilesFile.parentFile?.mkdirs()
                entrilesFile.writeText(entries.joinToString("\n") { e ->
                    "${e.pkg}|${e.outputDeviceId}|${e.volume}|${e.balance}|${e.eqLow}|${e.eqMid}|${e.eqHigh}|${e.locked}"
                })
            } catch (_: Exception) {}
        }
    }

    fun loadInstalledApps() {
        if (_state.value.isLoadingApps) return
        _state.update { it.copy(isLoadingApps = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = ctx.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.packageName != ctx.packageName }
                    .map { info ->
                        val name = pm.getApplicationLabel(info).toString()
                        Pair(info.packageName, name)
                    }
                    .sortedBy { it.second }
                _state.update { it.copy(availableApps = apps, isLoadingApps = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingApps = false, snackbar = "Error loading apps: ${e.message}") }
            }
        }
    }

    fun refreshDevices() {
        val devices = AccuSoundMasterService.getAudioDevices()
        _state.update { it.copy(audioDevices = if (devices.isEmpty()) listOf(null) else devices) }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(2000)
                _state.update { s ->
                    s.copy(
                        isServiceRunning = AccuSoundMasterService.running,
                        latencyMap = AccuSoundMasterService.getLatencyMap(),
                        rmsMap = AccuSoundMasterService.getRmsMap(),
                    )
                }
            }
        }
    }

    fun addEntry(pkg: String, appName: String, deviceId: Int = -1) {
        val current = _state.value.entries.toMutableList()
        if (current.any { it.pkg == pkg && it.outputDeviceId == deviceId }) {
            _state.update { it.copy(snackbar = "This app + output combination already exists") }
            return
        }
        val entry = SoundMasterEntry(pkg = pkg, outputDeviceId = deviceId)
        current.add(entry)
        _state.update { it.copy(entries = current) }
        persistEntries(current)
        if (AccuSoundMasterService.running) {
            val device = _state.value.audioDevices.find { it?.id == deviceId }
            AccuSoundMasterService.onDynamicAttach(entry, device)
        }
    }

    fun removeEntry(entry: SoundMasterEntry) {
        val current = _state.value.entries.toMutableList()
        current.remove(entry)
        _state.update { it.copy(entries = current) }
        persistEntries(current)
        if (AccuSoundMasterService.running) {
            AccuSoundMasterService.onDynamicDetach(entry.pkg, entry.outputDeviceId)
        }
        restoreAppAudio(entry.pkg)
    }

    fun updateVolume(entry: SoundMasterEntry, vol: Float) {
        updateEntry(entry) { it.copy(volume = vol) }
        if (AccuSoundMasterService.running)
            AccuSoundMasterService.setVolumeOf(entry.pkg, entry.outputDeviceId, vol)
    }

    fun updateBalance(entry: SoundMasterEntry, balance: Float) {
        updateEntry(entry) { it.copy(balance = balance) }
        if (AccuSoundMasterService.running)
            AccuSoundMasterService.setBalanceOf(entry.pkg, entry.outputDeviceId, balance)
    }

    fun updateEqBand(entry: SoundMasterEntry, band: Int, value: Float) {
        updateEntry(entry) { e ->
            when (band) {
                0 -> e.copy(eqLow = value)
                1 -> e.copy(eqMid = value)
                else -> e.copy(eqHigh = value)
            }
        }
        if (AccuSoundMasterService.running)
            AccuSoundMasterService.setBandOf(entry.pkg, entry.outputDeviceId, band, value)
    }

    fun applyPreset(entry: SoundMasterEntry, preset: SoundMasterPreset) {
        updateEntry(entry) { it.copy(volume = preset.volume, balance = preset.balance, eqLow = preset.eqLow, eqMid = preset.eqMid, eqHigh = preset.eqHigh) }
        if (AccuSoundMasterService.running) {
            AccuSoundMasterService.setVolumeOf(entry.pkg, entry.outputDeviceId, preset.volume)
            AccuSoundMasterService.setBalanceOf(entry.pkg, entry.outputDeviceId, preset.balance)
            AccuSoundMasterService.setBandOf(entry.pkg, entry.outputDeviceId, 0, preset.eqLow)
            AccuSoundMasterService.setBandOf(entry.pkg, entry.outputDeviceId, 1, preset.eqMid)
            AccuSoundMasterService.setBandOf(entry.pkg, entry.outputDeviceId, 2, preset.eqHigh)
        }
    }

    fun resetEntry(entry: SoundMasterEntry) = applyPreset(entry, DEFAULT_PRESETS[0])

    fun toggleLocked(entry: SoundMasterEntry) = updateEntry(entry) { it.copy(locked = !it.locked) }

    fun muteAll() {
        _state.value.entries.forEach { updateVolume(it, 0f) }
    }

    fun resetAllEq() {
        _state.value.entries.forEach { e ->
            updateEqBand(e, 0, 50f); updateEqBand(e, 1, 50f); updateEqBand(e, 2, 50f)
            updateBalance(e, 0f)
        }
    }

    fun onFilterChanged(q: String) = _state.update { it.copy(filterQuery = q) }
    fun onSortChanged(s: SoundMasterSort) = _state.update { it.copy(sortBy = s) }

    fun saveSettings(showNotification: Boolean, showOnVolumeChange: Boolean, autoHide: Boolean) {
        prefs.edit()
            .putBoolean("show_notification", showNotification)
            .putBoolean("show_on_volume_change", showOnVolumeChange)
            .putBoolean("auto_hide", autoHide)
            .apply()
        _state.update { it.copy(showNotification = showNotification, showOnVolumeChange = showOnVolumeChange, autoHide = autoHide) }
    }

    fun grantPermissionsAndStart(activity: Activity, onNeedProjection: (MediaProjectionManager) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pkg = ctx.packageName
                connectionManager.exec("pm grant $pkg android.permission.RECORD_AUDIO 2>/dev/null")
                connectionManager.exec("appops set $pkg PROJECT_MEDIA allow 2>/dev/null")
                val hasPerm = ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    viewModelScope.launch {
                        val pm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        onNeedProjection(pm)
                    }
                } else {
                    _state.update { it.copy(snackbar = "Failed to grant RECORD_AUDIO — check ADB connection") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = "Error: ${e.message}") }
            }
        }
    }

    fun onProjectionGranted(data: Intent, context: Context) {
        AccuSoundMasterService.projectionData = data
        AccuSoundMasterService.pendingEntries.clear()
        AccuSoundMasterService.pendingEntries.addAll(_state.value.entries)
        _state.value.entries.forEach { entry ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    connectionManager.exec("appops set ${entry.pkg} PLAY_AUDIO deny 2>/dev/null")
                } catch (_: Exception) {}
            }
        }
        context.startService(Intent(context, AccuSoundMasterService::class.java))
        if (_state.value.showOnVolumeChange) {
            try { SoundMasterOverlayService.start(context) } catch (_: Exception) {}
        }
        _state.update { it.copy(isServiceRunning = true) }
    }

    fun stopService(context: Context) {
        context.stopService(Intent(context, AccuSoundMasterService::class.java))
        try { SoundMasterOverlayService.stop(context) } catch (_: Exception) {}
        _state.value.entries.forEach { entry -> restoreAppAudio(entry.pkg) }
        _state.update { it.copy(isServiceRunning = false) }
    }

    private fun restoreAppAudio(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { connectionManager.exec("appops set $pkg PLAY_AUDIO allow 2>/dev/null") } catch (_: Exception) {}
        }
    }

    private fun updateEntry(entry: SoundMasterEntry, transform: (SoundMasterEntry) -> SoundMasterEntry) {
        val current = _state.value.entries.toMutableList()
        val idx = current.indexOfFirst { it.pkg == entry.pkg && it.outputDeviceId == entry.outputDeviceId }
        if (idx >= 0) {
            current[idx] = transform(current[idx])
            _state.update { it.copy(entries = current) }
            persistEntries(current)
        }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbar = null) }

    fun filteredEntries(): List<SoundMasterEntry> {
        val q = _state.value.filterQuery.trim().lowercase()
        val list = if (q.isBlank()) _state.value.entries
        else _state.value.entries.filter { it.pkg.contains(q, true) }
        return when (_state.value.sortBy) {
            SoundMasterSort.NAME    -> list.sortedBy { it.pkg.split(".").last() }
            SoundMasterSort.VOLUME  -> list.sortedByDescending { it.volume }
            SoundMasterSort.PACKAGE -> list.sortedBy { it.pkg }
        }
    }
}
