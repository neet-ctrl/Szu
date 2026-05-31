package com.accu.ui.crash

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.crash.CrashExportManager
import com.accu.crash.CrashRepository
import com.accu.data.db.entities.CrashEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CrashDetailUiState(
    val crash: CrashEntity? = null,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val snackbar: String? = null,
    val shareIntent: Intent? = null,
    val showDeleteConfirm: Boolean = false,
    val showNotesDialog: Boolean = false,
    val notesInput: String = "",
)

@HiltViewModel
class CrashDetailViewModel @Inject constructor(
    private val repo: CrashRepository,
    private val exportManager: CrashExportManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CrashDetailUiState())
    val state: StateFlow<CrashDetailUiState> = _state.asStateFlow()

    fun load(crashId: String) {
        viewModelScope.launch {
            repo.getById(crashId).collect { crash ->
                _state.update { it.copy(crash = crash, isLoading = false, notesInput = crash?.userNotes ?: "") }
            }
        }
    }

    fun toggleFavorite() {
        val c = _state.value.crash ?: return
        viewModelScope.launch { repo.setFavorited(c.crashId, !c.isFavorited) }
    }

    fun togglePin() {
        val c = _state.value.crash ?: return
        viewModelScope.launch { repo.setPinned(c.crashId, !c.isPinned) }
    }

    fun saveNotes() {
        val c = _state.value.crash ?: return
        viewModelScope.launch {
            repo.setNotes(c.crashId, _state.value.notesInput)
            _state.update { it.copy(showNotesDialog = false, snackbar = "Notes saved") }
        }
    }

    fun setNotesInput(s: String) = _state.update { it.copy(notesInput = s) }
    fun showNotesDialog() = _state.update { it.copy(showNotesDialog = true) }
    fun hideNotesDialog() = _state.update { it.copy(showNotesDialog = false) }
    fun showDeleteConfirm() = _state.update { it.copy(showDeleteConfirm = true) }
    fun hideDeleteConfirm() = _state.update { it.copy(showDeleteConfirm = false) }

    fun delete(onDeleted: () -> Unit) {
        val c = _state.value.crash ?: return
        viewModelScope.launch {
            repo.delete(c.crashId)
            onDeleted()
        }
    }

    // ─── Export ───────────────────────────────────────────────────────────────
    fun exportTxt()      = export { exportManager.exportAsTxt(it) }
    fun exportJson()     = export { exportManager.exportAsJson(it) }
    fun exportMarkdown() = export { exportManager.exportAsMarkdown(it) }
    fun exportHtml()     = export { exportManager.exportAsHtml(it) }
    fun exportZip()      = export { exportManager.exportAsZip(it) }

    private fun export(block: suspend (CrashEntity) -> java.io.File) {
        val c = _state.value.crash ?: return
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            try {
                val file = block(c)
                _state.update { it.copy(shareIntent = exportManager.buildShareIntent(file)) }
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = "Export failed: ${e.message}") }
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    fun clearShareIntent() = _state.update { it.copy(shareIntent = null) }
    fun clearSnackbar() = _state.update { it.copy(snackbar = null) }
}
