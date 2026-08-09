package com.emenjivar.simplebleclient.ble.mock

import com.emenjivar.simplebleclient.ble.BleNotifications
import com.emenjivar.simplebleclient.ble.CustomBleManager
import com.emenjivar.simplebleclient.ble.commands.BleCommand
import com.emenjivar.simplebleclient.ble.commands.LEDCommand
import com.emenjivar.simplebleclient.ble.commands.getIPCharacteristicUUID
import com.emenjivar.simplebleclient.ble.commands.getSSIDCharacteristicUUID
import com.emenjivar.simplebleclient.ble.commands.ledCharacteristicUUID
import com.emenjivar.simplebleclient.ble.model.BleConnectionState
import com.emenjivar.simplebleclient.ble.model.BluetoothDeviceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fake BLE implementation that lets the app run end-to-end without a peripheral.
 * Selected when the `mock` flavor is active.
 */
class MockBleManager(
    private val bleNotifications: BleNotifications,
    private val mockBleDataSource: MockBleDataSource
) : CustomBleManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceModel>> =
        _scannedDevices.asStateFlow()

    private var ledState = LEDCommand.OFF

    override fun startScan() {
        scope.launch {
            delay(SCAN_DELAY_MS.milliseconds)
            val mockDevices = mockBleDataSource.getAllDevices().map {
                BluetoothDeviceModel(name = it.name, macAddress = it.macAddress)
            }
            _scannedDevices.update { mockDevices }
        }
    }

    override fun stopScan() {
        // No-op for the mock layer.
    }

    override fun connect(model: BluetoothDeviceModel) {
        scope.launch {
            _connectionState.update { BleConnectionState.Connecting }
            delay(CONNECT_DELAY_MS.milliseconds)
            _connectionState.update { BleConnectionState.Connected(model, ready = true) }
        }
    }

    override fun disconnect() {
        _connectionState.update { BleConnectionState.Disconnected }
    }

    override fun <T> observe(command: BleCommand.Read<T>): Flow<T> =
        bleNotifications.observe(command)

    override fun <T> readCharacteristic(command: BleCommand.Read<T>) {
        val value = fakeValueFor(command.characteristic) ?: return
        bleNotifications.emit(
            service = command.service,
            characteristic = command.characteristic,
            value = value
        )
    }

    override fun <T> writeCharacteristic(command: BleCommand.Write<T>, value: T) {
        if (command.characteristic == ledCharacteristicUUID && value is LEDCommand) {
            ledState = value
            // Echo the write back so ReadLedStatus observers update.
            bleNotifications.emit(
                service = command.service,
                characteristic = command.characteristic,
                value = byteArrayOf(value.bytes)
            )
        }
    }

    override suspend fun getMTU(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun <T> write(
        command: BleCommand.Write<T>,
        value: T
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun <T> read(command: BleCommand.Read<T>): T {
        TODO("Not yet implemented")
    }

    private fun fakeValueFor(characteristic: UUID): ByteArray? {
        val connectedDevice = connectionState.value as? BleConnectionState.Connected ?: return null
        val device = mockBleDataSource.getDeviceDataByMacAddress(
            macAddress = connectedDevice.device.macAddress
        ) ?: return null

        return when (characteristic) {
            getIPCharacteristicUUID -> device.ip.toByteArray()
            getSSIDCharacteristicUUID -> device.ssid.toByteArray()
            ledCharacteristicUUID -> byteArrayOf(ledState.bytes)
            else -> null
        }
    }

    private companion object {
        const val SCAN_DELAY_MS = 800L
        const val CONNECT_DELAY_MS = 600L
    }
}