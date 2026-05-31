package com.accu.ui.crash

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.data.db.entities.CrashEntity
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.FeatureRow
import com.accu.ui.components.FeatureSwitch
import com.accu.ui.theme.GlassSurface
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashCenterScreen(
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit,
    viewModel: CrashCenterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSafeMode by viewModel.isSafeModeEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.exportMessage) {
        if (state.exportMessage != null) viewModel.clearExportMessage()
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete All Crash Logs?") },
            text = { Text("This permanently deletes all ${state.totalCrashes} crash records. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAll(); showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Crash Center",
                onBack = onBack,
                actions = {
                    if (state.totalCrashes > 0) {
                        IconButton(onClick = {
                            viewModel.exportAllAsZip { intent ->
                                context.startActivity(Intent.createChooser(intent, "Export Crash Logs"))
                            }
                        }) { Icon(Icons.Default.FileUpload, "Export All") }
                    }
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, "Clear All")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Stats dashboard ──
            item {
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(
                            MaterialTheme.colorScheme.errorContainer.copy(0.5f),
                            MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
                        ))
                    ).padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.BugReport, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.error)
                            Column {
                                Text("Crash Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text("${state.totalCrashes} total crash records", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Stat cards row 1
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("Today", state.todayCrashes.toString(), Icons.Default.Today, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            StatCard("This Week", state.weekCrashes.toString(), Icons.Default.DateRange, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                            StatCard("Total", state.totalCrashes.toString(), Icons.Default.Storage, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                        }
                        // Stat cards row 2
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("Fatal", state.fatalCrashes.toString(), Icons.Default.Error, Color(0xFFFF4444), Modifier.weight(1f))
                            StatCard("ANRs", state.anrCrashes.toString(), Icons.Default.HourglassEmpty, Color(0xFFFF8800), Modifier.weight(1f))
                            StatCard("Critical", state.criticalCrashes.toString(), Icons.Default.Warning, Color(0xFFFFD700), Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Safe Mode ──
            item { SectionHeader("Recovery") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FeatureSwitch(
                        title = "Safe Mode",
                        subtitle = if (isSafeMode)
                            "Active — experimental modules, automation, overlays disabled"
                        else
                            "Disable experimental modules if crashes persist",
                        checked = isSafeMode,
                        onCheckedChange = { if (it) viewModel.enableSafeMode() else viewModel.disableSafeMode() },
                        leadingIcon = { Icon(Icons.Default.Shield, null, tint = if (isSafeMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    )
                    if (isSafeMode) {
                        HorizontalDivider()
                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(0.4f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Disabled in safe mode:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text("Automation • Key Mapper • Overlays • Startup services • Shell QS Tiles • Liveprog", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ── Navigation ──
            item { SectionHeader("Crash History") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FeatureRow(
                        title = "View All Crashes",
                        subtitle = "${state.totalCrashes} records — search, filter, export",
                        leadingIcon = { Icon(Icons.Default.History, null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        onClick = onNavigateToHistory,
                    )
                    HorizontalDivider()
                    FeatureRow(
                        title = "Favorites",
                        subtitle = "Pinned & starred crash records",
                        leadingIcon = { Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF6B6B)) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        onClick = onNavigateToHistory,
                    )
                }
            }

            // ── Recent crashes ──
            if (state.recentCrashes.isNotEmpty()) {
                item { SectionHeader("Recent Crashes") }
                items(state.recentCrashes) { crash ->
                    RecentCrashCard(crash, onClick = {
                        onNavigateToHistory()
                    })
                }
            }

            // ── Empty state ──
            if (state.totalCrashes == 0) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = Color(0xFF81C995))
                        Text("No crashes recorded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("ACCU is running cleanly. Any future crashes will appear here instantly.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Export section ──
            if (state.totalCrashes > 0) {
                item { SectionHeader("Export") }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        FeatureRow(
                            title = "Export All as ZIP",
                            subtitle = "TXT + JSON + Markdown for all crashes",
                            leadingIcon = { Icon(Icons.Default.FolderZip, null) },
                            trailingContent = {
                                if (state.isExporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.FileUpload, null)
                            },
                            onClick = {
                                viewModel.exportAllAsZip { intent ->
                                    context.startActivity(Intent.createChooser(intent, "Export Crash Logs"))
                                }
                            },
                        )
                        HorizontalDivider()
                        FeatureRow(
                            title = "Clear All Crash Logs",
                            subtitle = "Permanently delete all ${state.totalCrashes} records",
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            onClick = { showClearConfirm = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, glassColor = color.copy(alpha = 0.1f), borderColor = color.copy(alpha = 0.3f)) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentCrashCard(crash: CrashEntity, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd HH:mm", Locale.US) }
    val riskColor = when (crash.riskLevel) {
        "CRITICAL" -> Color(0xFFFF4444)
        "HIGH"     -> Color(0xFFFF8800)
        "MEDIUM"   -> Color(0xFFFFD700)
        else       -> Color(0xFF81C995)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = riskColor.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.BugReport, null, tint = riskColor, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(crash.exceptionType.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(crash.exceptionMessage.take(60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = RoundedCornerShape(4.dp), color = riskColor.copy(alpha = 0.15f)) {
                    Text(crash.riskLevel, style = MaterialTheme.typography.labelSmall, color = riskColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(dateFormat.format(Date(crash.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}
