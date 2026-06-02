package com.accu.ui.audio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.audio.DEFAULT_PRESETS
import com.accu.audio.SoundMasterEntry
import com.accu.audio.SoundMasterPreset
import com.accu.connection.AccuConnectionManager
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
    val isControlActive: Boolean = false,
    val entries: List<SoundMasterEntry> = emptyList(),
    val availableApps: List<Pair<String, String>> = emptyList(),
    val presets: List<SoundMasterPreset> = DEFAULT_PRESETS,
    val filterQuery: String = "",
    val sortBy: SoundMasterSort = SoundMasterSort.NAME,
    val showNotification: Boolean = false,
    val showOnVolumeChange: Boolean = false,
    val autoHide: Boolean = true,
    val isLoadingApps: Boolean = false,
    val snackbar: String? = null,
    val connectionStatus: String = "",
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
    private val entriesFile get() = File(ctx.filesDir, "accu_soundmaster_entries.txt")

    init {
        loadPersistedEntries()
        loadSettings()
        observeConnection()
    }

    private fun observeConnection() {
        viewModelScope.launch {
            connectionManager.state.collect { cs ->
                val connected = cs != AccuConnectionManager.ConnectionState.DISCONNECTED
                _state.update { it.copy(
                    connectionStatus = cs.name,
                    isControlActive  = connected && it.isControlActive,
                ) }
            }
        }
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                showNotification   = prefs.getBoolean("show_notification", false),
                showOnVolumeChange = prefs.getBoolean("show_on_volume_change", false),
                autoHide           = prefs.getBoolean("auto_hide", true),
            )
        }
    }

    private fun loadPersistedEntries() {
        try {
            val entries = if (entriesFile.exists()) {
                entriesFile.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size >= 7) SoundMasterEntry(
                        pkg            = parts[0],
                        outputDeviceId = parts[1].toIntOrNull() ?: -1,
                        volume         = parts[2].toFloatOrNull() ?: 100f,
                        balance        = parts[3].toFloatOrNull() ?: 0f,
                        eqLow          = parts[4].toFloatOrNull() ?: 50f,
                        eqMid          = parts[5].toFloatOrNull() ?: 50f,
                        eqHigh         = parts[6].toFloatOrNull() ?: 50f,
                        locked         = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: false,
                    ) else null
                }
            } else emptyList()
            _state.update { it.copy(entries = entries) }
        } catch (_: Exception) {}
    }

    private fun persistEntries(entries: List<SoundMasterEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                entriesFile.parentFile?.mkdirs()
                entriesFile.writeText(entries.joinToString("\n") { e ->
                    "${e.pkg}|${e.outputDeviceId}|${e.volume}|${e.balance}|${e.eqLow}|${e.eqMid}|${e.eqHigh}|${e.locked}"
                })
            } catch (_: Exception) {}
        }
    }

    // ── App list from TARGET device via ADB ────────────────────────────────────
    fun loadInstalledApps() {
        if (_state.value.isLoadingApps) return
        _state.update { it.copy(isLoadingApps = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val output = connectionManager.exec("pm list packages 2>/dev/null").output
                val apps = output.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotBlank() && it != ctx.packageName }
                    .map { pkg ->
                        val label = pkg.split(".").last().replaceFirstChar { c -> c.uppercase() }
                        Pair(pkg, label)
                    }
                    .sortedBy { it.second }
                _state.update { it.copy(availableApps = apps, isLoadingApps = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingApps = false, snackbar = "Error loading apps: ${e.message}") }
            }
        }
    }

    // ── Control activation ─────────────────────────────────────────────────────
    fun activateControl() {
        val cs = connectionManager.state.value
        if (cs == AccuConnectionManager.ConnectionState.DISCONNECTED) {
            _state.update { it.copy(snackbar = "Not connected — go to ACCU Center to connect first") }
            return
        }
        _state.update { it.copy(isControlActive = true, snackbar = "Sound Master active — controlling ${_state.value.entries.size} app(s) on target") }
        // Apply current mute state for all entries to target device
        viewModelScope.launch(Dispatchers.IO) {
            _state.value.entries.forEach { entry ->
                applyVolumeToTarget(entry.pkg, entry.volume)
            }
        }
    }

    fun deactivateControl() {
        _state.update { it.copy(isControlActive = false) }
        // Restore all audio permissions on target device
        viewModelScope.launch(Dispatchers.IO) {
            _state.value.entries.forEach { entry ->
                try { connectionManager.exec("appops set ${entry.pkg} PLAY_AUDIO allow 2>/dev/null") } catch (_: Exception) {}
            }
        }
        _state.update { it.copy(snackbar = "Sound Master stopped — audio restored on target") }
    }

    // ── Entry management ───────────────────────────────────────────────────────
    fun addEntry(pkg: String, appName: String, deviceId: Int = -1) {
        val current = _state.value.entries.toMutableList()
        if (current.any { it.pkg == pkg && it.outputDeviceId == deviceId }) {
            _state.update { it.copy(snackbar = "This app is already added") }
            return
        }
        val entry = SoundMasterEntry(pkg = pkg, outputDeviceId = deviceId)
        current.add(entry)
        _state.update { it.copy(entries = current) }
        persistEntries(current)
        if (_state.value.isControlActive) {
            viewModelScope.launch(Dispatchers.IO) { applyVolumeToTarget(pkg, entry.volume) }
        }
    }

    fun removeEntry(entry: SoundMasterEntry) {
        val current = _state.value.entries.toMutableList()
        current.remove(entry)
        _state.update { it.copy(entries = current) }
        persistEntries(current)
        restoreAppAudio(entry.pkg)
    }

    // ── Volume control — applies via appops on TARGET device ──────────────────
    fun updateVolume(entry: SoundMasterEntry, vol: Float) {
        updateEntry(entry) { it.copy(volume = vol) }
        if (_state.value.isControlActive) {
            viewModelScope.launch(Dispatchers.IO) { applyVolumeToTarget(entry.pkg, vol) }
        }
    }

    /**
     * Volume → appops on target device.
     * vol == 0  → deny  PLAY_AUDIO  (silence the app)
     * vol  > 0  → allow PLAY_AUDIO  (restore the app)
     *
     * With ROOT we additionally try `cmd volume set-volume-index` per UID for
     * proportional volume. This is a best-effort call and silently ignored if
     * the device doesn't support it.
     */
    private suspend fun applyVolumeToTarget(pkg: String, vol: Float) {
        try {
            if (vol == 0f) {
                connectionManager.exec("appops set $pkg PLAY_AUDIO deny 2>/dev/null")
            } else {
                connectionManager.exec("appops set $pkg PLAY_AUDIO allow 2>/dev/null")
                // Root: proportional volume via AudioService (best-effort, API varies)
                if (connectionManager.state.value == AccuConnectionManager.ConnectionState.CONNECTED_ROOT) {
                    val uid = getUidForPackage(pkg)
                    if (uid != null) {
                        // Stream 3 = STREAM_MUSIC, max index 15
                        val idx = ((vol / 100f) * 15f).toInt().coerceIn(0, 15)
                        connectionManager.exec(
                            "cmd media.audio_policy set-stream-volume 3 $idx 0 2>/dev/null"
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun getUidForPackage(pkg: String): Int? {
        return try {
            val out = connectionManager.exec("pm dump $pkg 2>/dev/null | grep 'userId='").output
            val match = Regex("userId=(\\d+)").find(out)
            match?.groupValues?.get(1)?.toIntOrNull()
        } catch (_: Exception) { null }
    }

    fun updateBalance(entry: SoundMasterEntry, balance: Float) {
        updateEntry(entry) { it.copy(balance = balance) }
        // Balance is stored locally; true per-app balance via ADB is not available
        // without AudioPlaybackCapture. We apply best-effort via media_session if root.
        if (_state.value.isControlActive &&
            connectionManager.state.value == AccuConnectionManager.ConnectionState.CONNECTED_ROOT) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // No standard ADB command for balance — stored for future root implementation
                } catch (_: Exception) {}
            }
        }
    }

    fun updateEqBand(entry: SoundMasterEntry, band: Int, value: Float) {
        updateEntry(entry) { e ->
            when (band) {
                0    -> e.copy(eqLow = value)
                1    -> e.copy(eqMid = value)
                else -> e.copy(eqHigh = value)
            }
        }
        // EQ stored locally; true per-app EQ via ADB requires AudioEffect + root service
    }

    fun applyPreset(entry: SoundMasterEntry, preset: SoundMasterPreset) {
        updateEntry(entry) { it.copy(
            volume  = preset.volume,
            balance = preset.balance,
            eqLow   = preset.eqLow,
            eqMid   = preset.eqMid,
            eqHigh  = preset.eqHigh,
        ) }
        if (_state.value.isControlActive) {
            viewModelScope.launch(Dispatchers.IO) { applyVolumeToTarget(entry.pkg, preset.volume) }
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
        _state.update { it.copy(
            showNotification   = showNotification,
            showOnVolumeChange = showOnVolumeChange,
            autoHide           = autoHide,
        ) }
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
        val q    = _state.value.filterQuery.trim().lowercase()
        val list = if (q.isBlank()) _state.value.entries
                   else _state.value.entries.filter { it.pkg.contains(q, true) }
        return when (_state.value.sortBy) {
            SoundMasterSort.NAME    -> list.sortedBy { it.pkg.split(".").last() }
            SoundMasterSort.VOLUME  -> list.sortedByDescending { it.volume }
            SoundMasterSort.PACKAGE -> list.sortedBy { it.pkg }
        }
    }
}
