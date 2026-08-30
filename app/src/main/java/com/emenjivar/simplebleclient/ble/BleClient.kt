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

    /**
     * Subscribes to notifications for the characteristic in [command].
     * Emits every update the device pushes when calling [readCharacteristic]
     */
    fun <T> observe(command: BleCommand.Read<T>): Flow<T>

    /**
     * Fire-and-forget: request a read of the characteristic in [command].
     * The result arrives asynchronously through [observe].
     */
    fun <T> readCharacteristic(command: BleCommand.Read<T>)

    /**
     * Fire-and-forget: request a `write` to the characteristic in [command].
     * Does not wait for the `write` to complete
     */
    fun <T> writeCharacteristic(command: BleCommand.Write<T>, value: T)

    suspend fun getMTU(): Int

    /**
     * Writes [value] to the characteristic in [command] and suspends until it completes
     */
    suspend fun <T> write(command: BleCommand.Write<T>, value: T)

    /**
     * Reads the characteristic in [command] and suspend until the value is returned
     */
    suspend fun <T> read(command: BleCommand.Read<T>): T

    /**
     * Reads a single chunk from the characteristic in [command].
     * The caller must keep calling this until [JSONChunk.currentOffset]
     * reaches [JSONChunk.totalSize] to reassemble the full payload.
     */
    suspend fun read(command: BleCommand.ReadJSON): JSONChunk
}
