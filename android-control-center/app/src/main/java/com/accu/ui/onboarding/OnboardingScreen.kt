package com.accu.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.accu.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
)

val onboardingPages = listOf(
    OnboardingPage(
        "Android Control Center", "Ultimate",
        "The most comprehensive Android management suite. Combines features from 17 powerful open-source apps into one unified experience.",
        Icons.Default.Hub, listOf(Color(0xFF4A56E2), Color(0xFF7C4DFF)),
    ),
    OnboardingPage(
        "Shizuku Powered", "Rootless & Root",
        "Leverages Shizuku for elevated ADB access without rooting your device. Full root support too. Control your Android like never before.",
        Icons.Default.AdminPanelSettings, listOf(Color(0xFF00D4FF), Color(0xFF0097A7)),
    ),
    OnboardingPage(
        "App Manager", "Complete Control",
        "Freeze, hide, debloat, uninstall apps. Manage permissions, components, and app data. Everything Hail, Inure, Canta, and Blocker do — unified.",
        Icons.Default.Apps, listOf(Color(0xFFFF6D00), Color(0xFFE91E63)),
    ),
    OnboardingPage(
        "Audio DSP Engine", "JamesDSP Inside",
        "Full RootlessJamesDSP audio processing with 10-band EQ, bass boost, reverb, surround sound, AutoEQ, Convolver, and Liveprog scripting.",
        Icons.Default.Equalizer, listOf(Color(0xFFE91E63), Color(0xFF9C27B0)),
    ),
    OnboardingPage(
        "Network & Privacy", "Stay in Control",
        "Toggle Wi-Fi, mobile data, Bluetooth, and hotspot without system settings. Block trackers, manage permissions, and protect your privacy.",
        Icons.Default.Security, listOf(Color(0xFF00E676), Color(0xFF00BCD4)),
    ),
    OnboardingPage(
        "Shell Terminal", "Developer Power",
        "Full ADB shell terminal with command history, saved scripts, syntax hints, and multi-mode execution via Shizuku, root, or standard ADB.",
        Icons.Default.Terminal, listOf(Color(0xFF1A1A2E), Color(0xFF16213E)),
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val p = onboardingPages[page]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(p.gradientColors + listOf(MaterialTheme.colorScheme.background)))
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(120.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(p.icon, null, modifier = Modifier.size(64.dp), tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                    Text(p.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
                    Text(p.subtitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White.copy(0.8f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Text(p.description, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(0.85f), textAlign = TextAlign.Center, lineHeight = 26.sp)
                }
            }
        }

        // Bottom controls
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Page indicators
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(onboardingPages.size) { index ->
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (pagerState.currentPage == index) Color.White else Color.White.copy(0.4f))
                            .size(if (pagerState.currentPage == index) 10.dp else 7.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pagerState.currentPage < onboardingPages.size - 1) {
                    TextButton(onClick = onFinish, modifier = Modifier.weight(1f)) {
                        Text("Skip", color = Color.White.copy(0.7f))
                    }
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.2f), contentColor = Color.White),
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                    }
                } else {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
