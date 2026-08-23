package com.emenjivar.simplebleclient.ble

import com.emenjivar.simplebleclient.ble.commands.json.JSONChunk
import com.emenjivar.simplebleclient.ble.commands.json.ReadDataEmission
import com.emenjivar.simplebleclient.ble.commands.json.RequestDataEmission
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BleJsonManagerTest {

    @Test
    fun `resetOffset should write the usable chunk size to RequestDataEmission`() = runTest {
        val mtuSize = 120
        val bleManager = mock<CustomBleManager>()
        whenever(bleManager.getMTU()).thenReturn(mtuSize)
        val bleJsonManager = BleJsonManager(bleManager)

        bleJsonManager.resetOffset()

        val expectedChunkSize = bleJsonManager.getUsableBytesPerChunk()
        verify(bleManager)
            .write(
                command = RequestDataEmission,
                value = expectedChunkSize
            )
    }

    @Test
    fun `collectDataTransmission should reset the offset every time it's called`() = runTest {
        val mtuSize = 120
        val bleManager = mock<CustomBleManager>()
        whenever(bleManager.getMTU()).thenReturn(mtuSize)

        // We don't care about the parsing here (covered elsewhere), so an empty
        // response is fine even though it makes collectDataTransmission throw.
        whenever(bleManager.read(ReadDataEmission)).thenReturn(
            JSONChunk(
                currentOffset = 0,
                totalSize = 0,
                content = emptyList()
            )
        )
        val bleJsonManager = BleJsonManager(bleManager)

        // Call it twice, to prove the reset happens on every call, not just the first
        runCatching { bleJsonManager.collectDataTransmission<Any>() }
        runCatching { bleJsonManager.collectDataTransmission<Any>() }

        val expectedChunkSize = bleJsonManager.getUsableBytesPerChunk()
        verify(bleManager, times(2))
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
            @Serializable
            data class TestJSON(val message: String)

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

            val result = bleJsonManager.collectDataTransmission<TestJSON>()
            assertEquals(TestJSON(message = "hello"), result)
        }
}
