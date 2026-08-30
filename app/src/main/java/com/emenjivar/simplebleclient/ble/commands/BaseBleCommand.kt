package com.emenjivar.simplebleclient.ble.commands

import com.emenjivar.simplebleclient.ble.commands.json.JSONChunk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

val primaryServiceUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df20")
val jsonServiceUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df30")

val ledCharacteristicUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df21")
val getIPCharacteristicUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df22")
val getSSIDCharacteristicUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df23")

// JSON-related characteristics
val dataEmissionUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df32")
val verifyIntegrityUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df33")

// Used for listening notification changes
val clientCharacteristicConfigUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

sealed class BleCommand<T> {
    abstract val service: UUID
    abstract val characteristic: UUID

    abstract class Write<T>(
        override val service: UUID,
        override val characteristic: UUID,
    ): BleCommand<T>() {
        /**
         * Converts [value] into the raw bytes written to [characteristic].
         *
         * @param value the value to be sent, in whatever unit/shape the caller uses.
         * @return the exact bytes written to the characteristic.
         */
        abstract fun encode(value: T): ByteArray
    }

    abstract class Read<T>(
        override val service: UUID,
        override val characteristic: UUID
    ): BleCommand<T>() {
        val combinedHash = combineHash(service, characteristic)

        /**
         * Converts the raw bytes read from [characteristic] back into [T].
         *
         * @param bytes the raw bytes read from the characteristic.
         * @return the decoded value.
         */
        abstract fun decode(bytes: ByteArray): T

        // Runs when each object in initialized, self-registering into a shared map.
        init {
            register(this)
        }

        companion object {
            // Shared across all read objects.
            // allowing lookups by characteristic
            private val _registry = mutableMapOf<Int, Read<*>>()

            /**
             * The same characteristic UUID can appear in multiple services.
             * Combining both service + characteristic prevent collisions on [getCommand]
             */
            private fun combineHash(
                service: UUID,
                characteristic: UUID
            ) = (service.toString() + characteristic.toString()).hashCode()

            internal fun register(command: Read<*>) {
                val hash = command.combinedHash
                _registry[hash] = command
            }

            /**
             * Search a [BleCommand.Read] command by its [service] and [characteristic].
             * Returns null if no command is registered for the given combination
             */
            fun getCommand(service: UUID, characteristic: UUID): Read<*>? {
                val hash = combineHash(service, characteristic)
                return _registry[hash]
            }
        }
    }

    /**
     * Similar to [Read] but used for parsing JSON data.
     *
     * Read a chunk of data that follows the structure:
     * - 2 bytes: Current offset expressed in little-endian
     * - 2 bytes: Total size in bytes of the content expressed in little-endian
     * - other bytes: The content
     *
     * `C8 00 08 07 C4 00 01 B8 12 E9 BF FF 01 12 00` can be interpreted as:
     * - current offset: `C8 00` = 200 bytes
     * - total size: `08 07` = 1800 bytes
     * - content: `C4 00 01 B8 12 E9 BF FF 01 12 00`
     */
    abstract class ReadJSON(
        override val service: UUID,
        override val characteristic: UUID
    ): BleCommand<JSONChunk>() {
        fun decode(bytes: ByteArray): JSONChunk {
            require(bytes.size >= 4) { "Chunk too short: ${bytes.size} bytes "}
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return JSONChunk(
                currentOffset = buffer.getShort().toInt() and 0xFFFF,
                totalSize = buffer.getShort().toInt() and 0xFFFF,
                content = bytes.copyOfRange(fromIndex = 4, toIndex = bytes.size).toList()
            )
        }
    }
}
