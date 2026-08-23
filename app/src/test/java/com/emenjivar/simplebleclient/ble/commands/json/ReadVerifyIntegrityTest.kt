package com.emenjivar.simplebleclient.ble.commands.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadVerifyIntegrityTest {

    @Test
    fun `decode should parse a success value`() {
        val packet = byteArrayOf(0x01)
        assertEquals(
            CrcVerificationResult.SUCCESS,
            ReadVerifyIntegrity.decode(packet)
        )
    }

    @Test
    fun `decode should parse a corrupted value`() {
        val packet = byteArrayOf(0x00)
        assertEquals(
            CrcVerificationResult.CORRUPTED,
            ReadVerifyIntegrity.decode(packet)
        )
    }

    @Test
    fun `decode should throw an exception when pass an invalid packet`() {
        val packet = byteArrayOf(0xFF.toByte())
        assertThrows(IllegalArgumentException::class.java) {
            ReadDataEmission.decode(packet)
        }
    }

    @Test
    fun `decode should throw an exception when pass an empty packet`() {
        val packet = byteArrayOf()
        assertThrows(IllegalArgumentException::class.java) {
            ReadDataEmission.decode(packet)
        }
    }
}
