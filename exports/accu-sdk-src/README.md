# ACCU SDK

**Android Control Center Ultimate — Third-Party Integration SDK**

This package contains everything a third-party Android app needs to integrate with
**ACCU System Service**, gaining privileged system access
(shell execution, package management, runtime permissions, system settings)
with the user's informed consent.

---

## What is ACCU?

Android Control Center Ultimate (ACCU) is an Android app that acts as a
privilege broker, exactly like Shizuku. ACCU exposes a Binder IPC interface
that third-party apps can bind to and call privileged APIs on.

The user must:
1. Have ACCU (`com.accu.controlcenter`) installed
2. Enable **AccuSystemService** inside ACCU (ACCU → System Service → toggle ON)
3. Grant your app permission the first time it requests it — ACCU shows a Material 3 bottom-sheet

ACCU uses a **scope-based permission model** — users can grant or restrict
individual categories of access (Shell, Package Management, Permissions,
Settings, Locale) on a per-app basis.

---

## How Third-Party Apps Connect

```
Your App (any Android app)       ACCU (com.accu.controlcenter)
────────────────────────         ──────────────────────────────
1. Add <queries> block           AccuSystemService running as root/Shizuku
2. AccuClient(context)
3. accu.connect()   ──────────►  bindService(com.accu.api.AccuSystemService)
                    ◄──────────  onServiceConnected → IAccuService binder
4. accu.requestPermission() ──►  Shows bottom-sheet dialog to user
                            ◄──  IAccuPermissionCallback.onPermissionResult()
5. accu.exec("id")  ──────────►  sh -c "id" run with root/Shizuku privilege
                    ◄──────────  AccuExecResult(stdout, stderr, exitCode)
```

---

## Package Contents

```
accu-sdk/
│
├── README.md                          ← You are here
│
├── docs/
│   ├── ACCU_THIRD_PARTY_DEVELOPER_GUIDE.md   ← Start here — full integration guide
│   ├── ACCU_API_REFERENCE.md                 ← All 25 API methods documented
│   ├── ACCU_ARCHITECTURE.md                  ← IPC flow diagrams
│   ├── ACCU_TROUBLESHOOTING.md               ← Common errors and fixes
│   ├── ACCU_MIGRATION_FROM_SHIZUKU.md        ← Coming from Shizuku?
│   └── ACCU_INTEGRATION_CHECKLIST.md         ← Verification checklist
│
├── aidl/
│   └── com/accu/api/
│       ├── IAccuService.aidl                 ← Primary IPC contract (25 methods)
│       ├── IAccuPermissionCallback.aidl      ← One-shot permission result callback
│       └── IAccuProcessCallback.aidl         ← Streaming shell output callback
│
├── sdk/
│   ├── AccuClient.kt                         ← Main entry point — use this
│   ├── AccuConstants.kt                      ← Service address, permission codes
│   ├── AccuScopes.kt                         ← Scope name constants + descriptions
│   ├── AccuPermissionCodes.kt                ← Extension fns for permission codes
│   ├── AccuConnectionState.kt                ← Sealed class for connection lifecycle
│   └── AccuExceptions.kt                     ← Typed exception hierarchy
│
├── templates/
│   ├── AndroidManifest_Template.xml          ← Manifest changes needed
│   ├── BuildGradle_Template.kts              ← Gradle changes needed
│   ├── MainActivity_Template.kt              ← Basic Compose UI template
│   ├── ViewModel_Template.kt                 ← Recommended ViewModel pattern
│   └── ServiceConnection_Template.kt         ← Raw binding (no ViewModel)
│
└── samples/
    ├── MinimalSample/                        ← Bare minimum — log output only
    ├── ShellSample/                          ← Terminal UI with exec + execAsync
    ├── PackageManagerSample/                 ← Disable/enable/hide/grant perms
    ├── SettingsSample/                       ← Read/write system settings + locale
    └── FullDemoApp/                          ← Complete demo covering all APIs (14 screens)
```

---

## 5-Minute Quick Start

### Step 1 — Copy AIDL files

Create `app/src/main/aidl/com/accu/api/` and copy all three `.aidl` files there.
**Do not change package names or method signatures.**

### Step 2 — Copy SDK files

Copy the six `.kt` files from `sdk/` into `app/src/main/java/com/accu/sdk/`.

### Step 3 — Update Gradle

```kotlin
// app/build.gradle.kts
android {
    buildFeatures { aidl = true }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

### Step 4 — Update AndroidManifest.xml

```xml
<queries>
    <!-- Required — without this bindService() returns false on Android 11+ -->
    <package android:name="com.accu.controlcenter" />
    <intent>
        <action android:name="com.accu.api.AccuSystemService" />
    </intent>
</queries>
```

### Step 5 — Connect and use

```kotlin
// In your ViewModel
private val accu = AccuClient(applicationContext)
val state = accu.state  // StateFlow<AccuConnectionState>

init { accu.connect() }
override fun onCleared() { accu.disconnect() }

fun requestPermission() {
    viewModelScope.launch {
        val result = accu.requestPermission()   // shows ACCU dialog
        if (result.isGranted()) {
            val output = withContext(Dispatchers.IO) { accu.exec("id") }
            // output.stdout = "uid=0(root) gid=0(root)..."
        }
    }
}
```

---

## Supported APIs (25 total)

| Category | Methods |
|---|---|
| Identity | `ping`, `getVersion`, `getUid`, `getPid`, `getAccuVersion` |
| Permission | `requestPermission`, `checkPermission`, `hasScope`, `revokeSelf` |
| Shell | `exec`, `execAsync`, `execAndGetOutput` |
| Package Manager | `installApk`, `uninstallPackage`, `uninstallKeepData`, `enablePackage`, `disablePackage`, `hidePackage`, `unhidePackage`, `suspendPackage`, `unsuspendPackage`, `clearPackageData`, `enableComponent`, `disableComponent`, `forceStop` |
| Permissions | `grantPermission`, `revokePermission`, `setAppOp`, `getAppOp` |
| Locale | `setApplicationLocale` |
| Settings | `writeSecureSetting`, `readSecureSetting`, `writeGlobalSetting`, `readGlobalSetting`, `writeSystemSetting`, `readSystemSetting` |

Full documentation → `docs/ACCU_API_REFERENCE.md`

---

## Requirements

| Requirement | Value |
|---|---|
| ACCU app (`com.accu.controlcenter`) | Must be installed on device |
| AccuSystemService | Must be enabled by user in ACCU |
| Android | 10+ (API 29+) |
| Gradle build feature | `aidl = true` |
| Coroutines | `kotlinx-coroutines-android` |

---

## Common Errors

| Error | Cause | Fix |
|---|---|---|
| `bindService() returned false` | AccuSystemService not enabled | User: open ACCU → System Service → Enable |
| `bindService() returned false` | `<queries>` block missing | Add `<queries>` to AndroidManifest |
| `checkPermission() = SERVICE_UNAVAILABLE` | Not yet connected | Wait for `AccuConnectionState.Connected` |
| `AccuNotConnectedException` | API called before binder ready | Guard calls with connection state check |

---

## Full Integration Guide: `docs/ACCU_THIRD_PARTY_DEVELOPER_GUIDE.md`
