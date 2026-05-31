package com.accu.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.data.repositories.ShellRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
    val privateDnsHost: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val connectionManager: AccuConnectionManager,
    private val shellRepository: ShellRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    init {
        loadNetworkState()
    }

    fun loadNetworkState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // All queries run on the TARGET device via exec()
                val wifiOn       = connectionManager.exec("settings get global wifi_on 2>/dev/null").output.trim()
                val mobileOn     = connectionManager.exec("settings get global mobile_data 2>/dev/null").output.trim()
                val airplaneOn   = connectionManager.exec("settings get global airplane_mode_on 2>/dev/null").output.trim()
                val nfcOn        = connectionManager.exec("settings get secure nfc_on 2>/dev/null || settings get global nfc_on 2>/dev/null").output.trim()
                val btState      = connectionManager.exec("settings get global bluetooth_on 2>/dev/null").output.trim()

                // Wi-Fi SSID from wpa_supplicant or dumpsys
                val ssidRaw  = connectionManager.exec(
                    "dumpsys wifi 2>/dev/null | grep -m1 'SSID:' | sed 's/.*SSID: //' | sed 's/,.*//' | tr -d '\"'"
                ).output.trim()

                // IP address of target device
                val ipRaw    = connectionManager.exec(
                    "ip route get 8.8.8.8 2>/dev/null | grep -oE 'src [0-9.]+' | awk '{print \$2}'"
                ).output.trim()

                // VPN: check for tun/vpn interface
                val vpnRaw   = connectionManager.exec(
                    "ip link show 2>/dev/null | grep -c 'tun\\|ppp\\|vpn'"
                ).output.trim()

                // Carrier name from telephony
                val carrier  = connectionManager.exec(
                    "getprop gsm.operator.alpha 2>/dev/null"
                ).output.trim()

                // Network type
                val netType  = connectionManager.exec(
                    "dumpsys connectivity 2>/dev/null | grep -oE 'type: (WIFI|MOBILE|ETHERNET)[^,]*' | head -1 | sed 's/type: //'"
                ).output.trim()

                // Private DNS
                val dnsMode  = connectionManager.exec("settings get global private_dns_mode 2>/dev/null").output.trim()
                val dnsHost  = connectionManager.exec("settings get global private_dns_specifier 2>/dev/null").output.trim()

                // Hotspot
                val hotspotRaw = connectionManager.exec(
                    "dumpsys wifi 2>/dev/null | grep -m1 'isWifiApEnabled\\|SoftApState' | grep -i 'true\\|started'"
                ).output.trim()

                _uiState.update {
                    it.copy(
                        isLoading             = false,
                        isWifiEnabled         = wifiOn == "1",
                        isMobileDataEnabled   = mobileOn == "1",
                        isAirplaneModeEnabled = airplaneOn == "1",
                        isNfcEnabled          = nfcOn == "1",
                        isBluetoothEnabled    = btState == "1",
                        isVpnActive           = (vpnRaw.toIntOrNull() ?: 0) > 0,
                        wifiSsid              = ssidRaw.ifBlank { null },
                        ipAddress             = ipRaw.ifBlank { null },
                        carrierName           = carrier.ifBlank { null },
                        networkType           = netType.ifBlank { if (wifiOn == "1") "Wi-Fi" else if (mobileOn == "1") "Mobile" else "None" },
                        isHotspotEnabled      = hotspotRaw.isNotBlank(),
                        privateDnsMode        = dnsMode.ifBlank { null },
                        privateDnsHost        = dnsHost.ifBlank { null },
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "NetworkViewModel: failed to load state from target device")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleWifi() {
        viewModelScope.launch {
            val current = _uiState.value.isWifiEnabled
            val cmd = if (current) "svc wifi disable" else "svc wifi enable"
            connectionManager.exec(cmd)
            _uiState.update { it.copy(isWifiEnabled = !current) }
        }
    }

    fun toggleMobileData() {
        viewModelScope.launch {
            val current = _uiState.value.isMobileDataEnabled
            val cmd = if (current) "svc data disable" else "svc data enable"
            connectionManager.exec(cmd)
            _uiState.update { it.copy(isMobileDataEnabled = !current) }
        }
    }

    fun toggleHotspot() {
        viewModelScope.launch {
            val current = _uiState.value.isHotspotEnabled
            val cmd = if (current) "svc wifi hotspot disable" else "svc wifi hotspot enable"
            val result = connectionManager.exec(cmd)
            if (!result.output.contains("error", ignoreCase = true) &&
                !result.error.contains("error", ignoreCase = true)) {
                _uiState.update { it.copy(isHotspotEnabled = !current) }
            }
        }
    }

    fun toggleBluetooth() {
        viewModelScope.launch {
            val current = _uiState.value.isBluetoothEnabled
            val cmd = if (current) "svc bluetooth disable" else "svc bluetooth enable"
            connectionManager.exec(cmd)
            _uiState.update { it.copy(isBluetoothEnabled = !current) }
        }
    }

    fun toggleNfc() {
        viewModelScope.launch {
            val current = _uiState.value.isNfcEnabled
            val cmd = if (current) "svc nfc disable" else "svc nfc enable"
            connectionManager.exec(cmd)
            _uiState.update { it.copy(isNfcEnabled = !current) }
        }
    }

    fun toggleAirplaneMode() {
        viewModelScope.launch {
            val current = _uiState.value.isAirplaneModeEnabled
            val newVal = if (current) 0 else 1
            connectionManager.exec("settings put global airplane_mode_on $newVal")
            connectionManager.exec("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ${!current}")
            _uiState.update { it.copy(isAirplaneModeEnabled = !current) }
        }
    }

    fun openPrivateDnsSettings() {
        viewModelScope.launch {
            connectionManager.exec("am start -a android.settings.PRIVATE_DNS_SETTINGS")
        }
    }

    fun refresh() { loadNetworkState() }
}
