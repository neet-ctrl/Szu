package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.accu.sdkdemo.data.ApiMethod
import com.accu.sdkdemo.ui.components.SectionHeader
import com.accu.sdkdemo.ui.components.StatusBadge
import com.accu.sdkdemo.ui.components.StatusColor
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun ApiExplorerScreen(vm: MainViewModel) {
    val explorerResult by vm.apiExplorerResult.collectAsState()
    val methods = remember { vm.allApiMethods() }
    var filterCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember(methods) { listOf("All") + methods.map { it.category }.distinct() }
    val filtered = remember(methods, filterCategory, searchQuery) {
        methods.filter {
            (filterCategory == "All" || it.category == filterCategory) &&
            (searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.description.contains(searchQuery, true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("All 39 ACCU API Methods")
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    label = { Text("Search methods…") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.take(4).forEach { cat ->
                        FilterChip(selected = filterCategory == cat, onClick = { filterCategory = cat }, label = { Text(cat, fontSize = 10.sp) }, modifier = Modifier.wrapContentWidth())
                    }
                }
                if (categories.size > 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.drop(4).forEach { cat ->
                            FilterChip(selected = filterCategory == cat, onClick = { filterCategory = cat }, label = { Text(cat, fontSize = 10.sp) }, modifier = Modifier.wrapContentWidth())
                        }
                    }
                }
                Text("${filtered.size} methods shown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Last result banner
        explorerResult?.let { (method, result) ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Last result: $method", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(result, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 3)
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.name }) { method ->
                ApiMethodCard(method, explorerResult?.first == method.name) { vm.executeApiMethod(method) }
            }
        }
    }
}

@Composable
private fun ApiMethodCard(method: ApiMethod, isLastResult: Boolean, onExecute: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isLastResult) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(method.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text(method.signature, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(method.scope, if (method.scope == "None") StatusColor.GREY else StatusColor.GREEN)
            }
            Text(method.description, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text(method.category, fontSize = 10.sp) })
                    AssistChip(onClick = {}, label = { Text("tx#${method.transactionId}", fontSize = 10.sp) })
                }
                val testable = method.name in listOf("ping","getVersion","getUid","getPid","getAccuVersion","checkPermission",
                    "hasScope(SHELL)","hasScope(PACKAGE_MANAGE)","hasScope(PERMISSIONS)","hasScope(SETTINGS)","hasScope(LOCALE)",
                    "exec","execAndGetOutput","readSecureSetting","readGlobalSetting","readSystemSetting")
                if (testable) {
                    OutlinedButton(onClick = onExecute, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Test", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
