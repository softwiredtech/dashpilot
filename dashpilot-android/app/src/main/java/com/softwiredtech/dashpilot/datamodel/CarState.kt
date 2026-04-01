package com.softwiredtech.dashpilot.datamodel

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
    val odometer: Float = 0f
) {
    companion object {
        const val FIELD_COUNT = 26
    }
}