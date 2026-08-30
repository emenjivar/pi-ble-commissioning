package com.emenjivar.simplebleclient.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.emenjivar.simplebleclient.ble.commands.BleCommand
import com.emenjivar.simplebleclient.ble.commands.clientCharacteristicConfigUUID
import com.emenjivar.simplebleclient.ble.commands.json.JSONChunk
import com.emenjivar.simplebleclient.ble.commands.ledCharacteristicUUID
import com.emenjivar.simplebleclient.ble.commands.primaryServiceUUID
import com.emenjivar.simplebleclient.ble.exceptions.CharacteristicNotFoundException
import com.emenjivar.simplebleclient.ble.model.BleConnectionState
import com.emenjivar.simplebleclient.ble.model.BluetoothDeviceModel
import com.emenjivar.simplebleclient.ble.model.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Real BLE implementation backed by Android's GATT stack.
 * Selected when the `raspberry` flavor is active.
 */
@SuppressLint("MissingPermission")
class RaspberryBleClient(
    private val context: Context,
    private val bleNotifications: BleNotifications,
    private val bleOperationQueue: BleOperationQueue,
    private val scanner: BleScanner
) : BleClient, BleScanner by scanner {
    private var bluetoothGatt: BluetoothGatt? = null
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val connectedDevice = gatt?.device
                    if (connectedDevice != null) {
                        _connectionState.update { BleConnectionState.Connected(connectedDevice.toModel()) }
                    } else {
                        _connectionState.update { BleConnectionState.Failed }
                    }

                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt?.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    bleOperationQueue.clear()
                    bluetoothGatt?.close()
                    _connectionState.update { BleConnectionState.Disconnected }
                    bluetoothGatt = null
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.update { BleConnectionState.Connecting }
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    _connectionState.update { BleConnectionState.Disconnecting }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // TODO: you could iterate the notification characteristics here
                //  using [BleCommand.Read.registry], but filtering by `notification`
                val characteristic = gatt
                    ?.getService(primaryServiceUUID)
                    ?.getCharacteristic(ledCharacteristicUUID) ?: return

                // Enable notifications locally on android
                gatt.setCharacteristicNotification(characteristic, true)

                // Hardcoded UUID for receiving notifications
                val descriptor = characteristic.getDescriptor(clientCharacteristicConfigUUID)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bleNotifications.emit(
                    service = characteristic.service.uuid,
                    characteristic = characteristic.uuid,
                    value = value
                )
            }

            bleOperationQueue.operationComplete()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic?.value

                if (value != null) {
                    bleNotifications.emit(
                        service = characteristic.service.uuid,
                        characteristic = characteristic.uuid,
                        value = value
                    )
                } else {
                    // Handle error here
                }
            }

            bleOperationQueue.operationComplete()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            bleNotifications.emit(
                service = characteristic.service.uuid,
                characteristic = characteristic.uuid,
                value = value
            )
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            _connectionState.update { state ->
                if (state is BleConnectionState.Connected) {
                    state.copy(ready = true)
                } else {
                    state
                }
            }
            bleOperationQueue.operationComplete()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            bleOperationQueue.operationComplete()
        }
    }

    override fun connect(model: BluetoothDeviceModel) {
        val device = scanner.getRemoteDevice(model.macAddress)
        _connectionState.update { BleConnectionState.Connecting }
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt =
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    override fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    override fun <T> observe(command: BleCommand.Read<T>): Flow<T> =
        bleNotifications.observe(command)

    override fun <T> readCharacteristic(command: BleCommand.Read<T>) {
        val characteristic = command.getCharacteristic() ?: throw CharacteristicNotFoundException()
        bleOperationQueue.enqueue { bluetoothGatt?.readCharacteristic(characteristic) }
    }

    override fun <T> writeCharacteristic(command: BleCommand.Write<T>, value: T) {
        val characteristic = command.getCharacteristic() ?: throw CharacteristicNotFoundException()
        val encodedValue = command.encode(value)

        bleOperationQueue.enqueue {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(
                    characteristic,
                    encodedValue,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                characteristic.value = encodedValue
                bluetoothGatt?.writeCharacteristic(characteristic)
            }
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

    override suspend fun read(command: BleCommand.ReadJSON): JSONChunk {
        TODO("Not yet implemented")
    }

    private fun <T> BleCommand<T>.getCharacteristic(): BluetoothGattCharacteristic? {
        val characteristic = bluetoothGatt
            ?.getService(service)
            ?.getCharacteristic(characteristic)

        return characteristic
    }
}
