package com.accu.ui.language

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LocaleOption(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val script: String = "",
    val region: String = "",
)

val ALL_LOCALES = listOf(
    LocaleOption("en-US", "English (United States)", "English (United States)", region = "US"),
    LocaleOption("en-GB", "English (United Kingdom)", "English (United Kingdom)", region = "GB"),
    LocaleOption("en-AU", "English (Australia)", "English (Australia)", region = "AU"),
    LocaleOption("zh-CN", "Chinese Simplified", "中文（简体）", "Hans", "CN"),
    LocaleOption("zh-TW", "Chinese Traditional", "中文（繁體）", "Hant", "TW"),
    LocaleOption("ja-JP", "Japanese", "日本語", region = "JP"),
    LocaleOption("ko-KR", "Korean", "한국어", region = "KR"),
    LocaleOption("de-DE", "German", "Deutsch", region = "DE"),
    LocaleOption("fr-FR", "French", "Français", region = "FR"),
    LocaleOption("es-ES", "Spanish (Spain)", "Español (España)", region = "ES"),
    LocaleOption("es-MX", "Spanish (Mexico)", "Español (México)", region = "MX"),
    LocaleOption("it-IT", "Italian", "Italiano", region = "IT"),
    LocaleOption("pt-BR", "Portuguese (Brazil)", "Português (Brasil)", region = "BR"),
    LocaleOption("pt-PT", "Portuguese (Portugal)", "Português (Portugal)", region = "PT"),
    LocaleOption("ru-RU", "Russian", "Русский", region = "RU"),
    LocaleOption("ar-SA", "Arabic", "العربية", region = "SA"),
    LocaleOption("hi-IN", "Hindi", "हिन्दी", region = "IN"),
    LocaleOption("bn-BD", "Bengali", "বাংলা", region = "BD"),
    LocaleOption("tr-TR", "Turkish", "Türkçe", region = "TR"),
    LocaleOption("nl-NL", "Dutch", "Nederlands", region = "NL"),
    LocaleOption("pl-PL", "Polish", "Polski", region = "PL"),
    LocaleOption("sv-SE", "Swedish", "Svenska", region = "SE"),
    LocaleOption("da-DK", "Danish", "Dansk", region = "DK"),
    LocaleOption("fi-FI", "Finnish", "Suomi", region = "FI"),
    LocaleOption("nb-NO", "Norwegian", "Norsk Bokmål", region = "NO"),
    LocaleOption("uk-UA", "Ukrainian", "Українська", region = "UA"),
    LocaleOption("cs-CZ", "Czech", "Čeština", region = "CZ"),
    LocaleOption("sk-SK", "Slovak", "Slovenčina", region = "SK"),
    LocaleOption("hu-HU", "Hungarian", "Magyar", region = "HU"),
    LocaleOption("ro-RO", "Romanian", "Română", region = "RO"),
    LocaleOption("vi-VN", "Vietnamese", "Tiếng Việt", region = "VN"),
    LocaleOption("th-TH", "Thai", "ภาษาไทย", region = "TH"),
    LocaleOption("id-ID", "Indonesian", "Bahasa Indonesia", region = "ID"),
    LocaleOption("ms-MY", "Malay", "Bahasa Melayu", region = "MY"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDetailScreen(
    packageName: String,
    appName: String,
    currentLocale: String,
    onBack: () -> Unit,
    onLocaleSet: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLocale by remember { mutableStateOf(currentLocale) }
    var isApplying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val filtered = buildList {
        if (searchQuery.isBlank()) add(LocaleOption("system", "System Default", "System Default"))
        ALL_LOCALES.filter {
            searchQuery.isBlank() ||
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.nativeName.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true)
        }.forEach { add(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Set Language", style = MaterialTheme.typography.titleMedium)
                        Text(appName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                isApplying = true
                                delay(800)
                                onLocaleSet(selectedLocale)
                                isApplying = false
                                snackbar.showSnackbar("Language set to ${filtered.find { it.code == selectedLocale }?.displayName ?: selectedLocale}")
                            }
                        },
                        enabled = !isApplying && selectedLocale != currentLocale,
                    ) { Text("Apply") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search languages…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            if (isApplying) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(filtered, key = { it.code }) { locale ->
                    ListItem(
                        headlineContent = { Text(locale.displayName) },
                        supportingContent = { if (locale.nativeName != locale.displayName) Text(locale.nativeName) },
                        leadingContent = {
                            if (locale.code == "system") Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                            else Text(locale.region.take(2).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            if (selectedLocale == locale.code) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable { selectedLocale = locale.code },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selectedLocale == locale.code) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        )
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
