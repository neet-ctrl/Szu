# Android Control Center Ultimate (ACC Ultimate)

A comprehensive Android system management application that merges the functionality of **17 open-source apps** into a single, cohesive Material 3 experience.

---

## Features

| Feature Area | Source App | Capability |
|---|---|---|
| **Shizuku Center** | Shizuku | ADB-over-WiFi privilege bridge, permission management, status dashboard |
| **Shell** | aShellYou | Interactive ADB shell with history, syntax highlighting, command suggestions |
| **App Debloat** | Canta | Safe uninstall/disable of system & carrier bloatware with community lists |
| **App Freeze** | Hail | Suspend apps via Shizuku (no root needed) to reclaim resources |
| **App Inspector** | Inure | Full app details: storage, permissions, activities, services, receivers, providers |
| **Component Manager** | Blocker | Enable/disable individual Activities, Services, Receivers, Providers |
| **Theming** | ColorBlendr | Material You color palette editor, seed color picker, per-surface overrides |
| **Dark Mode** | DarQ | Per-app forced dark mode, scheduled dark hours, system-wide control |
| **Widgets** | SmartSpacer | Lock-screen / notification bar widget management |
| **Storage** | SD Maid SE | Junk cleaner, duplicate finder, large-file browser, empty-folder sweeper |
| **File Manager** | Material Files | Root-capable file manager with archive support and SMB browsing |
| **APK Installer** | InstallWithOptions | Install APKs with downgrade, test-package, grant-all-permissions flags |
| **Key Mapper** | Key Mapper | Remap hardware buttons to any action without root via accessibility |
| **Language** | Language Selector | Per-app language overrides + system locale switcher via Shizuku |
| **Internet Tiles** | Better Internet Tiles | Wi-Fi, Mobile Data, Hotspot quick-setting tiles that actually toggle |
| **Audio DSP** | RootlessJamesDSP | System-wide equalizer, bass boost, reverb, convolver (no root) |
| **Call Recording** | ShizuCallRecorder | Rootless call recording via scrcpy audio capture + Shizuku |

---

## Requirements

- Android 10+ (API 29+)
- **[Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)** — required for most privileged operations
- Root access (optional) — unlocks additional capabilities

---

## Building

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- Android SDK with Build Tools 35+

### Debug Build
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/
```

### Release Build
```bash
# Set environment variables or edit signingConfigs in app/build.gradle.kts
export KEYSTORE_PATH=keystore/release.keystore
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=your_alias
export KEY_PASSWORD=your_key_password

./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```

### GitHub Actions (CI/CD)
Push to `main`/`master`/`develop` → automatic debug APK build & upload.  
Push a tag (`v1.0.0`) → debug + release APKs + GitHub Release created.  
Manual trigger → choose `debug` or `release` from the Actions tab.

**Required GitHub Secrets for release builds:**
| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` or `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

---

## Project Structure

```
app/src/main/
├── java/com/accu/
│   ├── ACCApplication.kt          — Hilt application, Timber setup, LibSU config
│   ├── MainActivity.kt            — Single-activity host with edge-to-edge
│   ├── data/
│   │   ├── db/                    — Room database (16 entities, 16 DAOs)
│   │   └── repositories/          — AppRepository, ShellRepository
│   ├── di/                        — Hilt modules (AppModule, DatabaseModule)
│   ├── domain/usecases/           — Clean use-case layer
│   ├── navigation/                — NavRoutes, AppNavigation (5 bottom tabs)
│   ├── receivers/                 — BootReceiver, CallStateReceiver, PackageChangeReceiver
│   ├── services/
│   │   ├── ACCAccessibilityService.kt  — Key mapper accessibility service
│   │   ├── AudioEffectService.kt       — RootlessJamesDSP foreground service
│   │   ├── CallRecordingService.kt     — Rootless call recording service
│   │   ├── ShizukuUserService.kt       — Elevated Shizuku IPC service
│   │   ├── WiFiTileService.kt          — Wi-Fi quick-settings tile
│   │   ├── MobileDataTileService.kt    — Mobile data quick-settings tile
│   │   └── HotspotTileService.kt       — Hotspot quick-settings tile
│   ├── ui/
│   │   ├── dashboard/             — Dashboard overview + status cards
│   │   ├── shizuku/               — Shizuku center + setup wizard
│   │   ├── shell/                 — Interactive shell screen
│   │   ├── appmanager/            — App list, detail, debloat, freeze, components, permissions
│   │   ├── audio/                 — Equalizer, bass boost, DSP controls
│   │   ├── callrecorder/          — Call recording with playback
│   │   ├── customization/         — Color editor, dark mode, Monet theming
│   │   ├── filemanager/           — File browser with root support
│   │   ├── installer/             — APK installer with options
│   │   ├── language/              — Language selector (per-app + system)
│   │   ├── network/               — Network center + tile controls
│   │   ├── onboarding/            — Welcome flow + learning center
│   │   ├── privacy/               — Privacy dashboard + app ops
│   │   ├── settings/              — App settings
│   │   ├── storage/               — Storage analyzer + cleaner
│   │   ├── theme/                 — Material 3 Color, Typography, Theme
│   │   ├── widgets/               — SmartSpacer widget management
│   │   └── automation/            — Key mapper automation rules
│   ├── utils/ShizukuUtils.kt      — Shizuku bind/permission helpers
│   └── workers/CleanupWorker.kt   — Periodic junk cleanup WorkManager task
└── res/
    ├── drawable/                  — Vector drawables (tile icons, launcher)
    ├── mipmap-*/                  — Adaptive launcher icons
    ├── values/                    — strings.xml, themes.xml, colors.xml
    ├── values-night/              — Night theme override
    └── xml/                       — accessibility_service_config, file_paths,
                                     backup_rules, data_extraction_rules, locales_config
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 Expressive |
| Architecture | MVVM + Clean Architecture (UseCases) |
| DI | Hilt 2.54 |
| Database | Room 2.7 + Drizzle-style entity layer |
| Async | Kotlin Coroutines + Flow |
| Privileged ops | Shizuku 13.1.5 SDK |
| Root ops | LibSU 5.3.0 |
| Audio | Android AudioEffect API (no-root DSP) |
| Background | WorkManager |
| Image loading | Coil |
| Build | Gradle 8.11 + AGP 8.7 + KSP |
| CI/CD | GitHub Actions |

---

## Privacy

- No telemetry, no analytics, no network calls home
- All operations performed locally on-device
- Call recordings stored only in app-private storage
- Backup excludes sensitive Shizuku session data

---

## License

This project is released under the **Apache License 2.0**.  
Each integrated feature draws inspiration from its respective open-source project — see their individual repositories for their licenses.
