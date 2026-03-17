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
    val buckleStatus: Float = 0f
) {
    companion object {
        const val FIELD_COUNT = 15
    }
}