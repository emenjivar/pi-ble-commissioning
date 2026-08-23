package com.emenjivar.simplebleclient.ble

import com.emenjivar.simplebleclient.ble.commands.json.ReadDataEmission
import com.emenjivar.simplebleclient.ble.commands.json.RequestDataEmission
import kotlinx.serialization.json.Json

// TODO: use a better naming HERE
class BleJsonManager(
    private val bleManager: CustomBleManager
) {
    /**
     * Resets the offset of the chunk-emissions
     */
    internal suspend fun resetOffset() {
        bleManager.write(
            command = RequestDataEmission,
            value = getUsableBytesPerChunk()
        )
    }

    internal suspend inline fun <reified T> collectDataTransmission(): T {
        resetOffset()

        val response = bleManager.read(ReadDataEmission)
        var currentOffset: Int = response.currentOffset
        var totalSize: Int = response.totalSize
        val receivedBytes = mutableListOf<Byte>()
        receivedBytes.addAll(response.content)

        while (currentOffset < totalSize) {
            val newResponse = bleManager.read(ReadDataEmission)
            currentOffset = newResponse.currentOffset

            // This value should be the same for all the requests
            totalSize = newResponse.totalSize
            receivedBytes.addAll(newResponse.content)
        }

        val json = String(receivedBytes.toByteArray(), Charsets.UTF_8)
        val payload = Json.decodeFromString<T>(json)
        return payload
    }

    suspend fun getUsableBytesPerChunk(): Int = bleManager.getMTU() - ATT_HEADER_SIZE - CHUNK_HEADER_SIZE

    companion object {
        // Bytes used for ATT DPU header, prepend on every packet by the low level BLE protocol
        private const val ATT_HEADER_SIZE = 3

        // Bytes used for the data_emission notification
        // 2 bytes are for current offset of the data emission
        // 2 bytes are the total size of the content
        private const val CHUNK_HEADER_SIZE = 4
    }
}
