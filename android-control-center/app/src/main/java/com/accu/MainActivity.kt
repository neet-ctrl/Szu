package com.accu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.accu.connection.AccuConnectionManager
import com.accu.navigation.AppNavigation
import com.accu.navigation.Screen
import com.accu.ui.theme.ACCTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Determine if we should navigate to AdbPairingScreen on launch
        // (e.g. when user taps the "Enter PIN" notification)
        val startDest = resolveStartDestination(intent)
        setContent {
            ACCTheme {
                AppNavigation(initialRoute = startDest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // When the activity is already running and the notification is tapped,
        // we need to navigate to AdbPairingScreen. The AppNavigation handles
        // this via the LocalContext-based intent check on recomposition.
        // Re-setting the intent triggers the NavLaunchedEffect in AppNavigation.
    }

    private fun resolveStartDestination(intent: Intent?): String? {
        return when (intent?.action) {
            AccuConnectionManager.ACTION_OPEN_PAIRING,
            AccuConnectionManager.ACTION_OPEN_CONNECTION -> Screen.AdbPairing.route
            else -> null
        }
    }
}
