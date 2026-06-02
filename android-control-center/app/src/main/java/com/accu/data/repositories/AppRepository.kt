package com.accu.data.repositories

import com.accu.data.db.dao.AppRecordDao
import com.accu.data.db.dao.FrozenAppDao
import com.accu.data.db.dao.BlockedComponentDao
import com.accu.data.db.dao.DebloatPresetDao
import com.accu.data.db.entities.*
import com.accu.utils.ShizukuUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
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
            // Always load from the connected target device via exec() — never local PackageManager
            val entities = connectionManager.listPackages().map { pkg ->
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
            appRecordDao.insertAll(entities)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh app list from target device")
        }
    }

    suspend fun getAppInfo(packageName: String): AppRecordEntity? = appRecordDao.getByPackage(packageName)

    // ── Freeze / suspend / hide (Hail) ────────────────────────────────────────

    fun observeFrozenApps(): Flow<List<FrozenAppEntity>> = frozenAppDao.observeAll()

    suspend fun freezeApp(packageName: String, method: FreezeMethod = FreezeMethod.DISABLE): Boolean = withContext(Dispatchers.IO) {
        try {
            val appName = packageName.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName

            when (method) {
                FreezeMethod.DISABLE -> {
                    val r = connectionManager.exec("pm disable-user --user 0 $packageName 2>&1")
                    val ok = r.isSuccess || r.output.contains("disabled", ignoreCase = true) ||
                             r.output.contains("new disabled state", ignoreCase = true)
                    if (ok) frozenAppDao.insert(FrozenAppEntity(packageName = packageName, appName = appName, freezeMethod = "disable"))
                    ok
                }
                FreezeMethod.SUSPEND -> {
                    // pm suspend --user 0 is the correct command (not am suspend-packages)
                    val r = connectionManager.exec("pm suspend --user 0 $packageName 2>&1")
                    val ok = r.isSuccess || r.output.contains("suspended state: true", ignoreCase = true) ||
                             r.output.contains("new suspended state: 1", ignoreCase = true)
                    if (ok) frozenAppDao.insert(FrozenAppEntity(packageName = packageName, appName = appName, freezeMethod = "suspend"))
                    ok
                }
                FreezeMethod.HIDE -> {
                    // pm hide requires MANAGE_USERS (root). Try it first; fall back to disable-user on ADB.
                    val hideResult = connectionManager.exec("pm hide --user 0 $packageName 2>&1")
                    val hideOk = hideResult.output.contains("hidden state: true", ignoreCase = true) || hideResult.isSuccess
                    if (hideOk) {
                        frozenAppDao.insert(FrozenAppEntity(packageName = packageName, appName = appName, freezeMethod = "hide"))
                        return@withContext true
                    }
                    // Fallback: pm disable-user works at ADB uid=2000 without MANAGE_USERS
                    val fb = connectionManager.exec("pm disable-user --user 0 $packageName 2>&1")
                    val fbOk = fb.isSuccess || fb.output.contains("disabled", ignoreCase = true) ||
                               fb.output.contains("new disabled state", ignoreCase = true)
                    if (fbOk) frozenAppDao.insert(FrozenAppEntity(packageName = packageName, appName = appName, freezeMethod = "hide"))
                    fbOk
                }
                FreezeMethod.UNHIDE -> {
                    val r = connectionManager.exec("pm unhide --user 0 $packageName 2>&1")
                    val ok = r.isSuccess || r.output.contains("hidden state: false", ignoreCase = true)
                    if (ok) frozenAppDao.deleteByPackage(packageName)
                    ok
                }
            }
        } catch (e: Exception) { Timber.e(e); false }
    }

    suspend fun unfreezeApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val frozen = frozenAppDao.get(packageName) ?: return@withContext false
            when (frozen.freezeMethod) {
                "disable" -> {
                    val r = connectionManager.exec("pm enable --user 0 $packageName 2>&1")
                    val ok = r.isSuccess || r.output.contains("enabled", ignoreCase = true) ||
                             r.output.contains("new enabled state", ignoreCase = true)
                    if (ok) frozenAppDao.deleteByPackage(packageName)
                    ok
                }
                "suspend" -> {
                    val r = connectionManager.exec("pm unsuspend --user 0 $packageName 2>&1")
                    val ok = r.isSuccess || r.output.contains("suspended state: false", ignoreCase = true)
                    if (ok) frozenAppDao.deleteByPackage(packageName)
                    ok
                }
                "hide" -> {
                    // Try pm unhide first (root path), then fall back to pm enable (ADB path)
                    val unhideResult = connectionManager.exec("pm unhide --user 0 $packageName 2>&1")
                    val unhideOk = unhideResult.output.contains("hidden state: false", ignoreCase = true) || unhideResult.isSuccess
                    if (unhideOk) { frozenAppDao.deleteByPackage(packageName); return@withContext true }
                    val enableResult = connectionManager.exec("pm enable --user 0 $packageName 2>&1")
                    val enableOk = enableResult.isSuccess || enableResult.output.contains("enabled", ignoreCase = true) ||
                                   enableResult.output.contains("new enabled state", ignoreCase = true)
                    if (enableOk) frozenAppDao.deleteByPackage(packageName)
                    enableOk
                }
                else -> {
                    val r = connectionManager.exec("pm enable --user 0 $packageName 2>&1")
                    val ok = r.isSuccess || r.output.contains("enabled", ignoreCase = true)
                    if (ok) frozenAppDao.deleteByPackage(packageName)
                    ok
                }
            }
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
            // Always resolve APK path via pm path on the target device — never local PackageManager
            val src = shizukuUtils.execShizuku("pm path $packageName").output
                .removePrefix("package:").trim()
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
