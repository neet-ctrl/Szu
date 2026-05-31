package com.accu.ui.apiservice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed

// ── Data model for docs ───────────────────────────────────────────────────────

private data class DocSection(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val content: @Composable () -> Unit,
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuSdkDocsScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var expandedId by remember { mutableStateOf<String?>("overview") }

    val sections = remember { buildDocSections(clipboard) }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "ACCU SDK — Developer Docs",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Hero banner
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Api, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("ACCU System Service SDK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("IPC Protocol v1 · com.accu.api", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "Give your app full system-level privileges without root — by binding to ACCU's privilege broker. 100% compatible with Android 10+ (API 29+), no root needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Doc sections as expandable cards
            items(sections, key = { it.id }) { section ->
                val expanded = expandedId == section.id
                DocSectionCard(
                    section  = section,
                    expanded = expanded,
                    onToggle = { expandedId = if (expanded) null else section.id },
                )
            }
        }
    }
}

@Composable
private fun DocSectionCard(section: DocSection, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column {
            // Header row
            Row(
                Modifier.fillMaxWidth().clickable { onToggle() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(section.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 12.dp))
                    section.content()
                }
            }
        }
    }
}

// ── Section helpers ───────────────────────────────────────────────────────────

@Composable
private fun CodeBlock(code: String, language: String = "kotlin", clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.6f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .padding(12.dp)
        ) {
            Text(code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun Note(text: String, color: Color = AccentOrange) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.1f), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = color)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun MethodRow(method: String, scope: String, description: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(method, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(scope, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 2.dp), thickness = 0.5.dp)
}

// ── Build all doc sections ────────────────────────────────────────────────────

private fun buildDocSections(clipboard: androidx.compose.ui.platform.ClipboardManager): List<DocSection> = listOf(

    // 1. Overview
    DocSection("overview", Icons.Default.Info, "Overview", "What is ACCU System Service?") {
        Para("ACCU System Service is a privilege-brokering IPC daemon that works exactly like Shizuku — but you never need to ask users to install a separate app. If they have ACCU installed and running, any third-party app can bind to it and get full elevated shell + system API access through a familiar permission model.")
        Para("Architecture:\n• Your app → binds via AIDL → AccuSystemService (runs in ACCU process)\n• AccuSystemService → executes via Shizuku / root → Android system\n\nACCU bridges the privilege gap. Your app stays unprivileged but can request specific scopes that the user approves.")
        Note("Supported Android versions: 10+ (API 29). ACCU must be installed and the service must be running.")
        Heading("Permission Scopes")
        listOf(
            "SHELL" to "Run any sh -c command with shell-level privileges",
            "PACKAGE_MANAGE" to "Install, uninstall, enable, disable, hide, suspend apps",
            "PERMISSIONS" to "Grant/revoke runtime permissions, set AppOps modes",
            "SETTINGS" to "Write Settings.Secure, Settings.Global, Settings.System",
            "LOCALE" to "Set per-app language locale overrides",
            "ALL" to "Grants every scope above (user sees this as 'Full Access')",
        ).forEach { (scope, desc) ->
            Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(scope, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    },

    // 2. Gradle setup
    DocSection("gradle", Icons.Default.Code, "Step 1 — Gradle Setup", "Add AIDL dependency to your project") {
        Para("ACCU's AIDL interface lives inside the ACCU app. You do NOT add ACCU as a Gradle dependency. Instead, you copy the three AIDL files into your project so the AIDL compiler can generate the stub classes.")
        Heading("2.1 Enable AIDL in your app/build.gradle")
        CodeBlock("""android {
    buildFeatures {
        aidl = true       // ← Enable AIDL compilation
    }
}""", "groovy", clipboard)
        Heading("2.2 Copy these 3 AIDL files into your project")
        Para("Create the directory: app/src/main/aidl/com/accu/api/\n\nThen place these three files there — their content must be byte-for-byte identical to ACCU's originals.")
        CodeBlock("app/\n└── src/\n    └── main/\n        └── aidl/\n            └── com/\n                └── accu/\n                    └── api/\n                        ├── IAccuService.aidl\n                        ├── IAccuPermissionCallback.aidl\n                        └── IAccuProcessCallback.aidl", "filesystem", clipboard)
        Note("The package name in each AIDL file MUST be 'com.accu.api'. Do not rename or move them — Android AIDL package paths must match exactly, or the binder will throw a ClassNotFoundException at runtime.")
        Heading("2.3 IAccuPermissionCallback.aidl")
        CodeBlock("""// IAccuPermissionCallback.aidl
package com.accu.api;

oneway interface IAccuPermissionCallback {
    void onPermissionResult(int result);
}""", "aidl", clipboard)
        Heading("2.4 IAccuProcessCallback.aidl")
        CodeBlock("""// IAccuProcessCallback.aidl
package com.accu.api;

oneway interface IAccuProcessCallback {
    void onStdoutLine(String line);
    void onStderrLine(String line);
    void onExit(int exitCode);
}""", "aidl", clipboard)
        Heading("2.5 IAccuService.aidl (abbreviated — copy the full file)")
        Note("The full IAccuService.aidl is long. Find the complete version in ACCU's source: app/src/main/aidl/com/accu/api/IAccuService.aidl", AccentCyan)
        CodeBlock("""// IAccuService.aidl
package com.accu.api;

import com.accu.api.IAccuPermissionCallback;
import com.accu.api.IAccuProcessCallback;

interface IAccuService {
    int getVersion() = 1;
    int getUid() = 2;
    int getPid() = 3;
    String getAccuVersion() = 4;
    boolean ping() = 5;

    void requestPermission(IAccuPermissionCallback callback) = 10;
    int checkPermission() = 11;
    boolean hasScope(String scope) = 12;
    void revokeSelf() = 13;

    String[] exec(String command) = 20;
    void execAsync(String command, IAccuProcessCallback callback) = 21;
    String execAndGetOutput(String command) = 22;

    boolean installApk(String apkPath, String installerPackage) = 30;
    boolean uninstallPackage(String packageName) = 31;
    boolean uninstallKeepData(String packageName) = 32;
    boolean enablePackage(String packageName) = 33;
    boolean disablePackage(String packageName) = 34;
    boolean hidePackage(String packageName) = 35;
    boolean unhidePackage(String packageName) = 36;
    boolean suspendPackage(String packageName) = 37;
    boolean unsuspendPackage(String packageName) = 38;
    boolean clearPackageData(String packageName) = 39;
    boolean enableComponent(String packageName, String componentName) = 40;
    boolean disableComponent(String packageName, String componentName) = 41;

    boolean grantPermission(String packageName, String permission) = 50;
    boolean revokePermission(String packageName, String permission) = 51;
    boolean setAppOp(String packageName, String op, String mode) = 52;
    String getAppOp(String packageName, String op) = 53;

    boolean forceStop(String packageName) = 60;
    boolean setApplicationLocale(String packageName, String locale) = 61;

    boolean writeSecureSetting(String name, String value) = 70;
    String readSecureSetting(String name) = 71;
    boolean writeGlobalSetting(String name, String value) = 72;
    String readGlobalSetting(String name) = 73;
    boolean writeSystemSetting(String name, String value) = 74;
    String readSystemSetting(String name) = 75;
}""", "aidl", clipboard)
    },

    // 3. Connecting
    DocSection("connect", Icons.Default.Link, "Step 2 — Connecting to the Service", "ServiceConnection, binding, and lifecycle") {
        Para("Binding to ACCU follows the standard Android ServiceConnection pattern. The key is using an explicit Intent with both the action and package name — required by Android 8.0+ for external service binding.")
        Heading("3.1 AccuClient — recommended wrapper class")
        Para("Copy this class into your project. It handles the full connection lifecycle, coroutine bridging, and reconnection logic.")
        CodeBlock("""import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.accu.api.IAccuPermissionCallback
import com.accu.api.IAccuService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AccuClient(private val context: Context) {

    companion object {
        const val ACCU_SERVICE_ACTION  = "com.accu.api.AccuSystemService"
        const val ACCU_PACKAGE         = "com.accu.controlcenter"

        // checkPermission() / onPermissionResult() result codes
        const val PERMISSION_GRANTED         =  0
        const val PERMISSION_DENIED          =  1
        const val PERMISSION_NOT_REQUESTED   = -1
        const val PERMISSION_SERVICE_ERROR   = -2
    }

    private var service: IAccuService? = null
    private val connected = CompletableDeferred<IAccuService>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IAccuService.Stub.asInterface(binder)
            connected.complete(service!!)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    /** Call once — usually in onCreate() or Application.onCreate(). */
    fun connect() {
        val intent = Intent(ACCU_SERVICE_ACTION).setPackage(ACCU_PACKAGE)
        val ok = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!ok) {
            connected.completeExceptionally(
                IllegalStateException("ACCU is not installed or its service is not running.")
            )
        }
    }

    /** Call in onDestroy(). */
    fun disconnect() {
        try { context.unbindService(connection) } catch (_: Exception) {}
        service = null
    }

    /** Suspend until connected (or throws). */
    suspend fun awaitService(): IAccuService = connected.await()

    /** Non-suspend shortcut when you already know it's connected. */
    fun getServiceOrNull(): IAccuService? = service

    /** Returns true if ACCU is installed. */
    fun isAccuInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(ACCU_PACKAGE, 0)
        true
    } catch (_: Exception) { false }

    /** Request ACCU permission. Suspends until user responds. */
    suspend fun requestPermission(): Int = suspendCancellableCoroutine { cont ->
        val svc = service ?: run { cont.resume(PERMISSION_SERVICE_ERROR); return@suspendCancellableCoroutine }
        svc.requestPermission(object : IAccuPermissionCallback.Stub() {
            override fun onPermissionResult(result: Int) {
                if (cont.isActive) cont.resume(result)
            }
        })
    }

    /** Check if already granted (no dialog). */
    fun checkPermission(): Int = service?.checkPermission() ?: PERMISSION_SERVICE_ERROR
    fun hasScope(scope: String): Boolean = service?.hasScope(scope) ?: false
}""", "kotlin", clipboard)

        Heading("3.2 Add to AndroidManifest.xml")
        Note("Android 11+ requires you to declare which packages your app queries. Add this to your AndroidManifest.xml so bindService() can resolve ACCU.", AccentOrange)
        CodeBlock("""<manifest>
    <!-- Required for Android 11+ (API 30+) to find ACCU -->
    <queries>
        <package android:name="com.accu.controlcenter" />
    </queries>
    ...
</manifest>""", "xml", clipboard)

        Heading("3.3 In your Activity / Fragment")
        CodeBlock("""class MyActivity : AppCompatActivity() {
    private lateinit var accu: AccuClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accu = AccuClient(applicationContext)

        if (!accu.isAccuInstalled()) {
            // Show "Please install ACCU" message
            return
        }
        accu.connect()

        lifecycleScope.launch {
            try {
                val service = accu.awaitService()   // waits for binding
                val status  = service.checkPermission()

                if (status != AccuClient.PERMISSION_GRANTED) {
                    val result = accu.requestPermission()  // shows ACCU dialog
                    if (result != AccuClient.PERMISSION_GRANTED) {
                        return@launch   // user denied
                    }
                }
                // ✅ Ready to use all API methods
                doPrivilegedWork(service)

            } catch (e: Exception) {
                Log.e("MyApp", "ACCU not available: ${'$'}{e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        accu.disconnect()
    }
}""", "kotlin", clipboard)
    },

    // 4. Shell API
    DocSection("shell", Icons.Default.Terminal, "Step 3 — Shell Commands", "exec(), execAsync(), execAndGetOutput()") {
        Note("Requires SCOPE_SHELL. This gives full shell access equivalent to 'adb shell'. Use responsibly.", AccentOrange)

        Heading("4.1 Synchronous exec()")
        Para("exec() returns String[3]: [stdout, stderr, exitCode]. Always called on a background thread (it blocks).")
        CodeBlock("""// Run on a background thread / coroutine
val result = service.exec("pm list packages -3")
val stdout   = result[0]         // output text
val stderr   = result[1]         // error text (empty if success)
val exitCode = result[2].toInt() // 0 = success

// Examples:
service.exec("settings put secure dark_ui_mode 1")
service.exec("wm density 420")
service.exec("input tap 540 960")
service.exec("am start -n com.example/.MainActivity")""", "kotlin", clipboard)

        Heading("4.2 Streaming execAsync()")
        Para("For long-running commands (logcat, ping, tcpdump), use execAsync() with an IAccuProcessCallback to receive output line by line.")
        CodeBlock("""service.execAsync(
    "logcat -v time -d",
    object : IAccuProcessCallback.Stub() {
        override fun onStdoutLine(line: String) {
            runOnUiThread { appendLog(line) }
        }
        override fun onStderrLine(line: String) {
            runOnUiThread { appendError(line) }
        }
        override fun onExit(exitCode: Int) {
            runOnUiThread { showDone(exitCode) }
        }
    }
)""", "kotlin", clipboard)

        Heading("4.3 Convenience execAndGetOutput()")
        CodeBlock("""val output = service.execAndGetOutput("getprop ro.build.version.release")
Log.d("Android", "Version: ${'$'}output")   // e.g. "14"
""", "kotlin", clipboard)

        Heading("Common useful commands")
        val commands = listOf(
            "pm list packages -3" to "List all 3rd-party packages",
            "pm list packages -s" to "List system packages",
            "dumpsys battery" to "Battery state dump",
            "dumpsys activity top" to "Currently active app",
            "wm size" to "Screen resolution",
            "wm density" to "Screen density (dpi)",
            "settings get secure android_id" to "Get Android ID",
            "getprop ro.product.model" to "Device model",
            "pm get-install-location" to "Install location setting",
            "content query --uri content://settings/secure" to "All secure settings",
        )
        commands.forEach { (cmd, desc) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text("·", fontWeight = FontWeight.Bold, modifier = Modifier.width(8.dp))
                Column {
                    Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    },

    // 5. Package management
    DocSection("packages", Icons.Default.Apps, "Step 4 — Package Management", "Install, disable, freeze, clear") {
        Note("Requires SCOPE_PACKAGE_MANAGE. All ops use pm / am under the hood at shell level.", AccentOrange)

        Heading("5.1 Install APK")
        CodeBlock("""// APK must be accessible at the path (file system, not content URI)
val installed = service.installApk("/sdcard/Download/myapp.apk", null)
if (installed) Log.d("ACCU", "Install success")""", "kotlin", clipboard)

        Heading("5.2 Freeze / disable / hide")
        CodeBlock("""// Disable (pm disable-user --user 0) — app hidden, data kept
service.disablePackage("com.bloatware.app")

// Suspend (pm suspend) — icon greyed, can't be opened
service.suspendPackage("com.distracting.app")

// Hide (pm hide) — invisible, soft-uninstall
service.hidePackage("com.bloatware.app")

// Restore any of the above:
service.enablePackage("com.bloatware.app")
service.unsuspendPackage("com.distracting.app")
service.unhidePackage("com.bloatware.app")""", "kotlin", clipboard)

        Heading("5.3 Uninstall")
        CodeBlock("""// Remove app for current user
service.uninstallPackage("com.old.app")

// Remove but keep data (reversible — reinstall brings data back)
service.uninstallKeepData("com.old.app")""", "kotlin", clipboard)

        Heading("5.4 Component control (Blocker-style)")
        CodeBlock("""// Disable a specific component (receiver, service, activity, provider)
service.disableComponent(
    "com.example.app",
    "com.example.app.trackers.FirebaseInitProvider"   // full class name
)

// Re-enable it
service.enableComponent(
    "com.example.app",
    "com.example.app.trackers.FirebaseInitProvider"
)""", "kotlin", clipboard)

        Heading("5.5 Clear data / force stop")
        CodeBlock("""service.clearPackageData("com.example.app")   // clears cache + data
service.forceStop("com.example.app")          // am force-stop""", "kotlin", clipboard)
    },

    // 6. Permissions + App Ops
    DocSection("permissions", Icons.Default.Security, "Step 5 — Runtime Permissions & App Ops", "Grant, revoke, and control AppOps") {
        Note("Requires SCOPE_PERMISSIONS.", AccentOrange)

        Heading("6.1 Grant / revoke runtime permissions")
        CodeBlock("""// Grant CAMERA to another app
service.grantPermission("com.camera.app", "android.permission.CAMERA")

// Revoke LOCATION from a tracker
service.revokePermission("com.tracker.app", "android.permission.ACCESS_FINE_LOCATION")

// Common permissions:
// android.permission.CAMERA
// android.permission.RECORD_AUDIO
// android.permission.ACCESS_FINE_LOCATION
// android.permission.ACCESS_COARSE_LOCATION
// android.permission.READ_CONTACTS
// android.permission.READ_CALL_LOG
// android.permission.BODY_SENSORS""", "kotlin", clipboard)

        Heading("6.2 App Ops (fine-grained per-op control)")
        Para("App Ops allow you to control individual system operations beyond the runtime permission system. Each op can be set to: allow | deny | ignore | default")
        CodeBlock("""// Deny microphone access (even if permission is granted)
service.setAppOp("com.voiceapp", "RECORD_AUDIO", "deny")

// Allow camera op
service.setAppOp("com.camera", "CAMERA", "allow")

// Check current mode
val mode = service.getAppOp("com.voiceapp", "RECORD_AUDIO")
// Returns: "allow" | "deny" | "ignore" | "default" | "error"

// Useful App Ops:
// CAMERA, RECORD_AUDIO, READ_CONTACTS, WRITE_CONTACTS
// FINE_LOCATION, COARSE_LOCATION, GPS
// READ_CALL_LOG, WRITE_CALL_LOG, READ_EXTERNAL_STORAGE
// WRITE_EXTERNAL_STORAGE, SYSTEM_ALERT_WINDOW
// REQUEST_INSTALL_PACKAGES, RUN_IN_BACKGROUND""", "kotlin", clipboard)
    },

    // 7. Settings
    DocSection("settings", Icons.Default.Settings, "Step 6 — System Settings", "Read/write Secure, Global, System settings") {
        Note("Requires SCOPE_SETTINGS. Changes are immediate and survive reboots.", AccentOrange)

        Heading("7.1 Settings.Secure")
        Para("Secure settings are user-specific and require elevated permission to write.")
        CodeBlock("""// Enable dark mode
service.writeSecureSetting("ui_night_mode", "2")   // 2=dark, 1=light, 0=auto

// Disable animations (accessibility / speed)
service.writeSecureSetting("animator_duration_scale", "0")
service.writeSecureSetting("transition_animation_scale", "0")
service.writeSecureSetting("window_animation_scale", "0")

// Read any secure setting
val darkMode = service.readSecureSetting("ui_night_mode")""", "kotlin", clipboard)

        Heading("7.2 Settings.Global")
        CodeBlock("""// ADB over WiFi (port 5555)
service.writeGlobalSetting("adb_wifi_enabled", "1")

// Stay awake while charging
service.writeGlobalSetting("stay_on_while_plugged_in", "3")

// USB debugging
service.writeGlobalSetting("adb_enabled", "1")

// Animation scales
service.writeGlobalSetting("animator_duration_scale", "0.5")""", "kotlin", clipboard)

        Heading("7.3 Settings.System")
        CodeBlock("""// Screen timeout (ms)
service.writeSystemSetting("screen_off_timeout", "300000")  // 5 min

// Brightness (0–255)
service.writeSystemSetting("screen_brightness", "128")

// Auto brightness
service.writeSystemSetting("screen_brightness_mode", "1")  // 1=auto

// Font scale
service.writeSystemSetting("font_scale", "1.15")

// Display size (density)
service.writeSystemSetting("display_density_forced", "420")""", "kotlin", clipboard)
    },

    // 8. Locale
    DocSection("locale", Icons.Default.Translate, "Step 7 — Per-App Locale", "Set language per app (Language Selector)") {
        Note("Requires SCOPE_LOCALE. Uses ActivityManager.setApplicationLocales() API (Android 13+). On older versions, falls back to am set-app-locale.", AccentCyan)

        CodeBlock("""// Set Twitter to English
service.setApplicationLocale("com.twitter.android", "en-US")

// Set WhatsApp to Japanese
service.setApplicationLocale("com.whatsapp", "ja-JP")

// Reset to system default
service.setApplicationLocale("com.whatsapp", "")

// More BCP 47 locale tags:
// "zh-CN"  = Chinese Simplified
// "zh-TW"  = Chinese Traditional
// "de-DE"  = German
// "fr-FR"  = French
// "ar-SA"  = Arabic
// "ko-KR"  = Korean
// "pt-BR"  = Portuguese (Brazil)
// "es-419" = Spanish (Latin America)""", "kotlin", clipboard)
    },

    // 9. Error handling
    DocSection("errors", Icons.Default.BugReport, "Step 8 — Error Handling & Best Practices", "Exceptions, dead binder, reconnection") {
        Heading("9.1 Permission result codes")
        CodeBlock("""when (service.checkPermission()) {
    AccuClient.PERMISSION_GRANTED       -> doWork()   // 0
    AccuClient.PERMISSION_DENIED        -> showDenied() // 1
    AccuClient.PERMISSION_NOT_REQUESTED -> askPermission() // -1
    AccuClient.PERMISSION_SERVICE_ERROR -> showInstallPrompt() // -2
}""", "kotlin", clipboard)

        Heading("9.2 Handle SecurityException")
        Para("If you call a method without the required scope, ACCU throws SecurityException. Always catch it.")
        CodeBlock("""try {
    val result = service.exec("pm list packages")
    // use result
} catch (e: SecurityException) {
    // Missing scope or permission not granted
    Log.e("ACCU", "Permission denied: ${'$'}{e.message}")
} catch (e: android.os.DeadObjectException) {
    // ACCU service was killed — rebind
    accu.disconnect()
    accu.connect()
} catch (e: Exception) {
    Log.e("ACCU", "Unexpected error: ${'$'}{e.message}")
}""", "kotlin", clipboard)

        Heading("9.3 Check if ACCU is installed before binding")
        CodeBlock("""fun checkAccuInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.accu.controlcenter", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }
}

// Show install prompt if needed
if (!checkAccuInstalled(this)) {
    AlertDialog.Builder(this)
        .setTitle("ACCU Required")
        .setMessage("This feature requires ACCU (Android Control Center) to be installed.")
        .setPositiveButton("Install") { _, _ ->
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.accu.controlcenter")))
        }
        .show()
}""", "kotlin", clipboard)

        Heading("9.4 ViewModel pattern (recommended)")
        CodeBlock("""@HiltViewModel
class MyViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val accu = AccuClient(context)
    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            accu.connect()
            try {
                val svc = accu.awaitService()
                if (svc.checkPermission() != AccuClient.PERMISSION_GRANTED) {
                    val r = accu.requestPermission()
                    if (r != AccuClient.PERMISSION_GRANTED) return@launch
                }
                _isReady.value = true
            } catch (e: Exception) {
                // ACCU unavailable
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        accu.disconnect()
    }

    fun disableApp(pkg: String) = viewModelScope.launch(Dispatchers.IO) {
        accu.getServiceOrNull()?.disablePackage(pkg)
    }
}""", "kotlin", clipboard)

        Heading("9.5 Complete checklist")
        listOf(
            "✅  Copy 3 AIDL files to app/src/main/aidl/com/accu/api/",
            "✅  Enable 'aidl = true' in buildFeatures",
            "✅  Add <queries> block to AndroidManifest.xml for Android 11+",
            "✅  Call connect() in onCreate, disconnect() in onDestroy",
            "✅  Always call checkPermission() before privileged methods",
            "✅  Run blocking exec() / package ops on Dispatchers.IO",
            "✅  Catch SecurityException and DeadObjectException",
            "✅  Tell users they need ACCU installed if not found",
        ).forEach { item ->
            Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        }
    },

    // 10. Full API reference
    DocSection("reference", Icons.Default.List, "Full API Reference", "Every method in IAccuService — signature + scope") {
        Heading("Identity")
        MethodRow("getVersion(): Int", "—", "IPC protocol version. Currently 1.")
        MethodRow("getUid(): Int", "—", "UID of ACCU's process (0=root, 2000=shell via Shizuku).")
        MethodRow("getPid(): Int", "—", "PID of AccuSystemService.")
        MethodRow("getAccuVersion(): String", "—", "ACCU app version string, e.g. \"2.0.0\".")
        MethodRow("ping(): Boolean", "—", "Returns true if the service is alive.")

        Heading("Permission System")
        MethodRow("requestPermission(callback)", "—", "Show ACCU permission dialog. Result delivered via IAccuPermissionCallback.onPermissionResult(int).")
        MethodRow("checkPermission(): Int", "—", "0=granted, 1=denied, -1=not requested, -2=error.")
        MethodRow("hasScope(scope): Boolean", "—", "True if caller has the named scope.")
        MethodRow("revokeSelf()", "—", "Client revokes its own ACCU permission (useful for logout flow).")

        Heading("Shell")
        MethodRow("exec(cmd): String[3]", "SHELL", "[stdout, stderr, exitCode] — synchronous, blocks caller thread.")
        MethodRow("execAsync(cmd, cb)", "SHELL", "Streams stdout/stderr line-by-line. onExit() called when done.")
        MethodRow("execAndGetOutput(cmd): String", "SHELL", "Returns combined stdout+stderr as one string.")

        Heading("Package Management")
        MethodRow("installApk(path, installer): Boolean", "PACKAGE_MANAGE", "pm install -r on filesystem path.")
        MethodRow("uninstallPackage(pkg): Boolean", "PACKAGE_MANAGE", "pm uninstall --user 0.")
        MethodRow("uninstallKeepData(pkg): Boolean", "PACKAGE_MANAGE", "pm uninstall -k --user 0 (data preserved).")
        MethodRow("enablePackage(pkg): Boolean", "PACKAGE_MANAGE", "pm enable --user 0.")
        MethodRow("disablePackage(pkg): Boolean", "PACKAGE_MANAGE", "pm disable-user --user 0.")
        MethodRow("hidePackage(pkg): Boolean", "PACKAGE_MANAGE", "pm hide --user 0.")
        MethodRow("unhidePackage(pkg): Boolean", "PACKAGE_MANAGE", "pm unhide --user 0.")
        MethodRow("suspendPackage(pkg): Boolean", "PACKAGE_MANAGE", "pm suspend --user 0.")
        MethodRow("unsuspendPackage(pkg): Boolean", "PACKAGE_MANAGE", "pm unsuspend --user 0.")
        MethodRow("clearPackageData(pkg): Boolean", "PACKAGE_MANAGE", "pm clear — removes all data and cache.")
        MethodRow("enableComponent(pkg, cls): Boolean", "PACKAGE_MANAGE", "pm enable pkg/ComponentClass.")
        MethodRow("disableComponent(pkg, cls): Boolean", "PACKAGE_MANAGE", "pm disable pkg/ComponentClass.")

        Heading("Runtime Permissions")
        MethodRow("grantPermission(pkg, perm): Boolean", "PERMISSIONS", "pm grant pkg android.permission.X")
        MethodRow("revokePermission(pkg, perm): Boolean", "PERMISSIONS", "pm revoke pkg android.permission.X")
        MethodRow("setAppOp(pkg, op, mode): Boolean", "PERMISSIONS", "appops set pkg OP allow|deny|ignore|default")
        MethodRow("getAppOp(pkg, op): String", "PERMISSIONS", "appops get pkg OP — returns current mode string.")

        Heading("Activity Manager")
        MethodRow("forceStop(pkg): Boolean", "PACKAGE_MANAGE", "am force-stop pkg")
        MethodRow("setApplicationLocale(pkg, locale): Boolean", "LOCALE", "Set per-app BCP 47 locale. Empty string resets to system.")

        Heading("System Settings")
        MethodRow("writeSecureSetting(name, value): Boolean", "SETTINGS", "settings put secure name value")
        MethodRow("readSecureSetting(name): String", "SETTINGS", "settings get secure name")
        MethodRow("writeGlobalSetting(name, value): Boolean", "SETTINGS", "settings put global name value")
        MethodRow("readGlobalSetting(name): String", "SETTINGS", "settings get global name")
        MethodRow("writeSystemSetting(name, value): Boolean", "SETTINGS", "settings put system name value")
        MethodRow("readSystemSetting(name): String", "SETTINGS", "settings get system name")
    },
)
