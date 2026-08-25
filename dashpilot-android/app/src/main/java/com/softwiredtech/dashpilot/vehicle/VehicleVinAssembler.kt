package com.softwiredtech.dashpilot.vehicle

sealed interface VehicleVinState {
    data object Waiting : VehicleVinState
    data class Available(val vin: String) : VehicleVinState
    data object Invalid : VehicleVinState
}

class VehicleVinAssembler {
    companion object {
        const val VIN_BUS = 1
        const val VIN_CAN_ID = 0x405
        const val VIN_FRAME_LEN = 8
        const val VIN_LEN = 17
        const val MUX_A = 0x10
        const val MUX_B = 0x11
        const val MUX_C = 0x12
    }

    private val segments = arrayOfNulls<String>(3)
    private var completed: String? = null

    fun onFrame(bus: Int, address: Int, data: ByteArray): VehicleVinState = synchronized(this) {
        completed?.let { return@synchronized VehicleVinState.Available(it) }
        if (bus != VIN_BUS || address != VIN_CAN_ID || data.size != VIN_FRAME_LEN) {
            return@synchronized VehicleVinState.Waiting
        }

        val (index, offset) = when (data[0].toInt() and 0xFF) {
            MUX_A -> 0 to 5
            MUX_B -> 1 to 1
            MUX_C -> 2 to 1
            else -> return@synchronized VehicleVinState.Waiting
        }
        if (index == 0 && (1..4).any { data[it] != 0.toByte() }) {
            return@synchronized VehicleVinState.Invalid
        }
        val value = String(data, offset, VIN_FRAME_LEN - offset, Charsets.US_ASCII)
        if (!value.all(::isVinChar)) {
            return@synchronized VehicleVinState.Invalid
        }

        segments[index] = value
        if (segments.all { it != null }) {
            completed = segments.joinToString(separator = "") { it!! }
        }
        completed?.let { VehicleVinState.Available(it) } ?: VehicleVinState.Waiting
    }

    fun reset() = synchronized(this) {
        segments.fill(null)
        completed = null
    }
}

internal fun isVinChar(c: Char): Boolean =
    c in '0'..'9' || (c in 'A'..'Z' && c !in "IOQ")
