package com.accu.data.db.dao

import androidx.room.*
import com.accu.data.db.entities.CrashEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CrashDao {

    // ── Insert / Update ───────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crash: CrashEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crashes: List<CrashEntity>)

    @Update
    suspend fun update(crash: CrashEntity)

    // ── Delete ────────────────────────────────────────────────────────────────
    @Query("DELETE FROM crash_logs WHERE crashId = :crashId")
    suspend fun deleteById(crashId: String)

    @Query("DELETE FROM crash_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM crash_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM crash_logs WHERE timestamp < :before AND isPinned = 0")
    suspend fun deleteOlderThan(before: Long)

    // ── Single Fetch ──────────────────────────────────────────────────────────
    @Query("SELECT * FROM crash_logs WHERE crashId = :crashId LIMIT 1")
    fun getById(crashId: String): Flow<CrashEntity?>

    @Query("SELECT * FROM crash_logs WHERE crashId = :crashId LIMIT 1")
    suspend fun getByIdOnce(crashId: String): CrashEntity?

    // ── Paginated / Sorted Lists ──────────────────────────────────────────────
    @Query("""
        SELECT * FROM crash_logs
        ORDER BY isPinned DESC, timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getPage(limit: Int = 50, offset: Int = 0): Flow<List<CrashEntity>>

    @Query("""
        SELECT * FROM crash_logs
        ORDER BY isPinned DESC, timestamp DESC
    """)
    fun getAll(): Flow<List<CrashEntity>>

    @Query("SELECT * FROM crash_logs WHERE isFavorited = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<CrashEntity>>

    @Query("""
        SELECT * FROM crash_logs WHERE riskLevel = :level
        ORDER BY isPinned DESC, timestamp DESC
    """)
    fun getByRiskLevel(level: String): Flow<List<CrashEntity>>

    @Query("""
        SELECT * FROM crash_logs WHERE isFatal = :fatal
        ORDER BY isPinned DESC, timestamp DESC
    """)
    fun getByFatality(fatal: Boolean): Flow<List<CrashEntity>>

    @Query("""
        SELECT * FROM crash_logs WHERE isAnr = 1
        ORDER BY timestamp DESC
    """)
    fun getAnrs(): Flow<List<CrashEntity>>

    // ── Search ────────────────────────────────────────────────────────────────
    @Query("""
        SELECT * FROM crash_logs WHERE
            exceptionType LIKE '%' || :q || '%' OR
            exceptionMessage LIKE '%' || :q || '%' OR
            stackTrace LIKE '%' || :q || '%' OR
            affectedModule LIKE '%' || :q || '%' OR
            screenRoute LIKE '%' || :q || '%' OR
            possibleCause LIKE '%' || :q || '%'
        ORDER BY isPinned DESC, timestamp DESC
    """)
    fun search(q: String): Flow<List<CrashEntity>>

    // ── Statistics ────────────────────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM crash_logs")
    fun totalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crash_logs WHERE isFatal = 1")
    fun fatalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crash_logs WHERE isAnr = 1")
    fun anrCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crash_logs WHERE isFatal = 0 AND isAnr = 0")
    fun nonFatalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crash_logs WHERE timestamp >= :since")
    fun countSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM crash_logs WHERE riskLevel = 'CRITICAL'")
    fun criticalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crash_logs WHERE exceptionType = :type")
    suspend fun countByExceptionType(type: String): Int

    // ── Favorite / Pin toggles ────────────────────────────────────────────────
    @Query("UPDATE crash_logs SET isFavorited = :fav WHERE crashId = :crashId")
    suspend fun setFavorited(crashId: String, fav: Boolean)

    @Query("UPDATE crash_logs SET isPinned = :pin WHERE crashId = :crashId")
    suspend fun setPinned(crashId: String, pin: Boolean)

    @Query("UPDATE crash_logs SET userNotes = :notes WHERE crashId = :crashId")
    suspend fun setNotes(crashId: String, notes: String)

    // ── Most frequent crash types ─────────────────────────────────────────────
    @Query("""
        SELECT exceptionType, COUNT(*) as cnt
        FROM crash_logs
        GROUP BY exceptionType
        ORDER BY cnt DESC
        LIMIT 5
    """)
    suspend fun topExceptionTypes(): List<ExceptionFrequency>
}

data class ExceptionFrequency(val exceptionType: String, val cnt: Int)
