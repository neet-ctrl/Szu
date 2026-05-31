package com.accu.ui.crash

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.data.db.entities.CrashEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashDetailScreen(
    crashId: String,
    onBack: () -> Unit,
    viewModel: CrashDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var showExportSheet by remember { mutableStateOf(false) }

    LaunchedEffect(crashId) { viewModel.load(crashId) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.clearSnackbar() }
    }

    LaunchedEffect(state.shareIntent) {
        state.shareIntent?.let {
            context.startActivity(Intent.createChooser(it, "Share Crash Report"))
            viewModel.clearShareIntent()
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::hideDeleteConfirm,
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete This Crash?") },
            text = { Text("This crash record will be permanently removed.") },
            confirmButton = {
                Button(onClick = { viewModel.delete(onBack) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = viewModel::hideDeleteConfirm) { Text("Cancel") } },
        )
    }

    if (state.showNotesDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideNotesDialog,
            title = { Text("Add Notes") },
            text = {
                OutlinedTextField(
                    value = state.notesInput,
                    onValueChange = viewModel::setNotesInput,
                    placeholder = { Text("Your notes about this crash…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { Button(onClick = viewModel::saveNotes) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::hideNotesDialog) { Text("Cancel") } },
        )
    }

    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("Export Crash Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                ExportOption("Plain Text (.txt)", Icons.Default.TextSnippet) { viewModel.exportTxt(); showExportSheet = false }
                ExportOption("JSON (.json)", Icons.Default.DataObject) { viewModel.exportJson(); showExportSheet = false }
                ExportOption("Markdown (.md)", Icons.Default.Article) { viewModel.exportMarkdown(); showExportSheet = false }
                ExportOption("HTML Report (.html)", Icons.Default.Code) { viewModel.exportHtml(); showExportSheet = false }
                ExportOption("ZIP Archive (all formats)", Icons.Default.FolderZip) { viewModel.exportZip(); showExportSheet = false }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Crash Details", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    state.crash?.let { crash ->
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(if (crash.isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (crash.isFavorited) Color(0xFFFF6B6B) else LocalContentColor.current)
                        }
                        IconButton(onClick = { viewModel.togglePin() }) {
                            Icon(Icons.Default.PushPin, null, tint = if (crash.isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        IconButton(onClick = { showExportSheet = true }) { Icon(Icons.Default.FileUpload, "Export") }
                        IconButton(onClick = viewModel::showDeleteConfirm) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.crash == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Crash not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> CrashDetailContent(
                crash = state.crash!!,
                isExporting = state.isExporting,
                onExport = { showExportSheet = true },
                onNotes = viewModel::showNotesDialog,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun CrashDetailContent(
    crash: CrashEntity,
    isExporting: Boolean,
    onExport: () -> Unit,
    onNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM dd yyyy  HH:mm:ss.SSS", Locale.US) }
    val riskColor = crash.riskLevel.riskColor()
    var showFullTrace by remember { mutableStateOf(false) }
    var showUserActions by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // ── Risk + Classification header ──
        Surface(
            color = riskColor.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = riskColor.copy(alpha = 0.2f), modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.BugReport, null, tint = riskColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column {
                        Text(crash.exceptionType.substringAfterLast('.'), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text(dateFormat.format(Date(crash.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailBadge(crash.riskLevel, riskColor)
                    DetailBadge(crash.crashKind, Color(0xFF6B9FFF))
                    if (crash.isFatal) DetailBadge("FATAL", Color(0xFFFF4444))
                    if (crash.isAnr) DetailBadge("ANR", Color(0xFFFF8800))
                }
                if (crash.exceptionMessage.isNotBlank()) {
                    Text(crash.exceptionMessage.take(300), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Export/Notes row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                        if (isExporting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.FileUpload, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onNotes, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.EditNote, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Notes", fontSize = 13.sp)
                    }
                }
                if (crash.userNotes.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Note, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Text(crash.userNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        // ── Analysis ──
        DetailSection("Analysis") {
            AnalysisRow(Icons.Default.Psychology, "Possible Cause", crash.possibleCause, riskColor)
            HorizontalDivider()
            AnalysisRow(Icons.Default.Extension, "Affected Module", crash.affectedModule, MaterialTheme.colorScheme.secondary)
            HorizontalDivider()
            AnalysisRow(Icons.Default.Build, "Suggested Fix", crash.suggestedFix, MaterialTheme.colorScheme.primary)
            if (crash.similarCrashCount > 0) {
                HorizontalDivider()
                InfoDetailRow("Similar Crashes", "${crash.similarCrashCount} previous occurrences of this exception type")
            }
        }

        // ── Exception tree ──
        DetailSection("Exception") {
            Text(
                "${crash.exceptionType}\n${crash.exceptionMessage}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(12.dp),
            )
            if (crash.causeChain.isNotBlank() && crash.causeChain != "[]") {
                HorizontalDivider()
                Column(Modifier.padding(12.dp)) {
                    Text("Cause chain:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    Text(crash.causeChain, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Stack Trace ──
        DetailSection("Stack Trace") {
            val lines = crash.stackTrace.lines()
            val displayed = if (showFullTrace) crash.stackTrace else lines.take(25).joinToString("\n")
            Text(
                displayed,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
            )
            if (lines.size > 25) {
                TextButton(onClick = { showFullTrace = !showFullTrace }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showFullTrace) "Collapse" else "Show all ${lines.size} lines", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── Device ──
        DetailSection("Device") {
            InfoDetailRow("Model", "${crash.deviceManufacturer} ${crash.deviceModel}")
            HorizontalDivider()
            InfoDetailRow("Android", "${crash.androidVersion} (API ${crash.sdkInt})")
            HorizontalDivider()
            InfoDetailRow("ABI", crash.abi)
            HorizontalDivider()
            InfoDetailRow("RAM", "${crash.freeRamMb} MB free / ${crash.totalRamMb} MB total")
            HorizontalDivider()
            InfoDetailRow("CPU Usage", "${crash.cpuUsagePct.toInt()}%")
            HorizontalDivider()
            InfoDetailRow("Battery", "${crash.batteryPct}%${if (crash.batteryCharging) " (charging)" else ""}")
            HorizontalDivider()
            InfoDetailRow("Low Memory", crash.isLowMemory.toString())
        }

        // ── Process ──
        DetailSection("Process") {
            InfoDetailRow("Process", "${crash.processName} (PID ${crash.processId})")
            HorizontalDivider()
            InfoDetailRow("Thread", "${crash.threadName} (TID ${crash.threadId})")
        }

        // ── App Context ──
        DetailSection("App Context at Crash") {
            InfoDetailRow("App Version", "${crash.appVersion} (${crash.buildVersionCode}) [${crash.buildType}]")
            HorizontalDivider()
            InfoDetailRow("Activity", crash.activityName)
            HorizontalDivider()
            InfoDetailRow("Route", crash.screenRoute)
            if (crash.fragmentName.isNotBlank()) { HorizontalDivider(); InfoDetailRow("Fragment", crash.fragmentName) }
            if (crash.serviceName.isNotBlank()) { HorizontalDivider(); InfoDetailRow("Service", crash.serviceName) }
            if (crash.viewModelName.isNotBlank()) { HorizontalDivider(); InfoDetailRow("ViewModel", crash.viewModelName) }
            HorizontalDivider()
            InfoDetailRow("Session", crash.sessionId.take(16) + "…")
            HorizontalDivider()
            InfoDetailRow("Session Duration", "${crash.sessionDurationSec}s")
        }

        // ── System State ──
        DetailSection("System State") {
            InfoDetailRow("Network", crash.networkState)
            HorizontalDivider()
            InfoDetailRow("Shizuku", crash.shizukuState)
            HorizontalDivider()
            InfoDetailRow("Root", crash.rootState)
            HorizontalDivider()
            InfoDetailRow("Wireless ADB", crash.wirelessAdbState)
        }

        // ── User actions ──
        if (crash.userActionsJson.isNotBlank() && crash.userActionsJson != "[]") {
            DetailSection("User Actions Before Crash (most recent first)") {
                Row(
                    Modifier.fillMaxWidth().clickable { showUserActions = !showUserActions }.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent navigation & actions", style = MaterialTheme.typography.bodyMedium)
                    Icon(if (showUserActions) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                AnimatedVisibility(visible = showUserActions) {
                    Text(
                        crash.userActionsJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun InfoDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f), softWrap = true)
    }
}

@Composable
private fun AnalysisRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, iconTint: Color) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DetailBadge(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
private fun ExportOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun String.riskColor(): Color = when (this) {
    "CRITICAL" -> Color(0xFFFF4444); "HIGH" -> Color(0xFFFF8800)
    "MEDIUM" -> Color(0xFFFFD700); else -> Color(0xFF81C995)
}
