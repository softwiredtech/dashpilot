package com.softwiredtech.dashpilot.js

import android.webkit.JavascriptInterface
import com.softwiredtech.dashpilot.datamodel.CarState
import com.softwiredtech.dashpilot.datamodel.DisplaySettings

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
    @Volatile private var accSetSpeed: Float = 0f
    @Volatile private var fullPackEnergy: Float = 0f
    @Volatile private var nominalEnergyRemaining: Float = 0f
    @Volatile private var energyBuffer: Float = 0f
    @Volatile private var maxRegenPower: Float = 0f
    @Volatile private var maxDischargePower: Float = 0f
    @Volatile private var packVoltage: Float = 0f
    @Volatile private var packCurrent: Float = 0f
    @Volatile private var packTMin: Float = 0f
    @Volatile private var packTMax: Float = 0f
    @Volatile private var odometer: Float = 0f
    @Volatile private var phoneBattery: Int = -1
    @Volatile private var currentTime: Long = 0L
    @Volatile private var showPhoneBattery: Boolean = true
    @Volatile private var showCarBattery: Boolean = true
    @Volatile private var showOdometer: Boolean = true
    @Volatile private var useImperial: Boolean = false

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
        accSetSpeed = state.accSetSpeed
        fullPackEnergy = state.fullPackEnergy
        nominalEnergyRemaining = state.nominalEnergyRemaining
        energyBuffer = state.energyBuffer
        maxRegenPower = state.maxRegenPower
        maxDischargePower = state.maxDischargePower
        packVoltage = state.packVoltage
        packCurrent = state.packCurrent
        packTMin = state.packTMin
        packTMax = state.packTMax
        odometer = state.odometer
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
    @JavascriptInterface fun getAccSetSpeed(): Float = accSetSpeed
    @JavascriptInterface fun getFullPackEnergy(): Float = fullPackEnergy
    @JavascriptInterface fun getNominalEnergyRemaining(): Float = nominalEnergyRemaining
    @JavascriptInterface fun getEnergyBuffer(): Float = energyBuffer
    @JavascriptInterface fun getMaxRegenPower(): Float = maxRegenPower
    @JavascriptInterface fun getMaxDischargePower(): Float = maxDischargePower
    @JavascriptInterface fun getPackVoltage(): Float = packVoltage
    @JavascriptInterface fun getPackCurrent(): Float = packCurrent
    @JavascriptInterface fun getPackTMin(): Float = packTMin
    @JavascriptInterface fun getPackTMax(): Float = packTMax
    @JavascriptInterface fun getOdometer(): Float = odometer
    @JavascriptInterface fun getPhoneBattery(): Int = phoneBattery
    @JavascriptInterface fun getCurrentTime(): Long = currentTime
    @JavascriptInterface fun getShowPhoneBattery(): Boolean = showPhoneBattery
    @JavascriptInterface fun getShowCarBattery(): Boolean = showCarBattery
    @JavascriptInterface fun getShowOdometer(): Boolean = showOdometer
    @JavascriptInterface fun isImperial(): Boolean = useImperial

    fun updatePhoneBattery(level: Int) {
        phoneBattery = level
    }

    fun updateCurrentTime(time: Long) {
        currentTime = time
    }

    fun updateDisplaySettings(settings: DisplaySettings) {
        showPhoneBattery = settings.showPhoneBattery
        showCarBattery = settings.showCarBattery
        showOdometer = settings.showOdometer
        useImperial = settings.useImperial
    }
}
