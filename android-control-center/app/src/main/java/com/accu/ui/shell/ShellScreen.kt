package com.accu.ui.shell

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.components.InfoTooltipIcon
import androidx.compose.foundation.lazy.LazyRow

enum class ShellMode(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LOCAL("Local ADB", Icons.Outlined.PhoneAndroid),
    WIFI("Wi-Fi ADB", Icons.Outlined.Wifi),
    OTG("OTG ADB", Icons.Outlined.Usb)
}

fun ShellMode.toConnectionMode(): AdbConnectionMode = when (this) {
    ShellMode.WIFI  -> AdbConnectionMode.WIFI
    ShellMode.OTG   -> AdbConnectionMode.OTG
    ShellMode.LOCAL -> AdbConnectionMode.LOCAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    onNavigateToScripts: () -> Unit = {},
    onNavigateToCommandExamples: () -> Unit = {},
    onNavigateToFileBrowser: (AdbConnectionMode, String) -> Unit = { _, _ -> },
    viewModel: ShellViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val output by viewModel.output.collectAsStateWithLifecycle()
    val history by viewModel.commandHistory.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val commandExamples by viewModel.commandExamples.collectAsStateWithLifecycle()
    val aiAnalysis by viewModel.aiAnalysis.collectAsStateWithLifecycle()
    var currentMode by remember { mutableStateOf(ShellMode.LOCAL) }
    var command by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showExamples by remember { mutableStateOf(false) }
    var showAiAnalysis by remember { mutableStateOf(false) }
    var showWifiConnect by remember { mutableStateOf(false) }
    var showOtgWaitingDialog by remember { mutableStateOf(false) }
    var wifiHost by remember { mutableStateOf("") }
    var wifiPort by remember { mutableStateOf("5555") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveFileName by remember { mutableStateOf("output.txt") }
    var outputSearchQuery by remember { mutableStateOf("") }
    var showOutputSearch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) listState.animateScrollToItem(output.size - 1)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Shell", fontWeight = FontWeight.Bold)
                            InfoTooltipIcon(
                                title = "Shell — aShellYou Mode",
                                description = "Full-featured ADB shell with 3 modes:\n\n• Local ADB: Runs via Shizuku (no USB needed)\n• Wi-Fi ADB: Connect to remote device over network (port 5555)\n• OTG ADB: Connect via USB OTG cable\n\nFeatures: command history, bookmarks, AI analysis, file browser, output search, save to file, and 200+ preloaded command examples."
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showOutputSearch = !showOutputSearch }) {
                            Icon(Icons.Outlined.Search, "Search output")
                        }
                        IconButton(onClick = { showExamples = true }) {
                            Icon(Icons.Outlined.Book, "Command examples")
                        }
                        IconButton(onClick = { viewModel.clearOutput() }) {
                            Icon(Icons.Outlined.DeleteOutline, "Clear")
                        }
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Outlined.Save, "Save output")
                        }
                        IconButton(onClick = onNavigateToScripts) {
                            Icon(Icons.Outlined.Code, "Script Manager")
                        }
                    }
                )

                // Mode tabs
                TabRow(
                    selectedTabIndex = currentMode.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    ShellMode.values().forEach { mode ->
                        Tab(
                            selected = currentMode == mode,
                            onClick = {
                                currentMode = mode
                                if (mode == ShellMode.WIFI && !uiState.isWifiConnected) showWifiConnect = true
                                if (mode == ShellMode.OTG) showOtgWaitingDialog = true
                            },
                            text = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                            icon = { Icon(mode.icon, mode.label, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                // Connection status bar
                AnimatedVisibility(visible = currentMode == ShellMode.WIFI) {
                    Surface(color = if (uiState.isWifiConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (uiState.isWifiConnected) "Connected: ${uiState.connectedHost}" else "Not connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isWifiConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row {
                                if (uiState.isWifiConnected) {
                                    IconButton(onClick = { onNavigateToFileBrowser(ShellMode.WIFI.toConnectionMode(), uiState.connectedHost) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Outlined.FolderOpen, "Browse files", modifier = Modifier.size(18.dp))
                                    }
                                }
                                TextButton(onClick = { showWifiConnect = true }, contentPadding = PaddingValues(4.dp)) {
                                    Text(if (uiState.isWifiConnected) "Change" else "Connect", style = MaterialTheme.typography.labelSmall)
                                }
                                if (uiState.isWifiConnected) {
                                    TextButton(onClick = { viewModel.disconnectWifi() }, contentPadding = PaddingValues(4.dp)) {
                                        Text("Disconnect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                // Output search bar
                AnimatedVisibility(visible = showOutputSearch) {
                    OutlinedTextField(
                        value = outputSearchQuery,
                        onValueChange = { outputSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        placeholder = { Text("Search in output…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (outputSearchQuery.isNotEmpty()) IconButton(onClick = { outputSearchQuery = "" }) {
                                Icon(Icons.Outlined.Clear, null, modifier = Modifier.size(16.dp))
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        bottomBar = {
            Column {
                // AI suggestions row
                AnimatedVisibility(visible = suggestions.isNotEmpty() && command.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(suggestions) { suggestion ->
                            SuggestionChip(
                                onClick = { command = suggestion },
                                label = { Text(suggestion, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.widthIn(max = 160.dp)
                            )
                        }
                    }
                }

                // Utility buttons row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Tab", "Ctrl+C", "↑", "↓", "Esc").forEach { key ->
                        OutlinedButton(
                            onClick = {
                                when (key) {
                                    "↑" -> viewModel.navigateHistory(up = true) { command = it }
                                    "↓" -> viewModel.navigateHistory(up = false) { command = it }
                                    "Ctrl+C" -> viewModel.sendInterrupt()
                                    "Tab" -> viewModel.onTabComplete(command) { command = it }
                                    "Esc" -> command = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(key, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }

                // Input row
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bookmark button
                        IconButton(
                            onClick = { showBookmarks = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Outlined.BookmarkBorder, "Bookmarks", modifier = Modifier.size(20.dp))
                        }

                        OutlinedTextField(
                            value = command,
                            onValueChange = {
                                command = it
                                viewModel.updateSuggestions(it)
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Enter ADB command…", style = MaterialTheme.typography.bodySmall) },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (command.isNotBlank()) {
                                        viewModel.executeCommand(command, currentMode)
                                        command = ""
                                    }
                                }
                            ),
                            trailingIcon = {
                                if (command.isNotEmpty()) {
                                    IconButton(onClick = { command = "" }) {
                                        Icon(Icons.Outlined.Clear, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        )

                        // AI analyze button
                        if (command.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.analyzeWithAi(command)
                                    showAiAnalysis = true
                                },
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, "AI Analyze", modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        // Send/Stop button
                        if (uiState.isRunning) {
                            FilledIconButton(
                                onClick = { viewModel.sendInterrupt() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Icon(Icons.Filled.Stop, "Stop", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            FilledIconButton(
                                onClick = {
                                    if (command.isNotBlank()) {
                                        viewModel.executeCommand(command, currentMode)
                                        command = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Send, "Run")
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        val filteredOutput = if (outputSearchQuery.isBlank()) output
        else output.filter { it.text.contains(outputSearchQuery, ignoreCase = true) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 4.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (filteredOutput.isEmpty() && output.isEmpty()) {
                item {
                    ShellWelcomeBanner(mode = currentMode, onSuggestionSelected = { command = it })
                }
            }
            items(filteredOutput, key = { it.id }) { line ->
                ShellOutputLine(
                    line = line,
                    searchQuery = outputSearchQuery,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(line.text))
                    },
                    onBookmark = { viewModel.addBookmark(line.text) },
                    onAiAnalyze = {
                        viewModel.analyzeWithAi(line.text)
                        showAiAnalysis = true
                    }
                )
            }
            if (uiState.isRunning) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Running…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // History bottom sheet
    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            CommandHistorySheet(
                history = history,
                onSelectCommand = { cmd ->
                    command = cmd
                    showHistory = false
                },
                onDeleteCommand = viewModel::deleteHistoryEntry,
                onClearAll = viewModel::clearHistory
            )
        }
    }

    // Bookmarks bottom sheet
    if (showBookmarks) {
        ModalBottomSheet(onDismissRequest = { showBookmarks = false }) {
            BookmarksSheet(
                bookmarks = bookmarks,
                onSelectBookmark = { cmd ->
                    command = cmd
                    showBookmarks = false
                },
                onDeleteBookmark = viewModel::deleteBookmark,
                currentCommand = command,
                onBookmarkCurrent = { if (command.isNotBlank()) viewModel.addBookmark(command) }
            )
        }
    }

    // Command examples bottom sheet
    if (showExamples) {
        ModalBottomSheet(onDismissRequest = { showExamples = false }, modifier = Modifier.fillMaxHeight(0.9f)) {
            CommandExamplesSheet(
                examples = commandExamples,
                onSelectExample = { cmd ->
                    command = cmd
                    showExamples = false
                }
            )
        }
    }

    // AI analysis bottom sheet
    if (showAiAnalysis) {
        ModalBottomSheet(onDismissRequest = { showAiAnalysis = false }) {
            AiAnalysisSheet(
                command = uiState.lastAnalyzedCommand,
                analysis = aiAnalysis,
                onApplySuggestion = { cmd ->
                    command = cmd
                    showAiAnalysis = false
                }
            )
        }
    }

    // Wi-Fi connect dialog
    if (showWifiConnect) {
        AlertDialog(
            onDismissRequest = { showWifiConnect = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Wifi, null)
                    Text("Connect via Wi-Fi ADB")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enable Wireless Debugging in Developer Options, then pair with this device.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = wifiHost, onValueChange = { wifiHost = it }, label = { Text("Device IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = wifiPort, onValueChange = { wifiPort = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { viewModel.showQrPairing() }, label = { Text("Pair via QR") }, leadingIcon = { Icon(Icons.Outlined.QrCode, null, Modifier.size(16.dp)) })
                        AssistChip(onClick = { viewModel.showCodePairing() }, label = { Text("Pair via code") }, leadingIcon = { Icon(Icons.Outlined.Pin, null, Modifier.size(16.dp)) })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.connectWifi(wifiHost, wifiPort.toIntOrNull() ?: 5555)
                    showWifiConnect = false
                }) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { showWifiConnect = false }) { Text("Cancel") } }
        )
    }

    // OTG waiting dialog
    if (showOtgWaitingDialog) {
        AlertDialog(
            onDismissRequest = { showOtgWaitingDialog = false },
            icon = { Icon(Icons.Outlined.Usb, null) },
            title = { Text("Waiting for OTG Device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connect an Android device via USB OTG cable to begin ADB session.", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Make sure USB debugging is enabled on the target device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { showOtgWaitingDialog = false }) { Text("Start Scan") }
            },
            dismissButton = { TextButton(onClick = { showOtgWaitingDialog = false }) { Text("Cancel") } }
        )
    }

    // Save output dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Output") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save current terminal output to a text file.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = saveFileName,
                        onValueChange = { saveFileName = it },
                        label = { Text("File name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveOutputToFile(saveFileName, output.joinToString("\n") { it.text })
                    showSaveDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ShellWelcomeBanner(mode: ShellMode, onSuggestionSelected: (String) -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(mode.icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("ACC Shell — ${mode.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when (mode) {
            ShellMode.LOCAL -> Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Powered by Shizuku", style = MaterialTheme.typography.bodyMedium)
                Text("Runs ADB commands locally without USB debugging", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ShellMode.WIFI -> Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Wireless ADB", style = MaterialTheme.typography.bodyMedium)
                Text("Connect to any device on your local network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ShellMode.OTG -> Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("OTG Cable Mode", style = MaterialTheme.typography.bodyMedium)
                Text("Connect via USB OTG for ADB access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = { onSuggestionSelected("pm list packages") }, label = { Text("pm list packages") })
            SuggestionChip(onClick = { onSuggestionSelected("getprop ro.build.version.release") }, label = { Text("getprop ro.build.version.release") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = { onSuggestionSelected("dumpsys battery") }, label = { Text("dumpsys battery") })
            SuggestionChip(onClick = { onSuggestionSelected("wm density") }, label = { Text("wm density") })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShellOutputLine(
    line: OutputLine,
    searchQuery: String,
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onAiAnalyze: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    val bgColor = when {
        line.isCommand -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        line.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onLongClick = { showActions = !showActions }
        ),
        color = bgColor
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
            if (line.isCommand) {
                Text("$ ", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = when {
                    line.isError -> MaterialTheme.colorScheme.error
                    line.isCommand -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
    AnimatedVisibility(visible = showActions) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { onCopy(); showActions = false }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { onBookmark(); showActions = false }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Outlined.Bookmark, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Bookmark", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { onAiAnalyze(); showActions = false }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("AI", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CommandHistorySheet(
    history: List<String>,
    onSelectCommand: (String) -> Unit,
    onDeleteCommand: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Command History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClearAll) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("No history yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val clipboardManager = LocalClipboardManager.current
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history.reversed()) { cmd ->
                    ListItem(
                        headlineContent = { Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Outlined.History, null, Modifier.size(18.dp)) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { clipboardManager.setText(AnnotatedString(cmd)) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteCommand(cmd) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.clickable { onSelectCommand(cmd) }
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BookmarksSheet(
    bookmarks: List<String>,
    onSelectBookmark: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    currentCommand: String,
    onBookmarkCurrent: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Bookmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (currentCommand.isNotBlank()) {
                FilledTonalButton(onClick = onBookmarkCurrent) {
                    Icon(Icons.Outlined.Bookmark, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save current")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Long-press a command in output to bookmark it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val clipboardManager = LocalClipboardManager.current
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(bookmarks) { cmd ->
                    ListItem(
                        headlineContent = { Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Outlined.Bookmark, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { clipboardManager.setText(AnnotatedString(cmd)) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteBookmark(cmd) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.clickable { onSelectBookmark(cmd) }.clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

data class CommandExample(val command: String, val description: String, val category: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandExamplesSheet(examples: List<CommandExample>, onSelectExample: (String) -> Unit) {
    val categories = (listOf("All") + examples.map { it.category }.distinct())
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val filtered = examples.filter {
        (selectedCategory == "All" || it.category == selectedCategory) &&
                (searchQuery.isBlank() || it.command.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Command Examples", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            InfoTooltipIcon(title = "Command Examples", description = "200+ pre-loaded ADB command examples organized by category. Tap any command to paste it into the input field. You can also add your own custom commands.")
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search commands…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(filtered) { example ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelectExample(example.command) }, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(example.command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(example.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        AssistChip(onClick = {}, label = { Text(example.category, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAnalysisSheet(command: String, analysis: AiAnalysisState, onApplySuggestion: (String) -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Text("AI Command Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (analysis.explanation.isNotEmpty()) {
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(analysis.explanation)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Copy analysis", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (command.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                Text(command, modifier = Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        when {
            analysis.isLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Analyzing command…")
            }
            analysis.dangerLevel == DangerLevel.CRITICAL -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Column {
                        Text("⚠️ DANGEROUS COMMAND", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        if (analysis.explanation.isNotEmpty()) Text(analysis.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            analysis.explanation.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(analysis.explanation, style = MaterialTheme.typography.bodySmall)
                if (analysis.suggestions.isNotEmpty()) {
                    Text("Suggested corrections:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    analysis.suggestions.forEach { suggestion ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { onApplySuggestion(suggestion) }, shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(suggestion, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            else -> Text("No analysis available. AI model may not be loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

data class OutputLine(val id: Long, val text: String, val isCommand: Boolean = false, val isError: Boolean = false)
data class AiAnalysisState(val isLoading: Boolean = false, val explanation: String = "", val suggestions: List<String> = emptyList(), val dangerLevel: DangerLevel = DangerLevel.SAFE)
enum class DangerLevel { SAFE, MODERATE, HIGH, CRITICAL }
