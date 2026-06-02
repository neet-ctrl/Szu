# ACCU Third-Party Developer Guide

Welcome! This guide explains how to integrate your Android app with
**Android Control Center Ultimate (ACCU)** to gain privileged system access
— install/uninstall apps, manage permissions, write system settings, run
shell commands, and more — all with the user's informed consent.

ACCU works similarly to Shizuku: you bind to a running service and call
APIs over a Binder IPC. The difference is that ACCU uses its own
permission dialog system with per-scope granularity.

---

## Prerequisites

| Requirement | Details |
|---|---|
| ACCU installed | The user must have ACCU (`com.accu.controlcenter`) installed |
| AccuSystemService enabled | User must open ACCU → System Service → toggle **ON** (shows a persistent notification when running) |
| minSdk 29 | Android 10+ required |
| AIDL enabled in Gradle | `buildFeatures { aidl = true }` |

---

## How Connection Works (Overview)

```
Your App                          ACCU (com.accu.controlcenter)
────────────────────              ──────────────────────────────────
AccuClient.connect()   ────────►  AccuSystemService (running as root/Shizuku)
bindService(intent)               │
onServiceConnected()  ◄────────   IAccuService AIDL binder
IAccuService obtained             │
                                  │
accu.requestPermission() ──────►  Shows permission dialog to user
IAccuPermissionCallback  ◄──────  User grants or denies
                                  │
accu.exec("id")          ──────►  Runs privileged shell command
AccuExecResult           ◄──────  stdout / stderr / exitCode
```

The key insight: **your app never needs root itself**. ACCU holds the
privilege and runs your commands on your behalf, after the user consents.

---

## Step 1 — Copy the AIDL Files

Create the directory `app/src/main/aidl/com/accu/api/` in your project and copy:

```
aidl/com/accu/api/IAccuService.aidl
aidl/com/accu/api/IAccuPermissionCallback.aidl
aidl/com/accu/api/IAccuProcessCallback.aidl
```

**These files must stay in exactly this package path.** The AIDL package
declaration must match the directory. Do NOT change the package, interface names,
or transaction IDs — ACCU's compiled service stub and your generated proxy must
be binary-compatible.

---

## Step 2 — Copy the SDK Helper Files

Copy everything from `sdk/` into your project's source tree:

```
sdk/AccuClient.kt
sdk/AccuConstants.kt
sdk/AccuScopes.kt
sdk/AccuPermissionCodes.kt
sdk/AccuConnectionState.kt
sdk/AccuExceptions.kt
```

Recommended destination: `app/src/main/java/com/accu/sdk/`

These files provide a clean Kotlin API on top of the raw AIDL binder.
You can use the raw binder directly (`IAccuService.Stub.asInterface(binder)`)
if you prefer — see `templates/ServiceConnection_Template.kt`.

---

## Step 3 — Update app/build.gradle.kts

```kotlin
android {
    buildFeatures {
        aidl = true   // Required for AIDL compilation
    }
}

dependencies {
    // Required for AccuClient.requestPermission() suspend function
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

See `templates/BuildGradle_Template.kts` for a complete template.

---

## Step 4 — Update AndroidManifest.xml

Add the `<queries>` block so Android lets your app see the ACCU package.
Without this your `bindService()` will return `false` on Android 11+:

```xml
<manifest …>

    <queries>
        <!-- Required: tells Android your app is allowed to see ACCU -->
        <package android:name="com.accu.controlcenter" />
        <!-- Optional but recommended: also declare the service action -->
        <intent>
            <action android:name="com.accu.api.AccuSystemService" />
        </intent>
    </queries>

    <application …>
        …
    </application>
</manifest>
```

---

## Step 5 — Connect, Request Permission, and Call APIs

### ViewModel pattern (recommended)

```kotlin
class MyViewModel(app: Application) : AndroidViewModel(app) {

    // 1. Create the client
    private val accu = AccuClient(app)

    // 2. Expose connection state as a Flow for your UI to observe
    val connectionState: StateFlow<AccuConnectionState> = accu.state

    init {
        // 3. Connect on creation — bindService() is called here
        accu.connect()
    }

    override fun onCleared() {
        // 4. Always disconnect to release the binder
        accu.disconnect()
    }

    // 5. Request permission — shows ACCU's bottom-sheet dialog to the user
    fun requestPermission() {
        viewModelScope.launch {
            val result = accu.requestPermission()
            when {
                result.isGranted() -> {
                    // Permission granted — safe to call privileged APIs
                    val output = withContext(Dispatchers.IO) {
                        accu.exec("id")
                    }
                    println(output.stdout) // uid=0(root) gid=0(root)...
                }
                result == AccuConstants.PERMISSION_DENIED -> {
                    // User explicitly denied — tell them why you need it
                }
                result == AccuConstants.PERMISSION_SERVICE_UNAVAILABLE -> {
                    // ACCU not connected — check connection state
                }
            }
        }
    }

    // 6. Call a privileged API
    fun disableApp(packageName: String) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                accu.disablePackage(packageName)
            }
            // success == true → package is now disabled
        }
    }
}
```

### Observing connection state in Compose

```kotlin
@Composable
fun MyScreen(vm: MyViewModel = viewModel()) {
    val state by vm.connectionState.collectAsState()

    when (state) {
        is AccuConnectionState.Connected -> {
            val c = state as AccuConnectionState.Connected
            Text("Connected — ACCU ${c.accuVersion}")
            Text("Permission: ${c.permissionCode.toPermissionLabel()}")
        }
        AccuConnectionState.Connecting    -> Text("Connecting to ACCU…")
        AccuConnectionState.Disconnected  -> Text("Disconnected")
        is AccuConnectionState.Error      -> Text("Error: ${(state as AccuConnectionState.Error).reason}")
        else                              -> Text("Idle")
    }
}
```

---

## Connection States

```
Idle ──► Connecting ──► Connected ──► Disconnected
                  │                        │
                  └──► Error               └──► Connecting (auto-retry)
```

| State | Meaning |
|---|---|
| `Idle` | `connect()` not yet called |
| `Connecting` | `bindService()` sent, waiting for `onServiceConnected()` |
| `Connected` | Binder alive, APIs callable |
| `Disconnected` | Service died or `disconnect()` called; auto-retries |
| `Error` | `bindService()` returned `false` — ACCU not installed or service not enabled |

### Error: `bindService() returned false`

This is the most common error developers see. It means one of:

1. **ACCU is not installed** — `com.accu.controlcenter` not found on device
2. **AccuSystemService is not enabled** — user must open ACCU → System Service → toggle ON
3. **`<queries>` block missing** — your app can't see ACCU on Android 11+
4. **Wrong intent action** — `AccuConstants.SERVICE_ACTION` must match what ACCU exports

Check with the diagnostics in the `FullDemoApp` sample to identify the exact cause.

---

## Permission Model

ACCU uses a two-level permission system:

### Level 1: App Permission
Your app must be granted access to ACCU at all:
```kotlin
val code = accu.requestPermission()    // shows dialog
val code = accu.checkPermission()      // no dialog
```

| Code | Constant | Meaning |
|---|---|---|
| `0` | `PERMISSION_GRANTED` | Full access — all API calls work |
| `1` | `PERMISSION_DENIED` | User explicitly denied |
| `2` | `PERMISSION_NOT_YET_REQUESTED` | Dialog not yet shown |
| `3` | `PERMISSION_SERVICE_UNAVAILABLE` | ACCU not connected |

### Level 2: Scopes
Even with `PERMISSION_GRANTED`, each API category is gated by a scope:

| Scope constant | API category | What it covers |
|---|---|---|
| `AccuScopes.SHELL` | Shell execution | `exec`, `execAsync`, `execAndGetOutput` |
| `AccuScopes.PACKAGE_MANAGE` | Package management | install, enable, disable, hide, suspend, clear, forceStop, component |
| `AccuScopes.PERMISSIONS` | Runtime permissions | `grantPermission`, `revokePermission`, `setAppOp`, `getAppOp` |
| `AccuScopes.SETTINGS` | System settings | read/write Secure, Global, System settings |
| `AccuScopes.LOCALE` | Per-app locale | `setApplicationLocale` |

Check scopes before calling gated APIs:
```kotlin
if (accu.hasScope(AccuScopes.SHELL)) {
    val result = accu.exec("id")
}
```

---

## API Quick Reference

### Shell
```kotlin
// Synchronous — blocks until command exits
val result: AccuExecResult = accu.exec("pm list packages")
println(result.stdout)    // all output
println(result.exitCode)  // 0 = success

// Async streaming — line-by-line callback (great for long-running commands)
accu.execAsync(
    command  = "logcat -t 50",
    onStdout = { line -> println(line) },
    onStderr = { line -> println("[ERR] $line") },
    onExit   = { code -> println("Exit: $code") },
)

// Simple — returns combined stdout as String
val output: String = accu.execAndGetOutput("echo hello")
```

### Package Management
```kotlin
accu.disablePackage("com.bloat.app")       // pm disable-user --user 0
accu.enablePackage("com.bloat.app")        // pm enable --user 0
accu.hidePackage("com.bloat.app")          // pm hide
accu.unhidePackage("com.bloat.app")        // pm unhide
accu.suspendPackage("com.bloat.app")       // pm suspend
accu.unsuspendPackage("com.bloat.app")     // pm unsuspend
accu.clearPackageData("com.bloat.app")     // pm clear
accu.forceStop("com.bloat.app")            // am force-stop
accu.installApk("/sdcard/my.apk")         // pm install
accu.uninstallPackage("com.bloat.app")     // pm uninstall
```

### Runtime Permissions
```kotlin
accu.grantPermission("com.myapp", "android.permission.CAMERA")
accu.revokePermission("com.myapp", "android.permission.CAMERA")
accu.setAppOp("com.myapp", "RUN_IN_BACKGROUND", "deny")
val mode: String = accu.getAppOp("com.myapp", "RUN_IN_BACKGROUND")
```

### System Settings
```kotlin
// Read
val brightness = accu.readSystemSetting("screen_brightness")
val btOn       = accu.readSecureSetting("bluetooth_on")
val adbEnabled = accu.readGlobalSetting("adb_enabled")

// Write
accu.writeSystemSetting("screen_brightness", "200")
accu.writeSecureSetting("bluetooth_on", "1")
accu.writeGlobalSetting("adb_enabled", "1")
```

### Locale
```kotlin
accu.setApplicationLocale("com.myapp", "en-US")   // set locale
accu.setApplicationLocale("com.myapp", "")         // reset to system default
```

---

## Error Handling

Every API call that throws is wrapped in `AccuNotConnectedException` when the
service is not bound. Catch at the ViewModel layer:

```kotlin
fun doPrivilegedAction() {
    viewModelScope.launch {
        try {
            val result = withContext(Dispatchers.IO) { accu.exec("id") }
            // handle success
        } catch (e: AccuNotConnectedException) {
            // Service not connected — tell user to open ACCU
        } catch (e: Exception) {
            // Unexpected AIDL error — log and report
        }
    }
}
```

---

## Common Issues

| Symptom | Cause | Fix |
|---|---|---|
| `bindService() returned false` | Service not running | User: open ACCU → System Service → Enable |
| `bindService() returned false` | `<queries>` missing | Add `<queries>` block to AndroidManifest |
| `checkPermission() = SERVICE_UNAVAILABLE` | Not connected | Wait for `Connected` state before calling APIs |
| `AccuNotConnectedException` | Calling API before binder is ready | Collect `accu.state` and only call when `Connected` |
| `hasScope(SHELL) = false` | Scope not granted | User may have granted permission but not all scopes |
| All diagnostics FAIL | AccuSystemService not enabled | User must toggle ON in ACCU → System Service |

---

## Testing Your Integration

Use the **FullDemoApp** (`samples/FullDemoApp/`) as your reference. Install it
alongside ACCU and run the Automated Tests screen — it runs 17 end-to-end checks
covering every API surface and reports PASS/FAIL/WARN per test.

Connection Diagnostics runs 10 specific checks:
1. ACCU installed?
2. Package visibility (`<queries>` effective)?
3. Binder connected (bindService → onServiceConnected)?
4. Binder alive (ping)?
5. Protocol version match?
6. Permission granted?
7. All 5 scopes granted?
8. ACCU app version readable?
9. Backend type (root/shizuku)?
10. Service PID?

---

## For detailed API docs: `docs/ACCU_API_REFERENCE.md`
## For migration from Shizuku: `docs/ACCU_MIGRATION_FROM_SHIZUKU.md`
## For troubleshooting: `docs/ACCU_TROUBLESHOOTING.md`
