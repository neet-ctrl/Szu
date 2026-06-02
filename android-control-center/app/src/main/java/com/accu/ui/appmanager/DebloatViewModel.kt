package com.accu.ui.appmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.data.repositories.AppRepository
import com.accu.data.repositories.FreezeMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// ── Data models ──────────────────────────────────────────────────────────────

data class UadAppData(
    val id: String = "",
    val list: String = "",
    val description: String = "",
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val removal: String = "Expert",
)

data class DebloatAppModel(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isFrozen: Boolean,
    val isRemoved: Boolean = false,
    val uadData: UadAppData? = null,
) {
    val safetyLabel: String get() = uadData?.removal ?: "Unknown"
    val safetyColor: Long get() = when (uadData?.removal) {
        "Recommended" -> 0xFF43A047L
        "Advanced"    -> 0xFFFB8C00L
        "Expert"      -> 0xFFE53935L
        "Unsafe"      -> 0xFF6A1B9AL
        else          -> 0xFF757575L
    }
    val uadLabels: List<String> get() = uadData?.labels ?: emptyList()
    val uadDescription: String get() = uadData?.description ?: ""
    val uadDependencies: List<String> get() = uadData?.dependencies ?: emptyList()
    val uadNeededBy: List<String> get() = uadData?.neededBy ?: emptyList()
}

enum class DebloatTab { SYSTEM, USER, REMOVED }
enum class DebloatSortOrder { NAME_ASC, NAME_DESC, SAFETY, HAS_UAD }
enum class DebloatAction { REMOVE_USER, DISABLE, SUSPEND, HIDE, REMOVE_ALL_USERS, RESTORE }

data class DebloatUiState(
    val apps: List<DebloatAppModel> = emptyList(),
    val filteredApps: List<DebloatAppModel> = emptyList(),
    val removedApps: List<DebloatAppModel> = emptyList(),
    val selectedTab: DebloatTab = DebloatTab.SYSTEM,
    val searchQuery: String = "",
    val selectedSafety: Set<String> = emptySet(),
    val selectedLabel: String? = null,
    val sortOrder: DebloatSortOrder = DebloatSortOrder.SAFETY,
    val isLoading: Boolean = true,
    val isLoadingRemoved: Boolean = false,
    val selectedApps: Set<String> = emptySet(),
    val isMultiSelect: Boolean = false,
    val batchProgress: Int = 0,
    val batchTotal: Int = 0,
    val isBatchRunning: Boolean = false,
    val detailApp: DebloatAppModel? = null,
    val snackbarMessage: String? = null,
)

// ── Embedded UAD-inspired database ──────────────────────────────────────────

private val UAD_DB: Map<String, UadAppData> = buildMap {
    // ── Google ────────────────────────────────────────────────────────────────
    put("com.google.android.apps.tachyon", UadAppData(
        id="com.google.android.apps.tachyon", list="Google",
        description="Google Meet / Duo video calling app. Fully replaceable with any video calling app.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.videos", UadAppData(
        id="com.google.android.videos", list="Google",
        description="Google TV / Play Movies app for renting/buying video content.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.music", UadAppData(
        id="com.google.android.music", list="Google",
        description="Google Play Music (discontinued). Completely safe to remove.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.apps.magazines", UadAppData(
        id="com.google.android.apps.magazines", list="Google",
        description="Google News & Magazines aggregator app.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.apps.books", UadAppData(
        id="com.google.android.apps.books", list="Google",
        description="Google Play Books e-reader. Replaceable with Kindle or Moon+ Reader.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.apps.wellbeing", UadAppData(
        id="com.google.android.apps.wellbeing", list="Google",
        description="Digital Wellbeing app for usage tracking and screen-time limits. May disable usage stats on some devices.",
        labels=listOf("Google"), removal="Advanced",
        neededBy=listOf("com.android.settings")))
    put("com.google.android.talk", UadAppData(
        id="com.google.android.talk", list="Google",
        description="Google Hangouts (legacy). Discontinued and replaceable.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.googlequicksearchbox", UadAppData(
        id="com.google.android.googlequicksearchbox", list="Google",
        description="Google Search & Google App. Required for Google Assistant and some widgets. May break OK Google.",
        labels=listOf("Google"), removal="Advanced",
        neededBy=listOf("com.google.android.apps.googleassistant")))
    put("com.google.android.apps.googleassistant", UadAppData(
        id="com.google.android.apps.googleassistant", list="Google",
        description="Google Assistant. Safe to remove if you don't use voice commands.",
        labels=listOf("Google"), removal="Recommended",
        dependencies=listOf("com.google.android.googlequicksearchbox")))
    put("com.google.android.apps.photos", UadAppData(
        id="com.google.android.apps.photos", list="Google",
        description="Google Photos backup app. Safe to remove if using alternative backup solution.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.gm", UadAppData(
        id="com.google.android.gm", list="Google",
        description="Gmail. Can be safely removed if using another email client.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.youtube", UadAppData(
        id="com.google.android.youtube", list="Google",
        description="YouTube. Replaceable with ReVanced, NewPipe, or any browser.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.apps.youtube.music", UadAppData(
        id="com.google.android.apps.youtube.music", list="Google",
        description="YouTube Music streaming app.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.apps.chromecast.app", UadAppData(
        id="com.google.android.apps.chromecast.app", list="Google",
        description="Google Home / Chromecast setup app. Only needed for Chromecast devices.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.keep", UadAppData(
        id="com.google.android.keep", list="Google",
        description="Google Keep notes app. Replaceable with Obsidian, Notion, Markor etc.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.apps.fitness", UadAppData(
        id="com.google.android.apps.fitness", list="Google",
        description="Google Fit health and fitness tracking app.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.feedback", UadAppData(
        id="com.google.android.feedback", list="Google",
        description="Google Feedback reporter. Sends crash/feedback reports to Google.",
        labels=listOf("Google","Analytics"), removal="Recommended"))
    put("com.google.android.apps.subscriptions.red", UadAppData(
        id="com.google.android.apps.subscriptions.red", list="Google",
        description="Google One storage subscription management app.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.contacts", UadAppData(
        id="com.google.android.contacts", list="Google",
        description="Google Contacts app. Replaceable with AOSP contacts or Simple Contacts.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.dialer", UadAppData(
        id="com.google.android.dialer", list="Google",
        description="Google Phone dialer. May be system default on Pixel devices — check before removing.",
        labels=listOf("Google"), removal="Advanced",
        neededBy=listOf("com.android.phone")))
    put("com.google.android.apps.turbo", UadAppData(
        id="com.google.android.apps.turbo", list="Google",
        description="Device Health Services / Adaptive Battery. Removing may affect battery optimization.",
        labels=listOf("Google"), removal="Advanced"))
    put("com.google.android.apps.maps", UadAppData(
        id="com.google.android.apps.maps", list="Google",
        description="Google Maps. Safe to remove if using OsmAnd, HERE WeGo, or other alternatives.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.apps.translate", UadAppData(
        id="com.google.android.apps.translate", list="Google",
        description="Google Translate app. Safe to remove.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.inputmethod.latin", UadAppData(
        id="com.google.android.inputmethod.latin", list="Google",
        description="Gboard / Google Keyboard. Replaceable with FOSS alternatives like AnySoftKeyboard.",
        labels=listOf("Google"), removal="Advanced",
        neededBy=listOf("com.android.inputmethod.latin")))
    put("com.google.android.apps.docs", UadAppData(
        id="com.google.android.apps.docs", list="Google",
        description="Google Drive. Safe to remove if not using Google Drive cloud storage.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.apps.docs.editors.docs", UadAppData(
        id="com.google.android.apps.docs.editors.docs", list="Google",
        description="Google Docs word processor.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.apps.docs.editors.sheets", UadAppData(
        id="com.google.android.apps.docs.editors.sheets", list="Google",
        description="Google Sheets spreadsheet app.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.apps.docs.editors.slides", UadAppData(
        id="com.google.android.apps.docs.editors.slides", list="Google",
        description="Google Slides presentation app.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.android.apps.cloudprint", UadAppData(
        id="com.google.android.apps.cloudprint", list="Google",
        description="Google Cloud Print (discontinued). Always safe to remove.",
        labels=listOf("Google","Bloatware"), removal="Recommended"))
    put("com.google.ar.lens", UadAppData(
        id="com.google.ar.lens", list="Google",
        description="Google Lens AR-based visual search.",
        labels=listOf("Google"), removal="Recommended"))
    put("com.google.android.apps.work.clouddpc", UadAppData(
        id="com.google.android.apps.work.clouddpc", list="Google",
        description="Android Device Policy for Google Workspace MDM. Only needed in enterprise environments.",
        labels=listOf("Google"), removal="Recommended"))

    // ── Samsung ───────────────────────────────────────────────────────────────
    put("com.samsung.android.bixby.agent", UadAppData(
        id="com.samsung.android.bixby.agent", list="Samsung",
        description="Samsung Bixby voice assistant. Safe to remove; won't break the side button after configuration.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended",
        dependencies=listOf("com.samsung.android.bixbytouch")))
    put("com.samsung.android.bixbytouch", UadAppData(
        id="com.samsung.android.bixbytouch", list="Samsung",
        description="Bixby side-button launcher. Removing may disable the Bixby button.",
        labels=listOf("Samsung"), removal="Advanced"))
    put("com.samsung.android.bixby.wakeup", UadAppData(
        id="com.samsung.android.bixby.wakeup", list="Samsung",
        description="Bixby Wake Up listener. Listens for 'Hey Bixby'. Safe to remove.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.game.gamehome", UadAppData(
        id="com.samsung.android.game.gamehome", list="Samsung",
        description="Samsung Game Launcher. Safe to remove if you don't use Samsung's gaming overlay.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.game.gametools", UadAppData(
        id="com.samsung.android.game.gametools", list="Samsung",
        description="Samsung Game Booster tools and FPS overlay. Safe to remove.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.weather", UadAppData(
        id="com.samsung.android.weather", list="Samsung",
        description="Samsung Weather app. Replaceable with many better alternatives.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.dialer.samsungdialerplugin", UadAppData(
        id="com.samsung.android.dialer.samsungdialerplugin", list="Samsung",
        description="Samsung-specific dialer enhancements plugin. Safe to remove.",
        labels=listOf("Samsung"), removal="Advanced"))
    put("com.samsung.android.samsungpay.gear", UadAppData(
        id="com.samsung.android.samsungpay.gear", list="Samsung",
        description="Samsung Pay for Gear watches. Only needed if using Samsung Pay on a Galaxy Watch.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.app.spage", UadAppData(
        id="com.samsung.android.app.spage", list="Samsung",
        description="Bixby Home / Samsung Daily. The swipe-left feed from home screen.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.arzone", UadAppData(
        id="com.samsung.android.arzone", list="Samsung",
        description="Samsung AR Zone (AR Emoji, AR Doodle, etc.).",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.incallui", UadAppData(
        id="com.samsung.android.incallui", list="Samsung",
        description="Samsung in-call UI. Removing will break phone call interface — use only on advanced setups.",
        labels=listOf("Samsung"), removal="Expert"))
    put("com.samsung.android.app.galaxyfinder", UadAppData(
        id="com.samsung.android.app.galaxyfinder", list="Samsung",
        description="Samsung Quick Search indexing service. Safe to remove.",
        labels=listOf("Samsung"), removal="Recommended"))
    put("com.samsung.android.rubin.app", UadAppData(
        id="com.samsung.android.rubin.app", list="Samsung",
        description="Samsung Daily Briefing / Edge News. Part of Bixby ecosystem.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.mobileservice", UadAppData(
        id="com.samsung.android.mobileservice", list="Samsung",
        description="Samsung Mobile Services — app recommendations and notifications from Samsung.",
        labels=listOf("Samsung","Ads"), removal="Recommended"))
    put("com.samsung.android.app.tips", UadAppData(
        id="com.samsung.android.app.tips", list="Samsung",
        description="Samsung Tips app. Useless tip notifications from Samsung.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.sec.android.app.chromecustomizations", UadAppData(
        id="com.sec.android.app.chromecustomizations", list="Samsung",
        description="Samsung Chrome customizations. Safe to remove.",
        labels=listOf("Samsung"), removal="Recommended"))
    put("com.samsung.android.smartswitchassistant", UadAppData(
        id="com.samsung.android.smartswitchassistant", list="Samsung",
        description="Samsung Smart Switch migration assistant.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.kidsinstaller", UadAppData(
        id="com.samsung.android.kidsinstaller", list="Samsung",
        description="Samsung Kids Mode launcher. Safe to remove if not used.",
        labels=listOf("Samsung","Bloatware"), removal="Recommended"))
    put("com.samsung.android.app.cocktailbarservice", UadAppData(
        id="com.samsung.android.app.cocktailbarservice", list="Samsung",
        description="Edge panels service for Samsung phones. Removing disables edge panels.",
        labels=listOf("Samsung"), removal="Advanced"))
    put("com.samsung.android.privateshare", UadAppData(
        id="com.samsung.android.privateshare", list="Samsung",
        description="Samsung Private Share encrypted file transfer feature.",
        labels=listOf("Samsung"), removal="Recommended"))

    // ── Xiaomi / MIUI ─────────────────────────────────────────────────────────
    put("com.miui.player", UadAppData(
        id="com.miui.player", list="MIUI",
        description="MIUI Music player app. Replaceable with PowerAmp, VLC, or AIMP.",
        labels=listOf("MIUI","Bloatware"), removal="Recommended"))
    put("com.miui.video", UadAppData(
        id="com.miui.video", list="MIUI",
        description="MIUI Video player app.",
        labels=listOf("MIUI","Bloatware"), removal="Recommended"))
    put("com.miui.analytics", UadAppData(
        id="com.miui.analytics", list="MIUI",
        description="MIUI analytics and telemetry service. Sends device usage data to Xiaomi servers.",
        labels=listOf("MIUI","Analytics"), removal="Recommended"))
    put("com.miui.msa.global", UadAppData(
        id="com.miui.msa.global", list="MIUI",
        description="Xiaomi System Ads service. Responsible for ads shown in MIUI apps and settings.",
        labels=listOf("MIUI","Ads"), removal="Recommended"))
    put("com.miui.global.packageinstaller", UadAppData(
        id="com.miui.global.packageinstaller", list="MIUI",
        description="Xiaomi's alternative package installer with GetApps integration. The AOSP package installer is the fallback.",
        labels=listOf("MIUI"), removal="Advanced"))
    put("com.xiaomi.getapps", UadAppData(
        id="com.xiaomi.getapps", list="MIUI",
        description="GetApps — Xiaomi's proprietary app store. Safe to remove if using Play Store only.",
        labels=listOf("MIUI","Bloatware"), removal="Recommended"))
    put("com.miui.cloudservice", UadAppData(
        id="com.miui.cloudservice", list="MIUI",
        description="Xiaomi Mi Cloud sync service. Safe to remove if not using Mi Cloud.",
        labels=listOf("MIUI"), removal="Recommended"))
    put("com.miui.yellowpage", UadAppData(
        id="com.miui.yellowpage", list="MIUI",
        description="MIUI Yellow Pages / caller ID lookup service. Sends caller data to Xiaomi.",
        labels=listOf("MIUI","Analytics"), removal="Recommended"))
    put("com.miui.powerkeeper", UadAppData(
        id="com.miui.powerkeeper", list="MIUI",
        description="MIUI battery optimization service. Removing may affect battery life management.",
        labels=listOf("MIUI"), removal="Advanced"))
    put("com.mi.globalbrowser", UadAppData(
        id="com.mi.globalbrowser", list="MIUI",
        description="Xiaomi Mi Browser. Replaceable with Firefox, Brave, or Chrome.",
        labels=listOf("MIUI","Bloatware"), removal="Recommended"))
    put("com.miui.systemAdSolution", UadAppData(
        id="com.miui.systemAdSolution", list="MIUI",
        description="MIUI system-level ad delivery service. Core advertising component from Xiaomi.",
        labels=listOf("MIUI","Ads"), removal="Recommended"))
    put("com.miui.fm", UadAppData(
        id="com.miui.fm", list="MIUI",
        description="MIUI FM Radio app.",
        labels=listOf("MIUI","Bloatware"), removal="Recommended"))
    put("com.miui.notes", UadAppData(
        id="com.miui.notes", list="MIUI",
        description="MIUI Notes app. Replaceable with open-source note apps.",
        labels=listOf("MIUI","Bloatware"), removal="Recommended"))
    put("com.xiaomi.scanner", UadAppData(
        id="com.xiaomi.scanner", list="MIUI",
        description="MIUI QR code and barcode scanner.",
        labels=listOf("MIUI","Bloatware"), removal="Recommended"))
    put("com.miui.mishare.connectivity", UadAppData(
        id="com.miui.mishare.connectivity", list="MIUI",
        description="Mi Share wireless file transfer between MIUI devices.",
        labels=listOf("MIUI"), removal="Recommended"))

    // ── OnePlus / OxygenOS ────────────────────────────────────────────────────
    put("com.oneplus.brickmode", UadAppData(
        id="com.oneplus.brickmode", list="OxygenOS",
        description="OnePlus Zen Mode (Brick Mode). Blocks phone use for set intervals.",
        labels=listOf("OnePlus","Bloatware"), removal="Recommended"))
    put("com.oneplus.weather", UadAppData(
        id="com.oneplus.weather", list="OxygenOS",
        description="OxygenOS weather app.",
        labels=listOf("OnePlus","Bloatware"), removal="Recommended"))
    put("com.oneplus.gallery", UadAppData(
        id="com.oneplus.gallery", list="OxygenOS",
        description="OnePlus Gallery app.",
        labels=listOf("OnePlus","Bloatware"), removal="Recommended"))
    put("net.oneplus.odm", UadAppData(
        id="net.oneplus.odm", list="OxygenOS",
        description="OnePlus Device Manager — telemetry and analytics reporting to OnePlus servers.",
        labels=listOf("OnePlus","Analytics"), removal="Recommended"))
    put("com.oneplus.logkit", UadAppData(
        id="com.oneplus.logkit", list="OxygenOS",
        description="OnePlus diagnostic and log collection tool.",
        labels=listOf("OnePlus","Analytics"), removal="Recommended"))

    // ── Huawei / EMUI ─────────────────────────────────────────────────────────
    put("com.huawei.appmarket", UadAppData(
        id="com.huawei.appmarket", list="EMUI",
        description="Huawei AppGallery app store. Safe to remove if using another store.",
        labels=listOf("Huawei","Bloatware"), removal="Recommended"))
    put("com.huawei.android.tips", UadAppData(
        id="com.huawei.android.tips", list="EMUI",
        description="Huawei Tips app — shows usage tips on screen.",
        labels=listOf("Huawei","Bloatware"), removal="Recommended"))
    put("com.huawei.health", UadAppData(
        id="com.huawei.health", list="EMUI",
        description="Huawei Health — health tracking and Huawei Watch syncing app.",
        labels=listOf("Huawei","Bloatware"), removal="Recommended"))
    put("com.huawei.hicloud", UadAppData(
        id="com.huawei.hicloud", list="EMUI",
        description="Huawei Cloud sync and backup service.",
        labels=listOf("Huawei"), removal="Recommended"))

    // ── Carrier / Operator ────────────────────────────────────────────────────
    put("com.att.myatt", UadAppData(
        id="com.att.myatt", list="Carrier",
        description="AT&T My Account management app. Replaceable with the AT&T website.",
        labels=listOf("Carrier","Bloatware"), removal="Recommended"))
    put("com.att.tv", UadAppData(
        id="com.att.tv", list="Carrier",
        description="AT&T TV / DirecTV streaming app.",
        labels=listOf("Carrier","Bloatware"), removal="Recommended"))
    put("com.verizon.messaging.vzmsgs", UadAppData(
        id="com.verizon.messaging.vzmsgs", list="Carrier",
        description="Verizon Messages SMS replacement app.",
        labels=listOf("Carrier","Bloatware"), removal="Recommended"))
    put("com.verizon.myverizon", UadAppData(
        id="com.verizon.myverizon", list="Carrier",
        description="My Verizon account management app.",
        labels=listOf("Carrier","Bloatware"), removal="Recommended"))
    put("com.tmobile.pr.mytmobile", UadAppData(
        id="com.tmobile.pr.mytmobile", list="Carrier",
        description="T-Mobile account management app.",
        labels=listOf("Carrier","Bloatware"), removal="Recommended"))
    put("com.tmobile.pr.adapt", UadAppData(
        id="com.tmobile.pr.adapt", list="Carrier",
        description="T-Mobile Adaptive Sound noise reduction service.",
        labels=listOf("Carrier"), removal="Recommended"))
    put("com.sprint.ms.smf.services", UadAppData(
        id="com.sprint.ms.smf.services", list="Carrier",
        description="Sprint/T-Mobile core services framework.",
        labels=listOf("Carrier"), removal="Expert"))

    // ── Facebook / Meta ────────────────────────────────────────────────────────
    put("com.facebook.system", UadAppData(
        id="com.facebook.system", list="Facebook",
        description="Facebook System App — background Facebook services stub. Safe to remove; Facebook still works through the app.",
        labels=listOf("Facebook","Ads","Analytics"), removal="Recommended"))
    put("com.facebook.services", UadAppData(
        id="com.facebook.services", list="Facebook",
        description="Facebook App Services background process. Only needed if using Facebook app; otherwise safe to remove.",
        labels=listOf("Facebook","Analytics"), removal="Recommended"))
    put("com.facebook.appmanager", UadAppData(
        id="com.facebook.appmanager", list="Facebook",
        description="Facebook App Manager — background downloader for Facebook-related APKs.",
        labels=listOf("Facebook","Bloatware"), removal="Recommended"))
    put("com.facebook.katana", UadAppData(
        id="com.facebook.katana", list="Facebook",
        description="Facebook main app. Safe to remove and use the mobile website instead.",
        labels=listOf("Facebook"), removal="Recommended"))
    put("com.facebook.orca", UadAppData(
        id="com.facebook.orca", list="Facebook",
        description="Facebook Messenger standalone app.",
        labels=listOf("Facebook"), removal="Recommended"))
    put("com.instagram.android", UadAppData(
        id="com.instagram.android", list="Facebook",
        description="Instagram — Meta social media app.",
        labels=listOf("Facebook"), removal="Recommended"))

    // ── Microsoft ─────────────────────────────────────────────────────────────
    put("com.microsoft.office.officelens", UadAppData(
        id="com.microsoft.office.officelens", list="Microsoft",
        description="Microsoft Office Lens document scanner.",
        labels=listOf("Microsoft","Bloatware"), removal="Recommended"))
    put("com.microsoft.launcher", UadAppData(
        id="com.microsoft.launcher", list="Microsoft",
        description="Microsoft Launcher pre-installed on some OEM phones.",
        labels=listOf("Microsoft","Bloatware"), removal="Recommended"))
    put("com.microsoft.teams", UadAppData(
        id="com.microsoft.teams", list="Microsoft",
        description="Microsoft Teams communication app.",
        labels=listOf("Microsoft","Bloatware"), removal="Recommended"))
    put("com.microsoft.office.word", UadAppData(
        id="com.microsoft.office.word", list="Microsoft",
        description="Microsoft Word office app pre-installed on some phones.",
        labels=listOf("Microsoft","Bloatware"), removal="Recommended"))
    put("com.microsoft.office.excel", UadAppData(
        id="com.microsoft.office.excel", list="Microsoft",
        description="Microsoft Excel spreadsheet app.",
        labels=listOf("Microsoft","Bloatware"), removal="Recommended"))
    put("com.microsoft.office.powerpoint", UadAppData(
        id="com.microsoft.office.powerpoint", list="Microsoft",
        description="Microsoft PowerPoint presentation app.",
        labels=listOf("Microsoft","Bloatware"), removal="Recommended"))
    put("com.skype.raider", UadAppData(
        id="com.skype.raider", list="Microsoft",
        description="Skype VoIP calling app.",
        labels=listOf("Microsoft","Bloatware"), removal="Recommended"))
    put("com.microsoft.appmanager", UadAppData(
        id="com.microsoft.appmanager", list="Microsoft",
        description="Microsoft App Manager — installs Microsoft companion apps silently on some OEM devices.",
        labels=listOf("Microsoft","Bloatware"), removal="Recommended"))

    // ── Analytics / Ads ────────────────────────────────────────────────────────
    put("com.amazon.appmanager", UadAppData(
        id="com.amazon.appmanager", list="Amazon",
        description="Amazon App Manager pre-installed on some Motorola and other OEM devices.",
        labels=listOf("Amazon","Bloatware"), removal="Recommended"))
    put("com.amazon.mShop.android.shopping", UadAppData(
        id="com.amazon.mShop.android.shopping", list="Amazon",
        description="Amazon Shopping app.",
        labels=listOf("Amazon","Bloatware"), removal="Recommended"))
    put("com.android.backupconfirm", UadAppData(
        id="com.android.backupconfirm", list="AOSP",
        description="ADB backup confirmation dialog. Safe to remove if not using ADB backup.",
        labels=listOf("AOSP"), removal="Advanced"))
    put("com.qualcomm.qti.perfdump", UadAppData(
        id="com.qualcomm.qti.perfdump", list="Qualcomm",
        description="Qualcomm performance data collection. Sends benchmarking data to Qualcomm.",
        labels=listOf("Analytics"), removal="Recommended"))
    put("com.qualcomm.atfwd", UadAppData(
        id="com.qualcomm.atfwd", list="Qualcomm",
        description="Qualcomm AT command forwarder — used for manufacturing diagnostics. Safe to remove on consumer devices.",
        labels=listOf("Analytics"), removal="Recommended"))

    // ── AOSP extras ───────────────────────────────────────────────────────────
    put("com.android.dreams.basic", UadAppData(
        id="com.android.dreams.basic", list="AOSP",
        description="Basic daydream screensaver (color patterns). Safe to remove.",
        labels=listOf("AOSP"), removal="Recommended"))
    put("com.android.dreams.phototable", UadAppData(
        id="com.android.dreams.phototable", list="AOSP",
        description="Photo table / flip daydream screensaver.",
        labels=listOf("AOSP"), removal="Recommended"))
    put("com.android.wallpaper.livepicker", UadAppData(
        id="com.android.wallpaper.livepicker", list="AOSP",
        description="Live wallpaper picker app.",
        labels=listOf("AOSP"), removal="Advanced"))
    put("com.google.android.feedback", UadAppData(
        id="com.google.android.feedback", list="Google",
        description="Google Feedback data collection service.",
        labels=listOf("Google","Analytics"), removal="Recommended"))
    put("com.android.printservice.recommendation", UadAppData(
        id="com.android.printservice.recommendation", list="AOSP",
        description="Print service recommendation plugin. Shows suggestions for printer apps.",
        labels=listOf("AOSP"), removal="Recommended"))
    put("com.android.stk", UadAppData(
        id="com.android.stk", list="AOSP",
        description="SIM Toolkit app for carrier-provided SIM menus. Safe to remove if your SIM doesn't provide a SIM menu.",
        labels=listOf("AOSP","Carrier"), removal="Advanced"))
    put("com.android.egg", UadAppData(
        id="com.android.egg", list="AOSP",
        description="Android Easter Egg (hidden game/animation). 100% safe to remove.",
        labels=listOf("AOSP"), removal="Recommended"))
}

// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class DebloatViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DebloatUiState())
    val state: StateFlow<DebloatUiState> = _state.asStateFlow()

    // All known labels for filter chips
    val allLabels = listOf("Google", "Samsung", "MIUI", "OnePlus", "Huawei", "Carrier",
        "Facebook", "Microsoft", "Amazon", "Analytics", "Ads", "AOSP", "Bloatware")

    init { loadApps() }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val packages = connectionManager.listPackages()
                val models = packages.map { pkg ->
                    val label = pkg.packageName.split(".")
                        .lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg.packageName
                    DebloatAppModel(
                        packageName = pkg.packageName,
                        appName     = label,
                        isSystemApp = pkg.isSystem,
                        isEnabled   = pkg.isEnabled,
                        isFrozen    = !pkg.isEnabled,
                        uadData     = UAD_DB[pkg.packageName],
                    )
                }
                _state.update { it.copy(apps = models, isLoading = false) }
                applyFilters()
            } catch (e: Exception) {
                Timber.e(e, "DebloatViewModel: failed to load apps")
                _state.update { it.copy(isLoading = false, snackbarMessage = "Failed to load apps — check connection") }
            }
        }
    }

    fun loadRemovedApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoadingRemoved = true) }
            try {
                // pm list packages -u lists ALL packages including uninstalled ones
                val allOutput = connectionManager.exec("pm list packages -u 2>/dev/null").output
                val allPkgs = allOutput.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .toSet()
                val installedPkgs = _state.value.apps.map { it.packageName }.toSet()
                val removedPkgNames = allPkgs - installedPkgs
                val removed = removedPkgNames.map { pkg ->
                    val label = pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                    DebloatAppModel(
                        packageName = pkg,
                        appName     = label,
                        isSystemApp = true,
                        isEnabled   = false,
                        isFrozen    = false,
                        isRemoved   = true,
                        uadData     = UAD_DB[pkg],
                    )
                }.sortedBy { it.appName }
                _state.update { it.copy(removedApps = removed, isLoadingRemoved = false) }
            } catch (e: Exception) {
                Timber.e(e, "DebloatViewModel: failed to load removed apps")
                _state.update { it.copy(isLoadingRemoved = false, snackbarMessage = "Failed to scan removed packages") }
            }
        }
    }

    fun onTabChange(tab: DebloatTab) {
        _state.update { it.copy(selectedTab = tab, selectedApps = emptySet(), isMultiSelect = false) }
        if (tab == DebloatTab.REMOVED && _state.value.removedApps.isEmpty()) loadRemovedApps()
        applyFilters()
    }

    fun onSearchChange(q: String) {
        _state.update { it.copy(searchQuery = q) }
        applyFilters()
    }

    fun toggleSafety(safety: String) {
        _state.update { s ->
            val set = s.selectedSafety.toMutableSet()
            if (!set.add(safety)) set.remove(safety)
            s.copy(selectedSafety = set)
        }
        applyFilters()
    }

    fun selectLabel(label: String?) {
        _state.update { it.copy(selectedLabel = if (it.selectedLabel == label) null else label) }
        applyFilters()
    }

    fun onSortChange(sort: DebloatSortOrder) {
        _state.update { it.copy(sortOrder = sort) }
        applyFilters()
    }

    fun clearFilters() {
        _state.update { it.copy(selectedSafety = emptySet(), selectedLabel = null, searchQuery = "") }
        applyFilters()
    }

    private fun applyFilters() {
        val s = _state.value
        var list = when (s.selectedTab) {
            DebloatTab.SYSTEM  -> s.apps.filter { it.isSystemApp }
            DebloatTab.USER    -> s.apps.filter { !it.isSystemApp }
            DebloatTab.REMOVED -> s.removedApps
        }
        if (s.searchQuery.isNotBlank()) {
            val q = s.searchQuery.lowercase()
            list = list.filter {
                it.appName.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q) ||
                it.uadDescription.lowercase().contains(q)
            }
        }
        if (s.selectedSafety.isNotEmpty()) {
            list = list.filter { it.safetyLabel in s.selectedSafety }
        }
        if (s.selectedLabel != null) {
            list = list.filter { s.selectedLabel in it.uadLabels }
        }
        list = when (s.sortOrder) {
            DebloatSortOrder.NAME_ASC  -> list.sortedBy { it.appName }
            DebloatSortOrder.NAME_DESC -> list.sortedByDescending { it.appName }
            DebloatSortOrder.SAFETY    -> list.sortedWith(compareBy { safetyOrder(it.safetyLabel) })
            DebloatSortOrder.HAS_UAD   -> list.sortedByDescending { it.uadData != null }
        }
        _state.update { it.copy(filteredApps = list) }
    }

    private fun safetyOrder(label: String) = when (label) {
        "Recommended" -> 0; "Advanced" -> 1; "Expert" -> 2; "Unsafe" -> 3; else -> 4
    }

    fun showDetail(app: DebloatAppModel?) { _state.update { it.copy(detailApp = app) } }

    // ── Single-app operations ─────────────────────────────────────────────────

    fun removeForUser(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.uninstallForUser(packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "Removed $packageName for current user" else "Failed to remove — check connection", detailApp = null) }
            if (ok) loadApps()
        }
    }

    fun disableApp(packageName: String) {
        viewModelScope.launch {
            val ok = connectionManager.exec("pm disable-user --user 0 $packageName").isSuccess
            _state.update { it.copy(snackbarMessage = if (ok) "Disabled $packageName" else "Failed to disable", detailApp = null) }
            if (ok) loadApps()
        }
    }

    fun enableApp(packageName: String) {
        viewModelScope.launch {
            val ok = connectionManager.exec("pm enable --user 0 $packageName").isSuccess
            _state.update { it.copy(snackbarMessage = if (ok) "Enabled $packageName" else "Failed to enable", detailApp = null) }
            if (ok) loadApps()
        }
    }

    fun suspendApp(packageName: String) {
        viewModelScope.launch {
            val ok = connectionManager.exec("am suspend-packages $packageName").isSuccess
            _state.update { it.copy(snackbarMessage = if (ok) "Suspended $packageName" else "Failed to suspend", detailApp = null) }
            if (ok) loadApps()
        }
    }

    fun unsuspendApp(packageName: String) {
        viewModelScope.launch {
            val ok = connectionManager.exec("am unsuspend-packages $packageName").isSuccess
            _state.update { it.copy(snackbarMessage = if (ok) "Unsuspended $packageName" else "Failed to unsuspend", detailApp = null) }
            if (ok) loadApps()
        }
    }

    fun hideApp(packageName: String) {
        viewModelScope.launch {
            val ok = connectionManager.exec("pm hide --user 0 $packageName").isSuccess
            _state.update { it.copy(snackbarMessage = if (ok) "Hidden $packageName" else "Failed to hide", detailApp = null) }
            if (ok) loadApps()
        }
    }

    fun deepFreeze(packageName: String) {
        viewModelScope.launch {
            val r1 = connectionManager.exec("pm disable-user --user 0 $packageName").isSuccess
            val r2 = connectionManager.exec("am suspend-packages $packageName").isSuccess
            _state.update { it.copy(snackbarMessage = if (r1 || r2) "Deep-frozen $packageName" else "Failed to deep freeze", detailApp = null) }
            if (r1 || r2) loadApps()
        }
    }

    fun removeAllUsers(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.uninstallCompletely(packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "Removed $packageName for ALL users (root)" else "Failed — root required", detailApp = null) }
            if (ok) loadApps()
        }
    }

    fun restoreApp(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.reinstallForUser(packageName)
            _state.update { s ->
                val newRemoved = s.removedApps.filter { it.packageName != packageName }
                s.copy(removedApps = newRemoved, snackbarMessage = if (ok) "Restored $packageName" else "Failed to restore — check connection")
            }
            if (ok) loadApps()
        }
    }

    fun forceStop(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.forceStop(packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "Force stopped $packageName" else "Failed to force stop") }
        }
    }

    fun clearData(packageName: String) {
        viewModelScope.launch {
            val ok = appRepository.clearData(packageName)
            _state.update { it.copy(snackbarMessage = if (ok) "Data cleared for $packageName" else "Failed to clear data") }
        }
    }

    // ── Multi-select ─────────────────────────────────────────────────────────

    fun toggleMultiSelect() { _state.update { it.copy(isMultiSelect = !it.isMultiSelect, selectedApps = emptySet()) } }

    fun toggleSelection(pkg: String) {
        _state.update { s ->
            val sel = s.selectedApps.toMutableSet()
            if (!sel.add(pkg)) sel.remove(pkg)
            s.copy(selectedApps = sel)
        }
    }

    fun selectAll() {
        val all = _state.value.filteredApps.map { it.packageName }.toSet()
        _state.update { it.copy(selectedApps = all) }
    }

    fun clearSelection() { _state.update { it.copy(selectedApps = emptySet()) } }

    // ── Batch operations ──────────────────────────────────────────────────────

    fun batchRemoveForUser() { batchOp("remove") }
    fun batchDisable() { batchOp("disable") }
    fun batchFreeze() { batchOp("freeze") }

    private fun batchOp(action: String) {
        val pkgs = _state.value.selectedApps.toList()
        if (pkgs.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isBatchRunning = true, batchTotal = pkgs.size, batchProgress = 0, isMultiSelect = false, selectedApps = emptySet()) }
            var succeeded = 0
            pkgs.forEachIndexed { i, pkg ->
                val ok = when (action) {
                    "remove"  -> appRepository.uninstallForUser(pkg)
                    "disable" -> connectionManager.exec("pm disable-user --user 0 $pkg").isSuccess
                    "freeze"  -> connectionManager.exec("pm disable-user --user 0 $pkg").isSuccess.also {
                        if (it) connectionManager.exec("am suspend-packages $pkg")
                    }
                    else -> false
                }
                if (ok) succeeded++
                _state.update { it.copy(batchProgress = i + 1) }
            }
            val label = when (action) { "remove" -> "removed"; "disable" -> "disabled"; else -> "frozen" }
            _state.update { it.copy(isBatchRunning = false, snackbarMessage = "$succeeded/${pkgs.size} apps $label") }
            loadApps()
        }
    }

    fun batchRestoreSelected() {
        val pkgs = _state.value.selectedApps.toList()
        if (pkgs.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isBatchRunning = true, batchTotal = pkgs.size, batchProgress = 0, isMultiSelect = false, selectedApps = emptySet()) }
            var succeeded = 0
            pkgs.forEachIndexed { i, pkg ->
                if (appRepository.reinstallForUser(pkg)) succeeded++
                _state.update { it.copy(batchProgress = i + 1) }
            }
            _state.update { it.copy(isBatchRunning = false, snackbarMessage = "$succeeded/${pkgs.size} apps restored") }
            loadRemovedApps()
            loadApps()
        }
    }

    // ── Preset actions ────────────────────────────────────────────────────────

    fun selectPreset(label: String) {
        val matches = _state.value.filteredApps
            .filter { label in it.uadLabels && it.safetyLabel != "Unsafe" }
            .map { it.packageName }.toSet()
        _state.update { it.copy(selectedApps = matches, isMultiSelect = true) }
    }

    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
}
