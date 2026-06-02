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
import com.accu.ui.audio.SoundMasterScreen
import com.accu.ui.audio.MixedAudioScreen
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
import com.accu.ui.filemanager.UniversalFileViewerScreen
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
import com.accu.ui.settings.AccuPermissionsScreen
import com.accu.ui.notifications.NotificationCenterScreen
import com.accu.ui.settings.SettingsScreen
import com.accu.ui.shell.ShellScreen
import com.accu.ui.shizuku.AccuCenterScreen
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

// ========= BATCH 2 IMPORTS (32 new screens) =========
// Key Mapper
import com.accu.ui.automation.KeyMapListScreen
import com.accu.ui.automation.ConfigKeyMapScreen
import com.accu.ui.automation.ChooseActionScreen
import com.accu.ui.automation.ChooseConstraintScreen
import com.accu.ui.automation.KeyMapLogScreen
import com.accu.ui.automation.KeyMapperSettingsScreen
// Inure
import com.accu.ui.appmanager.InureHomeScreen
import com.accu.ui.appmanager.InureBatteryOptimizationScreen
import com.accu.ui.appmanager.InureBootManagerScreen
import com.accu.ui.appmanager.InureNotesScreen
import com.accu.ui.appmanager.InureMusicScreen
import com.accu.ui.appmanager.InureApksScreen
import com.accu.ui.appmanager.InureTrackersScreen
import com.accu.ui.appmanager.InureUsageStatsScreen
import com.accu.ui.appmanager.InureDisabledAppsScreen
import com.accu.ui.appmanager.BlockerComponentSearchScreen
// ColorBlendr
import com.accu.ui.customization.PerAppThemingScreen
// DarQ
import com.accu.ui.customization.DarQAppPickerScreen
// SmartSpacer
import com.accu.ui.customization.SmartSpacerComplicationsScreen
// SD Maid SE
import com.accu.ui.storage.SqueezerScreen
import com.accu.ui.storage.StorageAnalyzerScreen
// Material Files
import com.accu.ui.filemanager.FtpServerScreen
import com.accu.ui.filemanager.FilePropertiesScreen
import com.accu.ui.filemanager.TextEditorScreen
// aShellYou
import com.accu.ui.shell.CommandExamplesScreen
import com.accu.ui.shell.AdbFileBrowserScreen
import com.accu.ui.shell.AdbConnectionMode
// ACCU Connection
import com.accu.ui.shizuku.AdbPairingScreen
import com.accu.ui.shizuku.AccuAppsScreen
import com.accu.ui.connection.ConnectionGateScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.accu.ui.dashboard.NavHistoryViewModel
import com.accu.connection.AccuConnectionManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// ACCU System Service (IPC privilege hub)
import com.accu.ui.apiservice.AccuServiceScreen
import com.accu.ui.apiservice.AccuSdkDocsScreen
// ADB Toolkit screens
import com.accu.ui.shell.AdbLogcatScreen
import com.accu.ui.shell.AdbProcessScreen
import com.accu.ui.shell.AdbDeviceInfoScreen
import com.accu.ui.shell.AdbFastbootScreen
import com.accu.ui.shell.AdbScreenCaptureScreen
import com.accu.ui.shell.AdbTutorialScreen
import com.accu.ui.shell.ShellQsTileDashboardScreen
// Crash Center
import com.accu.ui.crash.CrashCenterScreen
import com.accu.ui.crash.CrashHistoryScreen
import com.accu.ui.crash.CrashDetailScreen

@Composable
fun AppNavigation(initialRoute: String? = null) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    val navHistoryViewModel: NavHistoryViewModel = hiltViewModel()
    val connectionViewModel: com.accu.ui.shizuku.ShizukuViewModel = hiltViewModel()
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()

    val isOnGate = currentDestination?.route == Screen.ConnectionGate.route
    val isOnPairing = currentDestination?.route == Screen.AdbPairing.route

    // Navigate to the requested route on first composition (e.g. when launched from a notification)
    androidx.compose.runtime.LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            navController.navigate(initialRoute) {
                launchSingleTop = true
            }
        }
    }

    // If connection drops while user is inside the app, send them back to the gate
    androidx.compose.runtime.LaunchedEffect(connectionState.connectionState) {
        val dropped = connectionState.connectionState == AccuConnectionManager.ConnectionState.DISCONNECTED
        if (dropped && !isOnGate && !isOnPairing) {
            navController.navigate(Screen.ConnectionGate.route) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let { navHistoryViewModel.record(it) }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    val haptic = LocalHapticFeedback.current

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            // Hide navigation bar on the gate and pairing screens
            if (!isOnGate && !isOnPairing) {
                TOP_LEVEL_DESTINATIONS.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.screen.route } == true
                    item(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = selected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate(dest.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.ConnectionGate.route,
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
            // ── Connection Gate — always shown on app open ─────────────────────
            composable(Screen.ConnectionGate.route) {
                ConnectionGateScreen(
                    onConnected = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.ConnectionGate.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAdbPairing = {
                        navController.navigate(Screen.AdbPairing.route)
                    },
                )
            }
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
            composable(Screen.AccuCenter.route) {
                AccuCenterScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToAdbPairing = { navController.navigate(Screen.AdbPairing.route) },
                    onNavigateToAccuApps = { navController.navigate(Screen.AccuApps.route) },
                    onNavigateToAccuServiceHub = { navController.navigate(Screen.AccuServiceHub.route) },
                )
            }
            composable(Screen.Shell.route) {
                ShellScreen(
                    onNavigateToScripts = { navController.navigate(Screen.ScriptEditor.route) },
                    onNavigateToCommandExamples = { navController.navigate(Screen.CommandExamples.route) },
                    onNavigateToFileBrowser = { mode, addr ->
                        val route = when (mode) {
                            AdbConnectionMode.WIFI  -> Screen.AdbFileBrowser.wifi(addr)
                            AdbConnectionMode.OTG   -> Screen.AdbFileBrowser.otg(addr)
                            AdbConnectionMode.LOCAL -> Screen.AdbFileBrowser.local()
                        }
                        navController.navigate(route)
                    },
                    onNavigateToLogcat       = { navController.navigate(Screen.AdbLogcat.route) },
                    onNavigateToProcesses    = { navController.navigate(Screen.AdbProcesses.route) },
                    onNavigateToDeviceInfo   = { navController.navigate(Screen.AdbDeviceInfo.route) },
                    onNavigateToFastboot     = { navController.navigate(Screen.AdbFastboot.route) },
                    onNavigateToScreenCapture = { navController.navigate(Screen.AdbScreenCapture.route) },
                    onNavigateToTutorial     = { navController.navigate(Screen.AdbTutorial.route) },
                    onNavigateToQsTileDashboard = { navController.navigate(Screen.ShellQsTileDashboard.route) },
                )
            }
            composable(Screen.AppManager.route) {
                AppManagerScreen(
                    onNavigateToDetail = { pkg -> navController.navigate(Screen.AppDetail.withPackage(pkg)) },
                    onNavigateToDebloat = { navController.navigate(Screen.Debloat.route) },
                    onNavigateToFreeze = { navController.navigate(Screen.FreezeApps.route) },
                    onNavigateToComponents = { navController.navigate(Screen.ComponentManager.route) },
                    onNavigateToPermissions = { navController.navigate(Screen.PermissionManager.route) },
                    onNavigateToAppExplorer = { navController.navigate(Screen.AppExplorer.route) },
                    onNavigateToInureHome = { navController.navigate(Screen.InureHome.route) },
                    onNavigateToBlockerSearch = { navController.navigate(Screen.BlockerComponentSearch.route) },
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
                    onNavigateToPerAppTheming = { navController.navigate(Screen.PerAppTheming.route) },
                    onNavigateToSmartSpacerComplications = { navController.navigate(Screen.SmartSpacerComplications.route) },
                    onNavigateToDarQAppPicker = { navController.navigate(Screen.DarQAppPicker.route) },
                    onNavigateToColorBlendrStyles = { navController.navigate(Screen.ColorBlendrStyles.route) },
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
                    onNavigateToStorageAnalyzer = { navController.navigate(Screen.StorageAnalyzer.route) },
                    onNavigateToSqueezer = { navController.navigate(Screen.Squeezer.route) },
                )
            }
            composable(Screen.FileManager.route) {
                FileManagerScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToFtpServer = { navController.navigate(Screen.FtpServer.route) },
                    onNavigateToFileProperties = { path -> navController.navigate(Screen.FileProperties.withPath(path)) },
                    onNavigateToTextEditor = { path -> navController.navigate(Screen.TextEditor.withPath(path)) },
                    onNavigateToFileViewer = { path -> navController.navigate(Screen.FileViewer.withPath(path)) },
                )
            }
            composable(Screen.Installer.route) {
                InstallerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Automation.route) {
                AutomationScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToKeyMapList = { navController.navigate(Screen.KeyMapList.route) },
                    onNavigateToKeyMapperSettings = { navController.navigate(Screen.KeyMapperSettings.route) },
                    onNavigateToAdvanced = { navController.navigate(Screen.KeyMapperAdvanced.route) },
                )
            }
            composable(Screen.KeyMapper.route) {
                AutomationScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToKeyMapList = { navController.navigate(Screen.KeyMapList.route) },
                    onNavigateToKeyMapperSettings = { navController.navigate(Screen.KeyMapperSettings.route) },
                    onNavigateToAdvanced = { navController.navigate(Screen.KeyMapperAdvanced.route) },
                )
            }
            composable(Screen.LanguageCenter.route) {
                LanguageCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.NetworkCenter.route) {
                NetworkCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SoundMaster.route) {
                SoundMasterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.MixedAudio.route) {
                MixedAudioScreen(onBack = { navController.popBackStack() })
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
                    onNavigateToAccuCenter = { navController.navigate(Screen.AccuCenter.route) },
                    onNavigateToCustomization = { navController.navigate(Screen.Customization.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onNavigateToNetwork = { navController.navigate(Screen.NetworkCenter.route) },
                    onNavigateToLearning = { navController.navigate(Screen.LearningCenter.route) },
                    onNavigateToCrashCenter = { navController.navigate(Screen.CrashCenter.route) },
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

            // ========= ROUTES (all 17 repos) =========

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

            // aShellYou — Shell QS Tile Dashboard
            composable(Screen.ShellQsTileDashboard.route) {
                ShellQsTileDashboardScreen(onBack = { navController.popBackStack() })
            }

            // Hail — Freeze Scheduler
            composable(Screen.FreezeScheduler.route) {
                FreezeSchedulerScreen(onBack = { navController.popBackStack() })
            }

            // ========= BATCH 2: 32 New Screens =========

            // Key Mapper — KeyMapList
            composable(Screen.KeyMapList.route) {
                KeyMapListScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToCreate = { navController.navigate(Screen.ConfigKeyMap.create()) },
                    onNavigateToEdit = { id -> navController.navigate(Screen.ConfigKeyMap.edit(id)) },
                    onNavigateToLog = { navController.navigate(Screen.KeyMapLog.route) },
                    onNavigateToSettings = { navController.navigate(Screen.KeyMapperSettings.route) },
                )
            }

            // Key Mapper — ConfigKeyMap
            composable(Screen.ConfigKeyMap.route) { back ->
                val mapId = back.arguments?.getString("mapId")?.takeIf { it != "new" }
                ConfigKeyMapScreen(
                    keyMapId = mapId,
                    onBack = { navController.popBackStack() },
                    onNavigateToChooseAction = { navController.navigate(Screen.ChooseAction.route) },
                    onNavigateToChooseConstraint = { navController.navigate(Screen.ChooseConstraint.route) },
                )
            }

            // Key Mapper — ChooseAction
            composable(Screen.ChooseAction.route) {
                ChooseActionScreen(
                    onBack = { navController.popBackStack() },
                    onActionSelected = { navController.popBackStack() },
                )
            }

            // Key Mapper — ChooseConstraint
            composable(Screen.ChooseConstraint.route) {
                ChooseConstraintScreen(
                    onBack = { navController.popBackStack() },
                    onConstraintSelected = { navController.popBackStack() },
                )
            }

            // Key Mapper — KeyMapLog
            composable(Screen.KeyMapLog.route) {
                KeyMapLogScreen(onBack = { navController.popBackStack() })
            }

            // Key Mapper — Settings
            composable(Screen.KeyMapperSettings.route) {
                KeyMapperSettingsScreen(onBack = { navController.popBackStack() })
            }

            // Inure — Home
            composable(Screen.InureHome.route) {
                InureHomeScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToBatteryOpt = { navController.navigate(Screen.InureBatteryOpt.route) },
                    onNavigateToBootManager = { navController.navigate(Screen.InureBootManager.route) },
                    onNavigateToNotes = { navController.navigate(Screen.InureNotes.route) },
                    onNavigateToMusic = { navController.navigate(Screen.InureMusic.route) },
                    onNavigateToApks = { navController.navigate(Screen.InureApks.route) },
                    onNavigateToTrackers = { navController.navigate(Screen.InureTrackers.route) },
                    onNavigateToUsageStats = { navController.navigate(Screen.InureUsageStats.route) },
                    onNavigateToDisabled = { navController.navigate(Screen.InureDisabledApps.route) },
                    onNavigateToAppDetail = { pkg -> navController.navigate(Screen.AppDetail.withPackage(pkg)) },
                )
            }

            // Inure — Battery Optimization
            composable(Screen.InureBatteryOpt.route) {
                InureBatteryOptimizationScreen(onBack = { navController.popBackStack() })
            }

            // Inure — Boot Manager
            composable(Screen.InureBootManager.route) {
                InureBootManagerScreen(onBack = { navController.popBackStack() })
            }

            // Inure — Notes
            composable(Screen.InureNotes.route) {
                InureNotesScreen(onBack = { navController.popBackStack() })
            }

            // Inure — Music
            composable(Screen.InureMusic.route) {
                InureMusicScreen(onBack = { navController.popBackStack() })
            }

            // Inure — APK Scanner
            composable(Screen.InureApks.route) {
                InureApksScreen(onBack = { navController.popBackStack() })
            }

            // Inure — Tracker Analytics
            composable(Screen.InureTrackers.route) {
                InureTrackersScreen(onBack = { navController.popBackStack() })
            }

            // Inure — Usage Stats
            composable(Screen.InureUsageStats.route) {
                InureUsageStatsScreen(onBack = { navController.popBackStack() })
            }

            // Inure — Disabled Apps
            composable(Screen.InureDisabledApps.route) {
                InureDisabledAppsScreen(onBack = { navController.popBackStack() })
            }

            // Blocker — Component Search
            composable(Screen.BlockerComponentSearch.route) {
                BlockerComponentSearchScreen(onBack = { navController.popBackStack() })
            }

            // ColorBlendr — Per-App Theming
            composable(Screen.PerAppTheming.route) {
                PerAppThemingScreen(onBack = { navController.popBackStack() })
            }

            // DarQ — App Picker
            composable(Screen.DarQAppPicker.route) {
                DarQAppPickerScreen(onBack = { navController.popBackStack() })
            }

            // SmartSpacer — Complications
            composable(Screen.SmartSpacerComplications.route) {
                SmartSpacerComplicationsScreen(onBack = { navController.popBackStack() })
            }

            // SD Maid SE — Storage Analyzer
            composable(Screen.StorageAnalyzer.route) {
                StorageAnalyzerScreen(onBack = { navController.popBackStack() })
            }

            // SD Maid SE — Squeezer
            composable(Screen.Squeezer.route) {
                SqueezerScreen(onBack = { navController.popBackStack() })
            }

            // Material Files — FTP Server
            composable(Screen.FtpServer.route) {
                FtpServerScreen(onBack = { navController.popBackStack() })
            }

            // Material Files — File Properties
            composable(Screen.FileProperties.route) { back ->
                val filePath = back.arguments?.getString("filePath")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                FilePropertiesScreen(
                    filePath = filePath,
                    onBack = { navController.popBackStack() },
                )
            }

            // Material Files — Text Editor
            composable(Screen.TextEditor.route) { back ->
                val filePath = back.arguments?.getString("filePath")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                TextEditorScreen(
                    filePath = filePath,
                    onBack = { navController.popBackStack() },
                )
            }

            // aShellYou — Command Examples
            composable(Screen.CommandExamples.route) {
                CommandExamplesScreen(
                    onBack = { navController.popBackStack() },
                    onCommandSelected = { cmd ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("selected_command", cmd)
                        navController.popBackStack()
                    },
                )
            }

            // aShellYou — ADB File Browser
            composable(Screen.AdbFileBrowser.route) { back ->
                val modeStr = back.arguments?.getString("connectionMode") ?: "local"
                val addr = back.arguments?.getString("deviceAddress")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                val connMode = when (modeStr) {
                    "wifi" -> AdbConnectionMode.WIFI
                    "otg"  -> AdbConnectionMode.OTG
                    else   -> AdbConnectionMode.LOCAL
                }
                AdbFileBrowserScreen(
                    connectionMode = connMode,
                    deviceAddress = addr,
                    onBack = { navController.popBackStack() },
                )
            }

            // ACCU — ADB Pairing
            composable(Screen.AdbPairing.route) {
                AdbPairingScreen(onBack = { navController.popBackStack() })
            }

            // ACCU — Authorized Apps
            composable(Screen.AccuApps.route) {
                AccuAppsScreen(onBack = { navController.popBackStack() })
            }

            // Advanced Permission Center
            composable(Screen.PermissionCenter.route) {
                AccuPermissionsScreen(onBack = { navController.popBackStack() })
            }

            // ── ADB Toolkit screens ───────────────────────────────────────────────
            composable(Screen.AdbLogcat.route) {
                AdbLogcatScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AdbProcesses.route) {
                AdbProcessScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AdbDeviceInfo.route) {
                AdbDeviceInfoScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AdbFastboot.route) {
                AdbFastbootScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AdbScreenCapture.route) {
                AdbScreenCaptureScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AdbTutorial.route) {
                AdbTutorialScreen(onBack = { navController.popBackStack() })
            }

            // ── ACCU System Service Hub ──────────────────────────────────────────
            composable(Screen.AccuServiceHub.route) {
                AccuServiceScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToDocs = { navController.navigate(Screen.AccuSdkDocs.route) },
                )
            }

            // ACCU SDK Documentation (full developer guide)
            composable(Screen.AccuSdkDocs.route) {
                AccuSdkDocsScreen(onBack = { navController.popBackStack() })
            }

            // Notification Center
            composable(Screen.NotificationCenter.route) {
                NotificationCenterScreen(onBack = { navController.popBackStack() })
            }

            // ── Crash Center ─────────────────────────────────────────────────────
            composable(Screen.CrashCenter.route) {
                CrashCenterScreen(
                    onNavigateToHistory = { navController.navigate(Screen.CrashHistory.route) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.CrashHistory.route) {
                CrashHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onCrashDetail = { id -> navController.navigate(Screen.CrashDetail.withId(id)) },
                )
            }
            composable(Screen.CrashDetail.route) { back ->
                val crashId = back.arguments?.getString("crashId") ?: return@composable
                CrashDetailScreen(
                    crashId = crashId,
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Universal File Viewer ─────────────────────────────────────────
            composable(Screen.FileViewer.route) { back ->
                val filePath = back.arguments?.getString("filePath")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                UniversalFileViewerScreen(
                    filePath = filePath,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
