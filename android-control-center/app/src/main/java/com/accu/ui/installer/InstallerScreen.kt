package com.accu.ui.installer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.data.db.dao.InstallSessionDao
import com.accu.data.db.entities.InstallSessionEntity
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class InstallerState(
    val selectedApks: List<Uri> = emptyList(),
    val sessions: List<InstallSessionEntity> = emptyList(),
    val isInstalling: Boolean = false,
    val installProgress: Float = 0f,
    val installLog: List<String> = emptyList(),
    val replaceExisting: Boolean = true,
    val allowVersionDowngrade: Boolean = false,
    val grantAllPermissions: Boolean = false,
    val allowTest: Boolean = false,
    val doNotKillApp: Boolean = false,
    val bypassLowTargetSdkBlock: Boolean = false,
    val requestUpdateOwnership: Boolean = false,
    val snackbarMessage: String? = null,
    val connectionStatus: String = "",
)

@HiltViewModel
class InstallerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installSessionDao: InstallSessionDao,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(InstallerState())
    val state: StateFlow<InstallerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            installSessionDao.observeAll().collect { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
        }
        viewModelScope.launch {
            connectionManager.state.collect { cs ->
                _state.update { it.copy(connectionStatus = cs.name) }
            }
        }
    }

    fun addApk(uri: Uri)     { _state.update { it.copy(selectedApks = it.selectedApks + uri) } }
    fun removeApk(uri: Uri)  { _state.update { it.copy(selectedApks = it.selectedApks - uri) } }
    fun clearApks()          { _state.update { it.copy(selectedApks = emptyList(), installLog = emptyList()) } }
    fun clearSnackbar()      { _state.update { it.copy(snackbarMessage = null) } }

    fun install() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _state.value
            val apks = s.selectedApks
            if (apks.isEmpty()) {
                _state.update { it.copy(snackbarMessage = "No APK selected") }
                return@launch
            }

            _state.update { it.copy(isInstalling = true, installProgress = 0f, installLog = emptyList()) }

            val connectionState = connectionManager.state.value
            addLog("Connection: ${connectionState.name}")

            val flags = buildString {
                if (s.replaceExisting)           append(" -r")
                if (s.allowVersionDowngrade)     append(" -d")
                if (s.grantAllPermissions)       append(" -g")
                if (s.allowTest)                 append(" -t")
                if (s.doNotKillApp)              append(" --dont-kill")
                if (s.bypassLowTargetSdkBlock)   append(" --bypass-low-target-sdk-block")
                if (s.requestUpdateOwnership)    append(" --update-ownership")
            }

            var successCount = 0
            var failCount    = 0

            apks.forEachIndexed { idx, uri ->
                _state.update { it.copy(installProgress = idx.toFloat() / apks.size) }
                val name = getApkDisplayName(uri)
                addLog("━━━ Installing: $name ━━━")

                val result = installSingleApk(uri, flags)
                if (result.startsWith("✓")) { successCount++ } else { failCount++ }
                addLog(result)
            }

            _state.update { it.copy(
                isInstalling    = false,
                installProgress = 1f,
                snackbarMessage = if (failCount == 0)
                    "✓ All $successCount APK(s) installed"
                else
                    "$successCount installed, $failCount failed — see log",
            ) }

            // Persist session
            try {
                installSessionDao.insert(InstallSessionEntity(
                    packageName = apks.first().lastPathSegment ?: "",
                    apkPaths    = apks.map { it.toString() },
                    status      = if (failCount == 0) "SUCCESS" else "FAILED",
                    startedAt   = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis(),
                ))
            } catch (_: Exception) {}
        }
    }

    private suspend fun installSingleApk(uri: Uri, flags: String): String {
        // Step 1 — copy APK from ContentResolver URI to a real temp file on this device
        val tempFile = File(context.cacheDir, "accu_install_${System.currentTimeMillis()}.apk")
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { out -> input.copyTo(out) }
                } ?: return@withContext
            }
            if (!tempFile.exists() || tempFile.length() == 0L) {
                return "✗ Cannot read APK from storage (check permission)"
            }

            val apkSizeMb = "%.1f".format(tempFile.length() / 1_000_000f)
            addLog("  Size: ${apkSizeMb} MB")

            val connectionState = connectionManager.state.value
            val isRoot = connectionState == AccuConnectionManager.ConnectionState.CONNECTED_ROOT

            if (isRoot) {
                // Root: target IS the local device — pm install runs locally
                addLog("  Mode: Root (local device)")
                val result = connectionManager.exec("pm install$flags '${tempFile.absolutePath}'")
                return formatResult(result)
            } else {
                // ADB (wireless / OTG): push APK to target device, then install there
                val remotePath = "/data/local/tmp/accu_install_${System.currentTimeMillis()}.apk"
                addLog("  Mode: ADB → pushing to target…")

                val pushed = connectionManager.pushFile(tempFile.absolutePath, remotePath)
                if (!pushed) return "✗ Failed to transfer APK to target device — check ADB connection"

                addLog("  Push OK — running pm install on target…")
                val result = connectionManager.exec("pm install$flags '$remotePath'")
                // Cleanup remote
                try { connectionManager.exec("rm -f '$remotePath' 2>/dev/null") } catch (_: Exception) {}
                return formatResult(result)
            }
        } catch (e: Exception) {
            Timber.e(e, "installSingleApk failed")
            return "✗ Exception: ${e.message}"
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
    }

    private fun formatResult(result: com.accu.connection.ShellResult): String {
        val out = result.output.trim()
        val err = result.error.trim()
        return if (result.isSuccess || out.contains("Success", ignoreCase = true)) {
            "✓ ${out.ifBlank { "Installed successfully" }}"
        } else {
            val msg = when {
                out.isNotBlank() -> out
                err.isNotBlank() -> err
                else             -> "Unknown error (exit ${result.exitCode})"
            }
            "✗ $msg"
        }
    }

    private fun addLog(msg: String) {
        _state.update { it.copy(installLog = it.installLog + msg) }
    }

    private fun getApkDisplayName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (idx >= 0) cursor.getString(idx) else null
            }
        } catch (_: Exception) { null }
            ?: uri.lastPathSegment
            ?: uri.toString()
    }

    fun toggleOption(option: String, value: Boolean) {
        _state.update { s ->
            when (option) {
                "replaceExisting"         -> s.copy(replaceExisting = value)
                "allowVersionDowngrade"   -> s.copy(allowVersionDowngrade = value)
                "grantAllPermissions"     -> s.copy(grantAllPermissions = value)
                "allowTest"               -> s.copy(allowTest = value)
                "doNotKillApp"            -> s.copy(doNotKillApp = value)
                "bypassLowTargetSdkBlock" -> s.copy(bypassLowTargetSdkBlock = value)
                "requestUpdateOwnership"  -> s.copy(requestUpdateOwnership = value)
                else -> s
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    onBack: () -> Unit,
    viewModel: InstallerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.forEach { viewModel.addApk(it) } }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Installer Center",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Installer Center",
                        description = "Advanced APK installer — installs on the ADB-connected target device.\n\n• Picks APK from this device's storage\n• Pushes APK to target via adb push (or base64 stream fallback)\n• Runs pm install on target device\n• Supports all pm install flags\n• Works with split APK sets\n\nConnection: ${state.connectionStatus}"
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.selectedApks.isNotEmpty() && !state.isInstalling) {
                ExtendedFloatingActionButton(
                    onClick  = { viewModel.install() },
                    icon    = { Icon(Icons.Default.InstallMobile, null) },
                    text    = {
                        Text("Install ${state.selectedApks.size} APK${if (state.selectedApks.size > 1) "s" else ""} → ${state.connectionStatus}")
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // Connection status banner
            item {
                val isConnected = state.connectionStatus.startsWith("CONNECTED")
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape  = MaterialTheme.shapes.medium,
                    color  = if (isConnected)
                                 Color(0xFF00C853).copy(0.12f)
                             else
                                 MaterialTheme.colorScheme.errorContainer.copy(0.5f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            Modifier.size(18.dp),
                            tint = if (isConnected) Color(0xFF00C853) else MaterialTheme.colorScheme.error,
                        )
                        Column {
                            Text(
                                if (isConnected) "Connected — installs on target device"
                                else "Not connected — connect via ACCU Center first",
                                style      = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color      = if (isConnected) Color(0xFF00C853)
                                             else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                state.connectionStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Drop zone
            item {
                Card(
                    onClick = { apkPicker.launch("application/vnd.android.package-archive") },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(110.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp, MaterialTheme.colorScheme.primary.copy(0.5f),
                    ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FileUpload,
                                null,
                                Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Tap to select APK / split APKs", fontWeight = FontWeight.Bold)
                            Text(
                                "Selected APKs will be installed on the connected device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Selected APKs list
            if (state.selectedApks.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Android, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Selected APKs (${state.selectedApks.size})",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.clearApks() }) {
                                    Icon(Icons.Default.Clear, "Clear all")
                                }
                            }
                            state.selectedApks.forEach { uri ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.InsertDriveFile,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        uri.lastPathSegment?.substringAfterLast('%')?.substringAfterLast('/') ?: uri.toString(),
                                        style    = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick  = { viewModel.removeApk(uri) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Install Options
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Install Options",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        listOf(
                            Triple("replaceExisting",         "Replace Existing App",          "Reinstall / update — use this for most installs"),
                            Triple("allowVersionDowngrade",   "Allow Version Downgrade",        "Install an older version over a newer one"),
                            Triple("grantAllPermissions",     "Grant All Permissions",           "Auto-grant all requested permissions on install"),
                            Triple("allowTest",               "Allow Test-Only Packages",        "Install builds flagged android:testOnly=true"),
                            Triple("doNotKillApp",            "Do Not Kill App",                 "Keep the app running while updating"),
                            Triple("bypassLowTargetSdkBlock", "Bypass Low Target SDK Block",    "Allow apps targeting very old API levels"),
                            Triple("requestUpdateOwnership",  "Request Update Ownership",        "Claim this installer as the update owner"),
                        ).forEach { (key, title, subtitle) ->
                            val value = when (key) {
                                "replaceExisting"         -> state.replaceExisting
                                "allowVersionDowngrade"   -> state.allowVersionDowngrade
                                "grantAllPermissions"     -> state.grantAllPermissions
                                "allowTest"               -> state.allowTest
                                "doNotKillApp"            -> state.doNotKillApp
                                "bypassLowTargetSdkBlock" -> state.bypassLowTargetSdkBlock
                                "requestUpdateOwnership"  -> state.requestUpdateOwnership
                                else -> false
                            }
                            ListItem(
                                headlineContent   = { Text(title,    style = MaterialTheme.typography.bodyMedium) },
                                supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
                                trailingContent   = { Switch(checked = value, onCheckedChange = { viewModel.toggleOption(key, it) }) },
                                modifier          = Modifier.clickable { viewModel.toggleOption(key, !value) },
                            )
                        }
                    }
                }
            }

            // Progress bar + live log
            if (state.isInstalling || state.installLog.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Terminal, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Installation Log",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (state.isInstalling) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { state.installProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${(state.installProgress * 100).toInt()}%  —  installing…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            state.installLog.forEach { line ->
                                Text(
                                    line,
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color      = when {
                                        line.startsWith("✓")  -> Color(0xFF00E676)
                                        line.startsWith("✗")  -> Color(0xFFFF5252)
                                        line.startsWith("━━━") -> MaterialTheme.colorScheme.primary
                                        else                  -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Recent sessions
            if (state.sessions.isNotEmpty()) {
                item {
                    Text(
                        "Recent Sessions",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.sessions.take(10), key = { it.id }) { session ->
                    ListItem(
                        headlineContent   = {
                            Text(
                                session.packageName.ifBlank { "Unknown package" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                "${session.status} · ${session.apkPaths.size} APK(s)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = {
                            Surface(
                                shape    = androidx.compose.foundation.shape.CircleShape,
                                color    = when (session.status) {
                                    "SUCCESS" -> Color(0xFF00C853).copy(0.2f)
                                    "FAILED"  -> MaterialTheme.colorScheme.errorContainer
                                    else      -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        when (session.status) {
                                            "SUCCESS" -> Icons.Default.Check
                                            "FAILED"  -> Icons.Default.Close
                                            else      -> Icons.Default.Pending
                                        },
                                        null,
                                        Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
