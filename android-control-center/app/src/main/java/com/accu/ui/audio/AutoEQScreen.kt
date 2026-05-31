package com.accu.ui.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class AutoEQResult(
    val id: String,
    val name: String,
    val type: String,
    val measurements: Int = 1,
    val curve: String = "ParametricEQ",
)

val SAMPLE_AUTOEQ_RESULTS = listOf(
    AutoEQResult("1", "Sony WH-1000XM5", "Over-Ear", 3, "Harman 2018"),
    AutoEQResult("2", "Bose QuietComfort 45", "Over-Ear", 2, "Harman 2018"),
    AutoEQResult("3", "Apple AirPods Pro (2nd Gen)", "In-Ear", 4, "Harman IEM 2019"),
    AutoEQResult("4", "Samsung Galaxy Buds2 Pro", "In-Ear", 2, "Harman IEM 2019"),
    AutoEQResult("5", "Sennheiser HD 650", "Open-Back", 5, "Flat"),
    AutoEQResult("6", "Beyerdynamic DT 990 Pro 250Ohm", "Open-Back", 3, "Flat"),
    AutoEQResult("7", "Audio-Technica ATH-M50x", "Closed-Back", 4, "Harman 2018"),
    AutoEQResult("8", "JBL Tune 760NC", "Over-Ear", 2, "Harman 2018"),
    AutoEQResult("9", "Jabra Evolve2 85", "Over-Ear", 2, "Harman 2018"),
    AutoEQResult("10", "Google Pixel Buds Pro", "In-Ear", 3, "Harman IEM 2019"),
)

enum class AutoEQTarget(val label: String) {
    HARMAN_2018("Harman 2018"),
    HARMAN_IEM_2019("Harman IEM 2019"),
    FLAT("Flat"),
    DIFFUSE_FIELD("Diffuse Field"),
    FREE_FIELD("Free Field"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEQScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTarget by remember { mutableStateOf(AutoEQTarget.HARMAN_2018) }
    var isLoading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(SAMPLE_AUTOEQ_RESULTS) }
    var appliedId by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }

    val filtered = results.filter { r ->
        (searchQuery.isBlank() || r.name.contains(searchQuery, ignoreCase = true)) &&
                (typeFilter == null || r.type == typeFilter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoEQ") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { isLoading = true; /* Simulate */ isLoading = false }) {
                        Icon(Icons.Default.Refresh, "Refresh database")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search headphones / earphones…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target Curve", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AutoEQTarget.entries) { target ->
                            FilterChip(
                                selected = selectedTarget == target,
                                onClick = { selectedTarget = target },
                                label = { Text(target.label) },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Over-Ear", "In-Ear", "Open-Back", "Closed-Back").forEach { type ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { typeFilter = if (typeFilter == type) null else type },
                        label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("${filtered.size} results", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(filtered, key = { it.id }) { result ->
                    AutoEQResultCard(
                        result = result,
                        isApplied = appliedId == result.id,
                        onApply = { appliedId = result.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoEQResultCard(
    result: AutoEQResult,
    isApplied: Boolean,
    onApply: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isApplied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                when (result.type) {
                    "In-Ear" -> Icons.Default.Hearing
                    "Open-Back" -> Icons.Default.GraphicEq
                    else -> Icons.Default.Headphones
                },
                null,
                modifier = Modifier.size(32.dp),
                tint = if (isApplied) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(result.type, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${result.measurements} measurements", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (isApplied) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            OutlinedButton(onClick = onApply) { Text(if (isApplied) "Applied" else "Apply") }
        }
    }
}
