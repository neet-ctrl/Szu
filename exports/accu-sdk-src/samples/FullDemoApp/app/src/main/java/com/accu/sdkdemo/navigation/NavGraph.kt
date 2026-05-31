package com.accu.sdkdemo.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard          : Screen("dashboard",    "Dashboard",          Icons.Default.Dashboard)
    object Connection         : Screen("connection",   "Connection",         Icons.Default.Cable)
    object Permission         : Screen("permission",   "Permission",         Icons.Default.Security)
    object Scopes             : Screen("scopes",       "Scope Inspector",    Icons.Default.Tune)
    object Shell              : Screen("shell",        "Shell Test",         Icons.Default.Terminal)
    object PackageManager     : Screen("packages",     "Package Manager",    Icons.Default.Apps)
    object PermissionOps      : Screen("permops",      "Permission Ops",     Icons.Default.ManageAccounts)
    object Settings           : Screen("settings",     "Settings Test",      Icons.Default.Settings)
    object Locale             : Screen("locale",       "Locale Test",        Icons.Default.Language)
    object ApiExplorer        : Screen("apiexplorer",  "API Explorer",       Icons.Default.Api)
    object LogCenter          : Screen("logs",         "Log Center",         Icons.Default.List)
    object CrashCenter        : Screen("crashes",      "Crash Center",       Icons.Default.BugReport)
    object DiagnosticsExport  : Screen("export",       "Export Diagnostics", Icons.Default.FileDownload)
    object AutomatedTests     : Screen("autotests",    "Automated Tests",    Icons.Default.PlayArrow)
}

val drawerSections = listOf(
    "Overview"     to listOf(Screen.Dashboard),
    "Connection"   to listOf(Screen.Connection, Screen.Permission, Screen.Scopes),
    "API Testing"  to listOf(Screen.Shell, Screen.PackageManager, Screen.PermissionOps, Screen.Settings, Screen.Locale, Screen.ApiExplorer),
    "Diagnostics"  to listOf(Screen.LogCenter, Screen.CrashCenter, Screen.DiagnosticsExport, Screen.AutomatedTests),
)
