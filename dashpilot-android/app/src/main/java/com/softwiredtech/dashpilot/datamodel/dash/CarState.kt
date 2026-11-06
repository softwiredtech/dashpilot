package com.softwiredtech.dashpilot.datamodel.dash

import java.util.Locale

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
    val acTempRight: Float = 0f,
    val hvacFanLevel: Float = 0f,
    val hvacPowerState: Float = 0f,
    val hvacAcMode: Float = 0f,
    val hvacRecirc: Float = 0f,
    val hvacKeepClimateOn: Float = 0f,
    val powerLevel: Float = 0f,
    // Openpilot
    val selfdriveActive: Boolean = false,
    val experimentalMode: Boolean = false,
    val madsActive: Boolean = false,

    val changingLane: Boolean = false,

    // TPMS: pressure (bar, -1 = none), temperature (degC, -100 = none) and sensor battery (V, -1 = none) per wheel.
    val tpmsPressureFL: Float = -1f,
    val tpmsPressureFR: Float = -1f,
    val tpmsPressureRL: Float = -1f,
    val tpmsPressureRR: Float = -1f,
    val tpmsTempFL: Float = -100f,
    val tpmsTempFR: Float = -100f,
    val tpmsTempRL: Float = -100f,
    val tpmsTempRR: Float = -100f,
    val tpmsBattFL: Float = -1f,
    val tpmsBattFR: Float = -1f,
    val tpmsBattRL: Float = -1f,
    val tpmsBattRR: Float = -1f,

    // Closures: 1 while the door/trunk latch is not closed, else 0. Trunk angle
    // in degrees from the powered liftgate (0 when closed or not powered).
    val doorOpenFL: Float = 0f,
    val doorOpenFR: Float = 0f,
    val doorOpenRL: Float = 0f,
    val doorOpenRR: Float = 0f,
    val trunkOpen: Float = 0f,
    val trunkPosition: Float = 0f,
    val frunkOpen: Float = 0f,

    // Empty until fully assembled from the CAN stream by the native mapper.
    val vin: String = "",
    // Candidate Tesla ambient lighting; unknown until a complete frame arrives.
    val ambientLightingAvailable: Boolean = false,
    val ambientLightingHex: String = "",
    val ambientLightingRed: Float = 0f,
    val ambientLightingGreen: Float = 0f,
    val ambientLightingBlue: Float = 0f,
    val ambientLightingBrightness: Float = 0f,
    val ambientLightingEnableEffectRaw: Float = 0f,
    val ambientLightingSectionMaskRaw: Float = 0f,
) {
    fun toImperial(): CarState = copy(
        odometer = odometer * KM_TO_MILES,
        packTMin = if (packTMin != 0f) packTMin * 1.8f + 32f else 0f,
        packTMax = if (packTMax != 0f) packTMax * 1.8f + 32f else 0f,
        acTemp = if (acTemp != 0f) acTemp * 1.8f + 32f else 0f,
        acTempRight = if (acTempRight != 0f) acTempRight * 1.8f + 32f else 0f,
        fusedSpeedLimit = if (speedLimitSignsInKm()) fusedSpeedLimit * KM_TO_MILES else fusedSpeedLimit,
    )

    companion object {
        const val FIELD_COUNT = 65
        private const val KM_TO_MILES = 0.621371f

        private val MILES_COUNTRIES = setOf("US", "GB", "MM", "LR")

        fun speedLimitSignsInKm(): Boolean =
            Locale.getDefault().country !in MILES_COUNTRIES
    }
}
