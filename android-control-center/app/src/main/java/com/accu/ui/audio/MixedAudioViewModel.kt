package com.accu.ui.audio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.audio.MixedAudioAppState
import com.accu.audio.MixedAudioFocus
import com.accu.connection.AccuConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MixedAudioUiState(
    val apps: List<MixedAudioAppState> = emptyList(),
    val isLoading: Boolean = false,
    val filterQuery: String = "",
    val filterMode: MixedAudioFilter = MixedAudioFilter.ALL,
    val sortBy: MixedAudioSort = MixedAudioSort.NAME,
    val selectedPkgs: Set<String> = emptySet(),
    val isBatchMode: Boolean = false,
    val snackbar: String? = null,
    val pendingPkg: String? = null,
)

enum class MixedAudioFilter { ALL, MUTED, CUSTOM_FOCUS, ACTIVE }
enum class MixedAudioSort { NAME, FOCUS_STATE, MUTE_STATE }

@HiltViewModel
class MixedAudioViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MixedAudioUiState())
    val state: StateFlow<MixedAudioUiState> = _state.asStateFlow()

    init { loadApps() }

    fun loadApps() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val muteMap  = mutableMapOf<String, Boolean>()
                val focusMap = mutableMapOf<String, MixedAudioFocus>()

                // ── appops state from TARGET device ────────────────────────────
                val deniedAudio = connectionManager.exec("appops query-op PLAY_AUDIO deny 2>/dev/null").output
                deniedAudio.lines().filter { it.isNotBlank() }.forEach { muteMap[it.trim()] = true }

                val allowedAudio = connectionManager.exec("appops query-op PLAY_AUDIO allow 2>/dev/null").output
                allowedAudio.lines().filter { it.isNotBlank() }.forEach { muteMap.putIfAbsent(it.trim(), false) }

                val ignoredFocus = connectionManager.exec("appops query-op TAKE_AUDIO_FOCUS ignore 2>/dev/null").output
                ignoredFocus.lines().filter { it.isNotBlank() }.forEach { focusMap[it.trim()] = MixedAudioFocus.IGNORED }

                val deniedFocus = connectionManager.exec("appops query-op TAKE_AUDIO_FOCUS deny 2>/dev/null").output
                deniedFocus.lines().filter { it.isNotBlank() }.forEach { focusMap.putIfAbsent(it.trim(), MixedAudioFocus.DENIED) }

                val allowedFocus = connectionManager.exec("appops query-op TAKE_AUDIO_FOCUS allow 2>/dev/null").output
                allowedFocus.lines().filter { it.isNotBlank() }.forEach { focusMap.putIfAbsent(it.trim(), MixedAudioFocus.ALLOWED) }

                // ── Package list from TARGET device ────────────────────────────
                val pmOutput = connectionManager.exec("pm list packages 2>/dev/null").output
                val targetPkgs = pmOutput.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotBlank() }

                val allPkgs = (muteMap.keys + focusMap.keys + targetPkgs).toSet()
                    .filter { it.isNotBlank() && it != ctx.packageName }

                val states = allPkgs.map { pkg ->
                    MixedAudioAppState(
                        pkg     = pkg,
                        appName = pkgToLabel(pkg),
                        muted   = muteMap[pkg] ?: false,
                        focus   = focusMap[pkg] ?: MixedAudioFocus.ALLOWED,
                    )
                }.sortedBy { it.appName }

                _state.update { it.copy(apps = states, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, snackbar = "Error loading apps: ${e.message}") }
            }
        }
    }

    fun muteApp(pkg: String)   = runCmd("appops set $pkg PLAY_AUDIO deny",  "Muted $pkg")
    fun unmuteApp(pkg: String) = runCmd("appops set $pkg PLAY_AUDIO allow", "Unmuted $pkg")

    fun setFocus(pkg: String, focus: MixedAudioFocus) {
        val op = when (focus) {
            MixedAudioFocus.ALLOWED -> "allow"
            MixedAudioFocus.IGNORED -> "ignore"
            MixedAudioFocus.DENIED  -> "deny"
        }
        runCmd("appops set $pkg TAKE_AUDIO_FOCUS $op", "Focus set to ${focus.name.lowercase()} for $pkg")
    }

    fun unmuteAll() {
        val muted = _state.value.apps.filter { it.muted }.map { it.pkg }
        if (muted.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            muted.forEach { pkg ->
                try { connectionManager.exec("appops set $pkg PLAY_AUDIO allow 2>/dev/null") } catch (_: Exception) {}
            }
            _state.update { it.copy(snackbar = "Unmuted ${muted.size} apps") }
            loadApps()
        }
    }

    fun resetAllFocus() {
        val nonDefault = _state.value.apps.filter { it.focus != MixedAudioFocus.ALLOWED }.map { it.pkg }
        if (nonDefault.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            nonDefault.forEach { pkg ->
                try { connectionManager.exec("appops set $pkg TAKE_AUDIO_FOCUS allow 2>/dev/null") } catch (_: Exception) {}
            }
            _state.update { it.copy(snackbar = "Reset focus for ${nonDefault.size} apps") }
            loadApps()
        }
    }

    fun applyQuickPreset(preset: MixedAudioPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = _state.value.apps
            when (preset) {
                MixedAudioPreset.MUSIC_MODE -> {
                    val musicPkgs = setOf(
                        "com.spotify.music", "com.google.android.music",
                        "com.amazon.mp3", "com.pandora.android", "com.apple.android.music",
                        "com.deezer.android", "com.tidal.music", "com.gaana",
                    )
                    apps.filter { it.pkg !in musicPkgs }.forEach { app ->
                        try { connectionManager.exec("appops set ${app.pkg} TAKE_AUDIO_FOCUS ignore 2>/dev/null") } catch (_: Exception) {}
                    }
                    _state.update { it.copy(snackbar = "Music Mode: non-music apps set to ignore focus") }
                }
                MixedAudioPreset.PODCAST_MODE -> {
                    val podcastPkgs = setOf(
                        "com.google.android.apps.podcasts", "fm.castbox.audiobook",
                        "au.com.shiftyjelly.pocketcasts", "com.bambuna.podcastaddict", "co.appnation.podcat",
                    )
                    apps.filter { it.pkg !in podcastPkgs }.forEach { app ->
                        try { connectionManager.exec("appops set ${app.pkg} TAKE_AUDIO_FOCUS ignore 2>/dev/null") } catch (_: Exception) {}
                    }
                    _state.update { it.copy(snackbar = "Podcast Mode: non-podcast apps set to ignore focus") }
                }
                MixedAudioPreset.SILENT_MODE -> {
                    apps.forEach { app ->
                        try { connectionManager.exec("appops set ${app.pkg} PLAY_AUDIO deny 2>/dev/null") } catch (_: Exception) {}
                    }
                    _state.update { it.copy(snackbar = "Silent Mode: muted all apps") }
                }
                MixedAudioPreset.RESTORE_ALL -> {
                    apps.forEach { app ->
                        try {
                            connectionManager.exec("appops set ${app.pkg} PLAY_AUDIO allow 2>/dev/null")
                            connectionManager.exec("appops set ${app.pkg} TAKE_AUDIO_FOCUS allow 2>/dev/null")
                        } catch (_: Exception) {}
                    }
                    _state.update { it.copy(snackbar = "Restored all audio settings") }
                }
            }
            loadApps()
        }
    }

    fun toggleBatchMode() = _state.update { it.copy(isBatchMode = !it.isBatchMode, selectedPkgs = emptySet()) }

    fun toggleSelection(pkg: String) {
        val current = _state.value.selectedPkgs.toMutableSet()
        if (current.contains(pkg)) current.remove(pkg) else current.add(pkg)
        _state.update { it.copy(selectedPkgs = current) }
    }

    fun batchMute() {
        val pkgs = _state.value.selectedPkgs.toList()
        viewModelScope.launch(Dispatchers.IO) {
            pkgs.forEach { pkg ->
                try { connectionManager.exec("appops set $pkg PLAY_AUDIO deny 2>/dev/null") } catch (_: Exception) {}
            }
            _state.update { it.copy(selectedPkgs = emptySet(), isBatchMode = false, snackbar = "Muted ${pkgs.size} apps") }
            loadApps()
        }
    }

    fun batchUnmute() {
        val pkgs = _state.value.selectedPkgs.toList()
        viewModelScope.launch(Dispatchers.IO) {
            pkgs.forEach { pkg ->
                try { connectionManager.exec("appops set $pkg PLAY_AUDIO allow 2>/dev/null") } catch (_: Exception) {}
            }
            _state.update { it.copy(selectedPkgs = emptySet(), isBatchMode = false, snackbar = "Unmuted ${pkgs.size} apps") }
            loadApps()
        }
    }

    fun batchSetFocus(focus: MixedAudioFocus) {
        val pkgs = _state.value.selectedPkgs.toList()
        val op   = when (focus) {
            MixedAudioFocus.ALLOWED -> "allow"
            MixedAudioFocus.IGNORED -> "ignore"
            MixedAudioFocus.DENIED  -> "deny"
        }
        viewModelScope.launch(Dispatchers.IO) {
            pkgs.forEach { pkg ->
                try { connectionManager.exec("appops set $pkg TAKE_AUDIO_FOCUS $op 2>/dev/null") } catch (_: Exception) {}
            }
            _state.update { it.copy(
                selectedPkgs = emptySet(),
                isBatchMode  = false,
                snackbar     = "Set focus to ${focus.name} for ${pkgs.size} apps",
            ) }
            loadApps()
        }
    }

    fun onFilterChanged(q: String)       = _state.update { it.copy(filterQuery = q) }
    fun onFilterModeChanged(m: MixedAudioFilter) = _state.update { it.copy(filterMode = m) }
    fun onSortChanged(s: MixedAudioSort) = _state.update { it.copy(sortBy = s) }
    fun clearSnackbar()                  = _state.update { it.copy(snackbar = null) }

    fun filteredApps(): List<MixedAudioAppState> {
        val q    = _state.value.filterQuery.trim().lowercase()
        var list = _state.value.apps
        if (q.isNotBlank()) list = list.filter { it.appName.contains(q, true) || it.pkg.contains(q, true) }
        list = when (_state.value.filterMode) {
            MixedAudioFilter.ALL          -> list
            MixedAudioFilter.MUTED        -> list.filter { it.muted }
            MixedAudioFilter.CUSTOM_FOCUS -> list.filter { it.focus != MixedAudioFocus.ALLOWED }
            MixedAudioFilter.ACTIVE       -> list.filter { it.muted || it.focus != MixedAudioFocus.ALLOWED }
        }
        return when (_state.value.sortBy) {
            MixedAudioSort.NAME        -> list.sortedBy { it.appName }
            MixedAudioSort.FOCUS_STATE -> list.sortedBy { it.focus.ordinal }
            MixedAudioSort.MUTE_STATE  -> list.sortedByDescending { it.muted }
        }
    }

    private fun runCmd(cmd: String, successMsg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                connectionManager.exec("$cmd 2>/dev/null")
                _state.update { it.copy(snackbar = successMsg) }
                loadApps()
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = "Error: ${e.message}") }
            }
        }
    }

    /** Derive a human-readable label from a package name — no local PackageManager needed. */
    private fun pkgToLabel(pkg: String): String =
        pkg.split(".").last().replaceFirstChar { it.uppercase() }
}

enum class MixedAudioPreset { MUSIC_MODE, PODCAST_MODE, SILENT_MODE, RESTORE_ALL }
