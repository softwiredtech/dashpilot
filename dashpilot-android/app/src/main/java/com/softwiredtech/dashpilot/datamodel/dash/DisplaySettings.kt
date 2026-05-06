package com.softwiredtech.dashpilot.datamodel.dash

enum class ThemeMode(val prefValue: String) {
    LIGHT("light"),
    DARK("dark"),
    AUTO("auto");

    fun resolve(isSystemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        AUTO -> isSystemDark
    }

    companion object {
        fun fromPref(value: String?): ThemeMode {
            return values().firstOrNull { it.prefValue == value } ?: AUTO
        }
    }
}

data class DisplaySettings(
    val showPhoneBattery: Boolean = DEFAULT_SHOW_PHONE_BATTERY,
    val showCarBattery: Boolean = DEFAULT_SHOW_CAR_BATTERY,
    val showOdometer: Boolean = DEFAULT_SHOW_ODOMETER,
    val useImperial: Boolean = DEFAULT_USE_IMPERIAL,
    val themeMode: ThemeMode = ThemeMode.fromPref(DEFAULT_THEME_MODE),
    val alwaysOnBlindSpotMonitor: Boolean = DEFAULT_ALWAYS_ON_BLIND_SPOT_MONITOR,
    val renderQuality: Int = DEFAULT_RENDER_QUALITY,
    val darkModeBackgroundGray: Int = DEFAULT_DARK_MODE_BACKGROUND_GRAY
) {
    fun isDarkMode(isSystemDark: Boolean): Boolean = themeMode.resolve(isSystemDark)
}
