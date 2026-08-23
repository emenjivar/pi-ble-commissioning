package com.emenjivar.simplebleclient.ble

import com.emenjivar.simplebleclient.ble.commands.json.JSONChunk
import com.emenjivar.simplebleclient.ble.commands.json.ReadDataEmission
import com.emenjivar.simplebleclient.ble.commands.json.RequestDataEmission
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BleJsonManagerTest {

    @Test
    fun `collectDataTransmission should call RequestDataEmission for resetting the offset in the FW side`() =
        runTest {
            val mtuSize = 120
            val bleManager = mock<CustomBleManager>()
            val bleJsonManager = BleJsonManager(bleManager)

            whenever(bleManager.getMTU()).thenReturn(mtuSize)
            // We don't really care about the parsing, so mocking an ampty response is fine
            whenever(bleManager.read(ReadDataEmission)).thenReturn(
                JSONChunk(
                    currentOffset = 0,
                    totalSize = 0,
                    content = emptyList()
                )
            )

            bleJsonManager.collectDataTransmission()

            val expectedChunkSize = bleJsonManager.getUsableBytesPerChunk()
            verify(bleManager)
                .write(
                    command = RequestDataEmission,
                    value = expectedChunkSize
                )
        }

    @Test
    fun `getUsableBytesPerChunk should subtract ATT and chuck headers from MTU`() = runTest {
        val bleManager = mock<CustomBleManager>()
        whenever(bleManager.getMTU()).thenReturn(120)
        val bleJsonManager = BleJsonManager(bleManager)

        val result = bleJsonManager.getUsableBytesPerChunk()
        assertEquals(113, result) // 120 - 3 (ATT header) - 4 (chunk header)
    }

    @Test
    fun `collectDataTransmission should reassemble chunks into the original JSON string`() =
        runTest {
            val mtuSize = 8
            val json = "{ \"message\" : \"hello\" }"
            val byteArray = json.toByteArray(Charsets.UTF_8)

            // Split the JSON into 8-byte chunks
            val chunks = byteArray.toList().chunked(mtuSize).mapIndexed { index, bytes ->
                JSONChunk(
                    currentOffset = ((index + 1) * mtuSize).coerceAtMost(byteArray.size),
                    totalSize = byteArray.size,
                    content = bytes
                )
            }

            val chunkIterator = chunks.iterator()
            val bleManager = mock<CustomBleManager>()
            whenever(bleManager.getMTU()).thenReturn(mtuSize)
            whenever(bleManager.read(ReadDataEmission))
                .thenAnswer { chunkIterator.next() }

            val bleJsonManager = BleJsonManager(bleManager)

            val result = bleJsonManager.collectDataTransmission()
            assertEquals(json, result)
        }
}
