package com.accu.ui.privacy

import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// ── AppOps sensor ops ─────────────────────────────────────────────────────────
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
    "GET_ACCOUNTS"                to "Accounts",
    "READ_PHONE_STATE"            to "Phone State",
    "READ_EXTERNAL_STORAGE"       to "Storage (Read)",
    "WRITE_EXTERNAL_STORAGE"      to "Storage (Write)",
    "PROCESS_OUTGOING_CALLS"      to "Outgoing Calls",
    "READ_CALENDAR"               to "Calendar",
    "SYSTEM_ALERT_WINDOW"         to "Draw Over Apps",
    "WRITE_SETTINGS"              to "Write Settings",
)

// ── Tracker SDK database — package-name prefixes ───────────────────────────────
val TRACKER_DB: Map<String, List<String>> = mapOf(
    "Analytics" to listOf(
        "com.google.firebase.analytics", "com.amplitude.api", "com.segment",
        "com.mixpanel.android", "com.flurry.android", "com.appsflyer",
        "io.branch.referral", "com.heap", "com.clevertap.android",
        "com.adjust.sdk", "com.braze", "com.leanplum",
        "com.moe.pushlibrary", "com.adobe.mobile", "com.localytics.android",
        "ly.count.android", "com.smartlook", "com.instana",
        "com.newrelic.agent", "com.dynatrace.android", "com.datadog.android",
        "com.countly", "com.posthog", "com.rudderstack",
    ),
    "Advertising" to listOf(
        "com.google.android.gms.ads", "com.facebook.ads", "com.unity3d.ads",
        "com.applovin", "com.mopub", "com.chartboost", "com.ironsource.mediationsdk",
        "com.vungle.warren", "com.inmobi.ads", "com.startapp", "com.tapjoy",
        "com.revmob", "com.adcolony", "com.smaato", "com.fyber",
        "com.ogury.ad", "com.bytedance.sdk", "com.bigo.ad", "com.digitalturbine",
        "com.criteo.publisher", "com.mintegral", "com.yandex.mobile.ads",
        "com.appnexus", "com.pubmatic", "com.amazon.advertising",
        "com.mobvista", "com.maio", "com.adikteev",
    ),
    "Social SDKs" to listOf(
        "com.facebook.share", "com.facebook.login", "com.facebook.applinks",
        "com.facebook.FacebookSdk", "com.twitter.sdk", "com.linkedin.android",
        "com.pinterest", "com.reddit.devplatform", "com.snap.sdk",
    ),
    "Crash Reporting" to listOf(
        "com.bugsnag.android", "com.instabug.library", "io.sentry.android",
        "com.rollbar.android", "com.google.firebase.crashlytics",
        "io.embrace.android", "com.datadog.android.error",
        "com.microsoft.appcenter.crashes", "com.kochava",
        "com.bugfender", "com.raygun", "com.airbrake",
    ),
    "Profiling / Session" to listOf(
        "com.contentsquare", "com.hotjar", "com.mouseflow", "com.fullstory",
        "com.appsee", "com.uxcam", "com.microsoft.clarity",
        "com.decibelinsight", "com.glassbox", "com.quantum_metric",
    ),
    "Location / Geo" to listOf(
        "com.foursquare", "com.xmode", "com.safegraph", "com.tamoco",
        "io.radar.sdk", "com.gimbal", "com.factual", "com.bountyhunter",
    ),
    "Attribution" to listOf(
        "com.appsflyer", "com.adjust.sdk", "com.kochava.base",
        "com.singular.sdk", "io.branch.referral", "com.trafficguard",
        "com.tune", "com.skadnetwork", "com.tenjin",
    ),
    "Identity / Fingerprinting" to listOf(
        "com.deviceid", "com.trusteer", "com.threatmetrix",
        "com.iovation", "com.precognitive", "com.neuro_id",
    ),
)

// ── Data classes ───────────────────────────────────────────────────────────────

/** App on the CONNECTED TARGET device — loaded via ADB. */
data class TargetApp(
    val packageName: String,
    val displayName: String,    // derived from package name
    val uid: Int = -1,
    val isSystem: Boolean = false,
    val versionName: String = "",
    val installerPkg: String = "",
)

data class SensorPrivacyApp(
    val packageName: String,
    val displayName: String,
    val isSystem: Boolean,
    val blockedOps: Set<String> = emptySet(),
    val checkedOps: Boolean = false,
)

data class FirewallApp(
    val packageName: String,
    val displayName: String,
    val uid: Int = -1,
    val isBlocked: Boolean = false,
    val isSystem: Boolean = false,
)

data class AuditApp(
    val packageName: String,
    val displayName: String,
    val grantedDangerousPerms: List<String> = emptyList(),
    val trackerHits: List<String> = emptyList(),
    val isSystem: Boolean = false,
    val privacyScore: Int = 100,
)

data class PrivacyTrackerCategory(
    val name: String,
    val knownSdkCount: Int,
    val trackerCount: Int,
    val packages: List<String>,
    val foundPackages: List<String>,
)

/** Boot-time auto-start receiver on the target device. */
data class BootReceiver(
    val packageName: String,
    val componentClass: String,
    val displayName: String,
    val isDisabled: Boolean = false,
)

enum class SecurityKind { ACCESSIBILITY, DEVICE_ADMIN, NOTIFICATION_LISTENER }

/** Accessibility service / device admin / notification listener on target. */
data class SecurityEntry(
    val componentName: String,
    val packageName: String,
    val displayName: String,
    val kind: SecurityKind,
    val isEnabled: Boolean = true,
)

/** Live network connection on the target device. */
data class NetConn(
    val proto: String,
    val localAddress: String,
    val remoteAddress: String,
    val state: String,
    val uid: Int = -1,
    val packageName: String = "",
    val process: String = "",
)

enum class PrivacyTab {
    DASHBOARD, TRACKERS, SENSORS, FIREWALL, BOOT, SECURITY, NETWORK, COMPONENTS, RULES, AUDIT
}

data class PrivacyUiState(
    // DB-backed
    val blockedComponents: List<BlockedComponentEntity> = emptyList(),
    val privacyRules: List<PrivacyRuleEntity> = emptyList(),
    val blockedCount: Int = 0,
    val isLoading: Boolean = true,
    // Navigation
    val selectedTab: PrivacyTab = PrivacyTab.DASHBOARD,
    val snackbarMessage: String? = null,
    // Shared target app cache
    val targetApps: List<TargetApp> = emptyList(),
    val targetAppsLoading: Boolean = false,
    // Trackers
    val trackerCategories: List<PrivacyTrackerCategory> = emptyList(),
    val trackerCount: Int = 0,
    val trackerScanDone: Boolean = false,
    // Sensors
    val sensorApps: List<SensorPrivacyApp> = emptyList(),
    val sensorAppsLoading: Boolean = false,
    val sensorSearchQuery: String = "",
    val sensorScanInProgress: Boolean = false,
    // Firewall
    val firewallApps: List<FirewallApp> = emptyList(),
    val firewallLoading: Boolean = false,
    val firewallSearchQuery: String = "",
    // Boot
    val bootReceivers: List<BootReceiver> = emptyList(),
    val bootLoading: Boolean = false,
    val bootSearchQuery: String = "",
    // Security services
    val securityEntries: List<SecurityEntry> = emptyList(),
    val securityLoading: Boolean = false,
    // Network
    val netConnections: List<NetConn> = emptyList(),
    val netLoading: Boolean = false,
    val netAutoRefresh: Boolean = false,
    // Audit
    val auditApps: List<AuditApp> = emptyList(),
    val auditLoading: Boolean = false,
    val auditSearchQuery: String = "",
)

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Derive a human-readable label from a package name. */
fun String.toDisplayName(): String =
    split(".").filter { seg -> seg.length > 2 && !seg.all { it.isDigit() } }
              .lastOrNull()
              ?.replaceFirstChar { it.uppercase() }
    ?: this

private val DANGEROUS_PERMS = setOf(
    "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR",
    "android.permission.CAMERA",
    "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.GET_ACCOUNTS",
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.RECORD_AUDIO",
    "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS",
    "android.permission.CALL_PHONE", "android.permission.ANSWER_PHONE_CALLS",
    "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
    "android.permission.ADD_VOICEMAIL", "android.permission.USE_SIP",
    "android.permission.PROCESS_OUTGOING_CALLS",
    "android.permission.BODY_SENSORS", "android.permission.BODY_SENSORS_BACKGROUND",
    "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS", "android.permission.READ_SMS",
    "android.permission.RECEIVE_WAP_PUSH", "android.permission.RECEIVE_MMS",
    "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT",
    "android.permission.UWB_RANGING",
    "android.permission.NEARBY_WIFI_DEVICES",
    "android.permission.POST_NOTIFICATIONS",
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

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

    init {
        observeBlockedComponents()
        observePrivacyRules()
        loadTargetAppsAndScan()
    }

    private fun observeBlockedComponents() {
        viewModelScope.launch {
            blockedComponentDao.observeAll().collect { list ->
                _state.update { it.copy(blockedComponents = list, blockedCount = list.size, isLoading = false) }
            }
        }
    }

    private fun observePrivacyRules() {
        viewModelScope.launch {
            privacyRuleDao.observeAll().collect { rules -> _state.update { it.copy(privacyRules = rules) } }
        }
    }

    // ── TARGET DEVICE APP LIST ─────────────────────────────────────────────────
    // All data originates here — loaded once via ADB and reused by every tab.

    fun loadTargetAppsAndScan() {
        _state.update { it.copy(targetAppsLoading = true, trackerScanDone = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // pm list packages -U → "package:com.pkg uid:10050"  (Android 7+)
                val rawU = connectionManager.exec("pm list packages -U 2>/dev/null").output
                // pm list packages -3 → third-party only
                val thirdPartyRaw = connectionManager.exec("pm list packages -3 2>/dev/null").output
                val thirdPartySet = thirdPartyRaw.lines()
                    .mapNotNull { Regex("""package:(.+)""").find(it.trim())?.groupValues?.getOrNull(1)?.trim() }
                    .toSet()

                val pkgUidRe = Regex("""package:([^\s]+)\s+uid:(\d+)""")
                val pkgOnlyRe = Regex("""package:([^\s]+)""")

                // Parse package+uid, fallback to package-only if -U not supported
                val apps: List<TargetApp> = if (pkgUidRe.containsMatchIn(rawU)) {
                    pkgUidRe.findAll(rawU).map { m ->
                        val pkg = m.groupValues[1].trim()
                        val uid = m.groupValues[2].toIntOrNull() ?: -1
                        TargetApp(pkg, pkg.toDisplayName(), uid, pkg !in thirdPartySet)
                    }.toList()
                } else {
                    // Fallback: parse plain package list, get UIDs separately
                    val rawPlain = connectionManager.exec("pm list packages 2>/dev/null").output
                    pkgOnlyRe.findAll(rawPlain).map { m ->
                        val pkg = m.groupValues[1].trim()
                        TargetApp(pkg, pkg.toDisplayName(), -1, pkg !in thirdPartySet)
                    }.toList()
                }

                val sorted = apps.sortedBy { it.displayName }
                _state.update { it.copy(targetApps = sorted, targetAppsLoading = false) }

                // Immediately run tracker scan on the loaded app list
                doTrackerScan(sorted)
            } catch (e: Exception) {
                Timber.e(e)
                _state.update { it.copy(targetAppsLoading = false, trackerScanDone = true, snackbarMessage = "Failed to load target apps: ${e.message?.take(80)}") }
            }
        }
    }

    // ── TRACKER SCAN ──────────────────────────────────────────────────────────
    // Cross-references the target's installed package names with TRACKER_DB.

    private fun doTrackerScan(apps: List<TargetApp>) {
        viewModelScope.launch(Dispatchers.IO) {
            val pkgNames = apps.map { it.packageName }.toSet()
            val categories = TRACKER_DB.map { (catName, knownPrefixes) ->
                val found = knownPrefixes.filter { prefix ->
                    pkgNames.any { it.startsWith(prefix) || it == prefix }
                }
                PrivacyTrackerCategory(
                    name          = catName,
                    knownSdkCount = knownPrefixes.size,
                    trackerCount  = found.size,
                    packages      = knownPrefixes,
                    foundPackages = found,
                )
            }
            val total = categories.sumOf { it.trackerCount }
            _state.update { it.copy(trackerCategories = categories, trackerCount = total, trackerScanDone = true) }
        }
    }

    fun scanInstalledTrackers() {
        val apps = _state.value.targetApps
        if (apps.isEmpty()) { loadTargetAppsAndScan(); return }
        _state.update { it.copy(trackerScanDone = false) }
        doTrackerScan(apps)
    }

    fun blockTrackersInCategory(category: String) {
        viewModelScope.launch {
            val cat = _state.value.trackerCategories.firstOrNull { it.name == category } ?: return@launch
            val pkgsToBlock = if (cat.foundPackages.isNotEmpty()) cat.foundPackages else cat.packages
            var blocked = 0
            pkgsToBlock.forEach { pkg ->
                try {
                    blockedComponentDao.insert(BlockedComponentEntity(
                        packageName = pkg, componentName = pkg,
                        componentType = "tracker", isTracker = true, ruleSource = "built_in",
                    ))
                    connectionManager.exec("pm disable $pkg 2>/dev/null")
                    blocked++
                } catch (e: Exception) { Timber.e(e) }
            }
            _state.update { it.copy(snackbarMessage = "Blocked $blocked tracker(s) in $category") }
        }
    }

    // ── SENSORS / APPOPS ──────────────────────────────────────────────────────
    // Uses the cached target app list. All appops via connectionManager.exec().

    fun loadSensorApps() {
        _state.update { it.copy(sensorAppsLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val source = _state.value.targetApps.ifEmpty {
                // Force load if cache empty
                val rawU = connectionManager.exec("pm list packages -U 2>/dev/null").output
                val re = Regex("""package:([^\s]+)\s+uid:(\d+)""")
                re.findAll(rawU).map { TargetApp(it.groupValues[1], it.groupValues[1].toDisplayName(), it.groupValues[2].toIntOrNull() ?: -1) }.toList()
            }
            val apps = source.map { SensorPrivacyApp(it.packageName, it.displayName, it.isSystem) }
            _state.update { it.copy(sensorApps = apps, sensorAppsLoading = false) }
        }
    }

    /** Batch-scan camera/mic/location ops for ALL apps at once via appops query-op. */
    fun scanAllSensorOps() {
        _state.update { it.copy(sensorScanInProgress = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val criticalOps = listOf("CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_SMS", "READ_CALL_LOG")
            val blockedMap = mutableMapOf<String, MutableSet<String>>()
            try {
                criticalOps.forEach { op ->
                    val out = connectionManager.exec("appops query-op $op deny 2>/dev/null").output
                    out.lines().forEach { line ->
                        val pkg = line.trim()
                        if (pkg.isNotBlank() && !pkg.startsWith("Package") && pkg.contains("."))
                            blockedMap.getOrPut(pkg) { mutableSetOf() }.add(op)
                    }
                    val out2 = connectionManager.exec("appops query-op $op ignore 2>/dev/null").output
                    out2.lines().forEach { line ->
                        val pkg = line.trim()
                        if (pkg.isNotBlank() && !pkg.startsWith("Package") && pkg.contains("."))
                            blockedMap.getOrPut(pkg) { mutableSetOf() }.add(op)
                    }
                }
            } catch (_: Exception) {}
            val updated = _state.value.sensorApps.map { app ->
                val blocked = blockedMap[app.packageName]
                app.copy(blockedOps = blocked ?: app.blockedOps, checkedOps = true)
            }
            _state.update { it.copy(sensorApps = updated, sensorScanInProgress = false) }
        }
    }

    fun loadSensorStateForApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val blocked = mutableSetOf<String>()
                SENSOR_OPS.forEach { (op, _) ->
                    val out = connectionManager.exec("appops get $packageName $op 2>/dev/null").output.trim().lowercase()
                    if ("deny" in out || "ignore" in out) blocked.add(op)
                }
                val updated = _state.value.sensorApps.map { if (it.packageName == packageName) it.copy(blockedOps = blocked, checkedOps = true) else it }
                _state.update { it.copy(sensorApps = updated) }
            } catch (_: Exception) {}
        }
    }

    fun blockSensorOp(packageName: String, op: String, block: Boolean) {
        viewModelScope.launch {
            val mode = if (block) "deny" else "allow"
            val result = connectionManager.exec("appops set $packageName $op $mode 2>/dev/null")
            val updated = _state.value.sensorApps.map { app ->
                if (app.packageName != packageName) app
                else app.copy(blockedOps = if (block) app.blockedOps + op else app.blockedOps - op, checkedOps = true)
            }
            _state.update { it.copy(sensorApps = updated, snackbarMessage = if (result.isSuccess) "$op → $mode ✓" else "Failed: ${result.error.take(60)}") }
        }
    }

    fun blockAllSensorsForApp(packageName: String) {
        viewModelScope.launch {
            SENSOR_OPS.forEach { (op, _) -> connectionManager.exec("appops set $packageName $op deny 2>/dev/null") }
            val all = SENSOR_OPS.map { it.first }.toSet()
            val updated = _state.value.sensorApps.map { if (it.packageName == packageName) it.copy(blockedOps = all, checkedOps = true) else it }
            _state.update { it.copy(sensorApps = updated, snackbarMessage = "All ${SENSOR_OPS.size} ops blocked for $packageName") }
        }
    }

    fun unblockAllSensorsForApp(packageName: String) {
        viewModelScope.launch {
            SENSOR_OPS.forEach { (op, _) -> connectionManager.exec("appops set $packageName $op allow 2>/dev/null") }
            val updated = _state.value.sensorApps.map { if (it.packageName == packageName) it.copy(blockedOps = emptySet(), checkedOps = true) else it }
            _state.update { it.copy(sensorApps = updated, snackbarMessage = "All ops allowed for $packageName") }
        }
    }

    // ── NETWORK FIREWALL ──────────────────────────────────────────────────────
    // App list from TARGET via ADB. Firewall state from real cmd netpolicy.

    fun loadFirewallApps() {
        _state.update { it.copy(firewallLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use cached target apps; fall back to fresh ADB query
                val source = _state.value.targetApps.ifEmpty {
                    val rawU = connectionManager.exec("pm list packages -U 2>/dev/null").output
                    val re = Regex("""package:([^\s]+)\s+uid:(\d+)""")
                    re.findAll(rawU).map { TargetApp(it.groupValues[1], it.groupValues[1].toDisplayName(), it.groupValues[2].toIntOrNull() ?: -1) }.toList()
                }

                // Real netpolicy state from target
                val policyOut = connectionManager.exec("cmd netpolicy list uid-rules 2>/dev/null").output
                val blockedUids = mutableSetOf<Int>()
                policyOut.lines().forEach { line ->
                    val m = Regex("""UID\s+(\d+).*REJECT""", RegexOption.IGNORE_CASE).find(line)
                    m?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { blockedUids.add(it) }
                }
                // iptables fallback for rooted targets
                val ipOut = connectionManager.exec("iptables -L OUTPUT -n 2>/dev/null | grep 'owner UID match'").output
                Regex("""owner UID match (\d+)""").findAll(ipOut)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .forEach { blockedUids.add(it) }

                val apps = source.map { app ->
                    FirewallApp(app.packageName, app.displayName, app.uid, app.uid in blockedUids, app.isSystem)
                }
                _state.update { it.copy(firewallApps = apps, firewallLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(firewallLoading = false, snackbarMessage = "Firewall load failed: ${e.message?.take(60)}") }
            }
        }
    }

    fun blockNetworkForApp(packageName: String, block: Boolean) {
        viewModelScope.launch {
            val uidOut = connectionManager.exec("pm list packages -U $packageName 2>/dev/null").output
            val uid = Regex("""uid:(\d+)""").find(uidOut)?.groupValues?.getOrNull(1)
                ?: _state.value.firewallApps.firstOrNull { it.packageName == packageName }?.uid?.takeIf { it > 0 }?.toString()
            if (uid != null) {
                val result = connectionManager.exec("cmd netpolicy set restrict-background $uid ${if (block) "true" else "false"} 2>/dev/null")
                val updated = _state.value.firewallApps.map { if (it.packageName == packageName) it.copy(isBlocked = block) else it }
                _state.update { it.copy(firewallApps = updated, snackbarMessage = if (result.isSuccess) "${if (block) "Blocked" else "Unblocked"} background network for $packageName (UID $uid)" else "Failed: ${result.error.take(60)}") }
            } else {
                // iptables root fallback
                val appUid = _state.value.firewallApps.firstOrNull { it.packageName == packageName }?.uid ?: -1
                if (appUid > 0) {
                    val del = if (!block) " -D" else ""
                    connectionManager.exec("iptables${del} -I OUTPUT -m owner --uid-owner $appUid -j ${if (block) "REJECT" else "ACCEPT"} 2>/dev/null")
                    connectionManager.exec("ip6tables${del} -I OUTPUT -m owner --uid-owner $appUid -j ${if (block) "REJECT" else "ACCEPT"} 2>/dev/null")
                    val updated = _state.value.firewallApps.map { if (it.packageName == packageName) it.copy(isBlocked = block) else it }
                    _state.update { it.copy(firewallApps = updated, snackbarMessage = "${if (block) "Blocked" else "Unblocked"} via iptables (UID $appUid)") }
                } else _state.update { it.copy(snackbarMessage = "Could not find UID — is ACCU connected?") }
            }
        }
    }

    // ── BOOT AUTO-START ───────────────────────────────────────────────────────
    // Real: pm query-receivers -a android.intent.action.BOOT_COMPLETED

    fun loadBootReceivers() {
        _state.update { it.copy(bootLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val out = connectionManager.exec("pm query-receivers -a android.intent.action.BOOT_COMPLETED 2>/dev/null").output
                // Extract component names: "com.pkg/com.pkg.ReceiverClass"
                val compRe = Regex("""([a-z][a-zA-Z0-9._]+)/([a-zA-Z0-9._$]+)""")
                val receivers = compRe.findAll(out).map { m ->
                    val pkg   = m.groupValues[1]
                    val clazz = m.groupValues[2]
                    BootReceiver(
                        packageName   = pkg,
                        componentClass = clazz,
                        displayName   = pkg.toDisplayName(),
                    )
                }.distinctBy { "${it.packageName}/${it.componentClass}" }.sortedBy { it.displayName }.toList()

                // Check disabled status
                val withStatus = receivers.map { r ->
                    val info = connectionManager.exec("pm dump ${r.packageName} 2>/dev/null | grep -A 1 '${r.componentClass.substringAfterLast('.')}'").output
                    r.copy(isDisabled = info.contains("enabled=false", ignoreCase = true))
                }
                _state.update { it.copy(bootReceivers = withStatus, bootLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(bootLoading = false, snackbarMessage = "Boot scan failed: ${e.message?.take(60)}") }
            }
        }
    }

    fun toggleBootReceiver(receiver: BootReceiver, disable: Boolean) {
        viewModelScope.launch {
            val cmd = if (disable) "pm disable ${receiver.packageName}/${receiver.componentClass}"
                      else         "pm enable ${receiver.packageName}/${receiver.componentClass}"
            val result = connectionManager.exec("$cmd 2>/dev/null")
            val updated = _state.value.bootReceivers.map { if (it.componentClass == receiver.componentClass && it.packageName == receiver.packageName) it.copy(isDisabled = disable) else it }
            _state.update { it.copy(bootReceivers = updated, snackbarMessage = if (result.isSuccess) "${if (disable) "Disabled" else "Enabled"} ${receiver.componentClass.substringAfterLast('.')} ✓" else "Failed (root/ADB needed): ${result.error.take(50)}") }
        }
    }

    // ── SECURITY SERVICES ─────────────────────────────────────────────────────
    // Accessibility services, device admins, notification listeners — all real.

    fun loadSecurityServices() {
        _state.update { it.copy(securityLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val entries = mutableListOf<SecurityEntry>()

            try {
                // 1. Accessibility services
                val accRaw = connectionManager.exec("settings get secure enabled_accessibility_services 2>/dev/null").output.trim()
                if (accRaw.isNotBlank() && accRaw != "null" && accRaw != "") {
                    accRaw.split(":").forEach { comp ->
                        val t = comp.trim()
                        if (t.isBlank()) return@forEach
                        val parts = t.split("/")
                        val pkg   = parts.getOrElse(0) { t }
                        val clazz = parts.getOrElse(1) { "" }
                        entries.add(SecurityEntry(t, pkg, pkg.toDisplayName(), SecurityKind.ACCESSIBILITY, true))
                    }
                }

                // 2. Installed accessibility services (all, not just enabled)
                val accInstalled = connectionManager.exec("dumpsys accessibility 2>/dev/null | grep 'packageName'").output
                Regex("""packageName=([a-z][a-zA-Z0-9._]+)""").findAll(accInstalled).forEach { m ->
                    val pkg = m.groupValues[1]
                    if (entries.none { it.packageName == pkg && it.kind == SecurityKind.ACCESSIBILITY }) {
                        entries.add(SecurityEntry(pkg, pkg, pkg.toDisplayName(), SecurityKind.ACCESSIBILITY, false))
                    }
                }

                // 3. Device admin apps
                val adminRaw = connectionManager.exec("dumpsys device_policy 2>/dev/null | grep -E 'ComponentInfo|admin:'").output
                Regex("""ComponentInfo\{([a-z][a-zA-Z0-9._]+)/([a-zA-Z0-9._$]+)\}""").findAll(adminRaw).forEach { m ->
                    val pkg   = m.groupValues[1]
                    val clazz = m.groupValues[2]
                    val comp  = "$pkg/$clazz"
                    if (entries.none { it.componentName == comp && it.kind == SecurityKind.DEVICE_ADMIN }) {
                        entries.add(SecurityEntry(comp, pkg, pkg.toDisplayName(), SecurityKind.DEVICE_ADMIN, true))
                    }
                }

                // 4. Notification listeners
                val nlRaw = connectionManager.exec("settings get secure enabled_notification_listeners 2>/dev/null").output.trim()
                if (nlRaw.isNotBlank() && nlRaw != "null") {
                    nlRaw.split(":").forEach { comp ->
                        val t = comp.trim()
                        if (t.isBlank()) return@forEach
                        val pkg = t.split("/").firstOrNull() ?: t
                        entries.add(SecurityEntry(t, pkg, pkg.toDisplayName(), SecurityKind.NOTIFICATION_LISTENER, true))
                    }
                }

            } catch (e: Exception) {
                Timber.e(e)
            }

            _state.update { it.copy(securityEntries = entries.distinctBy { it.componentName + it.kind }, securityLoading = false) }
        }
    }

    fun revokeSecurityService(entry: SecurityEntry) {
        viewModelScope.launch {
            val result = when (entry.kind) {
                SecurityKind.ACCESSIBILITY ->
                    connectionManager.exec("settings put secure enabled_accessibility_services \"\" 2>/dev/null")
                SecurityKind.DEVICE_ADMIN ->
                    connectionManager.exec("dpm remove-active-admin ${entry.componentName} 2>/dev/null")
                SecurityKind.NOTIFICATION_LISTENER ->
                    connectionManager.exec("cmd notification disallow_listener ${entry.componentName} 2>/dev/null")
            }
            if (result.isSuccess) {
                val updated = _state.value.securityEntries.filter { it.componentName != entry.componentName || it.kind != entry.kind }
                _state.update { it.copy(securityEntries = updated, snackbarMessage = "Revoked ${entry.displayName} ✓") }
            } else {
                _state.update { it.copy(snackbarMessage = "Failed: ${result.error.take(80)}") }
            }
        }
    }

    // ── LIVE NETWORK CONNECTIONS ──────────────────────────────────────────────
    // Real: ss -tuap from the connected target device.

    fun loadNetworkConnections() {
        _state.update { it.copy(netLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ssOut = connectionManager.exec("ss -tuap 2>/dev/null").output
                val conns = parseSsOutput(ssOut)
                _state.update { it.copy(netConnections = conns, netLoading = false) }
            } catch (e: Exception) {
                // Fallback: /proc/net/tcp + /proc/net/tcp6
                try {
                    val tcpOut = connectionManager.exec("cat /proc/net/tcp 2>/dev/null && cat /proc/net/tcp6 2>/dev/null").output
                    val conns = parseProcNetTcp(tcpOut, _state.value.targetApps)
                    _state.update { it.copy(netConnections = conns, netLoading = false) }
                } catch (e2: Exception) {
                    _state.update { it.copy(netLoading = false, snackbarMessage = "Network scan failed: ${e2.message?.take(60)}") }
                }
            }
        }
    }

    fun startNetAutoRefresh() {
        _state.update { it.copy(netAutoRefresh = true) }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _state.value.netAutoRefresh) {
                try {
                    val ssOut = connectionManager.exec("ss -tuap 2>/dev/null").output
                    val conns = parseSsOutput(ssOut)
                    _state.update { it.copy(netConnections = conns) }
                } catch (_: Exception) {}
                delay(3000L)
            }
        }
    }

    fun stopNetAutoRefresh() { _state.update { it.copy(netAutoRefresh = false) } }

    private fun parseSsOutput(raw: String): List<NetConn> {
        val result = mutableListOf<NetConn>()
        raw.lines().drop(1).forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 5) return@forEach
            val proto = parts.getOrElse(0) { "" }.uppercase()
            if (proto !in listOf("TCP", "UDP", "TCP6", "UDP6")) return@forEach
            val state  = parts.getOrElse(1) { "" }
            val local  = parts.getOrElse(4) { "" }
            val peer   = parts.getOrElse(5) { "" }
            val rest   = parts.drop(6).joinToString(" ")
            // Extract process name from users:(("com.example.pkg",pid=...,fd=...))
            val procMatch = Regex("""users:\(\("([^"]+)"""").find(rest)
            val process   = procMatch?.groupValues?.getOrNull(1) ?: ""
            // Extract UID if available
            val uidMatch = Regex("""uid:(\d+)""").find(rest)
            val uid = uidMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
            result.add(NetConn(proto, local, peer, state, uid, process, process))
        }
        return result
    }

    private fun parseProcNetTcp(raw: String, apps: List<TargetApp>): List<NetConn> {
        val uidToPkg = apps.associate { it.uid to it.packageName }
        val result = mutableListOf<NetConn>()
        raw.lines().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 10) return@forEach
            val local  = parseHexAddr(parts.getOrElse(1) { "" })
            val remote = parseHexAddr(parts.getOrElse(2) { "" })
            val stateHex = parts.getOrElse(3) { "00" }
            val state = when (stateHex) {
                "01" -> "ESTABLISHED"; "02" -> "SYN_SENT"; "03" -> "SYN_RECV"
                "04" -> "FIN_WAIT1"; "05" -> "FIN_WAIT2"; "06" -> "TIME_WAIT"
                "07" -> "CLOSE"; "08" -> "CLOSE_WAIT"; "09" -> "LAST_ACK"
                "0A" -> "LISTEN"; "0B" -> "CLOSING"; else -> stateHex
            }
            val uid = parts.getOrElse(7) { "-1" }.toIntOrNull() ?: -1
            val pkg = uidToPkg[uid] ?: ""
            result.add(NetConn("TCP", local, remote, state, uid, pkg, pkg.toDisplayName()))
        }
        return result.filter { it.state != "LISTEN" || it.remoteAddress != "0.0.0.0:0" }
    }

    private fun parseHexAddr(hex: String): String {
        val parts = hex.split(":")
        if (parts.size != 2) return hex
        val ipHex   = parts[0]
        val portHex = parts[1]
        return try {
            val ip = if (ipHex.length == 8) {
                // IPv4 — little-endian
                (0 until 4).map { i -> ipHex.substring((3 - i) * 2, (3 - i) * 2 + 2).toInt(16) }.joinToString(".")
            } else {
                // IPv6
                ipHex
            }
            val port = portHex.toInt(16)
            "$ip:$port"
        } catch (_: Exception) { hex }
    }

    // ── PRIVACY AUDIT ─────────────────────────────────────────────────────────
    // Uses target app list + real dumpsys package parse for permissions.

    fun startPrivacyAudit() {
        _state.update { it.copy(auditLoading = true, auditApps = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = _state.value.targetApps.ifEmpty {
                    val rawU = connectionManager.exec("pm list packages -U 2>/dev/null").output
                    val re = Regex("""package:([^\s]+)\s+uid:(\d+)""")
                    re.findAll(rawU).map { TargetApp(it.groupValues[1], it.groupValues[1].toDisplayName(), it.groupValues[2].toIntOrNull() ?: -1) }.toList()
                }

                // Bulk parse granted permissions from target via dumpsys package
                val permsByPkg = loadPermissionsFromTarget()
                val allTrackerPrefixes = TRACKER_DB.values.flatten().toSet()

                val apps = source.map { app ->
                    val grantedPerms = permsByPkg[app.packageName] ?: emptyList()
                    val dangerousGranted = grantedPerms.filter { it in DANGEROUS_PERMS }
                    val trackerHits = allTrackerPrefixes.filter { prefix ->
                        app.packageName.startsWith(prefix) || app.packageName == prefix
                    }.toList()
                    val score = maxOf(0, 100 - dangerousGranted.size * 5 - trackerHits.size * 15)
                    AuditApp(app.packageName, app.displayName, dangerousGranted, trackerHits, app.isSystem, score)
                }.sortedBy { it.privacyScore }

                _state.update { it.copy(auditApps = apps, auditLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(auditLoading = false, snackbarMessage = "Audit failed: ${e.message?.take(80)}") }
            }
        }
    }

    /** Parse granted dangerous permissions from target device via dumpsys package. */
    private suspend fun loadPermissionsFromTarget(): Map<String, List<String>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, MutableList<String>>()
            try {
                // Single ADB command — grep narrows the massive dumpsys output
                val dump = connectionManager.exec(
                    "dumpsys package 2>/dev/null | grep -E '^  Package \\[|android\\.permission\\.[A-Z_]+.*granted=true'"
                ).output
                var currentPkg = ""
                dump.lines().forEach { line ->
                    val pkgMatch = Regex("""^\s{1,3}Package \[([^\]]+)\]""").find(line)
                    if (pkgMatch != null) {
                        currentPkg = pkgMatch.groupValues[1]
                    } else if (line.contains("granted=true") && currentPkg.isNotBlank()) {
                        val permMatch = Regex("""(android\.permission\.[A-Z_]+)""").find(line)
                        permMatch?.groupValues?.getOrNull(1)?.let { perm ->
                            result.getOrPut(currentPkg) { mutableListOf() }.add(perm)
                        }
                    }
                }
            } catch (e: Exception) { Timber.e(e) }
            result
        }

    // ── Component / Rule management ───────────────────────────────────────────

    fun enableComponent(packageName: String, componentName: String) {
        viewModelScope.launch {
            val ok = appRepository.enableComponent(packageName, componentName)
            if (ok) blockedComponentDao.deleteByComponent(packageName, componentName)
            _state.update { it.copy(snackbarMessage = if (ok) "Component enabled ✓" else "Failed — ACCU/root required") }
        }
    }

    fun disableComponent(packageName: String, componentName: String, type: String) {
        viewModelScope.launch {
            val ok = appRepository.disableComponent(packageName, componentName, type)
            _state.update { it.copy(snackbarMessage = if (ok) "Component disabled ✓" else "Failed") }
        }
    }

    fun addPrivacyRule(packageName: String, ruleType: String, ruleName: String) {
        viewModelScope.launch { privacyRuleDao.insert(PrivacyRuleEntity(packageName = packageName, ruleType = ruleType, ruleName = ruleName)) }
    }

    fun deleteRule(rule: PrivacyRuleEntity) { viewModelScope.launch { privacyRuleDao.delete(rule) } }
    fun toggleRule(rule: PrivacyRuleEntity) { viewModelScope.launch { privacyRuleDao.update(rule.copy(isEnabled = !rule.isEnabled)) } }

    // ── Export / Import ───────────────────────────────────────────────────────

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
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                _state.update { it.copy(snackbarMessage = "Exported ${components.size} rules ✓") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun importRulesFrom(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var cc = 0; var rc = 0
                Regex(""""components"\s*:\s*\[([^\]]*)\]""", RegexOption.DOT_MATCHES_ALL).find(json)?.groupValues?.getOrNull(1)?.let { section ->
                    Regex("""\{[^}]+\}""").findAll(section).forEach { m ->
                        val o = m.value
                        val pkg  = Regex(""""pkg"\s*:\s*"([^"]+)"""").find(o)?.groupValues?.getOrNull(1) ?: return@forEach
                        val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(o)?.groupValues?.getOrNull(1) ?: return@forEach
                        val type = Regex(""""type"\s*:\s*"([^"]+)"""").find(o)?.groupValues?.getOrNull(1) ?: "receiver"
                        val tracker = o.contains(""""tracker":true""")
                        blockedComponentDao.insert(BlockedComponentEntity(packageName = pkg, componentName = name, componentType = type, isTracker = tracker, ruleSource = "import"))
                        connectionManager.exec("pm disable $pkg/$name 2>/dev/null")
                        cc++
                    }
                }
                _state.update { it.copy(snackbarMessage = "Imported $cc components, $rc rules ✓") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Import failed: ${e.message}") }
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

    fun syncCloudRules(url: String) {
        if (url.isBlank()) { _state.update { it.copy(snackbarMessage = "Enter a valid URL") }; return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val packages = body.lines().mapNotNull { it.trim().takeIf { t -> t.isNotBlank() && !t.startsWith("#") }?.split(",")?.firstOrNull()?.trim() }.filter { it.contains(".") }
                var inserted = 0
                packages.forEach { pkg ->
                    try { blockedComponentDao.insert(BlockedComponentEntity(pkg, pkg, "tracker", isTracker = true, ruleSource = "cloud:$url")); inserted++ } catch (_: Exception) {}
                }
                _state.update { it.copy(snackbarMessage = "Synced $inserted rules from cloud ✓") }
            } catch (e: Exception) { _state.update { it.copy(snackbarMessage = "Sync failed: ${e.message}") } }
        }
    }

    // ── Tab change ────────────────────────────────────────────────────────────

    fun onTabChange(tab: PrivacyTab) {
        _state.update { it.copy(selectedTab = tab) }
        when (tab) {
            PrivacyTab.SENSORS  -> if (_state.value.sensorApps.isEmpty()) loadSensorApps()
            PrivacyTab.FIREWALL -> if (_state.value.firewallApps.isEmpty()) loadFirewallApps()
            PrivacyTab.BOOT     -> if (_state.value.bootReceivers.isEmpty()) loadBootReceivers()
            PrivacyTab.SECURITY -> if (_state.value.securityEntries.isEmpty()) loadSecurityServices()
            PrivacyTab.NETWORK  -> if (_state.value.netConnections.isEmpty()) loadNetworkConnections()
            PrivacyTab.AUDIT    -> if (_state.value.auditApps.isEmpty()) startPrivacyAudit()
            PrivacyTab.TRACKERS -> if (!_state.value.trackerScanDone) scanInstalledTrackers()
            else -> {}
        }
    }

    fun onSensorSearch(q: String)   { _state.update { it.copy(sensorSearchQuery = q) } }
    fun onFirewallSearch(q: String) { _state.update { it.copy(firewallSearchQuery = q) } }
    fun onBootSearch(q: String)     { _state.update { it.copy(bootSearchQuery = q) } }
    fun onAuditSearch(q: String)    { _state.update { it.copy(auditSearchQuery = q) } }
    fun clearSnackbar()             { _state.update { it.copy(snackbarMessage = null) } }
}
