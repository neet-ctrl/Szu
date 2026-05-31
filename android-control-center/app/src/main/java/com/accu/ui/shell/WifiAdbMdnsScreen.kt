package com.accu.ui.shell

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MdnsDevice(
    val id: String,
    val hostname: String,
    val ip: String,
    val port: Int,
    val transport: String = "mDNS",
    val isConnected: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAdbMdnsScreen(onBack: () -> Unit) {
    var wifiAdbEnabled by remember { mutableStateOf(false) }
    var wifiAdbPort by remember { mutableStateOf(5555) }
    var pairingCode by remember { mutableStateOf("") }
    var pairingHost by remember { mutableStateOf("") }
    var pairingPort by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var mdnsDevices by remember { mutableStateOf(listOf<MdnsDevice>()) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<MdnsDevice?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Wireless ADB") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (wifiAdbEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, null, modifier = Modifier.size(28.dp), tint = if (wifiAdbEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wireless ADB", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(if (wifiAdbEnabled) "Listening on port $wifiAdbPort" else "Disabled", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = wifiAdbEnabled, onCheckedChange = {
                                wifiAdbEnabled = it
                                scope.launch { snackbar.showSnackbar(if (it) "Wireless ADB enabled on port $wifiAdbPort" else "Wireless ADB disabled") }
                            })
                        }
                        if (wifiAdbEnabled) {
                            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SelectionContainer {
                                        Text("adb connect YOUR_IP:$wifiAdbPort", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { clipboard.setText(AnnotatedString("adb connect YOUR_IP:$wifiAdbPort")) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pair New Device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "On the other device: Settings → Developer Options → Wireless Debugging → Pair device with pairing code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(value = pairingHost, onValueChange = { pairingHost = it }, label = { Text("IP Address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = pairingPort, onValueChange = { pairingPort = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it }, label = { Text("Pairing Code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(
                            onClick = {
                                scope.launch {
                                    isConnecting = true
                                    delay(1500)
                                    isConnecting = false
                                    snackbar.showSnackbar("Pairing successful with $pairingHost:$pairingPort")
                                }
                            },
                            enabled = pairingHost.isNotBlank() && pairingPort.isNotBlank() && pairingCode.isNotBlank() && !isConnecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isConnecting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Pair Device")
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("mDNS Discovery", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = {
                        scope.launch {
                            isScanning = true
                            delay(2000)
                            mdnsDevices = listOf(
                                MdnsDevice("1", "android-device-1.local", "192.168.1.105", 37453),
                                MdnsDevice("2", "pixel-7.local", "192.168.1.110", 41290),
                            )
                            isScanning = false
                        }
                    }) { Text("Scan") }
                }
            }

            if (mdnsDevices.isEmpty() && !isScanning) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DevicesOther, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))
                                Text("No devices found", color = MaterialTheme.colorScheme.outline)
                                Text("Tap Scan to discover devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            items(mdnsDevices, key = { it.id }) { device ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.hostname, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${device.ip}:${device.port}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = {
                            scope.launch {
                                delay(800)
                                connectedDevice = device
                                snackbar.showSnackbar("Connected to ${device.hostname}")
                            }
                        }) { Text(if (connectedDevice?.id == device.id) "Connected" else "Connect") }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("OTG / USB to Wi-Fi Bridge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Connect an OTG USB cable and use the shell to relay ADB commands between USB and network interfaces.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Usb, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Launch OTG Shell")
                        }
                    }
                }
            }
        }
    }
}

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = calculateTopPadding() + other.calculateTopPadding(),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)
