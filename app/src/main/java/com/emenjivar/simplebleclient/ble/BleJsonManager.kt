package com.emenjivar.simplebleclient.ble

import com.emenjivar.simplebleclient.ble.commands.json.RequestDataEmission

// TODO: use a better naming HERE
class BleJsonManager(
    private val bleManager: CustomBleManager,
    private val bleNotifications: BleNotifications
) {
    // TODO: should happens one single time unless a disconnection happens
    suspend fun prepare() {
        val usableBytesPerChunk = bleManager.getMTU() - ATT_HEADER_SIZE - CHUNK_HEADER_SIZE
        bleManager.write(RequestDataEmission, usableBytesPerChunk)
    }

    suspend fun collectDataTransmission() {
        // bleNotifications.observe()
    }

    companion object {
        // Bytes used for ATT DPU header, prepend on every packet by the low level BLE protocol
        private const val ATT_HEADER_SIZE = 3

        // Bytes used for the data_emission notification
        // 2 bytes are for current offset of the data emission
        // 2 bytes are the total size of the content
        private const val CHUNK_HEADER_SIZE = 4
    }
}
