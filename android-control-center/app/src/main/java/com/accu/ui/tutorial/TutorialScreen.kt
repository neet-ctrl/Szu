package com.accu.ui.tutorial

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class TutorialStep(
    val title: String,
    val subtitle: String,
    val body: String,
    val tips: List<String> = emptyList(),
    val icon: ImageVector,
    val accentColor: Color,
    val navTarget: String? = null
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(onNavigateTo: (String) -> Unit = {}, onFinish: () -> Unit = {}) {
    val steps = remember { buildTutorialSteps() }
    val pagerState = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Guide", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onFinish) { Text("Skip") }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                            Icon(Icons.Outlined.ArrowBack, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.width(80.dp))
                    }

                    // Page indicators
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        steps.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == i) 10.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    // Next / Finish button
                    Button(onClick = {
                        if (pagerState.currentPage < steps.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onFinish()
                        }
                    }) {
                        Text(if (pagerState.currentPage < steps.size - 1) "Next" else "Finish")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (pagerState.currentPage < steps.size - 1) Icons.Outlined.ArrowForward else Icons.Outlined.Check,
                            null,
                            Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            TutorialPage(
                step = steps[page],
                pageNumber = page + 1,
                totalPages = steps.size,
                onNavigateTo = onNavigateTo
            )
        }
    }
}

@Composable
private fun TutorialPage(step: TutorialStep, pageNumber: Int, totalPages: Int, onNavigateTo: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            // Step counter
            Text(
                "STEP $pageNumber OF $totalPages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            // Icon + title card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = step.accentColor.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                            .background(step.accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(step.icon, null, modifier = Modifier.size(40.dp), tint = step.accentColor)
                    }
                    Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(step.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        item {
            // Body text
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(step.body, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (step.tips.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Lightbulb, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Pro Tips", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        step.tips.forEach { tip ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
            }
        }
        if (step.navTarget != null) {
            item {
                FilledTonalButton(
                    onClick = { onNavigateTo(step.navTarget) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open ${step.title}")
                }
            }
        }
    }
}

fun buildTutorialSteps(): List<TutorialStep> = listOf(
    TutorialStep(
        title = "Welcome to ACC Ultimate",
        subtitle = "17 powerful Android tools in one app",
        body = """Android Control Center Ultimate combines the best features from 17 open-source apps into a single, cohesive experience.

From system-level shell access to audio DSP, call recording, app management, and beyond — everything is here.

This guide will walk you through setup and the key features so you get the most out of ACC.""",
        tips = listOf(
            "Tap the ⓘ icon anywhere in the app for instant help on that feature",
            "The All Features screen lists every feature from all 17 source apps with usage instructions",
            "Most features work without root — you just need Shizuku"
        ),
        icon = Icons.Outlined.Android,
        accentColor = Color(0xFF6750A4)
    ),
    TutorialStep(
        title = "Setting Up Shizuku",
        subtitle = "The key to most ACC features — no root needed",
        body = """Shizuku is a service that gives apps elevated system access through your own ADB authorization.

HOW TO START SHIZUKU:

Option 1 — Wireless Debugging (Android 11+):
1. Enable Developer Options (Settings → About Phone → tap Build Number 7 times)
2. Go to Settings → Developer Options → Wireless Debugging → Enable
3. Tap "Pair device with pairing code"
4. Open the Shizuku app → "Pairing" → enter the code shown

Option 2 — ADB via PC:
1. Enable USB Debugging in Developer Options
2. Run on PC: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh

Once Shizuku is running, open ACC → Shizuku Center → Grant Permission.""",
        tips = listOf(
            "Shizuku persists until reboot. Enable 'Auto-start' in Shizuku app settings",
            "On Android 11+, Wireless Debugging mode requires re-pairing after reboot but NOT re-authorization",
            "Shizuku is completely safe — you explicitly grant permission to each app that requests it"
        ),
        icon = Icons.Outlined.Security,
        accentColor = Color(0xFF0288D1),
        navTarget = "shizuku"
    ),
    TutorialStep(
        title = "Shell — Your ADB Powerhouse",
        subtitle = "Execute any ADB command directly from your phone",
        body = """The Shell tab gives you full ADB shell access in three modes:

LOCAL MODE (Shizuku required):
• Run ADB commands on your own device without a PC
• Fastest mode — no network latency

WI-FI MODE:
• Connect to any Android device on your network
• Pair via QR code or 6-digit code
• Save devices for quick reconnection

OTG MODE:
• Connect via USB OTG cable for direct ADB access

POWER FEATURES:
• 200+ pre-loaded command examples organized by category
• Command history (last 200 commands)
• Bookmarks for frequently-used commands
• AI-powered command analysis with danger detection
• Tab autocomplete and smart suggestions
• Search through output, save to file""",
        tips = listOf(
            "Long-press any output line to copy, bookmark, or run AI analysis on it",
            "Use the ↑/↓ buttons to navigate command history without a keyboard",
            "The AI button detects dangerous commands like 'rm -rf' before you run them"
        ),
        icon = Icons.Outlined.Terminal,
        accentColor = Color(0xFF00897B),
        navTarget = "shell"
    ),
    TutorialStep(
        title = "App Manager",
        subtitle = "Complete control over every installed app",
        body = """The App Manager tab gives you deep control over all apps:

DEBLOAT (Canta):
Remove system, carrier, and OEM bloatware safely using community-curated lists with safety ratings. Preset packs available for major manufacturers.

FREEZE (Hail):
Suspend apps without uninstalling. Frozen apps can't run in background, preserving battery and privacy. Auto-freeze on screen-off available.

COMPONENTS (Blocker):
Block individual activities, services, receivers, and providers within any app to stop unwanted background behavior.

PERMISSIONS:
Grant or revoke any runtime permission — even protected ones — using Shizuku.

APP DETAIL (Inure):
Deep inspection of any app: APK manifest, signing certificate, native libraries, storage breakdown.""",
        tips = listOf(
            "Use Debloat presets for your device brand to remove common bloatware safely",
            "Freeze apps you rarely use to significantly extend battery life",
            "Component blocking is more surgical than disabling — the app still works but specific behaviors stop"
        ),
        icon = Icons.Outlined.Apps,
        accentColor = Color(0xFF7E57C2),
        navTarget = "app_manager"
    ),
    TutorialStep(
        title = "Audio Center",
        subtitle = "System-wide DSP effects without root",
        body = """The Audio Center brings RootlessJamesDSP to your fingertips:

EQUALIZER:
• Parametric EQ with 5 bands — set frequency, gain, Q factor
• Graphic EQ with 31 bands for a visual approach
• One-tap presets: Flat, Rock, Pop, Classical, Vocal, Bass

EFFECTS:
• Bass Boost: 0–1000 strength
• Virtualizer/Stereo Widener
• Reverb with room presets (Small Room, Large Hall, etc.)
• Loudness Enhancer for low volumes

ADVANCED:
• Convolver: load .wav impulse response files for headphone profiles
• AutoEQ: search and download tuning profiles for your headphones
• Liveprog: run custom Faust DSP scripts
• Per-app blocklist: exclude specific apps from DSP

The DSP runs as a foreground service — keep it enabled for all audio apps.""",
        tips = listOf(
            "Start with the AutoEQ search to find a scientifically tuned profile for your headphones",
            "Use the blocklist to exclude phone calls and voice assistants from DSP",
            "Convolver impulse files (.wav) can simulate famous speakers and room acoustics"
        ),
        icon = Icons.Outlined.GraphicEq,
        accentColor = Color(0xFF3F51B5),
        navTarget = "audio"
    ),
    TutorialStep(
        title = "Quick Settings Tiles",
        subtitle = "Toggle network settings with one tap — actually",
        body = """Better Internet Tiles gives you QS tiles that ACTUALLY toggle:

AVAILABLE TILES:
• Wi-Fi — direct toggle (not just opens settings)
• Mobile Data — enable/disable with one tap
• Hotspot — toggle hotspot on/off
• Bluetooth — fast Bluetooth toggle
• NFC — one-tap NFC toggle
• Airplane Mode — direct airplane mode toggle

HOW TO ADD TILES:
1. Pull down your notification shade
2. Tap the pencil/edit icon to edit Quick Settings
3. Scroll down to find "ACC Wi-Fi", "ACC Mobile Data", etc.
4. Drag them to your active tiles area

All tiles require Shizuku to bypass Android's restriction on direct radio control.""",
        tips = listOf(
            "Long-press any ACC tile to open ACC directly to that feature's settings",
            "Enable 'Require Unlock' in tile settings to prevent accidental toggles",
            "The Wi-Fi tile shows your connected SSID — enable in Network Center settings"
        ),
        icon = Icons.Outlined.SettingsEthernet,
        accentColor = Color(0xFF2196F3),
        navTarget = "network"
    ),
    TutorialStep(
        title = "Call Recorder",
        subtitle = "Rootless call recording using scrcpy",
        body = """The Call Recorder uses ShizuCallRecorder's scrcpy-based approach for rootless recording:

HOW IT WORKS:
ACC bundles the scrcpy server binary, which captures audio output during calls. This works without root by using the Android audio projection API via Shizuku.

SETUP:
1. Ensure Shizuku is running
2. Go to Call Recorder → grant RECORD_AUDIO and phone state permissions
3. Enable Auto-record or use manual recording

SETTINGS:
• Format: AAC (smallest), OPUS (best quality/size ratio), FLAC (lossless)
• Save location: pick any folder via SAF file picker
• Filename format: customize with date, time, contact name tokens
• Contact filter: record only specific contacts or exclude some

RECORDINGS:
Browse, play, and delete recordings from the list. Each recording shows direction (incoming/outgoing), contact name, duration, and timestamp.""",
        tips = listOf(
            "OPUS format offers the best balance of quality and file size for long calls",
            "Set a dedicated folder on external storage so recordings survive app reinstalls",
            "Call recording legality varies by country — always check your local laws"
        ),
        icon = Icons.Outlined.PhoneCallback,
        accentColor = Color(0xFF4CAF50),
        navTarget = "call_recorder"
    ),
    TutorialStep(
        title = "Language & Theming",
        subtitle = "Make your device truly yours",
        body = """LANGUAGE SELECTOR:
Set different languages per app — your browser in English, a game in Japanese, messaging in your native language. Uses Shizuku to set locales that persist across reboots.

COLOR BLENDR:
Take full control of your Material You palette:
• Pick any seed color (not just wallpaper colors)
• Choose from 6 Monet styles: Tonal Spot, Vibrant, Expressive, Spritz, Rainbow, Fruit Salad
• Override individual color roles (primary, surface, error, etc.)
• Save and restore named color styles

DARQ (Dark Mode):
Force dark mode on any app that doesn't support it natively. Schedule by time of day or automatically at sunrise/sunset using your location.""",
        tips = listOf(
            "ColorBlendr's 'Spritz' style gives a neutral/grayscale palette — great for focus",
            "Per-app language requires Android 13+ for the best experience",
            "DarQ's sunrise/sunset mode only needs approximate location — no precise tracking"
        ),
        icon = Icons.Outlined.Palette,
        accentColor = Color(0xFFE91E63),
        navTarget = "customization"
    ),
    TutorialStep(
        title = "You're All Set! 🎉",
        subtitle = "Explore all 17 apps' worth of features",
        body = """ACC Ultimate is fully set up. Here's a quick reference:

DASHBOARD: System overview — Shizuku status, battery, network, recent actions
APPS tab: App Manager, Debloat, Freeze, Components, Permissions, Installer
TOOLS tab: Shell, File Manager, Storage, Key Mapper, Automation
PRIVACY tab: Privacy dashboard, sensor controls, App Ops
SETTINGS tab: All app settings, All Features reference, this guide

GETTING HELP:
• Tap ⓘ on any feature for instant inline help
• Visit All Features (Settings → All Features) for a complete reference of every feature
• The Learning Center (Settings → Learning Center) has deep-dives per feature area

Remember: Most features need Shizuku. When something doesn't work, check the Shizuku status card on the Dashboard first.""",
        tips = listOf(
            "Star your most-used features on the Dashboard for one-tap access",
            "The Shell's command examples library is searchable — great for learning ADB",
            "Check 'All Features' screen regularly — it's the complete manual for the app"
        ),
        icon = Icons.Outlined.CheckCircle,
        accentColor = Color(0xFF4CAF50)
    )
)
