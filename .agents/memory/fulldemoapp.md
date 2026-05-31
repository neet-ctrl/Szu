---
name: FullDemoApp architecture
description: 14-screen Compose ACCU SDK test app — layout, key decisions, and CI workflow setup
---

## Location
`exports/accu-sdk-src/samples/FullDemoApp/`

## App package
`com.accu.sdkdemo` — SDK package `com.accu.sdk` — ACCU target `com.accu.controlcenter`

## Key decisions
- Wrapper jar NOT committed (binary). CI downloads it from GitHub raw URL in a pre-build step.
- Single `MainViewModel` (AndroidViewModel) owns all state and all API call functions. All 14 screens inject only the VM.
- `AccuClient` is held in the VM (not a singleton) so it follows the lifecycle.
- `LogManager` and `CrashManager` are object singletons with `MutableStateFlow` — screens observe via VM delegates.

## Screen list (all in ui/screens/)
Dashboard · ConnectionDiagnostics · PermissionTest · ScopeInspector · ShellTest · PackageManager · PermissionOps · SettingsTest · LocaleTest · ApiExplorer · LogCenter · CrashCenter · DiagnosticsExport · AutomatedTest

## Navigation
`NavGraph.kt` — sealed `Screen` class + `drawerSections` list. `MainActivity.kt` uses `ModalNavigationDrawer` + `NavHost`.

## CI workflow
`.github/workflows/build-full-demo-app.yml`
- Triggers on push/PR to `exports/accu-sdk-src/samples/FullDemoApp/**` AND on `workflow_dispatch`
- Downloads `gradle-wrapper.jar` from `https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar` if missing
- Uploads debug APK as artifact (`FullDemoApp-debug-build<N>`)
- Optional lint job (non-blocking, triggered by `workflow_dispatch` input or PR)

## Why wrapper jar is not committed
Binary files cause merge noise in the workspace repo. The CI step downloads it at build time; local developers run `./gradlew` after downloading or letting their IDE do it.
