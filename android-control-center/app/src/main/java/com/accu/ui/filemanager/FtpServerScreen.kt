package com.accu.ui.filemanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpServerScreen(onBack: () -> Unit = {}) {
    var isRunning by remember { mutableStateOf(false) }

    // FTP settings
    var ftpEnabled by remember { mutableStateOf(true) }
    var ftpPort by remember { mutableStateOf("2121") }
    var ftpUsername by remember { mutableStateOf("") }
    var ftpPassword by remember { mutableStateOf("") }
    var ftpAnonymous by remember { mutableStateOf(false) }
    var ftpRootPath by remember { mutableStateOf("/sdcard") }
    var ftpReadOnly by remember { mutableStateOf(false) }
    var ftpPassiveMode by remember { mutableStateOf(true) }

    // SFTP settings
    var sftpEnabled by remember { mutableStateOf(false) }
    var sftpPort by remember { mutableStateOf("2222") }

    // SMB settings
    var smbEnabled by remember { mutableStateOf(false) }
    var smbShareName by remember { mutableStateOf("Android") }

    val localIp = remember { "192.168.1.42" }
    val ftpUrl = "ftp://$localIp:$ftpPort"
    val sftpUrl = "sftp://$localIp:$sftpPort"

    Scaffold(
        topBar = { ACCTopBar(title = "Network File Server", onBack = onBack) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Server status card
            item {
                ElevatedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isRunning) Icons.Default.CloudUpload else Icons.Default.CloudOff,
                            null,
                            tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isRunning) "Server Running" else "Server Stopped",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isRunning) {
                                if (ftpEnabled) Text(ftpUrl, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                if (sftpEnabled) Text(sftpUrl, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Button(
                            onClick = { isRunning = !isRunning },
                            colors = if (isRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                        ) { Text(if (isRunning) "Stop" else "Start") }
                    }
                }
            }

            // Connection info
            if (isRunning) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Connect from a PC or file manager:", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            if (ftpEnabled) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("FTP", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(ftpUrl, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = {}) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(18.dp)) }
                                    IconButton(onClick = {}) { Icon(Icons.Default.Share, "Share", Modifier.size(18.dp)) }
                                }
                            }
                            if (sftpEnabled) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("SFTP", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(sftpUrl, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = {}) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }

            // FTP Settings
            item {
                Spacer(Modifier.height(8.dp))
                SLabel("FTP Server")
                PrefRow("Enable FTP", "Standard file transfer protocol", ftpEnabled) { ftpEnabled = it }
                if (ftpEnabled) {
                    PrefField("Port", ftpPort) { ftpPort = it }
                    PrefField("Root directory", ftpRootPath) { ftpRootPath = it }
                    PrefRow("Anonymous login", "No username/password required", ftpAnonymous) { ftpAnonymous = it }
                    if (!ftpAnonymous) {
                        PrefField("Username", ftpUsername) { ftpUsername = it }
                        PrefField("Password", ftpPassword) { ftpPassword = it }
                    }
                    PrefRow("Read-only", "Prevent file modifications", ftpReadOnly) { ftpReadOnly = it }
                    PrefRow("Passive mode", "Use PASV for NAT/firewall compatibility", ftpPassiveMode) { ftpPassiveMode = it }
                }
                HorizontalDivider()
            }

            // SFTP Settings
            item {
                SLabel("SFTP Server (SSH File Transfer)")
                PrefRow("Enable SFTP", "Encrypted file transfer via SSH", sftpEnabled) { sftpEnabled = it }
                if (sftpEnabled) {
                    PrefField("SFTP port", sftpPort) { sftpPort = it }
                    Text("Note: Authentication uses the same FTP credentials.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
                HorizontalDivider()
            }

            // SMB Settings
            item {
                SLabel("SMB / Windows File Sharing")
                PrefRow("Enable SMB (Samba)", "Share files with Windows/macOS network", smbEnabled) { smbEnabled = it }
                if (smbEnabled) {
                    PrefField("Share name", smbShareName) { smbShareName = it }
                    Text("Access via: \\\\$localIp\\$smbShareName (Windows) or smb://$localIp/$smbShareName (macOS)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
                HorizontalDivider()
            }

            item {
                SLabel("Status")
                ListItem(headlineContent = { Text("Local IP") }, trailingContent = { Text(localIp, fontFamily = FontFamily.Monospace) })
                ListItem(headlineContent = { Text("Wi-Fi network") }, trailingContent = { Text("HomeNetwork_5G") })
                ListItem(headlineContent = { Text("Active connections") }, trailingContent = { Text(if (isRunning) "1" else "0") })
            }
        }
    }
}

@Composable
private fun SLabel(text: String) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }

@Composable
private fun PrefRow(title: String, sub: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(sub, fontSize = 12.sp) }, trailingContent = { Switch(checked = checked, onCheckedChange = onChanged) })
}

@Composable
private fun PrefField(label: String, value: String, onChanged: (String) -> Unit) {
    OutlinedTextField(value, onChanged, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true)
}
