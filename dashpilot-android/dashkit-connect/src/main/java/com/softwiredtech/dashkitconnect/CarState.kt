package com.softwiredtech.dashkitconnect

/**
 * Decoded vehicle state, updated from the DashKit CAN stream. Units are
 * metric (km, km/h, °C); convert for display in the host app.
 */
data class CarState(
    val egoSteeringAngle: Float = 0f,
    val egoSpeed: Float = 0f,
    val leftBlinker: Float = 0f,
    val rightBlinker: Float = 0f,
    val gear: Float = 0f,
    val adasOn: Boolean = false,
    val leftBlindSpot: Float = 0f,
    val rightBlindSpot: Float = 0f,
    val fusedSpeedLimit: Float = 0f,
    val stopLineDist: Float = 0f,
    val trafficLightColor: Float = 0f,
    val laneDepartureWarning: Float = 0f,
    val sideCollisionWarning: Float = 0f,
    val anyDoorOpen: Float = 0f,
    val buckleStatus: Float = 0f,
    val accSetSpeed: Float = 0f,
    // Vehicle bus
    val fullPackEnergy: Float = 0f,
    val nominalEnergyRemaining: Float = 0f,
    val energyBuffer: Float = 0f,
    val maxRegenPower: Float = 0f,
    val maxDischargePower: Float = 0f,
    val packVoltage: Float = 0f,
    val packCurrent: Float = 0f,
    val packTMin: Float = 0f,
    val packTMax: Float = 0f,
    val odometer: Float = 0f,
    val acTemp: Float = 0f,
    // Openpilot
    val selfdriveActive: Boolean = false,
    val experimentalMode: Boolean = false,
    val madsActive: Boolean = false,

    val changingLane: Boolean = false,

    // Empty until fully assembled from the CAN stream by the native mapper.
    val vin: String = ""
) {
    companion object {
        /** Length of the flat double array produced by the native decoder
         * (CarState::toArray in bridge/car/car_state.h). */
        const val FIELD_COUNT = 34

        private const val VIN_OFFSET = 31
        private const val VIN_DOUBLE_COUNT = 3

        fun fromArray(values: DoubleArray): CarState {
            return CarState(
                egoSteeringAngle = values[0].toFloat(),
                egoSpeed = values[1].toFloat(),
                leftBlinker = values[2].toFloat(),
                rightBlinker = values[3].toFloat(),
                gear = values[4].toFloat(),
                adasOn = values[5] > 0,
                leftBlindSpot = values[6].toFloat(),
                rightBlindSpot = values[7].toFloat(),
                fusedSpeedLimit = values[8].toFloat(),
                stopLineDist = values[9].toFloat(),
                trafficLightColor = values[10].toFloat(),
                laneDepartureWarning = values[11].toFloat(),
                sideCollisionWarning = values[12].toFloat(),
                anyDoorOpen = values[13].toFloat(),
                buckleStatus = values[14].toFloat(),
                accSetSpeed = values[15].toFloat(),
                fullPackEnergy = values[16].toFloat(),
                nominalEnergyRemaining = values[17].toFloat(),
                energyBuffer = values[18].toFloat(),
                maxRegenPower = values[19].toFloat(),
                maxDischargePower = values[20].toFloat(),
                packVoltage = values[21].toFloat(),
                packCurrent = values[22].toFloat(),
                packTMin = values[23].toFloat(),
                packTMax = values[24].toFloat(),
                odometer = values[25].toFloat(),
                selfdriveActive = values[26] > 0,
                experimentalMode = values[27] > 0,
                madsActive = values[28] > 0,
                changingLane = values[29] > 0,
                acTemp = values[30].toFloat(),
                vin = decodeVin(values),
            )
        }

        // VIN chars arrive bit-cast into doubles, 8 ASCII bytes per double,
        // little-endian; see CarState::toArray in bridge/car/car_state.h.
        private fun decodeVin(values: DoubleArray): String {
            val bytes = ByteArray(VIN_DOUBLE_COUNT * 8)
            for (chunk in 0 until VIN_DOUBLE_COUNT) {
                val bits = values[VIN_OFFSET + chunk].toRawBits()
                for (b in 0 until 8) {
                    bytes[chunk * 8 + b] = (bits ushr (8 * b)).toByte()
                }
            }
            val length = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
            return String(bytes, 0, length, Charsets.US_ASCII)
        }
    }
}
