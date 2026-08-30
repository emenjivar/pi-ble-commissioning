package com.emenjivar.simplebleclient.ble.commands.json

import com.emenjivar.simplebleclient.ble.commands.BleCommand
import com.emenjivar.simplebleclient.ble.commands.jsonServiceUUID
import com.emenjivar.simplebleclient.ble.commands.dataEmissionUUID
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class JSONChunk(
    val currentOffset: Int,
    val totalSize: Int,
    val content: List<Byte>
)

object ReadDataEmission: BleCommand.ReadJSON(
    service = jsonServiceUUID,
    characteristic = dataEmissionUUID
)

object ResetDataEmission: BleCommand.Write<Int>(
    service = jsonServiceUUID,
    characteristic = dataEmissionUUID
) {
    /**
     * @param value Size in bytes of the chunks to be emitted.
     *  the value expect some math before being sent.
     *  chuck = MTU - ATTHeaderSize - chunkHeaderSize
     *  where ATTHeaderSize is 3 bytes and chunkHeaderSize is 4 bytes.
     *  That math is not included here, but we expect [value] to be treated using that formula
     *  before passing it as a parameter here.
     */
    override fun encode(value: Int): ByteArray {
        return ByteBuffer.allocate(2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(value.toShort())
            .array()
    }
}
