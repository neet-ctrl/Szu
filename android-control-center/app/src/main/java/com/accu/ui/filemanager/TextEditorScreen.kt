package com.accu.ui.filemanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    filePath: String = "/sdcard/notes/todo.txt",
    onBack: () -> Unit = {},
) {
    val fileName = filePath.substringAfterLast("/")
    var content by remember {
        mutableStateOf(
            """# TODO List
            |
            |## High Priority
            |- [x] Set up Shizuku
            |- [ ] Configure Key Mapper for volume buttons
            |- [ ] Review battery optimization settings
            |
            |## Medium Priority
            |- [ ] Test DarQ on Chrome and Instagram
            |- [ ] Configure JamesDSP equalizer preset
            |- [ ] Set up FTP server for file transfer
            |
            |## Notes
            |Remember to check ColorBlendr per-app theming for Gmail.
            |The SD Maid squeezer found 1.2 GB of compressible media.
            """.trimMargin()
        )
    }
    var isModified by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var wrapLines by remember { mutableStateOf(true) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(14) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val lineCount = content.lines().size
    val charCount = content.length
    val wordCount = content.split(Regex("\\s+")).count { it.isNotBlank() }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save file?") },
            text = { Text("Save changes to $fileName?") },
            confirmButton = { TextButton(onClick = { isModified = false; showSaveDialog = false; onBack() }) { Text("Save") } },
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
                    IconButton(onClick = { isModified = false }) { Icon(Icons.Default.Save, "Save") }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More") }
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
