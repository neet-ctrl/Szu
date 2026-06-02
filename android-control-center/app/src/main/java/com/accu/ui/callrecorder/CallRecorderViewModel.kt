package com.accu.ui.callrecorder

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.db.dao.CallRecordingDao
import com.accu.data.db.entities.CallRecordingEntity
import com.accu.services.CallRecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val PREFS_NAME          = "call_recorder_prefs"
private const val KEY_OUTPUT_FOLDER_URI  = "output_folder_uri"
private const val KEY_OUTPUT_FOLDER_NAME = "output_folder_name"
private const val KEY_AUDIO_SOURCE    = "audio_source"
private const val KEY_FORMAT          = "format"

data class CallRecorderUiState(
    val recordings: List<CallRecordingEntity> = emptyList(),
    val isRecordingEnabled: Boolean = false,
    val isCurrentlyRecording: Boolean = false,
    val recordingFormat: String = "AAC",
    val audioSource: String = "VOICE_CALL",
    val outputFolderUri: String = "",
    val outputFolderName: String = "Not set — tap folder icon",
    val totalSizeBytes: Long = 0L,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val snackbarMessage: String? = null,
    val showSearch: Boolean = false,
    val showSettingsPanel: Boolean = false,
)

@HiltViewModel
class CallRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callRecordingDao: CallRecordingDao,
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(CallRecorderUiState())
    val state: StateFlow<CallRecorderUiState> = _state.asStateFlow()

    init {
        loadPersistedSettings()
        observeRecordings()
        loadStats()
    }

    private fun loadPersistedSettings() {
        val uri    = prefs.getString(KEY_OUTPUT_FOLDER_URI,  "") ?: ""
        val name   = prefs.getString(KEY_OUTPUT_FOLDER_NAME, "Not set — tap folder icon") ?: "Not set — tap folder icon"
        val source = prefs.getString(KEY_AUDIO_SOURCE, "VOICE_CALL") ?: "VOICE_CALL"
        val format = prefs.getString(KEY_FORMAT, "AAC") ?: "AAC"
        _state.update { it.copy(
            outputFolderUri  = uri,
            outputFolderName = name,
            audioSource      = source,
            recordingFormat  = format,
        ) }
    }

    private fun observeRecordings() {
        viewModelScope.launch {
            callRecordingDao.observeAll().collect { recs ->
                _state.update { it.copy(recordings = recs, isLoading = false) }
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val total = callRecordingDao.totalSizeBytes() ?: 0L
            _state.update { it.copy(totalSizeBytes = total) }
        }
    }

    fun setOutputFolderUri(uri: Uri, displayName: String) {
        val uriStr = uri.toString()
        prefs.edit()
            .putString(KEY_OUTPUT_FOLDER_URI,  uriStr)
            .putString(KEY_OUTPUT_FOLDER_NAME, displayName)
            .apply()
        _state.update { it.copy(outputFolderUri = uriStr, outputFolderName = displayName) }
        _state.update { it.copy(snackbarMessage = "Save folder: $displayName") }
    }

    fun toggleRecording() {
        val enabled = !_state.value.isRecordingEnabled
        _state.update { it.copy(isRecordingEnabled = enabled) }
        try {
            if (enabled) {
                CallRecordingService.start(
                    context,
                    format          = _state.value.recordingFormat,
                    source          = _state.value.audioSource,
                    outputFolderUri = _state.value.outputFolderUri,
                )
            } else {
                CallRecordingService.stop(context)
            }
            _state.update { it.copy(
                snackbarMessage = if (enabled) "Call recording enabled" else "Call recording disabled"
            ) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle CallRecordingService")
            _state.update { it.copy(
                isRecordingEnabled = !enabled,
                snackbarMessage    = "Service error: ${e.message}",
            ) }
        }
    }

    fun setFormat(format: String) {
        prefs.edit().putString(KEY_FORMAT, format).apply()
        _state.update { it.copy(recordingFormat = format) }
    }

    fun setAudioSource(source: String) {
        prefs.edit().putString(KEY_AUDIO_SOURCE, source).apply()
        _state.update { it.copy(audioSource = source) }
    }

    fun playRecording(recording: CallRecordingEntity) {
        try {
            val file = File(recording.filePath)
            if (!file.exists()) {
                _state.update { it.copy(snackbarMessage = "File not found: ${recording.filePath}") }
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e)
            _state.update { it.copy(snackbarMessage = "Cannot play: ${e.message}") }
        }
    }

    fun toggleStar(recording: CallRecordingEntity) {
        viewModelScope.launch {
            callRecordingDao.update(recording.copy(isStarred = !recording.isStarred))
        }
    }

    fun deleteRecording(recording: CallRecordingEntity) {
        viewModelScope.launch {
            try { File(recording.filePath).delete() } catch (_: Exception) {}
            callRecordingDao.delete(recording)
            loadStats()
            _state.update { it.copy(snackbarMessage = "Recording deleted") }
        }
    }

    fun shareRecording(recording: CallRecordingEntity) {
        try {
            val file = File(recording.filePath)
            if (!file.exists()) {
                _state.update { it.copy(snackbarMessage = "File not found") }
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, "Share Recording")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun clearSnackbar()       { _state.update { it.copy(snackbarMessage = null) } }
    fun toggleSearch()        { _state.update { it.copy(showSearch = !it.showSearch) } }
    fun toggleSettingsPanel() { _state.update { it.copy(showSettingsPanel = !it.showSettingsPanel) } }
}
