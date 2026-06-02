package com.accu.ui.appmanager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.db.AppDatabase
import com.accu.data.db.entities.AppTagEntity
import com.accu.data.db.entities.TaggedPackageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InureTagsViewModel @Inject constructor(private val db: AppDatabase) : ViewModel() {
    val tags: StateFlow<List<AppTagEntity>> = db.appTagDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTaggedPackages(tagId: Long) = db.taggedPackageDao().observeForTag(tagId)

    fun createTag(name: String, color: String) = viewModelScope.launch {
        db.appTagDao().insert(AppTagEntity(name = name, color = color))
    }

    fun deleteTag(tag: AppTagEntity) = viewModelScope.launch {
        db.appTagDao().delete(tag)
        db.taggedPackageDao().deleteForTag(tag.id)
    }

    fun removePackageFromTag(tagId: Long, pkg: String) = viewModelScope.launch {
        db.taggedPackageDao().delete(tagId, pkg)
    }
}

private val TAG_COLORS = listOf(
    "#6750A4", "#B5261E", "#0E6E3C", "#C4511B",
    "#065789", "#6E3E90", "#1A6262", "#7D4700",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InureTagsScreen(
    onBack: () -> Unit = {},
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val vm: InureTagsViewModel = hiltViewModel()
    val tags by vm.tags.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var expandedTagId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Tags") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Tag") },
            )
        }
    ) { padding ->
        if (tags.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.Label, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No tags yet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Create tags to organize your apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showCreateDialog = true }) { Text("Create First Tag") }
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tags, key = { it.id }) { tag ->
                    TagCard(
                        tag = tag,
                        isExpanded = expandedTagId == tag.id,
                        onToggle = { expandedTagId = if (expandedTagId == tag.id) null else tag.id },
                        onDelete = { vm.deleteTag(tag) },
                        onRemovePackage = { pkg -> vm.removePackageFromTag(tag.id, pkg) },
                        onNavigateToApp = onNavigateToAppDetail,
                        getPackages = { vm.getTaggedPackages(tag.id) },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showCreateDialog) {
        CreateTagDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color ->
                vm.createTag(name, color)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun TagCard(
    tag: AppTagEntity,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onRemovePackage: (String) -> Unit,
    onNavigateToApp: (String) -> Unit,
    getPackages: () -> Flow<List<TaggedPackageEntity>>,
) {
    val tagColor = remember(tag.color) {
        try { Color(android.graphics.Color.parseColor(tag.color)) } catch (_: Exception) { Color(0xFF6750A4.toInt()) }
    }
    val packages by getPackages().collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            // Tag header
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tagColor), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Label, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(tag.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("${packages.size} app${if (packages.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expanded package list
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider()
                    if (packages.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No apps tagged yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        packages.forEach { pkg ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onNavigateToApp(pkg.packageName) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pkg.appName.ifEmpty { pkg.packageName }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pkg.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onRemovePackage(pkg.packageName) }) {
                                    Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Tag") },
            text = { Text("Delete \"${tag.name}\"? This will remove it from all tagged apps.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton({ showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CreateTagDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var tagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(TAG_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    tagName, { tagName = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAG_COLORS.forEach { hex ->
                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c)
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selectedColor == hex) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (tagName.isNotBlank()) onCreate(tagName.trim(), selectedColor) },
                enabled = tagName.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
