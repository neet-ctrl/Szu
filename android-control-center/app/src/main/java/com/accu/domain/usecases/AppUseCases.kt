package com.accu.domain.usecases

import android.content.pm.PackageManager
import com.accu.data.repositories.AppRepository
import com.accu.data.repositories.ShellRepository
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(includeSystem: Boolean = false) =
        appRepository.getInstalledApps(includeSystem)
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
        appRepository.uninstallApp(packageName, keepData)
}

class ToggleComponentUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(
        packageName: String,
        componentName: String,
        enable: Boolean
    ): Boolean = appRepository.setComponentEnabled(packageName, componentName, enable)
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
    private val shellRepository: ShellRepository
) {
    suspend operator fun invoke(command: String): String =
        shellRepository.execute(command)
}

class ClearAppDataUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String): Boolean =
        appRepository.clearAppData(packageName)
}

class ForceStopAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String): Boolean =
        appRepository.forceStopApp(packageName)
}
