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
    val snackbarMessage: String? = null,
)

data class PermissionUiModel(
    val name: String,
    val isGranted: Boolean,
    val isProtected: Boolean,
    val protection: Int = 0,
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

                // Permissions: parse "requested permissions:" block
                val permissions = mutableListOf<PermissionUiModel>()
                val grantedPerms = mutableSetOf<String>()
                var inGranted = false
                var inRequested = false
                val requestedPerms = mutableListOf<String>()

                for (line in dump.lines()) {
                    when {
                        line.trimStart().startsWith("requested permissions:") -> { inRequested = true; inGranted = false }
                        line.trimStart().startsWith("install permissions:") ||
                        line.trimStart().startsWith("runtime permissions:") -> { inGranted = true; inRequested = false }
                        line.trimStart().startsWith("User ") && inGranted -> inGranted = false
                        inRequested && line.trimStart().startsWith("android.") -> {
                            requestedPerms.add(line.trim())
                        }
                        inGranted && line.contains(":") -> {
                            val perm = line.trim().substringBefore(":").trim()
                            if (perm.startsWith("android.")) grantedPerms.add(perm)
                        }
                    }
                }
                requestedPerms.forEach { perm ->
                    permissions.add(PermissionUiModel(
                        name        = perm,
                        isGranted   = perm in grantedPerms,
                        isProtected = false,
                    ))
                }

                // Components: parse activity/service/receiver/provider blocks
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

    fun extractApk() {
        val dest = "/sdcard/Download/${_state.value.packageName}.apk"
        viewModelScope.launch {
            val ok = appRepository.extractApk(_state.value.packageName, dest)
            _state.update { it.copy(snackbarMessage = if (ok) "APK saved to Downloads/${_state.value.packageName}.apk" else "Failed to extract APK — check connection") }
        }
    }

    fun forceStop() {
        viewModelScope.launch {
            val ok = appRepository.forceStop(_state.value.packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "Force stopped" else "Failed to force stop — check connection") }
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

    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }

    fun launchActivity(activityName: String) {
        viewModelScope.launch {
            val ok = appRepository.launchActivity(_state.value.packageName, activityName)
            if (!ok) _state.update { it.copy(snackbarMessage = "Failed to launch — check ACCU connection") }
        }
    }
}
