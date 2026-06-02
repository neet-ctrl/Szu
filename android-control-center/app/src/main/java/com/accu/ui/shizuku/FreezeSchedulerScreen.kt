package com.accu.ui.shizuku

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.ui.components.InfoTooltipIcon
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────

data class FreezeSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val action: FreezeAction,
    val triggerType: FreezeTrigger,
    val hour: Int = 22,
    val minute: Int = 0,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon..7=Sun
    val packages: List<String> = emptyList(),
    val freezeAll: Boolean = true,
    val isEnabled: Boolean = true,
)

enum class FreezeWorkingMode(val label: String, val subtitle: String) {
    SHIZUKU_SUSPEND("ACCU — Suspend", "pm suspend (app appears greyed out, no background)"),
    SHIZUKU_DISABLE("ACCU — Disable", "pm disable-user (app hidden from launcher)"),
    SHIZUKU_HIDE("ACCU — Hide", "pm hide (requires device admin or deeper privs)"),
    ROOT_PM("Root — pm disable", "Root pm disable/enable (permanent disable)"),
    DEVICE_OWNER("Device Owner", "DPM setPackagesSuspended (MDM method)"),
}

enum class FreezeAction(val label: String, val icon: ImageVector, val color: Color) {
    FREEZE("Freeze Apps",        Icons.Default.AcUnit,      Color(0xFF1E88E5)),
    UNFREEZE("Unfreeze Apps",    Icons.Default.Whatshot,    Color(0xFFFF8F00)),
    FREEZE_AND_KILL("Freeze + Kill", Icons.Default.Stop,   Color(0xFFE53935)),
}

enum class FreezeTrigger(val label: String, val icon: ImageVector) {
    TIME("Scheduled Time",       Icons.Default.Schedule),
    SCREEN_OFF("Screen Off",     Icons.Default.ScreenLockPortrait),
    SCREEN_ON("Screen On",       Icons.Default.PhoneAndroid),
    CHARGING("On Charging",      Icons.Default.BatteryChargingFull),
    UNPLUGGED("On Unplug",       Icons.Default.Battery3Bar),
    BOOT("On Boot",              Icons.Default.SystemUpdate),
    AIRPLANE_ON("Airplane Mode On", Icons.Default.AirplanemodeActive),
}

data class FreezeSchedulerState(
    val schedules: List<FreezeSchedule> = defaultSchedules(),
    val installedPackages: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val selectedSchedule: FreezeSchedule? = null,
    val showEditor: Boolean = false,
    val snackbarMessage: String? = null,
    val workingMode: FreezeWorkingMode = FreezeWorkingMode.SHIZUKU_SUSPEND,
    val skipForegroundApp: Boolean = true,
    val grayscaleIcons: Boolean = false,
    val showWorkingModeSheet: Boolean = false,
)

fun defaultSchedules() = listOf(
    FreezeSchedule(
        name = "Bedtime Freeze",
        description = "Freeze all user apps at 11 PM",
        action = FreezeAction.FREEZE,
        triggerType = FreezeTrigger.TIME,
        hour = 23, minute = 0,
        daysOfWeek = setOf(1,2,3,4,5,6,7),
        freezeAll = true,
        isEnabled = true,
    ),
    FreezeSchedule(
        name = "Screen-Off Auto Freeze",
        description = "Freeze background apps when screen turns off",
        action = FreezeAction.FREEZE,
        triggerType = FreezeTrigger.SCREEN_OFF,
        freezeAll = true,
        isEnabled = true,
    ),
    FreezeSchedule(
        name = "Morning Unfreeze",
        description = "Unfreeze all apps at 7 AM on weekdays",
        action = FreezeAction.UNFREEZE,
        triggerType = FreezeTrigger.TIME,
        hour = 7, minute = 0,
        daysOfWeek = setOf(1,2,3,4,5),
        freezeAll = true,
        isEnabled = true,
    ),
    FreezeSchedule(
        name = "Boot Unfreeze",
        description = "Unfreeze all apps after device restart",
        action = FreezeAction.UNFREEZE,
        triggerType = FreezeTrigger.BOOT,
        freezeAll = true,
        isEnabled = true,
    ),
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class FreezeSchedulerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {

    private val _state = MutableStateFlow(FreezeSchedulerState())
    val state: StateFlow<FreezeSchedulerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val pm = context.packageManager
            val pkgs = pm.getInstalledPackages(0).map { it.packageName }.sorted()
            _state.update { it.copy(installedPackages = pkgs) }
        }
    }

    fun toggleSchedule(id: String) = _state.update { s ->
        s.copy(schedules = s.schedules.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it },
            snackbarMessage = "Schedule ${if (s.schedules.find { it.id == id }?.isEnabled == false) "enabled" else "disabled"}")
    }

    fun deleteSchedule(id: String) = _state.update { s ->
        s.copy(schedules = s.schedules.filter { it.id != id }, snackbarMessage = "Schedule deleted")
    }

    fun saveSchedule(schedule: FreezeSchedule) {
        val existing = _state.value.schedules.indexOfFirst { it.id == schedule.id }
        _state.update { s ->
            val list = if (existing >= 0) s.schedules.toMutableList().also { it[existing] = schedule }
                       else s.schedules + schedule
            s.copy(schedules = list, showEditor = false, snackbarMessage = "Schedule saved")
        }
    }

    fun runNow(schedule: FreezeSchedule) {
        viewModelScope.launch {
            val targets = if (schedule.freezeAll) {
                val pm = context.packageManager
                pm.getInstalledPackages(0).filter { pi ->
                    val ai = pi.applicationInfo
                    ai != null && (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                }.map { it.packageName }
            } else schedule.packages

            var count = 0
            targets.forEach { pkg ->
                val cmd = when (schedule.action) {
                    FreezeAction.FREEZE, FreezeAction.FREEZE_AND_KILL -> "pm suspend --user 0 $pkg"
                    FreezeAction.UNFREEZE -> "pm unsuspend --user 0 $pkg"
                }
                try { shizukuUtils.execShizuku(cmd); count++ } catch (_: Exception) {}
                if (schedule.action == FreezeAction.FREEZE_AND_KILL) {
                    try { shizukuUtils.execShizuku("am force-stop $pkg") } catch (_: Exception) {}
                }
            }
            _state.update { it.copy(snackbarMessage = "${schedule.action.label}: $count apps") }
        }
    }

    fun openEditor(schedule: FreezeSchedule? = null) = _state.update {
        it.copy(selectedSchedule = schedule ?: FreezeSchedule(name = "New Schedule", action = FreezeAction.FREEZE, triggerType = FreezeTrigger.SCREEN_OFF), showEditor = true)
    }

    fun closeEditor() = _state.update { it.copy(showEditor = false, selectedSchedule = null) }
    fun clearSnackbar() = _state.update { it.copy(snackbarMessage = null) }
    fun setWorkingMode(mode: FreezeWorkingMode) = _state.update { it.copy(workingMode = mode, showWorkingModeSheet = false, snackbarMessage = "Working mode: ${mode.label}") }
    fun toggleSkipForeground() = _state.update { it.copy(skipForegroundApp = !it.skipForegroundApp) }
    fun toggleGrayscaleIcons() = _state.update { it.copy(grayscaleIcons = !it.grayscaleIcons) }
    fun openWorkingModeSheet() = _state.update { it.copy(showWorkingModeSheet = true) }
    fun closeWorkingModeSheet() = _state.update { it.copy(showWorkingModeSheet = false) }
}

// ─────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezeSchedulerScreen(
    onBack: () -> Unit,
    viewModel: FreezeSchedulerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Freeze Scheduler", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    InfoTooltipIcon(
                        title = "Freeze Scheduler — Hail",
                        description = "Hail's time-based and trigger-based freeze scheduler.\n\nTriggers:\n• Scheduled time: set hour/minute + days of week\n• Screen off/on: instant trigger when display changes\n• Boot: run once on device restart\n• Charging/unplug events\n• Airplane mode\n\nActions:\n• Freeze (pm suspend) — app still appears, just suspended\n• Unfreeze (pm unsuspend) — restore suspended apps\n• Freeze + Kill — suspend and force-stop for max savings\n\nRequires ACCU to execute. 'Run Now' executes immediately."
                    )
                    IconButton(onClick = { viewModel.openEditor() }) { Icon(Icons.Default.Add, "Add schedule") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openEditor() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Schedule") },
                expanded = state.schedules.isEmpty(),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Info card
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.5f))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Column {
                            Text("Auto Freeze Scheduler", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${state.schedules.count { it.isEnabled }} active schedules · Hail feature",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Working mode card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::openWorkingModeSheet,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Working Mode", fontWeight = FontWeight.SemiBold)
                                Text(state.workingMode.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text(state.workingMode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        // Skip foreground & grayscale toggles
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Skip foreground app", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("Never freeze the currently active app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = state.skipForegroundApp, onCheckedChange = { viewModel.toggleSkipForeground() }, modifier = Modifier.height(24.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tonality, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Grayscale frozen app icons", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("Show greyed-out icons for suspended apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = state.grayscaleIcons, onCheckedChange = { viewModel.toggleGrayscaleIcons() }, modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }

            // Schedules
            items(state.schedules, key = { it.id }) { schedule ->
                FreezeScheduleCard(
                    schedule = schedule,
                    onToggle = { viewModel.toggleSchedule(schedule.id) },
                    onEdit = { viewModel.openEditor(schedule) },
                    onDelete = { viewModel.deleteSchedule(schedule.id) },
                    onRunNow = { viewModel.runNow(schedule) },
                )
            }

            if (state.schedules.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                            Text("No schedules yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { viewModel.openEditor() }) { Text("Create First Schedule") }
                        }
                    }
                }
            }
        }
    }

    // Working mode bottom sheet
    if (state.showWorkingModeSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::closeWorkingModeSheet) {
            Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Freeze Working Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Choose how ACCU freezes apps. Each method has different privilege requirements and behavior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FreezeWorkingMode.entries.forEach { mode ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.setWorkingMode(mode) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.workingMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioButton(selected = state.workingMode == mode, onClick = { viewModel.setWorkingMode(mode) })
                            Column(Modifier.weight(1f)) {
                                Text(mode.label, fontWeight = FontWeight.SemiBold)
                                Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Editor bottom sheet
    if (state.showEditor && state.selectedSchedule != null) {
        FreezeScheduleEditorSheet(
            initial = state.selectedSchedule!!,
            onSave = viewModel::saveSchedule,
            onDismiss = viewModel::closeEditor,
        )
    }
}

@Composable
private fun FreezeScheduleCard(
    schedule: FreezeSchedule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRunNow: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val action = schedule.action
    val trigger = schedule.triggerType

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.isEnabled) action.color.copy(0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Action icon
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(action.color.copy(0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(action.icon, null, tint = action.color, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(schedule.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(trigger.icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            buildTriggerLabel(schedule),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = schedule.isEnabled, onCheckedChange = { onToggle() })
            }

            if (schedule.description.isNotBlank()) {
                Text(schedule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }

            // Tags row
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ScheduleChip(action.label, action.color)
                if (schedule.freezeAll) ScheduleChip("All Apps", MaterialTheme.colorScheme.secondary)
                else ScheduleChip("${schedule.packages.size} apps", MaterialTheme.colorScheme.tertiary)
                if (schedule.triggerType == FreezeTrigger.TIME) {
                    ScheduleChip("${schedule.daysOfWeek.size} days/week", MaterialTheme.colorScheme.primary)
                }
            }

            // Actions
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = onRunNow,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run Now", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ScheduleChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.15f)) {
        Text(label, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun buildTriggerLabel(schedule: FreezeSchedule): String = when (schedule.triggerType) {
    FreezeTrigger.TIME -> {
        val h = schedule.hour.toString().padStart(2, '0')
        val m = schedule.minute.toString().padStart(2, '0')
        val days = schedule.daysOfWeek
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dayStr = if (days.size == 7) "Every day" else days.sorted().joinToString(",") { dayNames.getOrElse(it - 1) { "?" } }
        "$h:$m · $dayStr"
    }
    FreezeTrigger.SCREEN_OFF -> "When screen turns off"
    FreezeTrigger.SCREEN_ON  -> "When screen turns on"
    FreezeTrigger.CHARGING   -> "When charging starts"
    FreezeTrigger.UNPLUGGED  -> "When charger unplugged"
    FreezeTrigger.BOOT       -> "On device boot"
    FreezeTrigger.AIRPLANE_ON -> "On airplane mode"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreezeScheduleEditorSheet(
    initial: FreezeSchedule,
    onSave: (FreezeSchedule) -> Unit,
    onDismiss: () -> Unit,
) {
    var schedule by remember { mutableStateOf(initial) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = schedule.hour, initialMinute = schedule.minute, is24Hour = true)
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Edit Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Name
            OutlinedTextField(value = schedule.name, onValueChange = { schedule = schedule.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            // Description
            OutlinedTextField(value = schedule.description, onValueChange = { schedule = schedule.copy(description = it) }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            // Trigger type
            Text("Trigger", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FreezeTrigger.entries) { t ->
                    FilterChip(
                        selected = schedule.triggerType == t,
                        onClick = { schedule = schedule.copy(triggerType = t) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(t.icon, null, Modifier.size(14.dp)) },
                    )
                }
            }

            // Time picker (only for TIME trigger)
            AnimatedVisibility(visible = schedule.triggerType == FreezeTrigger.TIME) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Time display + button
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Time: ${schedule.hour.toString().padStart(2,'0')}:${schedule.minute.toString().padStart(2,'0')}")
                    }
                    // Days of week
                    Text("Days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayNames.forEachIndexed { idx, day ->
                            val dayNum = idx + 1
                            val selected = dayNum in schedule.daysOfWeek
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    schedule = schedule.copy(daysOfWeek = if (selected) schedule.daysOfWeek - dayNum else schedule.daysOfWeek + dayNum)
                                },
                                label = { Text(day.take(2), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }

            // Action
            Text("Action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FreezeAction.entries.forEach { action ->
                    FilterChip(
                        selected = schedule.action == action,
                        onClick = { schedule = schedule.copy(action = action) },
                        label = { Text(action.label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(action.icon, null, Modifier.size(14.dp), tint = action.color) },
                    )
                }
            }

            // Target apps
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Target Apps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (schedule.freezeAll) "All user apps" else "${schedule.packages.size} selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = schedule.freezeAll, onCheckedChange = { schedule = schedule.copy(freezeAll = it) })
            }

            // Enable toggle
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Enable Schedule", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Switch(checked = schedule.isEnabled, onCheckedChange = { schedule = schedule.copy(isEnabled = it) })
            }

            // Save / Cancel
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onSave(schedule) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                Button(onClick = {
                    schedule = schedule.copy(hour = timePickerState.hour, minute = timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }
}
