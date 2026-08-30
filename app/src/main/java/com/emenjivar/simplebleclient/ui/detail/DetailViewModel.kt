package com.emenjivar.simplebleclient.ui.detail

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emenjivar.simplebleclient.ble.model.BleConnectionState
import com.emenjivar.simplebleclient.ble.model.BluetoothDeviceModel
import com.emenjivar.simplebleclient.ble.BleClient
import com.emenjivar.simplebleclient.ble.commands.GetIPAddress
import com.emenjivar.simplebleclient.ble.commands.GetSSID
import com.emenjivar.simplebleclient.ble.commands.LEDCommand
import com.emenjivar.simplebleclient.ble.commands.ReadLedStatus
import com.emenjivar.simplebleclient.ble.commands.WriteLedStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    private val bleClient: BleClient,
    @Assisted private val route: DetailRoute,
) : ViewModel() {

    // TODO: use backing fields here
    private val _uiState = MutableStateFlow(
        DetailUiState(
            macAddress = route.device.macAddress,
            deviceName = route.device.name ?: "Unknown"
        )
    )
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    // Assuming a connected device
    val connectionState = bleClient.connectionState

    private val ipAddress = bleClient.observe(GetIPAddress)
    private val ssid = bleClient.observe(GetSSID)

    // Notification type, needs an initial default value
    private val ledState = bleClient.observe(ReadLedStatus)
        .onStart { emit(LEDCommand.OFF) }

    init {
        connect(route.device)

        // Read characteristics when connection is ready
        connectionState.onEach { state ->
            if (state is BleConnectionState.Connected && state.ready) {
                bleClient.readCharacteristic(GetIPAddress)
                bleClient.readCharacteristic(GetSSID)
            }
        }.launchIn(viewModelScope)

        // Listed BLE responses and updated uiState
        combine(
            ipAddress,
            ssid,
            ledState,
            connectionState
        ) { ipAddress, ssid, ledState, connectionState ->
            _uiState.update {
                it.copy(
                    ipAddress = ipAddress,
                    ssid = ssid,
                    ledState = ledState,
                    connectionState = connectionState
                )
            }
        }.launchIn(viewModelScope)
    }

    fun updateLedState(state: LEDCommand) {
        bleClient.writeCharacteristic(WriteLedStatus, state)
    }

    private fun connect(device: BluetoothDeviceModel) = bleClient.connect(device)

    fun connect() = connect(route.device)

    fun disconnect() = bleClient.disconnect()

    @SuppressLint("MissingPermission")
    fun scanWifiNetworks() {
        _uiState.update { it.copy(wifiScanResult = StateResult.Loading) }
        val wifiManager = context.getSystemService(WifiManager::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                @Suppress("DEPRECATION")
                val networks = wifiManager.scanResults
                    .filter { it.SSID.isNotEmpty() }
                    .map { result ->
                        WifiNetwork(
                            ssid = result.SSID,
                            rssi = result.level
                        )
                    }.sortedByDescending { it.rssi }

                _uiState.update {
                    it.copy(
                        wifiScanResult = StateResult.Success(networks)
                    )
                }
                ctx.unregisterReceiver(this)
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        @Suppress("DEPRECATION")
        wifiManager.startScan()
    }

    override fun onCleared() {
        disconnect()
    }

    @AssistedFactory
    interface Factory {
        fun create(route: DetailRoute): DetailViewModel
    }
}