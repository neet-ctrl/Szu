package com.accu.ui.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.data.repositories.ShellRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetworkUiState(
    val isWifiEnabled: Boolean = false,
    val isMobileDataEnabled: Boolean = false,
    val isHotspotEnabled: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isNfcEnabled: Boolean = false,
    val isAirplaneModeEnabled: Boolean = false,
    val isVpnActive: Boolean = false,
    val wifiSsid: String? = null,
    val ipAddress: String? = null,
    val wifiSignalStrength: Int? = null,
    val carrierName: String? = null,
    val networkType: String? = null,
    val dnsServer: String? = null,
    val privateDnsMode: String? = null,
    val privateDnsHost: String? = null
)

@HiltViewModel
class NetworkViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRepository: ShellRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    init {
        loadNetworkState()
    }

    private fun loadNetworkState() {
        viewModelScope.launch {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val isVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val wifiInfo = wifiManager.connectionInfo
            val ssid = if (isWifi && wifiInfo.ssid != null && wifiInfo.ssid != "<unknown ssid>")
                wifiInfo.ssid.trim('"') else null

            _uiState.update {
                it.copy(
                    isWifiEnabled = wifiManager.isWifiEnabled,
                    isMobileDataEnabled = isCellular,
                    isVpnActive = isVpn,
                    wifiSsid = ssid,
                    wifiSignalStrength = if (isWifi) wifiInfo.rssi else null,
                    networkType = when {
                        isWifi -> "Wi-Fi"
                        isCellular -> "Mobile"
                        else -> "None"
                    }
                )
            }
            // Get IP
            try {
                val ipResult = shellRepository.execute("ip route get 8.8.8.8 | awk '{print \$7}'")
                _uiState.update { it.copy(ipAddress = ipResult.trim().ifBlank { null }) }
            } catch (_: Exception) {}
        }
    }

    fun toggleWifi() {
        viewModelScope.launch {
            val current = _uiState.value.isWifiEnabled
            val cmd = if (current) "svc wifi disable" else "svc wifi enable"
            shellRepository.execute(cmd)
            _uiState.update { it.copy(isWifiEnabled = !current) }
        }
    }

    fun toggleMobileData() {
        viewModelScope.launch {
            val current = _uiState.value.isMobileDataEnabled
            val cmd = if (current) "svc data disable" else "svc data enable"
            shellRepository.execute(cmd)
            _uiState.update { it.copy(isMobileDataEnabled = !current) }
        }
    }

    fun toggleHotspot() {
        viewModelScope.launch {
            val current = _uiState.value.isHotspotEnabled
            _uiState.update { it.copy(isHotspotEnabled = !current) }
        }
    }

    fun toggleBluetooth() {
        viewModelScope.launch {
            val current = _uiState.value.isBluetoothEnabled
            val cmd = if (current) "svc bluetooth disable" else "svc bluetooth enable"
            shellRepository.execute(cmd)
            _uiState.update { it.copy(isBluetoothEnabled = !current) }
        }
    }

    fun toggleNfc() {
        viewModelScope.launch {
            val current = _uiState.value.isNfcEnabled
            val cmd = if (current) "svc nfc disable" else "svc nfc enable"
            shellRepository.execute(cmd)
            _uiState.update { it.copy(isNfcEnabled = !current) }
        }
    }

    fun toggleAirplaneMode() {
        viewModelScope.launch {
            val current = _uiState.value.isAirplaneModeEnabled
            val newVal = if (current) 0 else 1
            shellRepository.execute("settings put global airplane_mode_on $newVal")
            shellRepository.execute("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ${!current}")
            _uiState.update { it.copy(isAirplaneModeEnabled = !current) }
        }
    }

    fun openPrivateDnsSettings() {
        viewModelScope.launch {
            shellRepository.execute("am start -a android.settings.PRIVATE_DNS_SETTINGS")
        }
    }
}
