---
name: ACCU Project Architecture
description: Android Control Center Ultimate — key structure, conventions, and gotchas for future sessions.
---

## Project root
`/home/runner/workspace/android-control-center/`
Package: `com.accu.controlcenter` (applicationId), code package `com.accu`
minSdk 29, targetSdk/compileSdk 36

## Critical conventions
- `NavRoutes.kt` — ALL screen destinations; add new routes here first.
- `AppNavigation.kt` — ONE composable() per route; imports must match package + function name, NOT file name.
- `FileManagerAdvancedFeaturesScreen` lives in `MaterialFilesAdvancedScreen.kt` → import as `FileManagerAdvancedFeaturesScreen`.
- `CantaPresetsScreen.onApplyPreset` takes `(DebloatPreset) -> Unit`, not `() -> Unit`.
- `StorageScreen` uses Hilt ViewModel — always include `viewModel: StorageViewModel = hiltViewModel()` last.
- `ShizukuUtils.kt` already existed with its own implementation — never overwrite it.

## Source repos covered (all 17)
Shizuku, aShellYou, Canta, Hail, Inure, Blocker, ColorBlendr, DarQ, SmartSpacer,
SD Maid SE, Material Files, InstallWithOptions, Key Mapper, Language Selector,
Better Internet Tiles, RootlessJamesDSP, ShizuCallRecorder

## Key file locations
- Navigation: `com/accu/navigation/NavRoutes.kt`, `AppNavigation.kt`
- All features catalog: `com/accu/ui/features/AllFeaturesScreen.kt`
- Services: `com/accu/services/` (InternetTileService, LanguageQSTileService, KeyEventRelayService, AutoFreezeService, FreezeAllTileService, etc.)
- Receivers: `com/accu/receivers/` (ACCDeviceAdminReceiver, ScreenOffReceiver, BootReceiver, etc.)
- Device admin XML: `res/xml/device_admin.xml`
- GitHub Actions: `.github/workflows/build.yml` (debug APK on push/PR)

**Why:** These conventions prevent the most common compile errors in this large multi-screen project.
