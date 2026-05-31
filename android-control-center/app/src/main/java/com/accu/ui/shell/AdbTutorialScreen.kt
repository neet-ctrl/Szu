package com.accu.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed

// ── Data model ────────────────────────────────────────────────────────────────

private data class TutorialStep(
    val title: String,
    val body: String,
    val code: String? = null,
    val tip: String? = null,
    val warning: String? = null,
)

private data class TutorialSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val steps: List<TutorialStep>,
)

// ─────────────────────────────────────────────────────────────────────────────

private val WIFI_ADB_TUTORIAL = TutorialSection(
    title    = "Wi-Fi ADB",
    subtitle = "Wireless debugging between two Android devices on the same network",
    icon     = Icons.Default.Wifi,
    color    = AccentCyan,
    steps    = listOf(
        TutorialStep(
            title = "Enable Developer Options on Target Device",
            body  = "The device you want to control (\"Target\") must have Developer Options unlocked:",
            code  = """Settings  →  About phone  →  Build number
Tap \"Build number\" 7 times rapidly
→ You'll see \"You are now a developer!\"

Then go to:
Settings  →  Developer Options""",
            tip   = "On Samsung: Settings → About phone → Software information → Build number. On MIUI: Settings → About phone → MIUI version.",
        ),
        TutorialStep(
            title   = "Enable Wireless Debugging (Android 11+)",
            body    = "In Developer Options on the Target device:",
            code    = "Settings → Developer Options → Wireless debugging → Toggle ON",
            tip     = "Both devices must be on the same Wi-Fi network (same router/AP). Mobile hotspot from one phone works too!",
            warning = "Wireless debugging disables automatically when you disconnect from Wi-Fi or restart the device.",
        ),
        TutorialStep(
            title = "Find the IP Address and Port",
            body  = "Under Wireless debugging you'll see the IP address and port. Note these — you'll need them.",
            code  = """Example display on target device:
  IP address & Port:  192.168.1.42:41547

Or run in shell:
  ip route | awk '{print $9}'
  # or
  ifconfig wlan0 | grep inet""",
            tip   = "The port shown next to the IP is the connection port (different from the pairing port).",
        ),
        TutorialStep(
            title = "Method A: Pair with Pairing Code",
            body  = "On Target: tap \"Pair device with pairing code\" → note the 6-digit code and pairing port.\n\nIn ACCU (on your host phone), go to Shell → Wi-Fi ADB and enter:",
            code  = """adb pair 192.168.1.42:<pairing_port> <6digit_code>

Example:
  adb pair 192.168.1.42:37839 123456
  → Enter pairing code: 123456
  → Successfully paired to 192.168.1.42:37839""",
            tip   = "Use the PAIRING port (shown in the pair dialog), not the connection port.",
        ),
        TutorialStep(
            title = "Method B: QR Code Pairing",
            body  = "On Target: tap \"Pair device with QR code\" — a QR code will appear.\n\nOn ACCU host phone, go to Shizuku → ADB Pairing → QR mode and scan the displayed QR code.",
            tip   = "QR pairing is only available on Android 11+ (API 30+). Both devices need Android 11+.",
        ),
        TutorialStep(
            title = "Connect After Pairing",
            body  = "After pairing, connect using the CONNECTION port (shown on the Wireless debugging main page):",
            code  = """adb connect 192.168.1.42:<connection_port>

Example:
  adb connect 192.168.1.42:41547
  → connected to 192.168.1.42:41547

Verify connection:
  adb devices
  → 192.168.1.42:41547  device""",
            tip   = "Pairing only needs to be done once per Wi-Fi network. After that, just connect with the IP and connection port each session.",
        ),
        TutorialStep(
            title = "Legacy Method: adb tcpip (Android 10 and below)",
            body  = "For older Android devices (no Wireless Debugging menu). The target device needs a USB connection first:",
            code  = """Step 1: Enable USB debugging on target
  Settings → Developer Options → USB debugging → ON

Step 2: Connect target to host via USB-OTG (or PC)
  adb devices
  → Should show your device

Step 3: Switch to TCP mode
  adb tcpip 5555

Step 4: Disconnect USB, then connect wirelessly
  adb connect 192.168.1.42:5555

Step 5: Verify
  adb shell getprop ro.product.model""",
            warning = "This method requires USB + physical connection first to set the TCP port. Android 11+ does not need this.",
        ),
        TutorialStep(
            title = "You're Connected! Run ADB Commands",
            body  = "Now go to ACCU → Shell → Wi-Fi ADB tab and run any command:\n\nOr use these directly:",
            code  = """adb shell getprop ro.product.model   # Device model
adb shell pm list packages -3        # User apps
adb shell dumpsys battery            # Battery info
adb shell screencap -p /sdcard/s.png # Screenshot
adb pull /sdcard/s.png               # Pull to host
adb shell am start -n com.pkg/.Activity  # Launch app""",
        ),
        TutorialStep(
            title = "Disconnect",
            body  = "When done:",
            code  = """adb disconnect 192.168.1.42:41547  # Specific device
adb disconnect                       # All devices""",
            tip   = "Or toggle off Wireless Debugging on the target to prevent further connections.",
        ),
    )
)

private val OTG_ADB_TUTORIAL = TutorialSection(
    title    = "OTG ADB (USB)",
    subtitle = "Control one Android device from another via USB OTG cable",
    icon     = Icons.Default.Usb,
    color    = AccentOrange,
    steps    = listOf(
        TutorialStep(
            title   = "Hardware Requirements",
            body    = "You need specific hardware for phone-to-phone USB ADB:",
            code    = """HOST phone (running ACCU):
  • Must support USB OTG host mode
  • Android 5.0+ recommended
  • Check: "Does my phone support USB OTG host mode?"

TARGET phone (being controlled):
  • Standard Android phone
  • USB debugging enabled

Cable:
  • USB-C OTG adapter + USB-C cable, OR
  • USB-C to USB-C OTG cable (host/device roles auto-assigned), OR
  • USB-A OTG adapter for older phones""",
            tip     = "Most phones from 2018+ support USB OTG host mode. Budget phones may not. Pixel, Samsung, OnePlus, Xiaomi flagships all support it.",
            warning = "A standard USB-C cable alone will NOT work — you need one with OTG host capability, or a USB OTG adapter.",
        ),
        TutorialStep(
            title = "Enable USB Debugging on Target Phone",
            body  = "On the TARGET phone (the one you want to control):",
            code  = """1. Settings  →  About phone  →  Build number
   Tap 7 times  →  Developer mode ON

2. Settings  →  Developer Options
   →  USB debugging  →  Toggle ON

3. Optional but recommended:
   →  Install via USB  →  ON
   →  USB debugging (Security settings)  →  ON""",
            tip   = "On Samsung: USB debugging is under Settings → Developer Options → USB debugging. On MIUI: same path but you may also need to enable \"USB Debugging (Security Settings)\" for full permissions.",
        ),
        TutorialStep(
            title = "Connect the Phones",
            body  = "Connect the two phones with your OTG cable/adapter:",
            code  = """HOST phone  ←→  [USB-OTG cable/adapter]  ←→  TARGET phone

Connection options:
  A) USB-C OTG adapter on HOST + USB-C cable to TARGET
  B) USB-A OTG adapter on HOST + USB-A cable to TARGET
  C) USB-C to USB-C OTG cable (check labeling carefully)""",
            tip   = "If using a USB-A OTG adapter, plug the OTG adapter into the HOST phone, then connect the USB-A cable to the TARGET phone's USB-C port using a USB-A to C cable.",
            warning = "Some USB-C cables are \"charge only\" — they won't carry data. Use a cable that supports USB 2.0 or higher data transfer.",
        ),
        TutorialStep(
            title = "Allow USB Debugging on Target",
            body  = "When you plug in, the TARGET phone will show a dialog:",
            code  = """[Target phone shows]
\"Allow USB debugging?\"
RSA key fingerprint: AB:CD:EF:...

☑ Always allow from this computer
→ Tap [Allow]

If the dialog doesn't appear:
  • Swipe down notification shade on target
  • Change USB mode from \"Charging\" to \"File Transfer\"
  • Then disconnect & reconnect""",
            tip   = "Tick \"Always allow\" so you don't need to re-approve on every connection.",
        ),
        TutorialStep(
            title = "Open ACCU Shell in OTG Mode",
            body  = "On the HOST phone (running ACCU):",
            code  = """1. Open ACCU  →  Shell
2. Tap the \"OTG ADB\" tab
3. Wait for the target device to appear
4. ACCU will detect the connected device

Verify detection:
  adb devices
  → Should show: <serial>  device

If it shows \"unauthorized\":
  → Check target phone — a permission dialog may be waiting""",
            warning = "If ACCU shows \"no devices found\", try: unlock the target phone screen, re-plug the cable, or tap \"Change USB preference\" in the target's notification.",
        ),
        TutorialStep(
            title = "Run ADB Commands on Target",
            body  = "Once connected, you can run any ADB command on the target phone:",
            code  = """# Device info
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

# List installed apps
adb shell pm list packages -3

# Screenshot and pull to host
adb shell screencap -p /sdcard/ss.png
adb pull /sdcard/ss.png

# Install APK from host to target
adb install /path/to/app.apk

# Remove bloatware
adb shell pm uninstall --user 0 com.bloat.app

# Force stop app
adb shell am force-stop com.example.app

# File transfer
adb push local_file.txt /sdcard/
adb pull /sdcard/remote_file.txt""",
            tip   = "Use the ACCU File Browser (Shell → OTG → File Browser icon) for a visual file manager on the target device.",
        ),
        TutorialStep(
            title = "Switch Target to Wireless After OTG Connect",
            body  = "Once connected via OTG, you can switch to wireless to remove the cable:",
            code  = """# While OTG connected, enable TCP on target
adb tcpip 5555

# Get target's IP address
adb shell ip route | awk '{print $9}'

# Disconnect OTG cable, then connect wirelessly
adb connect <TARGET_IP>:5555

# Verify
adb devices""",
            tip   = "This hybrid approach lets you set up wireless ADB without going through Developer Options UI on older Android versions.",
        ),
        TutorialStep(
            title = "Disconnect",
            body  = "When done:",
            code  = """adb kill-server  # Stop ADB daemon on host
# Then simply unplug the cable

# Or if switched to wireless:
adb disconnect <TARGET_IP>:5555""",
        ),
    )
)

private val FEATURES_TUTORIAL = TutorialSection(
    title    = "All ADB Features in ACCU",
    subtitle = "Complete reference of everything you can do once connected",
    icon     = Icons.Default.List,
    color    = AccentGreen,
    steps    = listOf(
        TutorialStep(
            title = "ADB Shell — Interactive Terminal",
            body  = "ACCU → Shell\nFull terminal with 3 modes: Local (Shizuku), Wi-Fi ADB, OTG ADB.",
            code  = """Features:
  • Run any ADB / shell command
  • Command history (↑/↓ keys)
  • Bookmarks for frequent commands
  • AI-powered command suggestions
  • Output search (Ctrl+F)
  • Save output to file
  • 200+ preloaded command examples
  • Script editor & runner""",
        ),
        TutorialStep(
            title = "App Management",
            body  = "ACCU → App Manager",
            code  = """Commands available:
  adb shell pm list packages -3         # User apps
  adb shell pm list packages -s         # System apps
  adb shell pm uninstall --user 0 <pkg> # Remove bloat
  adb shell pm disable-user <pkg>       # Disable
  adb shell pm enable <pkg>             # Enable
  adb shell pm clear <pkg>              # Clear data
  adb shell am force-stop <pkg>         # Force stop
  adb shell monkey -p <pkg> 1           # Launch app
  adb shell pm path <pkg>               # APK path
  adb pull <apk_path>                   # Extract APK""",
        ),
        TutorialStep(
            title = "Package Information",
            body  = "ACCU → App Manager → App detail",
            code  = """adb shell dumpsys package <pkg>    # Full info
adb shell pm list permissions <pkg> # Permissions
adb shell pm list instrumentation   # Test runners
adb shell pm dump <pkg> | grep versionName  # Version
adb shell pm dump <pkg> | grep firstInstallTime""",
        ),
        TutorialStep(
            title = "File Management",
            body  = "ACCU → Shell → File Browser (folder icon when connected)",
            code  = """adb shell ls -la /sdcard/          # List files
adb shell ls -la /sdcard/Android/data/
adb push local.txt /sdcard/        # Upload
adb pull /sdcard/file.txt          # Download
adb shell rm /sdcard/file.txt      # Delete
adb shell mkdir /sdcard/new_folder # Create folder
adb shell mv /sdcard/a.txt /sdcard/b.txt  # Rename
adb shell cp /sdcard/src.txt /sdcard/dst.txt""",
        ),
        TutorialStep(
            title = "Process Manager",
            body  = "ACCU → Shell → Processes (from Shell top bar or ADB Hub)",
            code  = """adb shell ps -A                    # All processes
adb shell top -b -n1               # CPU/RAM usage
adb shell kill <PID>               # Kill process
adb shell dumpsys activity services # Active services
adb shell am kill <package>         # Kill app process""",
        ),
        TutorialStep(
            title = "Logcat",
            body  = "ACCU → Shell → Logcat",
            code  = """adb logcat                        # All logs
adb logcat -v time                 # With timestamps
adb logcat ActivityManager:I *:S   # Filter by tag/level
adb logcat | grep -i "error"       # Search
adb logcat -d > log.txt            # Save to file
adb logcat -c                      # Clear log buffer""",
        ),
        TutorialStep(
            title = "Screenshots & Screen Recording",
            body  = "ACCU → Shell → Screen Capture",
            code  = """# Screenshot
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png
adb shell rm /sdcard/screen.png

# Screen recording (mp4, max 3 min)
adb shell screenrecord /sdcard/record.mp4
# Press Ctrl+C to stop, then:
adb pull /sdcard/record.mp4

# Custom options:
adb shell screenrecord --size 1280x720 --bit-rate 4000000 /sdcard/record.mp4""",
        ),
        TutorialStep(
            title = "Device Information",
            body  = "ACCU → Shell → Device Info",
            code  = """adb shell getprop                 # All properties
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell cat /proc/meminfo
adb shell cat /proc/cpuinfo
adb shell dumpsys battery
adb shell wm size && wm density""",
        ),
        TutorialStep(
            title = "Fastboot & Device Control",
            body  = "ACCU → Shell → Fastboot",
            code  = """adb reboot                        # Normal reboot
adb reboot bootloader             # Bootloader mode
adb reboot recovery               # Recovery mode
adb shell input keyevent 26       # Power button
adb shell input keyevent 224      # Wake screen
adb shell input tap 540 960       # Tap at x,y
adb shell input text "hello"      # Type text
fastboot devices                  # In bootloader
fastboot reboot                   # Exit fastboot""",
        ),
        TutorialStep(
            title = "Permissions, Settings & Power-User Commands",
            body  = "All available via ACCU Shell → Command Examples",
            code  = """# Grant/Revoke permissions
adb shell pm grant <pkg> android.permission.READ_CONTACTS
adb shell pm revoke <pkg> android.permission.CAMERA

# Settings database
adb shell settings get global airplane_mode_on
adb shell settings put global airplane_mode_on 1
adb shell settings put secure install_non_market_apps 1

# Activity manager
adb shell am start -a android.intent.action.VIEW -d "https://example.com"
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
adb shell am start-activity -n com.pkg/.HiddenActivity

# Animation scale (speed up / disable)
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0""",
        ),
    )
)

private val TROUBLESHOOT_TUTORIAL = TutorialSection(
    title    = "Troubleshooting",
    subtitle = "Common problems and how to fix them",
    icon     = Icons.Default.BugReport,
    color    = AccentRed,
    steps    = listOf(
        TutorialStep(
            title   = "\"no devices/emulators found\"",
            body    = "ADB cannot see the connected device.",
            code    = """Checks:
  1. USB debugging is enabled on target (Developer Options)
  2. USB cable supports data (not charge-only)
  3. Target screen is unlocked when connecting
  4. Check USB notification on target → \"File Transfer\" mode
  5. Run: adb kill-server && adb start-server
  6. Try a different USB port / cable

For OTG:
  7. Host phone supports USB OTG host mode
  8. OTG adapter (not a regular cable) is used""",
            warning = "Charge-only USB-C cables are very common. Always test with a known-good data cable.",
        ),
        TutorialStep(
            title = "\"unauthorized\" after adb devices",
            body  = "The target device hasn't approved the debug connection.",
            code  = """Fix:
  1. Look at target phone screen for an RSA dialog
  2. Check notification shade
  3. Tap [Allow]

If dialog doesn't appear:
  1. adb kill-server
  2. Delete ~/.android/adbkey (host side, if on PC)
  3. On target: Developer Options → Revoke USB debugging authorizations
  4. Reconnect""",
        ),
        TutorialStep(
            title = "Wi-Fi ADB: \"Connection refused\"",
            body  = "Cannot connect wirelessly to target.",
            code  = """Checks:
  1. Both phones on same Wi-Fi? (same SSID/router)
  2. IP address correct? Run: ip addr show wlan0
  3. Wireless debugging still enabled on target?
  4. Firewall/VPN on host phone interfering?
  5. Try: adb disconnect  (disconnect all first)
     Then: adb connect <IP>:<PORT>
  6. Port number correct? Get from Developer Options → Wireless debugging
  7. Phone went to sleep? Wake target & reconnect""",
        ),
        TutorialStep(
            title = "Pairing fails / wrong code",
            body  = "The 6-digit pairing code doesn't work.",
            code  = """Fix:
  1. Code expires after ~60 seconds — request a new one
  2. Use pairing PORT (not connection port — they're different)
  3. Both devices must be on same Wi-Fi network
  4. Disable VPN on both phones
  5. Retry: generate a fresh code on target, use immediately""",
        ),
        TutorialStep(
            title = "adb tcpip 5555 fails",
            body  = "Cannot switch to TCP mode.",
            code  = """Fix:
  1. Device must be detected first: adb devices → shows device
  2. Run: adb tcpip 5555 (wait for \"restarting in TCP mode\" message)
  3. Unplug cable, wait 3 seconds
  4. Run: adb connect <device_IP>:5555
  5. If it disconnects on sleep: adb shell svc wifi enable""",
        ),
        TutorialStep(
            title = "Permission denied for certain commands",
            body  = "Some ADB commands require root or elevated privileges.",
            code  = """Workaround options:
  1. Enable ACCU System Service (Shizuku) → higher privilege
  2. For pm commands with --user 0: may need system-level ADB
  3. Check if device has a custom ROM with relaxed permissions
  4. Some commands need root: su -c "command"
  5. Grant INSTALL_PACKAGES via:
     adb shell pm grant com.accu android.permission.INSTALL_PACKAGES""",
        ),
    )
)

private val ALL_SECTIONS = listOf(WIFI_ADB_TUTORIAL, OTG_ADB_TUTORIAL, FEATURES_TUTORIAL, TROUBLESHOOT_TUTORIAL)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbTutorialScreen(onBack: () -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    var selectedSection by remember { mutableIntStateOf(0) }
    val section = ALL_SECTIONS[selectedSection]
    val expandedSteps = remember { mutableStateMapOf<Int, Boolean>().also { map -> ALL_SECTIONS[0].steps.indices.forEach { map[it] = true } } }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "ADB Connection Guide",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Section selector
            item {
                Column(Modifier.fillMaxWidth()) {
                    ALL_SECTIONS.forEachIndexed { idx, sec ->
                        SectionSelectorRow(sec, idx == selectedSection, sec.color, onClick = {
                            selectedSection = idx
                            expandedSteps.clear()
                            sec.steps.indices.forEach { expandedSteps[it] = idx == 0 || idx == 1 }
                        })
                    }
                    HorizontalDivider()
                }
            }

            // Section header
            item {
                Surface(color = section.color.copy(0.08f)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = section.color.copy(0.15f), modifier = Modifier.size(52.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(section.icon, null, Modifier.size(28.dp), tint = section.color) }
                        }
                        Column {
                            Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = section.color)
                            Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Steps
            section.steps.forEachIndexed { idx, step ->
                item(key = "${selectedSection}_$idx") {
                    TutorialStepCard(
                        step = step,
                        stepNumber = idx + 1,
                        sectionColor = section.color,
                        expanded = expandedSteps[idx] != false,
                        onToggle = { expandedSteps[idx] = expandedSteps[idx] == false },
                        clipboard = clipboard,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionSelectorRow(section: TutorialSection, selected: Boolean, color: Color, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(section.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) color else MaterialTheme.colorScheme.onSurface) },
        supportingContent = { Text(section.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
        leadingContent = {
            Surface(shape = CircleShape, color = if (selected) color.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(section.icon, null, Modifier.size(20.dp), tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        trailingContent = { if (selected) Icon(Icons.Default.ChevronRight, null, tint = color) },
        colors = ListItemDefaults.colors(containerColor = if (selected) color.copy(0.04f) else MaterialTheme.colorScheme.surface),
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun TutorialStepCard(
    step: TutorialStep,
    stepNumber: Int,
    sectionColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = sectionColor.copy(0.15f), modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$stepNumber", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = sectionColor)
                }
            }
            Text(step.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step.body.isNotBlank()) {
                    Text(step.body, style = MaterialTheme.typography.bodyMedium)
                }
                step.code?.let { code ->
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Command", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(14.dp))
                                }
                            }
                            Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
                step.tip?.let { tip ->
                    Surface(shape = RoundedCornerShape(8.dp), color = AccentGreen.copy(0.08f)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lightbulb, null, Modifier.size(16.dp), tint = AccentGreen)
                            Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                step.warning?.let { warn ->
                    Surface(shape = RoundedCornerShape(8.dp), color = AccentRed.copy(0.08f)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = AccentRed)
                            Text(warn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}
