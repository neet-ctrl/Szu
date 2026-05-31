package com.accu.ui.shizuku

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class FreezeMethod(val label: String, val description: String, val requiresRoot: Boolean = false) {
    SHIZUKU_SUSPEND("Shizuku (pm suspend)", "Uses PackageManager.setPackagesSuspended() via Shizuku — most reliable"),
    SHIZUKU_DISABLE("Shizuku (pm disable)", "Uses setApplicationEnabledSetting via Shizuku — hides from launcher"),
    DEVICE_ADMIN("Device Admin (User Restriction)", "Uses DevicePolicyManager to restrict apps — works without root"),
    WORK_PROFILE("Island/Work Profile", "Moves app to work profile and freezes — requires Island or similar"),
    ROOT_DISABLE("Root (setprop / disable)", "Direct root shell to disable packages", requiresRoot = true),
    DHIZUKU("Dhizuku (Owner)", "Uses device owner privileges via Dhizuku for enhanced freeze"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HailWorkProfileScreen(onBack: () -> Unit) {
    var selectedMethod by remember { mutableStateOf(FreezeMethod.SHIZUKU_SUSPEND) }
    var islandInstalled by remember { mutableStateOf(false) }
    var workProfileEnabled by remember { mutableStateOf(false) }
    var dhizukuInstalled by remember { mutableStateOf(false) }
    var adminEnabled by remember { mutableStateOf(false) }
    var autoFreezeOnScreenOff by remember { mutableStateOf(false) }
    var autoFreezeSchedule by remember { mutableStateOf(false) }
    var scheduleHour by remember { mutableStateOf(22) }
    var showFuzzySearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Freeze Method & Work Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding + PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Freeze Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Choose how apps are frozen — different methods have different compatibility and requirements", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(FreezeMethod.entries) { method ->
                val isSupported = when (method) {
                    FreezeMethod.WORK_PROFILE -> islandInstalled || workProfileEnabled
                    FreezeMethod.ROOT_DISABLE -> false
                    FreezeMethod.DHIZUKU -> dhizukuInstalled
                    FreezeMethod.DEVICE_ADMIN -> adminEnabled
                    else -> true
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            selectedMethod == method -> MaterialTheme.colorScheme.primaryContainer
                            !isSupported -> MaterialTheme.colorScheme.surfaceContainerLowest
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    onClick = { if (isSupported) selectedMethod = method },
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedMethod == method, onClick = { if (isSupported) selectedMethod = method }, enabled = isSupported)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(method.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (!isSupported) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                                if (method.requiresRoot) {
                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                                        Text("Root", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                                if (!isSupported) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                        Text("Not Available", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                            Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                Text("Work Profile (Island)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ListItem(
                            headlineContent = { Text("Island Installed") },
                            supportingContent = { Text("Oasis Feng's Island app for work profile management") },
                            leadingContent = { Icon(Icons.Default.Work, null) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val ctx = LocalContext.current
                                if (!islandInstalled) OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.oasisfeng.island")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }) { Text("Install") }
                                    Switch(checked = islandInstalled, onCheckedChange = { islandInstalled = it }, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        )
                        if (islandInstalled) {
                            ListItem(
                                headlineContent = { Text("Work Profile Active") },
                                supportingContent = { Text("Apps can be cloned and frozen in work profile") },
                                trailingContent = { Switch(checked = workProfileEnabled, onCheckedChange = { workProfileEnabled = it }) }
                            )
                        }
                    }
                }
            }

            item {
                Text("Auto-Freeze Triggers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Auto-Freeze on Screen Off") },
                            supportingContent = { Text("Freeze all frozen-list apps when screen turns off") },
                            leadingContent = { Icon(Icons.Default.Bedtime, null) },
                            trailingContent = { Switch(checked = autoFreezeOnScreenOff, onCheckedChange = { autoFreezeOnScreenOff = it }) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Scheduled Auto-Freeze") },
                            supportingContent = { Text("Freeze apps at a set time daily") },
                            leadingContent = { Icon(Icons.Default.Schedule, null) },
                            trailingContent = { Switch(checked = autoFreezeSchedule, onCheckedChange = { autoFreezeSchedule = it }) }
                        )
                        AnimatedVisibility(visible = autoFreezeSchedule) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Freeze at: ", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = "$scheduleHour:00",
                                    onValueChange = {},
                                    modifier = Modifier.width(100.dp),
                                    singleLine = true,
                                    readOnly = true,
                                    trailingIcon = { IconButton(onClick = { if (scheduleHour < 23) scheduleHour++ else scheduleHour = 0 }) { Icon(Icons.Default.AccessTime, null) } }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Search Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Fuzzy Search") },
                            supportingContent = { Text("Find apps even with typos in search query") },
                            trailingContent = { Switch(checked = showFuzzySearch, onCheckedChange = { showFuzzySearch = it }) }
                        )
                        ListItem(
                            headlineContent = { Text("Pinyin Search") },
                            supportingContent = { Text("Search apps by Chinese Pinyin romanization") },
                            trailingContent = { Switch(checked = false, onCheckedChange = {}) }
                        )
                        ListItem(
                            headlineContent = { Text("Nine-Key Search") },
                            supportingContent = { Text("T9-style number pad search for app names") },
                            trailingContent = { Switch(checked = false, onCheckedChange = {}) }
                        )
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
