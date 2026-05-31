package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdkdemo.data.LogEntry
import com.accu.sdkdemo.data.LogLevel
import com.accu.sdkdemo.data.LogManager
import com.accu.sdkdemo.ui.theme.*
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun LogCenterScreen(vm: MainViewModel) {
    val logs      by vm.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    var search    by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val filtered = remember(logs, search, levelFilter) {
        logs.filter {
            (levelFilter == null || it.level == levelFilter) &&
            (search.isBlank() || it.message.contains(search, true) || it.tag.contains(search, true))
        }.reversed()
    }

    LaunchedEffect(filtered.size) {
        if (autoScroll && filtered.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        label = { Text("Search logs…") }, modifier = Modifier.weight(1f), singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } },
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(LogManager.exportText())) }) { Icon(Icons.Default.ContentCopy, "Export") }
                    IconButton(onClick = { LogManager.clear() }) { Icon(Icons.Default.Delete, "Clear") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = levelFilter == null, onClick = { levelFilter = null }, label = { Text("All", fontSize = 10.sp) })
                    LogLevel.entries.forEach { level ->
                        FilterChip(selected = levelFilter == level, onClick = { levelFilter = if (levelFilter == level) null else level }, label = { Text(level.name, fontSize = 10.sp, color = levelColor(level)) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${filtered.size} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Auto-scroll", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = autoScroll, onCheckedChange = { autoScroll = it }, modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(filtered, key = { it.id }) { entry ->
                LogRow(entry)
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(entry.formattedTime, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Box(Modifier.width(4.dp).fillMaxHeight().background(levelColor(entry.level)))
        Text("[${entry.tag}]", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(entry.message, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = levelColor(entry.level), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG   -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO    -> MaterialTheme.colorScheme.onSurface
    LogLevel.SUCCESS -> AccuGreen
    LogLevel.WARNING -> AccuAmber
    LogLevel.ERROR   -> AccuRed
}
