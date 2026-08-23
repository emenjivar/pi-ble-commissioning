package com.emenjivar.simplebleclient.ble.commands.json

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WriteVerifyIntegrityTest {

    @Test
    fun `encode should parse a value that uses 2 significant bytes and returns a 4-bytes array`() {
        // Given a value that uses 2 significant bytes
        val crc = 65535L // hex: 0x0000FFFF

        // When encode returns a 4-bytes array
        val byteArray = WriteVerifyIntegrity.encode(crc)

        // Then the output is expressed as little-endian: FF FF 00 00
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x0, 0x0), byteArray)
    }

    @Test
    fun `encode should parse a value that uses 4 significant bytes and return a 4-bytes array`() {
        // Given a value that uses 4 significant bytes
        val crc = 4294967295L // hex: 0xFFFFFFFF
        val byteArray = WriteVerifyIntegrity.encode(crc)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            byteArray
        )
    }

    @Test
    fun `encode should parse a value into a 4-bytes array expressed as little-endian`() {
        val crc: Long = 0xF // decimal: 15

        // When encode returns a 4-bytes array
        val byteArray = WriteVerifyIntegrity.encode(crc)

        // Then the output is expressed as little-endian
        assertArrayEquals(
            byteArrayOf(0xF, 0x0, 0x0, 0x0),
            byteArray
        )
    }

    @Test
    fun `encode should ignore the 5th significant byte and above`() {
        val crc = 0xAABBCCDDEEFF // AA BB CC DD EE FF = 6 bytes

        val byteArray = WriteVerifyIntegrity.encode(crc)

        // 0xAABBCCDDEEFF is truncated to 0xCCDDEEFF
        // we expect that value as little-endian: 0xFFEEDDCC
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xEE.toByte(), 0xDD.toByte(), 0xCC.toByte()),
            byteArray
        )
    }
}