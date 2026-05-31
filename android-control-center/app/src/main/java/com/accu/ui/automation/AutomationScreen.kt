package com.accu.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.data.db.dao.AutomationProfileDao
import com.accu.data.db.dao.KeyMappingDao
import com.accu.data.db.entities.AutomationProfileEntity
import com.accu.data.db.entities.KeyMappingEntity
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.components.InfoTooltipIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutomationState(
    val keyMappings: List<KeyMappingEntity> = emptyList(),
    val automationProfiles: List<AutomationProfileEntity> = emptyList(),
    val selectedTab: AutomationTab = AutomationTab.KEY_MAPPINGS,
    val serviceRunning: Boolean = false,
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null,
)
enum class AutomationTab { KEY_MAPPINGS, AUTOMATION }

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val keyMappingDao: KeyMappingDao,
    private val automationProfileDao: AutomationProfileDao,
) : ViewModel() {
    private val _state = MutableStateFlow(AutomationState())
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            keyMappingDao.observeAll().collect { mappings -> _state.update { it.copy(keyMappings = mappings, isLoading = false) } }
        }
        viewModelScope.launch {
            automationProfileDao.observeAll().collect { profiles -> _state.update { it.copy(automationProfiles = profiles) } }
        }
    }

    fun toggleMapping(id: Long, enabled: Boolean) { viewModelScope.launch { keyMappingDao.setEnabled(id, enabled) } }
    fun deleteMapping(mapping: KeyMappingEntity) { viewModelScope.launch { keyMappingDao.delete(mapping) } }
    fun insertSampleMapping() {
        viewModelScope.launch {
            keyMappingDao.insert(KeyMappingEntity(name = "Volume Up → Next Track", triggerJson = """{"keyCode":24}""", actionsJson = """[{"type":"MEDIA_NEXT_TRACK"}]""", isLongPress = true))
            _state.update { it.copy(snackbarMessage = "Sample mapping added") }
        }
    }
    fun toggleProfile(id: Long, enabled: Boolean) { viewModelScope.launch { automationProfileDao.update(automationProfileDao.observeAll().first().first { it.id == id }.copy(isEnabled = enabled)) } }
    fun deleteProfile(profile: AutomationProfileEntity) { viewModelScope.launch { automationProfileDao.delete(profile) } }
    fun insertSampleProfile() {
        viewModelScope.launch {
            automationProfileDao.insert(AutomationProfileEntity(name = "Auto-freeze on screen off", triggerType = "screen", triggerJson = """{"event":"off"}""", actionsJson = """[{"type":"FREEZE_ALL_LISTED"}]"""))
            _state.update { it.copy(snackbarMessage = "Sample automation added") }
        }
    }
    fun onTabChange(tab: AutomationTab) { _state.update { it.copy(selectedTab = tab) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onBack: () -> Unit,
    onNavigateToKeyMapList: () -> Unit = {},
    onNavigateToKeyMapperSettings: () -> Unit = {},
    onNavigateToAdvanced: () -> Unit = {},
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() } }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = if (state.selectedTab == AutomationTab.KEY_MAPPINGS) "Key Mapper" else "Automation",
                onBack = onBack,
                actions = {
                    if (state.selectedTab == AutomationTab.KEY_MAPPINGS) {
                        InfoTooltipIcon(
                            title = "Key Mapper",
                            description = "Remap any physical key or button to any action.\n\nBased on Key Mapper:\n• Remap volume buttons, power button, headphone buttons\n• Triggers: single press, long press, double press\n• Actions: launch app, toggle setting, run shell command, simulate key, etc.\n• Constraints: only active while screen on/off, specific app in foreground, etc.\n\nRequires Accessibility Service to be enabled."
                        )
                        IconButton(onClick = onNavigateToKeyMapList) { Icon(Icons.Default.List, "Key Map List") }
                        IconButton(onClick = onNavigateToKeyMapperSettings) { Icon(Icons.Default.Settings, "Settings") }
                        IconButton(onClick = onNavigateToAdvanced) { Icon(Icons.Default.Tune, "Advanced") }
                    } else {
                        InfoTooltipIcon(
                            title = "Automation",
                            description = "Create automation profiles that run actions based on triggers and constraints.\n\n• Triggers: time, location, charging state, screen state, app launch\n• Actions: run shell command, toggle setting, freeze/unfreeze app, send notification\n• Constraints: battery level, Wi-Fi connected, time range\n\nAll automation runs locally on-device, no cloud connection."
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (state.selectedTab == AutomationTab.KEY_MAPPINGS) viewModel.insertSampleMapping() else viewModel.insertSampleProfile() }) {
                Icon(Icons.Default.Add, "Add")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = AutomationTab.entries.indexOf(state.selectedTab)) {
                AutomationTab.entries.forEach { tab ->
                    Tab(selected = state.selectedTab == tab, onClick = { viewModel.onTabChange(tab) }, text = { Text(if (tab == AutomationTab.KEY_MAPPINGS) "Key Mapper" else "Automation") })
                }
            }

            when (state.selectedTab) {
                AutomationTab.KEY_MAPPINGS -> KeyMappingsTab(state = state, viewModel = viewModel, onNavigateToKeyMapList = onNavigateToKeyMapList)
                AutomationTab.AUTOMATION   -> AutomationProfilesTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun KeyMappingsTab(state: AutomationState, viewModel: AutomationViewModel, onNavigateToKeyMapList: () -> Unit = {}) {
    if (state.keyMappings.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Keyboard,
            title = "No Key Mappings",
            subtitle = "Tap + to add a key mapping. Map volume buttons, power button, and more to custom actions.",
            action = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { viewModel.insertSampleMapping() }) { Text("Add Sample Mapping") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onNavigateToKeyMapList) { Icon(Icons.Default.List, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Open Key Map List") }
                }
            },
        )
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.keyMappings, key = { it.id }) { mapping ->
                ListItem(
                    headlineContent = { Text(mapping.name, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Column {
                            Text("Trigger: ${mapping.triggerJson}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (mapping.isLongPress) AssistChip(onClick = {}, label = { Text("Long press", fontSize = 10.sp) }, Modifier.height(24.dp))
                                if (mapping.doublePressEnabled) AssistChip(onClick = {}, label = { Text("Double press", fontSize = 10.sp) }, Modifier.height(24.dp))
                            }
                        }
                    },
                    leadingContent = {
                        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (mapping.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Keyboard, null, Modifier.size(18.dp)) }
                        }
                    },
                    trailingContent = {
                        Row {
                            Switch(checked = mapping.isEnabled, onCheckedChange = { viewModel.toggleMapping(mapping.id, it) })
                            IconButton(onClick = { viewModel.deleteMapping(mapping) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AutomationProfilesTab(state: AutomationState, viewModel: AutomationViewModel) {
    if (state.automationProfiles.isEmpty()) {
        EmptyState(
            icon = Icons.Default.AutoAwesome,
            title = "No Automation Profiles",
            subtitle = "Create automations triggered by events like screen off, battery level, app launch, and more.",
            action = { Button(onClick = { viewModel.insertSampleProfile() }) { Text("Add Sample Profile") } },
        )
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.automationProfiles, key = { it.id }) { profile ->
                ListItem(
                    headlineContent = { Text(profile.name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("Trigger: ${profile.triggerType} · ${if (profile.isEnabled) "Enabled" else "Disabled"}", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = {
                        Icon(when(profile.triggerType) {
                            "screen" -> Icons.Default.PhoneAndroid
                            "time"   -> Icons.Default.Schedule
                            "network"-> Icons.Default.Wifi
                            "battery"-> Icons.Default.BatteryChargingFull
                            "boot"   -> Icons.Default.PowerSettingsNew
                            else     -> Icons.Default.AutoAwesome
                        }, null)
                    },
                    trailingContent = {
                        Row {
                            Switch(checked = profile.isEnabled, onCheckedChange = { viewModel.toggleProfile(profile.id, it) })
                            IconButton(onClick = { viewModel.deleteProfile(profile) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
