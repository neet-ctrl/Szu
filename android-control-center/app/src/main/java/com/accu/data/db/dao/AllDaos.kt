package com.accu.data.db.dao

import androidx.room.*
import com.accu.data.db.entities.*
import kotlinx.coroutines.flow.Flow

// ── Shell ─────────────────────────────────────────────────────────────────────

@Dao
interface ShellCommandDao {
    @Query("SELECT * FROM shell_commands ORDER BY isPinned DESC, lastUsed DESC")
    fun observeAll(): Flow<List<ShellCommandEntity>>

    @Query("SELECT * FROM shell_commands WHERE command LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<ShellCommandEntity>>

    @Query("SELECT COUNT(*) FROM shell_commands") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(cmd: ShellCommandEntity): Long
    @Update suspend fun update(cmd: ShellCommandEntity)
    @Delete suspend fun delete(cmd: ShellCommandEntity)
    @Query("DELETE FROM shell_commands WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("UPDATE shell_commands SET executionCount = executionCount + 1, lastUsed = :time WHERE id = :id")
    suspend fun incrementExecutionCount(id: Long, time: Long = System.currentTimeMillis())
    @Query("SELECT * FROM shell_commands WHERE isFavorite = 1") fun observeFavorites(): Flow<List<ShellCommandEntity>>
    @Query("SELECT * FROM shell_commands WHERE isPinned = 1") fun observePinned(): Flow<List<ShellCommandEntity>>
    @Query("SELECT * FROM shell_commands ORDER BY executionCount DESC LIMIT :limit") fun topUsed(limit: Int = 10): Flow<List<ShellCommandEntity>>
}

@Dao
interface SavedScriptDao {
    @Query("SELECT * FROM saved_scripts ORDER BY isPinned DESC, lastModified DESC")
    fun observeAll(): Flow<List<SavedScriptEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(s: SavedScriptEntity): Long
    @Update suspend fun update(s: SavedScriptEntity)
    @Delete suspend fun delete(s: SavedScriptEntity)
    @Query("UPDATE saved_scripts SET runCount = runCount + 1, lastRun = :time WHERE id = :id")
    suspend fun incrementRunCount(id: Long, time: Long = System.currentTimeMillis())
    @Query("SELECT * FROM saved_scripts WHERE name LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<SavedScriptEntity>>
}

// ── App Manager ───────────────────────────────────────────────────────────────

@Dao
interface AppRecordDao {
    @Query("SELECT * FROM app_records ORDER BY appName ASC") fun observeAll(): Flow<List<AppRecordEntity>>
    @Query("SELECT * FROM app_records WHERE isSystemApp = 0") fun observeUserApps(): Flow<List<AppRecordEntity>>
    @Query("SELECT * FROM app_records WHERE isSystemApp = 1") fun observeSystemApps(): Flow<List<AppRecordEntity>>
    @Query("SELECT * FROM app_records WHERE packageName = :pkg") suspend fun getByPackage(pkg: String): AppRecordEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(apps: List<AppRecordEntity>)
    @Update suspend fun update(app: AppRecordEntity)
    @Query("DELETE FROM app_records WHERE packageName = :pkg") suspend fun deleteByPackage(pkg: String)
    @Query("SELECT COUNT(*) FROM app_records WHERE isSystemApp = 0") suspend fun userAppCount(): Int
}

@Dao
interface FrozenAppDao {
    @Query("SELECT * FROM frozen_apps") fun observeAll(): Flow<List<FrozenAppEntity>>
    @Query("SELECT COUNT(*) FROM frozen_apps") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(app: FrozenAppEntity)
    @Delete suspend fun delete(app: FrozenAppEntity)
    @Query("DELETE FROM frozen_apps WHERE packageName = :pkg") suspend fun deleteByPackage(pkg: String)
    @Query("SELECT * FROM frozen_apps WHERE packageName = :pkg") suspend fun get(pkg: String): FrozenAppEntity?
    @Query("SELECT * FROM frozen_apps WHERE autoFreezeOnScreenOff = 1") fun observeAutoFreeze(): Flow<List<FrozenAppEntity>>
}

@Dao
interface DebloatPresetDao {
    @Query("SELECT * FROM debloat_presets") fun observeAll(): Flow<List<DebloatPresetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(preset: DebloatPresetEntity): Long
    @Update suspend fun update(preset: DebloatPresetEntity)
    @Delete suspend fun delete(preset: DebloatPresetEntity)
}

// ── Privacy / Blocker ─────────────────────────────────────────────────────────

@Dao
interface BlockedComponentDao {
    @Query("SELECT * FROM blocked_components ORDER BY packageName, componentType")
    fun observeAll(): Flow<List<BlockedComponentEntity>>
    @Query("SELECT * FROM blocked_components WHERE packageName = :pkg")
    fun observeForPackage(pkg: String): Flow<List<BlockedComponentEntity>>
    @Query("SELECT * FROM blocked_components WHERE isTracker = 1")
    fun observeTrackers(): Flow<List<BlockedComponentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(c: BlockedComponentEntity): Long
    @Delete suspend fun delete(c: BlockedComponentEntity)
    @Query("DELETE FROM blocked_components WHERE packageName = :pkg AND componentName = :comp")
    suspend fun deleteByComponent(pkg: String, comp: String)
    @Query("DELETE FROM blocked_components") suspend fun deleteAll()
    @Query("SELECT COUNT(*) FROM blocked_components") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM blocked_components WHERE isTracker = 1") suspend fun trackerCount(): Int
}

@Dao
interface PrivacyRuleDao {
    @Query("SELECT * FROM privacy_rules") fun observeAll(): Flow<List<PrivacyRuleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(rule: PrivacyRuleEntity): Long
    @Delete suspend fun delete(rule: PrivacyRuleEntity)
    @Update suspend fun update(rule: PrivacyRuleEntity)
    @Query("SELECT * FROM privacy_rules WHERE packageName = :pkg") fun forPackage(pkg: String): Flow<List<PrivacyRuleEntity>>
    @Query("DELETE FROM privacy_rules") suspend fun deleteAll()
}

// ── Audio ──────────────────────────────────────────────────────────────────────

@Dao
interface AudioPresetDao {
    @Query("SELECT * FROM audio_presets ORDER BY isEnabled DESC, name ASC") fun observeAll(): Flow<List<AudioPresetEntity>>
    @Query("SELECT * FROM audio_presets WHERE isEnabled = 1 LIMIT 1") suspend fun getActive(): AudioPresetEntity?
    @Query("UPDATE audio_presets SET isEnabled = 0") suspend fun disableAll()
    @Query("UPDATE audio_presets SET isEnabled = 1 WHERE id = :id") suspend fun enablePreset(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(p: AudioPresetEntity): Long
    @Update suspend fun update(p: AudioPresetEntity)
    @Delete suspend fun delete(p: AudioPresetEntity)
    @Query("SELECT * FROM audio_presets WHERE isBuiltIn = 1") fun observeBuiltIn(): Flow<List<AudioPresetEntity>>
}

// ── Call Recordings ─────────────────────────────────────────────────────────

@Dao
interface CallRecordingDao {
    @Query("SELECT * FROM call_recordings ORDER BY recordedAt DESC") fun observeAll(): Flow<List<CallRecordingEntity>>
    @Query("SELECT * FROM call_recordings WHERE contactName LIKE '%' || :q || '%' OR phoneNumber LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<CallRecordingEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(r: CallRecordingEntity): Long
    @Update suspend fun update(r: CallRecordingEntity)
    @Delete suspend fun delete(r: CallRecordingEntity)
    @Query("SELECT COUNT(*) FROM call_recordings") suspend fun count(): Int
    @Query("SELECT * FROM call_recordings WHERE isStarred = 1") fun observeStarred(): Flow<List<CallRecordingEntity>>
    @Query("SELECT SUM(fileSizeBytes) FROM call_recordings") suspend fun totalSizeBytes(): Long?
}

// ── Key Mapper ────────────────────────────────────────────────────────────────

@Dao
interface KeyMappingDao {
    @Query("SELECT * FROM key_mappings ORDER BY isEnabled DESC, name ASC") fun observeAll(): Flow<List<KeyMappingEntity>>
    @Query("SELECT * FROM key_mappings WHERE isEnabled = 1") fun observeEnabled(): Flow<List<KeyMappingEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(m: KeyMappingEntity): Long
    @Update suspend fun update(m: KeyMappingEntity)
    @Delete suspend fun delete(m: KeyMappingEntity)
    @Query("UPDATE key_mappings SET isEnabled = :enabled WHERE id = :id") suspend fun setEnabled(id: Long, enabled: Boolean)
    @Query("UPDATE key_mappings SET lastTriggered = :t, triggerCount = triggerCount + 1 WHERE id = :id")
    suspend fun recordTrigger(id: Long, t: Long = System.currentTimeMillis())
}

@Dao
interface AutomationProfileDao {
    @Query("SELECT * FROM automation_profiles ORDER BY name ASC") fun observeAll(): Flow<List<AutomationProfileEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(p: AutomationProfileEntity): Long
    @Update suspend fun update(p: AutomationProfileEntity)
    @Delete suspend fun delete(p: AutomationProfileEntity)
    @Query("SELECT * FROM automation_profiles WHERE isEnabled = 1") fun observeEnabled(): Flow<List<AutomationProfileEntity>>
}

// ── Language ──────────────────────────────────────────────────────────────────

@Dao
interface AppLanguageDao {
    @Query("SELECT * FROM app_languages ORDER BY appName ASC") fun observeAll(): Flow<List<AppLanguageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(lang: AppLanguageEntity)
    @Delete suspend fun delete(lang: AppLanguageEntity)
    @Query("DELETE FROM app_languages WHERE packageName = :pkg") suspend fun deleteForPackage(pkg: String)
    @Query("SELECT * FROM app_languages WHERE packageName = :pkg") suspend fun get(pkg: String): AppLanguageEntity?
    @Query("SELECT COUNT(*) FROM app_languages") suspend fun count(): Int
}

// ── Recent Actions / Dashboard ────────────────────────────────────────────────

@Dao
interface RecentActionDao {
    @Query("SELECT * FROM recent_actions ORDER BY timestamp DESC LIMIT :limit") fun observeRecent(limit: Int = 20): Flow<List<RecentActionEntity>>
    @Insert suspend fun insert(a: RecentActionEntity): Long
    @Query("DELETE FROM recent_actions WHERE id NOT IN (SELECT id FROM recent_actions ORDER BY timestamp DESC LIMIT 50)")
    suspend fun trimOld()
    @Query("DELETE FROM recent_actions") suspend fun clearAll()
}

// ── SmartSpacer ───────────────────────────────────────────────────────────────

@Dao
interface SmartSpacerPluginDao {
    @Query("SELECT * FROM smartspacer_plugins ORDER BY displayOrder ASC") fun observeAll(): Flow<List<SmartSpacerPluginEntity>>
    @Query("SELECT * FROM smartspacer_plugins WHERE isEnabled = 1 ORDER BY displayOrder ASC") fun observeEnabled(): Flow<List<SmartSpacerPluginEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(p: SmartSpacerPluginEntity)
    @Update suspend fun update(p: SmartSpacerPluginEntity)
    @Query("UPDATE smartspacer_plugins SET isEnabled = :enabled WHERE id = :id") suspend fun setEnabled(id: String, enabled: Boolean)
}

// ── Custom Themes ─────────────────────────────────────────────────────────────

@Dao
interface CustomThemeDao {
    @Query("SELECT * FROM custom_themes ORDER BY name ASC") fun observeAll(): Flow<List<CustomThemeEntity>>
    @Query("SELECT * FROM custom_themes WHERE isApplied = 1 LIMIT 1") suspend fun getApplied(): CustomThemeEntity?
    @Query("UPDATE custom_themes SET isApplied = 0") suspend fun clearApplied()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(t: CustomThemeEntity): Long
    @Update suspend fun update(t: CustomThemeEntity)
    @Delete suspend fun delete(t: CustomThemeEntity)
}

// ── Inure Tags ────────────────────────────────────────────────────────────────

@Dao
interface AppTagDao {
    @Query("SELECT * FROM app_tags ORDER BY name ASC") fun observeAll(): Flow<List<AppTagEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(tag: AppTagEntity): Long
    @Update suspend fun update(tag: AppTagEntity)
    @Delete suspend fun delete(tag: AppTagEntity)
    @Query("SELECT COUNT(*) FROM app_tags") suspend fun count(): Int
}

@Dao
interface TaggedPackageDao {
    @Query("SELECT * FROM tagged_packages WHERE tagId = :tagId ORDER BY appName ASC") fun observeForTag(tagId: Long): Flow<List<TaggedPackageEntity>>
    @Query("SELECT * FROM tagged_packages WHERE packageName = :pkg") fun observeForPackage(pkg: String): Flow<List<TaggedPackageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(tp: TaggedPackageEntity)
    @Query("DELETE FROM tagged_packages WHERE tagId = :tagId AND packageName = :pkg") suspend fun delete(tagId: Long, pkg: String)
    @Query("DELETE FROM tagged_packages WHERE tagId = :tagId") suspend fun deleteForTag(tagId: Long)
    @Query("DELETE FROM tagged_packages WHERE packageName = :pkg") suspend fun deleteForPackage(pkg: String)
}

// ── Uninstalled History ───────────────────────────────────────────────────────

@Dao
interface UninstalledHistoryDao {
    @Query("SELECT * FROM uninstalled_history ORDER BY uninstalledAt DESC") fun observeAll(): Flow<List<UninstalledHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(u: UninstalledHistoryEntity)
    @Delete suspend fun delete(u: UninstalledHistoryEntity)
    @Query("DELETE FROM uninstalled_history WHERE packageName = :pkg") suspend fun deleteByPackage(pkg: String)
    @Query("SELECT * FROM uninstalled_history WHERE packageName = :pkg") suspend fun get(pkg: String): UninstalledHistoryEntity?
    @Query("UPDATE uninstalled_history SET restoredAt = :time WHERE packageName = :pkg") suspend fun markRestored(pkg: String, time: Long = System.currentTimeMillis())
    @Query("SELECT COUNT(*) FROM uninstalled_history") suspend fun count(): Int
}

// ── Install Sessions ──────────────────────────────────────────────────────────

@Dao
interface InstallSessionDao {
    @Query("SELECT * FROM install_sessions ORDER BY startedAt DESC") fun observeAll(): Flow<List<InstallSessionEntity>>
    @Insert suspend fun insert(s: InstallSessionEntity): Long
    @Update suspend fun update(s: InstallSessionEntity)
    @Query("UPDATE install_sessions SET status = :status, errorMessage = :err, completedAt = :time WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, err: String = "", time: Long = System.currentTimeMillis())
}
