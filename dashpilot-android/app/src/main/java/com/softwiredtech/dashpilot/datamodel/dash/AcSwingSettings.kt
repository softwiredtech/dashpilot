package com.softwiredtech.dashpilot.datamodel.dash

const val AC_SWING_SIDE_DRIVER = 0
const val AC_SWING_SIDE_PASSENGER = 1
const val AC_SWING_SIDE_BOTH = 2

const val AC_SWING_INTENSITY_LOW = 0
const val AC_SWING_INTENSITY_MEDIUM = 1
const val AC_SWING_INTENSITY_FULL = 2

data class AcSwingSettings(
    val enabled: Boolean = false,
    val side: Int = AC_SWING_SIDE_DRIVER,
    val intensity: Int = AC_SWING_INTENSITY_FULL,
)
