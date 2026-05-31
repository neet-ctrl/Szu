package com.accu.ui.appmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class AppNote(val pkg: String, val appName: String, val content: String, val timestamp: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureNotesScreen(onBack: () -> Unit = {}) {
    var notes by remember {
        mutableStateOf(listOf(
            AppNote("com.whatsapp", "WhatsApp", "Remember to check privacy settings — chat backup goes to Google Drive. Disable for privacy.", "2024-05-30 14:22"),
            AppNote("com.android.chrome", "Chrome", "Switch to DNS over HTTPS in Settings > Privacy. Use 1.1.1.1 Cloudflare.", "2024-05-28 09:10"),
            AppNote("com.spotify.music", "Spotify", "Disable autoplay of podcast episodes. Background data allowed for offline downloads.", "2024-05-25 18:45"),
        ))
    }
    var selectedNote by remember { mutableStateOf<AppNote?>(null) }
    var editingNote by remember { mutableStateOf<AppNote?>(null) }
    var editContent by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newPkg by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    val filtered = notes.filter { search.isBlank() || it.appName.contains(search, ignoreCase = true) || it.content.contains(search, ignoreCase = true) }

    if (editingNote != null) {
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("Edit Note — ${editingNote!!.appName}") },
            text = {
                OutlinedTextField(editContent, { editContent = it }, modifier = Modifier.fillMaxWidth().height(200.dp), label = { Text("Note") })
            },
            confirmButton = {
                TextButton(onClick = {
                    notes = notes.map { if (it.pkg == editingNote!!.pkg) it.copy(content = editContent) else it }
                    editingNote = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("Cancel") } }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("App Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(newPkg, { newPkg = it }, label = { Text("Package name (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(newContent, { newContent = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth().height(150.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newContent.isNotBlank()) {
                        notes = listOf(AppNote(newPkg.ifBlank { "unknown.$newName" }, newName, newContent, "now")) + notes
                        newPkg = ""; newName = ""; newContent = ""; showAddDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = { ACCTopBar(title = "App Notes", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Search notes…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Note, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No notes yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tap + to add a note for any app", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp)) {
                    items(filtered, key = { it.pkg }) { note ->
                        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                            editingNote = note; editContent = note.content
                        }) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Note, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(note.appName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(note.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(note.content, fontSize = 13.sp, maxLines = 4)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { notes = notes.filter { it.pkg != note.pkg } }) { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                                    TextButton(onClick = { editingNote = note; editContent = note.content }) { Text("Edit", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
