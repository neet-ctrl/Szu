package com.accu.services

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class BluetoothTileService : TileService() {

    companion object {
        private const val TAG = "BluetoothTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val adapter = getBluetoothAdapter() ?: return
        try {
            if (adapter.isEnabled) {
                adapter.disable()
            } else {
                adapter.enable()
            }
            updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle Bluetooth", e)
        }
    }

    private fun updateTile() {
        val adapter = getBluetoothAdapter()
        val tile = qsTile ?: return
        if (adapter == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Bluetooth"
            tile.subtitle = "Not available"
        } else {
            when (adapter.state) {
                BluetoothAdapter.STATE_ON -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Bluetooth"
                    tile.subtitle = "On"
                }
                BluetoothAdapter.STATE_OFF -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Bluetooth"
                    tile.subtitle = "Off"
                }
                BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_TURNING_OFF -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Bluetooth"
                    tile.subtitle = "..."
                }
                else -> {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.label = "Bluetooth"
                    tile.subtitle = "Unknown"
                }
            }
        }
        tile.updateTile()
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }
}
