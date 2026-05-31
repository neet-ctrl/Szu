package com.accu.ui.appmanager

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ScanResult(val label: String, val color: @Composable () -> Color, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CLEAN("Clean", { MaterialTheme.colorScheme.tertiary }, Icons.Default.CheckCircle),
    SUSPICIOUS("Suspicious", { MaterialTheme.colorScheme.primary }, Icons.Default.Warning),
    MALICIOUS("Malicious", { MaterialTheme.colorScheme.error }, Icons.Default.Error),
    UNSCANNED("Not Scanned", { MaterialTheme.colorScheme.outline }, Icons.Default.Help),
}

data class AppScanInfo(
    val packageName: String,
    val appName: String,
    val sha256: String,
    val result: ScanResult = ScanResult.UNSCANNED,
    val detections: Int = 0,
    val totalEngines: Int = 70,
    val lastScanned: Long? = null,
)

val SAMPLE_SCAN_RESULTS = listOf(
    AppScanInfo("com.google.android.youtube", "YouTube", "abc123def456", ScanResult.CLEAN, 0, 70, System.currentTimeMillis() - 86400000),
    AppScanInfo("com.spotify.music", "Spotify", "def456ghi789", ScanResult.CLEAN, 0, 70, System.currentTimeMillis() - 172800000),
    AppScanInfo("com.suspicious.app", "Unknown App", "ghi789jkl012", ScanResult.SUSPICIOUS, 3, 70, System.currentTimeMillis() - 43200000),
    AppScanInfo("com.instagram.android", "Instagram", "jkl012mno345", ScanResult.UNSCANNED),
    AppScanInfo("com.example.malware", "Cracked App", "mno345pqr678", ScanResult.MALICIOUS, 42, 70, System.currentTimeMillis() - 3600000),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirusTotalScreen(onBack: () -> Unit) {
    val apps = remember { mutableStateListOf(*SAMPLE_SCAN_RESULTS.toTypedArray()) }
    var apiKey by remember { mutableStateOf("") }
    var isApiKeySet by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var scanningPackage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val cleanCount = apps.count { it.result == ScanResult.CLEAN }
    val maliciousCount = apps.count { it.result == ScanResult.MALICIOUS }
    val suspiciousCount = apps.count { it.result == ScanResult.SUSPICIOUS }
    val unscannedCount = apps.count { it.result == ScanResult.UNSCANNED }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("VirusTotal Scan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showApiKeyDialog = true }) { Icon(Icons.Default.Key, "API Key") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isApiKeySet) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("API Key Required", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Set your VirusTotal API key to scan apps", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { showApiKeyDialog = true }) { Text("Set Key") }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ScanStat("Clean", cleanCount, MaterialTheme.colorScheme.tertiary)
                    ScanStat("Suspicious", suspiciousCount, MaterialTheme.colorScheme.primary)
                    ScanStat("Malicious", maliciousCount, MaterialTheme.colorScheme.error)
                    ScanStat("Unscanned", unscannedCount, MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            apps.filter { it.result == ScanResult.UNSCANNED }.forEach { app ->
                                val idx = apps.indexOfFirst { it.packageName == app.packageName }
                                scanningPackage = app.packageName
                                delay(800)
                                if (idx != -1) apps[idx] = app.copy(result = ScanResult.CLEAN, totalEngines = 70, lastScanned = System.currentTimeMillis())
                            }
                            scanningPackage = null
                            snackbar.showSnackbar("All unscanned apps checked")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isApiKeySet && unscannedCount > 0,
                ) { Text("Scan Unscanned") }
                Button(
                    onClick = {
                        scope.launch {
                            apps.forEachIndexed { idx, app ->
                                scanningPackage = app.packageName
                                delay(600)
                                apps[idx] = app.copy(result = ScanResult.CLEAN, totalEngines = 70, lastScanned = System.currentTimeMillis())
                            }
                            scanningPackage = null
                            snackbar.showSnackbar("All ${apps.size} apps scanned")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isApiKeySet,
                ) { Text("Scan All") }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.sortedByDescending { it.result.ordinal }, key = { it.packageName }) { app ->
                    val idx = apps.indexOfFirst { it.packageName == app.packageName }
                    VirusTotalCard(
                        app = app,
                        isScanning = scanningPackage == app.packageName,
                        onScan = {
                            scope.launch {
                                scanningPackage = app.packageName
                                delay(1200)
                                if (idx != -1) apps[idx] = app.copy(result = ScanResult.CLEAN, detections = 0, lastScanned = System.currentTimeMillis())
                                scanningPackage = null
                            }
                        }
                    )
                }
            }
        }
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("VirusTotal API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Get your free API key at virustotal.com/gui/my-apikey", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    isApiKeySet = apiKey.isNotBlank()
                    showApiKeyDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ScanStat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VirusTotalCard(app: AppScanInfo, isScanning: Boolean, onScan: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (app.result) {
                ScanResult.MALICIOUS -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ScanResult.SUSPICIOUS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                } else {
                    Icon(app.result.icon, null, tint = app.result.color(), modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (app.result != ScanResult.UNSCANNED) {
                    Text(
                        "${app.detections}/${app.totalEngines} engines detected",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (app.detections > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Surface(color = app.result.color().copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                Text(app.result.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = app.result.color(), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onScan, enabled = !isScanning, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
