package com.accu.ui.apiservice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.service.AccuClientGrant
import com.accu.service.AccuSystemService
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import com.accu.ui.components.SectionHeaderWithInfo
import com.accu.ui.theme.AccentCyan
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuServiceScreen(
    onBack: () -> Unit,
    onNavigateToDocs: () -> Unit,
    viewModel: AccuServiceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "ACCU Service Hub",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "ACCU System Service",
                        description = "ACCU acts as a privileged broker — just like Shizuku, but built on top of it.\n\nOther apps bind to ACCU's IPC service and call shell commands, install APKs, grant permissions, and control system settings — all through ACCU's permission model.\n\nTap the Docs tab for the full integration guide."
                    )
                    IconButton(onClick = onNavigateToDocs) { Icon(Icons.Default.MenuBook, "SDK Docs") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Hero status card ──────────────────────────────────────────────
            ServiceStatusCard(
                isRunning        = state.isServiceRunning,
                appCount         = state.connectedApps.count { it.isGranted },
                pendingCount     = state.pendingRequests.size,
                callCount        = state.totalApiCalls,
                autostartEnabled = state.bootAutostartEnabled,
                onStart          = viewModel::startService,
                onStop           = viewModel::stopService,
                onAutostartToggle = viewModel::setBootAutostart,
            )

            // ── Tabs ──────────────────────────────────────────────────────────
            val tabs = listOf("Apps (${state.connectedApps.size})", "Pending (${state.pendingRequests.size})", "SDK Docs")
            val tabIdx = state.selectedTab.ordinal
            TabRow(
                selectedTabIndex = tabIdx,
                containerColor   = MaterialTheme.colorScheme.surface,
                indicator = { tabPositions ->
                    if (tabIdx < tabPositions.size)
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[tabIdx]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                },
                divider = {},
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected  = tabIdx == i,
                        onClick   = { viewModel.selectTab(ServiceTab.entries[i]) },
                        text      = { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (tabIdx == i) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }

            AnimatedContent(state.selectedTab, label = "service_tab") { tab ->
                when (tab) {
                    ServiceTab.APPS    -> AppsTab(state.connectedApps, viewModel)
                    ServiceTab.PENDING -> PendingTab(state.pendingRequests, viewModel)
                    ServiceTab.DOCS    -> DocsQuickRef(onNavigateToDocs)
                }
            }
        }
    }
}

// ── Status card ───────────────────────────────────────────────────────────────

@Composable
private fun ServiceStatusCard(
    isRunning: Boolean,
    appCount: Int,
    pendingCount: Int,
    callCount: Long,
    autostartEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAutostartToggle: (Boolean) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) AccentGreen.copy(0.08f) else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(if (isRunning) AccentGreen else AccentRed)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRunning) "Service Running" else "Service Stopped",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) AccentGreen else AccentRed,
                    modifier = Modifier.weight(1f),
                )
                if (isRunning) {
                    OutlinedButton(
                        onClick = onStop,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text("Stop", style = MaterialTheme.typography.labelSmall) }
                } else {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text("Start", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                }
            }

            // ── Boot autostart toggle ─────────────────────────────────────────
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        Modifier.size(16.dp),
                        tint = if (autostartEnabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Boot autostart",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Start the service automatically after reboot",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autostartEnabled,
                        onCheckedChange = onAutostartToggle,
                        thumbContent = if (autostartEnabled) {
                            { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }
                        } else null,
                    )
                }
            }

            if (isRunning) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("$appCount", "Connected", AccentGreen, Modifier.weight(1f))
                    StatChip("$pendingCount", "Pending", AccentOrange, Modifier.weight(1f))
                    StatChip("$callCount", "API Calls", AccentCyan, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Bind via: Intent(\"com.accu.api.AccuSystemService\").setPackage(\"com.accu.controlcenter\")",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(0.1f), modifier = modifier) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Apps tab ──────────────────────────────────────────────────────────────────

@Composable
private fun AppsTab(apps: List<AccuClientGrant>, viewModel: AccuServiceViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        if (apps.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Apps, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No connected apps", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Apps that bind to ACCU's service will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            return@LazyColumn
        }

        item {
            SectionHeaderWithInfo(
                title = "Connected Applications",
                infoTitle = "Connected Apps",
                infoDescription = "Apps that have been granted ACCU privileges.\nGreen = permission active. Grey = revoked.\n\nRevoke removes all privileges — the app must request again.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        items(apps, key = { it.packageName }) { grant ->
            ClientAppCard(grant = grant, onRevoke = { viewModel.revokeApp(grant.packageName) }, onDelete = { viewModel.deleteApp(grant.packageName) })
        }
    }
}

@Composable
private fun ClientAppCard(grant: AccuClientGrant, onRevoke: () -> Unit, onDelete: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(grant.appLabel, fontWeight = FontWeight.Medium)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (grant.isGranted) AccentGreen.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        if (grant.isGranted) "GRANTED" else "REVOKED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (grant.isGranted) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(grant.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (grant.isGranted && grant.grantedScopes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                        grant.grantedScopes.take(4).forEach { scope ->
                            Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(scope.take(4), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                            }
                        }
                        if (grant.grantedScopes.size > 4) Text("+${grant.grantedScopes.size - 4}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Granted: ${dateFmt.format(Date(grant.grantedAt))} · ${grant.callCount} calls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Android, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(showMenu, { showMenu = false }) {
                    if (grant.isGranted) {
                        DropdownMenuItem(
                            text = { Text("Revoke Access") },
                            leadingIcon = { Icon(Icons.Default.Block, null) },
                            onClick = { showMenu = false; onRevoke() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove Record", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        },
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

// ── Pending tab ───────────────────────────────────────────────────────────────

@Composable
private fun PendingTab(pending: List<AccuSystemService.PendingPermRequest>, viewModel: AccuServiceViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (pending.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(56.dp), tint = AccentGreen)
                        Text("No pending requests", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("New permission requests from apps will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            return@LazyColumn
        }
        items(pending, key = { it.packageName }) { req ->
            PendingCard(req, onGrant = { viewModel.grantPending(req.packageName) }, onDeny = { viewModel.denyPending(req.packageName) })
        }
    }
}

@Composable
private fun PendingCard(req: AccuSystemService.PendingPermRequest, onGrant: () -> Unit, onDeny: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(0.08f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = AccentOrange.copy(0.15f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Android, null, tint = AccentOrange, modifier = Modifier.size(20.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(req.appLabel, fontWeight = FontWeight.Bold)
                    Text(req.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)) {
                    Icon(Icons.Default.Block, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Deny")
                }
                Button(onClick = onGrant, modifier = Modifier.weight(2f), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Grant Full Access", color = Color.White)
                }
            }
        }
    }
}

// ── Quick ref ─────────────────────────────────────────────────────────────────

@Composable
private fun DocsQuickRef(onNavigateToDocs: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Quick Reference", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("See full docs for all methods, Gradle setup, and error handling.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onNavigateToDocs, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.MenuBook, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Full SDK Documentation")
            }
        }
        item { CodeCard("Binding action", "com.accu.api.AccuSystemService", clipboard) }
        item { CodeCard("Package", "com.accu.controlcenter", clipboard) }
        item {
            CodeCard(
                "Minimal connect snippet",
                """val intent = Intent("com.accu.api.AccuSystemService")
    .setPackage("com.accu.controlcenter")
context.bindService(intent, connection, BIND_AUTO_CREATE)

// In onServiceConnected:
val accu = IAccuService.Stub.asInterface(binder)
accu.requestPermission(callback)""",
                clipboard,
            )
        }
        item {
            CodeCard(
                "Run a shell command",
                """val result = accu.exec("pm list packages -3")
val stdout   = result[0]   // standard output
val stderr   = result[1]   // error output
val exitCode = result[2]   // "0" = success""",
                clipboard,
            )
        }
    }
}

@Composable
private fun CodeCard(label: String, code: String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            }
        }
    }
}
