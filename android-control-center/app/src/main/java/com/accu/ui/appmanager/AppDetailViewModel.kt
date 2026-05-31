package com.accu.ui.appmanager

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.repositories.AppRepository
import com.accu.data.repositories.FreezeMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppDetailUiState())
    val state: StateFlow<AppDetailUiState> = _state.asStateFlow()

    fun load(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val pkg = pm.getPackageInfo(
                    packageName,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                        PackageManager.GET_PERMISSIONS
                )
                val ai = pkg.applicationInfo!!
                val appName = pm.getApplicationLabel(ai).toString()
                val apkFile = java.io.File(ai.sourceDir)

                // Permissions
                val requestedPerms = pkg.requestedPermissions?.toList() ?: emptyList()
                val requestedFlags = pkg.requestedPermissionsFlags?.toList() ?: emptyList()
                val permissions = requestedPerms.mapIndexed { i, permName ->
                    val granted = requestedFlags.getOrNull(i)?.and(PackageManager.GET_PERMISSIONS) != 0
                    val pi = try { pm.getPermissionInfo(permName, 0) } catch (_: Exception) { null }
                    PermissionUiModel(
                        name = permName,
                        isGranted = granted,
                        isProtected = pi?.protection == PermissionInfo.PROTECTION_SIGNATURE,
                        protection = pi?.protection ?: 0,
                    )
                }

                // Activities
                val activities = pkg.activities?.map {
                    ComponentUiModel(it.name, it.enabled, "activity")
                } ?: emptyList()

                // Services
                val services = pkg.services?.map {
                    ComponentUiModel(it.name, it.enabled, "service")
                } ?: emptyList()

                // Receivers
                val receivers = pkg.receivers?.map {
                    ComponentUiModel(it.name, it.enabled, "receiver")
                } ?: emptyList()

                // Providers
                val providers = pkg.providers?.map {
                    ComponentUiModel(it.name, it.enabled, "provider")
                } ?: emptyList()

                _state.update {
                    it.copy(
                        isLoading = false,
                        packageName = packageName,
                        appName = appName,
                        versionName = pkg.versionName ?: "",
                        versionCode = pkg.longVersionCode,
                        minSdk = ai.minSdkVersion,
                        targetSdk = ai.targetSdkVersion,
                        apkSize = apkFile.length(),
                        installTime = pkg.firstInstallTime,
                        lastUpdate = pkg.lastUpdateTime,
                        sourceDir = ai.sourceDir ?: "",
                        dataDir = ai.dataDir ?: "",
                        isEnabled = ai.enabled,
                        permissions = permissions,
                        activities = activities,
                        services = services,
                        receivers = receivers,
                        providers = providers,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)
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
            appRepository.clearData(_state.value.packageName)
            _state.update { it.copy(snackbarMessage = "Data cleared") }
        }
    }

    fun uninstall() {
        viewModelScope.launch {
            appRepository.uninstallForUser(_state.value.packageName)
            _state.update { it.copy(snackbarMessage = "Uninstalled for current user") }
        }
    }

    fun extractApk() {
        viewModelScope.launch {
            appRepository.extractApk(_state.value.packageName, "/sdcard/Download/${_state.value.packageName}.apk")
            _state.update { it.copy(snackbarMessage = "APK extracted to Downloads") }
        }
    }

    fun forceStop() {
        viewModelScope.launch {
            appRepository.forceStop(_state.value.packageName)
            _state.update { it.copy(snackbarMessage = "Force stopped") }
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
            appRepository.launchActivity(_state.value.packageName, activityName)
        }
    }
}

suspend fun AppRepository.launchActivity(pkg: String, activity: String) {
    execShizuku("am start -n $pkg/$activity")
}

private suspend fun AppRepository.execShizuku(cmd: String) = Unit
