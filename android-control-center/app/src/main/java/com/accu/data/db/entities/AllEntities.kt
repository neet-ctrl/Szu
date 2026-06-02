package com.accu.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Shell / aShellYou ────────────────────────────────────────────────────────

@Entity(tableName = "shell_commands")
data class ShellCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val executionCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val output: String = "",
    val isTemplate: Boolean = false,
    val category: String = "General",
)

@Entity(tableName = "saved_scripts")
data class SavedScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String,
    val description: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val lastRun: Long = 0L,
    val runCount: Int = 0,
    val tags: List<String> = emptyList(),
)

// ── App Manager / Inure / Hail / Canta ───────────────────────────────────────

@Entity(tableName = "app_records")
data class AppRecordEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val installTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val isFrozen: Boolean = false,
    val isHidden: Boolean = false,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val tags: List<String> = emptyList(),
)

@Entity(tableName = "frozen_apps")
data class FrozenAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val frozenAt: Long = System.currentTimeMillis(),
    val freezeMethod: String = "disable",  // disable | suspend | hide | work_profile
    val isHidden: Boolean = false,
    val isSuspended: Boolean = false,
    val autoFreezeOnScreenOff: Boolean = false,
    val profileId: Int = 0,
    val note: String = "",
)

@Entity(tableName = "debloat_presets")
data class DebloatPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageList: List<String>,
    val description: String = "",
    val isBuiltIn: Boolean = false,
    val manufacturer: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

// ── Blocker / Privacy ─────────────────────────────────────────────────────────

@Entity(tableName = "blocked_components")
data class BlockedComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val componentName: String,
    val componentType: String,  // activity | service | receiver | provider
    val blockedAt: Long = System.currentTimeMillis(),
    val isTracker: Boolean = false,
    val ruleSource: String = "user",  // user | ifw | ufw | accu
)

@Entity(tableName = "privacy_rules")
data class PrivacyRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val ruleType: String,  // tracker | permission | component
    val ruleName: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

// ── Audio / RootlessJamesDSP ──────────────────────────────────────────────────

@Entity(tableName = "audio_presets")
data class AudioPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = false,
    // Bass/Treble
    val bassBoostStrength: Int = 0,          // 0-1000
    val trebleBoostStrength: Int = 0,
    // Stereo Widener
    val stereoWideStrength: Float = 0f,
    // Virtualizer
    val virtualizerStrength: Int = 0,        // 0-1000
    // Reverb
    val reverbPreset: Int = 0,
    val reverbRoomLevel: Int = -9000,
    val reverbRoomHfLevel: Int = 0,
    val reverbDecayTime: Int = 1490,
    val reverbDecayHfRatio: Int = 830,
    val reverbReflectionsLevel: Int = -2602,
    val reverbReflectionsDelay: Int = 7,
    val reverbReverbLevel: Int = 200,
    val reverbReverbDelay: Int = 11,
    val reverbDiffusion: Int = 1000,
    val reverbDensity: Int = 1000,
    // Equalizer (10-band: 31, 63, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz)
    val eqBand0: Int = 0,
    val eqBand1: Int = 0,
    val eqBand2: Int = 0,
    val eqBand3: Int = 0,
    val eqBand4: Int = 0,
    val eqBand5: Int = 0,
    val eqBand6: Int = 0,
    val eqBand7: Int = 0,
    val eqBand8: Int = 0,
    val eqBand9: Int = 0,
    // Graphic EQ (31-band)
    val graphicEqEnabled: Boolean = false,
    val graphicEqBands: List<String> = emptyList(),   // JSON array of floats
    // Dynamic range compression
    val drcEnabled: Boolean = false,
    val drcGain: Float = 0f,
    val drcKneeWidth: Float = 0f,
    val drcOutputGain: Float = 0f,
    val drcAttack: Float = 0f,
    val drcRelease: Float = 0f,
    // Limiter
    val limiterEnabled: Boolean = false,
    val limiterGain: Float = 0f,
    // Liveprog script
    val liveprogEnabled: Boolean = false,
    val liveprogScript: String = "",
    // Convolver (room correction)
    val convolverEnabled: Boolean = false,
    val convolverImpulseResponse: String = "",
    // AutoEQ
    val autoEqEnabled: Boolean = false,
    val autoEqProfileName: String = "",
    // Per-app targeting
    val targetPackages: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

// ── Call Recorder / ShizuCallRecorder ─────────────────────────────────────────

@Entity(tableName = "call_recordings")
data class CallRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val contactName: String = "",
    val phoneNumber: String = "",
    val callType: String = "INCOMING",  // INCOMING | OUTGOING | MISSED
    val durationSeconds: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val recordedAt: Long = System.currentTimeMillis(),
    val isStarred: Boolean = false,
    val notes: String = "",
    val transcription: String = "",
)

// ── Key Mapper / Automation ───────────────────────────────────────────────────

@Entity(tableName = "key_mappings")
data class KeyMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerJson: String,      // serialized Trigger
    val actionsJson: String,      // serialized list of ActionData
    val constraintsJson: String = "[]",
    val isEnabled: Boolean = true,
    val vibrateDuration: Int = 0,
    val showToast: Boolean = false,
    val toastMessage: String = "",
    val repeatDelay: Long = 0L,
    val repeatRate: Long = 0L,
    val isLongPress: Boolean = false,
    val longPressDelay: Long = 500L,
    val doublePressEnabled: Boolean = false,
    val doublePressDelay: Long = 300L,
    val sequenceTriggerEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggered: Long = 0L,
    val triggerCount: Long = 0L,
)

@Entity(tableName = "automation_profiles")
data class AutomationProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val triggerType: String,  // boot | time | app_launch | battery | network | screen | nfc
    val triggerJson: String,
    val actionsJson: String,
    val constraintsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggered: Long = 0L,
)

// ── Language Selector ─────────────────────────────────────────────────────────

@Entity(tableName = "app_languages")
data class AppLanguageEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val localeTag: String,        // BCP-47 tag e.g. "ja-JP"
    val localeName: String,
    val setAt: Long = System.currentTimeMillis(),
)

// ── Recent Actions ─────────────────────────────────────────────────────────────

@Entity(tableName = "recent_actions")
data class RecentActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitle: String,
    val iconRes: String,
    val route: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "",
)

// ── SmartSpacer plugins ────────────────────────────────────────────────────────

@Entity(tableName = "smartspacer_plugins")
data class SmartSpacerPluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val packageName: String = "",
    val isEnabled: Boolean = true,
    val configJson: String = "{}",
    val displayOrder: Int = 0,
    val targetSurface: String = "at_a_glance",  // at_a_glance | lockscreen | expanded
)

// ── ColorBlendr / Customization ───────────────────────────────────────────────

@Entity(tableName = "custom_themes")
data class CustomThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monetStyle: String = "TONAL_SPOT",
    val seedColor: Int = 0,
    val accurateShades: Boolean = true,
    val pitchBlackTheme: Boolean = false,
    val isApplied: Boolean = false,
    val colorPaletteJson: String = "{}",
    val perAppOverridesJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
)

// ── Inure Tags ────────────────────────────────────────────────────────────────

@Entity(tableName = "app_tags")
data class AppTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = "#6750A4",  // Material 3 primary
    val createdAt: Long = System.currentTimeMillis(),
    val appCount: Int = 0,
)

@Entity(tableName = "tagged_packages", primaryKeys = ["tagId", "packageName"])
data class TaggedPackageEntity(
    val tagId: Long,
    val packageName: String,
    val appName: String = "",
    val taggedAt: Long = System.currentTimeMillis(),
)

// ── Uninstalled History ───────────────────────────────────────────────────────

@Entity(tableName = "uninstalled_history")
data class UninstalledHistoryEntity(
    @PrimaryKey val packageName: String,
    val appName: String = "",
    val versionName: String = "",
    val isSystemApp: Boolean = false,
    val uninstalledAt: Long = System.currentTimeMillis(),
    val uninstallSource: String = "accu",  // accu | system | playstore | unknown
    val restoredAt: Long = 0L,
    val notes: String = "",
)

// ── Installer ─────────────────────────────────────────────────────────────────

@Entity(tableName = "install_sessions")
data class InstallSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String = "",
    val versionName: String = "",
    val apkPaths: List<String>,
    val installFlags: Int = 0,
    val status: String = "PENDING",   // PENDING | INSTALLING | SUCCESS | FAILED
    val errorMessage: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val isDowngrade: Boolean = false,
    val grantPermissions: Boolean = false,
    val replaceExisting: Boolean = true,
    val allowTest: Boolean = false,
    val allowVersionDowngrade: Boolean = false,
)
