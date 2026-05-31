package com.accu.ui.installer

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class InstallFlag(
    val id: String,
    val name: String,
    val description: String,
    val flagValue: Int,
    val sessionParam: String? = null,
    val dangerLevel: FlagDanger = FlagDanger.SAFE,
    val requiresElevation: Boolean = false,
    var enabled: Boolean = false,
)

enum class FlagDanger(val label: String, val color: @Composable () -> androidx.compose.ui.graphics.Color) {
    SAFE("Safe", { MaterialTheme.colorScheme.tertiary }),
    CAUTION("Caution", { MaterialTheme.colorScheme.primary }),
    ADVANCED("Advanced", { MaterialTheme.colorScheme.error }),
}

val ALL_INSTALL_FLAGS = listOf(
    InstallFlag("replace_existing", "Replace Existing App", "Replace existing installation if package is already installed (PackageInstaller.SessionParams.MODE_FULL_INSTALL)", 0x00000002, dangerLevel = FlagDanger.SAFE),
    InstallFlag("allow_downgrade", "Allow Version Downgrade", "Allow installing an older version than currently installed. Requires ADB/root.", 0x00000080, dangerLevel = FlagDanger.CAUTION, requiresElevation = true),
    InstallFlag("grant_permissions", "Grant All Runtime Permissions", "Automatically grant all requested runtime permissions upon install.", 0x00000100, dangerLevel = FlagDanger.CAUTION, requiresElevation = true),
    InstallFlag("dont_kill", "Don't Kill App", "Don't kill app on update — keeps the app running during installation", 0x00001000, dangerLevel = FlagDanger.ADVANCED, requiresElevation = true),
    InstallFlag("allow_test", "Allow Test APKs", "Allow installation of test-only APKs (android:testOnly=true)", 0x00000004, dangerLevel = FlagDanger.CAUTION),
    InstallFlag("bypass_target_sdk", "Bypass Low Target SDK Block", "Override Android's block on apps targeting very old SDK versions", 0, "bypass_sdk_check", dangerLevel = FlagDanger.ADVANCED, requiresElevation = true),
    InstallFlag("no_streaming", "Disable Streaming Install", "Force full download before install instead of streaming install", 0, "enable_incremental=false", dangerLevel = FlagDanger.SAFE),
    InstallFlag("force_full", "Force Full Install", "Force a full install even if partial would suffice", 0x00000040, dangerLevel = FlagDanger.SAFE),
    InstallFlag("forward_lock", "Forward Lock", "Install as forward-locked package (legacy, API < 29)", 0x00000001, dangerLevel = FlagDanger.ADVANCED),
    InstallFlag("install_from_adb", "Mark as ADB Install", "Sets INSTALL_FROM_ADB flag in PackageInstaller session", 0x00000020, dangerLevel = FlagDanger.SAFE),
    InstallFlag("inherit_existing", "Inherit Existing", "Inherit files not explicitly set in install session from existing install", 0x00000400, dangerLevel = FlagDanger.CAUTION),
    InstallFlag("virtual_preload", "Virtual Preload", "Mark as virtual preload — avoids certain new-install triggers", 0x00004000, dangerLevel = FlagDanger.ADVANCED, requiresElevation = true),
    InstallFlag("instant_app", "Install as Instant App", "Install as instant (lightweight) app", 0x00000800, dangerLevel = FlagDanger.ADVANCED),
    InstallFlag("enable_rollback", "Enable Rollback", "Enable rollback capability for this installation", 0x00002000, dangerLevel = FlagDanger.SAFE),
    InstallFlag("request_update_ownership", "Request Update Ownership", "Request ownership of future updates for this package", 0x08000000, dangerLevel = FlagDanger.SAFE),
)

data class SessionParam(
    val id: String,
    val name: String,
    val description: String,
    var value: String = "",
    val placeholder: String = "",
    val requiresElevation: Boolean = false,
)

val SESSION_PARAMS = listOf(
    SessionParam("installer_package", "Installer Package Name", "Set who appears as the installer of this app (e.g. com.android.vending)", placeholder = "com.android.vending"),
    SessionParam("originating_uri", "Originating URI", "Source URI for the install", placeholder = "https://example.com/app.apk"),
    SessionParam("referrer_uri", "Referrer URI", "Install referrer URI", placeholder = "https://example.com"),
    SessionParam("install_location", "Install Location", "0=auto, 1=internal, 2=external", "0", "0, 1, or 2"),
    SessionParam("size_bytes", "Reserved Size (bytes)", "Pre-reserve storage space for the install", placeholder = "10485760"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallFlagsScreen(onBack: () -> Unit) {
    val flags = remember { mutableStateListOf(*ALL_INSTALL_FLAGS.toTypedArray()) }
    val params = remember { mutableStateListOf(*SESSION_PARAMS.toTypedArray()) }
    var selectedTab by remember { mutableStateOf(0) }
    var showInfo by remember { mutableStateOf<InstallFlag?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Install Flags") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    val activeCount = flags.count { it.enabled }
                    if (activeCount > 0) {
                        Badge { Text("$activeCount") }
                    }
                    IconButton(onClick = { flags.replaceAll { it.copy(enabled = false) } }) { Icon(Icons.Default.ClearAll, "Reset all") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Install Flags") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Session Params") })
            }

            when (selectedTab) {
                0 -> {
                    val safeFlags = flags.filter { it.dangerLevel == FlagDanger.SAFE }
                    val cautionFlags = flags.filter { it.dangerLevel == FlagDanger.CAUTION }
                    val advancedFlags = flags.filter { it.dangerLevel == FlagDanger.ADVANCED }

                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        flagSection("Safe Flags", safeFlags, flags, { showInfo = it })
                        flagSection("Caution Flags", cautionFlags, flags, { showInfo = it })
                        flagSection("Advanced Flags", advancedFlags, flags, { showInfo = it })
                        item {
                            if (flags.any { it.enabled }) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Active Command Flags:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        val activeFlags = flags.filter { it.enabled }
                                        val total = activeFlags.fold(0) { acc, f -> acc or f.flagValue }
                                        Text("flags: 0x${"%08X".format(total)}", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(params) { idx, param ->
                            SessionParamCard(
                                param = param,
                                onValueChange = { params[idx] = param.copy(value = it) }
                            )
                        }
                    }
                }
            }
        }
    }

    showInfo?.let { flag ->
        AlertDialog(
            onDismissRequest = { showInfo = null },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text(flag.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(flag.description)
                    if (flag.requiresElevation) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text("Requires Shizuku or Root", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    Text("Flag value: 0x${"%08X".format(flag.flagValue)}", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = null }) { Text("Close") } }
        )
    }
}

private fun LazyListScope.flagSection(
    title: String,
    sectionFlags: List<InstallFlag>,
    allFlags: MutableList<InstallFlag>,
    onInfo: (InstallFlag) -> Unit,
) {
    item {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.primary)
    }
    items(sectionFlags, key = { it.id }) { flag ->
        val idx = allFlags.indexOfFirst { it.id == flag.id }
        FlagCard(flag = flag, onToggle = { if (idx != -1) allFlags[idx] = flag.copy(enabled = !flag.enabled) }, onInfo = { onInfo(flag) })
    }
}

@Composable
private fun FlagCard(flag: InstallFlag, onToggle: () -> Unit, onInfo: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (flag.enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(flag.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Surface(color = flag.dangerLevel.color().copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(flag.dangerLevel.label, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = flag.dangerLevel.color())
                    }
                    if (flag.requiresElevation) {
                        Icon(Icons.Default.Shield, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
                Text(flag.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) }
            Switch(checked = flag.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun SessionParamCard(param: SessionParam, onValueChange: (String) -> Unit) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(param.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(param.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = param.value,
                onValueChange = onValueChange,
                placeholder = { Text(param.placeholder, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}
