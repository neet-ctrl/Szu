package com.accu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.accu.connection.AccuConnectionManager
import com.accu.navigation.AppNavigation
import com.accu.navigation.Screen
import com.accu.ui.theme.ACCTheme
import com.accu.ui.theme.ACCThemeConfig
import com.accu.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDest = resolveStartDestination(intent)
        setContent {
            val config by themeManager.themeConfig.collectAsState(initial = ACCThemeConfig())
            ACCTheme(config = config) {
                AppNavigation(initialRoute = startDest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun resolveStartDestination(intent: Intent?): String? {
        return when (intent?.action) {
            AccuConnectionManager.ACTION_OPEN_PAIRING,
            AccuConnectionManager.ACTION_OPEN_CONNECTION -> Screen.AdbPairing.route
            else -> null
        }
    }
}
