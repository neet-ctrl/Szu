package com.accu.ui.filemanager

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class FileViewMode { LIST, GRID }

data class BookmarkedLocation(
    val id: String,
    val name: String,
    val path: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isRemote: Boolean = false,
)

enum class RemoteType(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FTP("FTP Server", Icons.Default.Dns),
    SFTP("SFTP / SSH", Icons.Default.Terminal),
    SMB("Windows Share (SMB)", Icons.Default.Computer),
    WEBDAV("WebDAV", Icons.Default.Cloud),
}

data class RemoteConnection(
    val id: String,
    val type: RemoteType,
    val host: String,
    val port: Int,
    val username: String,
    val path: String,
    val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerAdvancedFeaturesScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Bookmarks", "Remote", "FTP Server", "Archives")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced File Features") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, tab ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(tab) })
                }
            }
            when (selectedTab) {
                0 -> BookmarksTab()
                1 -> RemoteConnectionsTab()
                2 -> FtpServerTab()
                3 -> ArchivesTab()
            }
        }
    }
}

@Composable
private fun BookmarksTab() {
    val bookmarks = remember {
        mutableStateListOf(
            BookmarkedLocation("1", "Downloads", "/sdcard/Download", Icons.Default.Download),
            BookmarkedLocation("2", "Documents", "/sdcard/Documents", Icons.Default.Description),
            BookmarkedLocation("3", "DCIM Camera", "/sdcard/DCIM/Camera", Icons.Default.PhotoCamera),
            BookmarkedLocation("4", "Music", "/sdcard/Music", Icons.Default.MusicNote),
            BookmarkedLocation("5", "Android/data", "/sdcard/Android/data", Icons.Default.Android),
            BookmarkedLocation("6", "Root /system", "/system", Icons.Default.AdminPanelSettings, false),
        )
    }
    var showAdd by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bookmarked Locations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAdd = true }) { Text("Add") }
            }
        }
        items(bookmarks, key = { it.id }) { bm ->
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(bm.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bm.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(bm.path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    IconButton(onClick = { bookmarks.remove(bm) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteConnectionsTab() {
    val connections = remember {
        mutableStateListOf(
            RemoteConnection("1", RemoteType.SMB, "192.168.1.100", 445, "user", "/shared", "Home NAS"),
            RemoteConnection("2", RemoteType.FTP, "ftp.example.com", 21, "ftpuser", "/", "FTP Server"),
        )
    }
    var showAdd by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Remote Connections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Button(onClick = { showAdd = !showAdd }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add") }
            }
        }
        if (showAdd) {
            item { AddRemoteConnectionCard(onAdd = { connections.add(it); showAdd = false }) }
        }
        items(connections, key = { it.id }) { conn ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(conn.type.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(conn.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${conn.type.label} • ${conn.host}:${conn.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${conn.username}@${conn.path}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
                    }
                    OutlinedButton(onClick = {}) { Text("Connect") }
                }
            }
        }
    }
}

@Composable
private fun AddRemoteConnectionCard(onAdd: (RemoteConnection) -> Unit) {
    var type by remember { mutableStateOf(RemoteType.SMB) }
    var host by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("New Connection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RemoteType.entries) { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host / IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = {
                onAdd(RemoteConnection(System.currentTimeMillis().toString(), type, host, type.defaultPort(), username, "/", name.ifBlank { host }))
            }, enabled = host.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Add Connection") }
        }
    }
}

private fun RemoteType.defaultPort() = when (this) {
    RemoteType.FTP -> 21; RemoteType.SFTP -> 22; RemoteType.SMB -> 445; RemoteType.WEBDAV -> 80
}

@Composable
private fun FtpServerTab() {
    var ftpEnabled by remember { mutableStateOf(false) }
    var ftpPort by remember { mutableStateOf("2121") }
    var ftpReadOnly by remember { mutableStateOf(false) }
    var ftpPassword by remember { mutableStateOf("") }
    var ftpRootPath by remember { mutableStateOf("/sdcard") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (ftpEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, null, modifier = Modifier.size(28.dp), tint = if (ftpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("FTP Server", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (ftpEnabled) "Running on port $ftpPort" else "Not running", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = ftpEnabled, onCheckedChange = { ftpEnabled = it })
                    }
                    if (ftpEnabled) {
                        Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text("ftp://YOUR_DEVICE_IP:$ftpPort", modifier = Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(value = ftpPort, onValueChange = { ftpPort = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Router, null) })
        }
        item {
            OutlinedTextField(value = ftpRootPath, onValueChange = { ftpRootPath = it }, label = { Text("Root Path") }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Folder, null) })
        }
        item {
            OutlinedTextField(value = ftpPassword, onValueChange = { ftpPassword = it }, label = { Text("Password (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Lock, null) })
        }
        item {
            ListItem(headlineContent = { Text("Read-Only Mode") }, supportingContent = { Text("Prevent file modification via FTP") }, trailingContent = { Switch(checked = ftpReadOnly, onCheckedChange = { ftpReadOnly = it }) })
        }
    }
}

@Composable
private fun ArchivesTab() {
    val formats = listOf("ZIP" to "Most compatible", "TAR.GZ" to "Unix/Linux standard", "7Z" to "Best compression", "TAR.BZ2" to "Good compression", "RAR" to "Extract only")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Supported Archive Formats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(formats) { (format, desc) ->
            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)) {
                        Text(format, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create Archive", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Select files in the file manager and use the context menu to create archives. Supported creation formats: ZIP, TAR.GZ, 7Z", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
