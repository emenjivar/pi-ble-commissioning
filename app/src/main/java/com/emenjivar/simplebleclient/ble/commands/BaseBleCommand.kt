package com.emenjivar.simplebleclient.ble.commands

import java.util.UUID

val primaryServiceUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df20")
val jsonServiceUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df30")

val ledCharacteristicUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df21")
val getIPCharacteristicUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df22")
val getSSIDCharacteristicUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df23")

// JSON-related characteristics
val requestDataEmissionUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df31")
val readDataEmissionUUID: UUID = UUID.fromString("290edf15-b540-4e83-83cf-ba647bf4df32")
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
        abstract fun encode(value: T): ByteArray
    }

    abstract class Read<T>(
        override val service: UUID,
        override val characteristic: UUID
    ): BleCommand<T>() {
        val combinedHash = combineHash(service, characteristic)

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
}
