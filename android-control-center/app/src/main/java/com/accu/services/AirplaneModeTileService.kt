package com.accu.services

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class AirplaneModeTileService : TileService() {

    companion object {
        private const val TAG = "AirplaneModeTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val currentMode = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        val newMode = if (currentMode == 0) 1 else 0
        try {
            // Requires WRITE_SECURE_SETTINGS — use Shizuku:
            // settings put global airplane_mode_on <0|1>
            // Then broadcast: am broadcast -a android.intent.action.AIRPLANE_MODE
            Log.d(TAG, "Airplane mode toggle: $currentMode → $newMode")
            updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle airplane mode", e)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isEnabled = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Airplane Mode"
        tile.subtitle = if (isEnabled) "On" else "Off"
        tile.updateTile()
    }
}
