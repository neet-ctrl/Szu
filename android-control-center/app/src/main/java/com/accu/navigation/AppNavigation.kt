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
import com.accu.ui.shizuku.FreezeSchedulerScreen
import com.accu.ui.shell.ScriptEditorScreen
import com.accu.ui.storage.LargeFileFinderScreen
import com.accu.ui.appmanager.CantaPresetsScreen
import com.accu.ui.appmanager.VirusTotalScreen
import com.accu.ui.appmanager.AppExplorerScreen
import com.accu.ui.appmanager.CantaLogsScreen
import com.accu.ui.appmanager.InureAnalyticsScreen
import com.accu.ui.appmanager.AppBatchOperationsScreen
import com.accu.ui.audio.AudioCenterScreen
import com.accu.ui.audio.LiveprogEditorScreen
import com.accu.ui.audio.ParametricEQScreen
import com.accu.ui.audio.AutoEQScreen
import com.accu.ui.audio.AppAudioBlocklistScreen
import com.accu.ui.automation.AutomationScreen
import com.accu.ui.automation.KeyMapperAdvancedScreen
import com.accu.ui.callrecorder.CallRecorderScreen
import com.accu.ui.callrecorder.ScrcpyIntegrationScreen
import com.accu.ui.callrecorder.CallRecordingSettingsScreen
import com.accu.ui.customization.ColorEditorScreen
import com.accu.ui.customization.CustomizationScreen
import com.accu.ui.customization.DarkModeScreen
import com.accu.ui.customization.DarQFaqScreen
import com.accu.ui.customization.DarQSunriseSunsetScreen
import com.accu.ui.customization.ColorBlendrStylesScreen
import com.accu.ui.dashboard.DashboardScreen
import com.accu.ui.filemanager.FileManagerScreen
import com.accu.ui.filemanager.FileManagerAdvancedFeaturesScreen
import com.accu.ui.installer.InstallerScreen
import com.accu.ui.installer.InstallFlagsScreen
import com.accu.ui.language.LanguageCenterScreen
import com.accu.ui.language.LanguageDetailScreen
import com.accu.ui.network.NetworkCenterScreen
import com.accu.ui.network.BetterInternetTilesSettingsScreen
import com.accu.ui.features.AllFeaturesScreen
import com.accu.ui.onboarding.OnboardingScreen
import com.accu.ui.privacy.PrivacyScreen
import com.accu.ui.privacy.OnlineRulesScreen
import com.accu.ui.settings.SettingsScreen
import com.accu.ui.shell.ShellScreen
import com.accu.ui.shizuku.ShizukuCenterScreen
import com.accu.ui.shizuku.HailWorkProfileScreen
import com.accu.ui.storage.StorageScreen
import com.accu.ui.storage.AppCleanerScreen
import com.accu.ui.storage.SystemCleanerScreen
import com.accu.ui.storage.DeduplicatorScreen
import com.accu.ui.storage.CorpseFinderScreen
import com.accu.ui.tutorial.LearningCenterScreen
import com.accu.ui.tutorial.TutorialScreen
import com.accu.ui.widgets.SmartSpacerScreen
import com.accu.ui.widgets.SmartSpacerTargetsScreen

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
                ShellScreen(onNavigateToScripts = { navController.navigate(Screen.ScriptEditor.route) })
            }
            composable(Screen.AppManager.route) {
                AppManagerScreen(
                    onNavigateToDetail = { pkg -> navController.navigate(Screen.AppDetail.withPackage(pkg)) },
                    onNavigateToDebloat = { navController.navigate(Screen.Debloat.route) },
                    onNavigateToFreeze = { navController.navigate(Screen.FreezeApps.route) },
                    onNavigateToComponents = { navController.navigate(Screen.ComponentManager.route) },
                    onNavigateToPermissions = { navController.navigate(Screen.PermissionManager.route) },
                    onNavigateToAppExplorer = { navController.navigate(Screen.AppExplorer.route) },
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
                FreezeAppsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToScheduler = { navController.navigate(Screen.FreezeScheduler.route) },
                )
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
                    onNavigateToAppCleaner = { navController.navigate(Screen.AppCleaner.route) },
                    onNavigateToSystemCleaner = { navController.navigate(Screen.SystemCleaner.route) },
                    onNavigateToDeduplicator = { navController.navigate(Screen.Deduplicator.route) },
                    onNavigateToCorpseFinder = { navController.navigate(Screen.CorpseFinder.route) },
                    onNavigateToFileManagerAdvanced = { navController.navigate(Screen.FileManagerAdvanced.route) },
                    onNavigateToLargeFileFinder = { navController.navigate(Screen.LargeFileFinder.route) },
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
                AllFeaturesScreen(onBack = { navController.popBackStack() })
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

            // App Explorer
            composable(Screen.AppExplorer.route) {
                AppExplorerScreen(onBack = { navController.popBackStack() })
            }

            // SmartSpacer (alias route)
            composable(Screen.SmartSpacer.route) {
                SmartSpacerScreen(onBack = { navController.popBackStack() })
            }

            // VirusScan
            composable(Screen.VirusScan.route) {
                VirusTotalScreen(onBack = { navController.popBackStack() })
            }

            // ========= NEW ROUTES (all 17 repos) =========

            // Canta
            composable(Screen.CantaPresets.route) {
                CantaPresetsScreen(
                    onBack = { navController.popBackStack() },
                    onApplyPreset = { _ -> navController.popBackStack() }
                )
            }
            composable(Screen.CantaLogs.route) {
                CantaLogsScreen(onBack = { navController.popBackStack() })
            }

            // Hail
            composable(Screen.HailWorkProfile.route) {
                HailWorkProfileScreen(onBack = { navController.popBackStack() })
            }

            // DarQ
            composable(Screen.DarQFaq.route) {
                DarQFaqScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.DarQSunriseSunset.route) {
                DarQSunriseSunsetScreen(onBack = { navController.popBackStack() })
            }

            // ColorBlendr
            composable(Screen.ColorBlendrStyles.route) {
                ColorBlendrStylesScreen(onBack = { navController.popBackStack() })
            }

            // RootlessJamesDSP
            composable(Screen.LiveprogEditor.route) {
                LiveprogEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ParametricEQ.route) {
                ParametricEQScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AutoEQ.route) {
                AutoEQScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AppAudioBlocklist.route) {
                AppAudioBlocklistScreen(onBack = { navController.popBackStack() })
            }

            // SDMaid SE
            composable(Screen.AppCleaner.route) {
                AppCleanerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SystemCleaner.route) {
                SystemCleanerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Deduplicator.route) {
                DeduplicatorScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CorpseFinder.route) {
                CorpseFinderScreen(onBack = { navController.popBackStack() })
            }

            // InstallWithOptions
            composable(Screen.InstallFlags.route) {
                InstallFlagsScreen(onBack = { navController.popBackStack() })
            }

            // Blocker
            composable(Screen.OnlineRules.route) {
                OnlineRulesScreen(onBack = { navController.popBackStack() })
            }

            // Inure
            composable(Screen.InureAnalytics.route) {
                InureAnalyticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AppBatchOps.route) { back ->
                val pkg = back.arguments?.getString("packageName") ?: ""
                AppBatchOperationsScreen(
                    selectedPackages = listOf(pkg),
                    onBack = { navController.popBackStack() }
                )
            }

            // Key Mapper
            composable(Screen.KeyMapperAdvanced.route) {
                KeyMapperAdvancedScreen(onBack = { navController.popBackStack() })
            }

            // MaterialFiles
            composable(Screen.FileManagerAdvanced.route) {
                FileManagerAdvancedFeaturesScreen(onBack = { navController.popBackStack() })
            }

            // SmartSpacer
            composable(Screen.SmartSpacerTargets.route) {
                SmartSpacerTargetsScreen(onBack = { navController.popBackStack() })
            }

            // ShizuCallRecorder
            composable(Screen.ScrcpyIntegration.route) {
                ScrcpyIntegrationScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CallRecordingSettings.route) {
                CallRecordingSettingsScreen(onBack = { navController.popBackStack() })
            }

            // BetterInternetTiles
            composable(Screen.TilesSettings.route) {
                BetterInternetTilesSettingsScreen(onBack = { navController.popBackStack() })
            }

            // LanguageSelector
            composable(Screen.LanguageDetail.route) { back ->
                val pkg = back.arguments?.getString("packageName") ?: ""
                val appName = back.arguments?.getString("appName") ?: pkg
                LanguageDetailScreen(
                    packageName = pkg,
                    appName = appName,
                    currentLocale = "system",
                    onBack = { navController.popBackStack() },
                    onLocaleSet = { navController.popBackStack() }
                )
            }

            // SD Maid SE — Large File Finder
            composable(Screen.LargeFileFinder.route) {
                LargeFileFinderScreen(onBack = { navController.popBackStack() })
            }

            // aShellYou — Script Editor / Manager
            composable(Screen.ScriptEditor.route) {
                ScriptEditorScreen(onBack = { navController.popBackStack() })
            }

            // Hail — Freeze Scheduler
            composable(Screen.FreezeScheduler.route) {
                FreezeSchedulerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
