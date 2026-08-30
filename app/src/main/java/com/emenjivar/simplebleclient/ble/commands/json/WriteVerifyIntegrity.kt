package com.emenjivar.simplebleclient.ble.commands.json

import com.emenjivar.simplebleclient.ble.commands.BleCommand
import com.emenjivar.simplebleclient.ble.commands.jsonServiceUUID
import com.emenjivar.simplebleclient.ble.commands.verifyIntegrityUUID
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Send a CRC32 code to the GATT server
 */
object WriteVerifyIntegrity: BleCommand.Write<Long>(
    service = jsonServiceUUID,
    characteristic = verifyIntegrityUUID
) {
    override fun encode(value: Long): ByteArray {
        val bytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(value.toInt())
            .array()

        return bytes
    }
}
