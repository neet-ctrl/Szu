package com.accu.ui.language

import android.app.LocaleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.accu.data.db.dao.AppLanguageDao
import com.accu.data.db.entities.AppLanguageEntity
import androidx.compose.material3.LocalContentColor
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.EmptyState
import com.accu.ui.components.InfoTooltipIcon
import com.accu.utils.ShizukuUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

data class LangState(
    val appLanguages: List<AppLanguageEntity> = emptyList(),
    val availableApps: List<LangAppModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null,
    val showLocaleDialog: Boolean = false,
    val selectedApp: LangAppModel? = null,
    val showSystemApps: Boolean = false,
    val modifiedFirst: Boolean = false,
)
data class LangAppModel(val packageName: String, val appName: String, val currentLocale: String = "", val isSystemApp: Boolean = false)

@HiltViewModel
class LanguageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLanguageDao: AppLanguageDao,
    private val shizukuUtils: ShizukuUtils,
) : ViewModel() {
    private val _state = MutableStateFlow(LangState())
    val state: StateFlow<LangState> = _state.asStateFlow()

    init { loadApps(); observeAppLanguages() }

    private fun observeAppLanguages() {
        viewModelScope.launch { appLanguageDao.observeAll().collect { langs -> _state.update { it.copy(appLanguages = langs) } } }
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledPackages(0).mapNotNull { pkg ->
                val ai = pkg.applicationInfo ?: return@mapNotNull null
                val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                LangAppModel(pkg.packageName, pm.getApplicationLabel(ai).toString(), isSystemApp = isSystem)
            }.sortedBy { it.appName }
            _state.update { it.copy(availableApps = apps, isLoading = false) }
        }
    }

    fun setAppLocale(packageName: String, appName: String, localeTag: String) {
        viewModelScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val lm = context.getSystemService(LocaleManager::class.java)
                    lm.setApplicationLocales(packageName, LocaleList.forLanguageTags(localeTag))
                } else {
                    shizukuUtils.execShizuku("cmd locale set-app-locales $packageName --locales $localeTag")
                }
                val locale = Locale.forLanguageTag(localeTag)
                appLanguageDao.insert(AppLanguageEntity(packageName = packageName, appName = appName, localeTag = localeTag, localeName = locale.displayName))
                _state.update { it.copy(snackbarMessage = "Set $appName to ${locale.displayName}", showLocaleDialog = false) }
            } catch (e: Exception) {
                Timber.e(e)
                _state.update { it.copy(snackbarMessage = "Failed: ${e.message}") }
            }
        }
    }

    fun resetAppLocale(packageName: String) {
        viewModelScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val lm = context.getSystemService(LocaleManager::class.java)
                    lm.setApplicationLocales(packageName, LocaleList.getEmptyLocaleList())
                } else {
                    shizukuUtils.execShizuku("cmd locale set-app-locales $packageName --locales")
                }
                appLanguageDao.deleteForPackage(packageName)
                _state.update { it.copy(snackbarMessage = "Reset to system language") }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    fun onSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun selectApp(app: LangAppModel) { _state.update { it.copy(selectedApp = app, showLocaleDialog = true) } }
    fun dismissDialog() { _state.update { it.copy(showLocaleDialog = false, selectedApp = null) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
    fun toggleShowSystemApps() { _state.update { it.copy(showSystemApps = !it.showSystemApps) } }
    fun toggleModifiedFirst() { _state.update { it.copy(modifiedFirst = !it.modifiedFirst) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageCenterScreen(
    onBack: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val setLocales = remember {
        listOf(
            "System Default" to "",
            "English (US)" to "en-US",
            "English (UK)" to "en-GB",
            "Japanese" to "ja-JP",
            "Korean" to "ko-KR",
            "Chinese (Simplified)" to "zh-Hans-CN",
            "Chinese (Traditional)" to "zh-Hant-TW",
            "French" to "fr-FR",
            "German" to "de-DE",
            "Spanish" to "es-ES",
            "Portuguese (Brazil)" to "pt-BR",
            "Russian" to "ru-RU",
            "Arabic" to "ar-SA",
            "Hindi" to "hi-IN",
            "Turkish" to "tr-TR",
            "Polish" to "pl-PL",
            "Italian" to "it-IT",
            "Dutch" to "nl-NL",
            "Swedish" to "sv-SE",
            "Thai" to "th-TH",
            "Vietnamese" to "vi-VN",
            "Indonesian" to "in-ID",
        )
    }

    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() } }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Language Center",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Language Center — Language Selector",
                        description = "Per-app language overrides without root.\n\nBased on Language Selector — set any language for any app independently of system locale.\n\n• Set locale per-app (e.g. Twitter in English, WhatsApp in Spanish)\n• Supports all BCP 47 language tags\n• Changes apply immediately — no restart needed\n• System apps and user apps supported\n\nUses Shizuku to call ActivityManager setApplicationLocales API."
                    )
                    IconButton(onClick = viewModel::toggleShowSystemApps) {
                        Icon(
                            if (state.showSystemApps) Icons.Default.PhoneAndroid else Icons.Default.Apps,
                            "Toggle System Apps",
                            tint = if (state.showSystemApps) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = viewModel::toggleModifiedFirst) {
                        Icon(
                            Icons.Default.FilterList,
                            "Modified First",
                            tint = if (state.modifiedFirst) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Toggles row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.showSystemApps,
                    onClick = viewModel::toggleShowSystemApps,
                    label = { Text("System apps") },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(14.dp)) },
                )
                FilterChip(
                    selected = state.modifiedFirst,
                    onClick = viewModel::toggleModifiedFirst,
                    label = { Text("Modified first") },
                    leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(14.dp)) },
                )
            }

            SearchBar(
                query = state.searchQuery, onQueryChange = viewModel::onSearch,
                onSearch = {}, active = false, onActiveChange = {},
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {}

            // Set language apps
            if (state.appLanguages.isNotEmpty()) {
                Text("Custom Language Set (${state.appLanguages.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.appLanguages) { lang ->
                        AssistChip(
                            onClick = { viewModel.resetAppLocale(lang.packageName) },
                            label = { Text("${lang.appName}: ${lang.localeName}") },
                            trailingIcon = { Icon(Icons.Default.Close, "Reset", Modifier.size(14.dp)) },
                        )
                    }
                }
            }

            val filtered = remember(state.availableApps, state.searchQuery, state.showSystemApps, state.modifiedFirst, state.appLanguages) {
                state.availableApps
                    .filter { if (!state.showSystemApps) !it.isSystemApp else true }
                    .filter { it.appName.contains(state.searchQuery, true) || it.packageName.contains(state.searchQuery, true) }
                    .let { list ->
                        if (state.modifiedFirst) {
                            val modifiedPkgs = state.appLanguages.map { it.packageName }.toSet()
                            list.sortedByDescending { it.packageName in modifiedPkgs }
                        } else list
                    }
            }
            if (filtered.isEmpty()) {
                EmptyState(Icons.Default.Language, "No apps found", "Try a different search")
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val setLang = state.appLanguages.firstOrNull { it.packageName == app.packageName }
                        ListItem(
                            headlineContent = { Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (setLang != null) "Language: ${setLang.localeName}" else "System Default", style = MaterialTheme.typography.bodySmall, color = if (setLang != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(try { context.packageManager.getApplicationIcon(app.packageName) } catch (_: Exception) { null }).crossfade(true).build(),
                                    contentDescription = null, modifier = Modifier.size(40.dp),
                                )
                            },
                            trailingContent = {
                                Row {
                                    if (setLang != null) IconButton(onClick = { viewModel.resetAppLocale(app.packageName) }) { Icon(Icons.Default.RestartAlt, "Reset") }
                                    IconButton(onClick = { viewModel.selectApp(app) }) { Icon(Icons.Default.Language, "Set Language") }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Locale picker dialog
    if (state.showLocaleDialog) {
        val app = state.selectedApp!!
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Set language for ${app.appName}") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(setLocales) { (name, tag) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            trailingContent = { if (tag.isNotBlank()) Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.clickable { viewModel.setAppLocale(app.packageName, app.appName, tag) },
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
        )
    }
}

private fun Modifier.clickable(onClick: () -> Unit) = this.then(androidx.compose.ui.Modifier.clickable(onClick = onClick))
