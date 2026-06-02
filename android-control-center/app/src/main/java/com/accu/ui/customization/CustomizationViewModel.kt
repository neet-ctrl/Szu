package com.accu.ui.customization

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.db.dao.CustomThemeDao
import com.accu.data.db.entities.CustomThemeEntity
import com.accu.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomizationUiState(
    // Theme preset
    val preset: ACCThemePreset = ACCThemePreset.MIDNIGHT_AMOLED,
    val monetStyle: String = "TONAL_SPOT",
    val seedColor: Int = 0xFF4A56E2.toInt(),
    // Display mode
    val isDark: Boolean = true,
    val isAmoled: Boolean = false,
    val pitchBlack: Boolean = false,
    val accurateShades: Boolean = true,
    // Glass / surface style
    val useGlassEffect: Boolean = true,
    val useGradientBackground: Boolean = true,
    val useDynamicColor: Boolean = false,
    val cardStyle: CardStyle = CardStyle.GLASS,
    // Shapes
    val cornerRadiusScale: Float = 1.0f,
    val iconShape: String = "Squircle",
    // Typography
    val fontScale: Float = 1.0f,
    // Elevation & animation
    val elevationScale: Float = 1.0f,
    val animationScale: Float = 1.0f,
    // Accent
    val accentIntensity: Float = 1.0f,
    // Navigation bar
    val navBarStyle: NavBarStyle = NavBarStyle.GLASS,
    // Status bar
    val transparentStatusBar: Boolean = false,
    val transparentNavBar: Boolean = false,
    val hideNotch: Boolean = false,
    // Saved themes from DB
    val savedThemes: List<CustomThemeEntity> = emptyList(),
    val snackbarMessage: String? = null,
    // Non-null triggers CreateDocument SAF picker in Screen
    val pendingExportJson: String? = null,
    val pendingExportFileName: String? = null,
)

@HiltViewModel
class CustomizationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customThemeDao: CustomThemeDao,
    private val themeManager: ThemeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomizationUiState())
    val state: StateFlow<CustomizationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Load saved themes from DB
            customThemeDao.observeAll().collect { themes ->
                _state.update { it.copy(savedThemes = themes) }
            }
        }
        viewModelScope.launch {
            // Load persisted theme config from DataStore
            themeManager.themeConfig.collect { cfg ->
                _state.update { s ->
                    s.copy(
                        preset               = cfg.preset,
                        isDark               = cfg.isDark,
                        isAmoled             = cfg.isAmoled,
                        useGlassEffect       = cfg.useGlassEffect,
                        useGradientBackground= cfg.useGradientBackground,
                        useDynamicColor      = cfg.useDynamicColor,
                        cardStyle            = cfg.cardStyle,
                        cornerRadiusScale    = cfg.cornerRadiusScale,
                        fontScale            = cfg.fontScale,
                        elevationScale       = cfg.elevationScale,
                        animationScale       = cfg.animationScale,
                        accentIntensity      = cfg.accentIntensity,
                        navBarStyle          = cfg.navBarStyle,
                        monetStyle           = cfg.monetStyle,
                    )
                }
            }
        }
    }

    // ── Theme Preset ──────────────────────────────────────
    /**
     * Apply a preset immediately: update state with preset-specific defaults
     * AND persist to DataStore so the app re-composes with the new colors right away.
     * No need to tap "Apply" separately.
     */
    fun setPreset(preset: ACCThemePreset) {
        val (dark, amoled, dynamic) = when (preset) {
            ACCThemePreset.DYNAMIC          -> Triple(true,  false, true)
            ACCThemePreset.MIDNIGHT_AMOLED  -> Triple(true,  true,  false)
            ACCThemePreset.NEON_MATRIX      -> Triple(true,  false, false)
            ACCThemePreset.AURORA_BOREALIS  -> Triple(true,  false, false)
            ACCThemePreset.VOLCANIC_FIRE    -> Triple(true,  false, false)
            ACCThemePreset.GOLD_LUXURY      -> Triple(true,  false, false)
            ACCThemePreset.SAKURA_PINK      -> Triple(false, false, false)
            ACCThemePreset.OCEAN_DEPTH      -> Triple(true,  false, false)
            ACCThemePreset.ROYAL_VIOLET     -> Triple(true,  false, false)
            ACCThemePreset.FOREST_GROVE     -> Triple(true,  false, false)
            ACCThemePreset.ROSE_GOLD        -> Triple(false, false, false)
            ACCThemePreset.SLATE_MONOCHROME -> Triple(true,  false, false)
        }
        _state.update { s ->
            s.copy(
                preset          = preset,
                isDark          = dark,
                isAmoled        = amoled,
                pitchBlack      = amoled,
                useDynamicColor = dynamic,
            )
        }
        // Persist immediately — no need to tap "Apply"
        viewModelScope.launch {
            val s = _state.value
            themeManager.save(
                ACCThemeConfig(
                    preset                = s.preset,
                    isDark                = s.isDark,
                    useGlassEffect        = s.useGlassEffect,
                    useDynamicColor       = s.useDynamicColor,
                    isAmoled              = s.isAmoled,
                    cornerRadiusScale     = s.cornerRadiusScale,
                    elevationScale        = s.elevationScale,
                    animationScale        = s.animationScale,
                    fontScale             = s.fontScale,
                    navBarStyle           = s.navBarStyle,
                    cardStyle             = s.cardStyle,
                    monetStyle            = s.monetStyle,
                    useGradientBackground = s.useGradientBackground,
                    accentIntensity       = s.accentIntensity,
                )
            )
            _state.update { it.copy(snackbarMessage = "✅ ${preset.displayName} applied!") }
        }
    }

    // ── Display Mode ──────────────────────────────────────
    fun setDisplayMode(dark: Boolean, amoled: Boolean) = _state.update { it.copy(isDark = dark, isAmoled = amoled, pitchBlack = amoled) }

    // ── Glass / Surface ───────────────────────────────────
    fun toggleGlass(v: Boolean)              = _state.update { it.copy(useGlassEffect = v) }
    fun toggleGradientBackground(v: Boolean) = _state.update { it.copy(useGradientBackground = v) }
    fun toggleDynamicColor(v: Boolean)       = _state.update { it.copy(useDynamicColor = v) }
    fun setCardStyle(s: CardStyle)           = _state.update { it.copy(cardStyle = s) }

    // ── Shapes ────────────────────────────────────────────
    fun setCornerRadius(v: Float)  = _state.update { it.copy(cornerRadiusScale = v) }
    fun setIconShape(s: String)    = _state.update { it.copy(iconShape = s) }

    // ── Typography ────────────────────────────────────────
    fun setFontScale(v: Float) = _state.update { it.copy(fontScale = v) }

    // ── Elevation & Animation ─────────────────────────────
    fun setElevation(v: Float)       = _state.update { it.copy(elevationScale = v) }
    fun setAnimationScale(v: Float)  = _state.update { it.copy(animationScale = v) }

    // ── Accent ────────────────────────────────────────────
    fun setAccentIntensity(v: Float) = _state.update { it.copy(accentIntensity = v) }

    // ── Navigation ────────────────────────────────────────
    fun setNavBarStyle(s: NavBarStyle) = _state.update { it.copy(navBarStyle = s) }

    // ── Monet / Seed ──────────────────────────────────────
    fun setMonetStyle(style: String) = _state.update { it.copy(monetStyle = style) }

    /** Step 1 — builds the JSON and signals the Screen to open a SAF CreateDocument picker */
    fun exportTheme() {
        val s = _state.value
        val json = buildString {
            appendLine("{")
            appendLine("  \"preset\": \"${s.preset.name}\",")
            appendLine("  \"monetStyle\": \"${s.monetStyle}\",")
            appendLine("  \"isDark\": ${s.isDark},")
            appendLine("  \"isAmoled\": ${s.isAmoled},")
            appendLine("  \"pitchBlack\": ${s.pitchBlack},")
            appendLine("  \"accurateShades\": ${s.accurateShades},")
            appendLine("  \"useGlassEffect\": ${s.useGlassEffect},")
            appendLine("  \"useGradientBackground\": ${s.useGradientBackground},")
            appendLine("  \"useDynamicColor\": ${s.useDynamicColor},")
            appendLine("  \"cardStyle\": \"${s.cardStyle.name}\",")
            appendLine("  \"cornerRadiusScale\": ${s.cornerRadiusScale},")
            appendLine("  \"fontScale\": ${s.fontScale},")
            appendLine("  \"elevationScale\": ${s.elevationScale},")
            appendLine("  \"animationScale\": ${s.animationScale},")
            appendLine("  \"accentIntensity\": ${s.accentIntensity},")
            appendLine("  \"navBarStyle\": \"${s.navBarStyle.name}\",")
            appendLine("  \"seedColor\": ${s.seedColor},")
            appendLine("  \"iconShape\": \"${s.iconShape}\",")
            appendLine("  \"transparentStatusBar\": ${s.transparentStatusBar},")
            appendLine("  \"transparentNavBar\": ${s.transparentNavBar},")
            appendLine("  \"hideNotch\": ${s.hideNotch}")
            append("}")
        }
        val fileName = "accu_theme_${System.currentTimeMillis()}.json"
        _state.update { it.copy(pendingExportJson = json, pendingExportFileName = fileName) }
    }

    /** Step 2 — called after user picks a save location via CreateDocument */
    fun writeExportToUri(uri: Uri, contentResolver: ContentResolver) {
        val json = _state.value.pendingExportJson ?: return
        _state.update { it.copy(pendingExportJson = null, pendingExportFileName = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    ?: throw Exception("Cannot open output stream")
                _state.update { it.copy(snackbarMessage = "Theme exported to selected location") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun clearPendingExport() {
        _state.update { it.copy(pendingExportJson = null, pendingExportFileName = null) }
    }

    /** Apply imported JSON theme. Call from the Screen after user picks a file. */
    fun importThemeFromJson(json: String) {
        viewModelScope.launch {
            try {
                fun String.jStr(key: String) = Regex(""""$key"\s*:\s*"([^"]+)"""").find(this)?.groupValues?.getOrNull(1)
                fun String.jBool(key: String) = Regex(""""$key"\s*:\s*(true|false)""").find(this)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
                fun String.jFloat(key: String) = Regex(""""$key"\s*:\s*([\d.]+)""").find(this)?.groupValues?.getOrNull(1)?.toFloatOrNull()

                val preset     = json.jStr("preset")?.let { n -> ACCThemePreset.entries.firstOrNull { it.name == n } }
                val cardStyle  = json.jStr("cardStyle")?.let { n -> CardStyle.entries.firstOrNull { it.name == n } }
                val navBarStyle = json.jStr("navBarStyle")?.let { n -> NavBarStyle.entries.firstOrNull { it.name == n } }

                _state.update { s ->
                    s.copy(
                        preset               = preset                           ?: s.preset,
                        monetStyle           = json.jStr("monetStyle")          ?: s.monetStyle,
                        isDark               = json.jBool("isDark")             ?: s.isDark,
                        isAmoled             = json.jBool("isAmoled")           ?: s.isAmoled,
                        pitchBlack           = json.jBool("pitchBlack")         ?: s.pitchBlack,
                        accurateShades       = json.jBool("accurateShades")     ?: s.accurateShades,
                        useGlassEffect       = json.jBool("useGlassEffect")     ?: s.useGlassEffect,
                        useGradientBackground= json.jBool("useGradientBackground") ?: s.useGradientBackground,
                        useDynamicColor      = json.jBool("useDynamicColor")    ?: s.useDynamicColor,
                        cardStyle            = cardStyle                        ?: s.cardStyle,
                        cornerRadiusScale    = json.jFloat("cornerRadiusScale") ?: s.cornerRadiusScale,
                        fontScale            = json.jFloat("fontScale")         ?: s.fontScale,
                        elevationScale       = json.jFloat("elevationScale")    ?: s.elevationScale,
                        animationScale       = json.jFloat("animationScale")    ?: s.animationScale,
                        accentIntensity      = json.jFloat("accentIntensity")   ?: s.accentIntensity,
                        navBarStyle          = navBarStyle                      ?: s.navBarStyle,
                        iconShape            = json.jStr("iconShape")           ?: s.iconShape,
                        transparentStatusBar = json.jBool("transparentStatusBar") ?: s.transparentStatusBar,
                        transparentNavBar    = json.jBool("transparentNavBar")  ?: s.transparentNavBar,
                        hideNotch            = json.jBool("hideNotch")          ?: s.hideNotch,
                        snackbarMessage      = "Theme imported — tap Apply to save",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Import failed: ${e.message}") }
            }
        }
    }

    fun importTheme() {
        // Triggers the file picker in the Screen — actual import handled by importThemeFromJson()
        _state.update { it.copy(snackbarMessage = "Tap the import icon and select a .json theme file") }
    }
    fun setSeedColor(color: Int)     = _state.update { it.copy(seedColor = color) }

    // ── Legacy toggles ────────────────────────────────────
    fun togglePitchBlack(v: Boolean)            = _state.update { it.copy(pitchBlack = v) }
    fun toggleAccurateShades(v: Boolean)        = _state.update { it.copy(accurateShades = v) }
    fun toggleTransparentStatusBar(v: Boolean)  = _state.update { it.copy(transparentStatusBar = v) }
    fun toggleTransparentNavBar(v: Boolean)     = _state.update { it.copy(transparentNavBar = v) }
    fun toggleHideNotch(v: Boolean)             = _state.update { it.copy(hideNotch = v) }

    // ── Apply & Persist ───────────────────────────────────
    fun applyTheme(id: Long = -1) {
        viewModelScope.launch {
            val s = _state.value

            // Persist to DataStore via ThemeManager
            themeManager.save(
                ACCThemeConfig(
                    preset               = s.preset,
                    isDark               = s.isDark,
                    useGlassEffect       = s.useGlassEffect,
                    useDynamicColor      = s.useDynamicColor,
                    isAmoled             = s.isAmoled,
                    cornerRadiusScale    = s.cornerRadiusScale,
                    elevationScale       = s.elevationScale,
                    animationScale       = s.animationScale,
                    fontScale            = s.fontScale,
                    navBarStyle          = s.navBarStyle,
                    cardStyle            = s.cardStyle,
                    monetStyle           = s.monetStyle,
                    useGradientBackground= s.useGradientBackground,
                    accentIntensity      = s.accentIntensity,
                )
            )

            // Also save to Room DB for history
            if (id > 0) {
                customThemeDao.clearApplied()
                val existing = customThemeDao.observeAll().first().firstOrNull { it.id == id }
                existing?.let { customThemeDao.update(it.copy(isApplied = true)) }
            } else {
                customThemeDao.clearApplied()
                customThemeDao.insert(
                    CustomThemeEntity(
                        name           = "${s.preset.displayName} · ${System.currentTimeMillis()}",
                        monetStyle     = s.monetStyle,
                        seedColor      = s.seedColor,
                        accurateShades = s.accurateShades,
                        pitchBlackTheme= s.pitchBlack,
                        isApplied      = true,
                    )
                )
            }
            _state.update { it.copy(snackbarMessage = "✅ Theme applied! Changes take effect on next screen.") }
        }
    }

    fun resetToDefaults() {
        _state.update {
            it.copy(
                preset               = ACCThemePreset.MIDNIGHT_AMOLED,
                monetStyle           = "TONAL_SPOT",
                isDark               = true,
                isAmoled             = false,
                useGlassEffect       = true,
                useGradientBackground= true,
                useDynamicColor      = false,
                cardStyle            = CardStyle.GLASS,
                cornerRadiusScale    = 1.0f,
                fontScale            = 1.0f,
                elevationScale       = 1.0f,
                animationScale       = 1.0f,
                accentIntensity      = 1.0f,
                navBarStyle          = NavBarStyle.GLASS,
                snackbarMessage      = "Reset to defaults — tap Apply to save",
            )
        }
    }

    fun deleteTheme(id: Long) {
        viewModelScope.launch {
            val theme = customThemeDao.observeAll().first().firstOrNull { it.id == id } ?: return@launch
            customThemeDao.delete(theme)
        }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbarMessage = null) }
}
