package com.accu.ui.shizuku

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbPairingScreen(onBack: () -> Unit = {}) {
    var pairingMethod by remember { mutableStateOf("code") } // "code" or "qr"
    var step by remember { mutableIntStateOf(1) } // 1=enable dev, 2=enable wireless adb, 3=pair
    var pairingCode by remember { mutableStateOf("") }
    var ipPort by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var pairingResult by remember { mutableStateOf<Boolean?>(null) }
    var discoveredDevices by remember {
        mutableStateOf(listOf("192.168.1.42:41547"))
    }

    Scaffold(topBar = { ACCTopBar(title = "ADB / Wireless Pairing", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {

            // Step 1: Enable Developer Options
            item {
                StepCard(step = 1, currentStep = step, title = "Enable Developer Options") {
                    Text("On your device, go to:", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Settings → About phone → Build number\n(Tap 7 times until developer mode activates)", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Done — Next step") }
                }
            }

            // Step 2: Enable Wireless ADB
            item {
                StepCard(step = 2, currentStep = step, title = "Enable Wireless Debugging") {
                    Text("Go to:", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Settings → Developer Options → Wireless debugging\n→ Enable it", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { step = 1 }, Modifier.weight(1f)) { Text("Back") }
                        Button(onClick = { step = 3 }, Modifier.weight(1f)) { Text("Done — Next step") }
                    }
                }
            }

            // Step 3: Pair
            item {
                StepCard(step = 3, currentStep = step, title = "Pair with Code or QR") {
                    // Method tabs
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = pairingMethod == "code", onClick = { pairingMethod = "code" }, label = { Text("Code Pairing") }, leadingIcon = { Icon(Icons.Default.Pin, null, Modifier.size(16.dp)) })
                        FilterChip(selected = pairingMethod == "qr", onClick = { pairingMethod = "qr" }, label = { Text("QR Code") }, leadingIcon = { Icon(Icons.Default.QrCode, null, Modifier.size(16.dp)) })
                    }

                    Spacer(Modifier.height(12.dp))

                    if (pairingMethod == "code") {
                        Text("In Wireless debugging, tap \"Pair device with pairing code\".\nEnter the 6-digit code below:", fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { if (it.length <= 6) pairingCode = it },
                            label = { Text("Pairing code (6 digits)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))

                        // Auto-discovered devices
                        if (discoveredDevices.isNotEmpty()) {
                            Text("Discovered on network:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            discoveredDevices.forEach { device ->
                                OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(device, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { ipPort = device }) { Text("Select") }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = ipPort,
                            onValueChange = { ipPort = it },
                            label = { Text("IP:port (from wireless debugging screen)") },
                            placeholder = { Text("192.168.1.42:37777") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    } else {
                        Text("In Wireless debugging, tap \"Pair device with QR code\".\nScan the QR code shown.", fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.fillMaxWidth().height(200.dp).padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ElevatedCard(Modifier.size(180.dp)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Text("Point the device camera at this QR code.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(12.dp))

                    if (pairingResult == true) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Paired successfully! Shizuku can now use wireless ADB.", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else if (pairingResult == false) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text("Pairing failed. Check the code/IP and try again.", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { step = 2 }, Modifier.weight(1f)) { Text("Back") }
                        Button(
                            onClick = {
                                isPairing = true
                                pairingResult = pairingCode.length == 6 || pairingMethod == "qr"
                                isPairing = false
                            },
                            modifier = Modifier.weight(1f),
                            enabled = (pairingMethod == "code" && pairingCode.length == 6 && ipPort.isNotBlank()) || pairingMethod == "qr"
                        ) {
                            if (isPairing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPairing) "Pairing…" else "Pair")
                        }
                    }
                }
            }

            // Help section
            item {
                Spacer(Modifier.height(8.dp))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpOutline, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Troubleshooting", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("• Make sure device and phone are on the same Wi-Fi network\n• The IP:port changes every time Wireless Debugging is toggled\n• Android 11+ required for wireless ADB without USB\n• After pairing, Shizuku will use this connection automatically", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: Int, currentStep: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    val isDone = step < currentStep
    val isCurrent = step == currentStep
    ElevatedCard(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isDone -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isCurrent -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDone) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Badge(containerColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) {
                        Text("$step", color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isCurrent) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}
