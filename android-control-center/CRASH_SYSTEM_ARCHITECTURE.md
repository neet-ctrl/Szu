# ACCU Crash System Architecture

## Overview

The ACCU Crash Center is a self-contained, enterprise-grade crash monitoring, reporting, recovery, and diagnostics subsystem built directly into ACCU. It captures every crash type without any external SDK, persists reports locally via Room, surfaces them through a full-screen UI, dispatches high-priority notifications, and provides a comprehensive in-app dashboard.

---

## Architecture Layers

```
┌───────────────────────────────────────────────────────────────────────┐
│  Crash Sources                                                         │
│  ─────────────────────────────────────────────────────────────────    │
│  Thread.UncaughtExceptionHandler  ·  Kotlin coroutine CoroutineContext  │
│  ANR watchdog (SIGQUIT / main-thread timeout)  ·  C++ signal handler   │
│  NDK  ·  Non-fatal manual reports (CrashEngine.reportNonFatal())       │
└────────────────────────┬──────────────────────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────────────────────┐
│  CrashEngine  (com.accu.crash.CrashEngine)                             │
│  ─────────────────────────────────────────────────────────────────    │
│  · Installed in ACCApplication.onCreate() — first thing that runs     │
│  · Captures Throwable + thread + kind (JAVA / KOTLIN / ANR / NATIVE)  │
│  · Calls CrashContextCollector to snapshot 20+ device/process fields  │
│  · Calls CrashAnalyzer to produce possibleCause / suggestedFix        │
│  · Writes crash JSON to filesDir/crashes/pending/<crashId>.json        │
│    (survives process death; readable by the :crash process)            │
│  · Fires CrashNotificationManager (high-priority notification)        │
│  · Launches CrashReportActivity in the :crash process                 │
│  · Calls original default handler (chaining, allows crashlytics etc.) │
└────────────────────────┬──────────────────────────────────────────────┘
                         │
                   (App restarts / resumes)
                         │
                         ▼
┌───────────────────────────────────────────────────────────────────────┐
│  CrashRepository  (com.accu.crash.CrashRepository)                     │
│  ─────────────────────────────────────────────────────────────────    │
│  · migratePendingCrashes() — called from ACCApplication.onCreate()    │
│    after Hilt is ready; reads pending JSON files, inserts into Room,  │
│    then deletes the JSON files                                         │
│  · Exposes Flow-based query API: getAll, getById, search, filter,     │
│    totalCount, countSince, fatalCount, anrCount, riskLevel counts     │
│  · Mutations: delete, deleteByIds, deleteAll, setFavorited,           │
│    setPinned, setNotes                                                 │
└────────────────────────┬──────────────────────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────────────────────┐
│  Room Database  (AppDatabase v2)                                        │
│  ─────────────────────────────────────────────────────────────────    │
│  Table: crash_reports  (CrashEntity)                                   │
│  Key columns:                                                          │
│    crashId (UUID), timestamp, exceptionType, exceptionMessage,         │
│    stackTrace, causeChain, crashKind (JAVA/KOTLIN/ANR/NATIVE/NONF),    │
│    isFatal, isAnr, riskLevel (CRITICAL/HIGH/MEDIUM/LOW),               │
│    possibleCause, suggestedFix, affectedModule, similarCrashCount,     │
│    deviceModel, androidVersion, sdkInt, appVersion, buildVersionCode,  │
│    processName, processId, threadName, threadId, activityName,         │
│    screenRoute, freeRamMb, totalRamMb, isLowMemory, cpuUsagePct,      │
│    batteryPct, batteryCharging, networkState, sessionId,               │
│    sessionDurationSec, userActionsJson, shizukuState, rootState,       │
│    isFavorited, isPinned, userNotes                                    │
└────────────────────────┬──────────────────────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────────────────────┐
│  UI Layer  (com.accu.ui.crash)                                          │
│  ─────────────────────────────────────────────────────────────────    │
│  CrashCenterScreen       — dashboard: stats, safe mode, recent, export │
│  CrashHistoryScreen      — paginated list: search, filter, multi-select│
│  CrashDetailScreen       — full detail: all fields, trace, notes, export│
│  CrashReportActivity     — full-screen crash popup (:crash process)    │
│                                                                         │
│  ViewModels (Hilt):                                                     │
│    CrashCenterViewModel  — stats + safe-mode + bulk export             │
│    CrashHistoryViewModel — list + search + filter + sort + multi-select│
│    CrashDetailViewModel  — single crash + mutations + per-crash export │
└───────────────────────────────────────────────────────────────────────┘
```

---

## Key Components

### CrashEngine
- **File**: `com.accu.crash.CrashEngine`
- Registers a `Thread.UncaughtExceptionHandler` as the global last-resort handler.
- Also overrides `CoroutineExceptionHandler` factory via `ServiceLoader` so structured-concurrency coroutine crashes are caught without cancelling unrelated scopes.
- ANR detection: posts a watchdog `Runnable` on the main looper; if it doesn't execute within 5s, an ANR pseudo-crash is recorded.
- **Process isolation**: the pending-crash JSON is the only IPC channel between the crashing main process and the `:crash` display process. No Hilt, no Room, no Binder call from `:crash`.

### CrashContextCollector
- **File**: `com.accu.crash.CrashContextCollector`
- Runs synchronously inside the crash handler (no coroutines) — must be fast and safe.
- Collects: device model, manufacturer, ABI, Android version/SDK, app version/build type, process ID/name, thread name/ID, free/total RAM, low-memory flag, CPU usage, battery level/charging state, network state, active Activity name, current Compose navigation route, Shizuku/root/wireless-ADB state, session ID, session duration, and the last 20 user navigation events.

### CrashAnalyzer
- **File**: `com.accu.crash.CrashAnalyzer`
- Heuristic rule engine: matches exception type + stack + message against 60+ known patterns.
- Produces a human-readable `possibleCause` string and `suggestedFix` recommendation.
- Also computes `riskLevel` (CRITICAL / HIGH / MEDIUM / LOW) based on fatality, exception type, and memory state.
- Queries Room for similar past crashes to populate `similarCrashCount`.

### CrashExportManager
- **File**: `com.accu.crash.CrashExportManager`
- Formats a single `CrashEntity` or a list of them into:
  - **TXT** — plain-text human-readable log
  - **JSON** — full machine-readable dump
  - **Markdown** — structured report with code fences
  - **HTML** — self-contained HTML report with inline CSS
  - **ZIP** — all four formats bundled together
- All files land in `filesDir/crash_exports/` and are shared via `FileProvider` (`${applicationId}.provider`).

### SafeModeManager
- **File**: `com.accu.crash.SafeModeManager`
- Tracks crash-count-per-session in `SharedPreferences`.
- Can be toggled manually from Crash Center; is also auto-triggered after `N` crashes in a session (configurable, default 3).
- Exposes `isSafeModeEnabled: StateFlow<Boolean>`.
- When active: automation, key mapper, overlays, startup services, shell QS tiles, and Liveprog are all suppressed.

### CrashNotificationManager
- **File**: `com.accu.crash.CrashNotificationManager`
- Channel: `accu_crash` / `IMPORTANCE_HIGH` (heads-up).
- Notification includes three inline action buttons:
  - **Copy Log** → broadcasts `com.accu.crash.ACTION_COPY` → `CrashBroadcastReceiver`
  - **Restart ACCU** → broadcasts `com.accu.crash.ACTION_RESTART` → `CrashBroadcastReceiver`
  - **Dismiss** → broadcasts `com.accu.crash.ACTION_DISMISS` → `CrashBroadcastReceiver`
- Notification ID = `9900 + (crashId.hashCode() and 0x0FFF)` — unique per crash, allows stacking.

### CrashReportActivity
- **File**: `com.accu.ui.crash.CrashReportActivity`
- Registered in `AndroidManifest.xml` with `android:process=":crash"`.
- Uses plain `ComponentActivity` + `setContent {}` — **no Hilt injection**.
- Reads crash JSON via `CrashEngine.readPendingCrashFile(context, crashId)`.
- Displays: exception type, timestamp, badges (FATAL/ANR/kind), device info, stack trace (collapsible), and action buttons (Restart, Open in App, Copy, Share, Dismiss).
- "Open in App" navigates to `MainActivity` with `navigate_to=crash_detail/<id>` extra.

---

## Data Flow: Fatal Crash Sequence

```
1. Exception thrown on any thread
2. CrashEngine.uncaughtException() fires
3. CrashContextCollector.collect() → snapshot
4. CrashAnalyzer.analyze() → risk/cause/fix
5. JSON written to filesDir/crashes/pending/<uuid>.json
6. CrashNotificationManager.showCrashNotification() → heads-up notification
7. CrashReportActivity launched (new task, :crash process)
8. Main process terminates (original handler called)
   ──── process death ────
9. User restarts ACCU (or taps "Restart ACCU")
10. ACCApplication.onCreate()
    → CrashEngine.install()
    → crashRepository.migratePendingCrashes()
       · reads all *.json from pending/
       · inserts CrashEntity rows into Room
       · deletes JSON files
11. User opens Settings → Crash Center → sees the new crash
```

---

## Navigation Integration

Routes added to `NavRoutes.kt`:
- `crash_center` → `Screen.CrashCenter`
- `crash_history` → `Screen.CrashHistory`
- `crash_detail/{crashId}` → `Screen.CrashDetail`

Entry points:
- **Settings screen** — "Crash Center" section with `FeatureRow` → `CrashCenterScreen`
- **Search index** — "Crash Center" and "Crash History" entries in the System category
- **Dashboard** — future: `CrashBadge` widget showing today's crash count (extensibility point)
- **Deep link** from `CrashReportActivity` "Open in App" button → navigates directly to `CrashDetailScreen`

---

## File Layout

```
app/src/main/java/com/accu/
├── crash/
│   ├── CrashEngine.kt              – global handler installation & JSON persistence
│   ├── CrashContextCollector.kt    – device/process/UI context snapshot
│   ├── CrashAnalyzer.kt            – heuristic risk/cause/fix engine
│   ├── CrashRepository.kt          – Room-backed data access + migration
│   ├── CrashExportManager.kt       – TXT/JSON/MD/HTML/ZIP export + FileProvider share
│   ├── SafeModeManager.kt          – crash-count gate + safe-mode toggle
│   ├── CrashNotificationManager.kt – heads-up notification + action buttons
│   └── CrashBroadcastReceiver.kt   – handles notification action intents
├── data/db/
│   ├── entities/CrashEntity.kt     – Room entity (crash_reports table)
│   └── dao/CrashDao.kt             – DAO with all query / mutation methods
└── ui/crash/
    ├── CrashReportActivity.kt      – full-screen crash popup (:crash process)
    ├── CrashCenterScreen.kt        – dashboard composable
    ├── CrashCenterViewModel.kt     – stats, safe mode, bulk export
    ├── CrashHistoryScreen.kt       – list with search / filter / multi-select
    ├── CrashHistoryViewModel.kt    – list state, sort, filter, mutations
    ├── CrashDetailScreen.kt        – full detail view with all fields
    └── CrashDetailViewModel.kt     – single crash state + per-crash export
```

---

## Security & Privacy

- All crash data stays **on-device only** — no external reporting, no network calls.
- Pending JSON files are in `filesDir` (app-private, not accessible to other apps).
- Exports use `FileProvider` with `grantUriPermissions="true"` — no direct file:// URIs.
- `CrashBroadcastReceiver` is `android:exported="false"` — cannot be triggered by other apps.
- `CrashReportActivity` is `android:exported="false"` — only launchable by ACCU itself.
- Safe Mode cannot be remotely triggered — requires user interaction within the app.
