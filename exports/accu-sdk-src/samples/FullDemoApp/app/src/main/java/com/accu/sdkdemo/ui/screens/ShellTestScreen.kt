package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdkdemo.viewmodel.MainViewModel

private val PRESET_COMMANDS = listOf(
    "id",
    "whoami",
    "uname -a",
    "pm list packages -3",
    "getprop ro.build.version.release",
    "getprop ro.build.fingerprint",
    "dumpsys battery | head -15",
    "ls /data/",
    "echo 'ACCU_TEST_OK'",
    "cat /proc/version",
)

@Composable
fun ShellTestScreen(vm: MainViewModel) {
    val output    by vm.shellOutput.collectAsState()
    val isRunning by vm.isShellRunning.collectAsState()
    var command   by remember { mutableStateOf("id") }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        // Input area
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = command,
                    onValueChange = { command = it },
                    label         = { Text("Shell Command") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    leadingIcon   = { Text("$", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp)) },
                    trailingIcon  = {
                        if (command.isNotBlank())
                            IconButton(onClick = { command = "" }) { Icon(Icons.Default.Clear, null) }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.execShell(command) }, enabled = !isRunning && command.isNotBlank(), modifier = Modifier.weight(1f)) {
                        if (isRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sync")
                    }
                    OutlinedButton(onClick = { vm.execShellAsync(command) }, enabled = !isRunning && command.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stream, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Async")
                    }
                }
            }
        }

        // Preset chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PRESET_COMMANDS.forEach { preset ->
                FilterChip(selected = command == preset, onClick = { command = preset }, label = { Text(preset, fontSize = 11.sp) })
            }
        }

        // Output area
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Output", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (output.isNotBlank()) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(output)) }) { Text("Copy", fontSize = 12.sp) }
                    TextButton(onClick = { vm.clearShellOutput() }) { Text("Clear", fontSize = 12.sp) }
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text(
                    text = if (output.isBlank()) "Run a command above to see output here…" else output,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (output.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
