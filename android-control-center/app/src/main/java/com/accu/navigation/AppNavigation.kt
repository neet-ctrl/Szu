package com.accu.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.accu.ui.appmanager.AppDetailScreen
import com.accu.ui.appmanager.AppManagerScreen
import com.accu.ui.appmanager.ComponentManagerScreen
import com.accu.ui.appmanager.PermissionManagerScreen
import com.accu.ui.appmanager.DebloatScreen
import com.accu.ui.appmanager.FreezeAppsScreen
import com.accu.ui.audio.AudioCenterScreen
import com.accu.ui.automation.AutomationScreen
import com.accu.ui.callrecorder.CallRecorderScreen
import com.accu.ui.customization.ColorEditorScreen
import com.accu.ui.customization.CustomizationScreen
import com.accu.ui.customization.DarkModeScreen
import com.accu.ui.dashboard.DashboardScreen
import com.accu.ui.filemanager.FileManagerScreen
import com.accu.ui.installer.InstallerScreen
import com.accu.ui.language.LanguageCenterScreen
import com.accu.ui.network.NetworkCenterScreen
import com.accu.ui.features.AllFeaturesScreen
import com.accu.ui.onboarding.OnboardingScreen
import com.accu.ui.privacy.PrivacyScreen
import com.accu.ui.settings.SettingsScreen
import com.accu.ui.shell.ShellScreen
import com.accu.ui.shizuku.ShizukuCenterScreen
import com.accu.ui.storage.StorageScreen
import com.accu.ui.tutorial.LearningCenterScreen
import com.accu.ui.tutorial.TutorialScreen
import com.accu.ui.widgets.SmartSpacerScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination

    val isTopLevel = TOP_LEVEL_DESTINATIONS.any {
        currentDestination?.hierarchy?.any { d -> d.route == it.screen.route } == true
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TOP_LEVEL_DESTINATIONS.forEach { dest ->
                val selected = currentDestination?.hierarchy?.any { it.route == dest.screen.route } == true
                item(
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                    selected = selected,
                    onClick = {
                        navController.navigate(dest.screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            enterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { it / 6 } +
                    fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(300)) { -it / 6 } +
                    fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { -it / 6 } +
                    fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(300)) { it / 6 } +
                    fadeOut(animationSpec = tween(300))
            },
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onFinish = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(navController = navController)
            }
            composable(Screen.ShizukuCenter.route) {
                ShizukuCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Shell.route) {
                ShellScreen()
            }
            composable(Screen.AppManager.route) {
                AppManagerScreen(
                    onNavigateToDetail = { pkg -> navController.navigate(Screen.AppDetail.withPackage(pkg)) },
                    onNavigateToDebloat = { navController.navigate(Screen.Debloat.route) },
                    onNavigateToFreeze = { navController.navigate(Screen.FreezeApps.route) },
                    onNavigateToComponents = { navController.navigate(Screen.ComponentManager.route) },
                    onNavigateToPermissions = { navController.navigate(Screen.PermissionManager.route) },
                )
            }
            composable(Screen.AppDetail.route) { back ->
                val pkg = back.arguments?.getString("packageName") ?: return@composable
                AppDetailScreen(packageName = pkg, onBack = { navController.popBackStack() })
            }
            composable(Screen.Debloat.route) {
                DebloatScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.FreezeApps.route) {
                FreezeAppsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ComponentManager.route) {
                ComponentManagerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.PermissionManager.route) {
                PermissionManagerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Privacy.route) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Customization.route) {
                CustomizationScreen(
                    onNavigateToDarkMode = { navController.navigate(Screen.DarkMode.route) },
                    onNavigateToColorEditor = { navController.navigate(Screen.ColorEditor.route) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.DarkMode.route) {
                DarkModeScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ColorEditor.route) {
                ColorEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Widgets.route) {
                SmartSpacerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Storage.route) {
                StorageScreen(
                    onNavigateToFileManager = { navController.navigate(Screen.FileManager.route) },
                )
            }
            composable(Screen.FileManager.route) {
                FileManagerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Installer.route) {
                InstallerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Automation.route) {
                AutomationScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.KeyMapper.route) {
                AutomationScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.LanguageCenter.route) {
                LanguageCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.NetworkCenter.route) {
                NetworkCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AudioCenter.route) {
                AudioCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CallRecorder.route) {
                CallRecorderScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.LearningCenter.route) {
                LearningCenterScreen(onNavigateTo = { route -> navController.navigate(route) })
            }
            composable(Screen.AllFeatures.route) {
                AllFeaturesScreen(onNavigateTo = { route -> navController.navigate(route) })
            }
            composable(Screen.Tutorial.route) {
                TutorialScreen(
                    onNavigateTo = { route -> navController.navigate(route) },
                    onFinish = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    navController = navController,
                    onNavigateToShizuku = { navController.navigate(Screen.ShizukuCenter.route) },
                    onNavigateToCustomization = { navController.navigate(Screen.Customization.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onNavigateToNetwork = { navController.navigate(Screen.NetworkCenter.route) },
                    onNavigateToLearning = { navController.navigate(Screen.LearningCenter.route) },
                )
            }
        }
    }
}
