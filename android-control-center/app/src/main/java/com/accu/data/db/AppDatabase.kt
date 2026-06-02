package com.accu.data.db

import androidx.room.*
import com.accu.data.db.dao.*
import com.accu.data.db.entities.*

@Database(
    entities = [
        ShellCommandEntity::class,
        SavedScriptEntity::class,
        AppRecordEntity::class,
        FrozenAppEntity::class,
        BlockedComponentEntity::class,
        AudioPresetEntity::class,
        CallRecordingEntity::class,
        KeyMappingEntity::class,
        AutomationProfileEntity::class,
        AppLanguageEntity::class,
        RecentActionEntity::class,
        PrivacyRuleEntity::class,
        SmartSpacerPluginEntity::class,
        CustomThemeEntity::class,
        DebloatPresetEntity::class,
        InstallSessionEntity::class,
        CrashEntity::class,
        AppTagEntity::class,
        TaggedPackageEntity::class,
        UninstalledHistoryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(DbConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shellCommandDao(): ShellCommandDao
    abstract fun savedScriptDao(): SavedScriptDao
    abstract fun appRecordDao(): AppRecordDao
    abstract fun frozenAppDao(): FrozenAppDao
    abstract fun blockedComponentDao(): BlockedComponentDao
    abstract fun audioPresetDao(): AudioPresetDao
    abstract fun callRecordingDao(): CallRecordingDao
    abstract fun keyMappingDao(): KeyMappingDao
    abstract fun automationProfileDao(): AutomationProfileDao
    abstract fun appLanguageDao(): AppLanguageDao
    abstract fun recentActionDao(): RecentActionDao
    abstract fun privacyRuleDao(): PrivacyRuleDao
    abstract fun smartSpacerPluginDao(): SmartSpacerPluginDao
    abstract fun customThemeDao(): CustomThemeDao
    abstract fun debloatPresetDao(): DebloatPresetDao
    abstract fun installSessionDao(): InstallSessionDao
    abstract fun crashDao(): CrashDao
    abstract fun appTagDao(): AppTagDao
    abstract fun taggedPackageDao(): TaggedPackageDao
    abstract fun uninstalledHistoryDao(): UninstalledHistoryDao
}
