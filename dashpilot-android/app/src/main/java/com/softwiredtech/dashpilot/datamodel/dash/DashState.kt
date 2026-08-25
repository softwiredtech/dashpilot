package com.softwiredtech.dashpilot.datamodel.dash

data class DashState(
    val carState: CarState = CarState(),
    val phoneBattery: Int = -1,
    val currentTime: Long = 0L,
    val displaySettings: DisplaySettings = DisplaySettings(),
    val speedCameraDistance: Int = -1,
    val dataSourceType: String = ""
)
