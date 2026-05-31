package com.accu.ui.shell

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.ui.components.ACCTopBar
import com.accu.ui.theme.AccentGreen
import com.accu.ui.theme.AccentOrange
import com.accu.ui.theme.AccentRed
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel — real execution via LibSU / execShizuku
// ─────────────────────────────────────────────────────────────────────────────

data class FastbootUiState(
    val outputLines: List<String> = emptyList(),
    val isRunning: Boolean = false,
)

@HiltViewModel
class AdbFastbootViewModel @Inject constructor(
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {

    private val _state = MutableStateFlow(FastbootUiState())
    val state: StateFlow<FastbootUiState> = _state.asStateFlow()

    /**
     * Translate an `adb` command into the equivalent device-side shell command.
     *
     * On Android (the target device) there is no `adb` binary.
     * - `adb shell <cmd>` → `<cmd>` (strip the prefix; run via LibSU root)
     * - `adb reboot <mode>` → `reboot <mode>`
     * - `fastboot *`, `adb install *`, `adb pull *`, `adb push *`,
     *   `adb sideload *` → null (PC-only; can't run on device)
     */
    private fun toDeviceCmd(adbCmd: String): String? = when {
        adbCmd.startsWith("adb shell ") -> adbCmd.removePrefix("adb shell ")
        adbCmd == "adb reboot"           -> "reboot"
        adbCmd.startsWith("adb reboot ") -> "reboot ${adbCmd.removePrefix("adb reboot ")}"
        adbCmd.startsWith("fastboot ")   -> null
        adbCmd.startsWith("adb sideload")-> null
        adbCmd.startsWith("adb install") -> null
        adbCmd.startsWith("adb pull")    -> null
        adbCmd.startsWith("adb push")    -> null
        else -> adbCmd
    }

    fun runCmd(adbCommand: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isRunning = true, outputLines = it.outputLines + "> $adbCommand") }
        val deviceCmd = toDeviceCmd(adbCommand)
        if (deviceCmd == null) {
            _state.update { it.copy(
                isRunning = false,
                outputLines = it.outputLines + "[ PC ] This command runs on your PC, not on the device.\nCopy it and run from a terminal connected to this phone.",
            )}
            return@launch
        }
        val result = shizukuUtils.execShizuku(deviceCmd)
        val output = when {
            result.output.isNotBlank() && result.error.isNotBlank() ->
                result.output.trimEnd() + "\n[stderr] " + result.error.trimEnd()
            result.output.isNotBlank() -> result.output.trimEnd()
            result.error.isNotBlank()  -> "[stderr] " + result.error.trimEnd()
            result.exitCode == 0       -> "[ OK ] Done (no output)"
            else                       -> "[ FAIL ] Exit code ${result.exitCode}"
        }
        _state.update { it.copy(
            isRunning = false,
            outputLines = it.outputLines + output,
        )}
    }

    fun clearOutput() = _state.update { it.copy(outputLines = emptyList()) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Command definitions
// ─────────────────────────────────────────────────────────────────────────────

private data class FastbootCommand(
    val title: String,
    val command: String,
    val description: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val category: String,
)

private val FASTBOOT_COMMANDS = listOf(
    // Reboot actions
    FastbootCommand("Reboot System",       "adb reboot",                      "Normal system reboot",                       Icons.Default.Refresh,       false, "Reboot"),
    FastbootCommand("Reboot Bootloader",   "adb reboot bootloader",           "Enter bootloader / fastboot mode",           Icons.Default.DeveloperMode,  true,  "Reboot"),
    FastbootCommand("Reboot Recovery",     "adb reboot recovery",             "Enter recovery mode (TWRP / stock)",         Icons.Default.HealthAndSafety,true,  "Reboot"),
    FastbootCommand("Reboot to Download",  "adb reboot download",             "Samsung Odin / download mode",               Icons.Default.Download,       true,  "Reboot"),
    FastbootCommand("Reboot Sideload",     "adb reboot sideload",             "Stock recovery sideload mode",               Icons.Default.InstallMobile,  false, "Reboot"),

    // Screen / lock
    FastbootCommand("Wake Screen",         "adb shell input keyevent 224",    "Turn on the screen (KEYCODE_WAKEUP)",        Icons.Default.LightMode,      false, "Device Control"),
    FastbootCommand("Sleep Screen",        "adb shell input keyevent 223",    "Turn off the screen (KEYCODE_SLEEP)",        Icons.Default.DarkMode,       false, "Device Control"),
    FastbootCommand("Lock Screen",         "adb shell input keyevent 26",     "Press power key to lock (KEYCODE_POWER)",   Icons.Default.Lock,           false, "Device Control"),
    FastbootCommand("Volume Up",           "adb shell input keyevent 24",     "Simulate Volume Up key press",              Icons.Default.VolumeUp,       false, "Device Control"),
    FastbootCommand("Volume Down",         "adb shell input keyevent 25",     "Simulate Volume Down key press",            Icons.Default.VolumeDown,     false, "Device Control"),
    FastbootCommand("Home Button",         "adb shell input keyevent 3",      "Simulate pressing the Home button",         Icons.Default.Home,           false, "Device Control"),
    FastbootCommand("Back Button",         "adb shell input keyevent 4",      "Simulate pressing the Back button",         Icons.Default.ArrowBack,      false, "Device Control"),
    FastbootCommand("Recents Button",      "adb shell input keyevent 187",    "Simulate pressing Recents (App switcher)", Icons.Default.ViewCarousel,   false, "Device Control"),
    FastbootCommand("Screenshot",          "adb shell input keyevent 120",    "Capture screenshot (KEYCODE_SYSRQ)",       Icons.Default.Screenshot,     false, "Device Control"),
    FastbootCommand("Simulate Tap",        "adb shell input tap 540 960",     "Tap at X=540, Y=960 — edit coords",        Icons.Default.TouchApp,       false, "Device Control"),
    FastbootCommand("Swipe Up",            "adb shell input swipe 540 1800 540 300 300", "Swipe up gesture (300ms)",     Icons.Default.SwipeUp,        false, "Device Control"),

    // Fastboot commands (device must be in bootloader mode)
    FastbootCommand("fastboot devices",    "fastboot devices",                "List devices in fastboot mode",             Icons.Default.Devices,        false, "Fastboot"),
    FastbootCommand("fastboot reboot",     "fastboot reboot",                 "Reboot from fastboot mode",                 Icons.Default.Refresh,        false, "Fastboot"),
    FastbootCommand("Unlock Bootloader",   "fastboot flashing unlock",        "⚠ Unlock bootloader (wipes device!)",       Icons.Default.LockOpen,       true,  "Fastboot"),
    FastbootCommand("Lock Bootloader",     "fastboot flashing lock",          "Re-lock bootloader",                        Icons.Default.Lock,           true,  "Fastboot"),
    FastbootCommand("Boot image",          "fastboot boot image.img",         "Boot a kernel image without flashing",      Icons.Default.Memory,         false, "Fastboot"),
    FastbootCommand("Flash System",        "fastboot flash system system.img","⚠ Flash system partition",                  Icons.Default.FlashOn,        true,  "Fastboot"),
    FastbootCommand("Flash Recovery",      "fastboot flash recovery twrp.img","Flash a custom recovery image",             Icons.Default.HealthAndSafety,true,  "Fastboot"),
    FastbootCommand("Flash Boot",          "fastboot flash boot boot.img",    "Flash boot/kernel image",                   Icons.Default.Memory,         true,  "Fastboot"),
    FastbootCommand("Wipe Cache",          "fastboot erase cache",            "Erase the cache partition",                 Icons.Default.CleaningServices,true, "Fastboot"),
    FastbootCommand("OEM Info",            "fastboot oem device-info",        "Show OEM device info (e.g. lock state)",    Icons.Default.Info,           false, "Fastboot"),
    FastbootCommand("Get Var All",         "fastboot getvar all",             "Dump all fastboot variables",               Icons.Default.List,           false, "Fastboot"),

    // sideload
    FastbootCommand("ADB sideload APK",    "adb sideload update.zip",        "Sideload an OTA or zip via recovery",       Icons.Default.InstallMobile,  false, "Sideload"),
    FastbootCommand("Install APK",         "adb install -r app.apk",         "Install APK (keep data with -r)",           Icons.Default.Android,        false, "Sideload"),
    FastbootCommand("Install Split APKs",  "adb install-multiple base.apk split.apk", "Install split APK bundles",       Icons.Default.Android,        false, "Sideload"),
    FastbootCommand("Pull file",           "adb pull /sdcard/file.txt .",     "Download file from device to PC",          Icons.Default.Download,       false, "Sideload"),
    FastbootCommand("Push file",           "adb push local.txt /sdcard/",    "Upload file from PC to device",             Icons.Default.Upload,         false, "Sideload"),
)

private val CATEGORIES = FASTBOOT_COMMANDS.map { it.category }.distinct()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbFastbootScreen(
    onBack: () -> Unit = {},
    vm: AdbFastbootViewModel = hiltViewModel(),
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val uiState by vm.state.collectAsState()
    var selectedCategory by remember { mutableStateOf("Reboot") }
    var confirmCmd by remember { mutableStateOf<FastbootCommand?>(null) }
    var customCmd by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    val visibleCommands = FASTBOOT_COMMANDS.filter { it.category == selectedCategory }

    // Dangerous command confirmation
    confirmCmd?.let { cmd ->
        AlertDialog(
            onDismissRequest = { confirmCmd = null },
            icon = { Icon(Icons.Default.Warning, null, tint = AccentRed) },
            title = { Text("Dangerous Command") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This command can cause data loss or brick your device:")
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(cmd.command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Text("Proceed only if you know exactly what you're doing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { val c = cmd; confirmCmd = null; vm.runCmd(c.command) }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) { Text("Run Anyway", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { confirmCmd = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                ACCTopBar(
                    title = "Fastboot & Device Control",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { vm.clearOutput() }) { Icon(Icons.Outlined.DeleteSweep, "Clear output") }
                    },
                )
                // Warning banner
                Surface(color = AccentOrange.copy(0.1f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = AccentOrange)
                        Text("Bootloader & flash commands require the device in fastboot mode.", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                    }
                }
                // Category tabs
                ScrollableTabRow(selectedTabIndex = CATEGORIES.indexOf(selectedCategory), edgePadding = 12.dp) {
                    CATEGORIES.forEachIndexed { idx, cat ->
                        Tab(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, text = {
                            Text(cat, style = MaterialTheme.typography.labelMedium)
                        })
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visibleCommands) { cmd ->
                FastbootCommandCard(cmd, clipboard, onRun = {
                    if (cmd.danger) confirmCmd = cmd
                    else vm.runCmd(cmd.command)
                })
            }

            // Custom command
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Terminal, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Custom Command", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        OutlinedTextField(
                            value = customCmd, onValueChange = { customCmd = it },
                            placeholder = { Text("shell command or adb shell …") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(customCmd)) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Copy")
                            }
                            Button(onClick = {
                                if (customCmd.isNotBlank()) vm.runCmd(customCmd)
                            }, modifier = Modifier.weight(1f), enabled = customCmd.isNotBlank() && !uiState.isRunning) {
                                if (uiState.isRunning) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                else { Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Run") }
                            }
                        }
                    }
                }
            }

            // Output terminal
            if (uiState.outputLines.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Output", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.outputLines.joinToString("\n"))) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
                                }
                            }
                            HorizontalDivider()
                            uiState.outputLines.forEach { line ->
                                Text(
                                    line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = when {
                                        line.startsWith(">")   -> MaterialTheme.colorScheme.primary
                                        line.startsWith("[ OK ]") || line.contains("Success") -> AccentGreen
                                        line.startsWith("[ FAIL ]") || line.startsWith("[ PC ]") || line.contains("error", true) -> AccentRed
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FastbootCommandCard(cmd: FastbootCommand, clipboard: androidx.compose.ui.platform.ClipboardManager, onRun: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (cmd.danger) AccentRed.copy(0.04f) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = if (cmd.danger) AccentRed.copy(0.12f) else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(cmd.icon, null, Modifier.size(22.dp), tint = if (cmd.danger) AccentRed else MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(cmd.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (cmd.danger) {
                        Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(0.15f)) {
                            Text("DANGER", style = MaterialTheme.typography.labelSmall, color = AccentRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(cmd.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(cmd.command, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(cmd.command)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onRun, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = if (cmd.danger) AccentRed else MaterialTheme.colorScheme.primary)) {
                    Text("Run", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}
