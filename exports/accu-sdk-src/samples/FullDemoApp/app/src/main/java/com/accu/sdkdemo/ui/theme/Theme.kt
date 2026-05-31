package com.accu.sdkdemo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary        = PrimaryDark,
    secondary      = SecondaryDark,
    background     = BackgroundDark,
    surface        = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    error          = AccuRed,
    onPrimary      = Color.White,
    onBackground   = Color(0xFFE0E0E0),
    onSurface      = Color(0xFFE0E0E0),
)

private val LightColors = lightColorScheme(
    primary    = Primary,
    secondary  = Secondary,
    background = Background,
    surface    = Surface,
    error      = Error,
)

@Composable
fun AccuSdkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
