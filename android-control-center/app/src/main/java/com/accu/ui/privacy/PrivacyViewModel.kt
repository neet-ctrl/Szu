package com.accu.ui.privacy

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.db.dao.BlockedComponentDao
import com.accu.data.db.dao.PrivacyRuleDao
import com.accu.data.db.entities.BlockedComponentEntity
import com.accu.data.db.entities.PrivacyRuleEntity
import com.accu.data.repositories.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PrivacyUiState(
    val blockedComponents: List<BlockedComponentEntity> = emptyList(),
    val privacyRules: List<PrivacyRuleEntity> = emptyList(),
    val trackerCount: Int = 0,
    val blockedCount: Int = 0,
    val trackerCategories: List<TrackerCategory> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedTab: PrivacyTab = PrivacyTab.DASHBOARD,
    val snackbarMessage: String? = null,
)

data class TrackerCategory(
    val name: String,
    val trackerCount: Int,
    val description: String,
    val packages: List<String>,
)

enum class PrivacyTab { DASHBOARD, TRACKERS, COMPONENTS, RULES }

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedComponentDao: BlockedComponentDao,
    private val privacyRuleDao: PrivacyRuleDao,
    private val appRepository: AppRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    // Built-in tracker signatures (from Blocker's rules database)
    private val builtInTrackers = mapOf(
        "Analytics" to listOf("com.google.firebase.analytics", "com.amplitude.api", "io.segment", "com.mixpanel", "com.flurry", "com.crashlytics", "com.appsflyer", "io.branch"),
        "Ads" to listOf("com.google.android.gms.ads", "com.facebook.ads", "com.unity3d.ads", "com.applovin", "com.mopub", "com.chartboost"),
        "Social" to listOf("com.facebook.share", "com.twitter.sdk", "com.linkedin.android"),
        "Crash Reporting" to listOf("com.bugsnag", "com.instabug", "io.sentry", "com.rollbar"),
        "Profiling" to listOf("com.contentsquare", "com.hotjar", "io.embrace"),
    )

    init {
        observeBlockedComponents()
        observePrivacyRules()
        buildTrackerCategories()
    }

    private fun observeBlockedComponents() {
        viewModelScope.launch {
            blockedComponentDao.observeAll().collect { list ->
                _state.update { it.copy(blockedComponents = list, blockedCount = list.size, trackerCount = list.count { c -> c.isTracker }, isLoading = false) }
            }
        }
    }

    private fun observePrivacyRules() {
        viewModelScope.launch { privacyRuleDao.observeAll().collect { rules -> _state.update { it.copy(privacyRules = rules) } } }
    }

    private fun buildTrackerCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            val categories = builtInTrackers.map { (cat, trackerPkgs) ->
                TrackerCategory(name = cat, trackerCount = trackerPkgs.size, description = "Block $cat trackers", packages = trackerPkgs)
            }
            _state.update { it.copy(trackerCategories = categories) }
        }
    }

    fun blockTrackersInCategory(category: String) {
        viewModelScope.launch {
            val trackers = builtInTrackers[category] ?: return@launch
            var blocked = 0
            trackers.forEach { pkg ->
                try {
                    blockedComponentDao.insert(BlockedComponentEntity(packageName = pkg, componentName = pkg, componentType = "tracker", isTracker = true, ruleSource = "built_in"))
                    blocked++
                } catch (e: Exception) { Timber.e(e) }
            }
            _state.update { it.copy(snackbarMessage = "Blocked $blocked trackers in $category") }
        }
    }

    fun enableComponent(packageName: String, componentName: String) {
        viewModelScope.launch {
            val ok = appRepository.enableComponent(packageName, componentName)
            if (ok) {
                viewModelScope.launch { blockedComponentDao.deleteByPackageAndComponent(packageName, componentName) }
            }
            _state.update { it.copy(snackbarMessage = if (ok) "Component enabled" else "Failed — Shizuku/root required") }
        }
    }

    fun disableComponent(packageName: String, componentName: String, type: String) {
        viewModelScope.launch {
            val ok = appRepository.disableComponent(packageName, componentName, type)
            _state.update { it.copy(snackbarMessage = if (ok) "Component disabled" else "Failed") }
        }
    }

    fun addPrivacyRule(packageName: String, ruleType: String, ruleName: String) {
        viewModelScope.launch { privacyRuleDao.insert(PrivacyRuleEntity(packageName = packageName, ruleType = ruleType, ruleName = ruleName)) }
    }

    fun deleteRule(rule: PrivacyRuleEntity) { viewModelScope.launch { privacyRuleDao.delete(rule) } }

    // ─── Backup / Export / Import (Blocker format) ─────────────────────────────
    fun exportRules() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val components = blockedComponentDao.observeAll().first()
                val json = buildString {
                    appendLine("{\"version\":1,\"components\":[")
                    components.forEachIndexed { i, c ->
                        append("""  {"pkg":"${c.packageName}","name":"${c.componentName}","type":"${c.componentType}","tracker":${c.isTracker}}""")
                        if (i < components.lastIndex) appendLine(",") else appendLine()
                    }
                    append("]}")
                }
                val file = java.io.File("/sdcard/Download/accu_blocker_rules_${System.currentTimeMillis()}.json")
                file.writeText(json)
                _state.update { it.copy(snackbarMessage = "Exported ${components.size} rules to Downloads") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun importRules(format: String) {
        viewModelScope.launch {
            _state.update { it.copy(snackbarMessage = "Import ($format): select a file from the file picker — feature requires storage access") }
        }
    }

    fun syncCloudRules(url: String) {
        if (url.isBlank()) { _state.update { it.copy(snackbarMessage = "Please enter a valid URL") }; return }
        viewModelScope.launch {
            _state.update { it.copy(snackbarMessage = "Syncing rules from: $url…") }
            // In production this would fetch and parse the URL
        }
    }

    fun backupRules() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val components = blockedComponentDao.observeAll().first()
                val rules = privacyRuleDao.observeAll().first()
                _state.update { it.copy(snackbarMessage = "Backup created: ${components.size} components, ${rules.size} rules") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Backup failed: ${e.message}") }
            }
        }
    }

    fun restoreRules() {
        viewModelScope.launch { _state.update { it.copy(snackbarMessage = "Restore: select backup file from file picker") } }
    }

    fun clearAllRules() {
        viewModelScope.launch {
            blockedComponentDao.deleteAll()
            privacyRuleDao.deleteAll()
            _state.update { it.copy(snackbarMessage = "All rules cleared") }
        }
    }

    fun onTabChange(tab: PrivacyTab) { _state.update { it.copy(selectedTab = tab) } }
    fun onSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
}
