package com.accu.ui.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accu.ui.components.InfoTooltipIcon

data class LearningArticle(
    val title: String,
    val summary: String,
    val content: String,
    val icon: ImageVector,
    val accentColor: Color,
    val readTimeMin: Int
)

data class LearningSection(val sectionTitle: String, val articles: List<LearningArticle>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningCenterScreen(onNavigateTo: (String) -> Unit = {}) {
    val sections = remember { buildLearningSections() }
    var searchQuery by remember { mutableStateOf("") }
    val filtered = sections.map { section ->
        section.copy(articles = section.articles.filter {
            searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.summary.contains(searchQuery, ignoreCase = true)
        })
    }.filter { it.articles.isNotEmpty() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Learning Center", fontWeight = FontWeight.Bold)
                            InfoTooltipIcon(
                                title = "Learning Center",
                                description = "In-depth guides for every feature area. Each article explains concepts, step-by-step usage, and advanced tips. Start with 'Getting Started with Shizuku' if you're new."
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search topics…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            filtered.forEach { section ->
                item(key = section.sectionTitle) {
                    Text(
                        section.sectionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(section.articles, key = { it.title }) { article ->
                    LearningArticleCard(article = article)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun LearningArticleCard(article: LearningArticle) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            ListItem(
                headlineContent = { Text(article.title, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(article.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2) },
                leadingContent = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = article.accentColor.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(article.icon, null, Modifier.size(22.dp), tint = article.accentColor)
                        }
                    }
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${article.readTimeMin} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(20.dp))
                    }
                },
                modifier = Modifier.clickable { expanded = !expanded }
            )
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        article.content,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.6
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

fun buildLearningSections(): List<LearningSection> = listOf(
    LearningSection(
        "Getting Started",
        listOf(
            LearningArticle(
                title = "What is Shizuku and Why Do I Need It?",
                summary = "Understand the foundation of most ACC features",
                content = """WHAT IS SHIZUKU?
Shizuku is an Android service that acts as a bridge between user-installed apps and the Android system API. Normally, only system-privileged apps can call sensitive APIs (like toggling Wi-Fi, disabling components, or running pm commands). Shizuku lets you authorize specific apps to call those APIs using your own ADB authorization.

SHIZUKU vs ROOT:
• Shizuku: No permanent system modification. Works with the official Android security model. Can be revoked at any time. Safer.
• Root: Grants full unrestricted system access. Requires a modified bootloader/system. More powerful but higher risk.

ACC uses Shizuku for most features because it provides 90% of the power with much less risk.

HOW SHIZUKU WORKS:
1. You start the Shizuku service once (via ADB command or Wireless Debugging pairing)
2. Shizuku runs as a background service with "shell" user privileges (same as ADB)
3. When ACC needs elevated access, it requests permission from Shizuku
4. Shizuku proxies the API call with the shell user's permissions

STARTING SHIZUKU (Android 11+):
1. Settings → Developer Options → Wireless Debugging → Enable
2. Tap "Pair device with pairing code" → note the IP:port and code
3. Open Shizuku app → Pairing → enter code
4. Shizuku starts automatically

After reboot, Wireless Debugging mode re-enables quickly without re-pairing.""",
                icon = Icons.Outlined.Security,
                accentColor = Color(0xFF6750A4),
                readTimeMin = 4
            ),
            LearningArticle(
                title = "ACC Dashboard Overview",
                summary = "Navigate the main screen and understand status cards",
                content = """THE DASHBOARD is your command center. It shows:

STATUS CARDS:
• Shizuku Status — Green = running, Red = stopped. Tap to go to Shizuku Center.
• Root Access — Shows if root is available and which method (LibSU/Magisk)
• Battery — Level, charging status, temperature
• Network — Active connection, IP address, signal strength

QUICK ACTION TILES:
Fast buttons for the most common operations:
• Freeze All — freeze all tagged apps at once
• Clear Junk — trigger a quick storage clean
• Shell — jump directly to the shell
• Toggle Wi-Fi / Data — quick network toggles

RECENT ACTIVITY:
Last 5 actions performed in ACC (freezes, uninstalls, shell commands, recordings)

TIPS:
• Long-press any status card to pin it as a larger tile
• The dashboard refreshes every 30 seconds automatically
• Tap the ⓘ on any card to learn more about that metric""",
                icon = Icons.Outlined.Dashboard,
                accentColor = Color(0xFF4CAF50),
                readTimeMin = 2
            )
        )
    ),
    LearningSection(
        "Shell & ADB",
        listOf(
            LearningArticle(
                title = "ADB Command Cheat Sheet",
                summary = "The most useful ADB commands organized by category",
                content = """PACKAGE MANAGEMENT:
pm list packages -3          List user-installed apps
pm disable-user --user 0 <pkg>  Freeze an app (Shizuku)
pm enable <pkg>              Unfreeze an app
pm uninstall --user 0 <pkg>  Remove for current user
pm clear <pkg>               Clear app data
pm grant <pkg> <perm>        Grant runtime permission

ACTIVITY MANAGER:
am start -n <pkg>/<activity>  Launch an activity
am force-stop <pkg>           Force stop app
am broadcast -a <action>      Send a broadcast

WINDOW MANAGER:
wm density                   Get screen DPI
wm density 420               Set DPI to 420
wm size reset                Reset resolution to default

SETTINGS:
settings get global wifi_on
settings put secure location_mode 3
settings list global

NETWORK:
svc wifi enable/disable
svc data enable/disable
cmd connectivity airplane-mode enable

SYSTEM:
screencap /sdcard/ss.png    Take screenshot
screenrecord /sdcard/rec.mp4 Record screen
logcat -d > /sdcard/log.txt  Dump logs

USEFUL ONE-LINERS:
Disable animations:
  settings put global animator_duration_scale 0
  settings put global window_animation_scale 0
  settings put global transition_animation_scale 0

Show battery info:
  dumpsys battery | grep level

Get device fingerprint:
  getprop ro.build.fingerprint""",
                icon = Icons.Outlined.Terminal,
                accentColor = Color(0xFF00897B),
                readTimeMin = 5
            ),
            LearningArticle(
                title = "Using Wi-Fi ADB Mode",
                summary = "Connect to remote Android devices over your network",
                content = """Wi-Fi ADB allows you to run ADB commands on any Android device on your local network — great for managing a tablet from your phone, or debugging a device without a cable.

SETUP ON THE TARGET DEVICE (Android 11+):
1. Settings → Developer Options → Wireless Debugging → Enable
2. Note the IP address and port shown (usually 5555 or similar)

CONNECTING IN ACC SHELL:
1. Shell tab → select Wi-Fi mode
2. Tap "Connect" button
3. Enter the target device's IP address and port
4. Tap Connect

PAIRING (first time only):
On the target device: Wireless Debugging → "Pair device with pairing code"
In ACC Shell: "Pair via code" → enter the 6-digit code and pairing port

SAVED DEVICES:
Once connected, ACC saves the device IP for quick reconnection. Tap the saved device to reconnect without re-entering details.

TROUBLESHOOTING:
• Make sure both devices are on the same Wi-Fi network
• Some routers block device-to-device traffic (AP isolation) — use a hotspot if needed
• Port 5555 is default but may change; check Wireless Debugging settings on target""",
                icon = Icons.Outlined.Wifi,
                accentColor = Color(0xFF0288D1),
                readTimeMin = 3
            )
        )
    ),
    LearningSection(
        "App Management",
        listOf(
            LearningArticle(
                title = "Safe Debloating Guide",
                summary = "Remove bloatware without breaking your device",
                content = """Debloating removes pre-installed apps that you don't use. Done correctly, it's safe and reversible.

SAFETY LEVELS (from Canta's community database):
🟢 SAFE — These apps are safe to remove for all users. Removing them won't break anything.
🟡 RECOMMENDED — Safe to remove for most users. A small number of apps depend on these.
🟠 ADVANCED — Remove with caution. Some functionality may be lost.
🔴 EXPERT — Only remove if you know what you're doing. Can break core system functions.

USING PRESETS:
1. Debloat → tap Presets button
2. Select your device brand (Samsung, Xiaomi, Google, OnePlus, etc.)
3. Review the preset — apps are marked with safety levels
4. Select which to remove → Uninstall

REVERSING A DEBLOAT:
Apps removed with pm uninstall --user 0 are only removed for your user profile, NOT from the system partition. To restore:
1. Debloat → filter "Uninstalled" → select app → Reinstall
Or via shell: pm install-existing --user 0 <package.name>

WHAT NEVER TO REMOVE:
• com.android.systemui (System UI)
• com.android.phone (Phone app)
• com.android.settings (Settings)
• Any app labeled "EXPERT" unless you know its purpose""",
                icon = Icons.Outlined.CleaningServices,
                accentColor = Color(0xFFE91E63),
                readTimeMin = 4
            ),
            LearningArticle(
                title = "Component Blocking Deep Dive",
                summary = "Surgically disable app behaviors without fully disabling apps",
                content = """Component blocking lets you disable specific parts of an app while keeping the rest functional. This is more precise than freezing the whole app.

COMPONENT TYPES:
• Activities: UI screens. Blocking prevents the screen from launching.
• Services: Background processes. Blocking stops background work.
• Receivers: Respond to system events (boot, screen off, connectivity). Blocking stops event handling.
• Providers: Expose data to other apps. Blocking prevents data sharing.

COMMON USE CASES:
• Block the analytics/tracking service in an app: App Detail → Components → Services → find analytics service → disable
• Prevent an app from starting on boot: Receivers → find BootReceiver → disable
• Disable in-app ads activity: Activities → find ad activity → disable
• Stop an app from sharing data with others: Providers → disable

TWO BLOCKING METHODS:
1. PM Disable (via Shizuku): Uses pm disable-user. Works without root. May not work on all Samsung/MIUI devices.
2. IFW (Intent Firewall): Adds rules to Android's Intent Firewall. More effective, requires root.

IMPORTING ONLINE RULES:
Component Manager → Online Rules → search for your app to download community-curated blocking rules.""",
                icon = Icons.Outlined.Block,
                accentColor = Color(0xFFFF5722),
                readTimeMin = 5
            )
        )
    ),
    LearningSection(
        "Audio & Calls",
        listOf(
            LearningArticle(
                title = "Setting Up the Audio DSP",
                summary = "System-wide equalizer and effects without root",
                content = """The Audio DSP runs as a foreground service that processes all audio output system-wide. No root or Xposed required.

HOW IT WORKS:
RootlessJamesDSP uses Android's AudioEffect API at the global session (session ID 0), which affects all audio output. Some manufacturers restrict this — if effects don't work, try root mode.

QUICK SETUP:
1. Audio Center → toggle DSP On
2. Select an EQ preset or use AutoEQ to find your headphone's profile
3. Adjust bass boost if needed
4. Enable any other effects (reverb, virtualizer)

AUTOEQ PROFILES:
AutoEQ is a database of thousands of headphone measurement-based correction profiles. These flatten the frequency response of your headphones to neutral (Harman target).
• Search by headphone model name
• Download and apply in one tap
• Significantly improves audio accuracy on most headphones

CONVOLVER (Advanced):
Load .wav impulse response files to simulate headphone speakers, or famous speaker systems.
• Great for making headphones sound like speakers
• Free IR files available from various audio communities

PER-APP BLOCKLIST:
Some apps (phone dialer, voice assistant) sound better without DSP. Add them to the blocklist:
Audio Center → DSP Blocklist → Add app""",
                icon = Icons.Outlined.GraphicEq,
                accentColor = Color(0xFF3F51B5),
                readTimeMin = 4
            ),
            LearningArticle(
                title = "Call Recording Setup & Legality",
                summary = "How to set up ShizuCallRecorder and legal considerations",
                content = """TECHNICAL SETUP:
1. Ensure Shizuku is running
2. Call Recorder → grant microphone permission
3. Grant READ_PHONE_STATE permission
4. Enable Auto-record (or use manual mode)

HOW SCRCPY RECORDING WORKS:
The app bundles the scrcpy server binary. When a call starts, Shizuku launches the scrcpy server which can capture the device's audio output stream (including the earpiece/speaker audio). This is combined with microphone input to produce a stereo recording: left=microphone, right=device output.

RECOMMENDED FORMAT SETTINGS:
• Daily use: OPUS, 128kbps — best quality/size balance
• Archive: FLAC — lossless but large files
• Minimal storage: AAC, 64kbps

FILENAME FORMAT TOKENS:
{date} = Recording date (YYYY-MM-DD)
{time} = Recording time (HH-MM-SS)
{number} = Caller's phone number
{contact} = Contact name (or number if unknown)
Example: {contact}_{date}_{time}

⚠️ LEGAL NOTICE:
Call recording laws vary significantly by jurisdiction:
• One-party consent (legal): Most US states, UK, Australia
• Two-party/all-party consent (requires disclosure): California, many EU countries
• Fully prohibited: Some countries
Always check and comply with your local laws before recording calls.""",
                icon = Icons.Outlined.PhoneCallback,
                accentColor = Color(0xFF4CAF50),
                readTimeMin = 4
            )
        )
    ),
    LearningSection(
        "Customization",
        listOf(
            LearningArticle(
                title = "Material You Color System Explained",
                summary = "Understand and master the Monet theming engine",
                content = """Material You (Monet) generates a complete color palette from a single seed color. ACC's ColorBlendr integration gives you full control over this system.

THE 6 MONET STYLES:
• TONAL_SPOT (default): Muted, tonal palette. Classic Material You look.
• VIBRANT: More saturated version of your seed color.
• EXPRESSIVE: Wild color combinations using complementary hues.
• SPRITZ: Desaturated/grayscale with just a hint of color. Minimal.
• RAINBOW: Spreads color across all palette roles. Colorful.
• FRUIT_SALAD: Similar to rainbow but with different role assignments.

THE 24 COLOR ROLES:
Primary (3): primary, onPrimary, primaryContainer, onPrimaryContainer
Secondary (4): similar roles for secondary accent color
Tertiary (4): similar roles for tertiary/decorative color
Error (4): error states
Surface (6): background, surface variants, outlines
Others: inversePrimary, scrim, surfaceTint, etc.

HOW TO CUSTOMIZE:
1. Customization → Color Scheme → tap seed color swatch
2. Pick any color from the color wheel
3. See the full palette update in real time
4. Change Monet Style to see different palette variations
5. Tap Color Editor to override individual color roles
6. Save as a named style for later

RESTORING WALLPAPER COLORS:
Customization → Color Scheme → "Reset to wallpaper" re-syncs with Android's wallpaper color extraction.""",
                icon = Icons.Outlined.Palette,
                accentColor = Color(0xFFE91E63),
                readTimeMin = 5
            )
        )
    )
)
