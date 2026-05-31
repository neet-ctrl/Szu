package com.accu.ui.appmanager

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.accu.data.repositories.AppRepository
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext

data class PermMgrState(
    val groups: List<PermGroup> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedGroup: String = "",
)
data class PermGroup(val permission: String, val grants: List<PermGrant>)
data class PermGrant(val packageName: String, val appName: String, val isGranted: Boolean)

@HiltViewModel
class PermissionManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PermMgrState())
    val state: StateFlow<PermMgrState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val permMap = mutableMapOf<String, MutableList<PermGrant>>()
            packages.forEach { pkg ->
                val perms = pkg.requestedPermissions ?: return@forEach
                val flags = pkg.requestedPermissionsFlags ?: return@forEach
                val appName = try { pm.getApplicationLabel(pkg.applicationInfo!!).toString() } catch (_: Exception) { pkg.packageName }
                perms.forEachIndexed { i, perm ->
                    if (!perm.startsWith("android.permission.")) return@forEachIndexed
                    val granted = flags.getOrNull(i)?.and(PackageManager.GET_PERMISSIONS) != 0
                    permMap.getOrPut(perm) { mutableListOf() }.add(PermGrant(pkg.packageName, appName, granted))
                }
            }
            val groups = permMap.entries.map { (perm, grants) -> PermGroup(perm, grants.sortedByDescending { it.isGranted }) }
                .sortedBy { it.permission }
            _state.update { it.copy(groups = groups, isLoading = false) }
        }
    }

    fun onSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun revoke(pkg: String, perm: String) { viewModelScope.launch { appRepository.revokePermission(pkg, perm) } }
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
                        description = "View and revoke runtime permissions across all apps.\n\nSearch any permission (CAMERA, LOCATION, MICROPHONE, etc.) to see which apps have it granted.\n\n• Revoke: removes a granted permission from an app\n• Grant: re-grants a previously revoked permission\n• Protected permissions (signature-level) cannot be changed\n\nChanges apply instantly via Shizuku (pm revoke / pm grant)."
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
                                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), color = if (grant.isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(if (grant.isGranted) "Granted" else "Denied", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    if (grant.isGranted) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(onClick = { viewModel.revoke(grant.packageName, group.permission) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Block, "Revoke", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
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

private fun Modifier.clickable(onClick: () -> Unit) = this.then(androidx.compose.ui.Modifier.clickable(onClick = onClick))
