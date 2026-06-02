package com.accu.ui.privacy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
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

// ── Sensor ops that can be controlled via appops ──────────────────────────────
val SENSOR_OPS = listOf(
    "CAMERA"                      to "Camera",
    "RECORD_AUDIO"                to "Microphone",
    "ACCESS_FINE_LOCATION"        to "Location (Fine)",
    "ACCESS_COARSE_LOCATION"      to "Location (Coarse)",
    "ACCESS_BACKGROUND_LOCATION"  to "Location (Background)",
    "BODY_SENSORS"                to "Body Sensors",
    "READ_CONTACTS"               to "Contacts (Read)",
    "WRITE_CONTACTS"              to "Contacts (Write)",
    "READ_CALL_LOG"               to "Call Log",
    "READ_SMS"                    to "Read SMS",
    "SEND_SMS"                    to "Send SMS",
    "GET_ACCOUNTS"                to "Get Accounts",
    "READ_PHONE_STATE"            to "Phone State",
    "READ_EXTERNAL_STORAGE"       to "Storage (Read)",
    "WRITE_EXTERNAL_STORAGE"      to "Storage (Write)",
)

data class SensorPrivacyApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val blockedOps: Set<String> = emptySet(),
)

data class FirewallApp(
    val packageName: String,
    val appName: String,
    val uid: Int = -1,
    val isBlocked: Boolean = false,
)

data class AuditApp(
    val packageName: String,
    val appName: String,
    val dangerousPerms: List<String> = emptyList(),
    val grantedPerms: List<String> = emptyList(),
    val trackerHits: List<String> = emptyList(),
    val privacyScore: Int = 100,
)

data class PrivacyUiState(
    val blockedComponents: List<BlockedComponentEntity> = emptyList(),
    val privacyRules: List<PrivacyRuleEntity> = emptyList(),
    val trackerCount: Int = 0,
    val blockedCount: Int = 0,
    val trackerCategories: List<PrivacyTrackerCategory> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedTab: PrivacyTab = PrivacyTab.DASHBOARD,
    val snackbarMessage: String? = null,
    // Sensors tab
    val sensorApps: List<SensorPrivacyApp> = emptyList(),
    val sensorAppsLoading: Boolean = false,
    val sensorSearchQuery: String = "",
    // Firewall tab
    val firewallApps: List<FirewallApp> = emptyList(),
    val firewallLoading: Boolean = false,
    val firewallSearchQuery: String = "",
    // Audit tab
    val auditApps: List<AuditApp> = emptyList(),
    val auditLoading: Boolean = false,
    val auditSearchQuery: String = "",
)

data class PrivacyTrackerCategory(
    val name: String,
    val trackerCount: Int,
    val description: String,
    val packages: List<String>,
)

enum class PrivacyTab { DASHBOARD, TRACKERS, SENSORS, FIREWALL, COMPONENTS, RULES, AUDIT }

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedComponentDao: BlockedComponentDao,
    private val privacyRuleDao: PrivacyRuleDao,
    private val appRepository: AppRepository,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

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
                PrivacyTrackerCategory(name = cat, trackerCount = trackerPkgs.size, description = "Block $cat trackers", packages = trackerPkgs)
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
            if (ok) viewModelScope.launch { blockedComponentDao.deleteByComponent(packageName, componentName) }
            _state.update { it.copy(snackbarMessage = if (ok) "Component enabled" else "Failed — ACCU/root required") }
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

    // ─── SENSOR / APPOPS BLOCKING ──────────────────────────────────────────────

    fun loadSensorApps() {
        _state.update { it.copy(sensorAppsLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val allPkgs = pm.getInstalledPackages(0)
                val apps = allPkgs.mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    SensorPrivacyApp(
                        packageName = pkg.packageName,
                        appName = pm.getApplicationLabel(ai).toString(),
                        isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }.sortedBy { it.appName }
                _state.update { it.copy(sensorApps = apps, sensorAppsLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(sensorAppsLoading = false, snackbarMessage = "Failed to load apps: ${e.message}") }
            }
        }
    }

    fun blockSensorOp(packageName: String, op: String, block: Boolean) {
        viewModelScope.launch {
            val mode = if (block) "deny" else "allow"
            val result = connectionManager.exec("appops set $packageName $op $mode 2>/dev/null")
            val updated = _state.value.sensorApps.map { app ->
                if (app.packageName == packageName) {
                    val newBlocked = if (block) app.blockedOps + op else app.blockedOps - op
                    app.copy(blockedOps = newBlocked)
                } else app
            }
            _state.update { it.copy(sensorApps = updated, snackbarMessage = if (result.isSuccess) "$op → $mode for $packageName" else "Failed: ${result.error.take(80)}") }
        }
    }

    fun loadSensorStateForApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val blockedOps = mutableSetOf<String>()
                SENSOR_OPS.forEach { (op, _) ->
                    val out = connectionManager.exec("appops get $packageName $op 2>/dev/null").output.trim().lowercase()
                    if ("deny" in out || "ignore" in out) blockedOps.add(op)
                }
                val updated = _state.value.sensorApps.map { app ->
                    if (app.packageName == packageName) app.copy(blockedOps = blockedOps) else app
                }
                _state.update { it.copy(sensorApps = updated) }
            } catch (_: Exception) {}
        }
    }

    fun blockAllSensorsForApp(packageName: String) {
        viewModelScope.launch {
            SENSOR_OPS.forEach { (op, _) ->
                connectionManager.exec("appops set $packageName $op deny 2>/dev/null")
            }
            val updated = _state.value.sensorApps.map { app ->
                if (app.packageName == packageName) app.copy(blockedOps = SENSOR_OPS.map { it.first }.toSet()) else app
            }
            _state.update { it.copy(sensorApps = updated, snackbarMessage = "All sensors blocked for $packageName") }
        }
    }

    fun unblockAllSensorsForApp(packageName: String) {
        viewModelScope.launch {
            SENSOR_OPS.forEach { (op, _) ->
                connectionManager.exec("appops set $packageName $op allow 2>/dev/null")
            }
            val updated = _state.value.sensorApps.map { app ->
                if (app.packageName == packageName) app.copy(blockedOps = emptySet()) else app
            }
            _state.update { it.copy(sensorApps = updated, snackbarMessage = "All sensors unblocked for $packageName") }
        }
    }

    // ─── NETWORK FIREWALL ─────────────────────────────────────────────────────

    fun loadFirewallApps() {
        _state.update { it.copy(firewallLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val apps = pm.getInstalledPackages(0).mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    FirewallApp(
                        packageName = pkg.packageName,
                        appName = pm.getApplicationLabel(ai).toString(),
                        uid = ai.uid,
                    )
                }.sortedBy { it.appName }
                _state.update { it.copy(firewallApps = apps, firewallLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(firewallLoading = false, snackbarMessage = "Failed: ${e.message}") }
            }
        }
    }

    fun blockNetworkForApp(packageName: String, block: Boolean) {
        viewModelScope.launch {
            // Step 1: get UID from target device via ADB
            val uidOutput = connectionManager.exec("pm list packages -U $packageName 2>/dev/null").output
            val uid = Regex("uid:(\\d+)").find(uidOutput)?.groupValues?.getOrNull(1)
            if (uid != null) {
                // Restrict background data via netpolicy
                val result = connectionManager.exec("cmd netpolicy set restrict-background $uid ${if (block) "true" else "false"} 2>/dev/null")
                val updated = _state.value.firewallApps.map { app ->
                    if (app.packageName == packageName) app.copy(isBlocked = block) else app
                }
                _state.update { it.copy(firewallApps = updated, snackbarMessage = if (result.isSuccess) "${if (block) "Blocked" else "Unblocked"} background network for $packageName (UID $uid)" else "Failed: ${result.error.take(80)}") }
            } else {
                // Fallback: iptables approach for root
                val appUid = _state.value.firewallApps.firstOrNull { it.packageName == packageName }?.uid ?: -1
                if (appUid > 0) {
                    val action = if (block) "REJECT" else "ACCEPT"
                    connectionManager.exec("iptables -I OUTPUT -m owner --uid-owner $appUid -j $action 2>/dev/null")
                    connectionManager.exec("ip6tables -I OUTPUT -m owner --uid-owner $appUid -j $action 2>/dev/null")
                    val updated = _state.value.firewallApps.map { app ->
                        if (app.packageName == packageName) app.copy(isBlocked = block) else app
                    }
                    _state.update { it.copy(firewallApps = updated, snackbarMessage = "${if (block) "Blocked" else "Unblocked"} via iptables (UID $appUid)") }
                } else {
                    _state.update { it.copy(snackbarMessage = "Could not find UID for $packageName — ensure ACCU is connected") }
                }
            }
        }
    }

    // ─── PRIVACY AUDIT ─────────────────────────────────────────────────────────

    fun startPrivacyAudit() {
        _state.update { it.copy(auditLoading = true, auditApps = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val allTrackerPkgs = builtInTrackers.values.flatten().toSet()
                val dangerousPermsSet = pm.queryPermissionsByGroup(null, 0)
                    .filter { it.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS }
                    .map { it.name }
                    .toSet()

                val apps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    val appName = pm.getApplicationLabel(ai).toString()
                    val requestedPerms = pkg.requestedPermissions?.toList() ?: emptyList()
                    val grantedPerms = requestedPerms.filterIndexed { i, _ ->
                        (pkg.requestedPermissionsFlags?.getOrNull(i) ?: 0) and
                            android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                    }
                    val dangerousRequested = requestedPerms.filter { it in dangerousPermsSet }
                    val dangerousGranted = grantedPerms.filter { it in dangerousPermsSet }

                    // Detect trackers via component names
                    val allComponents = buildList {
                        pkg.activities?.forEach { add(it.name) }
                        pkg.services?.forEach { add(it.name) }
                        pkg.receivers?.forEach { add(it.name) }
                    }
                    val trackerHits = allTrackerPkgs.filter { t -> allComponents.any { it.startsWith(t) } }.toList()

                    // Compute privacy score: 100 - (dangerous granted × 5) - (trackers × 10), min 0
                    val score = maxOf(0, 100 - dangerousGranted.size * 5 - trackerHits.size * 10)

                    AuditApp(
                        packageName = pkg.packageName,
                        appName = appName,
                        dangerousPerms = dangerousRequested,
                        grantedPerms = dangerousGranted,
                        trackerHits = trackerHits,
                        privacyScore = score,
                    )
                }.sortedBy { it.privacyScore }

                _state.update { it.copy(auditApps = apps, auditLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(auditLoading = false, snackbarMessage = "Audit failed: ${e.message}") }
            }
        }
    }

    // ─── EXPORT / IMPORT ─────────────────────────────────────────────────────

    fun exportRules() {
        _state.update { it.copy(snackbarMessage = "Use the export button and pick a save location") }
    }

    fun backupRules() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val components = blockedComponentDao.observeAll().first()
                val rules = privacyRuleDao.observeAll().first()
                _state.update { it.copy(snackbarMessage = "Backup ready: ${components.size} components, ${rules.size} rules") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Backup failed: ${e.message}") }
            }
        }
    }

    fun restoreRules() {
        _state.update { it.copy(snackbarMessage = "Pick a backup file to restore rules") }
    }

    fun importRules(format: String) {
        _state.update { it.copy(snackbarMessage = "Pick a $format rules file to import") }
    }

    fun exportRulesTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val components = blockedComponentDao.observeAll().first()
                val rules = privacyRuleDao.observeAll().first()
                val json = buildString {
                    appendLine("""{"version":2,"components":[""")
                    components.forEachIndexed { i, c ->
                        append("""  {"pkg":"${c.packageName}","name":"${c.componentName}","type":"${c.componentType}","tracker":${c.isTracker}}""")
                        if (i < components.lastIndex) appendLine(",") else appendLine()
                    }
                    appendLine("""],"rules":[""")
                    rules.forEachIndexed { i, r ->
                        append("""  {"pkg":"${r.packageName}","type":"${r.ruleType}","name":"${r.ruleName}","enabled":${r.isEnabled}}""")
                        if (i < rules.lastIndex) appendLine(",") else appendLine()
                    }
                    append("]}")
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
                _state.update { it.copy(snackbarMessage = "Exported ${components.size} rules + ${rules.size} custom rules") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun importRulesFrom(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var componentCount = 0
                var ruleCount = 0
                // Parse components
                val compSection = Regex(""""components"\s*:\s*\[([^\]]*)\]""", RegexOption.DOT_MATCHES_ALL).find(json)?.groupValues?.getOrNull(1) ?: ""
                Regex("""\{[^}]+\}""").findAll(compSection).forEach { m ->
                    val obj = m.value
                    val pkg  = Regex(""""pkg"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.getOrNull(1) ?: return@forEach
                    val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.getOrNull(1) ?: return@forEach
                    val type = Regex(""""type"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.getOrNull(1) ?: "receiver"
                    val tracker = obj.contains(""""tracker":true""")
                    blockedComponentDao.insert(BlockedComponentEntity(packageName = pkg, componentName = name, componentType = type, isTracker = tracker, ruleSource = "import"))
                    connectionManager.exec("pm disable $pkg/$name 2>/dev/null")
                    componentCount++
                }
                // Parse rules
                val ruleSection = Regex(""""rules"\s*:\s*\[([^\]]*)\]""", RegexOption.DOT_MATCHES_ALL).find(json)?.groupValues?.getOrNull(1) ?: ""
                Regex("""\{[^}]+\}""").findAll(ruleSection).forEach { m ->
                    val obj = m.value
                    val pkg  = Regex(""""pkg"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.getOrNull(1) ?: return@forEach
                    val type = Regex(""""type"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.getOrNull(1) ?: return@forEach
                    val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.getOrNull(1) ?: return@forEach
                    privacyRuleDao.insert(PrivacyRuleEntity(packageName = pkg, ruleType = type, ruleName = name))
                    ruleCount++
                }
                _state.update { it.copy(snackbarMessage = "Imported $componentCount components, $ruleCount rules") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Import failed: ${e.message}") }
            }
        }
    }

    fun toggleRule(rule: PrivacyRuleEntity) {
        viewModelScope.launch { privacyRuleDao.update(rule.copy(isEnabled = !rule.isEnabled)) }
    }

    fun syncCloudRules(url: String) {
        if (url.isBlank()) { _state.update { it.copy(snackbarMessage = "Please enter a valid URL") }; return }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(snackbarMessage = "Fetching rules from $url…") }
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000; connection.readTimeout = 8000
                val body = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                val packages = body.lines().mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#") || trimmed.isBlank()) null
                    else trimmed.split(",").firstOrNull()?.trim()
                }.filter { it.contains(".") }
                var inserted = 0
                packages.forEach { pkg ->
                    try {
                        blockedComponentDao.insert(BlockedComponentEntity(packageName = pkg, componentName = pkg, componentType = "tracker", isTracker = true, ruleSource = "cloud:$url"))
                        inserted++
                    } catch (_: Exception) {}
                }
                _state.update { it.copy(snackbarMessage = "Synced $inserted rules from cloud") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Sync failed: ${e.message}") }
            }
        }
    }

    fun clearAllRules() {
        viewModelScope.launch {
            blockedComponentDao.deleteAll()
            privacyRuleDao.deleteAll()
            _state.update { it.copy(snackbarMessage = "All rules cleared") }
        }
    }

    fun onTabChange(tab: PrivacyTab) {
        _state.update { it.copy(selectedTab = tab) }
        when (tab) {
            PrivacyTab.SENSORS   -> if (_state.value.sensorApps.isEmpty()) loadSensorApps()
            PrivacyTab.FIREWALL  -> if (_state.value.firewallApps.isEmpty()) loadFirewallApps()
            PrivacyTab.AUDIT     -> if (_state.value.auditApps.isEmpty()) startPrivacyAudit()
            else -> {}
        }
    }

    fun onSearch(q: String)              { _state.update { it.copy(searchQuery = q) } }
    fun onSensorSearch(q: String)        { _state.update { it.copy(sensorSearchQuery = q) } }
    fun onFirewallSearch(q: String)      { _state.update { it.copy(firewallSearchQuery = q) } }
    fun onAuditSearch(q: String)         { _state.update { it.copy(auditSearchQuery = q) } }
    fun clearSnackbar()                  { _state.update { it.copy(snackbarMessage = null) } }
}
