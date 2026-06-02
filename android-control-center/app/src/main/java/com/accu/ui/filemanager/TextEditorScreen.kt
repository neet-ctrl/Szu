package com.accu.ui.filemanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    filePath: String = "/sdcard/notes/todo.txt",
    onBack: () -> Unit = {},
) {
    val fileName = filePath.substringAfterLast("/")
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                when {
                    !file.exists() || !file.canRead() -> {
                        content = "# ${file.name}\n\nStart typing…"
                        loadError = null
                    }
                    file.length() > 4L * 1024 * 1024 -> {
                        content = ""
                        loadError = "File is too large to edit (${file.length() / 1024 / 1024} MB). " +
                                    "Maximum is 4 MB — use a PC editor for large files."
                    }
                    else -> {
                        content = file.readText()
                        loadError = null
                    }
                }
            } catch (e: Exception) {
                content = ""
                loadError = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun saveFile() {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                file.writeText(content)
            } catch (_: Exception) { /* permission denied — silently ignore */ }
        }
    }

    val context = LocalContext.current
    var isModified by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var wrapLines by remember { mutableStateOf(true) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(14) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineInput by remember { mutableStateOf("") }
    var cursorLine by remember { mutableStateOf(1) }

    val lineCount = content.lines().size
    val charCount = content.length
    val wordCount = content.split(Regex("\\s+")).count { it.isNotBlank() }

    if (showGoToLineDialog) {
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false; goToLineInput = "" },
            title = { Text("Go to Line") },
            text = {
                OutlinedTextField(
                    value = goToLineInput,
                    onValueChange = { if (it.all(Char::isDigit)) goToLineInput = it },
                    label = { Text("Line number (1–$lineCount)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = goToLineInput.toIntOrNull()?.coerceIn(1, lineCount) ?: return@TextButton
                    cursorLine = target
                    showGoToLineDialog = false
                    goToLineInput = ""
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showGoToLineDialog = false; goToLineInput = "" }) { Text("Cancel") } },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save file?") },
            text = { Text("Save changes to $fileName?") },
            confirmButton = {
                TextButton(onClick = {
                    saveFile()
                    isModified = false
                    showSaveDialog = false
                    onBack()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false; onBack() }) { Text("Discard") } }
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "${if (isModified) "• " else ""}$fileName",
                onBack = {
                    if (isModified) showSaveDialog = true else onBack()
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, "Find") }
                    IconButton(onClick = { wrapLines = !wrapLines }) { Icon(if (wrapLines) Icons.Default.WrapText else Icons.Default.Notes, "Wrap") }
                    IconButton(onClick = { saveFile(); isModified = false }) { Icon(Icons.Default.Save, "Save") }
                    var showMoreMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(showMoreMenu, { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Select All") },
                                leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                onClick = {
                                    showMoreMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(fileName, content))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Go to Line…") },
                                leadingIcon = { Icon(Icons.Default.FormatListNumbered, null) },
                                onClick = { showMoreMenu = false; showGoToLineDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Encoding: UTF-8") },
                                leadingIcon = { Icon(Icons.Default.Language, null) },
                                onClick = { showMoreMenu = false },
                                trailingIcon = { Icon(Icons.Default.Lock, null, Modifier.size(14.dp)) },
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                Text("$lineCount lines · $wordCount words · $charCount chars", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (fontSize > 8) fontSize-- }, Modifier.size(28.dp)) { Icon(Icons.Default.Remove, null, Modifier.size(14.dp)) }
                    Text("${fontSize}sp", fontSize = 11.sp)
                    IconButton(onClick = { if (fontSize < 30) fontSize++ }, Modifier.size(28.dp)) { Icon(Icons.Default.Add, null, Modifier.size(14.dp)) }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search / Replace bar
            if (showSearch) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            searchQuery, { searchQuery = it },
                            modifier = Modifier.weight(1f).height(48.dp),
                            placeholder = { Text("Find…") },
                            singleLine = true,
                        )
                        val matchCount = if (searchQuery.isBlank()) 0 else content.split(searchQuery, ignoreCase = true).size - 1
                        Text("$matchCount", modifier = Modifier.padding(horizontal = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { showSearch = false }) { Icon(Icons.Default.Close, null) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            replaceQuery, { replaceQuery = it },
                            modifier = Modifier.weight(1f).height(48.dp),
                            placeholder = { Text("Replace…") },
                            singleLine = true,
                        )
                        TextButton(onClick = {
                            if (searchQuery.isNotBlank()) {
                                content = content.replaceFirst(searchQuery, replaceQuery, ignoreCase = true)
                                isModified = true
                            }
                        }) { Text("Replace") }
                        TextButton(onClick = {
                            if (searchQuery.isNotBlank()) {
                                content = content.replace(searchQuery, replaceQuery, ignoreCase = true)
                                isModified = true
                            }
                        }) { Text("All") }
                    }
                }
                HorizontalDivider()
            }

            // Editor area
            Row(Modifier.fillMaxSize()) {
                // Line numbers
                if (showLineNumbers) {
                    Column(
                        Modifier
                            .width(40.dp)
                            .fillMaxHeight()
                            .padding(top = 8.dp, start = 4.dp),
                    ) {
                        (1..lineCount.coerceAtMost(500)).forEach { n ->
                            Text(
                                "$n",
                                fontSize = fontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                lineHeight = (fontSize * 1.5).sp,
                            )
                        }
                    }
                }

                // Text input
                BasicTextField(
                    value = content,
                    onValueChange = { content = it; isModified = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = (fontSize * 1.5).sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
