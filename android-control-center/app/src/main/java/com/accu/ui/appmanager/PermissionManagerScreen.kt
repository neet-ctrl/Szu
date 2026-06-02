package com.accu.ui.appmanager

import androidx.compose.foundation.clickable
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
import com.accu.connection.AccuConnectionManager
import com.accu.data.repositories.AppRepository
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import timber.log.Timber

data class PermMgrState(
    val groups: List<PermGroup> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedGroup: String = "",
    val errorMessage: String = "",
)
data class PermGroup(val permission: String, val grants: List<PermGrant>)
data class PermGrant(val packageName: String, val appName: String, val isGranted: Boolean)

@HiltViewModel
class PermissionManagerViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(PermMgrState())
    val state: StateFlow<PermMgrState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Get dangerous permissions from target device
                val dangerousRaw = connectionManager.exec("pm list permissions -d 2>/dev/null").output
                val dangerousSet = dangerousRaw.lines()
                    .filter { it.startsWith("permission:") }
                    .map { it.removePrefix("permission:").trim() }
                    .filter { it.startsWith("android.permission.") }
                    .toSet()

                if (dangerousSet.isEmpty()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "No permissions found — check ACCU connection") }
                    return@launch
                }

                val permMap = mutableMapOf<String, MutableList<PermGrant>>()
                // Cache of package → set of granted permissions (avoid re-querying same package)
                val grantCache = mutableMapOf<String, Set<String>>()

                // Step 2: For each dangerous permission, get packages that declared it
                dangerousSet.forEach { perm ->
                    val pkgRaw = connectionManager.exec("pm list packages --permission $perm 2>/dev/null").output
                    val packages = pkgRaw.lines()
                        .filter { it.startsWith("package:") }
                        .map { it.removePrefix("package:").trim() }
                        .filter { it.isNotEmpty() }

                    if (packages.isNotEmpty()) {
                        val grants = packages.map { pkg ->
                            // Step 3: Determine grant status — parse from dumpsys (cached per package)
                            val grantedPerms = grantCache.getOrPut(pkg) {
                                parseGrantedPerms(pkg)
                            }
                            val appName = pkg.split(".").lastOrNull()
                                ?.replaceFirstChar { it.uppercase() } ?: pkg
                            PermGrant(pkg, appName, perm in grantedPerms)
                        }
                        permMap[perm] = grants.toMutableList()
                    }
                }

                val groups = permMap.entries
                    .map { (perm, grants) -> PermGroup(perm, grants.sortedByDescending { it.isGranted }) }
                    .sortedBy { it.permission }

                _state.update { it.copy(groups = groups, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "PermissionManagerViewModel: failed to load from target device")
                _state.update { it.copy(isLoading = false, errorMessage = "Failed to load — check ACCU connection") }
            }
        }
    }

    /** Parse the set of granted runtime permissions for a package from the target device. */
    private suspend fun parseGrantedPerms(pkg: String): Set<String> {
        return try {
            val dump = connectionManager.exec("dumpsys package $pkg 2>/dev/null").output
            val granted = mutableSetOf<String>()
            var inRuntime = false
            for (line in dump.lines()) {
                val t = line.trimStart()
                when {
                    t.startsWith("runtime permissions:") || t.startsWith("install permissions:") -> inRuntime = true
                    inRuntime && (t.startsWith("User ") || t.startsWith("declared permissions:") ||
                        t.startsWith("requested permissions:") || t.startsWith("shared users:")) -> inRuntime = false
                    inRuntime && t.contains("granted=true") -> {
                        val name = t.substringBefore(":").trim()
                        if (name.contains('.') && !name.contains(' ')) granted.add(name)
                    }
                }
            }
            granted
        } catch (_: Exception) { emptySet() }
    }

    fun onSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun revoke(pkg: String, perm: String) {
        viewModelScope.launch {
            val ok = appRepository.revokePermission(pkg, perm)
            if (ok) {
                _state.update { s ->
                    s.copy(groups = s.groups.map { g ->
                        if (g.permission != perm) g
                        else g.copy(grants = g.grants.map { gr ->
                            if (gr.packageName == pkg) gr.copy(isGranted = false) else gr
                        })
                    })
                }
            }
        }
    }
    fun grant(pkg: String, perm: String) {
        viewModelScope.launch {
            val ok = appRepository.grantPermission(pkg, perm)
            if (ok) {
                _state.update { s ->
                    s.copy(groups = s.groups.map { g ->
                        if (g.permission != perm) g
                        else g.copy(grants = g.grants.map { gr ->
                            if (gr.packageName == pkg) gr.copy(isGranted = true) else gr
                        })
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerScreen(
    onBack: () -> Unit,
    viewModel: PermissionManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filtered = state.groups.filter { it.permission.contains(state.searchQuery, true) }

    Scaffold(topBar = {
        Column {
            ACCTopBar(
                title = "Permission Manager",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Permission Manager",
                        description = "View and revoke runtime permissions across all apps on the TARGET device.\n\nData comes from the ADB/root connected device — not the local phone.\n\nSearch any permission (CAMERA, LOCATION, MICROPHONE, etc.) to see which apps have it granted.\n\n• Revoke: removes a granted permission\n• Grant: re-grants a revoked permission\n• Protected permissions cannot be changed\n\nChanges apply instantly via ACCU (pm revoke / pm grant)."
                    )
                }
            )
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearch,
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search permissions…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {}
        }
    }) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading permissions from target device…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("This queries each dangerous permission on the connected device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.errorMessage.isNotEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No permissions found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(filtered, key = { it.permission }) { group ->
                        var expanded by remember { mutableStateOf(false) }
                        val granted = group.grants.count { it.isGranted }
                        ListItem(
                            headlineContent = { Text(group.permission.removePrefix("android.permission."), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("$granted / ${group.grants.size} apps granted", style = MaterialTheme.typography.bodySmall) },
                            leadingContent = {
                                Icon(
                                    when {
                                        group.permission.contains("CAMERA") -> Icons.Default.CameraAlt
                                        group.permission.contains("LOCATION") -> Icons.Default.LocationOn
                                        group.permission.contains("CONTACTS") -> Icons.Default.Contacts
                                        group.permission.contains("STORAGE") -> Icons.Default.Storage
                                        group.permission.contains("MICROPHONE") || group.permission.contains("RECORD") -> Icons.Default.Mic
                                        group.permission.contains("PHONE") -> Icons.Default.Phone
                                        else -> Icons.Default.Shield
                                    }, null,
                                )
                            },
                            trailingContent = { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
                        if (expanded) {
                            group.grants.take(20).forEach { grant ->
                                ListItem(
                                    headlineContent = { Text(grant.appName, style = MaterialTheme.typography.bodySmall) },
                                    supportingContent = { Text(grant.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = { Spacer(Modifier.width(24.dp)) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                                color = if (grant.isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            ) {
                                                Text(
                                                    if (grant.isGranted) "Granted" else "Denied",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                            if (grant.isGranted) {
                                                Spacer(Modifier.width(4.dp))
                                                IconButton(onClick = { viewModel.revoke(grant.packageName, group.permission) }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.Block, "Revoke", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                                }
                                            } else {
                                                Spacer(Modifier.width(4.dp))
                                                IconButton(onClick = { viewModel.grant(grant.packageName, group.permission) }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.Check, "Grant", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
