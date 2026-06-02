package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdkdemo.data.AppInfo
import com.accu.sdkdemo.ui.components.SectionHeader
import com.accu.sdkdemo.viewmodel.MainViewModel

private data class LocaleOption(val tag: String, val label: String, val flag: String)

private val LOCALE_OPTIONS = listOf(
    LocaleOption("",    "System Default", "🔄"),
    LocaleOption("en",  "English",        "🇬🇧"),
    LocaleOption("en-US","English (US)",  "🇺🇸"),
    LocaleOption("hi",  "Hindi",          "🇮🇳"),
    LocaleOption("ja",  "Japanese",       "🇯🇵"),
    LocaleOption("ko",  "Korean",         "🇰🇷"),
    LocaleOption("zh-CN","Chinese (Simplified)","🇨🇳"),
    LocaleOption("zh-TW","Chinese (Traditional)","🇹🇼"),
    LocaleOption("fr",  "French",         "🇫🇷"),
    LocaleOption("de",  "German",         "🇩🇪"),
    LocaleOption("es",  "Spanish",        "🇪🇸"),
    LocaleOption("ar",  "Arabic",         "🇸🇦"),
    LocaleOption("ru",  "Russian",        "🇷🇺"),
    LocaleOption("pt",  "Portuguese",     "🇧🇷"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocaleTestScreen(vm: MainViewModel) {
    val apps        by vm.apps.collectAsState()
    val localeResult by vm.localeResult.collectAsState()
    var search      by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedLocale by remember { mutableStateOf(LOCALE_OPTIONS[0]) }

    val filtered = remember(apps, search) {
        if (search.isBlank()) apps.take(50) else apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (localeResult.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                    Text(localeResult, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }

        // Step 1: Select app
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Step 1 — Select App (requires LOCALE scope)")
                if (selectedApp != null) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(selectedApp!!.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(selectedApp!!.packageName, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            }
                            IconButton(onClick = { selectedApp = null }) { Icon(Icons.Default.Clear, null) }
                        }
                    }
                }
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search apps to select…") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } })
                if (search.isNotBlank() || selectedApp == null) {
                    filtered.take(8).forEach { app ->
                        OutlinedButton(onClick = { selectedApp = app; search = "" }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(Icons.Default.Android, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(app.label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Step 2: Select locale
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Step 2 — Select Locale")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LOCALE_OPTIONS.forEach { locale ->
                        FilterChip(selected = selectedLocale == locale, onClick = { selectedLocale = locale },
                            label = { Text("${locale.flag} ${locale.label}", fontSize = 11.sp) })
                    }
                }
                if (selectedLocale.tag.isNotBlank()) {
                    Text("Locale tag: \"${selectedLocale.tag}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Empty string = revert to system default locale", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Step 3: Apply
        Button(onClick = {
            selectedApp?.let { app -> vm.setLocale(app.packageName, selectedLocale.tag) }
        }, modifier = Modifier.fillMaxWidth(), enabled = selectedApp != null) {
            Icon(Icons.Default.Language, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Apply ${selectedLocale.flag} ${selectedLocale.label}" + (selectedApp?.let { " to ${it.label}" } ?: ""))
        }

        // Info
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notes", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("• setApplicationLocale() requires Android 13 (API 33)+", style = MaterialTheme.typography.bodySmall)
                Text("• The LOCALE scope must be granted", style = MaterialTheme.typography.bodySmall)
                Text("• The target app will reload with the new language", style = MaterialTheme.typography.bodySmall)
                Text("• Use empty string (\"\") to revert to system locale", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
