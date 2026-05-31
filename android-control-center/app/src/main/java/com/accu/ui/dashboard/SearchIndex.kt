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
        SearchResult("Canta Presets",        "Saved Canta debloat preset configurations",                "canta_presets",            "bookmark",            "Apps",          listOf("canta","preset","debloat","profile","saved")),
        SearchResult("Canta Logs",           "Debloat operation history & logs",                         "canta_logs",               "history",             "Apps",          listOf("canta","log","history","debloat","undo")),
        SearchResult("Hail Work Profile",    "Freeze apps inside a work profile",                        "hail_work_profile",        "work",                "Apps",          listOf("hail","work profile","freeze","managed","enterprise")),
        SearchResult("Virus Scan",           "Scan APKs against VirusTotal",                             "virus_scan",               "security",            "Apps",          listOf("virus","malware","scan","virustotal","security","threat")),
        SearchResult("Blocker Search",       "Search & disable components across all apps",              "blocker_component_search", "search",              "Apps",          listOf("blocker","component","disable","search","activity","bulk")),
        SearchResult("Inure Analytics",      "App install, permission & usage analytics",                "inure_analytics",          "bar_chart",           "Apps",          listOf("inure","analytics","stats","permission","install","chart")),
        SearchResult("Install Flags",        "Set custom flags when sideloading APKs",                   "install_flags",            "flag",                "Apps",          listOf("install","flags","apk","package manager","options","custom")),

        // ── Shell & ADB ───────────────────────────────────────────────────────
        SearchResult("Shell Terminal",       "Execute ADB / root shell commands",                        "shell",                    "terminal",            "Shell & ADB",   listOf("terminal","bash","sh","command","cli","adb","shell")),
        SearchResult("Shizuku Center",       "Manage Shizuku service & wireless ADB",                    "shizuku_center",           "shizuku",             "Shell & ADB",   listOf("shizuku","adb","wireless","elevated","service","binder")),
        SearchResult("Shizuku Apps",         "Apps currently authorized to use Shizuku",                 "shizuku_apps",             "apps",                "Shell & ADB",   listOf("shizuku","authorized","permission","binder","apps")),
        SearchResult("ADB Pairing",          "Pair device over Wi-Fi ADB (mDNS)",                        "adb_pairing",              "wifi",                "Shell & ADB",   listOf("adb","wireless","pair","connect","tcp","wifi")),
        SearchResult("Script Editor",        "Write & run multi-line shell scripts",                     "script_editor",            "code",                "Shell & ADB",   listOf("script","shell","editor","bash","run","write")),
        SearchResult("Command Examples",     "Curated library of useful ADB commands",                   "command_examples",         "terminal",            "Shell & ADB",   listOf("examples","commands","library","adb","reference","cheatsheet")),
        SearchResult("Scrcpy Integration",   "Mirror & control device screen on PC",                     "scrcpy_integration",       "cast",                "Shell & ADB",   listOf("scrcpy","mirror","cast","screen","remote","control","pc")),

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
        SearchResult("Audio Center",         "JamesDSP audio engine — main hub",                         "audio_center",             "equalizer",           "Audio",         listOf("dsp","audio","equalizer","effects","jamesdsp","sound")),
        SearchResult("Graphic EQ",           "31-band graphic equalizer",                                "graphic_eq",               "equalizer",           "Audio",         listOf("graphic","eq","equalizer","bands","boost","cut")),
        SearchResult("Parametric EQ",        "Parametric equalizer with biquad filters",                 "parametric_eq",            "tune",                "Audio",         listOf("parametric","eq","filter","peak","shelf","biquad")),
        SearchResult("Auto EQ",              "Load AutoEQ calibration profiles",                         "auto_eq",                  "equalizer",           "Audio",         listOf("autoeq","profile","headphone","preset","flat","calibration")),
        SearchResult("Convolution Engine",   "Apply IR impulse / room reverb files",                     "convolution",              "waves",               "Audio",         listOf("convolution","impulse","reverb","IR","room","acoustic")),
        SearchResult("DSP Controls",         "Real-time DSP bass/treble/stereo sliders",                 "dsp_controls",             "tune",                "Audio",         listOf("dsp","bass","treble","reverb","slider","stereo","real-time")),
        SearchResult("JamesDSP Settings",    "JamesDSP engine configuration & options",                  "jamesdsp_settings",        "settings",            "Audio",         listOf("jamesdsp","settings","engine","config","service")),
        SearchResult("Liveprog Editor",      "Script custom DSP effects in Eel language",                "liveprog_editor",          "code",                "Audio",         listOf("liveprog","eel","script","dsp","effect","code")),
        SearchResult("Liveprog Params",      "Live parameter knobs for current Liveprog script",         "liveprog_params",          "tune",                "Audio",         listOf("liveprog","params","live","script","dsp","knob")),
        SearchResult("App Audio Blocklist",  "Exclude specific apps from DSP processing",                "app_audio_blocklist",      "block",               "Audio",         listOf("blocklist","exclude","bypass","dsp","app","whitelist")),
        SearchResult("Call Recorder",        "Rootless call recording via ShizuCallRecorder",            "call_recorder",            "call",                "Audio",         listOf("call","record","phone","shizuku","voip","recording")),
        SearchResult("Recording Settings",   "Call recorder quality, format & storage",                  "call_recording_settings",  "settings",            "Audio",         listOf("recording","settings","quality","format","call","mp3")),

        // ── Privacy & Security ────────────────────────────────────────────────
        SearchResult("Privacy Center",       "Tracker blocker & component disabler (Blocker)",           "privacy",                  "security",            "Privacy",       listOf("blocker","tracker","privacy","firewall","ads","block")),
        SearchResult("Online Rules",         "Manage Blocker online rule / filter lists",                "online_rules",             "cloud",               "Privacy",       listOf("blocker","rules","online","list","import","filter","adblock")),
        SearchResult("Permission Center",    "All permissions for all 91 screens via Shizuku",           "permission_center",        "admin_panel_settings","Privacy",       listOf("permission","grant","revoke","shizuku","runtime","all","manage")),

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

        // ── System & Settings ─────────────────────────────────────────────────
        SearchResult("Settings",             "ACC global app settings & preferences",                   "settings",                 "settings",            "System",        listOf("settings","config","preferences","options","acc","app")),
        SearchResult("Notification Center",  "Per-channel notification control with snooze",            "notification_center",      "notifications",       "System",        listOf("notifications","channels","snooze","alerts","mute","bell")),
        SearchResult("Learning Center",      "Guides, tutorials & documentation",                       "learning_center",          "school",              "System",        listOf("guide","tutorial","help","docs","learn","howto")),
        SearchResult("All Features",         "Browse all 500+ features across 17 apps",                 "all_features",             "apps",                "System",        listOf("features","all","list","browse","discover","catalogue")),
        SearchResult("Tutorial",             "Onboarding walkthrough & quick start",                    "tutorial",                 "school",              "System",        listOf("tutorial","onboarding","start","walkthrough","new user","intro")),
    )

    val quickLaunch: List<SearchResult> = entries.filter { it.route in setOf(
        "shizuku_center", "shell", "app_manager", "privacy", "audio_center",
        "storage", "notification_center", "settings",
    ) }
}
