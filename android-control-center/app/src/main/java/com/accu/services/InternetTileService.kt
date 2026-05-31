package com.accu.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InternetTileService : TileService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        tile.state = if (hasInternet) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Internet"
        tile.subtitle = when {
            hasWifi -> "Wi-Fi"
            hasCellular -> "Mobile Data"
            else -> "Disconnected"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
