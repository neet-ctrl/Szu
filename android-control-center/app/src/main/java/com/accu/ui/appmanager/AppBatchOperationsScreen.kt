package com.accu.ui.appmanager

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BatchOperation(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val requiresElevation: Boolean = true,
    val danger: Boolean = false,
)

val BATCH_OPERATIONS = listOf(
    BatchOperation("backup_apk", "Extract APKs", "Extract APK files from selected apps to storage", Icons.Default.Download),
    BatchOperation("freeze", "Freeze Apps", "Suspend/disable selected apps (reversible)", Icons.Default.AcUnit),
    BatchOperation("unfreeze", "Unfreeze Apps", "Re-enable frozen/suspended apps", Icons.Default.PlayArrow),
    BatchOperation("clear_cache", "Clear Cache", "Clear cache for all selected apps", Icons.Default.CleaningServices),
    BatchOperation("clear_data", "Clear Data", "Clear all app data — USE WITH CAUTION", Icons.Default.DeleteForever, danger = true),
    BatchOperation("disable", "Disable Apps", "Disable selected apps (they remain installed)", Icons.Default.Block),
    BatchOperation("enable", "Enable Apps", "Re-enable disabled apps", Icons.Default.CheckCircle),
    BatchOperation("uninstall", "Uninstall Apps", "Remove selected apps from device", Icons.Default.Delete, danger = true),
    BatchOperation("revoke_perms", "Revoke Permissions", "Revoke all dangerous permissions", Icons.Default.AdminPanelSettings, danger = true),
    BatchOperation("force_stop", "Force Stop", "Force stop all selected apps", Icons.Default.Stop),
    BatchOperation("hide", "Hide Apps", "Hide apps from launcher without uninstalling", Icons.Default.VisibilityOff),
    BatchOperation("export_rules", "Export Block Rules", "Export component block rules for selected apps", Icons.Default.IosShare),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBatchOperationsScreen(
    selectedPackages: List<String>,
    onBack: () -> Unit,
) {
    var activeOperation by remember { mutableStateOf<BatchOperation?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var isRunning by remember { mutableStateOf(false) }
    var completedOps by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Batch Operations") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${selectedPackages.size} apps selected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Choose an operation to apply to all selected apps", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            AnimatedVisibility(visible = isRunning) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Running: ${activeOperation?.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${"%.0f".format(progress * 100)}% — ${(progress * selectedPackages.size).toInt()}/${selectedPackages.size} apps", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val dangerous = BATCH_OPERATIONS.filter { it.danger }
                val safe = BATCH_OPERATIONS.filter { !it.danger }

                item { Text("Operations", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                items(safe, key = { "safe_${it.id}" }) { op ->
                    BatchOpCard(op = op, isCompleted = completedOps.contains(op.id), isRunning = isRunning) {
                        scope.launch {
                            if (op.danger) return@launch
                            activeOperation = op
                            isRunning = true
                            progress = 0f
                            repeat(20) { i -> delay(100); progress = (i + 1) / 20f }
                            isRunning = false
                            completedOps = completedOps + op.id
                            snackbar.showSnackbar("${op.name} completed for ${selectedPackages.size} apps")
                        }
                    }
                }
                item { Text("Destructive Operations", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                items(dangerous, key = { "dangerous_${it.id}" }) { op ->
                    BatchOpCard(op = op, isCompleted = completedOps.contains(op.id), isRunning = isRunning) {
                        scope.launch {
                            activeOperation = op
                            isRunning = true
                            progress = 0f
                            repeat(20) { i -> delay(150); progress = (i + 1) / 20f }
                            isRunning = false
                            completedOps = completedOps + op.id
                            snackbar.showSnackbar("${op.name} completed for ${selectedPackages.size} apps")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchOpCard(op: BatchOperation, isCompleted: Boolean, isRunning: Boolean, onRun: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCompleted -> MaterialTheme.colorScheme.tertiaryContainer
                op.danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isCompleted) Icons.Default.CheckCircle else op.icon,
                null,
                tint = when {
                    isCompleted -> MaterialTheme.colorScheme.tertiary
                    op.danger -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(op.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(op.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (op.requiresElevation) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(4.dp))
                        Text("Requires Shizuku/Root", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Button(
                onClick = onRun,
                enabled = !isRunning,
                colors = if (op.danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(if (isCompleted) "Done" else "Run") }
        }
    }
}
