package com.softwiredtech.dashpilot.vehicle

import com.softwiredtech.dashpilot.datamodel.CarState
import com.softwiredtech.dashpilot.jni.VehicleBridge

// This is a wrapper for the C++ VehicleDecoder so that DataSources that are
// non-native (such as PandaBleDataSource) can still decode can frames.
class CanFrameDecoder(
    private val bridge: VehicleBridge,
    profile: VehicleProfile
) {
    val decoderHandle: Long = bridge.nativeCreateVehicleDecoder(
        profile.dbcContents, profile.busIndices, profile.type
    )

    fun decodeFrame(bus: Int, address: Int, data: ByteArray): CarState {
        val values = bridge.nativeDecodeCanFrame(decoderHandle, bus, address, data)
        return arrayToCarState(values)
    }

    fun destroy() {
        bridge.nativeDestroyVehicleDecoder(decoderHandle)
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
                laneDepartureWarning = values[11].toFloat(),
                sideCollisionWarning = values[12].toFloat(),
                anyDoorOpen = values[13].toFloat(),
                buckleStatus = values[14].toFloat(),
                accSetSpeed = values[15].toFloat(),
                // Vehicle bus
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
                //Openpilot
                selfdriveActive = values[26] > 0,
                experimentalMode = values[27] > 0,
                madsActive = values[28] > 0,
                changingLane = values[29] > 0
            )
        }
    }
}
