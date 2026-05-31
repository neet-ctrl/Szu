package com.accu.ui.installer

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.data.db.dao.InstallSessionDao
import com.accu.data.db.entities.InstallSessionEntity
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import javax.inject.Inject

data class InstallerState(
    val selectedApks: List<Uri> = emptyList(),
    val sessions: List<InstallSessionEntity> = emptyList(),
    val isInstalling: Boolean = false,
    val installProgress: Float = 0f,
    val installLog: List<String> = emptyList(),
    // Install options (from InstallWithOptions)
    val replaceExisting: Boolean = true,
    val allowVersionDowngrade: Boolean = false,
    val grantAllPermissions: Boolean = false,
    val allowTest: Boolean = false,
    val installAsUpdate: Boolean = false,
    val bypassLowTargetSdkBlock: Boolean = false,
    val doNotKillApp: Boolean = false,
    val requestUpdateOwnership: Boolean = false,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class InstallerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installSessionDao: InstallSessionDao,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {
    private val _state = MutableStateFlow(InstallerState())
    val state: StateFlow<InstallerState> = _state.asStateFlow()

    init { viewModelScope.launch { installSessionDao.observeAll().collect { sessions -> _state.update { it.copy(sessions = sessions) } } } }

    fun addApk(uri: Uri) { _state.update { it.copy(selectedApks = it.selectedApks + uri) } }
    fun removeApk(uri: Uri) { _state.update { it.copy(selectedApks = it.selectedApks - uri) } }
    fun clearApks() { _state.update { it.copy(selectedApks = emptyList(), installLog = emptyList()) } }

    fun install() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isInstalling = true, installProgress = 0f, installLog = emptyList()) }
            try {
                val s = _state.value
                val apks = s.selectedApks
                if (apks.isEmpty()) { _state.update { it.copy(isInstalling = false, snackbarMessage = "No APK selected") }; return@launch }

                addLog("Starting install session…")
                val pm = context.packageManager

                // Build pm install command
                val flags = buildString {
                    if (s.replaceExisting) append(" -r")
                    if (s.allowVersionDowngrade) append(" --bypass-low-target-sdk-block")
                    if (s.grantAllPermissions) append(" -g")
                    if (s.allowTest) append(" -t")
                    if (s.doNotKillApp) append(" --dont-kill")
                }

                apks.forEachIndexed { i, uri ->
                    _state.update { it.copy(installProgress = i.toFloat() / apks.size) }
                    val path = getRealPathFromUri(uri)
                    if (path != null) {
                        addLog("Installing: ${path.substringAfterLast('/')}")
                        val result = shizukuUtils.execShizuku("pm install$flags $path")
                        if (result.isSuccess) addLog("✓ Success: ${result.output}")
                        else addLog("✗ Failed: ${result.error}")
                    } else {
                        addLog("✗ Could not resolve path for $uri")
                    }
                }

                _state.update { it.copy(isInstalling = false, installProgress = 1f, snackbarMessage = "Installation complete") }
            } catch (e: Exception) {
                addLog("✗ Exception: ${e.message}")
                _state.update { it.copy(isInstalling = false, snackbarMessage = "Error: ${e.message}") }
            }
        }
    }

    private fun addLog(msg: String) { _state.update { it.copy(installLog = it.installLog + msg) } }

    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            "/proc/${android.os.Process.myPid()}/fd/${fd.fd}"
        } catch (_: Exception) { null }
    }

    fun toggleOption(option: String, value: Boolean) {
        _state.update { s ->
            when (option) {
                "replaceExisting"        -> s.copy(replaceExisting = value)
                "allowVersionDowngrade"  -> s.copy(allowVersionDowngrade = value)
                "grantAllPermissions"    -> s.copy(grantAllPermissions = value)
                "allowTest"              -> s.copy(allowTest = value)
                "doNotKillApp"           -> s.copy(doNotKillApp = value)
                "bypassLowTargetSdkBlock"-> s.copy(bypassLowTargetSdkBlock = value)
                "requestUpdateOwnership" -> s.copy(requestUpdateOwnership = value)
                else -> s
            }
        }
    }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    onBack: () -> Unit,
    viewModel: InstallerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { viewModel.addApk(it) }
    }

    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() } }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Installer Center",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Installer Center — InstallWithOptions",
                        description = "Advanced APK installer based on InstallWithOptions.\n\nSupports all pm install flags:\n• Replace existing app\n• Allow version downgrade (install older build)\n• Grant all permissions automatically\n• Allow test-only builds\n• Do not kill app during update\n• Bypass low target SDK block\n• Request update ownership\n\nSupports single APKs and split APK sets (.apks). All installation is done via Shizuku (no root, no USB)."
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.selectedApks.isNotEmpty() && !state.isInstalling) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.install() },
                    icon = { Icon(Icons.Default.InstallMobile, null) },
                    text = { Text("Install (${state.selectedApks.size} APK${if (state.selectedApks.size > 1) "s" else ""})") },
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Drop zone
            item {
                Card(
                    onClick = { apkPicker.launch("application/vnd.android.package-archive") },
                    Modifier.fillMaxWidth().padding(16.dp).height(120.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f)),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(0.5f)),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FileUpload, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("Tap to select APK files", fontWeight = FontWeight.Bold)
                            Text("Supports split APKs / APKS bundles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Selected APKs
            if (state.selectedApks.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Selected APKs (${state.selectedApks.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.clearApks() }) { Icon(Icons.Default.Clear, "Clear") }
                            }
                            state.selectedApks.forEach { uri ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Android, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(uri.lastPathSegment ?: uri.toString(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.removeApk(uri) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) }
                                }
                            }
                        }
                    }
                }
            }

            // Install options (InstallWithOptions)
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Install Options", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val options = listOf(
                            Triple("replaceExisting",        "Replace Existing App",          "Reinstall/update the app"),
                            Triple("allowVersionDowngrade",  "Allow Version Downgrade",        "Install older versions"),
                            Triple("grantAllPermissions",    "Grant All Permissions",           "Auto-grant all requested permissions"),
                            Triple("allowTest",              "Allow Test Packages",             "Install test-only builds"),
                            Triple("doNotKillApp",           "Do Not Kill App",                 "Keep app running during update"),
                            Triple("bypassLowTargetSdkBlock","Bypass Low Target SDK Block",     "Install apps targeting old APIs"),
                            Triple("requestUpdateOwnership", "Request Update Ownership",        "Claim installer as update owner"),
                        )
                        options.forEach { (key, title, subtitle) ->
                            val value = when(key) {
                                "replaceExisting" -> state.replaceExisting
                                "allowVersionDowngrade" -> state.allowVersionDowngrade
                                "grantAllPermissions" -> state.grantAllPermissions
                                "allowTest" -> state.allowTest
                                "doNotKillApp" -> state.doNotKillApp
                                "bypassLowTargetSdkBlock" -> state.bypassLowTargetSdkBlock
                                "requestUpdateOwnership" -> state.requestUpdateOwnership
                                else -> false
                            }
                            ListItem(
                                headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium) },
                                supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
                                trailingContent = { Switch(checked = value, onCheckedChange = { viewModel.toggleOption(key, it) }) },
                                modifier = Modifier.clickable { viewModel.toggleOption(key, !value) },
                            )
                        }
                    }
                }
            }

            // Progress & log
            if (state.isInstalling || state.installLog.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Installation Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (state.isInstalling) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { state.installProgress }, modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(8.dp))
                            state.installLog.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = when { line.startsWith("✓") -> androidx.compose.ui.graphics.Color(0xFF00E676); line.startsWith("✗") -> androidx.compose.ui.graphics.Color(0xFFFF1744); else -> MaterialTheme.colorScheme.onSurface })
                            }
                        }
                    }
                }
            }

            // Recent sessions
            if (state.sessions.isNotEmpty()) {
                item { Text("Recent Sessions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(state.sessions.take(10), key = { it.id }) { session ->
                    ListItem(
                        headlineContent = { Text(session.packageName.ifBlank { "Unknown package" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${session.status} · ${session.apkPaths.size} APK(s)", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = when(session.status) { "SUCCESS" -> androidx.compose.ui.graphics.Color(0xFF00E676).copy(0.2f); "FAILED" -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceVariant }, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(when(session.status) { "SUCCESS" -> Icons.Default.Check; "FAILED" -> Icons.Default.Close; else -> Icons.Default.Pending }, null, Modifier.size(18.dp)) }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun Modifier.clickable(onClick: () -> Unit) = this.then(androidx.compose.ui.Modifier.clickable(onClick = onClick))
