package com.softwiredtech.dashpilot.js

import android.webkit.JavascriptInterface
import com.softwiredtech.dashpilot.datamodel.CarState

class CarStateBridge {

    @Volatile private var egoSteeringAngle: Float = 0f
    @Volatile private var egoSpeed: Float = 0f
    @Volatile private var leftBlinker: Float = 0f
    @Volatile private var rightBlinker: Float = 0f
    @Volatile private var gear: Float = 0f
    @Volatile private var adasOn: Boolean = false
    @Volatile private var leftBlindSpot: Float = 0f
    @Volatile private var rightBlindSpot: Float = 0f
    @Volatile private var fusedSpeedLimit: Float = 0f
    @Volatile private var stopLineDist: Float = 0f
    @Volatile private var trafficLightColor: Float = 0f
    @Volatile private var buckleStatus: Float = 0f
    @Volatile private var anyDoorOpen: Float = 0f
    @Volatile private var laneDepartureWarning: Float = 0f

    fun update(state: CarState) {
        egoSteeringAngle = state.egoSteeringAngle
        egoSpeed = state.egoSpeed
        leftBlinker = state.leftBlinker
        rightBlinker = state.rightBlinker
        gear = state.gear
        adasOn = state.adasOn
        leftBlindSpot = state.leftBlindSpot
        rightBlindSpot = state.rightBlindSpot
        fusedSpeedLimit = state.fusedSpeedLimit
        stopLineDist = state.stopLineDist
        trafficLightColor = state.trafficLightColor
        buckleStatus = state.buckleStatus
        anyDoorOpen = state.anyDoorOpen
        laneDepartureWarning = state.laneDepartureWarning
    }

    @JavascriptInterface fun getEgoSteeringAngle(): Float = egoSteeringAngle
    @JavascriptInterface fun getEgoSpeed(): Float = egoSpeed
    @JavascriptInterface fun getLeftBlinker(): Float = leftBlinker
    @JavascriptInterface fun getRightBlinker(): Float = rightBlinker
    @JavascriptInterface fun getGear(): Float = gear
    @JavascriptInterface fun isAdasOn(): Boolean = adasOn
    @JavascriptInterface fun getLeftBlindSpot(): Float = leftBlindSpot
    @JavascriptInterface fun getRightBlindSpot(): Float = rightBlindSpot
    @JavascriptInterface fun getFusedSpeedLimit(): Float = fusedSpeedLimit
    @JavascriptInterface fun getStopLineDist(): Float = stopLineDist
    @JavascriptInterface fun getTrafficLightColor(): Float = trafficLightColor
    @JavascriptInterface fun getBuckleStatus(): Float = buckleStatus
    @JavascriptInterface fun getAnyDoorOpen(): Float = anyDoorOpen
    @JavascriptInterface fun getLaneDepartureWarning(): Float = laneDepartureWarning
}
