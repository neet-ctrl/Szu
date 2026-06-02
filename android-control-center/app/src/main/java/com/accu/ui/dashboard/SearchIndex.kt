package com.accu.ui.dashboard

object SearchIndex {

    val entries: List<SearchResult> = listOf(

        // ── Apps ──────────────────────────────────────────────────────────────
        SearchResult("App Manager",          "Browse, sort & batch-act on all installed apps",           "app_manager",              "apps",                "Apps",          listOf("inure","package","installed","user","system","manage")),
        SearchResult("Debloat",              "Remove system bloatware safely with Canta",                "debloat",                  "delete",              "Apps",          listOf("canta","bloatware","uninstall","system app","remove","clean")),
        SearchResult("Freeze Apps",          "Suspend & hide apps via Hail",                             "freeze_apps",              "ac_unit",             "Apps",          listOf("hail","suspend","disable","hide","freeze","ice")),
        SearchResult("Freeze Scheduler",     "Auto-freeze / unfreeze apps on a schedule",                "freeze_scheduler",         "schedule",            "Apps",          listOf("hail","schedule","auto","timer","cron","automated")),
        SearchResult("Component Manager",    "Disable activities, services & receivers",                 "component_manager",        "settings",            "Apps",          listOf("blocker","disable","activity","service","receiver","broadcast","component")),
        SearchResult("Permission Manager",   "View & grant/revoke app permissions",                      "permission_manager",       "admin_panel_settings","Apps",          listOf("permission","grant","revoke","runtime","allow","deny")),
        SearchResult("App Explorer",         "Deep-dive into any APK's internals",                       "app_explorer",             "search",              "Apps",          listOf("apk","manifest","explore","package","inure","inspect")),
        SearchResult("App Cleaner",          "Clear app caches & data (SD Maid SE)",                     "app_cleaner",              "delete",              "Apps",          listOf("sd maid","cache","clear data","clean","sdmaid")),
        SearchResult("Inure Home",           "Inure app suite — analytics & tools hub",                  "inure_home",               "apps",                "Apps",          listOf("inure","suite","analytics","hub","tools")),
        SearchResult("Battery Optimization", "Manage per-app battery optimization",                      "inure_battery_opt",        "battery_full",        "Apps",          listOf("inure","battery","doze","optimization","background","standby")),
        SearchResult("Boot Manager",         "Enable or disable apps at boot",                           "inure_boot_manager",       "power_settings_new",  "Apps",          listOf("inure","boot","startup","autostart","launch")),
        SearchResult("App Notes",            "Per-app notes & logs (Inure)",                             "inure_notes",              "note",                "Apps",          listOf("inure","notes","log","memo","annotation")),
        SearchResult("Inure Music",          "Built-in music player (Inure)",                            "inure_music",              "music_note",          "Apps",          listOf("inure","music","player","audio","media")),
        SearchResult("APK Manager",          "Browse & extract installed APK files",                     "inure_apks",               "folder",              "Apps",          listOf("inure","apk","extract","backup","split")),
        SearchResult("Tracker Scanner",      "Scan apps for embedded trackers (Inure)",                  "inure_trackers",           "security",            "Apps",          listOf("inure","trackers","scan","privacy","exodus","spy")),
        SearchResult("Usage Stats",          "Per-app screen time & data usage",                         "inure_usage_stats",        "bar_chart",           "Apps",          listOf("inure","usage","screen time","stats","analytics","time")),
        SearchResult("Disabled Apps",        "List and re-enable disabled apps (Inure)",                 "inure_disabled_apps",      "block",               "Apps",          listOf("inure","disabled","enable","blocked","list")),
        SearchResult("Most Used Apps",       "Apps ranked by foreground usage time",                     "inure_most_used",          "trending_up",         "Apps",          listOf("inure","most used","screen time","foreground","usage","top")),
        SearchResult("Recently Installed",   "Apps installed or updated in recent days",                  "inure_recently_installed", "install_mobile",      "Apps",          listOf("inure","installed","recently","updated","new","fresh")),
        SearchResult("Uninstalled Apps",     "Packages removed for current user",                         "inure_uninstalled",        "delete_outline",      "Apps",          listOf("inure","uninstalled","removed","deleted","history","restore")),
        SearchResult("App Tags",             "Organize apps with custom color labels",                    "inure_tags",               "label",               "Apps",          listOf("inure","tags","labels","organize","group","categorize")),
        SearchResult("FOSS Apps",            "Detect free & open-source apps by installer",              "inure_foss",               "volunteer_activism",   "Apps",          listOf("inure","foss","open source","fdroid","aurora","libre","free")),
        SearchResult("Canta Presets",        "Saved Canta debloat preset configurations",                "canta_presets",            "bookmark",            "Apps",          listOf("canta","preset","debloat","profile","saved")),
        SearchResult("Canta Logs",           "Debloat operation history & logs",                         "canta_logs",               "history",             "Apps",          listOf("canta","log","history","debloat","undo")),
        SearchResult("Hail Work Profile",    "Freeze apps inside a work profile",                        "hail_work_profile",        "work",                "Apps",          listOf("hail","work profile","freeze","managed","enterprise")),
        SearchResult("Virus Scan",           "Scan APKs against VirusTotal",                             "virus_scan",               "security",            "Apps",          listOf("virus","malware","scan","virustotal","security","threat")),
        SearchResult("Blocker Search",       "Search & disable components across all apps",              "blocker_component_search", "search",              "Apps",          listOf("blocker","component","disable","search","activity","bulk")),
        SearchResult("Inure Analytics",      "App install, permission & usage analytics",                "inure_analytics",          "bar_chart",           "Apps",          listOf("inure","analytics","stats","permission","install","chart")),
        SearchResult("Install Flags",        "Set custom flags when sideloading APKs",                   "install_flags",            "flag",                "Apps",          listOf("install","flags","apk","package manager","options","custom")),

        // ── Shell & ADB ───────────────────────────────────────────────────────
        SearchResult("Shell Terminal",       "Execute ADB / root shell commands",                        "shell",                    "terminal",            "Shell & ADB",   listOf("terminal","bash","sh","command","cli","adb","shell")),
        SearchResult("ACCU Center",           "Manage ACCU connection & wireless ADB",                    "accu_center",              "hub",                 "Shell & ADB",   listOf("accu","adb","wireless","elevated","service","binder","connection")),
        SearchResult("ACCU Apps",            "Apps currently authorized to use ACCU",                    "accu_apps",                "apps",                "Shell & ADB",   listOf("accu","authorized","permission","binder","apps")),
        SearchResult("ADB Pairing",          "Pair device over Wi-Fi ADB (mDNS)",                        "adb_pairing",              "wifi",                "Shell & ADB",   listOf("adb","wireless","pair","connect","tcp","wifi")),
        SearchResult("Script Editor",        "Write & run multi-line shell scripts",                     "script_editor",            "code",                "Shell & ADB",   listOf("script","shell","editor","bash","run","write")),
        SearchResult("Command Examples",     "Curated library of useful ADB commands",                   "command_examples",         "terminal",            "Shell & ADB",   listOf("examples","commands","library","adb","reference","cheatsheet")),
        SearchResult("Scrcpy Integration",   "Mirror & control device screen on PC",                     "scrcpy_integration",       "cast",                "Shell & ADB",   listOf("scrcpy","mirror","cast","screen","remote","control","pc")),
        SearchResult("Logcat",               "Real-time logcat viewer — filter by tag, level & search",  "adb_logcat",               "article",             "Shell & ADB",   listOf("logcat","logs","real-time","filter","level","tag","debug","verbose","error","crash")),
        SearchResult("Process Manager",      "View running processes & services, kill by PID",            "adb_processes",            "speed",               "Shell & ADB",   listOf("process","processes","ps","kill","pid","cpu","ram","services","top","running")),
        SearchResult("Device Info",          "Full hardware & software info — CPU, RAM, battery, build",  "adb_device_info",          "phone_android",       "Shell & ADB",   listOf("device","info","hardware","android version","api level","cpu","ram","battery","model","serial")),
        SearchResult("Fastboot & Control",   "Reboot modes, device control keys, fastboot flash commands","adb_fastboot",             "developer_mode",      "Shell & ADB",   listOf("fastboot","reboot","bootloader","recovery","flash","oem","unlock","boot","input","keyevent")),
        SearchResult("Screen Capture",       "Capture screenshots & record screen via ADB",               "adb_screen_capture",       "screenshot",          "Shell & ADB",   listOf("screenshot","screencap","screenrecord","record","capture","video","mp4","png","pull")),
        SearchResult("ADB Tutorial",         "Step-by-step guide: OTG ADB, Wi-Fi ADB, all features",     "adb_tutorial",             "school",              "Shell & ADB",   listOf("tutorial","guide","otg","wifi","wireless","connect","how to","adb","pair","usb","setup")),
        SearchResult("Shell QS Tiles",       "Create Quick Settings tiles that run custom shell commands","shell_qs_tile_dashboard",  "grid_view",           "Shell & ADB",   listOf("qs","quick settings","tile","toggle","shell","custom","status bar","notification","aShell","shortcut","one-tap")),

        // ── Storage & Files ───────────────────────────────────────────────────
        SearchResult("Storage Center",       "Overview & tools for storage management",                  "storage",                  "storage",             "Storage",       listOf("storage","clean","disk","space","sd maid","overview")),
        SearchResult("File Manager",         "Full-featured file browser with operations",               "file_manager",             "folder",              "Storage",       listOf("files","browse","copy","move","delete","rename","folder")),
        SearchResult("Advanced File Manager","Root file access & samba/network shares",                  "filemanager_advanced",     "folder",              "Storage",       listOf("files","advanced","ftp","root","samba","network","smb")),
        SearchResult("System Cleaner",       "Remove junk files & system cache (SD Maid SE)",            "system_cleaner",           "delete",              "Storage",       listOf("sd maid","system","junk","cache","clean","temp")),
        SearchResult("Deduplicator",         "Find & remove duplicate files",                            "deduplicator",             "content_copy",        "Storage",       listOf("sd maid","duplicate","copy","identical","hash","twin")),
        SearchResult("Corpse Finder",        "Orphaned data from uninstalled apps",                      "corpse_finder",            "search",              "Storage",       listOf("sd maid","orphan","leftover","uninstalled","clean","stale")),
        SearchResult("Large File Finder",    "Find files consuming the most space",                      "large_file_finder",        "storage",             "Storage",       listOf("large","big","space","size","files","heaviest")),
        SearchResult("Storage Analyzer",     "Visual disk usage breakdown chart",                        "storage_analyzer",         "pie_chart",           "Storage",       listOf("analyzer","chart","visual","disk","treemap","breakdown")),
        SearchResult("Squeezer",             "Compress images to free up space",                         "squeezer",                 "compress",            "Storage",       listOf("squeezer","compress","image","optimize","shrink","jpeg")),
        SearchResult("FTP Server",           "Serve files over local Wi-Fi FTP",                         "ftp_server",               "cloud_upload",        "Storage",       listOf("ftp","server","share","network","transfer","wifi")),
        SearchResult("Installer Center",     "Install APKs with advanced install options",               "installer",                "install_mobile",      "Storage",       listOf("install","apk","sideload","package","obb")),

        // ── Audio & Media ─────────────────────────────────────────────────────
        SearchResult("Sound Master",         "Per-app volume, balance, 3-band EQ & output routing",      "sound_master",             "volume_up",           "Audio",         listOf("soundmaster","volume","eq","equalizer","per-app","boost","audio","bass","treble","balance","output","device","bluetooth","routing")),
        SearchResult("Mixed Audio",          "Per-app audio focus control & mute — no interruptions",    "mixed_audio",              "tune",                "Audio",         listOf("mixedaudio","focus","mute","audio","per-app","batch","silence","ignore","deny","allow","mixed","concurrent")),
        SearchResult("Call Recorder",        "Rootless call recording via ACCU + scrcpy",                "call_recorder",            "call",                "Audio",         listOf("call","record","phone","accu","voip","recording")),
        SearchResult("Recording Settings",   "Call recorder quality, format & storage",                  "call_recording_settings",  "settings",            "Audio",         listOf("recording","settings","quality","format","call","mp3")),

        // ── Privacy & Security ────────────────────────────────────────────────
        SearchResult("Privacy Center",       "Tracker blocker & component disabler (Blocker)",           "privacy",                  "security",            "Privacy",       listOf("blocker","tracker","privacy","firewall","ads","block")),
        SearchResult("Online Rules",         "Manage Blocker online rule / filter lists",                "online_rules",             "cloud",               "Privacy",       listOf("blocker","rules","online","list","import","filter","adblock")),
        SearchResult("Permission Center",    "All permissions for all 91 screens via ACCU",              "permission_center",        "admin_panel_settings","Privacy",       listOf("permission","grant","revoke","accu","runtime","all","manage")),

        // ── Customization ─────────────────────────────────────────────────────
        SearchResult("Customization Hub",    "Material You, themes & per-app visuals",                   "customization",            "palette",             "Customization", listOf("theme","material you","monet","color","customize","personalize")),
        SearchResult("Dark Mode (DarQ)",     "Force dark mode per-app with DarQ",                        "dark_mode",                "dark_mode",           "Customization", listOf("darq","dark mode","force","per app","night","amoled")),
        SearchResult("Color Editor",         "Edit Monet / Material You color palette",                  "color_editor",             "color_lens",          "Customization", listOf("colorblendr","monet","palette","color","material you","edit")),
        SearchResult("ColorBlendr Styles",   "Browse & apply ColorBlendr style presets",                 "colorblendr_styles",       "style",               "Customization", listOf("colorblendr","style","preset","theme","color","scheme")),
        SearchResult("Per-App Theming",      "Apply custom color themes per app",                        "per_app_theming",          "palette",             "Customization", listOf("per app","theme","custom","icon","color","individual")),
        SearchResult("DarQ FAQ",             "Common questions about DarQ dark mode",                    "darq_faq",                 "help",                "Customization", listOf("darq","faq","help","dark mode","questions","how to")),
        SearchResult("Sunrise / Sunset",     "Auto-schedule dark mode by sun position",                  "darq_sunrise_sunset",      "wb_sunny",            "Customization", listOf("darq","sunrise","sunset","schedule","auto dark","solar")),
        SearchResult("DarQ App Picker",      "Choose which apps get forced dark mode",                   "darq_app_picker",          "apps",                "Customization", listOf("darq","apps","picker","select","dark","whitelist")),
        SearchResult("Smartspacer",          "Smartspacer At-a-Glance replacements",                     "smartspacer",              "widgets",             "Customization", listOf("smartspacer","widget","glance","lockscreen","home","pixel")),
        SearchResult("Spacer Targets",       "Add content targets to Smartspacer",                       "smartspacer_targets",      "widgets",             "Customization", listOf("smartspacer","target","content","glance","card")),
        SearchResult("Spacer Complications", "Add complication data to Smartspacer",                     "smartspacer_complications","widgets",             "Customization", listOf("smartspacer","complication","data","glance","source")),
        SearchResult("Widgets Hub",          "All widget configuration in one place",                    "widgets",                  "widgets",             "Customization", listOf("widgets","home screen","glance","at a glance","configure")),
        SearchResult("Language Center",      "Set per-app language (App Language Switcher)",             "language_center",          "language",            "Customization", listOf("language","locale","per app","switcher","region","l10n")),

        // ── Network ───────────────────────────────────────────────────────────
        SearchResult("Network Center",       "Wi-Fi, mobile data & Quick Settings tiles",               "network_center",           "wifi",                "Network",       listOf("wifi","network","mobile data","tiles","vpn","dns","internet")),
        SearchResult("Tiles Settings",       "Configure Quick Settings panel tiles",                    "tiles_settings",           "grid_view",           "Network",       listOf("quick settings","tiles","toggle","panel","status bar","qs")),

        // ── Automation ────────────────────────────────────────────────────────
        SearchResult("Automation Center",    "Key Mapper automation & triggers overview",               "automation",               "keyboard",            "Automation",    listOf("keymapper","automation","trigger","action","gesture","hub")),
        SearchResult("Key Mapper",           "Remap physical buttons & gestures",                       "key_mapper",               "keyboard",            "Automation",    listOf("keymapper","remap","button","gesture","input","shortcut")),
        SearchResult("Key Map List",         "All configured key mappings",                             "key_map_list",             "list",                "Automation",    listOf("keymapper","list","mappings","keys","all")),
        SearchResult("Advanced Key Mapper",  "Floating button, accessibility & advanced settings",      "keymapper_advanced",       "tune",                "Automation",    listOf("keymapper","advanced","floating","overlay","accessibility")),
        SearchResult("Key Mapper Settings",  "Global Key Mapper configuration",                         "keymapper_settings",       "settings",            "Automation",    listOf("keymapper","settings","config","global","options")),
        SearchResult("Key Map Log",          "Log of recent key mapping trigger events",                "key_map_log",              "history",             "Automation",    listOf("keymapper","log","events","trigger","history","debug")),

        // ── ACCU System Service ───────────────────────────────────────────────
        SearchResult("ACCU Service Hub",     "IPC privilege broker — let other apps use ACCU's power", "accu_service_hub",         "api",                 "System",        listOf("ipc","api","service","binder","aidl","privilege","accu","shell","developer","sdk","third-party","connect")),
        SearchResult("ACCU SDK Docs",        "Full developer SDK documentation for connecting apps",    "accu_sdk_docs",            "menu_book",           "System",        listOf("sdk","docs","api","developer","guide","integration","aidl","connect","bind")),

        // ── System & Settings ─────────────────────────────────────────────────
        SearchResult("Settings",             "ACC global app settings & preferences",                   "settings",                 "settings",            "System",        listOf("settings","config","preferences","options","acc","app")),
        SearchResult("Notification Center",  "Per-channel notification control with snooze",            "notification_center",      "notifications",       "System",        listOf("notifications","channels","snooze","alerts","mute","bell")),
        SearchResult("Learning Center",      "Guides, tutorials & documentation",                       "learning_center",          "school",              "System",        listOf("guide","tutorial","help","docs","learn","howto")),
        SearchResult("All Features",         "Browse all 500+ features across 17 apps",                 "all_features",             "apps",                "System",        listOf("features","all","list","browse","discover","catalogue")),
        SearchResult("Tutorial",             "Onboarding walkthrough & quick start",                    "tutorial",                 "school",              "System",        listOf("tutorial","onboarding","start","walkthrough","new user","intro")),

        // ── Crash Center ──────────────────────────────────────────────────────
        SearchResult("Crash Center",         "Crash dashboard — stats, safe mode & export",             "crash_center",             "bug_report",          "System",        listOf("crash","error","exception","bug","stacktrace","anr","fatal","diagnostic","log","report","history")),
        SearchResult("Crash History",        "All crash records — search, filter, sort, export",        "crash_history",            "history",             "System",        listOf("crash","log","history","exception","stack","trace","filter","search","export","bug")),
    )

    val quickLaunch: List<SearchResult> = entries.filter { it.route in setOf(
        "accu_center", "shell", "app_manager", "privacy", "sound_master",
        "storage", "notification_center", "settings", "accu_service_hub",
    ) }
}
