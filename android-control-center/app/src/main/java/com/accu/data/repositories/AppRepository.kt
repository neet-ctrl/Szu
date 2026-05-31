package com.accu.data.repositories

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.accu.data.db.dao.AppRecordDao
import com.accu.data.db.dao.FrozenAppDao
import com.accu.data.db.dao.BlockedComponentDao
import com.accu.data.db.dao.DebloatPresetDao
import com.accu.data.db.entities.*
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRecordDao: AppRecordDao,
    private val frozenAppDao: FrozenAppDao,
    private val blockedComponentDao: BlockedComponentDao,
    private val debloatPresetDao: DebloatPresetDao,
    private val shizukuUtils: ShizukuUtils,
    private val connectionManager: com.accu.connection.AccuConnectionManager,
) {
    // ── App listing ──────────────────────────────────────────────────────────

    fun observeAll(): Flow<List<AppRecordEntity>> = appRecordDao.observeAll()
    fun observeUserApps(): Flow<List<AppRecordEntity>> = appRecordDao.observeUserApps()
    fun observeSystemApps(): Flow<List<AppRecordEntity>> = appRecordDao.observeSystemApps()

    suspend fun refreshAppList() = withContext(Dispatchers.IO) {
        try {
            val entities = if (connectionManager.isRemoteSession()) {
                // Remote: shell-based listing via exec() → routes to the target device
                connectionManager.listPackages().map { pkg ->
                    AppRecordEntity(
                        packageName    = pkg.packageName,
                        appName        = pkg.packageName.split(".").lastOrNull()
                                            ?.replaceFirstChar { it.uppercase() } ?: pkg.packageName,
                        versionName    = "",
                        versionCode    = 0L,
                        installTime    = 0L,
                        lastUpdateTime = 0L,
                        isSystemApp    = pkg.isSystem,
                        isEnabled      = pkg.isEnabled,
                    )
                }
            } else {
                // Local: full data via Android PackageManager API
                val pm = context.packageManager
                pm.getInstalledPackages(
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
                ).mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    AppRecordEntity(
                        packageName    = pkg.packageName,
                        appName        = pm.getApplicationLabel(ai).toString(),
                        versionName    = pkg.versionName ?: "",
                        versionCode    = pkg.longVersionCode,
                        installTime    = pkg.firstInstallTime,
                        lastUpdateTime = pkg.lastUpdateTime,
                        isSystemApp    = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        isEnabled      = ai.enabled,
                    )
                }
            }
            appRecordDao.insertAll(entities)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh app list")
        }
    }

    suspend fun getAppInfo(packageName: String): AppRecordEntity? = appRecordDao.getByPackage(packageName)

    // ── Freeze / suspend / hide (Hail) ────────────────────────────────────────

    fun observeFrozenApps(): Flow<List<FrozenAppEntity>> = frozenAppDao.observeAll()

    suspend fun freezeApp(packageName: String, method: FreezeMethod = FreezeMethod.DISABLE): Boolean = withContext(Dispatchers.IO) {
        try {
            val appName = if (connectionManager.isRemoteSession()) {
                packageName.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
            } else {
                try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }
                catch (_: Exception) { packageName }
            }
            val result = when (method) {
                FreezeMethod.DISABLE   -> shizukuUtils.execShizuku("pm disable-user --user 0 $packageName")
                FreezeMethod.SUSPEND   -> shizukuUtils.execShizuku("am suspend-packages $packageName")
                FreezeMethod.HIDE      -> shizukuUtils.execShizuku("pm hide --user 0 $packageName")
                FreezeMethod.UNHIDE    -> shizukuUtils.execShizuku("pm unhide --user 0 $packageName")
            }
            if (result.isSuccess) {
                frozenAppDao.insert(FrozenAppEntity(packageName = packageName, appName = appName, freezeMethod = method.name.lowercase()))
            }
            result.isSuccess
        } catch (e: Exception) { Timber.e(e); false }
    }

    suspend fun unfreezeApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val frozen = frozenAppDao.get(packageName) ?: return@withContext false
            val result = when (frozen.freezeMethod) {
                "disable"  -> shizukuUtils.execShizuku("pm enable --user 0 $packageName")
                "suspend"  -> shizukuUtils.execShizuku("am unsuspend-packages $packageName")
                "hide"     -> shizukuUtils.execShizuku("pm unhide --user 0 $packageName")
                else       -> shizukuUtils.execShizuku("pm enable --user 0 $packageName")
            }
            if (result.isSuccess) frozenAppDao.deleteByPackage(packageName)
            result.isSuccess
        } catch (e: Exception) { Timber.e(e); false }
    }

    // ── Debloat (Canta + Inure) ───────────────────────────────────────────────

    suspend fun uninstallForUser(packageName: String): Boolean = withContext(Dispatchers.IO) {
        shizukuUtils.execShizuku("pm uninstall --user 0 $packageName").isSuccess
    }

    suspend fun reinstallForUser(packageName: String): Boolean = withContext(Dispatchers.IO) {
        shizukuUtils.execShizuku("cmd package install-existing --user 0 $packageName").isSuccess
    }

    suspend fun uninstallCompletely(packageName: String): Boolean = withContext(Dispatchers.IO) {
        shizukuUtils.execRoot("pm uninstall $packageName").isSuccess
    }

    fun observeDebloatPresets(): Flow<List<DebloatPresetEntity>> = debloatPresetDao.observeAll()
    suspend fun saveDebloatPreset(preset: DebloatPresetEntity) = debloatPresetDao.insert(preset)

    // ── Component manager (Blocker + Inure) ───────────────────────────────────

    fun observeBlockedComponents(): Flow<List<BlockedComponentEntity>> = blockedComponentDao.observeAll()
    fun observeBlockedForPackage(pkg: String): Flow<List<BlockedComponentEntity>> = blockedComponentDao.observeForPackage(pkg)
    fun observeTrackers(): Flow<List<BlockedComponentEntity>> = blockedComponentDao.observeTrackers()

    suspend fun disableComponent(packageName: String, componentName: String, type: String): Boolean = withContext(Dispatchers.IO) {
        val result = shizukuUtils.execShizuku("pm disable --user 0 $packageName/$componentName")
        if (result.isSuccess) {
            blockedComponentDao.insert(BlockedComponentEntity(
                packageName = packageName,
                componentName = componentName,
                componentType = type,
                isTracker = false,
            ))
        }
        result.isSuccess
    }

    suspend fun enableComponent(packageName: String, componentName: String): Boolean = withContext(Dispatchers.IO) {
        val result = shizukuUtils.execShizuku("pm enable --user 0 $packageName/$componentName")
        if (result.isSuccess) blockedComponentDao.deleteByComponent(packageName, componentName)
        result.isSuccess
    }

    // ── APK extraction ────────────────────────────────────────────────────────

    suspend fun extractApk(packageName: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val src = if (connectionManager.isRemoteSession()) {
                // Remote: resolve APK path via pm path (routes to target device)
                shizukuUtils.execShizuku("pm path $packageName").output
                    .removePrefix("package:").trim()
            } else {
                context.packageManager.getApplicationInfo(packageName, 0).sourceDir
            }
            shizukuUtils.execShizuku("cp $src $destPath").isSuccess
        } catch (e: Exception) { Timber.e(e); false }
    }

    // ── Permission management ─────────────────────────────────────────────────

    suspend fun revokePermission(packageName: String, permission: String): Boolean = withContext(Dispatchers.IO) {
        shizukuUtils.execShizuku("pm revoke $packageName $permission").isSuccess
    }

    suspend fun grantPermission(packageName: String, permission: String): Boolean = withContext(Dispatchers.IO) {
        shizukuUtils.execShizuku("pm grant $packageName $permission").isSuccess
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        shizukuUtils.execShizuku("am force-stop $packageName").isSuccess
    }

    suspend fun clearData(packageName: String): Boolean = withContext(Dispatchers.IO) {
        shizukuUtils.execShizuku("pm clear $packageName").isSuccess
    }

    suspend fun launchActivity(pkg: String, activity: String): Boolean = withContext(Dispatchers.IO) {
        shizukuUtils.execShizuku("am start -n $pkg/$activity").isSuccess
    }
}

enum class FreezeMethod { DISABLE, SUSPEND, HIDE, UNHIDE }
