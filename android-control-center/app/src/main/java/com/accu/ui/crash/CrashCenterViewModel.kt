package com.accu.ui.crash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.crash.CrashExportManager
import com.accu.crash.CrashRepository
import com.accu.crash.SafeModeManager
import com.accu.data.db.entities.CrashEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CrashCenterUiState(
    val totalCrashes: Int = 0,
    val todayCrashes: Int = 0,
    val weekCrashes: Int = 0,
    val fatalCrashes: Int = 0,
    val nonFatalCrashes: Int = 0,
    val anrCrashes: Int = 0,
    val criticalCrashes: Int = 0,
    val recentCrashes: List<CrashEntity> = emptyList(),
    val safeModeEnabled: Boolean = false,
    val crashCountForSafeMode: Int = 0,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
)

@HiltViewModel
class CrashCenterViewModel @Inject constructor(
    private val repo: CrashRepository,
    private val safeMode: SafeModeManager,
    private val exportManager: CrashExportManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CrashCenterUiState())
    val state: StateFlow<CrashCenterUiState> = _state.asStateFlow()

    val isSafeModeEnabled: StateFlow<Boolean> = safeMode.isSafeModeEnabled

    init {
        val todayStart = todayStartMs()
        val weekStart = weekStartMs()

        viewModelScope.launch {
            repo.totalCount().collect { n -> _state.update { it.copy(totalCrashes = n) } }
        }
        viewModelScope.launch {
            repo.countSince(todayStart).collect { n -> _state.update { it.copy(todayCrashes = n) } }
        }
        viewModelScope.launch {
            repo.countSince(weekStart).collect { n -> _state.update { it.copy(weekCrashes = n) } }
        }
        viewModelScope.launch {
            repo.fatalCount().collect { n -> _state.update { it.copy(fatalCrashes = n) } }
        }
        viewModelScope.launch {
            repo.nonFatalCount().collect { n -> _state.update { it.copy(nonFatalCrashes = n) } }
        }
        viewModelScope.launch {
            repo.anrCount().collect { n -> _state.update { it.copy(anrCrashes = n) } }
        }
        viewModelScope.launch {
            repo.criticalCount().collect { n -> _state.update { it.copy(criticalCrashes = n) } }
        }
        viewModelScope.launch {
            repo.getPage(limit = 5, offset = 0).collect { list ->
                _state.update { it.copy(recentCrashes = list) }
            }
        }
        viewModelScope.launch {
            safeMode.isSafeModeEnabled.collect { enabled ->
                _state.update { it.copy(safeModeEnabled = enabled, crashCountForSafeMode = safeMode.getCrashCount()) }
            }
        }
    }

    fun enableSafeMode() = safeMode.enableSafeMode()
    fun disableSafeMode() = safeMode.disableSafeMode()

    fun deleteAll() = viewModelScope.launch { repo.deleteAll() }

    fun exportAllAsZip(onDone: (android.content.Intent) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            try {
                val all = repo.getAll().first()
                val file = exportManager.exportAllAsZip(all)
                onDone(exportManager.buildShareIntent(file))
            } catch (e: Exception) {
                _state.update { it.copy(exportMessage = "Export failed: ${e.message}") }
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    fun clearExportMessage() = _state.update { it.copy(exportMessage = null) }

    private fun todayStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun weekStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
