package com.accu.ui.theme

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────
//  Theme Presets — 12 complete, hand-crafted Material 3 color palettes
//  Each has a dark + light variant. Used by ThemeManager.
// ─────────────────────────────────────────────────────────────────

enum class ACCThemePreset(
    val displayName: String,
    val emoji: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
    val accentGlow: Color,
) {
    DYNAMIC(
        "Material You",       "🎨", "Follows your wallpaper colors automatically",
        Color(0xFF4A56E2), Color(0xFF5D5C7D), Color(0xFF7A527C), Color(0xFF4A56E2),
    ),
    MIDNIGHT_AMOLED(
        "Midnight AMOLED",    "🌑", "Pure black with deep indigo accents — battery saver",
        Color(0xFF7C83FF), Color(0xFF9B8DFF), Color(0xFFBB86FC), Color(0xFF7C83FF),
    ),
    NEON_MATRIX(
        "Neon Matrix",        "💚", "Cyberpunk green on deep black — hacker aesthetic",
        Color(0xFF00FF88), Color(0xFF00E5CC), Color(0xFF69FF47), Color(0xFF00FF88),
    ),
    AURORA_BOREALIS(
        "Aurora Borealis",    "🌌", "Teal-green aurora shimmer over deep navy",
        Color(0xFF00D4FF), Color(0xFF00BFA5), Color(0xFF64FFDA), Color(0xFF00D4FF),
    ),
    VOLCANIC_FIRE(
        "Volcanic Fire",      "🌋", "Deep red-orange lava glow on near-black",
        Color(0xFFFF6D00), Color(0xFFFF1744), Color(0xFFFFAB40), Color(0xFFFF6D00),
    ),
    GOLD_LUXURY(
        "Gold Luxury",        "✨", "Premium amber gold on rich dark brown",
        Color(0xFFFFD600), Color(0xFFFFAB00), Color(0xFFFF6F00), Color(0xFFFFD600),
    ),
    SAKURA_PINK(
        "Sakura Pink",        "🌸", "Soft cherry blossom pink — elegant and light",
        Color(0xFFFF4081), Color(0xFFE91E63), Color(0xFFFF80AB), Color(0xFFFF4081),
    ),
    OCEAN_DEPTH(
        "Ocean Depth",        "🌊", "Deep sea blue-teal gradient — calm and focused",
        Color(0xFF1E88E5), Color(0xFF0097A7), Color(0xFF26C6DA), Color(0xFF1E88E5),
    ),
    ROYAL_VIOLET(
        "Royal Violet",       "👑", "Rich purple-magenta royalty — bold and vivid",
        Color(0xFFD500F9), Color(0xFF8E24AA), Color(0xFFEA80FC), Color(0xFFD500F9),
    ),
    FOREST_GROVE(
        "Forest Grove",       "🌿", "Natural deep green — calm earth tones",
        Color(0xFF43A047), Color(0xFF2E7D32), Color(0xFF81C784), Color(0xFF43A047),
    ),
    ROSE_GOLD(
        "Rose Gold",          "🌹", "Warm rose-gold metallic — premium feel",
        Color(0xFFE57373), Color(0xFFAD1457), Color(0xFFF06292), Color(0xFFE57373),
    ),
    SLATE_MONOCHROME(
        "Slate Monochrome",   "⬛", "Pure grey-white — minimal distraction-free",
        Color(0xFF78909C), Color(0xFF546E7A), Color(0xFF90A4AE), Color(0xFF78909C),
    ),
}

// ─── Build complete dark color scheme from preset ───
fun ACCThemePreset.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary              = primaryColor,
    onPrimary            = Color(0xFF000000),
    primaryContainer     = primaryColor.copy(alpha = 0.25f).compositeOver(Color(0xFF121212)),
    onPrimaryContainer   = primaryColor,
    secondary            = secondaryColor,
    onSecondary          = Color(0xFF000000),
    secondaryContainer   = secondaryColor.copy(alpha = 0.20f).compositeOver(Color(0xFF121212)),
    onSecondaryContainer = secondaryColor,
    tertiary             = tertiaryColor,
    onTertiary           = Color(0xFF000000),
    tertiaryContainer    = tertiaryColor.copy(alpha = 0.20f).compositeOver(Color(0xFF121212)),
    onTertiaryContainer  = tertiaryColor,
    background           = if (this == ACCThemePreset.MIDNIGHT_AMOLED) Color(0xFF000000) else Color(0xFF0E0E12),
    onBackground         = Color(0xFFE8E8F0),
    surface              = if (this == ACCThemePreset.MIDNIGHT_AMOLED) Color(0xFF000000) else Color(0xFF111116),
    onSurface            = Color(0xFFE8E8F0),
    surfaceVariant       = Color(0xFF1E1E26),
    onSurfaceVariant     = Color(0xFFB0B0C0),
    outline              = primaryColor.copy(alpha = 0.40f),
    outlineVariant       = Color(0xFF2A2A36),
    error                = Color(0xFFFF6B6B),
    errorContainer       = Color(0xFF4D1515),
    onError              = Color(0xFFFFFFFF),
    onErrorContainer     = Color(0xFFFFB3B3),
    inverseSurface       = Color(0xFFE8E8F0),
    inverseOnSurface     = Color(0xFF111116),
    inversePrimary       = primaryColor.copy(alpha = 0.70f),
    surfaceTint          = primaryColor,
    scrim                = Color(0xFF000000),
    surfaceContainer         = Color(0xFF16161E),
    surfaceContainerHigh     = Color(0xFF1C1C26),
    surfaceContainerHighest  = Color(0xFF22222E),
    surfaceContainerLow      = Color(0xFF12121A),
    surfaceContainerLowest   = Color(0xFF0E0E14),
    surfaceBright        = Color(0xFF242430),
    surfaceDim           = Color(0xFF0A0A10),
)

// ─── Build complete light color scheme from preset ───
fun ACCThemePreset.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary              = primaryColor.darker(0.2f),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = primaryColor.copy(alpha = 0.12f).compositeOver(Color(0xFFFFFFFF)),
    onPrimaryContainer   = primaryColor.darker(0.3f),
    secondary            = secondaryColor.darker(0.1f),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = secondaryColor.copy(alpha = 0.10f).compositeOver(Color(0xFFFFFFFF)),
    onSecondaryContainer = secondaryColor.darker(0.3f),
    tertiary             = tertiaryColor.darker(0.1f),
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = tertiaryColor.copy(alpha = 0.10f).compositeOver(Color(0xFFFFFFFF)),
    onTertiaryContainer  = tertiaryColor.darker(0.3f),
    background           = Color(0xFFFAFAFE),
    onBackground         = Color(0xFF1A1A22),
    surface              = Color(0xFFFAFAFE),
    onSurface            = Color(0xFF1A1A22),
    surfaceVariant       = Color(0xFFEEEEF6),
    onSurfaceVariant     = Color(0xFF48485A),
    outline              = primaryColor.darker(0.1f).copy(alpha = 0.50f),
    outlineVariant       = Color(0xFFCCCCDA),
    error                = Color(0xFFB71C1C),
    errorContainer       = Color(0xFFFFDAD6),
    onError              = Color(0xFFFFFFFF),
    onErrorContainer     = Color(0xFF410002),
    inverseSurface       = Color(0xFF1A1A22),
    inverseOnSurface     = Color(0xFFFAFAFE),
    inversePrimary       = primaryColor,
    surfaceTint          = primaryColor,
    scrim                = Color(0xFF000000),
    surfaceContainer         = Color(0xFFF0F0F8),
    surfaceContainerHigh     = Color(0xFFE8E8F2),
    surfaceContainerHighest  = Color(0xFFE0E0EC),
    surfaceContainerLow      = Color(0xFFF6F6FC),
    surfaceContainerLowest   = Color(0xFFFFFFFF),
    surfaceBright        = Color(0xFFFAFAFE),
    surfaceDim           = Color(0xFFDEDEEA),
)

private fun Color.darker(factor: Float): Color = Color(
    red   = (red   * (1f - factor)).coerceIn(0f, 1f),
    green = (green * (1f - factor)).coerceIn(0f, 1f),
    blue  = (blue  * (1f - factor)).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun Color.compositeOver(background: Color): Color {
    val fg = this
    val a = fg.alpha
    return Color(
        red   = fg.red   * a + background.red   * (1f - a),
        green = fg.green * a + background.green * (1f - a),
        blue  = fg.blue  * a + background.blue  * (1f - a),
        alpha = 1f,
    )
}

// ─── Glass/Mica surface colors for each theme ───
val ACCThemePreset.glassSurface: Color
    get() = primaryColor.copy(alpha = 0.08f)

val ACCThemePreset.glowColor: Color
    get() = accentGlow.copy(alpha = 0.15f)
