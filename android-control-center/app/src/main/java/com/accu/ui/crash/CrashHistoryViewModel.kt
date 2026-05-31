package com.accu.ui.crash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.crash.CrashExportManager
import com.accu.crash.CrashRepository
import com.accu.data.db.entities.CrashEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CrashSortOrder { NEWEST, OLDEST, RISK_HIGH, RISK_LOW }
enum class CrashFilter { ALL, FATAL, NON_FATAL, ANR, CRITICAL, FAVORITES }

data class CrashHistoryUiState(
    val crashes: List<CrashEntity> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: CrashFilter = CrashFilter.ALL,
    val sortOrder: CrashSortOrder = CrashSortOrder.NEWEST,
    val selectedIds: Set<Long> = emptySet(),
    val isMultiSelect: Boolean = false,
    val isExporting: Boolean = false,
    val snackbar: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CrashHistoryViewModel @Inject constructor(
    private val repo: CrashRepository,
    private val exportManager: CrashExportManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CrashHistoryUiState())
    val state: StateFlow<CrashHistoryUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(CrashFilter.ALL)
    private val sortFlow = MutableStateFlow(CrashSortOrder.NEWEST)

    init {
        viewModelScope.launch {
            combine(
                queryFlow.debounce(250),
                filterFlow,
                sortFlow,
            ) { q, f, s -> Triple(q, f, s) }
                .flatMapLatest { (q, f, _) ->
                    when {
                        q.isNotBlank() -> repo.search("%$q%")
                        else -> when (f) {
                            CrashFilter.ALL       -> repo.getAll()
                            CrashFilter.FATAL     -> repo.getByFatality(true)
                            CrashFilter.NON_FATAL -> repo.getByFatality(false)
                            CrashFilter.ANR       -> repo.getAnrs()
                            CrashFilter.CRITICAL  -> repo.getByRiskLevel("CRITICAL")
                            CrashFilter.FAVORITES -> repo.getFavorites()
                        }
                    }
                }
                .map { list -> sortFlow.value.sort(list) }
                .collect { crashes ->
                    _state.update { it.copy(crashes = crashes, isLoading = false) }
                }
        }
    }

    fun setQuery(q: String) { queryFlow.value = q; _state.update { it.copy(query = q) } }
    fun setFilter(f: CrashFilter) { filterFlow.value = f; _state.update { it.copy(filter = f, selectedIds = emptySet(), isMultiSelect = false) } }
    fun setSortOrder(s: CrashSortOrder) { sortFlow.value = s; _state.update { it.copy(sortOrder = s) } }

    // ─── Selection ────────────────────────────────────────────────────────────
    fun toggleSelection(id: Long) {
        _state.update {
            val newIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = newIds, isMultiSelect = newIds.isNotEmpty())
        }
    }
    fun selectAll() = _state.update { it.copy(selectedIds = it.crashes.map { c -> c.id }.toSet(), isMultiSelect = true) }
    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet(), isMultiSelect = false) }

    // ─── Mutations ────────────────────────────────────────────────────────────
    fun delete(crashId: String) = viewModelScope.launch {
        repo.delete(crashId)
        _state.update { it.copy(snackbar = "Crash deleted") }
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _state.value.selectedIds.toList()
        repo.deleteByIds(ids)
        clearSelection()
        _state.update { it.copy(snackbar = "${ids.size} crashes deleted") }
    }

    fun deleteAll() = viewModelScope.launch {
        repo.deleteAll()
        _state.update { it.copy(snackbar = "All crash logs cleared") }
    }

    fun toggleFavorite(crash: CrashEntity) = viewModelScope.launch {
        repo.setFavorited(crash.crashId, !crash.isFavorited)
    }

    fun togglePin(crash: CrashEntity) = viewModelScope.launch {
        repo.setPinned(crash.crashId, !crash.isPinned)
    }

    // ─── Export ───────────────────────────────────────────────────────────────
    fun exportSelected(onDone: (android.content.Intent) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            try {
                val crashes = _state.value.crashes.filter { it.id in _state.value.selectedIds }
                val file = exportManager.exportAllAsZip(crashes)
                onDone(exportManager.buildShareIntent(file))
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = "Export failed: ${e.message}") }
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbar = null) }

    // ─── Sort helper ──────────────────────────────────────────────────────────
    private fun CrashSortOrder.sort(list: List<CrashEntity>): List<CrashEntity> {
        val pinned = list.filter { it.isPinned }
        val rest = list.filter { !it.isPinned }
        val sorted = when (this) {
            CrashSortOrder.NEWEST    -> rest.sortedByDescending { it.timestamp }
            CrashSortOrder.OLDEST    -> rest.sortedBy { it.timestamp }
            CrashSortOrder.RISK_HIGH -> rest.sortedBy { riskOrdinal(it.riskLevel) }
            CrashSortOrder.RISK_LOW  -> rest.sortedByDescending { riskOrdinal(it.riskLevel) }
        }
        return pinned + sorted
    }

    private fun riskOrdinal(level: String) = when (level) {
        "CRITICAL" -> 0; "HIGH" -> 1; "MEDIUM" -> 2; else -> 3
    }
}
