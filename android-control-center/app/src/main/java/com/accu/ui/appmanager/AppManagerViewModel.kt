package com.accu.ui.appmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.data.db.entities.FrozenAppEntity
import com.accu.data.repositories.AppRepository
import com.accu.data.repositories.FreezeMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

data class AppManagerUiState(
    val apps: List<AppUiModel> = emptyList(),
    val filteredApps: List<AppUiModel> = emptyList(),
    val frozenApps: List<FrozenAppEntity> = emptyList(),
    val selectedTab: AppTab = AppTab.USER,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val filterFlags: Set<AppFilter> = emptySet(),
    val isLoading: Boolean = true,
    val selectedApps: Set<String> = emptySet(),
    val isMultiSelect: Boolean = false,
    val snackbarMessage: String? = null,
)

data class AppUiModel(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isFrozen: Boolean,
    val isHidden: Boolean,
    val installTime: Long,
    val lastUpdateTime: Long,
    val apkSize: Long = 0L,
    val targetSdk: Int = 0,
    val minSdk: Int = 0,
    val dataDir: String = "",
    val sourceDir: String = "",
)

enum class AppTab { USER, SYSTEM, FROZEN, ALL }
enum class SortOrder { NAME_ASC, NAME_DESC, INSTALL_DATE, UPDATE_DATE, SIZE }
enum class AppFilter { ENABLED, DISABLED, FROZEN, HIDDEN, SYSTEM, USER }

@HiltViewModel
class AppManagerViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AppManagerUiState())
    val state: StateFlow<AppManagerUiState> = _state.asStateFlow()

    init {
        loadApps()
        observeFrozen()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Always load from the connected target device via ADB
                val packages = connectionManager.listPackages()
                val models = packages.map { pkg ->
                    val label = pkg.packageName.split(".")
                        .lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg.packageName
                    AppUiModel(
                        packageName    = pkg.packageName,
                        appName        = label,
                        versionName    = "",
                        versionCode    = 0L,
                        isSystemApp    = pkg.isSystem,
                        isEnabled      = pkg.isEnabled,
                        isFrozen       = false,
                        isHidden       = false,
                        installTime    = 0L,
                        lastUpdateTime = 0L,
                        apkSize        = 0L,
                        targetSdk      = 0,
                        minSdk         = 0,
                        dataDir        = "",
                        sourceDir      = pkg.apkPath,
                    )
                }
                _state.update { it.copy(apps = models, isLoading = false) }
                applyFilters()
            } catch (e: Exception) {
                Timber.e(e, "AppManagerViewModel: failed to load apps from target device")
                _state.update { it.copy(isLoading = false, snackbarMessage = "Failed to load apps — check connection") }
            }
        }
    }

    private fun observeFrozen() {
        viewModelScope.launch {
            appRepository.observeFrozenApps().collect { frozen ->
                _state.update { s ->
                    val frozenPkgs = frozen.map { it.packageName }.toSet()
                    val updatedApps = s.apps.map { app ->
                        app.copy(isFrozen = app.packageName in frozenPkgs)
                    }
                    s.copy(frozenApps = frozen, apps = updatedApps)
                }
                applyFilters()
            }
        }
    }

    fun onTabChange(tab: AppTab) {
        _state.update { it.copy(selectedTab = tab) }
        applyFilters()
    }

    fun onSearchChange(q: String) {
        _state.update { it.copy(searchQuery = q) }
        applyFilters()
    }

    fun onSortChange(sort: SortOrder) {
        _state.update { it.copy(sortOrder = sort) }
        applyFilters()
    }

    fun toggleFilter(filter: AppFilter) {
        _state.update { s ->
            val filters = s.filterFlags.toMutableSet()
            if (!filters.add(filter)) {
                filters.remove(filter)
            } else {
                // USER and SYSTEM are mutually exclusive — remove the opposite when one is toggled on
                if (filter == AppFilter.USER)   filters.remove(AppFilter.SYSTEM)
                if (filter == AppFilter.SYSTEM) filters.remove(AppFilter.USER)
                // ENABLED and DISABLED are mutually exclusive
                if (filter == AppFilter.ENABLED)  filters.remove(AppFilter.DISABLED)
                if (filter == AppFilter.DISABLED) filters.remove(AppFilter.ENABLED)
            }
            s.copy(filterFlags = filters)
        }
        applyFilters()
    }

    private fun applyFilters() {
        val s = _state.value
        var list = when (s.selectedTab) {
            AppTab.USER   -> s.apps.filter { !it.isSystemApp }
            AppTab.SYSTEM -> s.apps.filter { it.isSystemApp }
            AppTab.FROZEN -> s.apps.filter { it.isFrozen }
            AppTab.ALL    -> s.apps
        }
        if (s.searchQuery.isNotBlank()) {
            val q = s.searchQuery.lowercase()
            list = list.filter { it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
        if (s.filterFlags.isNotEmpty()) {
            list = list.filter { app ->
                s.filterFlags.all { f ->
                    when (f) {
                        AppFilter.ENABLED  -> app.isEnabled
                        AppFilter.DISABLED -> !app.isEnabled
                        AppFilter.FROZEN   -> app.isFrozen
                        AppFilter.HIDDEN   -> app.isHidden
                        AppFilter.SYSTEM   -> app.isSystemApp
                        AppFilter.USER     -> !app.isSystemApp
                    }
                }
            }
        }
        list = when (s.sortOrder) {
            SortOrder.NAME_ASC    -> list.sortedBy { it.appName }
            SortOrder.NAME_DESC   -> list.sortedByDescending { it.appName }
            SortOrder.INSTALL_DATE-> list.sortedByDescending { it.installTime }
            SortOrder.UPDATE_DATE -> list.sortedByDescending { it.lastUpdateTime }
            SortOrder.SIZE        -> list.sortedByDescending { it.apkSize }
        }
        _state.update { it.copy(filteredApps = list) }
    }

    fun freezeApp(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.freezeApp(packageName, FreezeMethod.DISABLE)
            _state.update { it.copy(snackbarMessage = if (ok) "App frozen" else "Failed to freeze app") }
        }
    }

    fun unfreezeApp(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.unfreezeApp(packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "App unfrozen" else "Failed to unfreeze app") }
        }
    }

    fun hideApp(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.freezeApp(packageName, FreezeMethod.HIDE)
            _state.update { it.copy(snackbarMessage = if (ok) "App hidden" else "Failed to hide app") }
        }
    }

    fun uninstallForUser(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.uninstallForUser(packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "App removed for user" else "Failed to remove app") }
            if (ok) loadApps()
        }
    }

    fun forceStop(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.forceStop(packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "App force stopped" else "Failed to force stop — check connection") }
        }
    }

    fun clearData(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.clearData(packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "App data cleared" else "Failed to clear data — check connection") }
        }
    }

    fun extractApk(packageName: String) {
        viewModelScope.launch {
            val dest = "/sdcard/Download/${packageName}_${System.currentTimeMillis()}.apk"
            val ok = appRepository.extractApk(packageName, dest)
            _state.update { it.copy(snackbarMessage = if (ok) "APK saved to $dest" else "Failed to extract APK") }
        }
    }

    fun toggleMultiSelect() { _state.update { it.copy(isMultiSelect = !it.isMultiSelect, selectedApps = emptySet()) } }
    fun toggleAppSelection(pkg: String) {
        _state.update { s ->
            val sel = s.selectedApps.toMutableSet()
            if (!sel.add(pkg)) sel.remove(pkg)
            s.copy(selectedApps = sel)
        }
    }

    fun batchFreeze() {
        val pkgs = _state.value.selectedApps.toList()
        viewModelScope.launch {
            _state.update { it.copy(isMultiSelect = false, selectedApps = emptySet()) }
            var succeeded = 0
            pkgs.forEach { pkg -> if (appRepository.freezeApp(pkg, FreezeMethod.DISABLE)) succeeded++ }
            val msg = if (succeeded == pkgs.size) "$succeeded apps frozen"
                      else "$succeeded/${pkgs.size} frozen — ${pkgs.size - succeeded} failed (check connection)"
            _state.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun batchUninstall() {
        val pkgs = _state.value.selectedApps.toList()
        viewModelScope.launch {
            _state.update { it.copy(isMultiSelect = false, selectedApps = emptySet()) }
            var succeeded = 0
            pkgs.forEach { pkg -> if (appRepository.uninstallForUser(pkg)) succeeded++ }
            val msg = if (succeeded == pkgs.size) "$succeeded apps removed"
                      else "$succeeded/${pkgs.size} removed — ${pkgs.size - succeeded} failed"
            _state.update { it.copy(snackbarMessage = msg) }
            if (succeeded > 0) loadApps()
        }
    }

    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
    fun refresh() { loadApps() }
}
