package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdkdemo.data.CrashEntry
import com.accu.sdkdemo.data.CrashManager
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun CrashCenterScreen(vm: MainViewModel) {
    val crashes by vm.crashes.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Crash Center", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text("${crashes.size} recorded exceptions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        // Simulate a test crash for demonstration
                        CrashManager.record("TestCrash", "RuntimeException", "Simulated test exception from UI", "com.accu.sdkdemo.ui.screens.CrashCenterScreen\n  at line 35 (simulated)")
                    }) { Text("Simulate") }
                    OutlinedButton(onClick = { CrashManager.clear() }, enabled = crashes.isNotEmpty()) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        if (crashes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("No crashes recorded", style = MaterialTheme.typography.bodyLarge)
                    Text("Use the Simulate button to create a test entry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(crashes.reversed(), key = { it.id }) { crash ->
                    CrashCard(crash)
                }
            }
        }
    }
}

@Composable
private fun CrashCard(crash: CrashEntry) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(crash.exceptionType, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                    Text("API: ${crash.apiInvolved}", style = MaterialTheme.typography.bodySmall)
                    Text(crash.formattedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            Text(crash.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (expanded) {
                HorizontalDivider()
                Text("Stack Trace", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(crash.stackTrace, Modifier.fillMaxWidth().padding(8.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}
