package com.softwiredtech.dashpilot.datamodel.dash

data class DisplaySettings(
    val showPhoneBattery: Boolean = true,
    val showCarBattery: Boolean = true,
    val showOdometer: Boolean = true,
    val useImperial: Boolean = false,
    val darkMode: Boolean = false,
    val alwaysOnBlindSpotMonitor: Boolean = true
)
