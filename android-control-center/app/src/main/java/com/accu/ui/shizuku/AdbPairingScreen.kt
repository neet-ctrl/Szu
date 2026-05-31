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

    // Advance to step 3 automatically when ACCU detects the pairing service
    LaunchedEffect(state.connectionState) {
        if (state.connectionState == AccuConnectionManager.ConnectionState.AWAITING_CODE && step < 3) {
            step = 3
        }
        if (state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS
            || state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_ROOT
            || state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_OTG) {
            step = 4
        }
    }

    Scaffold(topBar = { ACCTopBar(title = "ACCU Wireless Setup", onBack = onBack) }) { padding ->
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
                        val isPcCommand = state.pairingStatus.contains("adb pair")
                        // Only truly successful if the paired-AND-connected message — not ConnectionFailed
                        // which also contains "✓" ("Pairing succeeded ✓ but...")
                        val isSuccess   = state.pairingStatus.contains("✓")
                                       && !state.pairingStatus.contains("but", ignoreCase = true)
                                       && !state.pairingStatus.contains("failed", ignoreCase = true)
                        val statusColor = when {
                            isSuccess   -> MaterialTheme.colorScheme.primary
                            isPcCommand -> MaterialTheme.colorScheme.tertiary
                            else        -> MaterialTheme.colorScheme.error
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSuccess   -> MaterialTheme.colorScheme.primaryContainer
                                    isPcCommand -> MaterialTheme.colorScheme.tertiaryContainer
                                    else        -> MaterialTheme.colorScheme.errorContainer
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                if (isPcCommand) {
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
                                }
                                SelectionContainer {
                                    Text(
                                        state.pairingStatus,
                                        fontSize = 12.sp,
                                        fontFamily = if (isPcCommand) FontFamily.Monospace else FontFamily.Default,
                                        color = statusColor,
                                        lineHeight = 18.sp,
                                    )
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
                AnimatedVisibility(
                    visible = state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS
                            || state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_ROOT
                            || state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_OTG,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("ACCU Connected!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "All privileged features are now active.\nThis connection is remembered automatically.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
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
