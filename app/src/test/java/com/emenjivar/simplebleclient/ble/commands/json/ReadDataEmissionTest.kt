package com.emenjivar.simplebleclient.ble.commands.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadDataEmissionTest {

    @Test
    fun `decode should parse a packet to extract currentOffset, totalSize and content`() {
        // Given the following headers
        val currentOffset =  200 // 0xC800 as big-endian, 0x00C8 as little-endian
        val totalSize = 1800 // 0x0708 as big-endian, 0x0807 as little-endian

        // Given the package C8 00 | 08 07 | 01 02 03 04
        val packet = byteArrayOf(
            0xC8.toByte(), 0x00, // currentOffset = 200 as little-endian
            0x08, 0x07, // totalSize = 1800 as little-endian
            // content
            0x01, 0x02, 0x03, 0x04
        )

        val chunk = ReadDataEmission.decode(packet)
        assertEquals(currentOffset, chunk.currentOffset)
        assertEquals(totalSize, chunk.totalSize)
        assertEquals(listOf<Byte>(0x01, 0x02, 0x03, 0x04), chunk.content)
    }

    @Test
    fun `decode should treat header values as unsigned even when the sign bit is set`() {
        // Given the following headers
        val currentOffset = 32_769 // 0x8001 as big-endian, 0x0180 as little-endian
        val totalSize = 49_153 // 0xC001 as big-endian, 0x01C0 as little-endian

        // Given the package 01 80 | 01 C0 | 00 00 00 00
        val packet = byteArrayOf(
            0x01, 0x80.toByte(), // currentOffset = 32,769 as little-endian
            0x01, 0xC0.toByte(), // totalSize = 49,153 as little-endian
            0x00, 0x00, 0x00, 0x00
        )

        val chunk = ReadDataEmission.decode(packet)
        assertEquals(currentOffset, chunk.currentOffset)
        assertEquals(totalSize, chunk.totalSize)
    }

    @Test
    fun `decode should parse correctly the max short value 0xFFFF`() {
        // Given the max short value 0xFFFF (65,535)
        val maxValue = 65535
        val packet = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), // currentOffset
            0xFF.toByte(), 0xFF.toByte(), // totalSize
        )

        val chunk = ReadDataEmission.decode(packet)
        assertEquals(maxValue, chunk.currentOffset)
        assertEquals(maxValue, chunk.totalSize)
    }

    @Test
    fun `decode should throw an exception when the packet is incomplete`() {
        // should be at least 4 bytes
        val packet = byteArrayOf(0x00, 0x00, 0x00)
        assertThrows(IllegalArgumentException::class.java) {
            ReadDataEmission.decode(packet)
        }
    }

    @Test
    fun `decode should parse packets with empty content`() {
        val packet = byteArrayOf(
            0x00, 0x00, // currentOffset
            0x00, 0x00, // totalSize
            // No content
        )

        val chunk = ReadDataEmission.decode(packet)
        assertEquals(0, chunk.currentOffset)
        assertEquals(0, chunk.totalSize)
        assertTrue(chunk.content.isEmpty())
    }
}
