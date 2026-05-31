package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdkdemo.ui.components.SectionHeader
import com.accu.sdkdemo.viewmodel.MainViewModel

private val COMMON_PERMISSIONS = listOf(
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_CALL_LOG",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.READ_SMS",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
)

private val COMMON_APP_OPS = listOf(
    "android:read_sms" to "READ_SMS op",
    "android:write_sms" to "WRITE_SMS op",
    "android:receive_sms" to "RECEIVE_SMS op",
    "android:send_sms" to "SEND_SMS op",
    "android:camera" to "CAMERA op",
    "android:record_audio" to "RECORD_AUDIO op",
    "android:fine_location" to "FINE_LOCATION op",
)

@Composable
fun PermissionOpsScreen(vm: MainViewModel) {
    val result by vm.permOpsResult.collectAsState()

    var targetPkg by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("android.permission.READ_CONTACTS") }
    var appOpKey by remember { mutableStateOf("android:read_sms") }
    var appOpMode by remember { mutableStateOf("allow") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Result banner
        if (result.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(result, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
            }
        }

        // Target package
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Target Package")
                OutlinedTextField(value = targetPkg, onValueChange = { targetPkg = it }, label = { Text("Package name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Android, null) })
                Text("Examples: com.example.app, com.google.android.gm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Runtime permissions
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Runtime Permissions (require PERMISSIONS scope)")
                OutlinedTextField(value = permission, onValueChange = { permission = it }, label = { Text("Permission") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Quick select:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    COMMON_PERMISSIONS.forEach { p ->
                        FilterChip(selected = permission == p, onClick = { permission = p }, label = { Text(p.removePrefix("android.permission."), fontSize = 10.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (targetPkg.isNotBlank()) vm.grantPermission(targetPkg, permission) }, modifier = Modifier.weight(1f), enabled = targetPkg.isNotBlank() && permission.isNotBlank()) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Grant")
                    }
                    OutlinedButton(onClick = { if (targetPkg.isNotBlank()) vm.revokePermission(targetPkg, permission) }, modifier = Modifier.weight(1f), enabled = targetPkg.isNotBlank() && permission.isNotBlank()) {
                        Icon(Icons.Default.Remove, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Revoke")
                    }
                }
            }
        }

        // App Ops
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("App Ops")
                OutlinedTextField(value = appOpKey, onValueChange = { appOpKey = it }, label = { Text("App Op key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Quick select:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    COMMON_APP_OPS.forEach { (key, label) ->
                        FilterChip(selected = appOpKey == key, onClick = { appOpKey = key }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
                OutlinedTextField(value = appOpMode, onValueChange = { appOpMode = it }, label = { Text("Mode (allow/deny/ignore/default)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (targetPkg.isNotBlank()) vm.getAppOp(targetPkg, appOpKey) }, modifier = Modifier.weight(1f), enabled = targetPkg.isNotBlank()) { Text("Get") }
                    Button(onClick = { if (targetPkg.isNotBlank()) vm.setAppOp(targetPkg, appOpKey, appOpMode) }, modifier = Modifier.weight(1f), enabled = targetPkg.isNotBlank()) { Text("Set") }
                }
            }
        }
    }
}
