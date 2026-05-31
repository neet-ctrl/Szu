package com.accu.ui.customization

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class FaqItem(val question: String, val answer: String)

val DARK_MODE_FAQ = listOf(
    FaqItem(
        question = "Why doesn't per-app dark mode work on some apps?",
        answer = "Some apps use custom rendering pipelines or have hardcoded light themes that bypass Android's force-dark mechanism. Per-app dark mode works best with apps that use system UI components."
    ),
    FaqItem(
        question = "Does this require root?",
        answer = "No! Both Shizuku and ADB modes are supported for rootless operation. Root is optional and provides enhanced compatibility."
    ),
    FaqItem(
        question = "What is auto dark mode scheduling?",
        answer = "Auto dark mode uses your device's location (or manual time settings) to calculate local sunrise and sunset times, then automatically switches dark mode on at sunset and off at sunrise."
    ),
    FaqItem(
        question = "Why does dark mode revert after reboot?",
        answer = "Make sure the app has RECEIVE_BOOT_COMPLETED permission granted and the startup service is enabled in settings. The boot receiver restores your dark mode configuration automatically."
    ),
    FaqItem(
        question = "Can I exclude specific apps from forced dark mode?",
        answer = "Yes! Go to the per-app dark mode section and add any apps to the exclusion list. These apps will always use their native theme regardless of system dark mode state."
    ),
    FaqItem(
        question = "What is the Xposed module option?",
        answer = "The Xposed integration provides deeper dark mode hooks for apps that normally resist force-dark, but requires an Xposed framework like LSPosed or EdXposed."
    ),
    FaqItem(
        question = "How does backup/restore work?",
        answer = "Your dark mode configuration (per-app settings, schedule, exclusions) is exported to a JSON file that you can save and restore later."
    ),
    FaqItem(
        question = "Dark mode flickers on app launch — why?",
        answer = "This is a known timing issue with force-dark. The Shizuku method is fastest. You can also try enabling 'Apply on window create' in advanced settings."
    ),
    FaqItem(
        question = "Which apps should I NOT force dark?",
        answer = "Avoid force-dark on banking apps (may fail security checks), camera apps (color accuracy), and any app where color accuracy is critical."
    ),
    FaqItem(
        question = "What's the difference between 'Dark' and 'Extra Dark'?",
        answer = "'Dark' applies Android's standard force-dark algorithm. 'Extra Dark' (pitch black) additionally sets OLED-friendly pure black backgrounds."
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarQFaqScreen(onBack: () -> Unit) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    var search by remember { mutableStateOf("") }

    val filtered = DARK_MODE_FAQ.filter {
        search.isBlank() || it.question.contains(search, ignoreCase = true) || it.answer.contains(search, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dark Mode FAQ") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search FAQ…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(filtered) { idx, item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth().clickable {
                            expandedIndex = if (expandedIndex == idx) null else idx
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.QuestionAnswer,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    item.question,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    if (expandedIndex == idx) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AnimatedVisibility(visible = expandedIndex == idx) {
                                Column {
                                    Spacer(Modifier.height(10.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        item.answer,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
