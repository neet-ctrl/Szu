package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdk.AccuConnectionState
import com.accu.sdk.AccuConstants
import com.accu.sdk.isDenied
import com.accu.sdk.isGranted
import com.accu.sdk.isNotYetRequested
import com.accu.sdk.isServiceUnavailable
import com.accu.sdk.toPermissionLabel
import com.accu.sdkdemo.ui.components.SectionHeader
import com.accu.sdkdemo.ui.components.StatusColor
import com.accu.sdkdemo.ui.components.StatusRow
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun PermissionTestScreen(vm: MainViewModel) {
    val state by vm.accuState.collectAsState()
    val permCode = if (state is AccuConnectionState.Connected) (state as AccuConnectionState.Connected).permissionCode
    else AccuConstants.PERMISSION_NOT_YET_REQUESTED

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ── Permission state card ─────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = when {
                permCode.isGranted()          -> MaterialTheme.colorScheme.primaryContainer
                permCode.isDenied()           -> MaterialTheme.colorScheme.errorContainer
                else                          -> MaterialTheme.colorScheme.surfaceVariant
            }
        )) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (permCode.isGranted()) Icons.Default.CheckCircle else Icons.Default.Lock,
                    null, Modifier.size(48.dp),
                    tint = if (permCode.isGranted()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(permCode.toPermissionLabel(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("Raw code: $permCode", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ── Actions ────────────────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Actions")
                Button(onClick = { vm.requestPermission() }, modifier = Modifier.fillMaxWidth(), enabled = !permCode.isGranted() || state is AccuConnectionState.Connected) {
                    Icon(Icons.Default.VpnKey, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Request Permission (shows dialog)")
                }
                OutlinedButton(onClick = { vm.checkPermission() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check Permission (no dialog)")
                }
                OutlinedButton(onClick = { vm.revokeSelf() }, modifier = Modifier.fillMaxWidth(), enabled = permCode.isGranted()) {
                    Icon(Icons.Default.Block, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Revoke Self")
                }
            }
        }

        // ── Permission code reference ──────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Permission Code Reference")
                listOf(
                    Triple(AccuConstants.PERMISSION_GRANTED,             "PERMISSION_GRANTED",             StatusColor.GREEN),
                    Triple(AccuConstants.PERMISSION_DENIED,              "PERMISSION_DENIED",              StatusColor.RED),
                    Triple(AccuConstants.PERMISSION_NOT_YET_REQUESTED,   "NOT_YET_REQUESTED",              StatusColor.YELLOW),
                    Triple(AccuConstants.PERMISSION_SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",            StatusColor.GREY),
                ).forEach { (code, label, color) ->
                    StatusRow(
                        label = "$code — $label",
                        value = if (permCode == code) "← Current" else "",
                        statusColor = if (permCode == code) color else StatusColor.GREY,
                    )
                }
            }
        }

        // ── Integration guide ──────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader("Integration Guide")
                Text("1. Call AccuClient.connect() in ViewModel.init", style = MaterialTheme.typography.bodySmall)
                Text("2. Observe accuState for ConnectedState", style = MaterialTheme.typography.bodySmall)
                Text("3. Call requestPermission() — user sees dialog", style = MaterialTheme.typography.bodySmall)
                Text("4. Callback returns PERMISSION_GRANTED (0)", style = MaterialTheme.typography.bodySmall)
                Text("5. Now all API calls work (within granted scopes)", style = MaterialTheme.typography.bodySmall)
                Text("6. Check hasScope(scope) before each API group", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
