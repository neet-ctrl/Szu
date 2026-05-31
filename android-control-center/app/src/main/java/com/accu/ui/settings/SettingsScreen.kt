package com.accu.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.accu.BuildConfig
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.FeatureRow
import com.accu.ui.components.FeatureSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onNavigateToShizuku: () -> Unit,
    onNavigateToCustomization: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToLearning: () -> Unit,
    onNavigateToAllFeatures: () -> Unit = { navController.navigate("all_features") },
    onNavigateToTutorial: () -> Unit = { navController.navigate("tutorial") },
) {
    var dynamicColor by remember { mutableStateOf(true) }
    var developerMode by remember { mutableStateOf(false) }
    var analyticsOptOut by remember { mutableStateOf(true) }
    var bootAutoStart by remember { mutableStateOf(false) }
    var showRootWarnings by remember { mutableStateOf(true) }
    var confirmDestructiveActions by remember { mutableStateOf(true) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Service Access
            item { SettingsSectionHeader("Service Access") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    FeatureRow("Shizuku Center", "Manage elevated access", leadingIcon = { Icon(Icons.Default.Hub, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, onClick = onNavigateToShizuku)
                    HorizontalDivider()
                    FeatureRow("Network Center", "Wi-Fi, mobile data, tiles", leadingIcon = { Icon(Icons.Default.Wifi, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, onClick = onNavigateToNetwork)
                    HorizontalDivider()
                    FeatureRow("Privacy Center", "Trackers & components", leadingIcon = { Icon(Icons.Default.Security, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, onClick = onNavigateToPrivacy)
                }
            }

            // Appearance
            item { SettingsSectionHeader("Appearance") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    FeatureRow("Customization", "Themes, colors & dark mode", leadingIcon = { Icon(Icons.Default.Palette, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, onClick = onNavigateToCustomization)
                    HorizontalDivider()
                    FeatureSwitch("Dynamic Color", "Use Material You wallpaper colors", checked = dynamicColor, onCheckedChange = { dynamicColor = it }, leadingIcon = { Icon(Icons.Default.ColorLens, null) })
                }
            }

            // Behavior
            item { SettingsSectionHeader("Behavior") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    FeatureSwitch("Start on Boot", "Auto-start ACC services on device boot", checked = bootAutoStart, onCheckedChange = { bootAutoStart = it }, leadingIcon = { Icon(Icons.Default.PowerSettingsNew, null) })
                    HorizontalDivider()
                    FeatureSwitch("Confirm Destructive Actions", "Ask before uninstall, wipe, etc.", checked = confirmDestructiveActions, onCheckedChange = { confirmDestructiveActions = it }, leadingIcon = { Icon(Icons.Default.Warning, null) })
                    HorizontalDivider()
                    FeatureSwitch("Show Root Warnings", "Warn before executing root commands", checked = showRootWarnings, onCheckedChange = { showRootWarnings = it }, leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null) })
                }
            }

            // Privacy
            item { SettingsSectionHeader("Privacy") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    FeatureSwitch("Analytics Opt-Out", "Do not send anonymous usage data", checked = analyticsOptOut, onCheckedChange = { analyticsOptOut = it }, leadingIcon = { Icon(Icons.Default.TrackChanges, null) })
                }
            }

            // Developer
            item { SettingsSectionHeader("Developer") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    FeatureSwitch("Developer Mode", "Show advanced diagnostics and debug options", checked = developerMode, onCheckedChange = { developerMode = it }, leadingIcon = { Icon(Icons.Default.Code, null) })
                    HorizontalDivider()
                    FeatureRow("ADB Shell", "Open integrated terminal", leadingIcon = { Icon(Icons.Default.Terminal, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, onClick = { navController.navigate("shell") })
                    HorizontalDivider()
                    FeatureRow("All Features", "Complete guide to all 17 apps' features", leadingIcon = { Icon(Icons.Default.List, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, onClick = onNavigateToAllFeatures)
                    HorizontalDivider()
                    FeatureRow("Setup Tutorial", "9-step guide to get started with ACC", leadingIcon = { Icon(Icons.Default.PlayCircle, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, onClick = onNavigateToTutorial)
                    HorizontalDivider()
                    FeatureRow("Learning Center", "In-depth guides & feature deep-dives", leadingIcon = { Icon(Icons.Default.School, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, onClick = onNavigateToLearning)
                }
            }

            // About
            item { SettingsSectionHeader("About") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ListItem(headlineContent = { Text("Android Control Center Ultimate") }, supportingContent = { Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }, leadingContent = { Icon(Icons.Default.Info, null) })
                    HorizontalDivider()
                    ListItem(headlineContent = { Text("Open Source") }, supportingContent = { Text("Combines 17 open-source Android tools") }, leadingContent = { Icon(Icons.Default.Code, null) })
                    HorizontalDivider()
                    ListItem(headlineContent = { Text("Android ${Build.VERSION.RELEASE}") }, supportingContent = { Text("API ${Build.VERSION.SDK_INT} · ${Build.MODEL}") }, leadingContent = { Icon(Icons.Default.Android, null) })
                    HorizontalDivider()
                    ListItem(headlineContent = { Text("Licenses") }, supportingContent = { Text("Shizuku, LibSU, JamesDSP, Coil, Room, Hilt…") }, leadingContent = { Icon(Icons.Default.Article, null) })
                }
            }

            // Credits
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Built on the work of", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val credits = listOf(
                            "Shizuku" to "rikka.app",
                            "aShellYou" to "iamr0s",
                            "Canta" to "samolego",
                            "Hail" to "aistra",
                            "Inure" to "Hamza417",
                            "Blocker" to "lihenggui",
                            "ColorBlendr" to "Mahmud0808",
                            "DarQ" to "KieronQuinn",
                            "SmartSpacer" to "KieronQuinn",
                            "SD Maid SE" to "d4rken-org",
                            "Material Files" to "zhanghai",
                            "InstallWithOptions" to "zacharee1",
                            "Key Mapper" to "sds100",
                            "Language Selector" to "MuntashirAkon",
                            "Better Internet Tiles" to "CasperVerswijvelt",
                            "RootlessJamesDSP" to "thepbone",
                            "ShizuCallRecorder" to "kitsumed",
                        )
                        credits.forEach { (project, author) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Text("• $project", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
