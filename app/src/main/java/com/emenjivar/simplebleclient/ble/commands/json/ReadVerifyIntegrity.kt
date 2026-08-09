package com.emenjivar.simplebleclient.ble.commands.json

import com.emenjivar.simplebleclient.ble.commands.BleCommand
import com.emenjivar.simplebleclient.ble.commands.jsonServiceUUID
import com.emenjivar.simplebleclient.ble.commands.verifyIntegrityUUID

enum class CrcVerificationResult(val bytes: Byte) {
    CORRUPTED(0x00),
    SUCCESS(0x01)
}

/**
 * NOTE: this works, but why don't just use [ReadDataEmission] instead,
 *  So I can use an arbitrary JSON instead of a fixed byte
 */
object ReadVerifyIntegrity: BleCommand.Read<CrcVerificationResult>(
    service = jsonServiceUUID,
    characteristic = verifyIntegrityUUID
) {
    override fun decode(bytes: ByteArray): CrcVerificationResult {
        val decodedValue = CrcVerificationResult.entries.find { it.bytes == bytes.firstOrNull() }
        if (decodedValue == null) {
            throw IllegalArgumentException("Failed to decode ${bytes.contentToString()}")
        }

        return decodedValue
    }

}

