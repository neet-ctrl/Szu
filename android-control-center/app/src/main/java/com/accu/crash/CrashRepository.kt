package com.accu.crash

import android.content.Context
import com.accu.data.db.dao.CrashDao
import com.accu.data.db.entities.CrashEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CrashDao,
) {
    // ─── Queries (delegated to DAO) ───────────────────────────────────────────
    fun getAll(): Flow<List<CrashEntity>> = dao.getAll()
    fun getPage(limit: Int = 50, offset: Int = 0): Flow<List<CrashEntity>> = dao.getPage(limit, offset)
    fun getById(crashId: String): Flow<CrashEntity?> = dao.getById(crashId)
    fun search(q: String): Flow<List<CrashEntity>> = dao.search(q)
    fun getFavorites(): Flow<List<CrashEntity>> = dao.getFavorites()
    fun getAnrs(): Flow<List<CrashEntity>> = dao.getAnrs()
    fun getByRiskLevel(level: String): Flow<List<CrashEntity>> = dao.getByRiskLevel(level)
    fun getByFatality(fatal: Boolean): Flow<List<CrashEntity>> = dao.getByFatality(fatal)

    // ─── Stats ────────────────────────────────────────────────────────────────
    fun totalCount(): Flow<Int> = dao.totalCount()
    fun fatalCount(): Flow<Int> = dao.fatalCount()
    fun anrCount(): Flow<Int> = dao.anrCount()
    fun nonFatalCount(): Flow<Int> = dao.nonFatalCount()
    fun criticalCount(): Flow<Int> = dao.criticalCount()
    fun countSince(since: Long): Flow<Int> = dao.countSince(since)

    // ─── Mutations ────────────────────────────────────────────────────────────
    suspend fun delete(crashId: String) = withContext(Dispatchers.IO) { dao.deleteById(crashId) }
    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }
    suspend fun deleteByIds(ids: List<Long>) = withContext(Dispatchers.IO) { dao.deleteByIds(ids) }
    suspend fun setFavorited(crashId: String, fav: Boolean) = withContext(Dispatchers.IO) { dao.setFavorited(crashId, fav) }
    suspend fun setPinned(crashId: String, pin: Boolean) = withContext(Dispatchers.IO) { dao.setPinned(crashId, pin) }
    suspend fun setNotes(crashId: String, notes: String) = withContext(Dispatchers.IO) { dao.setNotes(crashId, notes) }
    suspend fun update(entity: CrashEntity) = withContext(Dispatchers.IO) { dao.update(entity) }

    // ─── Pending crash migration ──────────────────────────────────────────────
    /**
     * Called on every app start. Reads crash JSON files written by [CrashEngine]
     * (which runs without Room/Hilt), inserts them into Room, then deletes the files.
     */
    suspend fun migratePendingCrashes() = withContext(Dispatchers.IO) {
        val files = CrashEngine.getAllPendingFiles(context)
        for (file in files) {
            try {
                val json = JSONObject(file.readText())
                val crashId = json.optString("crashId")
                if (crashId.isBlank()) { file.delete(); continue }
                val existing = dao.getByIdOnce(crashId)
                if (existing != null) { file.delete(); continue }
                val similarCount = dao.countByExceptionType(json.optString("exceptionType"))
                val analysis = CrashAnalyzer.analyze(
                    exceptionType = json.optString("exceptionType"),
                    exceptionMessage = json.optString("exceptionMessage"),
                    stackTrace = json.optString("stackTrace"),
                    crashKind = json.optString("crashKind", "JAVA"),
                    isAnr = json.optBoolean("isAnr", false),
                    isFatal = json.optBoolean("isFatal", true),
                )
                val entity = json.toEntity(analysis, similarCount)
                dao.insert(entity)
                file.delete()
            } catch (_: Throwable) {
                // Corrupted file — skip it
                file.delete()
            }
        }
    }

    // ─── JSON → Entity ────────────────────────────────────────────────────────
    private fun JSONObject.toEntity(analysis: CrashAnalyzer.Analysis, similarCount: Int): CrashEntity =
        CrashEntity(
            crashId              = optString("crashId"),
            sessionId            = optString("sessionId"),
            timestamp            = optLong("timestamp"),
            appVersion           = optString("appVersion"),
            buildVersionCode     = optInt("buildVersionCode"),
            buildType            = optString("buildType"),
            deviceModel          = optString("deviceModel"),
            deviceManufacturer   = optString("deviceManufacturer"),
            androidVersion       = optString("androidVersion"),
            sdkInt               = optInt("sdkInt"),
            abi                  = optString("abi"),
            totalRamMb           = optLong("totalRamMb"),
            freeRamMb            = optLong("freeRamMb"),
            cpuUsagePct          = optDouble("cpuUsagePct", 0.0).toFloat(),
            batteryPct           = optInt("batteryPct"),
            batteryCharging      = optBoolean("batteryCharging"),
            isLowMemory          = optBoolean("isLowMemory"),
            processName          = optString("processName"),
            processId            = optInt("processId"),
            threadName           = optString("threadName"),
            threadId             = optLong("threadId"),
            exceptionType        = optString("exceptionType"),
            exceptionMessage     = optString("exceptionMessage"),
            stackTrace           = optString("stackTrace"),
            causeChain           = optString("causeChain"),
            activityName         = optString("activityName"),
            fragmentName         = optString("fragmentName"),
            screenRoute          = optString("screenRoute"),
            serviceName          = optString("serviceName"),
            viewModelName        = optString("viewModelName"),
            networkState         = optString("networkState"),
            shizukuState         = optString("shizukuState"),
            rootState            = optString("rootState"),
            wirelessAdbState     = optString("wirelessAdbState"),
            userActionsJson      = optString("userActionsJson"),
            sessionDurationSec   = optLong("sessionDurationSec"),
            isFatal              = optBoolean("isFatal", true),
            isAnr                = optBoolean("isAnr"),
            crashKind            = optString("crashKind", "JAVA"),
            riskLevel            = analysis.riskLevel,
            possibleCause        = analysis.possibleCause,
            affectedModule       = analysis.affectedModule,
            suggestedFix         = analysis.suggestedFix,
            similarCrashCount    = similarCount,
        )
}
