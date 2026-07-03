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

    // Virtual lane geometry (DAS_lanes) forwarded to adasviz
    val virtualLaneWidth: Float = 0f,      // m
    val virtualLaneViewRange: Float = 0f,  // m
    val virtualLaneC0: Float = 0f,         // cm
    val virtualLaneC1: Float = 0f,         // deg
    val virtualLaneC2: Float = 0f,         // m-1
    val virtualLaneC3: Float = 0f          // m-2
) {
    fun toImperial(): CarState = copy(
        odometer = odometer * KM_TO_MILES,
        packTMin = if (packTMin != 0f) packTMin * 1.8f + 32f else 0f,
        packTMax = if (packTMax != 0f) packTMax * 1.8f + 32f else 0f,
        acTemp = if (acTemp != 0f) acTemp * 1.8f + 32f else 0f,
        fusedSpeedLimit = if (speedLimitSignsInKm()) fusedSpeedLimit * KM_TO_MILES else fusedSpeedLimit,
    )

    companion object {
        const val FIELD_COUNT = 37
        private const val KM_TO_MILES = 0.621371f

        private val MILES_COUNTRIES = setOf("US", "GB", "MM", "LR")

        fun speedLimitSignsInKm(): Boolean =
            Locale.getDefault().country !in MILES_COUNTRIES
    }
}