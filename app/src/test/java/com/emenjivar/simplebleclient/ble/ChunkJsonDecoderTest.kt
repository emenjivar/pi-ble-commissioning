package com.emenjivar.simplebleclient.ble

import com.emenjivar.simplebleclient.ble.commands.json.JSONChunk
import com.emenjivar.simplebleclient.ble.commands.json.ReadDataEmission
import com.emenjivar.simplebleclient.ble.commands.json.RequestDataEmission
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChunkJsonDecoderTest {

    @Test
    fun `resetOffset should write the usable chunk size to RequestDataEmission`() = runTest {
        val mtuSize = 120
        val bleManager = mock<BleClient>()
        whenever(bleManager.getMTU()).thenReturn(mtuSize)
        val chunkJsonDecoder = ChunkJsonDecoder(bleManager)

        chunkJsonDecoder.resetOffset()

        val expectedChunkSize = chunkJsonDecoder.getUsableBytesPerChunk()
        verify(bleManager)
            .write(
                command = RequestDataEmission,
                value = expectedChunkSize
            )
    }

    @Test
    fun `collectDataTransmission should reset the offset every time it's called`() = runTest {
        val mtuSize = 120
        val bleManager = mock<BleClient>()
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
        val chunkJsonDecoder = ChunkJsonDecoder(bleManager)

        // Call it twice, to prove the reset happens on every call, not just the first
        runCatching { chunkJsonDecoder.collectDataTransmission<Any>() }
        runCatching { chunkJsonDecoder.collectDataTransmission<Any>() }

        val expectedChunkSize = chunkJsonDecoder.getUsableBytesPerChunk()
        verify(bleManager, times(2))
            .write(
                command = RequestDataEmission,
                value = expectedChunkSize
            )
    }

    @Test
    fun `getUsableBytesPerChunk should subtract ATT and chuck headers from MTU`() = runTest {
        val bleManager = mock<BleClient>()
        whenever(bleManager.getMTU()).thenReturn(120)
        val chunkJsonDecoder = ChunkJsonDecoder(bleManager)

        val result = chunkJsonDecoder.getUsableBytesPerChunk()
        assertEquals(113, result) // 120 - 3 (ATT header) - 4 (chunk header)
    }

    @Test
    fun `collectDataTransmission should reassemble chunks into the original JSON string`() =
        runTest {
            @Serializable
            data class TestJSON(val message: String)

            val mtuSize = 8
            val json = "{ \"message\" : \"hello\" }"
            val chunkIterator = chunkString(value = json, mtuSize = mtuSize)
            val bleManager = mock<BleClient>()
            whenever(bleManager.getMTU()).thenReturn(mtuSize)
            whenever(bleManager.read(ReadDataEmission))
                .thenAnswer { chunkIterator.next() }

            val chunkJsonDecoder = ChunkJsonDecoder(bleManager)

            val result = chunkJsonDecoder.collectDataTransmission<TestJSON>()
            assertEquals(TestJSON(message = "hello"), result)
        }

    // Cannot declare it inside the test function
    enum class ConnectionState {
        CONNECTED, ERROR
    }

    @Test
    fun `collectDataTransmission should parse long and nested JSONs`() = runTest {
        // Given some complex/nested data classes
        @Serializable
        data class SensorReading(
            val name: String,
            val value: Double,
            val unit: String,
            val timestamp: Long
        )

        @Serializable
        data class NetworkInfo(
            val ssid: String,
            val rssi: Int,
            val ipAddress: String,
            val state: ConnectionState,
            val errorMessage: String?
        )

        @Serializable
        data class StatusSnapshot(
            val deviceId: String,
            val firmwareVersion: String,
            val batteryLevel: Int,
            val isCharging: Boolean,
            val uptimeSeconds: Long,
            val sensors: List<SensorReading>,
            val network: NetworkInfo,
            val tags: List<String>
        )

        val longJSON = """
            {
                "deviceId": "pi001",
                "firmwareVersion": "1.0.0",
                "batteryLevel": 87,
                "isCharging": false,
                "uptimeSeconds": 18430,
                "sensors": [
                        { "name": "temperature", "value": 22.5, "unit": "celsius", "timestamp": 1735489200 },
                        { "name": "humidity", "value": 48.2, "unit": "percent", "timestamp": 1735489200 },
                        { "name": "pressure", "value": 1013.25, "unit": "hpa", "timestamp": 1735489201 }
                ],
                "network" : {
                    "ssid": "Network5G",
                    "rssi": -58,
                    "ipAddress": "192.168.1.2",
                    "state": "CONNECTED",
                    "errorMessage": null
                },
                "tags": [ "school", "zone1", "outdoor" ]
            }
        """.trimIndent()

        val mtuSize = 16
        val iterator = chunkString(value = longJSON, mtuSize = mtuSize)
        val bleManager = mock<BleClient>()
        whenever(bleManager.getMTU()).thenReturn(mtuSize)
        whenever(bleManager.read(ReadDataEmission))
            .thenAnswer { iterator.next() }

        val chunkJsonDecoder = ChunkJsonDecoder(bleManager = bleManager)

        val result = chunkJsonDecoder.collectDataTransmission<StatusSnapshot>()
        val expectedStatusSnapshot = StatusSnapshot(
            deviceId = "pi001",
            firmwareVersion = "1.0.0",
            batteryLevel = 87,
            isCharging = false,
            uptimeSeconds = 18430,
            sensors = listOf(
                SensorReading(
                    name = "temperature",
                    value = 22.5,
                    unit = "celsius",
                    timestamp = 1735489200
                ),
                SensorReading(
                    name = "humidity",
                    value = 48.2,
                    unit = "percent",
                    timestamp = 1735489200
                ),
                SensorReading(
                    name = "pressure",
                    value = 1013.25,
                    unit = "hpa",
                    timestamp = 1735489201
                )
            ),
            network = NetworkInfo(
                ssid = "Network5G",
                rssi = -58,
                ipAddress = "192.168.1.2",
                state = ConnectionState.CONNECTED,
                errorMessage = null
            ),
            tags = listOf("school", "zone1", "outdoor")
        )
        assertEquals(expectedStatusSnapshot, result)
    }

    @Test
    fun `collectDataTransmission should throw an exception when parsing an invalid JSON`() =
        runTest {
            @Serializable
            data class TestJSON(val message: String)

            val mtuSize = 8

            // Missing the closing brace on purpose
            val invalidJson = "{ \"message\" : \"hello\" "

            val chunkIterator = chunkString(value = invalidJson, mtuSize = mtuSize)
            val bleManager = mock<BleClient>()
            whenever(bleManager.getMTU()).thenReturn(mtuSize)
            whenever(bleManager.read(ReadDataEmission))
                .thenAnswer { chunkIterator.next() }

            val chunkJsonDecoder = ChunkJsonDecoder(bleManager)

            val result = runCatching { chunkJsonDecoder.collectDataTransmission<TestJSON>() }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SerializationException)
        }

    private fun chunkString(value: String, mtuSize: Int): Iterator<JSONChunk> {
        val byteArray = value.toByteArray(Charsets.UTF_8)
        val chunkSize = byteArray.size / mtuSize
        val chunks = byteArray.toList().chunked(chunkSize)
            .mapIndexed { index, bytes ->
                JSONChunk(
                    currentOffset = ((index + 1) * chunkSize).coerceAtMost(byteArray.size),
                    totalSize = byteArray.size,
                    content = bytes
                )
            }
        return chunks.iterator()
    }
}
