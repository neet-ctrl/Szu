package com.accu.ui.language

import android.app.LocaleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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

// ── Data models ───────────────────────────────────────────────────────────────

data class PinnedLocale(
    val tag: String,
    val displayName: String,
    val nativeName: String,
)

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
    // ── Pinned locales (LanguageSelector QS Tile feature) ──────────────────
    val pinnedLocales: List<PinnedLocale> = listOf(
        PinnedLocale("en-US", "English (US)", "English"),
        PinnedLocale("ja-JP", "Japanese", "日本語"),
    ),
    val showPinnedManager: Boolean = false,
    // ── QS Tile tab ──────────────────────────────────────────────────────────
    val qsTileEnabled: Boolean = false,
)

data class LangAppModel(
    val packageName: String,
    val appName: String,
    val currentLocale: String = "",
    val isSystemApp: Boolean = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

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
                    lm.setApplicationLocales(LocaleList.forLanguageTags(localeTag))
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
                    lm.setApplicationLocales(LocaleList.getEmptyLocaleList())
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

    // ── Pinned locales (for QS Tile cycling) ─────────────────────────────────

    fun pinLocale(locale: PinnedLocale) {
        if (_state.value.pinnedLocales.any { it.tag == locale.tag }) return
        _state.update { it.copy(pinnedLocales = it.pinnedLocales + locale, snackbarMessage = "${locale.displayName} pinned to QS Tile cycle") }
    }

    fun unpinLocale(tag: String) {
        _state.update { it.copy(pinnedLocales = it.pinnedLocales.filter { p -> p.tag != tag }) }
    }

    fun movePinnedUp(index: Int) {
        if (index <= 0) return
        val list = _state.value.pinnedLocales.toMutableList()
        val tmp = list[index]; list[index] = list[index - 1]; list[index - 1] = tmp
        _state.update { it.copy(pinnedLocales = list) }
    }

    fun movePinnedDown(index: Int) {
        val list = _state.value.pinnedLocales.toMutableList()
        if (index >= list.lastIndex) return
        val tmp = list[index]; list[index] = list[index + 1]; list[index + 1] = tmp
        _state.update { it.copy(pinnedLocales = list) }
    }

    fun toggleQsTile() { _state.update { it.copy(qsTileEnabled = !it.qsTileEnabled) } }

    fun onSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun selectApp(app: LangAppModel) { _state.update { it.copy(selectedApp = app, showLocaleDialog = true) } }
    fun dismissDialog() { _state.update { it.copy(showLocaleDialog = false, selectedApp = null) } }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }
    fun toggleShowSystemApps() { _state.update { it.copy(showSystemApps = !it.showSystemApps) } }
    fun toggleModifiedFirst() { _state.update { it.copy(modifiedFirst = !it.modifiedFirst) } }
    fun showPinnedManager() { _state.update { it.copy(showPinnedManager = true) } }
    fun hidePinnedManager() { _state.update { it.copy(showPinnedManager = false) } }
}

// ── All available locales ────────────────────────────────────────────────────

val ALL_PINNABLE_LOCALES = listOf(
    PinnedLocale("", "System Default", "System Default"),
    PinnedLocale("en-US", "English (US)", "English"),
    PinnedLocale("en-GB", "English (UK)", "English"),
    PinnedLocale("en-AU", "English (Australia)", "English"),
    PinnedLocale("ja-JP", "Japanese", "日本語"),
    PinnedLocale("ko-KR", "Korean", "한국어"),
    PinnedLocale("zh-Hans-CN", "Chinese Simplified", "中文（简体）"),
    PinnedLocale("zh-Hant-TW", "Chinese Traditional", "中文（繁體）"),
    PinnedLocale("fr-FR", "French", "Français"),
    PinnedLocale("de-DE", "German", "Deutsch"),
    PinnedLocale("es-ES", "Spanish (Spain)", "Español"),
    PinnedLocale("es-MX", "Spanish (Mexico)", "Español (México)"),
    PinnedLocale("pt-BR", "Portuguese (Brazil)", "Português (Brasil)"),
    PinnedLocale("pt-PT", "Portuguese (Portugal)", "Português (Portugal)"),
    PinnedLocale("ru-RU", "Russian", "Русский"),
    PinnedLocale("ar-SA", "Arabic", "العربية"),
    PinnedLocale("hi-IN", "Hindi", "हिन्दी"),
    PinnedLocale("tr-TR", "Turkish", "Türkçe"),
    PinnedLocale("pl-PL", "Polish", "Polski"),
    PinnedLocale("it-IT", "Italian", "Italiano"),
    PinnedLocale("nl-NL", "Dutch", "Nederlands"),
    PinnedLocale("sv-SE", "Swedish", "Svenska"),
    PinnedLocale("th-TH", "Thai", "ภาษาไทย"),
    PinnedLocale("vi-VN", "Vietnamese", "Tiếng Việt"),
    PinnedLocale("id-ID", "Indonesian", "Bahasa Indonesia"),
    PinnedLocale("uk-UA", "Ukrainian", "Українська"),
    PinnedLocale("cs-CZ", "Czech", "Čeština"),
    PinnedLocale("hu-HU", "Hungarian", "Magyar"),
    PinnedLocale("ro-RO", "Romanian", "Română"),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LanguageCenterScreen(
    onBack: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by remember { mutableIntStateOf(0) }

    val setLocales = remember {
        ALL_PINNABLE_LOCALES.map { it.displayName to it.tag }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Language Center",
                onBack = onBack,
                actions = {
                    InfoTooltipIcon(
                        title = "Language Center — Language Selector",
                        description = "Per-app language overrides without root.\n\nBased on Language Selector — set any language for any app independently of system locale.\n\n• Set locale per-app (e.g. Twitter in English, WhatsApp in Spanish)\n• Pin locales for QS Tile cycling — tap tile to cycle the foreground app through pinned locales\n• Long-press any locale in the picker to pin it\n• Supports all BCP 47 language tags\n• Changes apply immediately — no restart needed\n\nUses Shizuku to call ActivityManager setApplicationLocales API."
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

            // ── Tabs ──────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Apps") }, icon = { Icon(Icons.Default.Apps, null, Modifier.size(18.dp)) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("QS Tile") }, icon = { Icon(Icons.Default.ViewDay, null, Modifier.size(18.dp)) })
            }

            when (activeTab) {
                0 -> AppLocaleTab(state, context, viewModel, setLocales)
                1 -> QsTileTab(state, viewModel)
            }
        }
    }

    // ── Locale picker dialog ──────────────────────────────────────────────────
    if (state.showLocaleDialog) {
        val app = state.selectedApp!!
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Set language for ${app.appName}") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(setLocales) { (name, tag) ->
                        val isPinned = state.pinnedLocales.any { it.tag == tag }
                        ListItem(
                            headlineContent = { Text(name) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isPinned) Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    if (tag.isNotBlank()) Spacer(Modifier.width(4.dp))
                                    if (tag.isNotBlank()) Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { viewModel.setAppLocale(app.packageName, app.appName, tag) },
                                onLongClick = {
                                    if (tag.isNotBlank()) {
                                        viewModel.pinLocale(PinnedLocale(tag, name, name))
                                    }
                                },
                            ),
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
        )
    }

    // ── Pinned locales manager ────────────────────────────────────────────────
    if (state.showPinnedManager) {
        PinnedLocalesManagerDialog(
            pinnedLocales = state.pinnedLocales,
            onDismiss = viewModel::hidePinnedManager,
            onUnpin = viewModel::unpinLocale,
            onMoveUp = viewModel::movePinnedUp,
            onMoveDown = viewModel::movePinnedDown,
            onAdd = { locale -> viewModel.pinLocale(locale) },
        )
    }
}

// ── App Locale Tab ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppLocaleTab(
    state: LangState,
    context: android.content.Context,
    viewModel: LanguageViewModel,
    setLocales: List<Pair<String, String>>,
) {
    Column(Modifier.fillMaxSize()) {
        // Filter chips
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

        // Modified apps chips
        if (state.appLanguages.isNotEmpty()) {
            Text(
                "Custom Language Set (${state.appLanguages.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
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
                        supportingContent = {
                            Text(
                                if (setLang != null) "Language: ${setLang.localeName}" else "System Default",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (setLang != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
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

// ── QS Tile Tab ───────────────────────────────────────────────────────────────

@Composable
private fun QsTileTab(
    state: LangState,
    viewModel: LanguageViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── QS Tile info card ──────────────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ViewDay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Language QS Tile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = state.qsTileEnabled, onCheckedChange = { viewModel.toggleQsTile() })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.15f))
                    Text(
                        "Add the Language Selector tile to Quick Settings to cycle through pinned locales for the current foreground app.\n\n" +
                        "Tap tile → cycles to next pinned locale\nLong-press tile → opens Language Center\n\nNo root or Shizuku required for QS Tile display; Shizuku is required to apply locale changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // ── How to add ────────────────────────────────────────────────────
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How to Add to Quick Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    listOf(
                        "Swipe down twice to fully open Quick Settings",
                        "Tap the pencil (Edit) icon",
                        "Find \"Language Selector\" tile",
                        "Drag it into your active tiles area",
                        "Tap tile to cycle through pinned locales for foreground app",
                    ).forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.size(22.dp).wrapContentSize(Alignment.Center))
                            }
                            Text(step, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── Pinned locales section ─────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pinned Locales (${state.pinnedLocales.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::showPinnedManager) { Text("Manage") }
            }
            Text(
                "The QS Tile cycles through these locales in order. Cycle always starts with System Default, then each pinned locale.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.pinnedLocales.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No pinned locales.\n\nLong-press a locale in the App tab locale picker to pin it, or tap Manage above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.pinnedLocales.size) { index ->
                val locale = state.pinnedLocales[index]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Cycle order badge
                        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.size(26.dp).wrapContentSize(Alignment.Center))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(locale.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (locale.nativeName != locale.displayName) Text(locale.nativeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (locale.tag.isNotBlank()) Text(locale.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                        Column {
                            IconButton(onClick = { viewModel.movePinnedUp(index) }, modifier = Modifier.size(30.dp), enabled = index > 0) {
                                Icon(Icons.Default.ArrowUpward, null, Modifier.size(16.dp))
                            }
                            IconButton(onClick = { viewModel.movePinnedDown(index) }, modifier = Modifier.size(30.dp), enabled = index < state.pinnedLocales.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, null, Modifier.size(16.dp))
                            }
                        }
                        IconButton(onClick = { viewModel.unpinLocale(locale.tag) }) {
                            Icon(Icons.Default.Close, "Unpin", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // ── Tile behavior notes ────────────────────────────────────────────
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Text("Tile Behavior Details", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Text(
                        "• Tile reads foreground app via UsageStats/ActivityManager (requires Shizuku)\n" +
                        "• System apps and ACCU itself are excluded from cycling\n" +
                        "• If foreground app has no custom locale, cycling starts from the first pinned locale\n" +
                        "• Cycling wraps around back to System Default after the last pinned locale\n" +
                        "• Tile label shows current locale; subtitle shows app name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

// ── Pinned locales manager dialog ─────────────────────────────────────────────

@Composable
private fun PinnedLocalesManagerDialog(
    pinnedLocales: List<PinnedLocale>,
    onDismiss: () -> Unit,
    onUnpin: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onAdd: (PinnedLocale) -> Unit,
) {
    var showAddPicker by remember { mutableStateOf(false) }
    var addSearch by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Manage Pinned Locales", modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddPicker = !showAddPicker }) { Icon(Icons.Default.Add, "Add") }
            }
        },
        text = {
            Column {
                if (showAddPicker) {
                    OutlinedTextField(
                        value = addSearch, onValueChange = { addSearch = it },
                        placeholder = { Text("Search locale to add…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(ALL_PINNABLE_LOCALES.filter {
                            it.tag.isNotBlank() &&
                            pinnedLocales.none { p -> p.tag == it.tag } &&
                            (addSearch.isBlank() || it.displayName.contains(addSearch, true) || it.nativeName.contains(addSearch, true))
                        }) { locale ->
                            ListItem(
                                headlineContent = { Text(locale.displayName) },
                                supportingContent = { Text(locale.nativeName) },
                                trailingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { onAdd(locale); showAddPicker = false; addSearch = "" },
                            )
                            HorizontalDivider()
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                if (pinnedLocales.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No pinned locales. Tap + to add.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(pinnedLocales.size) { i ->
                            val locale = pinnedLocales[i]
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(locale.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    if (locale.nativeName != locale.displayName) Text(locale.nativeName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onMoveUp(i) }, modifier = Modifier.size(28.dp), enabled = i > 0) { Icon(Icons.Default.ArrowUpward, null, Modifier.size(14.dp)) }
                                IconButton(onClick = { onMoveDown(i) }, modifier = Modifier.size(28.dp), enabled = i < pinnedLocales.lastIndex) { Icon(Icons.Default.ArrowDownward, null, Modifier.size(14.dp)) }
                                IconButton(onClick = { onUnpin(locale.tag) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

