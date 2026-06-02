# Agent Prompt — Building a Third-Party App for ACCU System Service

## Your Mission

You are building an Android app that integrates with **ACCU (Android Control Center Ultimate)**, a privilege-broker service app. ACCU works exactly like Shizuku — it is a running Android service that your app binds to over Binder IPC to execute privileged system operations.

**You have been given a complete SDK package.** Every file you need is included. Do NOT guess, invent, or fetch anything from the internet. Follow this document exactly.

---

## What ACCU Is

ACCU is an installed Android app (`com.accu.controlcenter`) that runs a foreground Service called `AccuSystemService`. Third-party apps bind to this service and call its APIs to:

- Execute shell commands (`sh -c`)
- Install/uninstall/disable/hide/suspend apps
- Grant/revoke runtime permissions and control AppOps
- Read/write Settings.Secure, Settings.Global, Settings.System
- Set per-app locale overrides

The user must:
1. Have ACCU installed
2. Have enabled AccuSystemService in ACCU (it shows a persistent notification when running)
3. Grant your app permission the first time it requests it (ACCU shows a bottom-sheet dialog)

---

## The IPC Contract

Your app talks to ACCU through three AIDL interfaces. These are already compiled for you — you just copy the `.aidl` source files and let Gradle generate the Java stubs.

```
IAccuService.aidl         — 25 privileged API methods you call
IAccuPermissionCallback.aidl — callback that fires when user grants/denies
IAccuProcessCallback.aidl    — streaming callback for async shell output
```

The binder you receive is `IAccuService`. Use `IAccuService.Stub.asInterface(binder)` to get the typed proxy. Or use `AccuClient.kt` which does all of this for you.

---

## Files In This Package

```
README.md                              — Overview and quick-start
docs/ACCU_THIRD_PARTY_DEVELOPER_GUIDE.md  — Step-by-step integration guide (READ THIS FIRST)
docs/ACCU_API_REFERENCE.md             — Every API method documented
docs/ACCU_ARCHITECTURE.md              — IPC flow diagrams
docs/ACCU_TROUBLESHOOTING.md           — Common errors and fixes
docs/ACCU_MIGRATION_FROM_SHIZUKU.md    — If migrating from Shizuku
docs/ACCU_INTEGRATION_CHECKLIST.md     — Verification checklist

aidl/com/accu/api/IAccuService.aidl            — COPY TO YOUR PROJECT
aidl/com/accu/api/IAccuPermissionCallback.aidl — COPY TO YOUR PROJECT
aidl/com/accu/api/IAccuProcessCallback.aidl    — COPY TO YOUR PROJECT

sdk/AccuClient.kt           — Main SDK class (wraps binder, manages lifecycle)
sdk/AccuConstants.kt        — Service address, permission result codes
sdk/AccuScopes.kt           — Scope name constants (SHELL, PACKAGE_MANAGE, etc.)
sdk/AccuPermissionCodes.kt  — Extension fns (isGranted(), isDenied(), etc.)
sdk/AccuConnectionState.kt  — Sealed class for connection state (Idle/Connecting/Connected/Error)
sdk/AccuExceptions.kt       — Typed exception classes

templates/AndroidManifest_Template.xml   — Copy <queries> block to your manifest
templates/BuildGradle_Template.kts       — Gradle changes required
templates/ViewModel_Template.kt          — Recommended ViewModel pattern
templates/MainActivity_Template.kt       — Compose UI with state handling
templates/ServiceConnection_Template.kt  — Raw binding (no ViewModel / AccuClient)

samples/MinimalSample/MainActivity.kt     — Bare minimum code
samples/ShellSample/MainActivity.kt       — Terminal UI with streaming
samples/PackageManagerSample/MainActivity.kt — disable/enable/hide/grant
samples/SettingsSample/MainActivity.kt    — Settings read/write + locale
samples/FullDemoApp/MainActivity.kt       — Full reference app (all APIs)
```

---

## Exact Integration Steps

Follow these in order. Do not skip steps.

### Step 1: Copy AIDL Files

Create this exact directory structure in your Android project:
```
app/src/main/aidl/com/accu/api/
```

Copy all three `.aidl` files there:
- `IAccuService.aidl`
- `IAccuPermissionCallback.aidl`
- `IAccuProcessCallback.aidl`

**Critical rules:**
- Do NOT change the package declarations inside the files. They must stay `package com.accu.api;`
- Do NOT rename the files or interfaces
- Do NOT change the transaction ID numbers (the `= 1`, `= 2`, etc. at end of each method)
- The directory path `com/accu/api/` under `aidl/` is not negotiable — it must match the package

### Step 2: Copy SDK Files

Copy all six files from `sdk/` into your project:
```
app/src/main/java/com/accu/sdk/
    AccuClient.kt
    AccuConstants.kt
    AccuScopes.kt
    AccuPermissionCodes.kt
    AccuConnectionState.kt
    AccuExceptions.kt
```

You may use a different package path, but if you do, update the `package com.accu.sdk` declarations at the top of each file to match.

### Step 3: Gradle Changes

In `app/build.gradle.kts`, add **exactly** these two things:

```kotlin
android {
    // ... your existing config ...

    buildFeatures {
        aidl = true    // ← REQUIRED. Without this, AIDL files will not compile.
    }
}

dependencies {
    // ... your existing dependencies ...

    // ← REQUIRED for AccuClient.requestPermission() which is a suspend function
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

Also ensure `minSdk = 29` or higher.

### Step 4: Manifest Changes

In `app/src/main/AndroidManifest.xml`, add the `<queries>` block inside `<manifest>` but OUTSIDE `<application>`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- REQUIRED on Android 11+ (API 30+): package visibility -->
    <queries>
        <package android:name="com.accu.controlcenter" />
    </queries>

    <application ...>
        ...
    </application>
</manifest>
```

Do NOT add any `<uses-permission>` entries for ACCU. Do NOT declare AccuSystemService in your manifest.

### Step 5: Build Verification

Before writing any business logic, verify the integration compiles:

```bash
./gradlew generateDebugAidlInterfaces
./gradlew assembleDebug
```

Both must succeed. If AIDL compilation fails, recheck Step 1 (file locations and package declarations).

---

## How to Use AccuClient

### Creating AccuClient

```kotlin
// Always use applicationContext (not Activity context) to survive rotations
private val accu = AccuClient(application.applicationContext)
```

Create it in a ViewModel, not in an Activity or Composable.

### Connection Lifecycle

```kotlin
class MyViewModel(app: Application) : AndroidViewModel(app) {
    private val accu = AccuClient(app)
    val accuState: StateFlow<AccuConnectionState> = accu.state

    init {
        accu.connect()  // ← Call immediately
    }

    override fun onCleared() {
        super.onCleared()
        accu.disconnect()  // ← Always clean up
    }
}
```

### Observing State in Compose

```kotlin
@Composable
fun MyScreen(vm: MyViewModel) {
    val state by vm.accuState.collectAsState()

    when (state) {
        is AccuConnectionState.Idle        -> { /* not started */ }
        is AccuConnectionState.Connecting  -> LinearProgressIndicator()
        is AccuConnectionState.Connected   -> {
            val s = state as AccuConnectionState.Connected
            Text("ACCU ${s.accuVersion} connected")
            if (!s.isPermissionGranted) {
                Button(onClick = { vm.requestPermission() }) {
                    Text("Grant ACCU Permission")
                }
            }
        }
        is AccuConnectionState.Disconnected -> Text("Reconnecting...")
        is AccuConnectionState.Error        -> Text("Error: ${(state as AccuConnectionState.Error).reason}")
    }
}
```

### Requesting Permission (First Time)

```kotlin
fun requestPermission() {
    viewModelScope.launch {
        // This suspends until the user taps Grant or Deny in ACCU's dialog
        val result = accu.requestPermission()
        when (result) {
            AccuConstants.PERMISSION_GRANTED -> { /* ✅ can call APIs */ }
            AccuConstants.PERMISSION_DENIED  -> { /* ❌ user denied */ }
            else                              -> { /* service unavailable */ }
        }
    }
}
```

On subsequent launches, ACCU remembers the grant — no dialog will appear.

### Calling APIs (always on Dispatchers.IO)

```kotlin
// Shell command
fun runId() {
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            accu.exec("id")
        }
        // result.stdout = "uid=0(root)..."
        // result.exitCode = 0
        // result.isSuccess = true
    }
}

// Package management
fun disableApp(pkg: String) {
    viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            accu.disablePackage(pkg)
        }
        // ok = true if success
    }
}

// Settings
fun disableAnimations() {
    viewModelScope.launch {
        withContext(Dispatchers.IO) {
            accu.writeGlobalSetting("animator_duration_scale", "0")
            accu.writeGlobalSetting("window_animation_scale", "0")
            accu.writeGlobalSetting("transition_animation_scale", "0")
        }
    }
}

// Streaming shell
fun streamLogs() {
    viewModelScope.launch {
        withContext(Dispatchers.IO) {
            accu.execAsync(
                command  = "logcat -T 50",
                onStdout = { line -> /* handle on bg thread */ },
                onStderr = { line -> },
                onExit   = { code -> },
            )
        }
    }
}
```

---

## Permission / Scope Rules

Before calling any API group, check you have the required scope:

| API Group | Required Scope |
|---|---|
| `exec`, `execAsync`, `execAndGetOutput` | `AccuScopes.SHELL` |
| `installApk`, `disablePackage`, `hidePackage`, `forceStop`, etc. | `AccuScopes.PACKAGE_MANAGE` |
| `grantPermission`, `revokePermission`, `setAppOp`, `getAppOp` | `AccuScopes.PERMISSIONS` |
| `writeSecureSetting`, `readGlobalSetting`, etc. | `AccuScopes.SETTINGS` |
| `setApplicationLocale` | `AccuScopes.LOCALE` |

Check with: `accu.hasScope(AccuScopes.SHELL)`

If a scope is not granted, ACCU throws `SecurityException`. Catch it as `AccuScopeDeniedException`.

---

## Checking ACCU Is Installed

```kotlin
fun isAccuInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo("com.accu.controlcenter", 0)
    true
} catch (_: PackageManager.NameNotFoundException) { false }
```

AccuClient also does this automatically in `connect()`. If ACCU is not installed,
`accuState` becomes `AccuConnectionState.Error` with a descriptive message.

---

## Critical Rules — Do Not Break These

1. **Never call ACCU APIs on the main thread.** They are synchronous and WILL
   cause ANR. Always use `withContext(Dispatchers.IO)`.

2. **Never hardcode** `"com.accu.controlcenter"`, `"com.accu.api.AccuSystemService"`,
   or permission codes. Import from `AccuConstants.*`.

3. **Never change AIDL files.** The transaction IDs (`= 1`, `= 2`, etc.) are the
   binary contract with ACCU's compiled service. Changing them causes binder mismatches.

4. **Never declare AccuSystemService in your manifest.** It is declared inside
   ACCU's own manifest and runs in ACCU's process.

5. **Never use `Context.BIND_IMPORTANT` or `Context.BIND_WAIVE_PRIORITY`** when
   binding. Use `Context.BIND_AUTO_CREATE` only.

6. **Always disconnect** in `ViewModel.onCleared()` or `Activity.onStop()`.
   Leaked service connections cause ANR on some OEM ROMs.

7. **Never create AccuClient with Activity context.** Use `applicationContext`.
   Activity context causes memory leaks and re-binding on rotation.

---

## Testing Your Integration

1. Install ACCU on your test device
2. Open ACCU → System Service → toggle ON (persistent notification will appear)
3. Build and install your app
4. Your app should connect → show "Connected" state
5. Tap your "Grant Permission" button → ACCU dialog appears
6. Tap "Grant Full Access" → your `requestPermission()` coroutine resumes with `0`
7. Now call APIs — they should succeed

**Logcat filter for debugging:**
```
adb logcat -s "ACCU" -s "AccuSystemService" -s "AccuServiceImpl"
```

ACCU logs every bind attempt, permission request, and API call with the caller package name.

---

## Sample Apps Reference

For working code you can copy directly:

| Sample | What it shows |
|---|---|
| `samples/MinimalSample/MainActivity.kt` | Absolute minimum — connect, request permission, run `id` |
| `samples/ShellSample/MainActivity.kt` | Terminal UI, exec + execAsync, streaming output |
| `samples/PackageManagerSample/MainActivity.kt` | disable/enable/hide/grant permission |
| `samples/SettingsSample/MainActivity.kt` | Settings read/write, per-app locale |
| `samples/FullDemoApp/MainActivity.kt` | All APIs in one tabbed app — use as reference |

---

## Troubleshooting Quick Reference

| Symptom | Fix |
|---|---|
| `bindService()` returns false | ACCU service not running. User must enable in ACCU |
| AIDL compile error | Check `aidl = true` in Gradle, check file paths |
| `SecurityException: does not have ACCU permission` | Call `requestPermission()` first |
| `SecurityException: lacks 'SHELL' scope` | User disabled that scope — call `revokeSelf()` + `requestPermission()` |
| Callbacks not on main thread | Post to main: `launch(Dispatchers.Main) { ... }` |
| `TransactionTooLargeException` | Use `execAsync()` instead of `exec()` for large output |
| Dialog never appears | Verify service is running, check Logcat for ACCU logs |

Full troubleshooting in `docs/ACCU_TROUBLESHOOTING.md`.

---

## Exact Connection Flow (What Happens Under the Hood)

Understanding this helps you debug connection problems:

```
Step 1 — AccuClient.connect() is called
         └─► Context.bindService(
                 Intent("com.accu.api.AccuSystemService").setPackage("com.accu.controlcenter"),
                 serviceConnection,
                 Context.BIND_AUTO_CREATE
             )
         └─► Returns true  → state becomes Connecting
             Returns false → state becomes Error("bindService() returned false")
                             CAUSE: ACCU not installed OR AccuSystemService not enabled
                             FIX:   User opens ACCU → System Service → toggle ON

Step 2 — Android delivers onServiceConnected(binder)
         └─► IAccuService.Stub.asInterface(binder)
         └─► accu.getVersion() called immediately to verify protocol
         └─► state becomes Connected(serviceVersion, accuVersion, permissionCode)

Step 3 — accu.requestPermission() called
         └─► Sends IAccuPermissionCallback.Stub() to ACCU over binder
         └─► ACCU shows Material 3 bottom-sheet dialog to user
         └─► User taps "Grant Full Access" → callback.onPermissionResult(0)
             User taps "Deny"              → callback.onPermissionResult(1)
         └─► AccuClient wraps this in a suspend function (CompletableDeferred)
         └─► Subsequent connections: stored grant, callback fires immediately (no dialog)

Step 4 — Privileged API call: e.g. accu.exec("id")
         └─► Binder call to IAccuService.exec("id")  [Transaction 20]
         └─► ACCU checks permission + SHELL scope
         └─► ACCU runs: sh -c "id"  (as root or uid=2000)
         └─► Returns AccuExecResult(stdout="uid=0(root)...", stderr="", exitCode=0)
```

**The two most common connection failures and their exact fixes:**

| Failure | Logcat clue | Fix |
|---|---|---|
| `bindService() returned false` | No log in AccuClient | User: ACCU → System Service → Enable. Check `<queries>` in manifest. |
| `onServiceDisconnected` fires immediately | `DeadObjectException` in AccuClient | ACCU crashed or was killed — AccuClient auto-reconnects, just wait. |

---

## Summary

You are building an app that:
1. Copies 3 AIDL files and 6 SDK files into an Android project
2. Enables AIDL in Gradle + adds `<queries>` to manifest
3. Creates `AccuClient(applicationContext)` in a ViewModel
4. Calls `connect()` on init, `disconnect()` in `onCleared()`
5. Shows a "Grant Permission" button when `accuState` is `Connected` but not yet granted
6. Calls `accu.requestPermission()` on button press (suspend function — awaits user dialog)
7. Calls privileged APIs on `Dispatchers.IO` using `withContext`

That is the complete integration. Everything else is your app's own business logic.

The SDK is self-contained. Every class, constant, and callback type you need
is in the files included in this package. No internet access required.
No additional libraries required (only coroutines).
