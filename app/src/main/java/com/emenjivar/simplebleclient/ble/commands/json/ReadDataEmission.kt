package com.emenjivar.simplebleclient.ble.commands.json

import com.emenjivar.simplebleclient.ble.commands.BleCommand
import com.emenjivar.simplebleclient.ble.commands.jsonServiceUUID
import com.emenjivar.simplebleclient.ble.commands.readDataEmissionUUID
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class JSONChunk(
    val currentOffset: Int,
    val totalSize: Int,
    val content: List<Byte>
)

/**
 * Read a chuck of data that followings the same structure:
 * - 2 bytes: Current offset expressed in little-endian
 * - 2 bytes: Total size in bytes of the content expressed in little-endian
 * - other bytes: The content
 *
 * `C8 00 08 07 C4 00 01 B8 12 E9 BF FF 01 12 00` can be interpreted as:
 * - current offset: `C8 00` = 200 bytes
 * - total size: `08 07` = 1800 bytes
 * - content: `C4 00 01 B8 12 E9 BF FF 01 12 00`
 */
object ReadDataEmission: BleCommand.Read<JSONChunk>(
    service = jsonServiceUUID,
    characteristic = readDataEmissionUUID
) {
    override fun decode(bytes: ByteArray): JSONChunk {
        require(bytes.size >= 4) { "Chunk too short: ${bytes.size} bytes "}
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return JSONChunk(
            currentOffset = buffer.getShort().toInt() and 0xFFFF,
            totalSize = buffer.getShort().toInt() and 0xFFFF,
            content = bytes.copyOfRange(fromIndex = 4, toIndex = bytes.size).toList()
        )
    }
}
