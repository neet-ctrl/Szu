package com.accu.ui.shizuku

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.ACCTopBar

/**
 * ACCU wireless ADB pairing screen.
 *
 * Standard wireless ADB pairing flow, self-contained inside ACCU:
 *   1. User enables Developer Options + Wireless Debugging (one-time)
 *   2. ACCU auto-discovers the pairing port via mDNS — no IP/port entry needed
 *   3. User enters only the 6-digit code shown on their screen
 *   4. ACCU pairs and connects automatically
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbPairingScreen(
    onBack: () -> Unit = {},
    viewModel: ShizukuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(1) }
    var pairingCode by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    // How many devices were connected when this screen opened — used to detect a new pairing
    val deviceCountAtEntry = remember { state.multiDevices.size }
    // True while the user is going through the wizard to add a second/third device
    var addingNewDevice by remember { mutableStateOf(false) }

    // Back handler: when adding a device, cancel back to step 4; otherwise exit the screen
    val handleBack: () -> Unit = {
        if (addingNewDevice) {
            viewModel.onAddDeviceCancelled()
            viewModel.stopDiscovery()
            addingNewDevice = false
            step = 4
        } else {
            onBack()
        }
    }

    // Auto-advance steps.  Also keyed on multiDevices.size so that when a new device is
    // registered (connectionState stays CONNECTED_WIRELESS but device count grows), we still
    // advance to step 4 correctly.
    LaunchedEffect(state.connectionState, state.multiDevices.size) {
        val isConnected = state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS
                || state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_ROOT
                || state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_OTG
        if (state.connectionState == AccuConnectionManager.ConnectionState.AWAITING_CODE && step < 3) {
            step = 3
        }
        // Advance to step 4 only when:
        //  (a) not currently adding a new device, OR
        //  (b) a new device just joined the registry (device count grew)
        if (isConnected && (!addingNewDevice || state.multiDevices.size > deviceCountAtEntry)) {
            addingNewDevice = false
            step = 4
        }
    }

    Scaffold(topBar = { ACCTopBar(title = "ACCU Wireless Setup", onBack = handleBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── Step 1: Enable Developer Options ─────────────────────────────
            item {
                StepCard(step = 1, currentStep = step, title = "Enable Developer Options") {
                    Text(
                        "Go to: Settings → About phone → tap Build number 7 times",
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Done — Next")
                    }
                }
            }

            // ── Step 2: Enable Wireless Debugging + start auto-discovery ─────
            item {
                StepCard(step = 2, currentStep = step, title = "Enable Wireless Debugging") {
                    Text(
                        "Settings → Developer Options → Wireless debugging → Enable",
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Then tap → Pair device with pairing code",
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(12.dp))

                    // Discovery status banner
                    AnimatedVisibility(state.connectionState == AccuConnectionManager.ConnectionState.DISCOVERING) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Scanning for Wireless Debugging service…",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { step = 1 }, Modifier.weight(1f)) { Text("Back") }
                        Button(
                            onClick = {
                                step = 3
                                viewModel.startDiscovery()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Start Discovery") }
                    }
                }
            }

            // ── Step 3: Enter 6-digit code (IP/port auto-detected) ───────────
            item {
                StepCard(step = 3, currentStep = step, title = "Enter Pairing Code") {
                    when (state.connectionState) {
                        AccuConnectionManager.ConnectionState.AWAITING_CODE -> {
                            // Pairing service auto-detected — show the actual IP and port
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("Pairing service detected!", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        if (state.discoveredPairingIp.isNotBlank() && state.discoveredPairingPort > 0) {
                                            Spacer(Modifier.height(2.dp))
                                            SelectionContainer {
                                                Text(
                                                    "${state.discoveredPairingIp}:${state.discoveredPairingPort}",
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        } else {
                                            Text("IP and port captured automatically", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        AccuConnectionManager.ConnectionState.DISCOVERING -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Waiting for Wireless Debugging pairing tap…", fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        else -> {}
                    }

                    Text(
                        "Enter the 6-digit code shown in Developer Options → Wireless Debugging → Pair device with pairing code:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { if (it.length <= 6) pairingCode = it.filter { c -> c.isDigit() } },
                        label = { Text("6-digit pairing code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("123456") },
                        trailingIcon = {
                            if (pairingCode.length == 6) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        },
                    )

                    if (state.pairingStatus.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        val isPcCommand      = state.pairingStatus.contains("adb pair")
                        val isConnectionFail = state.isConnectionFailed
                        val isSuccess        = state.pairingStatus.contains("✓")
                                            && !state.pairingStatus.contains("but", ignoreCase = true)
                                            && !state.pairingStatus.contains("failed", ignoreCase = true)

                        when {
                            // ── ConnectionFailed card — pairing OK but TLS session failed ──────
                            isConnectionFail -> ConnectionFailedCard(
                                host       = state.connectionFailedHost,
                                port       = state.connectionFailedPort,
                                rawError   = state.connectionFailedRaw,
                                debugLog   = state.connectionDebugLog,
                                isRetrying = state.isRetryingConnection,
                                onCopyDebugLog = {
                                    clipboardManager.setText(AnnotatedString(state.connectionDebugLog))
                                },
                                onRetry    = { viewModel.retryConnection() },
                            )

                            // ── PC-command card ───────────────────────────────────────────────
                            isPcCommand -> {
                                val statusColor = MaterialTheme.colorScheme.tertiary
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Computer, null, Modifier.size(14.dp), tint = statusColor)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Run from your PC", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = statusColor)
                                            Spacer(Modifier.weight(1f))
                                            IconButton(
                                                onClick = { clipboardManager.setText(AnnotatedString(state.pairingStatus)) },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(Icons.Default.ContentCopy, "Copy commands", Modifier.size(14.dp), tint = statusColor)
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        SelectionContainer {
                                            Text(state.pairingStatus, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = statusColor, lineHeight = 18.sp)
                                        }
                                    }
                                }
                            }

                            // ── Generic success / error card ──────────────────────────────────
                            else -> {
                                val statusColor = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    SelectionContainer {
                                        Text(state.pairingStatus, fontSize = 12.sp, color = statusColor, lineHeight = 18.sp, modifier = Modifier.padding(10.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            viewModel.stopDiscovery()
                            step = 2
                        }, Modifier.weight(1f)) { Text("Back") }
                        Button(
                            onClick = { viewModel.completePairing(pairingCode) },
                            modifier = Modifier.weight(1f),
                            enabled = pairingCode.length == 6 && !state.isPairing,
                        ) {
                            if (state.isPairing) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Pairing…")
                            } else {
                                Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pair & Connect")
                            }
                        }
                    }
                }
            }

            // ── Step 4: Connected! ────────────────────────────────────────────
            item {
                // Keyed on `step == 4` so the card hides properly when the user
                // taps "Add Another Device" and the wizard resets to step 1 —
                // even though the first device is still CONNECTED_WIRELESS.
                AnimatedVisibility(visible = step == 4) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    state.multiDevices.size > 1 ->
                                        "ACCU Connected! (${state.multiDevices.size} devices)"
                                    else -> "ACCU Connected!"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "All privileged features are now active.\nThis connection is remembered automatically.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )

                            // Show device list when multiple are connected
                            if (state.multiDevices.size > 1) {
                                Spacer(Modifier.height(10.dp))
                                state.multiDevices.forEach { device ->
                                    val isActive = device.id == state.activeDeviceId
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            if (isActive) Icons.Default.RadioButtonChecked
                                            else Icons.Default.RadioButtonUnchecked,
                                            null,
                                            Modifier.size(14.dp),
                                            tint = if (isActive) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            device.label.ifBlank { device.ip },
                                            fontSize = 12.sp,
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isActive) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (device.ip.isNotBlank()) {
                                            Text(
                                                device.ip,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                        if (isActive) {
                                            Text(
                                                "active",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                            Spacer(Modifier.height(8.dp))
                            // Add another device WITHOUT disconnecting the existing one
                            OutlinedButton(
                                onClick = {
                                    viewModel.onBeforeAddDevice()
                                    viewModel.stopDiscovery()
                                    pairingCode = ""
                                    addingNewDevice = true
                                    step = 1
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.AddCircle, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Another Device")
                            }
                        }
                    }
                }
            }

            // ── Help ─────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpOutline, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Troubleshooting", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• Phone and device must be on the same Wi-Fi network\n" +
                            "• The code changes each time you open Wireless Debugging\n" +
                            "• Android 11+ required for wireless ADB without USB\n" +
                            "• If discovery fails, try tapping 'Pair device with pairing code' again\n" +
                            "• Once paired, ACCU remembers the connection — no repeat setup needed",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
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
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isDone    -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                isCurrent -> MaterialTheme.colorScheme.surface
                else      -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) {
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

/**
 * Error card shown when SPAKE2 pairing succeeded but the TLS ADB connection failed.
 *
 * Shows:
 *  - "Pairing succeeded ✓" header so the user knows the code was correct
 *  - Target host:port that was tried
 *  - Inline diagnosis line extracted from the first error line
 *  - Expandable raw stack trace for the developer
 *  - "Copy Debug Log" button — copies a comprehensive self-contained report that
 *    includes host/device info, diagnosis, stack trace, and 100 session log entries
 *  - Retry Connection button — re-attempts TLS connect without a new pairing code
 */
@Composable
private fun ConnectionFailedCard(
    host: String,
    port: Int,
    rawError: String,
    debugLog: String,
    isRetrying: Boolean,
    onCopyDebugLog: () -> Unit,
    onRetry: () -> Unit,
) {
    var showFullLog by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    // Reset "Copied!" indicator after 2.5 s
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2500)
            copied = false
        }
    }

    // Inline one-liner diagnosis from first error line
    val firstLine = rawError.lines().firstOrNull().orEmpty()
    val quickDiag = when {
        "connection closed" in firstLine  -> "adbd rejected our TLS cert — key mismatch or session expired"
        "Handshake failed"  in firstLine  -> "TLS cipher mismatch — check Android version compatibility"
        "Connection refused" in firstLine -> "Session port is stale — re-discover via 'Wireless ADB' button"
        "timed out" in firstLine          -> "Network timeout — confirm both devices are on the same Wi-Fi"
        "ECONNRESET" in rawError          -> "Connection reset — wireless debugging may have restarted"
        "echo check" in firstLine         -> "TLS OK but ADB CNXN echo verification failed"
        firstLine.isNotBlank()            -> firstLine.take(80)
        else                              -> "Unknown — see full trace below"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {

            // ── Header row ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LinkOff, null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Pairing succeeded ✓ — Connection failed",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (host.isNotBlank() && port > 0) {
                        Text(
                            "$host : $port",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Inline diagnosis ──────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        Modifier.size(14.dp).padding(top = 1.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        quickDiag,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Expand / collapse raw stack trace ─────────────────────────────
            TextButton(
                onClick = { showFullLog = !showFullLog },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
            ) {
                Icon(
                    if (showFullLog) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (showFullLog) "Hide stack trace" else "Show raw stack trace",
                    fontSize = 12.sp,
                )
            }

            AnimatedVisibility(showFullLog) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    SelectionContainer {
                        Text(
                            rawError.ifBlank { "(no error detail captured)" },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Copy Debug Log button ─────────────────────────────────────────
            // Copies a complete self-contained report: device info, diagnosis,
            // full stack trace, and last 100 ACCU session log entries.
            // Paste it into a bug report or send it to the ACCU developer.
            OutlinedButton(
                onClick = {
                    onCopyDebugLog()
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (copied) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                    null, Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (copied) "Copied to clipboard!" else "Copy Full Debug Log",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Device info · diagnosis · stack trace · 100 session logs",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Retry ─────────────────────────────────────────────────────────
            Text(
                "The pairing code worked — your device already trusts ACCU. " +
                "Just tap Retry; no new code is needed.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                enabled = !isRetrying,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor   = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Retrying…")
                } else {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry Connection")
                }
            }
        }
    }
}
