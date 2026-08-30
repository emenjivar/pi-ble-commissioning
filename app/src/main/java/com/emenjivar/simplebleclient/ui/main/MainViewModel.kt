package com.emenjivar.simplebleclient.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emenjivar.simplebleclient.ble.BleClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val customBluetoothManager: BleClient
) : ViewModel() {
    val uiState = MutableStateFlow(MainUiState())

    val pairedDevices = customBluetoothManager.scannedDevices
    val connectionState = customBluetoothManager.connectionState

    init {
        combine(pairedDevices, connectionState) { pairedDevices, connectionState ->
            uiState.update {
                it.copy(pairedDevices = pairedDevices, connectionState = connectionState)
            }
        }.launchIn(viewModelScope)
    }

    fun startScan() = customBluetoothManager.startScan()
    fun stopScan() = customBluetoothManager.stopScan()
}