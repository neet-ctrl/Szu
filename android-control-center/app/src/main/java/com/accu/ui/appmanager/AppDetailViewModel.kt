package com.accu.ui.appmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.data.repositories.AppRepository
import com.accu.data.repositories.FreezeMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val packageName: String = "",
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val apkSize: Long = 0,
    val installTime: Long = 0,
    val lastUpdate: Long = 0,
    val sourceDir: String = "",
    val dataDir: String = "",
    val isFrozen: Boolean = false,
    val isHidden: Boolean = false,
    val isEnabled: Boolean = true,
    val permissions: List<PermissionUiModel> = emptyList(),
    val activities: List<ComponentUiModel> = emptyList(),
    val services: List<ComponentUiModel> = emptyList(),
    val receivers: List<ComponentUiModel> = emptyList(),
    val providers: List<ComponentUiModel> = emptyList(),
    // Manifest viewer
    val manifestContent: String = "",
    val manifestLoading: Boolean = false,
    // App Ops (op name → "Allow" | "Deny" | "Ignore" | "Default")
    val appOpsState: Map<String, String> = emptyMap(),
    val snackbarMessage: String? = null,
)

data class PermissionUiModel(
    val name: String,
    val isGranted: Boolean,
    val isProtected: Boolean,
    val protection: Int = 0,   // 0=normal, 1=dangerous, 2=signature
)

data class ComponentUiModel(
    val name: String,
    val isEnabled: Boolean,
    val type: String,
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val connectionManager: AccuConnectionManager,
    private val appRepository: AppRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppDetailUiState())
    val state: StateFlow<AppDetailUiState> = _state.asStateFlow()

    companion object {
        val APP_OPS_NAMES = listOf(
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "CAMERA", "RECORD_AUDIO",
            "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_CONTACTS", "WRITE_CONTACTS",
            "READ_CALL_LOG", "WRITE_CALL_LOG", "READ_SMS", "SEND_SMS", "RECEIVE_SMS",
            "READ_PHONE_STATE", "CALL_PHONE", "BODY_SENSORS", "GET_ACCOUNTS",
            "USE_BIOMETRIC", "VIBRATE", "WAKE_LOCK", "CHANGE_NETWORK_STATE",
            "REQUEST_INSTALL_PACKAGES",
        )
    }

    /**
     * Load app details from the TARGET device via `dumpsys package <pkg>`.
     * Never reads PackageManager of the local (ACCU host) device.
     */
    fun load(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dump = connectionManager.exec("dumpsys package $packageName 2>/dev/null").output

                val versionName = dump.lineSequence()
                    .mapNotNull { Regex("versionName=([^\\s]+)").find(it)?.groupValues?.getOrNull(1) }
                    .firstOrNull() ?: ""

                val versionCode = dump.lineSequence()
                    .mapNotNull { Regex("versionCode=(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toLongOrNull() }
                    .firstOrNull() ?: 0L

                val targetSdk = dump.lineSequence()
                    .mapNotNull { Regex("targetSdk=(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                    .firstOrNull() ?: 0

                val minSdk = dump.lineSequence()
                    .mapNotNull { Regex("minSdk=(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                    .firstOrNull() ?: 0

                val codePath = dump.lineSequence()
                    .mapNotNull { Regex("codePath=(.+)").find(it)?.groupValues?.getOrNull(1)?.trim() }
                    .firstOrNull() ?: ""

                val dataDir = dump.lineSequence()
                    .mapNotNull { Regex("dataDir=(.+)").find(it)?.groupValues?.getOrNull(1)?.trim() }
                    .firstOrNull() ?: ""

                val isEnabled = !dump.contains("enabled=false") && !dump.contains("enabledCaller=")

                // APK size via stat on target
                val sizeRaw = if (codePath.isNotBlank())
                    connectionManager.exec("stat -c %s $codePath 2>/dev/null || du -b $codePath 2>/dev/null | awk '{print \$1}'").output.trim()
                else ""
                val apkSize = sizeRaw.lines().firstOrNull()?.trim()?.toLongOrNull() ?: 0L

                // ── Dangerous permission list from target ──────────────────
                val dangerousRaw = connectionManager.exec("pm list permissions -d 2>/dev/null").output
                val dangerousPerms = dangerousRaw.lines()
                    .filter { it.startsWith("permission:") }
                    .map { it.removePrefix("permission:").trim() }
                    .toSet()

                // ── Permissions: parse dumpsys output ──────────────────────
                val permissions = mutableListOf<PermissionUiModel>()
                val grantedPerms = mutableSetOf<String>()
                val requestedPerms = mutableListOf<String>()
                var inGranted = false
                var inRequested = false

                for (line in dump.lines()) {
                    val trimmed = line.trimStart()
                    when {
                        trimmed.startsWith("requested permissions:") -> {
                            inRequested = true; inGranted = false
                        }
                        trimmed.startsWith("install permissions:") ||
                        trimmed.startsWith("runtime permissions:") -> {
                            inGranted = true; inRequested = false
                        }
                        // End of granted block when we hit User or next top-level section
                        (trimmed.startsWith("User ") || (trimmed.startsWith("declared permissions:")) ||
                         trimmed.startsWith("shared users:")) && inGranted -> {
                            inGranted = false
                        }
                        inRequested && trimmed.isNotBlank() && !trimmed.startsWith("#") -> {
                            // Permission line looks like "android.permission.INTERNET" (indented)
                            val perm = trimmed.trim()
                            if (perm.contains('.') && !perm.contains(' ') && perm.length > 5) {
                                requestedPerms.add(perm)
                            }
                        }
                        inGranted && trimmed.isNotBlank() && trimmed.contains(":") -> {
                            // Line looks like: "android.permission.INTERNET: granted=true"
                            // or "android.permission.CAMERA: granted=false, flags=[USER_SET]"
                            if (trimmed.contains("granted=")) {
                                val perm = trimmed.substringBefore(":").trim()
                                if (perm.contains('.') && !perm.contains(' ')) {
                                    if (trimmed.contains("granted=true")) grantedPerms.add(perm)
                                    // ensure it's in requested list too if not already
                                    if (!requestedPerms.contains(perm)) requestedPerms.add(perm)
                                }
                            }
                        }
                    }
                }

                requestedPerms.distinct().forEach { perm ->
                    val isDangerous = perm in dangerousPerms
                    permissions.add(
                        PermissionUiModel(
                            name        = perm,
                            isGranted   = perm in grantedPerms,
                            isProtected = !isDangerous && !grantedPerms.contains(perm) &&
                                          (perm.startsWith("android.permission.") || perm.startsWith("com.")),
                            protection  = if (isDangerous) 1 else 0,
                        )
                    )
                }

                // ── Components: parse activity/service/receiver/provider blocks ──
                val activities  = mutableListOf<ComponentUiModel>()
                val services    = mutableListOf<ComponentUiModel>()
                val receivers   = mutableListOf<ComponentUiModel>()
                val providers   = mutableListOf<ComponentUiModel>()

                val activityPat  = Regex("\\s+(${Regex.escape(packageName)}/[.\\w\$]+)\\s+filter.*", RegexOption.IGNORE_CASE)
                val servicePat   = Regex("\\s+Service\\s+(${Regex.escape(packageName)}/[.\\w\$]+):", RegexOption.IGNORE_CASE)
                val receiverPat  = Regex("\\s+Receiver\\s+(${Regex.escape(packageName)}/[.\\w\$]+):", RegexOption.IGNORE_CASE)
                val providerPat  = Regex("\\s+Provider\\s+(${Regex.escape(packageName)}/[.\\w\$]+):", RegexOption.IGNORE_CASE)

                for (line in dump.lines()) {
                    activityPat.find(line)?.groupValues?.getOrNull(1)?.let {
                        if (activities.none { a -> a.name == it }) activities.add(ComponentUiModel(it, true, "activity"))
                    }
                    servicePat.find(line)?.groupValues?.getOrNull(1)?.let {
                        if (services.none { s -> s.name == it }) services.add(ComponentUiModel(it, true, "service"))
                    }
                    receiverPat.find(line)?.groupValues?.getOrNull(1)?.let {
                        if (receivers.none { r -> r.name == it }) receivers.add(ComponentUiModel(it, true, "receiver"))
                    }
                    providerPat.find(line)?.groupValues?.getOrNull(1)?.let {
                        if (providers.none { p -> p.name == it }) providers.add(ComponentUiModel(it, true, "provider"))
                    }
                }

                val appName = packageName.split(".").lastOrNull()
                    ?.replaceFirstChar { it.uppercase() } ?: packageName

                _state.update {
                    it.copy(
                        isLoading   = false,
                        packageName = packageName,
                        appName     = appName,
                        versionName = versionName,
                        versionCode = versionCode,
                        minSdk      = minSdk,
                        targetSdk   = targetSdk,
                        apkSize     = apkSize,
                        sourceDir   = codePath,
                        dataDir     = dataDir,
                        isEnabled   = isEnabled,
                        permissions = permissions,
                        activities  = activities,
                        services    = services,
                        receivers   = receivers,
                        providers   = providers,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "AppDetailViewModel: failed to load $packageName from target device")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleFreeze() {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            val ok = if (_state.value.isFrozen) appRepository.unfreezeApp(pkg)
                     else appRepository.freezeApp(pkg, FreezeMethod.DISABLE)
            if (ok) _state.update { it.copy(isFrozen = !it.isFrozen) }
            _state.update { it.copy(snackbarMessage = if (ok) if (_state.value.isFrozen) "Frozen" else "Unfrozen" else "Operation failed") }
        }
    }

    fun toggleHide() {
        viewModelScope.launch {
            val ok = appRepository.freezeApp(_state.value.packageName, FreezeMethod.HIDE)
            _state.update { it.copy(snackbarMessage = if (ok) "App hidden" else "Failed") }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            val ok = appRepository.clearData(_state.value.packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "Data cleared" else "Failed to clear data — check connection") }
        }
    }

    fun uninstall() {
        viewModelScope.launch {
            val ok = appRepository.uninstallForUser(_state.value.packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "Uninstalled for current user" else "Failed to uninstall — check connection") }
        }
    }

    /** Extract APK to a specific destination path on the target device. */
    fun extractApkToPath(destPath: String) {
        viewModelScope.launch {
            val ok = appRepository.extractApk(_state.value.packageName, destPath)
            _state.update { it.copy(snackbarMessage = if (ok) "APK saved to $destPath" else "Failed to extract APK — check connection") }
        }
    }

    fun forceStop() {
        viewModelScope.launch {
            val ok = appRepository.forceStop(_state.value.packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "Force stopped" else "Failed to force stop — check connection") }
        }
    }

    /** Open the app using its main launcher activity. */
    fun openApp() {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            connectionManager.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>/dev/null")
        }
    }

    /** Fetch AndroidManifest.xml content via aapt on the target device. */
    fun fetchManifest() {
        if (_state.value.manifestContent.isNotBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(manifestLoading = true) }
            try {
                val pkg = _state.value.packageName
                val apkPath = connectionManager.exec("pm path $pkg 2>/dev/null").output
                    .removePrefix("package:").trim()
                val raw = if (apkPath.isNotBlank()) {
                    connectionManager.exec("aapt dump xmltree $apkPath AndroidManifest.xml 2>/dev/null").output
                } else ""
                val manifest = if (raw.isBlank() || raw.startsWith("ERROR")) {
                    // Fallback: dump package info
                    connectionManager.exec("dumpsys package $pkg 2>/dev/null").output.lines()
                        .take(200).joinToString("\n")
                } else raw
                _state.update {
                    it.copy(
                        manifestContent = manifest.ifBlank { "Manifest not available via ACCU connection" },
                        manifestLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(manifestContent = "Error: ${e.message}", manifestLoading = false) }
            }
        }
    }

    /** Read actual App Ops state from the target device. */
    fun fetchAppOps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = _state.value.packageName
            val opsMap = mutableMapOf<String, String>()
            APP_OPS_NAMES.forEach { op ->
                try {
                    val result = connectionManager.exec("appops get $pkg $op 2>/dev/null").output
                        .trim().lowercase()
                    opsMap[op] = when {
                        "allow"  in result -> "Allow"
                        "deny"   in result -> "Deny"
                        "ignore" in result -> "Ignore"
                        else               -> "Default"
                    }
                } catch (_: Exception) { opsMap[op] = "Default" }
            }
            _state.update { it.copy(appOpsState = opsMap) }
        }
    }

    /** Apply an App Op mode on the target device. */
    fun setAppOp(op: String, mode: String) {
        viewModelScope.launch {
            connectionManager.exec("appops set ${_state.value.packageName} $op ${mode.lowercase()} 2>/dev/null")
            _state.update { s -> s.copy(appOpsState = s.appOpsState + (op to mode)) }
        }
    }

    fun revokePermission(permission: String) {
        viewModelScope.launch {
            val ok = appRepository.revokePermission(_state.value.packageName, permission)
            if (ok) {
                _state.update { s -> s.copy(permissions = s.permissions.map { if (it.name == permission) it.copy(isGranted = false) else it }) }
            }
            _state.update { it.copy(snackbarMessage = if (ok) "Permission revoked" else "Failed") }
        }
    }

    fun grantPermission(permission: String) {
        viewModelScope.launch {
            val ok = appRepository.grantPermission(_state.value.packageName, permission)
            if (ok) {
                _state.update { s -> s.copy(permissions = s.permissions.map { if (it.name == permission) it.copy(isGranted = true) else it }) }
            }
            _state.update { it.copy(snackbarMessage = if (ok) "Permission granted" else "Failed") }
        }
    }

    fun toggleComponent(componentName: String, type: String) {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            val comp = when (type) {
                "activity" -> _state.value.activities.find { it.name == componentName }
                "service"  -> _state.value.services.find { it.name == componentName }
                "receiver" -> _state.value.receivers.find { it.name == componentName }
                "provider" -> _state.value.providers.find { it.name == componentName }
                else       -> null
            } ?: return@launch

            val ok = if (comp.isEnabled) appRepository.disableComponent(pkg, componentName, type)
                     else appRepository.enableComponent(pkg, componentName)

            if (ok) {
                _state.update { s ->
                    val toggle = { list: List<ComponentUiModel> ->
                        list.map { if (it.name == componentName) it.copy(isEnabled = !it.isEnabled) else it }
                    }
                    s.copy(
                        activities = if (type == "activity") toggle(s.activities) else s.activities,
                        services   = if (type == "service")  toggle(s.services)   else s.services,
                        receivers  = if (type == "receiver") toggle(s.receivers)  else s.receivers,
                        providers  = if (type == "provider") toggle(s.providers)  else s.providers,
                    )
                }
            }
        }
    }

    /**
     * Launch a component on the target device.
     * Dispatches am start / am startservice / am broadcast depending on type.
     */
    fun launchComponent(componentName: String, type: String) {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            // componentName from dumpsys is "pkg/pkg.ClassName" — extract just the class part
            val compClass = if (componentName.contains('/')) componentName.substringAfter('/') else componentName
            val cmd = when (type) {
                "service"  -> "am startservice -n $pkg/$compClass 2>/dev/null"
                "receiver" -> "am broadcast -n $pkg/$compClass 2>/dev/null"
                else       -> "am start -n $pkg/$compClass 2>/dev/null"
            }
            val result = connectionManager.exec(cmd)
            _state.update {
                it.copy(snackbarMessage = if (result.isSuccess) "Launched $compClass" else "Failed to launch — check ACCU connection")
            }
        }
    }

    /** Open App Info settings on the CONNECTED TARGET device via ADB/root. */
    fun openAppInfoOnTarget() {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            val result = connectionManager.exec("am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$pkg 2>/dev/null")
            _state.update { it.copy(snackbarMessage = if (result.isSuccess) "Opened App Info on target device" else "Failed — check ACCU connection") }
        }
    }

    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
}
