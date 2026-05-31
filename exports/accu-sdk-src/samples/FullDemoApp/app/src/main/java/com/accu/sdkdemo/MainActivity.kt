package com.accu.sdkdemo

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.platform.LocalContext
import com.accu.sdkdemo.navigation.Screen
import com.accu.sdkdemo.navigation.drawerSections
import com.accu.sdkdemo.ui.screens.*
import com.accu.sdkdemo.ui.theme.AccuSdkTheme
import com.accu.sdkdemo.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AccuSdkTheme { AccuTestApp(vm) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuTestApp(vm: MainViewModel) {
    val navController = rememberNavController()
    val drawerState   = rememberDrawerState(DrawerValue.Closed)
    val scope         = rememberCoroutineScope()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route ?: Screen.Dashboard.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("ACCU SDK Test App", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Text("com.accu.sdkdemo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                drawerSections.forEach { (sectionTitle, items) ->
                    Text(sectionTitle, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    items.forEach { screen ->
                        NavigationDrawerItem(
                            icon    = { Icon(screen.icon, null, modifier = Modifier.size(20.dp)) },
                            label   = { Text(screen.label, style = MaterialTheme.typography.bodyMedium) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(screen.route) { launchSingleTop = true; popUpTo(Screen.Dashboard.route) }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = drawerSections.flatMap { it.second }.find { it.route == currentRoute }?.label ?: "ACCU SDK Test"
                        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AppNavHost(navController, vm)
            }
        }
    }
}

@Composable
fun AppNavHost(nav: NavHostController, vm: MainViewModel) {
    NavHost(nav, startDestination = Screen.Dashboard.route) {
        composable(Screen.Dashboard.route)         { DashboardScreen(vm) }
        composable(Screen.Connection.route)        { ConnectionDiagnosticsScreen(vm) }
        composable(Screen.Permission.route)        { PermissionTestScreen(vm) }
        composable(Screen.Scopes.route)            { ScopeInspectorScreen(vm) }
        composable(Screen.Shell.route)             { ShellTestScreen(vm) }
        composable(Screen.PackageManager.route)    { PackageManagerScreen(vm) }
        composable(Screen.PermissionOps.route)     { PermissionOpsScreen(vm) }
        composable(Screen.Settings.route)          { SettingsTestScreen(vm) }
        composable(Screen.Locale.route)            { LocaleTestScreen(vm) }
        composable(Screen.ApiExplorer.route)       { ApiExplorerScreen(vm) }
        composable(Screen.LogCenter.route)         { LogCenterScreen(vm) }
        composable(Screen.CrashCenter.route)       { CrashCenterScreen(vm) }
        composable(Screen.DiagnosticsExport.route) { DiagnosticsExportScreen(vm) }
        composable(Screen.AutomatedTests.route)    { AutomatedTestScreen(vm) }
    }
}
