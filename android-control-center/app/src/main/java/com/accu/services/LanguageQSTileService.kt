package com.accu.services

import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.Locale

class LanguageQSTileService : TileService() {

    private val quickSwitchLocales = listOf("en", "zh", "ja", "de", "fr", "es", "ar")

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        cycleToNextLocale()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val currentLocale = Locale.getDefault()
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Language"
        tile.subtitle = currentLocale.displayName.take(16)
        tile.updateTile()
    }

    private fun cycleToNextLocale() {
        val prefs = getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
        val currentIdx = prefs.getInt("quick_locale_idx", 0)
        val nextIdx = (currentIdx + 1) % quickSwitchLocales.size
        prefs.edit().putInt("quick_locale_idx", nextIdx).apply()
    }
}
