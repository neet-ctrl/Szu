package com.accu.ui.customization

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.db.dao.CustomThemeDao
import com.accu.data.db.entities.CustomThemeEntity
import com.accu.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    fun setPreset(preset: ACCThemePreset) = _state.update { it.copy(preset = preset) }

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

    fun exportTheme() {
        viewModelScope.launch {
            _state.update { it.copy(snackbarMessage = "Theme export is not yet implemented") }
        }
    }

    fun importTheme() {
        viewModelScope.launch {
            _state.update { it.copy(snackbarMessage = "Theme import is not yet implemented") }
        }
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
