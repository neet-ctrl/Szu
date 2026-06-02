package com.accu.ui.customization

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.ACCTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Model ───────────────────────────────────────────────────────────────────

data class AppThemeEntry(
    val name: String,
    val pkg: String,
    val isThemed: Boolean,
    val seedColor: Color?,
    val useSystemColor: Boolean = true,
    // Tracks whether an AccuTheme_ overlay actually exists on the target
    val overlayApplied: Boolean = false,
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

data class PerAppThemingState(
    val isLoading: Boolean = false,
    val apps: List<AppThemeEntry> = emptyList(),
    val connectionStatus: String = "DISCONNECTED",
    val snackbar: String? = null,
    val applyingPkg: String? = null,
)

@HiltViewModel
class PerAppThemingViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PerAppThemingState())
    val state: StateFlow<PerAppThemingState> = _state.asStateFlow()

    // All ACCU-applied overlays use this prefix so we can track them
    private val OVERLAY_PREFIX = "AccuTheme_"

    init {
        viewModelScope.launch {
            connectionManager.state.collect { cs ->
                _state.update { it.copy(connectionStatus = cs.name) }
            }
        }
        loadApps()
    }

    // ── Load app list + current overlay state from target device ──────────────
    fun loadApps() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Get installed packages from target
                val pkgOutput = connectionManager.exec("pm list packages 2>/dev/null").output
                val packages = pkgOutput.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotBlank() && it != ctx.packageName }

                // 2. Get existing AccuTheme overlays from target
                val overlayOutput = connectionManager.exec(
                    "cmd overlay list 2>/dev/null"
                ).output
                val appliedOverlays = parseAppliedOverlays(overlayOutput)

                // 3. Build entry list
                val entries = packages.map { pkg ->
                    val overlayKey = "$pkg:$OVERLAY_PREFIX$pkg"
                    val hasOverlay = appliedOverlays[overlayKey]
                    AppThemeEntry(
                        name           = pkgToLabel(pkg),
                        pkg            = pkg,
                        isThemed       = hasOverlay == true,
                        seedColor      = null, // stored locally; ADB doesn't expose applied color
                        useSystemColor = hasOverlay == null,
                        overlayApplied = hasOverlay == true,
                    )
                }.sortedBy { it.name }

                _state.update { it.copy(apps = entries, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    snackbar  = "Error loading apps: ${e.message}",
                ) }
            }
        }
    }

    /**
     * Parse `cmd overlay list` output.
     * Returns map of overlayKey → isEnabled.
     * Lines look like:
     *   --- com.accu.ui:AccuTheme_com.accu.ui
     *   [x] com.accu.ui:AccuTheme_com.accu.ui
     */
    private fun parseAppliedOverlays(output: String): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        output.lines().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.contains(OVERLAY_PREFIX)) {
                val enabled = trimmed.startsWith("[x]") || trimmed.startsWith("[ ]").not()
                // extract the overlay identifier
                val colonIdx = trimmed.indexOf(' ')
                if (colonIdx >= 0) {
                    val id = trimmed.substring(colonIdx + 1).trim()
                    if (id.contains(OVERLAY_PREFIX)) {
                        result[id] = enabled && trimmed.startsWith("[x]")
                    }
                }
            }
        }
        return result
    }

    // ── Apply seed color overlay to a specific app on target device ───────────
    fun applyTheme(pkg: String, color: Color, useSystem: Boolean) {
        _state.update { it.copy(applyingPkg = pkg) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val overlayName = "$OVERLAY_PREFIX$pkg"

                if (useSystem) {
                    // Use system accent — just enable any existing ACCU overlay or
                    // clear custom one and let system handle it
                    connectionManager.exec(
                        "cmd overlay disable --user 0 $pkg:$overlayName 2>/dev/null"
                    )
                    _state.update { it.copy(snackbar = "✓ $pkg — reverted to system theme") }
                } else {
                    val hex = colorToAarrggbb(color)

                    // Fabricate overlay for key Material You color slots
                    val slots = listOf(
                        "android:color/system_accent1_100",
                        "android:color/system_accent1_200",
                        "android:color/system_accent1_300",
                        "android:color/system_accent1_600",
                        "android:color/system_accent1_700",
                        "android:color/system_accent2_100",
                        "android:color/system_accent2_200",
                        "android:color/system_accent3_100",
                    )

                    // type 0x1c = TYPE_COLOR (ARGB)
                    slots.forEach { slot ->
                        connectionManager.exec(
                            "cmd overlay fabricate" +
                            " --target $pkg" +
                            " --name $overlayName" +
                            " $slot 0x1c $hex" +
                            " 2>/dev/null"
                        )
                    }

                    // Enable the overlay
                    val enableResult = connectionManager.exec(
                        "cmd overlay enable --user 0 $pkg:$overlayName 2>/dev/null"
                    )

                    // Set overlay priority so it actually wins
                    connectionManager.exec(
                        "cmd overlay set-priority $pkg:$overlayName highest 2>/dev/null"
                    )

                    if (enableResult.isSuccess || enableResult.output.isBlank()) {
                        _state.update { s ->
                            s.copy(
                                snackbar    = "✓ Seed color applied to $pkg on target device",
                                applyingPkg = null,
                            )
                        }
                        // Refresh overlay state
                        updateEntryState(pkg, isThemed = true, seedColor = color)
                    } else {
                        _state.update { it.copy(
                            snackbar    = "✗ Failed: ${enableResult.output.ifBlank { enableResult.error }}",
                            applyingPkg = null,
                        ) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    snackbar    = "Error: ${e.message}",
                    applyingPkg = null,
                ) }
            }
        }
    }

    // ── Remove overlay from app on target device ──────────────────────────────
    fun clearTheme(pkg: String) {
        _state.update { it.copy(applyingPkg = pkg) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val overlayName = "$OVERLAY_PREFIX$pkg"
                connectionManager.exec("cmd overlay disable --user 0 $pkg:$overlayName 2>/dev/null")
                // Also erase the fabricated overlay (Android 13+)
                connectionManager.exec("cmd overlay fabricate --erase $pkg:$overlayName 2>/dev/null")
                _state.update { s ->
                    s.copy(
                        snackbar    = "✓ Theme cleared for $pkg on target device",
                        applyingPkg = null,
                    )
                }
                updateEntryState(pkg, isThemed = false, seedColor = null)
            } catch (e: Exception) {
                _state.update { it.copy(
                    snackbar    = "Error: ${e.message}",
                    applyingPkg = null,
                ) }
            }
        }
    }

    // ── Batch clear all ACCU overlays ─────────────────────────────────────────
    fun clearAllThemes() {
        viewModelScope.launch(Dispatchers.IO) {
            val themed = _state.value.apps.filter { it.isThemed }
            themed.forEach { app ->
                val overlayName = "$OVERLAY_PREFIX${app.pkg}"
                try {
                    connectionManager.exec("cmd overlay disable --user 0 ${app.pkg}:$overlayName 2>/dev/null")
                    connectionManager.exec("cmd overlay fabricate --erase ${app.pkg}:$overlayName 2>/dev/null")
                } catch (_: Exception) {}
            }
            _state.update { it.copy(snackbar = "✓ Cleared ${themed.size} overlays on target device") }
            loadApps()
        }
    }

    private fun updateEntryState(pkg: String, isThemed: Boolean, seedColor: Color?) {
        _state.update { s ->
            s.copy(
                apps = s.apps.map { app ->
                    if (app.pkg == pkg) app.copy(
                        isThemed       = isThemed,
                        seedColor      = seedColor,
                        overlayApplied = isThemed,
                        useSystemColor = !isThemed,
                    ) else app
                }
            )
        }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbar = null) }

    private fun pkgToLabel(pkg: String): String =
        pkg.split(".").last().replaceFirstChar { it.uppercase() }

    /** Convert a Compose Color to 0xAARRGGBB hex string suitable for overlay fabricate. */
    private fun colorToAarrggbb(color: Color): String {
        val a = (color.alpha * 255).toInt()
        val r = (color.red   * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue  * 255).toInt()
        return "0x%02X%02X%02X%02X".format(a, r, g, b)
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppThemingScreen(
    onBack: () -> Unit = {},
    viewModel: PerAppThemingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("All") }
    var editingApp by remember { mutableStateOf<AppThemeEntry?>(null) }
    var selectedColor by remember { mutableStateOf(Color(0xFF6750A4)) }
    var useSystem by remember { mutableStateOf(true) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.clearSnackbar() }
    }

    val filtered = state.apps.filter { app ->
        (filterMode == "All" ||
            (filterMode == "Themed" && app.isThemed) ||
            (filterMode == "Default" && !app.isThemed)) &&
        (search.isBlank() || app.name.contains(search, ignoreCase = true) ||
            app.pkg.contains(search, ignoreCase = true))
    }
    val themedCount = state.apps.count { it.isThemed }

    // Edit dialog
    editingApp?.let { app ->
        AlertDialog(
            onDismissRequest = { editingApp = null },
            title = {
                Column {
                    Text("Theme — ${app.name}", fontWeight = FontWeight.Bold)
                    Text(app.pkg, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // System vs custom toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Use system accent color",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Follows device wallpaper color",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = useSystem, onCheckedChange = { useSystem = it })
                        }
                    }

                    if (!useSystem) {
                        Text("Pick seed color:",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        val palette = listOf(
                            Color(0xFF6750A4) to "Purple",
                            Color(0xFF1A73E8) to "Blue",
                            Color(0xFFEA4335) to "Red",
                            Color(0xFF1DB954) to "Green",
                            Color(0xFFFF9800) to "Orange",
                            Color(0xFF9C27B0) to "Violet",
                            Color(0xFF00BCD4) to "Cyan",
                            Color(0xFF4CAF50) to "Lime",
                            Color(0xFFE91E63) to "Pink",
                            Color(0xFF2196F3) to "Cobalt",
                            Color(0xFF4A154B) to "Plum",
                            Color(0xFFFF5722) to "Deep Orange",
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            palette.chunked(4).forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    row.forEach { (color, _) ->
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .then(
                                                    if (selectedColor == color)
                                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                    else Modifier
                                                )
                                                .clickable { selectedColor = color },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (selectedColor == color)
                                                Icon(Icons.Default.Check, null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Color preview
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = selectedColor.copy(alpha = 0.2f),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(20.dp).clip(CircleShape).background(selectedColor))
                                Text(
                                    "Will run: cmd overlay fabricate --target ${app.pkg}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val isPending = state.applyingPkg == app.pkg
                Button(
                    onClick = {
                        viewModel.applyTheme(app.pkg, selectedColor, useSystem)
                        editingApp = null
                    },
                    enabled = !isPending,
                ) {
                    if (isPending) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Apply on target device")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (app.overlayApplied) {
                        OutlinedButton(
                            onClick = { viewModel.clearTheme(app.pkg); editingApp = null },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("Clear theme") }
                    }
                    TextButton(onClick = { editingApp = null }) { Text("Cancel") }
                }
            },
        )
    }

    // Confirm clear all
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear all themes?") },
            text  = { Text("This will disable all AccuTheme overlays on the target device ($themedCount app${if (themedCount != 1) "s" else ""}).") },
            confirmButton = {
                Button(
                    onClick   = { viewModel.clearAllThemes(); showClearAllDialog = false },
                    colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title  = "Per-App Theming",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Default.Refresh, "Reload from target device")
                    }
                    if (themedCount > 0) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.ClearAll, "Clear all themes")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Connection status banner ──────────────────────────────────────
            val isConnected = state.connectionStatus.startsWith("CONNECTED")
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (isConnected) Color(0xFF00C853).copy(0.12f)
                        else MaterialTheme.colorScheme.errorContainer.copy(0.5f),
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        Modifier.size(16.dp),
                        tint = if (isConnected) Color(0xFF00C853) else MaterialTheme.colorScheme.error,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isConnected)
                                "Connected — overlays applied on target device"
                            else
                                "Not connected — connect via ACCU Center first",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isConnected) Color(0xFF00C853)
                                    else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "${state.connectionStatus}  ·  ${state.apps.size} packages loaded  ·  $themedCount themed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── How it works info chip ────────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Uses `cmd overlay fabricate` to apply Material You seed colors per app on the target device. Requires Android 12+ and ADB/root privilege.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // ── Search ───────────────────────────────────────────────────────
            OutlinedTextField(
                value         = search,
                onValueChange = { search = it },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder   = { Text("Search apps on target device…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = {
                    if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Clear, null)
                    }
                },
                singleLine = true,
            )

            // ── Filters + stats ──────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Themed", "Default").forEach { f ->
                        FilterChip(
                            selected = filterMode == f,
                            onClick  = { filterMode = f },
                            label    = { Text(f, fontSize = 12.sp) },
                        )
                    }
                }
                Text(
                    "$themedCount / ${state.apps.size} themed",
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── App list ─────────────────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading apps from target device…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (state.apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhoneAndroid, null,
                            Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (isConnected) "No apps loaded — tap ↺ to refresh"
                            else "Connect to a device first, then tap ↺ to load apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filtered, key = { it.pkg }) { app ->
                        val isApplying = state.applyingPkg == app.pkg
                        ListItem(
                            headlineContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(app.name)
                                    if (app.overlayApplied) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ) {
                                            Text("THEMED", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            supportingContent = {
                                if (app.isThemed) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (app.seedColor != null) {
                                            Box(Modifier.size(10.dp).clip(CircleShape).background(app.seedColor))
                                        }
                                        Text(
                                            if (app.useSystemColor) "System accent color overlay active"
                                            else "Custom seed color overlay active",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else {
                                    Text(
                                        app.pkg,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingContent = {
                                if (isApplying) {
                                    CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Android,
                                        null,
                                        tint = if (app.isThemed) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.outline,
                                    )
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (app.isThemed && app.seedColor != null) {
                                        Box(Modifier.size(20.dp).clip(CircleShape).background(app.seedColor))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            editingApp    = app
                                            useSystem     = app.useSystemColor
                                            selectedColor = app.seedColor ?: Color(0xFF6750A4)
                                        },
                                        enabled = !isApplying,
                                    ) {
                                        Icon(Icons.Default.Edit, "Edit theme")
                                    }
                                }
                            },
                            modifier = Modifier.clickable(enabled = !isApplying) {
                                editingApp    = app
                                useSystem     = app.useSystemColor
                                selectedColor = app.seedColor ?: Color(0xFF6750A4)
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
