package com.accu.domain.usecases

import com.accu.data.repositories.AppRepository
import com.accu.utils.ShizukuUtils
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(includeSystem: Boolean = false) =
        appRepository.refreshAppList()
}

class FreezeAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String, freeze: Boolean): Boolean =
        if (freeze) appRepository.freezeApp(packageName)
        else appRepository.unfreezeApp(packageName)
}

class UninstallAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String, keepData: Boolean = false): Boolean =
        appRepository.uninstallForUser(packageName)
}

class ToggleComponentUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(
        packageName: String,
        componentName: String,
        enable: Boolean
    ): Boolean = if (enable) appRepository.enableComponent(packageName, componentName)
                 else appRepository.disableComponent(packageName, componentName, "")
}

class GrantRevokePermissionUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(
        packageName: String,
        permission: String,
        grant: Boolean
    ): Boolean = if (grant) appRepository.grantPermission(packageName, permission)
    else appRepository.revokePermission(packageName, permission)
}

class ExecuteShellCommandUseCase @Inject constructor(
    private val shizukuUtils: ShizukuUtils
) {
    suspend operator fun invoke(command: String): String =
        shizukuUtils.execShizuku(command).combinedOutput
}

class ClearAppDataUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String): Boolean =
        appRepository.clearData(packageName)
}

class ForceStopAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String): Boolean =
        appRepository.forceStop(packageName)
}
