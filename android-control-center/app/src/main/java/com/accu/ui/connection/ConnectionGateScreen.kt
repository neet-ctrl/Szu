package com.accu.ui.connection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.connection.AccuConnectionManager
import com.accu.ui.shizuku.ShizukuViewModel

/**
 * Full-screen connection gate that blocks entry into ACCU until a
 * privilege connection is established.
 *
 * Flow:
 *   Phase 1 — Connection options: Root / Wireless ADB / OTG USB
 *   Phase 2 — Device confirmation: shows model, Android version, IP, method
 *             → "Enter ACCU" button navigates to Dashboard
 *
 * If the device is already connected on launch (e.g. root auto-detected),
 * the gate skips directly to Phase 2.
 */
@Composable
fun ConnectionGateScreen(
    onConnected: () -> Unit,
    onNavigateToAdbPairing: () -> Unit,
    viewModel: ShizukuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val isConnected = state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_ROOT
            || state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS
            || state.connectionState == AccuConnectionManager.ConnectionState.CONNECTED_OTG

    AnimatedContent(
        targetState = isConnected,
        transitionSpec = {
            fadeIn(androidx.compose.animation.core.tween(400)) +
                slideInVertically(androidx.compose.animation.core.tween(400)) { it / 4 } togetherWith
                fadeOut(androidx.compose.animation.core.tween(200))
        },
        label = "gate_phase",
    ) { connected ->
        if (connected) {
            DeviceConfirmationPhase(state = state, viewModel = viewModel, onEnter = onConnected)
        } else {
            ConnectionOptionsPhase(
                state = state,
                viewModel = viewModel,
                onNavigateToAdbPairing = onNavigateToAdbPairing,
            )
        }
    }
}

// ── Phase 1: Connection Options ───────────────────────────────────────────────

@Composable
private fun ConnectionOptionsPhase(
    state: com.accu.ui.shizuku.ShizukuUiState,
    viewModel: ShizukuViewModel,
    onNavigateToAdbPairing: () -> Unit,
) {
    val isConnecting = state.connectionState == AccuConnectionManager.ConnectionState.CONNECTING
            || state.connectionState == AccuConnectionManager.ConnectionState.DISCOVERING
            || state.connectionState == AccuConnectionManager.ConnectionState.AWAITING_CODE
            || state.isLoading

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

            // ── Branding ──────────────────────────────────────────────────────
            Box(
                Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "ACCU",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Text(
                "Android Control Center",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )

            Spacer(Modifier.height(40.dp))

            // ── Header ────────────────────────────────────────────────────────
            Text(
                "Connect to Device",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Choose how ACCU should connect. All features will run on the connected device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(28.dp))

            // ── Connection Method Cards ───────────────────────────────────────
            ConnectionMethodCard(
                icon = Icons.Filled.AdminPanelSettings,
                title = "Root",
                subtitle = "Recommended · uid=0 · Full privilege",
                description = "Requires a rooted device. ACCU gets root access automatically — no extra setup.",
                color = Color(0xFF16A34A),
                loading = isConnecting && state.connectionState == AccuConnectionManager.ConnectionState.CONNECTING,
                onClick = { viewModel.startWithRoot() },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            ConnectionMethodCard(
                icon = Icons.Filled.Wifi,
                title = "Wireless ADB",
                subtitle = "Android 11+ · Wi-Fi · uid=2000",
                description = "Pair via Developer Options → Wireless debugging. Both devices must be on the same Wi-Fi.",
                color = Color(0xFF2563EB),
                loading = isConnecting && (
                    state.connectionState == AccuConnectionManager.ConnectionState.DISCOVERING ||
                    state.connectionState == AccuConnectionManager.ConnectionState.AWAITING_CODE
                ),
                onClick = onNavigateToAdbPairing,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            ConnectionMethodCard(
                icon = Icons.Filled.Usb,
                title = "OTG / USB ADB",
                subtitle = "USB cable · uid=2000",
                description = "Connect two phones with a USB OTG cable. Enable USB Debugging on the target device first.",
                color = Color(0xFFD97706),
                loading = isConnecting,
                onClick = { viewModel.connectOtg() },
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Connecting status ─────────────────────────────────────────────
            AnimatedVisibility(visible = isConnecting || state.pairingStatus.isNotBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isConnecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Info, null, Modifier.size(18.dp))
                            Text(
                                when (state.connectionState) {
                                    AccuConnectionManager.ConnectionState.DISCOVERING -> "Scanning for Wireless Debugging service…"
                                    AccuConnectionManager.ConnectionState.AWAITING_CODE -> "Pairing service found — enter your 6-digit code"
                                    AccuConnectionManager.ConnectionState.CONNECTING -> "Connecting…"
                                    else -> state.pairingStatus.ifEmpty { "Checking connection…" }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Recent / saved sessions ───────────────────────────────────────
            val savedSessions = remember(state) { viewModel.getSavedSessions() }
            if (savedSessions.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Recent Connections",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${savedSessions.size} saved",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    savedSessions.forEach { session ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF2563EB).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Wifi, null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        session.label.ifBlank { session.ip },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        "${session.ip}:${session.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteSavedSession(session.ip, session.port) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.reconnectToSession(session) },
                                    enabled = !isConnecting,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(Icons.Filled.Wifi, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reconnect", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Footer ────────────────────────────────────────────────────────
            Text(
                "ACCU connects once — all 85+ features share that single connection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun ConnectionMethodCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    color: Color,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        enabled = !loading,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = color,
                    )
                } else {
                    Icon(icon, null, Modifier.size(28.dp), tint = color)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = color,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

// ── Phase 2: Device Confirmation ──────────────────────────────────────────────

@Composable
private fun DeviceConfirmationPhase(
    state: com.accu.ui.shizuku.ShizukuUiState,
    viewModel: ShizukuViewModel,
    onEnter: () -> Unit,
) {
    val methodColor = when (state.connectionState) {
        AccuConnectionManager.ConnectionState.CONNECTED_ROOT     -> Color(0xFF16A34A)
        AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS -> Color(0xFF2563EB)
        AccuConnectionManager.ConnectionState.CONNECTED_OTG      -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.primary
    }
    val methodLabel = when (state.connectionState) {
        AccuConnectionManager.ConnectionState.CONNECTED_ROOT     -> "Root  ·  uid=0"
        AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS -> "Wireless ADB  ·  uid=2000"
        AccuConnectionManager.ConnectionState.CONNECTED_OTG      -> "OTG / USB ADB  ·  uid=2000"
        else -> state.serverStartMethod
    }
    val methodIcon = when (state.connectionState) {
        AccuConnectionManager.ConnectionState.CONNECTED_ROOT     -> Icons.Filled.AdminPanelSettings
        AccuConnectionManager.ConnectionState.CONNECTED_WIRELESS -> Icons.Filled.Wifi
        AccuConnectionManager.ConnectionState.CONNECTED_OTG      -> Icons.Filled.Usb
        else -> Icons.Filled.CheckCircle
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        methodColor.copy(alpha = 0.06f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

            // ── Connected checkmark ───────────────────────────────────────────
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(methodColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(52.dp), tint = methodColor)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Connection Verified",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = methodColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Target device is ready. All features are active.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            // ── Device info card ─────────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    // Connection method badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(methodIcon, null, Modifier.size(16.dp), tint = methodColor)
                        Text(
                            methodLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = methodColor,
                        )
                    }

                    HorizontalDivider()

                    // Device info rows
                    if (state.deviceModel.isNotBlank()) {
                        DeviceInfoRow(Icons.Outlined.PhoneAndroid, "Device", state.deviceModel)
                    }
                    if (state.androidVersion.isNotBlank()) {
                        DeviceInfoRow(
                            Icons.Outlined.Android,
                            "Android",
                            buildString {
                                append(state.androidVersion)
                                if (state.sdkLevel.isNotBlank()) append("  (API ${state.sdkLevel})")
                            },
                        )
                    }
                    if (state.deviceIp.isNotBlank()) {
                        DeviceInfoRow(Icons.Outlined.Language, "IP Address", state.deviceIp)
                    }
                    DeviceInfoRow(
                        Icons.Outlined.Memory,
                        "UID",
                        when (state.uid) {
                            0    -> "0  (root — full privilege)"
                            2000 -> "2000  (adb shell)"
                            else -> "${state.uid}"
                        },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Enter button ──────────────────────────────────────────────────
            Button(
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = methodColor),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Enter ACCU",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.ArrowForward, null, Modifier.size(18.dp))
            }

            Spacer(Modifier.height(14.dp))

            // Disconnect option
            TextButton(onClick = { viewModel.stopServer() }) {
                Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Disconnect & choose another method", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
