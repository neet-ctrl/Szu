package com.accu.ui.dashboard

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.repositories.AppRepository
import com.accu.data.repositories.NavigationHistoryRepository
import com.accu.data.repositories.ShellRepository
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.UNKNOWN,
    val recentActions: List<RecentAction> = emptyList(),
    val quickStats: QuickStats = QuickStats(),
    val moduleCards: List<ModuleCard> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val showCommandPalette: Boolean = false,
    val recentScreens: List<SearchResult> = emptyList(),
)

enum class ShizukuStatus { UNKNOWN, RUNNING, NOT_RUNNING, NOT_INSTALLED, ROOT_MODE }

data class QuickStats(
    val installedApps: Int = 0,
    val systemApps: Int = 0,
    val frozenApps: Int = 0,
    val freeStorageGb: Float = 0f,
    val totalStorageGb: Float = 0f,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val batteryLevel: Int = 0,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val deviceModel: String = android.os.Build.MODEL,
    val savedCommandCount: Int = 0,
    val recordingsCount: Int = 0,
)

data class RecentAction(
    val id: Long = 0L,
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
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val shellRepository: ShellRepository,
    private val shizukuUtils: ShizukuUtils,
    private val historyRepo: NavigationHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val allSearchableItems = SearchIndex.entries

    init {
        loadDashboard()
        startShizukuMonitor()
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
                val pm = context.packageManager
                val allApps = pm.getInstalledPackages(0)
                val userApps = allApps.filter { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                val sysApps = allApps.size - userApps.size

                val stat = StatFs(Environment.getDataDirectory().path)
                val freeBytes = stat.availableBytes
                val totalBytes = stat.totalBytes
                val freeGb = freeBytes / (1024f * 1024f * 1024f)
                val totalGb = totalBytes / (1024f * 1024f * 1024f)

                val actManager = context.getSystemService(ActivityManager::class.java)
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                val usedMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
                val totalMb = memInfo.totalMem / (1024 * 1024)

                val savedCmds = shellRepository.getCommandCount()

                val modules = buildModuleCards()

                _uiState.update {
                    it.copy(
                        quickStats = QuickStats(
                            installedApps = userApps.size,
                            systemApps = sysApps,
                            freeStorageGb = freeGb,
                            totalStorageGb = totalGb,
                            ramUsedMb = usedMb,
                            ramTotalMb = totalMb,
                            savedCommandCount = savedCmds,
                        ),
                        moduleCards = modules,
                        recentActions = buildRecentActions(),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Dashboard load error")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun startShizukuMonitor() {
        viewModelScope.launch {
            while (true) {
                val status = when {
                    shizukuUtils.isRootAvailable() -> ShizukuStatus.ROOT_MODE
                    shizukuUtils.isShizukuAvailable() -> ShizukuStatus.RUNNING
                    shizukuUtils.isShizukuInstalled(context) -> ShizukuStatus.NOT_RUNNING
                    else -> ShizukuStatus.NOT_INSTALLED
                }
                _uiState.update { it.copy(shizukuStatus = status) }
                delay(3000)
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
                            // multi-word: all words must match somewhere
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
        ModuleCard("shizuku",    "Shizuku Center",    "Service & wireless ADB",        "shizuku",         "shizuku_center",    0xFF4A56E2),
        ModuleCard("shell",      "Shell Terminal",    "ADB commands & scripts",        "terminal",        "shell",             0xFF00D4FF),
        ModuleCard("debloat",    "Debloat",           "Remove bloatware",              "delete",          "debloat",           0xFFFF1744),
        ModuleCard("freeze",     "Freeze Apps",       "Suspend & hide packages",       "ac_unit",         "freeze_apps",       0xFF00BCD4),
        ModuleCard("privacy",    "Privacy Center",    "Block trackers & components",   "security",        "privacy",           0xFFFF6D00),
        ModuleCard("custom",     "Customization",     "Themes, colors & dark mode",    "palette",         "customization",     0xFFD500F9),
        ModuleCard("storage",    "Storage Center",    "Clean & analyze storage",       "storage",         "storage",           0xFF00E676),
        ModuleCard("files",      "File Manager",      "Advanced file management",      "folder",          "file_manager",      0xFFFFD600),
        ModuleCard("installer",  "Installer Center",  "APK install with options",      "install_mobile",  "installer",         0xFF4A56E2),
        ModuleCard("keymapper",  "Key Mapper",        "Remap keys & gestures",         "keyboard",        "automation",        0xFF7C4DFF),
        ModuleCard("language",   "Language Center",   "Per-app language selection",    "language",        "language_center",   0xFF00BFA5),
        ModuleCard("network",    "Network Center",    "Wi-Fi & mobile data",           "wifi",            "network_center",    0xFF2196F3),
        ModuleCard("audio",      "Audio Center",      "DSP equalizer & effects",       "equalizer",       "audio_center",      0xFFE91E63),
        ModuleCard("calls",      "Call Recorder",     "Rootless call recording",       "call",            "call_recorder",     0xFF4CAF50),
        ModuleCard("widgets",    "Smart Widgets",     "At-a-Glance enhancements",      "widgets",         "widgets",           0xFFFF9800),
        ModuleCard("learning",   "Learning Center",   "Guides & tutorials",            "school",          "learning_center",   0xFF9C27B0),
    )

    private fun buildRecentActions(): List<RecentAction> {
        return try {
            val pm = context.packageManager
            pm.getInstalledPackages(0)
                .filter { pkg -> pkg.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .sortedByDescending { it.lastUpdateTime }
                .take(8)
                .mapIndexed { index, pkg ->
                    val name = try { pm.getApplicationLabel(pkg.applicationInfo!!).toString() } catch (_: Exception) { pkg.packageName }
                    RecentAction(id = pkg.lastUpdateTime.takeIf { it > 0 } ?: index.toLong(), title = name, subtitle = "Updated ${formatRelativeTime(pkg.lastUpdateTime)}", iconRes = "install_mobile", route = "app_detail/${pkg.packageName}")
                }
                .ifEmpty { listOf(RecentAction(id = -1L, title = "Welcome to ACC", subtitle = "No recently updated apps", iconRes = "home", route = null)) }
        } catch (_: Exception) {
            listOf(RecentAction(id = -2L, title = "Welcome to ACC", subtitle = "Android Control Center", iconRes = "home", route = null))
        }
    }

    private fun formatRelativeTime(timeMs: Long): String {
        val diff = System.currentTimeMillis() - timeMs
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> "${diff / 86_400_000}d ago"
        }
    }

    fun refresh() { loadDashboard() }
}
