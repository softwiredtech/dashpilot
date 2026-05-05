package com.softwiredtech.dashpilot.datamodel.dash

data class DisplaySettings(
    val showPhoneBattery: Boolean = DEFAULT_SHOW_PHONE_BATTERY,
    val showCarBattery: Boolean = DEFAULT_SHOW_CAR_BATTERY,
    val showOdometer: Boolean = DEFAULT_SHOW_ODOMETER,
    val useImperial: Boolean = DEFAULT_USE_IMPERIAL,
    val darkMode: Boolean = DEFAULT_DARK_MODE,
    val alwaysOnBlindSpotMonitor: Boolean = DEFAULT_ALWAYS_ON_BLIND_SPOT_MONITOR,
    val renderQuality: Int = DEFAULT_RENDER_QUALITY,
    val darkModeBackgroundGray: Int = DEFAULT_DARK_MODE_BACKGROUND_GRAY
)
