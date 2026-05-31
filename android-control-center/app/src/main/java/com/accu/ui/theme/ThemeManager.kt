package com.accu.ui.theme

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore("accu_theme")

data class ACCThemeConfig(
    val preset: ACCThemePreset = ACCThemePreset.MIDNIGHT_AMOLED,
    val isDark: Boolean = true,
    val useGlassEffect: Boolean = true,
    val useDynamicColor: Boolean = false,
    val isAmoled: Boolean = false,
    val cornerRadiusScale: Float = 1.0f,     // 0.5 = sharp, 1.0 = default, 1.5 = very round
    val elevationScale: Float = 1.0f,         // 0.0 = flat, 1.0 = normal, 2.0 = elevated
    val animationScale: Float = 1.0f,         // 0.5 = fast, 1.0 = normal, 2.0 = slow
    val fontScale: Float = 1.0f,
    val navBarStyle: NavBarStyle = NavBarStyle.GLASS,
    val cardStyle: CardStyle = CardStyle.GLASS,
    val monetStyle: String = "TONAL_SPOT",
    val useGradientBackground: Boolean = true,
    val useBlurEffect: Boolean = false,
    val accentIntensity: Float = 1.0f,        // 0.5 = subtle, 1.0 = normal, 2.0 = vivid
)

enum class NavBarStyle(val displayName: String) {
    SOLID("Solid"),
    GLASS("Glass / Transparent"),
    HIDDEN("Hidden (Gesture Only)"),
    FLOATING("Floating Pill"),
}

enum class CardStyle(val displayName: String) {
    FLAT("Flat / Minimal"),
    ELEVATED("Elevated Shadow"),
    GLASS("Glass Morphism"),
    OUTLINED("Outlined Border"),
    FILLED("Filled Container"),
}

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val PRESET_KEY         = stringPreferencesKey("theme_preset")
    private val IS_DARK_KEY        = booleanPreferencesKey("is_dark")
    private val GLASS_KEY          = booleanPreferencesKey("glass_effect")
    private val DYNAMIC_COLOR_KEY  = booleanPreferencesKey("dynamic_color")
    private val AMOLED_KEY         = booleanPreferencesKey("amoled")
    private val CORNER_KEY         = floatPreferencesKey("corner_radius")
    private val ELEVATION_KEY      = floatPreferencesKey("elevation")
    private val ANIMATION_KEY      = floatPreferencesKey("animation")
    private val FONT_SCALE_KEY     = floatPreferencesKey("font_scale")
    private val NAV_STYLE_KEY      = stringPreferencesKey("nav_style")
    private val CARD_STYLE_KEY     = stringPreferencesKey("card_style")
    private val MONET_KEY          = stringPreferencesKey("monet_style")
    private val GRADIENT_KEY       = booleanPreferencesKey("gradient_bg")
    private val BLUR_KEY           = booleanPreferencesKey("blur_effect")
    private val ACCENT_INTENSITY   = floatPreferencesKey("accent_intensity")

    val themeConfig: Flow<ACCThemeConfig> = context.themeDataStore.data.map { prefs ->
        ACCThemeConfig(
            preset          = ACCThemePreset.entries.find { it.name == prefs[PRESET_KEY] } ?: ACCThemePreset.MIDNIGHT_AMOLED,
            isDark          = prefs[IS_DARK_KEY] ?: true,
            useGlassEffect  = prefs[GLASS_KEY] ?: true,
            useDynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: false,
            isAmoled        = prefs[AMOLED_KEY] ?: false,
            cornerRadiusScale = prefs[CORNER_KEY] ?: 1.0f,
            elevationScale  = prefs[ELEVATION_KEY] ?: 1.0f,
            animationScale  = prefs[ANIMATION_KEY] ?: 1.0f,
            fontScale       = prefs[FONT_SCALE_KEY] ?: 1.0f,
            navBarStyle     = NavBarStyle.entries.find { it.name == prefs[NAV_STYLE_KEY] } ?: NavBarStyle.GLASS,
            cardStyle       = CardStyle.entries.find { it.name == prefs[CARD_STYLE_KEY] } ?: CardStyle.GLASS,
            monetStyle      = prefs[MONET_KEY] ?: "TONAL_SPOT",
            useGradientBackground = prefs[GRADIENT_KEY] ?: true,
            useBlurEffect   = prefs[BLUR_KEY] ?: false,
            accentIntensity = prefs[ACCENT_INTENSITY] ?: 1.0f,
        )
    }.catch { emit(ACCThemeConfig()) }

    suspend fun save(config: ACCThemeConfig) {
        context.themeDataStore.edit { prefs ->
            prefs[PRESET_KEY]        = config.preset.name
            prefs[IS_DARK_KEY]       = config.isDark
            prefs[GLASS_KEY]         = config.useGlassEffect
            prefs[DYNAMIC_COLOR_KEY] = config.useDynamicColor
            prefs[AMOLED_KEY]        = config.isAmoled
            prefs[CORNER_KEY]        = config.cornerRadiusScale
            prefs[ELEVATION_KEY]     = config.elevationScale
            prefs[ANIMATION_KEY]     = config.animationScale
            prefs[FONT_SCALE_KEY]    = config.fontScale
            prefs[NAV_STYLE_KEY]     = config.navBarStyle.name
            prefs[CARD_STYLE_KEY]    = config.cardStyle.name
            prefs[MONET_KEY]         = config.monetStyle
            prefs[GRADIENT_KEY]      = config.useGradientBackground
            prefs[BLUR_KEY]          = config.useBlurEffect
            prefs[ACCENT_INTENSITY]  = config.accentIntensity
        }
    }
}

// ─── Global composition locals for theme-aware components ───
val LocalACCThemeConfig = staticCompositionLocalOf { ACCThemeConfig() }

val ACCThemeConfig.effectiveCornerRadius: Float
    get() = 16f * cornerRadiusScale

val ACCThemeConfig.effectiveElevation: Float
    get() = 2f * elevationScale
