<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="96" alt="ACC Logo" />

# Android Control Center Ultimate

### One app to rule them all — 17 open-source tools merged into a single Material 3 powerhouse

[![Build](https://img.shields.io/github/actions/workflow/status/your-org/android-control-center/build.yml?branch=main&style=for-the-badge&logo=github&logoColor=white)](../../actions)
[![Version](https://img.shields.io/badge/version-1.0.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](../../releases)
[![API](https://img.shields.io/badge/API-29%2B%20(Android%2010)-34A853?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/10)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-EA4335?style=for-the-badge)](LICENSE)

</div>

---

## What is ACC Ultimate?

**Android Control Center Ultimate** is a native Android app that consolidates **17 best-in-class open-source tools** into a single, unified experience — one install, one UI language, zero redundancy. Every feature has been re-implemented in modern **Kotlin + Jetpack Compose** with a **Material 3 Expressive** design system, keeping the power of each source project while adding cross-tool workflows that were never possible before.

> Requires **[Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)** for most privileged operations. Root access is optional and unlocks additional capabilities.

---

## Feature Overview

| # | Feature Area | Source Project | What You Get |
|---|---|---|---|
| 1 | **Shizuku Center** | [Shizuku](https://github.com/RikkaApps/Shizuku) | ADB-over-WiFi bridge, per-app permission grants, live status dashboard, pairing wizard |
| 2 | **Interactive Shell** | [aShellYou](https://github.com/DP-Hridayan/aShellYou) | Full ADB shell with history, syntax highlighting, command suggestions, AI-powered analysis, Google Drive backup |
| 3 | **App Debloat** | [Canta](https://github.com/samolego/Canta) | Safe uninstall/disable of system & carrier bloatware, community presets, undo logs |
| 4 | **App Freeze** | [Hail](https://github.com/aistra0528/Hail) | Suspend apps via Shizuku (no root), freeze on screen-off, scheduled auto-freeze, QS tile |
| 5 | **App Inspector** | [Inure](https://github.com/Hamza417/Inure) | Deep app info: storage breakdown, permissions, activities, services, receivers, providers, sensors, trackers, VirusTotal scan |
| 6 | **Component Manager** | [Blocker](https://github.com/lihenggui/blocker) | Enable/disable individual Activities, Services, Receivers, Providers with rule import/export |
| 7 | **Monet Theming** | [ColorBlendr](https://github.com/Mahmud0808/ColorBlendr) | Material You color palette editor, seed color picker, Monet style selector, per-surface overrides |
| 8 | **Dark Mode** | [DarQ](https://github.com/KieronQuinn/DarQ) | Per-app forced dark mode, geolocation-based scheduling, sunrise/sunset automation, app picker |
| 9 | **Widgets** | [SmartSpacer](https://github.com/KieronQuinn/SmartSpacer) | Lock-screen and notification bar widget management, complication targets |
| 10 | **Storage & Cleanup** | [SD Maid SE](https://github.com/d4rken-org/sdmaid-se) | Junk cleaner, duplicate finder, large-file browser, empty-folder sweeper, app cleaner, corpse finder |
| 11 | **File Manager** | [Material Files](https://github.com/zhanghai/MaterialFiles) | Root-capable file browser, archive support, SMB/FTP server, text editor, file properties |
| 12 | **APK Installer** | [InstallWithOptions](https://github.com/zacharee/InstallWithOptions) | Install APKs with downgrade, test-package, grant-all-permissions and full session flags |
| 13 | **Key Mapper** | [Key Mapper](https://github.com/keymapperorg/KeyMapper) | Remap hardware buttons, gestures, and volume keys to any action via Accessibility Service |
| 14 | **Language Selector** | [Language Selector](https://github.com/VegaBobo/Language-Selector) | Per-app language overrides + system locale switcher without root |
| 15 | **Internet Tiles** | [BetterInternetTiles](https://github.com/CasperVerswijvelt/Better-Internet-Tiles) | Wi-Fi, Mobile Data, Hotspot, NFC, Bluetooth, Airplane Mode QS tiles that actually work |
| 16 | **Audio DSP** | [RootlessJamesDSP](https://github.com/ThePBone/RootlessJamesDSP) | System-wide EQ, bass boost, stereo widening, reverb, convolver, LiveProg scripting (no root) |
| 17 | **Call Recording** | [ShizuCallRecorder](https://github.com/chenxiaolong/BCR) | Rootless call recording via scrcpy audio capture + Shizuku, playback, auto-export |

---

## Highlights

- **🔍 Global Command Palette** — search all 85+ screens from anywhere with `⌘K`, scored fuzzy matching, per-category accent colors, Quick Launch tiles, and a **Recently Visited** row powered by DataStore
- **🔔 Notification Center** — 11 notification channels, snooze controls, per-channel preferences, animated bell hero
- **📊 Live Dashboard** — real-time stats (RAM, storage, battery, CPU), Shizuku health card, module grid
- **🎨 Material 3 Expressive** — glassmorphism surfaces, spring animations, adaptive navigation, dynamic color
- **🔒 Zero telemetry** — no analytics, no network calls home, fully on-device
- **🔌 ACCU System Service** — third-party app IPC broker with a full AIDL API, scope-based permission model, boot autostart, and built-in SDK documentation screen

---

## ACCU System Service — Third-Party Developer API

ACCU acts as a **privileged IPC broker** for other Android apps — similar to Shizuku, but built on top of it and packaged directly into ACCU. No Shizuku SDK required by client apps.

### How it works

```
Your App  →  bindService("com.accu.api.AccuSystemService")  →  AccuSystemService
                                                                      ↓
                                                             AccuPermissionManager
                                                             (user-controlled grants)
                                                                      ↓
                                                             Shizuku / root shell
                                                                      ↓
                                                             Android System APIs
```

### Service Hub UI

Navigate to **ACCU → Service Hub** (the API plug icon on the bottom nav or in the dashboard) to:

| Tab | What you see |
|---|---|
| **Apps** | All apps that have ever requested ACCU access — grant status, scopes, call count, last used timestamp. Revoke or delete any entry with a single tap. |
| **Pending** | Real-time list of apps currently waiting for your permission decision. Grant full access or deny from here without opening a separate dialog. |
| **SDK Docs** | Built-in quick reference with copy-to-clipboard code snippets for binding, shell execution, and the full method list. A button links to the extended developer guide. |

The hero status card shows:
- Green/red live indicator (running vs. stopped)
- Start / Stop button
- **Boot autostart toggle** — when enabled, AccuSystemService starts automatically on every boot via `BootReceiver`
- Connected app count · Pending count · Total API call counter
- Binding intent snippet for developers

### API capabilities

| Scope | What client apps can do |
|---|---|
| `SHELL` | Run any shell command via `exec()` / `execAsync()` / `execAndGetOutput()` |
| `PACKAGE_MANAGE` | Install, uninstall, enable, disable, hide, suspend, clear data, manage components, force-stop |
| `PERMISSIONS` | Grant/revoke runtime permissions, read/write App Ops |
| `SETTINGS` | Read/write Settings.Secure, Settings.Global, Settings.System |
| `LOCALE` | Set per-app locale overrides |
| `ALL` | All of the above |

### Integration (TL;DR for developers)

```kotlin
// 1. Copy 3 AIDL files into com/accu/api/ in your project
// 2. Add to AndroidManifest.xml:
//    <queries><package android:name="com.accu.controlcenter" /></queries>
// 3. Bind:
val intent = Intent("com.accu.api.AccuSystemService").setPackage("com.accu.controlcenter")
context.bindService(intent, connection, BIND_AUTO_CREATE)

// 4. In onServiceConnected:
val accu = IAccuService.Stub.asInterface(binder)
accu.requestPermission(callback)          // shows ACCU grant dialog once
val result = accu.exec("pm list packages") // [stdout, stderr, exitCode]
```

**Full guide:** [`docs/ACCU_THIRD_PARTY_DEVELOPER_GUIDE.md`](docs/ACCU_THIRD_PARTY_DEVELOPER_GUIDE.md) — 22 sections covering architecture, all 37 API methods, scope reference, error handling, threading model, lifecycle, security model, testing, troubleshooting, and a Shizuku migration guide.

### Boot autostart

The **Boot autostart** toggle in Service Hub persists across reboots. When enabled:
1. `BootReceiver` fires on `BOOT_COMPLETED` / `QUICKBOOT_POWERON` / `MY_PACKAGE_REPLACED`.
2. It reads `accu_service_autostart` from `accu_service_prefs` SharedPreferences.
3. If `true`, it calls `startForegroundService(Intent(AccuSystemService))` immediately.

The toggle is off by default; the user must enable it explicitly in Service Hub.

---

## Screenshots

> _Add screenshots here once the first build is signed and running on a device._

| Dashboard | Command Palette | Shell | App Manager |
|---|---|---|---|
| _(coming soon)_ | _(coming soon)_ | _(coming soon)_ | _(coming soon)_ |

---

## Requirements

| Requirement | Details |
|---|---|
| **Android version** | 10.0+ (API 29) |
| **Shizuku** | [Install from Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) — required for most features |
| **Root (optional)** | Unlocks additional shell, file manager, and component manager capabilities |
| **ADB (optional)** | `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh` to start Shizuku via USB |

---

## Building

### Prerequisites

- **Android Studio Meerkat** (2025.1+) or newer
- **JDK 17**
- **Android SDK** with Build Tools 35+, Platform 36

### Clone

```bash
git clone https://github.com/your-org/android-control-center.git
cd android-control-center/android-control-center
```

### Debug Build

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install directly to a connected device

```bash
./gradlew installDebug
```

### Release Build

```bash
# Configure signing (edit app/build.gradle.kts or set env vars)
export KEYSTORE_PATH=keystore/release.keystore
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=your_alias
export KEY_PASSWORD=your_key_password

./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### GitHub Actions (CI/CD)

| Trigger | Builds |
|---|---|
| Push to `main` / `develop` | Debug APK → uploaded as workflow artifact |
| Push tag `v1.x.x` | Debug + Release APKs → GitHub Release created |
| Manual dispatch | Choose `debug` or `release` from the Actions tab |

**Required GitHub Secrets for release builds:**

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` or `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                           │
│  Jetpack Compose  ·  Material 3  ·  MVVM ViewModels     │
│  85+ screens  ·  5 bottom tabs  ·  Command Palette      │
└──────────────────────────┬──────────────────────────────┘
                           │ StateFlow / collectAsState
┌──────────────────────────▼──────────────────────────────┐
│                    Domain Layer                         │
│         Use Cases  ·  Business Logic  ·  Models         │
└──────────┬────────────────────────┬────────────────────-┘
           │                        │
┌──────────▼───────────┐  ┌─────────▼──────────────────────┐
│    Data Layer         │  │     System/Privileged Layer     │
│  Room DB (16 tables) │  │  Shizuku IPC Service            │
│  DataStore Prefs     │  │  LibSU root shell               │
│  Repositories        │  │  Android system APIs            │
└──────────────────────┘  └────────────────────────────────┘
```

### Key Design Decisions

- **Single Activity** — `MainActivity` hosts the entire nav graph; edge-to-edge with `WindowCompat`
- **Hilt DI** — all ViewModels, repositories, and services are Hilt-injected; `@Singleton` shared across the graph
- **Navigation** — single `NavHost` with 94+ named routes; `NavigationSuiteScaffold` adapts to phone/tablet/foldable
- **Shizuku first** — privileged operations attempt Shizuku before falling back to root, then ADB
- **DataStore** — navigation history, notification preferences, and user settings use `androidx.datastore.preferences`

---

## Project Structure

```
app/src/main/java/com/accu/
├── ACCApplication.kt              — Hilt app, 12 notification channels, Timber, LibSU
├── MainActivity.kt                — Single-activity host, edge-to-edge
│
├── data/
│   ├── db/                        — Room database (16 entities, 16 DAOs)
│   └── repositories/
│       ├── AppRepository.kt
│       ├── ShellRepository.kt
│       └── NavigationHistoryRepository.kt  — DataStore-backed nav history
│
├── di/
│   ├── AppModule.kt               — Shell config, singleton providers
│   └── DatabaseModule.kt          — Room + DAO providers
│
├── domain/usecases/               — AudioUseCases, etc.
│
├── navigation/
│   ├── NavRoutes.kt               — 94+ typed screen objects
│   └── AppNavigation.kt           — Full nav graph + history tracking listener
│
├── notifications/
│   ├── AccuNotificationHelper.kt  — 12-channel notification manager
│   ├── NotificationPreferences.kt
│   └── NotificationCenterViewModel.kt
│
├── receivers/                     — Boot, CallState, PackageChange
├── services/                      — Accessibility, AudioEffect, CallRecording, Shizuku IPC, QS Tiles
├── workers/                       — CleanupWorker (WorkManager)
│
└── ui/
    ├── dashboard/                 — Dashboard, SearchIndex (85+ screens), Command Palette, NavHistoryViewModel
    ├── shizuku/                   — Shizuku center, ADB pairing, freeze scheduler, app list
    ├── shell/                     — Shell screen, script editor, ADB file browser, command examples
    ├── appmanager/                — App list, detail, debloat, freeze, components, permissions, Inure analytics, VirusTotal
    ├── audio/                     — Equalizer, parametric EQ, AutoEQ, DSP, liveprog editor, blocklist
    ├── automation/                — Key mapper rules, advanced key mapper
    ├── callrecorder/              — Call recording, playback, scrcpy integration, settings
    ├── customization/             — Color editor, dark mode, DarQ app picker, ColorBlendr styles, per-app themes
    ├── filemanager/               — File browser, text editor, FTP server, file properties
    ├── installer/                 — APK installer, install flags
    ├── language/                  — Language center, per-app language detail
    ├── network/                   — Network center, internet tiles settings
    ├── notifications/             — Notification center (11 channels, animated, snooze)
    ├── onboarding/                — Welcome flow
    ├── privacy/                   — Privacy dashboard, online rules
    ├── settings/                  — App settings, permissions
    ├── storage/                   — Storage analyzer, app cleaner, system cleaner, deduplicator, corpse finder, squeezer
    ├── tutorial/                  — Learning center, feature tutorials
    ├── widgets/                   — SmartSpacer, complication targets
    └── components/                — ACCTopBar, GlossyCard, StatusBadge, shared composables
```

---

## Tech Stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin | 2.1 |
| UI Toolkit | Jetpack Compose | BOM 2025.x |
| Design System | Material 3 Expressive | latest |
| Architecture | MVVM + Clean (UseCases) | — |
| Dependency Injection | Hilt | 2.54 |
| Navigation | Navigation Compose | 2.8.x |
| Database | Room | 2.7 |
| Preferences | DataStore Preferences | 1.1.2 |
| Async | Kotlin Coroutines + Flow | 1.9 |
| Privileged Ops | Shizuku SDK | 13.1.5 |
| Root Shell | LibSU | 5.3.0 |
| Image Loading | Coil | 3.x |
| Background Jobs | WorkManager | 2.10 |
| Annotation Processor | KSP | 2.1 |
| Build System | Gradle + AGP | 8.11 / 8.7 |
| CI/CD | GitHub Actions | — |

---

## Privacy

- **No telemetry** — zero analytics SDKs, no crash reporters phoning home
- **No network calls** — all operations are local, on-device only
- **Private storage** — call recordings stored in app-private storage (`/data/data/…`), excluded from media scans
- **Secure backup** — `backup_rules.xml` excludes Shizuku session data and sensitive prefs from cloud backup

---

## Contributing

Contributions, bug reports, and feature requests are welcome!

1. **Fork** the repository
2. Create a feature branch: `git checkout -b feat/your-feature`
3. Follow the existing code style — Kotlin, Compose, MVVM, Hilt
4. All new screens must be added to `SearchIndex.kt` so they appear in the Command Palette
5. Submit a **Pull Request** against `main`

### Code Style Guidelines

- Use `@HiltViewModel` for all ViewModels; inject via `hiltViewModel()` in composables
- New features go in `ui/<feature>/` with `Screen.kt`, `ViewModel.kt` pattern
- Navigation routes are typed objects in `NavRoutes.kt` — always add a `Screen.Xxx` entry
- Use `ACCTopBar` and `GlossyCard` composables for visual consistency
- Material 3 color roles only (`colorScheme.primary`, `colorScheme.surface`, etc.)

---

## Acknowledgements

This project stands on the shoulders of 17 amazing open-source projects. Deep thanks to their authors:

[aShellYou](https://github.com/DP-Hridayan/aShellYou) · [BetterInternetTiles](https://github.com/CasperVerswijvelt/Better-Internet-Tiles) · [Blocker](https://github.com/lihenggui/blocker) · [Canta](https://github.com/samolego/Canta) · [ColorBlendr](https://github.com/Mahmud0808/ColorBlendr) · [DarQ](https://github.com/KieronQuinn/DarQ) · [Hail](https://github.com/aistra0528/Hail) · [InstallWithOptions](https://github.com/zacharee/InstallWithOptions) · [Inure](https://github.com/Hamza417/Inure) · [Key Mapper](https://github.com/keymapperorg/KeyMapper) · [Language Selector](https://github.com/VegaBobo/Language-Selector) · [Material Files](https://github.com/zhanghai/MaterialFiles) · [RootlessJamesDSP](https://github.com/ThePBone/RootlessJamesDSP) · [SD Maid SE](https://github.com/d4rken-org/sdmaid-se) · [ShizuCallRecorder](https://github.com/chenxiaolong/BCR) · [Shizuku](https://github.com/RikkaApps/Shizuku) · [SmartSpacer](https://github.com/KieronQuinn/SmartSpacer)

---

## License

```
Copyright 2025 ACC Ultimate Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

<div align="center">

Made with ❤️ · Kotlin · Compose · Material 3

</div>
