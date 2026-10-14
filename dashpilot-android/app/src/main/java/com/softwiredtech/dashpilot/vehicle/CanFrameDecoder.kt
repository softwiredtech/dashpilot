package com.softwiredtech.dashpilot.vehicle

import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.jni.VehicleBridge

// This is a wrapper for the C++ VehicleDecoder so that DataSources that are
// non-native (such as PandaBleDataSource) can still decode can frames.
class CanFrameDecoder(
    private val bridge: VehicleBridge,
    profile: VehicleProfile
) {
    var decoderHandle: Long = bridge.nativeCreateVehicleDecoder(
        profile.dbcContents, profile.busIndices, profile.type
    )

    @Synchronized fun decodeFrame(bus: Int, address: Int, data: ByteArray): CarState? {
        if (decoderHandle == 0L) return null
        val values = bridge.nativeDecodeCanFrame(decoderHandle, bus, address, data)
        return arrayToCarState(values)
    }

    @Synchronized fun destroy() {
        bridge.nativeDestroyVehicleDecoder(decoderHandle)
        decoderHandle = 0
    }

    companion object {
        fun arrayToCarState(values: DoubleArray): CarState {
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
                anyDoorOpen = values[11].toFloat(),
                buckleStatus = values[12].toFloat(),
                accSetSpeed = values[13].toFloat(),
                // Vehicle bus
                fullPackEnergy = values[14].toFloat(),
                nominalEnergyRemaining = values[15].toFloat(),
                energyBuffer = values[16].toFloat(),
                maxRegenPower = values[17].toFloat(),
                maxDischargePower = values[18].toFloat(),
                packVoltage = values[19].toFloat(),
                packCurrent = values[20].toFloat(),
                packTMin = values[21].toFloat(),
                packTMax = values[22].toFloat(),
                odometer = values[23].toFloat(),
                //Openpilot
                selfdriveActive = values[24] > 0,
                experimentalMode = values[25] > 0,
                madsActive = values[26] > 0,
                changingLane = values[27] > 0,
                acTemp = values[28].toFloat(),
                acTempRight = values[29].toFloat(),
                hvacFanLevel = values[30].toFloat(),
                hvacPowerState = values[31].toFloat(),
                hvacAcMode = values[32].toFloat(),
                hvacRecirc = values[33].toFloat(),
                hvacKeepClimateOn = values[34].toFloat(),
                powerLevel = values[35].toFloat(),
                vin = decodeVin(values, offset = 36)
            )
        }

        // VIN chars arrive bit-cast into doubles, 8 ASCII bytes per double,
        // little-endian; see CarState::toArray in bridge/car/car_state.h.
        private fun decodeVin(values: DoubleArray, offset: Int): String {
            val bytes = ByteArray(VIN_DOUBLE_COUNT * 8)
            for (chunk in 0 until VIN_DOUBLE_COUNT) {
                val bits = values[offset + chunk].toRawBits()
                for (b in 0 until 8) {
                    bytes[chunk * 8 + b] = (bits ushr (8 * b)).toByte()
                }
            }
            val length = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
            return String(bytes, 0, length, Charsets.US_ASCII)
        }

        private const val VIN_DOUBLE_COUNT = 3
    }
}
