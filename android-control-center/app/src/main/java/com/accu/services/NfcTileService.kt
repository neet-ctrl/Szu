package com.accu.services

import android.nfc.NfcAdapter
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class NfcTileService : TileService() {

    companion object {
        private const val TAG = "NfcTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // NFC toggle requires WRITE_SECURE_SETTINGS via Shizuku
        // Toggle via "svc nfc enable/disable"
        try {
            val adapter = NfcAdapter.getDefaultAdapter(this)
            if (adapter != null) {
                // Actual toggle needs elevated permission — trigger via Shizuku shell
                Log.d(TAG, "NFC toggle requested — current: ${adapter.isEnabled}")
            }
            updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle NFC", e)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "NFC"
            tile.subtitle = "Not available"
        } else {
            tile.state = if (adapter.isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "NFC"
            tile.subtitle = if (adapter.isEnabled) "On" else "Off"
        }
        tile.updateTile()
    }
}
