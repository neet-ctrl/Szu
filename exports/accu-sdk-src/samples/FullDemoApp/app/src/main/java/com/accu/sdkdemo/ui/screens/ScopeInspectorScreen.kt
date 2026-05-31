package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accu.sdk.AccuScopes
import com.accu.sdkdemo.ui.components.SectionHeader
import com.accu.sdkdemo.ui.components.StatusBadge
import com.accu.sdkdemo.ui.components.StatusColor
import com.accu.sdkdemo.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private data class ScopeInfo(
    val name: String,
    val description: String,
    val apis: String,
    val transactionRange: String,
)

private val SCOPE_DETAILS = listOf(
    ScopeInfo("SHELL",          "Execute shell commands via exec(), execAsync(), execAndGetOutput()",    "exec, execAsync, execAndGetOutput",              "20–22"),
    ScopeInfo("PACKAGE_MANAGE", "Install, enable, disable, hide, suspend, clear data, force-stop",       "installApk, enable/disable/hide/suspend/clear, forceStop, enableComponent", "30–41, 60"),
    ScopeInfo("PERMISSIONS",    "Grant/revoke runtime permissions and App Ops",                          "grantPermission, revokePermission, setAppOp, getAppOp", "50–53"),
    ScopeInfo("SETTINGS",       "Read and write Settings.Secure / Global / System",                      "read/writeSecureSetting, read/writeGlobalSetting, read/writeSystemSetting", "70–75"),
    ScopeInfo("LOCALE",         "Set per-app locale override (Android 13+)",                             "setApplicationLocale",                           "61"),
)

@Composable
fun ScopeInspectorScreen(vm: MainViewModel) {
    val state by vm.accuState.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("About Scopes")
                    Text(
                        "Scopes are granted alongside the main ACCU permission in the permission dialog. " +
                        "If a scope is missing, the corresponding API group returns false or throws. " +
                        "All scopes are bundled — you cannot request them individually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { vm.checkAllScopes() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh All Scopes (writes to Log Center)")
                    }
                }
            }
        }

        items(SCOPE_DETAILS, key = { it.name }) { scope ->
            ScopeCard(vm, scope)
        }
    }
}

@Composable
private fun ScopeCard(vm: MainViewModel, scope: ScopeInfo) {
    val coroutineScope = rememberCoroutineScope()
    var checked by remember { mutableStateOf<Boolean?>(null) }
    val accuState by vm.accuState.collectAsState()

    LaunchedEffect(accuState) {
        checked = try { vm.accu.hasScope(scope.name) } catch (_: Exception) { null }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(scope.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                when (checked) {
                    true  -> StatusBadge("GRANTED",     StatusColor.GREEN)
                    false -> StatusBadge("NOT GRANTED", StatusColor.RED)
                    null  -> StatusBadge("UNKNOWN",     StatusColor.GREY)
                }
            }
            Text(scope.description, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("APIs covered", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(scope.apis, style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("Transactions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(scope.transactionRange, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = { coroutineScope.launch { checked = try { vm.accu.hasScope(scope.name) } catch (_: Exception) { false } } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Check ${scope.name} live", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
