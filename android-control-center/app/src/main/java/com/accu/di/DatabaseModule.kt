package com.accu.di

import android.content.Context
import androidx.room.Room
import com.accu.data.db.AppDatabase
import com.accu.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "acc_database")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideShellCommandDao(db: AppDatabase): ShellCommandDao = db.shellCommandDao()
    @Provides fun provideSavedScriptDao(db: AppDatabase): SavedScriptDao = db.savedScriptDao()
    @Provides fun provideAppRecordDao(db: AppDatabase): AppRecordDao = db.appRecordDao()
    @Provides fun provideFrozenAppDao(db: AppDatabase): FrozenAppDao = db.frozenAppDao()
    @Provides fun provideBlockedComponentDao(db: AppDatabase): BlockedComponentDao = db.blockedComponentDao()
    @Provides fun provideAudioPresetDao(db: AppDatabase): AudioPresetDao = db.audioPresetDao()
    @Provides fun provideCallRecordingDao(db: AppDatabase): CallRecordingDao = db.callRecordingDao()
    @Provides fun provideKeyMappingDao(db: AppDatabase): KeyMappingDao = db.keyMappingDao()
    @Provides fun provideAutomationProfileDao(db: AppDatabase): AutomationProfileDao = db.automationProfileDao()
    @Provides fun provideAppLanguageDao(db: AppDatabase): AppLanguageDao = db.appLanguageDao()
    @Provides fun provideRecentActionDao(db: AppDatabase): RecentActionDao = db.recentActionDao()
    @Provides fun providePrivacyRuleDao(db: AppDatabase): PrivacyRuleDao = db.privacyRuleDao()
    @Provides fun provideSmartSpacerPluginDao(db: AppDatabase): SmartSpacerPluginDao = db.smartSpacerPluginDao()
    @Provides fun provideCustomThemeDao(db: AppDatabase): CustomThemeDao = db.customThemeDao()
    @Provides fun provideDebloatPresetDao(db: AppDatabase): DebloatPresetDao = db.debloatPresetDao()
    @Provides fun provideInstallSessionDao(db: AppDatabase): InstallSessionDao = db.installSessionDao()
    @Provides fun provideCrashDao(db: AppDatabase): CrashDao = db.crashDao()
}
