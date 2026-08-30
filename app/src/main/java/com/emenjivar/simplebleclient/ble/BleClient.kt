package com.emenjivar.simplebleclient.ble

import com.emenjivar.simplebleclient.ble.commands.BleCommand
import com.emenjivar.simplebleclient.ble.commands.json.JSONChunk
import com.emenjivar.simplebleclient.ble.model.BleConnectionState
import com.emenjivar.simplebleclient.ble.model.BluetoothDeviceModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract exposed to the ViewModels. The active flavor (`raspberry` or `mock`)
 * decides which implementation is injected.
 */
interface BleClient {
    val connectionState: StateFlow<BleConnectionState>
    val scannedDevices: StateFlow<List<BluetoothDeviceModel>>

    fun startScan()
    fun stopScan()
    fun connect(model: BluetoothDeviceModel)
    fun disconnect()

    fun <T> observe(command: BleCommand.Read<T>): Flow<T>
    fun <T> readCharacteristic(command: BleCommand.Read<T>)
    fun <T> writeCharacteristic(command: BleCommand.Write<T>, value: T)

    suspend fun getMTU(): Int
    suspend fun <T> write(command: BleCommand.Write<T>, value: T)
    suspend fun <T> read(command: BleCommand.Read<T>): T

    /**
     * Same as read<T> but for JSON
     */
    suspend fun read(command: BleCommand.ReadJSON): JSONChunk
}
