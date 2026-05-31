package com.accu.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.data.repositories.NavigationHistoryRepository
import com.accu.data.repositories.ShellRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DashboardUiState(
    val accuStatus: AccuConnectionStatus = AccuConnectionStatus.UNKNOWN,
    val recentActions: List<RecentAction> = emptyList(),
    val quickStats: QuickStats = QuickStats(),
    val moduleCards: List<ModuleCard> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val showCommandPalette: Boolean = false,
    val recentScreens: List<SearchResult> = emptyList(),
)

enum class AccuConnectionStatus { UNKNOWN, RUNNING, NOT_RUNNING, NOT_INSTALLED, ROOT_MODE }

data class QuickStats(
    val installedApps: Int = 0,
    val systemApps: Int = 0,
    val frozenApps: Int = 0,
    val freeStorageGb: Float = 0f,
    val totalStorageGb: Float = 0f,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val batteryLevel: Int = 0,
    val cpuCores: Int = 0,
    val androidVersion: String = "",
    val deviceModel: String = "",
    val savedCommandCount: Int = 0,
    val recordingsCount: Int = 0,
)

data class RecentAction(
    val id: Long,
    val title: String,
    val subtitle: String,
    val iconRes: String,
    val timestamp: Long = System.currentTimeMillis(),
    val route: String? = null,
)

data class ModuleCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val route: String,
    val accentColor: Long,
    val badge: String? = null,
    val isEnabled: Boolean = true,
)

data class SearchResult(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: String,
    val category: String = "General",
    val tags: List<String> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectionManager: AccuConnectionManager,
    private val shellRepository: ShellRepository,
    private val historyRepo: NavigationHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val allSearchableItems = SearchIndex.entries

    init {
        loadDashboard()
        startConnectionMonitor()
        collectHistory()
    }

    private fun collectHistory() {
        viewModelScope.launch {
            historyRepo.historyFlow.collect { routes ->
                val screens = routes.mapNotNull { route ->
                    SearchIndex.entries.find { it.route == route }
                }
                _uiState.update { it.copy(recentScreens = screens) }
            }
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ── App counts from target device ─────────────────────────────
                val userAppsRaw = connectionManager.exec("pm list packages -3 2>/dev/null | wc -l").output.trim()
                val sysAppsRaw  = connectionManager.exec("pm list packages -s 2>/dev/null | wc -l").output.trim()
                val userApps    = userAppsRaw.toIntOrNull() ?: 0
                val sysApps     = sysAppsRaw.toIntOrNull() ?: 0

                // ── RAM from target /proc/meminfo ─────────────────────────────
                val memRaw   = connectionManager.exec("cat /proc/meminfo 2>/dev/null").output
                val memTotal = memRaw.lineSequence()
                    .firstOrNull { it.startsWith("MemTotal:") }
                    ?.replace(Regex("[^0-9]"), "")?.trim()?.toLongOrNull()?.div(1024) ?: 0L
                val memAvail = memRaw.lineSequence()
                    .firstOrNull { it.startsWith("MemAvailable:") }
                    ?.replace(Regex("[^0-9]"), "")?.trim()?.toLongOrNull()?.div(1024) ?: 0L
                val memUsed = (memTotal - memAvail).coerceAtLeast(0L)

                // ── Storage from target df /data ──────────────────────────────
                val dfRaw   = connectionManager.exec("df /data 2>/dev/null | tail -1").output.trim()
                val dfParts = dfRaw.split(Regex("\\s+"))
                val totalKb = dfParts.getOrNull(1)?.toLongOrNull() ?: 0L
                val freeKb  = dfParts.getOrNull(3)?.toLongOrNull() ?: 0L
                val totalGb = totalKb * 1024L / (1024f * 1024f * 1024f)
                val freeGb  = freeKb  * 1024L / (1024f * 1024f * 1024f)

                // ── Battery from target dumpsys ───────────────────────────────
                val battRaw   = connectionManager.exec("dumpsys battery 2>/dev/null | grep -m1 'level:'").output.trim()
                val battLevel = battRaw.replace(Regex("[^0-9]"), "").take(3).toIntOrNull() ?: 0

                // ── CPU cores from target ─────────────────────────────────────
                val cpuRaw   = connectionManager.exec("nproc 2>/dev/null || grep -c '^processor' /proc/cpuinfo 2>/dev/null").output.trim()
                val cpuCores = cpuRaw.lines().firstOrNull()?.trim()?.toIntOrNull() ?: 1

                // ── Device info from target getprop ───────────────────────────
                val model   = connectionManager.exec("getprop ro.product.model 2>/dev/null").output.trim()
                val version = connectionManager.exec("getprop ro.build.version.release 2>/dev/null").output.trim()

                val savedCmds = shellRepository.getCommandCount()
                val modules   = buildModuleCards()

                _uiState.update {
                    it.copy(
                        quickStats = QuickStats(
                            installedApps    = userApps,
                            systemApps       = sysApps,
                            freeStorageGb    = freeGb,
                            totalStorageGb   = totalGb,
                            ramUsedMb        = memUsed,
                            ramTotalMb       = memTotal,
                            batteryLevel     = battLevel,
                            cpuCores         = cpuCores,
                            androidVersion   = version.ifBlank { "—" },
                            deviceModel      = model.ifBlank { "—" },
                            savedCommandCount = savedCmds,
                        ),
                        moduleCards   = modules,
                        recentActions = buildRecentActions(),
                        isLoading     = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Dashboard load error")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun startConnectionMonitor() {
        viewModelScope.launch {
            while (true) {
                val status = when (connectionManager.getConnectionState()) {
                    AccuConnectionManager.ConnectionState.CONNECTED_ROOT     -> AccuConnectionStatus.ROOT_MODE
                    AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS -> AccuConnectionStatus.RUNNING
                    AccuConnectionManager.ConnectionState.CONNECTED_OTG      -> AccuConnectionStatus.RUNNING
                    AccuConnectionManager.ConnectionState.CONNECTING         -> AccuConnectionStatus.UNKNOWN
                    AccuConnectionManager.ConnectionState.DISCONNECTED       -> AccuConnectionStatus.NOT_RUNNING
                    else                                                     -> AccuConnectionStatus.UNKNOWN
                }
                _uiState.update { it.copy(accuStatus = status) }
                delay(5000)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val results = if (query.isBlank()) emptyList()
            else {
                val q = query.trim().lowercase()
                allSearchableItems
                    .mapNotNull { item ->
                        val score = when {
                            item.title.equals(q, ignoreCase = true)              -> 100
                            item.title.startsWith(q, ignoreCase = true)          -> 85
                            item.title.contains(q, ignoreCase = true)            -> 70
                            item.tags.any { it.equals(q, ignoreCase = true) }    -> 60
                            item.subtitle.contains(q, ignoreCase = true)         -> 50
                            item.tags.any { it.contains(q, ignoreCase = true) }  -> 35
                            q.contains(' ') && q.split(' ').all { word ->
                                item.title.contains(word, ignoreCase = true) ||
                                item.subtitle.contains(word, ignoreCase = true) ||
                                item.tags.any { t -> t.contains(word, ignoreCase = true) }
                            } -> 25
                            else -> 0
                        }
                        if (score > 0) score to item else null
                    }
                    .sortedByDescending { it.first }
                    .map { it.second }
                    .take(30)
            }
            state.copy(searchQuery = query, searchResults = results)
        }
    }

    fun toggleCommandPalette() {
        _uiState.update { it.copy(showCommandPalette = !it.showCommandPalette) }
    }

    fun dismissCommandPalette() {
        _uiState.update { it.copy(showCommandPalette = false, searchQuery = "", searchResults = emptyList()) }
    }

    private fun buildModuleCards(): List<ModuleCard> = listOf(
        ModuleCard("accu",       "ACCU Connection",   "Privilege & wireless ADB",      "wifi_protected_setup", "accu_center",      0xFF4A56E2),
        ModuleCard("shell",      "Shell Terminal",    "ADB commands & scripts",        "terminal",             "shell",            0xFF00D4FF),
        ModuleCard("debloat",    "Debloat",           "Remove bloatware",              "delete",               "debloat",          0xFFFF1744),
        ModuleCard("freeze",     "Freeze Apps",       "Suspend & hide packages",       "ac_unit",              "freeze_apps",      0xFF00BCD4),
        ModuleCard("privacy",    "Privacy Center",    "Block trackers & components",   "security",             "privacy",          0xFFFF6D00),
        ModuleCard("custom",     "Customization",     "Themes, colors & dark mode",    "palette",              "customization",    0xFFD500F9),
        ModuleCard("storage",    "Storage Center",    "Clean & analyze storage",       "storage",              "storage",          0xFF00E676),
        ModuleCard("files",      "File Manager",      "Advanced file management",      "folder",               "file_manager",     0xFFFFD600),
        ModuleCard("installer",  "Installer Center",  "APK install with options",      "install_mobile",       "installer",        0xFF4A56E2),
        ModuleCard("keymapper",  "Key Mapper",        "Remap keys & gestures",         "keyboard",             "automation",       0xFF7C4DFF),
        ModuleCard("language",   "Language Center",   "Per-app language selection",    "language",             "language_center",  0xFF00BFA5),
        ModuleCard("network",    "Network Center",    "Wi-Fi & mobile data",           "wifi",                 "network_center",   0xFF2196F3),
        ModuleCard("audio",      "Audio Center",      "DSP equalizer & effects",       "equalizer",            "audio_center",     0xFFE91E63),
        ModuleCard("calls",      "Call Recorder",     "Rootless call recording",       "call",                 "call_recorder",    0xFF4CAF50),
        ModuleCard("widgets",    "Smart Widgets",     "At-a-Glance enhancements",      "widgets",              "widgets",          0xFFFF9800),
        ModuleCard("learning",   "Learning Center",   "Guides & tutorials",            "school",               "learning_center",  0xFF9C27B0),
    )

    /** Show recently-installed user apps from the target device. */
    private suspend fun buildRecentActions(): List<RecentAction> {
        return try {
            // pm list packages -3 lists all user packages; reverse to approximate recency
            val out = connectionManager.exec(
                "pm list packages -3 2>/dev/null | sed 's/package://' | tail -8"
            ).output
            out.lines()
                .filter { it.isNotBlank() }
                .reversed()
                .mapIndexed { index, pkg ->
                    val pkgTrimmed = pkg.trim()
                    val label = pkgTrimmed.split(".").lastOrNull()
                        ?.replaceFirstChar { it.uppercase() } ?: pkgTrimmed
                    RecentAction(
                        id       = (index + 1).toLong(),
                        title    = label,
                        subtitle = pkgTrimmed,
                        iconRes  = "install_mobile",
                        route    = "app_detail/$pkgTrimmed",
                    )
                }
                .ifEmpty {
                    listOf(RecentAction(id = -1L, title = "Welcome to ACCU", subtitle = "Connected to target device", iconRes = "home", route = null))
                }
        } catch (_: Exception) {
            listOf(RecentAction(id = -2L, title = "Welcome to ACCU", subtitle = "Android Control Center", iconRes = "home", route = null))
        }
    }

    fun refresh() { loadDashboard() }
}
