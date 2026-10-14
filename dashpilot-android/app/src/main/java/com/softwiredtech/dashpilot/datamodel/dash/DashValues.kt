package com.softwiredtech.dashpilot.datamodel.dash

import java.time.Instant
import java.time.ZoneId

fun DashState.dashValues(): Map<String, Any> {
    val c = carState
    val d = displaySettings
    return mapOf(
        "egoSpeed" to c.egoSpeed,
        "gear" to c.gear,
        "egoSteeringAngle" to c.egoSteeringAngle,
        "leftBlinker" to c.leftBlinker,
        "rightBlinker" to c.rightBlinker,
        "anyDoorOpen" to c.anyDoorOpen,
        "buckleStatus" to c.buckleStatus,
        "odometer" to c.odometer,
        "adasOn" to c.adasOn,
        "accSetSpeed" to c.accSetSpeed,
        "fusedSpeedLimit" to c.fusedSpeedLimit,
        "leftBlindSpot" to c.leftBlindSpot,
        "rightBlindSpot" to c.rightBlindSpot,
        "trafficLightColor" to c.trafficLightColor,
        "stopLineDist" to c.stopLineDist,
        "changingLane" to c.changingLane,
        "madsActive" to c.madsActive,
        "experimentalMode" to c.experimentalMode,
        "selfdriveActive" to c.selfdriveActive,
        "nominalEnergyRemaining" to c.nominalEnergyRemaining,
        "fullPackEnergy" to c.fullPackEnergy,
        "energyBuffer" to c.energyBuffer,
        "powerLevel" to c.powerLevel,
        "packVoltage" to c.packVoltage,
        "packCurrent" to c.packCurrent,
        "maxRegenPower" to c.maxRegenPower,
        "maxDischargePower" to c.maxDischargePower,
        "packTMin" to c.packTMin,
        "packTMax" to c.packTMax,
        // Rive numbers are 32-bit floats, too coarse for epoch milliseconds.
        "currentTime" to secondsSinceLocalMidnight(currentTime),
        "phoneBattery" to phoneBattery.toFloat(),
        "isImperial" to d.useImperial,
        "darkMode" to d.darkMode,
        "darkModeBackgroundGray" to d.darkModeBackgroundGray.toFloat(),
        "showPhoneBattery" to d.showPhoneBattery,
        "showCarBattery" to d.showCarBattery,
        "showOdometer" to d.showOdometer,
        "alwaysOnBlindSpotMonitor" to d.alwaysOnBlindSpotMonitor,
        "renderQuality" to d.renderQuality.toFloat(),
        "dataSourceType" to dataSourceType,
        "speedCameraDistance" to speedCameraDistance.toFloat(),
    )
}

private fun secondsSinceLocalMidnight(epochMillis: Long): Float =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay().toFloat()
